package net.modernclient.module;

import net.modernclient.setting.ColorModeSetting;

import java.util.Map;

public final class HitColorState {
    public static final Map<String, Integer> HIT_COLORS = ColorModeSetting.ofPairs(
            "Client Accent", "FF38BDF8",
            "Crimson Red", "FFEF4444",
            "Hot Pink", "FFF472B6",
            "Electric Purple", "FFA855F7",
            "Neon Cyan", "FF22D3EE",
            "Emerald Green", "FF10B981",
            "Golden Yellow", "FFFACC15",
            "Pure White", "FFFFFFFF"
    );

    private static boolean active;
    private static int color = 0xFFEF4444;
    private static double alpha = 0.6;
    private static boolean dirty = true;

    private HitColorState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        if (active != value) {
            active = value;
            dirty = true;
        }
    }

    public static void update(int rgbColor, double alphaValue) {
        if (color != rgbColor || Math.abs(alpha - alphaValue) > 0.001) {
            color = rgbColor;
            alpha = alphaValue;
            dirty = true;
        }
    }

    public static int getOverlayPixelColor() {
        if (!active) {
            return -1291911168; // Vanilla default 0xB2FF0000 in ARGB
        }
        int a = Math.clamp((int) Math.round((1.0 - alpha) * 255.0), 0, 255);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static boolean consumeDirty() {
        if (dirty) {
            dirty = false;
            return true;
        }
        return false;
    }
}
