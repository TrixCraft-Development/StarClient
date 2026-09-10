package net.modernclient.mixin;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.modernclient.module.FullbrightState;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {

    @Inject(method = "extract(Lnet/minecraft/client/renderer/state/LightmapRenderState;F)V", at = @At("TAIL"))
    private void modernclient$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (FullbrightState.isGammaActive()) {
            state.brightness = 1.0f;
            state.blockFactor = 3.0f;
            state.skyFactor = 3.0f;
            state.ambientColor = new Vector3f(1.0f, 1.0f, 1.0f);
            state.darknessEffectScale = 0.0f;
            state.nightVisionEffectIntensity = 0.0f;
            state.needsUpdate = true;
        }
    }
}
