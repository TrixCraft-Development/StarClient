package net.starclient.mixin;

import net.minecraft.client.MouseHandler;
import net.starclient.module.ZoomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void zoomScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (!ZoomState.isActive()) return;
        ZoomState.onScroll(yoffset);
        ci.cancel();
    }
}
