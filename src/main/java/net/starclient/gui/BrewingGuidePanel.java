package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Interactive glassmorphic Potion Brewing Guide rendered alongside Minecraft's
 * Brewing Stand UI, highlighting ingredients currently in the player's inventory.
 */
public final class BrewingGuidePanel {
    private static final int PANEL_W = 178;
    private static final int ROW_H = 26;

    private record PotionRecipe(
            String name,
            Item ingredient,
            String baseLabel,
            int effectColor,
            boolean redstone,
            boolean glowstone,
            String corruptInto
    ) {
    }

    private static Module module;
    private static List<PotionRecipe> RECIPES;
    private static boolean craftableOnly = false;
    private static double scroll = 0.0;
    private static int lastPanelX, lastPanelY, lastPanelH;

    private BrewingGuidePanel() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static void reset() {
        scroll = 0.0;
    }

    private static List<PotionRecipe> getRecipes() {
        if (RECIPES != null) return RECIPES;
        List<PotionRecipe> list = new ArrayList<>();
        list.add(new PotionRecipe("Swiftness", Items.SUGAR, "Awkward", 0xFF38BDF8, true, true, "Slowness"));
        list.add(new PotionRecipe("Strength", Items.BLAZE_POWDER, "Awkward", 0xFFF87171, true, true, null));
        list.add(new PotionRecipe("Healing", Items.GLISTERING_MELON_SLICE, "Awkward", 0xFFFB7185, false, true, "Harming"));
        list.add(new PotionRecipe("Regeneration", Items.GHAST_TEAR, "Awkward", 0xFFF472B6, true, true, null));
        list.add(new PotionRecipe("Fire Resistance", Items.MAGMA_CREAM, "Awkward", 0xFFFB923C, true, false, null));
        list.add(new PotionRecipe("Night Vision", Items.GOLDEN_CARROT, "Awkward", 0xFF818CF8, true, false, "Invisibility"));
        list.add(new PotionRecipe("Invisibility", Items.FERMENTED_SPIDER_EYE, "Night Vision", 0xFFCBD5E1, true, false, null));
        list.add(new PotionRecipe("Water Breathing", Items.PUFFERFISH, "Awkward", 0xFF60A5FA, true, false, null));
        list.add(new PotionRecipe("Leaping", Items.RABBIT_FOOT, "Awkward", 0xFF4ADE80, true, true, "Slowness"));
        list.add(new PotionRecipe("Slow Falling", Items.PHANTOM_MEMBRANE, "Awkward", 0xFFE2E8F0, true, false, null));
        list.add(new PotionRecipe("Turtle Master", Items.TURTLE_SCUTE, "Awkward", 0xFFA78BFA, true, true, null));
        list.add(new PotionRecipe("Poison", Items.SPIDER_EYE, "Awkward", 0xFF84CC16, true, true, "Harming"));
        list.add(new PotionRecipe("Harming", Items.FERMENTED_SPIDER_EYE, "Heal / Poison", 0xFFEF4444, false, true, null));
        list.add(new PotionRecipe("Slowness", Items.FERMENTED_SPIDER_EYE, "Speed / Leap", 0xFF94A3B8, true, true, null));
        list.add(new PotionRecipe("Weakness", Items.FERMENTED_SPIDER_EYE, "Water Bottle", 0xFF9CA3AF, true, false, null));
        list.add(new PotionRecipe("Wind Charging", Items.BREEZE_ROD, "Awkward", 0xFFBAE6FD, false, false, null));
        list.add(new PotionRecipe("Weaving", Items.COBWEB, "Awkward", 0xFFD6D3D1, false, false, null));
        list.add(new PotionRecipe("Oozing", Items.SLIME_BLOCK, "Awkward", 0xFFA3E635, false, false, null));
        list.add(new PotionRecipe("Infestation", Items.STONE, "Awkward", 0xFF9CA3AF, false, false, null));
        RECIPES = list;
        return RECIPES;
    }

