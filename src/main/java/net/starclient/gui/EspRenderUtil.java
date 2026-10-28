package net.starclient.gui;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3fc;

public final class EspRenderUtil {

    private static final int LINE_THICKNESS = 2;

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
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (Math.abs(dx) < 0.01 && Math.abs(dy) < 0.01) return;

        float length = (float) Math.sqrt(dx * dx + dy * dy);
        float angle = (float) Math.atan2(dy, dx);

        Matrix3x2fStack pose = extractor.pose();
        pose.pushMatrix();
        pose.translate((float) x1, (float) y1);
        pose.rotate(angle);
        extractor.fill(0, 0, (int) Math.ceil(length), LINE_THICKNESS, color);
        pose.popMatrix();
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
