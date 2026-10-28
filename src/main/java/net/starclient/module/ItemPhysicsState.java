package net.starclient.module;

public final class ItemPhysicsState {
    private static volatile boolean active;
    private static volatile double rotationSpeed = 1.0;

    private ItemPhysicsState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        ItemPhysicsState.active = active;
    }

    public static double getRotationSpeed() {
        return rotationSpeed;
    }

    public static void setRotationSpeed(double rotationSpeed) {
        ItemPhysicsState.rotationSpeed = rotationSpeed;
    }
}
