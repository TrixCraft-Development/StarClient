package net.starclient.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.cosmetics.CosmeticManager;
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
    public static final record ModalStat(String icon, String label, String value, int color, String detail) {}

    private StatsTab activeTab = StatsTab.GLOBAL;
    private boolean showShareCardModal = false;
    private long lastCopiedTime = 0L;

    // Skin caching for offline / menu rendering
    private String cachedProfileName = null;
    private ResolvableProfile cachedResolvableProfile = null;

    public StatsTab getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(StatsTab tab) {
        if (tab != null) {
            this.activeTab = tab;
        }
    }

    public boolean isShowingCardModal() {
        return this.showShareCardModal;
    }

    public void openCardModal() {
        this.showShareCardModal = true;
    }

    public void closeCardModal() {
        this.showShareCardModal = false;
    }

    public void copyFormattedSummaryToClipboard() {
        Minecraft client = Minecraft.getInstance();
        String summary = generateFormattedSummary(this.activeTab);
        if (client.keyboardHandler != null) {
            client.keyboardHandler.setClipboard(summary);
        }
        this.lastCopiedTime = System.currentTimeMillis();
    }

    public boolean mouseClicked(int contentLeft, int contentWidth, int panelTop, double mouseX, double mouseY) {
        int tabsY = panelTop + 4;
        int tabH = 26;
        int gap = 6;
        int shareBtnW = 100;
        int tabW = Math.min(115, (contentWidth - shareBtnW - gap * 3) / 3);

        // Share button on far right of header
        int shareBtnX = contentLeft + contentWidth - shareBtnW;
        if (mouseX >= shareBtnX && mouseX <= shareBtnX + shareBtnW && mouseY >= tabsY && mouseY <= tabsY + tabH) {
            copyFormattedSummaryToClipboard();
            this.showShareCardModal = true;
            return true;
        }

        // Category Tabs
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

    public boolean handleCardModalClick(int screenW, int screenH, double mouseX, double mouseY) {
        if (!this.showShareCardModal) return false;

        int cardW = 380;
        int cardH = 282;
        int cardX = (screenW - cardW) / 2;
        int cardY = (screenH - cardH) / 2;

        // Close button (top right of card)
        int closeSize = 18;
        int closeX = cardX + cardW - closeSize - 12;
        int closeY = cardY + 12;
        if (mouseX >= closeX && mouseX <= closeX + closeSize && mouseY >= closeY && mouseY <= closeY + closeSize) {
            closeCardModal();
            return true;
        }

        // Copy button in footer
        int footerY = cardY + 228;
        int btnW = 154;
        int btnH = 26;
        int btnX = cardX + cardW - btnW - 14;
        int btnY = footerY + 11;
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            copyFormattedSummaryToClipboard();
            return true;
        }

        // If clicked inside the card, consume so background is not clicked
        if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
            return true;
        }

        // Clicked outside card on dark backdrop -> dismiss modal
        closeCardModal();
        return true;
    }

    public void drawCardModal(GuiGraphicsExtractor extractor, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        if (!this.showShareCardModal) return;

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int menuAccent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int menuSurfaceHover = cs != null ? cs.getMenuSurfaceHoverColor() : 0xFF334155;

        // Dim background
        extractor.fill(0, 0, screenW, screenH, 0xB0000000);

        int cardW = 380;
        int cardH = 282;
        int cardX = (screenW - cardW) / 2;
        int cardY = (screenH - cardH) / 2;

        // Card Drop Shadow & Background
        GuiRenderUtils.drawDropShadow(extractor, cardX, cardY, cardW, cardH, 20, 8, 160);
        GuiRenderUtils.fillRoundedRect(extractor, cardX, cardY, cardW, cardH, 8, 0xF80B132B);
        GuiRenderUtils.drawRoundedOutline(extractor, cardX, cardY, cardW, cardH, 8, menuAccent);

        // Header: Close button
        int closeSize = 18;
        int closeX = cardX + cardW - closeSize - 12;
        int closeY = cardY + 12;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + closeSize && mouseY >= closeY && mouseY <= closeY + closeSize;
        int closeBg = closeHover ? 0x44EF4444 : 0x1AFFFFFF;
        int closeOutline = closeHover ? 0xFFEF4444 : 0x33FFFFFF;
        int closeText = closeHover ? 0xFFF87171 : 0xFF94A3B8;
        GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, closeSize, closeSize, 4, closeBg);
        GuiRenderUtils.drawRoundedOutline(extractor, closeX, closeY, closeSize, closeSize, 4, closeOutline);
        GuiRenderUtils.drawCenteredText(extractor, font, "✕", closeX + closeSize / 2, closeY + (closeSize - font.lineHeight) / 2 + 1, closeText);

        // Header: Avatar
        int avatarX = cardX + 16;
        int avatarY = cardY + 14;
        int avatarSize = 36;
        GuiRenderUtils.fillRoundedRect(extractor, avatarX, avatarY, avatarSize, avatarSize, 4, 0xFF1E293B);
        Identifier skinTex = resolveSkinTexture();
        if (skinTex != null) {
            drawHead(extractor, skinTex, avatarX, avatarY, avatarSize);
        }
        GuiRenderUtils.drawRoundedOutline(extractor, avatarX - 1, avatarY - 1, avatarSize + 2, avatarSize + 2, 4, 0x44FFFFFF);

        // Header: Player Name & Badges
        int infoX = avatarX + avatarSize + 12;
        String pName = resolvePlayerName();
        GuiRenderUtils.drawText(extractor, font, pName, infoX, avatarY + 2, 0xFFFFFFFF, true);

        // Category Badge
        String badgeText = this.activeTab.icon + " " + this.activeTab.label.toUpperCase(Locale.ROOT) + " STATS";
        int pillW = font.width(badgeText) + 10;
        int pillH = 13;
        GuiRenderUtils.fillRoundedRect(extractor, infoX, avatarY + 18, pillW, pillH, 3, 0x3338BDF8);
        GuiRenderUtils.drawRoundedOutline(extractor, infoX, avatarY + 18, pillW, pillH, 3, 0x8838BDF8);
        GuiRenderUtils.drawText(extractor, font, badgeText, infoX + 5, avatarY + 20, 0xFF38BDF8);

        // Streak
        ClientStatsManager sm = ClientStatsManager.getInstance();
        int streakX = infoX + pillW + 8;
        String streakStr = "🔥 " + sm.getLoginStreak() + "d streak";
        GuiRenderUtils.drawText(extractor, font, streakStr, streakX, avatarY + 20, 0xFFF59E0B);

        // Separator below header
        int sepY = cardY + 58;
        extractor.fill(cardX + 12, sepY, cardX + cardW - 12, sepY + 1, 0x3364748B);

        // 6 Highlight Stats Grid
        GameplayStats stats = switch (this.activeTab) {
            case GLOBAL -> sm.getGlobalStats();
            case SINGLEPLAYER -> sm.getSingleplayerStats();
            case MULTIPLAYER -> sm.getMultiplayerStats();
        };

        long kills = stats.playerKills + stats.mobKills;
        double kd = stats.deaths > 0 ? ((double) kills / stats.deaths) : kills;
        String kdStr = String.format(Locale.ROOT, "%.2f", kd);
        String kdDetail = stats.playerKills + " PvP / " + stats.mobKills + " Mobs";

        List<ModalStat> modalStats = List.of(
                new ModalStat("⏱", "Total Playtime", formatPlaytime(stats.playtimeSeconds), 0xFF38BDF8, "Time in-game"),
                new ModalStat("🗡", "Kills / Deaths", String.format(Locale.ROOT, "%,d / %,d", kills, stats.deaths), 0xFFEF4444, kdDetail),
                new ModalStat("⚖", "K/D Ratio", kdStr, 0xFFF59E0B, stats.deaths == 0 ? "Flawless" : (kd >= 2.0 ? "High K/D" : "Balanced")),
                new ModalStat("🏃", "Distance", formatDistanceShort(stats.blocksTraveled), 0xFF60A5FA, "Traveled in worlds"),
                new ModalStat("⛏", "Blocks Mined", String.format(Locale.ROOT, "%,d mined", stats.blocksMined), 0xFFFBBF24, "Broken & mined"),
                new ModalStat("🧱", "Blocks Placed", String.format(Locale.ROOT, "%,d placed", stats.blocksPlaced), 0xFF34D399, "Placed in world")
        );

        int gridMarginX = 14;
        int gridStartY = sepY + 8;
        int miniGapX = 10;
        int miniGapY = 8;
        int miniW = (cardW - 2 * gridMarginX - miniGapX) / 2;
        int miniH = 46;

        for (int i = 0; i < modalStats.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            int cx = cardX + gridMarginX + col * (miniW + miniGapX);
            int cy = gridStartY + row * (miniH + miniGapY);

            GuiRenderUtils.fillRoundedRect(extractor, cx, cy, miniW, miniH, 5, 0xFF1E293B);
            GuiRenderUtils.drawRoundedOutline(extractor, cx, cy, miniW, miniH, 5, 0x2264748B);

            ModalStat stat = modalStats.get(i);
            GuiRenderUtils.drawText(extractor, font, stat.icon, cx + 8, cy + 14, 0xFFFFFFFF);
            GuiRenderUtils.drawText(extractor, font, stat.label, cx + 24, cy + 5, 0xFF94A3B8);
            GuiRenderUtils.drawText(extractor, font, stat.value, cx + 24, cy + 17, stat.color, true);
            GuiRenderUtils.drawText(extractor, font, stat.detail, cx + 24, cy + 30, 0xFF64748B);
        }

        // Footer separator line
        int footerY = cardY + 228;
        extractor.fill(cardX + 12, footerY, cardX + cardW - 12, footerY + 1, 0x3364748B);

        // Footer Branding
        GuiRenderUtils.drawText(extractor, font, "⭐ StarClient", cardX + 16, footerY + 13, 0xFF94A3B8, true);
        GuiRenderUtils.drawText(extractor, font, "Share with friends", cardX + 16, footerY + 25, 0xFF64748B);

        // Footer Copy Button
        int btnW = 154;
        int btnH = 26;
        int btnX = cardX + cardW - btnW - 14;
        int btnY = footerY + 11;
        boolean copyHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        boolean copied = (System.currentTimeMillis() - this.lastCopiedTime < 2500L);
        int btnBg = copied ? 0x3310B981 : (copyHover ? menuAccent : menuSurfaceHover);
        int btnBorder = copied ? 0xFF10B981 : (copyHover ? 0xFFFFFFFF : menuAccent);
        int btnText = copied ? 0xFF34D399 : (copyHover ? 0xFFFFFFFF : 0xFFF1F5F9);
        String btnLabel = copied ? "✓ Copied Summary!" : "📋 Copy Summary";

        GuiRenderUtils.drawDropShadow(extractor, btnX, btnY, btnW, btnH, 4, 2, 40);
        GuiRenderUtils.fillRoundedRect(extractor, btnX, btnY, btnW, btnH, 5, btnBg);
        GuiRenderUtils.drawRoundedOutline(extractor, btnX, btnY, btnW, btnH, 5, btnBorder);
        GuiRenderUtils.drawCenteredText(extractor, font, btnLabel, btnX + btnW / 2, btnY + (btnH - font.lineHeight) / 2 + 1, btnText);
    }

    public void draw(GuiGraphicsExtractor extractor, Font font, int contentLeft, int contentWidth,
                     int panelTop, int panelBottom, int mouseX, int mouseY, double scroll) {
        ClientStatsManager.getInstance().updatePlaytime(Minecraft.getInstance());
        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int menuBg = cs != null ? (0xF4000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xF40F172A;
        int menuAccent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int menuSurface = cs != null ? cs.getMenuSurfaceColor() : 0xFF1E293B;
        int menuSurfaceHover = cs != null ? cs.getMenuSurfaceHoverColor() : 0xFF334155;
        int menuBorder = cs != null ? cs.getMenuBorderColor() : 0x3364748B;

        int tabsY = panelTop + 4;
        int tabH = 26;
        int gap = 6;
        int shareBtnW = 100;
        int tabW = Math.min(115, (contentWidth - shareBtnW - gap * 3) / 3);
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

        // Share button on far right of header
        int shareBtnX = contentLeft + contentWidth - shareBtnW;
        boolean shareHovered = mouseX >= shareBtnX && mouseX <= shareBtnX + shareBtnW && mouseY >= tabsY && mouseY <= tabsY + tabH;
        boolean recentlyCopied = (System.currentTimeMillis() - this.lastCopiedTime < 2500L);
        int shareBg = recentlyCopied ? 0x3310B981 : (shareHovered ? menuSurfaceHover : menuSurface);
        int shareBorder = recentlyCopied ? 0xFF10B981 : (shareHovered ? menuAccent : menuBorder);
        int shareText = recentlyCopied ? 0xFF34D399 : (shareHovered ? 0xFFF1F5F9 : 0xFF94A3B8);
        String shareLabel = recentlyCopied ? "✓ Copied!" : "📋 Share Stats";

        GuiRenderUtils.drawDropShadow(extractor, shareBtnX, tabsY, shareBtnW, tabH, 4, 2, 50);
        GuiRenderUtils.fillRoundedRect(extractor, shareBtnX, tabsY, shareBtnW, tabH, 6, shareBg);
        GuiRenderUtils.drawRoundedOutline(extractor, shareBtnX, tabsY, shareBtnW, tabH, 6, shareBorder);
        GuiRenderUtils.drawCenteredText(extractor, font, shareLabel, shareBtnX + shareBtnW / 2, tabsY + (tabH - font.lineHeight) / 2 + 1, shareText);

        // Horizontal separator line below tabs
        extractor.fill(contentLeft, sepY, contentLeft + contentWidth, sepY + 1, menuBorder);
    }

    public String generateFormattedSummary(StatsTab tab) {
        ClientStatsManager sm = ClientStatsManager.getInstance();
        GameplayStats stats = switch (tab) {
            case GLOBAL -> sm.getGlobalStats();
            case SINGLEPLAYER -> sm.getSingleplayerStats();
            case MULTIPLAYER -> sm.getMultiplayerStats();
        };

        String playerName = resolvePlayerName();
        long kills = stats.playerKills + stats.mobKills;
        double kd = stats.deaths > 0 ? ((double) kills / stats.deaths) : kills;
        String kdStr = String.format(Locale.ROOT, "%.2f", kd);

        StringBuilder sb = new StringBuilder();
        sb.append("╭──────────────────────────────────────────────╮\n");
        sb.append("│  ⭐ STARCLIENT • PLAYER STAT CARD ⭐         │\n");
        sb.append("├──────────────────────────────────────────────┤\n");
        sb.append("│  👤 Player: ").append(playerName).append("\n");
        sb.append("│  📊 Category: ").append(tab.label).append(" Stats\n");
        if (tab == StatsTab.GLOBAL) {
            sb.append("│  🔥 Login Streak: ").append(sm.getLoginStreak()).append(" days (Best: ").append(sm.getLongestLoginStreak()).append(" days)\n");
            sb.append("│  🚀 Sessions: ").append(sm.getTimesStartedClient()).append(" launches (").append(sm.getUniqueDaysJoined()).append(" active days)\n");
        }
        sb.append("│  ⏱️ Playtime: ").append(formatPlaytime(stats.playtimeSeconds)).append("\n");
        sb.append("├──────────────────────────────────────────────┤\n");
        sb.append("│  🗡️ Combat & Survival:\n");
        sb.append(String.format(Locale.ROOT, "│    • Player Kills: %,d\n", stats.playerKills));
        sb.append(String.format(Locale.ROOT, "│    • Mob Kills: %,d\n", stats.mobKills));
        sb.append(String.format(Locale.ROOT, "│    • Deaths: %,d\n", stats.deaths));
        sb.append("│    • K/D Ratio: ").append(kdStr).append("\n");
        sb.append(String.format(Locale.ROOT, "│    • Damage Taken: %.1f ❤\n", stats.heartsLost));
        sb.append(String.format(Locale.ROOT, "│    • Health Restored: %.1f ❤\n", stats.heartsRegenerated));
        sb.append("├──────────────────────────────────────────────┤\n");
        sb.append("│  ⛏️ Exploration & Building:\n");
        sb.append("│    • Distance: ").append(formatDistance(stats.blocksTraveled)).append("\n");
        sb.append(String.format(Locale.ROOT, "│    • Blocks Mined: %,d\n", stats.blocksMined));
        sb.append(String.format(Locale.ROOT, "│    • Blocks Placed: %,d\n", stats.blocksPlaced));
        sb.append(String.format(Locale.ROOT, "│    • Items Crafted: %,d\n", stats.itemsCrafted));
        if (stats.chatMessagesSent > 0) {
            sb.append(String.format(Locale.ROOT, "│    • Chat Messages: %,d\n", stats.chatMessagesSent));
        }
        sb.append("╰──────────────────────────────────────────────╯\n");
        sb.append("✨ Generated with StarClient");

        return sb.toString();
    }

    private String resolvePlayerName() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            return client.player.getName().getString();
        }
        if (client.getUser() != null && !client.getUser().getName().isEmpty()) {
            return client.getUser().getName();
        }
        return "Player";
    }

    private Identifier resolveSkinTexture() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.getSkin() != null && client.player.getSkin().body() != null) {
            return client.player.getSkin().body().texturePath();
        }

        GameProfile profile = client.getGameProfile();
        if (profile != null) {
            PlayerSkinRenderCache cache = client.playerSkinRenderCache();
            if (cache != null) {
                String profileName = profile.name() != null ? profile.name() : "";
                if (this.cachedResolvableProfile == null || !profileName.equals(this.cachedProfileName)) {
                    this.cachedProfileName = profileName;
                    boolean hasTextures = profile.properties() != null && profile.properties().containsKey("textures");
                    this.cachedResolvableProfile = (hasTextures || profileName.isBlank())
                            ? ResolvableProfile.createResolved(profile)
                            : ResolvableProfile.createUnresolved(profileName);
                }
                PlayerSkinRenderCache.RenderInfo info = cache.getOrDefault(this.cachedResolvableProfile);
                if (info != null && info.playerSkin() != null && info.playerSkin().body() != null) {
                    return info.playerSkin().body().texturePath();
                }
            }
            return DefaultPlayerSkin.get(profile).body().texturePath();
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }

    private static void drawHead(GuiGraphicsExtractor graphics, Identifier skinTexture, int x, int y, int size) {
        graphics.blit(skinTexture, x, y, x + size, y + size,
                8 / 64.0F, 16 / 64.0F, 8 / 64.0F, 16 / 64.0F);
        graphics.blit(skinTexture, x, y, x + size, y + size,
                40 / 64.0F, 48 / 64.0F, 8 / 64.0F, 16 / 64.0F);
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

    private static String formatDistanceShort(double blocks) {
        if (blocks < 1000.0) {
            return String.format(Locale.ROOT, "%,d m", (long) blocks);
        } else {
            return String.format(Locale.ROOT, "%.2f km", blocks / 1000.0);
        }
    }
}
