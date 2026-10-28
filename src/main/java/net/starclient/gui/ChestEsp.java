package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.starclient.module.ChestEspState;

import java.util.List;

public class ChestEsp {

    public static void render(GuiGraphicsExtractor extractor, Minecraft client,
                              List<ChestEspState.Entry> entries, boolean boxes, boolean tracers, boolean labels) {
        if (client.level == null || client.player == null) return;
        if (entries.isEmpty()) return;

        GameRenderer gameRenderer = client.gameRenderer;
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();
        double originX = screenWidth / 2.0;
        double originY = screenHeight / 2.0;

        for (ChestEspState.Entry entry : entries) {
            BlockPos pos = entry.pos();
            Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Vec3 anchor = EspRenderUtil.projectToScreen(gameRenderer,
                    new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), screenWidth, screenHeight);
            if (anchor == null) continue;

            if (tracers) {
                EspRenderUtil.drawLine(extractor, originX, originY, anchor.x, anchor.y, entry.color());
            }
            if (boxes) {
                double[] box = projectBlockCorners(gameRenderer, pos, screenWidth, screenHeight);
                if (box != null) {
                    int fillColor = (entry.color() & 0x00FFFFFF) | 0x22000000;
                    extractor.fill((int) box[0], (int) box[1], (int) box[2], (int) box[3], fillColor);
                    EspRenderUtil.drawRect(extractor, box[0], box[1], box[2], box[3], entry.color());
                }
            }
            if (labels) {
                int dist = (int) Math.sqrt(client.player.distanceToSqr(center));
                String text = entry.label() + " " + dist + "m";
                extractor.text(client.font, text, (int) anchor.x - client.font.width(text) / 2, (int) anchor.y - 12, entry.color());
            }
        }
    }

    private static double[] projectBlockCorners(GameRenderer gameRenderer, BlockPos pos, int screenWidth, int screenHeight) {
        double x0 = pos.getX();
        double y0 = pos.getY();
        double z0 = pos.getZ();

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (int ix = 0; ix < 2; ix++) {
            double dx = ix == 0 ? x0 : x0 + 1.0;
            for (int iy = 0; iy < 2; iy++) {
                double dy = iy == 0 ? y0 : y0 + 1.0;
                for (int iz = 0; iz < 2; iz++) {
                    double dz = iz == 0 ? z0 : z0 + 1.0;
                    Vec3 p = EspRenderUtil.projectToScreen(gameRenderer, new Vec3(dx, dy, dz), screenWidth, screenHeight);
                    if (p == null) return null;
                    minX = Math.min(minX, p.x);
                    minY = Math.min(minY, p.y);
                    maxX = Math.max(maxX, p.x);
                    maxY = Math.max(maxY, p.y);
                }
            }
        }
        return new double[]{minX, minY, maxX, maxY};
    }
}
