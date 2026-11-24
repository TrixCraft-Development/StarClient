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
import net.minecraft.world.scores.Scoreboard;
import net.starclient.StarClient;
import net.starclient.module.BlockOutlineState;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.Setting;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bedwars Team Upgrades & Trap Tracker (SERVER — Draggable HUD Widget):
 * Parses match chat for team upgrade purchases (Sharpness, Protection I-IV, Maniac Miner I-II,
 * Forge I-Molten, Heal Pool, Dragon Buff) and queued Traps (up to 3), and flashes alerts
 * whenever an enemy triggers a trap.
 */
public final class BedwarsUpgradesRenderer {

    public static final int FULL_WIDTH = 162;
    private static final int COMPACT_WIDTH = 162;
    private static final int HEADER_HEIGHT = 16;
    private static final int GRID_ROW_HEIGHT = 14;
    private static final int TRAPS_ROW_HEIGHT = 19;

    private static final Pattern COLOR_STRIP = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern TIER_REGEX = Pattern.compile("\\b(iv|iii|ii|i|[1-4])\\b", Pattern.CASE_INSENSITIVE);

    public enum TrapType {
        ITS_A_TRAP("It's a Trap", "Blind", "⚠", 0xFFFACC15),
        COUNTER_OFFENSIVE("Counter-Offensive Trap", "Counter", "⚡", 0xFF38BDF8),
        ALARM("Alarm Trap", "Alarm", "👁", 0xFFF87171),
        MINER_FATIGUE("Miner Fatigue Trap", "Fatigue", "⛏", 0xFFA78BFA),
        GENERIC("Trap", "Trap", "🛡", 0xFF34D399);

        final String fullName;
        final String shortLabel;
        final String icon;
        final int color;

        TrapType(String fullName, String shortLabel, String icon, int color) {
            this.fullName = fullName;
            this.shortLabel = shortLabel;
            this.icon = icon;
            this.color = color;
        }
    }

    // Tracked team state
    private static int sharpnessTier = 0;   // 0..2
    private static int protectionTier = 0;  // 0..4
    private static int hasteTier = 0;       // 0..2
    private static int forgeTier = 0;       // 0..4 (1=Iron, 2=Gold, 3=Emerald, 4=Molten)
    private static boolean healPool = false;
    private static boolean dragonBuff = false;
    private static final List<TrapType> activeTraps = new ArrayList<>(3);

    private static long lastTrapTriggeredMs = 0L;
    private static String lastTriggeredTrapName = "";
    private static boolean inBedwarsMatch = false;

    // Lazy icons (never instantiate ItemStack during static init before MC binds components)
    private static ItemStack iconSharpness;
    private static ItemStack iconProtection;
    private static ItemStack iconHaste;
    private static ItemStack iconForge;
    private static ItemStack iconMoltenForge;
    private static ItemStack iconHealPool;
    private static ItemStack iconDragonBuff;
    private static ItemStack iconTrap;

    private BedwarsUpgradesRenderer() {
    }

    private static void ensureIcons() {
        if (iconSharpness == null) {
            iconSharpness = new ItemStack(Items.DIAMOND_SWORD);
            iconProtection = new ItemStack(Items.DIAMOND_CHESTPLATE);
            iconHaste = new ItemStack(Items.GOLDEN_PICKAXE);
            iconForge = new ItemStack(Items.FURNACE);
            iconMoltenForge = new ItemStack(Items.EMERALD);
            iconHealPool = new ItemStack(Items.BEACON);
            iconDragonBuff = new ItemStack(Items.DRAGON_HEAD);
            iconTrap = new ItemStack(Items.TRIPWIRE_HOOK);
        }
    }

    public static synchronized void reset() {
        sharpnessTier = 0;
        protectionTier = 0;
        hasteTier = 0;
        forgeTier = 0;
        healPool = false;
        dragonBuff = false;
        activeTraps.clear();
        lastTrapTriggeredMs = 0L;
        lastTriggeredTrapName = "";
        inBedwarsMatch = false;
    }

