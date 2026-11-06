package net.starclient.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.starclient.module.FogCustomizerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Inject(method = "getFogType", at = @At("RETURN"), cancellable = true)
    private void starclient$overrideFluidFogType(Camera camera, CallbackInfoReturnable<FogType> cir) {
        if (FogCustomizerState.shouldReplaceFluidFogType(cir.getReturnValue())) {
            cir.setReturnValue(FogType.ATMOSPHERIC);
        }
    }

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void starclient$customizeFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker,
                                         float darkenWorldAmount, ClientLevel level,
                                         CallbackInfoReturnable<FogData> cir) {
        FogCustomizerState.modifyFog(camera, cir.getReturnValue());
    }
}
