package net.starclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.starclient.module.HandCustomizerState;
import net.starclient.module.LowShieldState;
import net.starclient.module.OldAnimationsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Unique
    private boolean starclient$pushedLowShield;

    @Unique
    private boolean starclient$pushedHandCustom;

    @Unique
    private boolean starclient$pushedBlockHit;

    @Unique
    private boolean starclient$pushedRodBow;

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

    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void starclient$shouldInstantlyReplace(ItemStack current, ItemStack next, CallbackInfoReturnable<Boolean> cir) {
        if (OldAnimationsState.isOldReequip()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void starclient$tickReequip(CallbackInfo ci) {
        if (OldAnimationsState.isOldReequip()) {
            this.mainHandHeight = 1.0F;
            this.oMainHandHeight = 1.0F;
        }
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void starclient$onSwingArm(float swingProgress, PoseStack poseStack, int side, HumanoidArm arm, CallbackInfo ci) {
        if (OldAnimationsState.isBlockingWithSword()) {
            ci.cancel();
        }
    }

    @Inject(method = "applyItemArmAttackTransform", at = @At("HEAD"), cancellable = true)
    private void starclient$onApplyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float swingProgress, CallbackInfo ci) {
        if (OldAnimationsState.isOldSwing() && !OldAnimationsState.isBlockingWithSword()) {
            float side = (arm == HumanoidArm.RIGHT) ? 1.0F : -1.0F;
            float f = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
            poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0F + f * -20.0F)));
            float f1 = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * f1 * -20.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * -30.0F));
            ci.cancel();
        }
    }

    @Inject(method = "applyEatTransform", at = @At("HEAD"), cancellable = true)
    private void starclient$onApplyEatTransform(PoseStack poseStack, float partialTicks, HumanoidArm arm, ItemStack stack, Player player, CallbackInfo ci) {
        if (OldAnimationsState.isOldEating()) {
            float remaining = (float) player.getUseItemRemainingTicks() - partialTicks + 1.0F;
            float bob = Mth.abs(Mth.cos(remaining / 4.0F * (float) Math.PI) * 0.1F);
            poseStack.translate(0.0F, bob, 0.0F);
            ci.cancel();
        }
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void onSubmitArmWithItemHead(AbstractClientPlayer player, float partialTicks, float pitch,
                                         InteractionHand hand, float swingProgress, ItemStack stack,
                                         float equipProgress, PoseStack poseStack,
                                         SubmitNodeCollector submitNodeCollector, int packedLight, CallbackInfo ci) {
        if (OldAnimationsState.isHideOffhand() && hand == InteractionHand.OFF_HAND) {
            ci.cancel();
            return;
        }
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
        starclient$pushedLowShield = false;
        starclient$pushedBlockHit = false;
        starclient$pushedRodBow = false;

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
        }

        if (displayContext != null && displayContext.firstPerson() && entity == Minecraft.getInstance().player) {
            if (OldAnimationsState.isBlockingWithSword() && OldAnimationsState.isSword(itemStack)) {
                poseStack.pushPose();
                float side = displayContext.leftHand() ? -1.0F : 1.0F;

                boolean alreadyBlocking = (entity instanceof AbstractClientPlayer p)
                        && p.isUsingItem()
                        && itemStack.getUseAnimation() == ItemUseAnimation.BLOCK;

                if (!alreadyBlocking) {
                    poseStack.translate(side * -0.14142136F, 0.08F, 0.14142136F);
                    poseStack.mulPose(Axis.XP.rotationDegrees(-102.25F));
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * 13.365F));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(side * 78.05F));
                }

                float swingProgress = entity.getAttackAnim(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
                if (swingProgress > 0.0F) {
                    float f = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
                    float f1 = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                    poseStack.mulPose(Axis.XP.rotationDegrees(-f1 * 35.0F));
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * f1 * 30.0F));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(side * f * -15.0F));
                }
                starclient$pushedBlockHit = true;
            }

            if (OldAnimationsState.isOldRodAndBow()) {
                if (itemStack.getItem() instanceof FishingRodItem) {
                    poseStack.pushPose();
                    float side = displayContext.leftHand() ? -1.0F : 1.0F;
                    poseStack.translate(side * 0.08F, 0.05F, -0.05F);
                    poseStack.mulPose(Axis.XP.rotationDegrees(10.0F));
                    starclient$pushedRodBow = true;
                } else if (itemStack.getItem() instanceof BowItem && entity.isUsingItem()) {
                    poseStack.pushPose();
                    float side = displayContext.leftHand() ? -1.0F : 1.0F;
                    poseStack.mulPose(Axis.YP.rotationDegrees(side * -15.0F));
                    poseStack.mulPose(Axis.XP.rotationDegrees(10.0F));
                    starclient$pushedRodBow = true;
                }
            }
        }
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void onRenderItemReturn(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext,
                                    PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, CallbackInfo ci) {
        if (starclient$pushedRodBow) {
            poseStack.popPose();
            starclient$pushedRodBow = false;
        }
        if (starclient$pushedBlockHit) {
            poseStack.popPose();
            starclient$pushedBlockHit = false;
        }
        if (starclient$pushedLowShield) {
            poseStack.popPose();
            starclient$pushedLowShield = false;
        }
    }
}