    public static void render(GuiGraphicsExtractor graphics, BrewingStandMenu menu,
                              int screenW, int leftPos, int topPos, int imageWidth, int imageHeight,
                              int mouseX, int mouseY) {
        if (!isEnabled() || menu == null) return;

        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        StarClient sc = StarClient.getInstance();
        ClientSettings cs = sc != null ? sc.getClientSettings() : null;

        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int bg = cs != null ? (0xEE000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xEE0F172A;
        int bgDark = cs != null ? cs.getMenuBackgroundDarkColor() : 0xFF0A101E;
        int surface = cs != null ? cs.getMenuSurfaceColor() : 0xFF162032;
        int border = cs != null ? cs.getMenuBorderColor() : 0xFF334155;

        int panelX = leftPos + imageWidth + 6;
        if (panelX + PANEL_W > screenW - 4) {
            panelX = Math.max(4, leftPos - PANEL_W - 6);
        }
        int panelY = topPos;
        int panelH = Math.max(166, imageHeight);
        lastPanelX = panelX;
        lastPanelY = panelY;
        lastPanelH = panelH;

        Set<Item> ownedItems = collectOwnedItems(client, menu);
        boolean highlightOwned = getBool("Highlight Owned", true);
        boolean showModifiers = getBool("Show Modifier Legend", true);

        List<PotionRecipe> visible = new ArrayList<>();
        for (PotionRecipe r : getRecipes()) {
            if (!craftableOnly || ownedItems.contains(r.ingredient)) {
                visible.add(r);
            }
        }

        GuiRenderUtils.drawDropShadow(graphics, panelX, panelY, PANEL_W, panelH, 6, 4, 110);
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, PANEL_W, panelH, 6, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, panelX, panelY, PANEL_W, panelH, 6, border);

        // Header
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, PANEL_W, 22, 6, surface);
        graphics.fill(panelX, panelY + 21, panelX + PANEL_W, panelY + 22, border);
        GuiRenderUtils.drawText(graphics, font, "⚗ Brewing Guide", panelX + 7, panelY + 7, accent, true);

        // Filter toggle pill ("All" / "Owned")
        String fltLabel = craftableOnly ? "● Owned" : "All (" + visible.size() + ")";
        int fltW = font.width(fltLabel) + 10;
        int fltX = panelX + PANEL_W - fltW - 5;
        int fltY = panelY + 4;
        boolean fltHov = hovered(mouseX, mouseY, fltX, fltY, fltW, 14);
        GuiRenderUtils.fillRoundedRect(graphics, fltX, fltY, fltW, 14, 4,
                craftableOnly ? GuiRenderUtils.withAlpha(0xFF34D399, 55) : (fltHov ? surface : bgDark));
        GuiRenderUtils.drawRoundedOutline(graphics, fltX, fltY, fltW, 14, 4,
                craftableOnly ? 0xFF34D399 : (fltHov ? accent : border));
        GuiRenderUtils.drawCenteredText(graphics, font, fltLabel, fltX + fltW / 2, fltY + 3,
                craftableOnly ? 0xFF34D399 : 0xFFE2E8F0);

        // Base brew hint strip
        int tipY = panelY + 25;
        boolean hasWart = ownedItems.contains(Items.NETHER_WART);
        String baseTip = "Base: Nether Wart → Awkward";
        GuiRenderUtils.drawText(graphics, font, baseTip, panelX + 7, tipY, hasWart ? 0xFF34D399 : 0xFF94A3B8);

        int footerH = showModifiers ? 22 : 4;
        int listTop = tipY + 12;
        int listBottom = panelY + panelH - footerH - 4;
        int listH = Math.max(40, listBottom - listTop);

        int totalH = Math.max(0, visible.size() * (ROW_H + 3) - 3);
        int maxScroll = Math.max(0, totalH - listH);
        scroll = Math.clamp(scroll, 0.0, maxScroll);

