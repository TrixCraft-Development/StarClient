package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.starclient.stats.ClientStatsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void onItemCrafted(Player player, ItemStack carried, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player && carried != null && !carried.isEmpty()) {
            ClientStatsManager.getInstance().onItemCrafted(carried.getCount());
        }
    }
}
