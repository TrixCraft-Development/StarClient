package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.Setting;

import java.util.Locale;

/**
 * Glassmorphic Furnace, Blast Furnace, and Smoker helper showing exact stack
 * smelt time remaining, fuel requirements, burn efficiency, and fuel-waste warnings.
 */
public final class FurnaceHelperRenderer {
    private static final int PANEL_W = 166;
    private static Module module;

    private FurnaceHelperRenderer() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static void render(GuiGraphicsExtractor graphics, AbstractFurnaceMenu menu,
                              int screenW, int leftPos, int topPos, int imageWidth, int imageHeight) {
        if (!isEnabled() || menu == null) return;

        ItemStack input = menu.getSlot(0).getItem();
        ItemStack fuel = menu.getSlot(1).getItem();
        boolean isFast = (menu instanceof BlastFurnaceMenu) || (menu instanceof SmokerMenu);
        double secPerItem = isFast ? 5.0 : 10.0;
        String stationTitle = menu instanceof BlastFurnaceMenu ? "🔥 Blast Furnace"
                : (menu instanceof SmokerMenu ? "🍖 Smoker Helper" : "🔥 Furnace Helper");

        boolean alwaysShow = getBool("Always Show Panel", false);
        if (!alwaysShow && input.isEmpty() && fuel.isEmpty() && !menu.isLit()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
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

        int inputCount = input.isEmpty() ? 0 : input.getCount();
        float burnProg = Math.clamp(menu.getBurnProgress(), 0.0F, 1.0F);
        float litProg = Math.clamp(menu.getLitProgress(), 0.0F, 1.0F);
        boolean isLit = menu.isLit();

        double remainingSec = inputCount > 0
                ? Math.max(0.0, (inputCount - burnProg) * secPerItem)
                : 0.0;

        double itemsPerFuelUnit = fuel.isEmpty() ? 0.0 : getItemsSmeltedPerUnit(fuel);
        double loadedFuelCapacity = itemsPerFuelUnit * (fuel.isEmpty() ? 0 : fuel.getCount());

        // Fuel waste check: burning a single unit of high-value fuel (Lava=100, Coal Block=80, Kelp=20, Blaze=12, Coal=8)
        // for far fewer items than 1 unit can smelt
        boolean showWasteWarn = getBool("Fuel Waste Warning", true);
        boolean wasteWarning = false;
        String wasteMsg = "";
        if (showWasteWarn && inputCount > 0 && itemsPerFuelUnit >= 8.0) {
            double remOnUnit = inputCount % itemsPerFuelUnit;
            double wastedItems = remOnUnit == 0.0 ? 0.0 : (itemsPerFuelUnit - remOnUnit);
            if (itemsPerFuelUnit >= 20.0 && inputCount < itemsPerFuelUnit * 0.75) {
                wasteWarning = true;
                wasteMsg = String.format(Locale.ROOT, "1 unit smelts %.0f (only %d in)", itemsPerFuelUnit, inputCount);
            } else if (wastedItems >= 5.0 && inputCount < itemsPerFuelUnit) {
                wasteWarning = true;
                wasteMsg = String.format(Locale.ROOT, "Wastes %.0f smelt capacity", wastedItems);
            }
        }

        int panelH = 26;
        if (inputCount == 0 && fuel.isEmpty() && !isLit) {
            panelH = 54;
        } else {
            panelH += 34; // Time & progress card
            if (inputCount > 0) panelH += 38; // Fuel needed breakdown
            if (!fuel.isEmpty()) panelH += 24; // Loaded fuel capacity
            if (wasteWarning) panelH += 24;
            panelH = Math.min(Math.max(panelH + 6, 72), imageHeight + 18);
        }

        GuiRenderUtils.drawDropShadow(graphics, panelX, panelY, PANEL_W, panelH, 6, 4, 110);
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, PANEL_W, panelH, 6, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, panelX, panelY, PANEL_W, panelH, 6, border);

        // Header
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, PANEL_W, 20, 6, surface);
        graphics.fill(panelX, panelY + 19, panelX + PANEL_W, panelY + 20, border);
        GuiRenderUtils.drawText(graphics, font, stationTitle, panelX + 7, panelY + 6, accent, true);
        String speedTag = isFast ? "2×" : "1×";
        int sw = font.width(speedTag) + 6;
        GuiRenderUtils.fillRoundedRect(graphics, panelX + PANEL_W - sw - 5, panelY + 4, sw, 12, 3,
                GuiRenderUtils.withAlpha(accent, 45));
        GuiRenderUtils.drawCenteredText(graphics, font, speedTag, panelX + PANEL_W - sw / 2 - 5, panelY + 6, accent);

