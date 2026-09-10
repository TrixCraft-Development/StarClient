package net.modernclient.mixin;

import net.minecraft.client.MouseHandler;
import net.modernclient.module.ZoomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void modernclient$zoomScroll(long windowPointer, double horizontalAmount, double verticalAmount, CallbackInfo ci) {
        if (!ZoomState.isActive()) return;
        ZoomState.onScroll(verticalAmount);
        ci.cancel();
    }
}
