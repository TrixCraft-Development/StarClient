package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.starclient.StarClient;
import net.starclient.gui.AccountSwitcherOverlay;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TitleScreen.class, priority = 2000)
public abstract class TitleScreenMixin {

    @Unique
    private final AccountSwitcherOverlay starclient$accountSwitcher = new AccountSwitcherOverlay();

    @ModifyArg(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"
            ),
            index = 1
    )
    private String replaceBottomLeftVersionText(String original) {
        if (original == null || !original.startsWith("Minecraft ")) {
            return original;
        }
        return "StarClient-" + StarClient.MOD_VERSION;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderClientName(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.font == null) return;

        String name = StarClient.CLIENT_NAME;
        float scale = 4.0f;
        int screenWidth = graphics.guiWidth();
        int scaledWidth = (int) (client.font.width(name) * scale);
        int x = (screenWidth - scaledWidth) / 2;
        int y = 72;

        int color = 0xFF38BDF8;
        StarClient starClient = StarClient.getInstance();
        if (starClient != null) {
            starClient.ensureConfigLoaded();
            if (starClient.getClientSettings() != null) {
                color = starClient.getClientSettings().getMenuAccentColor();
            }
        }

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(scale, scale);
        graphics.text(client.font, name, (int) (x / scale), (int) (y / scale), color);
        pose.popMatrix();

        String version = StarClient.CLIENT_VERSION;
        graphics.text(client.font, version, screenWidth / 2 - client.font.width(version) / 2, y + (int) (client.font.lineHeight * scale) + 4, 0xFF94A3B8);

        this.starclient$accountSwitcher.render(graphics, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void starclient$onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (this.starclient$accountSwitcher.mouseClicked((Screen) (Object) this, event)) {
            cir.setReturnValue(true);
        }
    }
}
