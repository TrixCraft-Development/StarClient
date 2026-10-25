package net.modernclient.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.modernclient.ModernClient;
import net.modernclient.module.CustomCrosshairState;
import net.modernclient.module.Module;
import net.modernclient.setting.ColorModeSetting;
import net.modernclient.setting.NumberSetting;
import net.modernclient.setting.Setting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void hideVanillaEffects(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        ModernClient mod = ModernClient.getInstance();
        if (mod == null || mod.getModuleManager() == null) return;
        Module potions = mod.getModuleManager().getModuleByName("Potion Effects");
        if (potions != null && potions.isEnabled()) {
            Setting<?> hideVanilla = potions.getSetting("Hide Vanilla Icons");
            if (hideVanilla == null || (boolean) hideVanilla.getValue()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void renderCustomCrosshair(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!CustomCrosshairState.isActive()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.options.getCameraType().isFirstPerson()) {
            return;
        }
        ci.cancel();

        Module mod = CustomCrosshairState.getModule();
        if (mod == null) return;

        String shape = getString(mod, "Shape", "Cross");
        boolean useClientColor = getBool(mod, "Use Client Color", true);
        boolean centerDot = getBool(mod, "Center Dot", true);
        boolean outline = getBool(mod, "Outline", true);
        boolean dynamic = getBool(mod, "Dynamic", true);
        int size = (int) Math.round(getNum(mod, "Size", 4.0));
        int gap = (int) Math.round(getNum(mod, "Gap", 2.0));
        int thickness = (int) Math.round(getNum(mod, "Thickness", 1.0));

        if (dynamic) {
            double speed = Math.hypot(client.player.getX() - client.player.xo, client.player.getZ() - client.player.zo);
            if (speed > 0.08) gap += 2;
            if (client.player.getAttackStrengthScale(0.0F) < 0.9F) gap += 2;
        }

        int color = 0xFF38BDF8;
        if (useClientColor && ModernClient.getInstance() != null) {
            color = ModernClient.getInstance().getClientSettings().getMenuAccentColor();
        } else if (mod.getSetting("Color") instanceof ColorModeSetting cms) {
            color = cms.getColor();
        }
        color |= 0xFF000000;
        int outlineColor = 0xCC000000;

        int cx = extractor.guiWidth() / 2;
        int cy = extractor.guiHeight() / 2;
        int halfT = thickness / 2;

        if ("Dot".equals(shape) || "Cross + Dot".equals(shape) || centerDot) {
            if (outline) {
                extractor.fill(cx - halfT - 1, cy - halfT - 1, cx - halfT + thickness + 1, cy - halfT + thickness + 1, outlineColor);
            }
            extractor.fill(cx - halfT, cy - halfT, cx - halfT + thickness, cy - halfT + thickness, color);
        }

        if ("Cross".equals(shape) || "Cross + Dot".equals(shape)) {
            // Top
            drawRectWithOutline(extractor, cx - halfT, cy - gap - size, cx - halfT + thickness, cy - gap, color, outline, outlineColor);
            // Bottom
            drawRectWithOutline(extractor, cx - halfT, cy + gap + (thickness % 2), cx - halfT + thickness, cy + gap + (thickness % 2) + size, color, outline, outlineColor);
            // Left
            drawRectWithOutline(extractor, cx - gap - size, cy - halfT, cx - gap, cy - halfT + thickness, color, outline, outlineColor);
            // Right
            drawRectWithOutline(extractor, cx + gap + (thickness % 2), cy - halfT, cx + gap + (thickness % 2) + size, cy - halfT + thickness, color, outline, outlineColor);
        } else if ("Circle".equals(shape)) {
            int r = Math.max(2, gap + size);
            drawRectWithOutline(extractor, cx - r + 1, cy - r, cx + r, cy - r + thickness, color, outline, outlineColor);
            drawRectWithOutline(extractor, cx - r + 1, cy + r - thickness + 1, cx + r, cy + r + 1, color, outline, outlineColor);
            drawRectWithOutline(extractor, cx - r, cy - r + 1, cx - r + thickness, cy + r, color, outline, outlineColor);
            drawRectWithOutline(extractor, cx + r - thickness + 1, cy - r + 1, cx + r + 1, cy + r, color, outline, outlineColor);
        }
    }

    @Unique
    private static void drawRectWithOutline(GuiGraphicsExtractor extractor, int x1, int y1, int x2, int y2, int color, boolean outline, int outlineColor) {
        if (outline) {
            extractor.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, outlineColor);
        }
        extractor.fill(x1, y1, x2, y2, color);
    }

    @Unique
    private static String getString(Module mod, String name, String def) {
        Setting<?> s = mod.getSetting(name);
        return s == null ? def : (String) s.getValue();
    }

    @Unique
    private static boolean getBool(Module mod, String name, boolean def) {
        Setting<?> s = mod.getSetting(name);
        return s == null ? def : (boolean) s.getValue();
    }

    @Unique
    private static double getNum(Module mod, String name, double def) {
        return mod.getSetting(name) instanceof NumberSetting ns ? ns.getValue() : def;
    }
}
