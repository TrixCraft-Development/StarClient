package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.Setting;

import java.util.function.Consumer;

/**
 * Compact glassmorphic Color & Formatting Bar widget attached to Sign Editor,
 * Book & Quill, and Anvil Rename screens for 1-click Minecraft color & style codes.
 */
public class ColorFormattingBarWidget extends AbstractWidget {
    public static final int BAR_WIDTH = 266;
    public static final int BAR_HEIGHT = 44;

    private static Module module;

    private record ColorChip(char code, String name, int argb) {
    }

    private record StyleChip(char code, String label, String name) {
    }

    private static final ColorChip[] COLORS = {
            new ColorChip('0', "Black", 0xFF000000),
            new ColorChip('1', "Dark Blue", 0xFF0000AA),
            new ColorChip('2', "Dark Green", 0xFF00AA00),
            new ColorChip('3', "Dark Aqua", 0xFF00AAAA),
            new ColorChip('4', "Dark Red", 0xFFAA0000),
            new ColorChip('5', "Dark Purple", 0xFFAA00AA),
            new ColorChip('6', "Gold", 0xFFFFAA00),
            new ColorChip('7', "Gray", 0xFFAAAAAA),
            new ColorChip('8', "Dark Gray", 0xFF555555),
            new ColorChip('9', "Blue", 0xFF5555FF),
            new ColorChip('a', "Green", 0xFF55FF55),
            new ColorChip('b', "Aqua", 0xFF55FFFF),
            new ColorChip('c', "Red", 0xFFFF5555),
            new ColorChip('d', "Light Purple", 0xFFFF55FF),
            new ColorChip('e', "Yellow", 0xFFFFFF55),
            new ColorChip('f', "White", 0xFFFFFFFF)
    };

    private static final StyleChip[] STYLES = {
            new StyleChip('l', "B", "Bold"),
            new StyleChip('o', "I", "Italic"),
            new StyleChip('n', "U", "Underline"),
            new StyleChip('m', "S", "Strike"),
            new StyleChip('k', "?", "Obfuscated"),
            new StyleChip('r', "↺ Reset", "Reset")
    };

    private final Consumer<String> codeInserter;
    private final Runnable restoreFocus;
    private boolean useSectionPrefix;

