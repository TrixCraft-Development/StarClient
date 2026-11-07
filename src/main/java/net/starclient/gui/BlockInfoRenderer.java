package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.starclient.StarClient;
import net.starclient.mixin.MultiPlayerGameModeAccessor;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders the draggable "Block Info" (WAILA / What Am I Looking At) HUD widget:
 * 3D block icon, block name, harvestability badge, estimated break timer,
 * live mining progress bar, and contextual block state details (crop growth %, redstone power, honey, etc.).
 */
public final class BlockInfoRenderer {

    private BlockInfoRenderer() {
    }

    private record BlockSnapshot(
            ItemStack iconStack,
            String title,
            String subtitle,
            int subtitleColor,
            List<String> extraLines,
            float miningProgress,
            boolean preview
    ) {
    }

    private static BlockSnapshot capture(Minecraft client, Module mod, boolean allowEditorPreview) {
        boolean showHarvest = getBool(mod, "Show Harvestability", true);
        boolean showOptimalTool = getBool(mod, "Show Optimal Tool", true);
        boolean showBreakTime = getBool(mod, "Show Break Time", true);
        boolean showDetails = getBool(mod, "Show Block Details", true);
        boolean showCoords = getBool(mod, "Show Coordinates", false);
        boolean includeFluids = getBool(mod, "Include Fluids", false);
        double maxDist = getNum(mod);

        if (client != null && client.level != null && client.player != null) {
            Level level = client.level;
            Player player = client.player;

            HitResult hit = player.pick(maxDist, 1.0f, includeFluids);
            if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = bhr.getBlockPos();
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && (includeFluids || state.getFluidState().isEmpty() || !state.getCollisionShape(level, pos).isEmpty())) {
                    ItemStack icon = resolveBlockIcon(level, pos, state);
                    String blockName = state.getBlock().getName().getString();

                    // Harvestability & Break Time
                    float destroySpeedPerTick = state.getDestroyProgress(player, level, pos);
                    float destroyHardness = state.getDestroySpeed(level, pos);
                    boolean unbreakable = destroyHardness < 0.0f;
                    boolean harvestable = !unbreakable && (!state.requiresCorrectToolForDrops()
                            || player.getMainHandItem().isCorrectToolForDrops(state));

                    StringBuilder sub = new StringBuilder();
                    int subCol = 0xFFCBD5E1;

                    if (showHarvest) {
                        if (player.getAbilities().instabuild) {
                            sub.append("✦ Creative");
                            subCol = 0xFF38BDF8;
                        } else if (unbreakable) {
                            sub.append("∞ Unbreakable");
                            subCol = 0xFFF87171;
                        } else if (harvestable) {
                            sub.append("✔ Harvestable");
                            subCol = 0xFF34D399;
                        } else {
                            sub.append("✕ Wrong Tool");
                            subCol = 0xFFF87171;
                        }
                    }

                    if (showBreakTime && !player.getAbilities().instabuild && !unbreakable) {
                        if (!sub.isEmpty()) sub.append(" • ");
                        if (destroySpeedPerTick >= 1.0f) {
                            sub.append("Instant");
                        } else if (destroySpeedPerTick > 0.00001f) {
                            double seconds = Math.ceil(1.0 / destroySpeedPerTick) / 20.0;
                            if (seconds >= 60.0) {
                                sub.append(String.format(Locale.US, "%.0fs", seconds));
                            } else {
                                sub.append(String.format(Locale.US, "%.2fs", seconds));
                            }
                        } else {
                            sub.append("∞");
                        }
                    }

                    // Live mining progress
                    float miningProg = 0.0f;
                    if (client.gameMode != null && client.gameMode.isDestroying()) {
                        MultiPlayerGameModeAccessor acc = (MultiPlayerGameModeAccessor) client.gameMode;
                        BlockPos miningPos = acc.getDestroyBlockPos();
                        if (pos.equals(miningPos)) {
                            miningProg = Math.clamp(acc.getDestroyProgress(), 0.0f, 1.0f);
                            if (miningProg > 0.01f && showBreakTime) {
                                int pct = Math.round(miningProg * 100.0f);
                                if (!sub.isEmpty()) sub.append(" ");
                                sub.append("(").append(pct).append("%)");
                            }
                        }
                    }

                    List<String> extras = new ArrayList<>(4);
                    if (showOptimalTool) {
                        String optimalTool = resolveOptimalTool(state, unbreakable, destroyHardness);
                        if (optimalTool != null) {
                            extras.add("Tool: " + optimalTool);
                        }
                    }
                    if (showDetails) {
                        collectBlockStateDetails(state, extras);
                    }
                    if (showCoords) {
                        extras.add("XYZ: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                    }

                    return new BlockSnapshot(icon, blockName, sub.toString(), subCol, extras, miningProg, false);
                }
            }
        }

        if (allowEditorPreview) {
            List<String> extras = new ArrayList<>(3);
            if (showOptimalTool) extras.add("Tool: Shovel");
            if (showDetails) extras.add("Growth: 100% (Mature)");
            if (showCoords) extras.add("XYZ: 128, 64, -42");
            String sub = (showHarvest ? "✔ Harvestable" : "")
                    + (showHarvest && showBreakTime ? " • " : "")
                    + (showBreakTime ? "0.45s" : "");
            return new BlockSnapshot(new ItemStack(Items.GRASS_BLOCK), "Grass Block", sub, 0xFF34D399, extras, 0.35f, true);
        }

        return null;
    }

