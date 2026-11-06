package net.starclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.starclient.module.FogCustomizerState;
import net.starclient.module.LowFireState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void starclient$disableWaterOverlay(Minecraft minecraft, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (FogCustomizerState.isDisableWaterFog()) {
            ci.cancel();
        }
    }

    @Inject(method = "submitFire", at = @At("HEAD"))
    private static void lowerFireHead(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (LowFireState.isActive()) {
            poseStack.pushPose();
            poseStack.translate(0.0F, LowFireState.getOffset(), 0.0F);
        }
    }

    @Inject(method = "submitFire", at = @At("RETURN"))
    private static void lowerFireTail(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (LowFireState.isActive()) {
            poseStack.popPose();
        }
    }
}
