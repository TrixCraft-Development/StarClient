package net.starclient.module;

public final class LowFireState {
    private static boolean active;
    private static double offset = -0.25;

    private LowFireState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    public static float getOffset() {
        return (float) offset;
    }

    public static void setOffset(double value) {
        offset = value;
    }
}
