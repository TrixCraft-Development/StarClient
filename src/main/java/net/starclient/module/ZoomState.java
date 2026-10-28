package net.starclient.module;

public final class ZoomState {
    private static Module zoomModule;

    private ZoomState() {
    }

    public static void setZoomModule(Module module) {
        zoomModule = module;
    }

    public static boolean isActive() {
        return zoomModule != null && zoomModule.isEnabled();
    }

    public static void onScroll(double amount) {
        if (zoomModule != null && zoomModule.isEnabled()) {
            zoomModule.onScroll(amount);
        }
    }
}
