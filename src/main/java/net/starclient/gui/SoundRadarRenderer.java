package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.module.SoundRadarState;

import java.util.List;
import java.util.Locale;

/**
 * Renders the Sound Radar HUD card and optional crosshair directional sound chevrons.
 */
public final class SoundRadarRenderer {

    public static final int RADAR_SIZE = 96;

    private SoundRadarRenderer() {
    }

    public static int getWidth(Font font, Minecraft client, Module mod) {
        return RADAR_SIZE;
    }

    public static int getHeight(Minecraft client, Module mod) {
        return RADAR_SIZE;
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod,
                              HudLayout layout, int screenWidth, int screenHeight) {
        if (!SoundRadarState.isActive() || client == null || client.player == null) return;

        int x = layout.effectiveX(HudLayout.SOUND_RADAR, screenWidth, RADAR_SIZE);
        int y = layout.effectiveY(HudLayout.SOUND_RADAR, screenHeight);

        if (SoundRadarState.isRadarCard()) {
            renderRadarCard(extractor, client, mod, x, y, screenWidth, screenHeight);
        }

        if (SoundRadarState.isCrosshairIndicators()) {
            renderCrosshairIndicators(extractor, client, screenWidth, screenHeight);
        }
    }

    public static void renderPreview(GuiGraphicsExtractor extractor, Module mod) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return;
        HudLayout layout = StarClient.getInstance().getHudLayout();
        int x = layout.effectiveX(HudLayout.SOUND_RADAR, extractor.guiWidth(), RADAR_SIZE);
        int y = layout.effectiveY(HudLayout.SOUND_RADAR, extractor.guiHeight());

