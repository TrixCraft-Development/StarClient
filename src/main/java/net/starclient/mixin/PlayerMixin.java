package net.starclient.mixin;

import net.minecraft.world.entity.player.Player;
import net.starclient.module.OldAnimationsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "isStayingOnGroundSurface()Z", at = @At("HEAD"))
    private void safeWalk(CallbackInfoReturnable<Boolean> cir) {
    }

    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void starclient$hideAttackStrengthScale(float adjustTicks, CallbackInfoReturnable<Float> cir) {
        if (OldAnimationsState.isHideCooldown()) {
            cir.setReturnValue(1.0F);
        }
    }
}
