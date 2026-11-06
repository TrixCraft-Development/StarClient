package net.starclient.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.starclient.module.PerformanceState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {

    @Shadow
    private Vec3 cameraPos;

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void starclient$cullBlockEntityByDistance(
            E blockEntity,
            float partialTicks,
            ModelFeatureRenderer.CrumblingOverlay breakProgress,
            boolean isGloballyRendered,
            CallbackInfoReturnable<S> cir) {
        if (PerformanceState.shouldSkipBlockEntity(blockEntity, this.cameraPos)) {
            cir.setReturnValue(null);
        }
    }
}
