package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.friend.FriendEntry;
import net.starclient.friend.FriendManager;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Modern glassmorphic Friends Manager screen:
 * - Real-time Minecraft player skin face avatars (with hat layer)
 * - Custom per-friend notes / role tags (e.g. "2v2 Partner", "SMP Builder")
 * - Customizable friend badges (♥, ★, 🛡, ✦, [FRIEND])
 * - One-click add from online tab list
 * - Global client-wide persistence
 */
public class FriendsScreen extends Screen {
    private static final int PANEL_WIDTH = 460;
    private static final int PANEL_HEIGHT = 350;
    private static final int HEADER_HEIGHT = 40;
    private static final int ROW_HEIGHT = 28;

    private final Screen parent;
    private EditBox usernameBox;
    private EditBox noteBox;
    private String statusMessage = "";
    private int statusColor = 0xFF94A3B8;
    private double listScroll = 0.0;

    public FriendsScreen(Screen parent) {
        super(Component.literal("Friends Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int px = (this.width - PANEL_WIDTH) / 2;
        int py = (this.height - PANEL_HEIGHT) / 2;

        String prevUser = this.usernameBox != null ? this.usernameBox.getValue() : "";
        String prevNote = this.noteBox != null ? this.noteBox.getValue() : "";

        int boxY = py + 48;
        int userW = 145;
        int noteW = 180;

        this.usernameBox = new EditBox(this.font, px + 14 + 6, boxY + 5, userW - 12, 12, Component.literal(""));
        this.usernameBox.setMaxLength(32);
        this.usernameBox.setBordered(false);
        this.usernameBox.setTextColor(0xFFF8FAFC);
        this.usernameBox.setHint(Component.literal("Player username..."));
        this.usernameBox.setValue(prevUser);
        addWidget(this.usernameBox);

        this.noteBox = new EditBox(this.font, px + 14 + userW + 6 + 6, boxY + 5, noteW - 12, 12, Component.literal(""));
        this.noteBox.setMaxLength(32);
        this.noteBox.setBordered(false);
        this.noteBox.setTextColor(0xFFF8FAFC);
        this.noteBox.setHint(Component.literal("Optional tag/note (e.g. 2v2)..."));
        this.noteBox.setValue(prevNote);
        addWidget(this.noteBox);

        this.usernameBox.setFocused(true);
        this.setFocused(this.usernameBox);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs.getMenuAccentColor();
        int accentDark = cs.getMenuAccentDarkColor();
        int friendCol = cs.getFriendColor();
        int friendRgb = cs.getFriendColor() & 0x00FFFFFF;
        String badge = FriendManager.getFriendBadgeSymbol();

        int bg = 0xF5000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
        int bgDark = cs.getMenuBackgroundDarkColor();
        int surface = cs.getMenuSurfaceColor();
        int surfaceHover = cs.getMenuSurfaceHoverColor();
        int border = cs.getMenuBorderColor();

        int px = (this.width - PANEL_WIDTH) / 2;
        int py = (this.height - PANEL_HEIGHT) / 2;

        GuiRenderUtils.drawDropShadow(extractor, px, py, PANEL_WIDTH, PANEL_HEIGHT, 8, 8, 140);
        GuiRenderUtils.fillRoundedRect(extractor, px, py, PANEL_WIDTH, PANEL_HEIGHT, 8, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, px, py, PANEL_WIDTH, PANEL_HEIGHT, 8, border);

        // Header
        GuiRenderUtils.fillRoundedRect(extractor, px, py, PANEL_WIDTH, HEADER_HEIGHT, 8, surface);
        extractor.fill(px, py + HEADER_HEIGHT - 1, px + PANEL_WIDTH, py + HEADER_HEIGHT, border);

        GuiRenderUtils.drawText(extractor, this.font, badge + " Friends Manager", px + 14, py + 10, friendCol, true);
        GuiRenderUtils.drawText(extractor, this.font, "Manage global friends, personal role tags & face avatars", px + 14, py + 23, 0xFF94A3B8);

        // Close Button
        int closeX = px + PANEL_WIDTH - 28;
        int closeY = py + 11;
        boolean closeHov = isHovered(mouseX, mouseY, closeX, closeY, 18, 18);
        GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, 18, 18, 4, closeHov ? 0x44F87171 : bgDark);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + 9, closeY + 5, closeHov ? 0xFFF87171 : 0xFF94A3B8);

        // Clear All Button
        List<FriendEntry> friends = FriendManager.getFriendEntries();
        if (!friends.isEmpty()) {
            int clearW = 58;
            int clearX = closeX - clearW - 8;
            boolean clearHov = isHovered(mouseX, mouseY, clearX, closeY, clearW, 18);
            GuiRenderUtils.fillRoundedRect(extractor, clearX, closeY, clearW, 18, 4, clearHov ? 0x44EF4444 : bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, clearX, closeY, clearW, 18, 4, clearHov ? 0xFFEF4444 : border);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Clear All", clearX + clearW / 2, closeY + 5,
                    clearHov ? 0xFFF87171 : 0xFF94A3B8);
        }

