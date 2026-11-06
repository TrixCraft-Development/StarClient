package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.Setting;

import java.util.List;

public final class InventoryHudRenderer {

    private static final List<ItemStack> PREVIEW_MAIN = List.of(
            new ItemStack(Items.DIAMOND_SWORD),
            new ItemStack(Items.BOW),
            new ItemStack(Items.WATER_BUCKET),
            new ItemStack(Items.GOLDEN_APPLE, 8),
            new ItemStack(Items.ENDER_PEARL, 16),
            new ItemStack(Items.COOKED_BEEF, 64),
            new ItemStack(Items.OAK_PLANKS, 64),
            new ItemStack(Items.COBBLESTONE, 64),
            new ItemStack(Items.TORCH, 64),

            new ItemStack(Items.DIAMOND_PICKAXE),
            new ItemStack(Items.DIAMOND_AXE),
            new ItemStack(Items.DIAMOND_SHOVEL),
            new ItemStack(Items.IRON_INGOT, 48),
            new ItemStack(Items.GOLD_INGOT, 16),
            new ItemStack(Items.DIAMOND, 12),
            new ItemStack(Items.COAL, 64),
            new ItemStack(Items.ARROW, 64),
            ItemStack.EMPTY,

            new ItemStack(Items.FIREWORK_ROCKET, 32),
            new ItemStack(Items.REDSTONE, 64),
            new ItemStack(Items.LAPIS_LAZULI, 32),
            new ItemStack(Items.STRING, 16),
            new ItemStack(Items.FEATHER, 24),
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY
    );

    private static final List<ItemStack> PREVIEW_HOTBAR = List.of(
            new ItemStack(Items.NETHERITE_SWORD),
            new ItemStack(Items.BOW),
            new ItemStack(Items.WATER_BUCKET),
            new ItemStack(Items.GOLDEN_APPLE, 12),
            new ItemStack(Items.ENDER_PEARL, 16),
            new ItemStack(Items.COBBLESTONE, 64),
            new ItemStack(Items.GOLDEN_CARROT, 64),
            new ItemStack(Items.TOTEM_OF_UNDYING),
            new ItemStack(Items.DIAMOND_PICKAXE)
    );

