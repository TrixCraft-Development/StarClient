package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.starclient.StarClient;
import net.starclient.module.MediaHudState;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.Setting;

/**
 * Draggable "Now Playing" Media HUD widget featuring Card & Compact modes,
 * real song duration counter (e.g. 0:37/1:20), live progress/remaining bar,
 * animated audio equalizer bars, marquee scrolling, and Client Color sync.
 */
public final class MediaHudRenderer {
    public static final int CARD_WIDTH = 198;
    public static final int CARD_HEIGHT = 48;
    public static final int COMPACT_WIDTH = 200;
    public static final int COMPACT_HEIGHT = 24;

    private static final long PREVIEW_POS_MS = 37_000L;
    private static final long PREVIEW_DUR_MS = 80_000L; // 1:20

    private MediaHudRenderer() {
    }

    public static boolean isCompact(Module module) {
        if (module == null) return false;
        Setting<?> s = module.getSetting("Style");
        return s instanceof ModeSetting ms && "Compact".equalsIgnoreCase(ms.getValue());
    }

    public static int getWidth(Module module) {
        return isCompact(module) ? COMPACT_WIDTH : CARD_WIDTH;
    }

    public static int getHeight(Module module) {
        return isCompact(module) ? COMPACT_HEIGHT : CARD_HEIGHT;
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module module, int x, int y) {
        if (module == null || !module.isEnabled()) return;
        boolean hasMedia = MediaHudState.hasMedia();
        boolean hideWhenIdle = getBool(module, "Hide When Idle", false);
        if (!hasMedia && hideWhenIdle) {
            return;
        }

        String title = hasMedia ? MediaHudState.getTitle() : "No Media Playing";
        String artist = hasMedia ? MediaHudState.getArtist() : "Start Spotify, YouTube Music, etc.";
        String app = hasMedia ? MediaHudState.getSourceApp() : "";
        boolean playing = hasMedia && MediaHudState.isPlaying();
        long posMs = hasMedia ? MediaHudState.getCurrentPositionMs() : -1L;
        long durMs = hasMedia ? MediaHudState.getDurationMs() : 0L;
        renderInternal(extractor, client.font, module, x, y, title, artist, app, playing, hasMedia, posMs, durMs);
    }

    public static void renderPreview(GuiGraphicsExtractor extractor, Module module, int x, int y) {
        Minecraft client = Minecraft.getInstance();
        boolean live = MediaHudState.hasMedia();
        String title = live ? MediaHudState.getTitle() : "Blinding Lights";
        String artist = live ? MediaHudState.getArtist() : "The Weeknd";
        String app = live ? MediaHudState.getSourceApp() : "Spotify";
        boolean playing = !live || MediaHudState.isPlaying();
        long posMs = live ? MediaHudState.getCurrentPositionMs() : PREVIEW_POS_MS;
        long durMs = live ? MediaHudState.getDurationMs() : PREVIEW_DUR_MS;
        renderInternal(extractor, client.font, module, x, y, title, artist, app, playing, true, posMs, durMs);
    }

