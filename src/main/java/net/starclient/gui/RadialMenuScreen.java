package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.RadialMenuState;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

/**
 * Transparent glassmorphic 8-slice radial quick-swap wheel screen.
 * Held while key is pressed down, executes selection upon key release.
 */
public class RadialMenuScreen extends Screen {

    private final int triggerKey;
    private int hoveredSliceIndex = -1;

    public RadialMenuScreen(int triggerKey) {
        super(Component.literal("Radial Quick-Swap"));
        this.triggerKey = triggerKey;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int cx = this.width / 2;
        int cy = this.height / 2;
        int radius = RadialMenuState.getRadius();

        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.hypot(dx, dy);

        if (dist >= 22.0) {
            double angleDeg = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
            if (angleDeg < 0) angleDeg += 360.0;
            this.hoveredSliceIndex = (int) Math.floor((angleDeg + 22.5) / 45.0) % 8;
        } else {
            this.hoveredSliceIndex = -1;
        }

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs.getMenuAccentColor();
        int accentRgb = accent & 0x00FFFFFF;

        // Subtle dark radial blur background
        GuiRenderUtils.fillRoundedRect(extractor, cx - radius - 30, cy - radius - 30, (radius + 30) * 2, (radius + 30) * 2, radius + 30, 0x55000000);

        // Center hub circle
        int hubR = 24;
        GuiRenderUtils.drawDropShadow(extractor, cx - hubR, cy - hubR, hubR * 2, hubR * 2, hubR, 4, 120);
        GuiRenderUtils.fillRoundedRect(extractor, cx - hubR, cy - hubR, hubR * 2, hubR * 2, hubR, 0xE00F172A);
        GuiRenderUtils.drawRoundedOutline(extractor, cx - hubR, cy - hubR, hubR * 2, hubR * 2, hubR, 0x44FFFFFF);

        RadialMenuState.RadialSlice[] slices = RadialMenuState.computeSlices(mc.player);

        // Render the 8 slices
        for (int i = 0; i < 8; i++) {
            boolean isSelected = (i == this.hoveredSliceIndex);
            double angleDeg = (i * 45.0) - 90.0; // 0 is Top / North
            double rad = Math.toRadians(angleDeg);

            int currentRadius = radius + (isSelected ? 6 : 0);
            int cardCenterX = cx + (int) Math.round(currentRadius * Math.cos(rad));
            int cardCenterY = cy + (int) Math.round(currentRadius * Math.sin(rad));

            int cardSize = isSelected ? 42 : 36;
            int cardX = cardCenterX - cardSize / 2;
            int cardY = cardCenterY - cardSize / 2;

            int bgCol = isSelected ? (0x55000000 | accentRgb) : 0xD80F172A;
            int borderCol = isSelected ? accent : 0x30FFFFFF;

            GuiRenderUtils.drawDropShadow(extractor, cardX, cardY, cardSize, cardSize, 6, isSelected ? 5 : 3, isSelected ? 120 : 80);
            GuiRenderUtils.fillRoundedRect(extractor, cardX, cardY, cardSize, cardSize, 6, bgCol);
            GuiRenderUtils.drawRoundedOutline(extractor, cardX, cardY, cardSize, cardSize, 6, borderCol);

            RadialMenuState.RadialSlice slice = slices[i];
            if (slice != null && !slice.displayStack().isEmpty()) {
                ItemStack stack = slice.displayStack();
                int iconX = cardCenterX - 8;
                int iconY = cardCenterY - 8;
                extractor.item(stack, iconX, iconY);

                if (RadialMenuState.isShowItemCount() && stack.getCount() > 1) {
                    String countStr = String.valueOf(stack.getCount());
                    extractor.text(this.font, countStr, cardCenterX + 1, cardCenterY + 1, 0xFFFFFFFF, true);
                }
            }
        }

        // Center hub info
        if (this.hoveredSliceIndex >= 0 && this.hoveredSliceIndex < slices.length && slices[this.hoveredSliceIndex] != null) {
            RadialMenuState.RadialSlice sel = slices[this.hoveredSliceIndex];
            String name = sel.name();
            if (this.font.width(name) > 160) {
                name = this.font.plainSubstrByWidth(name, 155) + "..";
            }
            int nameW = this.font.width(name);
            int labelY = cy + radius + 34;
            GuiRenderUtils.fillRoundedRect(extractor, cx - nameW / 2 - 8, labelY - 3, nameW + 16, 16, 4, 0xD00F172A);
            GuiRenderUtils.drawRoundedOutline(extractor, cx - nameW / 2 - 8, labelY - 3, nameW + 16, 16, 4, accent);
            GuiRenderUtils.drawCenteredText(extractor, this.font, name, cx, labelY + 1, 0xFFFFFFFF);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Release key to equip", cx, cy - 4, accent);
        } else {
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Quick-Swap", cx, cy - 8, 0xFFCBD5E1);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Move mouse", cx, cy + 2, 0xFF94A3B8);
        }
    }

    private void executeAndClose() {
        Minecraft mc = Minecraft.getInstance();
        if (this.hoveredSliceIndex >= 0 && mc.player != null) {
            RadialMenuState.RadialSlice[] slices = RadialMenuState.computeSlices(mc.player);
            if (this.hoveredSliceIndex < slices.length && slices[this.hoveredSliceIndex] != null) {
                RadialMenuState.executeSelection(mc, slices[this.hoveredSliceIndex]);
            }
        }
        onClose();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        executeAndClose();
        return true;
    }

    @Override
    public boolean keyReleased(@NonNull KeyEvent event) {
        if (event.key() == this.triggerKey || this.triggerKey == GLFW.GLFW_KEY_UNKNOWN) {
            executeAndClose();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
