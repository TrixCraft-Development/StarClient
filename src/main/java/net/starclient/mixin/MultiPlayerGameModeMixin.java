package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.starclient.module.AutoToolState;
import net.starclient.module.CombatStatsTracker;
import net.starclient.module.FastBreakState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Shadow
    private float destroyProgress;

    @Inject(method = "startDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"))
    private void autoToolSelect(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!AutoToolState.isActive()) return;
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        Level level = client.level;
        if (player == null || level == null) return;
        if (player.getAbilities().instabuild) return;
        BlockState state = level.getBlockState(pos);
        Inventory inventory = player.getInventory();
        int current = inventory.getSelectedSlot();
        if (!AutoToolState.isMining()) {
            AutoToolState.setMining(true);
            AutoToolState.setPreviousSlot(current);
        }
        int best = findBestSlot(inventory, state, current);
        if (best != current) {
            inventory.setSelectedSlot(best);
        }
    }

    @Inject(method = "stopDestroyBlock()V", at = @At("HEAD"))
    private void autoToolRestore(CallbackInfo ci) {
        if (!AutoToolState.isActive() || !AutoToolState.isSwitchBack()) return;
        if (!AutoToolState.isMining()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.getInventory().setSelectedSlot(AutoToolState.getPreviousSlot());
        }
        AutoToolState.reset();
    }

    @Inject(method = "continueDestroyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z", at = @At("HEAD"))
    private void fastBreakBoost(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (!FastBreakState.isActive()) return;
        float speed = (float) FastBreakState.getSpeed();
        if (speed <= 1.0F) return;
        float progress = this.destroyProgress;
        if (progress < 0.001F) return;
        this.destroyProgress = Math.min(0.99F, progress + progress * (speed - 1.0F));
    }

    @Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void recordAttackStats(Player player, Entity entity, CallbackInfo ci) {
        CombatStatsTracker.onAttack(player, entity);
    }

    @Unique
    private static int findBestSlot(Inventory inventory, BlockState state, int current) {
        int best = current;
        float bestSpeed = inventory.getItem(current).getDestroySpeed(state);
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }
        return best;
    }
}
