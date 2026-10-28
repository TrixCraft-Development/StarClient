package net.starclient.module;

public final class FreeLookState {
    private static Module freeLookModule;
    private static boolean initialized;
    private static float yaw;
    private static float pitch;

    private FreeLookState() {
    }

    public static void setFreeLookModule(Module module) {
        freeLookModule = module;
    }

    public static boolean isActive() {
        return freeLookModule != null && freeLookModule.isEnabled();
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static void setInitialized(boolean value) {
        initialized = value;
    }

    public static void setRotation(float yaw, float pitch) {
        FreeLookState.yaw = yaw;
        FreeLookState.pitch = Math.clamp(pitch, -90.0F, 90.0F);
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
