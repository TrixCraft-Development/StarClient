package net.starclient.mixin;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.starclient.stats.ClientStatsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {

    @Inject(method = "onCreate", at = @At("HEAD"))
    private void onWorldCreated(CallbackInfo ci) {
        ClientStatsManager.getInstance().onWorldCreated();
    }
}
