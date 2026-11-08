package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.starclient.StarClient;
import net.starclient.module.DeathRecapState;
import net.starclient.module.Module;

import java.util.Locale;

/**
 * Renders the draggable, scalable Death Recap HUD card on the Minecraft DeathScreen
 * and in the Star Client HUD Editor.
 */
public final class DeathRecapRenderer {

    public static final int CARD_WIDTH = 260;
    public static final int CARD_HEIGHT = 92;

    private DeathRecapRenderer() {
    }

    public static void render(GuiGraphicsExtractor extractor, DeathScreen screen, int mouseX, int mouseY) {
        if (!DeathRecapState.isActive() || screen == null) return;
        Minecraft mc = Minecraft.getInstance();
        DeathRecapState.onDeathScreenOpened(screen, mc);

        DeathRecapState.DeathRecord record = DeathRecapState.getLastDeathRecord();
        if (record == null) return;

        HudLayout layout = StarClient.getInstance().getHudLayout();
        int cardX = layout.effectiveX(HudLayout.DEATH_RECAP, screen.width, CARD_WIDTH);
        int cardY = layout.effectiveY(HudLayout.DEATH_RECAP, screen.height);
        float scale = layout.getScale(HudLayout.DEATH_RECAP);

        double localMouseX = cardX + (mouseX - cardX) / scale;
        double localMouseY = cardY + (mouseY - cardY) / scale;

        boolean scaled = layout.beginScale(extractor, cardX, cardY, HudLayout.DEATH_RECAP);
        renderCard(extractor, mc, cardX, cardY, record, localMouseX, localMouseY);
        layout.endScale(extractor, scaled);
    }

    public static void renderPreview(GuiGraphicsExtractor extractor, int cardX, int cardY) {
        Minecraft mc = Minecraft.getInstance();
        DeathRecapState.DeathRecord record = DeathRecapState.getLastDeathRecord();
        if (record == null) {
            record = new DeathRecapState.DeathRecord(
                    "Defeated in battle",
                    "Zombie",
                    "Iron Sword",
                    false,
                    null,
                    128.0, 64.0, -256.0,
                    "Overworld",
                    "14:20:05",
                    7
            );
        }
        renderCard(extractor, mc, cardX, cardY, record, -1, -1);
    }

