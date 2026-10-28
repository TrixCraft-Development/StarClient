package net.starclient.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditBox.class)
public abstract class EditBoxMixin {

    @Shadow
    private int textY;

    @Inject(method = "updateTextPosition", at = @At("TAIL"))
    private void centerTextY(CallbackInfo ci) {
        this.textY = ((EditBox) (Object) this).getY() + (((EditBox) (Object) this).getHeight() - 8) / 2;
    }
}
