package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.starclient.module.ServerSessionTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {

    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void starclient$trackTargetServer(Screen parent, Minecraft minecraft, ServerAddress hostAndPort,
                                                     ServerData data, boolean isQuickPlay,
                                                     TransferState transferState, CallbackInfo ci) {
        if (data != null) {
            ServerSessionTracker.setLastServerData(data);
        }
    }
}
