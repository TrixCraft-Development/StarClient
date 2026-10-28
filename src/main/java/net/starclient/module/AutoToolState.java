package net.starclient.module;

public final class AutoToolState {
    private static boolean active;
    private static boolean switchBack = true;
    private static boolean mining;
    private static int previousSlot = -1;

    private AutoToolState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        AutoToolState.active = active;
    }

    public static boolean isSwitchBack() {
        return switchBack;
    }

    public static void setSwitchBack(boolean switchBack) {
        AutoToolState.switchBack = switchBack;
    }

    public static boolean isMining() {
        return mining;
    }

    public static void setMining(boolean mining) {
        AutoToolState.mining = mining;
    }

    public static int getPreviousSlot() {
        return previousSlot;
    }

    public static void setPreviousSlot(int previousSlot) {
        AutoToolState.previousSlot = previousSlot;
    }

    public static void reset() {
        mining = false;
        previousSlot = -1;
    }
}
