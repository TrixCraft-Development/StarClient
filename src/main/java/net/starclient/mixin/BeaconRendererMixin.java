package net.starclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.resources.Identifier;
import net.starclient.module.PerformanceState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconRenderer.class)
public class BeaconRendererMixin {

    @Inject(
            method = "submitBeaconBeam(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/resources/Identifier;FFIIIFF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void starclient$disableBeaconBeam(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            Identifier beamLocation,
            float scale,
            float animationTime,
            int beamStart,
            int height,
            int color,
            float solidBeamRadius,
            float beamGlowRadius,
            CallbackInfo ci) {
        if (PerformanceState.isDisableBeaconBeams()) {
            ci.cancel();
        }
    }
}
