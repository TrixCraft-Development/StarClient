package net.starclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.Setting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders a sleek side-by-side Equipped Gear Comparison card next to item tooltips
 * whenever hovering weapons, tools, or armor in inventories or containers ("Gear Comparison" UI module).
 */
public final class GearComparisonRenderer {

    private record StatRow(String label, String hoveredVal, double diff, String unit, boolean higherIsBetter) {
    }

    private record EnchDiff(String text, int color) {
    }

    private record ComparisonTarget(ItemStack equipped, String sourceLabel, EquipmentSlot attrSlot, boolean isArmor) {
    }

    private static Module module;
    private static Slot pendingSlot;
    private static ItemStack pendingStack = ItemStack.EMPTY;

    private GearComparisonRenderer() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static void queueForNextTooltip(Slot slot, ItemStack stack) {
        if (!isEnabled() || stack == null || stack.isEmpty()) {
            clearPending();
            return;
        }
        pendingSlot = slot;
        pendingStack = stack;
    }

    public static void clearPending() {
        pendingSlot = null;
        pendingStack = ItemStack.EMPTY;
    }

    public static boolean hasPending() {
        return pendingStack != null && !pendingStack.isEmpty();
    }

    public static void renderPendingForTooltip(GuiGraphicsExtractor graphics,
                                               int boxX, int boxY, int boxW, int boxH,
                                               int screenW, int screenH) {
        ItemStack hovered = pendingStack;
        Slot slot = pendingSlot;
        clearPending();
        if (!isEnabled() || hovered == null || hovered.isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        ComparisonTarget target = resolveComparisonTarget(client.player, slot, hovered);
        if (target == null) return;

        if (!isTriggerActive(client)) {
            if (isHoldKeyMode()) {
                renderHoldHint(graphics, client, boxX, boxY, boxW, boxH, screenW, screenH);
            }
            return;
        }

        boolean includeEnchBonuses = getBool("Include Enchant Bonuses", true);
        boolean showDurability = getBool("Show Durability Diff", true);
        boolean showEnchDiffs = getBool("Show Enchantment Diffs", true);

        List<StatRow> stats = computeStats(hovered, target.equipped(), target.attrSlot(), target.isArmor(), includeEnchBonuses, showDurability);
        List<EnchDiff> enchDiffs = showEnchDiffs ? computeEnchantmentDiffs(hovered, target.equipped()) : List.of();
        if (stats.isEmpty() && enchDiffs.isEmpty()) return;

        renderComparisonCard(graphics, client, hovered, target, stats, enchDiffs, boxX, boxY, boxW, boxH, screenW, screenH);
    }

    // -------------------------------------------------------------------------
    // 1. Target Matching (Armor / Offhand / Mainhand / Smart Hotbar Tool Match)
    // -------------------------------------------------------------------------

    private static ComparisonTarget resolveComparisonTarget(Player player, Slot hoveredSlot, ItemStack hovered) {
        Inventory inv = player.getInventory();
        boolean inPlayerInv = hoveredSlot != null && hoveredSlot.container == inv;
        int hoveredInvIdx = inPlayerInv ? hoveredSlot.getContainerSlot() : -1;

        // 1. Check Equippable Armor / Offhand (Helmet, Chestplate, Leggings, Boots, Elytra)
        Equippable equippable = hovered.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            EquipmentSlot eqSlot = equippable.slot();
            if (eqSlot == EquipmentSlot.HEAD || eqSlot == EquipmentSlot.CHEST
                    || eqSlot == EquipmentSlot.LEGS || eqSlot == EquipmentSlot.FEET) {
                if (!getBool("Compare Armor", true)) return null;
                int invSlotIndex = armorInventoryIndex(eqSlot);
                if (inPlayerInv && hoveredInvIdx == invSlotIndex) {
                    return null; // Hovering the already-equipped armor piece itself
                }
                ItemStack equipped = player.getItemBySlot(eqSlot);
                if (equipped == hovered) return null;
                String label = "Equipped " + slotTitle(eqSlot);
                return new ComparisonTarget(equipped, label, eqSlot, true);
            }
        }

        // 2. Check Weapons & Tools (Sword, Axe, Pickaxe, Shovel, Hoe, Bow, Crossbow, Trident, Mace, Shield, or Attack/Tool component)
        if (!getBool("Compare Weapons & Tools", true)) return null;
        String category = classifyWeaponOrTool(hovered);
        boolean hasAttackOrTool = hasCombatOrToolStats(hovered);
        if (category == null && !hasAttackOrTool) return null;

        // Shields compare against offhand first, then mainhand
        if ("shield".equals(category)) {
            if (inPlayerInv && hoveredInvIdx == 40) return null;
            ItemStack offhand = player.getOffhandItem();
            if (offhand == hovered) return null;
            if (!offhand.isEmpty() && "shield".equals(classifyWeaponOrTool(offhand))) {
                return new ComparisonTarget(offhand, "Equipped Offhand", EquipmentSlot.OFFHAND, false);
            }
            return new ComparisonTarget(ItemStack.EMPTY, "Offhand (Empty)", EquipmentSlot.OFFHAND, false);
        }

        // Don't compare against yourself if hovering your currently selected hotbar slot
        int selectedHotbar = inv.getSelectedSlot();
        if (inPlayerInv && hoveredInvIdx == selectedHotbar) {
            return null;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand == hovered) return null;

        boolean smartMatch = getBool("Smart Hotbar Match", true);
        if (smartMatch && category != null) {
            // If currently holding the same category, compare with main hand
            if (!mainHand.isEmpty() && category.equals(classifyWeaponOrTool(mainHand))) {
                return new ComparisonTarget(mainHand, "Equipped Main Hand", EquipmentSlot.MAINHAND, false);
            }
            // Otherwise search hotbar (slots 0..8) for the best matching item of the same category
            int bestIdx = -1;
            ItemStack bestStack = ItemStack.EMPTY;
            for (int i = 0; i < 9; i++) {
                if (inPlayerInv && i == hoveredInvIdx) continue;
                ItemStack cand = inv.getItem(i);
                if (cand.isEmpty() || cand == hovered) continue;
                if (category.equals(classifyWeaponOrTool(cand))) {
                    bestIdx = i;
                    bestStack = cand;
                    break;
                }
            }
            if (bestIdx >= 0) {
                return new ComparisonTarget(bestStack, "Hotbar #" + (bestIdx + 1) + " " + capitalize(category), EquipmentSlot.MAINHAND, false);
            }
            // If main hand has another weapon/tool, compare with main hand; else mark slot empty
            if (!mainHand.isEmpty() && hasCombatOrToolStats(mainHand)) {
                return new ComparisonTarget(mainHand, "Equipped Main Hand", EquipmentSlot.MAINHAND, false);
            }
            return new ComparisonTarget(ItemStack.EMPTY, "No " + capitalize(category) + " Equipped", EquipmentSlot.MAINHAND, false);
        }

        if (!mainHand.isEmpty() && hasCombatOrToolStats(mainHand)) {
            return new ComparisonTarget(mainHand, "Equipped Main Hand", EquipmentSlot.MAINHAND, false);
        }
        return new ComparisonTarget(ItemStack.EMPTY, "Main Hand (Empty)", EquipmentSlot.MAINHAND, false);
    }

