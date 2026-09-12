package net.modernclient.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.modernclient.module.NickToolState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ChatComponent.class)
public abstract class ChatMessageMixin {

    private static final String ADD_MESSAGE = "Lnet/minecraft/client/gui/components/ChatComponent;addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V";

    @ModifyArg(method = "addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At(value = "INVOKE", target = ADD_MESSAGE),
            index = 0)
    private Component rewritePlayerChat(Component message) {
        return rewriteChat(message);
    }

    @ModifyArg(method = "addServerSystemMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At(value = "INVOKE", target = ADD_MESSAGE),
            index = 0)
    private Component rewriteServerSystemChat(Component message) {
        return rewriteChat(message);
    }

    @ModifyArg(method = "addClientSystemMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At(value = "INVOKE", target = ADD_MESSAGE),
            index = 0)
    private Component rewriteClientSystemChat(Component message) {
        return rewriteChat(message);
    }

    private static Component rewriteChat(Component message) {
        if (message == null) return null;
        Component rewritten = NickToolState.rewriteNames(message);
        if (!rewritten.getString().equals(message.getString())) {
            NickToolState.debug("chat \"" + message.getString() + "\" -> \"" + rewritten.getString() + "\"");
        }
        return rewritten;
    }
}