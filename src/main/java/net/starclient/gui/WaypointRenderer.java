package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.module.WaypointsState;
import org.joml.Matrix3x2fStack;

import java.util.List;

public final class WaypointRenderer {

    private WaypointRenderer() {
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client) {
        if (client == null || client.level == null || client.player == null) return;
        if (!WaypointsState.isActive()) return;

        List<WaypointsState.ResolvedWaypoint> resolved = WaypointsState.getRenderableWaypoints(client);
        if (resolved.isEmpty()) return;

        GameRenderer gameRenderer = client.gameRenderer;
        int screenW = extractor.guiWidth();
        int screenH = extractor.guiHeight();
        boolean showBeam = WaypointsState.isShowBeams();
        boolean showDistance = WaypointsState.isShowDistance();
        float baseScale = (float) WaypointsState.getMarkerScale();
        boolean textShadow = StarClient.getInstance().getClientSettings().isHudTextShadow();

        for (WaypointsState.ResolvedWaypoint rwp : resolved) {
            WaypointsState.Waypoint wp = rwp.waypoint();
            int color = wp.getColor();

            if (showBeam) {
                int beamOuter = (color & 0x00FFFFFF) | 0x55000000;
                int beamCore = (color & 0x00FFFFFF) | 0xAA000000;
                Vec3 bottom = new Vec3(rwp.x(), Math.max(-64.0, rwp.y() - 64.0), rwp.z());
                Vec3 top = new Vec3(rwp.x(), Math.min(320.0, rwp.y() + 180.0), rwp.z());
                EspRenderUtil.drawSegment3D(extractor, gameRenderer, bottom, top, screenW, screenH, 3.0F, beamOuter);
                EspRenderUtil.drawSegment3D(extractor, gameRenderer, bottom, top, screenW, screenH, 1.2F, beamCore);
            }

            Vec3 anchorWorld = new Vec3(rwp.x(), rwp.y() + 1.35, rwp.z());
            Vec3 projected = EspRenderUtil.projectToScreen(gameRenderer, anchorWorld, screenW, screenH);
            if (projected == null) continue;

            double dist = Math.sqrt(client.player.distanceToSqr(rwp.x(), rwp.y(), rwp.z()));
            String distLabel = dist >= 1000.0
                    ? String.format(java.util.Locale.US, "%.1fkm", dist / 1000.0)
                    : ((int) Math.round(dist)) + "m";

            String title = wp.getName() + (rwp.netherTranslated() ? " [8:1]" : "");
            String fullText = showDistance ? (title + "  " + distLabel) : title;

            // Gentle distance scaling so distant waypoints stay compact without vanishing
            float distFactor = dist > 40.0 ? (float) Math.clamp(1.0 - (dist - 40.0) / 500.0, 0.72, 1.0) : 1.0F;
            float scale = baseScale * distFactor;

            int titleW = client.font.width(title);
            int distW = showDistance ? client.font.width(distLabel) + 6 : 0;
            int pillW = 14 + titleW + distW;
            int pillH = 15;

            Matrix3x2fStack pose = extractor.pose();
            pose.pushMatrix();
            pose.translate((float) projected.x, (float) projected.y);
            pose.scale(scale, scale);

            int left = -pillW / 2;
            int top = -pillH - 4;

            // Floating pointer diamond beneath pill
            extractor.fill(-2, -3, 2, 1, color);
            extractor.fill(-1, -4, 1, 2, 0xFFFFFFFF);

            // Pill backdrop + accent border
            GuiRenderUtils.fillRoundedRect(extractor, left, top, pillW, pillH, 4, 0xCC0B1120);
            GuiRenderUtils.drawRoundedOutline(extractor, left, top, pillW, pillH, 4, (color & 0x00FFFFFF) | 0xAA000000);

            // Left color indicator bar
            GuiRenderUtils.fillRoundedRect(extractor, left + 3, top + 3, 4, pillH - 6, 2, color);

            // Name + Distance
            extractor.text(client.font, title, left + 10, top + 4, 0xFFF8FAFC, textShadow);
            if (showDistance) {
                extractor.text(client.font, distLabel, left + 10 + titleW + 5, top + 4, color, textShadow);
            }
            // Suppress unused variable warning for fullText while keeping clean layout

            pose.popMatrix();
        }
    }
}