    private static final List<ItemStack> PREVIEW_ARMOR = List.of(
            new ItemStack(Items.DIAMOND_HELMET),
            new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS),
            new ItemStack(Items.DIAMOND_BOOTS)
    );

    private InventoryHudRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, Minecraft client, Module mod) {
        if (mod == null || !mod.isEnabled()) return;
        if (client == null || client.player == null) return;

        // If "Show HUD Widget" is false, do not render the on-screen HUD widget
        if (!isTrue(mod, "Show HUD Widget", true)) return;

        // Hide in inventory / container screens if setting enabled
        boolean hideInInventory = isTrue(mod, "Hide in Inventory", true);
        if (hideInInventory && client.gui.screen() instanceof AbstractContainerScreen<?>) {
            return;
        }

        renderInternal(graphics, client, mod, false);
    }

    public static void renderPreview(GuiGraphicsExtractor graphics, Module mod) {
        if (mod == null || !mod.isEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        renderInternal(graphics, client, mod, true);
    }

    private static void renderInternal(GuiGraphicsExtractor graphics, Minecraft client, Module mod, boolean isPreview) {
        HudLayout layout = StarClient.getInstance().getHudLayout();
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();

        int width = layout.inventoryWidth();
        int height = layout.inventoryHeight();

        int x = layout.effectiveX(HudLayout.INVENTORY, screenW, width);
        int y = layout.effectiveY(HudLayout.INVENTORY, screenH);
        x = Math.clamp(x, 4, Math.max(4, screenW - width - 4));
        y = Math.clamp(y, 4, Math.max(4, screenH - height - 4));

        boolean includeHotbar = isTrue(mod, "Include Hotbar", false);
        boolean showOffhand = isTrue(mod, "Show Offhand", true);
        boolean showArmor = isTrue(mod, "Show Armor", false);
        boolean showTitle = isTrue(mod, "Show Title", false);
        boolean highlightSelected = isTrue(mod, "Highlight Selected", true);
        boolean showSlotBg = isTrue(mod, "Show Slot Backgrounds", true);
        boolean transparent = mod.isTransparentBackground();

        int modBg = mod.getBackgroundColor();
        int textColor = mod.getTextColor();

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;

        // Outer glassmorphic frame
        if (!transparent) {
            int cardBg = (modBg & 0x00FFFFFF) | 0xCC000000;
            GuiRenderUtils.drawDropShadow(graphics, x, y, width, height, 6, 3, 90);
            GuiRenderUtils.fillRoundedRect(graphics, x, y, width, height, 6, cardBg);
            GuiRenderUtils.drawRoundedOutline(graphics, x, y, width, height, 6, 0x28FFFFFF);
        }

        // Header Title
        if (showTitle) {
            GuiRenderUtils.drawText(graphics, client.font, "Inventory", x + 6, y + 4, textColor);
            graphics.fill(x + 5, y + 14, x + width - 5, y + 15, 0x1FFFFFFF);
        }

        int gridX = x + 5;
        int gridY = y + 5 + (showTitle ? 12 : 0);
        int slotSize = 18;
        int slotGap = 2;
        int slotBg = (modBg & 0x00FFFFFF) | 0x55000000;
        int slotBorder = 0x22FFFFFF;

        Inventory inventory = (!isPreview && client.player != null) ? client.player.getInventory() : null;

        // 1. Main 27 Inventory Slots (3 rows of 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIdx = 9 + row * 9 + col;
                int sx = gridX + col * (slotSize + slotGap);
                int sy = gridY + row * (slotSize + slotGap);

                if (showSlotBg) {
                    GuiRenderUtils.fillRoundedRect(graphics, sx, sy, slotSize, slotSize, 3, slotBg);
                    GuiRenderUtils.drawRoundedOutline(graphics, sx, sy, slotSize, slotSize, 3, slotBorder);
                }

                ItemStack stack = ItemStack.EMPTY;
                if (inventory != null) {
                    stack = inventory.getItem(slotIdx);
                } else if (isPreview) {
                    int pIdx = row * 9 + col;
                    if (pIdx < PREVIEW_MAIN.size()) {
                        stack = PREVIEW_MAIN.get(pIdx);
                    }
                }

                if (!stack.isEmpty()) {
                    graphics.item(stack, sx + 1, sy + 1);
                    graphics.itemDecorations(client.font, stack, sx + 1, sy + 1);
                }
            }
        }

        // 2. Hotbar Slots (if enabled)
        if (includeHotbar) {
            int hotbarY = gridY + 3 * (slotSize + slotGap) + 3;
            graphics.fill(gridX, hotbarY - 2, gridX + 9 * (slotSize + slotGap) - slotGap, hotbarY - 1, 0x28FFFFFF);

            int selectedSlot = (inventory != null) ? inventory.getSelectedSlot() : 0;
            for (int col = 0; col < 9; col++) {
                int sx = gridX + col * (slotSize + slotGap);
                int sy = hotbarY;
                boolean isSelected = (col == selectedSlot);

                if (showSlotBg) {
                    int sBg = (isSelected && highlightSelected) ? GuiRenderUtils.withAlpha(accent, 70) : slotBg;
                    int sBorder = (isSelected && highlightSelected) ? accent : slotBorder;
                    GuiRenderUtils.fillRoundedRect(graphics, sx, sy, slotSize, slotSize, 3, sBg);
                    GuiRenderUtils.drawRoundedOutline(graphics, sx, sy, slotSize, slotSize, 3, sBorder);
                }

                ItemStack stack = ItemStack.EMPTY;
                if (inventory != null) {
                    stack = inventory.getItem(col);
                } else if (isPreview && col < PREVIEW_HOTBAR.size()) {
                    stack = PREVIEW_HOTBAR.get(col);
                }

                if (!stack.isEmpty()) {
                    graphics.item(stack, sx + 1, sy + 1);
                    graphics.itemDecorations(client.font, stack, sx + 1, sy + 1);
                }
            }
        }

        // 3. Side Columns: Armor & Offhand
        int extraX = gridX + 9 * (slotSize + slotGap) + 3;

        if (showArmor) {
            EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < armorSlots.length; i++) {
                int ay = gridY + i * (slotSize + slotGap);
                if (ay + slotSize > y + height + 4) break;

                if (showSlotBg) {
                    GuiRenderUtils.fillRoundedRect(graphics, extraX, ay, slotSize, slotSize, 3, slotBg);
                    GuiRenderUtils.drawRoundedOutline(graphics, extraX, ay, slotSize, slotSize, 3, slotBorder);
                }

                ItemStack aStack = ItemStack.EMPTY;
                if (!isPreview && client.player != null) {
                    aStack = client.player.getItemBySlot(armorSlots[i]);
                } else if (isPreview && i < PREVIEW_ARMOR.size()) {
                    aStack = PREVIEW_ARMOR.get(i);
                }

                if (!aStack.isEmpty()) {
                    graphics.item(aStack, extraX + 1, ay + 1);
                    graphics.itemDecorations(client.font, aStack, extraX + 1, ay + 1);
                } else if (showSlotBg) {
                    String icon = switch (i) {
                        case 0 -> "⛑";
                        case 1 -> "🦺";
                        case 2 -> "👖";
                        default -> "🥾";
                    };
                    GuiRenderUtils.drawCenteredText(graphics, client.font, icon, extraX + slotSize / 2, ay + 4, 0x44FFFFFF);
                }
            }
            extraX += slotSize + slotGap + 1;
        }

        if (showOffhand) {
            int offhandY = gridY;
            if (showSlotBg) {
                GuiRenderUtils.fillRoundedRect(graphics, extraX, offhandY, slotSize, slotSize, 3, slotBg);
                GuiRenderUtils.drawRoundedOutline(graphics, extraX, offhandY, slotSize, slotSize, 3, slotBorder);
            }

            ItemStack offhand = ItemStack.EMPTY;
            if (!isPreview && client.player != null) {
                offhand = client.player.getOffhandItem();
            } else if (isPreview) {
                offhand = new ItemStack(Items.SHIELD);
            }

            if (!offhand.isEmpty()) {
                graphics.item(offhand, extraX + 1, offhandY + 1);
                graphics.itemDecorations(client.font, offhand, extraX + 1, offhandY + 1);
            } else if (showSlotBg) {
                GuiRenderUtils.drawCenteredText(graphics, client.font, "🛡", extraX + slotSize / 2, offhandY + 4, 0x44FFFFFF);
            }
        }
    }

    private static boolean isTrue(Module mod, String settingName, boolean defaultValue) {
        if (mod == null) return defaultValue;
        Setting<?> s = mod.getSetting(settingName);
        return (s != null && s.getValue() instanceof Boolean b) ? b : defaultValue;
    }
}
