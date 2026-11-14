package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;

import java.util.Locale;

/**
 * TNT Blast Radius Wireframe module (SERVER / RENDER):
 * Renders glowing 3D blast radius spheres, ground danger rings, and floating fuse
 * countdown timers around primed TNT entities.
 */
public final class TntBlastRadiusRenderer {

    private TntBlastRadiusRenderer() {
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (client == null || client.level == null || client.player == null || mod == null) return;

        double blastRadius = mod.getSetting("Blast Radius") instanceof NumberSetting ns ? ns.getValue() : 4.0;
        String style = mod.getSetting("Style") instanceof ModeSetting ms ? ms.getValue() : "Both";
        boolean showFuse = !(mod.getSetting("Show Fuse Timer") instanceof BooleanSetting bs) || bs.getValue();
        boolean dangerAlert = !(mod.getSetting("Danger Alert") instanceof BooleanSetting bs) || bs.getValue();
        float thickness = (float) (mod.getSetting("Line Thickness") instanceof NumberSetting ns ? ns.getValue() : 1.8);
        double maxRange = mod.getSetting("Range") instanceof NumberSetting ns ? ns.getValue() : 36.0;
        boolean useClientColor = mod.getSetting("Use Client Color") instanceof BooleanSetting bs && bs.getValue();

        int clientAccent = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        GameRenderer gr = client.gameRenderer;
        int sw = extractor.guiWidth();
        int sh = extractor.guiHeight();
        Font font = client.font;
        long now = System.currentTimeMillis();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof PrimedTnt tnt) || !tnt.isAlive()) continue;

            double distToPlayer = client.player.distanceTo(tnt);
            if (distToPlayer > maxRange) continue;

            int fuse = tnt.getFuse();
            float fuseSec = Math.max(0.0F, fuse / 20.0F);
            boolean isDanger = distToPlayer <= blastRadius;

            // Wireframe & Tag Color
            int color;
            if (useClientColor) {
                color = (clientAccent & 0x00FFFFFF) | 0xCC000000;
            } else if (dangerAlert && isDanger) {
                // Pulse fast red when standing inside blast radius
                float pulse = 0.6F + 0.4F * Mth.sin((now % 300L) / 300.0F * (float) Math.PI * 2.0F);
                int alpha = (int) (pulse * 255);
                color = (alpha << 24) | 0xEF4444;
            } else if (fuseSec > 2.0F) {
                color = 0xCC10B981; // Green
            } else if (fuseSec > 1.0F) {
                color = 0xCCFBBF24; // Amber
            } else {
                color = 0xCCEF4444; // Red
            }

            double cx = tnt.getX();
            double cy = tnt.getY() + 0.49;
            double cz = tnt.getZ();

            // 1. Ground Ring
            if ("Ground Ring".equalsIgnoreCase(style) || "Both".equalsIgnoreCase(style)) {
                renderCircle(extractor, gr, cx, tnt.getY() + 0.04, cz, blastRadius, 0, sw, sh, thickness, color);
            }

            // 2. 3D Wireframe Sphere (Equator + 2 Vertical Meridians)
            if ("3D Sphere".equalsIgnoreCase(style) || "Both".equalsIgnoreCase(style)) {
                // Equator horizontal circle
                renderCircle(extractor, gr, cx, cy, cz, blastRadius, 0, sw, sh, thickness, color);

                // Meridian 1 (X-Y plane)
                renderCircle(extractor, gr, cx, cy, cz, blastRadius, 1, sw, sh, thickness * 0.9F, (color & 0x00FFFFFF) | 0x77000000);

                // Meridian 2 (Z-Y plane)
                renderCircle(extractor, gr, cx, cy, cz, blastRadius, 2, sw, sh, thickness * 0.9F, (color & 0x00FFFFFF) | 0x77000000);
            }

            // 3. Floating 3D Fuse Timer Tag
            if (showFuse) {
                Vec3 tagWorldPos = new Vec3(cx, tnt.getY() + 1.25, cz);
                Vec3 screenPos = EspRenderUtil.projectToScreen(gr, tagWorldPos, sw, sh);
                if (screenPos != null) {
                    String tagText = String.format(Locale.ROOT, "💣 %.1fs", fuseSec);
                    if (dangerAlert && isDanger) {
                        tagText += " ⚠ DANGER";
                    }

                    int textW = font.width(tagText);
                    int tagW = textW + 10;
                    int tagH = 14;
                    int tagX = (int) Math.round(screenPos.x - tagW * 0.5);
                    int tagY = (int) Math.round(screenPos.y - tagH * 0.5);

                    GuiRenderUtils.drawDropShadow(extractor, tagX, tagY, tagW, tagH, 4, 2, 90);
                    GuiRenderUtils.fillRoundedRect(extractor, tagX, tagY, tagW, tagH, 3, 0xCC0F172A);
                    GuiRenderUtils.drawRoundedOutline(extractor, tagX, tagY, tagW, tagH, 3, color);
                    GuiRenderUtils.drawText(extractor, font, tagText, tagX + 5, tagY + 3, isDanger ? 0xFFEF4444 : 0xFFFFFFFF, true);
                }
            }
        }
    }

    /**
     * Renders a 3D circle wireframe projected to screen.
     * plane: 0 = X-Z horizontal, 1 = X-Y vertical, 2 = Z-Y vertical.
     */
    private static void renderCircle(GuiGraphicsExtractor extractor, GameRenderer gr,
                                     double cx, double cy, double cz, double r, int plane,
                                     int sw, int sh, float thickness, int color) {
        int segments = 24;
        Vec3 first = null;
        Vec3 prev = null;

        for (int i = 0; i <= segments; i++) {
            double angle = (i % segments) * (Math.PI * 2.0 / segments);
            double cos = Math.cos(angle) * r;
            double sin = Math.sin(angle) * r;

            double px = cx;
            double py = cy;
            double pz = cz;

            if (plane == 0) {
                px += cos;
                pz += sin;
            } else if (plane == 1) {
                px += cos;
                py += sin;
            } else {
                pz += cos;
                py += sin;
            }

            Vec3 sPos = EspRenderUtil.projectToScreen(gr, new Vec3(px, py, pz), sw, sh);
            if (prev != null && sPos != null) {
                EspRenderUtil.drawLine(extractor, prev.x, prev.y, sPos.x, sPos.y, thickness, color);
            }
            if (i == 0) first = sPos;
            prev = sPos;
        }

        if (prev != null && first != null) {
            EspRenderUtil.drawLine(extractor, prev.x, prev.y, first.x, first.y, thickness, color);
        }
    }
}
