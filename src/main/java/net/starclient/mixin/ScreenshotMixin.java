package net.starclient.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.starclient.module.BetterScreenshotsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.function.Consumer;

@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {

    @Inject(
            method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void starclient$onGrab(File workDir, String forceName, RenderTarget target,
                                          int downscaleFactor, Consumer<Component> callback, CallbackInfo ci) {
        if (!BetterScreenshotsState.isEnabled()) {
            return;
        }
        Screenshot.takeScreenshot(target, downscaleFactor,
                image -> BetterScreenshotsState.handleCapturedScreenshot(workDir, forceName, image, callback));
        ci.cancel();
    }
}
