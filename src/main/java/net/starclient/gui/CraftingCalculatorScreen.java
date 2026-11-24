package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.Setting;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Glassmorphic Bulk Crafting Material Calculator supporting live unlocked recipes
 * from {@link net.minecraft.client.ClientRecipeBook} merged with curated essential
 * Minecraft recipes, recursive raw-material expansion, stack math, and live inventory comparison.
 */
public class CraftingCalculatorScreen extends Screen {
    private static final int HEADER_H = 42;
    private static final int ITEM_ROW_H = 22;
    private static final int ING_ROW_H = 28;
    private static final int[] QTY_PRESETS = {1, 16, 64, 128, 256};

    private static Module module;

    private record CalcRecipe(
            Item output,
            int outputCount,
            Map<Item, Integer> ingredients,
            String displayName,
            boolean allowRecursiveExpand
    ) {
    }

    private record MaterialRow(
            Item item,
            String name,
            long required,
            int inInventory
    ) {
        long missing() {
            return Math.max(0L, required - inInventory);
        }

        boolean satisfied() {
            return inInventory >= required;
        }
    }

    private final Screen parent;
    private final Item initialItem;

    private final Map<Item, CalcRecipe> recipeByOutput = new LinkedHashMap<>();
    private final List<CalcRecipe> allRecipes = new ArrayList<>();
    private final List<CalcRecipe> filteredRecipes = new ArrayList<>();

    private EditBox searchBox;
    private EditBox qtyBox;
    private String searchQuery = "";
    private CalcRecipe selectedRecipe;
    private int targetQty = 64;
    private boolean expandRaw = false;

    private double leftScroll = 0.0;
    private double rightScroll = 0.0;
    private String statusText = "";
    private long statusExpireMs = 0L;

    public CraftingCalculatorScreen(Screen parent) {
        this(parent, null);
    }

