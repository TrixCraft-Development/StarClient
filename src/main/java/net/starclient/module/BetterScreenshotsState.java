package net.starclient.module;

import com.mojang.blaze3d.platform.NativeImage;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;
import net.starclient.StarClient;
import net.starclient.gui.ToastNotificationManager;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * State & Win32 CF_DIB clipboard + async save pipeline for the "Screenshots" PLAYER module.
 */
public final class BetterScreenshotsState {

    public static final String COPY_ACTION_PREFIX = "starclient:copy_screenshot:";

    private static final DateTimeFormatter FILE_NAME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

    private static Module module;

    private interface WinUser32 extends StdCallLibrary {
        WinUser32 INSTANCE = Native.load("user32", WinUser32.class);

        boolean OpenClipboard(Pointer hWndNewOwner);

        boolean EmptyClipboard();

        Pointer SetClipboardData(int uFormat, Pointer hMem);

        boolean CloseClipboard();
    }

    private interface WinKernel32 extends StdCallLibrary {
        WinKernel32 INSTANCE = Native.load("kernel32", WinKernel32.class);

        Pointer GlobalAlloc(int uFlags, long dwBytes);

        Pointer GlobalLock(Pointer hMem);

        boolean GlobalUnlock(Pointer hMem);

        Pointer GlobalFree(Pointer hMem);
    }

    private static final int CF_DIB = 8;
    private static final int GHND = 0x0042; // GMEM_MOVEABLE | GMEM_ZEROINIT

    private BetterScreenshotsState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static boolean isAutoCopy() {
        return isEnabled() && getBool("Auto Copy to Clipboard");
    }

    public static boolean isActionButtons() {
        return isEnabled() && getBool("Chat Action Buttons");
    }

    public record TargetDimensions(int width, int height, String label) {
    }

    public static TargetDimensions getTargetDimensions(int srcWidth, int srcHeight) {
        if (module == null || !isEnabled()) {
            return new TargetDimensions(srcWidth, srcHeight, "F2");
        }

        Setting<?> resSetting = module.getSetting("Resolution");
        String mode = resSetting instanceof ModeSetting ms ? ms.getValue() : "Native";
        if ("Native".equalsIgnoreCase(mode)) {
            return new TargetDimensions(srcWidth, srcHeight, "F2");
        }

        boolean keepAspect = true;
        Setting<?> aspectSetting = module.getSetting("Keep Aspect Ratio");
        if (aspectSetting instanceof BooleanSetting bs) {
            keepAspect = bs.getValue();
        }

        int baseW;
        int baseH;
        String badge = mode;

        switch (mode) {
            case "720p" -> {
                baseW = 1280;
                baseH = 720;
            }
            case "1080p" -> {
                baseW = 1920;
                baseH = 1080;
            }
            case "1440p" -> {
                baseW = 2560;
                baseH = 1440;
            }
            case "4K" -> {
                baseW = 3840;
                baseH = 2160;
            }
            case "8K" -> {
                baseW = 7680;
                baseH = 4320;
            }
            case "Custom" -> {
                Setting<?> cw = module.getSetting("Custom Width");
                Setting<?> ch = module.getSetting("Custom Height");
                baseW = cw instanceof NumberSetting ns ? (int) Math.round(ns.getValue()) : 1920;
                baseH = ch instanceof NumberSetting ns ? (int) Math.round(ns.getValue()) : 1080;
                badge = baseW + "×" + baseH;
            }
            default -> {
                return new TargetDimensions(srcWidth, srcHeight, "F2");
            }
        }

        if (!keepAspect) {
            int w = Math.max(2, baseW);
            int h = Math.max(2, baseH);
            if ((w & 1) != 0) w++;
            if ((h & 1) != 0) h++;
            return new TargetDimensions(w, h, badge);
        }

        // Calculate aspect ratio preserving screen perspective
        double aspect = srcWidth / (double) Math.max(1, srcHeight);
        int finalH = Math.max(2, baseH);
        int finalW = (int) Math.round(finalH * aspect);
        if ((finalW & 1) != 0) finalW++;
        if ((finalH & 1) != 0) finalH++;

        return new TargetDimensions(finalW, finalH, badge);
    }