        if (inputCount == 0 && fuel.isEmpty() && !isLit) {
            GuiRenderUtils.drawCenteredText(graphics, font, "Insert items to calculate",
                    panelX + PANEL_W / 2, panelY + 33, 0xFF64748B);
            return;
        }

        int curY = panelY + 24;
        int rx = panelX + 6;
        int rw = PANEL_W - 12;

        // 1. Smelt Time & Progress Card
        GuiRenderUtils.fillRoundedRect(graphics, rx, curY, rw, 30, 4, surface);
        GuiRenderUtils.drawRoundedOutline(graphics, rx, curY, rw, 30, 4, border);
        String timeStr = inputCount > 0 ? formatTime(remainingSec) : (isLit ? "Lit (Idle)" : "Idle");
        GuiRenderUtils.drawText(graphics, font, "Time Left:", rx + 5, curY + 5, 0xFF94A3B8);
        int tw = font.width(timeStr);
        GuiRenderUtils.drawText(graphics, font, timeStr, rx + rw - tw - 5, curY + 5,
                inputCount > 0 ? 0xFFF8FAFC : 0xFF64748B, true);

        // Progress bar (item smelt progress or active flame gauge)
        int barX = rx + 5;
        int barY = curY + 19;
        int barW = rw - 38;
        GuiRenderUtils.fillRoundedRect(graphics, barX, barY, barW, 4, 2, 0x2AFFFFFF);
        float shownProg = inputCount > 0 ? burnProg : litProg;
        int fillW = Math.round(barW * shownProg);
        if (fillW > 0) {
            GuiRenderUtils.fillRoundedRect(graphics, barX, barY, Math.max(2, fillW), 4, 2,
                    isLit ? 0xFFFB923C : accent);
        }
        String pctStr = Math.round(shownProg * 100.0F) + "%";
        GuiRenderUtils.drawText(graphics, font, pctStr, rx + rw - font.width(pctStr) - 5, curY + 17, 0xFFCBD5E1);
        curY += 34;

        // 2. Fuel Needed Breakdown
        if (inputCount > 0) {
            int coalNeeded = (int) Math.ceil(inputCount / 8.0);
            int blazeNeeded = (int) Math.ceil(inputCount / 12.0);
            int kelpNeeded = (int) Math.ceil(inputCount / 20.0);

            GuiRenderUtils.fillRoundedRect(graphics, rx, curY, rw, 34, 4, surface);
            GuiRenderUtils.drawRoundedOutline(graphics, rx, curY, rw, 34, 4, border);
            GuiRenderUtils.drawText(graphics, font, "Fuel Needed (" + inputCount + " items):", rx + 5, curY + 4, accent);
            String row1 = "Coal: " + coalNeeded + "  •  Blaze: " + blazeNeeded;
            String row2 = "Kelp Blk: " + kelpNeeded + "  •  Lava: " + (int) Math.ceil(inputCount / 100.0);
            GuiRenderUtils.drawText(graphics, font, row1, rx + 5, curY + 14, 0xFFE2E8F0);
            GuiRenderUtils.drawText(graphics, font, row2, rx + 5, curY + 23, 0xFF94A3B8);
            curY += 38;
        }

