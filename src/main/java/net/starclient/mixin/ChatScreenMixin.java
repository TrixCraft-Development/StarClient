package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.gui.GuiRenderUtils;
import net.starclient.module.ChatCalculatorState;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {

    @Shadow
    protected EditBox input;

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void starclient$renderChatCalculatorPill(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (this.input == null || !ChatCalculatorState.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        ChatCalculatorState.CalcResult res = ChatCalculatorState.evaluate(mc, this.input.getValue());
        if (res == null) return;

        StarClient sc = StarClient.getInstance();
        if (sc == null || sc.getClientSettings() == null) return;
        ClientSettings cs = sc.getClientSettings();

        int accent = cs.getMenuAccentColor();
        int bg = 0xEA000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
        int border = cs.getMenuBorderColor();

        String mainLabel = "∑ " + res.displayAnswer();
        String extra = res.extraHint();
        boolean tabHint = ChatCalculatorState.isTabToInsert();
        String tabBadge = "[Tab] Insert";

        int mainW = this.font.width(mainLabel);
        int extraW = extra.isEmpty() ? 0 : (6 + this.font.width(extra));
        int tabW = tabHint ? (8 + this.font.width(tabBadge) + 8) : 0;

        int pillW = 12 + mainW + extraW + tabW;
        int pillH = 18;
        int pillX = 4;
        int pillY = Math.max(4, this.height - 34);

        GuiRenderUtils.drawDropShadow(graphics, pillX, pillY, pillW, pillH, 5, 3, 95);
        GuiRenderUtils.fillRoundedRect(graphics, pillX, pillY, pillW, pillH, 5, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, pillX, pillY, pillW, pillH, 5, (accent & 0x00FFFFFF) | 0x88000000);

        int cursorX = pillX + 6;
        graphics.text(this.font, mainLabel, cursorX, pillY + 5, 0xFFF8FAFC, true);
        cursorX += mainW;

        if (!extra.isEmpty()) {
            cursorX += 6;
            graphics.text(this.font, extra, cursorX, pillY + 5, 0xFF94A3B8, true);
            cursorX += this.font.width(extra);
        }

        if (tabHint) {
            int bx = cursorX + 6;
            int bw = this.font.width(tabBadge) + 8;
            GuiRenderUtils.fillRoundedRect(graphics, bx, pillY + 3, bw, 12, 3, (accent & 0x00FFFFFF) | 0x33000000);
            GuiRenderUtils.drawRoundedOutline(graphics, bx, pillY + 3, bw, 12, 3, border);
            graphics.text(this.font, tabBadge, bx + 4, pillY + 5, accent, false);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void starclient$handleChatCalculatorTab(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.input == null || !ChatCalculatorState.isTabToInsert()) return;
        if (event.key() != GLFW.GLFW_KEY_TAB) return;

        Minecraft mc = Minecraft.getInstance();
        ChatCalculatorState.CalcResult res = ChatCalculatorState.evaluate(mc, this.input.getValue());
        if (res != null) {
            this.input.setValue(res.replacementText());
            this.input.moveCursorToEnd(false);
            cir.setReturnValue(true);
        }
    }
}