    private static String resolveOptimalTool(BlockState state, boolean unbreakable, float destroyHardness) {
        if (state.is(Blocks.WATER) || state.is(Blocks.LAVA)) {
            return "Bucket";
        }
        if (unbreakable) {
            return null;
        }
        if (destroyHardness == 0.0f && !state.requiresCorrectToolForDrops()) {
            return "Any";
        }

        if (state.is(BlockTags.SWORD_INSTANTLY_MINES)) {
            return state.is(BlockTags.MINEABLE_WITH_AXE) ? "Sword / Axe" : "Sword";
        }
        if (state.is(BlockTags.SHEARS_EXTREME_BREAKING_SPEED) || state.is(BlockTags.SHEARS_MAJOR_BREAKING_SPEED)) {
            if (state.is(BlockTags.SWORD_EFFICIENT)) return "Shears / Sword";
            if (state.is(BlockTags.MINEABLE_WITH_HOE)) return "Hoe / Shears";
            if (state.is(BlockTags.MINEABLE_WITH_AXE)) return "Axe / Shears";
            return "Shears";
        }
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return "Pickaxe" + resolveRequiredTierSuffix(state);
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return "Axe" + resolveRequiredTierSuffix(state);
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return "Shovel" + resolveRequiredTierSuffix(state);
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return "Hoe" + resolveRequiredTierSuffix(state);
        }
        if (state.is(BlockTags.SHEARS_MINOR_BREAKING_SPEED)) {
            return "Shears";
        }
        if (state.is(BlockTags.SWORD_EFFICIENT)) {
            return "Sword";
        }