    public static synchronized boolean hasAnyPurchased() {
        return sharpnessTier > 0
                || protectionTier > 0
                || hasteTier > 0
                || forgeTier > 0
                || healPool
                || dragonBuff
                || !activeTraps.isEmpty()
                || lastTrapTriggeredMs > 0L;
    }

    public static synchronized void onServerMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) return;
        String clean = COLOR_STRIP.matcher(rawMessage).replaceAll("").replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) return;

        // Ignore normal player chat lines to prevent fake chat spoofing
        if (looksLikePlayerChat(clean)) return;

        String lower = clean.toLowerCase(Locale.ROOT);

        // 1. Match start / game reset triggers
        if (lower.contains("protect your bed and destroy the enemy beds")
                || lower.contains("sending you to ")
                || lower.contains("welcome to bed wars")
                || lower.equals("bed wars")) {
            reset();
            return;
        }

        // 2. Trap triggered detection
        boolean isTriggerPhrase = lower.contains("was set off")
                || lower.contains("has been set off")
                || lower.contains("was triggered")
                || lower.contains("has been triggered")
                || lower.contains("set off your")
                || lower.contains("triggered your");
        if (isTriggerPhrase && (lower.contains("trap") || lower.contains("alarm")
                || lower.contains("counter-offensive") || lower.contains("miner fatigue")
                || lower.contains("it's a trap") || lower.contains("its a trap"))) {
            handleTrapTriggered(lower);
            return;
        }

        // 3. Upgrade / Trap purchase detection
        boolean isPurchase = lower.contains("purchased ")
                || lower.contains("bought ")
                || lower.contains("unlocked ")
                || lower.contains("upgraded ")
                || lower.contains("has activated ");
        if (!isPurchase) return;

        Module mod = getModule();
        boolean changed = false;

        if (lower.contains("sharpened swords") || lower.contains("sharpness")) {
            int tier = extractTier(lower, Math.min(2, sharpnessTier + 1), 2);
            sharpnessTier = Math.max(sharpnessTier, tier);
            changed = true;
        } else if (lower.contains("reinforced armor") || lower.contains("protection")) {
            int tier = extractTier(lower, Math.min(4, protectionTier + 1), 4);
            protectionTier = Math.max(protectionTier, tier);
            changed = true;
        } else if (lower.contains("maniac miner") || lower.contains("haste")) {
            int tier = extractTier(lower, Math.min(2, hasteTier + 1), 2);
            hasteTier = Math.max(hasteTier, tier);
            changed = true;
        } else if (lower.contains("forge")) {
            int parsed = forgeTier;
            if (lower.contains("molten")) {
                parsed = 4;
            } else if (lower.contains("emerald")) {
                parsed = Math.max(parsed, 3);
            } else if (lower.contains("gold")) {
                parsed = Math.max(parsed, 2);
            } else if (lower.contains("iron")) {
                parsed = Math.max(parsed, 1);
            } else {
                parsed = extractTier(lower, Math.min(4, forgeTier + 1), 4);
            }
            forgeTier = Math.max(forgeTier, parsed);
            changed = true;
        } else if (lower.contains("heal pool") || lower.contains("healing pool")) {
            healPool = true;
            changed = true;
        } else if (lower.contains("dragon buff")) {
            dragonBuff = true;
            changed = true;
        } else if (lower.contains("trap") || lower.contains("alarm")) {
            TrapType type = classifyTrap(lower);
            if (activeTraps.size() < 3) {
                activeTraps.add(type);
            }
            changed = true;
        }

        if (changed && mod != null && mod.isEnabled() && getBool(mod, "Alert Sound", true)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                mc.execute(() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.25F)));
            }
        }
    }

    private static void handleTrapTriggered(String lower) {
        TrapType type = classifyTrap(lower);
        int removeIdx = -1;
        for (int i = 0; i < activeTraps.size(); i++) {
            if (activeTraps.get(i) == type) {
                removeIdx = i;
                break;
            }
        }
        if (removeIdx == -1 && !activeTraps.isEmpty()) {
            removeIdx = 0;
        }
        if (removeIdx >= 0) {
            TrapType removed = activeTraps.remove(removeIdx);
            if (type == TrapType.GENERIC) {
                type = removed;
            }
        }

        lastTrapTriggeredMs = System.currentTimeMillis();
        lastTriggeredTrapName = type.fullName;

        Module mod = getModule();
        if (mod == null || !mod.isEnabled()) return;

        if (getBool(mod, "Alert Sound", true)) {
            String soundType = getString(mod, "Sound Type", "Bell");
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> playAlertSound(mc, soundType, 1.35F));
            }
        }

        if (getBool(mod, "Toast Notifications", true)) {
            String subtitle = activeTraps.isEmpty()
                    ? "No traps remaining — Re-buy trap!"
                    : "Queued traps remaining: " + activeTraps.size() + "/3";
            ToastNotificationManager.pushToast(
                    "bw_trap_trigger",
                    "⚠ " + type.fullName + " Triggered!",
                    subtitle,
                    "TRAP",
                    0xFFEF4444,
                    3600L
            );
        }
    }

    private static TrapType classifyTrap(String lower) {
        if (lower.contains("it's a trap") || lower.contains("its a trap") || lower.contains("blind")) {
            return TrapType.ITS_A_TRAP;
        }
        if (lower.contains("counter")) {
            return TrapType.COUNTER_OFFENSIVE;
        }
        if (lower.contains("alarm") || lower.contains("reveal")) {
            return TrapType.ALARM;
        }
        if (lower.contains("fatigue") || lower.contains("miner")) {
            return TrapType.MINER_FATIGUE;
        }
        return TrapType.GENERIC;
    }

    private static int extractTier(String lower, int fallback, int maxTier) {
        Matcher m = TIER_REGEX.matcher(lower);
        int result = -1;
        while (m.find()) {
            String tok = m.group(1).toLowerCase(Locale.ROOT);
            int val = switch (tok) {
                case "iv", "4" -> 4;
                case "iii", "3" -> 3;
                case "ii", "2" -> 2;
                case "i", "1" -> 1;
                default -> -1;
            };
            if (val > 0 && val <= maxTier) {
                result = val;
            }
        }
        return result > 0 ? result : fallback;
    }

    private static boolean looksLikePlayerChat(String clean) {
        if (clean.startsWith("<") && clean.contains("> ")) {
            return true;
        }
        int colon = clean.indexOf(": ");
        if (colon > 0 && colon < 48) {
            String prefix = clean.substring(0, colon).toLowerCase(Locale.ROOT);
            return !prefix.contains("bedwars") && !prefix.contains("bed wars")
                    && !prefix.contains("upgrade") && !prefix.contains("trap");
        }
        return false;
    }

    public static void onTick(Module mod) {
        if (mod == null) return;
        if (mod.getSetting("Reset Tracker") instanceof BooleanSetting bs && bs.getValue()) {
            reset();
            bs.setValue(false);
            ToastNotificationManager.pushToast("bw_upgrades_reset", "Bedwars Upgrades", "Upgrade tracker reset", "RESET", 0xFF94A3B8, 1800L);
        }
    }

    private static void updateScoreboardState(Minecraft client) {
        if (client == null || client.level == null) {
            inBedwarsMatch = false;
            return;
        }
        Scoreboard scoreboard = client.level.getScoreboard();
        if (scoreboard == null) return;
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return;
        String title = objective.getDisplayName().getString().toLowerCase(Locale.ROOT);
        inBedwarsMatch = title.contains("bed wars") || title.contains("bedwars") || title.contains("bw");
    }

    public static int getWidth(Font font, Module mod) {
        if (isCompact(mod)) {
            return COMPACT_WIDTH;
        }
        return FULL_WIDTH;
    }

    public static int getHeight(Module mod) {
        if (isCompact(mod)) {
            return 22;
        }
        boolean hideUnpurchased = getBool(mod, "Hide Unpurchased", false);
        int upgradeRows = hideUnpurchased ? computeVisibleRowCount() : 3;
        int gridHeight = upgradeRows * GRID_ROW_HEIGHT;
        return HEADER_HEIGHT + 3 + gridHeight + 4 + TRAPS_ROW_HEIGHT + 3;
    }

    private static int computeVisibleRowCount() {
        int activeSlots = 0;
        if (sharpnessTier > 0) activeSlots++;
        if (protectionTier > 0) activeSlots++;
        if (hasteTier > 0) activeSlots++;
        if (forgeTier > 0) activeSlots++;
        if (healPool) activeSlots++;
        if (dragonBuff) activeSlots++;
        if (activeSlots == 0) return 1;
        return (activeSlots + 1) / 2;
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod, int x, int y) {
        if (client == null || mod == null) return;
        updateScoreboardState(client);

        boolean inEditor = client.gui.screen() instanceof HudEditorScreen;
        boolean onlyBedwars = getBool(mod, "Only In Bedwars", false);
        if (onlyBedwars && !inBedwarsMatch && !inEditor) {
            return;
        }

        boolean previewFallback = inEditor && !hasAnyPurchased();
        renderWidget(extractor, client.font, mod, x, y, previewFallback);
    }

    public static void renderPreview(GuiGraphicsExtractor extractor, Module mod, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc != null ? mc.font : null;
        if (font == null || mod == null) return;
        renderWidget(extractor, font, mod, x, y, !hasAnyPurchased());
    }

    private static synchronized void renderWidget(GuiGraphicsExtractor extractor, Font font, Module mod,
                                                  int x, int y, boolean usePreviewSample) {
        ensureIcons();

        int sharp = usePreviewSample ? 1 : sharpnessTier;
        int prot = usePreviewSample ? 2 : protectionTier;
        int haste = usePreviewSample ? 1 : hasteTier;
        int forge = usePreviewSample ? 2 : forgeTier;
        boolean heal = usePreviewSample || healPool;
        boolean dragon = !usePreviewSample && dragonBuff;
        List<TrapType> traps = usePreviewSample
                ? List.of(TrapType.ITS_A_TRAP, TrapType.ALARM)
                : activeTraps;

        long now = System.currentTimeMillis();
        boolean trapAlertEnabled = getBool(mod, "Trap Trigger Alert", true);
        boolean trapRecentlyTriggered = !usePreviewSample
                && trapAlertEnabled
                && lastTrapTriggeredMs > 0L
                && (now - lastTrapTriggeredMs) < 6500L;

        boolean hideUnpurchased = !usePreviewSample && getBool(mod, "Hide Unpurchased", false);
        boolean compact = isCompact(mod);

        int accent = resolveAccentColor(mod);
        int rgb = accent & 0x00FFFFFF;
        int outlineCol = trapRecentlyTriggered
                ? pulsingAlertColor(now, 220)
                : (0x3A000000 | rgb);

        int cardW = getWidth(font, mod);
        int cardH = getHeight(mod);
        boolean shadow = StarClient.getInstance().getClientSettings().isHudTextShadow();

        // Card container
        if (!mod.isTransparentBackground()) {
            GuiRenderUtils.drawDropShadow(extractor, x, y, cardW, cardH, 5, 2, 85);
            int bgColor = (mod.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
            GuiRenderUtils.fillRoundedRect(extractor, x, y, cardW, cardH, 5, bgColor);
        }
        GuiRenderUtils.drawRoundedOutline(extractor, x, y, cardW, cardH, 5, outlineCol);

        if (compact) {
            renderCompact(extractor, font, x, y, cardW, cardH, sharp, prot, haste, forge, heal, traps, trapRecentlyTriggered, shadow);
            return;
        }

        // Header
        int headerCol = 0xFF000000 | rgb;
        extractor.text(font, "⚔ Upgrades", x + 6, y + 4, headerCol, shadow);

        String trapBadgeText;
        int trapBadgeBg;
        int trapBadgeFg;
        if (trapRecentlyTriggered && traps.isEmpty()) {
            trapBadgeText = "⚠ RE-BUY!";
            trapBadgeBg = 0x55EF4444;
            trapBadgeFg = 0xFFFCA5A5;
        } else {
            trapBadgeText = "Traps " + traps.size() + "/3";
            trapBadgeBg = traps.isEmpty() ? 0x2264748B : 0x3334D399;
            trapBadgeFg = traps.isEmpty() ? 0xFF94A3B8 : 0xFF6EE7B7;
        }
        int badgeW = font.width(trapBadgeText) + 8;
        int badgeX = x + cardW - badgeW - 5;
        GuiRenderUtils.fillRoundedRect(extractor, badgeX, y + 3, badgeW, 11, 3, trapBadgeBg);
        extractor.text(font, trapBadgeText, badgeX + 4, y + 4, trapBadgeFg, shadow);

        extractor.fill(x + 5, y + HEADER_HEIGHT - 1, x + cardW - 5, y + HEADER_HEIGHT, 0x22FFFFFF);

        // Upgrade Slots Grid
        List<UpgradeSlot> slots = buildVisibleSlots(sharp, prot, haste, forge, heal, dragon, traps.size(), hideUnpurchased);
        int gridTop = y + HEADER_HEIGHT + 3;
        int colWidth = (cardW - 12) / 2;

        if (slots.isEmpty()) {
            extractor.text(font, "No team upgrades yet", x + 8, gridTop + 3, 0xFF64748B, shadow);
        } else {
            for (int i = 0; i < slots.size(); i++) {
                UpgradeSlot slot = slots.get(i);
                int col = i % 2;
                int row = i / 2;
                int cellX = x + 6 + col * colWidth;
                int cellY = gridTop + row * GRID_ROW_HEIGHT;
                renderUpgradeCell(extractor, font, cellX, cellY, colWidth - 3, slot, mod.getTextColor(), shadow);
            }
        }

        int numRows = hideUnpurchased ? Math.max(1, (slots.size() + 1) / 2) : 3;
        int dividerY = gridTop + numRows * GRID_ROW_HEIGHT + 1;
        extractor.fill(x + 5, dividerY, x + cardW - 5, dividerY + 1, 0x1FFFFFFF);

        // Traps Footer Row
        int trapsY = dividerY + 4;
        if (trapRecentlyTriggered) {
            int alertBg = pulsingAlertColor(now, 85);
            GuiRenderUtils.fillRoundedRect(extractor, x + 5, trapsY, cardW - 10, 15, 3, alertBg);
            String alertText = "⚠ " + (lastTriggeredTrapName.isEmpty() ? "Trap" : lastTriggeredTrapName) + " Triggered!";
            if (font.width(alertText) > cardW - 16) {
                alertText = font.plainSubstrByWidth(alertText, cardW - 20) + "…";
            }
            int tw = font.width(alertText);
            extractor.text(font, alertText, x + (cardW - tw) / 2, trapsY + 4, 0xFFFEE2E2, shadow);
        } else if (traps.isEmpty()) {
            drawMiniatureIcon(extractor, iconTrap, x + 6, trapsY + 1);
            extractor.text(font, "No Active Traps", x + 20, trapsY + 3, 0xFF64748B, shadow);
        } else {
            int chipX = x + 5;
            for (int i = 0; i < Math.min(3, traps.size()); i++) {
                TrapType trap = traps.get(i);
                String label = trap.icon + " " + trap.shortLabel;
                int chipW = Math.min(50, font.width(label) + 7);
                if (chipX + chipW > x + cardW - 4) break;
                int chipBg = GuiRenderUtils.withAlpha(trap.color, 38);
                int chipBorder = GuiRenderUtils.withAlpha(trap.color, 130);
                GuiRenderUtils.fillRoundedRect(extractor, chipX, trapsY + 1, chipW, 13, 3, chipBg);
                GuiRenderUtils.drawRoundedOutline(extractor, chipX, trapsY + 1, chipW, 13, 3, chipBorder);
                extractor.text(font, label, chipX + 4, trapsY + 4, trap.color, shadow);
                chipX += chipW + 3;
            }
        }
    }

    private record UpgradeSlot(ItemStack icon, String name, String value, boolean active, int valueColor) {
    }

    private static List<UpgradeSlot> buildVisibleSlots(int sharp, int prot, int haste, int forge,
                                                       boolean heal, boolean dragon, int trapCount, boolean hideUnpurchased) {
        List<UpgradeSlot> list = new ArrayList<>(6);

        if (!hideUnpurchased || sharp > 0) {
            list.add(new UpgradeSlot(iconSharpness, "Sharp", sharp > 0 ? roman(sharp) : "—", sharp > 0,
                    sharp > 0 ? 0xFFF87171 : 0xFF64748B));
        }
        if (!hideUnpurchased || prot > 0) {
            list.add(new UpgradeSlot(iconProtection, "Prot", prot > 0 ? roman(prot) : "—", prot > 0,
                    prot > 0 ? 0xFF38BDF8 : 0xFF64748B));
        }
        if (!hideUnpurchased || haste > 0) {
            list.add(new UpgradeSlot(iconHaste, "Haste", haste > 0 ? roman(haste) : "—", haste > 0,
                    haste > 0 ? 0xFFFACC15 : 0xFF64748B));
        }
        if (!hideUnpurchased || forge > 0) {
            list.add(new UpgradeSlot(forge >= 3 ? iconMoltenForge : iconForge, "Forge", forgeLabel(forge), forge > 0,
                    forgeColor(forge)));
        }
        if (!hideUnpurchased || heal) {
            list.add(new UpgradeSlot(iconHealPool, "Heal", heal ? "ON" : "—", heal,
                    heal ? 0xFF34D399 : 0xFF64748B));
        }
        if (dragon) {
            list.add(new UpgradeSlot(iconDragonBuff, "Dragon", "ON", true, 0xFFD946EF));
        } else if (!hideUnpurchased) {
            list.add(new UpgradeSlot(iconTrap, "Queue", trapCount + "/3", trapCount > 0,
                    trapCount > 0 ? 0xFF34D399 : 0xFF64748B));
        }

        return list;
    }

    private static void renderUpgradeCell(GuiGraphicsExtractor extractor, Font font, int x, int y, int width,
                                          UpgradeSlot slot, int defaultTextColor, boolean shadow) {
        drawMiniatureIcon(extractor, slot.icon(), x, y + 1);
        int nameCol = slot.active() ? defaultTextColor : 0xFF64748B;
        extractor.text(font, slot.name(), x + 13, y + 3, nameCol, shadow);

        int valW = font.width(slot.value());
        extractor.text(font, slot.value(), x + width - valW, y + 3, slot.valueColor(), shadow);
    }

    private static void renderCompact(GuiGraphicsExtractor extractor, Font font, int x, int y, int w, int h,
                                      int sharp, int prot, int haste, int forge, boolean heal,
                                      List<TrapType> traps, boolean trapTriggered, boolean shadow) {
        int curX = x + 5;
        int textY = y + 7;

        curX = drawCompactChip(extractor, font, curX, y, textY, iconSharpness, sharp > 0 ? roman(sharp) : "-", sharp > 0 ? 0xFFF87171 : 0xFF64748B, shadow);
        curX = drawCompactChip(extractor, font, curX, y, textY, iconProtection, prot > 0 ? roman(prot) : "-", prot > 0 ? 0xFF38BDF8 : 0xFF64748B, shadow);
        curX = drawCompactChip(extractor, font, curX, y, textY, iconHaste, haste > 0 ? roman(haste) : "-", haste > 0 ? 0xFFFACC15 : 0xFF64748B, shadow);
        curX = drawCompactChip(extractor, font, curX, y, textY, forge >= 3 ? iconMoltenForge : iconForge, forge > 0 ? String.valueOf(forge) : "-", forgeColor(forge), shadow);
        curX = drawCompactChip(extractor, font, curX, y, textY, iconHealPool, heal ? "✓" : "-", heal ? 0xFF34D399 : 0xFF64748B, shadow);

        extractor.fill(curX, y + 4, curX + 1, y + h - 4, 0x28FFFFFF);
        curX += 4;

        String trapStr = (trapTriggered ? "⚠" : "🛡") + traps.size() + "/3";
        int trapCol = trapTriggered ? 0xFFF87171 : (traps.isEmpty() ? 0xFF94A3B8 : 0xFF34D399);
        extractor.text(font, trapStr, curX, textY, trapCol, shadow);
    }

    private static int drawCompactChip(GuiGraphicsExtractor extractor, Font font, int curX, int cardY, int textY,
                                       ItemStack icon, String val, int color, boolean shadow) {
        drawMiniatureIcon(extractor, icon, curX, cardY + 5);
        extractor.text(font, val, curX + 11, textY, color, shadow);
        return curX + 12 + font.width(val) + 3;
    }

    private static void drawMiniatureIcon(GuiGraphicsExtractor extractor, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        Matrix3x2fStack pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(0.625F, 0.625F);
        extractor.item(stack, 0, 0);
        pose.popMatrix();
    }

    private static int pulsingAlertColor(long now, int maxAlpha) {
        float pulse = 0.55F + 0.45F * Mth.sin((now % 500L) / 500.0F * (float) Math.PI * 2.0F);
        int alpha = Math.round(maxAlpha * pulse);
        return (alpha << 24) | 0xEF4444;
    }

    private static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "—";
        };
    }

    private static String forgeLabel(int tier) {
        return switch (tier) {
            case 1 -> "Iron";
            case 2 -> "Gold";
            case 3 -> "Emrld";
            case 4 -> "Molten";
            default -> "—";
        };
    }

    private static int forgeColor(int tier) {
        return switch (tier) {
            case 1 -> 0xFFE2E8F0;
            case 2 -> 0xFFFACC15;
            case 3 -> 0xFF34D399;
            case 4 -> 0xFFFB923C;
            default -> 0xFF64748B;
        };
    }

    private static int resolveAccentColor(Module mod) {
        if (getBool(mod, "Use Client Color", true)
                && StarClient.getInstance() != null
                && StarClient.getInstance().getClientSettings() != null) {
            return StarClient.getInstance().getClientSettings().getMenuAccentColor();
        }
        if (mod != null && mod.getSetting("Color") instanceof ColorModeSetting cms) {
            return cms.getColor();
        }
        return BlockOutlineState.OUTLINE_COLORS.getOrDefault("Sky Blue", 0xFF38BDF8);
    }

    private static void playAlertSound(Minecraft client, String type, float pitch) {
        if (client == null || client.player == null) return;
        SoundEvent se = switch (type) {
            case "Pling" -> SoundEvents.NOTE_BLOCK_PLING.value();
            case "Ding" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case "Anvil" -> SoundEvents.ANVIL_LAND;
            case "Bass" -> SoundEvents.NOTE_BLOCK_BASS.value();
            default -> SoundEvents.NOTE_BLOCK_BELL.value();
        };
        client.getSoundManager().play(SimpleSoundInstance.forUI(se, pitch));
    }

    private static Module getModule() {
        StarClient sc = StarClient.getInstance();
        return (sc != null && sc.getModuleManager() != null)
                ? sc.getModuleManager().getModuleByName("Bedwars Upgrades")
                : null;
    }

    private static boolean isCompact(Module mod) {
        return "Compact".equalsIgnoreCase(getString(mod, "Display Mode", "Full Card"));
    }

    private static boolean getBool(Module mod, String name, boolean def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }

    private static String getString(Module mod, String name, String def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s instanceof ModeSetting ms ? ms.getValue() : def;
    }
}