        // Input Row
        int boxY = py + 48;
        int boxH = 20;
        int userX = px + 14;
        int userW = 145;
        int noteX = userX + userW + 6;
        int noteW = 180;
        int addBtnX = noteX + noteW + 6;
        int addBtnW = PANEL_WIDTH - (addBtnX - px) - 14;

        // Username EditBox
        GuiRenderUtils.fillRoundedRect(extractor, userX, boxY, userW, boxH, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, userX, boxY, userW, boxH, 5,
                this.usernameBox != null && this.usernameBox.isFocused() ? accent : border);
        if (this.usernameBox != null) {
            this.usernameBox.setPosition(userX + 6, boxY + 6);
            this.usernameBox.setWidth(userW - 12);
            this.usernameBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        // Note EditBox
        GuiRenderUtils.fillRoundedRect(extractor, noteX, boxY, noteW, boxH, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, noteX, boxY, noteW, boxH, 5,
                this.noteBox != null && this.noteBox.isFocused() ? accent : border);
        if (this.noteBox != null) {
            this.noteBox.setPosition(noteX + 6, boxY + 6);
            this.noteBox.setWidth(noteW - 12);
            this.noteBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        // Add / Update Button
        String curInputName = this.usernameBox != null ? this.usernameBox.getValue().trim() : "";
        boolean isExisting = !curInputName.isEmpty() && FriendManager.isFriend(curInputName);
        String btnLabel = isExisting ? "✔ Update" : "+ Add";
        int btnBaseCol = isExisting ? (0xFF000000 | friendRgb) : accent;
        int btnHoverCol = isExisting ? ((friendCol & 0x00FFFFFF) | 0xDD000000) : accentDark;

        boolean addHov = isHovered(mouseX, mouseY, addBtnX, boxY, addBtnW, boxH);
        GuiRenderUtils.fillRoundedRect(extractor, addBtnX, boxY, addBtnW, boxH, 5, addHov ? btnHoverCol : btnBaseCol);
        GuiRenderUtils.drawCenteredText(extractor, this.font, btnLabel, addBtnX + addBtnW / 2, boxY + 6, 0xFFFFFFFF);

        // Quick-add online players row (when in a server or multiplayer world)
        List<String> onlineSuggestions = collectOnlineCandidates();
        int subY = boxY + 26;
        if (!onlineSuggestions.isEmpty()) {
            GuiRenderUtils.drawText(extractor, this.font, "Online:", px + 14, subY + 3, 0xFF94A3B8);
            int chipX = px + 54;
            for (String onlineName : onlineSuggestions) {
                int chipW = this.font.width("+ " + onlineName) + 10;
                if (chipX + chipW > px + PANEL_WIDTH - 14) break;
                boolean chipHov = isHovered(mouseX, mouseY, chipX, subY, chipW, 14);
                GuiRenderUtils.fillRoundedRect(extractor, chipX, subY, chipW, 14, 4, chipHov ? accentDark : bgDark);
                GuiRenderUtils.drawRoundedOutline(extractor, chipX, subY, chipW, 14, 4, chipHov ? accent : border);
                GuiRenderUtils.drawCenteredText(extractor, this.font, "+ " + onlineName, chipX + chipW / 2, subY + 3,
                        chipHov ? 0xFFFFFFFF : 0xFFCBD5E1);
                chipX += chipW + 4;
            }
            subY += 20;
        }

        // Status / Count Subheader
        String countLabel = "Saved Friends (" + friends.size() + ")";
        GuiRenderUtils.drawText(extractor, this.font, countLabel, px + 14, subY, 0xFFE2E8F0);
        if (!statusMessage.isEmpty()) {
            int sw = this.font.width(statusMessage);
            GuiRenderUtils.drawText(extractor, this.font, statusMessage, px + PANEL_WIDTH - 14 - sw, subY, statusColor);
        }

        // Friends List
        int listTop = subY + 14;
        int listBottom = py + PANEL_HEIGHT - 12;
        int maxScroll = Math.max(0, friends.size() * (ROW_HEIGHT + 4) - (listBottom - listTop));
        this.listScroll = Math.clamp(this.listScroll, 0.0, maxScroll);

        extractor.enableScissor(px + 10, listTop, px + PANEL_WIDTH - 10, listBottom);
        if (friends.isEmpty()) {
            GuiRenderUtils.drawCenteredText(extractor, this.font,
                    "No friends added yet — enter a username above or middle-click players in-game!",
                    px + PANEL_WIDTH / 2, listTop + (listBottom - listTop) / 2 - 4, 0xFF64748B);
        } else {
            int ry = (int) Math.round(listTop - listScroll);
            int rx = px + 14;
            int rw = PANEL_WIDTH - 28;
            Minecraft mc = Minecraft.getInstance();

            for (int i = 0; i < friends.size(); i++) {
                FriendEntry friend = friends.get(i);
                if (ry + ROW_HEIGHT >= listTop && ry <= listBottom) {
                    boolean rowHov = isHovered(mouseX, mouseY, rx, ry, rw, ROW_HEIGHT);
                    GuiRenderUtils.fillRoundedRect(extractor, rx, ry, rw, ROW_HEIGHT, 5, rowHov ? surfaceHover : surface);
                    GuiRenderUtils.drawRoundedOutline(extractor, rx, ry, rw, ROW_HEIGHT, 5, border);

                    // 1. Face avatar
                    int avatarX = rx + 6;
                    int avatarY = ry + 5;
                    int avatarSize = 18;
                    boolean isOnline = isPlayerOnline(friend.getName());

                    PlayerInfo onlineInfo = (mc.player != null && mc.player.connection != null)
                            ? mc.player.connection.getPlayerInfoIgnoreCase(friend.getName())
                            : null;

                    if (onlineInfo != null) {
                        PlayerFaceExtractor.extractRenderState(extractor, onlineInfo.getSkin(), avatarX, avatarY, avatarSize);
                    } else {
                        ResolvableProfile rp = friend.getResolvableProfile();
                        if (rp != null) {
                            PlayerFaceExtractor.extractRenderState(extractor, rp, avatarX, avatarY, avatarSize);
                        }
                    }
                    GuiRenderUtils.drawRoundedOutline(extractor, avatarX - 1, avatarY - 1, avatarSize + 2, avatarSize + 2, 3,
                            isOnline ? ((0x99 << 24) | friendRgb) : 0x33FFFFFF);

                    // 2. Friend Badge & Username
                    int textX = avatarX + avatarSize + 6;
                    GuiRenderUtils.drawText(extractor, this.font, badge, textX, ry + 10, friendCol);
                    textX += this.font.width(badge) + 4;

                    String nameStr = GuiRenderUtils.truncate(this.font, friend.getName(), 115);
                    GuiRenderUtils.drawText(extractor, this.font, nameStr, textX, ry + 10, 0xFFF8FAFC);
                    textX += this.font.width(nameStr) + 6;

                    // 3. Per-Friend Note / Tag pill
                    String note = friend.getNote();
                    if (!note.isEmpty()) {
                        String noteClipped = GuiRenderUtils.truncate(this.font, note, 120);
                        int tagW = this.font.width(noteClipped) + 8;
                        int tagH = 13;
                        GuiRenderUtils.fillRoundedRect(extractor, textX, ry + 7, tagW, tagH, 3, (0x30 << 24) | friendRgb);
                        GuiRenderUtils.drawRoundedOutline(extractor, textX, ry + 7, tagW, tagH, 3, (0x77 << 24) | friendRgb);
                        GuiRenderUtils.drawCenteredText(extractor, this.font, noteClipped, textX + tagW / 2, ry + 10, friendCol);
                    }

                    // 4. Delete button
                    int delX = rx + rw - 22;
                    int delY = ry + 6;
                    boolean delHov = isHovered(mouseX, mouseY, delX, delY, 16, 16);
                    GuiRenderUtils.fillRoundedRect(extractor, delX, delY, 16, 16, 4, delHov ? 0x44EF4444 : bgDark);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", delX + 8, delY + 4,
                            delHov ? 0xFFF87171 : 0xFF94A3B8);

                    // 5. Edit note button
                    int editX = delX - 22;
                    int editY = ry + 6;
                    boolean editHov = isHovered(mouseX, mouseY, editX, editY, 16, 16);
                    GuiRenderUtils.fillRoundedRect(extractor, editX, editY, 16, 16, 4, editHov ? 0x4438BDF8 : bgDark);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, "✏", editX + 8, editY + 4,
                            editHov ? 0xFF38BDF8 : 0xFF94A3B8);

                    // 6. Online indicator
                    String statusTag = isOnline ? "● Online" : "○ Offline";
                    int tagCol = isOnline ? 0xFF34D399 : 0xFF64748B;
                    int statusX = editX - this.font.width(statusTag) - 8;
                    GuiRenderUtils.drawText(extractor, this.font, statusTag, statusX, ry + 10, tagCol);
                }
                ry += ROW_HEIGHT + 4;
            }
        }
        extractor.disableScissor();
    }

    private List<String> collectOnlineCandidates() {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return out;
        String selfName = mc.player.getGameProfile().name();
        for (PlayerInfo info : mc.player.connection.getListedOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name == null || name.isBlank()) continue;
            if (name.equalsIgnoreCase(selfName)) continue;
            if (FriendManager.isFriend(name)) continue;
            out.add(name);
            if (out.size() >= 5) break;
        }
        return out;
    }

    private static boolean isPlayerOnline(String username) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return false;
        for (PlayerInfo info : mc.player.connection.getListedOnlinePlayers()) {
            if (username.equalsIgnoreCase(info.getProfile().name())) {
                return true;
            }
        }
        return false;
    }

    private void tryAddOrUpdateFriend() {
        if (this.usernameBox == null) return;
        String inputName = this.usernameBox.getValue().trim();
        String inputNote = this.noteBox != null ? this.noteBox.getValue().trim() : "";
        if (inputName.isEmpty()) {
            this.statusMessage = "Enter a username first";
            this.statusColor = 0xFFF87171;
            return;
        }
        boolean wasFriend = FriendManager.isFriend(inputName);
        if (FriendManager.addFriend(inputName, inputNote)) {
            this.usernameBox.setValue("");
            if (this.noteBox != null) this.noteBox.setValue("");
            this.statusMessage = wasFriend ? ("Updated note for '" + inputName + "'") : ("Added '" + inputName + "'");
            this.statusColor = 0xFF34D399;
        } else {
            this.statusMessage = "Already a friend";
            this.statusColor = 0xFFFBBF24;
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int px = (this.width - PANEL_WIDTH) / 2;
        int py = (this.height - PANEL_HEIGHT) / 2;

        int closeX = px + PANEL_WIDTH - 28;
        int closeY = py + 11;
        if (isHovered(mx, my, closeX, closeY, 18, 18)) {
            onClose();
            return true;
        }

        List<FriendEntry> friends = FriendManager.getFriendEntries();
        if (!friends.isEmpty()) {
            int clearW = 58;
            int clearX = closeX - clearW - 8;
            if (isHovered(mx, my, clearX, closeY, clearW, 18)) {
                FriendManager.clearFriends();
                this.statusMessage = "Cleared all friends";
                this.statusColor = 0xFF94A3B8;
                return true;
            }
        }

        int boxY = py + 48;
        int boxH = 20;
        int userX = px + 14;
        int userW = 145;
        int noteX = userX + userW + 6;
        int noteW = 180;
        int addBtnX = noteX + noteW + 6;
        int addBtnW = PANEL_WIDTH - (addBtnX - px) - 14;

        if (this.usernameBox != null && isHovered(mx, my, userX, boxY, userW, boxH)) {
            this.usernameBox.setFocused(true);
            if (this.noteBox != null) this.noteBox.setFocused(false);
            this.setFocused(this.usernameBox);
            this.usernameBox.mouseClicked(event, doubleClick);
            return true;
        }

        if (this.noteBox != null && isHovered(mx, my, noteX, boxY, noteW, boxH)) {
            this.noteBox.setFocused(true);
            if (this.usernameBox != null) this.usernameBox.setFocused(false);
            this.setFocused(this.noteBox);
            this.noteBox.mouseClicked(event, doubleClick);
            return true;
        }

        if (isHovered(mx, my, addBtnX, boxY, addBtnW, boxH)) {
            tryAddOrUpdateFriend();
            return true;
        }

        // Quick add chips
        List<String> onlineSuggestions = collectOnlineCandidates();
        int subY = boxY + 26;
        if (!onlineSuggestions.isEmpty()) {
            int chipX = px + 54;
            for (String onlineName : onlineSuggestions) {
                int chipW = this.font.width("+ " + onlineName) + 10;
                if (chipX + chipW > px + PANEL_WIDTH - 14) break;
                if (isHovered(mx, my, chipX, subY, chipW, 14)) {
                    if (this.usernameBox != null) {
                        this.usernameBox.setValue(onlineName);
                    }
                    if (this.noteBox != null) {
                        this.noteBox.setFocused(true);
                        this.setFocused(this.noteBox);
                    }
                    this.statusMessage = "Adding '" + onlineName + "' — add optional tag or click + Add";
                    this.statusColor = 0xFF38BDF8;
                    return true;
                }
                chipX += chipW + 4;
            }
            subY += 20;
        }

        int listTop = subY + 14;
        int listBottom = py + PANEL_HEIGHT - 12;
        if (my >= listTop && my <= listBottom) {
            int ry = (int) Math.round(listTop - listScroll);
            int rx = px + 14;
            int rw = PANEL_WIDTH - 28;
            for (int i = 0; i < friends.size(); i++) {
                FriendEntry friend = friends.get(i);
                int delX = rx + rw - 22;
                int delY = ry + 6;
                int editX = delX - 22;
                int editY = ry + 6;

                // Delete
                if (isHovered(mx, my, delX, delY, 16, 16)) {
                    String removedName = friend.getName();
                    FriendManager.removeFriendAt(i);
                    this.statusMessage = "Removed '" + removedName + "'";
                    this.statusColor = 0xFF94A3B8;
                    return true;
                }

                // Edit Note
                if (isHovered(mx, my, editX, editY, 16, 16)) {
                    if (this.usernameBox != null) this.usernameBox.setValue(friend.getName());
                    if (this.noteBox != null) {
                        this.noteBox.setValue(friend.getNote());
                        this.noteBox.setFocused(true);
                        this.setFocused(this.noteBox);
                    }
                    this.statusMessage = "Editing tag for '" + friend.getName() + "'";
                    this.statusColor = 0xFF38BDF8;
                    return true;
                }

                // Clicking row loads into inputs
                if (isHovered(mx, my, rx, ry, rw, ROW_HEIGHT)) {
                    if (this.usernameBox != null) this.usernameBox.setValue(friend.getName());
                    if (this.noteBox != null) {
                        this.noteBox.setValue(friend.getNote());
                        this.noteBox.setFocused(true);
                        this.setFocused(this.noteBox);
                    }
                    this.statusMessage = "Selected '" + friend.getName() + "'";
                    this.statusColor = 0xFF38BDF8;
                    return true;
                }

                ry += ROW_HEIGHT + 4;
            }
        }

        if (this.usernameBox != null) this.usernameBox.setFocused(false);
        if (this.noteBox != null) this.noteBox.setFocused(false);
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.listScroll -= verticalAmount * 20.0;
        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            tryAddOrUpdateFriend();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            if (this.usernameBox != null && this.usernameBox.isFocused()) {
                this.usernameBox.setFocused(false);
                if (this.noteBox != null) {
                    this.noteBox.setFocused(true);
                    this.setFocused(this.noteBox);
                }
                return true;
            } else if (this.noteBox != null && this.noteBox.isFocused()) {
                this.noteBox.setFocused(false);
                if (this.usernameBox != null) {
                    this.usernameBox.setFocused(true);
                    this.setFocused(this.usernameBox);
                }
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        FriendManager.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean isHovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