    public static void handleCapturedScreenshot(File gameDir, String customName,
                                                NativeImage image, Consumer<Component> callback) {
        File screenshotsDir = new File(gameDir, "screenshots");
        //noinspection ResultOfMethodCallIgnored
        screenshotsDir.mkdirs();

        File targetFile = (customName == null)
                ? nextUniqueScreenshotFile(screenshotsDir)
                : new File(screenshotsDir, customName);

        Util.ioPool().execute(() -> {
            try (NativeImage raw = image) {
                int srcW = raw.getWidth();
                int srcH = raw.getHeight();
                TargetDimensions dims = getTargetDimensions(srcW, srcH);

                NativeImage finalImg = raw;
                boolean isScaled = (dims.width() != srcW || dims.height() != srcH);
                if (isScaled) {
                    try {
                        NativeImage scaled = new NativeImage(raw.format(), dims.width(), dims.height(), false);
                        raw.resizeSubRectTo(0, 0, srcW, srcH, scaled);
                        finalImg = scaled;
                    } catch (Throwable t) {
                        StarClient.LOGGER.warn("Failed to scale screenshot to " + dims.width() + "x" + dims.height() + ", saving native", t);
                        finalImg = raw;
                        isScaled = false;
                    }
                }

                try {
                    boolean copied = false;
                    if (isAutoCopy()) {
                        copied = copyNativeImageToClipboard(finalImg);
                    }
                    finalImg.writeToFile(targetFile);

                    String badge = isScaled ? dims.label() : "F2";
                    String resInfo = finalImg.getWidth() + "×" + finalImg.getHeight();
                    String subtitle = targetFile.getName() + " (" + resInfo + ")";

                    ToastNotificationManager.pushToast(
                            "screenshot_capture",
                            copied ? "Screenshot Copied & Saved" : "Screenshot Saved",
                            subtitle,
                            copied ? "COPIED" : badge,
                            copied ? 0xFF34D399 : 0xFF38BDF8
                    );
                    Component chatMsg = buildChatNotification(targetFile, copied, finalImg.getWidth(), finalImg.getHeight(), isScaled ? dims.label() : null);
                    callback.accept(chatMsg);
                } finally {
                    if (finalImg != raw) {
                        finalImg.close();
                    }
                }
            } catch (Exception e) {
                StarClient.LOGGER.warn("Failed to save screenshot", e);
                callback.accept(Component.translatable("screenshot.failure", e.getMessage()));
            }
        });
    }

    public static void handleCopyActionClick(String payload, Minecraft client) {
        if (payload == null || !payload.startsWith(COPY_ACTION_PREFIX)) return;
        String pathStr = payload.substring(COPY_ACTION_PREFIX.length());
        File file = new File(pathStr);
        if (!file.isFile()) {
            if (client != null) {
                client.gui.hud.getChat().addClientSystemMessage(
                        Component.literal("⚠ Screenshot file not found: " + file.getName())
                                .withStyle(ChatFormatting.RED));
            }
            return;
        }
        Util.ioPool().execute(() -> {
            boolean ok = false;
            try (InputStream in = Files.newInputStream(file.toPath());
                 NativeImage img = NativeImage.read(in)) {
                ok = copyNativeImageToClipboard(img);
            } catch (Throwable t) {
                StarClient.LOGGER.warn("Failed to copy screenshot file to clipboard", t);
            }
            final boolean success = ok;
            if (client != null) {
                client.execute(() -> {
                    Component feedback = success
                            ? Component.literal("📸 Copied screenshot ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(file.getName()).withStyle(ChatFormatting.AQUA))
                            .append(Component.literal(" to clipboard!").withStyle(ChatFormatting.GREEN))
                            : Component.literal("⚠ Could not copy screenshot to clipboard.")
                            .withStyle(ChatFormatting.RED);
                    if (success) {
                        ToastNotificationManager.pushToast(
                                "screenshot_copy",
                                "Screenshot Copied",
                                file.getName(),
                                "CLIPBOARD",
                                0xFF34D399
                        );
                    }
                    client.gui.hud.getChat().addClientSystemMessage(feedback);
                });
            }
        });
    }

    private static Component buildChatNotification(File targetFile, boolean autoCopied, int width, int height, String presetLabel) {
        File absFile = targetFile.getAbsoluteFile();
        File parentDir = absFile.getParentFile() != null ? absFile.getParentFile() : absFile;

        MutableComponent fileLink = Component.literal(targetFile.getName())
                .withStyle(Style.EMPTY
                        .withUnderlined(true)
                        .withColor(ChatFormatting.WHITE)
                        .withClickEvent(new ClickEvent.OpenFile(absFile))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("Click to open screenshot (" + width + "×" + height + ")").withStyle(ChatFormatting.GRAY))));

        MutableComponent root = Component.translatable("screenshot.success", fileLink);

        // Resolution badge
        String resText = (presetLabel != null && !presetLabel.isEmpty())
                ? " [" + presetLabel + " • " + width + "×" + height + "]"
                : " [" + width + "×" + height + "]";
        root.append(Component.literal(resText).withStyle(ChatFormatting.DARK_GRAY));

        if (autoCopied) {
            root.append(Component.literal(" (Copied)").withStyle(ChatFormatting.GREEN));
        }

