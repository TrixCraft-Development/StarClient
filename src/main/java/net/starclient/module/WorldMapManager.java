package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.starclient.StarClient;
import net.starclient.gui.WorldMapScreen;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.Setting;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Background explored-terrain chunk cache & persistence manager for the
 * Fullscreen Interactive "World Map" (WORLD module).
 * Stores 16x16 surface color, surface Y height, and biome metadata per explored chunk
 * separately for each Singleplayer world / Multiplayer server and dimension.
 */
public final class WorldMapManager {

    private static final int MAGIC = 0x53544D50; // "STMP"
    private static final int FILE_VERSION = 1;
    private static final int MAX_CHUNKS_PER_MAP = 6400;
    private static final long REFRESH_INTERVAL_MS = 10_000L;

    public static final class ChunkTile {
        public final int cx;
        public final int cz;
        public final int[] colors;    // 256 ARGB pixels (lz * 16 + lx)
        public final short[] heights; // 256 surface Y heights
        public volatile String biomeName;
        public volatile long updatedMs;

        public ChunkTile(int cx, int cz, int[] colors, short[] heights, String biomeName, long updatedMs) {
            this.cx = cx;
            this.cz = cz;
            this.colors = colors;
            this.heights = heights;
            this.biomeName = biomeName != null ? biomeName : "Unknown";
            this.updatedMs = updatedMs;
        }
    }

    private static Module worldMapModule;
    private static final Map<String, Map<Long, ChunkTile>> MAPS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LOADED_KEYS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> DIRTY_KEYS = new ConcurrentHashMap<>();
    private static final AtomicLong REVISION = new AtomicLong(1L);

    private static String activeMapKey = null;
    private static int scanCursor = 0;
    private static long lastAutoSaveMs = 0L;

    private WorldMapManager() {
    }

    public static void setModule(Module mod) {
        worldMapModule = mod;
    }

    public static Module getModule() {
        return worldMapModule;
    }

    public static boolean isEnabled() {
        return worldMapModule != null && worldMapModule.isEnabled() && worldMapModule.canUse();
    }

    public static long getRevision() {
        return REVISION.get();
    }

    public static String getCurrentMapKey(Minecraft client) {
        if (client == null || client.level == null) return null;
        String wk = WaypointsState.getCurrentWorldKey(client);
        String dim = WaypointsState.getCurrentDimension(client);
        if (wk == null) return null;
        return wk + "|" + dim;
    }

    public static Map<Long, ChunkTile> getCurrentTiles(Minecraft client) {
        String key = getCurrentMapKey(client);
        if (key == null) return Map.of();
        ensureLoaded(key);
        return MAPS.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
    }

    public static ChunkTile getTile(Map<Long, ChunkTile> map, int cx, int cz) {
        if (map == null) return null;
        return map.get(packChunkKey(cx, cz));
    }

    public static int getExploredChunkCount(Minecraft client) {
        Map<Long, ChunkTile> tiles = getCurrentTiles(client);
        return tiles.size();
    }

    public static boolean isShowWaypoints() {
        return getBool("Show Waypoints", true);
    }

    public static boolean isShowPlayers() {
        return getBool("Show Players", true);
    }

    public static boolean isShowChunkGrid() {
        return getBool("Show Chunk Grid", false);
    }

    public static void toggleChunkGrid() {
        if (worldMapModule != null && worldMapModule.getSetting("Show Chunk Grid") instanceof BooleanSetting bs) {
            bs.setValue(!bs.getValue());
            REVISION.incrementAndGet();
        }
    }

    public static boolean isShowCoordsAndBiome() {
        return getBool("Show Coordinates & Biome", true);
    }

    public static boolean isSaveExploredMap() {
        return getBool("Save Explored Map", true);
    }

