package net.starclient.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern procedural UI rendering utilities for Star Client's ClickGUI and overlays.
 * Provides zero-overlap rounded rectangles, outlines, soft drop shadows, animated
 * pill switches, color swatches, and text helpers.
 */
public final class GuiRenderUtils {
    private GuiRenderUtils() {
    }

    public static int textWidth(Font font, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return font.width(text);
    }

    public static void drawText(GuiGraphicsExtractor extractor, Font font, String text, int x, int y, int color) {
        drawText(extractor, font, text, x, y, color, false);
    }

    public static void drawText(GuiGraphicsExtractor extractor, Font font, String text, int x, int y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return;
        }
        extractor.text(font, text, x, y, color, shadow);
    }

    public static void drawCenteredText(GuiGraphicsExtractor extractor, Font font, String text, int centerX, int y, int color) {
        drawCenteredText(extractor, font, text, centerX, y, color, false);
    }

    public static void drawCenteredText(GuiGraphicsExtractor extractor, Font font, String text, int centerX, int y, int color, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int width = font.width(text);
        extractor.text(font, text, centerX - width / 2, y, color, shadow);
    }

    public static String truncate(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (textWidth(font, text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisW = textWidth(font, ellipsis);
        if (ellipsisW >= maxWidth) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() > 1 && textWidth(font, sb.toString()) + ellipsisW > maxWidth) {
            sb.setLength(sb.length() - 1);
        }
        return sb.append(ellipsis).toString();
    }

    public static String truncateWithEllipsis(Font font, String text, int maxWidth) {
        return truncate(font, text, maxWidth);
    }

    public static List<String> wrapLines(Font font, String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.isEmpty()) {
                current.append(word);
            } else {
                String candidate = current + " " + word;
                if (textWidth(font, candidate) <= maxWidth) {
                    current.append(" ").append(word);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    public static void fillGradient(GuiGraphicsExtractor extractor, int x1, int y1, int x2, int y2, int colorFrom, int colorTo) {
        extractor.fillGradient(x1, y1, x2, y2, colorFrom, colorTo);
    }

    /**
     * Fills a rounded rectangle without overlapping scanlines so semi-transparent colors
     * blend uniformly with no seams.
     */
    public static void fillRoundedRect(GuiGraphicsExtractor extractor, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0 || (color >>> 24) == 0) {
            return;
        }
        int r = Math.min(radius, Math.min(width / 2, height / 2));
        if (r <= 0) {
            extractor.fill(x, y, x + width, y + height, color);
            return;
        }

        if (height > 2 * r) {
            extractor.fill(x, y + r, x + width, y + height - r, color);
        }

        for (int i = 0; i < r; i++) {
            int dx = cornerInset(r, i);
            int left = x + dx;
            int right = x + width - dx;
            if (right > left) {
                extractor.fill(left, y + i, right, y + i + 1, color);
                int bottomY = y + height - 1 - i;
                if (bottomY >= y + r) {
                    extractor.fill(left, bottomY, right, bottomY + 1, color);
                }
            }
        }
    }

    /**
     * Draws a 1px crisp rounded outline matching {@link #fillRoundedRect}.
     */
    public static void drawRoundedOutline(GuiGraphicsExtractor extractor, int x, int y, int width, int height, int radius, int color) {
        if (width <= 1 || height <= 1 || (color >>> 24) == 0) {
            return;
        }
        int r = Math.min(radius, Math.min(width / 2, height / 2));
        if (r <= 0) {
            extractor.fill(x, y, x + width, y + 1, color);
            extractor.fill(x, y + height - 1, x + width, y + height, color);
            extractor.fill(x, y + 1, x + 1, y + height - 1, color);
            extractor.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
            return;
        }

        int topStart = x + cornerInset(r, 0);
        int topEnd = x + width - cornerInset(r, 0);
        if (topEnd > topStart) {
            extractor.fill(topStart, y, topEnd, y + 1, color);
            extractor.fill(topStart, y + height - 1, topEnd, y + height, color);
        }

        if (height > 2 * r) {
            extractor.fill(x, y + r, x + 1, y + height - r, color);
            extractor.fill(x + width - 1, y + r, x + width, y + height - r, color);
        }

        for (int i = 1; i < r; i++) {
            int dx = cornerInset(r, i);
            int prevDx = cornerInset(r, i - 1);
            int span = Math.max(1, prevDx - dx);

            int topY = y + i;
            int botY = y + height - 1 - i;

            extractor.fill(x + dx, topY, x + dx + span, topY + 1, color);
            extractor.fill(x + width - dx - span, topY, x + width - dx, topY + 1, color);

            if (botY != topY) {
                extractor.fill(x + dx, botY, x + dx + span, botY + 1, color);
                extractor.fill(x + width - dx - span, botY, x + width - dx, botY + 1, color);
            }
        }
    }

    /**
     * Draws a soft multi-step outer drop shadow around a rounded rectangle.
     */
    public static void drawDropShadow(GuiGraphicsExtractor extractor, int x, int y, int width, int height, int radius, int layers, int maxAlpha) {
        for (int i = layers; i >= 1; i--) {
            float t = 1.0F - (float) i / (layers + 1);
            int alpha = Math.clamp(Math.round(maxAlpha * t * t), 0, 255);
            if (alpha <= 0) continue;
            int color = alpha << 24;
            drawRoundedOutline(extractor, x - i, y - i, width + i * 2, height + i * 2, radius + i, color);
        }
    }

    /**
     * Draws a modern animated pill toggle switch.
     *
     * @param progress 0.0F (OFF) to 1.0F (ON)
     */
    public static void drawToggleSwitch(GuiGraphicsExtractor extractor, int x, int y, int width, int height, float progress, int activeColor, int inactiveColor) {
        float clamped = Math.clamp(progress, 0.0F, 1.0F);
        int trackColor = lerpColor(inactiveColor, activeColor, clamped);
        int radius = height / 2;
        fillRoundedRect(extractor, x, y, width, height, radius, trackColor);

        int knobSize = height - 4;
        int minKnobX = x + 2;
        int maxKnobX = x + width - knobSize - 2;
        int knobX = Math.round(minKnobX + (maxKnobX - minKnobX) * clamped);
        int knobY = y + 2;
        int knobColor = lerpColor(0xFFCBD5E1, 0xFFFFFFFF, clamped);
        fillRoundedRect(extractor, knobX, knobY, knobSize, knobSize, knobSize / 2, knobColor);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (Math.clamp(alpha, 0, 255) << 24) | (rgb & 0x00FFFFFF);
    }

    public static int withAlphaFactor(int argb, float factor) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        if (baseAlpha == 0) baseAlpha = 255;
        int newAlpha = Math.clamp(Math.round(baseAlpha * factor), 0, 255);
        return (newAlpha << 24) | (argb & 0x00FFFFFF);
    }

    public static int lerpColor(int colorA, int colorB, float t) {
        float clamped = Math.clamp(t, 0.0F, 1.0F);
        int aA = (colorA >>> 24) & 0xFF;
        int rA = (colorA >>> 16) & 0xFF;
        int gA = (colorA >>> 8) & 0xFF;
        int bA = colorA & 0xFF;

        int aB = (colorB >>> 24) & 0xFF;
        int rB = (colorB >>> 16) & 0xFF;
        int gB = (colorB >>> 8) & 0xFF;
        int bB = colorB & 0xFF;

        int a = Math.round(aA + (aB - aA) * clamped);
        int r = Math.round(rA + (rB - rA) * clamped);
        int g = Math.round(gA + (gB - gA) * clamped);
        int b = Math.round(bA + (bB - bA) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int cornerInset(int radius, int rowFromEdge) {
        double dy = radius - rowFromEdge - 0.5;
        double dx = Math.sqrt(Math.max(0.0, (double) radius * radius - dy * dy));
        return Math.max(0, radius - (int) Math.round(dx));
    }
}