        if (isActionButtons()) {
            MutableComponent openBtn = Component.literal(" [Open]")
                    .withStyle(Style.EMPTY
                            .withColor(0x38BDF8)
                            .withClickEvent(new ClickEvent.OpenFile(absFile))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("Open image in viewer").withStyle(ChatFormatting.AQUA))));

            MutableComponent folderBtn = Component.literal(" [Folder]")
                    .withStyle(Style.EMPTY
                            .withColor(0xFACC15)
                            .withClickEvent(new ClickEvent.OpenFile(parentDir))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("Open screenshots folder").withStyle(ChatFormatting.YELLOW))));

            MutableComponent copyBtn = Component.literal(" [Copy]")
                    .withStyle(Style.EMPTY
                            .withColor(0x34D399)
                            .withClickEvent(new ClickEvent.CopyToClipboard(COPY_ACTION_PREFIX + absFile.getPath()))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal("Copy image to clipboard").withStyle(ChatFormatting.GREEN))));

            root.append(openBtn).append(folderBtn).append(copyBtn);
        }
        return root;
    }

    /**
     * Copies a {@link NativeImage} directly to the Windows Clipboard as a 32-bit BGRA {@code CF_DIB}
     * bitmap without touching AWT (avoiding {@code HeadlessException}).
     */
    public static synchronized boolean copyNativeImageToClipboard(NativeImage image) {
        if (image == null || image.isClosed()) return false;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            return false;
        }

        int w = image.getWidth();
        int h = image.getHeight();
        if (w <= 0 || h <= 0) return false;

        long pixelBytes = (long) w * (long) h * 4L;
        long totalBytes = 40L + pixelBytes;

        Pointer hGlobal = null;
        boolean ownershipTransferred = false;
        try {
            WinKernel32 k32 = WinKernel32.INSTANCE;
            WinUser32 u32 = WinUser32.INSTANCE;

            hGlobal = k32.GlobalAlloc(GHND, totalBytes);
            if (hGlobal == null) return false;

            Pointer locked = k32.GlobalLock(hGlobal);
            if (locked == null) return false;
            try {
                // BITMAPINFOHEADER (40 bytes)
                locked.setInt(0, 40);               // biSize
                locked.setInt(4, w);                // biWidth
                locked.setInt(8, h);                // biHeight (positive = bottom-up DIB)
                locked.setShort(12, (short) 1);     // biPlanes
                locked.setShort(14, (short) 32);    // biBitCount (32-bit BGRA)
                locked.setInt(16, 0);               // biCompression = BI_RGB
                locked.setInt(20, (int) pixelBytes);// biSizeImage
                locked.setInt(24, 0);               // biXPelsPerMeter
                locked.setInt(28, 0);               // biYPelsPerMeter
                locked.setInt(32, 0);               // biClrUsed
                locked.setInt(36, 0);               // biClrImportant

                int[] rowBuf = new int[w];
                long offset = 40L;
                for (int y = h - 1; y >= 0; y--) {
                    for (int x = 0; x < w; x++) {
                        // NativeImage.getPixel returns 0xAARRGGBB, which in little-endian memory
                        // is [BB, GG, RR, AA] — exact 32-bit Win32 DIB BGRA byte order!
                        rowBuf[x] = image.getPixel(x, y) | 0xFF000000;
                    }
                    locked.write(offset, rowBuf, 0, w);
                    offset += (long) w * 4L;
                }
            } finally {
                k32.GlobalUnlock(hGlobal);
            }

            // Retry briefly in case another app briefly holds the clipboard lock
            boolean opened = false;
            for (int attempt = 0; attempt < 5; attempt++) {
                if (u32.OpenClipboard(null)) {
                    opened = true;
                    break;
                }
                try {
                    Thread.sleep(12L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (!opened) return false;

            try {
                u32.EmptyClipboard();
                Pointer res = u32.SetClipboardData(CF_DIB, hGlobal);
                if (res != null) {
                    ownershipTransferred = true;
                    return true;
                }
            } finally {
                u32.CloseClipboard();
            }
        } catch (Throwable t) {
            StarClient.LOGGER.warn("Win32 clipboard screenshot copy failed", t);
        } finally {
            if (!ownershipTransferred && hGlobal != null) {
                try {
                    WinKernel32.INSTANCE.GlobalFree(hGlobal);
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    private static File nextUniqueScreenshotFile(File dir) {
        String base = LocalDateTime.now().format(FILE_NAME_FMT);
        int index = 1;
        while (true) {
            String name = base + (index == 1 ? "" : "_" + index) + ".png";
            File candidate = new File(dir, name);
            if (!candidate.exists()) {
                return candidate;
            }
            index++;
        }
    }

    private static boolean getBool(String name) {
        if (module == null) return true;
        Setting<?> s = module.getSetting(name);
        return s == null || !(s.getValue() instanceof Boolean b) || b;
    }
}
