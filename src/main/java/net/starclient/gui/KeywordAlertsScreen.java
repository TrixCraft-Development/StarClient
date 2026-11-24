package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.KeywordAlertsState;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Glassmorphic in-game manager screen for Chat Keyword Alerts rules,
 * featuring quick presets, live highlight preview, and sound chime test.
 */
public class KeywordAlertsScreen extends Screen {
    private static final int PANEL_WIDTH = 440;
    private static final int PANEL_HEIGHT = 350;
    private static final int HEADER_HEIGHT = 40;
    private static final int ROW_HEIGHT = 24;

    private static final String[] QUICK_PRESETS = {"party", "event", "boss", "drop", "bed", "gg"};

    private final Screen parent;
    private EditBox keywordBox;
    private String statusMessage = "";
    private int statusColor = 0xFF94A3B8;
    private double listScroll = 0.0;

    public KeywordAlertsScreen(Screen parent) {
        super(Component.literal("Chat Keyword Alerts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int px = (this.width - PANEL_WIDTH) / 2;
        int py = (this.height - PANEL_HEIGHT) / 2;

        String prev = this.keywordBox != null ? this.keywordBox.getValue() : "";
        int boxX = px + 16;
        int boxY = py + 52;
        int boxW = PANEL_WIDTH - 122;

        this.keywordBox = new EditBox(this.font, boxX + 6, boxY + 5, boxW - 12, 12, Component.literal(""));
        this.keywordBox.setMaxLength(64);
        this.keywordBox.setBordered(false);
        this.keywordBox.setTextColor(0xFFF8FAFC);
        this.keywordBox.setHint(Component.literal("Enter keyword, phrase, or player name..."));
        this.keywordBox.setValue(prev);
        addWidget(this.keywordBox);
        this.keywordBox.setFocused(true);
        this.setFocused(this.keywordBox);
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
        int hlColor = KeywordAlertsState.getHighlightArgb();

        int px = (this.width - PANEL_WIDTH) / 2;
        int py = (this.height - PANEL_HEIGHT) / 2;

        GuiRenderUtils.drawDropShadow(extractor, px, py, PANEL_WIDTH, PANEL_HEIGHT, 8, 8, 140);
        GuiRenderUtils.fillRoundedRect(extractor, px, py, PANEL_WIDTH, PANEL_HEIGHT, 8, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, px, py, PANEL_WIDTH, PANEL_HEIGHT, 8, border);

        // Header
        GuiRenderUtils.fillRoundedRect(extractor, px, py, PANEL_WIDTH, HEADER_HEIGHT, 8, surface);
        extractor.fill(px, py + HEADER_HEIGHT - 1, px + PANEL_WIDTH, py + HEADER_HEIGHT, border);

        GuiRenderUtils.drawText(extractor, this.font, "🔔 Chat Keyword Alerts", px + 14, py + 10, accent, true);
        GuiRenderUtils.drawText(extractor, this.font, "Highlights matching chat messages, pops a toast & plays a chime",
                px + 14, py + 23, 0xFF94A3B8);

        // Close Button
        int closeX = px + PANEL_WIDTH - 28;
        int closeY = py + 11;
        boolean closeHov = isHovered(mouseX, mouseY, closeX, closeY, 18, 18);
        GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, 18, 18, 4, closeHov ? 0x44F87171 : bgDark);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + 9, closeY + 5,
                closeHov ? 0xFFF87171 : 0xFF94A3B8);

        // Test Sound Button
        int testW = 54;
        int testX = closeX - testW - 6;
        boolean testHov = isHovered(mouseX, mouseY, testX, closeY, testW, 18);
        GuiRenderUtils.fillRoundedRect(extractor, testX, closeY, testW, 18, 4, testHov ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, testX, closeY, testW, 18, 4, testHov ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "♪ Test", testX + testW / 2, closeY + 5,
                testHov ? accent : 0xFFE2E8F0);

        // Clear All Button (if non-empty)
        List<String> keywords = KeywordAlertsState.getKeywords();
        if (!keywords.isEmpty()) {
            int clearW = 56;
            int clearX = testX - clearW - 6;
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
                this.keywordBox != null && this.keywordBox.isFocused() ? accent : border);
        if (this.keywordBox != null) {
            this.keywordBox.setPosition(boxX + 6, boxY + 6);
            this.keywordBox.setWidth(boxW - 12);
            this.keywordBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        int addBtnX = boxX + boxW + 8;
        int addBtnW = 78;
        boolean addHov = isHovered(mouseX, mouseY, addBtnX, boxY, addBtnW, boxH);
        GuiRenderUtils.fillRoundedRect(extractor, addBtnX, boxY, addBtnW, boxH, 5, addHov ? accentDark : accent);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "+ Add Rule", addBtnX + addBtnW / 2, boxY + 6, 0xFFFFFFFF);

        // Quick Preset Chips
        int chipY = boxY + 26;
        GuiRenderUtils.drawText(extractor, this.font, "Quick add:", px + 16, chipY + 3, 0xFF64748B);
        int chipX = px + 68;
        for (String preset : QUICK_PRESETS) {
            String chipLabel = "+ " + preset;
            int chipW = this.font.width(chipLabel) + 10;
            if (chipX + chipW > px + PANEL_WIDTH - 16) break;
            boolean alreadyAdded = containsIgnoreCase(keywords, preset);
            boolean chipHov = !alreadyAdded && isHovered(mouseX, mouseY, chipX, chipY, chipW, 14);
            int cBg = alreadyAdded ? 0x2234D399 : (chipHov ? surfaceHover : bgDark);
            int cBorder = alreadyAdded ? 0x6634D399 : (chipHov ? accent : border);
            int cFg = alreadyAdded ? 0xFF34D399 : (chipHov ? 0xFFFFFFFF : 0xFFCBD5E1);
            GuiRenderUtils.fillRoundedRect(extractor, chipX, chipY, chipW, 14, 4, cBg);
            GuiRenderUtils.drawRoundedOutline(extractor, chipX, chipY, chipW, 14, 4, cBorder);
            GuiRenderUtils.drawCenteredText(extractor, this.font, alreadyAdded ? "✓ " + preset : chipLabel,
                    chipX + chipW / 2, chipY + 3, cFg);
            chipX += chipW + 5;
        }

        // Subheader
        int subY = chipY + 20;
        String countLabel = "Active Alert Keywords (" + keywords.size() + ")";
        GuiRenderUtils.drawText(extractor, this.font, countLabel, px + 16, subY, 0xFFE2E8F0);
        if (!statusMessage.isEmpty()) {
            int sw = this.font.width(statusMessage);
            GuiRenderUtils.drawText(extractor, this.font, statusMessage, px + PANEL_WIDTH - 16 - sw, subY, statusColor);
        }

        // Keyword List
        int listTop = subY + 14;
        int listBottom = py + PANEL_HEIGHT - 12;
        int maxScroll = Math.max(0, keywords.size() * (ROW_HEIGHT + 4) - (listBottom - listTop));
        this.listScroll = Math.clamp(this.listScroll, 0.0, maxScroll);

        extractor.enableScissor(px + 12, listTop, px + PANEL_WIDTH - 12, listBottom);
        if (keywords.isEmpty()) {
            GuiRenderUtils.drawCenteredText(extractor, this.font,
                    "No alert keywords yet — type above or click a quick-add chip!",
                    px + PANEL_WIDTH / 2, listTop + (listBottom - listTop) / 2 - 4, 0xFF64748B);
        } else {
            int ry = (int) Math.round(listTop - listScroll);
            int rx = px + 16;
            int rw = PANEL_WIDTH - 32;
            for (int i = 0; i < keywords.size(); i++) {
                String kw = keywords.get(i);
                if (ry + ROW_HEIGHT >= listTop && ry <= listBottom) {
                    boolean rowHov = isHovered(mouseX, mouseY, rx, ry, rw, ROW_HEIGHT);
                    GuiRenderUtils.fillRoundedRect(extractor, rx, ry, rw, ROW_HEIGHT, 5, rowHov ? surfaceHover : surface);
                    GuiRenderUtils.drawRoundedOutline(extractor, rx, ry, rw, ROW_HEIGHT, 5, border);

                    // Left dot in highlight color
                    GuiRenderUtils.fillRoundedRect(extractor, rx + 8, ry + 9, 6, 6, 3, hlColor);

                    String kwClipped = GuiRenderUtils.truncate(this.font, kw, 165);
                    GuiRenderUtils.drawText(extractor, this.font, kwClipped, rx + 20, ry + 8, 0xFFF8FAFC);

                    // Highlight preview
                    String previewText = GuiRenderUtils.truncate(this.font, kw, 130);
                    GuiRenderUtils.drawText(extractor, this.font, "Preview: ", rx + 195, ry + 8, 0xFF64748B);
                    int pStart = rx + 195 + this.font.width("Preview: ");
                    GuiRenderUtils.drawText(extractor, this.font, previewText, pStart, ry + 8, hlColor, true);

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

    private void tryAddCurrentKeyword() {
        if (this.keywordBox == null) return;
        String input = this.keywordBox.getValue().trim();
        if (input.isEmpty()) {
            this.statusMessage = "Enter a keyword first";
            this.statusColor = 0xFFF87171;
            return;
        }
        if (KeywordAlertsState.addKeyword(input)) {
            this.keywordBox.setValue("");
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
            playClick();
            onClose();
            return true;
        }

        int testW = 54;
        int testX = closeX - testW - 6;
        if (isHovered(mx, my, testX, closeY, testW, 18)) {
            KeywordAlertsState.playPreviewSound();
            this.statusMessage = "Played alert chime";
            this.statusColor = 0xFF38BDF8;
            return true;
        }

        List<String> keywords = KeywordAlertsState.getKeywords();
        if (!keywords.isEmpty()) {
            int clearW = 56;
            int clearX = testX - clearW - 6;
            if (isHovered(mx, my, clearX, closeY, clearW, 18)) {
                playClick();
                KeywordAlertsState.clearKeywords();
                this.statusMessage = "Cleared all keywords";
                this.statusColor = 0xFF94A3B8;
                saveClientConfig();
                return true;
            }
        }

        int boxX = px + 16;
        int boxY = py + 50;
        int boxW = PANEL_WIDTH - 118;
        int boxH = 20;
        if (this.keywordBox != null && isHovered(mx, my, boxX, boxY, boxW, boxH)) {
            this.keywordBox.setFocused(true);
            this.setFocused(this.keywordBox);
            this.keywordBox.mouseClicked(event, doubleClick);
            return true;
        }

        int addBtnX = boxX + boxW + 8;
        int addBtnW = 78;
        if (isHovered(mx, my, addBtnX, boxY, addBtnW, boxH)) {
            playClick();
            tryAddCurrentKeyword();
            return true;
        }

        // Quick preset chips
        int chipY = boxY + 26;
        int chipX = px + 68;
        for (String preset : QUICK_PRESETS) {
            String chipLabel = "+ " + preset;
            int chipW = this.font.width(chipLabel) + 10;
            if (chipX + chipW > px + PANEL_WIDTH - 16) break;
            if (isHovered(mx, my, chipX, chipY, chipW, 14)) {
                playClick();
                if (KeywordAlertsState.addKeyword(preset)) {
                    this.statusMessage = "Added '" + preset + "'";
                    this.statusColor = 0xFF34D399;
                    saveClientConfig();
                }
                return true;
            }
            chipX += chipW + 5;
        }

        int listTop = chipY + 34;
        int listBottom = py + PANEL_HEIGHT - 12;
        if (my >= listTop && my <= listBottom) {
            int ry = (int) Math.round(listTop - listScroll);
            int rx = px + 16;
            int rw = PANEL_WIDTH - 32;
            for (int i = 0; i < keywords.size(); i++) {
                int delX = rx + rw - 24;
                int delY = ry + 4;
                if (isHovered(mx, my, delX, delY, 16, 16)) {
                    playClick();
                    String removed = keywords.get(i);
                    KeywordAlertsState.removeKeywordAt(i);
                    this.statusMessage = "Removed '" + removed + "'";
                    this.statusColor = 0xFF94A3B8;
                    saveClientConfig();
                    return true;
                }
                ry += ROW_HEIGHT + 4;
            }
        }

        if (this.keywordBox != null) {
            this.keywordBox.setFocused(false);
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
            if (this.keywordBox != null && this.keywordBox.isFocused()) {
                tryAddCurrentKeyword();
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

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String item : list) {
            if (item.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
