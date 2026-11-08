package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * State bridge for the "Sound Radar" module:
 * Listens to in-game sound events and tracks their 3D coordinates, categories, and lifetimes
 * for HUD radar card and crosshair directional indicators.
 */
public final class SoundRadarState {

    public enum SoundCategoryType {
        COMBAT_EXPLOSION(0xFFEF4444, "💥"),
        PLAYER_ACTION(0xFF38BDF8, "👤"),
        BLOCK_INTERACT(0xFFF59E0B, "📦"),
        PROJECTILE(0xFFFACC15, "🏹"),
        MOB_HOSTILE(0xFFF87171, "👾"),
        GENERIC(0xFFCBD5E1, "🔊");

        public final int defaultColor;
        public final String icon;

        SoundCategoryType(int defaultColor, String icon) {
            this.defaultColor = defaultColor;
            this.icon = icon;
        }
    }

    public static class SoundPing {
        public final double x;
        public final double y;
        public final double z;
        public final Identifier soundId;
        public final SoundSource source;
        public final SoundCategoryType category;
        public final String label;
        public final long timestampMs;
        public final float volume;

        public SoundPing(double x, double y, double z, Identifier soundId, SoundSource source,
                         SoundCategoryType category, String label, float volume) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.soundId = soundId;
            this.source = source;
            this.category = category;
            this.label = label;
            this.timestampMs = System.currentTimeMillis();
            this.volume = volume;
        }

