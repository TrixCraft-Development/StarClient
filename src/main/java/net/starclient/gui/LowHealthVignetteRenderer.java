package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

/**
 * Renders a smooth pulsing screen-edge vignette when the player's health drops
 * below the configured threshold ("Low Health Vignette" RENDER module).
 */
public final class LowHealthVignetteRenderer {

    private LowHealthVignetteRenderer() {
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod, int screenWidth, int screenHeight) {
        if (client == null || client.player == null || mod == null) return;
        Player player = client.player;
        if (player.getAbilities().instabuild || player.isSpectator() || player.isDeadOrDying()) return;

        float thresholdHp = (float) getNum(mod, "Health Threshold", 8.0);
        float hp = player.getHealth();
        if (hp > thresholdHp) return;

        float severity = Math.clamp(1.0f - (hp / Math.max(1.0f, thresholdHp)), 0.18f, 1.0f);
        double pulseSpeed = getNum(mod, "Pulse Speed", 2.0);
        double maxOpacityPct = getNum(mod, "Max Opacity %", 55.0);

        float pulse = (float) ((Math.sin(System.currentTimeMillis() / (260.0 / Math.max(0.2, pulseSpeed))) + 1.0) * 0.5);
        float alphaRatio = (float) (maxOpacityPct / 100.0) * severity * (0.55f + 0.45f * pulse);
        int edgeAlpha = Math.clamp(Math.round(alphaRatio * 255.0f), 8, 225);

        boolean useClientCol = getBool(mod, "Use Client Color", false);
        int baseRgb = 0xFFEF4444;
        if (useClientCol && StarClient.getInstance() != null && StarClient.getInstance().getClientSettings() != null) {
            baseRgb = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        } else if (mod.getSetting("Color") instanceof ColorModeSetting cms) {
            baseRgb = cms.getColor();
        }

        int rgb = baseRgb & 0x00FFFFFF;
        int outerColor = (edgeAlpha << 24) | rgb;
        int clearColor = rgb; // 0 alpha with matching RGB for clean gradient interpolation

        int bandDepthV = Math.max(36, screenHeight / 4);
        int bandDepthH = Math.max(44, screenWidth / 5);

        // Top and bottom vertical gradient bands
        extractor.fillGradient(0, 0, screenWidth, bandDepthV, outerColor, clearColor);
        extractor.fillGradient(0, screenHeight - bandDepthV, screenWidth, screenHeight, clearColor, outerColor);

        // Left and right smooth stepped horizontal vignette bands
        int steps = 16;
        int stepW = Math.max(2, bandDepthH / steps);
        for (int i = 0; i < steps; i++) {
            float t = 1.0f - (float) i / steps;
            int stepAlpha = Math.round(edgeAlpha * t * t * 0.85f);
            if (stepAlpha <= 1) continue;
            int col = (stepAlpha << 24) | rgb;
            int x0 = i * stepW;
            int x1 = Math.min(bandDepthH, (i + 1) * stepW);
            extractor.fill(x0, 0, x1, screenHeight, col);
            extractor.fill(screenWidth - x1, 0, screenWidth - x0, screenHeight, col);
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
