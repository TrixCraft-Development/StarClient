package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.mixin.ChatComponentAccessor;
import net.starclient.module.ChatPlusState;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * In-Chat history search overlay (triggered via Ctrl+F or the 🔍 pill button)
 * integrated into the "Chat" module. Filters saved chat messages live with
 * highlighted matches and 1-click Copy.
 */
public final class ChatSearchOverlay {
    private static final int ROW_H = 18;

    private boolean open = false;
    private EditBox searchBox;
    private double scroll = 0.0;
    private String copiedFeedback = "";
    private long copiedUntilMs = 0L;

    // Last rendered bounds for click/scroll hit testing
    private int btnX, btnY, btnW, btnH;
    private int panelX, panelY, panelW, panelH;

    public boolean isOpen() {
        return open;
    }

    public void open(Font font, EditBox chatInput) {
        if (!ChatPlusState.isChatSearchEnabled()) return;
        ensureBox(font);
        this.open = true;
        this.scroll = 0.0;
        this.searchBox.setFocused(true);
        if (chatInput != null) {
            chatInput.setFocused(false);
        }
    }

    public void close(EditBox chatInput) {
        this.open = false;
        if (this.searchBox != null) {
            this.searchBox.setFocused(false);
        }
        if (chatInput != null) {
            chatInput.setFocused(true);
        }
    }

