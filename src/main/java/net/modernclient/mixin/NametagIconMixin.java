package net.modernclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class NametagIconMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void prependNametagIcon(Entity entity, CallbackInfoReturnable<Component> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && entity == client.player) {
            Component name = cir.getReturnValue();
            if (name != null) {
                Component icon = Component.literal("⭐").withStyle(style -> style.withColor(0xFFD700));
                Component result = Component.literal("").append(icon).append(Component.literal(" ")).append(name);
                cir.setReturnValue(result);
            }
        }
    }
}