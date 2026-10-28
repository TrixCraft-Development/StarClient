package net.starclient.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.starclient.StarClient;
import net.starclient.module.FreeLookState;
import net.starclient.module.MotionBlurState;
import net.starclient.module.NoHurtCamState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererFreeLookMixin {

    @Unique
    private static final Identifier[] MB_TIERS = {
            Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "motion_blur_1"),
            Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "motion_blur_2"),
            Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "motion_blur_3"),
            Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "motion_blur_4"),
            Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "motion_blur_5")
    };

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private RenderTarget mainRenderTarget;

    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;

    @Shadow
    @Final
    private Camera mainCamera;

    @Unique
    private float starclient$prevYaw;
    @Unique
    private float starclient$prevPitch;
    @Unique
    private float starclient$smoothedDegPerSec;
    @Unique
    private long starclient$lastFrameNanos;

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void hideHandDuringFreeLook(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix, CallbackInfo ci) {
        if (FreeLookState.isActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void adjustHurtCam(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (!NoHurtCamState.isActive() || cameraState.entityRenderState == null) {
            return;
        }
        if (cameraState.entityRenderState.isDeadOrDying) {
            return;
        }
        double mult = NoHurtCamState.getMultiplier();
        if (mult <= 0.001) {
            ci.cancel();
        } else {
            cameraState.entityRenderState.hurtTime *= (float) mult;
        }
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void starclient$applyMotionBlur(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!MotionBlurState.isActive() || this.minecraft.level == null || this.mainCamera == null) {
            this.starclient$smoothedDegPerSec = 0.0F;
            this.starclient$lastFrameNanos = 0L;
            return;
        }

        long nowNanos = System.nanoTime();
        float yaw = this.mainCamera.yRot();
        float pitch = this.mainCamera.xRot();

        if (this.starclient$lastFrameNanos == 0L) {
            this.starclient$lastFrameNanos = nowNanos;
            this.starclient$prevYaw = yaw;
            this.starclient$prevPitch = pitch;
            return;
        }

        float dt = Math.clamp((nowNanos - this.starclient$lastFrameNanos) / 1_000_000_000.0F, 0.0005F, 0.1F);
        this.starclient$lastFrameNanos = nowNanos;

        float dYaw = Math.abs(Mth.wrapDegrees(yaw - this.starclient$prevYaw));
        float dPitch = Math.abs(pitch - this.starclient$prevPitch);
        this.starclient$prevYaw = yaw;
        this.starclient$prevPitch = pitch;

        // Angular speed in degrees per second (FPS-independent)
        float degPerSec = (dYaw + dPitch) / dt;
        float blend = 1.0F - (float) Math.exp(-dt * 22.0F);
        this.starclient$smoothedDegPerSec = Mth.lerp(blend, this.starclient$smoothedDegPerSec, degPerSec);

        if (this.starclient$smoothedDegPerSec < 35.0F) {
            return;
        }

        // Map camera rotation speed (35..240 deg/s) up to the user's selected Strength tier (1..5)
        int maxTier = Math.clamp((int) Math.round(MotionBlurState.getStrength()), 1, MB_TIERS.length);
        float speedFactor = Math.clamp((this.starclient$smoothedDegPerSec - 35.0F) / 205.0F, 0.0F, 1.0F);
        int tierIndex = Math.clamp(Math.round(speedFactor * (maxTier - 1)), 0, maxTier - 1);

        Identifier chainId = MB_TIERS[tierIndex];
        try {
            PostChain chain = this.minecraft.getShaderManager().getPostChain(chainId, LevelTargetBundle.MAIN_TARGETS);
            if (chain != null) {
                chain.process(this.mainRenderTarget, this.resourcePool);
            }
        } catch (Exception ignored) {
        }
    }
}
