package net.starclient.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.component.ResolvableProfile;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.stats.ClientStatsManager;
import net.starclient.stats.GameplayStats;
import net.starclient.stats.RewindManager;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Interactive multi-slide "Rewind of the Year" screen showcasing the player's yearly StarClient stats,
 * milestones, playtime breakdown, combat achievements, world exploration, and shareable summary card.
 */
public class RewindScreen extends Screen {

    private static final int TOTAL_SLIDES = 5;
    private static final long SLIDE_DURATION_MS = 6500L;
    private static final int CARD_WIDTH = 440;
    private static final int CARD_HEIGHT = 310;

    private final Screen parent;
    private int currentSlide = 0;
    private boolean paused = false;
    private long slideElapsedMs = 0L;
    private long lastFrameTimeMs = System.currentTimeMillis();
    private long lastCopiedTime = 0L;

    private String cachedProfileName = null;
    private ResolvableProfile cachedResolvableProfile = null;

    private record MiniCard(String icon, String label, String value, int color, String sub) {
    }

    public RewindScreen(Screen parent) {
        super(Component.literal("StarClient Rewind of the Year"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        RewindManager.getInstance().markWatched();
        ClientStatsManager.getInstance().updatePlaytime(Minecraft.getInstance());
        this.lastFrameTimeMs = System.currentTimeMillis();
        playSlideChime(1.1F);
    }

    private int getAccentColor() {
        StarClient sc = StarClient.getInstance();
        if (sc != null && sc.getClientSettings() != null) {
            return sc.getClientSettings().getMenuAccentColor();
        }
        return 0xFF38BDF8;
    }

    private void goToSlide(int index) {
        int clamped = Math.clamp(index, 0, TOTAL_SLIDES - 1);
        if (clamped != this.currentSlide) {
            this.currentSlide = clamped;
            this.slideElapsedMs = 0L;
            this.lastFrameTimeMs = System.currentTimeMillis();
            playSlideChime(1.0F + clamped * 0.12F);
        }
    }

    private void playSlideChime(float pitch) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getSoundManager() != null) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), pitch));
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        StarClient sc = StarClient.getInstance();
        ClientSettings cs = sc != null ? sc.getClientSettings() : null;

        if (client.level == null) {
            String bgMode = cs != null ? cs.getTitleBackground() : ClientSettings.BG_MODERN;
            TitleBackgroundOverlay.renderBackground(extractor, this.width, this.height, delta, bgMode);
        }

        // Dark cosmic backdrop
        extractor.fill(0, 0, this.width, this.height, 0xCC050914);

        // Update slide auto-advance timer
        long now = System.currentTimeMillis();
        long dt = Math.clamp(now - this.lastFrameTimeMs, 0L, 250L);
        this.lastFrameTimeMs = now;
        if (!this.paused && this.currentSlide < TOTAL_SLIDES - 1) {
            this.slideElapsedMs += dt;
            if (this.slideElapsedMs >= SLIDE_DURATION_MS) {
                goToSlide(this.currentSlide + 1);
            }
        }

        int accent = getAccentColor();
        float pulse = (float) (Math.sin(now / 320.0) * 0.5 + 0.5);
        int slideThemeColor = switch (this.currentSlide) {
            case 0 -> 0xFFFBBF24; // Gold (Intro)
            case 1 -> 0xFF38BDF8; // Sky Blue (Playtime)
            case 2 -> 0xFFEF4444; // Crimson (Combat)
            case 3 -> 0xFF34D399; // Emerald (World & Exploration)
            default -> accent;    // Client Accent (Summary Card)
        };
        int borderGlow = GuiRenderUtils.lerpColor(slideThemeColor, 0xFFFFFFFF, pulse * 0.35F);

        int cardW = Math.min(CARD_WIDTH, this.width - 24);
        int cardH = Math.min(CARD_HEIGHT, this.height - 24);
        int cardX = (this.width - cardW) / 2;
        int cardY = (this.height - cardH) / 2;

        // Main Rewind Card Frame
        GuiRenderUtils.drawDropShadow(extractor, cardX, cardY, cardW, cardH, 18, 7, 165);
        GuiRenderUtils.fillRoundedRect(extractor, cardX, cardY, cardW, cardH, 9, 0xF80B132B);
        GuiRenderUtils.drawRoundedOutline(extractor, cardX, cardY, cardW, cardH, 9, borderGlow);

        // Top Story Progress Bar Segments (5 bars)
        int segGap = 6;
        int segAreaW = cardW - 64; // Leave room for close button on right
        int segW = (segAreaW - segGap * (TOTAL_SLIDES - 1)) / TOTAL_SLIDES;
        int segY = cardY + 12;
        int segH = 4;

        for (int i = 0; i < TOTAL_SLIDES; i++) {
            int sx = cardX + 16 + i * (segW + segGap);
            GuiRenderUtils.fillRoundedRect(extractor, sx, segY, segW, segH, 2, 0x33FFFFFF);
            if (i < this.currentSlide) {
                GuiRenderUtils.fillRoundedRect(extractor, sx, segY, segW, segH, 2, slideThemeColor);
            } else if (i == this.currentSlide) {
                float progress = (this.currentSlide == TOTAL_SLIDES - 1)
                        ? 1.0F
                        : Math.clamp((float) this.slideElapsedMs / (float) SLIDE_DURATION_MS, 0.05F, 1.0F);
                int fillW = Math.max(4, Math.round(segW * progress));
                GuiRenderUtils.fillRoundedRect(extractor, sx, segY, fillW, segH, 2, slideThemeColor);
            }
        }

        // Top-Right "✕" Close Button
        int closeSize = 20;
        int closeX = cardX + cardW - closeSize - 12;
        int closeY = cardY + 8;
        boolean closeHover = mouseX >= closeX && mouseX <= closeX + closeSize
                && mouseY >= closeY && mouseY <= closeY + closeSize;
        GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, closeSize, closeSize, 4, closeHover ? 0x44EF4444 : 0x1AFFFFFF);
        GuiRenderUtils.drawRoundedOutline(extractor, closeX, closeY, closeSize, closeSize, 4, closeHover ? 0xFFEF4444 : 0x33FFFFFF);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕",
                closeX + closeSize / 2, closeY + (closeSize - this.font.lineHeight) / 2 + 1,
                closeHover ? 0xFFF87171 : 0xFF94A3B8);

        // Slide Content Area
        int contentTop = cardY + 26;
        int footerTop = cardY + cardH - 38;
        switch (this.currentSlide) {
            case 0 -> renderIntroSlide(extractor, cardX, contentTop, cardW, slideThemeColor);
            case 1 -> renderPlaytimeSlide(extractor, cardX, contentTop, cardW, slideThemeColor);
            case 2 -> renderCombatSlide(extractor, cardX, contentTop, cardW, slideThemeColor);
            case 3 -> renderWorldSlide(extractor, cardX, contentTop, cardW, slideThemeColor);
            default -> renderSummarySlide(extractor, cardX, contentTop, cardW, slideThemeColor, mouseX, mouseY);
        }

        // Footer Divider & Navigation Controls
        extractor.fill(cardX + 14, footerTop, cardX + cardW - 14, footerTop + 1, 0x26FFFFFF);
        renderFooterControls(extractor, cardX, footerTop + 6, cardW, slideThemeColor, mouseX, mouseY);
    }

    private void renderIntroSlide(GuiGraphicsExtractor extractor, int cardX, int topY, int cardW, int themeColor) {
        ClientStatsManager sm = ClientStatsManager.getInstance();
        int year = RewindManager.getInstance().getRewindYear();
        int centerX = cardX + cardW / 2;

        String pill = "✨ STARCLIENT " + year + " YEAR IN REVIEW ✨";
        GuiRenderUtils.drawCenteredText(extractor, this.font, pill, centerX, topY + 4, themeColor);

        // Avatar with glowing frame
        int avSize = 40;
        int avX = centerX - avSize / 2;
        int avY = topY + 20;
        GuiRenderUtils.fillRoundedRect(extractor, avX - 2, avY - 2, avSize + 4, avSize + 4, 6, 0x33FBBF24);
        GuiRenderUtils.drawRoundedOutline(extractor, avX - 2, avY - 2, avSize + 4, avSize + 4, 6, themeColor);
        drawHead(extractor, resolveSkinTexture(), avX, avY, avSize);

        String playerName = resolvePlayerName();
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Welcome back, " + playerName + "!", centerX, avY + avSize + 8, 0xFFFFFFFF);
        GuiRenderUtils.drawCenteredText(extractor, this.font,
                "Playing since " + sm.getFirstLaunchDate() + " • Here are your highlights of the year",
                centerX, avY + avSize + 20, 0xFF94A3B8);

        List<MiniCard> cards = List.of(
                new MiniCard("🚀", "Client Launches", String.format(Locale.ROOT, "%,d times", sm.getTimesStartedClient()), 0xFF38BDF8, "Total sessions started"),
                new MiniCard("📅", "Active Days", String.format(Locale.ROOT, "%,d days", sm.getUniqueDaysJoined()), 0xFF34D399, "Unique days played"),
                new MiniCard("🔥", "Current Streak", sm.getLoginStreak() + " days", 0xFFF59E0B, "Consecutive daily logins"),
                new MiniCard("🏆", "Longest Streak", sm.getLongestLoginStreak() + " days", 0xFFFBBF24, "All-time best login streak")
        );
        drawMiniCardGrid(extractor, cardX + 16, topY + 98, cardW - 32, cards);
    }

    private void renderPlaytimeSlide(GuiGraphicsExtractor extractor, int cardX, int topY, int cardW, int themeColor) {
        ClientStatsManager sm = ClientStatsManager.getInstance();
        GameplayStats global = sm.getGlobalStats();
        GameplayStats sp = sm.getSingleplayerStats();
        GameplayStats mp = sm.getMultiplayerStats();
        long menuSec = sm.getMenuPlaytimeSeconds();
        int centerX = cardX + cardW / 2;

        GuiRenderUtils.drawCenteredText(extractor, this.font, "⏱ TIME WELL SPENT", centerX, topY + 4, themeColor);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Total Time on StarClient", centerX, topY + 18, 0xFF94A3B8);

        String totalStr = formatPlaytime(global.playtimeSeconds);
        GuiRenderUtils.drawCenteredText(extractor, this.font, totalStr, centerX, topY + 32, 0xFFFFFFFF);

        String titleBadge = global.playtimeSeconds >= 360000 ? "🌟 Dedicated Legend"
                : (global.playtimeSeconds >= 36000 ? "⚡ Core Grinder" : "🚀 Rising Star");
        GuiRenderUtils.drawCenteredText(extractor, this.font, titleBadge, centerX, topY + 46, 0xFFFBBF24);

        // Visual Playtime Breakdown Bar
        int barX = cardX + 24;
        int barY = topY + 64;
        int barW = cardW - 48;
        int barH = 10;
        long total = Math.max(1L, global.playtimeSeconds);
        int mpW = Math.round(barW * (mp.playtimeSeconds / (float) total));
        int spW = Math.round(barW * (sp.playtimeSeconds / (float) total));
        int menuW = Math.max(0, barW - mpW - spW);

        GuiRenderUtils.fillRoundedRect(extractor, barX, barY, barW, barH, 3, 0xFF1E293B);
        if (mpW > 0) extractor.fill(barX, barY, barX + mpW, barY + barH, 0xFF38BDF8);
        if (spW > 0) extractor.fill(barX + mpW, barY, barX + mpW + spW, barY + barH, 0xFF34D399);
        if (menuW > 0) extractor.fill(barX + mpW + spW, barY, barX + barW, barY + barH, 0xFFA78BFA);
        GuiRenderUtils.drawRoundedOutline(extractor, barX, barY, barW, barH, 3, 0x44FFFFFF);

        List<MiniCard> cards = List.of(
                new MiniCard("👥", "Multiplayer Time", formatPlaytime(mp.playtimeSeconds), 0xFF38BDF8, String.format(Locale.ROOT, "%,d servers joined", mp.serversJoined)),
                new MiniCard("👤", "Singleplayer Time", formatPlaytime(sp.playtimeSeconds), 0xFF34D399, String.format(Locale.ROOT, "%,d solo worlds", sp.worldsJoined)),
                new MiniCard("🖥", "Menus & Tweaking", formatPlaytime(menuSec), 0xFFA78BFA, "Customizing HUD & settings"),
                new MiniCard("🌍", "Total Sessions", String.format(Locale.ROOT, "%,d worlds/servers", mp.serversJoined + sp.worldsJoined), 0xFFFBBF24, "Combined world entries")
        );
        drawMiniCardGrid(extractor, cardX + 16, topY + 98, cardW - 32, cards);
    }

    private void renderCombatSlide(GuiGraphicsExtractor extractor, int cardX, int topY, int cardW, int themeColor) {
        ClientStatsManager sm = ClientStatsManager.getInstance();
        GameplayStats global = sm.getGlobalStats();
        long totalKills = global.playerKills + global.mobKills;
        double kd = global.deaths > 0 ? ((double) totalKills / global.deaths) : totalKills;
        int centerX = cardX + cardW / 2;

        GuiRenderUtils.drawCenteredText(extractor, this.font, "⚔ COMBAT & BATTLES", centerX, topY + 4, themeColor);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Your Year on the Battlefield", centerX, topY + 18, 0xFF94A3B8);

        String headline = String.format(Locale.ROOT, "%,d Total Eliminations  •  %.2f K/D", totalKills, kd);
        GuiRenderUtils.drawCenteredText(extractor, this.font, headline, centerX, topY + 36, 0xFFFFFFFF);

        String combatRank = kd >= 3.0 ? "👑 Unstoppable Warlord"
                : (totalKills >= 500 ? "🗡 Relentless Slayer" : "🛡 Steadfast Fighter");
        GuiRenderUtils.drawCenteredText(extractor, this.font, combatRank, centerX, topY + 54, 0xFFF87171);

        List<MiniCard> cards = List.of(
                new MiniCard("🗡", "Player Kills (PvP)", String.format(Locale.ROOT, "%,d kills", global.playerKills), 0xFFEF4444, "Opponents defeated"),
                new MiniCard("👾", "Mob Kills (PvE)", String.format(Locale.ROOT, "%,d mobs", global.mobKills), 0xFFF97316, "Creatures & monsters slain"),
                new MiniCard("💀", "Total Deaths", String.format(Locale.ROOT, "%,d deaths", global.deaths), 0xFF94A3B8, String.format(Locale.ROOT, "Overall K/D: %.2f", kd)),
                new MiniCard("❤", "Hearts Lost / Regen", String.format(Locale.ROOT, "%,.0f ❤ / %,.0f 💖", global.heartsLost, global.heartsRegenerated), 0xFFF43F5E, "Damage taken & healed")
        );
        drawMiniCardGrid(extractor, cardX + 16, topY + 98, cardW - 32, cards);
    }

    private void renderWorldSlide(GuiGraphicsExtractor extractor, int cardX, int topY, int cardW, int themeColor) {
        ClientStatsManager sm = ClientStatsManager.getInstance();
        GameplayStats global = sm.getGlobalStats();
        int centerX = cardX + cardW / 2;

        GuiRenderUtils.drawCenteredText(extractor, this.font, "🌍 BUILDER & EXPLORER", centerX, topY + 4, themeColor);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Across Every Block & Horizon", centerX, topY + 18, 0xFF94A3B8);

        String distStr = formatDistance(global.blocksTraveled);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "🏃 " + distStr + " Traveled", centerX, topY + 36, 0xFFFFFFFF);

        long totalBlocks = global.blocksMined + global.blocksPlaced;
        GuiRenderUtils.drawCenteredText(extractor, this.font,
                String.format(Locale.ROOT, "%,d Total Blocks Mined & Placed", totalBlocks),
                centerX, topY + 54, 0xFF34D399);

        List<MiniCard> cards = List.of(
                new MiniCard("⛏", "Blocks Mined", String.format(Locale.ROOT, "%,d blocks", global.blocksMined), 0xFFFBBF24, "Excavated & harvested"),
                new MiniCard("🧱", "Blocks Placed", String.format(Locale.ROOT, "%,d blocks", global.blocksPlaced), 0xFF34D399, "Built & bridged"),
                new MiniCard("🔨", "Crafted & Eaten", String.format(Locale.ROOT, "%,d crafted • %,d eaten", global.itemsCrafted, global.timesEaten), 0xFFA78BFA, "Items forged & food consumed"),
                new MiniCard("💬", "Social & Chat", String.format(Locale.ROOT, "%,d messages sent", global.chatMessagesSent), 0xFF38BDF8, String.format(Locale.ROOT, "%,d worlds created", global.worldsCreated))
        );
        drawMiniCardGrid(extractor, cardX + 16, topY + 98, cardW - 32, cards);
    }

    private void renderSummarySlide(GuiGraphicsExtractor extractor, int cardX, int topY, int cardW, int themeColor,
                                    int mouseX, int mouseY) {
        ClientStatsManager sm = ClientStatsManager.getInstance();
        GameplayStats global = sm.getGlobalStats();
        int year = RewindManager.getInstance().getRewindYear();

        // Player Header inside Summary
        int avX = cardX + 20;
        int avY = topY + 4;
        int avSize = 32;
        drawHead(extractor, resolveSkinTexture(), avX, avY, avSize);
        GuiRenderUtils.drawRoundedOutline(extractor, avX - 1, avY - 1, avSize + 2, avSize + 2, 4, themeColor);

        String name = resolvePlayerName();
        GuiRenderUtils.drawText(extractor, this.font, name + "'s " + year + " Rewind", avX + avSize + 10, avY + 4, 0xFFFFFFFF, true);
        GuiRenderUtils.drawText(extractor, this.font,
                "🔥 Best Streak: " + sm.getLongestLoginStreak() + "d  •  🚀 " + sm.getTimesStartedClient() + " Launches",
                avX + avSize + 10, avY + 18, 0xFFFBBF24);

        // Copy Rewind Button in top-right of summary slide
        int copyW = 112;
        int copyH = 22;
        int copyX = cardX + cardW - copyW - 18;
        int copyY = avY + 5;
        boolean copyHover = mouseX >= copyX && mouseX <= copyX + copyW && mouseY >= copyY && mouseY <= copyY + copyH;
        boolean copied = (System.currentTimeMillis() - this.lastCopiedTime < 2500L);
        int copyBg = copied ? 0x3310B981 : (copyHover ? themeColor : 0xFF1E293B);
        int copyBorder = copied ? 0xFF10B981 : (copyHover ? 0xFFFFFFFF : themeColor);
        String copyLabel = copied ? "✓ Copied!" : "📋 Copy Rewind";

        GuiRenderUtils.fillRoundedRect(extractor, copyX, copyY, copyW, copyH, 5, copyBg);
        GuiRenderUtils.drawRoundedOutline(extractor, copyX, copyY, copyW, copyH, 5, copyBorder);
        GuiRenderUtils.drawCenteredText(extractor, this.font, copyLabel,
                copyX + copyW / 2, copyY + (copyH - this.font.lineHeight) / 2 + 1, 0xFFFFFFFF);

        long kills = global.playerKills + global.mobKills;
        double kd = global.deaths > 0 ? ((double) kills / global.deaths) : kills;

        List<MiniCard> summaryCards = List.of(
                new MiniCard("⏱", "Total Playtime", formatPlaytime(global.playtimeSeconds), 0xFF38BDF8, sm.getUniqueDaysJoined() + " active days"),
                new MiniCard("🗡", "Total Kills", String.format(Locale.ROOT, "%,d kills", kills), 0xFFEF4444, global.playerKills + " PvP / " + global.mobKills + " PvE"),
                new MiniCard("⚖", "K/D Ratio", String.format(Locale.ROOT, "%.2f K/D", kd), 0xFFF59E0B, global.deaths + " total deaths"),
                new MiniCard("🏃", "Distance", formatDistanceShort(global.blocksTraveled), 0xFF60A5FA, "Total blocks traveled"),
                new MiniCard("⛏", "Blocks Mined", String.format(Locale.ROOT, "%,d", global.blocksMined), 0xFFFBBF24, "Mined this year"),
                new MiniCard("🧱", "Blocks Placed", String.format(Locale.ROOT, "%,d", global.blocksPlaced), 0xFF34D399, "Placed this year")
        );

        int gridX = cardX + 16;
        int gridY = topY + 44;
        int gapX = 8;
        int gapY = 6;
        int cols = 2;
        int cellW = (cardW - 32 - gapX) / cols;
        int cellH = 44;

        for (int i = 0; i < summaryCards.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = gridX + col * (cellW + gapX);
            int cy = gridY + row * (cellH + gapY);
            MiniCard mc = summaryCards.get(i);

            GuiRenderUtils.fillRoundedRect(extractor, cx, cy, cellW, cellH, 5, 0xFF162036);
            GuiRenderUtils.drawRoundedOutline(extractor, cx, cy, cellW, cellH, 5, 0x26FFFFFF);
            GuiRenderUtils.drawText(extractor, this.font, mc.icon, cx + 8, cy + 14, 0xFFFFFFFF);
            GuiRenderUtils.drawText(extractor, this.font, mc.label, cx + 24, cy + 5, 0xFF94A3B8);
            GuiRenderUtils.drawText(extractor, this.font, mc.value, cx + 24, cy + 17, mc.color, true);
            GuiRenderUtils.drawText(extractor, this.font, mc.sub, cx + 24, cy + 29, 0xFF64748B);
        }
    }

    private void drawMiniCardGrid(GuiGraphicsExtractor extractor, int gridX, int gridY, int totalW, List<MiniCard> cards) {
        int gapX = 10;
        int gapY = 8;
        int cellW = (totalW - gapX) / 2;
        int cellH = 52;

        for (int i = 0; i < cards.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            int cx = gridX + col * (cellW + gapX);
            int cy = gridY + row * (cellH + gapY);
            MiniCard c = cards.get(i);

            GuiRenderUtils.fillRoundedRect(extractor, cx, cy, cellW, cellH, 6, 0xFF162036);
            GuiRenderUtils.drawRoundedOutline(extractor, cx, cy, cellW, cellH, 6, 0x28FFFFFF);
            GuiRenderUtils.drawText(extractor, this.font, c.icon, cx + 10, cy + 16, 0xFFFFFFFF);
            GuiRenderUtils.drawText(extractor, this.font, c.label, cx + 28, cy + 7, 0xFF94A3B8);
            GuiRenderUtils.drawText(extractor, this.font, c.value, cx + 28, cy + 20, c.color, true);
            GuiRenderUtils.drawText(extractor, this.font, c.sub, cx + 28, cy + 34, 0xFF64748B);
        }
    }

    private void renderFooterControls(GuiGraphicsExtractor extractor, int cardX, int y, int cardW, int themeColor,
                                      int mouseX, int mouseY) {
        int btnH = 24;
        int prevW = 72;
        int pauseW = 78;
        int nextW = 86;

        int prevX = cardX + 16;
        int pauseX = cardX + (cardW - pauseW) / 2;
        int nextX = cardX + cardW - nextW - 16;

        // Prev Button
        boolean canPrev = this.currentSlide > 0;
        boolean prevHover = canPrev && mouseX >= prevX && mouseX <= prevX + prevW && mouseY >= y && mouseY <= y + btnH;
        GuiRenderUtils.fillRoundedRect(extractor, prevX, y, prevW, btnH, 5, prevHover ? 0xFF334155 : 0xFF1E293B);
        GuiRenderUtils.drawRoundedOutline(extractor, prevX, y, prevW, btnH, 5, prevHover ? themeColor : 0x33FFFFFF);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "◀ Prev",
                prevX + prevW / 2, y + (btnH - this.font.lineHeight) / 2 + 1,
                canPrev ? 0xFFF1F5F9 : 0xFF475569);

        // Pause / Play Button
        boolean pauseHover = mouseX >= pauseX && mouseX <= pauseX + pauseW && mouseY >= y && mouseY <= y + btnH;
        String pauseLabel = this.paused ? "▶ Auto" : "⏸ Pause";
        GuiRenderUtils.fillRoundedRect(extractor, pauseX, y, pauseW, btnH, 5, pauseHover ? 0xFF334155 : 0xFF1E293B);
        GuiRenderUtils.drawRoundedOutline(extractor, pauseX, y, pauseW, btnH, 5, pauseHover ? themeColor : 0x33FFFFFF);
        GuiRenderUtils.drawCenteredText(extractor, this.font, pauseLabel,
                pauseX + pauseW / 2, y + (btnH - this.font.lineHeight) / 2 + 1, 0xFFE2E8F0);

        // Next / Replay / Done Button
        boolean isLast = (this.currentSlide == TOTAL_SLIDES - 1);
        String nextLabel = isLast ? "↻ Replay" : "Next ▶";
        boolean nextHover = mouseX >= nextX && mouseX <= nextX + nextW && mouseY >= y && mouseY <= y + btnH;
        int nextBg = nextHover ? themeColor : (0x38000000 | (themeColor & 0x00FFFFFF));
        GuiRenderUtils.fillRoundedRect(extractor, nextX, y, nextW, btnH, 5, nextBg);
        GuiRenderUtils.drawRoundedOutline(extractor, nextX, y, nextW, btnH, 5, nextHover ? 0xFFFFFFFF : themeColor);
        GuiRenderUtils.drawCenteredText(extractor, this.font, nextLabel,
                nextX + nextW / 2, y + (btnH - this.font.lineHeight) / 2 + 1, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();

            int cardW = Math.min(CARD_WIDTH, this.width - 24);
            int cardH = Math.min(CARD_HEIGHT, this.height - 24);
            int cardX = (this.width - cardW) / 2;
            int cardY = (this.height - cardH) / 2;

            // 1. Check Close "✕" Button
            int closeSize = 20;
            int closeX = cardX + cardW - closeSize - 12;
            int closeY = cardY + 8;
            if (mouseX >= closeX && mouseX <= closeX + closeSize
                    && mouseY >= closeY && mouseY <= closeY + closeSize) {
                closeRewind();
                return true;
            }

            // 2. Check Top Story Progress Bar Segments
            int segGap = 6;
            int segAreaW = cardW - 64;
            int segW = (segAreaW - segGap * (TOTAL_SLIDES - 1)) / TOTAL_SLIDES;
            int segY = cardY + 12;
            for (int i = 0; i < TOTAL_SLIDES; i++) {
                int sx = cardX + 16 + i * (segW + segGap);
                if (mouseX >= sx && mouseX <= sx + segW && mouseY >= segY - 4 && mouseY <= segY + 10) {
                    goToSlide(i);
                    return true;
                }
            }

            // 3. Check Copy Rewind Button on Summary Slide (Slide 4)
            if (this.currentSlide == TOTAL_SLIDES - 1) {
                int avY = cardY + 26 + 4;
                int copyW = 112;
                int copyH = 22;
                int copyX = cardX + cardW - copyW - 18;
                int copyY = avY + 5;
                if (mouseX >= copyX && mouseX <= copyX + copyW && mouseY >= copyY && mouseY <= copyY + copyH) {
                    copyRewindSummary();
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }

            // 4. Check Footer Navigation Buttons
            int footerY = cardY + cardH - 32;
            int btnH = 24;
            int prevW = 72;
            int pauseW = 78;
            int nextW = 86;
            int prevX = cardX + 16;
            int pauseX = cardX + (cardW - pauseW) / 2;
            int nextX = cardX + cardW - nextW - 16;

            if (this.currentSlide > 0 && mouseX >= prevX && mouseX <= prevX + prevW && mouseY >= footerY && mouseY <= footerY + btnH) {
                goToSlide(this.currentSlide - 1);
                return true;
            }
            if (mouseX >= pauseX && mouseX <= pauseX + pauseW && mouseY >= footerY && mouseY <= footerY + btnH) {
                this.paused = !this.paused;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mouseX >= nextX && mouseX <= nextX + nextW && mouseY >= footerY && mouseY <= footerY + btnH) {
                if (this.currentSlide == TOTAL_SLIDES - 1) {
                    goToSlide(0);
                } else {
                    goToSlide(this.currentSlide + 1);
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            closeRewind();
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
            if (this.currentSlide < TOTAL_SLIDES - 1) {
                goToSlide(this.currentSlide + 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
            if (this.currentSlide > 0) {
                goToSlide(this.currentSlide - 1);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE) {
            this.paused = !this.paused;
            return true;
        }
        return super.keyPressed(event);
    }

    private void copyRewindSummary() {
        ClientStatsManager sm = ClientStatsManager.getInstance();
        GameplayStats global = sm.getGlobalStats();
        int year = RewindManager.getInstance().getRewindYear();
        long kills = global.playerKills + global.mobKills;
        double kd = global.deaths > 0 ? ((double) kills / global.deaths) : kills;

        String summary = String.format(Locale.ROOT,
                "╭──────────────────────────────────────────────╮\n" +
                "│  ✨ STARCLIENT %d REWIND OF THE YEAR ✨    │\n" +
                "├──────────────────────────────────────────────┤\n" +
                "│  👤 Player: %s\n" +
                "│  ⏱️ Total Playtime: %s\n" +
                "│  🔥 Best Streak: %d days (%d active days)\n" +
                "│  🗡️ Eliminations: %,d (%.2f K/D)\n" +
                "│  🏃 Distance Traveled: %s\n" +
                "│  ⛏️ Blocks Mined: %,d  •  🧱 Placed: %,d\n" +
                "╰──────────────────────────────────────────────╯",
                year,
                resolvePlayerName(),
                formatPlaytime(global.playtimeSeconds),
                sm.getLongestLoginStreak(),
                sm.getUniqueDaysJoined(),
                kills,
                kd,
                formatDistanceShort(global.blocksTraveled),
                global.blocksMined,
                global.blocksPlaced
        );
        Minecraft.getInstance().keyboardHandler.setClipboard(summary);
        this.lastCopiedTime = System.currentTimeMillis();
    }

    private void closeRewind() {
        Minecraft client = Minecraft.getInstance();
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (this.parent != null) {
            client.gui.setScreen(this.parent);
        } else {
            this.onClose();
        }
    }

    private String resolvePlayerName() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            return client.player.getName().getString();
        }
        if (!client.getUser().getName().isEmpty()) {
            return client.getUser().getName();
        }
        return "Player";
    }

    private Identifier resolveSkinTexture() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            return client.player.getSkin().body().texturePath();
        }
        GameProfile profile = client.getGameProfile();
        PlayerSkinRenderCache cache = client.playerSkinRenderCache();
        String profileName = profile.name() != null ? profile.name() : "";
        if (this.cachedResolvableProfile == null || !profileName.equals(this.cachedProfileName)) {
            this.cachedProfileName = profileName;
            boolean hasTextures = profile.properties() != null && profile.properties().containsKey("textures");
            this.cachedResolvableProfile = (hasTextures || profileName.isBlank())
                    ? ResolvableProfile.createResolved(profile)
                    : ResolvableProfile.createUnresolved(profileName);
        }
        PlayerSkinRenderCache.RenderInfo info = cache.getOrDefault(this.cachedResolvableProfile);
        return info.playerSkin().body().texturePath();
    }

    private static void drawHead(GuiGraphicsExtractor graphics, Identifier skinTexture, int x, int y, int size) {
        graphics.blit(skinTexture, x, y, x + size, y + size,
                8 / 64.0F, 16 / 64.0F, 8 / 64.0F, 16 / 64.0F);
        graphics.blit(skinTexture, x, y, x + size, y + size,
                40 / 64.0F, 48 / 64.0F, 8 / 64.0F, 16 / 64.0F);
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
        }
        return String.format(Locale.ROOT, "%.2f km (%,d blocks)", blocks / 1000.0, (long) blocks);
    }

    private static String formatDistanceShort(double blocks) {
        if (blocks < 1000.0) {
            return String.format(Locale.ROOT, "%,d m", (long) blocks);
        }
        return String.format(Locale.ROOT, "%.2f km", blocks / 1000.0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
