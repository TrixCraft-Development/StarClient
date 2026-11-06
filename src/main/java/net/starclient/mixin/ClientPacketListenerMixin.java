package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.config.ProfileManager;
import net.starclient.module.AntiKnockbackState;
import net.starclient.module.ServerSessionTracker;
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
        StarClient sc = StarClient.getInstance();
        if (sc != null) {
            sc.ensureConfigLoaded();
        }
        ServerSessionTracker.onSessionJoined(client.getCurrentServer());
        ProfileManager pm = sc != null ? sc.getProfileManager() : null;
        if (client.isLocalServer()) {
            ClientStatsManager.getInstance().onWorldJoined();
            if (pm != null && client.getSingleplayerServer() != null) {
                IntegratedServer sp = client.getSingleplayerServer();
                String levelName = sp.getWorldData().getLevelName();
                String folderName = null;
                try {
                    var rootPath = sp.getWorldPath(LevelResource.ROOT).normalize();
                    if (rootPath.getFileName() != null) {
                        folderName = rootPath.getFileName().toString();
                    }
                } catch (Exception ignored) {
                }
                pm.onWorldJoined(levelName, folderName);
            }
        } else {
            ClientStatsManager.getInstance().onServerJoined();
            if (pm != null && client.getCurrentServer() != null) {
                ServerData sd = client.getCurrentServer();
                pm.onServerJoined(sd.ip, sd.name);
            }
        }
    }

    @Inject(method = "handleSetTime", at = @At("HEAD"))
    private void onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        ServerSessionTracker.onTimePacket(packet.gameTime());
    }

    @Inject(method = "sendChat", at = @At("HEAD"))
    private void onSendChat(String content, CallbackInfo ci) {
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