        // 3. Loaded Fuel Capacity
        if (!fuel.isEmpty()) {
            boolean enough = inputCount == 0 || loadedFuelCapacity >= inputCount;
            int col = itemsPerFuelUnit <= 0 ? 0xFFF87171 : (enough ? 0xFF34D399 : 0xFFFBBF24);
            GuiRenderUtils.fillRoundedRect(graphics, rx, curY, rw, 20, 4, surface);
            GuiRenderUtils.drawRoundedOutline(graphics, rx, curY, rw, 20, 4, GuiRenderUtils.withAlpha(col, 150));
            String capLabel = itemsPerFuelUnit <= 0
                    ? "Not a valid fuel!"
                    : String.format(Locale.ROOT, "Fuel smelts: %.1f items", loadedFuelCapacity);
            GuiRenderUtils.drawText(graphics, font, GuiRenderUtils.truncate(font, capLabel, rw - 10),
                    rx + 5, curY + 6, col);
            curY += 24;
        }

        // 4. Fuel Waste Warning
        if (wasteWarning) {
            GuiRenderUtils.fillRoundedRect(graphics, rx, curY, rw, 20, 4, 0x2AF59E0B);
            GuiRenderUtils.drawRoundedOutline(graphics, rx, curY, rw, 20, 4, 0xCCF59E0B);
            GuiRenderUtils.drawText(graphics, font, GuiRenderUtils.truncate(font, "⚠ " + wasteMsg, rw - 8),
                    rx + 5, curY + 6, 0xFFFBBF24);
        }
    }

    /**
     * Returns vanilla smelting capacity in items smelted per 1 fuel item (burnTicks / 200.0).
     */
    private static double getItemsSmeltedPerUnit(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0;
        Item item = stack.getItem();
        if (item == Items.LAVA_BUCKET) return 100.0;
        if (item == Items.COAL_BLOCK) return 80.0;
        if (item == Items.DRIED_KELP_BLOCK) return 20.0;
        if (item == Items.BLAZE_ROD) return 12.0;
        if (item == Items.COAL || item == Items.CHARCOAL) return 8.0;
        if (item == Items.STICK || item == Items.LADDER) return item == Items.LADDER ? 1.5 : 0.5;
        if (item == Items.BAMBOO) return 0.25;
        if (item == Items.SCAFFOLDING) return 0.25;
        if (item == Items.BOW || item == Items.CROSSBOW || item == Items.FISHING_ROD) return 1.5;
        if (item == Items.WOODEN_SWORD || item == Items.WOODEN_PICKAXE || item == Items.WOODEN_AXE
                || item == Items.WOODEN_SHOVEL || item == Items.WOODEN_HOE) return 1.0;

        // Check common wooden items cleanly via item description ID
        String id = item.toString().toLowerCase(Locale.ROOT);
        if (id.contains("crimson") || id.contains("warped")) return 0.0; // Nether wood does not burn
        if (id.contains("log") || id.contains("wood") || id.contains("planks")
                || id.contains("chest") || id.contains("barrel") || id.contains("crafting_table")
                || id.contains("bookshelf") || id.contains("lectern") || id.contains("fence")
                || id.contains("stairs") && isWoodName(id)
                || id.contains("boat")) {
            return 1.5;
        }
        if (id.contains("slab") && isWoodName(id)) return 0.75;
        if (id.contains("sapling") || id.contains("wool") || id.contains("carpet") || id.contains("sign")) {
            return id.contains("sign") ? 1.0 : 0.5;
        }
        return 0.0;
    }

    private static boolean isWoodName(String id) {
        return id.contains("oak") || id.contains("spruce") || id.contains("birch")
                || id.contains("jungle") || id.contains("acacia") || id.contains("mangrove")
                || id.contains("cherry") || id.contains("bamboo") || id.contains("pale_oak");
    }

    private static String formatTime(double totalSeconds) {
        int sec = (int) Math.ceil(totalSeconds);
        int mins = sec / 60;
        int rem = sec % 60;
        return String.format(Locale.ROOT, "%02d:%02d", mins, rem);
    }

    private static boolean getBool(String name, boolean fallback) {
        if (module == null) return fallback;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : fallback;
    }
}
