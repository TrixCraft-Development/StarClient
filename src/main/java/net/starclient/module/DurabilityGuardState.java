package net.starclient.module;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.Locale;

/**
 * Handles Low-Durability Sound Pings and Prevent Tool / Elytra Breaking
 * configured inside the "Armor HUD" module.
 */
public final class DurabilityGuardState {

    private static final EquipmentSlot[] TRACKED_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND
    };

    private static Module armorHudModule;
    private static final boolean[] slotWarned = new boolean[TRACKED_SLOTS.length];
    private static final Item[] lastSlotItem = new Item[TRACKED_SLOTS.length];
    private static long lastPreventNotifyMs = 0L;
    private static long lastElytraFlightWarnMs = 0L;

    private DurabilityGuardState() {
    }

    public static void setModule(Module mod) {
        armorHudModule = mod;
    }

    public static void reset() {
        for (int i = 0; i < TRACKED_SLOTS.length; i++) {
            slotWarned[i] = false;
            lastSlotItem[i] = null;
        }
    }

    public static boolean isModuleActive() {
        return armorHudModule != null && armorHudModule.isEnabled() && armorHudModule.canUse();
    }

    public static float getWarningThresholdRatio() {
        if (armorHudModule != null && armorHudModule.getSetting("Warning Threshold %") instanceof NumberSetting ns) {
            return (float) Math.clamp(ns.getValue() / 100.0, 0.05, 0.30);
        }
        return 0.15f;
    }

    public static boolean isSoundPingEnabled() {
        return isModuleActive() && getBool("Durability Sound Ping", true);
    }

    public static boolean isPreventBreakingEnabled() {
        return isModuleActive() && getBool("Prevent Tool Breaking", false);
    }

    public static void onTick(Minecraft client) {
        if (client == null || client.player == null || client.level == null) return;
        if (!isModuleActive()) return;

        Player player = client.player;
        if (player.getAbilities().instabuild) return;

        float threshold = getWarningThresholdRatio();
        boolean soundPing = getBool("Durability Sound Ping", true);

        for (int i = 0; i < TRACKED_SLOTS.length; i++) {
            ItemStack stack = player.getItemBySlot(TRACKED_SLOTS[i]);
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
                slotWarned[i] = false;
                lastSlotItem[i] = null;
                continue;
            }

            Item item = stack.getItem();
            if (lastSlotItem[i] != item) {
                // Re-evaluate if a different item was swapped into this slot
                slotWarned[i] = false;
                lastSlotItem[i] = item;
            }

            int max = stack.getMaxDamage();
            int remaining = Math.max(0, max - stack.getDamageValue());
            float ratio = (float) remaining / max;

            if (ratio <= threshold) {
                if (!slotWarned[i]) {
                    slotWarned[i] = true;
                    if (soundPing) {
                        player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.75f, 1.65f);
                        int pct = Math.max(1, Math.round(ratio * 100.0f));
                        String msg = String.format(Locale.US, "⚠ Low Durability: %s (%d/%d • %d%%)",
                                stack.getHoverName().getString(), remaining, max, pct);
                        showActionbar(Component.literal(msg).withStyle(ChatFormatting.RED));
                    }
                }
            } else if (ratio > threshold + 0.03f) {
                slotWarned[i] = false;
            }
        }

        // Urgent mid-air Elytra critical durability warning (<= 3 durability while gliding)
        if (isPreventBreakingEnabled() && player.isFallFlying()) {
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            if (!chest.isEmpty() && chest.is(Items.ELYTRA) && chest.isDamageableItem()) {
                int rem = chest.getMaxDamage() - chest.getDamageValue();
                long now = System.currentTimeMillis();
                if (rem <= 3 && now - lastElytraFlightWarnMs > 1200L) {
                    lastElytraFlightWarnMs = now;
                    player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.9f, 0.7f);
                    showActionbar(Component.literal("⚠ ELYTRA CRITICAL (" + rem + " durability left) — Land immediately!")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                }
            }
        }
    }

    public static boolean shouldPreventMining(Player player) {
        if (!isPreventBreakingEnabled() || player == null || player.getAbilities().instabuild) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (isCriticalBreakableGear(stack)) {
            notifySaved(player, stack);
            return true;
        }
        return false;
    }

    public static boolean shouldPreventAttack(Player player) {
        if (!isPreventBreakingEnabled() || player == null || player.getAbilities().instabuild) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (isCriticalBreakableGear(stack)) {
            notifySaved(player, stack);
            return true;
        }
        return false;
    }

    public static boolean shouldPreventItemUse(Player player, InteractionHand hand) {
        if (!isPreventBreakingEnabled() || player == null || hand == null || player.getAbilities().instabuild) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (isCriticalBreakableGear(stack)) {
            notifySaved(player, stack);
            return true;
        }
        return false;
    }

    public static boolean shouldPreventUseOnBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
        if (!isPreventBreakingEnabled() || player == null || hand == null || player.getAbilities().instabuild) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!isCriticalBreakableGear(stack)) {
            return false;
        }
        // Still allow opening chests/containers when not sneaking so holding a 1-durability tool doesn't lock chests
        if (hit != null && player.level() != null && !player.isSecondaryUseActive()) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = player.level().getBlockState(pos);
            if (state.getMenuProvider(player.level(), pos) != null) {
                return false;
            }
        }
        notifySaved(player, stack);
        return true;
    }

    private static boolean isCriticalBreakableGear(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem()) {
            return false;
        }
        int max = stack.getMaxDamage();
        if (max <= 12) {
            return false;
        }
        int remaining = max - stack.getDamageValue();
        return remaining <= 2;
    }

    private static void notifySaved(Player player, ItemStack stack) {
        long now = System.currentTimeMillis();
        if (now - lastPreventNotifyMs < 650L) return;
        lastPreventNotifyMs = now;

        int rem = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.85f);
        showActionbar(Component.literal("🛡 Saved ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(stack.getHoverName().getString()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(" from breaking (" + rem + " durability left)!").withStyle(ChatFormatting.RED)));
    }

    private static void showActionbar(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.hud != null) {
            mc.gui.hud.setOverlayMessage(message, false);
        }
    }

    private static boolean getBool(String name, boolean def) {
        if (armorHudModule == null) return def;
        Setting<?> s = armorHudModule.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }
}
