package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.starclient.StarClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void customWindowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(StarClient.CLIENT_NAME);
    }
}
