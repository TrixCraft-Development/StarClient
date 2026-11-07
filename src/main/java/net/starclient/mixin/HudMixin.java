package net.starclient.mixin;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.starclient.StarClient;
import net.starclient.gui.BossBarRenderer;
import net.starclient.gui.HungerOverlayRenderer;
import net.starclient.gui.ScoreboardRenderer;
import net.starclient.gui.TabListRenderer;
import net.starclient.module.CustomCrosshairState;
import net.starclient.module.Module;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {

    @Shadow
    @Final
    private PlayerTabOverlay tabList;

    @Shadow
    @Final
    private BossHealthOverlay bossOverlay;

    @Shadow
    @Final
    private static Identifier FOOD_FULL_SPRITE;

    @Shadow
    @Final
    private static Identifier FOOD_HALF_SPRITE;

    @Inject(method = "extractFood", at = @At("HEAD"))
    private void starclient$renderExhaustionUnderlay(GuiGraphicsExtractor graphics, Player player, int y, int rightX, CallbackInfo ci) {
        HungerOverlayRenderer.renderExhaustionUnderlay(graphics, player, y, rightX);
    }

    @Inject(method = "extractFood", at = @At("TAIL"))
    private void starclient$renderSaturationAndFoodPreview(GuiGraphicsExtractor graphics, Player player, int y, int rightX, CallbackInfo ci) {
        HungerOverlayRenderer.renderSaturationAndPreview(graphics, player, y, rightX, FOOD_FULL_SPRITE, FOOD_HALF_SPRITE);
    }

    @Unique
    private static final Identifier CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE =
            Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
    @Unique
    private static final Identifier CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE =
            Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
    @Unique
    private static final Identifier CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE =
            Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void hideVanillaEffects(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        StarClient mod = StarClient.getInstance();
        if (mod == null || mod.getModuleManager() == null) return;
        Module potions = mod.getModuleManager().getModuleByName("Potion Effects");
        if (potions != null && potions.isEnabled()) {
            Setting<?> hideVanilla = potions.getSetting("Hide Vanilla Icons");
            if (hideVanilla == null || (boolean) hideVanilla.getValue()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void displayCustomScoreboard(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        StarClient mod = StarClient.getInstance();
        if (mod == null || mod.getModuleManager() == null) return;
        Module scoreboardMod = mod.getModuleManager().getModuleByName("Scoreboard");
        if (scoreboardMod != null && scoreboardMod.isEnabled()) {
            ScoreboardRenderer.render(graphics, objective, scoreboardMod);
            ci.cancel();
        }
    }

    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
    private void extractCustomBossOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        StarClient mod = StarClient.getInstance();
        if (mod == null || mod.getModuleManager() == null) return;
        Module bossBarMod = mod.getModuleManager().getModuleByName("Boss Bar");
        if (bossBarMod != null && bossBarMod.isEnabled()) {
            BossBarRenderer.render(graphics, this.bossOverlay, bossBarMod);
            ci.cancel();
        }
    }

    @Inject(method = "extractTabList", at = @At("HEAD"), cancellable = true)
    private void extractCustomTabList(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        StarClient mod = StarClient.getInstance();
        if (mod == null || mod.getModuleManager() == null) return;
        Module tabMod = mod.getModuleManager().getModuleByName("Tab");
        if (tabMod != null && tabMod.isEnabled()) {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;
            if (client.options.keyPlayerList.isDown()) {
                this.tabList.setVisible(true);
                graphics.nextStratum();
                Scoreboard scoreboard = client.level != null ? client.level.getScoreboard() : null;
                Objective objective = scoreboard != null ? scoreboard.getDisplayObjective(DisplaySlot.LIST) : null;
                TabListRenderer.render(graphics, this.tabList, scoreboard, objective, tabMod);
            } else {
                this.tabList.setVisible(false);
            }
            ci.cancel();
        }
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void renderCustomCrosshair(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!CustomCrosshairState.isActive()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.options.getCameraType().isFirstPerson()) {
            return;
        }
        if (client.gameMode != null && client.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.ENTITY) {
                return;
            }
        }
        ci.cancel();

        Module mod = CustomCrosshairState.getModule();
        if (mod == null) return;

        String shape = getString(mod);
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
        if (useClientColor && StarClient.getInstance() != null) {
            color = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        } else if (mod.getSetting("Color") instanceof ColorModeSetting cms) {
            color = cms.getColor();
        }
        color |= 0xFF000000;
        int outlineColor = 0xCC000000;

        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        int halfT = thickness / 2;
        int bottomOffset = 7;

        if ("Dot".equals(shape) || "Cross + Dot".equals(shape) || centerDot) {
            bottomOffset = Math.max(bottomOffset, halfT + thickness);
            if (outline) {
                graphics.fill(cx - halfT - 1, cy - halfT - 1, cx - halfT + thickness + 1, cy - halfT + thickness + 1, outlineColor);
            }
            graphics.fill(cx - halfT, cy - halfT, cx - halfT + thickness, cy - halfT + thickness, color);
        }

        if ("Cross".equals(shape) || "Cross + Dot".equals(shape)) {
            bottomOffset = Math.max(bottomOffset, gap + (thickness % 2) + size);
            // Top
            drawRectWithOutline(graphics, cx - halfT, cy - gap - size, cx - halfT + thickness, cy - gap, color, outline, outlineColor);
            // Bottom
            drawRectWithOutline(graphics, cx - halfT, cy + gap + (thickness % 2), cx - halfT + thickness, cy + gap + (thickness % 2) + size, color, outline, outlineColor);
            // Left
            drawRectWithOutline(graphics, cx - gap - size, cy - halfT, cx - gap, cy - halfT + thickness, color, outline, outlineColor);
            // Right
            drawRectWithOutline(graphics, cx + gap + (thickness % 2), cy - halfT, cx + gap + (thickness % 2) + size, cy - halfT + thickness, color, outline, outlineColor);
        } else if ("Circle".equals(shape)) {
            int r = Math.max(2, gap + size);
            bottomOffset = Math.max(bottomOffset, r);
            drawRectWithOutline(graphics, cx - r + 1, cy - r, cx + r, cy - r + thickness, color, outline, outlineColor);
            drawRectWithOutline(graphics, cx - r + 1, cy + r - thickness + 1, cx + r, cy + r + 1, color, outline, outlineColor);
            drawRectWithOutline(graphics, cx - r, cy - r + 1, cx - r + thickness, cy + r, color, outline, outlineColor);
            drawRectWithOutline(graphics, cx + r - thickness + 1, cy - r + 1, cx + r + 1, cy + r, color, outline, outlineColor);
        }

        renderAttackIndicator(graphics, client, mod, bottomOffset);
    }

    @Unique
    private static void renderAttackIndicator(GuiGraphicsExtractor graphics, Minecraft client, Module mod, int bottomOffset) {
        if (client.player == null) return;
        boolean enabled = getBool(mod, "Cooldown Indicator", true);
        if (!enabled) return;

        boolean syncSetting = client.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR;
        boolean forceShow = getBool(mod, "Always Show Cooldown", false);
        if (!syncSetting && !forceShow) return;

        float strength = client.player.getAttackStrengthScale(0.0F);
        boolean showFull = false;
        if (client.crosshairPickEntity instanceof LivingEntity && strength >= 1.0F) {
            showFull = client.player.getCurrentItemAttackStrengthDelay() > 5.0F && client.crosshairPickEntity.isAlive();
            ItemStack activeItem = client.player.getActiveItem();
            AttackRange attackRange = activeItem.get(DataComponents.ATTACK_RANGE);
            if (attackRange != null && client.hitResult != null) {
                showFull = showFull && attackRange.isInRange(client.player, client.hitResult.getLocation());
            }
        }

        int x = graphics.guiWidth() / 2 - 8;
        int y = graphics.guiHeight() / 2 - 7 + 16 + Math.max(0, bottomOffset - 7);

        if (showFull) {
            graphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_FULL_SPRITE, x, y, 16, 16);
        } else if (strength < 1.0F) {
            int progressWidth = (int) (strength * 17.0F);
            graphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_BACKGROUND_SPRITE, x, y, 16, 4);
            graphics.blitSprite(RenderPipelines.CROSSHAIR, CROSSHAIR_ATTACK_INDICATOR_PROGRESS_SPRITE, 16, 4, 0, 0, x, y, progressWidth, 4);
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
    private static String getString(Module mod) {
        Setting<?> s = mod.getSetting("Shape");
        return s == null ? "Cross" : (String) s.getValue();
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
