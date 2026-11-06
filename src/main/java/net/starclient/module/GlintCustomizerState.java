package net.starclient.module;

import net.minecraft.client.renderer.state.OptionsRenderState;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.NumberSetting;

public final class GlintCustomizerState {
    private static Module module;

    private GlintCustomizerState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isActive() {
        return module != null && module.isEnabled();
    }

    public static boolean isDisableAllGlint() {
        return isActive() && isBool("Disable All Glint");
    }

    public static boolean isForceAllGlint() {
        return isActive() && !isDisableAllGlint() && isBool("Force Glint on All Items");
    }

    public static void applyOptions(OptionsRenderState state) {
        if (!isActive() || state == null) {
            return;
        }
        if (isDisableAllGlint()) {
            state.glintStrength = 0.0;
            return;
        }
        double speed = getNum("Glint Speed");
        double strength = getNum("Glint Strength");
        state.glintSpeed = Math.clamp(speed * 0.5, 0.0, 5.0);
        state.glintStrength = Math.clamp(strength * 0.75, 0.0, 1.0);
    }

    private static boolean isBool(String name) {
        if (module != null && module.getSetting(name) instanceof BooleanSetting bs) {
            return bs.getValue();
        }
        return false;
    }

    private static double getNum(String name) {
        if (module != null && module.getSetting(name) instanceof NumberSetting ns) {
            return ns.getValue();
        }
        return 1.0;
    }
}
