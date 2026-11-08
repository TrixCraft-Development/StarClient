package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.module.StopwatchTimerState;

/**
 * Renders the draggable "Stopwatch & Timer" HUD widget.
 */
public final class StopwatchRenderer {

    private StopwatchRenderer() {
    }

    public static int getWidth(Font font, Module mod) {
        if (font == null) return 130;
        String label = buildLabel();
        return Math.max(122, font.width(label) + 46);
    }

    public static int getHeight() {
        return HudLayout.ELEMENT_HEIGHT;
    }

    private static String buildLabel() {
        boolean countdown = StopwatchTimerState.isCountdownMode();
        String prefix = countdown ? "⏳ " : "⏱ ";
        return prefix + StopwatchTimerState.formatDisplayTime();
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod,
                              HudLayout layout, int screenWidth, int screenHeight) {
        if (mod == null || client == null || client.font == null) return;
        int width = getWidth(client.font, mod);
        int height = getHeight();

        int x = layout.effectiveX(HudLayout.STOPWATCH, screenWidth, width);
        int y = layout.effectiveY(HudLayout.STOPWATCH, screenHeight);
        x = Math.clamp(x, 2, Math.max(2, screenWidth - width - 2));
        y = Math.clamp(y, 2, Math.max(2, screenHeight - height - 2));

        boolean shadow = StarClient.getInstance().getClientSettings().isHudTextShadow();
        boolean running = StopwatchTimerState.isRunning();
        boolean finished = StopwatchTimerState.isCountdownFinished();

        if (!mod.isTransparentBackground()) {
            int bg = (mod.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
            GuiRenderUtils.fillRoundedRect(extractor, x, y, width, height, 4, bg);
            int borderCol = finished
                    ? 0xFFFACC15
                    : (running ? 0x6634D399 : 0x28FFFFFF);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, width, height, 4, borderCol);
        }

        String label = buildLabel();
        extractor.text(client.font, label, x + 6, y + 5, mod.getTextColor(), shadow);

        String stateTag;
        int stateCol;
        if (finished) {
            stateTag = "DONE";
            stateCol = 0xFFFACC15;
        } else if (running) {
            stateTag = "RUN";
            stateCol = 0xFF34D399;
        } else {
            stateTag = "PAUSE";
            stateCol = 0xFF94A3B8;
        }

        int pillW = client.font.width(stateTag) + 8;
        int pillX = x + width - pillW - 4;
        int pillY = y + 3;
        GuiRenderUtils.fillRoundedRect(extractor, pillX, pillY, pillW, 12, 3, (stateCol & 0x00FFFFFF) | 0x28000000);
        extractor.text(client.font, stateTag, pillX + 4, pillY + 2, stateCol, shadow);
    }

    public static void renderPreview(GuiGraphicsExtractor extractor, Module mod) {
        Minecraft client = Minecraft.getInstance();
        if (mod == null || client.font == null) return;
        HudLayout layout = StarClient.getInstance().getHudLayout();
        render(extractor, client, mod, layout, extractor.guiWidth(), extractor.guiHeight());
    }
}
