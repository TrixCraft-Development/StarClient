package net.starclient.module;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Shared state for "Container Preview" (Shulker Box, Filled Map, & Ender Chest hover preview)
 * and "Scrollable Tooltips" (vertical + horizontal tooltip scrolling).
 */
public final class TooltipFeaturesState {

    private static Module containerPreviewModule;
    private static Module scrollableTooltipsModule;

    // Scrollable Tooltips state
    private static float scrollX;
    private static float scrollY;
    private static float targetScrollX;
    private static float targetScrollY;
    private static int lastHoveredSlotIndex = -999;
    private static ItemStack lastHoveredItem = ItemStack.EMPTY;
    private static boolean lastTooltipOverflowed;

    // Cached Ender Chest contents (27 slots) captured whenever player opens an Ender Chest
    private static final NonNullList<ItemStack> cachedEnderChest = NonNullList.withSize(27, ItemStack.EMPTY);
    private static boolean hasCachedEnderChest;

    private TooltipFeaturesState() {
    }

    public static void setContainerPreviewModule(Module mod) {
        containerPreviewModule = mod;
    }

    public static void setScrollableTooltipsModule(Module mod) {
        scrollableTooltipsModule = mod;
    }

    // -------------------------------------------------------------------------
    // Container Preview
    // -------------------------------------------------------------------------

    public static boolean isContainerPreviewEnabled() {
        return containerPreviewModule != null
                && containerPreviewModule.isEnabled()
                && containerPreviewModule.canUse();
    }

    public static boolean isPreviewShulkers() {
        return isContainerPreviewEnabled() && getBool(containerPreviewModule, "Shulker Boxes", true);
    }

    public static boolean isPreviewMaps() {
        return isContainerPreviewEnabled() && getBool(containerPreviewModule, "Filled Maps", true);
    }

    public static boolean isPreviewEnderChest() {
        return isContainerPreviewEnabled() && getBool(containerPreviewModule, "Ender Chest", true);
    }

    /**
     * Returns true when the preview card should actively display (either Display Mode is "Always",
     * or Display Mode is "Hold Key" and the configured modifier key is currently held down).
     */
    public static boolean isPreviewTriggerActive(Minecraft client) {
        if (!isContainerPreviewEnabled() || client == null) return false;
        String mode = getMode(containerPreviewModule, "Display Mode", "Always");
        if (!"Hold Key".equalsIgnoreCase(mode)) {
            return true;
        }
        Window window = client.getWindow();
        String keyMode = getMode(containerPreviewModule, "Preview Key", "Shift");
        return switch (keyMode) {
            case "Ctrl" -> InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
            case "Alt" -> InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
            default -> InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                    || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        };
    }

    public static String getHoldKeyName() {
        return getMode(containerPreviewModule, "Preview Key", "Shift");
    }

    public static boolean isHoldKeyMode() {
        return "Hold Key".equalsIgnoreCase(getMode(containerPreviewModule, "Display Mode", "Always"));
    }

    public static void captureEnderChestIfOpen(String screenTitle, AbstractContainerMenu menu) {
        if (!(menu instanceof ChestMenu chestMenu) || chestMenu.getRowCount() != 3) return;
        if (screenTitle == null) return;
        String lower = screenTitle.toLowerCase(Locale.ROOT);
        if (!lower.contains("ender chest") && !lower.contains("endertruhe")) {
            return;
        }
        for (int i = 0; i < 27 && i < chestMenu.slots.size(); i++) {
            cachedEnderChest.set(i, chestMenu.slots.get(i).getItem().copy());
        }
        hasCachedEnderChest = true;
    }

    public static boolean hasCachedEnderChest() {
        return hasCachedEnderChest;
    }

    public static NonNullList<ItemStack> getCachedEnderChest() {
        return cachedEnderChest;
    }

    // -------------------------------------------------------------------------
    // Scrollable Tooltips
    // -------------------------------------------------------------------------

    public static boolean isScrollableTooltipsEnabled() {
        return scrollableTooltipsModule != null
                && scrollableTooltipsModule.isEnabled()
                && scrollableTooltipsModule.canUse();
    }

    public static boolean isVerticalScroll() {
        return isScrollableTooltipsEnabled() && getBool(scrollableTooltipsModule, "Vertical Scroll", true);
    }

    public static boolean isHorizontalScroll() {
        return isScrollableTooltipsEnabled() && getBool(scrollableTooltipsModule, "Horizontal Scroll", true);
    }