    public static void clearCurrentMap(Minecraft client) {
        String key = getCurrentMapKey(client);
        if (key == null) return;
        Map<Long, ChunkTile> tiles = MAPS.get(key);
        if (tiles != null) {
            tiles.clear();
        }
        DIRTY_KEYS.remove(key);
        File file = getMapFile(key);
        if (file != null && file.exists()) {
            try {
                file.delete();
            } catch (Throwable ignored) {
            }
        }
        REVISION.incrementAndGet();
    }

    public static void clearRuntimeState() {
        saveAllDirtyAsync();
        scanCursor = 0;
    }

    public static void onClientTick(Minecraft client) {
        if (worldMapModule == null) return;

        // Handle ClickGUI action buttons ("Open World Map" / "Clear Map Cache")
        if (worldMapModule.getSetting("Open World Map") instanceof BooleanSetting openBtn && openBtn.getValue()) {
            openBtn.setValue(false);
            if (client != null && client.level != null && client.player != null) {
                scanLoadedNow(client);
                client.gui.setScreen(new WorldMapScreen(null));
            }
        }
        if (worldMapModule.getSetting("Clear Map Cache") instanceof BooleanSetting clearBtn && clearBtn.getValue()) {
            clearBtn.setValue(false);
            if (client != null && client.level != null) {
                clearCurrentMap(client);
            }
        }

        if (!isEnabled() || client == null || client.level == null || client.player == null) {
            if (activeMapKey != null) {
                saveAllDirtyAsync();
                activeMapKey = null;
            }
            return;
        }

        String mapKey = getCurrentMapKey(client);
        if (mapKey == null) return;

        if (!mapKey.equals(activeMapKey)) {
            if (activeMapKey != null) {
                saveMapAsync(activeMapKey);
            }
            activeMapKey = mapKey;
            ensureLoaded(mapKey);
            scanCursor = 0;
        }

        // Incrementally scan loaded chunks around the player (low CPU impact: max 6 chunks/tick)
        int pcx = client.player.chunkPosition().x();
        int pcz = client.player.chunkPosition().z();
        int playerY = Mth.floor(client.player.getY());
        int radius = Math.clamp(client.options.renderDistance().get(), 4, 14);
        int diameter = radius * 2 + 1;
        int totalSlots = diameter * diameter;
        if (totalSlots <= 0) return;

        Map<Long, ChunkTile> tiles = MAPS.computeIfAbsent(mapKey, k -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();
        boolean hasCeiling = client.level.dimensionType().hasCeiling();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        int scannedThisTick = 0;
        int checks = Math.min(totalSlots, 48);
        for (int i = 0; i < checks && scannedThisTick < 6; i++) {
            int idx = (scanCursor + i) % totalSlots;
            int dx = (idx % diameter) - radius;
            int dz = (idx / diameter) - radius;
            int cx = pcx + dx;
            int cz = pcz + dz;

            if (!client.level.hasChunk(cx, cz)) continue;
            long chunkKey = packChunkKey(cx, cz);
            ChunkTile existing = tiles.get(chunkKey);
            boolean nearPlayer = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
            long maxAge = nearPlayer ? REFRESH_INTERVAL_MS : (REFRESH_INTERVAL_MS * 6L);
            if (existing != null && (now - existing.updatedMs) < maxAge) {
                continue;
            }

            ChunkTile tile = sampleChunk(client.level, cx, cz, playerY, hasCeiling, mutable, now);
            if (tile != null) {
                tiles.put(chunkKey, tile);
                DIRTY_KEYS.put(mapKey, Boolean.TRUE);
                scannedThisTick++;
            }
        }
        scanCursor = (scanCursor + checks) % totalSlots;

        if (scannedThisTick > 0) {
            pruneIfOversized(tiles, pcx, pcz);
            REVISION.incrementAndGet();
        }

        if (isSaveExploredMap() && now - lastAutoSaveMs > 45_000L && Boolean.TRUE.equals(DIRTY_KEYS.get(mapKey))) {
            lastAutoSaveMs = now;
            saveMapAsync(mapKey);
        }
    }

    /**
     * Immediately populates all currently loaded chunks within a reasonable radius around the player
     * when opening the World Map so there are no blank patches around the player's position.
     */
    public static void scanLoadedNow(Minecraft client) {
        if (client == null || client.level == null || client.player == null) return;
        String mapKey = getCurrentMapKey(client);
        if (mapKey == null) return;
        ensureLoaded(mapKey);

        Map<Long, ChunkTile> tiles = MAPS.computeIfAbsent(mapKey, k -> new ConcurrentHashMap<>());
        int pcx = client.player.chunkPosition().x();
        int pcz = client.player.chunkPosition().z();
        int playerY = Mth.floor(client.player.getY());
        int radius = Math.clamp(client.options.renderDistance().get(), 4, 12);
        boolean hasCeiling = client.level.dimensionType().hasCeiling();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        long now = System.currentTimeMillis();
        int added = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                if (!client.level.hasChunk(cx, cz)) continue;
                long key = packChunkKey(cx, cz);
                ChunkTile existing = tiles.get(key);
                if (existing != null && (now - existing.updatedMs) < REFRESH_INTERVAL_MS * 2L) continue;
                ChunkTile tile = sampleChunk(client.level, cx, cz, playerY, hasCeiling, mutable, now);
                if (tile != null) {
                    tiles.put(key, tile);
                    added++;
                }
            }
        }
        if (added > 0) {
            DIRTY_KEYS.put(mapKey, Boolean.TRUE);
            pruneIfOversized(tiles, pcx, pcz);
            REVISION.incrementAndGet();
        }
    }

    private static ChunkTile sampleChunk(ClientLevel level, int cx, int cz, int playerY,
                                         boolean hasCeiling, BlockPos.MutableBlockPos pos, long now) {
        int[] colors = new int[256];
        short[] heights = new short[256];
        int baseX = cx << 4;
        int baseZ = cz << 4;
        int minY = level.getMinY();
        int maxY = level.getMaxY() - 1;
        boolean hasNorthChunk = level.hasChunk(cx, cz - 1);

        int[] prevRowY = new int[16];
        for (int lx = 0; lx < 16; lx++) {
            int bx = baseX + lx;
            if (!hasCeiling && hasNorthChunk) {
                prevRowY[lx] = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, baseZ - 1) - 1;
            } else {
                prevRowY[lx] = Integer.MIN_VALUE;
            }
        }

        for (int lz = 0; lz < 16; lz++) {
            int bz = baseZ + lz;
            for (int lx = 0; lx < 16; lx++) {
                int bx = baseX + lx;
                int idx = (lz << 4) | lx;

                int topY;
                if (hasCeiling) {
                    topY = findCaveFloor(level, bx, bz, playerY, minY, maxY, pos);
                } else {
                    topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
                }

                if (topY < minY) {
                    colors[idx] = 0xFF0F172A;
                    heights[idx] = (short) Math.clamp(minY, Short.MIN_VALUE, Short.MAX_VALUE);
                    prevRowY[lx] = minY;
                    continue;
                }

                pos.set(bx, topY, bz);
                BlockState state = level.getBlockState(pos);
                MapColor mapColor = state.getMapColor(level, pos);
                while (mapColor == MapColor.NONE && topY > minY) {
                    topY--;
                    pos.set(bx, topY, bz);
                    state = level.getBlockState(pos);
                    mapColor = state.getMapColor(level, pos);
                }

                heights[idx] = (short) Math.clamp(topY, Short.MIN_VALUE, Short.MAX_VALUE);
                int northY = prevRowY[lx] == Integer.MIN_VALUE ? topY : prevRowY[lx];
                prevRowY[lx] = topY;

                if (mapColor == MapColor.NONE) {
                    colors[idx] = 0xFF182234;
                    continue;
                }

                MapColor.Brightness brightness = MapColor.Brightness.NORMAL;
                if (mapColor == MapColor.WATER) {
                    int depth = 0;
                    int wy = topY;
                    while (wy > minY && depth < 8 && !level.getFluidState(pos.set(bx, wy, bz)).isEmpty()) {
                        wy--;
                        depth++;
                    }
                    brightness = depth > 5 ? MapColor.Brightness.LOW
                            : (depth > 2 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH);
                } else if (!hasCeiling) {
                    if (topY > northY) {
                        brightness = MapColor.Brightness.HIGH;
                    } else if (topY < northY) {
                        brightness = MapColor.Brightness.LOW;
                    }
                } else {
                    // Subtle elevation tint inside Nether/caves
                    brightness = ((topY & 3) == 0) ? MapColor.Brightness.HIGH
                            : (((topY & 3) == 3) ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL);
                }

                colors[idx] = mapColor.calculateARGBColor(brightness) | 0xFF000000;
            }
        }

        int centerH = heights[(8 << 4) | 8];
        String biomeName = resolveBiomeName(level, pos.set(baseX + 8, Math.clamp(centerH, minY, maxY), baseZ + 8));
        return new ChunkTile(cx, cz, colors, heights, biomeName, now);
    }

    private static int findCaveFloor(ClientLevel level, int bx, int bz, int playerY,
                                     int minY, int maxY, BlockPos.MutableBlockPos pos) {
        int startY = Math.min(maxY, playerY + 14);
        int floorLimit = Math.max(minY, playerY - 36);
        boolean seenAir = false;
        for (int y = startY; y >= floorLimit; y--) {
            pos.set(bx, y, bz);
            BlockState bs = level.getBlockState(pos);
            if (bs.isAir()) {
                seenAir = true;
            } else if (seenAir && bs.getMapColor(level, pos) != MapColor.NONE) {
                return y;
            }
        }
        return Math.clamp(playerY - 1, minY, maxY);
    }

    private static String resolveBiomeName(ClientLevel level, BlockPos pos) {
        try {
            Holder<Biome> holder = level.getBiome(pos);
            ResourceKey<Biome> key = holder.unwrapKey().orElse(null);
            if (key == null) return "Unknown";
            String path = key.identifier().getPath();
            String[] parts = path.split("_");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isEmpty()) continue;
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) {
                    sb.append(parts[i].substring(1));
                }
            }
            return sb.isEmpty() ? "Unknown" : sb.toString();
        } catch (Throwable t) {
            return "Unknown";
        }
    }

    private static void pruneIfOversized(Map<Long, ChunkTile> tiles, int pcx, int pcz) {
        if (tiles.size() <= MAX_CHUNKS_PER_MAP) return;
        int toRemove = tiles.size() - (MAX_CHUNKS_PER_MAP - 400);
        if (toRemove <= 0) return;
        List<ChunkTile> all = new ArrayList<>(tiles.values());
        all.sort(Comparator.comparingLong(t -> {
            long dx = (long) t.cx - pcx;
            long dz = (long) t.cz - pcz;
            return -(dx * dx + dz * dz);
        }));
        for (int i = 0; i < toRemove && i < all.size(); i++) {
            ChunkTile t = all.get(i);
            tiles.remove(packChunkKey(t.cx, t.cz));
        }
    }

    public static long packChunkKey(int cx, int cz) {
        return (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
    }

    // -------------------------------------------------------------------------
    // Lightweight GZIP Binary Persistence (.minecraft/config/starclient/worldmap/)
    // -------------------------------------------------------------------------

    private static void ensureLoaded(String mapKey) {
        if (!isSaveExploredMap()) return;
        if (LOADED_KEYS.putIfAbsent(mapKey, Boolean.TRUE) != null) return;
        MAPS.computeIfAbsent(mapKey, k -> new ConcurrentHashMap<>());
        Thread loader = new Thread(() -> loadMapSync(mapKey), "StarClient-WorldMap-Load");
        loader.setDaemon(true);
        loader.start();
    }

    private static void loadMapSync(String mapKey) {
        File file = getMapFile(mapKey);
        if (file == null || !file.isFile()) return;
        Map<Long, ChunkTile> tiles = MAPS.computeIfAbsent(mapKey, k -> new ConcurrentHashMap<>());
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(new FileInputStream(file))))) {
            int magic = in.readInt();
            int ver = in.readInt();
            if (magic != MAGIC || ver != FILE_VERSION) return;
            int count = Math.min(in.readInt(), MAX_CHUNKS_PER_MAP);
            for (int i = 0; i < count; i++) {
                int cx = in.readInt();
                int cz = in.readInt();
                String biome = in.readUTF();
                int[] colors = new int[256];
                short[] heights = new short[256];
                for (int j = 0; j < 256; j++) {
                    colors[j] = in.readInt();
                }
                for (int j = 0; j < 256; j++) {
                    heights[j] = in.readShort();
                }
                tiles.putIfAbsent(packChunkKey(cx, cz), new ChunkTile(cx, cz, colors, heights, biome, 0L));
            }
            REVISION.incrementAndGet();
        } catch (Throwable t) {
            StarClient.LOGGER.warn("Failed to load World Map cache {}", file.getName(), t);
        }
    }

    public static void saveAllDirtyAsync() {
        if (!isSaveExploredMap()) return;
        List<String> keys = new ArrayList<>(DIRTY_KEYS.keySet());
        for (String key : keys) {
            saveMapAsync(key);
        }
    }

    public static void saveAllDirtySync() {
        if (!isSaveExploredMap()) return;
        List<String> keys = new ArrayList<>(DIRTY_KEYS.keySet());
        for (String key : keys) {
            saveMapSync(key);
        }
    }

    private static void saveMapAsync(String mapKey) {
        if (!isSaveExploredMap() || mapKey == null) return;
        if (DIRTY_KEYS.remove(mapKey) == null) return;
        Thread saver = new Thread(() -> saveMapSync(mapKey), "StarClient-WorldMap-Save");
        saver.setDaemon(true);
        saver.start();
    }

    private static void saveMapSync(String mapKey) {
        if (mapKey == null) return;
        DIRTY_KEYS.remove(mapKey);
        Map<Long, ChunkTile> tiles = MAPS.get(mapKey);
        if (tiles == null || tiles.isEmpty()) return;
        File file = getMapFile(mapKey);
        if (file == null) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            List<ChunkTile> snapshot = new ArrayList<>(tiles.values());
            File tmp = new File(parent, file.getName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(tmp))))) {
                out.writeInt(MAGIC);
                out.writeInt(FILE_VERSION);
                out.writeInt(snapshot.size());
                for (ChunkTile tile : snapshot) {
                    out.writeInt(tile.cx);
                    out.writeInt(tile.cz);
                    out.writeUTF(tile.biomeName != null ? tile.biomeName : "Unknown");
                    for (int j = 0; j < 256; j++) {
                        out.writeInt(tile.colors[j]);
                    }
                    for (int j = 0; j < 256; j++) {
                        out.writeShort(tile.heights[j]);
                    }
                }
            }
            if (file.exists()) {
                file.delete();
            }
            tmp.renameTo(file);
        } catch (Throwable t) {
            StarClient.LOGGER.warn("Failed to save World Map cache {}", file.getName(), t);
        }
    }

    private static File getMapFile(String mapKey) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) return null;
        File dir = new File(new File(mc.gameDirectory, StarClient.CONFIG_FOLDER), "worldmap");
        return new File(dir, hashKey(mapKey) + ".stmp");
    }

    private static String hashKey(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format(Locale.ROOT, "%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private static boolean getBool(String name, boolean def) {
        if (worldMapModule == null) return def;
        Setting<?> s = worldMapModule.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }
}
