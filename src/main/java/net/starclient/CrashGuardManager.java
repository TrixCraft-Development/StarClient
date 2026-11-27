package net.starclient;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.util.MemoryReserve;
import net.starclient.gui.BaseAlertRenderer;
import net.starclient.gui.BeaconHelperRenderer;
import net.starclient.gui.BedDefenseRenderer;
import net.starclient.gui.BedwarsTimersRenderer;
import net.starclient.gui.BedwarsUpgradesRenderer;
import net.starclient.gui.FireballAlertRenderer;
import net.starclient.gui.LightOverlayRenderer;
import net.starclient.gui.LootFeedRenderer;
import net.starclient.gui.LowBlocksWarningRenderer;
import net.starclient.gui.SpawnerOverlayRenderer;
import net.starclient.gui.TacticalPingRenderer;
import net.starclient.mixin.BlockableEventLoopAccessor;
import net.starclient.mixin.MinecraftAccessor;
import net.starclient.module.FreeCamState;
import net.starclient.module.FreeLookState;
import net.starclient.module.StorageFinderManager;
import net.starclient.module.WorldMapManager;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Coordinates the "Prevent Game Crashes" general client setting.
 * When enabled, intercepts runtime crashes on the client loop, saves the crash report
 * to crash-reports/, resets broken render/world state, disconnects cleanly from any
 * active world/server, and returns the player to the Main Menu (TitleScreen) so the
 * game stays open without needing a restart.
 */
public final class CrashGuardManager {

    private static final long LOOP_WINDOW_MS = 4500L;
    private static final int MAX_CRASHES_IN_WINDOW = 6;
    private static final int MAX_CONSECUTIVE_TITLE_CRASHES = 3;

    private static final Deque<Long> RECENT_CRASH_TIMESTAMPS = new ArrayDeque<>();
    private static int consecutiveTitleCrashes = 0;
    private static boolean recovering = false;
    private static volatile boolean pendingRunReentry = false;

    // Last caught crash notice shown cleanly on TitleScreen
    private static volatile boolean noticeActive = false;
    private static volatile String lastCrashSummary = "";
    private static volatile int preventedCount = 0;

    private CrashGuardManager() {
    }

    public static boolean isEnabled() {
        StarClient sc = StarClient.getInstance();
        return sc != null && sc.getClientSettings() != null && sc.getClientSettings().isPreventGameCrashes();
    }

    public static boolean hasActiveNotice() {
        return noticeActive;
    }

    public static String getLastCrashSummary() {
        return lastCrashSummary;
    }

    public static int getPreventedCount() {
        return preventedCount;
    }

    public static void dismissNotice() {
        noticeActive = false;
    }

    public static synchronized void resetTitleCrashStreak() {
        consecutiveTitleCrashes = 0;
    }

    public static boolean consumePendingRunReentry() {
        if (pendingRunReentry) {
            pendingRunReentry = false;
            return true;
        }
        return false;
    }

