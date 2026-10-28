package net.starclient.module;

public final class NoHurtCamState {
    private static boolean active;
    private static double multiplier = 0.0;

    private NoHurtCamState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    public static double getMultiplier() {
        return multiplier;
    }

    public static void setMultiplier(double value) {
        multiplier = value;
    }
}
