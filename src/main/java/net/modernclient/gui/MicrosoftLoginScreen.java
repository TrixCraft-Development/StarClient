package net.modernclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.modernclient.ClientSettings;
import net.modernclient.ModernClient;
import net.modernclient.account.MicrosoftAuthService;
import net.modernclient.account.MinecraftAccount;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class MicrosoftLoginScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 200;
    private static final int HEADER_HEIGHT = 36;
    private static final int BUTTON_HEIGHT = 24;

    private final Screen parent;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile String userCode = "...";
    private volatile String verificationUri = "https://www.microsoft.com/link";
    private volatile String statusText = "Requesting Microsoft login code...";
    private volatile boolean hasError = false;

    public MicrosoftLoginScreen(Screen parent) {
        super(Component.literal("Microsoft Login"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        if ("...".equals(this.userCode) && !this.hasError) {
            startDeviceCodeFlow();
        }
    }

    private void startDeviceCodeFlow() {
        this.cancelled.set(false);
        this.hasError = false;
        this.userCode = "...";
        this.statusText = "Requesting Microsoft login code...";

        CompletableFuture.runAsync(() -> {
            try {
                MicrosoftAuthService.DeviceCodeSession session = MicrosoftAuthService.requestDeviceCode();
                if (this.cancelled.get()) return;

                this.userCode = session.userCode();
                this.verificationUri = session.verificationUri();
                this.statusText = "Code copied! Paste it in your browser to sign in.";

                Minecraft client = Minecraft.getInstance();
                client.execute(() -> {
                    client.keyboardHandler.setClipboard(session.userCode());
                    Util.getPlatform().openUri(session.verificationUri());
                });

                MinecraftAccount account = MicrosoftAuthService.pollAndCompleteLogin(
                        session,
                        this.cancelled::get,
                        msg -> this.statusText = msg
                );
                if (this.cancelled.get()) return;

                ModernClient.getInstance().getAccountManager().addOrUpdateMicrosoftAccount(account);
                client.execute(() -> client.gui.setScreen(this.parent));
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                if (!this.cancelled.get()) {
                    this.hasError = true;
                    this.statusText = e.getMessage() != null ? e.getMessage() : "Login failed.";
                    ModernClient.LOGGER.error("Microsoft login failed", e);
                }
            }
        });
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
        ClientSettings cs = ModernClient.getInstance().getClientSettings();
        int pX = panelX();
        int pY = panelY();

        extractor.fill(pX, pY, pX + PANEL_WIDTH, pY + PANEL_HEIGHT, cs.getMenuBackgroundColor());
        extractor.fill(pX, pY, pX + PANEL_WIDTH, pY + HEADER_HEIGHT, cs.getMenuBackgroundLightColor());
        extractor.fill(pX, pY, pX + PANEL_WIDTH, pY + 2, cs.getMenuAccentColor());

        extractor.text(this.font, "★ Microsoft Account Login", pX + 16, pY + 13, cs.getMenuAccentColor());

        String instruction = "1. Open " + verificationUri + "   2. Enter code:";
        extractor.text(this.font, instruction, pX + (PANEL_WIDTH - this.font.width(instruction)) / 2, pY + 52, 0xFFCBD5E1);

        // Code box
        int codeBoxW = 180;
        int codeBoxH = 36;
        int codeBoxX = pX + (PANEL_WIDTH - codeBoxW) / 2;
        int codeBoxY = pY + 70;
        extractor.fill(codeBoxX, codeBoxY, codeBoxX + codeBoxW, codeBoxY + codeBoxH, 0xFF0F172A);
        extractor.fill(codeBoxX, codeBoxY, codeBoxX + codeBoxW, codeBoxY + 1, cs.getMenuAccentColor());
        extractor.fill(codeBoxX, codeBoxY + codeBoxH - 1, codeBoxX + codeBoxW, codeBoxY + codeBoxH, cs.getMenuAccentColor());

        float scale = 2.0f;
        int scaledW = (int) (this.font.width(userCode) * scale);
        int codeTextX = codeBoxX + (codeBoxW - scaledW) / 2;
        int codeTextY = codeBoxY + (codeBoxH - (int) (this.font.lineHeight * scale)) / 2;
        Matrix3x2fStack pose = extractor.pose();
        pose.pushMatrix();
        pose.scale(scale, scale);
        extractor.text(this.font, userCode, (int) (codeTextX / scale), (int) (codeTextY / scale), 0xFF34D399);
        pose.popMatrix();

        // Status text
        int statusColor = hasError ? 0xFFEF4444 : 0xFF94A3B8;
        extractor.textWithWordWrap(this.font, Component.literal(statusText), pX + 20, pY + 118, PANEL_WIDTH - 40, statusColor);

        int btnY = pY + PANEL_HEIGHT - 36;
        int cancelW = 100;
        int actionW = 220;
        drawButton(extractor, "Cancel", pX + 18, btnY, cancelW, 0xFF475569, mouseX, mouseY);
        String actionLabel = hasError ? "Retry New Code" : "Copy Code & Open Browser";
        drawButton(extractor, actionLabel, pX + PANEL_WIDTH - 18 - actionW, btnY, actionW, cs.getMenuAccentColor(), mouseX, mouseY);
    }

    private void drawButton(GuiGraphicsExtractor extractor, String label, int x, int y, int width, int color, int mouseX, int mouseY) {
        boolean isHovered = hovered(x, y, width, BUTTON_HEIGHT, mouseX, mouseY);
        int bg = isHovered ? darken(color) : color;
        extractor.fill(x, y, x + width, y + BUTTON_HEIGHT, bg);
        extractor.text(this.font, label, x + (width - this.font.width(label)) / 2, y + 8, 0xFFFFFFFF);
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
            int btnY = pY + PANEL_HEIGHT - 36;

            if (hovered(pX + 18, btnY, 100, BUTTON_HEIGHT, mouseX, mouseY)) {
                this.cancelled.set(true);
                Minecraft.getInstance().gui.setScreen(parent);
                return true;
            }
            if (hovered(pX + PANEL_WIDTH - 18 - 220, btnY, 220, BUTTON_HEIGHT, mouseX, mouseY)) {
                if (hasError) {
                    startDeviceCodeFlow();
                } else if (!"...".equals(userCode)) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(userCode);
                    Util.getPlatform().openUri(verificationUri);
                    this.statusText = "Code copied & browser opened! Waiting for login...";
                }
                return true;
            }
        }
        return super.mouseClicked(event, action);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.cancelled.set(true);
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        this.cancelled.set(true);
        super.removed();
    }

    private static boolean hovered(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
