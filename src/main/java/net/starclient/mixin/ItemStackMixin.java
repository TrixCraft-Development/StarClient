package net.starclient.mixin;

import net.minecraft.world.item.ItemStack;
import net.starclient.module.GlintCustomizerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Shadow
    public abstract boolean isEmpty();

    @Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
    private void starclient$customizeFoil(CallbackInfoReturnable<Boolean> cir) {
        if (GlintCustomizerState.isDisableAllGlint()) {
            cir.setReturnValue(false);
        } else if (GlintCustomizerState.isForceAllGlint()) {
            cir.setReturnValue(!this.isEmpty());
        }
    }
}
