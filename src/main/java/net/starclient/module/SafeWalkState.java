package net.starclient.module;

public final class SafeWalkState {
    private static boolean active;

    private SafeWalkState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        SafeWalkState.active = active;
    }
}