    public ColorFormattingBarWidget(int x, int y, boolean defaultSectionPrefix,
                                    Consumer<String> codeInserter, Runnable restoreFocus) {
        super(x, y, BAR_WIDTH, BAR_HEIGHT, Component.literal("Color Formatting Bar"));
        this.codeInserter = codeInserter;
        this.restoreFocus = restoreFocus;
        this.useSectionPrefix = resolveInitialSectionPrefix(defaultSectionPrefix);
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabledFor(String targetSetting) {
        if (module == null || !module.isEnabled() || !module.canUse()) return false;
        Setting<?> s = module.getSetting(targetSetting);
        return !(s instanceof BooleanSetting bs) || bs.getValue();
    }

    private static boolean resolveInitialSectionPrefix(boolean screenDefaultSection) {
        if (module != null && module.getSetting("Code Prefix") instanceof ModeSetting ms) {
            String v = ms.getValue();
            if ("§ (Vanilla)".equals(v)) return true;
            if ("& (Servers)".equals(v)) return false;
        }
        return screenDefaultSection;
    }

    private char activePrefix() {
        return this.useSectionPrefix ? '§' : '&';
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        StarClient sc = StarClient.getInstance();
        ClientSettings cs = sc != null ? sc.getClientSettings() : null;

        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int bg = cs != null ? (0xEE000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xEE0F172A;
        int bgDark = cs != null ? cs.getMenuBackgroundDarkColor() : 0xFF0A101E;
        int surfaceHover = cs != null ? cs.getMenuSurfaceHoverColor() : 0xFF1E293B;
        int border = cs != null ? cs.getMenuBorderColor() : 0xFF334155;

        int x = this.getX();
        int y = this.getY();

        GuiRenderUtils.drawDropShadow(graphics, x, y, this.width, this.height, 6, 3, 95);
        GuiRenderUtils.fillRoundedRect(graphics, x, y, this.width, this.height, 6, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, x, y, this.width, this.height, 6, border);

        char pfx = activePrefix();
        String hoverTip = null;

        // Row 1: 16 Minecraft Color Swatches
        int chipSize = 14;
        int chipGap = 2;
        int startX = x + 6;
        int row1Y = y + 5;
        for (int i = 0; i < COLORS.length; i++) {
            ColorChip c = COLORS[i];
            int cx = startX + i * (chipSize + chipGap);
            boolean hov = hovered(mouseX, mouseY, cx, row1Y, chipSize, chipSize);
            GuiRenderUtils.fillRoundedRect(graphics, cx, row1Y, chipSize, chipSize, 3, c.argb);
            GuiRenderUtils.drawRoundedOutline(graphics, cx, row1Y, chipSize, chipSize, 3,
                    hov ? 0xFFFFFFFF : 0x66FFFFFF);
            if (hov) {
                hoverTip = "" + pfx + c.code + " " + c.name;
            }
        }

        // Row 2: Style Buttons (B, I, U, S, ?, Reset) + Prefix Toggle (& / §)
        int row2Y = y + 23;
        int btnH = 16;
        int sx = x + 6;
        for (StyleChip st : STYLES) {
            int bw = st.code == 'r' ? 48 : 20;
            boolean hov = hovered(mouseX, mouseY, sx, row2Y, bw, btnH);
            GuiRenderUtils.fillRoundedRect(graphics, sx, row2Y, bw, btnH, 4, hov ? surfaceHover : bgDark);
            GuiRenderUtils.drawRoundedOutline(graphics, sx, row2Y, bw, btnH, 4, hov ? accent : border);
            GuiRenderUtils.drawCenteredText(graphics, font, st.label, sx + bw / 2, row2Y + 4,
                    hov ? accent : 0xFFE2E8F0);
            if (hov) {
                hoverTip = "" + pfx + st.code + " " + st.name;
            }
            sx += bw + 4;
        }

        // Right side: Hover Preview or Prefix Mode Toggle Pill
        String pfxLabel = this.useSectionPrefix ? "Prefix: §" : "Prefix: &";
        int pfxW = font.width(pfxLabel) + 10;
        int pfxX = x + this.width - pfxW - 6;
        boolean pfxHov = hovered(mouseX, mouseY, pfxX, row2Y, pfxW, btnH);
        GuiRenderUtils.fillRoundedRect(graphics, pfxX, row2Y, pfxW, btnH, 4, pfxHov ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(graphics, pfxX, row2Y, pfxW, btnH, 4, pfxHov ? accent : border);
        GuiRenderUtils.drawCenteredText(graphics, font, pfxLabel, pfxX + pfxW / 2, row2Y + 4,
                pfxHov ? 0xFFFFFFFF : accent);

        // Live mini preview label in remaining middle gap
        if (hoverTip != null) {
            int avail = pfxX - sx - 6;
            if (avail > 24) {
                GuiRenderUtils.drawText(graphics, font, GuiRenderUtils.truncate(font, hoverTip, avail),
                        sx + 2, row2Y + 4, 0xFFCBD5E1);
            }
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int x = this.getX();
        int y = this.getY();

        // Check Row 1 Color Swatches
        int chipSize = 14;
        int chipGap = 2;
        int startX = x + 6;
        int row1Y = y + 5;
        for (int i = 0; i < COLORS.length; i++) {
            int cx = startX + i * (chipSize + chipGap);
            if (hovered(mx, my, cx, row1Y, chipSize, chipSize)) {
                dispatchCode(COLORS[i].code);
                return;
            }
        }

        // Check Row 2 Style Buttons
        int row2Y = y + 23;
        int btnH = 16;
        int sx = x + 6;
        for (StyleChip st : STYLES) {
            int bw = st.code == 'r' ? 48 : 20;
            if (hovered(mx, my, sx, row2Y, bw, btnH)) {
                dispatchCode(st.code);
                return;
            }
            sx += bw + 4;
        }

        // Check Prefix Toggle Pill
        Font font = Minecraft.getInstance().font;
        String pfxLabel = this.useSectionPrefix ? "Prefix: §" : "Prefix: &";
        int pfxW = font.width(pfxLabel) + 10;
        int pfxX = x + this.width - pfxW - 6;
        if (hovered(mx, my, pfxX, row2Y, pfxW, btnH)) {
            this.useSectionPrefix = !this.useSectionPrefix;
            if (this.restoreFocus != null) {
                this.restoreFocus.run();
            }
        }
    }

    private void dispatchCode(char code) {
        if (this.codeInserter != null) {
            this.codeInserter.accept("" + activePrefix() + code);
        }
        if (this.restoreFocus != null) {
            this.restoreFocus.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        this.defaultButtonNarrationText(narration);
    }

    private static boolean hovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
