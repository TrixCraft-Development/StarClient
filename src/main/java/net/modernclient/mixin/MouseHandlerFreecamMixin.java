package net.modernclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.modernclient.module.FreeCamState;
import net.modernclient.module.FreeLookState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerFreecamMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"), cancellable = true)
    private void cameraTurn(CallbackInfo ci) {
        if (!FreeCamState.isActive() && !FreeLookState.isActive()) return;

        double sensitivity = minecraft.options.sensitivity().get() * 0.6 + 0.2;
        double factor = sensitivity * sensitivity * sensitivity * 8.0 * 0.15;

        double dYaw = accumulatedDX * factor;
        double dPitch = accumulatedDY * factor;

        if (minecraft.options.invertMouseX().get()) dYaw = -dYaw;
        if (minecraft.options.invertMouseY().get()) dPitch = -dPitch;

        if (FreeCamState.isActive()) {
            FreeCamState.addRotation(dYaw, dPitch);
        } else {
            FreeLookState.addRotation(dYaw, dPitch);
        }

        accumulatedDX = 0.0;
        accumulatedDY = 0.0;
        ci.cancel();
    }
}
