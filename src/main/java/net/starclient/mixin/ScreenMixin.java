package net.starclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.gui.TitleBackgroundOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void starclient$onExtractPanorama(GuiGraphicsExtractor graphics, float delta, CallbackInfo ci) {
        StarClient client = StarClient.getInstance();
        if (client == null) return;
        client.ensureConfigLoaded();
        ClientSettings cs = client.getClientSettings();
        if (cs == null) return;

        String mode = cs.getTitleBackground();
        if (!ClientSettings.BG_VANILLA.equals(mode)) {
            ci.cancel();
            TitleBackgroundOverlay.renderBackground(graphics, this.width, this.height, delta, mode);
        }
    }
}
