package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RPG-style Loot & Pickup Feed HUD widget.
 * Tracks items picked up (+X) and dropped/used (-X) with 3D item icons,
 * automatic stack merging, rarity colors, and smooth slide/fade animations.
 */
public final class LootFeedRenderer {

    public static final Map<String, Integer> ACCENT_COLORS = ColorModeSetting.ofPairs(
            "Sky Blue", "FF38BDF8",
            "Cyan", "FF22D3EE",
            "Emerald", "FF34D399",
            "Gold", "FFFACC15",
            "Crimson", "FFF87171",
            "Pink", "FFF472B6",
            "Purple", "FFA78BFA",
            "White", "FFF8FAFC"
    );

    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 3;
    private static final int DEFAULT_WIDTH = 148;

    private static final class FeedEntry {
        final Item item;
        ItemStack iconStack;
        String displayName;
        int delta; // positive for pickup, negative for drop/use
        long createdMs;
        long updatedMs;

        FeedEntry(Item item, ItemStack iconStack, String displayName, int delta, long now) {
            this.item = item;
            this.iconStack = iconStack.copyWithCount(1);
            this.displayName = displayName;
            this.delta = delta;
            this.createdMs = now;
            this.updatedMs = now;
        }
    }

    private static final List<FeedEntry> activeEntries = new ArrayList<>();
    private static final Map<Item, Integer> previousCounts = new HashMap<>();
    private static final Map<Item, ItemStack> sampleStacks = new HashMap<>();
    private static Player lastTrackedPlayer = null;
    private static boolean baselineInitialized = false;
    private static List<FeedEntry> previewEntries = null;

    private static List<FeedEntry> getPreviewEntries() {
        if (previewEntries == null) {
            previewEntries = List.of(
                    new FeedEntry(Items.DIAMOND, new ItemStack(Items.DIAMOND), "Diamond", 12, 0L),
                    new FeedEntry(Items.ENDER_PEARL, new ItemStack(Items.ENDER_PEARL), "Ender Pearl", 4, 0L),
                    new FeedEntry(Items.GOLDEN_APPLE, new ItemStack(Items.GOLDEN_APPLE), "Golden Apple", -1, 0L)
            );
        }
        return previewEntries;
    }

    private LootFeedRenderer() {
    }

    public static void reset() {
        activeEntries.clear();
        previousCounts.clear();
        sampleStacks.clear();
        lastTrackedPlayer = null;
        baselineInitialized = false;
    }

    public static void onTick(Minecraft client, Module mod) {
        if (client == null || client.player == null || client.level == null || mod == null || !mod.isEnabled()) {
            reset();
            return;
        }

        Player player = client.player;
        if (player != lastTrackedPlayer || player.tickCount < 5 || ! player.isAlive()) {
            lastTrackedPlayer = player;
            captureInventoryCounts(player, previousCounts, sampleStacks);
            baselineInitialized = true;
            if (!player.isAlive()) {
                activeEntries.clear();
            }
            return;
        }

        Map<Item, Integer> currentCounts = new LinkedHashMap<>();
        Map<Item, ItemStack> currentSamples = new HashMap<>();
        captureInventoryCounts(player, currentCounts, currentSamples);

        if (!baselineInitialized) {
            previousCounts.clear();
            previousCounts.putAll(currentCounts);
            sampleStacks.clear();
            sampleStacks.putAll(currentSamples);
            baselineInitialized = true;
            return;
        }

        boolean showPickups = getBool(mod, "Show Pickups", true);
        boolean showDrops = getBool(mod, "Show Drops / Used", true);
        boolean mergeStacks = getBool(mod, "Merge Stacks", true);
        int maxEntries = (int) Math.round(getNumber(mod, "Max Entries", 5.0));
        long durationMs = Math.round(getNumber(mod, "Duration", 4.0) * 1000.0);
        long now = System.currentTimeMillis();

        // Remove expired entries first
        pruneExpired(now, durationMs);

        // Compare all items present in current or previous inventory snapshot
        Map<Item, Integer> allDeltas = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> entry : currentCounts.entrySet()) {
            Item item = entry.getKey();
            int curr = entry.getValue();
            int prev = previousCounts.getOrDefault(item, 0);
            if (curr != prev) {
                allDeltas.put(item, curr - prev);
            }
        }
        for (Map.Entry<Item, Integer> entry : previousCounts.entrySet()) {
            Item item = entry.getKey();
            if (!currentCounts.containsKey(item)) {
                int prev = entry.getValue();
                if (prev > 0) {
                    allDeltas.put(item, -prev);
                }
            }
        }

