package net.starclient.gui;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Enhances both the Enchanting Table UI (Bookshelf Power 0–15 & obstructed-gap detector)
 * and the Grindstone UI (XP orb/level return preview & treasure enchantment warnings).
 */
public final class EnchantGrindstoneHelper {
    private static final int PANEL_W = 168;
    private static Module module;

    private EnchantGrindstoneHelper() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    // -------------------------------------------------------------------------
    // 1. Enchanting Table Helper
    // -------------------------------------------------------------------------

    public static void renderEnchantmentPanel(GuiGraphicsExtractor graphics, EnchantmentMenu menu,
                                              int screenW, int leftPos, int topPos, int imageWidth, int imageHeight) {
        if (!isEnabled() || !getBool("Enchanting Table Helper", true) || menu == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        BlockPos tablePos = findActiveEnchantingTable(client);
        int activeShelves = 0;
        int blockedShelves = 0;
        Set<String> blockerNames = new LinkedHashSet<>();

        if (tablePos != null) {
            Level level = client.level;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                    for (int dy = 0; dy <= 1; dy++) {
                        BlockPos shelfPos = tablePos.offset(dx, dy, dz);
                        BlockState shelfState = level.getBlockState(shelfPos);
                        if (shelfState.is(BlockTags.ENCHANTMENT_POWER_PROVIDER) || shelfState.is(Blocks.BOOKSHELF)) {
                            BlockPos gapPos = tablePos.offset(dx / 2, dy, dz / 2);
                            BlockState gapState = level.getBlockState(gapPos);
                            if (gapState.isAir()) {
                                activeShelves++;
                            } else {
                                blockedShelves++;
                                blockerNames.add(gapState.getBlock().getName().getString());
                            }
                        }
                    }
                }
            }
        }

        int cappedShelves = Math.min(15, activeShelves);
        boolean maxReady = cappedShelves >= 15;
        int maxCostShown = Math.max(menu.costs[0], Math.max(menu.costs[1], menu.costs[2]));

        Font font = client.font;
        StarClient sc = StarClient.getInstance();
        ClientSettings cs = sc != null ? sc.getClientSettings() : null;
        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int bg = cs != null ? (0xEE000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xEE0F172A;
        int surface = cs != null ? cs.getMenuSurfaceColor() : 0xFF162032;
        int border = cs != null ? cs.getMenuBorderColor() : 0xFF334155;

        int panelX = leftPos + imageWidth + 6;
        if (panelX + PANEL_W > screenW - 4) {
            panelX = Math.max(4, leftPos - PANEL_W - 6);
        }
        int panelY = topPos;

        int panelH = 26 + 38 + (blockedShelves > 0 ? 28 : 0) + (maxCostShown > 0 ? 34 : 0) + 6;
        panelH = Math.min(panelH, imageHeight + 16);

        GuiRenderUtils.drawDropShadow(graphics, panelX, panelY, PANEL_W, panelH, 6, 4, 110);
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, PANEL_W, panelH, 6, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, panelX, panelY, PANEL_W, panelH, 6, border);

