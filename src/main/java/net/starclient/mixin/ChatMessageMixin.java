package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.starclient.gui.WorldInfoRenderer;
import net.starclient.module.AutoGgState;
import net.starclient.module.ChatPlusState;
import net.starclient.module.NickToolState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatMessageMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private List<GuiMessage> allMessages;

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    private java.util.function.Predicate<GuiMessage> visibleMessageFilter;

    @Shadow
    private void logChatMessage(GuiMessage message) {
    }

    @Shadow
    private void addMessageToDisplayQueue(GuiMessage message) {
    }

    @Shadow
    private void addMessageToQueue(GuiMessage message) {
    }

    @Shadow
    private void refreshTrimmedMessages() {
    }

    @Unique
    private List<GuiMessage> starclient$preservedHistory;

    @Unique
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
        if (message != null) {
            WorldInfoRenderer.captureSeedFromServerMessage(message);
            AutoGgState.onServerMessage(message.getString());
        }
        return rewriteChat(message);
    }

    @ModifyArg(method = "addClientSystemMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At(value = "INVOKE", target = ADD_MESSAGE),
            index = 0)
    private Component rewriteClientSystemChat(Component message) {
        return rewriteChat(message);
    }

    @Inject(method = "clearMessages(Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;flushQueue()V", shift = At.Shift.AFTER))
    private void starclient$beforeClearMessages(boolean clearSentMsgHistory, CallbackInfo ci) {
        this.starclient$preservedHistory = null;
        if (clearSentMsgHistory) {
            boolean hadMessages = !this.allMessages.isEmpty();
            if (ChatPlusState.isSaveChatHistory() && hadMessages) {
                this.starclient$preservedHistory = new ArrayList<>(this.allMessages);
            }
            ChatPlusState.onClientDisconnected(hadMessages && ChatPlusState.isSaveChatHistory());
        } else {
            ChatPlusState.onManualChatCleared();
        }
    }

    @Inject(method = "clearMessages(Z)V", at = @At("TAIL"))
    private void starclient$afterClearMessages(boolean clearSentMsgHistory, CallbackInfo ci) {
        if (this.starclient$preservedHistory != null && !this.starclient$preservedHistory.isEmpty()) {
            int fadedTime = this.minecraft.gui.hud.getGuiTicks() - 300;
            for (GuiMessage msg : this.starclient$preservedHistory) {
                this.allMessages.add(new GuiMessage(fadedTime, msg.content(), null, msg.source(), msg.tag()));
            }
            this.starclient$preservedHistory = null;
            this.refreshTrimmedMessages();
        }
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void starclient$onAddMessage(Component content, MessageSignature signature,
                                         GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        if (content == null || !ChatPlusState.isEnabled() || ChatPlusState.isSessionDivider(content)) {
            return;
        }

        String rawPlain = ChatPlusState.normalizeKey(content);
        int repeatCount = 1;

        if (ChatPlusState.isCompactChat() && !rawPlain.isEmpty()) {
            int maxScan = Math.min(8, this.allMessages.size());
            for (int i = 0; i < maxScan; i++) {
                GuiMessage prev = this.allMessages.get(i);
                if (ChatPlusState.isSessionDivider(prev.content())) {
                    break;
                }
                ChatPlusState.CompactTrack track = ChatPlusState.getCompactTrack(prev);
                String prevKey = track != null ? track.key() : ChatPlusState.normalizeKey(prev.content());
                if (rawPlain.equals(prevKey)) {
                    repeatCount = (track != null ? track.count() : 1) + 1;
                    this.allMessages.remove(i);
                    this.trimmedMessages.removeIf(line -> line.parent() == prev);
                    break;
                }
            }
        }

        Component decorated = ChatPlusState.decorateMessage(content, rawPlain, repeatCount, source);
        int ticks = this.minecraft.gui.hud.getGuiTicks();
        GuiMessage guiMessage = new GuiMessage(ticks, decorated, signature, source, tag);
        ChatPlusState.recordCompactTrack(guiMessage, rawPlain, repeatCount);

        if (this.visibleMessageFilter == null || this.visibleMessageFilter.test(guiMessage)) {
            this.logChatMessage(guiMessage);
            this.addMessageToDisplayQueue(guiMessage);
            this.addMessageToQueue(guiMessage);
        }
        ci.cancel();
    }

    @ModifyConstant(
            method = {"addMessageToDisplayQueue", "addMessageToQueue"},
            constant = @Constant(intValue = 100)
    )
    private int starclient$modifyMaxChatHistory(int original) {
        return ChatPlusState.getMaxHistory();
    }

    @ModifyVariable(
            method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At("STORE"),
            ordinal = 2
    )
    private float starclient$modifyChatBgOpacity(float originalOpacity) {
        if (ChatPlusState.isTransparentBackground()) {
            return 0.0f;
        }
        return originalOpacity;
    }

    @Unique
    private static Component rewriteChat(Component message) {
        if (message == null || ChatPlusState.isSessionDivider(message)) return message;
        Component rewritten = NickToolState.rewriteNames(message);
        rewritten = net.starclient.friend.FriendManager.decorateChat(rewritten);
        if (!rewritten.getString().equals(message.getString())) {
            NickToolState.debug("chat \"" + message.getString() + "\" -> \"" + rewritten.getString() + "\"");
        }
        return rewritten;
    }
}
