package net.modernclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.modernclient.ModernClient;
import net.modernclient.module.NickToolState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class NickTabMixin {

    @Inject(method = "getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void rewriteAndDecorateTabName(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        Component name = cir.getReturnValue();
        if (name == null) return;
        Component display = NickToolState.rewriteNames(name);
        if (!display.getString().equals(name.getString())) {
            cir.setReturnValue(display);
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && ModernClient.getInstance().getClientSettings().isTabIcon()
                && info.getProfile().id().equals(client.player.getUUID())) {
            Component icon = Component.literal("⭐").withStyle(style -> style.withColor(0xFFD700));
            cir.setReturnValue(Component.literal("").append(icon).append(Component.literal(" ")).append(display));
        }
    }
}