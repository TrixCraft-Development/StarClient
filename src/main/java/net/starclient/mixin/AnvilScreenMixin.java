package net.starclient.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.ItemCombinerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.starclient.gui.AnvilHelperRenderer;
import net.starclient.gui.ColorFormattingBarWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Integrates the Anvil Helper & XP Optimizer overlay and the Color Formatting Bar
 * directly into Minecraft's Anvil screen.
 */
@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin extends ItemCombinerScreen<AnvilMenu> {

    @Shadow
    private EditBox name;

    protected AnvilScreenMixin(AnvilMenu menu, Inventory playerInventory, Component title, Identifier menuResource) {
        super(menu, playerInventory, title, menuResource);
    }

    @Inject(method = "subInit", at = @At("TAIL"))
    private void starclient$addAnvilColorBar(CallbackInfo ci) {
        if (!ColorFormattingBarWidget.isEnabledFor("Show on Anvil")) return;

        int barX = Math.clamp(this.leftPos + (this.imageWidth - ColorFormattingBarWidget.BAR_WIDTH) / 2,
                4, Math.max(4, this.width - ColorFormattingBarWidget.BAR_WIDTH - 4));
        int barY = Math.max(4, this.topPos - ColorFormattingBarWidget.BAR_HEIGHT - 6);

        this.addRenderableWidget(new ColorFormattingBarWidget(
                barX,
                barY,
                false,
                code -> {
                    if (this.name != null && this.name.isActive()) {
                        if (code.startsWith("§")) {
                            // Append directly so '§' is not stripped if user explicitly chose '§'
                            String cur = this.name.getValue();
                            int pos = Math.clamp(this.name.getCursorPosition(), 0, cur.length());
                            String next = cur.substring(0, pos) + code + cur.substring(pos);
                            if (next.length() <= 50) {
                                this.name.setValue(next);
                                this.name.setCursorPosition(pos + code.length());
                            }
                        } else {
                            this.name.insertText(code);
                        }
                    }
                },
                () -> {
                    if (this.name != null) {
                        this.setFocused(this.name);
                        this.name.setFocused(true);
                    }
                }
        ));
    }

    @Inject(method = "extractLabels", at = @At("TAIL"))
    private void starclient$renderAnvilHelper(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        String customName = this.name != null ? this.name.getValue() : "";
        AnvilHelperRenderer.renderInAnvilLabels(
                graphics,
                this.font,
                this.menu,
                this.width,
                this.leftPos,
                this.topPos,
                this.imageWidth,
                this.imageHeight,
                customName
        );
    }
}
