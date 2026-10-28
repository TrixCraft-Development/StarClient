package net.starclient.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.starclient.api.HypixelApi;

import java.util.Locale;

/**
 * Renders a {@link HypixelApi.Stats} into chat.
 * <p>
 * Chat was chosen over a permanent HUD panel: the useful case is deciding whether to accept a
 * Bedwars challenge or a duel, which is a one-off question, and a ten line breakdown pinned to
 * the screen would sit right in the middle of the fight it is meant to inform. Chat also keeps
 * the API traffic to one request per deliberate lookup instead of a poll per nearby player, and
 * the answer stays readable after it has scrolled past.
 */
public final class HypixelPlayerStats {

    /** Which games and which detail rows the user asked to see. */
    public record Options(boolean bedwars, boolean skywars, boolean duels, boolean level,
                          boolean winLoss, boolean kills, boolean extras) {
    }

    private HypixelPlayerStats() {
    }

    public static void print(Minecraft client, String name, HypixelApi.Stats stats, Options options) {
        chat(client, header(name, stats, options));

        boolean anyGame = false;
        if (options.bedwars()) {
            printGame(client, "Bedwars", stats.bedwarsLevel(), stats.bedwars(), options, true);
            anyGame = true;
        }
        if (options.skywars()) {
            printGame(client, "SkyWars", stats.skywarsLevel(), stats.skywars(), options, false);
            anyGame = true;
        }
        if (options.duels()) {
            printGame(client, "Duels", -1, stats.duels(), options, false);
            anyGame = true;
        }

        if (!anyGame || (stats.bedwars().isEmpty() && stats.skywars().isEmpty() && stats.duels().isEmpty())) {
            chat(client, Component.literal("  No Bedwars, SkyWars or Duels stats on this account.")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static MutableComponent header(String name, HypixelApi.Stats stats, Options options) {
        if (name == null || name.isEmpty()) {
            name = stats.name();
        }
        if (name == null || name.isEmpty()) {
            name = "Unknown player";
        }

        MutableComponent header = Component.literal("[Hypixel] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(name).withStyle(ChatFormatting.AQUA));
        if (!stats.rank().isEmpty()) {
            header = header.append(Component.literal(" " + stats.rank()).withStyle(ChatFormatting.GOLD));
        }
        if (options.level() && stats.networkLevel() >= 0) {
            header = header.append(Component.literal("  Network " + level(stats.networkLevel()))
                    .withStyle(ChatFormatting.GRAY));
        }
        return header;
    }

    private static void printGame(Minecraft client, String game, double level, HypixelApi.GameStats stats,
                                  Options options, boolean isBedwars) {
        chat(client, title(game, level, options));

        if (stats.isEmpty()) {
            return;
        }

        // Counters Hypixel did not report are dropped rather than shown as zero, so a blank always
        // means "unknown" and never quietly reads as "this player has no kills".
        Line line = new Line();

        if (options.winLoss()) {
            line.add("W", stats.wins());
            line.add("L", stats.losses());
            if (stats.winRate() >= 0) {
                line.add("WR", String.format(Locale.ROOT, "%.1f%%", stats.winRate()));
            }
        }
        if (options.kills()) {
            line.add("K", stats.kills());
            line.add("D", stats.deaths());
            if (stats.kd() >= 0) {
                line.add("K/D", String.format(Locale.ROOT, "%.2f", stats.kd()), kdColor(stats.kd()));
            }
        }
        if (options.extras()) {
            if (isBedwars) {
                line.add("FK", stats.finalKills());
                line.add("FD", stats.finalDeaths());
                line.add("Beds", stats.bedsBroken());
                line.add("Lost", stats.bedsLost());
            }
            line.add("Goals", stats.goals());
            line.add("Assists", stats.assists());
            line.add("Streak", stats.winstreak() >= 0 ? stats.winstreak() : stats.bestWinstreak());
        }

        if (!line.isEmpty()) {
            chat(client, line.build());
        }
    }

    private static MutableComponent title(String game, double level, Options options) {
        MutableComponent title = Component.literal("  " + game).withStyle(ChatFormatting.AQUA);
        if (options.level() && level >= 0) {
            title = title.append(Component.literal(" " + level(level)).withStyle(ChatFormatting.GOLD));
        }
        return title.append(Component.literal(" "));
    }

    /** One indented stat row, built as "Label:value" pairs with the labels dimmed. */
    private static final class Line {
        private final MutableComponent root = Component.literal("  ").withStyle(ChatFormatting.GRAY);
        private int fields;

        void add(String label, long value) {
            if (value >= 0) {
                add(label, String.valueOf(value), ChatFormatting.WHITE);
            }
        }

        void add(String label, String value) {
            add(label, value, ChatFormatting.WHITE);
        }

        void add(String label, String value, ChatFormatting color) {
            if (fields > 0) {
                root.append("  ");
            }
            root.append(Component.literal(label + ":").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(value).withStyle(color));
            fields++;
        }

        boolean isEmpty() {
            return fields == 0;
        }

        MutableComponent build() {
            return root;
        }
    }

    private static ChatFormatting kdColor(double kd) {
        if (kd >= 1.0) {
            return ChatFormatting.GREEN;
        }
        return kd >= 0.7 ? ChatFormatting.YELLOW : ChatFormatting.RED;
    }

    private static String level(double level) {
        return Math.abs(level - Math.rint(level)) < 0.05
                ? String.valueOf((long) Math.rint(level))
                : String.format(Locale.ROOT, "%.1f", level);
    }

    private static void chat(Minecraft client, Component message) {
        client.gui.hud.getChat().addClientSystemMessage(message);
    }
}
