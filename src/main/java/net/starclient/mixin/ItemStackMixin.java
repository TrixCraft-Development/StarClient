package net.starclient.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.starclient.module.GlintCustomizerState;
import net.starclient.module.HungerSaturationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @Shadow
    public abstract boolean isEmpty();

    @Inject(method = "hasFoil", at = @At("HEAD"), cancellable = true)
    private void starclient$customizeFoil(CallbackInfoReturnable<Boolean> cir) {
        if (GlintCustomizerState.isDisableAllGlint()) {
            cir.setReturnValue(false);
        } else if (GlintCustomizerState.isForceAllGlint()) {
            cir.setReturnValue(!this.isEmpty());
        }
    }

    @Inject(method = "getTooltipLines", at = @At("RETURN"), cancellable = true)
    private void starclient$appendFoodTooltipInfo(Item.TooltipContext context, Player player, TooltipFlag flag,
                                                  CallbackInfoReturnable<List<Component>> cir) {
        if (!HungerSaturationState.isFoodTooltipInfo() || this.isEmpty()) return;

        ItemStack self = (ItemStack) (Object) this;
        FoodProperties food = self.get(DataComponents.FOOD);
        if (food == null) return;

        List<Component> lines = cir.getReturnValue();
        if (lines == null) return;

        String shanksStr = (food.nutrition() % 2 == 0)
                ? String.valueOf(food.nutrition() / 2)
                : String.format(Locale.US, "%.1f", food.nutrition() / 2.0f);
        String satStr = String.format(Locale.US, "%.1f", food.saturation());

        Component infoLine = Component.empty()
                .append(Component.literal("🍖 +" + food.nutrition() + " Hunger").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" (" + shanksStr + " shanks) • ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("✦ +" + satStr + " Saturation").withStyle(ChatFormatting.YELLOW));

        try {
            lines.add(infoLine);
        } catch (UnsupportedOperationException e) {
            List<Component> copy = new ArrayList<>(lines);
            copy.add(infoLine);
            cir.setReturnValue(copy);
        }
    }
}