        // Header
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, PANEL_W, 20, 6, surface);
        graphics.fill(panelX, panelY + 19, panelX + PANEL_W, panelY + 20, border);
        GuiRenderUtils.drawText(graphics, font, "✨ Enchanting Helper", panelX + 7, panelY + 6, accent, true);

        int curY = panelY + 24;
        int rx = panelX + 6;
        int rw = PANEL_W - 12;

        // Bookshelf Power card
        int shelfCol = maxReady ? 0xFF34D399 : (cappedShelves >= 8 ? 0xFFFBBF24 : 0xFFF87171);
        GuiRenderUtils.fillRoundedRect(graphics, rx, curY, rw, 34, 4, surface);
        GuiRenderUtils.drawRoundedOutline(graphics, rx, curY, rw, 34, 4, GuiRenderUtils.withAlpha(shelfCol, 165));

        String shelfHeader = "Bookshelves: " + cappedShelves + " / 15";
        GuiRenderUtils.drawText(graphics, font, shelfHeader, rx + 5, curY + 4, shelfCol, true);
        String statusLine = maxReady
                ? "✓ Max Level 30 Ready!"
                : "Need " + (15 - cappedShelves) + " more for Lvl 30";
        GuiRenderUtils.drawText(graphics, font, statusLine, rx + 5, curY + 14, 0xFFCBD5E1);

        int barW = rw - 10;
        GuiRenderUtils.fillRoundedRect(graphics, rx + 5, curY + 26, barW, 4, 2, 0x2AFFFFFF);
        int fillW = Math.round(barW * (cappedShelves / 15.0F));
        if (fillW > 0) {
            GuiRenderUtils.fillRoundedRect(graphics, rx + 5, curY + 26, Math.max(2, fillW), 4, 2, shelfCol);
        }
        curY += 38;

        // Blocked Bookshelves Warning
        if (blockedShelves > 0) {
            GuiRenderUtils.fillRoundedRect(graphics, rx, curY, rw, 24, 4, 0x2AF59E0B);
            GuiRenderUtils.drawRoundedOutline(graphics, rx, curY, rw, 24, 4, 0xCCF59E0B);
            String warn1 = "⚠ " + blockedShelves + (blockedShelves == 1 ? " shelf blocked!" : " shelves blocked!");
            String warn2 = "Remove: " + String.join(", ", blockerNames);
            GuiRenderUtils.drawText(graphics, font, warn1, rx + 5, curY + 3, 0xFFFBBF24, true);
            GuiRenderUtils.drawText(graphics, font, GuiRenderUtils.truncate(font, warn2, rw - 10),
                    rx + 5, curY + 13, 0xFFFEF3C7);
            curY += 28;
        }

        // Live Enchant Tier Summary
        if (maxCostShown > 0) {
            GuiRenderUtils.fillRoundedRect(graphics, rx, curY, rw, 30, 4, surface);
            GuiRenderUtils.drawRoundedOutline(graphics, rx, curY, rw, 30, 4, border);
            GuiRenderUtils.drawText(graphics, font, "Tier Requirements:", rx + 5, curY + 4, accent);
            String tiers = "I: Lvl " + menu.costs[0] + "  •  II: Lvl " + menu.costs[1] + "  •  III: Lvl " + menu.costs[2];
            GuiRenderUtils.drawText(graphics, font, GuiRenderUtils.truncate(font, tiers, rw - 10),
                    rx + 5, curY + 16, 0xFFE2E8F0);
        }
    }

    private static BlockPos findActiveEnchantingTable(Minecraft client) {
        if (client.hitResult instanceof BlockHitResult bhr) {
            BlockPos p = bhr.getBlockPos();
            if (client.level.getBlockState(p).is(Blocks.ENCHANTING_TABLE)) {
                return p;
            }
        }
        BlockPos playerPos = client.player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos p = playerPos.offset(dx, dy, dz);
                    if (client.level.getBlockState(p).is(Blocks.ENCHANTING_TABLE)) {
                        double d = p.distSqr(playerPos);
                        if (d < bestDist) {
                            bestDist = d;
                            best = p;
                        }
                    }
                }
            }
        }
        return best;
    }

    // -------------------------------------------------------------------------
    // 2. Grindstone Disenchant Preview & Treasure Warning
    // -------------------------------------------------------------------------

    public static void renderGrindstonePanel(GuiGraphicsExtractor graphics, GrindstoneMenu menu,
                                             int screenW, int leftPos, int topPos, int imageWidth, int imageHeight) {
        if (!isEnabled() || !getBool("Grindstone Helper", true) || menu == null) return;

        ItemStack in1 = menu.getSlot(0).getItem();
        ItemStack in2 = menu.getSlot(1).getItem();
        if (in1.isEmpty() && in2.isEmpty()) return;

        int totalMinCost = 0;
        int removableCount = 0;
        List<String> treasureWarnings = new ArrayList<>();
        List<String> curseWarnings = new ArrayList<>();

        for (ItemStack stack : List.of(in1, in2)) {
            if (stack.isEmpty()) continue;
            ItemEnchantments enchs = stack.is(Items.ENCHANTED_BOOK)
                    ? stack.get(DataComponents.STORED_ENCHANTMENTS)
                    : stack.get(DataComponents.ENCHANTMENTS);
            if (enchs == null) continue;

            for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchs.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                int lvl = entry.getIntValue();
                String key = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("").toLowerCase(Locale.ROOT);
                String displayName = stripFormatting(Enchantment.getFullname(holder, lvl).getString());

                if (key.contains("curse") || key.contains("binding") || key.contains("vanishing")) {
                    curseWarnings.add(displayName);
                } else {
                    removableCount++;
                    totalMinCost += Math.max(1, holder.value().getMinCost(lvl));
                    if (isTreasureEnchant(key)) {
                        treasureWarnings.add(displayName);
                    }
                }
            }
        }

        if (removableCount == 0 && curseWarnings.isEmpty()) return;

        int minXp = totalMinCost > 0 ? (int) Math.ceil(totalMinCost / 2.0) : 0;
        int maxXp = totalMinCost;
        double avgXp = (minXp + maxXp) / 2.0;

        Minecraft client = Minecraft.getInstance();
        int curLvl = client.player != null ? client.player.experienceLevel : 0;
        double estLevels = estimateLevelsGained(curLvl, avgXp);

        Font font = client.font;
        StarClient sc = StarClient.getInstance();
        ClientSettings cs = sc != null ? sc.getClientSettings() : null;
        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int bg = cs != null ? (0xEE000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xEE0F172A;
        int surface = cs != null ? cs.getMenuSurfaceColor() : 0xFF162032;
        int border = cs != null ? cs.getMenuBorderColor() : 0xFF334155;

        int panelX = leftPos + imageWidth + 6;
        if (panelX + PANEL_W > screenW - 4) {
            panelX = Math.max(4, leftPos - PANEL_W - 6);
        }
        int panelY = topPos;

        boolean showTreasureWarn = getBool("Treasure Enchant Warning", true) && !treasureWarnings.isEmpty();
        int panelH = 26 + (removableCount > 0 ? 36 : 0)
                + (showTreasureWarn ? 30 : 0)
                + (!curseWarnings.isEmpty() ? 24 : 0) + 6;
        panelH = Math.min(Math.max(panelH, 60), imageHeight);

        GuiRenderUtils.drawDropShadow(graphics, panelX, panelY, PANEL_W, panelH, 6, 4, 110);
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, PANEL_W, panelH, 6, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, panelX, panelY, PANEL_W, panelH, 6, border);

        // Header
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, PANEL_W, 20, 6, surface);
        graphics.fill(panelX, panelY + 19, panelX + PANEL_W, panelY + 20, border);
        GuiRenderUtils.drawText(graphics, font, "⚙ Grindstone Preview", panelX + 7, panelY + 6, accent, true);

        int curY = panelY + 24;
        int rx = panelX + 6;
        int rw = PANEL_W - 12;

        if (removableCount > 0) {
            GuiRenderUtils.fillRoundedRect(graphics, rx, curY, rw, 32, 4, surface);
            GuiRenderUtils.drawRoundedOutline(graphics, rx, curY, rw, 32, 4, 0xAA34D399);
            String xpRange = "XP Return: " + minXp + "–" + maxXp + " orbs";
            String lvlEst = String.format(Locale.ROOT, "Est. +%.1f levels (%d ench)", estLevels, removableCount);
            GuiRenderUtils.drawText(graphics, font, xpRange, rx + 5, curY + 5, 0xFF34D399, true);
            GuiRenderUtils.drawText(graphics, font, GuiRenderUtils.truncate(font, lvlEst, rw - 10),
                    rx + 5, curY + 18, 0xFFCBD5E1);
            curY += 36;
        }

        if (showTreasureWarn) {
            GuiRenderUtils.fillRoundedRect(graphics, rx, curY, rw, 26, 4, 0x33EF4444);
            GuiRenderUtils.drawRoundedOutline(graphics, rx, curY, rw, 26, 4, 0xEEEF4444);
            GuiRenderUtils.drawText(graphics, font, "⚠ Treasure Enchant!", rx + 5, curY + 4, 0xFFF87171, true);
            String lost = "Will destroy: " + String.join(", ", treasureWarnings);
            GuiRenderUtils.drawText(graphics, font, GuiRenderUtils.truncate(font, lost, rw - 10),
                    rx + 5, curY + 14, 0xFFFECACA);
            curY += 30;
        }

        if (!curseWarnings.isEmpty()) {
            GuiRenderUtils.fillRoundedRect(graphics, rx, curY, rw, 20, 4, 0x28F59E0B);
            GuiRenderUtils.drawRoundedOutline(graphics, rx, curY, rw, 20, 4, 0xAAF59E0B);
            GuiRenderUtils.drawText(graphics, font, "⚠ Curses cannot be removed", rx + 5, curY + 6, 0xFFFBBF24);
        }
    }

    private static double estimateLevelsGained(int startLevel, double xpOrbs) {
        double rem = xpOrbs;
        int lvl = Math.max(0, startLevel);
        double gained = 0.0;
        while (rem > 0.0 && gained < 50.0) {
            int xpForNext = lvl >= 30 ? (112 + (lvl - 30) * 9) : (lvl >= 15 ? (37 + (lvl - 15) * 5) : (7 + lvl * 2));
            if (rem >= xpForNext) {
                rem -= xpForNext;
                lvl++;
                gained += 1.0;
            } else {
                gained += rem / xpForNext;
                break;
            }
        }
        return gained;
    }

    private static boolean isTreasureEnchant(String path) {
        return path.equals("mending")
                || path.equals("soul_speed")
                || path.equals("swift_sneak")
                || path.equals("wind_burst")
                || path.equals("frost_walker");
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
