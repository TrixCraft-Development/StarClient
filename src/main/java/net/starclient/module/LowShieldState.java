package net.starclient.module;

public final class LowShieldState {
    private static boolean active;
    private static double heightOffset = -0.25;
    private static double sideOffset = 0.0;
    private static double scale = 1.0;
    private static boolean lowerWhenBlocking = true;

    private LowShieldState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    public static float getHeightOffset() {
        return (float) heightOffset;
    }

    public static void setHeightOffset(double value) {
        heightOffset = value;
    }

    public static float getSideOffset() {
        return (float) sideOffset;
    }

    public static void setSideOffset(double value) {
        sideOffset = value;
    }

    public static float getScale() {
        return (float) scale;
    }

    public static void setScale(double value) {
        scale = value;
    }

    public static boolean isLowerWhenBlocking() {
        return lowerWhenBlocking;
    }

    public static void setLowerWhenBlocking(boolean value) {
        lowerWhenBlocking = value;
    }
}
