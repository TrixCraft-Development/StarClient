package net.modernclient.mixin;

import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.modernclient.module.TimeChangerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
public abstract class ClientClockManagerMixin {

    @Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true)
    private void starclient$timeChanger(Holder<WorldClock> definition, CallbackInfoReturnable<Long> cir) {
        TimeChangerState.modify(definition, cir);
    }
}