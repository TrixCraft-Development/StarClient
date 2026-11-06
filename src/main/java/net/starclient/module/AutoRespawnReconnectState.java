package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.gui.GuiRenderUtils;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.NumberSetting;

import java.util.Locale;

public final class AutoRespawnReconnectState {
    private static Module autoRespawnModule;
    private static Module autoReconnectModule;

    private static Screen trackedScreen;
    private static long screenOpenMillis;
    private static boolean reconnectCancelled;

    private static int reconnectNowBtnX;
    private static int reconnectNowBtnY;
    private static int reconnectNowBtnW;
    private static int reconnectNowBtnH;

    private static int cancelBtnX;
    private static int cancelBtnY;
    private static int cancelBtnW;
    private static int cancelBtnH;

    private AutoRespawnReconnectState() {
    }

    public static void setAutoRespawnModule(Module module) {
        autoRespawnModule = module;
    }

    public static void setAutoReconnectModule(Module module) {
        autoReconnectModule = module;
    }

    public static void onClientTick(Minecraft client) {
        Screen current = client.gui.screen();
        if (current != trackedScreen) {
            trackedScreen = current;
            screenOpenMillis = System.currentTimeMillis();
            reconnectCancelled = false;
        }

        if (current instanceof DeathScreen) {
            tickAutoRespawn(client);
        } else if (current instanceof DisconnectedScreen) {
            tickAutoReconnect(client);
        }
    }

    private static void tickAutoRespawn(Minecraft client) {
        if (autoRespawnModule == null || !autoRespawnModule.isEnabled() || client.player == null) {
            return;
        }
        if (client.level != null && client.level.getLevelData().isHardcore()) {
            return;
        }
        double delaySec = getNum(autoRespawnModule, "Respawn Delay", 0.0);
        double elapsedSec = (System.currentTimeMillis() - screenOpenMillis) / 1000.0;
        if (elapsedSec >= delaySec) {
            client.player.respawn();
            client.gui.setScreen(null);
        }
    }

    private static void tickAutoReconnect(Minecraft client) {
        if (autoReconnectModule == null || !autoReconnectModule.isEnabled() || reconnectCancelled) {
            return;
        }
        ServerData target = ServerSessionTracker.getLastServerData();
        if (target == null || target.ip.isBlank()) {
            return;
        }
        double delaySec = getNum(autoReconnectModule, "Reconnect Delay", 5.0);
        double elapsedSec = (System.currentTimeMillis() - screenOpenMillis) / 1000.0;
        if (elapsedSec >= delaySec) {
            triggerReconnect(client, target);
        }
    }

    private static void triggerReconnect(Minecraft client, ServerData target) {
        trackedScreen = null;
        Screen parent = new JoinMultiplayerScreen(new TitleScreen());
        ConnectScreen.startConnecting(parent, client, ServerAddress.parseString(target.ip), target, false, null);
    }

    public static void renderScreenOverlay(GuiGraphicsExtractor extractor, Screen screen, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (screen != trackedScreen) {
            trackedScreen = screen;
            screenOpenMillis = System.currentTimeMillis();
            reconnectCancelled = false;
        }

        if (screen instanceof DeathScreen) {
            renderDeathOverlay(extractor, client, screen.width);
        } else if (screen instanceof DisconnectedScreen) {
            renderDisconnectedOverlay(extractor, client, screen.width, mouseX, mouseY);
        }
    }

    private static void renderDeathOverlay(GuiGraphicsExtractor extractor, Minecraft client, int screenWidth) {
        if (autoRespawnModule == null || !autoRespawnModule.isEnabled() || client.player == null) {
            return;
        }
        if (client.level != null && client.level.getLevelData().isHardcore()) {
            return;
        }
        boolean showCountdown = !(autoRespawnModule.getSetting("Show Countdown") instanceof BooleanSetting bs) || bs.getValue();
        if (!showCountdown) {
            return;
        }
        double delaySec = getNum(autoRespawnModule, "Respawn Delay", 0.0);
        double remaining = Math.max(0.0, delaySec - (System.currentTimeMillis() - screenOpenMillis) / 1000.0);
        if (remaining <= 0.01) {
            return;
        }

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs.getMenuAccentColor();
        int bg = 0xE60F172A;

        String text = String.format(Locale.US, "⟳ Auto Respawning in %.1fs...", remaining);
        int boxW = Math.max(170, client.font.width(text) + 24);
        int boxH = 24;
        int boxX = (screenWidth - boxW) / 2;
        int boxY = 18;

        GuiRenderUtils.drawDropShadow(extractor, boxX, boxY, boxW, boxH, 6, 4, 90);
        GuiRenderUtils.fillRoundedRect(extractor, boxX, boxY, boxW, boxH, 6, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, boxX, boxY, boxW, boxH, 6, accent);
        GuiRenderUtils.drawCenteredText(extractor, client.font, text, boxX + boxW / 2, boxY + 6, 0xFFF8FAFC);

        if (delaySec > 0.05) {
            float progress = (float) Math.clamp(1.0 - (remaining / delaySec), 0.0, 1.0);
            int barW = boxW - 16;
            int barX = boxX + 8;
            int barY = boxY + boxH - 4;
            extractor.fill(barX, barY, barX + barW, barY + 2, 0x4464748B);
            extractor.fill(barX, barY, barX + Math.round(barW * progress), barY + 2, accent);
        }
    }

