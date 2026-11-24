package net.starclient.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.starclient.module.SoundMufflerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Scales volume of sounds muffled by the "Sound Muffler" module when
 * Muffle Volume (%) is configured above 0%.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"), cancellable = true)
    private void starclient$scaleMuffledSoundVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float original = cir.getReturnValueF();
        float scaled = SoundMufflerState.scaleVolumeIfMuffled(sound, original);
        if (scaled != original) {
            cir.setReturnValue(scaled);
        }
    }
}
