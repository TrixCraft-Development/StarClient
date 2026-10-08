package net.modernclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.modernclient.ClientSettings;
import net.modernclient.ModernClient;
import net.modernclient.setting.TextSetting;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

public class TextSettingScreen extends Screen {
    private static final int PANEL_WIDTH = 460;
    private static final int PANEL_HEIGHT = 236;
    private static final int HEADER_HEIGHT = 40;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_WIDTH = 110;

    private final TextSetting target;
    private final Screen parent;
    private EditBox valueBox;
    private boolean revealed;

    public TextSettingScreen(TextSetting target, Screen parent) {
        super(Component.literal(target.getName()));
        this.target = target;
        this.parent = parent;
        this.revealed = !target.isSecret();
    }

    @Override
    protected void init() {
        super.init();
        installField(target.getValue() == null ? "" : target.getValue());
    }

    private void installField(String value) {
        EditBox box = new EditBox(this.font, fieldX(), panelY() + 96, fieldWidth(), FIELD_HEIGHT, Component.literal(""));
        box.setMaxLength(target.isSecret() ? 128 : 64);
        box.setHint(Component.literal(target.isSecret() && !revealed ? "Hidden" : "Click here and type"));
        box.setValue(value);
        if (target.isSecret() && !revealed) {
            box.addFormatter((text, offset) ->
                    Component.literal("*".repeat(text.length())).getVisualOrderText());
        }

        if (this.valueBox != null) {
            removeWidget(this.valueBox);
        }
        this.valueBox = box;
        addWidget(box);
    }

    private int panelX() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_HEIGHT) / 2;
    }

    private int fieldX() {
        return panelX() + 16;
    }

    private int fieldWidth() {
        return PANEL_WIDTH - 32;
    }

    private int buttonY() {
        return panelY() + PANEL_HEIGHT - 52;
    }

    private String revealLabel() {
        return revealed ? "Hide" : "Reveal";
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        ClientSettings cs = ModernClient.getInstance().getClientSettings();

        extractor.fill(panelX(), panelY(), panelX() + PANEL_WIDTH, panelY() + PANEL_HEIGHT, cs.getMenuBackgroundColor());
        extractor.fill(panelX(), panelY(), panelX() + PANEL_WIDTH, panelY() + HEADER_HEIGHT, cs.getMenuBackgroundLightColor());
        extractor.text(this.font, "★ " + target.getName(), panelX() + 16, panelY() + 12, cs.getMenuAccentColor());

        extractor.textWithWordWrap(this.font, Component.literal(target.getDescription()),
                fieldX(), panelY() + 56, fieldWidth() - 8, 0xFF94A3B8);

        extractor.text(this.font, "Value", fieldX(), panelY() + 80, 0xFF94A3B8);
        this.valueBox.extractRenderState(extractor, mouseX, mouseY, delta);

        if (target.isSecret()) {
            extractor.text(this.font, "Saved as plain text inside the client config file.",
                    fieldX(), panelY() + 140, 0xFF64748B);

            int revealWidth = this.font.width(revealLabel()) + 4;
            int revealX = fieldX() + fieldWidth() - revealWidth;
            int revealY = buttonY() + 4;
            extractor.text(this.font, revealLabel(), revealX, revealY,
                    hovered(revealX, revealY - 4, revealWidth, 16, mouseX, mouseY) ? cs.getMenuAccentColor() : 0xFF64748B);
        }

        drawButton(extractor, "Clear", fieldX(), buttonY(), 0xFF64748B, mouseX, mouseY);
        drawButton(extractor, "Cancel", fieldX() + BUTTON_WIDTH + 8, buttonY(), 0xFF64748B, mouseX, mouseY);
        drawButton(extractor, "Done", fieldX() + fieldWidth() - BUTTON_WIDTH, buttonY(), cs.getMenuAccentColor(), mouseX, mouseY);
    }

    private void drawButton(GuiGraphicsExtractor extractor, String label, int x, int y, int color, int mouseX, int mouseY) {
        boolean active = hovered(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);
        extractor.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, active ? darken(color) : color);
        extractor.text(this.font, label, x + (BUTTON_WIDTH - this.font.width(label)) / 2, y + 8, 0xFFFFFFFF);
    }

    private static int darken(int color) {
        int r = (int) ((color >> 16 & 0xFF) * 0.75);
        int g = (int) ((color >> 8 & 0xFF) * 0.75);
        int b = (int) ((color & 0xFF) * 0.75);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        if (event.button() != 0) {
            return super.mouseClicked(event, action);
        }
        double mouseX = event.x();
        double mouseY = event.y();

        if (target.isSecret()) {
            int revealWidth = this.font.width(revealLabel()) + 4;
            int revealX = fieldX() + fieldWidth() - revealWidth;
            int revealY = buttonY() + 4;
            if (hovered(revealX, revealY - 4, revealWidth, 16, mouseX, mouseY)) {
                this.revealed = !this.revealed;
                installField(this.valueBox.getValue());
                return true;
            }
        }

        if (hovered(fieldX(), buttonY(), BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
            installField("");
            save();
            return true;
        }
        if (hovered(fieldX() + BUTTON_WIDTH + 8, buttonY(), BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
            commit(currentValue());
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        if (hovered(fieldX() + fieldWidth() - BUTTON_WIDTH, buttonY(), BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
            commit(currentValue());
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        return super.mouseClicked(event, action);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            commit(currentValue());
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        commit(currentValue());
        super.removed();
    }

    private String currentValue() {
        return this.valueBox == null ? (target.getValue() == null ? "" : target.getValue()) : this.valueBox.getValue();
    }

    private void commit(String value) {
        this.target.setValue(value == null ? "" : value);
        save();
    }

    private void save() {
        ModernClient.getInstance().getConfigManager().saveConfig();
    }

    private static boolean hovered(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
