package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.starclient.stats.ClientStatsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void onBlockPlaced(BlockPlaceContext placeContext, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue().consumesAction()) {
            Player player = placeContext.getPlayer();
            Minecraft mc = Minecraft.getInstance();
            if (player != null && player == mc.player) {
                ClientStatsManager.getInstance().onBlockPlaced();
            }
        }
    }
}