    /**
     * Attempts to intercept a runtime crash and safely return the player to the Main Menu.
     *
     * @param client       Active Minecraft instance
     * @param report       Existing CrashReport if available, otherwise built from throwable
     * @param throwable    Caught Throwable (may be null if called directly from emergencySaveAndCrash)
     * @param fromOuterRun True when invoked from Minecraft.run()'s outer catch block so run() re-enters
     * @return true if the crash was handled and recovery succeeded; false to let vanilla crash normally
     */
    public static boolean tryHandleCrash(Minecraft client, CrashReport report, Throwable throwable, boolean fromOuterRun) {
        if (!isEnabled() || client == null) {
            return false;
        }

        // Never block intentional JVM shutdown or non-running teardown
        if (!client.isRunning()) {
            return false;
        }

        Throwable root = extractRootThrowable(report, throwable);
        if (root != null && "java.lang.ThreadDeath".equals(root.getClass().getName())) {
            return false;
        }

        synchronized (CrashGuardManager.class) {
            if (recovering) {
                return false;
            }

            long now = System.currentTimeMillis();
            while (!RECENT_CRASH_TIMESTAMPS.isEmpty() && now - RECENT_CRASH_TIMESTAMPS.peekFirst() > LOOP_WINDOW_MS) {
                RECENT_CRASH_TIMESTAMPS.pollFirst();
            }
            RECENT_CRASH_TIMESTAMPS.addLast(now);

            boolean alreadyOnTitleMenu = client.level == null
                    && client.gui != null
                    && client.gui.screen() instanceof TitleScreen;
            if (alreadyOnTitleMenu) {
                consecutiveTitleCrashes++;
            }

            // Circuit breaker: avoid locking the window in an unclosable crash loop on TitleScreen
            if (RECENT_CRASH_TIMESTAMPS.size() >= MAX_CRASHES_IN_WINDOW
                    || consecutiveTitleCrashes >= MAX_CONSECUTIVE_TITLE_CRASHES) {
                StarClient.LOGGER.error(
                        "[Prevent Game Crashes] Detected repeated unrecoverable crash loop (recent={}, titleStreak={}); allowing normal crash exit.",
                        RECENT_CRASH_TIMESTAMPS.size(), consecutiveTitleCrashes, root);
                return false;
            }

            recovering = true;
        }

        try {
            CrashReport finalReport = resolveReport(report, throwable);
            StarClient.LOGGER.error("[Prevent Game Crashes] Intercepted crash and returning player to Main Menu!", root);

            // 1. Always clear delayedCrash immediately so pollTask() does not rethrow during recovery
            clearDelayedCrashQuietly();

            // 2. Release reserved memory early on OutOfMemoryError so report saving & cleanup succeed
            if (isOutOfMemory(root)) {
                try {
                    MemoryReserve.release();
                } catch (Throwable ignored) {
                }
            }

            // 3. Save the full crash report to crash-reports/ without shutting down sound or JVM
            saveCrashReportOnly(client, finalReport);

            // 4. Clean up Star Client overlays & broken render/world state, then return to Main Menu
            resetStarClientStates();
            resetRenderAndInputState(client);
            disconnectAndReturnToTitle(client);

            // 5. Final safety sweep of delayedCrash & matrices
            clearDelayedCrashQuietly();
            try {
                RenderSystem.getModelViewStack().clear();
            } catch (Throwable ignored) {
            }
            if (isOutOfMemory(root)) {
                System.gc();
            }

            // 6. Record notice banner for Main Menu
            preventedCount++;
            lastCrashSummary = formatCrashSummary(finalReport, root);
            noticeActive = true;

            if (fromOuterRun) {
                pendingRunReentry = true;
            }
            return true;
        } catch (Throwable recoveryFailure) {
            StarClient.LOGGER.error("[Prevent Game Crashes] Recovery itself failed; falling back to normal crash.", recoveryFailure);
            return false;
        } finally {
            synchronized (CrashGuardManager.class) {
                recovering = false;
            }
        }
    }

    public static void saveCrashReportOnly(Minecraft client, CrashReport report) {
        if (client == null || report == null) return;
        try {
            CrashReport filled = client.fillReport(report);
            Minecraft.saveReport(client.gameDirectory, filled);
        } catch (Throwable t) {
            try {
                Minecraft.saveReport(client.gameDirectory, report);
            } catch (Throwable ignored) {
            }
        }
    }

    private static CrashReport resolveReport(CrashReport report, Throwable throwable) {
        if (report != null) return report;
        if (throwable instanceof ReportedException re && re.getReport() != null) {
            return re.getReport();
        }
        return new CrashReport("Unexpected error", throwable != null ? throwable : new RuntimeException("Unknown crash"));
    }

    private static Throwable extractRootThrowable(CrashReport report, Throwable throwable) {
        Throwable t = throwable;
        if (t == null && report != null) {
            t = report.getException();
        }
        if (t instanceof ReportedException re && re.getReport() != null && re.getReport().getException() != null) {
            t = re.getReport().getException();
        }
        return t;
    }

