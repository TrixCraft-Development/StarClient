package net.modernclient.gui;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

public class EntityEsp {

    public static List<Entity> getTargets(Minecraft client, boolean playersOnly, double maxRange) {
        List<Entity> targets = new ArrayList<>();
        if (client.level == null || client.player == null) return targets;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player || !(entity instanceof LivingEntity) || !entity.isAlive()) continue;
            if (playersOnly && !(entity instanceof Player)) continue;
            if (entity.distanceTo(client.player) > maxRange) continue;
            targets.add(entity);
        }
        return targets;
    }

    public static void applyGlow(Minecraft client, boolean playersOnly, double maxRange, boolean glowing) {
        if (client.level == null || client.player == null) return;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player || !(entity instanceof LivingEntity)) continue;
            boolean target = (!playersOnly || entity instanceof Player) && entity.distanceTo(client.player) <= maxRange;
            if (glowing && target && entity.isAlive()) {
                entity.setGlowingTag(true);
            } else {
                entity.setGlowingTag(false);
            }
        }
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, boolean playersOnly, double maxRange, String style) {
        if (client.level == null || client.player == null) return;

        GameRenderer gameRenderer = client.gameRenderer;
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();

        for (Entity entity : getTargets(client, playersOnly, maxRange)) {
            Vec3 head = projectToScreen(gameRenderer, new Vec3(entity.getX(), entity.getY() + entity.getBbHeight(), entity.getZ()), screenWidth, screenHeight);
            if (head == null) continue;

            boolean isPlayer = entity instanceof Player;
            int color = isPlayer ? 0xFF38BDF8 : 0xFF22C55E;
            int dist = (int) client.player.distanceTo(entity);

            Vec3 playerHead = projectToScreen(gameRenderer, new Vec3(client.player.getX(), client.player.getY() + client.player.getBbHeight(), client.player.getZ()), screenWidth, screenHeight);
            double originX = playerHead == null ? screenWidth / 2.0 : playerHead.x;
            double originY = playerHead == null ? screenHeight / 2.0 : playerHead.y;

            drawLine(extractor, originX, originY, head.x, head.y, 0xAA38BDF8);

            if ("Box 2D".equals(style) || "Chams".equals(style)) {
                double[] box = projectCorners(gameRenderer, entity, screenWidth, screenHeight);
                if (box == null) continue;

                if ("Chams".equals(style)) {
                    extractor.fill((int) box[0], (int) box[1], (int) box[2], (int) box[3], 0x5538BDF8);
                }
                drawRect(extractor, box[0], box[1], box[2], box[3], color);
            }

            String label = entity.getName().getString() + " " + dist + "m";
            extractor.text(client.font, label, (int) head.x - client.font.width(label) / 2, (int) head.y - 12, 0xFFFFFFFF);
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

    private static double[] projectCorners(GameRenderer gameRenderer, Entity entity, int screenWidth, int screenHeight) {
        float halfWidth = entity.getBbWidth() / 2.0f;
        float height = entity.getBbHeight();
        double cx = entity.getX();
        double cy = entity.getY();
        double cz = entity.getZ();

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (float dx : new float[]{-halfWidth, halfWidth}) {
            for (float dy : new float[]{0.0f, height}) {
                for (float dz : new float[]{-halfWidth, halfWidth}) {
                    Vec3 p = projectToScreen(gameRenderer, new Vec3(cx + dx, cy + dy, cz + dz), screenWidth, screenHeight);
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
