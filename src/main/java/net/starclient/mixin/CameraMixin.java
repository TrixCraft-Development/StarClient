package net.starclient.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.starclient.module.FreeCamState;
import net.starclient.module.FreeLookState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Unique
    private static final double FREE_LOOK_DISTANCE = 4.0;

    @Shadow
    private boolean detached;

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void cameraOverride(DeltaTracker deltaTracker, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        CameraInvoker invoker = (CameraInvoker) this;

        if (FreeCamState.isActive()) {
            if (!FreeCamState.isInitialized()) {
                if (player == null) return;
                FreeCamState.setPosition(player.getX(), player.getY() + player.getEyeHeight(), player.getZ());
                FreeCamState.setRotation(player.getYRot(), player.getXRot());
                FreeCamState.setInitialized(true);
            }

            invoker.starclient$setPosition(FreeCamState.getX(), FreeCamState.getY(), FreeCamState.getZ());
            invoker.starclient$setRotation(FreeCamState.getYaw(), FreeCamState.getPitch());
        } else if (FreeLookState.isActive()) {
            if (player == null) return;
            if (!FreeLookState.isInitialized()) {
                FreeLookState.setRotation(player.getYRot() + 180.0F, 0.0F);
                FreeLookState.setInitialized(true);
            }

            float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
            Vec3 eye = player.getEyePosition(partialTick);

            double distance = FREE_LOOK_DISTANCE;
            double yawRad = Math.toRadians(FreeLookState.getYaw());
            double pitchRad = Math.toRadians(FreeLookState.getPitch());
            double cosPitch = Math.cos(pitchRad);

            // Offset direction matches Minecraft's calculateViewVector convention:
            // x = -sin(yaw) * cos(pitch), y = -sin(pitch), z = cos(yaw) * cos(pitch)
            double offX = -Math.sin(yawRad) * cosPitch;
            double offY = -Math.sin(pitchRad);
            double offZ = Math.cos(yawRad) * cosPitch;

            double camX = eye.x + offX * distance;
            double camY = eye.y + offY * distance;
            double camZ = eye.z + offZ * distance;

            invoker.starclient$setPosition(camX, camY, camZ);

            Vec3 toPlayer = eye.subtract(camX, camY, camZ).normalize();
            float lookYaw = (float) Math.toDegrees(Math.atan2(-toPlayer.x, toPlayer.z));
            float lookPitch = (float) Math.toDegrees(Math.asin(-toPlayer.y));
            invoker.starclient$setRotation(lookYaw, lookPitch);

            detached = true;
        }
    }
}
