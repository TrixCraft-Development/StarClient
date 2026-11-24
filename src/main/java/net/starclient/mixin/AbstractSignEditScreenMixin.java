package net.starclient.mixin;

import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import net.starclient.gui.ColorFormattingBarWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the glassmorphic Color & Formatting Bar above Minecraft's Sign Editor screen.
 */
@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin extends Screen {

    @Shadow
    private TextFieldHelper signField;

    protected AbstractSignEditScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void starclient$addSignColorBar(CallbackInfo ci) {
        if (!ColorFormattingBarWidget.isEnabledFor("Show on Signs")) return;

        int barX = Math.max(4, (this.width - ColorFormattingBarWidget.BAR_WIDTH) / 2);
        int barY = 8;

        this.addRenderableWidget(new ColorFormattingBarWidget(
                barX,
                barY,
                false,
                code -> {
                    if (this.signField != null) {
                        this.signField.insertText(code);
                    }
                },
                () -> this.setFocused(null)
        ));
    }
}
