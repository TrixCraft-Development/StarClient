package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.gui.TitleBackgroundOverlay;
import net.starclient.module.BetterScreenshotsState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @Shadow public int width;
    @Shadow public int height;
    @Shadow @Final protected Minecraft minecraft;

    @Shadow protected abstract void extractBlurredBackground(GuiGraphicsExtractor graphics);

    @Inject(method = "extractBackground", at = @At("HEAD"))
    private void starclient$onExtractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if ((Object) this instanceof AbstractContainerScreen<?> && this.minecraft != null && this.minecraft.level != null) {
            StarClient sc = StarClient.getInstance();
            if (sc != null && sc.getClientSettings() != null && sc.getClientSettings().isContainerBlur()) {
                this.extractBlurredBackground(graphics);
            }
        }
    }

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void starclient$onExtractPanorama(GuiGraphicsExtractor graphics, float a, CallbackInfo ci) {
        StarClient client = StarClient.getInstance();
        if (client == null) return;
        client.ensureConfigLoaded();
        ClientSettings cs = client.getClientSettings();
        if (cs == null) return;

        String mode = cs.getTitleBackground();
        if (!ClientSettings.BG_VANILLA.equals(mode)) {
            ci.cancel();
            TitleBackgroundOverlay.renderBackground(graphics, this.width, this.height, a, mode);
        }
    }

    @Inject(method = "defaultHandleClickEvent", at = @At("HEAD"), cancellable = true)
    private static void starclient$onDefaultHandleClickEvent(ClickEvent clickEvent, Minecraft client, Screen screen, CallbackInfo ci) {
        if (clickEvent instanceof ClickEvent.CopyToClipboard copy && copy.value() != null) {
            if (copy.value().startsWith(BetterScreenshotsState.COPY_ACTION_PREFIX)) {
                BetterScreenshotsState.handleCopyActionClick(copy.value(), client);
                ci.cancel();
                return;
            }
            if (screen instanceof ChatScreen && client != null && client.gui != null) {
                client.gui.hud.setOverlayMessage(Component.literal("§a✔ Copied message to clipboard"), false);
            }
        }
    }
}
