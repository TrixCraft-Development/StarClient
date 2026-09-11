package net.modernclient.module;

import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class TimeChangerState {
    public static final long PERIOD = 24000L;
    private static boolean active;
    private static double desiredTime = 6000.0;

    private TimeChangerState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        TimeChangerState.active = active;
    }

    public static double getDesiredTime() {
        return desiredTime;
    }

    public static void setDesiredTime(double desiredTime) {
        TimeChangerState.desiredTime = desiredTime;
    }

    public static void modify(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
        if (!active || !clock.is(WorldClocks.OVERWORLD)) return;
        long real = cir.getReturnValue();
        long desired = Math.floorMod(Math.round(desiredTime), PERIOD);
        cir.setReturnValue(real - Math.floorMod(real, PERIOD) + desired);
    }
}