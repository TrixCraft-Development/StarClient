package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.setting.TextSetting;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

public class TextSettingScreen extends Screen {
    private static final int PANEL_WIDTH = 420;
    private static final int PANEL_HEIGHT = 196;
    private static final int HEADER_HEIGHT = 42;
    private static final int BTN_HEIGHT = 22;
    private static final int DONE_BTN_WIDTH = 76;
    private static final int CANCEL_BTN_WIDTH = 68;
    private static final int CLEAR_BTN_WIDTH = 62;

    private final TextSetting target;
    private final Screen parent;
    private final String initialValue;
    private EditBox valueBox;
    private boolean revealed;

    public TextSettingScreen(TextSetting target, Screen parent) {
        super(Component.literal(target.getName()));
        this.target = target;
        this.parent = parent;
        this.initialValue = target.getValue() == null ? "" : target.getValue();
        this.revealed = !target.isSecret();
    }

    @Override
    protected void init() {
        super.init();
        String current = this.valueBox != null ? this.valueBox.getValue() : this.initialValue;
        installField(current);
    }

    private void installField(String value) {
        int cardX = panelX() + 16;
        int cardY = panelY() + 50;
        int cardW = PANEL_WIDTH - 32;
        int boxX = cardX + 10;
        int boxY = cardY + 26;
        int boxW = cardW - 20;

        EditBox box = new EditBox(this.font, boxX + 6, boxY + 7, boxW - 12, 12, Component.literal(""));
        box.setMaxLength(target.isSecret() ? 128 : 64);
        box.setBordered(false);
        box.setTextColor(0xFFF8FAFC);
        box.setHint(Component.literal(target.isSecret() && !revealed ? "Hidden value..." : "Enter value..."));
        box.setValue(value);
        if (target.isSecret() && !revealed) {
            box.addFormatter((text, offset) ->
                    Component.literal("•".repeat(text.length())).getVisualOrderText());
        }

        if (this.valueBox != null) {
            removeWidget(this.valueBox);
        }
        this.valueBox = box;
        addWidget(box);
        setInitialFocus(box);
    }

