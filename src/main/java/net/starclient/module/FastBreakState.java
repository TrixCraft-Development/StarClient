package net.starclient.module;

public final class FastBreakState {
    private static boolean active;
    private static double speed = 3.0;

    private FastBreakState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        FastBreakState.active = active;
    }

    public static double getSpeed() {
        return speed;
    }

    public static void setSpeed(double speed) {
        FastBreakState.speed = speed;
    }
}