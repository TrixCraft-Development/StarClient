package net.starclient.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.util.SmoothDouble;
import net.starclient.module.ZoomState;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Shadow
    @Final
    private SmoothDouble smoothTurnX;

    @Shadow
    @Final
    private SmoothDouble smoothTurnY;

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Unique
    private boolean starclient$wasZoomCinematic = false;

    @Inject(method = "onScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void zoomScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (!ZoomState.isZoomHeldNow()) return;
        ZoomState.onScroll(yoffset);
        ci.cancel();
    }

    @Inject(method = "turnPlayer(D)V", at = @At("HEAD"))
    private void starclient$scaleZoomMouseSensitivity(double mousea, CallbackInfo ci) {
        if (!ZoomState.isZoomHeldNow()) return;
        double mult = ZoomState.getSensitivityMultiplier();
        if (Math.abs(mult - 1.0) > 0.001) {
            this.accumulatedDX *= mult;
            this.accumulatedDY *= mult;
        }
    }

    @Redirect(
            method = "turnPlayer(D)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;smoothCamera:Z", opcode = Opcodes.GETFIELD)
    )
    private boolean starclient$overrideZoomSmoothCamera(Options options) {
        if (ZoomState.isZoomHeldNow()) {
            boolean zoomCinematic = ZoomState.isCinematicCameraEnabled();
            if (!zoomCinematic && this.starclient$wasZoomCinematic) {
                this.smoothTurnX.reset();
                this.smoothTurnY.reset();
            }
            this.starclient$wasZoomCinematic = zoomCinematic;
            return zoomCinematic;
        }
        if (this.starclient$wasZoomCinematic) {
            this.smoothTurnX.reset();
            this.smoothTurnY.reset();
            this.starclient$wasZoomCinematic = false;
        }
        return options.smoothCamera;
    }
}
