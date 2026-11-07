package net.starclient.module;

import net.starclient.setting.BooleanSetting;
import net.starclient.setting.Setting;

/**
 * State bridge for the "Hunger & Saturation" (AppleSkin-style) UI module.
 */
public final class HungerSaturationState {

    private static Module module;

    private HungerSaturationState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static boolean isShowSaturation() {
        return isEnabled() && getBool("Show Saturation", true);
    }

    public static boolean isShowFoodPreview() {
        return isEnabled() && getBool("Show Food Preview", true);
    }

    public static boolean isShowExhaustionBar() {
        return isEnabled() && getBool("Show Exhaustion Bar", true);
    }

    public static boolean isFoodTooltipInfo() {
        return isEnabled() && getBool("Food Tooltip Info", true);
    }

    private static boolean getBool(String name, boolean def) {
        if (module == null) return def;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }
}