        renderRadarCard(extractor, client, mod, x, y, extractor.guiWidth(), extractor.guiHeight());
    }

    private static void renderRadarCard(GuiGraphicsExtractor extractor, Minecraft client, Module mod,
                                        int x, int y, int screenWidth, int screenHeight) {
        x = Math.clamp(x, 2, Math.max(2, screenWidth - RADAR_SIZE - 2));
        y = Math.clamp(y, 2, Math.max(2, screenHeight - RADAR_SIZE - 2));

        int cx = x + RADAR_SIZE / 2;
        int cy = y + RADAR_SIZE / 2;
        int r = RADAR_SIZE / 2 - 4;

        int accent = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        boolean shadow = StarClient.getInstance().getClientSettings().isHudTextShadow();

        if (!mod.isTransparentBackground()) {
            int bg = (mod.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
            GuiRenderUtils.drawDropShadow(extractor, x, y, RADAR_SIZE, RADAR_SIZE, 6, 3, 90);
            GuiRenderUtils.fillRoundedRect(extractor, x, y, RADAR_SIZE, RADAR_SIZE, 6, bg);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, RADAR_SIZE, RADAR_SIZE, 6, 0x30FFFFFF);
        }

        // Radar grid rings
        drawRadarRing(extractor, cx, cy, r / 2, 0x22FFFFFF);
        drawRadarRing(extractor, cx, cy, r, 0x44FFFFFF);

        // Compass orientation (relative to player yaw)
        Player player = client.player;
        float playerYaw = player != null ? player.getYRot() : 0.0f;
        double radYaw = Math.toRadians(playerYaw);

        // Center player pointer (always points UP on radar)
        GuiRenderUtils.drawCenteredText(extractor, client.font, "▲", cx, cy - 4, accent);

        // North indicator tick on outer ring
        double northAngle = Math.toRadians(-playerYaw);
        int nx = cx + (int) Math.round((r - 2) * Math.sin(northAngle));
        int ny = cy - (int) Math.round((r - 2) * Math.cos(northAngle));
        GuiRenderUtils.drawCenteredText(extractor, client.font, "N", nx, ny - 3, 0xFFEF4444);

        double maxDist = SoundRadarState.getMaxDistance();
        float fadeTime = SoundRadarState.getFadeTime();
        List<SoundRadarState.SoundPing> pings = SoundRadarState.getRecentPings();

        if (pings.isEmpty() && (player == null || client.gui.screen() instanceof ClickGuiScreen || client.gui.screen() instanceof HudEditorScreen)) {
            // Preview pings in editor
            renderPingBlip(extractor, client.font, cx + 18, cy - 14, 0xFF38BDF8, "👤", "Footsteps", "14m", 1.0f, shadow);
            renderPingBlip(extractor, client.font, cx - 22, cy + 16, 0xFFEF4444, "💥", "Explosion", "26m", 0.85f, shadow);
            return;
        }

        if (player == null) return;
        double px = player.getX();
        double pz = player.getZ();

        for (SoundRadarState.SoundPing ping : pings) {
            float age = ping.getAgeSeconds();
            if (age >= fadeTime) continue;

            float alphaRatio = Math.clamp(1.0f - (age / fadeTime), 0.0f, 1.0f);
            double dx = ping.x - px;
            double dz = ping.z - pz;
            double dist = Math.hypot(dx, dz);
            if (dist > maxDist || dist < 0.5) continue;

            // Angle relative to player view: 0 is forward, 90 is right, 180 is behind
            double worldAngle = Math.atan2(dz, dx); // radians
            double relAngle = worldAngle - radYaw - Math.PI / 2.0;

            double normalizedDist = (dist / maxDist) * (r - 8);
            int pingX = cx + (int) Math.round(normalizedDist * Math.cos(relAngle));
            int pingY = cy + (int) Math.round(normalizedDist * Math.sin(relAngle));

            int col = ping.category.defaultColor;
            String distStr = String.format(Locale.US, "%.0fm", dist);
            renderPingBlip(extractor, client.font, pingX, pingY, col, ping.category.icon, ping.label, distStr, alphaRatio, shadow);
        }
    }

    private static void renderPingBlip(GuiGraphicsExtractor extractor, Font font, int x, int y, int baseCol,
                                       String icon, String label, String distStr, float alpha, boolean shadow) {
        int a = Math.clamp(Math.round(alpha * 255.0f), 20, 255);
        int pingCol = (a << 24) | (baseCol & 0x00FFFFFF);

        // Glow ring & dot
        GuiRenderUtils.fillRoundedRect(extractor, x - 3, y - 3, 6, 6, 3, pingCol);
        GuiRenderUtils.drawRoundedOutline(extractor, x - 4, y - 4, 8, 8, 4, (Math.round(a * 0.5f) << 24) | 0x00FFFFFF);

        // Distance & label text (small readout)
        if (SoundRadarState.isShowDistance() && font != null) {
            String text = SoundRadarState.isShowSoundName() ? (label + " " + distStr) : distStr;
            int textCol = (a << 24) | 0x00E2E8F0;
            extractor.text(font, text, x + 5, y - 4, textCol, shadow);
        }
    }

    private static void renderCrosshairIndicators(GuiGraphicsExtractor extractor, Minecraft client, int screenWidth, int screenHeight) {
        Player player = client.player;
        if (player == null) return;

        double px = player.getX();
        double pz = player.getZ();
        float playerYaw = player.getYRot();
        double radYaw = Math.toRadians(playerYaw);

        int cx = screenWidth / 2;
        int cy = screenHeight / 2;
        int crosshairRadius = 45;

        double maxDist = SoundRadarState.getMaxDistance();
        float fadeTime = SoundRadarState.getFadeTime();

        for (SoundRadarState.SoundPing ping : SoundRadarState.getRecentPings()) {
            float age = ping.getAgeSeconds();
            if (age >= fadeTime) continue;

            float alphaRatio = Math.clamp(1.0f - (age / fadeTime), 0.0f, 1.0f);
            double dx = ping.x - px;
            double dz = ping.z - pz;
            double dist = Math.hypot(dx, dz);
            if (dist > maxDist || dist < 1.0) continue;

            double worldAngle = Math.atan2(dz, dx);
            double relAngle = worldAngle - radYaw - Math.PI / 2.0;

            int indX = cx + (int) Math.round(crosshairRadius * Math.cos(relAngle));
            int indY = cy + (int) Math.round(crosshairRadius * Math.sin(relAngle));

            int a = Math.clamp(Math.round(alphaRatio * 220.0f), 20, 255);
            int col = (a << 24) | (ping.category.defaultColor & 0x00FFFFFF);

            // Draw a chevron or indicator dot on screen edge around crosshair
            GuiRenderUtils.fillRoundedRect(extractor, indX - 2, indY - 2, 5, 5, 2, col);
            if (SoundRadarState.isShowDistance()) {
                String distStr = String.format(Locale.US, "%.0fm", dist);
                extractor.text(client.font, distStr, indX + 4, indY - 4, (a << 24) | 0x00FFFFFF, true);
            }
        }
    }

    private static void drawRadarRing(GuiGraphicsExtractor extractor, int cx, int cy, int radius, int color) {
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            double a1 = (i * 2 * Math.PI) / segments;
            double a2 = ((i + 1) * 2 * Math.PI) / segments;
            int x1 = cx + (int) Math.round(radius * Math.cos(a1));
            int y1 = cy + (int) Math.round(radius * Math.sin(a1));
            int x2 = cx + (int) Math.round(radius * Math.cos(a2));
            int y2 = cy + (int) Math.round(radius * Math.sin(a2));
            extractor.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
        }
    }
}