    private void ensureBox(Font font) {
        if (this.searchBox != null) return;
        this.searchBox = new EditBox(font, 0, 0, 180, 14, Component.literal(""));
        this.searchBox.setMaxLength(80);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFF8FAFC);
        this.searchBox.setHint(Component.literal("Search chat history..."));
        this.searchBox.setResponder(v -> this.scroll = 0.0);
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int screenW, int screenH, int mouseX, int mouseY, float delta) {
        if (!ChatPlusState.isChatSearchEnabled()) {
            this.open = false;
            return;
        }

        StarClient sc = StarClient.getInstance();
        ClientSettings cs = sc != null ? sc.getClientSettings() : null;
        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int bg = cs != null ? (0xEE000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xEE0F172A;
        int bgDark = cs != null ? cs.getMenuBackgroundDarkColor() : 0xFF0A101E;
        int surface = cs != null ? cs.getMenuSurfaceColor() : 0xFF162032;
        int surfaceHover = cs != null ? cs.getMenuSurfaceHoverColor() : 0xFF1E293B;
        int border = cs != null ? cs.getMenuBorderColor() : 0xFF334155;

        // Bottom-right toggle pill above chat input bar (keeps left side clear for Chat Calculator!)
        String label = this.open ? "✕ Close Search" : "🔍 Search (Ctrl+F)";
        this.btnW = font.width(label) + 12;
        this.btnH = 16;
        this.btnX = screenW - this.btnW - 4;
        this.btnY = Math.max(4, screenH - 34);

        boolean btnHov = hovered(mouseX, mouseY, this.btnX, this.btnY, this.btnW, this.btnH);
        GuiRenderUtils.drawDropShadow(graphics, this.btnX, this.btnY, this.btnW, this.btnH, 4, 2, 85);
        GuiRenderUtils.fillRoundedRect(graphics, this.btnX, this.btnY, this.btnW, this.btnH, 4,
                this.open ? GuiRenderUtils.withAlpha(accent, 65) : (btnHov ? surfaceHover : bg));
        GuiRenderUtils.drawRoundedOutline(graphics, this.btnX, this.btnY, this.btnW, this.btnH, 4,
                this.open || btnHov ? accent : border);
        GuiRenderUtils.drawCenteredText(graphics, font, label, this.btnX + this.btnW / 2, this.btnY + 4,
                this.open || btnHov ? 0xFFFFFFFF : 0xFFCBD5E1);

        if (!this.open) return;

        ensureBox(font);
        this.panelW = Math.min(360, screenW - 16);
        this.panelH = Math.min(196, Math.max(110, screenH - 80));
        this.panelX = screenW - this.panelW - 4;
        this.panelY = Math.max(6, this.btnY - this.panelH - 6);

        graphics.nextStratum();
        GuiRenderUtils.drawDropShadow(graphics, this.panelX, this.panelY, this.panelW, this.panelH, 6, 6, 140);
        GuiRenderUtils.fillRoundedRect(graphics, this.panelX, this.panelY, this.panelW, this.panelH, 6, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, this.panelX, this.panelY, this.panelW, this.panelH, 6, border);

        // Top Search Input Bar
        int inX = this.panelX + 8;
        int inY = this.panelY + 8;
        int inH = 18;
        String query = this.searchBox.getValue().trim();
        List<String> matches = findMatchingMessages(query);

        String countText = (!this.copiedFeedback.isEmpty() && System.currentTimeMillis() < this.copiedUntilMs)
                ? this.copiedFeedback
                : (matches.size() + (matches.size() == 1 ? " match" : " matches"));
        int countW = font.width(countText) + 10;
        int inW = Math.max(80, this.panelW - 16 - countW - 6);

        GuiRenderUtils.fillRoundedRect(graphics, inX, inY, inW, inH, 4, bgDark);
        GuiRenderUtils.drawRoundedOutline(graphics, inX, inY, inW, inH, 4,
                this.searchBox.isFocused() ? accent : border);
        GuiRenderUtils.drawText(graphics, font, "🔍", inX + 5, inY + 5, accent);
        this.searchBox.setPosition(inX + 17, inY + 5);
        this.searchBox.setWidth(inW - 22);
        this.searchBox.extractRenderState(graphics, mouseX, mouseY, delta);

        int badgeX = inX + inW + 6;
        boolean copiedActive = !this.copiedFeedback.isEmpty() && System.currentTimeMillis() < this.copiedUntilMs;
        GuiRenderUtils.fillRoundedRect(graphics, badgeX, inY + 2, countW, 14, 4,
                copiedActive ? 0x3334D399 : bgDark);
        GuiRenderUtils.drawCenteredText(graphics, font, countText, badgeX + countW / 2, inY + 5,
                copiedActive ? 0xFF34D399 : 0xFF94A3B8);

        // Results List
        int listTop = inY + inH + 6;
        int listBottom = this.panelY + this.panelH - 8;
        int listH = Math.max(30, listBottom - listTop);
        int totalH = Math.max(0, matches.size() * (ROW_H + 3) - 3);
        int maxScroll = Math.max(0, totalH - listH);
        this.scroll = Math.clamp(this.scroll, 0.0, maxScroll);

        GuiRenderUtils.fillRoundedRect(graphics, this.panelX + 6, listTop - 2, this.panelW - 12, listH + 4, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(graphics, this.panelX + 6, listTop - 2, this.panelW - 12, listH + 4, 5, border);

        graphics.enableScissor(this.panelX + 8, listTop, this.panelX + this.panelW - 8, listBottom);
        if (matches.isEmpty()) {
            String msg = query.isEmpty() ? "No chat messages recorded yet" : "No messages match \"" + query + "\"";
            GuiRenderUtils.drawCenteredText(graphics, font, msg,
                    this.panelX + this.panelW / 2, listTop + listH / 2 - 4, 0xFF64748B);
        } else {
            int ry = (int) Math.round(listTop + 2 - this.scroll);
            int rx = this.panelX + 10;
            int rw = this.panelW - 22;
            String qLow = query.toLowerCase(Locale.ROOT);
            for (String line : matches) {
                if (ry + ROW_H >= listTop && ry <= listBottom) {
                    boolean hov = mouseY >= listTop && mouseY <= listBottom
                            && hovered(mouseX, mouseY, rx, ry, rw, ROW_H);
                    GuiRenderUtils.fillRoundedRect(graphics, rx, ry, rw, ROW_H, 4, hov ? surfaceHover : surface);
                    if (hov) {
                        GuiRenderUtils.drawRoundedOutline(graphics, rx, ry, rw, ROW_H, 4,
                                GuiRenderUtils.withAlpha(accent, 140));
                    }

                    String copyTag = "📋";
                    int textMaxW = rw - 24;
                    String display = GuiRenderUtils.truncate(font, line, textMaxW);
                    drawHighlightedLine(graphics, font, display, qLow, rx + 5, ry + 5, accent);
                    GuiRenderUtils.drawText(graphics, font, copyTag, rx + rw - 14, ry + 5,
                            hov ? accent : 0xFF64748B);
                }
                ry += ROW_H + 3;
            }
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int sbX = this.panelX + this.panelW - 9;
            int thumbH = Math.max(16, (int) ((double) listH * listH / Math.max(1, totalH)));
            int thumbY = listTop + (int) ((listH - thumbH) * (this.scroll / maxScroll));
            GuiRenderUtils.fillRoundedRect(graphics, sbX, listTop, 2, listH, 1, 0x26FFFFFF);
            GuiRenderUtils.fillRoundedRect(graphics, sbX, thumbY, 2, thumbH, 1, accent);
        }
    }

    private static void drawHighlightedLine(GuiGraphicsExtractor graphics, Font font, String text,
                                            String queryLow, int x, int y, int accent) {
        if (queryLow.isEmpty()) {
            graphics.text(font, text, x, y, 0xFFE2E8F0, false);
            return;
        }
        String textLow = text.toLowerCase(Locale.ROOT);
        int idx = textLow.indexOf(queryLow);
        if (idx < 0) {
            graphics.text(font, text, x, y, 0xFFE2E8F0, false);
            return;
        }
        String before = text.substring(0, idx);
        String match = text.substring(idx, Math.min(text.length(), idx + queryLow.length()));
        String after = text.substring(Math.min(text.length(), idx + queryLow.length()));
        int cx = x;
        if (!before.isEmpty()) {
            graphics.text(font, before, cx, y, 0xFFE2E8F0, false);
            cx += font.width(before);
        }
        if (!match.isEmpty()) {
            int mw = font.width(match);
            GuiRenderUtils.fillRoundedRect(graphics, cx - 1, y - 1, mw + 2, 10, 2, GuiRenderUtils.withAlpha(accent, 55));
            graphics.text(font, match, cx, y, accent, true);
            cx += mw;
        }
        if (!after.isEmpty()) {
            graphics.text(font, after, cx, y, 0xFFE2E8F0, false);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick, Font font, EditBox chatInput) {
        if (!ChatPlusState.isChatSearchEnabled()) return false;
        double mx = event.x();
        double my = event.y();

        if (hovered(mx, my, this.btnX, this.btnY, this.btnW, this.btnH)) {
            playClick();
            if (this.open) {
                close(chatInput);
            } else {
                open(font, chatInput);
            }
            return true;
        }

        if (!this.open) return false;

        if (!hovered(mx, my, this.panelX, this.panelY, this.panelW, this.panelH)) {
            // Clicked outside panel (e.g. into chat input) -> unfocus search box cleanly
            if (this.searchBox != null) this.searchBox.setFocused(false);
            return false;
        }

        int inX = this.panelX + 8;
        int inY = this.panelY + 8;
        if (this.searchBox != null && my >= inY && my <= inY + 18) {
            this.searchBox.setFocused(true);
            if (chatInput != null) chatInput.setFocused(false);
            this.searchBox.mouseClicked(event, doubleClick);
            return true;
        }

        int listTop = inY + 24;
        int listBottom = this.panelY + this.panelH - 8;
        if (my >= listTop && my <= listBottom) {
            String query = this.searchBox != null ? this.searchBox.getValue().trim() : "";
            List<String> matches = findMatchingMessages(query);
            int ry = (int) Math.round(listTop + 2 - this.scroll);
            int rx = this.panelX + 10;
            int rw = this.panelW - 22;
            for (String line : matches) {
                if (hovered(mx, my, rx, ry, rw, ROW_H)) {
                    playClick();
                    Minecraft.getInstance().keyboardHandler.setClipboard(line);
                    this.copiedFeedback = "✓ Copied!";
                    this.copiedUntilMs = System.currentTimeMillis() + 2000L;
                    return true;
                }
                ry += ROW_H + 3;
            }
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (!this.open || !ChatPlusState.isChatSearchEnabled()) return false;
        if (hovered(mouseX, mouseY, this.panelX, this.panelY, this.panelW, this.panelH)) {
            this.scroll -= verticalAmount * 22.0;
            return true;
        }
        return false;
    }

    public boolean keyPressed(KeyEvent event, Font font, EditBox chatInput) {
        if (!ChatPlusState.isChatSearchEnabled()) return false;
        int key = event.key();
        Minecraft mc = Minecraft.getInstance();
        long win = mc.getWindow().handle();
        boolean ctrlDown = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        if (ctrlDown && key == GLFW.GLFW_KEY_F) {
            if (this.open) {
                close(chatInput);
            } else {
                open(font, chatInput);
            }
            return true;
        }

        if (!this.open) return false;

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close(chatInput);
            return true;
        }

        if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.keyPressed(event);
            return true;
        }
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        if (this.open && this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.charTyped(event);
            return true;
        }
        return false;
    }

    private List<String> findMatchingMessages(String query) {
        List<String> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.hud == null) return out;
        List<GuiMessage> all = ((ChatComponentAccessor) mc.gui.hud.getChat()).starclient$getAllMessages();
        if (all == null || all.isEmpty()) return out;

        String qLow = query == null ? "" : query.toLowerCase(Locale.ROOT);
        for (GuiMessage msg : all) {
            if (msg == null || msg.content() == null || ChatPlusState.isSessionDivider(msg.content())) continue;
            String plain = msg.content().getString().replaceAll("§[0-9a-fk-orxA-FK-ORX]", "").trim();
            if (plain.isEmpty()) continue;
            if (qLow.isEmpty() || plain.toLowerCase(Locale.ROOT).contains(qLow)) {
                out.add(plain);
                if (out.size() >= 250) break;
            }
        }
        return out;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static boolean hovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
