package net.modernclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.modernclient.ModernClient;
import net.modernclient.api.HypixelApi;
import net.modernclient.setting.ColorModeSetting;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class HypixelLevelHead {
    private static final String[] HYPIXEL_HOSTS = {"hypixel.net"};

    /** Nametag-style sizing: the badge is roughly this many pixels tall per block of distance. */
    private static final double SCALE_CONSTANT = 6.0;
    private static final double MIN_SCALE = 0.3;
    private static final double MAX_SCALE = 1.4;
    private static final float SCALE_STEP = 20.0f;
    private static final int BACKGROUND_PADDING = 2;
    private static final int BACKGROUND_COLOR = 0x80000000;

    public static final Map<String, Integer> LEVEL_COLORS = ColorModeSetting.ofPairs(
            "Gold", "FFFACC15",
            "Cyan", "FF22D3EE",
            "Lime", "FFA3E635",
            "Green", "FF34D399",
            "White", "FFF1F5F9",
            "Orange", "FFFB923C",
            "Red", "FFF87171",
            "Purple", "FFA78BFA",
            "Pink", "FFF472B6");

    private HypixelLevelHead() {
    }

    public static boolean isHypixel(Minecraft client) {
        ServerData server = client.getCurrentServer();
        if (server == null || server.ip == null) {
            return false;
        }
        String ip = server.ip.toLowerCase(Locale.ROOT);
        for (String host : HYPIXEL_HOSTS) {
            if (ip.equals(host) || ip.endsWith("." + host)) {
                return true;
            }
        }
        return ip.contains("hypixel");
    }

    /** Short reason the module cannot work, or null when it is running normally. */
    public static String status(Minecraft client, boolean serverCheck) {
        if (!HypixelApi.isConfigured()) {
            return "Hypixel Level Head: set your API key in General Settings";
        }
        if (client.level == null || client.player == null) {
            return null;
        }
        if (serverCheck && !isHypixel(client)) {
            ServerData server = client.getCurrentServer();
            String host = server == null || server.ip == null ? "unknown" : server.ip;
            return "Hypixel Level Head: not on Hypixel (" + host + ")";
        }
        return null;
    }

    /** Queues lookups for every nearby player and prunes the cache for everyone else. */
    public static void request(Minecraft client, double range, boolean serverCheck, long ttlMillis) {
        HypixelApi.invalidateIfKeyChanged();

        if (client.level == null || client.player == null || !HypixelApi.isConfigured()) {
            return;
        }
        if (serverCheck && !isHypixel(client)) {
            return;
        }

        List<UUID> nearby = new ArrayList<>();
        for (Player player : playersInRange(client, range, true)) {
            nearby.add(player.getUUID());
            HypixelApi.request(player.getUUID(), ttlMillis);
        }
        HypixelApi.retainOnly(nearby);
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, String mode, boolean showSelf,
                              boolean showPrefix, boolean serverCheck, double range, int yOffset, int color,
                              boolean showBackground, boolean distanceScale) {
        if (client.level == null || client.player == null) {
            return;
        }
        if (serverCheck && !isHypixel(client)) {
            return;
        }

        GameRenderer gameRenderer = client.gameRenderer;
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();
        boolean shadow = ModernClient.getInstance().getClientSettings().isHudTextShadow();
        Vec3 cameraPos = gameRenderer.mainCamera().position();

        for (Player player : playersInRange(client, range, showSelf)) {
            // The badge is always drawn so the module is visibly active. It starts at 0 and the
            // number is replaced as soon as the background lookup for that player comes back.
            HypixelApi.Stats stats = HypixelApi.getStats(player.getUUID());
            double level = stats == null ? 0 : Math.max(0, stats.level(mode));

            Vec3 head = EspRenderUtil.projectToScreen(gameRenderer,
                    new Vec3(player.getX(), player.getY() + player.getBbHeight(), player.getZ()),
                    screenWidth, screenHeight);
            if (head == null) {
                continue;
            }

            String label = showPrefix ? prefixFor(mode) + formatLevel(level) : formatLevel(level);
            int width = client.font.width(label);
            int height = client.font.lineHeight;

            // A fixed size stays rock steady while walking. Distance scaling is quantised into steps
            // so the badge snaps between sizes instead of continuously resizing and shimmering.
            float scale = 1.0f;
            if (distanceScale) {
                Vec3 playerPos = player.position();
                double distance = Math.max(1.0, Math.sqrt(playerPos.distanceToSqr(cameraPos)));
                scale = (float) Math.clamp(SCALE_CONSTANT / distance, MIN_SCALE, MAX_SCALE);
                scale = Math.round(scale * SCALE_STEP) / SCALE_STEP;
            }

            // Whole pixels keep the glyphs from shimmering on the sub-pixel projection.
            int centerX = (int) Math.round(head.x);
            int topY = (int) Math.round(head.y + yOffset - height * scale);

            Matrix3x2fStack pose = extractor.pose();
            pose.pushMatrix();
            pose.translate(centerX, topY);
            pose.scale(scale, scale);
            if (showBackground) {
                extractor.fill(-width / 2 - BACKGROUND_PADDING, -BACKGROUND_PADDING,
                        width / 2 + BACKGROUND_PADDING, height + BACKGROUND_PADDING, BACKGROUND_COLOR);
            }
            extractor.text(client.font, label, -width / 2, 0, color, shadow);
            pose.popMatrix();
        }
    }

    private static List<Player> playersInRange(Minecraft client, double range, boolean includeSelf) {
        List<Player> result = new ArrayList<>();
        assert client.level != null;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Player player) || !player.isAlive()) {
                continue;
            }
            if (player == client.player) {
                if (includeSelf) {
                    result.add(player);
                }
                continue;
            }
            assert client.player != null;
            if (player.distanceTo(client.player) <= range) {
                result.add(player);
            }
        }
        return result;
    }

    private static String prefixFor(String mode) {
        return switch (mode) {
            case "Bedwars" -> "Bedwars: ";
            case "Skywars" -> "Skywars: ";
            default -> "Network: ";
        };
    }

    private static String formatLevel(double level) {
        if (Math.abs(level - Math.rint(level)) < 0.05) {
            return String.valueOf((long) Math.rint(level));
        }
        return String.format(Locale.ROOT, "%.1f", level);
    }
}
