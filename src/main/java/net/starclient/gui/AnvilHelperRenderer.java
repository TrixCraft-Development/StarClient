package net.starclient.gui;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhances Minecraft's Anvil GUI with Prior Work Penalty (PWP / Anvil Uses)
 * badges, true XP cost calculation past vanilla's "Too Expensive!" (40+) cap,
 * conflicting enchantment warnings, and a reverse-order Swap XP Optimizer.
 */
public final class AnvilHelperRenderer {
    private static final int MAX_VANILLA_COST = 39;

    private static Module module;

    private record SimulationResult(
            boolean valid,
            int totalCost,
            int nextRepairCost,
            List<String> conflicts
    ) {
    }

    private AnvilHelperRenderer() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    /**
     * Renders in AnvilScreen local coordinates where (0, 0) == (leftPos, topPos).
     */
    public static void renderInAnvilLabels(GuiGraphicsExtractor graphics, Font font, AnvilMenu menu,
                                           int screenW, int leftPos, int topPos, int imageWidth, int imageHeight,
                                           String customNameInput) {
        if (!isEnabled() || menu == null) return;

        ItemStack left = menu.getSlot(0).getItem();
        ItemStack right = menu.getSlot(1).getItem();
        ItemStack result = menu.getSlot(2).getItem();

        boolean showSlotBadges = getBool("Show Slot Use Badges", true);
        boolean showTrueCost = getBool("Show True Cost (40+)", true);
        boolean showSwapHint = getBool("Swap Order Optimizer", true);
        boolean showPanel = getBool("Show Helper Panel", true);
        boolean alwaysShowPanel = getBool("Always Show Panel", false);

        // 1. Subtle PWP / Anvil-use badges directly under input slots (slot 0: 27,47; slot 1: 76,47)
        if (showSlotBadges) {
            if (!left.isEmpty()) {
                drawSlotUseBadge(graphics, font, left, 27, 65);
            }
            if (!right.isEmpty()) {
                drawSlotUseBadge(graphics, font, right, 76, 65);
            }
        }

        int menuCost = menu.getCost();
        SimulationResult forward = simulateCombine(left, right, customNameInput);
        SimulationResult swapped = (!left.isEmpty() && !right.isEmpty())
                ? simulateCombine(right, left, customNameInput)
                : new SimulationResult(false, 0, 0, List.of());

        int displayedCost = menuCost > 0 ? menuCost : (forward.valid ? forward.totalCost : 0);

        // 2. Reveal actual level cost when vanilla displays "Too Expensive!" (cost >= 40)
        if (showTrueCost && displayedCost >= 40 && !left.isEmpty()) {
            String realCostLabel = "Real Cost: " + displayedCost + " lvls";
            int lw = font.width(realCostLabel);
            int rx = imageWidth - 8 - lw;
            int ry = 58;
            GuiRenderUtils.fillRoundedRect(graphics, rx - 3, ry - 1, lw + 6, 10, 3, 0xCC1E1016);
            GuiRenderUtils.drawRoundedOutline(graphics, rx - 3, ry - 1, lw + 6, 10, 3, 0xAAEF4444);
            graphics.text(font, realCostLabel, rx, ry, 0xFFFCA5A5, true);
        }

        // 3. Glassmorphic Anvil Helper side panel
        if (showPanel && (alwaysShowPanel || !left.isEmpty() || !right.isEmpty())) {
            renderSidePanel(graphics, font, screenW, leftPos, imageWidth, imageHeight,
                    left, right, result, displayedCost, forward, swapped, showSwapHint);
        }
    }

    private static void drawSlotUseBadge(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int slotX, int badgeY) {
        int pwp = getRepairCost(stack);
        int uses = pwpToUses(pwp);
        if (uses <= 0) return;

        String label = uses + "/6";
        int color = uses <= 2 ? 0xFF34D399 : (uses <= 4 ? 0xFFFBBF24 : 0xFFF87171);
        int w = font.width(label) + 4;
        int x = slotX + (16 - w) / 2;
        GuiRenderUtils.fillRoundedRect(graphics, x, badgeY, w, 10, 2, 0xDD0F172A);
        GuiRenderUtils.drawRoundedOutline(graphics, x, badgeY, w, 10, 2, GuiRenderUtils.withAlpha(color, 175));
        GuiRenderUtils.drawCenteredText(graphics, font, label, x + w / 2, badgeY + 1, color);
    }