        // Fallback speed & drop probe for special or modded blocks
        ItemStack[] probes = {
                new ItemStack(Items.NETHERITE_PICKAXE),
                new ItemStack(Items.NETHERITE_AXE),
                new ItemStack(Items.NETHERITE_SHOVEL),
                new ItemStack(Items.NETHERITE_HOE),
                new ItemStack(Items.SHEARS),
                new ItemStack(Items.NETHERITE_SWORD)
        };
        String[] names = {"Pickaxe", "Axe", "Shovel", "Hoe", "Shears", "Sword"};
        float bestSpeed = 1.0f;
        String bestTool = null;
        for (int i = 0; i < probes.length; i++) {
            float spd = probes[i].getDestroySpeed(state);
            if (spd > bestSpeed || (bestTool == null && state.requiresCorrectToolForDrops() && probes[i].isCorrectToolForDrops(state))) {
                bestSpeed = spd;
                bestTool = names[i];
            }
        }
        if (bestTool != null) {
            return bestTool + resolveRequiredTierSuffix(state);
        }
        return "Any";
    }

    private static String resolveRequiredTierSuffix(BlockState state) {
        if (!state.requiresCorrectToolForDrops()) {
            return "";
        }
        if (state.is(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)) {
            return " (Netherite+)";
        }
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return " (Diamond+)";
        }
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return " (Iron+)";
        }
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
            return " (Stone+)";
        }
        return "";
    }

    private static ItemStack resolveBlockIcon(Level level, BlockPos pos, BlockState state) {
        try {
            ItemStack clone = state.getCloneItemStack(level, pos, false);
            if (clone != null && !clone.isEmpty()) {
                return clone;
            }
        } catch (Throwable ignored) {
        }
        if (state.is(Blocks.WATER)) return new ItemStack(Items.WATER_BUCKET);
        if (state.is(Blocks.LAVA)) return new ItemStack(Items.LAVA_BUCKET);
        if (state.getBlock().asItem() != Items.AIR) {
            return new ItemStack(state.getBlock().asItem());
        }
        return ItemStack.EMPTY;
    }

    private static void collectBlockStateDetails(BlockState state, List<String> out) {
        if (state.getBlock() instanceof CropBlock crop) {
            int age = crop.getAge(state);
            int max = crop.getMaxAge();
            int pct = max > 0 ? Math.round((age * 100.0f) / max) : 100;
            out.add(age >= max ? "Growth: 100% (Mature ✔)" : ("Growth: " + pct + "% (" + age + "/" + max + ")"));
        } else {
            IntegerProperty ageProp = findAgeProperty(state);
            if (ageProp != null) {
                int age = state.getValue(ageProp);
                int max = ageProp.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(age);
                if (max > 0) {
                    int pct = Math.round((age * 100.0f) / max);
                    out.add(age >= max ? "Growth: 100% (Mature ✔)" : ("Growth: " + pct + "% (" + age + "/" + max + ")"));
                }
            }
        }

        if (state.hasProperty(BlockStateProperties.POWER)) {
            int power = state.getValue(BlockStateProperties.POWER);
            out.add("Redstone Power: " + power + " / 15");
        }
        if (state.hasProperty(BlockStateProperties.DELAY)) {
            int delay = state.getValue(BlockStateProperties.DELAY);
            out.add("Repeater Delay: " + delay + " tick" + (delay == 1 ? "" : "s"));
        }
        if (state.hasProperty(BlockStateProperties.LEVEL_HONEY)) {
            int honey = state.getValue(BlockStateProperties.LEVEL_HONEY);
            out.add("Honey: " + honey + " / 5" + (honey >= 5 ? " (Ready ✔)" : ""));
        }
        if (state.hasProperty(BlockStateProperties.LEVEL_COMPOSTER)) {
            int comp = state.getValue(BlockStateProperties.LEVEL_COMPOSTER);
            out.add("Compost: " + comp + " / 8");
        }
        if (state.hasProperty(BlockStateProperties.LEVEL_CAULDRON)) {
            int caul = state.getValue(BlockStateProperties.LEVEL_CAULDRON);
            out.add("Cauldron Level: " + caul + " / 3");
        }
        if (state.hasProperty(BlockStateProperties.NOTE)) {
            int note = state.getValue(BlockStateProperties.NOTE);
            out.add("Note Pitch: " + note + " / 24");
        }
    }

    private static IntegerProperty findAgeProperty(BlockState state) {
        if (state.hasProperty(BlockStateProperties.AGE_3)) return BlockStateProperties.AGE_3;
        if (state.hasProperty(BlockStateProperties.AGE_4)) return BlockStateProperties.AGE_4;
        if (state.hasProperty(BlockStateProperties.AGE_5)) return BlockStateProperties.AGE_5;
        if (state.hasProperty(BlockStateProperties.AGE_7)) return BlockStateProperties.AGE_7;
        if (state.hasProperty(BlockStateProperties.AGE_15)) return BlockStateProperties.AGE_15;
        return null;
    }

    public static int getWidth(Font font, Minecraft client, Module mod) {
        if (font == null) return 154;
        boolean editorMode = client == null || client.player == null
                || client.gui.screen() instanceof ClickGuiScreen
                || client.gui.screen() instanceof HudEditorScreen;
        BlockSnapshot snap = capture(client, mod, editorMode);
        if (snap == null) return 154;
        return computeWidth(font, mod, snap);
    }

    public static int getHeight(Minecraft client, Module mod) {
        boolean editorMode = client == null || client.player == null
                || client.gui.screen() instanceof ClickGuiScreen
                || client.gui.screen() instanceof HudEditorScreen;
        BlockSnapshot snap = capture(client, mod, editorMode);
        if (snap == null) return 32;
        return computeHeight(mod, snap);
    }

    private static int computeWidth(Font font, Module mod, BlockSnapshot snap) {
        boolean showIcon = getBool(mod, "Show Icon", true) && !snap.iconStack().isEmpty();
        int textLeft = showIcon ? 28 : 8;
        int maxTextW = font.width(snap.title());
        if (!snap.subtitle().isEmpty()) {
            maxTextW = Math.max(maxTextW, font.width(snap.subtitle()));
        }
        for (String line : snap.extraLines()) {
            maxTextW = Math.max(maxTextW, font.width(line));
        }
        return Math.max(124, textLeft + maxTextW + 10);
    }

    private static int computeHeight(Module mod, BlockSnapshot snap) {
        boolean showIcon = getBool(mod, "Show Icon", true) && !snap.iconStack().isEmpty();
        int lines = 1 + (snap.subtitle().isEmpty() ? 0 : 1) + snap.extraLines().size();
        int h = 8 + lines * 10;
        if (showIcon) {
            h = Math.max(26, h);
        }
        if (snap.miningProgress() > 0.005f) {
            h += 5;
        }
        return h;
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod,
                              HudLayout layout, int screenWidth, int screenHeight) {
        if (mod == null || client == null || client.font == null) return;
        boolean editorMode = client.gui.screen() instanceof ClickGuiScreen
                || client.gui.screen() instanceof HudEditorScreen;
        BlockSnapshot snap = capture(client, mod, editorMode);
        if (snap == null) return;

        renderSnapshot(extractor, client, mod, layout, screenWidth, screenHeight, snap);
    }

    public static void renderPreview(GuiGraphicsExtractor extractor, Module mod) {
        Minecraft client = Minecraft.getInstance();
        if (mod == null || client.font == null) return;
        BlockSnapshot snap = capture(client, mod, true);
        if (snap == null) return;
        HudLayout layout = StarClient.getInstance().getHudLayout();
        renderSnapshot(extractor, client, mod, layout, extractor.guiWidth(), extractor.guiHeight(), snap);
    }

    private static void renderSnapshot(GuiGraphicsExtractor extractor, Minecraft client, Module mod,
                                       HudLayout layout, int screenWidth, int screenHeight, BlockSnapshot snap) {
        int width = computeWidth(client.font, mod, snap);
        int height = computeHeight(mod, snap);

        int x = layout.effectiveX(HudLayout.BLOCK_INFO, screenWidth, width);
        int y = layout.effectiveY(HudLayout.BLOCK_INFO, screenHeight);
        x = Math.clamp(x, 2, Math.max(2, screenWidth - width - 2));
        y = Math.clamp(y, 2, Math.max(2, screenHeight - height - 2));

        int accent = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        boolean shadow = StarClient.getInstance().getClientSettings().isHudTextShadow();

        if (!mod.isTransparentBackground()) {
            int bg = (mod.getBackgroundColor() & 0x00FFFFFF) | 0xD4000000;
            GuiRenderUtils.drawDropShadow(extractor, x, y, width, height, 5, 3, 85);
            GuiRenderUtils.fillRoundedRect(extractor, x, y, width, height, 5, bg);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, width, height, 5, 0x30FFFFFF);
        }

        boolean showIcon = getBool(mod, "Show Icon", true) && !snap.iconStack().isEmpty();
        int textX = x + 8;
        if (showIcon) {
            int iconX = x + 6;
            int iconY = y + 5;
            GuiRenderUtils.fillRoundedRect(extractor, iconX - 1, iconY - 1, 18, 18, 4, 0x44000000);
            extractor.item(snap.iconStack(), iconX, iconY);
            textX = x + 28;
        }

        int curY = y + 5;
        extractor.text(client.font, snap.title(), textX, curY, mod.getTextColor(), shadow);
        curY += 10;

        if (!snap.subtitle().isEmpty()) {
            extractor.text(client.font, snap.subtitle(), textX, curY, snap.subtitleColor(), shadow);
            curY += 10;
        }

        for (String extra : snap.extraLines()) {
            extractor.text(client.font, extra, textX, curY, 0xFF94A3B8, shadow);
            curY += 10;
        }

        if (snap.miningProgress() > 0.005f) {
            int barX = x + 6;
            int barY = y + height - 6;
            int barW = width - 12;
            GuiRenderUtils.fillRoundedRect(extractor, barX, barY, barW, 3, 1, 0x881E293B);
            int fillW = Math.max(2, Math.round(barW * snap.miningProgress()));
            GuiRenderUtils.fillRoundedRect(extractor, barX, barY, fillW, 3, 1, accent);
        }
    }

    private static boolean getBool(Module mod, String name, boolean def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }

    private static double getNum(Module mod) {
        if (mod == null) return 5.5;
        Setting<?> s = mod.getSetting("Max Distance");
        return s instanceof NumberSetting ns ? ns.getValue() : 5.5;
    }
}
