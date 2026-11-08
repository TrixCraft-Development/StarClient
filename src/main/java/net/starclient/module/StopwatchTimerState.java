package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.starclient.gui.ToastNotificationManager;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.Locale;

/**
 * State and timing controller for the "Stopwatch & Timer" HUD module.
 */
public final class StopwatchTimerState {

    private static Module module;
    private static boolean running = false;
    private static long accumulatedElapsedMs = 0L;
    private static long lastStartTimestampMs = 0L;
    private static long lastKeyPressMs = 0L;
    private static boolean countdownFinishedAlerted = false;
    private static double lastConfiguredCountdownMin = 5.0;

    private StopwatchTimerState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static Module getModule() {
        return module;
    }

    public static boolean isCountdownMode() {
        if (module == null) return false;
        Setting<?> s = module.getSetting("Mode");
        return s instanceof ModeSetting ms && "Countdown".equalsIgnoreCase(ms.getValue());
    }

    public static boolean isShowMilliseconds() {
        if (module == null) return true;
        Setting<?> s = module.getSetting("Show Milliseconds");
        return !(s instanceof BooleanSetting bs) || bs.getValue();
    }

    public static boolean isKeybindStartPause() {
        if (module == null) return true;
        Setting<?> s = module.getSetting("Keybind Action");
        return !(s instanceof ModeSetting ms) || "Start / Pause".equalsIgnoreCase(ms.getValue());
    }

    public static double getCountdownMinutes() {
        if (module != null && module.getSetting("Countdown Minutes") instanceof NumberSetting ns) {
            return ns.getValue();
        }
        return 5.0;
    }

    public static long getCountdownTotalMs() {
        return Math.round(getCountdownMinutes() * 60_000.0);
    }

    public static boolean isRunning() {
        return running;
    }

    public static boolean isCountdownFinished() {
        return isCountdownMode() && getRemainingCountdownMs() <= 0L;
    }

    public static long getElapsedMs() {
        if (!running) {
            return accumulatedElapsedMs;
        }
        long now = System.currentTimeMillis();
        return accumulatedElapsedMs + Math.max(0L, now - lastStartTimestampMs);
    }

    public static long getRemainingCountdownMs() {
        long total = getCountdownTotalMs();
        long elapsed = getElapsedMs();
        return Math.max(0L, total - elapsed);
    }

    public static void setRunning(boolean newRunning) {
        if (running == newRunning) {
            syncRunningSetting(running);
            return;
        }
        long now = System.currentTimeMillis();
        if (newRunning) {
            if (isCountdownMode() && getRemainingCountdownMs() <= 0L) {
                accumulatedElapsedMs = 0L;
                countdownFinishedAlerted = false;
            }
            lastStartTimestampMs = now;
            running = true;
        } else {
            accumulatedElapsedMs += Math.max(0L, now - lastStartTimestampMs);
            running = false;
        }
        syncRunningSetting(running);
    }

    public static void toggleRunning() {
        setRunning(!running);
    }

    public static void reset() {
        running = false;
        accumulatedElapsedMs = 0L;
        lastStartTimestampMs = System.currentTimeMillis();
        countdownFinishedAlerted = false;
        syncRunningSetting(false);
    }

    private static void syncRunningSetting(boolean state) {
        if (module != null && module.getSetting("Running") instanceof BooleanSetting bs) {
            if (bs.getValue() != state) {
                bs.setValue(state);
            }
        }
    }

    public static void onKeyPress(Minecraft client) {
        if (module == null || !module.canUse()) return;
        if (!module.isEnabled()) {
            module.setEnabled(true);
        }

        long now = System.currentTimeMillis();
        boolean doubleTapReset = module.getSetting("Double-Tap Reset") instanceof BooleanSetting bs ? bs.getValue() : true;
        if (doubleTapReset && (now - lastKeyPressMs) <= 350L) {
            lastKeyPressMs = 0L;
            reset();
            if (client != null && client.player != null) {
                client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 0.8f);
            }
            ToastNotificationManager.pushToast(
                    "stopwatch_state",
                    isCountdownMode() ? "Countdown Reset" : "Stopwatch Reset",
                    formatDisplayTime(),
                    "RESET",
                    0xFF94A3B8
            );
            return;
        }

        lastKeyPressMs = now;
        toggleRunning();
        if (client != null && client.player != null) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, running ? 1.25f : 0.95f);
        }
        ToastNotificationManager.pushToast(
                "stopwatch_state",
                isCountdownMode() ? "Countdown Timer" : "Stopwatch",
                formatDisplayTime(),
                running ? "RUNNING" : "PAUSED",
                running ? 0xFF34D399 : 0xFFFACC15
        );
    }

    public static void onClientTick(Minecraft client) {
        if (module == null) return;

        // Sync UI toggle buttons in ClickGUI drawer
        if (module.getSetting("Reset Timer") instanceof BooleanSetting resetBtn && resetBtn.getValue()) {
            resetBtn.setValue(false);
            reset();
            ToastNotificationManager.pushToast(
                    "stopwatch_state",
                    isCountdownMode() ? "Countdown Reset" : "Stopwatch Reset",
                    formatDisplayTime(),
                    "RESET",
                    0xFF94A3B8
            );
        }

        if (module.getSetting("Running") instanceof BooleanSetting runSetting) {
            if (runSetting.getValue() != running) {
                setRunning(runSetting.getValue());
            }
        }

        double curMin = getCountdownMinutes();
        if (Math.abs(curMin - lastConfiguredCountdownMin) > 0.001) {
            lastConfiguredCountdownMin = curMin;
            if (!running && isCountdownMode()) {
                accumulatedElapsedMs = 0L;
                countdownFinishedAlerted = false;
            }
        }

        if (running && isCountdownMode() && getRemainingCountdownMs() <= 0L) {
            accumulatedElapsedMs = getCountdownTotalMs();
            running = false;
            syncRunningSetting(false);
            if (!countdownFinishedAlerted) {
                countdownFinishedAlerted = true;
                boolean sound = !(module.getSetting("Alert Sound") instanceof BooleanSetting bs) || bs.getValue();
                if (sound && client != null && client.player != null) {
                    client.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.85f, 1.2f);
                }
                ToastNotificationManager.pushToast(
                        "stopwatch_finished",
                        "Timer Finished!",
                        String.format(Locale.US, "%.1f min countdown complete", curMin),
                        "DONE",
                        0xFFFACC15,
                        3500L
                );
            }
        }
    }

    public static String formatDisplayTime() {
        long ms = isCountdownMode() ? getRemainingCountdownMs() : getElapsedMs();
        boolean showMillis = isShowMilliseconds();

        long totalSeconds = ms / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        long tenths = (ms % 1000L) / 100L;

        if (hours > 0) {
            return showMillis
                    ? String.format(Locale.US, "%d:%02d:%02d.%d", hours, minutes, seconds, tenths)
                    : String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return showMillis
                ? String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
                : String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
}
