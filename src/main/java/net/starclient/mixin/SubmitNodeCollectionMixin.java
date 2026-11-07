package net.starclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.starclient.module.NametagsPlusState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin {

    @Unique
    private Vec3 starclient$currentNametagPos;

    @ModifyVariable(method = "submitNameTag", at = @At("HEAD"), argsOnly = true, name = "nameTagAttachment")
    private Vec3 starclient$captureNametagPos(Vec3 nameTagAttachment) {
        this.starclient$currentNametagPos = nameTagAttachment;
        return nameTagAttachment;
    }

    @Redirect(
            method = "submitNameTag",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V")
    )
    private void starclient$scaleNametag(PoseStack poseStack, float xScale, float yScale, float zScale) {
        float mult = NametagsPlusState.computeScaleMultiplier(this.starclient$currentNametagPos);
        poseStack.scale(xScale * mult, yScale * mult, zScale * mult);
    }

    @Redirect(
            method = "submitNameTag",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;color(FI)I")
    )
    private int starclient$modifyNametagBackground(float alpha, int rgb) {
        if (NametagsPlusState.isTransparentBackground()) {
            return 0;
        }
        return ARGB.color(alpha, rgb);
    }
}
