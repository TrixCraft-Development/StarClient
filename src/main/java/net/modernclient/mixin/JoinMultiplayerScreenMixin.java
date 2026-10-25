package net.modernclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.modernclient.gui.AccountSwitcherOverlay;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({JoinMultiplayerScreen.class, SelectWorldScreen.class})
public abstract class JoinMultiplayerScreenMixin extends Screen {

    @Unique
    private final AccountSwitcherOverlay starclient$accountSwitcher = new AccountSwitcherOverlay();

    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        this.starclient$accountSwitcher.render(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        if (this.starclient$accountSwitcher.mouseClicked(this, event)) {
            return true;
        }
        return super.mouseClicked(event, action);
    }
}
