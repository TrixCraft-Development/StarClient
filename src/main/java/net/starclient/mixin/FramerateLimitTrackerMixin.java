package net.starclient.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import net.starclient.module.PerformanceState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void starclient$limitUnfocusedFramerate(CallbackInfoReturnable<Integer> cir) {
        if (PerformanceState.isLimitUnfocusedFps()
                && this.minecraft != null
                && !this.minecraft.isWindowActive()) {
            int limit = PerformanceState.getUnfocusedFpsLimit();
            if (cir.getReturnValue() > limit) {
                cir.setReturnValue(limit);
            }
        }
    }
}
