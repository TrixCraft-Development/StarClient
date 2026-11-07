package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class ScoreboardRenderer {

    private ScoreboardRenderer() {
    }

    private record DisplayRow(Component name, Component score, int nameWidth, int scoreWidth) {
    }

    public static void render(GuiGraphicsExtractor graphics, Objective objective, Module mod) {
        if (objective == null || mod == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        Scoreboard scoreboard = objective.getScoreboard();

        Collection<PlayerScoreEntry> rawScores = scoreboard.listPlayerScores(objective);
        if (rawScores.isEmpty()) return;

        NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);

        List<PlayerScoreEntry> activeScores = rawScores.stream()
                .filter(e -> !e.isHidden())
                .sorted(Comparator.comparing(PlayerScoreEntry::value).reversed()
                        .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
                .limit(15)
                .toList();

        if (activeScores.isEmpty()) return;

        boolean hideRedNumbers = getBool(mod, "Hide Red Numbers");
        boolean useClientTheme = getBool(mod, "Use Client Theme");
        boolean showBorder = getBool(mod, "Show Border");
        double opacity = getNum(mod, "Background Opacity", 70.0);
        boolean rounded = getBool(mod, "Rounded Corners");
        boolean textShadow = getBool(mod, "Text Shadow");
        int offsetY = (int) Math.round(getNum(mod, "Offset Y", 0.0));

        Font font = client.font;
        Component titleComp = objective.getDisplayName();
        int titleWidth = font.width(titleComp);

        List<DisplayRow> rows = new ArrayList<>(activeScores.size());
        int maxRowWidth = titleWidth;

        for (PlayerScoreEntry entry : activeScores) {
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            Component name = PlayerTeam.formatNameForTeam(team, entry.ownerName());
            Component score = entry.formatValue(numberFormat);

            int nameW = font.width(name);
            int scoreW = !hideRedNumbers ? font.width(score) : 0;
            int totalW = nameW + (scoreW > 0 ? scoreW + 10 : 0);

            maxRowWidth = Math.max(maxRowWidth, totalW);
            rows.add(new DisplayRow(name, score, nameW, scoreW));
        }

        int padX = 7;
        int padY = 5;
        int headerH = 12;
        int lineH = 10;
        int cardWidth = maxRowWidth + padX * 2;
        int cardHeight = padY + headerH + 3 + rows.size() * lineH + padY;

        HudLayout layout = StarClient.getInstance().getHudLayout();
        layout.setScoreboardSize(cardWidth, cardHeight);

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();

        int cardX;
        int cardY;
        if (layout.isAuto(HudLayout.SCOREBOARD)) {
            cardX = screenW - cardWidth - 6;
            cardY = (screenH - cardHeight) / 2 + offsetY;
        } else {
            cardX = layout.getX(HudLayout.SCOREBOARD);
            cardY = layout.getY(HudLayout.SCOREBOARD);
        }
        cardX = Math.clamp(cardX, 0, Math.max(0, screenW - cardWidth));
        cardY = Math.clamp(cardY, 0, Math.max(0, screenH - cardHeight));

        boolean transparent = mod.isTransparentBackground();

        boolean scaled = layout.beginScale(graphics, cardX, cardY, HudLayout.SCOREBOARD);
        renderCard(graphics, font, cardX, cardY, cardWidth, cardHeight, padX, padY, headerH, lineH,
                titleComp, titleWidth, rows, hideRedNumbers, useClientTheme, showBorder,
                opacity, rounded, textShadow, transparent);
        layout.endScale(graphics, scaled);
    }

    public static void renderPreview(GuiGraphicsExtractor graphics, Module mod) {
        if (mod == null || !mod.isEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;

        boolean hideRedNumbers = getBool(mod, "Hide Red Numbers");
        boolean useClientTheme = getBool(mod, "Use Client Theme");
        boolean showBorder = getBool(mod, "Show Border");
        double opacity = getNum(mod, "Background Opacity", 70.0);
        boolean rounded = getBool(mod, "Rounded Corners");
        boolean textShadow = getBool(mod, "Text Shadow");
        boolean transparent = mod.isTransparentBackground();
        int offsetY = (int) Math.round(getNum(mod, "Offset Y", 0.0));

        Component titleComp = Component.literal("§6§lSTAR CLIENT");
        int titleWidth = font.width(titleComp);

        List<DisplayRow> rows = List.of(
                new DisplayRow(Component.literal("§729/10/26 §8m12A"), Component.literal("15"), font.width("29/10/26 m12A"), font.width("15")),
                new DisplayRow(Component.literal(""), null, 0, 0),
                new DisplayRow(Component.literal("Kills: §a12"), Component.literal("13"), font.width("Kills: 12"), font.width("13")),
                new DisplayRow(Component.literal("Wins: §a4"), Component.literal("12"), font.width("Wins: 4"), font.width("12")),
                new DisplayRow(Component.literal("Coins: §e1,250"), Component.literal("11"), font.width("Coins: 1,250"), font.width("11")),
                new DisplayRow(Component.literal(""), null, 0, 0),
                new DisplayRow(Component.literal("§estarclient.net"), Component.literal("1"), font.width("starclient.net"), font.width("1"))
        );

        int maxRowWidth = titleWidth;
        for (DisplayRow row : rows) {
            int w = row.nameWidth + (!hideRedNumbers && row.score != null ? row.scoreWidth + 10 : 0);
            maxRowWidth = Math.max(maxRowWidth, w);
        }

        int padX = 7;
        int padY = 5;
        int headerH = 12;
        int lineH = 10;
        int cardWidth = maxRowWidth + padX * 2;
        int cardHeight = padY + headerH + 3 + rows.size() * lineH + padY;

        HudLayout layout = StarClient.getInstance().getHudLayout();
        layout.setScoreboardSize(cardWidth, cardHeight);

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();

        int cardX;
        int cardY;
        if (layout.isAuto(HudLayout.SCOREBOARD)) {
            cardX = screenW - cardWidth - 6;
            cardY = (screenH - cardHeight) / 2 + offsetY;
        } else {
            cardX = layout.getX(HudLayout.SCOREBOARD);
            cardY = layout.getY(HudLayout.SCOREBOARD);
        }
        cardX = Math.clamp(cardX, 0, Math.max(0, screenW - cardWidth));
        cardY = Math.clamp(cardY, 0, Math.max(0, screenH - cardHeight));

        boolean scaled = layout.beginScale(graphics, cardX, cardY, HudLayout.SCOREBOARD);
        renderCard(graphics, font, cardX, cardY, cardWidth, cardHeight, padX, padY, headerH, lineH,
                titleComp, titleWidth, rows, hideRedNumbers, useClientTheme, showBorder,
                opacity, rounded, textShadow, transparent);
        layout.endScale(graphics, scaled);
    }

    private static void renderCard(GuiGraphicsExtractor graphics, Font font,
                                   int cardX, int cardY, int cardWidth, int cardHeight,
                                   int padX, int padY, int headerH, int lineH,
                                   Component titleComp, int titleWidth, List<DisplayRow> rows,
                                   boolean hideRedNumbers, boolean useClientTheme, boolean showBorder,
                                   double opacity, boolean rounded, boolean textShadow, boolean transparent) {
        int radius = rounded ? 5 : 0;
        int accent = 0xFF38BDF8;
        if (StarClient.getInstance() != null) {
            accent = (StarClient.getInstance().getClientSettings().getMenuAccentColor() & 0x00FFFFFF) | 0xFF000000;
        }

        if (!transparent) {
            // Drop shadow
            GuiRenderUtils.drawDropShadow(graphics, cardX, cardY, cardWidth, cardHeight, radius, 3, 40);

            // Glass background
            int alpha = Math.clamp((int) Math.round(opacity * 255.0 / 100.0), 15, 255);
            int bgColor = (alpha << 24) | 0x0A0F1D;
            GuiRenderUtils.fillRoundedRect(graphics, cardX, cardY, cardWidth, cardHeight, radius, bgColor);

            // Border
            if (showBorder) {
                int borderColor = useClientTheme ? ((0x55 << 24) | (accent & 0x00FFFFFF)) : 0x22FFFFFF;
                GuiRenderUtils.drawRoundedOutline(graphics, cardX, cardY, cardWidth, cardHeight, radius, borderColor);
            }

            // Header divider
            if (showBorder || useClientTheme) {
                int dividerColor = useClientTheme ? ((0x60 << 24) | (accent & 0x00FFFFFF)) : 0x1AFFFFFF;
                graphics.fill(cardX + 4, cardY + padY + headerH, cardX + cardWidth - 4, cardY + padY + headerH + 1, dividerColor);
            }
        }

        // Title text centered
        int titleX = cardX + (cardWidth - titleWidth) / 2;
        graphics.text(font, titleComp, titleX, cardY + padY + 1, 0xFFFFFFFF, textShadow);

        // Entries
        int rowY = cardY + padY + headerH + 3;
        for (DisplayRow row : rows) {
            graphics.text(font, row.name, cardX + padX, rowY, 0xFFE2E8F0, textShadow);
            if (!hideRedNumbers && row.score != null && row.scoreWidth > 0) {
                int scoreX = cardX + cardWidth - padX - row.scoreWidth;
                graphics.text(font, row.score, scoreX, rowY, 0xFFFF5555, textShadow);
            }
            rowY += lineH;
        }
    }

    private static boolean getBool(Module mod, String name) {
        Setting<?> s = mod.getSetting(name);
        return s == null ? true : (boolean) s.getValue();
    }

    private static double getNum(Module mod, String name, double def) {
        return mod.getSetting(name) instanceof NumberSetting ns ? ns.getValue() : def;
    }
}
