package net.modernclient.module;

public final class CustomCrosshairState {
    private static Module module;

    private CustomCrosshairState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static Module getModule() {
        return module;
    }

    public static boolean isActive() {
        return module != null && module.isEnabled();
    }
}
