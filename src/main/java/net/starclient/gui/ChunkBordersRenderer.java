package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.OptionalLong;

/**
 * Renders clean customizable 3D 16x16 chunk borders, sub-chunk section rings,
 * neighbor chunk corner pillars, and Overworld Slime Chunk floor highlights ("Chunk Borders" WORLD module).
 */
public final class ChunkBordersRenderer {

    private ChunkBordersRenderer() {
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (client == null || client.level == null || client.player == null || mod == null) return;

        GameRenderer gr = client.gameRenderer;
        int sw = extractor.guiWidth();
        int sh = extractor.guiHeight();

        int chunkX = client.player.blockPosition().getX() >> 4;
        int chunkZ = client.player.blockPosition().getZ() >> 4;
        double minX = chunkX << 4;
        double maxX = minX + 16.0;
        double minZ = chunkZ << 4;
        double maxZ = minZ + 16.0;

        double heightRange = getNum(mod, "Height Range", 32.0);
        double playerY = client.player.getY();
        double minY = Math.max(client.level.getMinY(), Math.floor((playerY - heightRange) / 16.0) * 16.0);
        double maxY = Math.min(client.level.getMaxY(), Math.ceil((playerY + heightRange) / 16.0) * 16.0);

        float thickness = (float) getNum(mod, "Line Thickness", 1.5);
        boolean useClientCol = getBool(mod, "Use Client Color", false);
        boolean showSubChunks = getBool(mod, "Show Sub-Chunks", true);
        boolean showWallGrid = getBool(mod, "Show Wall Grid", true);
        boolean showNeighbors = getBool(mod, "Show Neighbor Corners", true);
        boolean slimeCheck = getBool(mod, "Highlight Slime Chunks", true);

        int mainRgb = 0xFFFACC15;
        if (useClientCol && StarClient.getInstance() != null) {
            mainRgb = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        } else if (mod.getSetting("Border Color") instanceof ColorModeSetting cms) {
            mainRgb = cms.getColor();
        }

        boolean isSlimeChunk = false;
        if (slimeCheck && client.level.dimension() == Level.OVERWORLD) {
            OptionalLong knownSeed = WorldInfoRenderer.getKnownSeed(client);
            if (knownSeed.isPresent()) {
                isSlimeChunk = WorldgenRandom.seedSlimeChunk(chunkX, chunkZ, knownSeed.getAsLong(), 987234911L)
                        .nextInt(10) == 0;
            }
        }

        int cornerColor = (mainRgb & 0x00FFFFFF) | 0xEE000000;
        int gridColor = (mainRgb & 0x00FFFFFF) | 0x77000000;
        int subChunkColor = isSlimeChunk ? 0xCC4ADE80 : 0xAA38BDF8;
        int neighborColor = 0x88EF4444;

        // 1. Neighbor chunk outer corner pillars (F3+G style surrounding chunk markers)
        if (showNeighbors) {
            double[] neighborXs = {minX - 16.0, minX, maxX, maxX + 16.0};
            double[] neighborZs = {minZ - 16.0, minZ, maxZ, maxZ + 16.0};
            for (double nx : neighborXs) {
                for (double nz : neighborZs) {
                    boolean ownCorner = (nx == minX || nx == maxX) && (nz == minZ || nz == maxZ);
                    if (ownCorner) continue;
                    drawVerticalLine(extractor, gr, nx, nz, minY, maxY, sw, sh, Math.max(1.0f, thickness - 0.5f), neighborColor);
                }
            }
        }

        // 2. Current chunk 4 primary corner pillars
        int primaryCornerCol = isSlimeChunk ? 0xFF4ADE80 : cornerColor;
        drawVerticalLine(extractor, gr, minX, minZ, minY, maxY, sw, sh, thickness + 0.5f, primaryCornerCol);
        drawVerticalLine(extractor, gr, maxX, minZ, minY, maxY, sw, sh, thickness + 0.5f, primaryCornerCol);
        drawVerticalLine(extractor, gr, maxX, maxZ, minY, maxY, sw, sh, thickness + 0.5f, primaryCornerCol);
        drawVerticalLine(extractor, gr, minX, maxZ, minY, maxY, sw, sh, thickness + 0.5f, primaryCornerCol);

        // 3. Vertical 4-block wall subdivisions along the 4 faces
        if (showWallGrid) {
            for (int step = 4; step < 16; step += 4) {
                drawVerticalLine(extractor, gr, minX + step, minZ, minY, maxY, sw, sh, 1.0f, gridColor);
                drawVerticalLine(extractor, gr, minX + step, maxZ, minY, maxY, sw, sh, 1.0f, gridColor);
                drawVerticalLine(extractor, gr, minX, minZ + step, minY, maxY, sw, sh, 1.0f, gridColor);
                drawVerticalLine(extractor, gr, maxX, minZ + step, minY, maxY, sw, sh, 1.0f, gridColor);
            }
        }

        // 4. Horizontal 16-block Sub-Chunk section rings
        if (showSubChunks) {
            for (double sy = minY; sy <= maxY; sy += 16.0) {
                Vec3 c1 = new Vec3(minX, sy, minZ);
                Vec3 c2 = new Vec3(maxX, sy, minZ);
                Vec3 c3 = new Vec3(maxX, sy, maxZ);
                Vec3 c4 = new Vec3(minX, sy, maxZ);
                EspRenderUtil.drawSegment3D(extractor, gr, c1, c2, sw, sh, thickness, subChunkColor);
                EspRenderUtil.drawSegment3D(extractor, gr, c2, c3, sw, sh, thickness, subChunkColor);
                EspRenderUtil.drawSegment3D(extractor, gr, c3, c4, sw, sh, thickness, subChunkColor);
                EspRenderUtil.drawSegment3D(extractor, gr, c4, c1, sw, sh, thickness, subChunkColor);
            }
        }

        // 5. Slime Chunk ground highlight grid + subtle top-center/corner badge when standing inside a Slime Chunk
        if (isSlimeChunk) {
            double floorY = Math.floor(client.player.getY()) + 0.04;
            int slimeCol = 0xAA4ADE80;
            for (int step = 0; step <= 16; step += 4) {
                EspRenderUtil.drawSegment3D(extractor, gr,
                        new Vec3(minX + step, floorY, minZ),
                        new Vec3(minX + step, floorY, maxZ),
                        sw, sh, thickness, slimeCol);
                EspRenderUtil.drawSegment3D(extractor, gr,
                        new Vec3(minX, floorY, minZ + step),
                        new Vec3(maxX, floorY, minZ + step),
                        sw, sh, thickness, slimeCol);
            }

            if (!(client.gui.screen() instanceof ClickGuiScreen) && !(client.gui.screen() instanceof HudEditorScreen)) {
                String badge = "🟢 Slime Chunk [" + chunkX + ", " + chunkZ + "]";
                int bw = client.font.width(badge) + 12;
                int bx = (sw - bw) / 2;
                int by = sh - 58;
                GuiRenderUtils.fillRoundedRect(extractor, bx, by, bw, 15, 4, 0xCC064E3B);
                GuiRenderUtils.drawRoundedOutline(extractor, bx, by, bw, 15, 4, 0xFF4ADE80);
                extractor.text(client.font, badge, bx + 6, by + 4, 0xFFECFDF5, true);
            }
        }
    }

    private static void drawVerticalLine(GuiGraphicsExtractor extractor, GameRenderer gr,
                                         double x, double z, double minY, double maxY,
                                         int sw, int sh, float thickness, int color) {
        // Subdivide tall vertical segments into 16-block steps so perspective projection & near-clip stay crisp
        for (double y = minY; y < maxY; y += 16.0) {
            double nextY = Math.min(maxY, y + 16.0);
            EspRenderUtil.drawSegment3D(extractor, gr, new Vec3(x, y, z), new Vec3(x, nextY, z), sw, sh, thickness, color);
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
