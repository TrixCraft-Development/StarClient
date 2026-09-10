package net.modernclient.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraInvoker {

    @Invoker("setPosition")
    void modernclient$setPosition(double x, double y, double z);

    @Invoker("setRotation")
    void modernclient$setRotation(float yaw, float pitch);
}
