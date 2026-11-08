package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * State bridge for the "1.8 Module" (Old Animations & 1.8 Combat):
 * Brings authentic Minecraft 1.8.9 mechanics, animations, and visual feel to modern Minecraft:
 * - Sword block-hitting & chops
 * - Snappy 1.8 arm swing trajectory
 * - Old instant re-equip without lowering dip delay
 * - Old fishing rod angle & centered bow draw
 * - Bouncy mouth-bobbing eating/drinking without sideways hand tilt
 * - Red armor damage flash on hurt entities
 * - Hide modern 1.9+ crosshair and hotbar attack cooldown indicators
 * - Disable modern sweep particles & sweep swoosh sound
 * - Hide offhand item in first person
 * - Instant 1.8 sneak eye height snapping without camera glide
 * - Classic hit impact sounds & bow ding chime
 */
public final class OldAnimationsState {

    private static volatile boolean active;
    private static volatile boolean blockHitting = true;
    private static volatile boolean oldSwing = true;
    private static volatile boolean oldReequip = true;
    private static volatile boolean oldRodAndBow = true;
    private static volatile boolean oldEating = true;
    private static volatile boolean redArmorFlash = true;
    private static volatile boolean hideCooldown = true;
    private static volatile boolean disableSweep = true;
    private static volatile boolean hideOffhand = true;
    private static volatile boolean oldSneak = false;
    private static volatile boolean oldHitSounds = true;
    private static volatile boolean bowDing = true;

    private OldAnimationsState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        OldAnimationsState.active = active;
    }

    public static boolean isBlockHitting() {
        return active && blockHitting;
    }

    public static void setBlockHitting(boolean blockHitting) {
        OldAnimationsState.blockHitting = blockHitting;
    }

    public static boolean isOldSwing() {
        return active && oldSwing;
    }

    public static void setOldSwing(boolean oldSwing) {
        OldAnimationsState.oldSwing = oldSwing;
    }

    public static boolean isOldReequip() {
        return active && oldReequip;
    }

    public static void setOldReequip(boolean oldReequip) {
        OldAnimationsState.oldReequip = oldReequip;
    }

    public static boolean isOldRodAndBow() {
        return active && oldRodAndBow;
    }

    public static void setOldRodAndBow(boolean oldRodAndBow) {
        OldAnimationsState.oldRodAndBow = oldRodAndBow;
    }

    public static boolean isOldEating() {
        return active && oldEating;
    }

    public static void setOldEating(boolean oldEating) {
        OldAnimationsState.oldEating = oldEating;
    }

    public static boolean isRedArmorFlash() {
        return active && redArmorFlash;
    }

    public static void setRedArmorFlash(boolean redArmorFlash) {
        OldAnimationsState.redArmorFlash = redArmorFlash;
    }

    public static boolean isHideCooldown() {
        return active && hideCooldown;
    }

    public static void setHideCooldown(boolean hideCooldown) {
        OldAnimationsState.hideCooldown = hideCooldown;
    }

    public static boolean isDisableSweep() {
        return active && disableSweep;
    }

    public static void setDisableSweep(boolean disableSweep) {
        OldAnimationsState.disableSweep = disableSweep;
    }

    public static boolean isHideOffhand() {
        return active && hideOffhand;
    }

    public static void setHideOffhand(boolean hideOffhand) {
        OldAnimationsState.hideOffhand = hideOffhand;
    }

    public static boolean isOldSneak() {
        return active && oldSneak;
    }

    public static void setOldSneak(boolean oldSneak) {
        OldAnimationsState.oldSneak = oldSneak;
    }

    public static boolean isOldHitSounds() {
        return active && oldHitSounds;
    }

    public static void setOldHitSounds(boolean oldHitSounds) {
        OldAnimationsState.oldHitSounds = oldHitSounds;
    }

    public static boolean isBowDing() {
        return active && bowDing;
    }

    public static void setBowDing(boolean bowDing) {
        OldAnimationsState.bowDing = bowDing;
    }

    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(ItemTags.SWORDS);
    }

    /**
     * Returns true if the client player is actively holding a sword and right-clicking (or blocking).
     */
    public static boolean isBlockingWithSword() {
        if (!isActive() || !blockHitting) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (mc.gui != null && mc.gui.screen() != null)) return false;
        ItemStack main = mc.player.getMainHandItem();
        if (!isSword(main)) return false;
        if (mc.player.isUsingItem() && mc.player.getUsedItemHand() != InteractionHand.MAIN_HAND) {
            return false;
        }
        return mc.options.keyUse.isDown() || mc.player.isUsingItem();
    }
}
