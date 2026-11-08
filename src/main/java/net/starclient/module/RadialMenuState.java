package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.starclient.gui.RadialMenuScreen;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.List;

/**
 * State bridge for the "Radial Quick-Swap" module:
 * Opens a glassmorphic 8-slot radial wheel around your crosshair while holding a hotkey
 * to instantly equip tools, food, pearls, or water buckets without hotbar scrolling.
 */
public final class RadialMenuState {

    public record RadialSlice(String name, ItemStack displayStack, int targetSlot) {
    }

    private static Module module;

    private RadialMenuState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static Module getModule() {
        return module;
    }

    public static boolean isActive() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static void openScreen(Minecraft client) {
        if (!isActive() || client == null || client.player == null) return;
        if (client.gui.screen() != null) return;
        client.gui.setScreen(new RadialMenuScreen(module.getKeyBind()));
    }

    public static RadialSlice[] computeSlices(Player player) {
        RadialSlice[] slices = new RadialSlice[8];
        if (player == null) return slices;

        Inventory inv = player.getInventory();
        String mode = getMode();

        if ("Hotbar Slots (1-8)".equals(mode)) {
            for (int i = 0; i < 8; i++) {
                ItemStack stack = inv.getItem(i);
                String label = "Slot " + (i + 1);
                slices[i] = new RadialSlice(label, stack, i);
            }
            return slices;
        }

        // Smart Survival Mode
        // 0: Sword / Weapon
        slices[0] = findBestMatching(inv, "Sword / Weapon", stack -> stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES), new ItemStack(Items.DIAMOND_SWORD));
        // 1: Pickaxe
        slices[1] = findBestMatching(inv, "Pickaxe", stack -> stack.is(ItemTags.PICKAXES), new ItemStack(Items.DIAMOND_PICKAXE));
        // 2: Axe
        slices[2] = findBestMatching(inv, "Axe", stack -> stack.is(ItemTags.AXES), new ItemStack(Items.DIAMOND_AXE));
        // 3: Shovel
        slices[3] = findBestMatching(inv, "Shovel", stack -> stack.is(ItemTags.SHOVELS), new ItemStack(Items.DIAMOND_SHOVEL));
        // 4: Water Bucket
        slices[4] = findBestMatching(inv, "Water Bucket", stack -> stack.is(Items.WATER_BUCKET), new ItemStack(Items.WATER_BUCKET));
        // 5: Ender Pearl
        slices[5] = findBestMatching(inv, "Ender Pearl", stack -> stack.is(Items.ENDER_PEARL), new ItemStack(Items.ENDER_PEARL));
        // 6: Food / Gapple
        slices[6] = findBestMatching(inv, "Food / Gapple", stack -> stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.has(net.minecraft.core.component.DataComponents.FOOD), new ItemStack(Items.GOLDEN_APPLE));
        // 7: Blocks / Bow
        slices[7] = findBestMatching(inv, "Blocks / Bow", stack -> stack.getItem() instanceof BlockItem || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem, new ItemStack(Items.COBBLESTONE));

        return slices;
    }

    private static RadialSlice findBestMatching(Inventory inv, String defaultLabel, java.util.function.Predicate<ItemStack> matcher, ItemStack fallback) {
        // Check hotbar first (0..8)
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && matcher.test(s)) {
                return new RadialSlice(s.getHoverName().getString(), s, i);
            }
        }
        // Check main inventory (9..35)
        for (int i = 9; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && matcher.test(s)) {
                return new RadialSlice(s.getHoverName().getString(), s, i);
            }
        }
        return new RadialSlice(defaultLabel, fallback, -1);
    }

    public static void executeSelection(Minecraft client, RadialSlice slice) {
        if (client == null || client.player == null || slice == null) return;
        int slot = slice.targetSlot();
        if (slot < 0) return;

        Inventory inv = client.player.getInventory();
        if (slot >= 0 && slot < 9) {
            inv.setSelectedSlot(slot);
        } else if (slot >= 9 && slot < 36) {
            inv.pickSlot(slot);
        }

        if (isPlaySound()) {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.65f, 1.4f);
        }
    }

    public static String getMode() {
        if (module == null) return "Smart Survival";
        Setting<?> s = module.getSetting("Mode");
        return s instanceof ModeSetting ms ? ms.getValue() : "Smart Survival";
    }

    public static int getRadius() {
        if (module == null) return 85;
        Setting<?> s = module.getSetting("Radius");
        return s instanceof NumberSetting ns ? (int) Math.round(ns.getValue()) : 85;
    }

    public static boolean isPlaySound() {
        return getBool("Play Sound", true);
    }

    public static boolean isShowItemCount() {
        return getBool("Show Item Count", true);
    }

    private static boolean getBool(String name, boolean def) {
        if (module == null) return def;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }
}
