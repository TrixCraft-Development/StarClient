package net.starclient.module;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.starclient.gui.ToastNotificationManager;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Handles Potion Expiry warnings and harmful debuff cure alerts with sound pings,
 * actionbar banners, and slide-in toasts.
 */
public final class PotionAlertsState {

    private static Module module;
    private static final Set<Holder<MobEffect>> warnedExpiring = new HashSet<>();
    private static final Set<Holder<MobEffect>> alertedDebuffs = new HashSet<>();
    private static String activeDebuffCureMessage = "";
    private static long lastDebuffAlertMs = 0L;

    private PotionAlertsState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static Module getModule() {
        return module;
    }

    public static boolean isActive() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static void onClientTick(Minecraft client) {
        if (!isActive() || client == null || client.player == null || client.level == null) {
            warnedExpiring.clear();
            alertedDebuffs.clear();
            activeDebuffCureMessage = "";
            return;
        }

        Player player = client.player;
        if (player.getAbilities().instabuild) return;

        Collection<MobEffectInstance> effects = player.getActiveEffects();
        if (effects == null || effects.isEmpty()) {
            warnedExpiring.clear();
            alertedDebuffs.clear();
            activeDebuffCureMessage = "";
            return;
        }

        int thresholdTicks = (int) Math.round(getExpiryThresholdSeconds() * 20.0);
        boolean expiryEnabled = isExpiryWarning();
        boolean debuffEnabled = isDebuffAlert();
        boolean soundPing = isSoundPing();
        boolean debuffSound = isDebuffSoundPing();
        boolean toastEnabled = isToastNotifications();
        boolean actionbarEnabled = isActionbarWarning();

        Set<Holder<MobEffect>> currentEffects = new HashSet<>();
        String currentDebuffMsg = "";

        for (MobEffectInstance instance : effects) {
            Holder<MobEffect> holder = instance.getEffect();
            MobEffect effect = holder.value();
            currentEffects.add(holder);

            int duration = instance.getDuration();
            int amp = instance.getAmplifier();
            String name = getEffectDisplayName(effect, amp);

            // 1. Harmful Debuff Detection
            if (debuffEnabled && effect.getCategory() == MobEffectCategory.HARMFUL) {
                currentDebuffMsg = "🥛 Afflicted with " + name + " — Drink Milk / Use Totem!";
                if (!alertedDebuffs.contains(holder)) {
                    alertedDebuffs.add(holder);
                    if (debuffSound) {
                        player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.9f, 0.65f);
                    }
                    if (actionbarEnabled) {
                        showActionbar(client, Component.literal("⚠ Afflicted with " + name + " — Drink Milk to Cure!")
                                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
                    }
                    if (toastEnabled) {
                        ToastNotificationManager.pushToast(
                                "debuff_" + effect.getColor(),
                                "Debuff: " + name,
                                "Drink Milk or eat Enchanted Gapple!",
                                "DEBUFF",
                                0xFFEF4444
                        );
                    }
                }
            }

            // 2. Beneficial Buff Expiry Detection
            if (expiryEnabled && effect.isBeneficial()) {
                if (duration <= thresholdTicks && duration > 0) {
                    if (!warnedExpiring.contains(holder)) {
                        warnedExpiring.add(holder);
                        int secondsLeft = Math.max(1, duration / 20);
                        if (soundPing) {
                            player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.85f, 1.85f);
                        }
                        if (actionbarEnabled) {
                            showActionbar(client, Component.literal("⚠ " + name + " Expiring in " + secondsLeft + "s!")
                                    .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
                        }
                        if (toastEnabled) {
                            ToastNotificationManager.pushToast(
                                    "potion_expire_" + effect.getColor(),
                                    name + " Expiring",
                                    secondsLeft + "s remaining!",
                                    "EXPIRING",
                                    0xFFF59E0B
                            );
                        }
                    }
                } else if (duration > thresholdTicks + 40) {
                    // Reset if renewed/re-drank
                    warnedExpiring.remove(holder);
                }
            }
        }

        activeDebuffCureMessage = currentDebuffMsg;

        // Clean up expired effect tracking sets
        warnedExpiring.retainAll(currentEffects);
        alertedDebuffs.retainAll(currentEffects);
    }

    private static String getEffectDisplayName(MobEffect effect, int amplifier) {
        String baseName = Component.translatable(effect.getDescriptionId()).getString();
        if (amplifier <= 0) return baseName;
        String roman = switch (amplifier) {
            case 1 -> "II";
            case 2 -> "III";
            case 3 -> "IV";
            case 4 -> "V";
            default -> String.valueOf(amplifier + 1);
        };
        return baseName + " " + roman;
    }

    private static void showActionbar(Minecraft client, Component message) {
        if (client != null && client.gui != null) {
            client.gui.hud.setOverlayMessage(message, false);
        }
    }

    public static String getActiveDebuffCureMessage() {
        return activeDebuffCureMessage;
    }

    public static boolean isShowCureIndicator() {
        return getBool("Show Cure Indicator", true);
    }

    public static boolean isExpiryWarning() {
        return getBool("Expiry Warning", true);
    }

    public static double getExpiryThresholdSeconds() {
        if (module == null) return 10.0;
        Setting<?> s = module.getSetting("Expiry Threshold (s)");
        return s instanceof NumberSetting ns ? ns.getValue() : 10.0;
    }

    public static boolean isSoundPing() {
        return getBool("Expiry Sound Ping", true);
    }

    public static boolean isDebuffAlert() {
        return getBool("Debuff Alert", true);
    }

    public static boolean isDebuffSoundPing() {
        return getBool("Debuff Sound Ping", true);
    }

    public static boolean isToastNotifications() {
        return getBool("Toast Notifications", true);
    }

    public static boolean isActionbarWarning() {
        return getBool("Actionbar Warning", true);
    }

    private static boolean getBool(String name, boolean def) {
        if (module == null) return def;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }
}