    private static void renderSidePanel(GuiGraphicsExtractor graphics, Font font, int screenW, int leftPos,
                                        int imageWidth, int imageHeight,
                                        ItemStack left, ItemStack right, ItemStack result,
                                        int displayedCost, SimulationResult forward, SimulationResult swapped,
                                        boolean showSwapHint) {
        StarClient sc = StarClient.getInstance();
        ClientSettings cs = sc != null ? sc.getClientSettings() : null;
        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int bg = cs != null ? (0xEE000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xEE0F172A;
        int surface = cs != null ? cs.getMenuSurfaceColor() : 0xFF162032;
        int border = cs != null ? cs.getMenuBorderColor() : 0xFF334155;

        int panelW = 166;
        int panelX = imageWidth + 6;
        if (leftPos + panelX + panelW > screenW - 6) {
            panelX = -panelW - 6;
        }
        int panelY = 0;

        boolean hasSwapSaving = showSwapHint
                && forward.valid && swapped.valid
                && swapped.totalCost > 0
                && swapped.totalCost < displayedCost;
        int savedLevels = hasSwapSaving ? (displayedCost - swapped.totalCost) : 0;

        int panelH = 26;
        if (left.isEmpty() && right.isEmpty()) {
            panelH = 54;
        } else {
            if (!left.isEmpty()) panelH += 24;
            if (!right.isEmpty()) panelH += 24;
            if (displayedCost > 0) panelH += 34;
            if (hasSwapSaving) panelH += 26;
            if (!forward.conflicts.isEmpty()) panelH += 24;
            panelH = Math.min(Math.max(panelH + 6, 74), imageHeight + 24);
        }

        GuiRenderUtils.drawDropShadow(graphics, panelX, panelY, panelW, panelH, 6, 4, 110);
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, panelW, panelH, 6, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, panelX, panelY, panelW, panelH, 6, border);

        // Header
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, panelW, 20, 6, surface);
        graphics.fill(panelX, panelY + 19, panelX + panelW, panelY + 20, border);
        GuiRenderUtils.drawText(graphics, font, "⚒ Anvil Helper", panelX + 7, panelY + 6, accent, true);

        if (left.isEmpty() && right.isEmpty()) {
            GuiRenderUtils.drawCenteredText(graphics, font, "Place items to analyze XP",
                    panelX + panelW / 2, panelY + 33, 0xFF64748B);
            return;
        }

        int curY = panelY + 25;
        int rowX = panelX + 6;
        int rowW = panelW - 12;

        if (!left.isEmpty()) {
            drawInputPwpRow(graphics, font, rowX, curY, rowW, "Slot 1", left, surface, border);
            curY += 24;
        }
        if (!right.isEmpty()) {
            drawInputPwpRow(graphics, font, rowX, curY, rowW, "Slot 2", right, surface, border);
            curY += 24;
        }

        // Combine XP & Resulting PWP summary
        if (displayedCost > 0) {
            boolean overLimit = displayedCost > MAX_VANILLA_COST;
            Minecraft mc = Minecraft.getInstance();
            int playerLvls = mc.player != null ? mc.player.experienceLevel : 0;
            boolean creative = mc.player != null && mc.player.getAbilities().instabuild;
            boolean canAfford = creative || (!overLimit && playerLvls >= displayedCost);

            int cardBorder = overLimit ? 0xFFEF4444 : (canAfford ? 0xFF34D399 : 0xFFFBBF24);
            GuiRenderUtils.fillRoundedRect(graphics, rowX, curY, rowW, 30, 4, surface);
            GuiRenderUtils.drawRoundedOutline(graphics, rowX, curY, rowW, 30, 4, GuiRenderUtils.withAlpha(cardBorder, 165));

            String costLine = overLimit
                    ? "Cost: " + displayedCost + " lvls (Too High!)"
                    : "XP Cost: " + displayedCost + " Levels";
            GuiRenderUtils.drawText(graphics, font, GuiRenderUtils.truncate(font, costLine, rowW - 10),
                    rowX + 5, curY + 5, cardBorder, true);

            int nextPwp = !result.isEmpty() ? getRepairCost(result) : forward.nextRepairCost;
            int nextUses = pwpToUses(nextPwp);
            String resLine = "After: " + nextUses + "/6 uses (+" + nextPwp + " PWP)";
            GuiRenderUtils.drawText(graphics, font, GuiRenderUtils.truncate(font, resLine, rowW - 10),
                    rowX + 5, curY + 17, 0xFF94A3B8);
            curY += 34;
        }