    public static void renderCard(GuiGraphicsExtractor extractor, Minecraft mc, int cardX, int cardY,
                                  DeathRecapState.DeathRecord record, double mouseX, double mouseY) {
        Module mod = DeathRecapState.getModule();
        boolean transparent = mod != null && mod.isTransparentBackground();
        int bg = mod != null ? mod.getBackgroundColor() : 0xD80F172A;
        int textColor = mod != null ? mod.getTextColor() : 0xFFF8FAFC;
        Font font = mc.font;

        if (!transparent) {
            GuiRenderUtils.drawDropShadow(extractor, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 6, 4, 110);
            GuiRenderUtils.fillRoundedRect(extractor, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 6, bg);
        }
        int border = 0x44EF4444; // Subtle red accent outline for death card
        GuiRenderUtils.drawRoundedOutline(extractor, cardX, cardY, CARD_WIDTH, CARD_HEIGHT, 6, border);

        // Header
        GuiRenderUtils.drawText(extractor, font, "💀 Death Recap", cardX + 10, cardY + 8, 0xFFEF4444, true);
        GuiRenderUtils.drawText(extractor, font, record.timeString(), cardX + CARD_WIDTH - font.width(record.timeString()) - 10, cardY + 8, 0xFF94A3B8);
        extractor.fill(cardX + 8, cardY + 20, cardX + CARD_WIDTH - 8, cardY + 21, 0x24FFFFFF);

        // Killer Face Avatar or Icon
        int avatarX = cardX + 10;
        int avatarY = cardY + 26;
        int avatarSize = 22;

        if (record.isPlayerKiller() && record.killerProfile() != null && DeathRecapState.isShowKillerAvatar()) {
            PlayerFaceExtractor.extractRenderState(extractor, record.killerProfile(), avatarX, avatarY, avatarSize);
            GuiRenderUtils.drawRoundedOutline(extractor, avatarX - 1, avatarY - 1, avatarSize + 2, avatarSize + 2, 3, 0x55EF4444);
        } else {
            GuiRenderUtils.fillRoundedRect(extractor, avatarX, avatarY, avatarSize, avatarSize, 3, 0x33EF4444);
            GuiRenderUtils.drawRoundedOutline(extractor, avatarX - 1, avatarY - 1, avatarSize + 2, avatarSize + 2, 3, 0x88EF4444);
            GuiRenderUtils.drawCenteredText(extractor, font, "⚔", avatarX + avatarSize / 2, avatarY + 7, 0xFFEF4444);
        }

        // Details next to avatar
        int infoX = avatarX + avatarSize + 8;
        String killerLabel = "Killed by: " + record.killerName();
        if (font.width(killerLabel) > 175) {
            killerLabel = font.plainSubstrByWidth(killerLabel, 170) + "..";
        }
        GuiRenderUtils.drawText(extractor, font, killerLabel, infoX, cardY + 25, textColor, true);

        if (!record.killerWeapon().isEmpty()) {
            String weaponText = "Using: " + record.killerWeapon();
            if (font.width(weaponText) > 175) {
                weaponText = font.plainSubstrByWidth(weaponText, 170) + "..";
            }
            GuiRenderUtils.drawText(extractor, font, weaponText, infoX, cardY + 36, 0xFFFBBF24);
        } else {
            String xpText = "Lost " + record.xpLevels() + " XP Levels";
            GuiRenderUtils.drawText(extractor, font, xpText, infoX, cardY + 36, 0xFF94A3B8);
        }

        // Coordinates row
        String coords = String.format(Locale.US, "XYZ: %.0f, %.0f, %.0f (%s)",
                record.x(), record.y(), record.z(), record.dimension());
        GuiRenderUtils.drawText(extractor, font, coords, infoX, cardY + 48, 0xFFCBD5E1);

        // Buttons row
        int btnY = cardY + CARD_HEIGHT - 22;
        int wpBtnW = 96;
        int wpBtnX = cardX + 10;
        boolean wpHov = isHovered(mouseX, mouseY, wpBtnX, btnY, wpBtnW, 16);
        GuiRenderUtils.fillRoundedRect(extractor, wpBtnX, btnY, wpBtnW, 16, 4, wpHov ? 0x4438BDF8 : 0x2238BDF8);
        GuiRenderUtils.drawRoundedOutline(extractor, wpBtnX, btnY, wpBtnW, 16, 4, wpHov ? 0xFF38BDF8 : 0x6638BDF8);
        GuiRenderUtils.drawCenteredText(extractor, font, "📌 Set Waypoint", wpBtnX + wpBtnW / 2, btnY + 4,
                wpHov ? 0xFFFFFFFF : 0xFF38BDF8);

        int copyBtnW = 92;
        int copyBtnX = wpBtnX + wpBtnW + 8;
        boolean copyHov = isHovered(mouseX, mouseY, copyBtnX, btnY, copyBtnW, 16);
        GuiRenderUtils.fillRoundedRect(extractor, copyBtnX, btnY, copyBtnW, 16, 4, copyHov ? 0x4434D399 : 0x2234D399);
        GuiRenderUtils.drawRoundedOutline(extractor, copyBtnX, btnY, copyBtnW, 16, 4, copyHov ? 0xFF34D399 : 0x6634D399);
        GuiRenderUtils.drawCenteredText(extractor, font, "📋 Copy Coords", copyBtnX + copyBtnW / 2, btnY + 4,
                copyHov ? 0xFFFFFFFF : 0xFF34D399);
    }

    public static boolean onMouseClicked(DeathScreen screen, double mouseX, double mouseY) {
        if (!DeathRecapState.isActive() || screen == null) return false;
        DeathRecapState.DeathRecord record = DeathRecapState.getLastDeathRecord();
        if (record == null) return false;

        HudLayout layout = StarClient.getInstance().getHudLayout();
        int cardX = layout.effectiveX(HudLayout.DEATH_RECAP, screen.width, CARD_WIDTH);
        int cardY = layout.effectiveY(HudLayout.DEATH_RECAP, screen.height);
        float scale = layout.getScale(HudLayout.DEATH_RECAP);

        double localMouseX = cardX + (mouseX - cardX) / scale;
        double localMouseY = cardY + (mouseY - cardY) / scale;

        int btnY = cardY + CARD_HEIGHT - 22;

        int wpBtnW = 96;
        int wpBtnX = cardX + 10;
        if (isHovered(localMouseX, localMouseY, wpBtnX, btnY, wpBtnW, 16)) {
            DeathRecapState.createWaypointFromLastDeath();
            return true;
        }

        int copyBtnW = 92;
        int copyBtnX = wpBtnX + wpBtnW + 8;
        if (isHovered(localMouseX, localMouseY, copyBtnX, btnY, copyBtnW, 16)) {
            DeathRecapState.copyCoordsToClipboard();
            return true;
        }

        return false;
    }

    public static boolean onMouseClicked(DeathScreen screen, net.minecraft.client.input.MouseButtonEvent event) {
        if (event == null || event.button() != 0) return false;
        return onMouseClicked(screen, event.x(), event.y());
    }

    private static boolean isHovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
