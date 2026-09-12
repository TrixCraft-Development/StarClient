package net.modernclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.modernclient.ModernClient;
import net.modernclient.module.NickToolState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class NickTagMixin<T extends Entity> {

    @Inject(method = "getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void rewriteAndDecorateNametag(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (!(entity instanceof Player)) return;
        Component name = cir.getReturnValue();
        if (name == null) return;
        Component display = NickToolState.rewriteNames(name);
        if (!display.getString().equals(name.getString())) {
            NickToolState.debug("nametag \"" + name.getString() + "\" -> \"" + display.getString() + "\"");
            cir.setReturnValue(display);
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && entity == client.player && ModernClient.getInstance().getClientSettings().isTabIcon()) {
            Component icon = Component.literal("⭐").withStyle(style -> style.withColor(0xFFD700));
            cir.setReturnValue(Component.literal("").append(icon).append(Component.literal(" ")).append(display));
        }
    }
}