package net.starclient.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import net.starclient.module.TooltipFeaturesState;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsExtractorMixin {

    @Shadow
    @Final
    private Matrix3x2fStack pose;

    @Shadow
    public abstract int guiWidth();

    @Shadow
    public abstract int guiHeight();

    @Inject(
            method = "tooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
            at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;pushMatrix()Lorg/joml/Matrix3x2fStack;", shift = At.Shift.AFTER)
    )
    private void starclient$translateTooltipScroll(Font font, List<ClientTooltipComponent> lines,
                                                   int xo, int yo, ClientTooltipPositioner positioner,
                                                   Identifier style, CallbackInfo ci) {
        if (!TooltipFeaturesState.isScrollableTooltipsEnabled() || lines == null || lines.isEmpty()) {
            return;
        }
        int maxW = 0;
        int totalH = lines.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent comp : lines) {
            maxW = Math.max(maxW, comp.getWidth(font));
            totalH += comp.getHeight(font);
        }
        TooltipFeaturesState.markTooltipDimensions(maxW, totalH, this.guiWidth(), this.guiHeight());

        float sx = TooltipFeaturesState.getScrollX();
        float sy = TooltipFeaturesState.getScrollY();
        if (Math.abs(sx) > 0.01f || Math.abs(sy) > 0.01f) {
            this.pose.translate(sx, sy);
        }
    }
}
