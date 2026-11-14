package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;

/**
 * Low Blocks / Bridge Warning module (PLAYER / SERVER):
 * Monitors bridging blocks across your inventory and alerts you with on-screen badges,
 * actionbar warnings, and audio chimes when your block count drops low.
 */
public final class LowBlocksWarningRenderer {

    private static int lastTotalBlocks = -1;
    private static long lastSoundAlertTime = 0L;
    private static boolean wasInWarningState = false;

    private LowBlocksWarningRenderer() {
    }

    public static void reset() {
        lastTotalBlocks = -1;
        lastSoundAlertTime = 0L;
        wasInWarningState = false;
    }

    public static boolean isBridgeBlock(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof BlockItem)) return false;

        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null) return false;
        String path = key.getPath();

        return path.endsWith("_wool")
                || path.endsWith("_planks")
                || path.endsWith("_terracotta")
                || path.endsWith("_concrete")
                || path.contains("sandstone")
                || path.endsWith("_stained_glass")
                || path.equals("glass")
                || path.equals("end_stone")
                || path.equals("cobblestone")
                || path.equals("stone")
                || path.equals("cobbled_deepslate")
                || path.equals("deepslate")
                || path.equals("netherrack")
                || path.equals("dirt")
                || path.equals("tuff")
                || path.equals("blackstone")
                || path.equals("andesite")
                || path.equals("diorite")
                || path.equals("granite");
    }

    public static int countTotalBridgeBlocks(Inventory inventory) {
        if (inventory == null) return 0;
        int count = 0;
        int size = inventory.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inventory.getItem(i);
            if (isBridgeBlock(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void playAlertSound(Minecraft client, String soundType, boolean critical) {
        if (client.player == null) return;
        SoundEvent se = switch (soundType) {
            case "Bell" -> SoundEvents.NOTE_BLOCK_BELL.value();
            case "Bass" -> SoundEvents.NOTE_BLOCK_BASS.value();
            case "Ding" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case "Anvil" -> SoundEvents.ANVIL_LAND;
            default -> SoundEvents.NOTE_BLOCK_PLING.value();
        };
        float pitch = critical ? 1.6F : 1.1F;
        client.getSoundManager().play(SimpleSoundInstance.forUI(se, pitch));
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (client == null || client.player == null || client.level == null || mod == null) return;

        double warnThresh = mod.getSetting("Warning Threshold") instanceof NumberSetting ns ? ns.getValue() : 16.0;
        double critThresh = mod.getSetting("Critical Threshold") instanceof NumberSetting ns ? ns.getValue() : 4.0;
        boolean soundAlert = !(mod.getSetting("Sound Alert") instanceof BooleanSetting bs) || bs.getValue();
        String soundType = mod.getSetting("Sound Type") instanceof ModeSetting ms ? ms.getValue() : "Pling";
        boolean screenBadge = !(mod.getSetting("Screen Badge") instanceof BooleanSetting bs) || bs.getValue();
        boolean actionbarAlert = mod.getSetting("Actionbar Alert") instanceof BooleanSetting bs && bs.getValue();
        boolean onlyHolding = !(mod.getSetting("Only When Holding") instanceof BooleanSetting bs) || bs.getValue();
        double cooldownSec = mod.getSetting("Sound Cooldown") instanceof NumberSetting ns ? ns.getValue() : 3.0;

        ItemStack mainHand = client.player.getMainHandItem();
        ItemStack offHand = client.player.getOffhandItem();
        boolean isHolding = isBridgeBlock(mainHand) || isBridgeBlock(offHand);

        int totalBlocks = countTotalBridgeBlocks(client.player.getInventory());

        if (onlyHolding && !isHolding) {
            wasInWarningState = false;
            lastTotalBlocks = totalBlocks;
            return;
        }

        boolean isWarning = totalBlocks <= warnThresh && totalBlocks > 0;
        boolean isCritical = totalBlocks <= critThresh && totalBlocks > 0;
        boolean isZero = totalBlocks == 0 && isHolding;

        if (!isWarning && !isZero) {
            wasInWarningState = false;
            lastTotalBlocks = totalBlocks;
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMs = (long) (cooldownSec * 1000L);

        // Sound Trigger
        if (soundAlert) {
            boolean justEnteredWarning = !wasInWarningState;
            boolean countDecreased = (lastTotalBlocks > 0 && totalBlocks < lastTotalBlocks);
            if ((justEnteredWarning || (countDecreased && now - lastSoundAlertTime >= cooldownMs))) {
                lastSoundAlertTime = now;
                playAlertSound(client, soundType, isCritical || isZero);
            }
        }
        wasInWarningState = true;
        lastTotalBlocks = totalBlocks;

        // Actionbar message
        if (actionbarAlert) {
            String actText = isZero
                    ? "§c§l⚠ OUT OF BLOCKS!"
                    : (isCritical
                    ? "§c§l⚠ CRITICAL: §e" + totalBlocks + " BLOCKS REMAINING!"
                    : "§6§l⚠ LOW BLOCKS: §e" + totalBlocks + " REMAINING!");
            client.player.sendOverlayMessage(Component.literal(actText));
        }

        // On-screen floating pill badge
        if (screenBadge) {
            Font font = client.font;
            int sw = extractor.guiWidth();
            int sh = extractor.guiHeight();

            String badgeText = isZero
                    ? "⚠ OUT OF BLOCKS!"
                    : (isCritical
                    ? "⚠ CRITICAL: " + totalBlocks + " BLOCKS!"
                    : "⚠ Low Blocks: " + totalBlocks + " remaining");

            int textWidth = font.width(badgeText);
            int badgeW = textWidth + 24;
            int badgeH = 18;
            int badgeX = (sw - badgeW) / 2;
            int badgeY = sh - 74;

            int accentCol = (isCritical || isZero) ? 0xFFEF4444 : 0xFFF59E0B;

            // Pulse glow for critical / warning
            float pulse = 0.6F + 0.4F * Mth.sin((now % 500L) / 500.0F * (float) Math.PI * 2.0F);
            int borderAlpha = (int) (pulse * 255);
            int borderCol = (borderAlpha << 24) | (accentCol & 0x00FFFFFF);

            GuiRenderUtils.drawDropShadow(extractor, badgeX, badgeY, badgeW, badgeH, 6, 3, 100);
            GuiRenderUtils.fillRoundedRect(extractor, badgeX, badgeY, badgeW, badgeH, 4, 0xEE0F172A);
            GuiRenderUtils.drawRoundedOutline(extractor, badgeX, badgeY, badgeW, badgeH, 4, borderCol);

            // Icon + Text
            GuiRenderUtils.drawText(extractor, font, "🧱", badgeX + 5, badgeY + 4, 0xFFFFFFFF, false);
            GuiRenderUtils.drawText(extractor, font, badgeText, badgeX + 18, badgeY + 4, accentCol, true);
        }
    }
}
