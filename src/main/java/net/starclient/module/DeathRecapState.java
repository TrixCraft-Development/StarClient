package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ResolvableProfile;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.Setting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * State bridge for the "Death Recap" module:
 * Records comprehensive combat & death metrics when the player dies (killer, weapon,
 * coordinates, dimension, lost XP) and exposes quick-action helpers.
 */
public final class DeathRecapState {

    public record DeathRecord(
            String deathMessage,
            String killerName,
            String killerWeapon,
            boolean isPlayerKiller,
            ResolvableProfile killerProfile,
            double x,
            double y,
            double z,
            String dimension,
            String timeString,
            int xpLevels
    ) {
    }

    private static Module module;
    private static DeathRecord lastDeathRecord;
    private static DeathScreen trackedScreen;

    private DeathRecapState() {
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

    public static DeathRecord getLastDeathRecord() {
        return lastDeathRecord;
    }

    public static void onDeathScreenOpened(DeathScreen screen, Minecraft client) {
        if (!isActive() || screen == null || client == null) return;
        if (screen == trackedScreen) return;
        trackedScreen = screen;

        Player player = client.player;
        if (player == null) return;

        double dx = player.getX();
        double dy = player.getY();
        double dz = player.getZ();

        String dim = "Overworld";
        if (client.level != null) {
            String path = client.level.dimension().identifier().getPath().toLowerCase(Locale.ROOT);
            if (path.contains("nether")) dim = "The Nether";
            else if (path.contains("end")) dim = "The End";
            else dim = "Overworld";
        }

        LivingEntity killerEntity = player.getKillCredit();
        if (killerEntity == null) {
            killerEntity = player.getLastHurtByMob();
        }

        String killerName = "Environmental / Natural";
        String killerWeapon = "";
        boolean isPlayer = false;
        ResolvableProfile profile = null;

        if (killerEntity instanceof Player pKiller) {
            isPlayer = true;
            killerName = pKiller.getGameProfile().name();
            if (killerName == null || killerName.isBlank()) {
                killerName = pKiller.getName().getString();
            }
            profile = ResolvableProfile.createResolved(pKiller.getGameProfile());
            if (!pKiller.getMainHandItem().isEmpty()) {
                killerWeapon = pKiller.getMainHandItem().getHoverName().getString();
            }
        } else if (killerEntity != null) {
            killerName = killerEntity.getName().getString();
            if (!killerEntity.getMainHandItem().isEmpty()) {
                killerWeapon = killerEntity.getMainHandItem().getHoverName().getString();
            }
        }

        String timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        int levels = player.experienceLevel;

        lastDeathRecord = new DeathRecord(
                "Defeated in battle",
                killerName,
                killerWeapon,
                isPlayer,
                profile,
                dx, dy, dz,
                dim,
                timeStr,
                levels
        );
    }

    public static boolean createWaypointFromLastDeath() {
        if (lastDeathRecord == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        String name = "Death: " + lastDeathRecord.killerName();
        if (name.length() > 24) name = name.substring(0, 24);

        int col = 0xFFEF4444; // Red for death waypoint
        String dim = WaypointsState.getCurrentDimension(mc);
        String wk = WaypointsState.getCurrentWorldKey(mc);
        int px = (int) Math.round(lastDeathRecord.x());
        int py = (int) Math.round(lastDeathRecord.y());
        int pz = (int) Math.round(lastDeathRecord.z());
        return WaypointsState.addWaypoint(name, px, py, pz, dim, wk, col) != null;
    }

    public static void copyCoordsToClipboard() {
        if (lastDeathRecord == null) return;
        Minecraft mc = Minecraft.getInstance();
        String coords = String.format(Locale.US, "%.0f, %.0f, %.0f (%s)",
                lastDeathRecord.x(), lastDeathRecord.y(), lastDeathRecord.z(), lastDeathRecord.dimension());
        mc.keyboardHandler.setClipboard(coords);
        if (mc.gui != null) {
            mc.gui.hud.setOverlayMessage(Component.literal("§a✔ Copied death coordinates: " + coords), false);
        }
    }

    public static boolean isAutoDeathWaypoint() {
        return getBool("Auto Death Waypoint", true);
    }

    public static boolean isShowKillerAvatar() {
        return getBool("Show Killer Avatar", true);
    }

    private static boolean getBool(String name, boolean def) {
        if (module == null) return def;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }
}
