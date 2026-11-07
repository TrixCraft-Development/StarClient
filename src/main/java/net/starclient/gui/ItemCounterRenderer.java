package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Draggable HUD widget counting key PvP/survival items across main inventory + offhand.
 */
public final class ItemCounterRenderer {

    private ItemCounterRenderer() {
    }

    private record TrackedEntry(ItemStack icon, int count) {
    }

    private static final ItemStack ICON_TOTEM = new ItemStack(Items.TOTEM_OF_UNDYING);
    private static final ItemStack ICON_GAPPLE = new ItemStack(Items.GOLDEN_APPLE);
    private static final ItemStack ICON_EGAPPLE = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
    private static final ItemStack ICON_PEARL = new ItemStack(Items.ENDER_PEARL);
    private static final ItemStack ICON_ARROW = new ItemStack(Items.ARROW);
    private static final ItemStack ICON_POTION = new ItemStack(Items.SPLASH_POTION);
    private static final ItemStack ICON_XP = new ItemStack(Items.EXPERIENCE_BOTTLE);
    private static final ItemStack ICON_CRYSTAL = new ItemStack(Items.END_CRYSTAL);
    private static final ItemStack ICON_BLOCKS = new ItemStack(Items.COBBLESTONE);

    private static List<TrackedEntry> collectEntries(Minecraft client, Module mod, boolean forcePreview) {
        boolean hideZero = getBool(mod, "Hide Zero", true);
        boolean inEditor = forcePreview
                || client == null
                || client.player == null
                || client.gui.screen() instanceof ClickGuiScreen
                || client.gui.screen() instanceof HudEditorScreen;

        List<TrackedEntry> list = new ArrayList<>(9);
        addIfEnabled(list, client, mod, "Totems", true, ICON_TOTEM, s -> s.is(Items.TOTEM_OF_UNDYING), hideZero, 2, inEditor);
        addIfEnabled(list, client, mod, "Golden Apples", true, ICON_GAPPLE, s -> s.is(Items.GOLDEN_APPLE), hideZero, 12, inEditor);
        addIfEnabled(list, client, mod, "Enchanted Gapples", false, ICON_EGAPPLE, s -> s.is(Items.ENCHANTED_GOLDEN_APPLE), hideZero, 1, inEditor);
        addIfEnabled(list, client, mod, "Ender Pearls", true, ICON_PEARL, s -> s.is(Items.ENDER_PEARL), hideZero, 16, inEditor);
        addIfEnabled(list, client, mod, "Arrows", true, ICON_ARROW, s -> s.getItem() instanceof ArrowItem, hideZero, 64, inEditor);
        addIfEnabled(list, client, mod, "Healing Potions", false, ICON_POTION, ItemCounterRenderer::isHealingPotion, hideZero, 6, inEditor);
        addIfEnabled(list, client, mod, "XP Bottles", false, ICON_XP, s -> s.is(Items.EXPERIENCE_BOTTLE), hideZero, 64, inEditor);
        addIfEnabled(list, client, mod, "End Crystals", false, ICON_CRYSTAL, s -> s.is(Items.END_CRYSTAL), hideZero, 32, inEditor);
        addIfEnabled(list, client, mod, "Building Blocks", false, ICON_BLOCKS, s -> s.getItem() instanceof BlockItem, hideZero, 128, inEditor);

        if (list.isEmpty() && inEditor) {
            list.add(new TrackedEntry(ICON_TOTEM, client != null && client.player != null ? 0 : 2));
            list.add(new TrackedEntry(ICON_GAPPLE, client != null && client.player != null ? 0 : 12));
            list.add(new TrackedEntry(ICON_PEARL, client != null && client.player != null ? 0 : 16));
        }
        return list;
    }

    private static void addIfEnabled(List<TrackedEntry> out, Minecraft client, Module mod,
                                     String settingName, boolean defEnabled,
                                     ItemStack icon, Predicate<ItemStack> matcher,
                                     boolean hideZero, int sampleCount, boolean inEditor) {
        if (!getBool(mod, settingName, defEnabled)) return;
        int count;
        if (client == null || client.player == null) {
            count = sampleCount;
        } else {
            count = countMatching(client, matcher);
        }
        if (count > 0 || !hideZero) {
            out.add(new TrackedEntry(icon, count));
        }
    }

