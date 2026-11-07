package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.ChatPlusState;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class BannedWordsScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 330;
    private static final int HEADER_HEIGHT = 40;
    private static final int ROW_HEIGHT = 24;

    private final Screen parent;
    private EditBox wordBox;
    private String statusMessage = "";
    private int statusColor = 0xFF94A3B8;
    private double listScroll = 0.0;

    public BannedWordsScreen(Screen parent) {
        super(Component.literal("Banned Chat Words"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int px = (this.width - PANEL_WIDTH) / 2;
        int py = (this.height - PANEL_HEIGHT) / 2;

        String prev = this.wordBox != null ? this.wordBox.getValue() : "";
        int boxX = px + 16;
        int boxY = py + 52;
        int boxW = PANEL_WIDTH - 122;

        this.wordBox = new EditBox(this.font, boxX + 6, boxY + 5, boxW - 12, 12, Component.literal(""));
        this.wordBox.setMaxLength(64);
        this.wordBox.setBordered(false);
        this.wordBox.setTextColor(0xFFF8FAFC);
        this.wordBox.setHint(Component.literal("Enter banned word or phrase..."));
        this.wordBox.setValue(prev);
        addWidget(this.wordBox);
        this.wordBox.setFocused(true);
        this.setFocused(this.wordBox);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs.getMenuAccentColor();
        int accentDark = cs.getMenuAccentDarkColor();
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

        GuiRenderUtils.drawText(extractor, this.font, "🚫 Banned Chat Words", px + 14, py + 10, accent, true);
        GuiRenderUtils.drawText(extractor, this.font, "Replaces each letter of matching words with '*'", px + 14, py + 23, 0xFF94A3B8);

        // Close Button
        int closeX = px + PANEL_WIDTH - 28;
        int closeY = py + 11;
        boolean closeHov = isHovered(mouseX, mouseY, closeX, closeY, 18, 18);
        GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, 18, 18, 4, closeHov ? 0x44F87171 : bgDark);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + 9, closeY + 5, closeHov ? 0xFFF87171 : 0xFF94A3B8);

        // Clear All Button (if non-empty)
        List<String> words = ChatPlusState.getBannedWords();
        if (!words.isEmpty()) {
            int clearW = 58;
            int clearX = closeX - clearW - 8;
            boolean clearHov = isHovered(mouseX, mouseY, clearX, closeY, clearW, 18);
            GuiRenderUtils.fillRoundedRect(extractor, clearX, closeY, clearW, 18, 4, clearHov ? 0x44EF4444 : bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, clearX, closeY, clearW, 18, 4, clearHov ? 0xFFEF4444 : border);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Clear All", clearX + clearW / 2, closeY + 5,
                    clearHov ? 0xFFF87171 : 0xFF94A3B8);
        }

        // Input Row
        int boxX = px + 16;
        int boxY = py + 50;
        int boxW = PANEL_WIDTH - 118;
        int boxH = 20;

        GuiRenderUtils.fillRoundedRect(extractor, boxX, boxY, boxW, boxH, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, boxX, boxY, boxW, boxH, 5,
                this.wordBox != null && this.wordBox.isFocused() ? accent : border);
        if (this.wordBox != null) {
            this.wordBox.setPosition(boxX + 6, boxY + 6);
            this.wordBox.setWidth(boxW - 12);
            this.wordBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        int addBtnX = boxX + boxW + 8;
        int addBtnW = 78;
        boolean addHov = isHovered(mouseX, mouseY, addBtnX, boxY, addBtnW, boxH);
        GuiRenderUtils.fillRoundedRect(extractor, addBtnX, boxY, addBtnW, boxH, 5, addHov ? accentDark : accent);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "+ Add Word", addBtnX + addBtnW / 2, boxY + 6, 0xFFFFFFFF);

        // Status / Count Subheader
        int subY = boxY + 26;
        String countLabel = "Active Filter Words (" + words.size() + ")";
        GuiRenderUtils.drawText(extractor, this.font, countLabel, px + 16, subY, 0xFFE2E8F0);
        if (!statusMessage.isEmpty()) {
            int sw = this.font.width(statusMessage);
            GuiRenderUtils.drawText(extractor, this.font, statusMessage, px + PANEL_WIDTH - 16 - sw, subY, statusColor);
        }

        // Word List
        int listTop = subY + 14;
        int listBottom = py + PANEL_HEIGHT - 12;
        int maxScroll = Math.max(0, words.size() * (ROW_HEIGHT + 4) - (listBottom - listTop));
        this.listScroll = Math.clamp(this.listScroll, 0.0, maxScroll);

        extractor.enableScissor(px + 12, listTop, px + PANEL_WIDTH - 12, listBottom);
        if (words.isEmpty()) {
            GuiRenderUtils.drawCenteredText(extractor, this.font,
                    "No banned words added yet — type a word above and click '+ Add Word'",
                    px + PANEL_WIDTH / 2, listTop + (listBottom - listTop) / 2 - 4, 0xFF64748B);
        } else {
            int ry = (int) Math.round(listTop - listScroll);
            int rx = px + 16;
            int rw = PANEL_WIDTH - 32;
            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);
                if (ry + ROW_HEIGHT >= listTop && ry <= listBottom) {
                    boolean rowHov = isHovered(mouseX, mouseY, rx, ry, rw, ROW_HEIGHT);
                    GuiRenderUtils.fillRoundedRect(extractor, rx, ry, rw, ROW_HEIGHT, 5, rowHov ? surfaceHover : surface);
                    GuiRenderUtils.drawRoundedOutline(extractor, rx, ry, rw, ROW_HEIGHT, 5, border);

                    String wordClipped = GuiRenderUtils.truncate(this.font, word, 160);
                    GuiRenderUtils.drawText(extractor, this.font, wordClipped, rx + 10, ry + 8, 0xFFF8FAFC);

                    String masked = ChatPlusState.maskWord(word);
                    String preview = "→  " + GuiRenderUtils.truncate(this.font, masked, 130);
                    GuiRenderUtils.drawText(extractor, this.font, preview, rx + 180, ry + 8, 0xFF94A3B8);

                    int delX = rx + rw - 24;
                    int delY = ry + 4;
                    boolean delHov = isHovered(mouseX, mouseY, delX, delY, 16, 16);
                    GuiRenderUtils.fillRoundedRect(extractor, delX, delY, 16, 16, 4, delHov ? 0x44EF4444 : bgDark);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", delX + 8, delY + 4,
                            delHov ? 0xFFF87171 : 0xFF94A3B8);
                }
                ry += ROW_HEIGHT + 4;
            }
        }
        extractor.disableScissor();
    }

    private void tryAddCurrentWord() {
        if (this.wordBox == null) return;
        String input = this.wordBox.getValue().trim();
        if (input.isEmpty()) {
            this.statusMessage = "Enter a word first";
            this.statusColor = 0xFFF87171;
            return;
        }
        if (ChatPlusState.addBannedWord(input)) {
            this.wordBox.setValue("");
            this.statusMessage = "Added '" + input + "'";
            this.statusColor = 0xFF34D399;
            saveClientConfig();
        } else {
            this.statusMessage = "Already in list";
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

        List<String> words = ChatPlusState.getBannedWords();
        if (!words.isEmpty()) {
            int clearW = 58;
            int clearX = closeX - clearW - 8;
            if (isHovered(mx, my, clearX, closeY, clearW, 18)) {
                ChatPlusState.clearBannedWords();
                this.statusMessage = "Cleared all banned words";
                this.statusColor = 0xFF94A3B8;
                saveClientConfig();
                return true;
            }
        }

        int boxX = px + 16;
        int boxY = py + 50;
        int boxW = PANEL_WIDTH - 118;
        int boxH = 20;
        if (this.wordBox != null && isHovered(mx, my, boxX, boxY, boxW, boxH)) {
            this.wordBox.setFocused(true);
            this.setFocused(this.wordBox);
            this.wordBox.mouseClicked(event, doubleClick);
            return true;
        }

        int addBtnX = boxX + boxW + 8;
        int addBtnW = 78;
        if (isHovered(mx, my, addBtnX, boxY, addBtnW, boxH)) {
            tryAddCurrentWord();
            return true;
        }

        int listTop = boxY + 40;
        int listBottom = py + PANEL_HEIGHT - 12;
        if (my >= listTop && my <= listBottom) {
            int ry = (int) Math.round(listTop - listScroll);
            int rx = px + 16;
            int rw = PANEL_WIDTH - 32;
            for (int i = 0; i < words.size(); i++) {
                int delX = rx + rw - 24;
                int delY = ry + 4;
                if (isHovered(mx, my, delX, delY, 16, 16)) {
                    String removed = words.get(i);
                    ChatPlusState.removeBannedWordAt(i);
                    this.statusMessage = "Removed '" + removed + "'";
                    this.statusColor = 0xFF94A3B8;
                    saveClientConfig();
                    return true;
                }
                ry += ROW_HEIGHT + 4;
            }
        }

        if (this.wordBox != null) {
            this.wordBox.setFocused(false);
        }
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
            if (this.wordBox != null && this.wordBox.isFocused()) {
                tryAddCurrentWord();
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
        saveClientConfig();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void saveClientConfig() {
        StarClient sc = StarClient.getInstance();
        if (sc != null && sc.getConfigManager() != null) {
            sc.getConfigManager().saveConfig();
        }
    }

    private static boolean isHovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
