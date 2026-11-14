package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.starclient.StarClient;
import net.starclient.module.BlockOutlineState;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.ModeSetting;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bedwars Generator Timers HUD Module (SERVER / UI):
 * Tracks Hypixel & Bedwars Diamond and Emerald island generator spawn cycles with
 * live countdown timers, upgrade event progression, audio chimes, and smooth progress bars.
 */
public final class BedwarsTimersRenderer {

    public static final int DEFAULT_WIDTH = 136;
    public static final int DEFAULT_HEIGHT = 66;

    private static ItemStack iconDiamond;
    private static ItemStack iconEmerald;

    private static ItemStack getDiamondIcon() {
        if (iconDiamond == null) iconDiamond = new ItemStack(Items.DIAMOND);
        return iconDiamond;
    }

    private static ItemStack getEmeraldIcon() {
        if (iconEmerald == null) iconEmerald = new ItemStack(Items.EMERALD);
        return iconEmerald;
    }

    private static final Pattern EVENT_PATTERN = Pattern.compile(
            "(?i)(diamond\\s*ii|emerald\\s*ii|diamond\\s*iii|emerald\\s*iii|bed\\s*destruction|beds?\\s*gone|sudden\\s*death|dragons|game\\s*end)[^0-9]*(\\d+):(\\d+)"
    );

    // State tracking
    private static long matchStartTime = -1L;
    private static int diamondTier = 1;
    private static int emeraldTier = 1;
    private static String nextEventName = "Diamond II";
    private static int nextEventSeconds = 360;
    private static boolean inBedwarsMatch = false;

    private static long lastDiamondAlertTime = 0L;
    private static long lastEmeraldAlertTime = 0L;
    private static long lastScoreboardSyncTime = 0L;

    private BedwarsTimersRenderer() {
    }

    public static int getWidth(Module mod) {
        boolean compact = "Compact".equals(mod != null && mod.getSetting("Display Mode") != null
                ? mod.getSetting("Display Mode").getValue() : "");
        return compact ? 128 : DEFAULT_WIDTH;
    }

    public static int getHeight(Module mod) {
        boolean compact = "Compact".equals(mod != null && mod.getSetting("Display Mode") != null
                ? mod.getSetting("Display Mode").getValue() : "");
        if (compact) return 20;
        boolean showNext = !(mod != null && mod.getSetting("Show Next Event") instanceof BooleanSetting bs) || bs.getValue();
        return showNext ? DEFAULT_HEIGHT : 52;
    }

    public static void reset() {
        matchStartTime = -1L;
        diamondTier = 1;
        emeraldTier = 1;
        nextEventName = "Diamond II";
        nextEventSeconds = 360;
        inBedwarsMatch = false;
        lastDiamondAlertTime = 0L;
        lastEmeraldAlertTime = 0L;
        lastScoreboardSyncTime = 0L;
    }

    public static void update(Minecraft client) {
        if (client == null || client.level == null) {
            inBedwarsMatch = false;
            return;
        }

        long now = System.currentTimeMillis();
        Scoreboard scoreboard = client.level.getScoreboard();
        if (scoreboard == null) return;

        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return;

        String title = objective.getDisplayName().getString().toLowerCase(Locale.ROOT);
        inBedwarsMatch = title.contains("bed wars") || title.contains("bedwars") || title.contains("bw");

        Collection<PlayerScoreEntry> scores = scoreboard.listPlayerScores(objective);
        if (scores.isEmpty()) return;

        for (PlayerScoreEntry entry : scores) {
            if (entry.isHidden()) continue;
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            String rawLine = PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString();
            String plainLine = rawLine.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();

            Matcher matcher = EVENT_PATTERN.matcher(plainLine);
            if (matcher.find()) {
                String eventKey = matcher.group(1).toLowerCase(Locale.ROOT);
                int mins = Integer.parseInt(matcher.group(2));
                int secs = Integer.parseInt(matcher.group(3));
                int totalSecs = mins * 60 + secs;

                nextEventSeconds = totalSecs;

                int baseMilestone;
                if (eventKey.contains("diamond") && eventKey.contains("ii")) {
                    diamondTier = 1;
                    emeraldTier = 1;
                    nextEventName = "Diamond II";
                    baseMilestone = 360; // 6:00
                } else if (eventKey.contains("emerald") && eventKey.contains("ii")) {
                    diamondTier = 2;
                    emeraldTier = 1;
                    nextEventName = "Emerald II";
                    baseMilestone = 720; // 12:00
                } else if (eventKey.contains("diamond") && eventKey.contains("iii")) {
                    diamondTier = 2;
                    emeraldTier = 2;
                    nextEventName = "Diamond III";
                    baseMilestone = 1080; // 18:00
                } else if (eventKey.contains("emerald") && eventKey.contains("iii")) {
                    diamondTier = 3;
                    emeraldTier = 2;
                    nextEventName = "Emerald III";
                    baseMilestone = 1440; // 24:00
                } else if (eventKey.contains("bed") || eventKey.contains("destruction") || eventKey.contains("gone")) {
                    diamondTier = 3;
                    emeraldTier = 3;
                    nextEventName = "Bed Destroy";
                    baseMilestone = 1800; // 30:00
                } else if (eventKey.contains("sudden") || eventKey.contains("death") || eventKey.contains("dragon")) {
                    diamondTier = 3;
                    emeraldTier = 3;
                    nextEventName = "Sudden Death";
                    baseMilestone = 2400; // 40:00
                } else {
                    diamondTier = 3;
                    emeraldTier = 3;
                    nextEventName = "Game End";
                    baseMilestone = 3000;
                }

                // Synchronize match start time directly to server countdown
                int elapsed = Math.max(0, baseMilestone - totalSecs);
                matchStartTime = now - (elapsed * 1000L);
                lastScoreboardSyncTime = now;
                break;
            }
        }
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod, int x, int y) {
        if (client == null || mod == null) return;

        update(client);

        boolean onlyBedwars = mod.getSetting("Only In Bedwars") instanceof BooleanSetting bs && bs.getValue();
        if (onlyBedwars && !inBedwarsMatch) {
            return;
        }

        renderInternal(extractor, client.font, mod, x, y, false);
    }

