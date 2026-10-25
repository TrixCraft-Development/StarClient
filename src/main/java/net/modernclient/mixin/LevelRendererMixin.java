package net.modernclient.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.modernclient.ModernClient;
import net.modernclient.module.BlockOutlineState;
import net.modernclient.module.Module;
import net.modernclient.setting.ColorModeSetting;
import net.modernclient.setting.NumberSetting;
import net.modernclient.setting.Setting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @ModifyVariable(method = "submitHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int customOutlineColor(int originalColor) {
        if (!BlockOutlineState.isActive()) return originalColor;
        Module mod = BlockOutlineState.getModule();
        if (mod == null) return originalColor;

        Setting<?> useClient = mod.getSetting("Use Client Color");
        boolean clientColor = useClient == null || (boolean) useClient.getValue();
        int rgb = 0xFF38BDF8;
        if (clientColor && ModernClient.getInstance() != null) {
            rgb = ModernClient.getInstance().getClientSettings().getMenuAccentColor();
        } else if (mod.getSetting("Color") instanceof ColorModeSetting cms) {
            rgb = cms.getColor();
        }

        double opacity = mod.getSetting("Opacity") instanceof NumberSetting ns ? ns.getValue() : 0.85;
        int alpha = Math.clamp((int) Math.round(opacity * 255.0), 0, 255);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    @ModifyVariable(method = "submitHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float customOutlineThickness(float originalLineWidth) {
        if (!BlockOutlineState.isActive()) return originalLineWidth;
        Module mod = BlockOutlineState.getModule();
        if (mod == null) return originalLineWidth;

        double mult = mod.getSetting("Thickness") instanceof NumberSetting ns ? ns.getValue() : 2.0;
        return originalLineWidth * (float) mult;
    }
}
