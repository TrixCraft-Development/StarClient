package net.starclient.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.starclient.module.HitColorState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin {

    @Shadow
    @Final
    private DynamicTexture texture;

    @Inject(method = "getTextureView", at = @At("HEAD"))
    private void updateHitColorTexture(CallbackInfoReturnable<GpuTextureView> cir) {
        if (!HitColorState.consumeDirty()) return;
        NativeImage pixels = this.texture.getPixels();
        if (pixels == null) return;
        int pixelColor = HitColorState.getOverlayPixelColor();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                pixels.setPixel(x, y, pixelColor);
            }
        }
        this.texture.upload();
    }
}
