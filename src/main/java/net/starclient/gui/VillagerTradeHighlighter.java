package net.starclient.gui;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Enhances Minecraft's Villager Trading UI ({@link MerchantScreen}) by highlighting
 * Max-Level & Rare Enchanted Books (and enchanted gear), displaying a glassmorphic
 * Trade Inspector side panel with accurate emerald price ratings, and playing
 * celebratory audio + toast roll alerts when rerolling Librarians.
 */
public final class VillagerTradeHighlighter {
    private static final int TRADE_BTN_X = 5;
    private static final int TRADE_BTN_TOP_Y = 18;
    private static final int TRADE_BTN_W = 88;
    private static final int TRADE_BTN_H = 20;
    private static final int VISIBLE_OFFERS = 7;

    private static Module module;
    private static int lastAlertContainerId = -1;
    private static String lastAlertSignature = "";

    private enum PriceRating {
        GREAT("GREAT", 0xFF34D399),
        FAIR("FAIR", 0xFFFBBF24),
        PRICY("PRICY", 0xFFF87171),
        CURSE("CURSE", 0xFFEF4444);

        final String label;
        final int color;

        PriceRating(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    private record AnalyzedOffer(
            int offerIndex,
            MerchantOffer offer,
            ItemStack result,
            String primaryLabel,
            int extraEnchCount,
            boolean isBook,
            boolean isMaxLevel,
            boolean isTreasure,
            boolean isCurse,
            int emeraldCost,
            boolean discounted,
            PriceRating rating
    ) {
        boolean isNotable() {
            return isBook || !primaryLabel.isEmpty();
        }

        boolean isTopTier() {
            return !isCurse && (isMaxLevel || isTreasure);
        }

        int accentColor() {
            if (isCurse) return 0xFFEF4444;
            if (isTreasure && isMaxLevel) return 0xFFFACC15; // Gold for Mending / Treasure Max
            if (rating == PriceRating.GREAT && isMaxLevel) return 0xFF34D399; // Emerald green
            if (isMaxLevel) return 0xFF38BDF8; // Sky blue
            return 0xFF94A3B8;
        }
    }

    private VillagerTradeHighlighter() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static void resetAlertState() {
        lastAlertContainerId = -1;
        lastAlertSignature = "";
    }

    public static void renderOverlay(GuiGraphicsExtractor graphics, MerchantScreen screen, MerchantMenu menu,
                                     int leftPos, int topPos, int imageWidth, int imageHeight, int scrollOff) {
        if (!isEnabled() || menu == null) return;
        MerchantOffers offers = menu.getOffers();
        if (offers == null || offers.isEmpty()) return;

        boolean includeGear = getBool("Include Enchanted Gear", true);
        boolean onlyMaxLevel = getBool("Highlight Only Max Level", false);
        boolean highlightRows = getBool("Highlight Trade Rows", true);
        boolean showPanel = getBool("Show Inspector Panel", true);
        boolean onlyShowWhenEnchanted = getBool("Only Show When Enchanted", true);
        boolean showPriceRatings = getBool("Show Price Ratings", true);

        List<AnalyzedOffer> analyzed = new ArrayList<>();
        AnalyzedOffer[] byIndex = new AnalyzedOffer[offers.size()];

        for (int i = 0; i < offers.size(); i++) {
            AnalyzedOffer ao = analyzeOffer(i, offers.get(i), includeGear);
            if (ao != null) {
                if (!onlyMaxLevel || ao.isTopTier() || ao.isCurse()) {
                    analyzed.add(ao);
                    byIndex[i] = ao;
                }
            }
        }

        // Fire Librarian roll alert once per unique containerId + offer set
        checkAndFireRollAlert(menu.containerId, analyzed);

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;

        // 1. Highlight matching trade rows inside the Merchant trade list
        if (highlightRows) {
            int maxVisible = Math.min(offers.size(), scrollOff + VISIBLE_OFFERS);
            for (int i = scrollOff; i < maxVisible; i++) {
                AnalyzedOffer ao = byIndex[i];
                if (ao == null) continue;
                int rowIdx = i - scrollOff;
                int rx = leftPos + TRADE_BTN_X;
                int ry = topPos + TRADE_BTN_TOP_Y + rowIdx * TRADE_BTN_H;
                int col = ao.accentColor();

                int tint = ao.offer.isOutOfStock() ? 0x1864748B : GuiRenderUtils.withAlpha(col, ao.isTopTier() ? 38 : 22);
                int border = ao.offer.isOutOfStock() ? 0x6664748B : GuiRenderUtils.withAlpha(col, ao.isTopTier() ? 225 : 140);
                GuiRenderUtils.fillRoundedRect(graphics, rx, ry, TRADE_BTN_W, TRADE_BTN_H, 2, tint);
                GuiRenderUtils.drawRoundedOutline(graphics, rx, ry, TRADE_BTN_W, TRADE_BTN_H, 2, border);

                // Small star or warning pip in top-right corner of the trade button
                if (ao.isTopTier() || ao.isCurse()) {
                    String mark = ao.isCurse() ? "!" : "★";
                    graphics.text(font, mark, rx + TRADE_BTN_W - 8, ry + 1, col, true);
                }
            }
        }

        // 2. Render glassmorphic Trade Inspector side panel
        if (showPanel && (!onlyShowWhenEnchanted || !analyzed.isEmpty())) {
            renderInspectorPanel(graphics, font, screen.width, leftPos, topPos, imageWidth, imageHeight,
                    analyzed, showPriceRatings);
        }
    }

    private static void renderInspectorPanel(GuiGraphicsExtractor graphics, Font font, int screenW,
                                             int leftPos, int topPos, int imageWidth, int imageHeight,
                                             List<AnalyzedOffer> analyzed, boolean showPriceRatings) {
        StarClient sc = StarClient.getInstance();
        ClientSettings cs = sc != null ? sc.getClientSettings() : null;
        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int bg = cs != null ? (0xEE000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xEE0F172A;
        int surface = cs != null ? cs.getMenuSurfaceColor() : 0xFF162032;
        int border = cs != null ? cs.getMenuBorderColor() : 0xFF334155;

        int panelW = 166;
        int panelX = leftPos + imageWidth + 6;
        if (panelX + panelW > screenW - 6) {
            panelX = Math.max(4, leftPos - panelW - 6);
        }
        int rowH = 26;
        int maxRows = Math.max(1, Math.min(analyzed.size(), 5));
        int panelH = analyzed.isEmpty()
                ? 54
                : Math.min(imageHeight, 26 + maxRows * (rowH + 4) + 6);
        int panelY = topPos;

        GuiRenderUtils.drawDropShadow(graphics, panelX, panelY, panelW, panelH, 6, 4, 110);
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, panelW, panelH, 6, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, panelX, panelY, panelW, panelH, 6, border);

        // Header
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, panelW, 20, 6, surface);
        graphics.fill(panelX, panelY + 19, panelX + panelW, panelY + 20, border);
        GuiRenderUtils.drawText(graphics, font, "✨ Trade Inspector", panelX + 7, panelY + 6, accent, true);
        String countBadge = String.valueOf(analyzed.size());
        int cbW = Math.max(14, font.width(countBadge) + 6);
        GuiRenderUtils.fillRoundedRect(graphics, panelX + panelW - cbW - 6, panelY + 4, cbW, 12, 4,
                GuiRenderUtils.withAlpha(accent, 45));
        GuiRenderUtils.drawCenteredText(graphics, font, countBadge,
                panelX + panelW - cbW / 2 - 6, panelY + 6, accent);

        if (analyzed.isEmpty()) {
            GuiRenderUtils.drawCenteredText(graphics, font, "No enchanted trades",
                    panelX + panelW / 2, panelY + 33, 0xFF64748B);
            return;
        }

        int ry = panelY + 24;
        int shown = 0;
        for (AnalyzedOffer ao : analyzed) {
            if (shown >= maxRows) break;
            int rx = panelX + 5;
            int rw = panelW - 10;
            int col = ao.accentColor();

            GuiRenderUtils.fillRoundedRect(graphics, rx, ry, rw, rowH, 4, surface);
            GuiRenderUtils.drawRoundedOutline(graphics, rx, ry, rw, rowH, 4,
                    GuiRenderUtils.withAlpha(col, ao.isTopTier() ? 185 : 90));

            // Result item icon
            graphics.item(ao.result, rx + 3, ry + 5);

            // Title line (enchantment name)
            int tx = rx + 22;
            int maxTitleW = rw - 26;
            String title = (ao.isTopTier() ? "★ " : "") + ao.primaryLabel
                    + (ao.extraEnchCount > 0 ? " +" + ao.extraEnchCount : "");
            GuiRenderUtils.drawText(graphics, font, GuiRenderUtils.truncate(font, title, maxTitleW),
                    tx, ry + 4, ao.isTopTier() || ao.isCurse() ? col : 0xFFF1F5F9, true);

            // Bottom metadata: emerald cost + rating badge
            String costStr = ao.emeraldCost > 0
                    ? ao.emeraldCost + " em" + (ao.discounted ? " ↓" : "")
                    : "Trade";
            GuiRenderUtils.drawText(graphics, font, costStr, tx, ry + 15,
                    ao.discounted ? 0xFF34D399 : 0xFF94A3B8);

            if (showPriceRatings && ao.rating != null && (ao.emeraldCost > 0 || ao.isCurse())) {
                String badge = ao.rating.label;
                int bw = font.width(badge) + 6;
                int bx = rx + rw - bw - 4;
                int by = ry + 13;
                GuiRenderUtils.fillRoundedRect(graphics, bx, by, bw, 10, 3,
                        GuiRenderUtils.withAlpha(ao.rating.color, 45));
                GuiRenderUtils.drawRoundedOutline(graphics, bx, by, bw, 10, 3,
                        GuiRenderUtils.withAlpha(ao.rating.color, 175));
                GuiRenderUtils.drawCenteredText(graphics, font, badge, bx + bw / 2, by + 1, ao.rating.color);
            }

            ry += rowH + 4;
            shown++;
        }
    }

