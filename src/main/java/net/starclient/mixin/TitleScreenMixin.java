package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.starclient.StarClient;
import net.starclient.changelog.ChangelogManager;
import net.starclient.gui.AccountSwitcherOverlay;
import net.starclient.gui.ChangelogScreen;
import net.starclient.gui.GuiRenderUtils;
import net.starclient.gui.TitleBackgroundOverlay;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TitleScreen.class, priority = 2000)
public abstract class TitleScreenMixin {

    @Unique
    private final AccountSwitcherOverlay starclient$accountSwitcher = new AccountSwitcherOverlay();
    @Unique
    private final TitleBackgroundOverlay starclient$backgroundOverlay = new TitleBackgroundOverlay();

    @ModifyArg(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"
            ),
            index = 1
    )
    private String replaceBottomLeftVersionText(String original) {
        if (original == null || !original.startsWith("Minecraft ")) {
            return original;
        }
        return "StarClient-" + StarClient.MOD_VERSION;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderClientName(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.font == null) return;

        String name = StarClient.CLIENT_NAME;
        float scale = 4.0f;
        int screenWidth = graphics.guiWidth();
        int scaledWidth = (int) (client.font.width(name) * scale);
        int x = (screenWidth - scaledWidth) / 2;
        int y = 72;

        int color = 0xFF38BDF8;
        StarClient starClient = StarClient.getInstance();
        if (starClient != null) {
            starClient.ensureConfigLoaded();
            if (starClient.getClientSettings() != null) {
                color = starClient.getClientSettings().getMenuAccentColor();
            }
        }

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(scale, scale);
        graphics.text(client.font, name, (int) (x / scale), (int) (y / scale), color);
        pose.popMatrix();

        boolean isUnread = ChangelogManager.getInstance().isCurrentVersionUnread();
        long now = System.currentTimeMillis();
        float pulse = (float) (Math.sin(now / 260.0) * 0.5 + 0.5);
        int glowColor = GuiRenderUtils.lerpColor(0xFF0EA5E9, color, pulse);

        // Center version text under Title
        String version = StarClient.CLIENT_VERSION;
        int midW = client.font.width(version);
        int midX = screenWidth / 2 - midW / 2;
        int midY = y + (int) (client.font.lineHeight * scale) + 4;
        int centerAreaW = midW + (isUnread ? 32 : 12);
        boolean midHovered = mouseX >= midX - 6 && mouseX <= midX + centerAreaW && mouseY >= midY - 2 && mouseY <= midY + 12;

        if (midHovered) {
            GuiRenderUtils.fillRoundedRect(graphics, midX - 4, midY - 1, centerAreaW, 12, 3, 0x33000000 | (color & 0x00FFFFFF));
            GuiRenderUtils.drawRoundedOutline(graphics, midX - 4, midY - 1, centerAreaW, 12, 3, 0x88000000 | (color & 0x00FFFFFF));
            graphics.text(client.font, version, midX, midY, color);
        } else {
            graphics.text(client.font, version, midX, midY, 0xFF94A3B8);
        }

        if (isUnread) {
            int badgeX = midX + midW + 5;
            int badgeY = midY - 1;
            int badgeW = client.font.width("NEW") + 8;
            int badgeH = 11;
            GuiRenderUtils.fillRoundedRect(graphics, badgeX, badgeY, badgeW, badgeH, 3, 0x33000000 | (glowColor & 0x00FFFFFF));
            GuiRenderUtils.drawRoundedOutline(graphics, badgeX, badgeY, badgeW, badgeH, 3, glowColor);
            GuiRenderUtils.drawCenteredText(graphics, client.font, "NEW", badgeX + badgeW / 2, badgeY + 1, 0xFFFFFFFF);
        }

        // Bottom-left version text interactive hover & tooltip
        Screen self = (Screen) (Object) this;
        String verText = "StarClient-" + StarClient.MOD_VERSION;
        int textW = client.font.width(verText);
        int textH = client.font.lineHeight;
        int verX = 2;
        int verY = self.height - 10;
        int unreadBadgeW = client.font.width("NEW") + 8;
        int fullVerW = textW + (isUnread ? unreadBadgeW + 6 : 0);
        boolean verHovered = mouseX >= verX - 2 && mouseX <= verX + fullVerW + 4 && mouseY >= verY - 2 && mouseY <= verY + textH + 2;

        if (isUnread) {
            int badgeX = verX + textW + 5;
            int badgeY = verY - 1;
            int badgeH = 11;
            GuiRenderUtils.fillRoundedRect(graphics, badgeX, badgeY, unreadBadgeW, badgeH, 3, 0x33000000 | (glowColor & 0x00FFFFFF));
            GuiRenderUtils.drawRoundedOutline(graphics, badgeX, badgeY, unreadBadgeW, badgeH, 3, glowColor);
            GuiRenderUtils.drawCenteredText(graphics, client.font, "NEW", badgeX + unreadBadgeW / 2, badgeY + 1, 0xFFFFFFFF);
        }

        if (verHovered) {
            // Re-render text in accent color with subtle glow pill
            GuiRenderUtils.fillRoundedRect(graphics, verX - 2, verY - 2, textW + 4, textH + 3, 3, 0x33000000 | (color & 0x00FFFFFF));
            GuiRenderUtils.drawRoundedOutline(graphics, verX - 2, verY - 2, textW + 4, textH + 3, 3, 0x99000000 | (color & 0x00FFFFFF));
            graphics.text(client.font, verText, verX, verY, color);

            // Tooltip
            String tooltip = isUnread ? "✦ Star Client updated! Click to view What's New" : "✦ Click to view Changelog (v" + StarClient.MOD_VERSION + ")";
            int tipW = client.font.width(tooltip) + 14;
            int tipH = 17;
            int tipX = verX;
            int tipY = Math.max(4, verY - tipH - 5);
            GuiRenderUtils.drawDropShadow(graphics, tipX, tipY, tipW, tipH, 4, 3, 80);
            GuiRenderUtils.fillRoundedRect(graphics, tipX, tipY, tipW, tipH, 4, 0xF00F172A);
            GuiRenderUtils.drawRoundedOutline(graphics, tipX, tipY, tipW, tipH, 4, 0x88000000 | (color & 0x00FFFFFF));
            GuiRenderUtils.drawCenteredText(graphics, client.font, tooltip, tipX + tipW / 2, tipY + 4, 0xFFF1F5F9);
        }

        // Main Menu Update Toast Banner (Top-Right)
        if (isUnread && !ChangelogManager.getInstance().isToastDismissed()) {
            int toastW = 224;
            int toastH = 36;
            int toastX = screenWidth - toastW - 10;
            int toastY = 40;

            boolean toastHovered = mouseX >= toastX && mouseX <= toastX + toastW && mouseY >= toastY && mouseY <= toastY + toastH;
            int closeX = toastX + toastW - 16;
            int closeY = toastY + 4;
            boolean closeHovered = mouseX >= closeX - 3 && mouseX <= closeX + 11 && mouseY >= closeY - 3 && mouseY <= closeY + 11;

            // Soft drop shadow & dark glass background
            GuiRenderUtils.drawDropShadow(graphics, toastX, toastY, toastW, toastH, 6, 3, 90);
            GuiRenderUtils.fillRoundedRect(graphics, toastX, toastY, toastW, toastH, 6, 0xF20F172A);
            int toastBorder = toastHovered ? color : GuiRenderUtils.lerpColor(0x3364748B, color, pulse);
            GuiRenderUtils.drawRoundedOutline(graphics, toastX, toastY, toastW, toastH, 6, toastBorder);

            // Left accent icon circle
            int iconX = toastX + 7;
            int iconY = toastY + 7;
            int iconSize = 22;
            GuiRenderUtils.fillRoundedRect(graphics, iconX, iconY, iconSize, iconSize, 4, 0x25000000 | (color & 0x00FFFFFF));
            GuiRenderUtils.drawCenteredText(graphics, client.font, "✦", iconX + iconSize / 2, iconY + 5, color);

            // Toast Text
            int textX1 = iconX + iconSize + 7;
            int textY1 = toastY + 6;
            graphics.text(client.font, "Star Client Updated!", textX1, textY1, 0xFFFFFFFF);
            int textY2 = textY1 + client.font.lineHeight + 1;
            String sub = "v" + StarClient.MOD_VERSION + " • What's New →";
            graphics.text(client.font, sub, textX1, textY2, toastHovered ? color : 0xFF94A3B8);

            // Close button '✕'
            if (closeHovered) {
                GuiRenderUtils.fillRoundedRect(graphics, closeX - 2, closeY - 2, 12, 12, 3, 0x33EF4444);
            }
            GuiRenderUtils.drawCenteredText(graphics, client.font, "✕", closeX + 4, closeY + 1, closeHovered ? 0xFFEF4444 : 0xFF64748B);
        }

        this.starclient$accountSwitcher.render(graphics, mouseX, mouseY);
        this.starclient$backgroundOverlay.render(graphics, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void starclient$onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (this.starclient$backgroundOverlay.mouseClicked((Screen) (Object) this, event)) {
            if (this.starclient$backgroundOverlay.isDropdownOpen()) {
                this.starclient$accountSwitcher.closeDropdown();
            }
            cir.setReturnValue(true);
            return;
        }
        if (this.starclient$accountSwitcher.mouseClicked((Screen) (Object) this, event)) {
            if (this.starclient$accountSwitcher.isDropdownOpen()) {
                this.starclient$backgroundOverlay.closeDropdown();
            }
            cir.setReturnValue(true);
            return;
        }

        if (event.button() == 0) {
            Minecraft client = Minecraft.getInstance();
            if (client.font != null) {
                Screen self = (Screen) (Object) this;
                double mx = event.x();
                double my = event.y();

                // Check Toast Banner clicks (Top-Right)
                if (ChangelogManager.getInstance().isCurrentVersionUnread() && !ChangelogManager.getInstance().isToastDismissed()) {
                    int toastW = 224;
                    int toastH = 36;
                    int toastX = self.width - toastW - 10;
                    int toastY = 40;

                    int closeX = toastX + toastW - 16;
                    int closeY = toastY + 4;
                    if (mx >= closeX - 3 && mx <= closeX + 11 && my >= closeY - 3 && my <= closeY + 11) {
                        ChangelogManager.getInstance().dismissToast();
                        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        cir.setReturnValue(true);
                        return;
                    }

                    if (mx >= toastX && mx <= toastX + toastW && my >= toastY && my <= toastY + toastH) {
                        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        client.gui.setScreen(new ChangelogScreen(self));
                        cir.setReturnValue(true);
                        return;
                    }
                }

                String verText = "StarClient-" + StarClient.MOD_VERSION;
                int textW = client.font.width(verText);
                int textH = client.font.lineHeight;
                int verX = 2;
                int verY = self.height - 10;
                int unreadBadgeW = client.font.width("NEW") + 8;
                int fullVerW = textW + (ChangelogManager.getInstance().isCurrentVersionUnread() ? unreadBadgeW + 6 : 0);

                // Click bottom-left version text
                if (mx >= verX - 2 && mx <= verX + fullVerW + 4 && my >= verY - 2 && my <= verY + textH + 2) {
                    client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    client.gui.setScreen(new ChangelogScreen(self));
                    cir.setReturnValue(true);
                    return;
                }

                // Click center version text under title
                int screenWidth = self.width;
                String version = StarClient.CLIENT_VERSION;
                int midW = client.font.width(version);
                int midX = screenWidth / 2 - midW / 2;
                int midY = 72 + (int) (client.font.lineHeight * 4.0f) + 4;
                int centerAreaW = midW + (ChangelogManager.getInstance().isCurrentVersionUnread() ? 32 : 12);
                if (mx >= midX - 6 && mx <= midX + centerAreaW && my >= midY - 2 && my <= midY + 12) {
                    client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    client.gui.setScreen(new ChangelogScreen(self));
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
    }
}
