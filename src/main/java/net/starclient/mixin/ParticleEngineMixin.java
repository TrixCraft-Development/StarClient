package net.starclient.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.starclient.module.OldAnimationsState;
import net.starclient.module.PerformanceState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Shadow
    private <T extends ParticleOptions> Particle makeParticle(
            T options,
            double x,
            double y,
            double z,
            double xa,
            double ya,
            double za) {
        return null;
    }

    @Unique
    private boolean starclient$insideCreateParticle;

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void starclient$filterParticleCreationHead(
            ParticleOptions options,
            double x,
            double y,
            double z,
            double xa,
            double ya,
            double za,
            CallbackInfoReturnable<Particle> cir) {
        if (OldAnimationsState.isDisableSweep() && options != null && options.getType() == ParticleTypes.SWEEP_ATTACK) {
            cir.setReturnValue(this.makeParticle(options, x, y, z, xa, ya, za));
            return;
        }
        if (PerformanceState.shouldSkipParticle(options)) {
            // Return the un-queued Particle instance without calling add(particle).
            // Callers like FireworkParticles$Starter dereference the returned Particle
            // immediately (e.g. sparkParticle.setTrail(...)) without a null check.
            cir.setReturnValue(this.makeParticle(options, x, y, z, xa, ya, za));
            return;
        }
        this.starclient$insideCreateParticle = true;
    }

    @Inject(method = "createParticle", at = @At("RETURN"))
    private void starclient$filterParticleCreationReturn(
            ParticleOptions options,
            double x,
            double y,
            double z,
            double xa,
            double ya,
            double za,
            CallbackInfoReturnable<Particle> cir) {
        this.starclient$insideCreateParticle = false;
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void starclient$filterDirectParticleAdd(Particle p, CallbackInfo ci) {
        if (!this.starclient$insideCreateParticle && PerformanceState.shouldSkipDirectParticle(p)) {
            ci.cancel();
        }
    }
}