        graphics.enableScissor(panelX + 4, listTop, panelX + PANEL_W - 4, listBottom);
        if (visible.isEmpty()) {
            GuiRenderUtils.drawCenteredText(graphics, font, "No matching ingredients",
                    panelX + PANEL_W / 2, listTop + listH / 2 - 4, 0xFF64748B);
        } else {
            int ry = (int) Math.round(listTop - scroll);
            int rx = panelX + 6;
            int rw = PANEL_W - 14;
            for (PotionRecipe r : visible) {
                if (ry + ROW_H >= listTop && ry <= listBottom) {
                    boolean owned = highlightOwned && ownedItems.contains(r.ingredient);
                    GuiRenderUtils.fillRoundedRect(graphics, rx, ry, rw, ROW_H, 4, surface);
                    GuiRenderUtils.drawRoundedOutline(graphics, rx, ry, rw, ROW_H, 4,
                            owned ? 0xAA34D399 : border);

                    graphics.item(new ItemStack(r.ingredient), rx + 3, ry + 5);

                    int tx = rx + 22;
                    // Right badges: Redstone (R), Glowstone (II), Owned check
                    int rightX = rx + rw - 4;
                    if (owned) {
                        GuiRenderUtils.drawText(graphics, font, "✓", rightX - 7, ry + 4, 0xFF34D399, true);
                        rightX -= 10;
                    }

                    GuiRenderUtils.drawText(graphics, font,
                            GuiRenderUtils.truncate(font, r.name, Math.max(40, rightX - tx)),
                            tx, ry + 4, r.effectColor, true);

                    StringBuilder modStr = new StringBuilder(r.baseLabel);
                    if (r.redstone) modStr.append(" • +Time");
                    if (r.glowstone) modStr.append(" • +II");
                    if (r.corruptInto != null && !r.redstone && !r.glowstone) {
                        modStr.append(" → ").append(r.corruptInto);
                    }
                    GuiRenderUtils.drawText(graphics, font,
                            GuiRenderUtils.truncate(font, modStr.toString(), rw - 26),
                            tx, ry + 15, 0xFF94A3B8);
                }
                ry += ROW_H + 3;
            }
        }
        graphics.disableScissor();

        // Scrollbar
        if (maxScroll > 0) {
            int sbX = panelX + PANEL_W - 5;
            int thumbH = Math.max(16, (int) ((double) listH * listH / Math.max(1, totalH)));
            int thumbY = listTop + (int) ((listH - thumbH) * (scroll / maxScroll));
            GuiRenderUtils.fillRoundedRect(graphics, sbX, listTop, 2, listH, 1, 0x26FFFFFF);
            GuiRenderUtils.fillRoundedRect(graphics, sbX, thumbY, 2, thumbH, 1, accent);
        }

        // Bottom Modifier Legend
        if (showModifiers) {
            int footY = panelY + panelH - footerH;
            graphics.fill(panelX + 4, footY, panelX + PANEL_W - 4, footY + 1, border);
            GuiRenderUtils.drawText(graphics, font, "Redst:Time  Glow:II  Gunp:Splash",
                    panelX + 6, footY + 4, 0xFF94A3B8);
            GuiRenderUtils.drawText(graphics, font, "Breath:Linger  F.Eye:Invert",
                    panelX + 6, footY + 13, 0xFF64748B);
        }
    }

    public static boolean handleMouseClick(MouseButtonEvent event) {
        if (!isEnabled() || lastPanelH <= 0) return false;
        double mx = event.x();
        double my = event.y();
        if (!hovered(mx, my, lastPanelX, lastPanelY, PANEL_W, lastPanelH)) {
            return false;
        }
        // Toggle "Owned" filter pill in header
        if (my >= lastPanelY + 2 && my <= lastPanelY + 20 && mx >= lastPanelX + PANEL_W - 65) {
            craftableOnly = !craftableOnly;
            scroll = 0.0;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return true;
    }

    public static boolean handleMouseScroll(double mouseX, double mouseY, double verticalAmount) {
        if (!isEnabled() || lastPanelH <= 0) return false;
        if (!hovered(mouseX, mouseY, lastPanelX, lastPanelY, PANEL_W, lastPanelH)) {
            return false;
        }
        scroll -= verticalAmount * 24.0;
        return true;
    }

    private static Set<Item> collectOwnedItems(Minecraft client, BrewingStandMenu menu) {
        Set<Item> set = new HashSet<>();
        if (client != null && client.player != null) {
            Inventory inv = client.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                if (!st.isEmpty()) set.add(st.getItem());
            }
        }
        if (menu != null && menu.slots.size() > 3) {
            ItemStack ing = menu.getSlot(3).getItem();
            if (!ing.isEmpty()) set.add(ing.getItem());
        }
        return set;
    }

    private static boolean getBool(String name, boolean fallback) {
        if (module == null) return fallback;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : fallback;
    }

    private static boolean hovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