    private static AnalyzedOffer analyzeOffer(int index, MerchantOffer offer, boolean includeGear) {
        if (offer == null) return null;
        ItemStack result = offer.getResult();
        if (result == null || result.isEmpty()) return null;

        boolean isBook = result.is(Items.ENCHANTED_BOOK);
        ItemEnchantments enchants = isBook
                ? result.get(DataComponents.STORED_ENCHANTMENTS)
                : (includeGear ? result.get(DataComponents.ENCHANTMENTS) : null);

        if (enchants == null || enchants.entrySet().isEmpty()) {
            return null;
        }

        // Pick the best / highest-priority enchantment on the item for display
        Holder<Enchantment> bestHolder = null;
        int bestLevel = 0;
        boolean anyCurse = false;
        boolean bestTreasure = false;
        boolean bestMax = false;
        int count = 0;

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchants.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getIntValue();
            count++;

            String key = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("").toLowerCase(Locale.ROOT);
            boolean curse = key.contains("curse") || key.contains("binding") || key.contains("vanishing");
            if (curse) anyCurse = true;

            int maxLvl = holder.value().getMaxLevel();
            boolean isMax = level >= maxLvl;
            boolean isTreasure = isTreasureEnchant(key);

            if (bestHolder == null
                    || (!curse && isTreasure && !bestTreasure)
                    || (!curse && isMax && !bestMax)
                    || (level > bestLevel && !curse)) {
                bestHolder = holder;
                bestLevel = level;
                bestTreasure = isTreasure;
                bestMax = isMax;
            }
        }

