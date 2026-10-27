package net.modernclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class EntityEsp {

    public static List<Entity> getTargets(Minecraft client, boolean playersOnly, double maxRange) {
        List<Entity> targets = new ArrayList<>();
        if (client.level == null || client.player == null) return targets;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!isTarget(client, entity, playersOnly, maxRange)) continue;
            targets.add(entity);
        }
        return targets;
    }

    private static boolean isTarget(Minecraft client, Entity entity, boolean playersOnly, double maxRange) {
        if (entity == client.player || !(entity instanceof LivingEntity) || !entity.isAlive()) return false;
        if (playersOnly && !(entity instanceof Player)) return false;
        assert client.player != null;
        return entity.distanceTo(client.player) <= maxRange;
    }

    public static void applyGlow(Minecraft client, boolean playersOnly, double maxRange, boolean glowing) {
        if (client.level == null || client.player == null) return;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player || !(entity instanceof LivingEntity)) continue;
            boolean target = (!playersOnly || entity instanceof Player) && entity.distanceTo(client.player) <= maxRange;
            entity.setGlowingTag(glowing && target && entity.isAlive());
        }
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, boolean playersOnly, double maxRange, String style) {
        if (client.level == null || client.player == null) return;

        GameRenderer gameRenderer = client.gameRenderer;
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();
        double originX = screenWidth / 2.0;
        double originY = screenHeight / 2.0;
        boolean drawBox = "Box 2D".equals(style) || "Chams".equals(style);
        boolean chams = "Chams".equals(style);

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!isTarget(client, entity, playersOnly, maxRange)) continue;

            Vec3 head = EspRenderUtil.projectToScreen(gameRenderer,
                    new Vec3(entity.getX(), entity.getY() + entity.getBbHeight(), entity.getZ()), screenWidth, screenHeight);
            if (head == null) continue;

            boolean isPlayer = entity instanceof Player;
            int color = isPlayer ? 0xFF38BDF8 : 0xFF22C55E;
            int dist = (int) client.player.distanceTo(entity);

            EspRenderUtil.drawLine(extractor, originX, originY, head.x, head.y, 0xAA38BDF8);

            if (drawBox) {
                double[] box = projectCorners(gameRenderer, entity, screenWidth, screenHeight);
                if (box == null) continue;

                if (chams) {
                    extractor.fill((int) box[0], (int) box[1], (int) box[2], (int) box[3], 0x5538BDF8);
                }
                EspRenderUtil.drawRect(extractor, box[0], box[1], box[2], box[3], color);
            }

            String label = entity.getName().getString() + " " + dist + "m";
            extractor.text(client.font, label, (int) head.x - client.font.width(label) / 2, (int) head.y - 12, 0xFFFFFFFF);
        }
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

        for (int ix = 0; ix < 2; ix++) {
            double dx = ix == 0 ? -halfWidth : halfWidth;
            for (int iy = 0; iy < 2; iy++) {
                double dy = iy == 0 ? 0.0 : height;
                for (int iz = 0; iz < 2; iz++) {
                    double dz = iz == 0 ? -halfWidth : halfWidth;
                    Vec3 p = EspRenderUtil.projectToScreen(gameRenderer,
                            new Vec3(cx + dx, cy + dy, cz + dz), screenWidth, screenHeight);
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