    private static void renderInternal(GuiGraphicsExtractor extractor, Font font, Module module, int x, int y,
                                       String title, String artist, String app, boolean playing, boolean hasMedia,
                                       long posMs, long durMs) {
        boolean compact = isCompact(module);
        boolean transparent = module.isTransparentBackground();
        boolean showViz = getBool(module, "Show Visualizer", true);
        boolean showArtist = getBool(module, "Show Artist", true);
        boolean showApp = getBool(module, "Show Source App", true);
        boolean showDuration = getBool(module, "Show Duration", true);
        boolean remainingBar = isRemainingBarMode(module);
        boolean textShadow = StarClient.getInstance().getClientSettings().isHudTextShadow();

        int accent = resolveAccentColor(module);
        int bgColor = (module.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
        int borderColor = playing ? GuiRenderUtils.withAlpha(accent, 110) : 0x28FFFFFF;
        int primaryText = module.getTextColor();
        String feedback = MediaHudState.getActiveActionFeedback();

        boolean hasTimeline = hasMedia && durMs > 0L && posMs >= 0L;
        float progress = hasTimeline ? Math.clamp((float) posMs / (float) durMs, 0.0F, 1.0F) : 0.0F;
        float barRatio = remainingBar ? (1.0F - progress) : progress;

        if (compact) {
            int w = COMPACT_WIDTH;
            int h = COMPACT_HEIGHT;
            if (!transparent) {
                GuiRenderUtils.fillRoundedRect(extractor, x, y, w, h, 5, bgColor);
                GuiRenderUtils.drawRoundedOutline(extractor, x, y, w, h, 5, borderColor);
            }

            int leftX = x + 6;
            if (showViz) {
                drawEqualizerBars(extractor, leftX, y + 4, 3, 12, playing, accent);
                leftX += 13;
            } else {
                extractor.text(font, playing ? "♪" : "⏸", leftX, y + 6, accent, textShadow);
                leftX += 10;
            }

            String stateIcon = !hasMedia ? "♪" : (playing ? "▶" : "⏸");
            int stateX = x + w - 11;
            extractor.text(font, stateIcon, stateX, y + 6, playing ? accent : 0xFF64748B, textShadow);

            int rightEdge = stateX - 4;
            if (showDuration && hasTimeline) {
                String timeText = MediaHudState.formatDuration(posMs) + "/" + MediaHudState.formatDuration(durMs);
                int timeW = font.width(timeText);
                int timeX = rightEdge - timeW;
                extractor.text(font, timeText, timeX, y + 6, 0xFF94A3B8, textShadow);
                rightEdge = timeX - 5;
            }

            String line;
            if (!feedback.isEmpty()) {
                line = feedback;
            } else if (hasMedia && showArtist && artist != null && !artist.isEmpty()) {
                line = title + " — " + artist;
            } else {
                line = title;
            }

            int maxTextW = Math.max(20, rightEdge - leftX);
            drawMarqueeText(extractor, font, line, leftX, y + 6, maxTextW,
                    hasMedia ? primaryText : 0xFF94A3B8, textShadow);

            // Real song duration bar along bottom of Compact pill
            int barX = x + 5;
            int barY = y + h - 4;
            int barW = w - 10;
            drawProgressBar(extractor, barX, barY, barW, 2, hasTimeline, barRatio, playing, accent, false);
            return;
        }

        // Full Card layout
        int w = CARD_WIDTH;
        int h = CARD_HEIGHT;
        if (!transparent) {
            GuiRenderUtils.fillRoundedRect(extractor, x, y, w, h, 6, bgColor);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, w, h, 6, borderColor);
        }

        // Left album / music note badge
        int boxX = x + 6;
        int boxY = y + 6;
        int boxSize = 24;
        GuiRenderUtils.fillRoundedRect(extractor, boxX, boxY, boxSize, boxSize, 5,
                GuiRenderUtils.withAlpha(accent, playing ? 52 : 28));
        GuiRenderUtils.drawRoundedOutline(extractor, boxX, boxY, boxSize, boxSize, 5,
                GuiRenderUtils.withAlpha(accent, playing ? 165 : 85));

        if (hasMedia && playing) {
            drawEqualizerBars(extractor, boxX + 5, boxY + 5, 4, 14, true, accent);
        } else {
            String badgeIcon = hasMedia ? "⏸" : "♪";
            GuiRenderUtils.drawCenteredText(extractor, font, badgeIcon, boxX + boxSize / 2, boxY + 8, accent, textShadow);
        }

        // Right-side mini visualizer (optional)
        int rightEdge = x + w - 8;
        if (showViz && hasMedia) {
            int vizW = 19;
            int vizX = x + w - vizW - 7;
            drawEqualizerBars(extractor, vizX, y + 11, 5, 14, playing, accent);
            rightEdge = vizX - 6;
        }

        int textX = boxX + boxSize + 7;
        int maxTextW = Math.max(36, rightEdge - textX);

        // Top row: Track Title (or transient media-key feedback)
        String topText = !feedback.isEmpty() ? feedback : title;
        int topColor = !feedback.isEmpty() ? accent : (hasMedia ? primaryText : 0xFFCBD5E1);
        drawMarqueeText(extractor, font, topText, textX, y + 7, maxTextW, topColor, textShadow);

        // Second row: Artist + Source App
        StringBuilder sub = new StringBuilder();
        if (showArtist && artist != null && !artist.isEmpty()) {
            sub.append(artist);
        }
        if (showApp && app != null && !app.isEmpty()) {
            if (!sub.isEmpty()) sub.append(" • ");
            sub.append(app);
        }
        if (sub.isEmpty()) {
            sub.append(playing ? "Playing" : (hasMedia ? "Paused" : "Idle"));
        }
        drawMarqueeText(extractor, font, sub.toString(), textX, y + 19, maxTextW, 0xFF94A3B8, textShadow);

        // Bottom row: Song Progress Bar + Duration Label (e.g. 0:37/1:20)
        int barX = x + 7;
        int timeY = y + 35;
        int barY = y + 38;
        int barRight = x + w - 7;

        if (showDuration) {
            String timeLabel;
            int timeColor;
            if (hasTimeline) {
                timeLabel = MediaHudState.formatDuration(posMs) + "/" + MediaHudState.formatDuration(durMs);
                timeColor = playing ? 0xFFE2E8F0 : 0xFF94A3B8;
            } else if (hasMedia) {
                timeLabel = playing ? "LIVE" : "PAUSED";
                timeColor = playing ? accent : 0xFF94A3B8;
            } else {
                timeLabel = "--:--/--:--";
                timeColor = 0xFF64748B;
            }
            int timeW = font.width(timeLabel);
            int timeX = x + w - 7 - timeW;
            extractor.text(font, timeLabel, timeX, timeY, timeColor, textShadow);
            barRight = timeX - 7;
        }

        int barW = Math.max(24, barRight - barX);
        drawProgressBar(extractor, barX, barY, barW, 3, hasTimeline, barRatio, playing, accent, true);
    }

