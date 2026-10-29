package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.starclient.stats.ClientStatsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void onCompleteUsingItem(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && (Object) this == client.player) {
            LivingEntity self = (LivingEntity) (Object) this;
            ItemStack stack = self.getUseItem();
            if (!stack.isEmpty()) {
                ItemUseAnimation anim = stack.getUseAnimation();
                if (anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK) {
                    ClientStatsManager.getInstance().onEat();
                }
            }
        }
    }

    @Inject(method = "onEquippedItemBroken", at = @At("HEAD"))
    private void onEquippedItemBroken(Item item, EquipmentSlot slot, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && (Object) this == client.player) {
            ClientStatsManager.getInstance().onItemBroken();
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void onHandleEntityEvent(byte b, CallbackInfo ci) {
        if (b == 3) {
            ClientStatsManager.getInstance().onEntityDied((LivingEntity) (Object) this);
        }
    }
}
