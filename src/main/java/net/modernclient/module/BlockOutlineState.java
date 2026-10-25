package net.modernclient.module;

import net.modernclient.setting.ColorModeSetting;

import java.util.Map;

public final class BlockOutlineState {
    public static final Map<String, Integer> OUTLINE_COLORS = ColorModeSetting.ofPairs(
            "Client Accent", "FF38BDF8",
            "Sky Blue", "FF38BDF8",
            "Cyan", "FF22D3EE",
            "Emerald", "FF34D399",
            "Gold", "FFFACC15",
            "Crimson", "FFEF4444",
            "Pink", "FFF472B6",
            "Purple", "FFA78BFA",
            "White", "FFFFFFFF"
    );

    private static Module module;

    private BlockOutlineState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static Module getModule() {
        return module;
    }

    public static boolean isActive() {
        return module != null && module.isEnabled();
    }
}
