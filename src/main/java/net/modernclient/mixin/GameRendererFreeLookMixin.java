package net.modernclient.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.modernclient.module.FreeLookState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererFreeLookMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void hideHandDuringFreeLook(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
        if (FreeLookState.isActive()) {
            ci.cancel();
        }
    }
}
