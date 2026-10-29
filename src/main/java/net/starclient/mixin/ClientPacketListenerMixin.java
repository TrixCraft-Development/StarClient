package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.starclient.module.AntiKnockbackState;
import net.starclient.stats.ClientStatsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    @Inject(method = "handleSetEntityMotion(Lnet/minecraft/network/protocol/game/ClientboundSetEntityMotionPacket;)V", at = @At("HEAD"), cancellable = true)
    private void antiknockback(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (!AntiKnockbackState.isActive()) return;
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null || client.player == null) return;
        Entity entity = level.getEntity(packet.id());
        if (entity == client.player) {
            Vec3 motion = packet.movement();
            assert entity != null;
            entity.lerpMotion(motion.scale(1.0 - AntiKnockbackState.getReduction() / 100.0));
            ci.cancel();
        }
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void onLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.isLocalServer()) {
            ClientStatsManager.getInstance().onWorldJoined();
        } else {
            ClientStatsManager.getInstance().onServerJoined();
        }
    }

    @Inject(method = "sendChat", at = @At("HEAD"))
    private void onSendChat(String message, CallbackInfo ci) {
        ClientStatsManager.getInstance().onChatSent();
    }

    @Inject(method = "handlePlayerCombatKill", at = @At("HEAD"))
    private void onPlayerCombatKill(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && packet.playerId() == client.player.getId()) {
            ClientStatsManager.getInstance().onPlayerDeath();
        }
    }
}
