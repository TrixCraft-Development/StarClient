package net.starclient.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.starclient.gui.InventorySearchHolder;
import net.starclient.gui.InventorySearchManager;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Shadow
    private EditBox searchBox;

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void starclient$onCreativeMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!InventorySearchManager.isEnabled()) return;
        if (((Object) this) instanceof InventorySearchHolder holder) {
            EditBox box = holder.starclient$getSearchBox();
            if (box == null) return;

            int barW = holder.starclient$getBarW();
            int barX = holder.starclient$getBarX();
            int barY = holder.starclient$getBarY();
            int barH = 18;

            double mx = event.x();
            double my = event.y();

            boolean inBar = mx >= barX && mx <= barX + barW && my >= barY && my <= barY + barH;
            if (inBar) {
                // Yield focus from vanilla creative search box if clicking StarClient search bar
                if (this.searchBox != null) {
                    this.searchBox.setCanLoseFocus(true);
                    this.searchBox.setFocused(false);
                }
            } else if (box.isFocused()) {
                box.setFocused(false);
                InventorySearchManager.setSearchFocused(false);
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void starclient$onCreativeKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!InventorySearchManager.isEnabled()) return;
        if (((Object) this) instanceof InventorySearchHolder holder) {
            EditBox box = holder.starclient$getSearchBox();
            if (box != null && box.isFocused()) {
                int key = event.key();
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    if (!box.getValue().isEmpty()) {
                        box.setValue("");
                        box.setFocused(false);
                        InventorySearchManager.setSearchFocused(false);
                        cir.setReturnValue(true);
                        return;
                    }
                } else if (key == GLFW.GLFW_KEY_ENTER) {
                    box.setFocused(false);
                    InventorySearchManager.setSearchFocused(false);
                    cir.setReturnValue(true);
                    return;
                }

                if (box.keyPressed(event)) {
                    cir.setReturnValue(true);
                    return;
                }

                // Consume all key presses while StarClient search box is focused so hotbar keys,
                // 'E' (inventory close), and 'T' (chat/switch tab) do not disrupt typing.
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void starclient$onCreativeCharTyped(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!InventorySearchManager.isEnabled()) return;
        if (((Object) this) instanceof InventorySearchHolder holder) {
            EditBox box = holder.starclient$getSearchBox();
            if (box != null && box.isFocused()) {
                if (box.charTyped(event)) {
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "preeditUpdated", at = @At("HEAD"), cancellable = true)
    private void starclient$onCreativePreeditUpdated(PreeditEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!InventorySearchManager.isEnabled()) return;
        if (((Object) this) instanceof InventorySearchHolder holder) {
            EditBox box = holder.starclient$getSearchBox();
            if (box != null && box.isFocused()) {
                if (box.preeditUpdated(event)) {
                    cir.setReturnValue(true);
                }
            }
        }
    }
}
