package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified glassmorphic slide-in corner toast notification system for Star Client.
 * Supports keybind module toggles, profile switches, friend updates, screenshots,
 * durability warnings, timer alerts, and waypoint events.
 */
public final class ToastNotificationManager {

    private static final long DEFAULT_DURATION_MS = 2400L;
    private static final long ANIM_IN_MS = 200L;
    private static final long ANIM_OUT_MS = 260L;
    private static final int MAX_TOASTS = 5;

    private static final class ToastEntry {
        final String key;
        String title;
        String subtitle;
        String badgeText;
        int accentColor;
        long createdAtMs;
        long durationMs;

        ToastEntry(String key, String title, String subtitle, String badgeText,
                   int accentColor, long createdAtMs, long durationMs) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
            this.badgeText = badgeText;
            this.accentColor = accentColor;
            this.createdAtMs = createdAtMs;
            this.durationMs = durationMs;
        }
    }

    private static final List<ToastEntry> ACTIVE_TOASTS = new ArrayList<>();

    private ToastNotificationManager() {
    }

    public static synchronized void pushModuleToggle(Module module) {
        if (module == null) return;
        StarClient sc = StarClient.getInstance();
        if (sc == null || sc.getClientSettings() == null || !sc.getClientSettings().isKeybindNotifications()) {
            return;
        }

        String name = module.getName();
        boolean state = module.isEnabled();
        String badge = state ? "ON" : "OFF";
        int color = state ? 0xFF34D399 : 0xFFF87171;
        enqueueInternal("mod:" + name, name, null, badge, color, 2200L);
    }

    public static synchronized void pushToast(String key, String title, String subtitle, String badgeText, int accentColor) {
        pushToast(key, title, subtitle, badgeText, accentColor, DEFAULT_DURATION_MS);
    }

    public static synchronized void pushToast(String key, String title, String subtitle, String badgeText,
                                              int accentColor, long durationMs) {
        if (title == null || title.isBlank()) return;
        StarClient sc = StarClient.getInstance();
        if (sc == null || sc.getClientSettings() == null || !sc.getClientSettings().isToastNotifications()) {
            return;
        }
        String safeKey = (key == null || key.isBlank()) ? ("toast:" + title) : key;
        enqueueInternal(safeKey, title, subtitle, badgeText, accentColor | 0xFF000000, Math.max(1200L, durationMs));
    }

    private static void enqueueInternal(String key, String title, String subtitle, String badgeText,
                                        int accentColor, long durationMs) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < ACTIVE_TOASTS.size(); i++) {
            ToastEntry entry = ACTIVE_TOASTS.get(i);
            if (entry.key.equals(key)) {
                entry.title = title;
                entry.subtitle = subtitle;
                entry.badgeText = badgeText;
                entry.accentColor = accentColor;
                entry.durationMs = durationMs;
                entry.createdAtMs = Math.max(entry.createdAtMs, now - ANIM_IN_MS);
                ACTIVE_TOASTS.remove(i);
                ACTIVE_TOASTS.add(entry);
                return;
            }
        }

        if (ACTIVE_TOASTS.size() >= MAX_TOASTS) {
            ACTIVE_TOASTS.removeFirst();
        }
        ACTIVE_TOASTS.add(new ToastEntry(key, title, subtitle, badgeText, accentColor, now, durationMs));
    }

    public static synchronized void render(GuiGraphicsExtractor extractor, Minecraft client, int screenWidth, int screenHeight) {
        if (ACTIVE_TOASTS.isEmpty() || client == null || client.font == null) return;

        StarClient sc = StarClient.getInstance();
        if (sc == null || sc.getClientSettings() == null) {
            ACTIVE_TOASTS.clear();
            return;
        }

        if (client.gui.screen() instanceof ClickGuiScreen || client.gui.screen() instanceof HudEditorScreen) {
            return;
        }

        long now = System.currentTimeMillis();
        ACTIVE_TOASTS.removeIf(e -> now - e.createdAtMs >= e.durationMs);
        if (ACTIVE_TOASTS.isEmpty()) return;

        ClientSettings cs = sc.getClientSettings();
        int baseBg = 0xE6000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
        int borderBase = cs.getMenuBorderColor();
        boolean shadow = cs.isHudTextShadow();

        int gap = 5;
        int rightMargin = 10;
        int bottomY = screenHeight - 12;

        for (int i = ACTIVE_TOASTS.size() - 1; i >= 0; i--) {
            ToastEntry toast = ACTIVE_TOASTS.get(i);
            long age = Math.max(0L, now - toast.createdAtMs);
            long dur = toast.durationMs;

            float slideProgress;
            if (age < ANIM_IN_MS) {
                float t = (float) age / ANIM_IN_MS;
                slideProgress = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
            } else if (age > dur - ANIM_OUT_MS) {
                float t = (float) (dur - age) / ANIM_OUT_MS;
                slideProgress = Math.clamp(t * t, 0.0f, 1.0f);
            } else {
                slideProgress = 1.0f;
            }

            boolean hasSubtitle = toast.subtitle != null && !toast.subtitle.isBlank();
            boolean hasBadge = toast.badgeText != null && !toast.badgeText.isBlank();
            int cardH = hasSubtitle ? 34 : 26;

            int accentColor = toast.accentColor | 0xFF000000;
            int badgeBg = (accentColor & 0x00FFFFFF) | 0x33000000;
            int badgeW = hasBadge ? (client.font.width(toast.badgeText) + 10) : 0;

            int titleW = client.font.width(toast.title);
            int subW = hasSubtitle ? client.font.width(toast.subtitle) : 0;
            int textW = Math.max(titleW, subW);
            int cardW = Math.clamp(16 + textW + (hasBadge ? (10 + badgeW) : 0) + 8, 128, 250);

            int targetX = screenWidth - cardW - rightMargin;
            int offscreenX = screenWidth + 8;
            int x = Math.round(offscreenX + (targetX - offscreenX) * slideProgress);
            int y = bottomY - cardH;

            GuiRenderUtils.drawDropShadow(extractor, x, y, cardW, cardH, 6, 4, (int) (100 * slideProgress));
            GuiRenderUtils.fillRoundedRect(extractor, x, y, cardW, cardH, 6, baseBg);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, cardW, cardH, 6, borderBase);

            // Left status stripe
            GuiRenderUtils.fillRoundedRect(extractor, x + 3, y + 4, 3, cardH - 8, 1, accentColor);

            int maxTextW = cardW - 18 - (hasBadge ? (badgeW + 8) : 0);
            String clippedTitle = GuiRenderUtils.truncate(client.font, toast.title, maxTextW);
            if (hasSubtitle) {
                extractor.text(client.font, clippedTitle, x + 11, y + 5, 0xFFF8FAFC, shadow);
                String clippedSub = GuiRenderUtils.truncate(client.font, toast.subtitle, maxTextW);
                extractor.text(client.font, clippedSub, x + 11, y + 16, 0xFF94A3B8, shadow);
            } else {
                extractor.text(client.font, clippedTitle, x + 11, y + 7, 0xFFF8FAFC, shadow);
            }

            if (hasBadge) {
                int bx = x + cardW - badgeW - 6;
                int by = y + (cardH - 15) / 2;
                GuiRenderUtils.fillRoundedRect(extractor, bx, by, badgeW, 13, 3, badgeBg);
                GuiRenderUtils.drawRoundedOutline(extractor, bx, by, badgeW, 13, 3, (accentColor & 0x00FFFFFF) | 0x66000000);
                extractor.text(client.font, toast.badgeText, bx + 5, by + 3, accentColor, shadow);
            }

            // Remaining duration thin progress bar along bottom
            float remainingRatio = Math.clamp(1.0f - (float) age / dur, 0.0f, 1.0f);
            int progMaxW = cardW - 12;
            int progW = Math.round(progMaxW * remainingRatio);
            if (progW > 1) {
                extractor.fill(x + 6, y + cardH - 3, x + 6 + progW, y + cardH - 2, (accentColor & 0x00FFFFFF) | 0xAA000000);
            }

            bottomY -= (cardH + gap);
        }
    }
}
