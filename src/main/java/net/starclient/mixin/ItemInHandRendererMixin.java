package net.starclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.starclient.module.HandCustomizerState;
import net.starclient.module.LowShieldState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Unique
    private boolean starclient$pushedLowShield;

    @Unique
    private boolean starclient$pushedHandCustom;

    @Unique
    private static boolean starclient$shouldModifyShield(LivingEntity entity, ItemStack stack, ItemDisplayContext context) {
        if (!LowShieldState.isActive()) return false;
        if (context == null || !context.firstPerson()) return false;
        if (stack == null || stack.isEmpty()) return false;
        if (!stack.is(Items.SHIELD) && !(stack.getItem() instanceof ShieldItem)) return false;

        if (!LowShieldState.isLowerWhenBlocking() && entity != null && entity.isUsingItem() && entity.getUseItem() == stack) {
            return false;
        }

        return true;
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void onSubmitArmWithItemHead(AbstractClientPlayer player, float partialTicks, float pitch,
                                         InteractionHand hand, float swingProgress, ItemStack stack,
                                         float equipProgress, PoseStack poseStack,
                                         SubmitNodeCollector submitNodeCollector, int packedLight, CallbackInfo ci) {
        if (HandCustomizerState.isActive()) {
            poseStack.pushPose();
            HandCustomizerState.applyTransform(poseStack, hand == InteractionHand.MAIN_HAND);
            starclient$pushedHandCustom = true;
        } else {
            starclient$pushedHandCustom = false;
        }
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void onSubmitArmWithItemReturn(AbstractClientPlayer player, float partialTicks, float pitch,
                                           InteractionHand hand, float swingProgress, ItemStack stack,
                                           float equipProgress, PoseStack poseStack,
                                           SubmitNodeCollector submitNodeCollector, int packedLight, CallbackInfo ci) {
        if (starclient$pushedHandCustom) {
            poseStack.popPose();
            starclient$pushedHandCustom = false;
        }
    }

    @Inject(method = "renderItem", at = @At("HEAD"))
    private void onRenderItemHead(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                  PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, CallbackInfo ci) {
        if (starclient$shouldModifyShield(entity, itemStack, displayContext)) {
            poseStack.pushPose();
            float y = LowShieldState.getHeightOffset();
            float x = LowShieldState.getSideOffset();
            float sign = displayContext.leftHand() ? -1.0F : 1.0F;
            poseStack.translate(x * sign, y, 0.0F);

            float scale = LowShieldState.getScale();
            if (Math.abs(scale - 1.0F) > 0.001F) {
                poseStack.scale(scale, scale, scale);
            }
            starclient$pushedLowShield = true;
        } else {
            starclient$pushedLowShield = false;
        }
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void onRenderItemReturn(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                    PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, CallbackInfo ci) {
        if (starclient$pushedLowShield) {
            poseStack.popPose();
            starclient$pushedLowShield = false;
        }
    }
}
