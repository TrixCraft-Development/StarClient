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
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

public class OfflineAccountScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 176;
    private static final int HEADER_HEIGHT = 36;
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 24;

    private final Screen parent;
    private EditBox usernameBox;
    private String errorMessage = "";

    public OfflineAccountScreen(Screen parent) {
        super(Component.literal("Offline Account"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int fieldX = panelX() + 18;
        int fieldY = panelY() + 78;
        int fieldW = PANEL_WIDTH - 36;
        this.usernameBox = new EditBox(this.font, fieldX, fieldY, fieldW, 20, Component.literal(""));
        this.usernameBox.setMaxLength(16);
        this.usernameBox.setHint(Component.literal("Enter Minecraft username (3-16 chars)"));
        this.addWidget(this.usernameBox);
        this.setInitialFocus(this.usernameBox);
    }

    private int panelX() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_HEIGHT) / 2;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int pX = panelX();
        int pY = panelY();

        extractor.fill(pX, pY, pX + PANEL_WIDTH, pY + PANEL_HEIGHT, cs.getMenuBackgroundColor());
        extractor.fill(pX, pY, pX + PANEL_WIDTH, pY + HEADER_HEIGHT, cs.getMenuBackgroundLightColor());
        extractor.fill(pX, pY, pX + PANEL_WIDTH, pY + 2, cs.getMenuAccentColor());

        extractor.text(this.font, "★ Add / Switch Offline Account", pX + 16, pY + 13, cs.getMenuAccentColor());
        extractor.text(this.font, "Username:", pX + 18, pY + 62, 0xFF94A3B8);

        this.usernameBox.extractRenderState(extractor, mouseX, mouseY, delta);

        if (!errorMessage.isEmpty()) {
            extractor.text(this.font, errorMessage, pX + 18, pY + 106, 0xFFEF4444);
        } else {
            extractor.text(this.font, "Works on offline/cracked servers & singleplayer.", pX + 18, pY + 106, 0xFF64748B);
        }

        int btnY = pY + PANEL_HEIGHT - 38;
        drawButton(extractor, "Cancel", pX + 18, btnY, 0xFF475569, mouseX, mouseY);
        drawButton(extractor, "Login & Switch", pX + PANEL_WIDTH - 18 - BUTTON_WIDTH, btnY, cs.getMenuAccentColor(), mouseX, mouseY);
    }

    private void drawButton(GuiGraphicsExtractor extractor, String label, int x, int y, int color, int mouseX, int mouseY) {
        boolean isHovered = hovered(x, y, mouseX, mouseY);
        int bg = isHovered ? darken(color) : color;
        extractor.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, bg);
        extractor.text(this.font, label, x + (BUTTON_WIDTH - this.font.width(label)) / 2, y + 8, 0xFFFFFFFF);
    }

    private static int darken(int color) {
        int r = (int) ((color >> 16 & 0xFF) * 0.8);
        int g = (int) ((color >> 8 & 0xFF) * 0.8);
        int b = (int) ((color & 0xFF) * 0.8);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            int pX = panelX();
            int pY = panelY();
            int btnY = pY + PANEL_HEIGHT - 38;

            if (hovered(pX + 18, btnY, mouseX, mouseY)) {
                Minecraft.getInstance().gui.setScreen(parent);
                return true;
            }
            if (hovered(pX + PANEL_WIDTH - 18 - BUTTON_WIDTH, btnY, mouseX, mouseY)) {
                submit();
                return true;
            }
        }
        return super.mouseClicked(event, action);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    private void submit() {
        String name = this.usernameBox != null ? this.usernameBox.getValue().trim() : "";
        if (name.length() < 3 || name.length() > 16 || !name.matches("[A-Za-z0-9_]+")) {
            this.errorMessage = "Invalid username (use 3-16 letters, numbers, or _).";
            return;
        }
        StarClient.getInstance().getAccountManager().addOrUpdateOfflineAccount(name);
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private static boolean hovered(int x, int y, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + OfflineAccountScreen.BUTTON_WIDTH && mouseY >= y && mouseY <= y + OfflineAccountScreen.BUTTON_HEIGHT;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
