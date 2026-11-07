package net.starclient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.gui.ContainerPreviewRenderer;
import net.starclient.gui.GuiRenderUtils;
import net.starclient.gui.InventorySearchHolder;
import net.starclient.gui.InventorySearchManager;
import net.starclient.module.TooltipFeaturesState;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen implements InventorySearchHolder {

    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Final
    @Shadow protected int imageWidth;
    @Final
    @Shadow protected int imageHeight;
    @Shadow protected Slot hoveredSlot;
    @Final
    @Shadow protected AbstractContainerMenu menu;

    @Unique
    private EditBox starclient$searchBox;

    @Unique
    private long starclient$openTimeMs;

    @Unique
    private boolean starclient$animMatrixPushed;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Override
    public EditBox starclient$getSearchBox() {
        return this.starclient$searchBox;
    }

    @Override
    public int starclient$getBarW() {
        return Math.min(160, this.imageWidth);
    }

    @Override
    public int starclient$getBarX() {
        return this.leftPos + (this.imageWidth - starclient$getBarW()) / 2;
    }

    @Override
    public int starclient$getBarY() {
        // In CreativeModeInventoryScreen, bottom category tabs extend down to topPos + imageHeight + 28.
        // Place the search bar cleanly below them with a gap (+32) so it never overlaps the tabs.
        int extra = ((Object) this) instanceof CreativeModeInventoryScreen ? 32 : 4;
        int y = this.topPos + this.imageHeight + extra;
        return Math.min(y, this.height - 20);
    }

    @Unique
    private void starclient$ensureSearchBox() {
        if (this.starclient$searchBox != null) return;
        int barW = starclient$getBarW();
        int barX = starclient$getBarX();
        int barY = starclient$getBarY();
        int barH = 18;

        this.starclient$searchBox = new EditBox(this.font, barX + 20, barY + 3, barW - 36, barH - 4, Component.literal(""));
        this.starclient$searchBox.setHint(Component.literal("Search items..."));
        this.starclient$searchBox.setBordered(false);
        this.starclient$searchBox.setTextColor(0xFFF8FAFC);
        this.starclient$searchBox.setResponder(InventorySearchManager::setSearchQuery);
        this.starclient$searchBox.setValue(InventorySearchManager.getSearchQuery());
        this.addWidget(this.starclient$searchBox);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void starclient$onInit(CallbackInfo ci) {
        if (this.starclient$openTimeMs == 0L) {
            this.starclient$openTimeMs = System.currentTimeMillis();
        }
        TooltipFeaturesState.resetScroll();
        if (!InventorySearchManager.isEnabled()) {
            this.starclient$searchBox = null;
            return;
        }
        starclient$ensureSearchBox();
    }

    @Inject(
            method = "extractContents",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void starclient$beginContainerAnim(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        this.starclient$animMatrixPushed = false;
        StarClient sc = StarClient.getInstance();
        if (sc == null || sc.getClientSettings() == null || !sc.getClientSettings().isContainerAnimations()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - this.starclient$openTimeMs;
        if (elapsed < 0L || elapsed >= 170L) {
            return;
        }
        float t = elapsed / 170.0f;
        float ease = 1.0f - (float) Math.pow(1.0f - t, 3.0); // Cubic ease-out
        float scale = 0.90f + 0.10f * ease;
        float slideY = (1.0f - ease) * 7.0f;

        float cx = this.leftPos + this.imageWidth / 2.0f;
        float cy = this.topPos + this.imageHeight / 2.0f;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(cx, cy + slideY);
        pose.scale(scale, scale);
        pose.translate(-cx, -cy);
        this.starclient$animMatrixPushed = true;
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void starclient$onExtractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        // Cache Ender Chest contents if viewing an Ender Chest
        if (this.title != null && this.menu != null) {
            TooltipFeaturesState.captureEnderChestIfOpen(this.title.getString(), this.menu);
        }

        if (InventorySearchManager.isEnabled()) {
            starclient$ensureSearchBox();
            if (this.starclient$searchBox != null) {
                int barW = starclient$getBarW();
                int barX = starclient$getBarX();
                int barY = starclient$getBarY();
                int barH = 18;

                this.starclient$searchBox.setPosition(barX + 20, barY + 3);
                this.starclient$searchBox.setWidth(barW - 36);

                ClientSettings cs = StarClient.getInstance().getClientSettings();
                int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
                boolean focused = this.starclient$searchBox.isFocused();
                boolean hovered = mouseX >= barX && mouseX <= barX + barW && mouseY >= barY && mouseY <= barY + barH;

                graphics.nextStratum();

                // Search bar background & shadow
                GuiRenderUtils.drawDropShadow(graphics, barX, barY, barW, barH, 4, 2, 60);
                GuiRenderUtils.fillRoundedRect(graphics, barX, barY, barW, barH, 4, 0xEE0F172A);
                int borderCol = focused ? accent : (hovered ? 0x8864748B : 0x3364748B);
                GuiRenderUtils.drawRoundedOutline(graphics, barX, barY, barW, barH, 4, borderCol);

                // Magnifying glass icon
                GuiRenderUtils.drawText(graphics, this.font, "🔍", barX + 5, barY + 5, focused ? accent : 0xFF94A3B8);

                // EditBox text rendering
                this.starclient$searchBox.extractRenderState(graphics, mouseX, mouseY, a);

                // Clear button '✕'
                if (!this.starclient$searchBox.getValue().isEmpty()) {
                    int clearX = barX + barW - 14;
                    int clearY = barY + 4;
                    boolean clearHover = mouseX >= clearX - 3 && mouseX <= clearX + 11 && mouseY >= clearY - 3 && mouseY <= clearY + 11;
                    if (clearHover) {
                        GuiRenderUtils.fillRoundedRect(graphics, clearX - 2, clearY - 2, 12, 12, 3, 0x33EF4444);
                    }
                    GuiRenderUtils.drawCenteredText(graphics, this.font, "✕", clearX + 4, clearY + 1, clearHover ? 0xFFEF4444 : 0xFF94A3B8);
                }
            }
        }

        if (this.starclient$animMatrixPushed) {
            graphics.pose().popMatrix();
            this.starclient$animMatrixPushed = false;
        }
    }

    @Inject(method = "extractTooltip", at = @At("TAIL"))
    private void starclient$onExtractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        TooltipFeaturesState.updateHoveredSlot(this.hoveredSlot);
        if (this.hoveredSlot != null
                && this.hoveredSlot.hasItem()
                && this.menu.getCarried().isEmpty()
                && this.minecraft != null) {
            ContainerPreviewRenderer.renderForHoveredItem(
                    graphics, this.minecraft, this.hoveredSlot.getItem(), mouseX, mouseY, this.width, this.height);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void starclient$onMouseScrolled(double mouseX, double mouseY,
                                            double horizontalAmount, double verticalAmount,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem() && this.minecraft != null) {
            boolean shiftHeld = InputConstants.isKeyDown(this.minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                    || InputConstants.isKeyDown(this.minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
            if (TooltipFeaturesState.onMouseScroll(horizontalAmount, verticalAmount, shiftHeld)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void starclient$onExtractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!InventorySearchManager.isEnabled() || this.starclient$searchBox == null) return;
        String query = this.starclient$searchBox.getValue();
        if (query.isBlank()) return;

        ItemStack stack = slot.getItem();
        boolean matches = InventorySearchManager.matchesSearch(stack, query);

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;

        if (matches) {
            GuiRenderUtils.drawRoundedOutline(graphics, slot.x - 1, slot.y - 1, 18, 18, 2, accent);
            GuiRenderUtils.fillRoundedRect(graphics, slot.x, slot.y, 16, 16, 2, 0x2538BDF8);
        } else {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xCC000000);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void starclient$onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!InventorySearchManager.isEnabled()) return;
        starclient$ensureSearchBox();
        if (this.starclient$searchBox == null) return;

        int barW = starclient$getBarW();
        int barX = starclient$getBarX();
        int barY = starclient$getBarY();
        int barH = 18;

        double mx = event.x();
        double my = event.y();

        if (!this.starclient$searchBox.getValue().isEmpty()) {
            int clearX = barX + barW - 14;
            int clearY = barY + 4;
            if (mx >= clearX - 3 && mx <= clearX + 11 && my >= clearY - 3 && my <= clearY + 11) {
                this.starclient$searchBox.setValue("");
                this.starclient$searchBox.setFocused(true);
                this.setFocused(this.starclient$searchBox);
                InventorySearchManager.setSearchFocused(true);
                cir.setReturnValue(true);
                return;
            }
        }

        if (mx >= barX && mx <= barX + barW && my >= barY && my <= barY + barH) {
            this.starclient$searchBox.setFocused(true);
            this.setFocused(this.starclient$searchBox);
            InventorySearchManager.setSearchFocused(true);
            this.starclient$searchBox.mouseClicked(event, doubleClick);
            cir.setReturnValue(true);
        } else if (this.starclient$searchBox.isFocused()) {
            this.starclient$searchBox.setFocused(false);
            this.setFocused(null);
            InventorySearchManager.setSearchFocused(false);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void starclient$onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (this.starclient$searchBox != null && this.starclient$searchBox.isFocused()) {
            int key = event.key();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!this.starclient$searchBox.getValue().isEmpty()) {
                    this.starclient$searchBox.setValue("");
                    this.starclient$searchBox.setFocused(false);
                    this.setFocused(null);
                    InventorySearchManager.setSearchFocused(false);
                    cir.setReturnValue(true);
                    return;
                }
            } else if (key == GLFW.GLFW_KEY_ENTER) {
                this.starclient$searchBox.setFocused(false);
                this.setFocused(null);
                InventorySearchManager.setSearchFocused(false);
                cir.setReturnValue(true);
                return;
            }

            if (this.starclient$searchBox.keyPressed(event)) {
                cir.setReturnValue(true);
                return;
            }

            cir.setReturnValue(true);
            return;
        }

        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            if (TooltipFeaturesState.onArrowKey(event.key())) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void starclient$onRemoved(CallbackInfo ci) {
        InventorySearchManager.reset();
        TooltipFeaturesState.resetScroll();
        this.starclient$openTimeMs = 0L;
    }
}
