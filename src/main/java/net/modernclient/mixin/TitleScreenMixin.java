package net.modernclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.modernclient.ModernClient;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void modernclient$renderClientName(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.font == null) return;

        String name = ModernClient.CLIENT_NAME;
        float scale = 4.0f;
        int screenWidth = extractor.guiWidth();
        int scaledWidth = (int) (client.font.width(name) * scale);
        int x = (screenWidth - scaledWidth) / 2;
        int y = 72;

        Matrix3x2fStack pose = extractor.pose();
        pose.pushMatrix();
        pose.scale(scale, scale);
        extractor.text(client.font, name, (int) (x / scale), (int) (y / scale), 0xFF38BDF8);
        pose.popMatrix();

        String version = ModernClient.CLIENT_VERSION;
        extractor.text(client.font, version, screenWidth / 2 - client.font.width(version) / 2, y + (int) (client.font.lineHeight * scale) + 4, 0xFF94A3B8);
    }
}
