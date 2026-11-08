package net.starclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.starclient.gui.DeathRecapRenderer;
import net.starclient.module.AutoRespawnReconnectState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeathScreen.class)
public abstract class DeathScreenMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void starclient$onRenderDeathScreen(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        DeathScreen self = (DeathScreen) (Object) this;
        AutoRespawnReconnectState.renderScreenOverlay(graphics, self, mouseX, mouseY);
        DeathRecapRenderer.render(graphics, self, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void starclient$onDeathScreenMouseClicked(MouseButtonEvent event, boolean action, CallbackInfoReturnable<Boolean> cir) {
        DeathScreen self = (DeathScreen) (Object) this;
        if (DeathRecapRenderer.onMouseClicked(self, event)) {
            cir.setReturnValue(true);
        }
    }
}