    private static String classifyWeaponOrTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();
        if (item == Items.BOW) return "bow";
        if (item == Items.CROSSBOW) return "crossbow";
        if (item == Items.TRIDENT) return "trident";
        if (item == Items.MACE) return "mace";
        if (item == Items.SHIELD) return "shield";
        if (item == Items.FISHING_ROD) return "rod";
        if (item == Items.SHEARS) return "shears";

        String path = BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith("_sword")) return "sword";
        if (path.endsWith("_pickaxe")) return "pickaxe";
        if (path.endsWith("_axe")) return "axe";
        if (path.endsWith("_shovel")) return "shovel";
        if (path.endsWith("_hoe")) return "hoe";
        if (path.endsWith("_spear")) return "spear";
        return null;
    }

    private static boolean hasCombatOrToolStats(ItemStack stack) {
        if (stack.get(DataComponents.TOOL) != null) return true;
        ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods != null) {
            double dmg = mods.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
            if (Math.abs(dmg - 1.0) > 0.01) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // 2. Stat & Enchantment Diff Calculation
    // -------------------------------------------------------------------------

    private static List<StatRow> computeStats(ItemStack hovered, ItemStack equipped,
                                              EquipmentSlot slot, boolean isArmor,
                                              boolean includeEnchBonuses, boolean showDurability) {
        List<StatRow> rows = new ArrayList<>();
        boolean eqEmpty = equipped == null || equipped.isEmpty();

        if (isArmor) {
            double hArmor = getAttr(hovered, Attributes.ARMOR, 0.0, slot);
            double eArmor = eqEmpty ? 0.0 : getAttr(equipped, Attributes.ARMOR, 0.0, slot);
            if (hArmor > 0.001 || eArmor > 0.001) {
                rows.add(new StatRow("Armor", fmtNum(hArmor), hArmor - eArmor, "", true));
            }

            double hTough = getAttr(hovered, Attributes.ARMOR_TOUGHNESS, 0.0, slot);
            double eTough = eqEmpty ? 0.0 : getAttr(equipped, Attributes.ARMOR_TOUGHNESS, 0.0, slot);
            if (hTough > 0.001 || eTough > 0.001) {
                rows.add(new StatRow("Toughness", fmtNum(hTough), hTough - eTough, "", true));
            }

            double hKb = getAttr(hovered, Attributes.KNOCKBACK_RESISTANCE, 0.0, slot) * 100.0;
            double eKb = eqEmpty ? 0.0 : getAttr(equipped, Attributes.KNOCKBACK_RESISTANCE, 0.0, slot) * 100.0;
            if (hKb > 0.01 || eKb > 0.01) {
                rows.add(new StatRow("KB Resist", fmtNum(hKb) + "%", hKb - eKb, "%", true));
            }
        } else {
            double hDmg = getEffectiveDamage(hovered, includeEnchBonuses);
            double eDmg = eqEmpty ? 1.0 : getEffectiveDamage(equipped, includeEnchBonuses);
            if (hDmg > 1.01 || eDmg > 1.01) {
                rows.add(new StatRow("Damage", fmtNum(hDmg), hDmg - eDmg, "", true));

                double hSpd = getAttr(hovered, Attributes.ATTACK_SPEED, 4.0, EquipmentSlot.MAINHAND);
                double eSpd = eqEmpty ? 4.0 : getAttr(equipped, Attributes.ATTACK_SPEED, 4.0, EquipmentSlot.MAINHAND);
                rows.add(new StatRow("Atk Speed", fmtNum(hSpd), hSpd - eSpd, "", true));

                double hDps = hDmg * Math.max(0.1, hSpd);
                double eDps = eDmg * Math.max(0.1, eSpd);
                rows.add(new StatRow("Est. DPS", fmtNum(hDps), hDps - eDps, "", true));
            }

            double hMine = getEffectiveMiningSpeed(hovered, includeEnchBonuses);
            double eMine = eqEmpty ? 1.0 : getEffectiveMiningSpeed(equipped, includeEnchBonuses);
            if (hMine > 1.05 || eMine > 1.05) {
                rows.add(new StatRow("Mine Speed", fmtNum(hMine), hMine - eMine, "", true));
            }
        }

        if (showDurability && (hovered.isDamageableItem() || (!eqEmpty && equipped.isDamageableItem()))) {
            int hDur = hovered.isDamageableItem() ? Math.max(0, hovered.getMaxDamage() - hovered.getDamageValue()) : 0;
            int eDur = (!eqEmpty && equipped.isDamageableItem()) ? Math.max(0, equipped.getMaxDamage() - equipped.getDamageValue()) : 0;
            rows.add(new StatRow("Durability", String.valueOf(hDur), hDur - eDur, "", true));
        }

        return rows;
    }

    private static double getAttr(ItemStack stack, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                  double base, EquipmentSlot slot) {
        if (stack == null || stack.isEmpty()) return base;
        ItemAttributeModifiers mods = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        return mods != null ? mods.compute(attr, base, slot) : base;
    }

    private static double getEffectiveDamage(ItemStack stack, boolean includeSharpness) {
        double dmg = getAttr(stack, Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
        if (includeSharpness) {
            int sharp = getEnchantLevelByPath(stack, "sharpness");
            if (sharp > 0) {
                dmg += 0.5 * sharp + 0.5;
            }
        }
        return dmg;
    }

    private static double getEffectiveMiningSpeed(ItemStack stack, boolean includeEfficiency) {
        if (stack == null || stack.isEmpty()) return 1.0;
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool == null) return 1.0;
        float best = tool.defaultMiningSpeed();
        for (Tool.Rule rule : tool.rules()) {
            if (rule.speed().isPresent() && rule.speed().get() > best) {
                best = rule.speed().get();
            }
        }
        double speed = best;
        if (includeEfficiency && best > 1.0f) {
            int eff = getEnchantLevelByPath(stack, "efficiency");
            if (eff > 0) {
                speed += (eff * eff + 1);
            }
        }
        return speed;
    }

    private static int getEnchantLevelByPath(ItemStack stack, String pathName) {
        ItemEnchantments enchs = stack.get(DataComponents.ENCHANTMENTS);
        if (enchs == null || enchs.isEmpty()) return 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchs.entrySet()) {
            String path = entry.getKey().unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            if (pathName.equals(path)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    private static List<EnchDiff> computeEnchantmentDiffs(ItemStack hovered, ItemStack equipped) {
        Map<Holder<Enchantment>, Integer> hMap = new LinkedHashMap<>();
        Map<Holder<Enchantment>, Integer> eMap = new LinkedHashMap<>();

        ItemEnchantments hEnch = hovered.get(DataComponents.ENCHANTMENTS);
        if (hEnch != null) {
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : hEnch.entrySet()) {
                hMap.put(entry.getKey(), entry.getIntValue());
            }
        }
        if (equipped != null && !equipped.isEmpty()) {
            ItemEnchantments eEnch = equipped.get(DataComponents.ENCHANTMENTS);
            if (eEnch != null) {
                for (Object2IntMap.Entry<Holder<Enchantment>> entry : eEnch.entrySet()) {
                    eMap.put(entry.getKey(), entry.getIntValue());
                }
            }
        }

        List<EnchDiff> out = new ArrayList<>();
        // Gains and upgrades
        for (Map.Entry<Holder<Enchantment>, Integer> entry : hMap.entrySet()) {
            Holder<Enchantment> h = entry.getKey();
            int hLvl = entry.getValue();
            int eLvl = eMap.getOrDefault(h, 0);
            String name = formatEnchant(h, hLvl);
            boolean curse = isCurse(h);
            if (eLvl == 0) {
                out.add(new EnchDiff("+ " + name, curse ? 0xFFF87171 : 0xFF34D399));
            } else if (hLvl > eLvl) {
                out.add(new EnchDiff("▲ " + name + " (was " + roman(eLvl) + ")", curse ? 0xFFF87171 : 0xFF34D399));
            } else if (hLvl < eLvl) {
                out.add(new EnchDiff("▼ " + name + " (was " + roman(eLvl) + ")", curse ? 0xFF34D399 : 0xFFF87171));
            }
        }
        // Lost enchantments present on equipped item
        for (Map.Entry<Holder<Enchantment>, Integer> entry : eMap.entrySet()) {
            Holder<Enchantment> h = entry.getKey();
            if (!hMap.containsKey(h)) {
                boolean curse = isCurse(h);
                out.add(new EnchDiff("− " + formatEnchant(h, entry.getValue()), curse ? 0xFF34D399 : 0xFFF87171));
            }
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // 3. Glassmorphic Side-by-Side Card Rendering
    // -------------------------------------------------------------------------

    private static void renderComparisonCard(GuiGraphicsExtractor graphics, Minecraft client,
                                             ItemStack hovered, ComparisonTarget target,
                                             List<StatRow> stats, List<EnchDiff> enchDiffs,
                                             int boxX, int boxY, int boxW, int boxH,
                                             int screenW, int screenH) {
        Font font = client.font;
        StarClient sc = StarClient.getInstance();
        ClientSettings cs = sc != null ? sc.getClientSettings() : null;
        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int bg = cs != null ? (0xF2000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xF20B1120;
        int surface = cs != null ? cs.getMenuSurfaceColor() : 0xFF162032;
        int border = cs != null ? cs.getMenuBorderColor() : 0xFF334155;

        // Overall verdict
        int gains = 0;
        int losses = 0;
        for (StatRow row : stats) {
            if (Math.abs(row.diff()) < 0.01) continue;
            if (row.diff() > 0) gains++;
            else losses++;
        }
        String verdict;
        int verdictCol;
        if (target.equipped().isEmpty()) {
            verdict = "NEW";
            verdictCol = 0xFF38BDF8;
        } else if (gains > 0 && losses == 0) {
            verdict = "▲ UPGRADE";
            verdictCol = 0xFF34D399;
        } else if (losses > 0 && gains == 0) {
            verdict = "▼ LOWER";
            verdictCol = 0xFFF87171;
        } else if (gains > 0 && losses > 0) {
            verdict = "◆ MIXED";
            verdictCol = 0xFFFBBF24;
        } else {
            verdict = "＝ EQUAL";
            verdictCol = 0xFF94A3B8;
        }

        int cardW = 166;
        int maxEnchShown = Math.min(enchDiffs.size(), 6);
        int headerH = 34;
        int statsH = stats.size() * 13 + (stats.isEmpty() ? 0 : 4);
        int enchH = maxEnchShown > 0 ? (10 + maxEnchShown * 11 + (enchDiffs.size() > maxEnchShown ? 10 : 0)) : 0;
        int cardH = headerH + statsH + enchH + 6;

        int[] pos = placeBesideTooltip(cardW, cardH, boxX, boxY, boxW, boxH, screenW, screenH);
        int x = pos[0];
        int y = pos[1];

        graphics.nextStratum();
        GuiRenderUtils.drawDropShadow(graphics, x, y, cardW, cardH, 6, 4, 120);
        GuiRenderUtils.fillRoundedRect(graphics, x, y, cardW, cardH, 6, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, x, y, cardW, cardH, 6, GuiRenderUtils.withAlpha(verdictCol, 185));

        // Header plate
        GuiRenderUtils.fillRoundedRect(graphics, x, y, cardW, headerH, 6, surface);
        graphics.fill(x, y + headerH - 1, x + cardW, y + headerH, border);

        // Top line: "⚖ Compare" + Verdict badge
        graphics.text(font, "⚖ " + GuiRenderUtils.truncate(font, target.sourceLabel(), cardW - font.width(verdict) - 24),
                x + 6, y + 4, 0xFFCBD5E1, false);
        int vw = font.width(verdict) + 8;
        int vx = x + cardW - vw - 5;
        GuiRenderUtils.fillRoundedRect(graphics, vx, y + 3, vw, 10, 3, GuiRenderUtils.withAlpha(verdictCol, 45));
        graphics.text(font, verdict, vx + 4, y + 4, verdictCol, false);

        // Second header line: equipped item icon + name
        if (!target.equipped().isEmpty()) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 6, y + 16);
            graphics.pose().scale(0.85f, 0.85f);
            graphics.item(target.equipped(), 0, 0);
            graphics.pose().popMatrix();
            String eqName = GuiRenderUtils.truncate(font, target.equipped().getHoverName().getString(), cardW - 28);
            graphics.text(font, eqName, x + 22, y + 20, 0xFFF8FAFC, true);
        } else {
            graphics.text(font, "No item currently equipped", x + 6, y + 20, 0xFF64748B, false);
        }

        int curY = y + headerH + 4;

        // Stat diff rows
        for (StatRow row : stats) {
            graphics.text(font, row.label(), x + 7, curY, 0xFF94A3B8, false);

            String diffStr;
            int diffCol;
            if (Math.abs(row.diff()) < 0.01) {
                diffStr = "(=)";
                diffCol = 0xFF64748B;
            } else if (row.diff() > 0) {
                diffStr = "(+" + fmtNum(row.diff()) + row.unit() + ")";
                diffCol = row.higherIsBetter() ? 0xFF34D399 : 0xFFF87171;
            } else {
                diffStr = "(" + fmtNum(row.diff()) + row.unit() + ")";
                diffCol = row.higherIsBetter() ? 0xFFF87171 : 0xFF34D399;
            }

            int diffW = font.width(diffStr);
            int valW = font.width(row.hoveredVal());
            graphics.text(font, diffStr, x + cardW - 7 - diffW, curY, diffCol, false);
            graphics.text(font, row.hoveredVal(), x + cardW - 11 - diffW - valW, curY, 0xFFF8FAFC, false);
            curY += 13;
        }

        // Enchantment diffs section
        if (maxEnchShown > 0) {
            curY += 2;
            graphics.fill(x + 7, curY, x + cardW - 7, curY + 1, border);
            curY += 4;
            for (int i = 0; i < maxEnchShown; i++) {
                EnchDiff ed = enchDiffs.get(i);
                String line = GuiRenderUtils.truncate(font, ed.text(), cardW - 14);
                graphics.text(font, line, x + 7, curY, ed.color(), false);
                curY += 11;
            }
            if (enchDiffs.size() > maxEnchShown) {
                String more = "+" + (enchDiffs.size() - maxEnchShown) + " more enchant diffs";
                graphics.text(font, more, x + 7, curY, 0xFF64748B, false);
            }
        }
    }

    private static void renderHoldHint(GuiGraphicsExtractor graphics, Minecraft client,
                                       int boxX, int boxY, int boxW, int boxH,
                                       int screenW, int screenH) {
        graphics.nextStratum();
        StarClient sc = StarClient.getInstance();
        int accent = (sc != null && sc.getClientSettings() != null)
                ? sc.getClientSettings().getMenuAccentColor()
                : 0xFF38BDF8;
        String hint = "Hold [" + getMode("Compare Key", "Shift") + "] to compare gear";
        int w = client.font.width(hint) + 12;
        int h = 15;
        int[] pos = placeBesideTooltip(w, h, boxX, boxY, boxW, boxH, screenW, screenH);
        GuiRenderUtils.drawDropShadow(graphics, pos[0], pos[1], w, h, 4, 2, 95);
        GuiRenderUtils.fillRoundedRect(graphics, pos[0], pos[1], w, h, 4, 0xF20B1120);
        GuiRenderUtils.drawRoundedOutline(graphics, pos[0], pos[1], w, h, 4, GuiRenderUtils.withAlpha(accent, 190));
        graphics.text(client.font, hint, pos[0] + 6, pos[1] + 4, 0xFFE2E8F0, false);
    }

    private static int[] placeBesideTooltip(int cardW, int cardH,
                                            int boxX, int boxY, int boxW, int boxH,
                                            int screenW, int screenH) {
        int gap = 6;
        int clampedY = Math.clamp(boxY, 6, Math.max(6, screenH - cardH - 6));

        // 1. Prefer cleanly to the right of the vanilla tooltip
        if (boxX + boxW + gap + cardW <= screenW - 6) {
            return new int[]{boxX + boxW + gap, clampedY};
        }
        // 2. Otherwise cleanly to the left of the vanilla tooltip
        if (boxX - cardW - gap >= 6) {
            return new int[]{boxX - cardW - gap, clampedY};
        }
        // 3. Fallback: below or above
        int clampedX = Math.clamp(boxX, 6, Math.max(6, screenW - cardW - 6));
        if (boxY + boxH + gap + cardH <= screenH - 6) {
            return new int[]{clampedX, boxY + boxH + gap};
        }
        if (boxY - cardH - gap >= 6) {
            return new int[]{clampedX, boxY - cardH - gap};
        }
        return new int[]{clampedX, clampedY};
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static boolean isHoldKeyMode() {
        return "Hold Key".equalsIgnoreCase(getMode("Display Mode", "Always"));
    }

    private static boolean isTriggerActive(Minecraft client) {
        if (!isHoldKeyMode()) return true;
        Window window = client.getWindow();
        String keyMode = getMode("Compare Key", "Shift");
        return switch (keyMode) {
            case "Ctrl" -> InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
            case "Alt" -> InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
            default -> InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        };
    }

    private static int armorInventoryIndex(EquipmentSlot slot) {
        return switch (slot) {
            case FEET -> 36;
            case LEGS -> 37;
            case CHEST -> 38;
            case HEAD -> 39;
            case OFFHAND -> 40;
            default -> -1;
        };
    }

    private static String slotTitle(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "Helmet";
            case CHEST -> "Chestplate";
            case LEGS -> "Leggings";
            case FEET -> "Boots";
            case OFFHAND -> "Offhand";
            default -> "Gear";
        };
    }

    private static String formatEnchant(Holder<Enchantment> holder, int level) {
        String name = holder.value().description().getString();
        if (level > 1 || holder.value().getMaxLevel() > 1) {
            return name + " " + roman(level);
        }
        return name;
    }

    private static boolean isCurse(Holder<Enchantment> holder) {
        String path = holder.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
        return path.contains("curse");
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private static String fmtNum(double val) {
        if (Math.abs(val - Math.rint(val)) < 0.05) {
            return String.valueOf((long) Math.rint(val));
        }
        return String.format(Locale.US, "%.1f", val);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static boolean getBool(String name, boolean def) {
        if (module == null) return def;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }

    private static String getMode(String name, String def) {
        if (module == null) return def;
        Setting<?> s = module.getSetting(name);
        return s instanceof ModeSetting ms ? ms.getValue() : def;
    }
}