    private int panelX() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_HEIGHT) / 2;
    }

    private String revealLabel() {
        return revealed ? "👁 Hide" : "👁 Reveal";
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs.getMenuAccentColor();
        int accentDark = cs.getMenuAccentDarkColor();
        int bg = 0xF4000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
        int bgDark = cs.getMenuBackgroundDarkColor();
        int surface = cs.getMenuSurfaceColor();
        int surfaceHover = cs.getMenuSurfaceHoverColor();
        int border = cs.getMenuBorderColor();

        int pX = panelX();
        int pY = panelY();

        // Modal container with multi-layer drop shadow & rounded outline
        GuiRenderUtils.drawDropShadow(extractor, pX, pY, PANEL_WIDTH, PANEL_HEIGHT, 8, 8, 140);
        GuiRenderUtils.fillRoundedRect(extractor, pX, pY, PANEL_WIDTH, PANEL_HEIGHT, 8, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, pX, pY, PANEL_WIDTH, PANEL_HEIGHT, 8, border);

        // Header bar
        GuiRenderUtils.fillRoundedRect(extractor, pX, pY, PANEL_WIDTH, HEADER_HEIGHT, 8, surface);
        extractor.fill(pX, pY + HEADER_HEIGHT, pX + PANEL_WIDTH, pY + HEADER_HEIGHT + 1, border);

        int iconBadgeW = 22;
        int iconBadgeH = 22;
        int iconBadgeX = pX + 12;
        int iconBadgeY = pY + (HEADER_HEIGHT - iconBadgeH) / 2;
        GuiRenderUtils.fillRoundedRect(extractor, iconBadgeX, iconBadgeY, iconBadgeW, iconBadgeH, 5, accentDark);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✎", iconBadgeX + iconBadgeW / 2, iconBadgeY + 6, 0xFFFFFFFF);

        GuiRenderUtils.drawText(extractor, this.font, target.getName(), iconBadgeX + iconBadgeW + 8, pY + 11, 0xFFF8FAFC, true);
        String desc = GuiRenderUtils.truncate(this.font, target.getDescription(), PANEL_WIDTH - 100);
        GuiRenderUtils.drawText(extractor, this.font, desc, iconBadgeX + iconBadgeW + 8, pY + 24, 0xFF64748B);

        // Top-right close button
        int closeX = pX + PANEL_WIDTH - 28;
        int closeY = pY + (HEADER_HEIGHT - 18) / 2;
        boolean closeHover = hovered(closeX, closeY, 18, 18, mouseX, mouseY);
        if (closeHover) {
            GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, 18, 18, 4, 0x33EF4444);
            GuiRenderUtils.drawRoundedOutline(extractor, closeX, closeY, 18, 18, 4, 0x88EF4444);
        }
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + 9, closeY + 4, closeHover ? 0xFFF87171 : 0xFF94A3B8);

        // Content Card
        int cardX = pX + 16;
        int cardY = pY + 50;
        int cardW = PANEL_WIDTH - 32;
        int cardH = 92;
        GuiRenderUtils.fillRoundedRect(extractor, cardX, cardY, cardW, cardH, 6, surface);
        GuiRenderUtils.drawRoundedOutline(extractor, cardX, cardY, cardW, cardH, 6, border);

        GuiRenderUtils.drawText(extractor, this.font, "Value:", cardX + 10, cardY + 8, 0xFFE2E8F0);

        // Secret reveal toggle button
        if (target.isSecret()) {
            String rLabel = revealLabel();
            int rW = GuiRenderUtils.textWidth(this.font, rLabel) + 12;
            int rH = 16;
            int rX = cardX + cardW - 10 - rW;
            int rY = cardY + 6;
            boolean rHover = hovered(rX, rY, rW, rH, mouseX, mouseY);
            int rBg = revealed ? GuiRenderUtils.withAlpha(accent, 40) : (rHover ? surfaceHover : bgDark);
            int rBorder = revealed ? accent : (rHover ? GuiRenderUtils.withAlpha(accent, 120) : border);
            int rFg = revealed ? accent : (rHover ? 0xFFF8FAFC : 0xFF94A3B8);

            GuiRenderUtils.fillRoundedRect(extractor, rX, rY, rW, rH, 4, rBg);
            GuiRenderUtils.drawRoundedOutline(extractor, rX, rY, rW, rH, 4, rBorder);
            GuiRenderUtils.drawCenteredText(extractor, this.font, rLabel, rX + rW / 2, rY + 4, rFg);
        }

        // Input Field Container
        int boxX = cardX + 10;
        int boxY = cardY + 26;
        int boxW = cardW - 20;
        int boxH = 26;
        boolean focused = this.valueBox != null && this.valueBox.isFocused();
        boolean boxHover = hovered(boxX, boxY, boxW, boxH, mouseX, mouseY);
        int boxBg = focused ? surfaceHover : bgDark;
        int boxBorder = focused ? accent : (boxHover ? GuiRenderUtils.withAlpha(accent, 120) : border);

        GuiRenderUtils.fillRoundedRect(extractor, boxX, boxY, boxW, boxH, 5, boxBg);
        GuiRenderUtils.drawRoundedOutline(extractor, boxX, boxY, boxW, boxH, 5, boxBorder);

        if (this.valueBox != null) {
            this.valueBox.setX(boxX + 6);
            this.valueBox.setY(boxY + 7);
            this.valueBox.setWidth(boxW - 12);
            this.valueBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        // Hint below input box
        String tip = target.isSecret()
                ? "Saved locally in your client configuration"
                : "Press Enter to save • Esc to cancel";
        GuiRenderUtils.drawText(extractor, this.font, tip, cardX + 10, cardY + 60, 0xFF64748B);

        // Footer Action Buttons
        int footerY = pY + PANEL_HEIGHT - 32;

        // Clear Button
        int clearX = pX + 16;
        boolean clearHover = hovered(clearX, footerY, CLEAR_BTN_WIDTH, BTN_HEIGHT, mouseX, mouseY);
        int clearBg = clearHover ? 0x28F87171 : surface;
        int clearBorder = clearHover ? 0xFFF87171 : border;
        int clearFg = clearHover ? 0xFFF87171 : 0xFF94A3B8;
        GuiRenderUtils.fillRoundedRect(extractor, clearX, footerY, CLEAR_BTN_WIDTH, BTN_HEIGHT, 5, clearBg);
        GuiRenderUtils.drawRoundedOutline(extractor, clearX, footerY, CLEAR_BTN_WIDTH, BTN_HEIGHT, 5, clearBorder);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Clear", clearX + CLEAR_BTN_WIDTH / 2, footerY + (BTN_HEIGHT - this.font.lineHeight) / 2, clearFg, true);

        // Cancel Button
        int cancelX = pX + PANEL_WIDTH - 16 - DONE_BTN_WIDTH - 8 - CANCEL_BTN_WIDTH;
        boolean cancelHover = hovered(cancelX, footerY, CANCEL_BTN_WIDTH, BTN_HEIGHT, mouseX, mouseY);
        int cancelBg = cancelHover ? surfaceHover : surface;
        int cancelBorder = cancelHover ? 0xFF94A3B8 : border;
        int cancelFg = cancelHover ? 0xFFF8FAFC : 0xFF94A3B8;
        GuiRenderUtils.fillRoundedRect(extractor, cancelX, footerY, CANCEL_BTN_WIDTH, BTN_HEIGHT, 5, cancelBg);
        GuiRenderUtils.drawRoundedOutline(extractor, cancelX, footerY, CANCEL_BTN_WIDTH, BTN_HEIGHT, 5, cancelBorder);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Cancel", cancelX + CANCEL_BTN_WIDTH / 2, footerY + (BTN_HEIGHT - this.font.lineHeight) / 2, cancelFg, true);

        // Done Button
        int doneX = pX + PANEL_WIDTH - 16 - DONE_BTN_WIDTH;
        boolean doneHover = hovered(doneX, footerY, DONE_BTN_WIDTH, BTN_HEIGHT, mouseX, mouseY);
        int doneBg = doneHover ? accentDark : accent;
        int doneBorder = doneHover ? 0xFFFFFFFF : accent;
        GuiRenderUtils.fillRoundedRect(extractor, doneX, footerY, DONE_BTN_WIDTH, BTN_HEIGHT, 5, doneBg);
        GuiRenderUtils.drawRoundedOutline(extractor, doneX, footerY, DONE_BTN_WIDTH, BTN_HEIGHT, 5, doneBorder);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Done", doneX + DONE_BTN_WIDTH / 2, footerY + (BTN_HEIGHT - this.font.lineHeight) / 2, 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        if (event.button() != 0) {
            return super.mouseClicked(event, action);
        }
        double mouseX = event.x();
        double mouseY = event.y();

        int pX = panelX();
        int pY = panelY();

        // Close button
        int closeX = pX + PANEL_WIDTH - 28;
        int closeY = pY + (HEADER_HEIGHT - 18) / 2;
        if (hovered(closeX, closeY, 18, 18, mouseX, mouseY)) {
            cancelAndClose();
            return true;
        }

        // Secret reveal toggle
        if (target.isSecret()) {
            int cardX = pX + 16;
            int cardY = pY + 50;
            int cardW = PANEL_WIDTH - 32;
            String rLabel = revealLabel();
            int rW = GuiRenderUtils.textWidth(this.font, rLabel) + 12;
            int rX = cardX + cardW - 10 - rW;
            int rY = cardY + 6;
            if (hovered(rX, rY, rW, 16, mouseX, mouseY)) {
                this.revealed = !this.revealed;
                installField(this.valueBox != null ? this.valueBox.getValue() : "");
                return true;
            }
        }

        // Input field box focus
        int cardX = pX + 16;
        int cardY = pY + 50;
        int cardW = PANEL_WIDTH - 32;
        int boxX = cardX + 10;
        int boxY = cardY + 26;
        int boxW = cardW - 20;
        if (hovered(boxX, boxY, boxW, 26, mouseX, mouseY)) {
            if (this.valueBox != null) {
                this.valueBox.setFocused(true);
                this.setFocused(this.valueBox);
            }
            return true;
        }

        // Footer buttons
        int footerY = pY + PANEL_HEIGHT - 32;

        // Clear
        int clearX = pX + 16;
        if (hovered(clearX, footerY, CLEAR_BTN_WIDTH, BTN_HEIGHT, mouseX, mouseY)) {
            installField("");
            return true;
        }

        // Cancel
        int cancelX = pX + PANEL_WIDTH - 16 - DONE_BTN_WIDTH - 8 - CANCEL_BTN_WIDTH;
        if (hovered(cancelX, footerY, CANCEL_BTN_WIDTH, BTN_HEIGHT, mouseX, mouseY)) {
            cancelAndClose();
            return true;
        }

        // Done
        int doneX = pX + PANEL_WIDTH - 16 - DONE_BTN_WIDTH;
        if (hovered(doneX, footerY, DONE_BTN_WIDTH, BTN_HEIGHT, mouseX, mouseY)) {
            commitAndClose();
            return true;
        }

        return super.mouseClicked(event, action);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            commitAndClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            cancelAndClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void commitAndClose() {
        String value = this.valueBox == null ? initialValue : this.valueBox.getValue();
        this.target.setValue(value == null ? "" : value.trim());
        save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private void cancelAndClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private void save() {
        StarClient.getInstance().getConfigManager().saveConfig();
    }

    private static boolean hovered(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
