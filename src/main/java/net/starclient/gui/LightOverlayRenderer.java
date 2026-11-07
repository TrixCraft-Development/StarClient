package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders 3D mob-spawn light overlay markers ("Light Overlay" WORLD module):
 * - Red: Hostile mobs can spawn anytime (Block Light <= threshold and Skylight == 0).
 * - Yellow: Hostile mobs can spawn at night/thunderstorms (Block Light <= threshold and Skylight > 0).
 */
public final class LightOverlayRenderer {

    private record SpawnSpot(int x, int y, int z, boolean alwaysSpawns) {
    }

    private static final List<SpawnSpot> CACHED_SPOTS = new ArrayList<>();
    private static BlockPos lastScanCenter = null;
    private static long lastScanTick = -100L;
    private static int lastRange = -1;
    private static int lastThreshold = -1;
    private static boolean lastShowNight = true;

    private LightOverlayRenderer() {
    }

    public static void clear() {
        CACHED_SPOTS.clear();
        lastScanCenter = null;
    }

    private static void refreshCacheIfNeeded(Minecraft client, Module mod) {
        Level level = client.level;
        if (level == null || client.player == null) {
            CACHED_SPOTS.clear();
            return;
        }

        int range = (int) Math.round(getNum(mod, "Range", 16.0));
        int threshold = (int) Math.round(getNum(mod, "Max Safe Block Light", 0.0));
        boolean showNight = getBool(mod);
        BlockPos playerPos = client.player.blockPosition();
        long gameTime = level.getGameTime();

        if (playerPos.equals(lastScanCenter)
                && Math.abs(gameTime - lastScanTick) < 6L
                && range == lastRange
                && threshold == lastThreshold
                && showNight == lastShowNight) {
            return;
        }

        lastScanCenter = playerPos.immutable();
        lastScanTick = gameTime;
        lastRange = range;
        lastThreshold = threshold;
        lastShowNight = showNight;
        CACHED_SPOTS.clear();

        int vRange = Math.clamp(range / 2, 6, 10);
        boolean hasSky = level.dimensionType().hasSkyLight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

        int px = playerPos.getX();
        int py = playerPos.getY();
        int pz = playerPos.getZ();
        int minY = Math.max(level.getMinY() + 1, py - vRange);
        int maxY = Math.min(level.getMaxY() - 1, py + vRange);
        int maxDistSq = range * range;

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (dx * dx + dz * dz > maxDistSq) continue;
                int wx = px + dx;
                int wz = pz + dz;
                for (int wy = minY; wy <= maxY; wy++) {
                    pos.set(wx, wy, wz);
                    BlockState space = level.getBlockState(pos);
                    if (!space.getFluidState().isEmpty()) continue;
                    if (!space.getCollisionShape(level, pos).isEmpty()) continue;
                    if (space.isSignalSource()) continue;

                    int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
                    if (blockLight > threshold) continue;

                    below.set(wx, wy - 1, wz);
                    BlockState ground = level.getBlockState(below);
                    if (ground.isAir() || ground.is(Blocks.BEDROCK) || ground.is(Blocks.BARRIER)) continue;
                    if (!ground.isFaceSturdy(level, below, Direction.UP)) continue;

                    int skyLight = hasSky ? level.getBrightness(LightLayer.SKY, pos) : 0;
                    boolean always = !hasSky || skyLight == 0;
                    if (!always && !showNight) continue;

                    CACHED_SPOTS.add(new SpawnSpot(wx, wy, wz, always));
                }
            }
        }
    }

    public static void render(net.minecraft.client.gui.GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (client == null || client.level == null || client.player == null || mod == null) return;

        refreshCacheIfNeeded(client, mod);
        if (CACHED_SPOTS.isEmpty()) return;

        GameRenderer gr = client.gameRenderer;
        int sw = extractor.guiWidth();
        int sh = extractor.guiHeight();
        String style = getMode(mod);
        float thickness = (float) getNum(mod, "Line Thickness", 1.5);

        int redColor = 0xE6EF4444;
        int yellowColor = 0xE6FACC15;

        for (SpawnSpot spot : CACHED_SPOTS) {
            int col = spot.alwaysSpawns() ? redColor : yellowColor;
            double y = spot.y() + 0.03;
            double minX = spot.x() + 0.22;
            double maxX = spot.x() + 0.78;
            double minZ = spot.z() + 0.22;
            double maxZ = spot.z() + 0.78;

            if ("Square".equals(style)) {
                Vec3 c1 = new Vec3(minX, y, minZ);
                Vec3 c2 = new Vec3(maxX, y, minZ);
                Vec3 c3 = new Vec3(maxX, y, maxZ);
                Vec3 c4 = new Vec3(minX, y, maxZ);
                EspRenderUtil.drawSegment3D(extractor, gr, c1, c2, sw, sh, thickness, col);
                EspRenderUtil.drawSegment3D(extractor, gr, c2, c3, sw, sh, thickness, col);
                EspRenderUtil.drawSegment3D(extractor, gr, c3, c4, sw, sh, thickness, col);
                EspRenderUtil.drawSegment3D(extractor, gr, c4, c1, sw, sh, thickness, col);
            } else if ("Diamond".equals(style)) {
                double cx = spot.x() + 0.5;
                double cz = spot.z() + 0.5;
                Vec3 n = new Vec3(cx, y, spot.z() + 0.18);
                Vec3 e = new Vec3(spot.x() + 0.82, y, cz);
                Vec3 s = new Vec3(cx, y, spot.z() + 0.82);
                Vec3 w = new Vec3(spot.x() + 0.18, y, cz);
                EspRenderUtil.drawSegment3D(extractor, gr, n, e, sw, sh, thickness, col);
                EspRenderUtil.drawSegment3D(extractor, gr, e, s, sw, sh, thickness, col);
                EspRenderUtil.drawSegment3D(extractor, gr, s, w, sw, sh, thickness, col);
                EspRenderUtil.drawSegment3D(extractor, gr, w, n, sw, sh, thickness, col);
            } else {
                // Default "Cross" (✕)
                Vec3 a1 = new Vec3(minX, y, minZ);
                Vec3 a2 = new Vec3(maxX, y, maxZ);
                Vec3 b1 = new Vec3(maxX, y, minZ);
                Vec3 b2 = new Vec3(minX, y, maxZ);
                EspRenderUtil.drawSegment3D(extractor, gr, a1, a2, sw, sh, thickness, col);
                EspRenderUtil.drawSegment3D(extractor, gr, b1, b2, sw, sh, thickness, col);
            }
        }
    }

    private static boolean getBool(Module mod) {
        Setting<?> s = mod.getSetting("Show Night Spawns");
        return !(s instanceof BooleanSetting bs) || bs.getValue();
    }

    private static double getNum(Module mod, String name, double def) {
        Setting<?> s = mod.getSetting(name);
        return s instanceof NumberSetting ns ? ns.getValue() : def;
    }

    private static String getMode(Module mod) {
        Setting<?> s = mod.getSetting("Style");
        return s instanceof ModeSetting ms ? ms.getValue() : "Cross";
    }
}
