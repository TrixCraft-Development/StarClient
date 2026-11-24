package net.starclient.module;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.client.Minecraft;
import net.starclient.StarClient;
import net.starclient.gui.ToastNotificationManager;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.Setting;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Windows media detection, real-time playback timeline tracking, and media key
 * controller for the "Now Playing" Media HUD module.
 * Uses a single lightweight Windows SMTC (GlobalSystemMediaTransportControls) PowerShell worker
 * with an instant JNA Win32 window-title fallback, plus JNA {@code keybd_event} media controls.
 */
public final class MediaHudState {

    private static final int VK_MEDIA_NEXT_TRACK = 0xB0;
    private static final int VK_MEDIA_PREV_TRACK = 0xB1;
    private static final int VK_MEDIA_PLAY_PAUSE = 0xB3;
    private static final int KEYEVENTF_KEYUP = 0x0002;

    private interface WinUser32 extends StdCallLibrary {
        WinUser32 INSTANCE = Native.load("user32", WinUser32.class);

        interface WNDENUMPROC extends StdCallLibrary.StdCallCallback {
            boolean callback(Pointer hWnd, Pointer arg);
        }

        boolean EnumWindows(WNDENUMPROC lpEnumFunc, Pointer arg);

        boolean IsWindowVisible(Pointer hWnd);

        int GetWindowTextLengthW(Pointer hWnd);

        int GetWindowTextW(Pointer hWnd, char[] lpString, int nMaxCount);

