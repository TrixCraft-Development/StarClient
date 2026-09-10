package net.modernclient.module;

public final class FreeCamState {
    private static Module freeCamModule;
    private static boolean initialized;
    private static double x;
    private static double y;
    private static double z;
    private static float yaw;
    private static float pitch;

    private FreeCamState() {
    }

    public static void setFreeCamModule(Module module) {
        freeCamModule = module;
    }

    public static boolean isActive() {
        return freeCamModule != null && freeCamModule.isEnabled();
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void setInitialized(boolean value) {
        initialized = value;
    }

    public static void setPosition(double x, double y, double z) {
        FreeCamState.x = x;
        FreeCamState.y = y;
        FreeCamState.z = z;
    }

    public static double getX() {
        return x;
    }

    public static double getY() {
        return y;
    }

    public static double getZ() {
        return z;
    }

    public static void addPosition(double dx, double dy, double dz) {
        x += dx;
        y += dy;
        z += dz;
    }

    public static void setRotation(float yaw, float pitch) {
        FreeCamState.yaw = yaw;
        FreeCamState.pitch = Math.clamp(pitch, -90.0F, 90.0F);
    }

    public static float getYaw() {
        return yaw;
    }

    public static float getPitch() {
        return pitch;
    }

    public static void addRotation(double dYaw, double dPitch) {
        yaw += (float) dYaw;
        pitch = Math.clamp(pitch + (float) dPitch, -90.0F, 90.0F);
    }
}