        if (bestHolder == null) return null;

        String name = stripFormatting(Enchantment.getFullname(bestHolder, bestLevel).getString());
        int emeraldCost = extractEmeraldCost(offer.getCostA(), offer.getCostB());
        int baseEmerald = extractEmeraldCost(offer.getBaseCostA(), offer.getCostB());
        boolean discounted = emeraldCost > 0 && baseEmerald > emeraldCost;

        PriceRating rating = rateOffer(emeraldCost, bestLevel, bestTreasure, anyCurse, discounted, isBook);
        return new AnalyzedOffer(
                index,
                offer,
                result,
                name,
                Math.max(0, count - 1),
                isBook,
                bestMax,
                bestTreasure,
                anyCurse,
                emeraldCost,
                discounted,
                rating
        );
    }

    private static PriceRating rateOffer(int emeraldCost, int level, boolean treasure,
                                         boolean curse, boolean discounted, boolean isBook) {
        if (curse) return PriceRating.CURSE;
        if (emeraldCost <= 0) return null;
        if (emeraldCost <= 1 || (discounted && emeraldCost <= 6)) return PriceRating.GREAT;

        if (!isBook) {
            if (emeraldCost <= 12) return PriceRating.GREAT;
            if (emeraldCost <= 22) return PriceRating.FAIR;
            return PriceRating.PRICY;
        }

        int greatMax;
        int fairMax;
        if (treasure) {
            greatMax = switch (level) {
                case 1 -> 14;
                case 2 -> 22;
                default -> 26;
            };
            fairMax = switch (level) {
                case 1 -> 24;
                case 2 -> 38;
                default -> 44;
            };
        } else {
            greatMax = switch (level) {
                case 1 -> 8;
                case 2 -> 12;
                case 3 -> 16;
                case 4 -> 20;
                default -> 24;
            };
            fairMax = switch (level) {
                case 1 -> 13;
                case 2 -> 20;
                case 3 -> 28;
                case 4 -> 36;
                default -> 42;
            };
        }

        if (emeraldCost <= greatMax) return PriceRating.GREAT;
        if (emeraldCost <= fairMax) return PriceRating.FAIR;
        return PriceRating.PRICY;
    }

