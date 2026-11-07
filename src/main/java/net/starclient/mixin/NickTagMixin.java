package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.starclient.StarClient;
import net.starclient.module.NametagsPlusState;
import net.starclient.module.NickToolState;
import net.starclient.module.TntTimerState;
import net.starclient.rank.Rank;
import net.starclient.rank.RankManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;

@Mixin(EntityRenderer.class)
public abstract class NickTagMixin<T extends Entity> {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Entity;D)Z", at = @At("HEAD"), cancellable = true)
    private void showTntTimerName(Entity entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof PrimedTnt && TntTimerState.isActive() && distanceToCameraSq < 4096.0) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getNameTag(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void rewriteAndDecorateNametag(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (entity instanceof PrimedTnt tnt && TntTimerState.isActive()) {
            int fuseTicks = Math.max(0, tnt.getFuse());
            double seconds = fuseTicks / 20.0;
            int color = seconds > 2.0 ? 0x55FF55 : (seconds > 1.0 ? 0xFFFF55 : 0xFF5555);
            String label = String.format(Locale.US, "💣 %.2fs", seconds);
            cir.setReturnValue(Component.literal(label).withStyle(style -> style.withColor(color)));
            return;
        }

        if (!(entity instanceof Player player)) return;
        Component name = cir.getReturnValue();
        if (name == null) return;
        Component display = NickToolState.rewriteNames(name);
        if (!display.getString().equals(name.getString())) {
            NickToolState.debug("nametag \"" + name.getString() + "\" -> \"" + display.getString() + "\"");
            cir.setReturnValue(display);
        }
        Minecraft client = Minecraft.getInstance();
        StarClient mod = StarClient.getInstance();
        if (client.player != null && mod != null && mod.getClientSettings().isTabIcon()) {
            boolean isSelf = player == client.player;
            String username = player.getGameProfile().name();
            RankManager rankManager = mod.getRankManager();
            Rank rank = rankManager != null
                    ? rankManager.getDisplayRank(player.getUUID(), username, isSelf)
                    : (isSelf ? Rank.USER : null);
            if (rank != null) {
                Component icon = Component.literal("⭐").withStyle(style -> style.withColor(rank.getRgb()));
                cir.setReturnValue(Component.literal("").append(icon).append(Component.literal(" ")).append(display));
            }
        }
        Component decorated = NametagsPlusState.decoratePlayerNametag(player, cir.getReturnValue());
        if (decorated != cir.getReturnValue()) {
            cir.setReturnValue(decorated);
        }
    }
}
