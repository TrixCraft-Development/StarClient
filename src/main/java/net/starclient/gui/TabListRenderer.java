package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.starclient.StarClient;
import net.starclient.mixin.PlayerTabOverlayAccessor;
import net.starclient.module.Module;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.List;

public final class TabListRenderer {

    private TabListRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, PlayerTabOverlay tabOverlay, Scoreboard scoreboard, Objective objective, Module mod) {
        if (tabOverlay == null || mod == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        List<PlayerInfo> players = null;
        if (tabOverlay instanceof PlayerTabOverlayAccessor accessor) {
            try {
                players = accessor.invokeGetPlayerInfos();
            } catch (Throwable ignored) {
            }
        }
        if (players == null || players.isEmpty()) {
            players = client.player.connection.getListedOnlinePlayers().stream().limit(80).toList();
        }
        if (players.isEmpty()) return;

        boolean useClientTheme = getBool(mod, "Use Client Theme");
        boolean showPingNumbers = getBool(mod, "Show Ping Numbers");
        boolean showPlayerHeads = getBool(mod, "Show Player Heads");
        boolean showHeaderFooter = getBool(mod, "Show Header & Footer");
        boolean highlightSelf = getBool(mod, "Highlight Self");
        double opacity = getNum(mod);
        boolean rounded = getBool(mod, "Rounded Corners");
        boolean textShadow = getBool(mod, "Text Shadow");
        boolean transparent = mod.isTransparentBackground();

        Font font = client.font;
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();

        int totalCount = players.size();
        int rows = Math.clamp(totalCount, 1, 20);
        int cols = (totalCount + rows - 1) / rows;
        cols = Math.clamp(cols, 1, 4);
        rows = (totalCount + cols - 1) / cols;

        // Measure max row content width
        int maxRowContentWidth = 0;
        for (PlayerInfo player : players) {
            Component nameComp = tabOverlay.getNameForDisplay(player);
            int nameW = font.width(nameComp);
            int faceW = showPlayerHeads ? 13 : 0;
            int pingW = showPingNumbers ? 32 : 14;

            int scoreW = 0;
            if (objective != null && scoreboard != null) {
                ScoreHolder scoreHolder = ScoreHolder.fromGameProfile(player.getProfile());
                ReadOnlyScoreInfo scoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
                if (scoreInfo != null) {
                    if (objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS) {
                        scoreW = font.width(scoreInfo.value() + " ❤") + 6;
                    } else {
                        Component scoreComp = scoreInfo.formatValue(objective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT));
                        scoreW = font.width(scoreComp) + 6;
                    }
                }
            }

            int w = faceW + nameW + scoreW + pingW + 10;
            maxRowContentWidth = Math.max(maxRowContentWidth, w);
        }

        int colWidth = Math.max(125, maxRowContentWidth + 6);
        int maxAllowedColW = (screenW - 40 - (cols - 1) * 5) / cols;
        colWidth = Math.clamp(maxAllowedColW, 90, colWidth);

        int padX = 6;
        int padY = 6;
        int rowHeight = 14;
        int rowGap = 2;
        int colGap = 5;

        int cardWidth = padX * 2 + cols * colWidth + (cols - 1) * colGap;
        int cardHeight = padY * 2 + rows * rowHeight + (rows - 1) * rowGap;
        int cardX = (screenW - cardWidth) / 2;

        Component header = null;
        Component footer = null;
        if (showHeaderFooter && tabOverlay instanceof PlayerTabOverlayAccessor accessor) {
            header = accessor.getHeader();
            footer = accessor.getFooter();
        }

        List<FormattedCharSequence> headerLines = (header != null && !header.getString().isEmpty())
                ? font.split(header, Math.clamp(cardWidth, 220, screenW - 40))
                : List.of();

        List<FormattedCharSequence> footerLines = (footer != null && !footer.getString().isEmpty())
                ? font.split(footer, Math.clamp(cardWidth, 220, screenW - 40))
                : List.of();

        int headerHeight = headerLines.isEmpty() ? 0 : (headerLines.size() * 10 + 4);
        int cardY = 12 + headerHeight;
        cardY = Math.clamp(cardY, 10, Math.max(10, screenH - cardHeight - 10));

        int radius = rounded ? 6 : 0;
        int accent = 0xFF38BDF8;
        if (StarClient.getInstance() != null) {
            accent = (StarClient.getInstance().getClientSettings().getMenuAccentColor() & 0x00FFFFFF) | 0xFF000000;
        }

        // Header text above card
        if (!headerLines.isEmpty()) {
            int hy = 10;
            for (FormattedCharSequence line : headerLines) {
                int lw = font.width(line);
                graphics.text(font, line, (screenW - lw) / 2, hy, 0xFFFFFFFF, textShadow);
                hy += 10;
            }
        }

        if (!transparent) {
            // Card Drop shadow
            GuiRenderUtils.drawDropShadow(graphics, cardX, cardY, cardWidth, cardHeight, radius, 3, 45);

            // Glass background
            int alpha = Math.clamp((int) Math.round(opacity * 255.0 / 100.0), 15, 255);
            int bgColor = (alpha << 24) | 0x080D1A;
            GuiRenderUtils.fillRoundedRect(graphics, cardX, cardY, cardWidth, cardHeight, radius, bgColor);

            // Card Outline
            if (useClientTheme) {
                int borderColor = (0x50 << 24) | (accent & 0x00FFFFFF);
                GuiRenderUtils.drawRoundedOutline(graphics, cardX, cardY, cardWidth, cardHeight, radius, borderColor);
            } else {
                GuiRenderUtils.drawRoundedOutline(graphics, cardX, cardY, cardWidth, cardHeight, radius, 0x22FFFFFF);
            }
        }

        // Render player rows
        for (int i = 0; i < totalCount; i++) {
            PlayerInfo player = players.get(i);
            int col = i / rows;
            int row = i % rows;
            int px = cardX + padX + col * (colWidth + colGap);
            int py = cardY + padY + row * (rowHeight + rowGap);
            int pw = colWidth;
            int ph = rowHeight;

            boolean isSelf = client.player != null && player.getProfile().id().equals(client.player.getUUID());

            // Row background
            if (isSelf && highlightSelf) {
                int selfFill = useClientTheme ? ((0x30 << 24) | (accent & 0x00FFFFFF)) : 0x2838BDF8;
                GuiRenderUtils.fillRoundedRect(graphics, px, py, pw, ph, 3, selfFill);
                int selfBorder = useClientTheme ? ((0x55 << 24) | (accent & 0x00FFFFFF)) : 0x4438BDF8;
                GuiRenderUtils.drawRoundedOutline(graphics, px, py, pw, ph, 3, selfBorder);
            } else if (!transparent) {
                GuiRenderUtils.fillRoundedRect(graphics, px, py, pw, ph, 3, 0x0AFFFFFF);
            }

            int contentX = px + 3;

            // Face avatar
            if (showPlayerHeads) {
                player.getSkin();
                PlayerFaceExtractor.extractRenderState(graphics, player.getSkin(), contentX, py + 3, 8);
                contentX += 8 + 4;
            }

            // Ping
            int latency = player.getLatency();
            int pingRight = px + pw - 4;
            int pingLeft;
            if (showPingNumbers) {
                String pingStr = latency >= 0 ? (latency + "ms") : "?";
                int pingColor = getPingColor(latency);
                int pingW = font.width(pingStr);
                pingLeft = pingRight - pingW;
                graphics.text(font, pingStr, pingLeft, py + 3, pingColor, textShadow);
            } else {
                pingLeft = pingRight - 11;
                drawSignalBars(graphics, pingLeft, py + 3, latency);
            }

            // Objective score
            int rightBoundary = pingLeft - 4;
            if (objective != null && scoreboard != null) {
                ScoreHolder scoreHolder = ScoreHolder.fromGameProfile(player.getProfile());
                ReadOnlyScoreInfo scoreInfo = scoreboard.getPlayerScoreInfo(scoreHolder, objective);
                if (scoreInfo != null) {
                    if (objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS) {
                        String hp = scoreInfo.value() + " ❤";
                        int hpW = font.width(hp);
                        rightBoundary -= hpW;
                        graphics.text(font, hp, rightBoundary, py + 3, 0xFFF43F5E, textShadow);
                    } else {
                        Component scoreComp = scoreInfo.formatValue(objective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT));
                        int scoreW = font.width(scoreComp);
                        rightBoundary -= scoreW;
                        graphics.text(font, scoreComp, rightBoundary, py + 3, 0xFFFFE066, textShadow);
                    }
                    rightBoundary -= 4;
                }
            }

            // Player name
            Component nameComp = tabOverlay.getNameForDisplay(player);
            if (rightBoundary > contentX) {
                graphics.text(font, nameComp, contentX, py + 3, 0xFFFFFFFF, textShadow);
            }
        }

        // Footer text below card
        if (!footerLines.isEmpty()) {
            int fy = cardY + cardHeight + 6;
            for (FormattedCharSequence line : footerLines) {
                int lw = font.width(line);
                graphics.text(font, line, (screenW - lw) / 2, fy, 0xFFE2E8F0, textShadow);
                fy += 10;
            }
        }
    }

    private static int getPingColor(int latency) {
        if (latency < 0) return 0xFF9CA3AF;
        if (latency <= 50) return 0xFF4ADE80;
        if (latency <= 100) return 0xFFA3E635;
        if (latency <= 150) return 0xFFFACC15;
        if (latency <= 250) return 0xFFFB923C;
        return 0xFFF87171;
    }

    private static void drawSignalBars(GuiGraphicsExtractor graphics, int x, int y, int latency) {
        int bars;
        int color;
        if (latency < 0) {
            bars = 0;
            color = 0xFF6B7280;
        } else if (latency <= 80) {
            bars = 4;
            color = 0xFF4ADE80;
        } else if (latency <= 150) {
            bars = 3;
            color = 0xFFA3E635;
        } else if (latency <= 250) {
            bars = 2;
            color = 0xFFFACC15;
        } else {
            bars = 1;
            color = 0xFFF87171;
        }
        for (int b = 0; b < 4; b++) {
            int barH = 2 + b * 2;
            int barY = y + 8 - barH;
            int barColor = (b < bars) ? color : 0x40FFFFFF;
            graphics.fill(x + b * 3, barY, x + b * 3 + 2, y + 8, barColor);
        }
    }

    private static boolean getBool(Module mod, String name) {
        Setting<?> s = mod.getSetting(name);
        return s == null || (boolean) s.getValue();
    }

    private static double getNum(Module mod) {
        return mod.getSetting("Background Opacity") instanceof NumberSetting ns ? ns.getValue() : 75.0;
    }
}
