package net.starclient.module;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.MutedSoundListSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Runtime sound filter, category matcher, volume scaler, and recent-sound
 * ring buffer for the "Sound Muffler" PLAYER module.
 */
public final class SoundMufflerState {
    private static final int MAX_RECENT_SOUNDS = 24;

    private static Module module;
    private static final Deque<String> RECENT_SOUNDS = new ConcurrentLinkedDeque<>();

    private SoundMufflerState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    private static MutedSoundListSetting getListSetting() {
        if (module == null) return null;
        Setting<?> s = module.getSetting("Custom Sounds");
        return s instanceof MutedSoundListSetting mls ? mls : null;
    }

    public static List<String> getCustomFilters() {
        MutedSoundListSetting s = getListSetting();
        return s != null ? s.getValue() : Collections.emptyList();
    }

    public static boolean addCustomFilter(String filter) {
        MutedSoundListSetting s = getListSetting();
        return s != null && s.addFilter(filter);
    }

    public static void removeCustomFilterAt(int index) {
        MutedSoundListSetting s = getListSetting();
        if (s != null) {
            s.removeAt(index);
        }
    }

    public static void clearCustomFilters() {
        MutedSoundListSetting s = getListSetting();
        if (s != null) {
            s.getValue().clear();
        }
    }

    public static void recordRecentSound(Identifier id) {
        if (id == null) return;
        String path = id.getPath();
        if (path == null || path.isEmpty() || "ui.button.click".equals(path)) return;
        String entry = "minecraft".equals(id.getNamespace()) ? path : id.toString();

        RECENT_SOUNDS.remove(entry);
        RECENT_SOUNDS.addFirst(entry);
        while (RECENT_SOUNDS.size() > MAX_RECENT_SOUNDS) {
            RECENT_SOUNDS.pollLast();
        }
    }

    public static List<String> getRecentSounds() {
        return new ArrayList<>(RECENT_SOUNDS);
    }

    public static float getMuffleVolumeMultiplier() {
        if (module == null) return 0.0F;
        Setting<?> s = module.getSetting("Muffle Volume (%)");
        if (s instanceof NumberSetting ns) {
            return (float) Math.clamp(ns.getValue() / 100.0, 0.0, 1.0);
        }
        return 0.0F;
    }

    public static boolean shouldMuffle(Identifier id) {
        if (!isEnabled() || id == null) return false;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String full = id.toString().toLowerCase(Locale.ROOT);

        // 1. Built-in one-click annoying sound category presets
        if (getBool("Nether Portals", true)
                && (path.startsWith("block.portal.") || path.contains("portal.ambient"))) {
            return true;
        }
        if (getBool("Rain & Thunder", false)
                && (path.startsWith("weather.rain") || path.startsWith("entity.lightning_bolt."))) {
            return true;
        }
        if (getBool("Villagers", false)
                && (path.startsWith("entity.villager.") || path.startsWith("entity.wandering_trader."))) {
            return true;
        }
        if (getBool("Endermen", false) && path.startsWith("entity.enderman.")) {
            return true;
        }
        if (getBool("Minecarts", false) && path.startsWith("entity.minecart.")) {
            return true;
        }
        if (getBool("Pistons", false) && path.startsWith("block.piston.")) {
            return true;
        }
        if (getBool("Fire & Lava", false)
                && (path.startsWith("block.fire.") || path.startsWith("block.lava.") || path.startsWith("block.campfire."))) {
            return true;
        }
        if (getBool("Elytra Wind", false) && path.equals("item.elytra.flying")) {
            return true;
        }
        if (getBool("Water & Cave Ambient", false)
                && (path.startsWith("block.water.ambient")
                || path.startsWith("ambient.underwater.")
                || path.startsWith("ambient.cave")
                || path.startsWith("block.bubble_column."))) {
            return true;
        }
        if (getBool("Farm Animals", false)
                && (path.startsWith("entity.cow.")
                || path.startsWith("entity.sheep.")
                || path.startsWith("entity.pig.")
                || path.startsWith("entity.chicken.")
                || path.startsWith("entity.horse.")
                || path.startsWith("entity.bee.")
                || path.startsWith("entity.cat.")
                || path.startsWith("entity.wolf."))) {
            return true;
        }
        if (getBool("Beacon & Conduit", false)
                && (path.startsWith("block.beacon.") || path.startsWith("block.conduit."))) {
            return true;
        }

        // 2. Custom user sound IDs / substring rules
        List<String> custom = getCustomFilters();
        for (String rule : custom) {
            if (rule == null || rule.isBlank()) continue;
            String needle = rule.trim().toLowerCase(Locale.ROOT);
            if (path.contains(needle) || full.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldCompletelyCancel(Identifier id) {
        return shouldMuffle(id) && getMuffleVolumeMultiplier() <= 0.001F;
    }

    public static float scaleVolumeIfMuffled(SoundInstance sound, float originalVolume) {
        if (sound == null || sound.getIdentifier() == null || !isEnabled()) {
            return originalVolume;
        }
        if (shouldMuffle(sound.getIdentifier())) {
            return originalVolume * getMuffleVolumeMultiplier();
        }
        return originalVolume;
    }

    public static boolean isMatchedByCustomRule(String soundName) {
        if (soundName == null || soundName.isBlank()) return false;
        String low = soundName.toLowerCase(Locale.ROOT);
        for (String rule : getCustomFilters()) {
            if (rule != null && !rule.isBlank() && low.contains(rule.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean getBool(String name, boolean fallback) {
        if (module == null) return fallback;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : fallback;
    }
}
