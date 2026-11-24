package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.gui.GuiRenderUtils;
import net.starclient.gui.ScreenshotGalleryScreen;
import net.starclient.gui.ServerSwitcherScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds sleek glassmorphic Quick Server Switcher and Screenshot Gallery
 * buttons to the top-right corner of the Pause Menu (ESC).
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    @Shadow
    public abstract boolean showsPauseMenu();

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void starclient$addPauseQuickButtons(CallbackInfo ci) {
        if (!this.showsPauseMenu()) return;

        Minecraft client = Minecraft.getInstance();
        int btnH = 24;
        int y = 8;

        String switchLabel = "🌐 Switch Server";
        int switchW = client.font.width(switchLabel) + 18;
        int switchX = this.width - switchW - 8;

        this.addRenderableWidget(starclient$createGlassPill(switchX, y, switchW, btnH, switchLabel,
                () -> client.gui.setScreen(new ServerSwitcherScreen(this))));

        String galleryLabel = "📷 Gallery";
        int galleryW = client.font.width(galleryLabel) + 18;
        int galleryX = switchX - galleryW - 6;

        this.addRenderableWidget(starclient$createGlassPill(galleryX, y, galleryW, btnH, galleryLabel,
                () -> client.gui.setScreen(new ScreenshotGalleryScreen(this))));
    }

    @Unique
    private AbstractWidget starclient$createGlassPill(int x, int y, int width, int height, String label, Runnable action) {
        return new AbstractWidget(x, y, width, height, Component.literal(label)) {
            @Override
            protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
                Minecraft client = Minecraft.getInstance();
                StarClient sc = StarClient.getInstance();
                ClientSettings cs = sc != null ? sc.getClientSettings() : null;

                int bg = 0xEE0F172A;
                int hoverBg = 0xEE1E293B;
                int border = 0xFF334155;
                int accent = 0xFF38BDF8;
                if (cs != null) {
                    bg = 0xEE000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
                    hoverBg = cs.getMenuSurfaceHoverColor();
                    border = cs.getMenuBorderColor();
                    accent = cs.getMenuAccentColor();
                }

                boolean hovered = this.isHovered();
                GuiRenderUtils.drawDropShadow(graphics, this.getX(), this.getY(), this.width, this.height, 6, 3, 85);
                GuiRenderUtils.fillRoundedRect(graphics, this.getX(), this.getY(), this.width, this.height, 6,
                        hovered ? hoverBg : bg);
                GuiRenderUtils.drawRoundedOutline(graphics, this.getX(), this.getY(), this.width, this.height, 6,
                        hovered ? accent : border);
                GuiRenderUtils.drawCenteredText(graphics, client.font, label,
                        this.getX() + this.width / 2,
                        this.getY() + (this.height - client.font.lineHeight) / 2 + 1,
                        hovered ? accent : 0xFFFFFFFF);
            }

            @Override
            public void onClick(MouseButtonEvent event, boolean doubleClick) {
                action.run();
            }

            @Override
            protected void updateWidgetNarration(NarrationElementOutput narration) {
                this.defaultButtonNarrationText(narration);
            }
        };
    }
}
