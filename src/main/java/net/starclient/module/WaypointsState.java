package net.starclient.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.starclient.StarClient;
import net.starclient.gui.WaypointsScreen;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class WaypointsState {

    public static final int[] PALETTE = {
            0xFF38BDF8, // Sky Blue
            0xFF34D399, // Emerald
            0xFFFACC15, // Yellow
            0xFFFB923C, // Orange
            0xFFF87171, // Red
            0xFFA78BFA, // Purple
            0xFFF472B6, // Pink
            0xFF22D3EE, // Cyan
            0xFFA3E635, // Lime
            0xFFFFFFFF  // White
    };

    public static final String DIM_OVERWORLD = "minecraft:overworld";
    public static final String DIM_NETHER = "minecraft:the_nether";
    public static final String DIM_END = "minecraft:the_end";

    public static final class Waypoint {
        private final String id;
        private String name;
        private int x;
        private int y;
        private int z;
        private String dimension;
        private String worldKey;
        private int color;
        private boolean enabled;

        public Waypoint(String id, String name, int x, int y, int z, String dimension, String worldKey, int color, boolean enabled) {
            this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
            this.name = (name == null || name.isBlank()) ? "Waypoint" : name.trim();
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = normalizeDimension(dimension);
            this.worldKey = (worldKey == null || worldKey.isBlank()) ? "global" : worldKey;
            this.color = color | 0xFF000000;
            this.enabled = enabled;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            if (name != null && !name.isBlank()) {
                this.name = name.trim();
            }
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getZ() {
            return z;
        }

        public void setZ(int z) {
            this.z = z;
        }

        public String getDimension() {
            return dimension;
        }

        public void setDimension(String dimension) {
            if (dimension != null && !dimension.isBlank()) {
                this.dimension = normalizeDimension(dimension);
            }
        }

        public String getWorldKey() {
            return worldKey;
        }

        public void setWorldKey(String worldKey) {
            if (worldKey != null && !worldKey.isBlank()) {
                this.worldKey = worldKey;
            }
        }

        public int getColor() {
            return color;
        }

        public void setColor(int color) {
            this.color = color | 0xFF000000;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void toggleEnabled() {
            this.enabled = !this.enabled;
        }
    }

    public record ResolvedWaypoint(Waypoint waypoint, double x, double y, double z, boolean netherTranslated) {
    }

    private static Module waypointsModule;
    private static final List<Waypoint> waypoints = new ArrayList<>();
    private static boolean loaded;
    private static int quickAddKey = GLFW.GLFW_KEY_INSERT;
    private static boolean quickAddHeldLastTick;
    private static boolean wasOnDeathScreen;

    private WaypointsState() {
    }

    public static void setWaypointsModule(Module module) {
        waypointsModule = module;
    }

    public static boolean isActive() {
        return waypointsModule != null && waypointsModule.isEnabled();
    }

    public static boolean isShowBeams() {
        return waypointsModule == null
                || !(waypointsModule.getSetting("Show Beam") instanceof BooleanSetting bs)
                || bs.getValue();
    }

    public static boolean isShowDistance() {
        return waypointsModule == null
                || !(waypointsModule.getSetting("Show Distance") instanceof BooleanSetting bs)
                || bs.getValue();
    }

    public static boolean isNetherCoordsSync() {
        return waypointsModule != null
                && waypointsModule.getSetting("Cross-Dimension Sync (8:1)") instanceof BooleanSetting bs
                && bs.getValue();
    }

    public static boolean isAutoDeathWaypoint() {
        return waypointsModule == null
                || !(waypointsModule.getSetting("Auto Death Waypoint") instanceof BooleanSetting bs)
                || bs.getValue();
    }

    public static double getMaxRenderDistance() {
        if (waypointsModule != null && waypointsModule.getSetting("Max Distance") instanceof NumberSetting ns) {
            return ns.getValue();
        }
        return 5000.0;
    }

    public static double getMarkerScale() {
        if (waypointsModule != null && waypointsModule.getSetting("Scale") instanceof NumberSetting ns) {
            return ns.getValue();
        }
        return 1.0;
    }

    public static String getQuickAddAction() {
        if (waypointsModule != null && waypointsModule.getSetting("Quick Add Action") instanceof ModeSetting ms) {
            return ms.getValue();
        }
        return "Open Creator";
    }

    public static int getQuickAddKey() {
        ensureLoaded();
        return quickAddKey;
    }

    public static void setQuickAddKey(int key) {
        ensureLoaded();
        quickAddKey = key;
        save();
    }

    public static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        File file = getStorageFile();
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return;
            JsonObject root = parsed.getAsJsonObject();
            if (root.has("quickAddKey")) {
                quickAddKey = root.get("quickAddKey").getAsInt();
            }
            if (root.has("waypoints") && root.get("waypoints").isJsonArray()) {
                waypoints.clear();
                for (JsonElement elem : root.getAsJsonArray("waypoints")) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject obj = elem.getAsJsonObject();
                    String id = obj.has("id") ? obj.get("id").getAsString() : UUID.randomUUID().toString();
                    String name = obj.has("name") ? obj.get("name").getAsString() : "Waypoint";
                    int x = obj.has("x") ? obj.get("x").getAsInt() : 0;
                    int y = obj.has("y") ? obj.get("y").getAsInt() : 64;
                    int z = obj.has("z") ? obj.get("z").getAsInt() : 0;
                    String dim = obj.has("dimension") ? obj.get("dimension").getAsString() : DIM_OVERWORLD;
                    String wk = obj.has("worldKey") ? obj.get("worldKey").getAsString() : "global";
                    int col = obj.has("color") ? obj.get("color").getAsInt() : PALETTE[0];
                    boolean en = !obj.has("enabled") || obj.get("enabled").getAsBoolean();
                    waypoints.add(new Waypoint(id, name, x, y, z, dim, wk, col, en));
                }
            }
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to load waypoints.json", e);
        }
    }

    public static synchronized void save() {
        ensureLoaded();
        try {
            File file = getStorageFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JsonObject root = new JsonObject();
            root.addProperty("quickAddKey", quickAddKey);
            JsonArray arr = new JsonArray();
            for (Waypoint wp : waypoints) {
                JsonObject o = new JsonObject();
                o.addProperty("id", wp.getId());
                o.addProperty("name", wp.getName());
                o.addProperty("x", wp.getX());
                o.addProperty("y", wp.getY());
                o.addProperty("z", wp.getZ());
                o.addProperty("dimension", wp.getDimension());
                o.addProperty("worldKey", wp.getWorldKey());
                o.addProperty("color", wp.getColor());
                o.addProperty("enabled", wp.isEnabled());
                arr.add(o);
            }
            root.add("waypoints", arr);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to save waypoints.json", e);
        }
    }

    private static File getStorageFile() {
        File dir = new File(Minecraft.getInstance().gameDirectory, StarClient.CONFIG_FOLDER);
        return new File(dir, "waypoints.json");
    }

    public static String getCurrentWorldKey(Minecraft client) {
        if (client == null || client.level == null) {
            return null;
        }
        if (!client.isLocalServer() && client.getCurrentServer() != null) {
            ServerData sd = client.getCurrentServer();
            String ip = sd.ip.trim().toLowerCase(Locale.ROOT);
            return "mp:" + (ip.isEmpty() ? sd.name : ip);
        }
        if (client.getSingleplayerServer() != null) {
            String levelName = client.getSingleplayerServer().getWorldData().getLevelName();
            return "sp:" + levelName;
        }
        return "local";
    }

    public static String normalizeDimension(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return DIM_OVERWORLD;
        }
        String d = dimension.trim().toLowerCase(Locale.ROOT);
        if ("overworld".equals(d) || "ow".equals(d) || DIM_OVERWORLD.equals(d)) {
            return DIM_OVERWORLD;
        }
        if ("the_nether".equals(d) || "nether".equals(d) || DIM_NETHER.equals(d)) {
            return DIM_NETHER;
        }
        if ("the_end".equals(d) || "end".equals(d) || DIM_END.equals(d)) {
            return DIM_END;
        }
        return d.contains(":") ? d : "minecraft:" + d;
    }

    public static String getCurrentDimension(Minecraft client) {
        if (client == null || client.level == null) {
            return DIM_OVERWORLD;
        }
        ResourceKey<Level> key = client.level.dimension();
        if (Level.OVERWORLD.equals(key)) return DIM_OVERWORLD;
        if (Level.NETHER.equals(key)) return DIM_NETHER;
        if (Level.END.equals(key)) return DIM_END;
        return normalizeDimension(key.identifier().toString());
    }

    public static String formatDimensionShort(String dimension) {
        if (dimension == null) return "OW";
        return switch (dimension) {
            case DIM_OVERWORLD -> "OW";
            case DIM_NETHER -> "Nether";
            case DIM_END -> "End";
            default -> {
                int idx = dimension.indexOf(':');
                yield idx >= 0 ? dimension.substring(idx + 1) : dimension;
            }
        };
    }

    public static synchronized List<Waypoint> getAllWaypoints() {
        ensureLoaded();
        return new ArrayList<>(waypoints);
    }

    public static synchronized List<Waypoint> getWaypointsForCurrentContext(Minecraft client) {
        ensureLoaded();
        String wk = getCurrentWorldKey(client);
        if (wk == null) {
            return new ArrayList<>(waypoints);
        }
        List<Waypoint> result = new ArrayList<>();
        for (Waypoint wp : waypoints) {
            if (wk.equalsIgnoreCase(wp.getWorldKey())) {
                result.add(wp);
            }
        }
        return result;
    }

    public static synchronized List<ResolvedWaypoint> getRenderableWaypoints(Minecraft client) {
        ensureLoaded();
        if (client == null || client.level == null || client.player == null) {
            return List.of();
        }
        String wk = getCurrentWorldKey(client);
        String curDim = getCurrentDimension(client);
        boolean netherSync = isNetherCoordsSync();
        double maxDist = getMaxRenderDistance();
        double maxDistSq = maxDist * maxDist;

        List<ResolvedWaypoint> out = new ArrayList<>();
        for (Waypoint wp : waypoints) {
            if (!wp.isEnabled() || !wk.equalsIgnoreCase(wp.getWorldKey())) continue;

            double tx = wp.getX() + 0.5;
            double ty = wp.getY() + 0.5;
            double tz = wp.getZ() + 0.5;
            boolean translated = false;

            if (curDim.equals(wp.getDimension())) {
                // Direct match
            } else if (netherSync && DIM_OVERWORLD.equals(curDim) && DIM_NETHER.equals(wp.getDimension())) {
                tx = wp.getX() * 8.0 + 0.5;
                tz = wp.getZ() * 8.0 + 0.5;
                translated = true;
            } else if (netherSync && DIM_NETHER.equals(curDim) && DIM_OVERWORLD.equals(wp.getDimension())) {
                tx = (wp.getX() / 8.0) + 0.5;
                tz = (wp.getZ() / 8.0) + 0.5;
                translated = true;
            } else {
                continue;
            }

            double dx = tx - client.player.getX();
            double dy = ty - client.player.getY();
            double dz = tz - client.player.getZ();
            if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                out.add(new ResolvedWaypoint(wp, tx, ty, tz, translated));
            }
        }
        return out;
    }

    public static synchronized Waypoint addWaypoint(String name, int x, int y, int z, String dimension, String worldKey, int color) {
        ensureLoaded();
        Waypoint wp = new Waypoint(UUID.randomUUID().toString(), name, x, y, z, dimension, worldKey, color, true);
        waypoints.add(wp);
        save();
        return wp;
    }

    public static synchronized Waypoint addQuickWaypointHere(Minecraft client) {
        ensureLoaded();
        if (client == null || client.player == null || client.level == null) return null;
        String wk = getCurrentWorldKey(client);
        String dim = getCurrentDimension(client);
        BlockPos pos = client.player.blockPosition();
        int existingInWorld = getWaypointsForCurrentContext(client).size();
        int color = PALETTE[existingInWorld % PALETTE.length];
        String name = "Waypoint #" + (existingInWorld + 1);
        return addWaypoint(name, pos.getX(), pos.getY(), pos.getZ(), dim, wk, color);
    }

    public static synchronized void removeWaypoint(String id) {
        ensureLoaded();
        if (waypoints.removeIf(w -> w.getId().equals(id))) {
            save();
        }
    }

    public static void onClientTick(Minecraft client) {
        if (client == null) return;
        ensureLoaded();

        boolean onDeath = client.gui.screen() instanceof DeathScreen;
        if (onDeath && !wasOnDeathScreen && client.player != null && client.level != null) {
            if (isActive() && isAutoDeathWaypoint()) {
                recordDeathWaypoint(client);
            }
        }
        wasOnDeathScreen = onDeath;

        if (client.player != null && client.level != null && client.gui.screen() == null && isActive()) {
            if (quickAddKey > 0) {
                boolean down = InputConstants.isKeyDown(client.getWindow(), quickAddKey);
                if (down && !quickAddHeldLastTick) {
                    if ("Instant Save".equals(getQuickAddAction())) {
                        Waypoint wp = addQuickWaypointHere(client);
                        if (wp != null) {
                            client.gui.hud.getChat().addClientSystemMessage(
                                    Component.literal("§b⭐ §fSaved waypoint §b" + wp.getName()
                                            + " §7at §f" + wp.getX() + ", " + wp.getY() + ", " + wp.getZ()));
                        }
                    } else {
                        client.gui.setScreen(WaypointsScreen.openCreateAtPlayer(null));
                    }
                }
                quickAddHeldLastTick = down;
            } else {
                quickAddHeldLastTick = false;
            }
        } else {
            quickAddHeldLastTick = false;
        }
    }

    private static synchronized void recordDeathWaypoint(Minecraft client) {
        String wk = getCurrentWorldKey(client);
        String dim = getCurrentDimension(client);
        assert client.player != null;
        BlockPos pos = client.player.blockPosition();
        String deathLabel = "Last Death";

        Waypoint target = null;
        for (Waypoint wp : waypoints) {
            if (wk.equalsIgnoreCase(wp.getWorldKey()) && deathLabel.equalsIgnoreCase(wp.getName())) {
                target = wp;
                break;
            }
        }
        if (target != null) {
            target.setX(pos.getX());
            target.setY(pos.getY());
            target.setZ(pos.getZ());
            target.setDimension(dim);
            target.setColor(0xFFF87171);
            target.setEnabled(true);
            save();
        } else {
            addWaypoint(deathLabel, pos.getX(), pos.getY(), pos.getZ(), dim, wk, 0xFFF87171);
        }

        client.gui.hud.getChat().addClientSystemMessage(
                Component.literal("§c☠ §fDeath waypoint saved at §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                        + " §7(" + formatDimensionShort(dim) + ")"));
    }
}
