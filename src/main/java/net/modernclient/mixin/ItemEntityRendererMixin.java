package net.modernclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.modernclient.module.ItemEntityRenderStateDuck;
import net.modernclient.module.ItemPhysicsState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin extends EntityRenderer<ItemEntity, ItemEntityRenderState> {

    @Shadow
    @Final
    private RandomSource random;

    @Shadow
    public static void submitMultipleFromCount(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            ItemClusterRenderState state,
            RandomSource random,
            AABB aabb
    ) {
    }

    protected ItemEntityRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void starclient$extractOnGround(ItemEntity entity, ItemEntityRenderState state, float partialTicks, CallbackInfo ci) {
        ((ItemEntityRenderStateDuck) state).starclient$setOnGround(entity.onGround());
    }

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void starclient$renderItemPhysics(
            ItemEntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState,
            CallbackInfo ci
    ) {
        if (!ItemPhysicsState.isActive()) {
            return;
        }
        if (state.item.isEmpty()) {
            ci.cancel();
            return;
        }

        poseStack.pushPose();
        AABB aabb = state.item.getModelBoundingBox();
        boolean onGround = ((ItemEntityRenderStateDuck) state).starclient$isOnGround();
        float speed = (float) ItemPhysicsState.getRotationSpeed();

        if (onGround) {
            // Lie flat on the ground with a consistent random yaw based on bobOffset
            poseStack.translate(0.0F, 0.04F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.bobOffset * 57.295776F));
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        } else {
            // Tumble in 3D while airborne
            float tumble = (state.ageInTicks * 14.0F * speed) + (state.bobOffset * 57.295776F);
            poseStack.translate(0.0F, 0.12F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(tumble));
            poseStack.mulPose(Axis.XP.rotationDegrees(tumble * 1.3F));
        }

        submitMultipleFromCount(poseStack, collector, state.lightCoords, state, this.random, aabb);
        poseStack.popPose();
        super.submit(state, poseStack, collector, cameraState);
        ci.cancel();
    }
}
