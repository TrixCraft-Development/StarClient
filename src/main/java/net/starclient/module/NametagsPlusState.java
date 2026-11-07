package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.Locale;

/**
 * State & nametag decoration/scaling helpers for the "Nametags" RENDER module.
 */
public final class NametagsPlusState {

    private static Module module;

    private NametagsPlusState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    /**
     * Own 3rd-person nametag is visible by default in Star Client; enabling Nametags Plus
     * lets the user toggle "Own Nametag (3rd Person)" ON or OFF.
     */
    public static boolean shouldShowOwnNametag() {
        if (!isEnabled()) {
            return true;
        }
        return getBool("Own Nametag (3rd Person)", true);
    }

    public static boolean isTransparentBackground() {
        return isEnabled() && module.isTransparentBackground();
    }

    public static float computeScaleMultiplier(Vec3 cameraRelPos) {
        if (!isEnabled()) {
            return 1.0f;
        }
        float scale = 1.0f;
        if (module.getSetting("Scale") instanceof NumberSetting ns) {
            scale = ns.getValue().floatValue();
        }
        if (getBool("Distance Scale", false) && cameraRelPos != null) {
            double dist = cameraRelPos.length();
            if (dist > 10.0) {
                float distFactor = 1.0f + (float) Math.clamp((dist - 10.0) * 0.045, 0.0, 2.2);
                scale *= distFactor;
            }
        }
        return scale;
    }

    public static Component decoratePlayerNametag(Player player, Component base) {
        if (!isEnabled() || player == null || base == null) {
            return base;
        }

        boolean showPing = getBool("Show Ping", true);
        boolean showHealth = getBool("Show Health", true);
        if (!showPing && !showHealth) {
            return base;
        }

        MutableComponent out = Component.empty().append(base);

        if (showPing) {
            Minecraft mc = Minecraft.getInstance();
            ClientPacketListener conn = mc.getConnection();
            PlayerInfo info = conn != null ? conn.getPlayerInfo(player.getUUID()) : null;
            int ping = info != null ? Math.max(0, info.getLatency()) : -1;
            if (ping >= 0) {
                int pingColor = ping < 80 ? 0x34D399 : (ping < 160 ? 0xFBBF24 : 0xF87171);
                out.append(Component.literal(" [" + ping + "ms]")
                        .withStyle(style -> style.withColor(pingColor)));
            }
        }

        if (showHealth) {
            float absorption = Math.max(0.0f, player.getAbsorptionAmount());
            float hp = Math.max(0.0f, player.getHealth() + absorption);
            float maxHp = Math.max(1.0f, player.getMaxHealth());
            float ratio = hp / maxHp;

            int hpColor;
            if (absorption > 0.05f) {
                hpColor = 0xFACC15; // Gold when player has absorption hearts
            } else if (ratio > 0.65f) {
                hpColor = 0x34D399; // Healthy emerald
            } else if (ratio > 0.30f) {
                hpColor = 0xFBBF24; // Amber warning
            } else {
                hpColor = 0xF87171; // Critical red
            }

            String hpStr = Math.abs(hp - Math.round(hp)) < 0.05f
                    ? String.format(Locale.US, " ❤ %d", Math.round(hp))
                    : String.format(Locale.US, " ❤ %.1f", hp);
            out.append(Component.literal(hpStr).withStyle(style -> style.withColor(hpColor)));
        }

        return out;
    }

    private static boolean getBool(String name, boolean def) {
        if (module == null) return def;
        Setting<?> s = module.getSetting(name);
        return s != null && s.getValue() instanceof Boolean b ? b : def;
    }
}
