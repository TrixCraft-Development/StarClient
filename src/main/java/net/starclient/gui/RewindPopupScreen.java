package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.stats.RewindManager;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

/**
 * Initial popup menu shown when first launching the game during the active Yearly Rewind window.
 * Displays "Your rewind of the year is here. Press below to watch it", a Watch button,
 * and closes when pressing the "✕" button or ESC.
 */
public class RewindPopupScreen extends Screen {

    private static final int CARD_WIDTH = 360;
    private static final int CARD_HEIGHT = 178;

    private final Screen parent;

    public RewindPopupScreen(Screen parent) {
        super(Component.literal("Rewind of the Year"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        RewindManager.getInstance().markPopupShown();
    }

    private int getAccentColor() {
        StarClient sc = StarClient.getInstance();
        if (sc != null && sc.getClientSettings() != null) {
            return sc.getClientSettings().getMenuAccentColor();
        }
        return 0xFF38BDF8;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        StarClient sc = StarClient.getInstance();
        ClientSettings cs = sc != null ? sc.getClientSettings() : null;

        // Render background if opened from TitleScreen (outside a world)
        if (client.level == null) {
            String bgMode = cs != null ? cs.getTitleBackground() : ClientSettings.BG_MODERN;
            TitleBackgroundOverlay.renderBackground(extractor, this.width, this.height, delta, bgMode);
        }

        // Dim backdrop
        extractor.fill(0, 0, this.width, this.height, 0xC4050914);

        int accent = getAccentColor();
        long now = System.currentTimeMillis();
        float pulse = (float) (Math.sin(now / 300.0) * 0.5 + 0.5);
        int borderGlow = GuiRenderUtils.lerpColor(accent, 0xFFFBBF24, pulse * 0.65F);

        int cardX = (this.width - CARD_WIDTH) / 2;
        int cardY = (this.height - CARD_HEIGHT) / 2;

        // Card drop shadow, dark glass surface, and animated outline
        GuiRenderUtils.drawDropShadow(extractor, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 16, 6, 150);
        GuiRenderUtils.fillRoundedRect(extractor, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 8, 0xF80B132B);
        GuiRenderUtils.drawRoundedOutline(extractor, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 8, borderGlow);

        // Top-right "✕" close button
        int closeSize = 20;
        int closeX = cardX + CARD_WIDTH - closeSize - 10;
        int closeY = cardY + 10;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + closeSize
                && mouseY >= closeY && mouseY <= closeY + closeSize;
        int closeBg = closeHovered ? 0x44EF4444 : 0x1AFFFFFF;
        int closeOutline = closeHovered ? 0xFFEF4444 : 0x33FFFFFF;
        int closeText = closeHovered ? 0xFFF87171 : 0xFF94A3B8;

        GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, closeSize, closeSize, 4, closeBg);
        GuiRenderUtils.drawRoundedOutline(extractor, closeX, closeY, closeSize, closeSize, 4, closeOutline);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕",
                closeX + closeSize / 2, closeY + (closeSize - this.font.lineHeight) / 2 + 1, closeText);

        // Festive top badge
        int year = RewindManager.getInstance().getRewindYear();
        String badgeText = "✨ STARCLIENT " + year + " REWIND ✨";
        int badgeW = this.font.width(badgeText) + 16;
        int badgeH = 16;
        int badgeX = cardX + (CARD_WIDTH - badgeW) / 2;
        int badgeY = cardY + 18;
        GuiRenderUtils.fillRoundedRect(extractor, badgeX, badgeY, badgeW, badgeH, 4, 0x28FBBF24);
        GuiRenderUtils.drawRoundedOutline(extractor, badgeX, badgeY, badgeW, badgeH, 4, 0x88FBBF24);
        GuiRenderUtils.drawCenteredText(extractor, this.font, badgeText,
                cardX + CARD_WIDTH / 2, badgeY + (badgeH - this.font.lineHeight) / 2 + 1, 0xFFFBBF24);

        // Separator line
        extractor.fill(cardX + 20, cardY + 42, cardX + CARD_WIDTH - 20, cardY + 43, 0x28FFFFFF);

        // Main Prompt Text
        GuiRenderUtils.drawCenteredText(extractor, this.font,
                "Your rewind of the year is here.",
                cardX + CARD_WIDTH / 2, cardY + 60, 0xFFFFFFFF);
        GuiRenderUtils.drawCenteredText(extractor, this.font,
                "Press below to watch it",
                cardX + CARD_WIDTH / 2, cardY + 76, 0xFFE2E8F0);

        // Duration info text
        int daysLeft = RewindManager.getInstance().getDaysRemaining();
        String subText = "Available for " + daysLeft + "d in the Main Menu & Stats Menu";
        GuiRenderUtils.drawCenteredText(extractor, this.font,
                subText, cardX + CARD_WIDTH / 2, cardY + 102, 0xFF94A3B8);

        // Watch Button
        int btnW = 190;
        int btnH = 28;
        int btnX = cardX + (CARD_WIDTH - btnW) / 2;
        int btnY = cardY + CARD_HEIGHT - btnH - 16;
        boolean btnHovered = mouseX >= btnX && mouseX <= btnX + btnW
                && mouseY >= btnY && mouseY <= btnY + btnH;

        int btnBg = btnHovered ? accent : (0x38000000 | (accent & 0x00FFFFFF));
        int btnBorder = btnHovered ? 0xFFFFFFFF : borderGlow;
        int btnTextCol = 0xFFFFFFFF;

        GuiRenderUtils.drawDropShadow(extractor, btnX, btnY, btnW, btnH, 6, 3, 80);
        GuiRenderUtils.fillRoundedRect(extractor, btnX, btnY, btnW, btnH, 6, btnBg);
        GuiRenderUtils.drawRoundedOutline(extractor, btnX, btnY, btnW, btnH, 6, btnBorder);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "🎬 Watch Your Rewind",
                btnX + btnW / 2, btnY + (btnH - this.font.lineHeight) / 2 + 1, btnTextCol);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();

            int cardX = (this.width - CARD_WIDTH) / 2;
            int cardY = (this.height - CARD_HEIGHT) / 2;

            // 1. Check "✕" Close button
            int closeSize = 20;
            int closeX = cardX + CARD_WIDTH - closeSize - 10;
            int closeY = cardY + 10;
            if (mouseX >= closeX && mouseX <= closeX + closeSize
                    && mouseY >= closeY && mouseY <= closeY + closeSize) {
                closePopup();
                return true;
            }

            // 2. Check "Watch Your Rewind" button
            int btnW = 190;
            int btnH = 28;
            int btnX = cardX + (CARD_WIDTH - btnW) / 2;
            int btnY = cardY + CARD_HEIGHT - btnH - 16;
            if (mouseX >= btnX && mouseX <= btnX + btnW
                    && mouseY >= btnY && mouseY <= btnY + btnH) {
                Minecraft client = Minecraft.getInstance();
                client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                client.gui.setScreen(new RewindScreen(this.parent));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            closePopup();
            return true;
        }
        return super.keyPressed(event);
    }

    private void closePopup() {
        Minecraft client = Minecraft.getInstance();
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (this.parent != null) {
            client.gui.setScreen(this.parent);
        } else {
            this.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