    private static void checkAndFireRollAlert(int containerId, List<AnalyzedOffer> analyzed) {
        if (analyzed.isEmpty()) return;
        boolean alertSound = getBool("Roll Alert Sound", true);
        boolean alertToast = getBool("Roll Alert Toast", true);
        if (!alertSound && !alertToast) return;

        boolean onlyGreat = getBool("Alert Only Great Prices", false);
        AnalyzedOffer bestHit = null;
        StringBuilder sig = new StringBuilder();

        for (AnalyzedOffer ao : analyzed) {
            sig.append(ao.primaryLabel).append(':').append(ao.emeraldCost).append(';');
            if (!ao.isBook || !ao.isTopTier()) continue;
            if (onlyGreat && ao.rating != PriceRating.GREAT) continue;
            if (bestHit == null
                    || (ao.isTreasure && !bestHit.isTreasure)
                    || (ao.rating == PriceRating.GREAT && bestHit.rating != PriceRating.GREAT)) {
                bestHit = ao;
            }
        }

        if (bestHit == null) return;
        String signature = sig.toString();
        if (containerId == lastAlertContainerId && signature.equals(lastAlertSignature)) {
            return;
        }
        lastAlertContainerId = containerId;
        lastAlertSignature = signature;

        if (alertSound) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.getSoundManager().play(SimpleSoundInstance.forUI(
                        bestHit.rating == PriceRating.GREAT ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_PLING.value(),
                        bestHit.rating == PriceRating.GREAT ? 1.15F : 1.45F));
            }
        }
        if (alertToast) {
            String ratingTag = bestHit.rating != null ? bestHit.rating.label : "MAX";
            String subtitle = bestHit.primaryLabel
                    + (bestHit.emeraldCost > 0 ? " • " + bestHit.emeraldCost + " Emeralds" : "");
            ToastNotificationManager.pushToast(
                    "villager_roll",
                    "★ Librarian Roll!",
                    subtitle,
                    ratingTag,
                    bestHit.accentColor()
            );
        }
    }

    private static int extractEmeraldCost(ItemStack a, ItemStack b) {
        if (a != null && !a.isEmpty() && a.is(Items.EMERALD)) return a.getCount();
        if (b != null && !b.isEmpty() && b.is(Items.EMERALD)) return b.getCount();
        return 0;
    }

    private static boolean isTreasureEnchant(String path) {
        return path.equals("mending")
                || path.equals("frost_walker")
                || path.equals("soul_speed")
                || path.equals("swift_sneak")
                || path.equals("wind_burst");
    }

    private static String stripFormatting(String s) {
        return s == null ? "" : s.replaceAll("§[0-9a-fk-orxA-FK-ORX]", "");
    }

    private static boolean getBool(String name, boolean fallback) {
        if (module == null) return fallback;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : fallback;
    }
}