    private static void drawProgressBar(GuiGraphicsExtractor extractor, int x, int y, int width, int height,
                                        boolean hasTimeline, float ratio, boolean playing, int accent, boolean showPip) {
        if (width <= 2) return;
        int r = Math.max(1, height / 2);
        // Background track (visually represents total duration / remaining portion)
        GuiRenderUtils.fillRoundedRect(extractor, x, y, width, height, r, 0x2EFFFFFF);

        if (!hasTimeline) return;

        float clamped = Math.clamp(ratio, 0.0F, 1.0F);
        int fillW = Math.round(width * clamped);
        int fillColor = playing ? accent : GuiRenderUtils.withAlpha(accent, 145);

        if (fillW > 0) {
            if (fillW >= height) {
                GuiRenderUtils.fillRoundedRect(extractor, x, y, fillW, height, r, fillColor);
            } else {
                extractor.fill(x, y, x + fillW, y + height, fillColor);
            }
        }

        // Crisp glowing playhead pip at the boundary between elapsed and remaining time
        if (showPip) {
            int pipW = 3;
            int pipH = height + 2;
            int pipX = Math.clamp(x + fillW - 1, x, x + width - pipW);
            int pipY = y - 1;
            GuiRenderUtils.fillRoundedRect(extractor, pipX, pipY, pipW, pipH, 1,
                    playing ? 0xFFFFFFFF : 0xFFCBD5E1);
        }
    }

    private static void drawEqualizerBars(GuiGraphicsExtractor extractor, int x, int y,
                                          int barCount, int maxHeight, boolean playing, int color) {
        double t = System.currentTimeMillis() / 130.0;
        int barW = 2;
        int gap = 2;
        for (int i = 0; i < barCount; i++) {
            float level;
            if (playing) {
                double wave = 0.5 + 0.5 * Math.sin(t * (1.15 + i * 0.33) + i * 1.45);
                double wave2 = 0.5 + 0.5 * Math.cos(t * (0.75 + i * 0.21) - i * 0.8);
                level = (float) (0.22 + 0.78 * (wave * 0.65 + wave2 * 0.35));
            } else {
                level = 0.22F + (i % 2) * 0.12F;
            }
            int bh = Math.max(2, Math.round(maxHeight * level));
            int bx = x + i * (barW + gap);
            int by = y + maxHeight - bh;
            int barCol = playing ? color : GuiRenderUtils.withAlpha(color, 130);
            extractor.fill(bx, by, bx + barW, y + maxHeight, barCol);
        }
    }

    private static void drawMarqueeText(GuiGraphicsExtractor extractor, Font font, String text,
                                        int x, int y, int maxWidth, int color, boolean shadow) {
        if (text == null || text.isEmpty() || maxWidth <= 4) return;
        int tw = font.width(text);
        if (tw <= maxWidth) {
            extractor.text(font, text, x, y, color, shadow);
            return;
        }

        // Smooth ping-pong scroll with pause at both ends
        int overflow = tw - maxWidth;
        double cycleSec = Math.max(4.0, overflow / 18.0 + 2.4);
        double phase = ((System.currentTimeMillis() / 1000.0) % cycleSec) / cycleSec;
        double ratio;
        if (phase < 0.18) {
            ratio = 0.0;
        } else if (phase < 0.50) {
            ratio = (phase - 0.18) / 0.32;
        } else if (phase < 0.68) {
            ratio = 1.0;
        } else {
            ratio = 1.0 - (phase - 0.68) / 0.32;
        }
        int offset = (int) Math.round(overflow * Math.clamp(ratio, 0.0, 1.0));

        extractor.enableScissor(x, y - 1, x + maxWidth, y + font.lineHeight + 1);
        extractor.text(font, text, x - offset, y, color, shadow);
        extractor.disableScissor();
    }

    private static boolean isRemainingBarMode(Module module) {
        Setting<?> s = module.getSetting("Bar Mode");
        return s instanceof ModeSetting ms && "Remaining".equalsIgnoreCase(ms.getValue());
    }

    private static int resolveAccentColor(Module module) {
        if (getBool(module, "Use Client Color", true)) {
            StarClient sc = StarClient.getInstance();
            if (sc != null && sc.getClientSettings() != null) {
                return sc.getClientSettings().getMenuAccentColor();
            }
        }
        Setting<?> s = module.getSetting("Accent Color");
        if (s instanceof ColorModeSetting cms) {
            return cms.getColor();
        }
        return 0xFF38BDF8;
    }

    private static boolean getBool(Module module, String name, boolean fallback) {
        Setting<?> s = module.getSetting(name);
        if (s instanceof BooleanSetting bs) {
            return bs.getValue();
        }
        return fallback;
    }
}
