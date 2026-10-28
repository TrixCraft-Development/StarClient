package net.starclient.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.starclient.StarClient;
import net.starclient.module.BlockOutlineState;
import net.starclient.module.Module;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @ModifyVariable(method = "submitHitOutline", at = @At("HEAD"), argsOnly = true, name = "color")
    private int customOutlineColor(int color) {
        if (!BlockOutlineState.isActive()) return color;
        Module mod = BlockOutlineState.getModule();
        if (mod == null) return color;

        Setting<?> useClient = mod.getSetting("Use Client Color");
        boolean clientColor = useClient == null || (boolean) useClient.getValue();
        int rgb = 0xFF38BDF8;
        if (clientColor && StarClient.getInstance() != null) {
            rgb = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        } else if (mod.getSetting("Color") instanceof ColorModeSetting cms) {
            rgb = cms.getColor();
        }

        double opacity = mod.getSetting("Opacity") instanceof NumberSetting ns ? ns.getValue() : 0.85;
        int alpha = Math.clamp((int) Math.round(opacity * 255.0), 0, 255);
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    @ModifyVariable(method = "submitHitOutline", at = @At("HEAD"), argsOnly = true, name = "width")
    private float customOutlineThickness(float width) {
        if (!BlockOutlineState.isActive()) return width;
        Module mod = BlockOutlineState.getModule();
        if (mod == null) return width;

        double mult = mod.getSetting("Thickness") instanceof NumberSetting ns ? ns.getValue() : 2.0;
        return width * (float) mult;
    }
}
