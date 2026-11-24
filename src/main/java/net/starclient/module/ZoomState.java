package net.starclient.module;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

public final class ZoomState {
    private static Module zoomModule;
    private static float currentScale = 1.0F;
    private static long lastFrameNanos = 0L;

    private ZoomState() {
    }

    public static void setZoomModule(Module module) {
        zoomModule = module;
    }

    public static boolean isActive() {
        return zoomModule != null && zoomModule.isEnabled();
    }

    /**
     * Real-time zoom active check that immediately reflects key release on the render/mouse thread
     * instead of waiting for the next 20Hz client tick.
     */
    public static boolean isZoomHeldNow() {
        if (zoomModule == null || !zoomModule.isEnabled() || !zoomModule.canUse()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.gui.screen() != null) {
            return false;
        }
        int key = zoomModule.getKeyBind();
        if (key != GLFW.GLFW_KEY_UNKNOWN && zoomModule.isHoldKey()) {
            return InputConstants.isKeyDown(mc.getWindow(), key);
        }
        return true;
    }

    public static boolean isCinematicCameraEnabled() {
        return zoomModule != null
                && zoomModule.getSetting("Cinematic Camera") instanceof BooleanSetting bs
                && bs.getValue();
    }

    public static int getZoomKeyBind() {
        return zoomModule != null ? zoomModule.getKeyBind() : GLFW.GLFW_KEY_UNKNOWN;
    }

    public static void onScroll(double amount) {
        if (isZoomHeldNow()) {
            zoomModule.onScroll(amount);
        }
    }

    public static double getSensitivityMultiplier() {
        if (isZoomHeldNow()
                && zoomModule.getSetting("Mouse Sensitivity") instanceof NumberSetting ns) {
            return ns.getValue() / 100.0;
        }
        return 1.0;
    }

    public static float modifyFov(float baseFov) {
        boolean active = isZoomHeldNow();
        if (!active && Math.abs(currentScale - 1.0F) < 0.001F) {
            currentScale = 1.0F;
            lastFrameNanos = 0L;
            return baseFov;
        }

        double targetFov = 30.0;
        boolean smooth = true;
        double speed = 14.0;
        if (zoomModule != null) {
            if (zoomModule.getSetting("FOV") instanceof NumberSetting ns) {
                targetFov = ns.getValue();
            }
            if (zoomModule.getSetting("Smooth Transition") instanceof BooleanSetting bs) {
                smooth = bs.getValue();
            }
            if (zoomModule.getSetting("Transition Speed") instanceof NumberSetting ns) {
                speed = ns.getValue();
            }
        }

        float targetScale = active
                ? (float) Math.clamp(targetFov / Math.max(baseFov, 10.0F), 0.01, 1.5)
                : 1.0F;

        if (!smooth) {
            currentScale = targetScale;
            lastFrameNanos = 0L;
            return baseFov * currentScale;
        }

        long now = System.nanoTime();
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
        }
        float dt = Math.clamp((now - lastFrameNanos) / 1_000_000_000.0F, 0.0005F, 0.1F);
        lastFrameNanos = now;

        float blend = 1.0F - (float) Math.exp(-dt * (float) speed);
        currentScale = Mth.lerp(blend, currentScale, targetScale);
        if (!active && Math.abs(currentScale - 1.0F) < 0.001F) {
            currentScale = 1.0F;
        }
        return baseFov * currentScale;
    }
}
