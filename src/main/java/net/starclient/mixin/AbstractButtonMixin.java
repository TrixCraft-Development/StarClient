package net.starclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.gui.GuiRenderUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void starclient$onExtractDefaultSprite(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        StarClient starClient = StarClient.getInstance();
        if (starClient == null) return;
        starClient.ensureConfigLoaded();
        ClientSettings cs = starClient.getClientSettings();
        if (cs == null || !cs.isModernButtons()) return;

        ci.cancel();

        AbstractButton self = (AbstractButton) (Object) this;
        int x = self.getX();
        int y = self.getY();
        int w = self.getWidth();
        int h = self.getHeight();
        if (w <= 0 || h <= 0) return;

        int radius = Math.min(5, Math.min(w, h) / 2);
        float alpha = self.getAlpha();
        boolean active = self.isActive();
        boolean hovered = active && self.isHoveredOrFocused();

        if (!active) {
            int bg = GuiRenderUtils.withAlphaFactor(0x440F172A, alpha);
            int border = GuiRenderUtils.withAlphaFactor(0x22334155, alpha);
            GuiRenderUtils.fillRoundedRect(graphics, x, y, w, h, radius, bg);
            GuiRenderUtils.drawRoundedOutline(graphics, x, y, w, h, radius, border);
            return;
        }

        int surface = GuiRenderUtils.withAlphaFactor(cs.getMenuSurfaceColor(), alpha);
        int surfaceHover = GuiRenderUtils.withAlphaFactor(cs.getMenuSurfaceHoverColor(), alpha);
        int border = GuiRenderUtils.withAlphaFactor(cs.getMenuBorderColor(), alpha);
        int accent = GuiRenderUtils.withAlphaFactor(cs.getMenuAccentColor(), alpha);

        // Soft drop shadow, smooth card background, and glowing outline
        GuiRenderUtils.drawDropShadow(graphics, x, y, w, h, hovered ? 6 : 4, hovered ? 3 : 2, hovered ? 90 : 70);
        GuiRenderUtils.fillRoundedRect(graphics, x, y, w, h, radius, hovered ? surfaceHover : surface);
        GuiRenderUtils.drawRoundedOutline(graphics, x, y, w, h, radius, hovered ? accent : border);
    }
}
