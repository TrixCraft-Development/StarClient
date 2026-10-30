package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.starclient.gui.DamageIndicatorManager;
import net.starclient.stats.ClientStatsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    private static EntityDataAccessor<Float> DATA_HEALTH_ID;

    @Inject(method = "onSyncedDataUpdated", at = @At("HEAD"))
    private void onHealthDataUpdated(EntityDataAccessor<?> key, CallbackInfo ci) {
        if (key.equals(DATA_HEALTH_ID)) {
            try {
                DamageIndicatorManager.getInstance().onEntityHealthUpdate((LivingEntity) (Object) this);
            } catch (Throwable ignored) {
            }
        }
    }

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
        try {
            if (b == 3) {
                ClientStatsManager.getInstance().onEntityDied((LivingEntity) (Object) this);
            } else if (b == 2) {
                DamageIndicatorManager.getInstance().onEntityHurt((LivingEntity) (Object) this);
            }
        } catch (Throwable ignored) {
        }
    }
}
