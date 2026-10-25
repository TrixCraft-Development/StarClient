package net.modernclient.module;

public final class TntTimerState {
    private static volatile boolean active;

    private TntTimerState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        TntTimerState.active = active;
    }
}