    private static void renderDisconnectedOverlay(GuiGraphicsExtractor extractor, Minecraft client, int screenWidth, int mouseX, int mouseY) {
        ServerData target = ServerSessionTracker.getLastServerData();
        if (target == null || target.ip.isBlank()) {
            return;
        }
        boolean autoEnabled = autoReconnectModule != null && autoReconnectModule.isEnabled();
        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs.getMenuAccentColor();
        int accentDark = cs.getMenuAccentDarkColor();
        int border = cs.getMenuBorderColor();
        int bg = 0xEE0F172A;

        double delaySec = autoEnabled ? getNum(autoReconnectModule, "Reconnect Delay", 5.0) : 5.0;
        double remaining = Math.max(0.0, delaySec - (System.currentTimeMillis() - screenOpenMillis) / 1000.0);

        String statusText;
        if (autoEnabled && !reconnectCancelled) {
            statusText = String.format(Locale.US, "⟳ Auto Reconnecting to %s in %.1fs", target.ip, remaining);
        } else {
            statusText = "Last Server: " + target.ip;
        }

        int cardW = 310;
        int cardH = 44;
        int cardX = (screenWidth - cardW) / 2;
        int cardY = 14;

        GuiRenderUtils.drawDropShadow(extractor, cardX, cardY, cardW, cardH, 7, 5, 100);
        GuiRenderUtils.fillRoundedRect(extractor, cardX, cardY, cardW, cardH, 7, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, cardX, cardY, cardW, cardH, 7, (autoEnabled && !reconnectCancelled) ? accent : border);

        GuiRenderUtils.drawCenteredText(extractor, client.font,
                GuiRenderUtils.truncateWithEllipsis(client.font, statusText, cardW - 16),
                cardX + cardW / 2, cardY + 6, 0xFFF8FAFC);

        // Action buttons: [Reconnect Now] and [Cancel]
        reconnectNowBtnW = (autoEnabled && !reconnectCancelled) ? 110 : 140;
        reconnectNowBtnH = 16;
        cancelBtnW = 74;
        cancelBtnH = 16;

        int totalBtnsW = (autoEnabled && !reconnectCancelled) ? (reconnectNowBtnW + 8 + cancelBtnW) : reconnectNowBtnW;
        reconnectNowBtnX = cardX + (cardW - totalBtnsW) / 2;
        reconnectNowBtnY = cardY + 22;

        boolean hoverNow = mouseX >= reconnectNowBtnX && mouseX <= reconnectNowBtnX + reconnectNowBtnW
                && mouseY >= reconnectNowBtnY && mouseY <= reconnectNowBtnY + reconnectNowBtnH;
        GuiRenderUtils.fillRoundedRect(extractor, reconnectNowBtnX, reconnectNowBtnY, reconnectNowBtnW, reconnectNowBtnH, 4, hoverNow ? accent : accentDark);
        GuiRenderUtils.drawRoundedOutline(extractor, reconnectNowBtnX, reconnectNowBtnY, reconnectNowBtnW, reconnectNowBtnH, 4, accent);
        GuiRenderUtils.drawCenteredText(extractor, client.font, "Reconnect Now",
                reconnectNowBtnX + reconnectNowBtnW / 2, reconnectNowBtnY + 4, 0xFFFFFFFF);

        if (autoEnabled && !reconnectCancelled) {
            cancelBtnX = reconnectNowBtnX + reconnectNowBtnW + 8;
            cancelBtnY = reconnectNowBtnY;
            boolean hoverCancel = mouseX >= cancelBtnX && mouseX <= cancelBtnX + cancelBtnW
                    && mouseY >= cancelBtnY && mouseY <= cancelBtnY + cancelBtnH;
            GuiRenderUtils.fillRoundedRect(extractor, cancelBtnX, cancelBtnY, cancelBtnW, cancelBtnH, 4, hoverCancel ? 0x44F87171 : 0x331E293B);
            GuiRenderUtils.drawRoundedOutline(extractor, cancelBtnX, cancelBtnY, cancelBtnW, cancelBtnH, 4, hoverCancel ? 0xFFF87171 : border);
            GuiRenderUtils.drawCenteredText(extractor, client.font, "Cancel",
                    cancelBtnX + cancelBtnW / 2, cancelBtnY + 4, hoverCancel ? 0xFFF87171 : 0xFFCBD5E1);
        }
    }

    public static boolean mouseClicked(Screen screen, MouseButtonEvent event) {
        if (!(screen instanceof DisconnectedScreen) || event.button() != 0) {
            return false;
        }
        ServerData target = ServerSessionTracker.getLastServerData();
        if (target == null || target.ip.isBlank()) {
            return false;
        }
        double mx = event.x();
        double my = event.y();
        if (mx >= reconnectNowBtnX && mx <= reconnectNowBtnX + reconnectNowBtnW
                && my >= reconnectNowBtnY && my <= reconnectNowBtnY + reconnectNowBtnH) {
            triggerReconnect(Minecraft.getInstance(), target);
            return true;
        }
        boolean autoEnabled = autoReconnectModule != null && autoReconnectModule.isEnabled();
        if (autoEnabled && !reconnectCancelled
                && mx >= cancelBtnX && mx <= cancelBtnX + cancelBtnW
                && my >= cancelBtnY && my <= cancelBtnY + cancelBtnH) {
            reconnectCancelled = true;
            return true;
        }
        return false;
    }

    private static double getNum(Module mod, String name, double fallback) {
        if (mod != null && mod.getSetting(name) instanceof NumberSetting ns) {
            return ns.getValue();
        }
        return fallback;
    }
}
