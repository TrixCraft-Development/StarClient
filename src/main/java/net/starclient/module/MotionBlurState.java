package net.starclient.module;

public final class MotionBlurState {
    private static volatile boolean active;
    private static volatile double strength = 3.0;

    private MotionBlurState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        MotionBlurState.active = active;
    }

    public static double getStrength() {
        return strength;
    }

    public static void setStrength(double strength) {
        MotionBlurState.strength = strength;
    }
}
