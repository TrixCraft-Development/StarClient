package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.Setting;

import java.util.List;
import java.util.Locale;

public final class InventorySearchManager {

    private static String searchQuery = "";
    private static boolean searchFocused = false;

    private InventorySearchManager() {
    }

    public static boolean isEnabled() {
        StarClient sc = StarClient.getInstance();
        if (sc == null || sc.getModuleManager() == null) return false;
        Module mod = sc.getModuleManager().getModuleByName("Inventory HUD");
        if (mod == null) return false;
        Setting<?> s = mod.getSetting("Inventory Search");
        return s != null && Boolean.TRUE.equals(s.getValue());
    }

    public static String getSearchQuery() {
        return searchQuery;
    }

    public static void setSearchQuery(String query) {
        searchQuery = (query != null) ? query : "";
    }

    public static boolean isSearchFocused() {
        return searchFocused;
    }

    public static void setSearchFocused(boolean focused) {
        searchFocused = focused;
    }

    public static void reset() {
        searchQuery = "";
        searchFocused = false;
    }

    public static boolean matchesSearch(ItemStack stack, String query) {
        if (stack == null || stack.isEmpty()) return false;
        if (query == null || query.isBlank()) return true;

        String q = query.trim().toLowerCase(Locale.ROOT);

        // 1. Display name
        try {
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (name.contains(q)) return true;
        } catch (Exception ignored) {}

        // 2. Registry ID (e.g. "diamond_sword", "iron_ingot")
        try {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
            if (id.contains(q)) return true;
        } catch (Exception ignored) {}

        // 3. Tooltip lines & Lore (Enchantments, Potion effects, Lore, custom attributes)
        try {
            Minecraft client = Minecraft.getInstance();
            List<Component> tooltip = Screen.getTooltipFromItem(client, stack);
            for (Component line : tooltip) {
                if (line.getString().toLowerCase(Locale.ROOT).contains(q)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        return false;
    }
}
