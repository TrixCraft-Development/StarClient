package net.starclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.starclient.gui.VillagerTradeHighlighter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into Minecraft's Villager Trading screen to draw trade row highlights,
 * emerald price ratings, and the glassmorphic Trade Inspector side panel.
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {

    @Shadow
    private int scrollOff;

    protected MerchantScreenMixin(MerchantMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void starclient$renderVillagerTradeHighlights(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        VillagerTradeHighlighter.renderOverlay(
                graphics,
                (MerchantScreen) (Object) this,
                this.menu,
                this.leftPos,
                this.topPos,
                this.imageWidth,
                this.imageHeight,
                this.scrollOff
        );
    }
}