        for (Map.Entry<Item, Integer> deltaEntry : allDeltas.entrySet()) {
            Item item = deltaEntry.getKey();
            int delta = deltaEntry.getValue();
            if (delta == 0) continue;
            if (delta > 0 && !showPickups) continue;
            if (delta < 0 && !showDrops) continue;

            ItemStack representative = currentSamples.getOrDefault(item, sampleStacks.getOrDefault(item, new ItemStack(item)));
            String name = representative.getHoverName().getString();

            boolean merged = false;
            if (mergeStacks) {
                for (FeedEntry existing : activeEntries) {
                    if (existing.item == item && ((existing.delta > 0 && delta > 0) || (existing.delta < 0 && delta < 0))) {
                        existing.delta += delta;
                        existing.updatedMs = now;
                        existing.iconStack = representative.copyWithCount(1);
                        existing.displayName = name;
                        merged = true;
                        break;
                    }
                }
            }

            if (!merged) {
                activeEntries.add(0, new FeedEntry(item, representative, name, delta, now));
                while (activeEntries.size() > maxEntries) {
                    activeEntries.remove(activeEntries.size() - 1);
                }
            }
        }

        previousCounts.clear();
        previousCounts.putAll(currentCounts);
        sampleStacks.clear();
        sampleStacks.putAll(currentSamples);
    }

    private static void captureInventoryCounts(Player player, Map<Item, Integer> countsOut, Map<Item, ItemStack> samplesOut) {
        countsOut.clear();
        samplesOut.clear();
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            addStackToMap(inv.getItem(i), countsOut, samplesOut);
        }
        addStackToMap(player.getOffhandItem(), countsOut, samplesOut);
        if (player.containerMenu != null) {
            addStackToMap(player.containerMenu.getCarried(), countsOut, samplesOut);
        }
    }

    private static void addStackToMap(ItemStack stack, Map<Item, Integer> countsOut, Map<Item, ItemStack> samplesOut) {
        if (stack == null || stack.isEmpty()) return;
        Item item = stack.getItem();
        countsOut.merge(item, stack.getCount(), Integer::sum);
        samplesOut.putIfAbsent(item, stack.copyWithCount(1));
    }

    private static void pruneExpired(long now, long durationMs) {
        Iterator<FeedEntry> it = activeEntries.iterator();
        while (it.hasNext()) {
            FeedEntry e = it.next();
            if (now - e.updatedMs > durationMs) {
                it.remove();
            }
        }
    }

    public static int getWidth(Font font, Module mod) {
        boolean showName = getBool(mod, "Show Item Name", true);
        if (!showName) {
            return 68;
        }
        if (font == null) {
            return DEFAULT_WIDTH;
        }
        int maxW = DEFAULT_WIDTH;
        List<FeedEntry> list = activeEntries.isEmpty() ? getPreviewEntries() : activeEntries;
        for (FeedEntry e : list) {
            String deltaStr = (e.delta > 0 ? "+" : "") + e.delta;
            int rowW = 28 + font.width(deltaStr) + 6 + font.width(e.displayName) + 10;
            if (rowW > maxW) {
                maxW = rowW;
            }
        }
        return Math.min(220, maxW);
    }

    public static int getHeight(Module mod) {
        int count = activeEntries.isEmpty() ? 3 : activeEntries.size();
        int maxEntries = (int) Math.round(getNumber(mod, "Max Entries", 5.0));
        count = Math.clamp(count, 1, maxEntries);
        return count * ROW_HEIGHT + (count - 1) * ROW_GAP;
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod, int x, int y) {
        if (mod == null || client == null) return;
        boolean inEditor = client.gui.screen() instanceof HudEditorScreen;
        long durationMs = Math.round(getNumber(mod, "Duration", 4.0) * 1000.0);
        long now = System.currentTimeMillis();
        pruneExpired(now, durationMs);

        if (activeEntries.isEmpty()) {
            if (inEditor) {
                renderEntries(extractor, client, mod, x, y, getPreviewEntries(), true, now, durationMs);
            }
            return;
        }
        renderEntries(extractor, client, mod, x, y, activeEntries, false, now, durationMs);
    }

    public static void renderPreview(GuiGraphicsExtractor extractor, Module mod, int x, int y) {
        if (mod == null) return;
        Minecraft client = Minecraft.getInstance();
        long durationMs = Math.round(getNumber(mod, "Duration", 4.0) * 1000.0);
        List<FeedEntry> list = activeEntries.isEmpty() ? getPreviewEntries() : activeEntries;
        renderEntries(extractor, client, mod, x, y, list, true, System.currentTimeMillis(), durationMs);
    }

    private static void renderEntries(GuiGraphicsExtractor extractor, Minecraft client, Module mod,
                                      int baseX, int baseY, List<FeedEntry> entries,
                                      boolean isPreview, long now, long durationMs) {
        Font font = client.font;
        boolean transparent = mod.isTransparentBackground();
        int baseBgColor = mod.getBackgroundColor();
        int baseTextColor = mod.getTextColor();
        boolean shadow = StarClient.getInstance().getClientSettings().isHudTextShadow();

        boolean showName = getBool(mod, "Show Item Name", true);
        boolean showRarity = getBool(mod, "Show Rarity Color", true);
        String animMode = getString(mod, "Animation", "Slide");
        int baseAccent = resolveAccentColor(mod);

        int maxEntries = (int) Math.round(getNumber(mod, "Max Entries", 5.0));
        int count = Math.min(entries.size(), maxEntries);

        for (int i = 0; i < count; i++) {
            FeedEntry entry = entries.get(i);
            float alphaFactor = 1.0f;
            int slideOffsetX = 0;

            if (!isPreview) {
                long age = Math.max(0L, now - entry.createdMs);
                long sinceUpdate = Math.max(0L, now - entry.updatedMs);
                long remaining = Math.max(0L, durationMs - sinceUpdate);

                // Entry fade/slide in (first 180ms)
                if (!"None".equalsIgnoreCase(animMode) && age < 180L) {
                    float t = age / 180.0f;
                    float easeOut = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
                    alphaFactor = easeOut;
                    if ("Slide".equalsIgnoreCase(animMode)) {
                        slideOffsetX = Math.round((1.0f - easeOut) * -18.0f);
                    }
                }
                // Exit fade/slide out (last 400ms)
                if (!"None".equalsIgnoreCase(animMode) && remaining < 400L) {
                    float t = remaining / 400.0f;
                    alphaFactor = Math.min(alphaFactor, t);
                    if ("Slide".equalsIgnoreCase(animMode)) {
                        slideOffsetX = Math.round((1.0f - t) * -14.0f);
                    }
                }
            }

            if (alphaFactor <= 0.04f) continue;

            boolean isPickup = entry.delta > 0;
            String deltaStr = (isPickup ? "+" : "") + entry.delta;
            int deltaW = font.width(deltaStr);
            int nameW = showName ? font.width(entry.displayName) : 0;
            int rowW = showName ? (26 + deltaW + 6 + nameW + 10) : (26 + deltaW + 8);

            int rx = baseX + slideOffsetX;
            int ry = baseY + i * (ROW_HEIGHT + ROW_GAP);

            int bgAlpha = Math.round(204 * alphaFactor);
            int textAlpha = Math.round(255 * alphaFactor);
            int cardBg = (baseBgColor & 0x00FFFFFF) | (bgAlpha << 24);

            int statusColor = isPickup ? 0xFF34D399 : 0xFFF87171;
            int barColor = GuiRenderUtils.withAlpha(statusColor, textAlpha);
            int outlineColor = GuiRenderUtils.withAlpha(baseAccent, Math.round(90 * alphaFactor));

            // Recent merge pulse highlight
            if (!isPreview && (now - entry.updatedMs) < 220L && (now - entry.createdMs) > 50L) {
                float pulse = 1.0f - ((now - entry.updatedMs) / 220.0f);
                outlineColor = GuiRenderUtils.withAlpha(statusColor, Math.round((90 + 140 * pulse) * alphaFactor));
            }

            if (!transparent) {
                GuiRenderUtils.fillRoundedRect(extractor, rx, ry, rowW, ROW_HEIGHT, 4, cardBg);
                GuiRenderUtils.drawRoundedOutline(extractor, rx, ry, rowW, ROW_HEIGHT, 4, outlineColor);
                // Left status accent stripe
                extractor.fill(rx + 1, ry + 3, rx + 3, ry + ROW_HEIGHT - 3, barColor);
            }

            // 3D Item Icon
            extractor.item(entry.iconStack, rx + 5, ry + 2);

            // Delta text (+16 / -1)
            int tx = rx + 24;
            int ty = ry + (ROW_HEIGHT - font.lineHeight) / 2 + 1;
            extractor.text(font, deltaStr, tx, ty, barColor, shadow);

            // Item Name
            if (showName) {
                int itemColor = resolveItemNameColor(entry.iconStack, baseTextColor, showRarity);
                int nameColWithAlpha = GuiRenderUtils.withAlpha(itemColor, textAlpha);
                extractor.text(font, entry.displayName, tx + deltaW + 5, ty, nameColWithAlpha, shadow);
            }
        }
    }

    private static int resolveItemNameColor(ItemStack stack, int fallbackColor, boolean showRarity) {
        if (!showRarity || stack == null || stack.isEmpty()) {
            return fallbackColor;
        }
        Rarity rarity = stack.get(DataComponents.RARITY);
        if (rarity != null) {
            return switch (rarity) {
                case EPIC -> 0xFFD946EF;     // Light Purple
                case RARE -> 0xFF38BDF8;     // Aqua
                case UNCOMMON -> 0xFFFACC15; // Yellow
                case COMMON -> stack.isEnchanted() ? 0xFF38BDF8 : fallbackColor;
            };
        }
        return stack.isEnchanted() ? 0xFF38BDF8 : fallbackColor;
    }

    private static int resolveAccentColor(Module mod) {
        if (getBool(mod, "Use Client Color", true)) {
            return StarClient.getInstance().getClientSettings().getMenuAccentColor();
        }
        Setting<?> colorSetting = mod.getSetting("Color");
        if (colorSetting instanceof ColorModeSetting cms) {
            return cms.getColor();
        }
        return 0xFF38BDF8;
    }

    private static boolean getBool(Module mod, String name, boolean def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s != null && s.getValue() instanceof Boolean b ? b : def;
    }

    private static double getNumber(Module mod, String name, double def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s != null && s.getValue() instanceof Number n ? n.doubleValue() : def;
    }

    private static String getString(Module mod, String name, String def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s != null && s.getValue() instanceof String str ? str : def;
    }
}
