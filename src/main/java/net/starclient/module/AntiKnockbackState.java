package net.starclient.module;

public final class AntiKnockbackState {
    private static boolean active;
    private static double reduction;

    private AntiKnockbackState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        AntiKnockbackState.active = active;
    }

    public static double getReduction() {
        return reduction;
    }

    public static void setReduction(double reduction) {
        AntiKnockbackState.reduction = reduction;
    }
}
