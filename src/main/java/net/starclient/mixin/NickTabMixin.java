package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.starclient.StarClient;
import net.starclient.module.NickToolState;
import net.starclient.rank.Rank;
import net.starclient.rank.RankManager;
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
        StarClient mod = StarClient.getInstance();
        if (client.player != null && mod != null && mod.getClientSettings().isTabIcon() && info.getProfile() != null) {
            boolean isSelf = info.getProfile().id().equals(client.player.getUUID());
            RankManager rankManager = mod.getRankManager();
            Rank rank = rankManager != null
                    ? rankManager.getDisplayRank(info.getProfile().id(), info.getProfile().name(), isSelf)
                    : (isSelf ? Rank.USER : null);
            if (rank != null) {
                Component icon = Component.literal("⭐").withStyle(style -> style.withColor(rank.getRgb()));
                cir.setReturnValue(Component.literal("").append(icon).append(Component.literal(" ")).append(display));
            }
        }
    }
}