    public CraftingCalculatorScreen(Screen parent, Item initialItem) {
        super(Component.literal("Crafting Material Calculator"));
        this.parent = parent;
        this.initialItem = initialItem;
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static int getHotkey() {
        if (module == null) return GLFW.GLFW_KEY_O;
        int k = module.getKeyBind();
        return k == GLFW.GLFW_KEY_UNKNOWN ? GLFW.GLFW_KEY_O : k;
    }

    public static void onTick(Minecraft client, Module mod) {
        if (mod == null) return;
        Setting<?> openSetting = mod.getSetting("Open Calculator");
        if (openSetting instanceof BooleanSetting bs && bs.getValue()) {
            bs.setValue(false);
            if (client != null) {
                client.gui.setScreen(new CraftingCalculatorScreen(client.gui.screen()));
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        if (this.allRecipes.isEmpty()) {
            this.expandRaw = getDefaultExpandRaw();
            buildRecipeCatalog();
        }

        String prevSearch = this.searchQuery;
        this.searchBox = new EditBox(this.font, 0, 0, 140, 14, Component.literal(""));
        this.searchBox.setMaxLength(48);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFF8FAFC);
        this.searchBox.setHint(Component.literal("Search items (piston, hopper...)"));
        this.searchBox.setValue(prevSearch);
        this.searchBox.setResponder(v -> {
            this.searchQuery = v;
            applyFilter();
        });
        addWidget(this.searchBox);

        this.qtyBox = new EditBox(this.font, 0, 0, 44, 14, Component.literal(""));
        this.qtyBox.setMaxLength(4);
        this.qtyBox.setBordered(false);
        this.qtyBox.setTextColor(0xFFF8FAFC);
        this.qtyBox.setValue(String.valueOf(this.targetQty));
        this.qtyBox.setResponder(v -> {
            String digits = v.replaceAll("[^0-9]", "");
            if (!digits.equals(v)) {
                this.qtyBox.setValue(digits);
                return;
            }
            if (!digits.isEmpty()) {
                try {
                    this.targetQty = Math.clamp(Integer.parseInt(digits), 1, 9999);
                } catch (NumberFormatException ignored) {
                }
            }
        });
        addWidget(this.qtyBox);

        applyFilter();
        if (this.selectedRecipe == null) {
            if (this.initialItem != null && this.recipeByOutput.containsKey(this.initialItem)) {
                this.selectedRecipe = this.recipeByOutput.get(this.initialItem);
            } else if (this.recipeByOutput.containsKey(Items.PISTON)) {
                this.selectedRecipe = this.recipeByOutput.get(Items.PISTON);
            } else if (!this.filteredRecipes.isEmpty()) {
                this.selectedRecipe = this.filteredRecipes.get(0);
            }
        }

        this.searchBox.setFocused(true);
        this.setFocused(this.searchBox);
    }

    private void buildRecipeCatalog() {
        this.recipeByOutput.clear();
        this.allRecipes.clear();

        // 1. Add curated essential Minecraft crafting recipes (always available in SP & MP!)
        registerEssentialRecipes();

        // 2. Merge live unlocked crafting recipes from the player's ClientRecipeBook
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null && client.level != null) {
            try {
                ContextMap ctx = SlotDisplayContext.fromLevel(client.level);
                for (RecipeCollection col : client.player.getRecipeBook().getCollections()) {
                    for (RecipeDisplayEntry entry : col.getRecipes()) {
                        RecipeDisplay disp = entry.display();
                        List<SlotDisplay> slotIngredients = null;
                        SlotDisplay resultDisp = null;

                        if (disp instanceof ShapedCraftingRecipeDisplay shaped) {
                            slotIngredients = shaped.ingredients();
                            resultDisp = shaped.result();
                        } else if (disp instanceof ShapelessCraftingRecipeDisplay shapeless) {
                            slotIngredients = shapeless.ingredients();
                            resultDisp = shapeless.result();
                        }
                        if (slotIngredients == null || resultDisp == null) continue;

                        List<ItemStack> results = resultDisp.resolveForStacks(ctx);
                        if (results.isEmpty() || results.get(0).isEmpty()) continue;
                        ItemStack outStack = results.get(0);
                        Item outItem = outStack.getItem();
                        int outCount = Math.max(1, outStack.getCount());

                        if (this.recipeByOutput.containsKey(outItem)) continue;

                        Map<Item, Integer> ingMap = new LinkedHashMap<>();
                        boolean valid = true;
                        for (SlotDisplay sd : slotIngredients) {
                            List<ItemStack> opts = sd.resolveForStacks(ctx);
                            if (opts.isEmpty()) continue;
                            ItemStack pick = opts.get(0);
                            if (pick.isEmpty()) continue;
                            if (pick.getItem() == outItem) {
                                valid = false;
                                break;
                            }
                            ingMap.merge(pick.getItem(), 1, Integer::sum);
                        }
                        if (!valid || ingMap.isEmpty()) continue;

                        // Prevent unpacking loops like Iron Block -> 9 Iron Ingots from recursively expanding
                        boolean expandable = !(ingMap.size() == 1 && outCount == 9 && ingMap.values().iterator().next() == 1);
                        addRecipe(outItem, outCount, ingMap, expandable);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        this.allRecipes.addAll(this.recipeByOutput.values());
        this.allRecipes.sort(Comparator.comparing(r -> r.displayName.toLowerCase(Locale.ROOT)));
    }

    private void registerEssentialRecipes() {
        // Basic building & intermediates (expandable!)
        addCurated(Items.OAK_PLANKS, 4, true, Items.OAK_LOG, 1);
        addCurated(Items.STICK, 4, true, Items.OAK_PLANKS, 2);
        addCurated(Items.CRAFTING_TABLE, 1, true, Items.OAK_PLANKS, 4);
        addCurated(Items.CHEST, 1, true, Items.OAK_PLANKS, 8);
        addCurated(Items.FURNACE, 1, true, Items.COBBLESTONE, 8);
        addCurated(Items.OAK_SLAB, 6, true, Items.OAK_PLANKS, 3);
        addCurated(Items.OAK_STAIRS, 4, true, Items.OAK_PLANKS, 6);
        addCurated(Items.OAK_FENCE, 3, true, Items.OAK_PLANKS, 4, Items.STICK, 2);
        addCurated(Items.OAK_DOOR, 3, true, Items.OAK_PLANKS, 6);
        addCurated(Items.OAK_TRAPDOOR, 2, true, Items.OAK_PLANKS, 6);
        addCurated(Items.OAK_SIGN, 3, true, Items.OAK_PLANKS, 6, Items.STICK, 1);
        addCurated(Items.LADDER, 3, true, Items.STICK, 7);
        addCurated(Items.TORCH, 4, true, Items.COAL, 1, Items.STICK, 1);
        addCurated(Items.SOUL_TORCH, 4, true, Items.COAL, 1, Items.STICK, 1, Items.SOUL_SAND, 1);
        addCurated(Items.REDSTONE_TORCH, 1, true, Items.REDSTONE, 1, Items.STICK, 1);
        addCurated(Items.PAPER, 3, true, Items.SUGAR_CANE, 3);
        addCurated(Items.BOOK, 1, true, Items.PAPER, 3, Items.LEATHER, 1);
        addCurated(Items.GLASS_PANE, 16, true, Items.GLASS, 6);
        addCurated(Items.IRON_BARS, 16, true, Items.IRON_INGOT, 6);
        addCurated(Items.IRON_NUGGET, 9, false, Items.IRON_INGOT, 1);
        addCurated(Items.GOLD_NUGGET, 9, false, Items.GOLD_INGOT, 1);
        addCurated(Items.BOW, 1, true, Items.STICK, 3, Items.STRING, 3);
        addCurated(Items.ARROW, 4, true, Items.FLINT, 1, Items.STICK, 1, Items.FEATHER, 1);

        // Redstone & Automation favorites
        addCurated(Items.PISTON, 1, true, Items.OAK_PLANKS, 3, Items.COBBLESTONE, 4, Items.IRON_INGOT, 1, Items.REDSTONE, 1);
        addCurated(Items.STICKY_PISTON, 1, true, Items.PISTON, 1, Items.SLIME_BALL, 1);
        addCurated(Items.HOPPER, 1, true, Items.IRON_INGOT, 5, Items.CHEST, 1);
        addCurated(Items.OBSERVER, 1, true, Items.COBBLESTONE, 6, Items.REDSTONE, 2, Items.QUARTZ, 1);
        addCurated(Items.DISPENSER, 1, true, Items.COBBLESTONE, 7, Items.BOW, 1, Items.REDSTONE, 1);
        addCurated(Items.DROPPER, 1, true, Items.COBBLESTONE, 7, Items.REDSTONE, 1);
        addCurated(Items.REPEATER, 1, true, Items.REDSTONE_TORCH, 2, Items.REDSTONE, 1, Items.STONE, 3);
        addCurated(Items.COMPARATOR, 1, true, Items.REDSTONE_TORCH, 3, Items.QUARTZ, 1, Items.STONE, 3);
        addCurated(Items.REDSTONE_LAMP, 1, true, Items.GLOWSTONE, 1, Items.REDSTONE, 4);
        addCurated(Items.CRAFTER, 1, true, Items.IRON_INGOT, 5, Items.CRAFTING_TABLE, 1, Items.REDSTONE, 2, Items.DROPPER, 1);
        addCurated(Items.DAYLIGHT_DETECTOR, 1, true, Items.GLASS, 3, Items.QUARTZ, 3, Items.OAK_SLAB, 3);
        addCurated(Items.TARGET, 1, true, Items.HAY_BLOCK, 1, Items.REDSTONE, 4);
        addCurated(Items.NOTE_BLOCK, 1, true, Items.OAK_PLANKS, 8, Items.REDSTONE, 1);
        addCurated(Items.JUKEBOX, 1, true, Items.OAK_PLANKS, 8, Items.DIAMOND, 1);
        addCurated(Items.TNT, 1, true, Items.GUNPOWDER, 5, Items.SAND, 4);
        addCurated(Items.RAIL, 16, true, Items.IRON_INGOT, 6, Items.STICK, 1);
        addCurated(Items.POWERED_RAIL, 6, true, Items.GOLD_INGOT, 6, Items.STICK, 1, Items.REDSTONE, 1);
        addCurated(Items.DETECTOR_RAIL, 6, true, Items.IRON_INGOT, 6, Items.STONE_PRESSURE_PLATE, 1, Items.REDSTONE, 1);
        addCurated(Items.ACTIVATOR_RAIL, 6, true, Items.IRON_INGOT, 6, Items.STICK, 2, Items.REDSTONE_TORCH, 1);
        addCurated(Items.MINECART, 1, true, Items.IRON_INGOT, 5);
        addCurated(Items.HOPPER_MINECART, 1, true, Items.HOPPER, 1, Items.MINECART, 1);
        addCurated(Items.CHEST_MINECART, 1, true, Items.CHEST, 1, Items.MINECART, 1);

        // Workstations, Storage & Utility
        addCurated(Items.BARREL, 1, true, Items.OAK_PLANKS, 6, Items.OAK_SLAB, 2);
        addCurated(Items.BOOKSHELF, 1, true, Items.OAK_PLANKS, 6, Items.BOOK, 3);
        addCurated(Items.LECTERN, 1, true, Items.OAK_SLAB, 4, Items.BOOKSHELF, 1);
        addCurated(Items.ENCHANTING_TABLE, 1, true, Items.BOOK, 1, Items.DIAMOND, 2, Items.OBSIDIAN, 4);
        addCurated(Items.ANVIL, 1, true, Items.IRON_BLOCK, 3, Items.IRON_INGOT, 4);
        addCurated(Items.BEACON, 1, true, Items.GLASS, 5, Items.NETHER_STAR, 1, Items.OBSIDIAN, 3);
        addCurated(Items.BREWING_STAND, 1, true, Items.BLAZE_ROD, 1, Items.COBBLESTONE, 3);
        addCurated(Items.BLAST_FURNACE, 1, true, Items.IRON_INGOT, 5, Items.FURNACE, 1, Items.SMOOTH_STONE, 3);
        addCurated(Items.SMOKER, 1, true, Items.OAK_LOG, 4, Items.FURNACE, 1);
        addCurated(Items.GRINDSTONE, 1, true, Items.STICK, 2, Items.STONE_SLAB, 1, Items.OAK_PLANKS, 2);
        addCurated(Items.STONECUTTER, 1, true, Items.IRON_INGOT, 1, Items.STONE, 3);
        addCurated(Items.SMITHING_TABLE, 1, true, Items.IRON_INGOT, 2, Items.OAK_PLANKS, 4);
        addCurated(Items.CARTOGRAPHY_TABLE, 1, true, Items.PAPER, 2, Items.OAK_PLANKS, 4);
        addCurated(Items.SHULKER_BOX, 1, true, Items.SHULKER_SHELL, 2, Items.CHEST, 1);
        addCurated(Items.ENDER_CHEST, 1, true, Items.OBSIDIAN, 8, Items.ENDER_EYE, 1);
        addCurated(Items.LANTERN, 1, true, Items.IRON_NUGGET, 8, Items.TORCH, 1);
        addCurated(Items.SOUL_LANTERN, 1, true, Items.IRON_NUGGET, 8, Items.SOUL_TORCH, 1);
        addCurated(Items.CAMPFIRE, 1, true, Items.STICK, 3, Items.COAL, 1, Items.OAK_LOG, 3);
        addCurated(Items.SCAFFOLDING, 6, true, Items.BAMBOO, 6, Items.STRING, 1);
        addCurated(Items.END_CRYSTAL, 1, true, Items.GLASS, 7, Items.ENDER_EYE, 1, Items.GHAST_TEAR, 1);
        addCurated(Items.FIREWORK_ROCKET, 3, true, Items.PAPER, 1, Items.GUNPOWDER, 1);
        addCurated(Items.SHIELD, 1, true, Items.OAK_PLANKS, 6, Items.IRON_INGOT, 1);
        addCurated(Items.BUCKET, 1, true, Items.IRON_INGOT, 3);
        addCurated(Items.COMPASS, 1, true, Items.IRON_INGOT, 4, Items.REDSTONE, 1);
        addCurated(Items.CLOCK, 1, true, Items.GOLD_INGOT, 4, Items.REDSTONE, 1);
        addCurated(Items.LIGHTNING_ROD.asList().get(0), 1, true, Items.COPPER_INGOT, 3);
        addCurated(Items.SPYGLASS, 1, true, Items.AMETHYST_SHARD, 1, Items.COPPER_INGOT, 2);
        addCurated(Items.LEAD, 2, true, Items.STRING, 4, Items.SLIME_BALL, 1);
        addCurated(Items.BED.white(), 1, true, Items.WOOL.white(), 3, Items.OAK_PLANKS, 3);
        addCurated(Items.OAK_BOAT, 1, true, Items.OAK_PLANKS, 5);
        addCurated(Items.GOLDEN_APPLE, 1, true, Items.GOLD_INGOT, 8, Items.APPLE, 1);
        addCurated(Items.GOLDEN_CARROT, 1, true, Items.GOLD_NUGGET, 8, Items.CARROT, 1);
        addCurated(Items.BREAD, 1, true, Items.WHEAT, 3);
        addCurated(Items.STONE_BRICKS, 4, true, Items.STONE, 4);
        addCurated(Items.DEEPSLATE_BRICKS, 4, true, Items.POLISHED_DEEPSLATE, 4);
        addCurated(Items.QUARTZ_BLOCK, 1, true, Items.QUARTZ, 4);
        addCurated(Items.IRON_BLOCK, 1, true, Items.IRON_INGOT, 9);
        addCurated(Items.GOLD_BLOCK, 1, true, Items.GOLD_INGOT, 9);
        addCurated(Items.DIAMOND_BLOCK, 1, true, Items.DIAMOND, 9);
        addCurated(Items.EMERALD_BLOCK, 1, true, Items.EMERALD, 9);
        addCurated(Items.REDSTONE_BLOCK, 1, true, Items.REDSTONE, 9);
        addCurated(Items.HAY_BLOCK, 1, true, Items.WHEAT, 9);
        addCurated(Items.STONE_PRESSURE_PLATE, 1, true, Items.STONE, 2);
        addCurated(Items.STONE_SLAB, 6, true, Items.STONE, 3);
        addCurated(Items.ENDER_EYE, 1, true, Items.ENDER_PEARL, 1, Items.BLAZE_POWDER, 1);
        addCurated(Items.BLAZE_POWDER, 2, true, Items.BLAZE_ROD, 1);
    }

    private void addCurated(Item out, int outCount, boolean expandable, Object... pairs) {
        Map<Item, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put((Item) pairs[i], (Integer) pairs[i + 1]);
        }
        addRecipe(out, outCount, map, expandable);
    }

    private void addRecipe(Item out, int outCount, Map<Item, Integer> ingredients, boolean expandable) {
        String name = new ItemStack(out).getHoverName().getString();
        this.recipeByOutput.put(out, new CalcRecipe(out, outCount, ingredients, name, expandable));
    }

    private void applyFilter() {
        this.filteredRecipes.clear();
        String q = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);
        for (CalcRecipe r : this.allRecipes) {
            if (q.isEmpty() || r.displayName.toLowerCase(Locale.ROOT).contains(q)) {
                this.filteredRecipes.add(r);
            }
        }
        this.leftScroll = 0.0;
    }

    private List<MaterialRow> computeMaterials(CalcRecipe recipe, int desiredOutputQty) {
        if (recipe == null || desiredOutputQty <= 0) return List.of();
        long crafts = (desiredOutputQty + recipe.outputCount - 1L) / recipe.outputCount;

        Map<Item, Long> totals = new LinkedHashMap<>();
        if (this.expandRaw) {
            Set<Item> visiting = new HashSet<>();
            visiting.add(recipe.output);
            for (Map.Entry<Item, Integer> e : recipe.ingredients.entrySet()) {
                expandItem(e.getKey(), crafts * e.getValue(), totals, visiting, 0);
            }
        } else {
            for (Map.Entry<Item, Integer> e : recipe.ingredients.entrySet()) {
                totals.merge(e.getKey(), crafts * e.getValue(), Long::sum);
            }
        }

        Map<Item, Integer> invCounts = scanPlayerInventory();
        List<MaterialRow> rows = new ArrayList<>();
        for (Map.Entry<Item, Long> e : totals.entrySet()) {
            Item item = e.getKey();
            String name = new ItemStack(item).getHoverName().getString();
            rows.add(new MaterialRow(item, name, e.getValue(), invCounts.getOrDefault(item, 0)));
        }
        rows.sort(Comparator.comparingLong(MaterialRow::required).reversed());
        return rows;
    }

    private void expandItem(Item item, long neededQty, Map<Item, Long> totals, Set<Item> visiting, int depth) {
        CalcRecipe sub = this.recipeByOutput.get(item);
        if (sub == null || !sub.allowRecursiveExpand || depth >= 4 || visiting.contains(item)) {
            totals.merge(item, neededQty, Long::sum);
            return;
        }
        long subCrafts = (neededQty + sub.outputCount - 1L) / sub.outputCount;
        visiting.add(item);
        for (Map.Entry<Item, Integer> e : sub.ingredients.entrySet()) {
            expandItem(e.getKey(), subCrafts * e.getValue(), totals, visiting, depth + 1);
        }
        visiting.remove(item);
    }

    private Map<Item, Integer> scanPlayerInventory() {
        Map<Item, Integer> counts = new HashMap<>();
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return counts;
        Inventory inv = client.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty()) {
                counts.merge(st.getItem(), st.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private int panelW() {
        return Math.clamp(this.width - 36, 510, 690);
    }

    private int panelH() {
        return Math.clamp(this.height - 32, 320, 445);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs.getMenuAccentColor();
        int accentDark = cs.getMenuAccentDarkColor();
        int bg = 0xF5000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
        int bgDark = cs.getMenuBackgroundDarkColor();
        int surface = cs.getMenuSurfaceColor();
        int surfaceHover = cs.getMenuSurfaceHoverColor();
        int border = cs.getMenuBorderColor();

        int pw = panelW();
        int ph = panelH();
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        extractor.fill(0, 0, this.width, this.height, 0x88040812);
        GuiRenderUtils.drawDropShadow(extractor, px, py, pw, ph, 8, 8, 150);
        GuiRenderUtils.fillRoundedRect(extractor, px, py, pw, ph, 8, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, px, py, pw, ph, 8, border);

        // Header
        GuiRenderUtils.fillRoundedRect(extractor, px, py, pw, HEADER_H, 8, surface);
        extractor.fill(px, py + HEADER_H - 1, px + pw, py + HEADER_H, border);
        GuiRenderUtils.drawText(extractor, this.font, "🧮 Crafting Material Calculator", px + 14, py + 10, accent, true);
        GuiRenderUtils.drawText(extractor, this.font,
                this.allRecipes.size() + " recipes loaded • Live inventory check", px + 14, py + 24, 0xFF94A3B8);

        int btnY = py + 11;
        int closeX = px + pw - 30;
        boolean closeHov = isHovered(mouseX, mouseY, closeX, btnY, 20, 20);
        GuiRenderUtils.fillRoundedRect(extractor, closeX, btnY, 20, 20, 5, closeHov ? 0x44EF4444 : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, closeX, btnY, 20, 20, 5, closeHov ? 0xFFEF4444 : border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + 10, btnY + 6, closeHov ? 0xFFF87171 : 0xFF94A3B8);

        // Expand Raw Materials Toggle Pill in header
        String rawLabel = this.expandRaw ? "● Raw Materials: ON" : "○ Raw Materials: OFF";
        int rawW = this.font.width(rawLabel) + 14;
        int rawX = closeX - rawW - 8;
        boolean rawHov = isHovered(mouseX, mouseY, rawX, btnY, rawW, 20);
        GuiRenderUtils.fillRoundedRect(extractor, rawX, btnY, rawW, 20, 5,
                this.expandRaw ? GuiRenderUtils.withAlpha(accent, 55) : (rawHov ? surfaceHover : bgDark));
        GuiRenderUtils.drawRoundedOutline(extractor, rawX, btnY, rawW, 20, 5,
                this.expandRaw || rawHov ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, rawLabel, rawX + rawW / 2, btnY + 6,
                this.expandRaw ? accent : 0xFFE2E8F0);

        // Split Body Layout
        int bodyTop = py + HEADER_H + 10;
        int bodyBottom = py + ph - 12;
        int leftW = Math.min(220, (pw - 36) * 42 / 100);
        int leftX = px + 12;
        int rightX = leftX + leftW + 10;
        int rightW = (px + pw - 12) - rightX;

        // LEFT PANE: Search + Craftable Items List
        int sBoxH = 20;
        boolean sFoc = this.searchBox != null && this.searchBox.isFocused();
        GuiRenderUtils.fillRoundedRect(extractor, leftX, bodyTop, leftW, sBoxH, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, leftX, bodyTop, leftW, sBoxH, 5, sFoc ? accent : border);
        GuiRenderUtils.drawText(extractor, this.font, "🔍", leftX + 6, bodyTop + 6, sFoc ? accent : 0xFF64748B);
        if (this.searchBox != null) {
            this.searchBox.setPosition(leftX + 18, bodyTop + 6);
            this.searchBox.setWidth(leftW - 24);
            this.searchBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        int listTop = bodyTop + sBoxH + 6;
        int listH = bodyBottom - listTop;
        int maxLeftScroll = Math.max(0, this.filteredRecipes.size() * (ITEM_ROW_H + 3) - listH);
        this.leftScroll = Math.clamp(this.leftScroll, 0.0, maxLeftScroll);

        GuiRenderUtils.fillRoundedRect(extractor, leftX, listTop, leftW, listH, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, leftX, listTop, leftW, listH, 5, border);

        extractor.enableScissor(leftX + 1, listTop + 1, leftX + leftW - 1, bodyBottom - 1);
        if (this.filteredRecipes.isEmpty()) {
            GuiRenderUtils.drawCenteredText(extractor, this.font, "No matching items",
                    leftX + leftW / 2, listTop + listH / 2 - 4, 0xFF64748B);
        } else {
            int ry = (int) Math.round(listTop + 3 - this.leftScroll);
            for (CalcRecipe r : this.filteredRecipes) {
                if (ry + ITEM_ROW_H >= listTop && ry <= bodyBottom) {
                    boolean sel = r == this.selectedRecipe;
                    boolean hov = mouseY >= listTop && mouseY <= bodyBottom
                            && isHovered(mouseX, mouseY, leftX + 3, ry, leftW - 6, ITEM_ROW_H);
                    int rBg = sel ? GuiRenderUtils.withAlpha(accent, 52) : (hov ? surfaceHover : surface);
                    GuiRenderUtils.fillRoundedRect(extractor, leftX + 3, ry, leftW - 6, ITEM_ROW_H, 4, rBg);
                    if (sel) {
                        GuiRenderUtils.drawRoundedOutline(extractor, leftX + 3, ry, leftW - 6, ITEM_ROW_H, 4, accent);
                    }
                    extractor.item(new ItemStack(r.output), leftX + 6, ry + 3);
                    String nameClip = GuiRenderUtils.truncate(this.font, r.displayName, leftW - 50);
                    GuiRenderUtils.drawText(extractor, this.font, nameClip, leftX + 26, ry + 7,
                            sel ? 0xFFFFFFFF : 0xFFE2E8F0);
                    if (r.outputCount > 1) {
                        String yld = "×" + r.outputCount;
                        GuiRenderUtils.drawText(extractor, this.font, yld,
                                leftX + leftW - this.font.width(yld) - 8, ry + 7, 0xFF94A3B8);
                    }
                }
                ry += ITEM_ROW_H + 3;
            }
        }
        extractor.disableScissor();

        // RIGHT PANE: Quantity Controls + Material Breakdown
        if (this.selectedRecipe == null) {
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Select an item on the left",
                    rightX + rightW / 2, bodyTop + (bodyBottom - bodyTop) / 2, 0xFF64748B);
            return;
        }

        // Hero Card
        int heroH = 66;
        GuiRenderUtils.fillRoundedRect(extractor, rightX, bodyTop, rightW, heroH, 6, surface);
        GuiRenderUtils.drawRoundedOutline(extractor, rightX, bodyTop, rightW, heroH, 6, border);

        extractor.item(new ItemStack(this.selectedRecipe.output), rightX + 8, bodyTop + 9);
        GuiRenderUtils.drawText(extractor, this.font,
                GuiRenderUtils.truncate(this.font, this.selectedRecipe.displayName, rightW - 140),
                rightX + 30, bodyTop + 8, 0xFFFFFFFF, true);

        long crafts = (this.targetQty + this.selectedRecipe.outputCount - 1L) / this.selectedRecipe.outputCount;
        long actualYield = crafts * this.selectedRecipe.outputCount;
        String yieldInfo = "Crafts: " + crafts + " • Yields: " + actualYield + " (" + formatStacks(actualYield) + ")";
        GuiRenderUtils.drawText(extractor, this.font, GuiRenderUtils.truncate(this.font, yieldInfo, rightW - 140),
                rightX + 30, bodyTop + 20, 0xFF94A3B8);

        // Quantity Step Controls (top-right of Hero Card)
        int qy = bodyTop + 8;
        int qh = 18;
        int stepW = 20;
        int plus16X = rightX + rightW - stepW - 6;
        int plus1X = plus16X - stepW - 3;
        int qBoxW = 38;
        int qBoxX = plus1X - qBoxW - 3;
        int minus1X = qBoxX - stepW - 3;

        drawSmallBtn(extractor, minus1X, qy, stepW, qh, "-", mouseX, mouseY, bgDark, surfaceHover, border, accent);
        boolean qFoc = this.qtyBox != null && this.qtyBox.isFocused();
        GuiRenderUtils.fillRoundedRect(extractor, qBoxX, qy, qBoxW, qh, 4, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, qBoxX, qy, qBoxW, qh, 4, qFoc ? accent : border);
        if (this.qtyBox != null) {
            this.qtyBox.setPosition(qBoxX + 5, qy + 5);
            this.qtyBox.setWidth(qBoxW - 8);
            this.qtyBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }
        drawSmallBtn(extractor, plus1X, qy, stepW, qh, "+", mouseX, mouseY, bgDark, surfaceHover, border, accent);
        drawSmallBtn(extractor, plus16X, qy, stepW, qh, "+16", mouseX, mouseY, bgDark, surfaceHover, border, accent);

        // Quick Qty Preset Chips (bottom row of Hero Card)
        int chipY = bodyTop + 40;
        GuiRenderUtils.drawText(extractor, this.font, "Quantity:", rightX + 8, chipY + 4, 0xFF94A3B8);
        int chipX = rightX + 58;
        for (int preset : QTY_PRESETS) {
            String lbl = "×" + preset;
            int cw = this.font.width(lbl) + 10;
            boolean active = this.targetQty == preset;
            boolean hov = isHovered(mouseX, mouseY, chipX, chipY, cw, 16);
            GuiRenderUtils.fillRoundedRect(extractor, chipX, chipY, cw, 16, 4,
                    active ? accent : (hov ? surfaceHover : bgDark));
            GuiRenderUtils.drawRoundedOutline(extractor, chipX, chipY, cw, 16, 4, active || hov ? accent : border);
            GuiRenderUtils.drawCenteredText(extractor, this.font, lbl, chipX + cw / 2, chipY + 4,
                    active ? 0xFFFFFFFF : 0xFFE2E8F0);
            chipX += cw + 4;
        }

        // Materials List
        List<MaterialRow> materials = computeMaterials(this.selectedRecipe, this.targetQty);
        int matTop = bodyTop + heroH + 8;
        int footerH = 26;
        int matBottom = bodyBottom - footerH - 6;
        int matH = Math.max(40, matBottom - matTop);

        int maxRightScroll = Math.max(0, materials.size() * (ING_ROW_H + 4) - matH);
        this.rightScroll = Math.clamp(this.rightScroll, 0.0, maxRightScroll);

        GuiRenderUtils.fillRoundedRect(extractor, rightX, matTop, rightW, matH, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, rightX, matTop, rightW, matH, 5, border);

        int readyCount = 0;
        extractor.enableScissor(rightX + 1, matTop + 1, rightX + rightW - 1, matBottom - 1);
        int my = (int) Math.round(matTop + 4 - this.rightScroll);
        for (MaterialRow row : materials) {
            if (row.satisfied()) readyCount++;
            if (my + ING_ROW_H >= matTop && my <= matBottom) {
                int rx = rightX + 4;
                int rw = rightW - 8;
                int statusCol = row.satisfied() ? 0xFF34D399 : 0xFFF87171;

                GuiRenderUtils.fillRoundedRect(extractor, rx, my, rw, ING_ROW_H, 4, surface);
                GuiRenderUtils.drawRoundedOutline(extractor, rx, my, rw, ING_ROW_H, 4,
                        GuiRenderUtils.withAlpha(statusCol, row.satisfied() ? 130 : 85));

                extractor.item(new ItemStack(row.item), rx + 4, my + 6);

                String reqLabel = row.required + " (" + formatStacks(row.required) + ")";
                String statusTag = row.satisfied() ? "✓ Ready" : ("-" + row.missing());
                int tagW = this.font.width(statusTag) + 10;
                int tagX = rx + rw - tagW - 5;

                int textMaxW = Math.max(40, tagX - (rx + 26) - 6);
                GuiRenderUtils.drawText(extractor, this.font, GuiRenderUtils.truncate(this.font, row.name, textMaxW),
                        rx + 25, my + 4, 0xFFF8FAFC);
                String invSub = "Need: " + reqLabel + "  •  Have: " + row.inInventory;
                GuiRenderUtils.drawText(extractor, this.font, GuiRenderUtils.truncate(this.font, invSub, textMaxW),
                        rx + 25, my + 15, 0xFF94A3B8);

                // Status badge
                GuiRenderUtils.fillRoundedRect(extractor, tagX, my + 7, tagW, 14, 4,
                        GuiRenderUtils.withAlpha(statusCol, 38));
                GuiRenderUtils.drawCenteredText(extractor, this.font, statusTag, tagX + tagW / 2, my + 10, statusCol);
            }
            my += ING_ROW_H + 4;
        }
        extractor.disableScissor();

        // Footer summary & Copy Shopping List button
        int footY = bodyBottom - footerH;
        GuiRenderUtils.fillRoundedRect(extractor, rightX, footY, rightW, footerH, 5, surface);
        GuiRenderUtils.drawRoundedOutline(extractor, rightX, footY, rightW, footerH, 5, border);

        boolean allReady = !materials.isEmpty() && readyCount == materials.size();
        String summary;
        int sumColor;
        if (!this.statusText.isEmpty() && System.currentTimeMillis() < this.statusExpireMs) {
            summary = this.statusText;
            sumColor = 0xFF34D399;
        } else if (allReady) {
            summary = "✓ All " + materials.size() + " materials ready in inventory!";
            sumColor = 0xFF34D399;
        } else {
            summary = readyCount + "/" + materials.size() + " ingredients ready in inventory";
            sumColor = 0xFFCBD5E1;
        }

        int copyW = 74;
        int copyX = rightX + rightW - copyW - 5;
        boolean copyHov = isHovered(mouseX, mouseY, copyX, footY + 4, copyW, 18);
        GuiRenderUtils.fillRoundedRect(extractor, copyX, footY + 4, copyW, 18, 4, copyHov ? accentDark : accent);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "📋 Copy List", copyX + copyW / 2, footY + 9, 0xFFFFFFFF);

        GuiRenderUtils.drawText(extractor, this.font,
                GuiRenderUtils.truncate(this.font, summary, copyX - rightX - 14),
                rightX + 8, footY + 9, sumColor);
    }

    private void drawSmallBtn(GuiGraphicsExtractor extractor, int x, int y, int w, int h, String label,
                              int mouseX, int mouseY, int bgDark, int hoverBg, int border, int accent) {
        boolean hov = isHovered(mouseX, mouseY, x, y, w, h);
        GuiRenderUtils.fillRoundedRect(extractor, x, y, w, h, 4, hov ? hoverBg : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, x, y, w, h, 4, hov ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, label, x + w / 2, y + 5, hov ? accent : 0xFFE2E8F0);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = event.x();
        double my = event.y();

        int pw = panelW();
        int ph = panelH();
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        int btnY = py + 11;

        int closeX = px + pw - 30;
        if (isHovered(mx, my, closeX, btnY, 20, 20)) {
            playClick();
            onClose();
            return true;
        }

        String rawLabel = this.expandRaw ? "● Raw Materials: ON" : "○ Raw Materials: OFF";
        int rawW = this.font.width(rawLabel) + 14;
        int rawX = closeX - rawW - 8;
        if (isHovered(mx, my, rawX, btnY, rawW, 20)) {
            playClick();
            this.expandRaw = !this.expandRaw;
            this.rightScroll = 0.0;
            return true;
        }

        int bodyTop = py + HEADER_H + 10;
        int bodyBottom = py + ph - 12;
        int leftW = Math.min(220, (pw - 36) * 42 / 100);
        int leftX = px + 12;
        int rightX = leftX + leftW + 10;
        int rightW = (px + pw - 12) - rightX;

        // Search Box click
        if (this.searchBox != null && isHovered(mx, my, leftX, bodyTop, leftW, 20)) {
            this.searchBox.setFocused(true);
            if (this.qtyBox != null) this.qtyBox.setFocused(false);
            this.setFocused(this.searchBox);
            this.searchBox.mouseClicked(event, doubleClick);
            return true;
        }

        // Left list item selection
        int listTop = bodyTop + 26;
        if (mx >= leftX && mx <= leftX + leftW && my >= listTop && my <= bodyBottom) {
            int ry = (int) Math.round(listTop + 3 - this.leftScroll);
            for (CalcRecipe r : this.filteredRecipes) {
                if (isHovered(mx, my, leftX + 3, ry, leftW - 6, ITEM_ROW_H)) {
                    playClick();
                    this.selectedRecipe = r;
                    this.rightScroll = 0.0;
                    return true;
                }
                ry += ITEM_ROW_H + 3;
            }
        }

        // Right pane controls
        if (this.selectedRecipe != null) {
            int qy = bodyTop + 8;
            int qh = 18;
            int stepW = 20;
            int plus16X = rightX + rightW - stepW - 6;
            int plus1X = plus16X - stepW - 3;
            int qBoxW = 38;
            int qBoxX = plus1X - qBoxW - 3;
            int minus1X = qBoxX - stepW - 3;

            if (isHovered(mx, my, minus1X, qy, stepW, qh)) {
                playClick();
                setQuantity(this.targetQty - 1);
                return true;
            }
            if (isHovered(mx, my, plus1X, qy, stepW, qh)) {
                playClick();
                setQuantity(this.targetQty + 1);
                return true;
            }
            if (isHovered(mx, my, plus16X, qy, stepW, qh)) {
                playClick();
                setQuantity(this.targetQty + 16);
                return true;
            }
            if (this.qtyBox != null && isHovered(mx, my, qBoxX, qy, qBoxW, qh)) {
                this.qtyBox.setFocused(true);
                if (this.searchBox != null) this.searchBox.setFocused(false);
                this.setFocused(this.qtyBox);
                this.qtyBox.mouseClicked(event, doubleClick);
                return true;
            }

            // Preset chips
            int chipY = bodyTop + 40;
            int chipX = rightX + 58;
            for (int preset : QTY_PRESETS) {
                String lbl = "×" + preset;
                int cw = this.font.width(lbl) + 10;
                if (isHovered(mx, my, chipX, chipY, cw, 16)) {
                    playClick();
                    setQuantity(preset);
                    return true;
                }
                chipX += cw + 4;
            }

            // Copy List button
            int footY = bodyBottom - 26;
            int copyW = 74;
            int copyX = rightX + rightW - copyW - 5;
            if (isHovered(mx, my, copyX, footY + 4, copyW, 18)) {
                playClick();
                copyShoppingList();
                return true;
            }
        }

        if (this.searchBox != null) this.searchBox.setFocused(false);
        if (this.qtyBox != null) this.qtyBox.setFocused(false);
        return super.mouseClicked(event, doubleClick);
    }

    private void setQuantity(int qty) {
        this.targetQty = Math.clamp(qty, 1, 9999);
        if (this.qtyBox != null) {
            this.qtyBox.setValue(String.valueOf(this.targetQty));
        }
    }

    private void copyShoppingList() {
        if (this.selectedRecipe == null) return;
        List<MaterialRow> rows = computeMaterials(this.selectedRecipe, this.targetQty);
        StringBuilder sb = new StringBuilder();
        sb.append(this.targetQty).append("x ").append(this.selectedRecipe.displayName);
        if (this.expandRaw) sb.append(" (Raw Materials)");
        sb.append(":\n");
        for (MaterialRow r : rows) {
            sb.append("- ").append(r.required).append("x ").append(r.name)
                    .append(" (").append(formatStacks(r.required)).append(")");
            if (r.missing() > 0) {
                sb.append(" [Missing ").append(r.missing()).append("]");
            } else {
                sb.append(" [Ready]");
            }
            sb.append('\n');
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString().trim());
        this.statusText = "✓ Copied material list to clipboard!";
        this.statusExpireMs = System.currentTimeMillis() + 3000L;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int pw = panelW();
        int px = (this.width - pw) / 2;
        int leftW = Math.min(220, (pw - 36) * 42 / 100);
        if (mouseX < px + 12 + leftW + 5) {
            this.leftScroll -= verticalAmount * 24.0;
        } else {
            this.rightScroll -= verticalAmount * 24.0;
        }
        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String formatStacks(long count) {
        if (count < 64) return count + " pcs";
        long stacks = count / 64;
        long rem = count % 64;
        return rem == 0 ? (stacks + "s") : (stacks + "s + " + rem);
    }

    private static boolean getDefaultExpandRaw() {
        if (module == null) return false;
        Setting<?> s = module.getSetting("Default Raw Breakdown");
        return s instanceof BooleanSetting bs && bs.getValue();
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static boolean isHovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
