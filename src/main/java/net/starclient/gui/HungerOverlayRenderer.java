package net.starclient.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.starclient.mixin.FoodDataAccessor;
import net.starclient.module.HungerSaturationState;

/**
 * Renders AppleSkin-style exhaustion bars, golden saturation outlines,
 * and pulsing food hunger/saturation previews over the vanilla hunger bar.
 */
public final class HungerOverlayRenderer {

    private HungerOverlayRenderer() {
    }

    public static void renderExhaustionUnderlay(GuiGraphicsExtractor graphics, Player player, int y, int rightX) {
        if (!HungerSaturationState.isShowExhaustionBar() || player == null) return;

        FoodData foodData = player.getFoodData();
        float exhaustion = ((FoodDataAccessor) foodData).starclient$getExhaustionLevel();
        float ratio = Math.clamp(exhaustion / 4.0f, 0.0f, 1.0f);
        if (ratio <= 0.005f) return;

        int maxBarW = 81;
        int filledW = Math.round(maxBarW * ratio);
        if (filledW <= 0) return;

        // Hunger bar grows right-to-left from rightX to (rightX - 81)
        int leftEdge = rightX - filledW;
        // Subtle dark track + warm amber/gray exhaustion fill behind the 10 shanks
        graphics.fill(rightX - maxBarW, y + 7, rightX, y + 9, 0x44000000);
        graphics.fill(leftEdge, y + 7, rightX, y + 9, 0x8894A3B8);
    }

    public static void renderSaturationAndPreview(GuiGraphicsExtractor graphics, Player player, int y, int rightX,
                                                  Identifier fullFoodSprite, Identifier halfFoodSprite) {
        if (!HungerSaturationState.isEnabled() || player == null) return;

        FoodData foodData = player.getFoodData();
        int currentFood = foodData.getFoodLevel();
        float currentSat = foodData.getSaturationLevel();

        // 1. Current Saturation golden outline per shank
        if (HungerSaturationState.isShowSaturation() && currentSat > 0.0f) {
            int satUnits = Math.min(20, (int) Math.ceil(currentSat));
            for (int i = 0; i < 10; i++) {
                int shankThreshold = i * 2;
                if (satUnits <= shankThreshold) break;
                int iconX = rightX - i * 8 - 9;
                boolean fullShankSat = satUnits >= shankThreshold + 2;
                drawSaturationRim(graphics, iconX, y, fullShankSat, 0xE6FACC15);
            }
        }

        // 2. Pulsing Food Restoration Preview when holding edible food
        if (HungerSaturationState.isShowFoodPreview()) {
            FoodProperties heldFood = resolveHeldFood(player);
            if (heldFood != null && (currentFood < 20 || heldFood.canAlwaysEat())) {
                float wave = (float) ((Math.sin(System.currentTimeMillis() / 220.0) + 1.0) * 0.5);
                float pulseAlpha = 0.25f + wave * 0.55f;

                int previewFood = Math.min(20, currentFood + heldFood.nutrition());
                float previewSat = Math.min((float) previewFood, currentSat + heldFood.saturation());

                // Draw pulsing restored hunger shanks
                if (previewFood > currentFood) {
                    for (int i = currentFood / 2; i < (previewFood + 1) / 2; i++) {
                        int iconX = rightX - i * 8 - 9;
                        Identifier sprite = (i * 2 + 1 < previewFood) ? fullFoodSprite : halfFoodSprite;
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, iconX, y, 9, 9, pulseAlpha);
                    }
                }

                // Draw pulsing restored saturation golden rim
                if (HungerSaturationState.isShowSaturation() && previewSat > currentSat) {
                    int startShank = (int) (currentSat / 2.0f);
                    int previewSatUnits = Math.min(20, (int) Math.ceil(previewSat));
                    int alphaByte = Math.clamp(Math.round(pulseAlpha * 255.0f), 40, 230);
                    int previewGold = (alphaByte << 24) | 0x00FDE047;
                    for (int i = startShank; i < 10; i++) {
                        int shankThreshold = i * 2;
                        if (previewSatUnits <= shankThreshold) break;
                        int iconX = rightX - i * 8 - 9;
                        boolean fullShankSat = previewSatUnits >= shankThreshold + 2;
                        drawSaturationRim(graphics, iconX, y, fullShankSat, previewGold);
                    }
                }
            }
        }
    }

    private static FoodProperties resolveHeldFood(Player player) {
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty()) {
            FoodProperties fp = main.get(DataComponents.FOOD);
            if (fp != null) return fp;
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty()) {
            return off.get(DataComponents.FOOD);
        }
        return null;
    }

    /**
     * Draws a crisp AppleSkin-inspired golden saturation border around a 9x9 hunger shank.
     * Full saturation wraps the whole shank; half saturation wraps the right half (matching vanilla right-to-left shanks).
     */
    private static void drawSaturationRim(GuiGraphicsExtractor g, int x, int y, boolean full, int color) {
        int left = full ? x : (x + 4);
        int right = x + 9;
        // Top & bottom golden bars
        g.fill(left + 1, y, right - 1, y + 1, color);
        g.fill(left + 1, y + 8, right - 1, y + 9, color);
        // Left & right vertical golden edges
        g.fill(left, y + 1, left + 1, y + 8, color);
        g.fill(right - 1, y + 1, right, y + 8, color);
    }
}