    public static boolean isOnlyWhenOverflowing() {
        return isScrollableTooltipsEnabled() && getBool(scrollableTooltipsModule, "Only When Overflowing", false);
    }

    public static float getScrollSpeed() {
        if (scrollableTooltipsModule != null
                && scrollableTooltipsModule.getSetting("Scroll Speed") instanceof NumberSetting ns) {
            return ns.getValue().floatValue();
        }
        return 14.0f;
    }

    public static void updateHoveredSlot(Slot hoveredSlot) {
        if (!isScrollableTooltipsEnabled()) {
            resetScroll();
            return;
        }
        if (hoveredSlot == null || !hoveredSlot.hasItem()) {
            if (lastHoveredSlotIndex != -999) {
                lastHoveredSlotIndex = -999;
                lastHoveredItem = ItemStack.EMPTY;
                if (getBool(scrollableTooltipsModule, "Reset on New Item", true)) {
                    resetScroll();
                }
            }
            return;
        }
        if (hoveredSlot.index != lastHoveredSlotIndex
                || !ItemStack.isSameItemSameComponents(hoveredSlot.getItem(), lastHoveredItem)) {
            lastHoveredSlotIndex = hoveredSlot.index;
            lastHoveredItem = hoveredSlot.getItem().copy();
            lastTooltipOverflowed = false;
            if (getBool(scrollableTooltipsModule, "Reset on New Item", true)) {
                resetScroll();
            }
        }
    }

    public static boolean onMouseScroll(double horizontalAmount, double verticalAmount, boolean shiftHeld) {
        if (!isScrollableTooltipsEnabled()) return false;
        if (isOnlyWhenOverflowing() && !lastTooltipOverflowed) return false;

        float speed = getScrollSpeed();
        boolean handled = false;

        if (shiftHeld && Math.abs(verticalAmount) > 0.001) {
            if (isHorizontalScroll()) {
                targetScrollX += (float) verticalAmount * speed;
                handled = true;
            }
        } else {
            if (Math.abs(verticalAmount) > 0.001 && isVerticalScroll()) {
                targetScrollY += (float) verticalAmount * speed;
                handled = true;
            }
            if (Math.abs(horizontalAmount) > 0.001 && isHorizontalScroll()) {
                targetScrollX += (float) horizontalAmount * speed;
                handled = true;
            }
        }

        targetScrollX = Math.clamp(targetScrollX, -1200.0f, 1200.0f);
        targetScrollY = Math.clamp(targetScrollY, -2400.0f, 2400.0f);
        return handled;
    }

    public static boolean onArrowKey(int keyCode) {
        if (!isScrollableTooltipsEnabled()) return false;
        if (isOnlyWhenOverflowing() && !lastTooltipOverflowed) return false;

        float step = getScrollSpeed();
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                if (!isVerticalScroll()) return false;
                targetScrollY += step;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (!isVerticalScroll()) return false;
                targetScrollY -= step;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (!isHorizontalScroll()) return false;
                targetScrollX += step;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (!isHorizontalScroll()) return false;
                targetScrollX -= step;
            }
            default -> {
                return false;
            }
        }
        targetScrollX = Math.clamp(targetScrollX, -1200.0f, 1200.0f);
        targetScrollY = Math.clamp(targetScrollY, -2400.0f, 2400.0f);
        return true;
    }

    public static void markTooltipDimensions(int tooltipW, int tooltipH, int screenW, int screenH) {
        lastTooltipOverflowed = (tooltipH + 16 >= screenH) || (tooltipW + 16 >= screenW);
        // Smoothly interpolate toward target scroll each frame
        scrollX += (targetScrollX - scrollX) * 0.45f;
        scrollY += (targetScrollY - scrollY) * 0.45f;
        if (Math.abs(targetScrollX - scrollX) < 0.25f) scrollX = targetScrollX;
        if (Math.abs(targetScrollY - scrollY) < 0.25f) scrollY = targetScrollY;
    }

    public static float getScrollX() {
        return isScrollableTooltipsEnabled() ? scrollX : 0.0f;
    }

    public static float getScrollY() {
        return isScrollableTooltipsEnabled() ? scrollY : 0.0f;
    }

    public static void resetScroll() {
        scrollX = 0.0f;
        scrollY = 0.0f;
        targetScrollX = 0.0f;
        targetScrollY = 0.0f;
        lastTooltipOverflowed = false;
    }

    private static boolean getBool(Module mod, String name, boolean def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s != null && s.getValue() instanceof Boolean b ? b : def;
    }

    private static String getMode(Module mod, String name, String def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s != null && s.getValue() instanceof String str ? str : def;
    }
}
