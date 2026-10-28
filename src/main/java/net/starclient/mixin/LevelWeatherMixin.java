package net.starclient.mixin;

import net.minecraft.world.level.Level;
import net.starclient.module.WeatherChangerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelWeatherMixin {

    @Inject(method = "getRainLevel(F)F", at = @At("RETURN"), cancellable = true)
    private void weatherChangerRain(float a, CallbackInfoReturnable<Float> cir) {
        WeatherChangerState.modifyRain((Level) (Object) this, a, cir);
    }

    @Inject(method = "getThunderLevel(F)F", at = @At("RETURN"), cancellable = true)
    private void weatherChangerThunder(float a, CallbackInfoReturnable<Float> cir) {
        WeatherChangerState.modifyThunder((Level) (Object) this, a, cir);
    }
}