    private static boolean isOutOfMemory(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 12) {
            if (cur instanceof OutOfMemoryError) return true;
            cur = cur.getCause();
            depth++;
        }
        return false;
    }

    private static void clearDelayedCrashQuietly() {
        try {
            BlockableEventLoopAccessor.starclient$setDelayedCrash(null);
        } catch (Throwable ignored) {
        }
    }

    private static void resetStarClientStates() {
        try { BaseAlertRenderer.clear(); } catch (Throwable ignored) {}
        try { FireballAlertRenderer.clear(); } catch (Throwable ignored) {}
        try { TacticalPingRenderer.clear(); } catch (Throwable ignored) {}
        try { BedDefenseRenderer.clear(); } catch (Throwable ignored) {}
        try { BedwarsTimersRenderer.reset(); } catch (Throwable ignored) {}
        try { BedwarsUpgradesRenderer.reset(); } catch (Throwable ignored) {}
        try { LootFeedRenderer.reset(); } catch (Throwable ignored) {}
        try { LowBlocksWarningRenderer.reset(); } catch (Throwable ignored) {}
        try { SpawnerOverlayRenderer.clear(); } catch (Throwable ignored) {}
        try { BeaconHelperRenderer.clear(); } catch (Throwable ignored) {}
        try { WorldMapManager.clearRuntimeState(); } catch (Throwable ignored) {}
        try { StorageFinderManager.clearActiveHighlight(); } catch (Throwable ignored) {}
        try { LightOverlayRenderer.clear(); } catch (Throwable ignored) {}
        try { FreeCamState.setInitialized(false); } catch (Throwable ignored) {}
        try { FreeLookState.setInitialized(false); } catch (Throwable ignored) {}
    }

    private static void resetRenderAndInputState(Minecraft client) {
        try {
            RenderSystem.getModelViewStack().clear();
        } catch (Throwable ignored) {
        }
        try {
            RenderSystem.disableScissorForRenderTypeDraws();
        } catch (Throwable ignored) {
        }
        try {
            if (client.gameRenderer != null) {
                if (client.gameRenderer.gameRenderState() != null) {
                    if (client.gameRenderer.gameRenderState().guiRenderState != null) {
                        client.gameRenderer.gameRenderState().guiRenderState.reset();
                    }
                    if (client.gameRenderer.gameRenderState().levelRenderState != null) {
                        client.gameRenderer.gameRenderState().levelRenderState.reset();
                    }
                }
                client.gameRenderer.clearPostEffect();
                client.gameRenderer.resetData();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (client.mouseHandler != null) {
                client.mouseHandler.releaseMouse();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (client.getSoundManager() != null) {
                client.getSoundManager().stop();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (client.gui != null) {
                client.gui.setOverlay(null);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void disconnectAndReturnToTitle(Minecraft client) {
        MinecraftAccessor accessor = (MinecraftAccessor) client;

        // Drop stale tasks queued before the crash so they don't immediately re-trigger it
        try {
            ((BlockableEventLoopAccessor) client).starclient$dropAllTasks();
        } catch (Throwable ignored) {
        }
        clearDelayedCrashQuietly();

        boolean hadWorldOrConnection = client.level != null
                || client.player != null
                || client.getConnection() != null
                || client.getSingleplayerServer() != null
                || accessor.starclient$getPendingConnection() != null;

        if (hadWorldOrConnection) {
            try {
                // Halt singleplayer server gracefully first so disconnect doesn't stall on a crashed server thread
                IntegratedServer sp = client.getSingleplayerServer();
                if (sp != null) {
                    try {
                        sp.halt(false);
                    } catch (Throwable ignored) {
                    }
                }
                client.disconnect(new TitleScreen(), false, false);
                if (client.gui != null) {
                    client.gui.setClientLevelTeardownInProgress(false);
                    if (!(client.gui.screen() instanceof TitleScreen)) {
                        client.gui.setScreen(new TitleScreen());
                    }
                }
                return;
            } catch (Throwable disconnectError) {
                StarClient.LOGGER.warn("[Prevent Game Crashes] Standard disconnect threw; performing hard fallback cleanup.", disconnectError);
            }

            // Hard fallback teardown when the world/connection state is too broken for normal disconnect()
            try {
                ClientPacketListener conn = client.getConnection();
                if (conn != null) {
                    conn.close();
                }
            } catch (Throwable ignored) {
            }
            try {
                Connection pending = accessor.starclient$getPendingConnection();
                if (pending != null) {
                    pending.disconnect(Component.literal("Recovered from client crash"));
                }
            } catch (Throwable ignored) {
            }
            try {
                IntegratedServer sp = client.getSingleplayerServer();
                if (sp != null) {
                    sp.halt(false);
                }
            } catch (Throwable ignored) {
            }
            try {
                accessor.starclient$setSingleplayerServer(null);
                accessor.starclient$setIsLocalServer(false);
                accessor.starclient$setPendingConnection(null);
                accessor.starclient$setPendingReload(null);
            } catch (Throwable ignored) {
            }
            client.level = null;
            client.player = null;
            client.gameMode = null;
            try {
                if (client.gui != null && client.gui.hud != null) {
                    client.gui.hud.onDisconnected();
                }
            } catch (Throwable ignored) {
            }
            try {
                ((BlockableEventLoopAccessor) client).starclient$dropAllTasks();
            } catch (Throwable ignored) {
            }
            clearDelayedCrashQuietly();
        }

        if (client.gui != null) {
            try {
                client.gui.setClientLevelTeardownInProgress(false);
            } catch (Throwable ignored) {
            }
            client.gui.setScreen(new TitleScreen());
        }
    }

    private static String formatCrashSummary(CrashReport report, Throwable root) {
        if (root != null) {
            String type = root.getClass().getSimpleName();
            String msg = root.getMessage();
            if (msg != null && !msg.isBlank()) {
                msg = msg.replace('\n', ' ').replace('\r', ' ').trim();
                if (msg.length() > 64) {
                    msg = msg.substring(0, 61) + "...";
                }
                return type + ": " + msg;
            }
            return type;
        }
        if (report != null && report.getTitle() != null && !report.getTitle().isBlank()) {
            return report.getTitle();
        }
        return "Unexpected error";
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> RuntimeException sneakyThrow(Throwable t) throws E {
        throw (E) t;
    }
}
