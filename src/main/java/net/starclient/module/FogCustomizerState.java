package net.starclient.module;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.level.material.FogType;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.NumberSetting;

public final class FogCustomizerState {
    private static Module module;

    private FogCustomizerState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isActive() {
        return module != null && module.isEnabled();
    }

    public static boolean isDisableWaterFog() {
        return isActive() && isBool("Disable Water Fog", true);
    }

    public static boolean isDisableLavaFog() {
        return isActive() && isBool("Disable Lava Fog", true);
    }

    public static boolean shouldDisableBlindnessFog() {
        return isActive() && isBool("Disable Blindness Fog", true);
    }

    public static boolean shouldReplaceFluidFogType(FogType fogType) {
        if (!isActive() || fogType == null) {
            return false;
        }
        return switch (fogType) {
            case WATER -> isBool("Disable Water Fog", true);
            case LAVA -> isBool("Disable Lava Fog", true);
            case POWDER_SNOW -> isBool("Disable Blindness Fog", true);
            default -> false;
        };
    }

    public static void modifyFog(Camera camera, FogData fog) {
        if (!isActive() || fog == null || camera == null) {
            return;
        }

        FogType fluid = camera.getFluidInCamera();
        if (fluid == FogType.WATER) {
            if (isBool("Disable Water Fog", true)) {
                pushFogFar(fog);
            }
            return;
        }
        if (fluid == FogType.LAVA) {
            if (isBool("Disable Lava Fog", true)) {
                pushFogFar(fog);
            }
            return;
        }
        if (fluid == FogType.POWDER_SNOW) {
            if (isBool("Disable Blindness Fog", true)) {
                pushFogFar(fog);
            }
            return;
        }

        if (isBool("Disable Terrain Fog", false)) {
            pushFogFar(fog);
        } else {
            float mult = (float) getNum();
            if (Math.abs(mult - 1.0F) > 0.01F) {
                fog.environmentalStart *= mult;
                fog.environmentalEnd *= mult;
                fog.renderDistanceStart *= mult;
                fog.renderDistanceEnd *= mult;
            }
        }
    }

    private static void pushFogFar(FogData fog) {
        final float far = Float.MAX_VALUE;
        fog.environmentalStart = far;
        fog.environmentalEnd = far;
        fog.renderDistanceStart = far;
        fog.renderDistanceEnd = far;
        fog.skyEnd = far;
        fog.cloudEnd = far;
        fog.color.w = 0.0F;
    }

    private static boolean isBool(String name, boolean fallback) {
        if (module != null && module.getSetting(name) instanceof BooleanSetting bs) {
            return bs.getValue();
        }
        return fallback;
    }

    private static double getNum() {
        if (module != null && module.getSetting("Fog Distance") instanceof NumberSetting ns) {
            return ns.getValue();
        }
        return 1.5;
    }
}
