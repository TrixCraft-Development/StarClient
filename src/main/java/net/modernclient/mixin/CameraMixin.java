package net.modernclient.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.modernclient.module.FreeCamState;
import net.modernclient.module.FreeLookState;
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

            invoker.modernclient$setPosition(FreeCamState.getX(), FreeCamState.getY(), FreeCamState.getZ());
            invoker.modernclient$setRotation(FreeCamState.getYaw(), FreeCamState.getPitch());
        } else if (FreeLookState.isActive()) {
            if (player == null) return;
            if (!FreeLookState.isInitialized()) {
                FreeLookState.setRotation(player.getYRot() + 180.0F, 0.0F);
                FreeLookState.setInitialized(true);
            }

            double distance = FREE_LOOK_DISTANCE;
            Vec3 eye = player.getEyePosition();
            double yawRad = Math.toRadians(FreeLookState.getYaw());
            double pitchRad = Math.toRadians(FreeLookState.getPitch());
            double cosPitch = Math.cos(pitchRad);

            double camX = eye.x + cosPitch * Math.sin(yawRad) * distance;
            double camY = eye.y + Math.sin(pitchRad) * distance;
            double camZ = eye.z + cosPitch * Math.cos(yawRad) * distance;

            invoker.modernclient$setPosition(camX, camY, camZ);

            double lookX = eye.x - camX;
            double lookY = eye.y - camY;
            double lookZ = eye.z - camZ;
            double horizontal = Math.sqrt(lookX * lookX + lookZ * lookZ);
            float lookYaw = (float) Math.toDegrees(Math.atan2(-lookX, lookZ));
            float lookPitch = (float) (-Math.toDegrees(Math.atan2(lookY, Math.max(horizontal, 1.0E-6))));
            invoker.modernclient$setRotation(lookYaw, lookPitch);

            detached = true;
        }
    }
}
