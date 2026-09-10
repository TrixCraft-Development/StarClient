package net.modernclient.gui;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.modernclient.module.ChestEspState;
import org.joml.Vector3fc;

import java.util.List;

public class ChestEsp {

    public static void render(GuiGraphicsExtractor extractor, Minecraft client,
                              List<ChestEspState.Entry> entries, boolean boxes, boolean tracers, boolean labels) {
        if (client.level == null || client.player == null) return;
        if (entries.isEmpty()) return;

        GameRenderer gameRenderer = client.gameRenderer;
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();

        Vec3 playerHead = projectToScreen(gameRenderer,
                new Vec3(client.player.getX(), client.player.getY() + client.player.getBbHeight(), client.player.getZ()),
                screenWidth, screenHeight);
        double originX = playerHead == null ? screenWidth / 2.0 : playerHead.x;
        double originY = playerHead == null ? screenHeight / 2.0 : playerHead.y;

        for (ChestEspState.Entry entry : entries) {
            BlockPos pos = entry.pos();
            Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            Vec3 anchor = projectToScreen(gameRenderer,
                    new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5), screenWidth, screenHeight);
            if (anchor == null) continue;

            if (tracers) {
                drawLine(extractor, originX, originY, anchor.x, anchor.y, entry.color());
            }
            if (boxes) {
                double[] box = projectBlockCorners(gameRenderer, pos, screenWidth, screenHeight);
                if (box != null) {
                    int fillColor = (entry.color() & 0x00FFFFFF) | 0x22000000;
                    extractor.fill((int) box[0], (int) box[1], (int) box[2], (int) box[3], fillColor);
                    drawRect(extractor, box[0], box[1], box[2], box[3], entry.color());
                }
            }
            if (labels) {
                int dist = (int) Math.sqrt(client.player.distanceToSqr(center));
                String text = entry.label() + " " + dist + "m";
                extractor.text(client.font, text, (int) anchor.x - client.font.width(text) / 2, (int) anchor.y - 12, entry.color());
            }
        }
    }

    private static Vec3 projectToScreen(GameRenderer gameRenderer, Vec3 worldPos, int screenWidth, int screenHeight) {
        Camera camera = gameRenderer.mainCamera();
        Vec3 diff = worldPos.subtract(camera.position());
        Vector3fc forward = camera.forwardVector();
        if (diff.x * forward.x() + diff.y * forward.y() + diff.z * forward.z() <= 0.0) return null;

        Vec3 ndc = gameRenderer.projectPointToScreen(worldPos);
        if (ndc.z > 1.0) return null;

        return new Vec3((ndc.x + 1.0) * 0.5 * screenWidth, (1.0 - ndc.y) * 0.5 * screenHeight, ndc.z);
    }

    private static double[] projectBlockCorners(GameRenderer gameRenderer, BlockPos pos, int screenWidth, int screenHeight) {
        double x0 = pos.getX();
        double y0 = pos.getY();
        double z0 = pos.getZ();

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (double dx : new double[]{x0, x0 + 1.0}) {
            for (double dy : new double[]{y0, y0 + 1.0}) {
                for (double dz : new double[]{z0, z0 + 1.0}) {
                    Vec3 p = projectToScreen(gameRenderer, new Vec3(dx, dy, dz), screenWidth, screenHeight);
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

    private static void drawLine(GuiGraphicsExtractor extractor, double x1, double y1, double x2, double y2, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.max(Math.abs(dx), Math.abs(dy));
        if (length == 0) return;

        int step = 4;
        int iterations = (int) Math.ceil(length / step);
        for (int i = 0; i <= iterations; i++) {
            double t = (i * step) / length;
            int px = (int) Math.round(x1 + dx * t);
            int py = (int) Math.round(y1 + dy * t);
            extractor.fill(px, py, px + step, py + step, color);
        }
    }

    private static void drawRect(GuiGraphicsExtractor extractor, double x1, double y1, double x2, double y2, int color) {
        int xi1 = (int) x1;
        int yi1 = (int) y1;
        int xi2 = (int) x2;
        int yi2 = (int) y2;
        extractor.fill(xi1, yi1, xi2, yi1 + 1, color);
        extractor.fill(xi1, yi2 - 1, xi2, yi2, color);
        extractor.fill(xi1, yi1, xi1 + 1, yi2, color);
        extractor.fill(xi2 - 1, yi1, xi2, yi2, color);
    }
}