        void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);
    }

    private static final String SMTC_WORKER_PS = String.join("\n",
            "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8",
            "try {",
            "  [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null",
            "  Add-Type -AssemblyName System.Runtime.WindowsRuntime",
            "  $asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } | Select-Object -First 1",
            "  function AwaitOp($op, $t) {",
            "    try {",
            "      $task = $asTask.MakeGenericMethod($t).Invoke($null, @($op))",
            "      if ($task.Wait(1500)) { return $task.Result }",
            "    } catch {}",
            "    return $null",
            "  }",
            "  $mgr = AwaitOp ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])",
            "  if (-not $mgr) { Write-Output 'STARMEDIA|UNAVAILABLE'; [Console]::Out.Flush(); exit 0 }",
            "  while ($true) {",
            "    try {",
            "      $s = $mgr.GetCurrentSession()",
            "      if ($s) {",
            "        $p = AwaitOp ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])",
            "        $info = $s.GetPlaybackInfo()",
            "        $st = if ($info) { [string]$info.PlaybackStatus } else { 'Unknown' }",
            "        $t = if ($p -and $p.Title) { ($p.Title -replace '[\\r\\n|]', ' ').Trim() } else { '' }",
            "        $a = if ($p -and $p.Artist) { ($p.Artist -replace '[\\r\\n|]', ' ').Trim() } else { '' }",
            "        $app = if ($s.SourceAppUserModelId) { ($s.SourceAppUserModelId -replace '[\\r\\n|]', ' ').Trim() } else { '' }",
            "        $posMs = -1",
            "        $durMs = 0",
            "        try {",
            "          $tl = $s.GetTimelineProperties()",
            "          if ($tl) {",
            "            $dur = [long]($tl.EndTime - $tl.StartTime).TotalMilliseconds",
            "            $pos = [long]($tl.Position - $tl.StartTime).TotalMilliseconds",
            "            if ($dur -gt 0) {",
            "              if ($st -eq 'Playing' -and $tl.LastUpdatedTime.Year -gt 2000) {",
            "                $dt = ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalMilliseconds",
            "                if ($dt -gt 0 -and $dt -lt 86400000) { $pos = [long]($pos + $dt) }",
            "              }",
            "              if ($pos -lt 0) { $pos = 0 }",
            "              if ($pos -gt $dur) { $pos = $dur }",
            "              $posMs = $pos",
            "              $durMs = $dur",
            "            }",
            "          }",
            "        } catch {}",
            "        Write-Output ('STARMEDIA|' + $st + '|' + $t + '|' + $a + '|' + $app + '|' + $posMs + '|' + $durMs)",
            "      } else {",
            "        Write-Output 'STARMEDIA|NONE'",
            "      }",
            "    } catch {",
            "      Write-Output 'STARMEDIA|NONE'",
            "    }",
            "    [Console]::Out.Flush()",
            "    Start-Sleep -Milliseconds 1000",
            "  }",
            "} catch {",
            "  Write-Output 'STARMEDIA|UNAVAILABLE'",
            "  [Console]::Out.Flush()",
            "}"
    );

    private static Module module;
    private static final AtomicBoolean workerRunning = new AtomicBoolean(false);
    private static volatile Thread workerThread;
    private static volatile Process smtcProcess;

    private static volatile boolean hasMedia = false;
    private static volatile boolean playing = false;
    private static volatile String title = "";
    private static volatile String artist = "";
    private static volatile String sourceApp = "";
    private static volatile long basePositionMs = -1L;
    private static volatile long durationMs = 0L;
    private static volatile long positionAnchorMs = 0L;

    private static volatile String actionFeedback = "";
    private static volatile long actionFeedbackUntilMs = 0L;

    // Hotkey edge-detection state
    private static boolean prevPlayKeyDown = false;
    private static boolean prevNextKeyDown = false;
    private static boolean prevBackKeyDown = false;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(MediaHudState::stopWorker, "StarClient-MediaHud-Shutdown"));
    }

    private MediaHudState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static boolean hasMedia() {
        return hasMedia && title != null && !title.isEmpty();
    }

    public static boolean isPlaying() {
        return playing;
    }

    public static String getTitle() {
        return title != null ? title : "";
    }

    public static String getArtist() {
        return artist != null ? artist : "";
    }

    public static String getSourceApp() {
        return sourceApp != null ? sourceApp : "";
    }

    public static boolean hasDuration() {
        return durationMs > 0L && basePositionMs >= 0L;
    }

    public static long getDurationMs() {
        return Math.max(0L, durationMs);
    }

    /**
     * Returns the smoothly interpolated current playback position in milliseconds,
     * ticking forward in real time while playing and freezing cleanly while paused.
     */
    public static long getCurrentPositionMs() {
        long dur = durationMs;
        long base = basePositionMs;
        if (dur <= 0L || base < 0L) {
            return -1L;
        }
        if (!playing) {
            return Math.clamp(base, 0L, dur);
        }
        long elapsed = Math.max(0L, System.currentTimeMillis() - positionAnchorMs);
        return Math.clamp(base + elapsed, 0L, dur);
    }

    public static float getProgress() {
        long dur = durationMs;
        if (dur <= 0L) return 0.0F;
        long pos = getCurrentPositionMs();
        if (pos < 0L) return 0.0F;
        return Math.clamp((float) pos / (float) dur, 0.0F, 1.0F);
    }

    public static String formatDuration(long ms) {
        if (ms < 0L) return "--:--";
        long totalSec = ms / 1000L;
        long hours = totalSec / 3600L;
        long mins = (totalSec % 3600L) / 60L;
        long secs = totalSec % 60L;
        if (hours > 0L) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, mins, secs);
        }
        return String.format(Locale.ROOT, "%d:%02d", mins, secs);
    }

    public static String getActiveActionFeedback() {
        if (System.currentTimeMillis() < actionFeedbackUntilMs) {
            return actionFeedback;
        }
        return "";
    }

    public static void onTick(Minecraft client) {
        if (!isEnabled()) {
            if (workerRunning.get()) {
                stopWorker();
            }
            prevPlayKeyDown = false;
            prevNextKeyDown = false;
            prevBackKeyDown = false;
            return;
        }

        ensureWorkerStarted();

        // Optional in-game media shortcut keys: Alt+P (Play/Pause), Alt+Right (Next), Alt+Left (Previous)
        if (client != null && client.getWindow() != null && client.gui != null && client.gui.screen() == null
                && isMediaHotkeysEnabled()) {
            long handle = client.getWindow().handle();
            boolean altDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;

            boolean playDown = altDown && GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_P) == GLFW.GLFW_PRESS;
            boolean nextDown = altDown && GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS;
            boolean backDown = altDown && GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS;

            if (playDown && !prevPlayKeyDown) {
                playPause();
            }
            if (nextDown && !prevNextKeyDown) {
                nextTrack();
            }
            if (backDown && !prevBackKeyDown) {
                prevTrack();
            }

            prevPlayKeyDown = playDown;
            prevNextKeyDown = nextDown;
            prevBackKeyDown = backDown;
        } else {
            prevPlayKeyDown = false;
            prevNextKeyDown = false;
            prevBackKeyDown = false;
        }
    }

    private static boolean isMediaHotkeysEnabled() {
        if (module == null) return true;
        Setting<?> s = module.getSetting("Media Hotkeys");
        return !(s instanceof BooleanSetting bs) || bs.getValue();
    }

    public static void playPause() {
        sendMediaKey(VK_MEDIA_PLAY_PAUSE);
        // Freeze or re-anchor timeline cleanly at the exact pause/resume moment
        long currentPos = getCurrentPositionMs();
        if (currentPos >= 0L) {
            basePositionMs = currentPos;
            positionAnchorMs = System.currentTimeMillis();
        }
        boolean nextPlay = !playing;
        playing = nextPlay;
        setFeedback(nextPlay ? "▶ Resumed" : "⏸ Paused");
        ToastNotificationManager.pushToast("media_ctrl", "Media Control",
                nextPlay ? "Play / Resume" : "Paused", "MEDIA", 0xFF38BDF8);
    }

    public static void nextTrack() {
        sendMediaKey(VK_MEDIA_NEXT_TRACK);
        setFeedback("⏭ Next Track");
        ToastNotificationManager.pushToast("media_ctrl", "Media Control",
                "Skipped to Next Track", "MEDIA", 0xFF38BDF8);
    }

    public static void prevTrack() {
        sendMediaKey(VK_MEDIA_PREV_TRACK);
        setFeedback("⏮ Previous Track");
        ToastNotificationManager.pushToast("media_ctrl", "Media Control",
                "Previous Track", "MEDIA", 0xFF38BDF8);
    }

    private static void setFeedback(String text) {
        actionFeedback = text;
        actionFeedbackUntilMs = System.currentTimeMillis() + 2000L;
    }

    private static void sendMediaKey(int vk) {
        if (!isWindows()) return;
        try {
            WinUser32 u32 = WinUser32.INSTANCE;
            u32.keybd_event((byte) vk, (byte) 0, 0, 0);
            u32.keybd_event((byte) vk, (byte) 0, KEYEVENTF_KEYUP, 0);
        } catch (Throwable t) {
            StarClient.LOGGER.debug("Failed to dispatch Win32 media key", t);
        }
    }

    private static void ensureWorkerStarted() {
        if (!isWindows()) return;
        if (!workerRunning.compareAndSet(false, true)) return;

        Thread t = new Thread(MediaHudState::workerLoop, "StarClient-MediaHud-Poller");
        t.setDaemon(true);
        workerThread = t;
        t.start();
    }

    public static void stopWorker() {
        workerRunning.set(false);
        Process p = smtcProcess;
        smtcProcess = null;
        if (p != null) {
            try {
                p.destroyForcibly();
            } catch (Throwable ignored) {
            }
        }
        Thread t = workerThread;
        workerThread = null;
        if (t != null) {
            t.interrupt();
        }
    }

    private static void workerLoop() {
        boolean smtcSupported = true;
        String encodedScript = Base64.getEncoder().encodeToString(
                SMTC_WORKER_PS.getBytes(StandardCharsets.UTF_16LE));

        while (workerRunning.get()) {
            if (smtcSupported) {
                Process proc = null;
                try {
                    ProcessBuilder pb = new ProcessBuilder(
                            "powershell.exe",
                            "-NoProfile",
                            "-NonInteractive",
                            "-WindowStyle", "Hidden",
                            "-EncodedCommand", encodedScript
                    );
                    pb.redirectErrorStream(true);
                    proc = pb.start();
                    smtcProcess = proc;

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while (workerRunning.get() && (line = reader.readLine()) != null) {
                            if (!line.startsWith("STARMEDIA|")) continue;
                            String[] parts = line.split("\\|", -1);
                            if (parts.length >= 2) {
                                String status = parts[1];
                                if ("UNAVAILABLE".equalsIgnoreCase(status)) {
                                    smtcSupported = false;
                                    break;
                                }
                                if ("NONE".equalsIgnoreCase(status)) {
                                    if (!pollWin32WindowFallback()) {
                                        clearMedia();
                                    }
                                    continue;
                                }
                                String rawTitle = parts.length > 2 ? parts[2].trim() : "";
                                String rawArtist = parts.length > 3 ? parts[3].trim() : "";
                                String rawApp = parts.length > 4 ? normalizeAppName(parts[4]) : "";
                                long rawPosMs = parts.length > 5 ? parseLongSafe(parts[5], -1L) : -1L;
                                long rawDurMs = parts.length > 6 ? parseLongSafe(parts[6], 0L) : 0L;

                                if (!rawTitle.isEmpty()) {
                                    boolean nowPlaying = "Playing".equalsIgnoreCase(status);
                                    boolean sameTrack = rawTitle.equals(title) && rawArtist.equals(artist);
                                    boolean stateChanged = (nowPlaying != playing);
                                    long nowMs = System.currentTimeMillis();

                                    title = rawTitle;
                                    artist = rawArtist;
                                    sourceApp = rawApp;
                                    playing = nowPlaying;
                                    hasMedia = true;

                                    if (rawDurMs > 0L && rawPosMs >= 0L) {
                                        long curInterp = getCurrentPositionMs();
                                        boolean needReanchor = !sameTrack
                                                || stateChanged
                                                || durationMs != rawDurMs
                                                || curInterp < 0L
                                                || !nowPlaying
                                                || Math.abs(rawPosMs - curInterp) > 1500L;
                                        durationMs = rawDurMs;
                                        if (needReanchor) {
                                            basePositionMs = Math.clamp(rawPosMs, 0L, rawDurMs);
                                            positionAnchorMs = nowMs;
                                        }
                                    } else {
                                        durationMs = 0L;
                                        basePositionMs = -1L;
                                    }
                                } else if (!pollWin32WindowFallback()) {
                                    clearMedia();
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    smtcSupported = false;
                } finally {
                    if (proc != null) {
                        try {
                            proc.destroyForcibly();
                        } catch (Throwable ignored) {
                        }
                    }
                    smtcProcess = null;
                }
            }

            if (!workerRunning.get()) break;

            // Instant Win32 window-title fallback loop when SMTC isn't available
            if (!pollWin32WindowFallback()) {
                clearMedia();
            }
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private static boolean pollWin32WindowFallback() {
        if (!isWindows()) return false;
        try {
            final String[] found = new String[3]; // [title, artist, app]
            WinUser32 u32 = WinUser32.INSTANCE;
            u32.EnumWindows((hWnd, arg) -> {
                if (!u32.IsWindowVisible(hWnd)) return true;
                int len = u32.GetWindowTextLengthW(hWnd);
                if (len <= 2 || len > 256) return true;
                char[] buf = new char[len + 1];
                u32.GetWindowTextW(hWnd, buf, buf.length);
                String text = Native.toString(buf).trim();
                if (text.isEmpty()) return true;

                if (matchSuffix(text, " - YouTube Music", "YouTube Music", found)) return false;
                if (matchSuffix(text, " - Spotify", "Spotify", found)) return false;
                if (matchSuffix(text, " - Apple Music", "Apple Music", found)) return false;
                if (matchSuffix(text, " - SoundCloud", "SoundCloud", found)) return false;
                if (matchSuffix(text, " - VLC media player", "VLC", found)) return false;
                if (matchSuffix(text, " - YouTube", "YouTube", found)) return false;
                return true;
            }, null);

            if (found[0] != null && !found[0].isEmpty()) {
                title = found[0];
                artist = found[1] != null ? found[1] : "";
                sourceApp = found[2] != null ? found[2] : "";
                playing = true;
                hasMedia = true;
                durationMs = 0L;
                basePositionMs = -1L;
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean matchSuffix(String windowTitle, String suffix, String appName, String[] out) {
        String clean = windowTitle
                .replaceAll("\\s*-\\s*(Google Chrome|Microsoft\\s*Edge|Mozilla Firefox|Brave|Opera(\\s*GX)?|Vivaldi)$", "")
                .trim();
        int idx = clean.lastIndexOf(suffix);
        if (idx <= 0) return false;
        String core = clean.substring(0, idx).trim();
        if (core.isEmpty() || core.equalsIgnoreCase("Home") || core.equalsIgnoreCase("Search")) return false;

        int dash = core.indexOf(" - ");
        if (dash > 0 && dash + 3 < core.length()) {
            out[1] = core.substring(0, dash).trim();
            out[0] = core.substring(dash + 3).trim();
        } else {
            out[0] = core;
            out[1] = "";
        }
        out[2] = appName;
        return true;
    }

    private static void clearMedia() {
        hasMedia = false;
        playing = false;
        title = "";
        artist = "";
        sourceApp = "";
        durationMs = 0L;
        basePositionMs = -1L;
    }

    private static long parseLongSafe(String s, long fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String normalizeAppName(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String low = raw.toLowerCase(Locale.ROOT);
        if (low.contains("spotify")) return "Spotify";
        if (low.contains("applemusic") || low.contains("itunes")) return "Apple Music";
        if (low.contains("chrome")) return "Chrome";
        if (low.contains("msedge") || low.contains("edge")) return "Edge";
        if (low.contains("firefox")) return "Firefox";
        if (low.contains("brave")) return "Brave";
        if (low.contains("opera")) return "Opera";
        if (low.contains("vlc")) return "VLC";
        if (low.contains("tidal")) return "Tidal";
        if (low.contains("deezer")) return "Deezer";
        if (low.contains("zune") || low.contains("mediaplayer")) return "Media Player";
        int bang = raw.indexOf('!');
        String trimmed = bang > 0 ? raw.substring(0, bang) : raw;
        if (trimmed.endsWith(".exe")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        }
        int dot = trimmed.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < trimmed.length()) {
            trimmed = trimmed.substring(dot + 1);
        }
        return trimmed.length() > 16 ? trimmed.substring(0, 16) : trimmed;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
