package net.starclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.sounds.SoundEvents;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Glassmorphic Quick Server Switcher accessible from the Pause Menu (ESC) and ClickGUI.
 * Lists saved servers with live pings, player counts, icons, MOTD previews,
 * search / direct-connect input, and instant 1-click server switching.
 */
public class ServerSwitcherScreen extends Screen {
    private static final int HEADER_H = 42;
    private static final int CARD_H = 48;
    private static final int CARD_GAP = 6;
    private static final AtomicInteger ICON_SEQ = new AtomicInteger(1);

    private static final class ServerEntry {
        final ServerData data;
        byte[] lastIconBytes;
        Identifier iconTexId;
        DynamicTexture iconTex;

        ServerEntry(ServerData data) {
            this.data = data;
        }
    }

    private final Screen parent;
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private final List<ServerEntry> allEntries = new ArrayList<>();
    private final List<ServerEntry> filteredEntries = new ArrayList<>();

    private EditBox searchOrIpBox;
    private String query = "";
    private double scroll = 0.0;
    private double targetScroll = 0.0;
    private long lastFrameNanos = System.nanoTime();
    private String statusMessage = "";
    private int statusColor = 0xFF94A3B8;

    public ServerSwitcherScreen(Screen parent) {
        super(Component.literal("Quick Server Switcher"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.lastFrameNanos = System.nanoTime();

        String prev = this.query;
        this.searchOrIpBox = new EditBox(this.font, 0, 0, 260, 14, Component.literal(""));
        this.searchOrIpBox.setMaxLength(128);
        this.searchOrIpBox.setBordered(false);
        this.searchOrIpBox.setTextColor(0xFFF8FAFC);
        this.searchOrIpBox.setHint(Component.literal("Filter saved servers or enter IP to direct join..."));
        this.searchOrIpBox.setValue(prev);
        this.searchOrIpBox.setResponder(v -> {
            this.query = v;
            applyFilter();
        });
        addWidget(this.searchOrIpBox);
        this.searchOrIpBox.setFocused(true);
        this.setFocused(this.searchOrIpBox);

        if (this.allEntries.isEmpty()) {
            reloadAndPingServers();
        } else {
            applyFilter();
        }
    }

    private void reloadAndPingServers() {
        this.pinger.removeAll();
        releaseIcons();
        this.allEntries.clear();
        this.filteredEntries.clear();
        this.scroll = 0.0;
        this.targetScroll = 0.0;

        Minecraft client = Minecraft.getInstance();
        ServerList serverList = new ServerList(client);
        serverList.load();

        EventLoopGroupHolder eventLoop = EventLoopGroupHolder.remote(client.options.useNativeTransport());
        for (int i = 0; i < serverList.size(); i++) {
            ServerData data = serverList.get(i);
            data.setState(ServerData.State.PINGING);
            data.ping = -1L;
            ServerEntry entry = new ServerEntry(data);
            this.allEntries.add(entry);

            try {
                this.pinger.pingServer(
                        data,
                        () -> {},
                        () -> {},
                        eventLoop
                );
            } catch (UnknownHostException e) {
                data.setState(ServerData.State.UNREACHABLE);
                data.motd = Component.literal("Can't resolve hostname");
            } catch (Throwable t) {
                data.setState(ServerData.State.UNREACHABLE);
            }
        }
        applyFilter();
    }

    private void applyFilter() {
        this.filteredEntries.clear();
        String q = this.query == null ? "" : this.query.trim().toLowerCase(Locale.ROOT);
        for (ServerEntry e : this.allEntries) {
            if (q.isEmpty()
                    || (e.data.name != null && e.data.name.toLowerCase(Locale.ROOT).contains(q))
                    || (e.data.ip != null && e.data.ip.toLowerCase(Locale.ROOT).contains(q))) {
                this.filteredEntries.add(e);
            }
        }
        this.targetScroll = 0.0;
        this.scroll = 0.0;
    }

    private void syncServerIcons() {
        Minecraft client = Minecraft.getInstance();
        for (ServerEntry entry : this.allEntries) {
            byte[] bytes = entry.data.getIconBytes();
            if (bytes != null && bytes.length > 0 && !Arrays.equals(bytes, entry.lastIconBytes)) {
                entry.lastIconBytes = Arrays.copyOf(bytes, bytes.length);
                releaseEntryIcon(entry, client);
                try {
                    NativeImage img = NativeImage.read(bytes);
                    Identifier id = Identifier.fromNamespaceAndPath(StarClient.MOD_ID,
                            "server_icon_" + ICON_SEQ.getAndIncrement());
                    DynamicTexture tex = new DynamicTexture(() -> "starclient_srv_icon", img);
                    client.getTextureManager().register(id, tex);
                    entry.iconTexId = id;
                    entry.iconTex = tex;
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void releaseEntryIcon(ServerEntry entry, Minecraft client) {
        if (entry.iconTexId != null) {
            client.getTextureManager().release(entry.iconTexId);
            entry.iconTexId = null;
        }
        if (entry.iconTex != null) {
            entry.iconTex.close();
            entry.iconTex = null;
        }
    }

    private void releaseIcons() {
        Minecraft client = Minecraft.getInstance();
        for (ServerEntry entry : this.allEntries) {
            releaseEntryIcon(entry, client);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.pinger.tick();
    }

    private int panelW() {
        return Math.clamp(this.width - 36, 460, 620);
    }

    private int panelH() {
        return Math.clamp(this.height - 32, 300, 440);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        syncServerIcons();

        long now = System.nanoTime();
        float dt = Math.clamp((now - lastFrameNanos) / 1_000_000_000.0F, 0.001F, 0.1F);
        lastFrameNanos = now;
        this.scroll += (this.targetScroll - this.scroll) * Math.min(1.0F, 16.0F * dt);
        if (Math.abs(this.targetScroll - this.scroll) < 0.25) {
            this.scroll = this.targetScroll;
        }

        Minecraft client = Minecraft.getInstance();
        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs.getMenuAccentColor();
        int accentDark = cs.getMenuAccentDarkColor();
        int bg = 0xF4000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
        int bgDark = cs.getMenuBackgroundDarkColor();
        int surface = cs.getMenuSurfaceColor();
        int surfaceHover = cs.getMenuSurfaceHoverColor();
        int border = cs.getMenuBorderColor();

        int pw = panelW();
        int ph = panelH();
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        extractor.fill(0, 0, this.width, this.height, 0x88040812);

        GuiRenderUtils.drawDropShadow(extractor, px, py, pw, ph, 8, 8, 145);
        GuiRenderUtils.fillRoundedRect(extractor, px, py, pw, ph, 8, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, px, py, pw, ph, 8, border);

        // Header
        GuiRenderUtils.fillRoundedRect(extractor, px, py, pw, HEADER_H, 8, surface);
        extractor.fill(px, py + HEADER_H - 1, px + pw, py + HEADER_H, border);

        GuiRenderUtils.drawText(extractor, this.font, "🌐 Quick Server Switcher", px + 14, py + 10, accent, true);

        ServerData currentSrv = client.getCurrentServer();
        String currentLabel;
        int currentColor;
        if (currentSrv != null && currentSrv.ip != null) {
            currentLabel = "● Connected: " + currentSrv.ip;
            currentColor = 0xFF34D399;
        } else if (client.isLocalServer()) {
            currentLabel = "● Singleplayer World";
            currentColor = 0xFFFBBF24;
        } else {
            currentLabel = "● Not in a server";
            currentColor = 0xFF94A3B8;
        }
        GuiRenderUtils.drawText(extractor, this.font, GuiRenderUtils.truncate(this.font, currentLabel, pw - 160),
                px + 14, py + 24, currentColor);

        int btnY = py + 11;
        int closeX = px + pw - 30;
        boolean closeHov = isHovered(mouseX, mouseY, closeX, btnY, 20, 20);
        GuiRenderUtils.fillRoundedRect(extractor, closeX, btnY, 20, 20, 5, closeHov ? 0x44EF4444 : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, closeX, btnY, 20, 20, 5, closeHov ? 0xFFEF4444 : border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + 10, btnY + 6, closeHov ? 0xFFF87171 : 0xFF94A3B8);

        int refW = 72;
        int refX = closeX - refW - 6;
        boolean refHov = isHovered(mouseX, mouseY, refX, btnY, refW, 20);
        GuiRenderUtils.fillRoundedRect(extractor, refX, btnY, refW, 20, 5, refHov ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, refX, btnY, refW, 20, 5, refHov ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "↻ Refresh", refX + refW / 2, btnY + 6,
                refHov ? accent : 0xFFE2E8F0);

        // Direct Connect / Search Row
        int rowX = px + 14;
        int rowY = py + HEADER_H + 10;
        int directW = 86;
        int boxW = pw - 28 - directW - 8;
        int boxH = 22;

        boolean boxFocused = this.searchOrIpBox != null && this.searchOrIpBox.isFocused();
        GuiRenderUtils.fillRoundedRect(extractor, rowX, rowY, boxW, boxH, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, rowX, rowY, boxW, boxH, 5, boxFocused ? accent : border);
        GuiRenderUtils.drawText(extractor, this.font, "🔍", rowX + 6, rowY + 7, boxFocused ? accent : 0xFF64748B);
        if (this.searchOrIpBox != null) {
            this.searchOrIpBox.setPosition(rowX + 18, rowY + 7);
            this.searchOrIpBox.setWidth(boxW - 24);
            this.searchOrIpBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        String trimmedQuery = this.query == null ? "" : this.query.trim();
        boolean canDirect = !trimmedQuery.isEmpty() && ServerAddress.isValidAddress(trimmedQuery);
        int dirX = rowX + boxW + 8;
        boolean dirHov = canDirect && isHovered(mouseX, mouseY, dirX, rowY, directW, boxH);
        int dirBg = canDirect ? (dirHov ? accentDark : accent) : bgDark;
        GuiRenderUtils.fillRoundedRect(extractor, dirX, rowY, directW, boxH, 5, dirBg);
        if (!canDirect) {
            GuiRenderUtils.drawRoundedOutline(extractor, dirX, rowY, directW, boxH, 5, border);
        }
        GuiRenderUtils.drawCenteredText(extractor, this.font, "▶ Direct Join", dirX + directW / 2, rowY + 7,
                canDirect ? 0xFFFFFFFF : 0xFF64748B);

        // Subheader
        int subY = rowY + boxH + 7;
        GuiRenderUtils.drawText(extractor, this.font, "Saved Servers (" + this.filteredEntries.size() + ")", rowX, subY, 0xFFCBD5E1);
        if (!this.statusMessage.isEmpty()) {
            int sw = this.font.width(this.statusMessage);
            GuiRenderUtils.drawText(extractor, this.font, this.statusMessage, px + pw - 14 - sw, subY, this.statusColor);
        }

        // Scrollable server list
        int listLeft = px + 14;
        int listRight = px + pw - 14;
        int listTop = subY + 13;
        int listBottom = py + ph - 12;
        int listW = listRight - listLeft;
        int listH = listBottom - listTop;

        int totalH = Math.max(0, this.filteredEntries.size() * (CARD_H + CARD_GAP) - CARD_GAP);
        int maxScroll = Math.max(0, totalH - listH);
        this.targetScroll = Math.clamp(this.targetScroll, 0.0, maxScroll);
        this.scroll = Math.clamp(this.scroll, 0.0, maxScroll);

        String currentIp = currentSrv != null && currentSrv.ip != null ? currentSrv.ip.toLowerCase(Locale.ROOT) : null;

        extractor.enableScissor(listLeft - 2, listTop, listRight + 2, listBottom);
        if (this.filteredEntries.isEmpty()) {
            String emptyMsg = this.allEntries.isEmpty()
                    ? "No saved servers yet — enter an IP above and click 'Direct Join'!"
                    : "No saved servers match \"" + trimmedQuery + "\" — press Enter to Direct Join!";
            GuiRenderUtils.drawCenteredText(extractor, this.font, emptyMsg,
                    px + pw / 2, listTop + listH / 2 - 4, 0xFF64748B);
        } else {
            int cy = (int) Math.round(listTop - this.scroll);
            for (ServerEntry entry : this.filteredEntries) {
                if (cy + CARD_H >= listTop && cy <= listBottom) {
                    ServerData data = entry.data;
                    boolean isCurrent = currentIp != null && data.ip != null
                            && data.ip.toLowerCase(Locale.ROOT).equals(currentIp);
                    boolean cardHov = mouseY >= listTop && mouseY <= listBottom
                            && isHovered(mouseX, mouseY, listLeft, cy, listW, CARD_H);

                    int cardBg = isCurrent
                            ? GuiRenderUtils.lerpColor(surface, 0xFF065F46, 0.22F)
                            : (cardHov ? surfaceHover : surface);
                    int cardBorder = isCurrent ? 0xFF34D399 : (cardHov ? accent : border);
                    GuiRenderUtils.fillRoundedRect(extractor, listLeft, cy, listW, CARD_H, 6, cardBg);
                    GuiRenderUtils.drawRoundedOutline(extractor, listLeft, cy, listW, CARD_H, 6, cardBorder);

                    // Server Icon (32x32)
                    int iconX = listLeft + 8;
                    int iconY = cy + 8;
                    GuiRenderUtils.fillRoundedRect(extractor, iconX, iconY, 32, 32, 5, bgDark);
                    if (entry.iconTexId != null) {
                        extractor.blit(entry.iconTexId, iconX, iconY, iconX + 32, iconY + 32, 0.0F, 1.0F, 0.0F, 1.0F);
                    } else {
                        GuiRenderUtils.drawCenteredText(extractor, this.font, "🌐", iconX + 16, iconY + 12, accent);
                    }
                    GuiRenderUtils.drawRoundedOutline(extractor, iconX, iconY, 32, 32, 5, border);

                    // Join / Reconnect button on right
                    int joinW = isCurrent ? 74 : 56;
                    int joinH = 22;
                    int joinX = listLeft + listW - joinW - 8;
                    int joinY = cy + (CARD_H - joinH) / 2;
                    boolean joinHov = cardHov && isHovered(mouseX, mouseY, joinX, joinY, joinW, joinH);
                    int joinBg = joinHov ? accentDark : (cardHov ? accent : bgDark);
                    GuiRenderUtils.fillRoundedRect(extractor, joinX, joinY, joinW, joinH, 5, joinBg);
                    if (!cardHov) {
                        GuiRenderUtils.drawRoundedOutline(extractor, joinX, joinY, joinW, joinH, 5, border);
                    }
                    GuiRenderUtils.drawCenteredText(extractor, this.font, isCurrent ? "Reconnect" : "Join →",
                            joinX + joinW / 2, joinY + 7, cardHov ? 0xFFFFFFFF : accent);

                    // Ping + Player count column (just left of Join button)
                    String pingStr;
                    int pingColor;
                    String playersStr = "";
                    ServerData.State st = data.state();
                    if (st == ServerData.State.PINGING || st == ServerData.State.INITIAL) {
                        pingStr = "Pinging...";
                        pingColor = 0xFF94A3B8;
                    } else if (st == ServerData.State.UNREACHABLE) {
                        pingStr = "Offline";
                        pingColor = 0xFFF87171;
                    } else {
                        long ms = Math.max(0L, data.ping);
                        pingStr = ms + " ms";
                        pingColor = ms < 80 ? 0xFF34D399 : (ms < 160 ? 0xFFFBBF24 : 0xFFF87171);
                        if (data.players != null) {
                            playersStr = data.players.online() + "/" + data.players.max();
                        } else if (data.status != null && !data.status.getString().isEmpty()) {
                            playersStr = stripFormatting(data.status.getString());
                        }
                    }

                    int statsRight = joinX - 10;
                    int pingW = this.font.width(pingStr);
                    int playersW = this.font.width(playersStr);
                    int statsBlockW = Math.max(pingW, playersW) + 6;
                    GuiRenderUtils.drawText(extractor, this.font, pingStr, statsRight - pingW, cy + 12, pingColor);
                    if (!playersStr.isEmpty()) {
                        GuiRenderUtils.drawText(extractor, this.font, playersStr, statsRight - playersW, cy + 25, 0xFF94A3B8);
                    }

                    // Server Name, IP, and MOTD
                    int textX = iconX + 40;
                    int textMaxW = Math.max(80, statsRight - statsBlockW - textX - 8);
                    String srvName = (data.name != null && !data.name.isBlank()) ? data.name : data.ip;
                    String nameClipped = GuiRenderUtils.truncate(this.font, srvName, textMaxW - (isCurrent ? 54 : 0));
                    GuiRenderUtils.drawText(extractor, this.font, nameClipped, textX, cy + 8, 0xFFFFFFFF, true);

                    if (isCurrent) {
                        int tagX = textX + this.font.width(nameClipped) + 6;
                        int tagW = 46;
                        GuiRenderUtils.fillRoundedRect(extractor, tagX, cy + 7, tagW, 11, 3, 0x3334D399);
                        GuiRenderUtils.drawCenteredText(extractor, this.font, "CURRENT", tagX + tagW / 2, cy + 9, 0xFF34D399);
                    }

                    String ipLine = data.ip != null ? data.ip : "";
                    GuiRenderUtils.drawText(extractor, this.font, GuiRenderUtils.truncate(this.font, ipLine, textMaxW),
                            textX, cy + 20, accent);

                    String motd = data.motd != null ? stripFormatting(data.motd.getString()).replace('\n', ' ').trim() : "";
                    if (motd.isEmpty()) {
                        motd = st == ServerData.State.PINGING ? "Querying server status..." : "Minecraft Multiplayer Server";
                    }
                    GuiRenderUtils.drawText(extractor, this.font, GuiRenderUtils.truncate(this.font, motd, textMaxW),
                            textX, cy + 32, 0xFF94A3B8);
                }
                cy += CARD_H + CARD_GAP;
            }
        }
        extractor.disableScissor();

        if (maxScroll > 0) {
            int sbX = px + pw - 7;
            int thumbH = Math.max(24, (int) ((double) listH * listH / Math.max(1, totalH)));
            int thumbY = listTop + (int) ((listH - thumbH) * (this.scroll / maxScroll));
            GuiRenderUtils.fillRoundedRect(extractor, sbX, listTop, 3, listH, 1, 0x33FFFFFF);
            GuiRenderUtils.fillRoundedRect(extractor, sbX, thumbY, 3, thumbH, 1, accent);
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = event.x();
        double my = event.y();

        int pw = panelW();
        int ph = panelH();
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        int btnY = py + 11;
        int closeX = px + pw - 30;
        if (isHovered(mx, my, closeX, btnY, 20, 20)) {
            playClick();
            onClose();
            return true;
        }

        int refW = 72;
        int refX = closeX - refW - 6;
        if (isHovered(mx, my, refX, btnY, refW, 20)) {
            playClick();
            reloadAndPingServers();
            this.statusMessage = "Refreshing pings...";
            this.statusColor = 0xFF94A3B8;
            return true;
        }

        int rowX = px + 14;
        int rowY = py + HEADER_H + 10;
        int directW = 86;
        int boxW = pw - 28 - directW - 8;
        int boxH = 22;

        if (this.searchOrIpBox != null && isHovered(mx, my, rowX, rowY, boxW, boxH)) {
            this.searchOrIpBox.setFocused(true);
            this.setFocused(this.searchOrIpBox);
            this.searchOrIpBox.mouseClicked(event, doubleClick);
            return true;
        }

        int dirX = rowX + boxW + 8;
        if (isHovered(mx, my, dirX, rowY, directW, boxH)) {
            tryDirectConnect();
            return true;
        }

        int listLeft = px + 14;
        int listRight = px + pw - 14;
        int listTop = rowY + boxH + 20;
        int listBottom = py + ph - 12;
        int listW = listRight - listLeft;

        if (mx >= listLeft && mx <= listRight && my >= listTop && my <= listBottom) {
            int cy = (int) Math.round(listTop - this.scroll);
            ServerData currentSrv = Minecraft.getInstance().getCurrentServer();
            String currentIp = currentSrv != null && currentSrv.ip != null ? currentSrv.ip.toLowerCase(Locale.ROOT) : null;

            for (ServerEntry entry : this.filteredEntries) {
                if (isHovered(mx, my, listLeft, cy, listW, CARD_H)) {
                    boolean isCurrent = currentIp != null && entry.data.ip != null
                            && entry.data.ip.toLowerCase(Locale.ROOT).equals(currentIp);
                    int joinW = isCurrent ? 74 : 56;
                    int joinH = 22;
                    int joinX = listLeft + listW - joinW - 8;
                    int joinY = cy + (CARD_H - joinH) / 2;
                    if (doubleClick || isHovered(mx, my, joinX, joinY, joinW, joinH)) {
                        playClick();
                        switchToServer(entry.data);
                        return true;
                    }
                    // Single click on card also connects smoothly!
                    playClick();
                    switchToServer(entry.data);
                    return true;
                }
                cy += CARD_H + CARD_GAP;
            }
        }

        if (this.searchOrIpBox != null) {
            this.searchOrIpBox.setFocused(false);
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void tryDirectConnect() {
        String ip = this.query == null ? "" : this.query.trim();
        if (ip.isEmpty() || !ServerAddress.isValidAddress(ip)) {
            this.statusMessage = "Enter a valid server address";
            this.statusColor = 0xFFF87171;
            return;
        }
        playClick();
        ServerData direct = new ServerData(ip, ip, ServerData.Type.OTHER);
        switchToServer(direct);
    }

    public static void switchToServer(ServerData target) {
        if (target == null || target.ip == null || target.ip.isBlank()) return;
        Minecraft client = Minecraft.getInstance();
        ServerData serverCopy = new ServerData(
                target.name != null && !target.name.isBlank() ? target.name : target.ip,
                target.ip,
                ServerData.Type.OTHER
        );
        serverCopy.copyFrom(target);

        boolean inWorld = client.level != null || client.getConnection() != null || client.getSingleplayerServer() != null;
        if (inWorld) {
            if (client.level != null) {
                client.level.disconnect(Component.literal("Switching server"));
            }
            if (client.isLocalServer()) {
                client.disconnectWithSavingScreen();
            } else {
                client.disconnectWithProgressScreen();
            }
        }

        ServerAddress address = ServerAddress.parseString(serverCopy.ip);
        ConnectScreen.startConnecting(new TitleScreen(), client, address, serverCopy, false, null);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.targetScroll -= verticalAmount * 30.0;
        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.filteredEntries.size() == 1 && !this.query.trim().contains(".")) {
                playClick();
                switchToServer(this.filteredEntries.get(0).data);
                return true;
            }
            tryDirectConnect();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public void removed() {
        this.pinger.removeAll();
        releaseIcons();
        super.removed();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String stripFormatting(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static boolean isHovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
