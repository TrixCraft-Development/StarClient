package net.starclient.mixin;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.network.chat.Component;
import net.starclient.gui.ColorFormattingBarWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the glassmorphic Color & Formatting Bar above Minecraft's Book & Quill screen.
 */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin extends Screen {

    @Shadow
    private MultiLineEditBox page;

    protected BookEditScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void starclient$addBookColorBar(CallbackInfo ci) {
        if (!ColorFormattingBarWidget.isEnabledFor("Show on Books")) return;

        int barX = Math.max(4, (this.width - ColorFormattingBarWidget.BAR_WIDTH) / 2);
        // Book GUI sits at y=2..192 and Sign/Done buttons sit at y=196..216
        int barY = (this.height >= 268) ? 220 : Math.max(4, this.height - ColorFormattingBarWidget.BAR_HEIGHT - 4);

        this.addRenderableWidget(new ColorFormattingBarWidget(
                barX,
                barY,
                true,
                code -> {
                    if (this.page != null) {
                        ((MultiLineEditBoxAccessor) this.page).starclient$getTextField().insertText(code);
                    }
                },
                () -> {
                    if (this.page != null) {
                        this.setFocused(this.page);
                        this.page.setFocused(true);
                    }
                }
        ));
    }
}
