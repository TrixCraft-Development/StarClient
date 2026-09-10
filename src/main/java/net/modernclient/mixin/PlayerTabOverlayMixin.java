package net.modernclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {

    @Inject(method = "getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void modernclient$prependStar(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        Minecraft client = Minecraft.getInstance();
        Component name = cir.getReturnValue();
        if (client.player != null && name != null && info.getProfile().id().equals(client.player.getUUID())) {
            Component icon = Component.literal("\u2B50").withStyle(style -> style.withColor(0xFFD700));
            cir.setReturnValue(Component.literal("").append(icon).append(Component.literal(" ")).append(name));
        }
    }
}
