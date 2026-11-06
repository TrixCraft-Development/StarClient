package net.starclient.gui;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.module.ServerSessionTracker;
import net.starclient.setting.BooleanSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ServerInfoRenderer {

    private ServerInfoRenderer() {
    }

    public static List<String> buildLines(Minecraft client, Module module) {
        boolean showIp = module == null || !(module.getSetting("Show IP / World") instanceof BooleanSetting bs) || bs.getValue();
        boolean showBrand = module == null || !(module.getSetting("Show Server Brand") instanceof BooleanSetting bs) || bs.getValue();
        boolean showVersion = module == null || !(module.getSetting("Show Version") instanceof BooleanSetting bs) || bs.getValue();
        boolean showTps = module == null || !(module.getSetting("Show TPS") instanceof BooleanSetting bs) || bs.getValue();
        boolean showSession = module == null || !(module.getSetting("Show Session Time") instanceof BooleanSetting bs) || bs.getValue();
        boolean compact = module != null && module.getSetting("Compact Mode") instanceof BooleanSetting bs && bs.getValue();

        String ipOrWorld = "Singleplayer";
        String version = SharedConstants.getCurrentVersion().name();
        if (client != null) {
            if (!client.isLocalServer() && client.getCurrentServer() != null) {
                ServerData sd = client.getCurrentServer();
                ipOrWorld = !sd.ip.isBlank() ? sd.ip : sd.name;
                if (!sd.version.getString().isBlank()) {
                    version = sd.version.getString();
                }
            } else if (client.isLocalServer() && client.getSingleplayerServer() != null) {
                ipOrWorld = client.getSingleplayerServer().getWorldData().getLevelName();
            } else if (client.player == null) {
                ipOrWorld = "mc.hypixel.net";
            }
        }

        String brand = "Vanilla";
        if (client != null && client.player != null) {
            String sb = client.player.connection.serverBrand();
            if (sb != null && !sb.isBlank()) {
                brand = sb;
            }
        } else {
            brand = "Paper";
        }

        double tps = ServerSessionTracker.getEstimatedTps(client);
        String tpsStr = String.format(Locale.US, "%.1f TPS", tps);
        String sessionStr = ServerSessionTracker.getFormattedSessionDuration();

        List<String> parts = new ArrayList<>(5);
        if (showIp) parts.add(compact ? ipOrWorld : ("Server: " + ipOrWorld));
        if (showBrand) parts.add(compact ? brand : ("Brand: " + brand));
        if (showVersion) parts.add(compact ? ("v" + version) : ("Version: " + version));
        if (showTps) parts.add(compact ? tpsStr : ("TPS: " + String.format(Locale.US, "%.1f", tps)));
        if (showSession) parts.add(compact ? sessionStr : ("Session: " + sessionStr));

        if (parts.isEmpty()) {
            parts.add("Server: " + ipOrWorld);
        }

        if (compact) {
            return List.of(String.join(" • ", parts));
        }
        return parts;
    }

    public static int getWidth(Font font, Minecraft client, Module module) {
        List<String> lines = buildLines(client, module);
        int maxW = HudLayout.STAT_WIDTH;
        if (font != null) {
            for (String line : lines) {
                maxW = Math.max(maxW, font.width(line) + 14);
            }
        }
        return maxW;
    }

    public static int getHeight(Minecraft client, Module module) {
        List<String> lines = buildLines(client, module);
        if (lines.size() <= 1) {
            return HudLayout.ELEMENT_HEIGHT;
        }
        return 8 + lines.size() * 11;
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module module, HudLayout layout, int screenW, int screenH) {
        if (module == null || !module.isEnabled()) {
            return;
        }
        List<String> lines = buildLines(client, module);
        int w = getWidth(client.font, client, module);
        int h = getHeight(client, module);
        int x = layout.effectiveX(HudLayout.SERVER_INFO, screenW, w);
        int y = layout.effectiveY(HudLayout.SERVER_INFO, screenH);

        if (!module.isTransparentBackground()) {
            int boxBg = (module.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
            GuiRenderUtils.fillRoundedRect(extractor, x, y, w, h, 4, boxBg);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, w, h, 4, 0x28FFFFFF);
        }

        double tps = ServerSessionTracker.getEstimatedTps(client);
        int textColor = module.getTextColor();
        boolean textShadow = StarClient.getInstance().getClientSettings().isHudTextShadow();
        int ty = y + 5;
        for (String line : lines) {
            int lineCol = textColor;
            if (line.startsWith("TPS:")) {
                lineCol = tps >= 18.0 ? textColor : (tps >= 14.0 ? 0xFFFBBF24 : 0xFFF87171);
            }
            extractor.text(client.font, line, x + 6, ty, lineCol, textShadow);
            ty += 11;
        }
    }
}
