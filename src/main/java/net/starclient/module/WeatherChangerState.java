package net.starclient.module;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class WeatherChangerState {
    private static boolean active;
    private static float rainLevel;
    private static float thunderLevel;

    private WeatherChangerState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        WeatherChangerState.active = active;
    }

    public static void setRainLevel(float rainLevel) {
        WeatherChangerState.rainLevel = rainLevel;
    }

    public static void setThunderLevel(float thunderLevel) {
        WeatherChangerState.thunderLevel = thunderLevel;
    }

    public static void modifyRain(Level level, float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!active || !level.canHaveWeather()) return;
        cir.setReturnValue(rainLevel);
    }

    public static void modifyThunder(Level level, float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!active || !level.canHaveWeather()) return;
        cir.setReturnValue(thunderLevel);
    }
}