package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.starclient.StarClient;
import net.starclient.mixin.BossHealthOverlayAccessor;
import net.starclient.module.Module;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.Collection;
import java.util.Locale;

public final class BossBarRenderer {

    private BossBarRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, BossHealthOverlay overlay, Module mod) {
        if (overlay == null || mod == null) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        Collection<LerpingBossEvent> events = ((BossHealthOverlayAccessor) overlay).getEvents().values();
        if (events.isEmpty()) return;

        graphics.nextStratum();
        renderEvents(graphics, client, events, mod);
    }

    public static void renderPreview(GuiGraphicsExtractor graphics, Module mod) {
        if (mod == null) return;
        Minecraft client = Minecraft.getInstance();

        Component sampleName = Component.literal("Ender Dragon");
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        renderCard(graphics, client, sampleName, 0.74f, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS, mod, 0, screenWidth, screenHeight);
    }

    private static void renderEvents(GuiGraphicsExtractor graphics, Minecraft client, Collection<LerpingBossEvent> events, Module mod) {
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();

        int index = 0;
        for (LerpingBossEvent event : events) {
            renderCard(graphics, client, event.getName(), event.getProgress(), event.getColor(), event.getOverlay(), mod, index, screenWidth, screenHeight);
            index++;
            if (index >= 4) break; // Limit active stacked boss bars
        }
    }

    private static void renderCard(GuiGraphicsExtractor graphics, Minecraft client, Component name, float progress,
                                   BossEvent.BossBarColor bossColor, BossEvent.BossBarOverlay overlay,
                                   Module mod, int stackIndex, int screenWidth, int screenHeight) {
        Font font = client.font;
        progress = Math.clamp(progress, 0.0f, 1.0f);

        boolean useClientTheme = getBool(mod, "Use Client Theme");
        boolean showPercentage = getBool(mod, "Show Percentage");
        boolean showName = getBool(mod, "Show Boss Name");
        boolean rounded = getBool(mod, "Rounded Corners");
        boolean textShadow = getBool(mod, "Text Shadow");
        boolean showBorder = getBool(mod, "Show Border");
        double barWidthVal = getNum(mod, "Bar Width", 150.0);
        double barHeightVal = getNum(mod, "Bar Height", 5.0);
        double opacity = getNum(mod, "Background Opacity", 70.0);

        int baseW = (int) Math.round(barWidthVal);
        int barH = (int) Math.round(barHeightVal);

        // Variable width sizing: dynamically expand bar width if the boss name requires more room
        String pctStr = String.format(Locale.ROOT, "%.0f%%", progress * 100.0f);
        int pctW = showPercentage ? font.width(pctStr) : 0;
        int nameW = showName && name != null ? font.width(name) : 0;

        int requiredW = baseW;
        if (showName && showPercentage) {
            int gap = 8;
            // With centered boss name, right half needs to fit nameW/2 + gap + pctW
            int needed = nameW + 2 * (pctW + gap);
            requiredW = Math.max(requiredW, needed);
        } else if (showName) {
            requiredW = Math.max(requiredW, nameW + 12);
        } else if (showPercentage) {
            requiredW = Math.max(requiredW, pctW * 2 + 16);
        }

        // Clamp to screen width with padding
        int maxAllowedW = Math.max(100, screenWidth - 28);
        int barW = Math.min(requiredW, maxAllowedW);

        int padX = 6;
        int padY = 3;
        int titleH = (showName || showPercentage) ? font.lineHeight : 0;
        int gapY = (titleH > 0) ? 2 : 0;
        int cardW = barW + padX * 2;
        int cardH = padY * 2 + titleH + gapY + barH;

        HudLayout layout = StarClient.getInstance().getHudLayout();
        layout.setBossBarSize(cardW, cardH);

        int startX = layout.effectiveX(HudLayout.BOSS_BAR, screenWidth, cardW);
        startX = Math.clamp(startX, 4, Math.max(4, screenWidth - cardW - 4));

        int startY;
        if (layout.isAuto(HudLayout.BOSS_BAR)) {
            startY = 12 + stackIndex * (cardH + 4);
        } else {
            startY = layout.effectiveY(HudLayout.BOSS_BAR, screenHeight) + stackIndex * (cardH + 4);
            startY = Math.clamp(startY, 4, Math.max(4, screenHeight - cardH - 4));
        }

        // Background card
        boolean transparent = mod.isTransparentBackground();
        if (!transparent) {
            int alpha = Math.clamp((int) Math.round(opacity * 2.55), 0, 255);
            int cardBg = (alpha << 24) | 0x0F172A; // slate-900 glass
            int cardRadius = rounded ? 4 : 0;
            GuiRenderUtils.fillRoundedRect(graphics, startX, startY, cardW, cardH, cardRadius, cardBg);

            if (showBorder) {
                int borderAlpha = Math.min(255, (int) (alpha * 0.7));
                int borderCol = (borderAlpha << 24) | 0x334155; // slate-700
                GuiRenderUtils.drawRoundedOutline(graphics, startX, startY, cardW, cardH, cardRadius, borderCol);
            }
        }

        // Accent / Bar Color
        int barColor = resolveBarColor(mod, bossColor, useClientTheme);

        int contentX = startX + padX;
        int currentY = startY + padY;

        // Top Row: Boss Title (Centered) & Percentage
        if (showName || showPercentage) {
            int pctX = contentX + barW - pctW;

            if (showName && name != null) {
                int nameX = contentX + (barW - nameW) / 2;
                if (showPercentage && (nameX + nameW > pctX - 3)) {
                    nameX = Math.max(contentX, pctX - 3 - nameW);
                }
                graphics.text(font, name, nameX, currentY, 0xFFF8FAFC, textShadow);
            }
            if (showPercentage) {
                graphics.text(font, pctStr, pctX, currentY, barColor, textShadow);
            }
            currentY += titleH + gapY;
        }

        // Health Bar Track
        int trackRadius = rounded ? Math.min(2, barH / 2) : 0;
        int trackBg = 0x88000000;
        GuiRenderUtils.fillRoundedRect(graphics, contentX, currentY, barW, barH, trackRadius, trackBg);
        GuiRenderUtils.drawRoundedOutline(graphics, contentX, currentY, barW, barH, trackRadius, 0x33FFFFFF);

        // Filled Portion
        int fillW = Math.round(barW * progress);
        if (fillW > 0) {
            GuiRenderUtils.fillRoundedRect(graphics, contentX, currentY, fillW, barH, trackRadius, barColor);

            // Subtle top shine
            if (barH >= 4) {
                int shineH = Math.max(1, barH / 2);
                int shineCol = 0x25FFFFFF;
                GuiRenderUtils.fillRoundedRect(graphics, contentX, currentY, fillW, shineH, trackRadius, shineCol);
            }
        }

        // Notch Dividers (if overlay has notches)
        int notches = getNotchCount(overlay);
        if (notches > 0) {
            float step = (float) barW / (float) notches;
            for (int i = 1; i < notches; i++) {
                int nx = contentX + Math.round(step * i);
                graphics.fill(nx, currentY, nx + 1, currentY + barH, 0x88000000);
            }
        }
    }

    private static int getNotchCount(BossEvent.BossBarOverlay overlay) {
        if (overlay == null) return 0;
        return switch (overlay) {
            case NOTCHED_6 -> 6;
            case NOTCHED_10 -> 10;
            case NOTCHED_12 -> 12;
            case NOTCHED_20 -> 20;
            default -> 0;
        };
    }

    private static int resolveBarColor(Module mod, BossEvent.BossBarColor bossColor, boolean useClientTheme) {
        if (useClientTheme && StarClient.getInstance() != null) {
            return StarClient.getInstance().getClientSettings().getMenuAccentColor();
        }
        if (mod != null && mod.getSetting("Color") instanceof ColorModeSetting cms) {
            return cms.getColor();
        }
        if (bossColor == null) return 0xFF38BDF8;
        return switch (bossColor) {
            case PINK -> 0xFFF472B6;
            case BLUE -> 0xFF38BDF8;
            case RED -> 0xFFEF4444;
            case GREEN -> 0xFF22C55E;
            case YELLOW -> 0xFFFACC15;
            case PURPLE -> 0xFFA855F7;
            case WHITE -> 0xFFF8FAFC;
        };
    }

    private static boolean getBool(Module mod, String name) {
        Setting<?> s = mod.getSetting(name);
        return s == null || (boolean) s.getValue();
    }

    private static double getNum(Module mod, String name, double def) {
        return mod.getSetting(name) instanceof NumberSetting ns ? ns.getValue() : def;
    }
}