        public float getAgeSeconds() {
            return (System.currentTimeMillis() - timestampMs) / 1000.0f;
        }
    }

    private static Module module;
    private static boolean listenerRegistered = false;
    private static final List<SoundPing> recentPings = new CopyOnWriteArrayList<>();

    private SoundRadarState() {
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

    public static void ensureRegistered(Minecraft client) {
        if (listenerRegistered || client == null || client.getSoundManager() == null) return;
        try {
            client.getSoundManager().addListener(new SoundEventListener() {
                @Override
                public void onPlaySound(SoundInstance sound, WeighedSoundEvents soundEvents, float volume) {
                    handleIncomingSound(sound, volume);
                }
            });
            listenerRegistered = true;
        } catch (Throwable ignored) {
        }
    }

    private static void handleIncomingSound(SoundInstance sound, float volume) {
        if (!isActive() || sound == null || sound.isRelative()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        double sx = sound.getX();
        double sy = sound.getY();
        double sz = sound.getZ();

        double distSq = mc.player.distanceToSqr(sx, sy, sz);
        double maxDist = getMaxDistance();
        if (distSq > maxDist * maxDist) return;

        // Ignore sounds originating directly from self (distance < 0.6 blocks)
        if (distSq < 0.36) return;

        Identifier id = sound.getIdentifier();
        if (id == null) return;
        String path = id.getPath();

        SoundCategoryType cat = classifySound(path, sound.getSource());
        if (!shouldTrackCategory(cat)) return;

        String label = formatSoundLabel(path, cat);
        recentPings.add(new SoundPing(sx, sy, sz, id, sound.getSource(), cat, label, volume));

        // Limit maximum queued pings
        while (recentPings.size() > 30) {
            recentPings.remove(0);
        }
    }

    private static SoundCategoryType classifySound(String path, SoundSource source) {
        if (path.contains("explode") || path.contains("tnt") || path.contains("creeper.primed") || path.contains("warden.sonic")) {
            return SoundCategoryType.COMBAT_EXPLOSION;
        }
        if (path.contains("arrow") || path.contains("trident") || path.contains("crossbow") || path.contains("wind_charge")) {
            return SoundCategoryType.PROJECTILE;
        }
        if (source == SoundSource.PLAYERS || path.contains("player") || path.contains("step") || path.contains("swim")
                || path.contains("splash") || path.contains("teleport") || path.contains("eat") || path.contains("drink")
                || path.contains("elytra")) {
            return SoundCategoryType.PLAYER_ACTION;
        }
        if (source == SoundSource.BLOCKS || path.contains("chest") || path.contains("door") || path.contains("piston")
                || path.contains("shulker_box") || path.contains("anvil") || path.contains("lever") || path.contains("button")) {
            return SoundCategoryType.BLOCK_INTERACT;
        }
        if (source == SoundSource.HOSTILE) {
            return SoundCategoryType.MOB_HOSTILE;
        }
        return SoundCategoryType.GENERIC;
    }

    private static String formatSoundLabel(String path, SoundCategoryType cat) {
        if (path.contains("footstep") || path.contains("step")) return "Footsteps";
        if (path.contains("swim") || path.contains("splash")) return "Splash";
        if (path.contains("explode") || path.contains("tnt")) return "Explosion";
        if (path.contains("creeper.primed")) return "Creeper Hiss!";
        if (path.contains("chest.open")) return "Chest Opened";
        if (path.contains("chest.close")) return "Chest Closed";
        if (path.contains("door")) return "Door";
        if (path.contains("piston")) return "Piston";
        if (path.contains("shulker_box")) return "Shulker Box";
        if (path.contains("teleport") || path.contains("portal")) return "Pearl / Teleport";
        if (path.contains("eat") || path.contains("drink")) return "Eating / Drinking";
        if (path.contains("arrow.shoot") || path.contains("bow.shoot")) return "Bow Shot";
        if (path.contains("arrow.hit")) return "Arrow Hit";
        if (path.contains("elytra")) return "Elytra Glide";

        int lastDot = path.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < path.length() - 1) {
            String shortName = path.substring(lastDot + 1).replace('_', ' ');
            return Character.toUpperCase(shortName.charAt(0)) + shortName.substring(1);
        }
        return cat.icon + " Sound";
    }

    private static boolean shouldTrackCategory(SoundCategoryType cat) {
        if (module == null) return true;
        if (cat == SoundCategoryType.PLAYER_ACTION) return getBool("Track Players", true);
        if (cat == SoundCategoryType.COMBAT_EXPLOSION) return getBool("Track Explosions", true);
        if (cat == SoundCategoryType.PROJECTILE) return getBool("Track Projectiles", true);
        if (cat == SoundCategoryType.BLOCK_INTERACT) return getBool("Track Chests & Blocks", true);
        if (cat == SoundCategoryType.MOB_HOSTILE) return getBool("Track Mobs", true);
        return true;
    }

    public static void onClientTick(Minecraft client) {
        ensureRegistered(client);
        float fadeTime = getFadeTime();
        long now = System.currentTimeMillis();

        Iterator<SoundPing> it = recentPings.iterator();
        while (it.hasNext()) {
            SoundPing ping = it.next();
            if ((now - ping.timestampMs) / 1000.0f > fadeTime) {
                recentPings.remove(ping);
            }
        }
    }

    public static List<SoundPing> getRecentPings() {
        return recentPings;
    }

    public static double getMaxDistance() {
        if (module == null) return 32.0;
        Setting<?> s = module.getSetting("Range");
        return s instanceof NumberSetting ns ? ns.getValue() : 32.0;
    }

    public static float getFadeTime() {
        if (module == null) return 2.5f;
        Setting<?> s = module.getSetting("Fade Time (s)");
        return s instanceof NumberSetting ns ? ns.getValue().floatValue() : 2.5f;
    }

    public static boolean isShowDistance() {
        return getBool("Show Distance", true);
    }

    public static boolean isShowSoundName() {
        return getBool("Show Sound Name", true);
    }

    public static boolean isCrosshairIndicators() {
        String mode = getMode();
        return "Crosshair Indicators".equals(mode) || "Both".equals(mode);
    }

    public static boolean isRadarCard() {
        String mode = getMode();
        return "Radar Card".equals(mode) || "Both".equals(mode);
    }

    public static String getMode() {
        if (module == null) return "Radar Card";
        Setting<?> s = module.getSetting("Display Mode");
        return s instanceof ModeSetting ms ? ms.getValue() : "Radar Card";
    }

    private static boolean getBool(String name, boolean def) {
        if (module == null) return def;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }
}