    private static int countMatching(Minecraft client, Predicate<ItemStack> matcher) {
        if (client == null || client.player == null) return 0;
        int total = 0;
        Inventory inv = client.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && matcher.test(s)) {
                total += s.getCount();
            }
        }
        ItemStack offhand = client.player.getOffhandItem();
        if (!offhand.isEmpty() && matcher.test(offhand)) {
            total += offhand.getCount();
        }
        return total;
    }

    private static boolean isHealingPotion(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect().equals(MobEffects.INSTANT_HEALTH)
                    || effect.getEffect().equals(MobEffects.REGENERATION)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isVertical(Module mod) {
        if (mod == null) return false;
        Setting<?> s = mod.getSetting("Orientation");
        return s != null && "Vertical".equalsIgnoreCase(String.valueOf(s.getValue()));
    }

    public static int getWidth(Font font, Minecraft client, Module mod) {
        List<TrackedEntry> entries = collectEntries(client, mod, false);
        int n = Math.max(1, entries.size());
        if (isVertical(mod)) {
            int maxTextW = 14;
            if (font != null) {
                for (TrackedEntry e : entries) {
                    maxTextW = Math.max(maxTextW, font.width(String.valueOf(e.count())));
                }
            }
            return 26 + maxTextW + 8;
        }
        return n * 26 + (n - 1) * 3;
    }

    public static int getHeight(Minecraft client, Module mod) {
        List<TrackedEntry> entries = collectEntries(client, mod, false);
        int n = Math.max(1, entries.size());
        if (isVertical(mod)) {
            return n * 20 + (n - 1) * 3;
        }
        return 28;
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod,
                              HudLayout layout, int screenWidth, int screenHeight) {
        if (mod == null) return;
        renderInternal(extractor, client, mod, layout, screenWidth, screenHeight, false);
    }

    public static void renderPreview(GuiGraphicsExtractor extractor, Module mod) {
        if (mod == null) return;
        Minecraft client = Minecraft.getInstance();
        HudLayout layout = StarClient.getInstance().getHudLayout();
        renderInternal(extractor, client, mod, layout, extractor.guiWidth(), extractor.guiHeight(), true);
    }

    private static void renderInternal(GuiGraphicsExtractor extractor, Minecraft client, Module mod,
                                       HudLayout layout, int screenWidth, int screenHeight, boolean forcePreview) {
        List<TrackedEntry> entries = collectEntries(client, mod, forcePreview);
        if (entries.isEmpty()) return;

        boolean vertical = isVertical(mod);
        int width = getWidth(client.font, client, mod);
        int height = getHeight(client, mod);

        int baseX = layout.effectiveX(HudLayout.ITEM_COUNTER, screenWidth, width);
        int baseY = layout.effectiveY(HudLayout.ITEM_COUNTER, screenHeight);
        baseX = Math.clamp(baseX, 2, Math.max(2, screenWidth - width - 2));
        baseY = Math.clamp(baseY, 2, Math.max(2, screenHeight - height - 2));

        boolean transparent = mod.isTransparentBackground();
        int bgColor = (mod.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
        int textColor = mod.getTextColor();
        boolean shadow = StarClient.getInstance().getClientSettings().isHudTextShadow();

        for (int i = 0; i < entries.size(); i++) {
            TrackedEntry entry = entries.get(i);
            String countStr = String.valueOf(entry.count());
            int countCol = entry.count() == 0 ? 0xFF94A3B8 : textColor;

            if (vertical) {
                int cx = baseX;
                int cy = baseY + i * 23;
                if (!transparent) {
                    GuiRenderUtils.fillRoundedRect(extractor, cx, cy, width, 20, 4, bgColor);
                    GuiRenderUtils.drawRoundedOutline(extractor, cx, cy, width, 20, 4, 0x28FFFFFF);
                }
                extractor.item(entry.icon(), cx + 2, cy + 2);
                extractor.text(client.font, countStr, cx + 22, cy + 6, countCol, shadow);
            } else {
                int cx = baseX + i * 29;
                int cy = baseY;
                if (!transparent) {
                    GuiRenderUtils.fillRoundedRect(extractor, cx, cy, 26, 28, 4, bgColor);
                    GuiRenderUtils.drawRoundedOutline(extractor, cx, cy, 26, 28, 4, 0x28FFFFFF);
                }
                extractor.item(entry.icon(), cx + 5, cy + 2);
                int tw = client.font.width(countStr);
                extractor.text(client.font, countStr, cx + (26 - tw) / 2, cy + 18, countCol, shadow);
            }
        }
    }

    private static boolean getBool(Module mod, String name, boolean def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s != null && s.getValue() instanceof Boolean b ? b : def;
    }
}