    public static void renderPreview(GuiGraphicsExtractor extractor, Module mod, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc != null ? mc.font : null;
        if (font == null) return;

        renderInternal(extractor, font, mod, x, y, true);
    }

    private static void renderInternal(GuiGraphicsExtractor extractor, Font font, Module mod,
                                       int x, int y, boolean isPreview) {
        long now = System.currentTimeMillis();

        // If no match start recorded, initialize
        if (matchStartTime <= 0L) {
            matchStartTime = now;
        }

        long elapsedSec = isPreview ? 142L : Math.max(0L, (now - matchStartTime) / 1000L);
        int curDTier = isPreview ? 2 : diamondTier;
        int curETier = isPreview ? 1 : emeraldTier;
        String curNextName = isPreview ? "Diamond II" : nextEventName;
        int curNextSec = isPreview ? 218 : nextEventSeconds;

        // Intervals per tier
        int dInterval = (curDTier == 1) ? 30 : (curDTier == 2 ? 23 : 12);
        int eInterval = (curETier == 1) ? 60 : (curETier == 2 ? 45 : 30);

        int dLeft = isPreview ? 14 : (dInterval - (int) (elapsedSec % dInterval));
        if (dLeft == dInterval) dLeft = 0;
        int eLeft = isPreview ? 32 : (eInterval - (int) (elapsedSec % eInterval));
        if (eLeft == eInterval) eLeft = 0;

        float subSec = (now % 1000L) / 1000.0F;
        float dProgress = isPreview ? 0.65F : Mth.clamp((dInterval - (dLeft - subSec)) / (float) dInterval, 0.0F, 1.0F);
        float eProgress = isPreview ? 0.45F : Mth.clamp((eInterval - (eLeft - subSec)) / (float) eInterval, 0.0F, 1.0F);

        // Sound Alerts (3 seconds before spawn)
        if (!isPreview) {
            boolean audioChimes = !(mod.getSetting("Audio Chimes") instanceof BooleanSetting bs) || bs.getValue();
            if (audioChimes) {
                String soundType = mod.getSetting("Sound Type") instanceof ModeSetting ms ? ms.getValue() : "Pling";
                if (dLeft == 3 && now - lastDiamondAlertTime > 4000L) {
                    lastDiamondAlertTime = now;
                    playChime(Minecraft.getInstance(), soundType, 1.2F);
                }
                if (eLeft == 3 && now - lastEmeraldAlertTime > 4000L) {
                    lastEmeraldAlertTime = now;
                    playChime(Minecraft.getInstance(), soundType, 1.6F);
                }
            }
        }

        // Color & Theme settings
        boolean useClientColor = !(mod.getSetting("Use Client Color") instanceof BooleanSetting bs) || bs.getValue();
        int accent;
        if (useClientColor && StarClient.getInstance() != null && StarClient.getInstance().getClientSettings() != null) {
            accent = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        } else if (mod.getSetting("Color") instanceof ColorModeSetting cms) {
            accent = cms.getColor();
        } else {
            accent = BlockOutlineState.OUTLINE_COLORS.getOrDefault("Sky Blue", 0xFF38BDF8);
        }

        int headerColor = accent | 0xFF000000;
        int cardOutline = (accent & 0x00FFFFFF) | 0x33000000;

        String displayMode = mod.getSetting("Display Mode") instanceof ModeSetting ms ? ms.getValue() : "Full Card";
        boolean compact = "Compact".equalsIgnoreCase(displayMode);
        boolean showBar = !(mod.getSetting("Show Progress Bar") instanceof BooleanSetting bs) || bs.getValue();
        boolean showTiers = !(mod.getSetting("Show Tier Badges") instanceof BooleanSetting bs) || bs.getValue();
        boolean showNext = !(mod.getSetting("Show Next Event") instanceof BooleanSetting bs) || bs.getValue();

        int cardW = getWidth(mod);
        int cardH = getHeight(mod);

        // Render Background Frame
        if (!mod.isTransparentBackground()) {
            GuiRenderUtils.drawDropShadow(extractor, x, y, cardW, cardH, 5, 2, 85);
            int bgColor = (mod.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
            GuiRenderUtils.fillRoundedRect(extractor, x, y, cardW, cardH, 5, bgColor);
        }
        GuiRenderUtils.drawRoundedOutline(extractor, x, y, cardW, cardH, 5, cardOutline);

        if (compact) {
            // Compact pill view: [ICON] 14s [II] | [ICON] 32s [I]
            extractor.item(getDiamondIcon(), x + 3, y + 2);
            String dStr = dLeft + "s" + (showTiers ? " " + tierRoman(curDTier) : "");
            GuiRenderUtils.drawText(extractor, font, dStr, x + 21, y + 6, 0xFF38BDF8, true);

            extractor.fill(x + 64, y + 3, x + 65, y + cardH - 3, 0x22FFFFFF);

            extractor.item(getEmeraldIcon(), x + 68, y + 2);
            String eStr = eLeft + "s" + (showTiers ? " " + tierRoman(curETier) : "");
            GuiRenderUtils.drawText(extractor, font, eStr, x + 86, y + 6, 0xFF34D399, true);
        } else {
            // Full card view
            // Header
            GuiRenderUtils.drawText(extractor, font, "💎 Generators", x + 6, y + 3, headerColor, true);
            extractor.fill(x + 5, y + 13, x + cardW - 5, y + 14, 0x1FFFFFFF);

            // Diamond Row
            int dY = y + 16;
            extractor.item(getDiamondIcon(), x + 3, dY);
            String dTitle = "Diamond" + (showTiers ? " " + tierRoman(curDTier) : "");
            GuiRenderUtils.drawText(extractor, font, dTitle, x + 22, dY + 1, 0xFFE2E8F0, true);
            String dCountdown = dLeft + "s";
            int dCountW = font.width(dCountdown);
            GuiRenderUtils.drawText(extractor, font, dCountdown, x + cardW - dCountW - 6, dY + 1, 0xFF38BDF8, true);

            if (showBar) {
                int barX = x + 22;
                int barY = dY + 11;
                int barW = cardW - barX - 6;
                int barH = 3;
                GuiRenderUtils.fillRoundedRect(extractor, barX, barY, barW, barH, 1, 0x44000000);
                int fillW = Math.max(1, (int) (barW * dProgress));
                GuiRenderUtils.fillRoundedRect(extractor, barX, barY, fillW, barH, 1, 0xFF38BDF8);
            }

            // Emerald Row
            int eY = y + 33;
            extractor.item(getEmeraldIcon(), x + 3, eY);
            String eTitle = "Emerald" + (showTiers ? " " + tierRoman(curETier) : "");
            GuiRenderUtils.drawText(extractor, font, eTitle, x + 22, eY + 1, 0xFFE2E8F0, true);
            String eCountdown = eLeft + "s";
            int eCountW = font.width(eCountdown);
            GuiRenderUtils.drawText(extractor, font, eCountdown, x + cardW - eCountW - 6, eY + 1, 0xFF34D399, true);

            if (showBar) {
                int barX = x + 22;
                int barY = eY + 11;
                int barW = cardW - barX - 6;
                int barH = 3;
                GuiRenderUtils.fillRoundedRect(extractor, barX, barY, barW, barH, 1, 0x44000000);
                int fillW = Math.max(1, (int) (barW * eProgress));
                GuiRenderUtils.fillRoundedRect(extractor, barX, barY, fillW, barH, 1, 0xFF34D399);
            }

            // Next Event Row
            if (showNext) {
                int evY = y + 50;
                String evStr = "⏳ " + curNextName + ": " + formatTime(curNextSec);
                if (font.width(evStr) > cardW - 10) {
                    evStr = font.plainSubstrByWidth(evStr, cardW - 14) + "…";
                }
                GuiRenderUtils.drawText(extractor, font, evStr, x + 6, evY, 0xFFFACC15, true);
            }
        }
    }

    private static String tierRoman(int tier) {
        return switch (tier) {
            case 2 -> "[II]";
            case 3 -> "[III]";
            default -> "[I]";
        };
    }

    private static String formatTime(int totalSeconds) {
        int m = Math.max(0, totalSeconds / 60);
        int s = Math.max(0, totalSeconds % 60);
        return String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    private static void playChime(Minecraft client, String soundType, float pitch) {
        if (client == null || client.player == null) return;
        SoundEvent se = switch (soundType) {
            case "Bell" -> SoundEvents.NOTE_BLOCK_BELL.value();
            case "Bass" -> SoundEvents.NOTE_BLOCK_BASS.value();
            case "Ding" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            default -> SoundEvents.NOTE_BLOCK_PLING.value();
        };
        client.getSoundManager().play(SimpleSoundInstance.forUI(se, pitch));
    }
}