        // Swap Order Optimizer banner
        if (hasSwapSaving) {
            GuiRenderUtils.fillRoundedRect(graphics, rowX, curY, rowW, 22, 4, 0x2834D399);
            GuiRenderUtils.drawRoundedOutline(graphics, rowX, curY, rowW, 22, 4, 0xCC34D399);
            String saveTitle = "💡 Swap inputs: Save " + savedLevels + (savedLevels == 1 ? " lvl!" : " lvls!");
            String saveSub = displayedCost + " lvls → " + swapped.totalCost + " lvls";
            GuiRenderUtils.drawText(graphics, font, GuiRenderUtils.truncate(font, saveTitle, rowW - 8),
                    rowX + 5, curY + 3, 0xFF34D399, true);
            GuiRenderUtils.drawText(graphics, font, saveSub, rowX + 5, curY + 12, 0xFFD1FAE5);
            curY += 26;
        }

        // Enchantment Conflict Warning
        if (!forward.conflicts.isEmpty()) {
            GuiRenderUtils.fillRoundedRect(graphics, rowX, curY, rowW, 20, 4, 0x28EF4444);
            GuiRenderUtils.drawRoundedOutline(graphics, rowX, curY, rowW, 20, 4, 0xAAEF4444);
            String warn = "⚠ Lost: " + String.join(", ", forward.conflicts);
            GuiRenderUtils.drawText(graphics, font, GuiRenderUtils.truncate(font, warn, rowW - 8),
                    rowX + 5, curY + 6, 0xFFFCA5A5);
        }
    }

    private static void drawInputPwpRow(GuiGraphicsExtractor graphics, Font font, int x, int y, int w,
                                        String slotTag, ItemStack stack, int surface, int border) {
        int pwp = getRepairCost(stack);
        int uses = pwpToUses(pwp);
        int col = uses <= 2 ? 0xFF34D399 : (uses <= 4 ? 0xFFFBBF24 : 0xFFF87171);

        GuiRenderUtils.fillRoundedRect(graphics, x, y, w, 20, 4, surface);
        GuiRenderUtils.drawRoundedOutline(graphics, x, y, w, 20, 4, border);
        graphics.item(stack, x + 2, y + 2);

        String info = slotTag + ": " + uses + "/6 uses";
        GuiRenderUtils.drawText(graphics, font, info, x + 21, y + 6, 0xFFE2E8F0);

        String pwpTag = "+" + pwp;
        int tw = font.width(pwpTag) + 6;
        GuiRenderUtils.fillRoundedRect(graphics, x + w - tw - 4, y + 4, tw, 12, 3, GuiRenderUtils.withAlpha(col, 40));
        GuiRenderUtils.drawCenteredText(graphics, font, pwpTag, x + w - tw / 2 - 4, y + 6, col);
    }

    /**
     * Exact client-side simulation of vanilla Anvil combination cost & conflicts so we can
     * show true costs beyond level 39 and compare (left + right) vs (right + left).
     */
    private static SimulationResult simulateCombine(ItemStack left, ItemStack right, String customNameInput) {
        if (left == null || left.isEmpty()) {
            return new SimulationResult(false, 0, 0, List.of());
        }

        int leftPwp = getRepairCost(left);
        int rightPwp = (right != null && !right.isEmpty()) ? getRepairCost(right) : 0;
        long basePwp = (long) leftPwp + (long) rightPwp;

        int addedCost = 0;
        boolean renamed = false;
        String currentName = left.getHoverName().getString();
        if (customNameInput != null && !customNameInput.isBlank() && !customNameInput.equals(currentName)) {
            renamed = true;
            addedCost += 1;
        }

        List<String> conflicts = new ArrayList<>();

        if (right == null || right.isEmpty()) {
            if (!renamed) return new SimulationResult(false, 0, leftPwp, List.of());
            int total = (int) Math.min(Integer.MAX_VALUE, basePwp + addedCost);
            return new SimulationResult(true, total, leftPwp, List.of());
        }

        boolean rightIsBook = right.is(Items.ENCHANTED_BOOK) && !getEnchantments(right).entrySet().isEmpty();
        boolean leftIsBook = left.is(Items.ENCHANTED_BOOK);
        boolean sameItem = left.is(right.getItem());
        boolean repairByMaterial = left.isDamageableItem() && left.isValidRepairItem(right);

        if (!rightIsBook && !sameItem && !repairByMaterial) {
            return new SimulationResult(false, 0, leftPwp, List.of());
        }

        if (repairByMaterial && !sameItem) {
            if (!left.isDamaged()) {
                return new SimulationResult(false, 0, leftPwp, List.of());
            }
            int dmg = left.getDamageValue();
            int step = Math.max(1, left.getMaxDamage() / 4);
            int matsNeeded = Math.min(right.getCount(), (dmg + step - 1) / step);
            addedCost += matsNeeded;
        } else {
            // Durability sacrifice repair
            if (left.isDamageableItem() && sameItem && left.isDamaged()) {
                addedCost += 2;
            }

            // Merge enchantments
            ItemEnchantments leftEnchs = getEnchantments(left);
            ItemEnchantments rightEnchs = getEnchantments(right);

            Map<Holder<Enchantment>, Integer> merged = new HashMap<>();
            for (Object2IntMap.Entry<Holder<Enchantment>> e : leftEnchs.entrySet()) {
                merged.put(e.getKey(), e.getIntValue());
            }

            boolean anyApplied = false;
            boolean anyIncompatible = false;

            for (Object2IntMap.Entry<Holder<Enchantment>> entry : rightEnchs.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                Enchantment ench = holder.value();
                int rLvl = entry.getIntValue();
                int lLvl = merged.getOrDefault(holder, 0);
                int newLvl = (lLvl == rLvl) ? Math.min(rLvl + 1, ench.getMaxLevel()) : Math.max(lLvl, rLvl);

                boolean canApply = leftIsBook || ench.canEnchant(left);
                if (canApply) {
                    for (Holder<Enchantment> existing : merged.keySet()) {
                        if (!existing.equals(holder) && !Enchantment.areCompatible(existing, holder)) {
                            canApply = false;
                            addedCost += 1;
                            anyIncompatible = true;
                            conflicts.add(stripFormatting(Enchantment.getFullname(holder, rLvl).getString()));
                            break;
                        }
                    }
                }

                if (canApply) {
                    anyApplied = true;
                    merged.put(holder, newLvl);
                    int anvilCost = ench.getAnvilCost();
                    if (rightIsBook) {
                        anvilCost = Math.max(1, anvilCost / 2);
                    }
                    addedCost += anvilCost * newLvl;
                    if (left.getCount() > 1) {
                        addedCost = 40;
                    }
                }
            }

            if (anyIncompatible && !anyApplied && !renamed && !(left.isDamageableItem() && left.isDamaged())) {
                return new SimulationResult(false, 0, leftPwp, conflicts);
            }
            if (!anyApplied && !renamed && !(left.isDamageableItem() && sameItem && left.isDamaged())) {
                return new SimulationResult(false, 0, leftPwp, conflicts);
            }
        }

        int total = (int) Math.min(Integer.MAX_VALUE, basePwp + addedCost);
        int nextPwp = AnvilMenu.calculateIncreasedRepairCost(Math.max(leftPwp, rightPwp));
        return new SimulationResult(true, total, nextPwp, conflicts);
    }

    private static ItemEnchantments getEnchantments(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemEnchantments.EMPTY;
        if (stack.is(Items.ENCHANTED_BOOK)) {
            ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
            return stored != null ? stored : ItemEnchantments.EMPTY;
        }
        ItemEnchantments enchs = stack.get(DataComponents.ENCHANTMENTS);
        return enchs != null ? enchs : ItemEnchantments.EMPTY;
    }

    public static int getRepairCost(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        Integer c = stack.get(DataComponents.REPAIR_COST);
        return c != null ? Math.max(0, c) : 0;
    }

    public static int pwpToUses(int pwp) {
        if (pwp <= 0) return 0;
        return Math.min(99, (int) Math.round(Math.log(pwp + 1.0) / Math.log(2.0)));
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
