package net.starclient.gui;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3fc;

public final class EspRenderUtil {

    private static final int LINE_THICKNESS = 2;

    private static final int[][] BOX_EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private EspRenderUtil() {
    }

    public static Vec3 projectToScreen(GameRenderer gameRenderer, Vec3 worldPos, int screenWidth, int screenHeight) {
        Camera camera = gameRenderer.mainCamera();
        Vec3 cam = camera.position();
        double dx = worldPos.x - cam.x;
        double dy = worldPos.y - cam.y;
        double dz = worldPos.z - cam.z;
        Vector3fc forward = camera.forwardVector();
        if (dx * forward.x() + dy * forward.y() + dz * forward.z() <= 0.0) return null;

        Vec3 ndc = gameRenderer.projectPointToScreen(worldPos);
        if (ndc.z > 1.0) return null;

        return new Vec3((ndc.x + 1.0) * 0.5 * screenWidth, (1.0 - ndc.y) * 0.5 * screenHeight, ndc.z);
    }

    public static void drawLine(GuiGraphicsExtractor extractor, double x1, double y1, double x2, double y2, int color) {
        drawLine(extractor, x1, y1, x2, y2, LINE_THICKNESS, color);
    }

    public static void drawLine(GuiGraphicsExtractor extractor, double x1, double y1, double x2, double y2, float thickness, int color) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (Math.abs(dx) < 0.01 && Math.abs(dy) < 0.01) return;

        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float angle = (float) Math.atan2(dy, dx);
        int thickPx = Math.max(1, Math.round(thickness));

        Matrix3x2fStack pose = extractor.pose();
        pose.pushMatrix();
        pose.translate((float) x1, (float) y1);
        pose.rotate(angle);
        extractor.fill(0, 0, (int) Math.ceil(length), thickPx, color);
        pose.popMatrix();
    }

    public static void drawSegment3D(GuiGraphicsExtractor extractor, GameRenderer gameRenderer,
                                     Vec3 p1, Vec3 p2, int screenWidth, int screenHeight, float thickness, int color) {
        Camera camera = gameRenderer.mainCamera();
        Vec3 cam = camera.position();
        Vector3fc fwd = camera.forwardVector();

        double d1 = (p1.x - cam.x) * fwd.x() + (p1.y - cam.y) * fwd.y() + (p1.z - cam.z) * fwd.z();
        double d2 = (p2.x - cam.x) * fwd.x() + (p2.y - cam.y) * fwd.y() + (p2.z - cam.z) * fwd.z();
        final double nearClip = 0.08;

        if (d1 <= nearClip && d2 <= nearClip) {
            return;
        }
        if (d1 <= nearClip) {
            double t = (nearClip - d1) / (d2 - d1);
            p1 = p1.lerp(p2, Math.clamp(t, 0.0, 1.0));
        } else if (d2 <= nearClip) {
            double t = (nearClip - d2) / (d1 - d2);
            p2 = p2.lerp(p1, Math.clamp(t, 0.0, 1.0));
        }

        Vec3 s1 = projectToScreen(gameRenderer, p1, screenWidth, screenHeight);
        Vec3 s2 = projectToScreen(gameRenderer, p2, screenWidth, screenHeight);
        if (s1 != null && s2 != null) {
            drawLine(extractor, s1.x, s1.y, s2.x, s2.y, thickness, color);
        }
    }

    public static void drawBox3D(GuiGraphicsExtractor extractor, GameRenderer gameRenderer,
                                 AABB box, int screenWidth, int screenHeight, float thickness, int color) {
        Vec3[] corners = new Vec3[]{
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ)
        };
        for (int[] edge : BOX_EDGES) {
            drawSegment3D(extractor, gameRenderer, corners[edge[0]], corners[edge[1]], screenWidth, screenHeight, thickness, color);
        }
    }

    public static void drawRect(GuiGraphicsExtractor extractor, double x1, double y1, double x2, double y2, int color) {
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
