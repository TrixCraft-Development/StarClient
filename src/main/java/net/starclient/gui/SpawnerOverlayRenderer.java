package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders 3D bounding boxes, 16-block activation radius rings, and floating mob/status
 * badges above Mob Spawners, Trial Spawners, and Vaults ("Spawner Overlay" WORLD module).
 */
public final class SpawnerOverlayRenderer {

    private static final double ACTIVATION_RADIUS = 16.0;
    private static final int RING_SEGMENTS = 36;

    private record SpawnerEntry(BlockPos pos, String title, boolean isTrialOrVault, int accentColor) {
    }

    private static final List<SpawnerEntry> CACHED_SPAWNERS = new ArrayList<>();
    private static long lastScanTick = -100L;
    private static BlockPos lastPlayerPos = null;
    private static int lastRange = -1;

    private SpawnerOverlayRenderer() {
    }

    public static void clear() {
        CACHED_SPAWNERS.clear();
        lastPlayerPos = null;
    }

    private static void refreshIfNeeded(Minecraft client, Module mod) {
        Level level = client.level;
        if (level == null || client.player == null) {
            CACHED_SPAWNERS.clear();
            return;
        }

        int range = (int) Math.round(getNum(mod, "Range", 48.0));
        BlockPos playerPos = client.player.blockPosition();
        long gameTime = level.getGameTime();

        if (playerPos.equals(lastPlayerPos)
                && Math.abs(gameTime - lastScanTick) < 10L
                && range == lastRange) {
            return;
        }

        lastPlayerPos = playerPos.immutable();
        lastScanTick = gameTime;
        lastRange = range;
        CACHED_SPAWNERS.clear();

        boolean includeTrial = getBool(mod, "Include Trial & Vaults", true);
        int chunkRadius = (range >> 4) + 1;
        int pcx = client.player.chunkPosition().x();
        int pcz = client.player.chunkPosition().z();
        double rangeSqr = (double) range * range;

        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                LevelChunk chunk = level.getChunk(pcx + cx, pcz + cz);
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    double distSqr = client.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distSqr > rangeSqr) continue;

