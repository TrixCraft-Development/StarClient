package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;

import java.util.Locale;

public final class MemoryHudRenderer {
    private static final int GRAPH_SAMPLES = 48;
    private static final int[] fpsSamples = new int[GRAPH_SAMPLES];
    private static int sampleIndex = 0;
    private static long lastSampleMillis = 0L;

    private MemoryHudRenderer() {
    }

    public static int getWidth(Font font, Module module) {
        boolean showMb = module != null && module.getSetting("Show Megabytes") instanceof BooleanSetting bs && bs.getValue();
        return showMb ? Math.max(HudLayout.STAT_WIDTH, 134) : HudLayout.STAT_WIDTH;
    }

    public static int getHeight(Module module) {
        boolean showBar = module == null || !(module.getSetting("Show Usage Bar") instanceof BooleanSetting bs) || bs.getValue();
        boolean showGraph = module != null && module.getSetting("Show FPS Graph") instanceof BooleanSetting bs && bs.getValue();
        int h = HudLayout.ELEMENT_HEIGHT;
        if (showBar) h += 5;
        if (showGraph) h += 18;
        return h;
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module module, HudLayout layout, int screenW, int screenH) {
        if (module == null || !module.isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastSampleMillis >= 100L) {
            lastSampleMillis = now;
            fpsSamples[sampleIndex] = Math.max(0, client.getFps());
            sampleIndex = (sampleIndex + 1) % GRAPH_SAMPLES;
        }

        Runtime rt = Runtime.getRuntime();
        long maxMem = rt.maxMemory();
        long totalMem = rt.totalMemory();
        long freeMem = rt.freeMemory();
        long usedMem = Math.max(0L, totalMem - freeMem);
        int pct = maxMem > 0 ? Math.clamp(Math.round(usedMem * 100.0 / maxMem), 0, 100) : 0;
        long usedMb = usedMem / (1024L * 1024L);
        long maxMb = maxMem / (1024L * 1024L);

        boolean showPct = !(module.getSetting("Show Percentage") instanceof BooleanSetting bs) || bs.getValue();
        boolean showMb = module.getSetting("Show Megabytes") instanceof BooleanSetting bs && bs.getValue();
        boolean showBar = !(module.getSetting("Show Usage Bar") instanceof BooleanSetting bs) || bs.getValue();
        boolean showGraph = module.getSetting("Show FPS Graph") instanceof BooleanSetting bs && bs.getValue();

        String label;
        if (showPct && showMb) {
            label = String.format(Locale.US, "RAM: %d%% (%d/%dMB)", pct, usedMb, maxMb);
        } else if (showMb) {
            label = String.format(Locale.US, "RAM: %d / %d MB", usedMb, maxMb);
        } else {
            label = String.format(Locale.US, "RAM: %d%%", pct);
        }

        int w = Math.max(getWidth(client.font, module), client.font.width(label) + 12);
        int h = getHeight(module);
        int x = layout.effectiveX(HudLayout.MEMORY, screenW, w);
        int y = layout.effectiveY(HudLayout.MEMORY, screenH);

        int accent = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        boolean textShadow = StarClient.getInstance().getClientSettings().isHudTextShadow();
        if (!module.isTransparentBackground()) {
            int boxBg = (module.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
            GuiRenderUtils.fillRoundedRect(extractor, x, y, w, h, 4, boxBg);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, w, h, 4, 0x28FFFFFF);
        }

        extractor.text(client.font, label, x + 6, y + 5, module.getTextColor(), textShadow);
        int curY = y + 17;

        if (showBar) {
            int barX = x + 6;
            int barW = w - 12;
            extractor.fill(barX, curY, barX + barW, curY + 2, 0x4464748B);
            int barCol = pct >= 88 ? 0xFFF87171 : (pct >= 75 ? 0xFFFBBF24 : accent);
            extractor.fill(barX, curY, barX + Math.round(barW * (pct / 100.0F)), curY + 2, barCol);
            curY += 5;
        }

        if (showGraph) {
            int gx = x + 6;
            int gw = w - 12;
            int gh = 14;
            int gy = curY + 1;
            extractor.fill(gx, gy, gx + gw, gy + gh, 0x330F172A);

            int maxFps = 60;
            for (int v : fpsSamples) {
                if (v > maxFps) maxFps = v;
            }
            for (int i = 0; i < GRAPH_SAMPLES; i++) {
                int sIdx = (sampleIndex + i) % GRAPH_SAMPLES;
                int fpsVal = fpsSamples[sIdx];
                int colX1 = gx + (i * gw) / GRAPH_SAMPLES;
                int colX2 = Math.max(colX1 + 1, gx + ((i + 1) * gw) / GRAPH_SAMPLES);
                int barH = Math.clamp(Math.round((fpsVal / (float) maxFps) * (gh - 2)), 1, gh - 2);
                extractor.fill(colX1, gy + gh - barH, colX2, gy + gh, (0xAA << 24) | (accent & 0x00FFFFFF));
            }
        }
    }
}
