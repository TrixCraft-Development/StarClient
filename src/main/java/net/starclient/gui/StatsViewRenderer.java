package net.starclient.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.stats.ClientStatsManager;
import net.starclient.stats.GameplayStats;

import java.util.List;
import java.util.Locale;

public final class StatsViewRenderer {
    public enum StatsTab {
        GLOBAL("Global", "🌐"),
        SINGLEPLAYER("Singleplayer", "👤"),
        MULTIPLAYER("Multiplayer", "👥");

        public final String label;
        public final String icon;

        StatsTab(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    public static final record StatCard(String icon, String label, String value, int valueColor, String detail) {}
    public static final record StatSection(String title, List<StatCard> cards) {}

    private StatsTab activeTab = StatsTab.GLOBAL;

    public StatsTab getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(StatsTab tab) {
        if (tab != null) {
            this.activeTab = tab;
        }
    }

    public boolean mouseClicked(int contentLeft, int contentWidth, int panelTop, double mouseX, double mouseY) {
        int tabsY = panelTop + 4;
        int tabH = 26;
        int tabW = Math.min(130, (contentWidth - 16) / 3);
        int gap = 8;

        StatsTab[] tabs = StatsTab.values();
        for (int i = 0; i < tabs.length; i++) {
            int tx = contentLeft + i * (tabW + gap);
            if (mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabsY && mouseY <= tabsY + tabH) {
                if (this.activeTab != tabs[i]) {
                    this.activeTab = tabs[i];
                    return true;
                }
            }
        }
        return false;
    }

    public void draw(GuiGraphicsExtractor extractor, Font font, int contentLeft, int contentWidth,
                     int panelTop, int panelBottom, int mouseX, int mouseY, double scroll) {
        ClientStatsManager.getInstance().updatePlaytime(net.minecraft.client.Minecraft.getInstance());
        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int menuBg = cs != null ? (0xF4000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xF40F172A;
        int menuAccent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int menuSurface = cs != null ? cs.getMenuSurfaceColor() : 0xFF1E293B;
        int menuSurfaceHover = cs != null ? cs.getMenuSurfaceHoverColor() : 0xFF334155;
        int menuBorder = cs != null ? cs.getMenuBorderColor() : 0x3364748B;

        int tabsY = panelTop + 4;
        int tabH = 26;
        int tabW = Math.min(130, (contentWidth - 16) / 3);
        int gap = 8;
        int sepY = panelTop + 36;
        int cardsClipTop = sepY + 2;

        // 1. Render Scrollable Content with Scissor strictly below the sticky tabs & separator
        extractor.enableScissor(contentLeft - 2, cardsClipTop, contentLeft + contentWidth + 2, panelBottom);

        List<StatSection> sections = buildSections(this.activeTab);
        int startY = cardsClipTop + 6 - (int) scroll;
        int currentY = startY;

        int cols = contentWidth >= 520 ? 3 : 2;
        int cardGap = 8;
        int cardW = (contentWidth - (cols - 1) * cardGap) / cols;
        int cardH = 46;

        for (StatSection sec : sections) {
            // Section Title
            if (currentY + 16 >= cardsClipTop && currentY <= panelBottom) {
                GuiRenderUtils.drawText(extractor, font, sec.title.toUpperCase(Locale.ROOT), contentLeft + 2, currentY + 2, menuAccent, true);
                int titleW = font.width(sec.title) + 8;
                extractor.fill(contentLeft + titleW, currentY + 7, contentLeft + contentWidth, currentY + 8, menuBorder);
            }
            currentY += 18;

            // Cards Grid
            List<StatCard> cards = sec.cards;
            for (int i = 0; i < cards.size(); i++) {
                int col = i % cols;
                int row = i / cols;

                int cx = contentLeft + col * (cardW + cardGap);
                int cy = currentY + row * (cardH + cardGap);

                if (cy + cardH >= cardsClipTop && cy <= panelBottom) {
                    boolean cardHovered = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH && mouseY >= cardsClipTop;

                    int cardBg = cardHovered ? menuSurfaceHover : menuSurface;
                    int cardOutline = cardHovered ? menuAccent : menuBorder;

                    GuiRenderUtils.drawDropShadow(extractor, cx, cy, cardW, cardH, 4, 2, 40);
                    GuiRenderUtils.fillRoundedRect(extractor, cx, cy, cardW, cardH, 6, cardBg);
                    GuiRenderUtils.drawRoundedOutline(extractor, cx, cy, cardW, cardH, 6, cardOutline);

                    StatCard card = cards.get(i);
                    // Icon
                    GuiRenderUtils.drawText(extractor, font, card.icon, cx + 10, cy + (cardH - font.lineHeight) / 2 - 1, 0xFFFFFFFF);

                    // Label
                    GuiRenderUtils.drawText(extractor, font, card.label, cx + 28, cy + 6, 0xFF94A3B8);

                    // Value
                    GuiRenderUtils.drawText(extractor, font, card.value, cx + 28, cy + 18, card.valueColor, true);

                    // Detail / Subtitle
                    GuiRenderUtils.drawText(extractor, font, card.detail, cx + 28, cy + 30, 0xFF64748B);
                }
            }

            int rowCount = (cards.size() + cols - 1) / cols;
            currentY += rowCount * (cardH + cardGap) + 10;
        }

        extractor.disableScissor();

        // 2. Render Sticky Header & Category Switch on top with an opaque background
        extractor.fill(contentLeft - 4, panelTop - 2, contentLeft + contentWidth + 4, sepY + 1, menuBg);

        StatsTab[] tabs = StatsTab.values();
        for (int i = 0; i < tabs.length; i++) {
            StatsTab t = tabs[i];
            int tx = contentLeft + i * (tabW + gap);
            boolean active = (t == this.activeTab);
            boolean hovered = mouseX >= tx && mouseX <= tx + tabW && mouseY >= tabsY && mouseY <= tabsY + tabH;

            int bg = active ? menuAccent : (hovered ? menuSurfaceHover : menuSurface);
            int border = active ? 0xFFFFFFFF : (hovered ? menuAccent : menuBorder);
            int textCol = active ? 0xFFFFFFFF : (hovered ? 0xFFF1F5F9 : 0xFF94A3B8);

            GuiRenderUtils.drawDropShadow(extractor, tx, tabsY, tabW, tabH, 4, 2, 50);
            GuiRenderUtils.fillRoundedRect(extractor, tx, tabsY, tabW, tabH, 6, bg);
            GuiRenderUtils.drawRoundedOutline(extractor, tx, tabsY, tabW, tabH, 6, border);

            String title = t.icon + " " + t.label;
            GuiRenderUtils.drawCenteredText(extractor, font, title, tx + tabW / 2, tabsY + (tabH - font.lineHeight) / 2 + 1, textCol);
        }

        // Horizontal separator line below tabs
        extractor.fill(contentLeft, sepY, contentLeft + contentWidth, sepY + 1, menuBorder);
    }

    public double computeMaxScroll(int contentWidth, int viewportHeight) {
        List<StatSection> sections = buildSections(this.activeTab);
        int cols = contentWidth >= 520 ? 3 : 2;
        int cardGap = 8;
        int cardH = 46;

        int totalH = 12;
        for (StatSection sec : sections) {
            totalH += 18; // section header
            int rows = (sec.cards.size() + cols - 1) / cols;
            totalH += rows * (cardH + cardGap) + 10;
        }

        return Math.max(0, totalH - viewportHeight);
    }

    private List<StatSection> buildSections(StatsTab tab) {
        ClientStatsManager sm = ClientStatsManager.getInstance();
        GameplayStats stats = switch (tab) {
            case GLOBAL -> sm.getGlobalStats();
            case SINGLEPLAYER -> sm.getSingleplayerStats();
            case MULTIPLAYER -> sm.getMultiplayerStats();
        };

        if (tab == StatsTab.GLOBAL) {
            return List.of(
                    new StatSection("Client & Streaks", List.of(
                            new StatCard("📅", "First Launch", sm.getFirstLaunchDate(), 0xFF38BDF8, "Initial client install date"),
                            new StatCard("🚀", "Client Launches", String.format(Locale.ROOT, "%,d times", sm.getTimesStartedClient()), 0xFFF8FAFC, "Total game sessions"),
                            new StatCard("🗓", "Unique Days", String.format(Locale.ROOT, "%,d days", sm.getUniqueDaysJoined()), 0xFF34D399, "Distinct days launched"),
                            new StatCard("🔥", "Login Streak", sm.getLoginStreak() + " days", 0xFFF59E0B, "Consecutive daily logins"),
                            new StatCard("🏆", "Best Streak", sm.getLongestLoginStreak() + " days", 0xFFFBBF24, "Longest daily login record")
                    )),
                    new StatSection("Activity & Exploration", List.of(
                            new StatCard("⏱", "Total Playtime", formatPlaytime(stats.playtimeSeconds), 0xFF38BDF8, "In-game & menu time"),
                            new StatCard("🏃", "Distance Traveled", formatDistance(stats.blocksTraveled), 0xFF60A5FA, "Blocks moved in worlds"),
                            new StatCard("💬", "Chat Messages", String.format(Locale.ROOT, "%,d sent", stats.chatMessagesSent), 0xFFA78BFA, "Messages sent in chat"),
                            new StatCard("🌟", "Worlds Created", String.format(Locale.ROOT, "%,d created", stats.worldsCreated), 0xFF38BDF8, "Singleplayer worlds generated"),
                            new StatCard("🌍", "Worlds Joined", String.format(Locale.ROOT, "%,d joined", stats.worldsJoined), 0xFF34D399, "Singleplayer sessions joined"),
                            new StatCard("🌐", "Servers Joined", String.format(Locale.ROOT, "%,d joined", stats.serversJoined), 0xFF60A5FA, "Multiplayer servers joined")
                    )),
                    new StatSection("Combat & Survival", List.of(
                            new StatCard("🗡", "Player Kills", String.format(Locale.ROOT, "%,d kills", stats.playerKills), 0xFFEF4444, "Players eliminated"),
                            new StatCard("🏹", "Mob Kills", String.format(Locale.ROOT, "%,d mobs", stats.mobKills), 0xFFF87171, "Monsters & animals defeated"),
                            new StatCard("💀", "Deaths", String.format(Locale.ROOT, "%,d deaths", stats.deaths), 0xFF94A3B8, "Total times died"),
                            new StatCard("💔", "Hearts Lost", String.format(Locale.ROOT, "%.1f ❤", stats.heartsLost), 0xFFFB7185, "Total damage received"),
                            new StatCard("💖", "Hearts Regenerated", String.format(Locale.ROOT, "%.1f ❤", stats.heartsRegenerated), 0xFF34D399, "Total health restored"),
                            new StatCard("🍖", "Times Eaten", String.format(Locale.ROOT, "%,d times", stats.timesEaten), 0xFFF59E0B, "Food & drinks consumed")
                    )),
                    new StatSection("Building & Crafting", List.of(
                            new StatCard("🧱", "Blocks Placed", String.format(Locale.ROOT, "%,d placed", stats.blocksPlaced), 0xFF38BDF8, "Blocks placed into world"),
                            new StatCard("⛏", "Blocks Mined", String.format(Locale.ROOT, "%,d mined", stats.blocksMined), 0xFFFBBF24, "Blocks broken & mined"),
                            new StatCard("🔨", "Items Crafted", String.format(Locale.ROOT, "%,d crafted", stats.itemsCrafted), 0xFF818CF8, "Crafting table & inventory"),
                            new StatCard("💥", "Items Broken", String.format(Locale.ROOT, "%,d broken", stats.itemsBroken), 0xFFF43F5E, "Tools & armor worn out")
                    ))
            );
        } else if (tab == StatsTab.SINGLEPLAYER) {
            return List.of(
                    new StatSection("Singleplayer Activity", List.of(
                            new StatCard("⏱", "SP Playtime", formatPlaytime(stats.playtimeSeconds), 0xFF38BDF8, "Time in solo worlds"),
                            new StatCard("🏃", "Distance Traveled", formatDistance(stats.blocksTraveled), 0xFF60A5FA, "Blocks moved in solo worlds"),
                            new StatCard("🌟", "Worlds Created", String.format(Locale.ROOT, "%,d created", stats.worldsCreated), 0xFF38BDF8, "Singleplayer worlds generated"),
                            new StatCard("🌍", "Worlds Joined", String.format(Locale.ROOT, "%,d joined", stats.worldsJoined), 0xFF34D399, "Singleplayer sessions joined")
                    )),
                    new StatSection("Singleplayer Combat & Survival", List.of(
                            new StatCard("🗡", "Player Kills", String.format(Locale.ROOT, "%,d kills", stats.playerKills), 0xFFEF4444, "Player kills in solo worlds"),
                            new StatCard("🏹", "Mob Kills", String.format(Locale.ROOT, "%,d mobs", stats.mobKills), 0xFFF87171, "Monsters & animals defeated"),
                            new StatCard("💀", "Deaths", String.format(Locale.ROOT, "%,d deaths", stats.deaths), 0xFF94A3B8, "Deaths in solo worlds"),
                            new StatCard("💔", "Hearts Lost", String.format(Locale.ROOT, "%.1f ❤", stats.heartsLost), 0xFFFB7185, "Total damage received in SP"),
                            new StatCard("💖", "Hearts Regenerated", String.format(Locale.ROOT, "%.1f ❤", stats.heartsRegenerated), 0xFF34D399, "Total health restored in SP"),
                            new StatCard("🍖", "Times Eaten", String.format(Locale.ROOT, "%,d times", stats.timesEaten), 0xFFF59E0B, "Food & drinks consumed in SP")
                    )),
                    new StatSection("Singleplayer Building & Crafting", List.of(
                            new StatCard("🧱", "Blocks Placed", String.format(Locale.ROOT, "%,d placed", stats.blocksPlaced), 0xFF38BDF8, "Blocks placed in solo worlds"),
                            new StatCard("⛏", "Blocks Mined", String.format(Locale.ROOT, "%,d mined", stats.blocksMined), 0xFFFBBF24, "Blocks mined in solo worlds"),
                            new StatCard("🔨", "Items Crafted", String.format(Locale.ROOT, "%,d crafted", stats.itemsCrafted), 0xFF818CF8, "Items crafted in solo worlds"),
                            new StatCard("💥", "Items Broken", String.format(Locale.ROOT, "%,d broken", stats.itemsBroken), 0xFFF43F5E, "Tools & armor broken in SP")
                    ))
            );
        } else {
            return List.of(
                    new StatSection("Multiplayer Activity", List.of(
                            new StatCard("⏱", "Online Playtime", formatPlaytime(stats.playtimeSeconds), 0xFF38BDF8, "Time on multiplayer servers"),
                            new StatCard("🏃", "Distance Traveled", formatDistance(stats.blocksTraveled), 0xFF60A5FA, "Blocks moved on servers"),
                            new StatCard("🌐", "Servers Joined", String.format(Locale.ROOT, "%,d joined", stats.serversJoined), 0xFF60A5FA, "Multiplayer servers joined"),
                            new StatCard("💬", "Chat Messages", String.format(Locale.ROOT, "%,d sent", stats.chatMessagesSent), 0xFFA78BFA, "Chat messages sent online")
                    )),
                    new StatSection("Multiplayer Combat & Survival", List.of(
                            new StatCard("🗡", "Player Kills", String.format(Locale.ROOT, "%,d kills", stats.playerKills), 0xFFEF4444, "PvP players eliminated"),
                            new StatCard("🏹", "Mob Kills", String.format(Locale.ROOT, "%,d mobs", stats.mobKills), 0xFFF87171, "Monsters & animals defeated"),
                            new StatCard("💀", "Deaths", String.format(Locale.ROOT, "%,d deaths", stats.deaths), 0xFF94A3B8, "Deaths on servers"),
                            new StatCard("💔", "Hearts Lost", String.format(Locale.ROOT, "%.1f ❤", stats.heartsLost), 0xFFFB7185, "Damage taken on servers"),
                            new StatCard("💖", "Hearts Regenerated", String.format(Locale.ROOT, "%.1f ❤", stats.heartsRegenerated), 0xFF34D399, "Health restored on servers"),
                            new StatCard("🍖", "Times Eaten", String.format(Locale.ROOT, "%,d times", stats.timesEaten), 0xFFF59E0B, "Food & drinks consumed online")
                    )),
                    new StatSection("Multiplayer Building & Crafting", List.of(
                            new StatCard("🧱", "Blocks Placed", String.format(Locale.ROOT, "%,d placed", stats.blocksPlaced), 0xFF38BDF8, "Blocks placed on servers"),
                            new StatCard("⛏", "Blocks Mined", String.format(Locale.ROOT, "%,d mined", stats.blocksMined), 0xFFFBBF24, "Blocks mined on servers"),
                            new StatCard("🔨", "Items Crafted", String.format(Locale.ROOT, "%,d crafted", stats.itemsCrafted), 0xFF818CF8, "Items crafted on servers"),
                            new StatCard("💥", "Items Broken", String.format(Locale.ROOT, "%,d broken", stats.itemsBroken), 0xFFF43F5E, "Tools & armor broken online")
                    ))
            );
        }
    }

    private static String formatPlaytime(long totalSeconds) {
        if (totalSeconds <= 0) return "0s";
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (days > 0) {
            return String.format(Locale.ROOT, "%dd %dh %dm %ds", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format(Locale.ROOT, "%dm %ds", minutes, seconds);
        } else {
            return seconds + "s";
        }
    }

    private static String formatDistance(double blocks) {
        if (blocks < 1000.0) {
            return String.format(Locale.ROOT, "%,d blocks", (long) blocks);
        } else {
            return String.format(Locale.ROOT, "%.2f km (%,d blocks)", blocks / 1000.0, (long) blocks);
        }
    }
}
