package net.modernclient.module;

public final class FullbrightState {
    private static boolean gammaActive;

    private FullbrightState() {
    }

    public static boolean isGammaActive() {
        return gammaActive;
    }

    public static void setGammaActive(boolean active) {
        gammaActive = active;
    }
}