                    BlockEntity be = entry.getValue();
                    if (be instanceof SpawnerBlockEntity spawnerBe) {
                        String mobName = "Mob Spawner";
                        try {
                            Entity displayEntity = spawnerBe.getSpawner().getOrCreateDisplayEntity(level, pos);
                            if (displayEntity != null) {
                                mobName = displayEntity.getName().getString() + " Spawner";
                            }
                        } catch (Throwable ignored) {
                        }
                        CACHED_SPAWNERS.add(new SpawnerEntry(pos.immutable(), mobName, false, 0xFFFB923C));
                    } else if (includeTrial && be instanceof TrialSpawnerBlockEntity trialBe) {
                        String mobName = "Trial Spawner";
                        try {
                            var ts = trialBe.getTrialSpawner();
                            Entity displayEntity = ts.getStateData().getOrCreateDisplayEntity(ts, level, trialBe.getState());
                            if (displayEntity != null) {
                                mobName = "Trial: " + displayEntity.getName().getString();
                            }
                        } catch (Throwable ignored) {
                        }
                        CACHED_SPAWNERS.add(new SpawnerEntry(pos.immutable(), mobName, true, 0xFF38BDF8));
                    } else if (includeTrial && be instanceof VaultBlockEntity) {
                        BlockState st = level.getBlockState(pos);
                        boolean ominous = st.is(Blocks.VAULT) && st.toString().contains("ominous=true");
                        String title = ominous ? "Ominous Vault" : "Trial Vault";
                        int col = ominous ? 0xFFA78BFA : 0xFF22D3EE;
                        CACHED_SPAWNERS.add(new SpawnerEntry(pos.immutable(), title, true, col));
                    }
                }
            }
        }
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (client == null || client.level == null || client.player == null || mod == null) return;

        refreshIfNeeded(client, mod);
        if (CACHED_SPAWNERS.isEmpty()) return;

        GameRenderer gr = client.gameRenderer;
        int sw = extractor.guiWidth();
        int sh = extractor.guiHeight();
        double originX = sw / 2.0;
        double originY = sh / 2.0;

        boolean showBox = getBool(mod, "Highlight Box", true);
        boolean showSphere = getBool(mod, "Activation Radius (16m)", true);
        boolean showLabels = getBool(mod, "Show Info Label", true);
        boolean showTracers = getBool(mod, "Tracers", false);
        boolean useClientCol = getBool(mod, "Use Client Color", false);
        float thickness = (float) getNum(mod, "Line Thickness", 1.5);

        int customRgb = 0xFFFB923C;
        if (useClientCol && StarClient.getInstance() != null && StarClient.getInstance().getClientSettings() != null) {
            customRgb = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        } else if (mod.getSetting("Color") instanceof ColorModeSetting cms) {
            customRgb = cms.getColor();
        }
        int bgColor = StarClient.getInstance() != null && StarClient.getInstance().getClientSettings() != null
                ? StarClient.getInstance().getClientSettings().getMenuBackgroundColor()
                : 0xFF0F172A;

        Vec3 eyePos = client.player.getEyePosition();

        for (SpawnerEntry spawner : CACHED_SPAWNERS) {
            BlockPos pos = spawner.pos();
            Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            double dist = eyePos.distanceTo(center);
            boolean active = dist <= ACTIVATION_RADIUS;

            int baseCol = useClientCol ? customRgb : spawner.accentColor();
            int stateCol = active ? 0xFF34D399 : baseCol;
            int boxCol = (baseCol & 0x00FFFFFF) | 0xEE000000;

            if (showBox) {
                AABB blockBox = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0).inflate(0.005);
                EspRenderUtil.drawBox3D(extractor, gr, blockBox, sw, sh, thickness, boxCol);
            }

            if (showSphere && !spawner.isTrialOrVault()) {
                int ringCol = active ? 0xAA34D399 : 0x88F87171;
                drawHorizontalCircle(extractor, gr, center.x, center.y, center.z, ACTIVATION_RADIUS, sw, sh, thickness, ringCol);
            }

            Vec3 labelPos = EspRenderUtil.projectToScreen(gr, new Vec3(center.x, pos.getY() + 1.35, center.z), sw, sh);
            if (labelPos == null) continue;

            if (showTracers) {
                EspRenderUtil.drawLine(extractor, originX, originY, labelPos.x, labelPos.y, 1.5f, (baseCol & 0x00FFFFFF) | 0xAA000000);
            }

            if (showLabels) {
                String statusStr = active ? "● Active" : "○ Standby";
                String text = spawner.title() + " • " + statusStr + String.format(Locale.US, " (%.1fm)", dist);
                int tw = client.font.width(text);
                int pillW = tw + 10;
                int pillH = 13;
                int px = (int) Math.round(labelPos.x) - pillW / 2;
                int py = (int) Math.round(labelPos.y) - pillH / 2;

                GuiRenderUtils.fillRoundedRect(extractor, px, py, pillW, pillH, 3, (bgColor & 0x00FFFFFF) | 0xDC000000);
                GuiRenderUtils.drawRoundedOutline(extractor, px, py, pillW, pillH, 3, (stateCol & 0x00FFFFFF) | 0xAA000000);
                extractor.text(client.font, text, px + 5, py + 2, active ? 0xFFECFDF5 : 0xFFE2E8F0, true);
            }
        }
    }

    private static void drawHorizontalCircle(GuiGraphicsExtractor extractor, GameRenderer gr,
                                             double cx, double cy, double cz, double radius,
                                             int sw, int sh, float thickness, int color) {
        double prevX = cx + radius;
        double prevZ = cz;
        for (int i = 1; i <= RING_SEGMENTS; i++) {
            double angle = (i * 2.0 * Math.PI) / RING_SEGMENTS;
            double nextX = cx + Math.cos(angle) * radius;
            double nextZ = cz + Math.sin(angle) * radius;
            EspRenderUtil.drawSegment3D(extractor, gr,
                    new Vec3(prevX, cy, prevZ),
                    new Vec3(nextX, cy, nextZ),
                    sw, sh, thickness, color);
            prevX = nextX;
            prevZ = nextZ;
        }
    }

    private static boolean getBool(Module mod, String name, boolean def) {
        Setting<?> s = mod.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }

    private static double getNum(Module mod, String name, double def) {
        Setting<?> s = mod.getSetting(name);
        return s instanceof NumberSetting ns ? ns.getValue() : def;
    }
}
