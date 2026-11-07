package net.starclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.module.WaypointsState;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.List;
import java.util.Locale;

/**
 * Top-down HUD Minimap with Square/Circle modes, rotation with player yaw or North-Up,
 * cached terrain surface MapColor shading, entity radar dots, and Waypoint pins.
 */
public final class MinimapRenderer {

    private static final int TEX_SIZE = 96;
    private static final Identifier MINIMAP_TEX_ID = Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "minimap_dynamic");

    private static DynamicTexture dynamicTexture;
    private static NativeImage nativeImage;

    private static final Long2IntOpenHashMap columnColorCache = new Long2IntOpenHashMap(16384);
    private static ClientLevel cachedLevel;
    private static long lastCacheSweepMs;
    private static int cachedSamplePlayerY = Integer.MIN_VALUE;

    private static long lastRasterMs;
    private static double lastPlayerX = Double.NaN;
    private static double lastPlayerZ = Double.NaN;
    private static float lastYaw = Float.NaN;
    private static double lastZoom = Double.NaN;
    private static boolean lastCircle;
    private static boolean lastRotate;
    private static boolean lastPreviewMode;

    private MinimapRenderer() {
    }

    private static void ensureTexture(Minecraft client) {
        if (dynamicTexture != null && nativeImage != null) return;
        nativeImage = new NativeImage(TEX_SIZE, TEX_SIZE, true);
        dynamicTexture = new DynamicTexture(() -> "starclient_minimap", nativeImage);
        client.getTextureManager().register(MINIMAP_TEX_ID, dynamicTexture);
    }

    public static int getMapBoxSize(Module mod) {
        if (mod != null && mod.getSetting("Size") instanceof NumberSetting ns) {
            return Math.clamp((int) Math.round(ns.getValue()), 64, 160);
        }
        return 96;
    }

    public static int getWidth(Module mod) {
        return getMapBoxSize(mod) + 6;
    }

    public static int getHeight(Module mod) {
        boolean showCoords = getBool(mod, "Show Coordinates", true);
        return getMapBoxSize(mod) + 6 + (showCoords ? 14 : 0);
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod,
                              HudLayout layout, int screenWidth, int screenHeight) {
        if (mod == null || client == null) return;
        renderInternal(extractor, client, mod, layout, screenWidth, screenHeight, client.level == null || client.player == null);
    }

    public static void renderPreview(GuiGraphicsExtractor extractor, Module mod) {
        if (mod == null) return;
        Minecraft client = Minecraft.getInstance();
        HudLayout layout = StarClient.getInstance().getHudLayout();
        renderInternal(extractor, client, mod, layout, extractor.guiWidth(), extractor.guiHeight(), true);
    }

    private static void renderInternal(GuiGraphicsExtractor extractor, Minecraft client, Module mod,
                                       HudLayout layout, int screenWidth, int screenHeight, boolean preview) {
        ensureTexture(client);

        int mapSize = getMapBoxSize(mod);
        int totalW = getWidth(mod);
        int totalH = getHeight(mod);

        int x = layout.effectiveX(HudLayout.MINIMAP, screenWidth, totalW);
        int y = layout.effectiveY(HudLayout.MINIMAP, screenHeight);
        x = Math.clamp(x, 2, Math.max(2, screenWidth - totalW - 2));
        y = Math.clamp(y, 2, Math.max(2, screenHeight - totalH - 2));

        boolean circle = isCircle(mod);
        boolean rotate = getBool(mod, "Rotate with Player", true);
        double zoom = getNum(mod);
        boolean showWaypoints = getBool(mod, "Show Waypoints", true);
        boolean showPlayers = getBool(mod, "Show Players", true);
        boolean showHostile = getBool(mod, "Show Hostile Mobs", true);
        boolean showPassive = getBool(mod, "Show Passive Mobs", false);
        boolean showItems = getBool(mod, "Show Items", false);
        boolean showCardinals = getBool(mod, "Show Cardinals", true);
        boolean showCoords = getBool(mod, "Show Coordinates", true);

        int mapX = x + 3;
        int mapY = y + 3;
        int centerX = mapX + mapSize / 2;
        int centerY = mapY + mapSize / 2;
        float halfMap = mapSize / 2.0f;

        int accent = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        boolean transparent = mod.isTransparentBackground();
        int frameBg = (mod.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;

        // Frame
        if (!transparent) {
            if (circle) {
                int r = (mapSize + 6) / 2;
                GuiRenderUtils.drawDropShadow(extractor, x, y, mapSize + 6, mapSize + 6, r, 3, 85);
                GuiRenderUtils.fillRoundedRect(extractor, x, y, mapSize + 6, mapSize + 6, r, frameBg);
                GuiRenderUtils.drawRoundedOutline(extractor, x, y, mapSize + 6, mapSize + 6, r, GuiRenderUtils.withAlpha(accent, 140));
            } else {
                GuiRenderUtils.drawDropShadow(extractor, x, y, totalW, mapSize + 6, 6, 3, 85);
                GuiRenderUtils.fillRoundedRect(extractor, x, y, totalW, mapSize + 6, 6, frameBg);
                GuiRenderUtils.drawRoundedOutline(extractor, x, y, totalW, mapSize + 6, 6, GuiRenderUtils.withAlpha(accent, 140));
            }
        }

        // Update terrain texture
        double px;
        if (preview) {
            px = 120.5;
        } else {
            assert client.player != null;
            px = client.player.getX();
        }
        double py = preview ? 64.0 : client.player.getY();
        double pz = preview ? -85.5 : client.player.getZ();
        float playerYaw = preview ? 180.0f : client.player.getYRot();

        updateTerrainTexture(client, px, py, pz, playerYaw, zoom, circle, rotate, preview);

        // Blit terrain
        extractor.blit(MINIMAP_TEX_ID, mapX, mapY, mapX + mapSize, mapY + mapSize, 0.0f, 1.0f, 0.0f, 1.0f);

        // Rotation trig (North-up corresponds to yaw = 180°)
        float effectiveYawDeg = rotate ? playerYaw : 180.0f;
        double rad = Math.toRadians(effectiveYawDeg);
        double cosYaw = Math.cos(rad);
        double sinYaw = Math.sin(rad);

        // Scale factor from world blocks to screen pixels on the minimap:
        // In texture space, half-width (TEX_SIZE / 2) covers (TEX_SIZE / 2) / zoom blocks.
        // Mapped onto mapSize pixels: pixelsPerBlock = zoom * (mapSize / (double) TEX_SIZE).
        double pixelsPerBlock = zoom * (mapSize / (double) TEX_SIZE);
        double maxWorldRadius = (halfMap - 3.0) / Math.max(0.01, pixelsPerBlock);

        // Entity Radar Dots
        if (!preview && client.level != null && client.player != null
                && (showPlayers || showHostile || showPassive || showItems)) {
            double scanRange = maxWorldRadius + 2.0;
            AABB box = new AABB(px - scanRange, py - 48.0, pz - scanRange, px + scanRange, py + 48.0, pz + scanRange);
            List<Entity> entities = client.level.getEntities(client.player, box, Entity::isAlive);
            for (Entity e : entities) {
                int dotColor;
                int dotSize = 2;
                switch (e) {
                    case Player player -> {
                        if (!showPlayers) continue;
                        dotColor = 0xFF38BDF8;
                        dotSize = 3;
                    }
                    case Enemy enemy -> {
                        if (!showHostile) continue;
                        dotColor = 0xFFEF4444;
                    }
                    case Mob mob -> {
                        if (!showPassive) continue;
                        dotColor = 0xFF34D399;
                    }
                    case ItemEntity itemEntity -> {
                        if (!showItems) continue;
                        dotColor = 0xFFFACC15;
                    }
                    case null, default -> {
                        continue;
                    }
                }

                double wx = e.getX() - px;
                double wz = e.getZ() - pz;
                double sx = (-wx * cosYaw - wz * sinYaw) * pixelsPerBlock;
                double sy = (wx * sinYaw - wz * cosYaw) * pixelsPerBlock;

                if (circle) {
                    if (sx * sx + sy * sy > (halfMap - 3) * (halfMap - 3)) continue;
                } else {
                    if (Math.abs(sx) > halfMap - 3 || Math.abs(sy) > halfMap - 3) continue;
                }

                int drawX = (int) Math.round(centerX + sx);
                int drawY = (int) Math.round(centerY + sy);
                extractor.fill(drawX - 1, drawY - 1, drawX + dotSize, drawY + dotSize, 0xCC000000);
                extractor.fill(drawX, drawY, drawX + dotSize - 1, drawY + dotSize - 1, dotColor);
            }
        }

        // Waypoints on Minimap
        if (showWaypoints && !preview && client.level != null) {
            List<WaypointsState.ResolvedWaypoint> wps = WaypointsState.getRenderableWaypoints(client);
            for (WaypointsState.ResolvedWaypoint rwp : wps) {
                double wx = rwp.x() - px;
                double wz = rwp.z() - pz;
                double sx = (-wx * cosYaw - wz * sinYaw) * pixelsPerBlock;
                double sy = (wx * sinYaw - wz * cosYaw) * pixelsPerBlock;

                float limit = halfMap - 4.0f;
                if (circle) {
                    double dist = Math.hypot(sx, sy);
                    if (dist > limit && dist > 0.001) {
                        sx = sx / dist * limit;
                        sy = sy / dist * limit;
                    }
                } else {
                    double maxAbs = Math.max(Math.abs(sx), Math.abs(sy));
                    if (maxAbs > limit && maxAbs > 0.001) {
                        sx = sx / maxAbs * limit;
                        sy = sy / maxAbs * limit;
                    }
                }

                int pinX = (int) Math.round(centerX + sx);
                int pinY = (int) Math.round(centerY + sy);
                int wpCol = 0xFF000000 | (rwp.waypoint().getColor() & 0x00FFFFFF);

                GuiRenderUtils.fillRoundedRect(extractor, pinX - 4, pinY - 4, 8, 8, 2, 0xDD0F172A);
                GuiRenderUtils.drawRoundedOutline(extractor, pinX - 4, pinY - 4, 8, 8, 2, wpCol);
                String wpName = rwp.waypoint().getName();
                String initial = wpName == null || wpName.isEmpty()
                        ? "W"
                        : wpName.substring(0, 1).toUpperCase(Locale.ROOT);
                int iw = client.font.width(initial);
                extractor.pose().pushMatrix();
                extractor.pose().translate(pinX, pinY);
                extractor.pose().scale(0.65f, 0.65f);
                extractor.text(client.font, initial, -iw / 2, -4, wpCol, false);
                extractor.pose().popMatrix();
            }
        }

        // Center Player Pointer
        if (rotate) {
            // Player always faces UP when map rotates with player
            extractor.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, 0xDD000000);
            extractor.fill(centerX - 1, centerY - 2, centerX + 2, centerY + 2, 0xFFFFFFFF);
            extractor.fill(centerX, centerY - 3, centerX + 1, centerY - 2, accent);
        } else {
            // Directional dot + small heading tick when North-Up
            double headRad = Math.toRadians(playerYaw);
            int hx = (int) Math.round(centerX - Math.sin(headRad) * 3.2);
            int hy = (int) Math.round(centerY + Math.cos(headRad) * 3.2);
            extractor.fill(centerX - 2, centerY - 2, centerX + 3, centerY + 3, 0xDD000000);
            extractor.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFFFFFFFF);
            extractor.fill(hx - 1, hy - 1, hx + 1, hy + 1, accent);
        }

        // Cardinal Directions (N, E, S, W)
        if (showCardinals) {
            drawCardinal(extractor, client, "N", 0, -1, cosYaw, sinYaw, centerX, centerY, halfMap - 5.0f, circle, 0xFFF87171);
            drawCardinal(extractor, client, "E", 1, 0, cosYaw, sinYaw, centerX, centerY, halfMap - 5.0f, circle, 0xFFE2E8F0);
            drawCardinal(extractor, client, "S", 0, 1, cosYaw, sinYaw, centerX, centerY, halfMap - 5.0f, circle, 0xFFE2E8F0);
            drawCardinal(extractor, client, "W", -1, 0, cosYaw, sinYaw, centerX, centerY, halfMap - 5.0f, circle, 0xFFE2E8F0);
        }

        // Coordinates Footer Pill
        if (showCoords) {
            String coordStr = String.format(Locale.US, "%d, %d, %d",
                    Mth.floor(px), Mth.floor(py), Mth.floor(pz));
            int pillY = y + mapSize + 7;
            int tw = client.font.width(coordStr);
            int pillW = Math.min(totalW, tw + 10);
            int pillX = x + (totalW - pillW) / 2;
            if (!transparent) {
                GuiRenderUtils.fillRoundedRect(extractor, pillX, pillY, pillW, 12, 3, frameBg);
                GuiRenderUtils.drawRoundedOutline(extractor, pillX, pillY, pillW, 12, 3, 0x28FFFFFF);
            }
            extractor.pose().pushMatrix();
            extractor.pose().translate(x + totalW / 2.0f, pillY + 2.5f);
            extractor.pose().scale(0.85f, 0.85f);
            extractor.text(client.font, coordStr, -tw / 2, 0, mod.getTextColor(),
                    StarClient.getInstance().getClientSettings().isHudTextShadow());
            extractor.pose().popMatrix();
        }
    }

    private static void drawCardinal(GuiGraphicsExtractor extractor, Minecraft client, String label,
                                     double dirX, double dirZ, double cosYaw, double sinYaw,
                                     int centerX, int centerY, float radius, boolean circle, int color) {
        double sx = -dirX * cosYaw - dirZ * sinYaw;
        double sy = dirX * sinYaw - dirZ * cosYaw;
        double px;
        double py;
        if (circle) {
            px = sx * radius;
            py = sy * radius;
        } else {
            double maxAbs = Math.max(Math.abs(sx), Math.abs(sy));
            double scale = maxAbs > 0.001 ? (radius / maxAbs) : radius;
            px = sx * scale;
            py = sy * scale;
        }
        int cx = (int) Math.round(centerX + px);
        int cy = (int) Math.round(centerY + py);
        GuiRenderUtils.fillRoundedRect(extractor, cx - 4, cy - 4, 8, 8, 2, 0xBB0B1120);
        int tw = client.font.width(label);
        extractor.pose().pushMatrix();
        extractor.pose().translate(cx, cy);
        extractor.pose().scale(0.75f, 0.75f);
        extractor.text(client.font, label, -tw / 2, -4, color, true);
        extractor.pose().popMatrix();
    }

    private static void updateTerrainTexture(Minecraft client, double px, double py, double pz,
                                             float playerYaw, double zoom, boolean circle, boolean rotate,
                                             boolean preview) {
        long now = System.currentTimeMillis();
        if (!preview && client.level != cachedLevel) {
            cachedLevel = client.level;
            columnColorCache.clear();
            lastCacheSweepMs = 0L;
        }

        int playerBlockY = Mth.floor(py);
        if (!preview && (now - lastCacheSweepMs > 650L || Math.abs(playerBlockY - cachedSamplePlayerY) > 6)) {
            if (columnColorCache.size() > 24000 || Math.abs(playerBlockY - cachedSamplePlayerY) > 6) {
                columnColorCache.clear();
            }
            cachedSamplePlayerY = playerBlockY;
            lastCacheSweepMs = now;
        }

        float effYaw = rotate ? playerYaw : 180.0f;
        boolean movedEnough = Double.isNaN(lastPlayerX)
                || Math.hypot(px - lastPlayerX, pz - lastPlayerZ) >= 0.45
                || Math.abs(Mth.wrapDegrees(effYaw - lastYaw)) >= 0.8f
                || Math.abs(zoom - lastZoom) > 0.001
                || circle != lastCircle
                || rotate != lastRotate
                || preview != lastPreviewMode;

        if (!movedEnough && (now - lastRasterMs) < 350L) {
            return;
        }
        if ((now - lastRasterMs) < 28L && preview == lastPreviewMode && circle == lastCircle) {
            return;
        }

        lastRasterMs = now;
        lastPlayerX = px;
        lastPlayerZ = pz;
        lastYaw = effYaw;
        lastZoom = zoom;
        lastCircle = circle;
        lastRotate = rotate;
        lastPreviewMode = preview;

        double rad = Math.toRadians(effYaw);
        double cosYaw = Math.cos(rad);
        double sinYaw = Math.sin(rad);
        double invZoom = 1.0 / Math.max(0.25, zoom);
        float half = TEX_SIZE / 2.0f;
        float circleLimitSq = (half - 0.5f) * (half - 0.5f);

        ClientLevel level = preview ? null : client.level;
        boolean hasCeiling = level != null && level.dimensionType().hasCeiling();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int ty = 0; ty < TEX_SIZE; ty++) {
            double sy = (ty + 0.5 - half);
            for (int tx = 0; tx < TEX_SIZE; tx++) {
                double sx = (tx + 0.5 - half);
                double distSq = sx * sx + sy * sy;
                if (circle && distSq > circleLimitSq) {
                    nativeImage.setPixel(tx, ty, 0x00000000);
                    continue;
                }

                double blockDx = (-sx * cosYaw + sy * sinYaw) * invZoom;
                double blockDz = (-sx * sinYaw - sy * cosYaw) * invZoom;
                int bx = Mth.floor(px + blockDx);
                int bz = Mth.floor(pz + blockDz);

                int argb = preview
                        ? samplePreviewColor(bx, bz)
                        : getOrSampleColumnColor(level, bx, bz, playerBlockY, hasCeiling, mutablePos);

                // Subtle grid / vignette polish near edge
                nativeImage.setPixel(tx, ty, argb);
            }
        }
        dynamicTexture.upload();
    }

    private static int getOrSampleColumnColor(ClientLevel level, int bx, int bz, int playerY,
                                              boolean hasCeiling, BlockPos.MutableBlockPos pos) {
        if (level == null || !level.hasChunk(bx >> 4, bz >> 4)) {
            return 0xFF0F172A;
        }
        long key = (((long) bx) << 32) | (bz & 0xFFFFFFFFL);
        int cached = columnColorCache.get(key);
        if (cached != 0) {
            return cached;
        }

        int color = sampleColumnColor(level, bx, bz, playerY, hasCeiling, pos);
        columnColorCache.put(key, color);
        return color;
    }

    private static int sampleColumnColor(ClientLevel level, int bx, int bz, int playerY,
                                         boolean hasCeiling, BlockPos.MutableBlockPos pos) {
        int minY = level.getMinY();
        int topY;
        if (hasCeiling) {
            topY = findCaveFloorOrSurface(level, bx, bz, playerY, minY, pos);
        } else {
            int surfY = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
            if (surfY < minY) {
                return 0xFF0F172A;
            }
            topY = surfY;
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
        if (mapColor == MapColor.NONE) {
            return 0xFF1E293B;
        }

        // Slope shading against block to the north (bz - 1)
        int northY = hasCeiling
                ? topY
                : (level.hasChunk(bx >> 4, (bz - 1) >> 4)
                ? level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz - 1) - 1
                : topY);

        MapColor.Brightness brightness = MapColor.Brightness.NORMAL;
        if (mapColor == MapColor.WATER) {
            // Water depth shading
            int depth = 0;
            int wy = topY;
            while (wy > minY && depth < 8 && !level.getFluidState(pos.set(bx, wy, bz)).isEmpty()) {
                wy--;
                depth++;
            }
            brightness = depth > 5 ? MapColor.Brightness.LOW : (depth > 2 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH);
        } else if (topY > northY) {
            brightness = MapColor.Brightness.HIGH;
        } else if (topY < northY) {
            brightness = MapColor.Brightness.LOW;
        }

        return mapColor.calculateARGBColor(brightness) | 0xFF000000;
    }

    private static int findCaveFloorOrSurface(ClientLevel level, int bx, int bz, int playerY,
                                              int minY, BlockPos.MutableBlockPos pos) {
        int maxY = Math.min(level.getMaxY() - 1, playerY + 12);
        int floorLimit = Math.max(minY, playerY - 32);
        boolean foundAir = false;
        for (int y = maxY; y >= floorLimit; y--) {
            pos.set(bx, y, bz);
            BlockState bs = level.getBlockState(pos);
            if (bs.isAir()) {
                foundAir = true;
            } else if (foundAir && bs.getMapColor(level, pos) != MapColor.NONE) {
                return y;
            }
        }
        return Math.clamp(playerY - 1, minY, level.getMaxY() - 1);
    }

    private static int samplePreviewColor(int bx, int bz) {
        double n = Math.sin(bx * 0.08) * Math.cos(bz * 0.08) + 0.5 * Math.sin((bx + bz) * 0.04);
        if (n < -0.35) return 0xFF2563EB; // River / lake
        if (n < -0.22) return 0xFFEAB308; // Sand beach
        if (n < 0.45) return ((bx + bz) & 1) == 0 ? 0xFF4D7C0F : 0xFF558B2F; // Plains / grass
        return 0xFF365314; // Forest hills
    }

    private static boolean isCircle(Module mod) {
        if (mod == null) return false;
        Setting<?> s = mod.getSetting("Shape");
        return s != null && "Circle".equalsIgnoreCase(String.valueOf(s.getValue()));
    }

    private static boolean getBool(Module mod, String name, boolean def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s != null && s.getValue() instanceof Boolean b ? b : def;
    }

    private static double getNum(Module mod) {
        if (mod != null && mod.getSetting("Zoom") instanceof NumberSetting ns) {
            return ns.getValue();
        }
        return 1.0;
    }
}
