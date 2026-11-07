package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Renders sleek glassmorphic slide-in corner notifications exclusively when
 * modules are toggled via keybinds (when enabled in ClientSettings).
 */
public final class ToastNotificationManager {

    private static final long DURATION_MS = 2200L;
    private static final long ANIM_IN_MS = 200L;
    private static final long ANIM_OUT_MS = 260L;
    private static final int MAX_TOASTS = 5;

    private static final class ToastEntry {
        final String moduleName;
        boolean enabled;
        long createdAtMs;

        ToastEntry(String moduleName, boolean enabled, long createdAtMs) {
            this.moduleName = moduleName;
            this.enabled = enabled;
            this.createdAtMs = createdAtMs;
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

        long now = System.currentTimeMillis();
        String name = module.getName();
        boolean state = module.isEnabled();

        // Update existing toast for the same module if already visible so rapid toggles stay clean
        for (int i = 0; i < ACTIVE_TOASTS.size(); i++) {
            ToastEntry entry = ACTIVE_TOASTS.get(i);
            if (entry.moduleName.equals(name)) {
                entry.enabled = state;
                entry.createdAtMs = Math.max(entry.createdAtMs, now - ANIM_IN_MS);
                // Move to newest slot
                ACTIVE_TOASTS.remove(i);
                ACTIVE_TOASTS.add(entry);
                return;
            }
        }

        if (ACTIVE_TOASTS.size() >= MAX_TOASTS) {
            ACTIVE_TOASTS.removeFirst();
        }
        ACTIVE_TOASTS.add(new ToastEntry(name, state, now));
    }

    public static synchronized void render(GuiGraphicsExtractor extractor, Minecraft client, int screenWidth, int screenHeight) {
        if (ACTIVE_TOASTS.isEmpty() || client == null || client.font == null) return;

        StarClient sc = StarClient.getInstance();
        if (sc == null || sc.getClientSettings() == null || !sc.getClientSettings().isKeybindNotifications()) {
            ACTIVE_TOASTS.clear();
            return;
        }

        // Do not draw keybind corner toasts on top of the ClickGUI or HUD Editor screens
        if (client.gui.screen() instanceof ClickGuiScreen || client.gui.screen() instanceof HudEditorScreen) {
            return;
        }

        long now = System.currentTimeMillis();
        ACTIVE_TOASTS.removeIf(e -> now - e.createdAtMs >= DURATION_MS);
        if (ACTIVE_TOASTS.isEmpty()) return;

        ClientSettings cs = sc.getClientSettings();
        int baseBg = 0xE6000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
        int borderBase = cs.getMenuBorderColor();
        boolean shadow = cs.isHudTextShadow();

        int cardH = 26;
        int gap = 5;
        int rightMargin = 10;
        int bottomY = screenHeight - 12;

        // Render newest at bottom, stacking upward
        for (int i = ACTIVE_TOASTS.size() - 1; i >= 0; i--) {
            ToastEntry toast = ACTIVE_TOASTS.get(i);
            long age = Math.max(0L, now - toast.createdAtMs);

            float slideProgress;
            if (age < ANIM_IN_MS) {
                float t = (float) age / ANIM_IN_MS;
                slideProgress = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
            } else if (age > DURATION_MS - ANIM_OUT_MS) {
                float t = (float) (DURATION_MS - age) / ANIM_OUT_MS;
                slideProgress = Math.clamp(t * t, 0.0f, 1.0f);
            } else {
                slideProgress = 1.0f;
            }

            String stateBadge = toast.enabled ? "ON" : "OFF";
            int accentColor = toast.enabled ? 0xFF34D399 : 0xFFF87171;
            int badgeBg = toast.enabled ? 0x3334D399 : 0x33F87171;

            int badgeW = client.font.width(stateBadge) + 10;
            int nameW = client.font.width(toast.moduleName);
            int cardW = Math.max(126, 16 + nameW + 10 + badgeW + 8);

            int targetX = screenWidth - cardW - rightMargin;
            int offscreenX = screenWidth + 8;
            int x = Math.round(offscreenX + (targetX - offscreenX) * slideProgress);
            int y = bottomY - cardH;

            GuiRenderUtils.drawDropShadow(extractor, x, y, cardW, cardH, 6, 4, (int) (100 * slideProgress));
            GuiRenderUtils.fillRoundedRect(extractor, x, y, cardW, cardH, 6, baseBg);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, cardW, cardH, 6, borderBase);

            // Left status stripe
            GuiRenderUtils.fillRoundedRect(extractor, x + 3, y + 4, 3, cardH - 8, 1, accentColor);

            // Module Name
            extractor.text(client.font, toast.moduleName, x + 11, y + 7, 0xFFF8FAFC, shadow);

            // ON / OFF Pill Badge
            int bx = x + cardW - badgeW - 6;
            int by = y + 5;
            GuiRenderUtils.fillRoundedRect(extractor, bx, by, badgeW, 13, 3, badgeBg);
            GuiRenderUtils.drawRoundedOutline(extractor, bx, by, badgeW, 13, 3, (accentColor & 0x00FFFFFF) | 0x66000000);
            extractor.text(client.font, stateBadge, bx + 5, by + 3, accentColor, shadow);

            // Remaining duration thin progress bar along bottom
            float remainingRatio = Math.clamp(1.0f - (float) age / DURATION_MS, 0.0f, 1.0f);
            int progMaxW = cardW - 12;
            int progW = Math.round(progMaxW * remainingRatio);
            if (progW > 1) {
                extractor.fill(x + 6, y + cardH - 3, x + 6 + progW, y + cardH - 2, (accentColor & 0x00FFFFFF) | 0xAA000000);
            }

            bottomY -= (cardH + gap);
        }
    }
}
