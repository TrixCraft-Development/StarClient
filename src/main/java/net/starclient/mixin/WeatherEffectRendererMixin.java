package net.starclient.mixin;

import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import net.starclient.module.PerformanceState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void starclient$disableWeatherRendering(
            Vec3 cameraPos,
            WeatherRenderState renderState,
            CallbackInfo ci) {
        if (PerformanceState.isDisableWeatherRender()) {
            ci.cancel();
        }
    }
}
