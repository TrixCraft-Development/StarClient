package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.SoundMufflerState;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Glassmorphic Sound Muffler filter editor featuring custom sound ID/substring
 * rules on the left and a live "Recent Sounds" 1-click mute feed on the right.
 */
public class SoundMufflerScreen extends Screen {
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 360;
    private static final int HEADER_HEIGHT = 40;
    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private EditBox filterBox;
    private String statusMessage = "";
    private int statusColor = 0xFF94A3B8;
    private double leftScroll = 0.0;
    private double rightScroll = 0.0;

    public SoundMufflerScreen(Screen parent) {
        super(Component.literal("Sound Muffler — Custom Filters"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int px = (this.width - PANEL_WIDTH) / 2;
        int py = (this.height - PANEL_HEIGHT) / 2;

        String prev = this.filterBox != null ? this.filterBox.getValue() : "";
        int boxX = px + 16;
        int boxY = py + 50;
        int boxW = PANEL_WIDTH - 122;

        this.filterBox = new EditBox(this.font, boxX + 6, boxY + 5, boxW - 12, 12, Component.literal(""));
        this.filterBox.setMaxLength(96);
        this.filterBox.setBordered(false);
        this.filterBox.setTextColor(0xFFF8FAFC);
        this.filterBox.setHint(Component.literal("Add sound ID or keyword (e.g. ghast, anvil, block.chest)..."));
        this.filterBox.setValue(prev);
        addWidget(this.filterBox);
        this.filterBox.setFocused(true);
        this.setFocused(this.filterBox);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs.getMenuAccentColor();
        int accentDark = cs.getMenuAccentDarkColor();
        int bg = 0xF5000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
        int bgDark = cs.getMenuBackgroundDarkColor();
        int surface = cs.getMenuSurfaceColor();
        int surfaceHover = cs.getMenuSurfaceHoverColor();
        int border = cs.getMenuBorderColor();

        int px = (this.width - PANEL_WIDTH) / 2;
        int py = (this.height - PANEL_HEIGHT) / 2;

        GuiRenderUtils.drawDropShadow(extractor, px, py, PANEL_WIDTH, PANEL_HEIGHT, 8, 8, 145);
        GuiRenderUtils.fillRoundedRect(extractor, px, py, PANEL_WIDTH, PANEL_HEIGHT, 8, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, px, py, PANEL_WIDTH, PANEL_HEIGHT, 8, border);

        // Header
        GuiRenderUtils.fillRoundedRect(extractor, px, py, PANEL_WIDTH, HEADER_HEIGHT, 8, surface);
        extractor.fill(px, py + HEADER_HEIGHT - 1, px + PANEL_WIDTH, py + HEADER_HEIGHT, border);

        GuiRenderUtils.drawText(extractor, this.font, "🔇 Sound Muffler — Custom Filters", px + 14, py + 10, accent, true);
        int volPct = Math.round(SoundMufflerState.getMuffleVolumeMultiplier() * 100.0F);
        String subText = volPct == 0
                ? "Muffled sounds are completely silenced (0% volume) • Click '+ Mute' on any recent sound"
                : "Muffled sounds are reduced to " + volPct + "% volume • Click '+ Mute' on any recent sound";
        GuiRenderUtils.drawText(extractor, this.font, GuiRenderUtils.truncate(this.font, subText, PANEL_WIDTH - 120),
                px + 14, py + 23, 0xFF94A3B8);

        int closeX = px + PANEL_WIDTH - 28;
        int closeY = py + 11;
        boolean closeHov = isHovered(mouseX, mouseY, closeX, closeY, 18, 18);
        GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, 18, 18, 4, closeHov ? 0x44F87171 : bgDark);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + 9, closeY + 5,
                closeHov ? 0xFFF87171 : 0xFF94A3B8);

        List<String> filters = SoundMufflerState.getCustomFilters();
        if (!filters.isEmpty()) {
            int clearW = 58;
            int clearX = closeX - clearW - 8;
            boolean clearHov = isHovered(mouseX, mouseY, clearX, closeY, clearW, 18);
            GuiRenderUtils.fillRoundedRect(extractor, clearX, closeY, clearW, 18, 4, clearHov ? 0x44EF4444 : bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, clearX, closeY, clearW, 18, 4, clearHov ? 0xFFEF4444 : border);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Clear All", clearX + clearW / 2, closeY + 5,
                    clearHov ? 0xFFF87171 : 0xFF94A3B8);
        }

        // Input Row
        int boxX = px + 16;
        int boxY = py + 50;
        int boxW = PANEL_WIDTH - 122;
        int boxH = 20;

        GuiRenderUtils.fillRoundedRect(extractor, boxX, boxY, boxW, boxH, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, boxX, boxY, boxW, boxH, 5,
                this.filterBox != null && this.filterBox.isFocused() ? accent : border);
        if (this.filterBox != null) {
            this.filterBox.setPosition(boxX + 6, boxY + 6);
            this.filterBox.setWidth(boxW - 12);
            this.filterBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        int addBtnX = boxX + boxW + 8;
        int addBtnW = 82;
        boolean addHov = isHovered(mouseX, mouseY, addBtnX, boxY, addBtnW, boxH);
        GuiRenderUtils.fillRoundedRect(extractor, addBtnX, boxY, addBtnW, boxH, 5, addHov ? accentDark : accent);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "+ Add Filter", addBtnX + addBtnW / 2, boxY + 6, 0xFFFFFFFF);

        // Status line
        int colHeaderY = boxY + 26;
        int colGap = 12;
        int colW = (PANEL_WIDTH - 32 - colGap) / 2;
        int leftColX = px + 16;
        int rightColX = leftColX + colW + colGap;

        GuiRenderUtils.drawText(extractor, this.font, "Custom Muted Rules (" + filters.size() + ")",
                leftColX, colHeaderY, 0xFFE2E8F0);
        List<String> recent = SoundMufflerState.getRecentSounds();
        GuiRenderUtils.drawText(extractor, this.font, "Recently Played Sounds (" + recent.size() + ")",
                rightColX, colHeaderY, 0xFFE2E8F0);

        if (!this.statusMessage.isEmpty()) {
            int sw = this.font.width(this.statusMessage);
            GuiRenderUtils.drawText(extractor, this.font, this.statusMessage,
                    px + PANEL_WIDTH - 16 - sw, colHeaderY, this.statusColor);
        }

        int listTop = colHeaderY + 14;
        int listBottom = py + PANEL_HEIGHT - 12;
        int listH = listBottom - listTop;

        // Left column: Custom Muted Rules
        int maxLeftScroll = Math.max(0, filters.size() * (ROW_HEIGHT + 4) - listH);
        this.leftScroll = Math.clamp(this.leftScroll, 0.0, maxLeftScroll);

        GuiRenderUtils.fillRoundedRect(extractor, leftColX - 2, listTop - 2, colW + 4, listH + 4, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, leftColX - 2, listTop - 2, colW + 4, listH + 4, 5, border);
        extractor.enableScissor(leftColX, listTop, leftColX + colW, listBottom);
        if (filters.isEmpty()) {
            GuiRenderUtils.drawCenteredText(extractor, this.font, "No custom rules yet",
                    leftColX + colW / 2, listTop + listH / 2 - 4, 0xFF64748B);
        } else {
            int ry = (int) Math.round(listTop + 2 - this.leftScroll);
            for (int i = 0; i < filters.size(); i++) {
                String rule = filters.get(i);
                if (ry + ROW_HEIGHT >= listTop && ry <= listBottom) {
                    boolean hov = isHovered(mouseX, mouseY, leftColX + 2, ry, colW - 4, ROW_HEIGHT);
                    GuiRenderUtils.fillRoundedRect(extractor, leftColX + 2, ry, colW - 4, ROW_HEIGHT, 4,
                            hov ? surfaceHover : surface);
                    GuiRenderUtils.drawText(extractor, this.font,
                            GuiRenderUtils.truncate(this.font, rule, colW - 34),
                            leftColX + 8, ry + 7, 0xFFF8FAFC);

                    int delX = leftColX + colW - 22;
                    int delY = ry + 3;
                    boolean delHov = isHovered(mouseX, mouseY, delX, delY, 16, 16);
                    GuiRenderUtils.fillRoundedRect(extractor, delX, delY, 16, 16, 4, delHov ? 0x44EF4444 : bgDark);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", delX + 8, delY + 4,
                            delHov ? 0xFFF87171 : 0xFF94A3B8);
                }
                ry += ROW_HEIGHT + 4;
            }
        }
        extractor.disableScissor();

        // Right column: Recently Played Sounds (1-click Mute)
        int maxRightScroll = Math.max(0, recent.size() * (ROW_HEIGHT + 4) - listH);
        this.rightScroll = Math.clamp(this.rightScroll, 0.0, maxRightScroll);

        GuiRenderUtils.fillRoundedRect(extractor, rightColX - 2, listTop - 2, colW + 4, listH + 4, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, rightColX - 2, listTop - 2, colW + 4, listH + 4, 5, border);
        extractor.enableScissor(rightColX, listTop, rightColX + colW, listBottom);
        if (recent.isEmpty()) {
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Play in-world to see sounds",
                    rightColX + colW / 2, listTop + listH / 2 - 4, 0xFF64748B);
        } else {
            int ry = (int) Math.round(listTop + 2 - this.rightScroll);
            for (String soundId : recent) {
                if (ry + ROW_HEIGHT >= listTop && ry <= listBottom) {
                    boolean alreadyMuted = SoundMufflerState.isMatchedByCustomRule(soundId);
                    boolean hov = isHovered(mouseX, mouseY, rightColX + 2, ry, colW - 4, ROW_HEIGHT);
                    GuiRenderUtils.fillRoundedRect(extractor, rightColX + 2, ry, colW - 4, ROW_HEIGHT, 4,
                            hov ? surfaceHover : surface);

                    int btnW = 44;
                    GuiRenderUtils.drawText(extractor, this.font,
                            GuiRenderUtils.truncate(this.font, soundId, colW - btnW - 16),
                            rightColX + 8, ry + 7, alreadyMuted ? 0xFF94A3B8 : 0xFFE2E8F0);

                    int muteX = rightColX + colW - btnW - 6;
                    int muteY = ry + 3;
                    boolean muteHov = !alreadyMuted && isHovered(mouseX, mouseY, muteX, muteY, btnW, 16);
                    int mBg = alreadyMuted ? 0x2234D399 : (muteHov ? accent : bgDark);
                    int mBorder = alreadyMuted ? 0x6634D399 : (muteHov ? accent : border);
                    int mFg = alreadyMuted ? 0xFF34D399 : (muteHov ? 0xFFFFFFFF : accent);
                    GuiRenderUtils.fillRoundedRect(extractor, muteX, muteY, btnW, 16, 4, mBg);
                    GuiRenderUtils.drawRoundedOutline(extractor, muteX, muteY, btnW, 16, 4, mBorder);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, alreadyMuted ? "✓ Muted" : "+ Mute",
                            muteX + btnW / 2, muteY + 4, mFg);
                }
                ry += ROW_HEIGHT + 4;
            }
        }
        extractor.disableScissor();
    }

    private void tryAddCurrentFilter() {
        if (this.filterBox == null) return;
        String input = this.filterBox.getValue().trim();
        if (input.isEmpty()) {
            this.statusMessage = "Enter a sound ID first";
            this.statusColor = 0xFFF87171;
            return;
        }
        if (SoundMufflerState.addCustomFilter(input)) {
            this.filterBox.setValue("");
            this.statusMessage = "Muted '" + input + "'";
            this.statusColor = 0xFF34D399;
            saveClientConfig();
        } else {
            this.statusMessage = "Already muted";
            this.statusColor = 0xFFFBBF24;
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int px = (this.width - PANEL_WIDTH) / 2;
        int py = (this.height - PANEL_HEIGHT) / 2;

        int closeX = px + PANEL_WIDTH - 28;
        int closeY = py + 11;
        if (isHovered(mx, my, closeX, closeY, 18, 18)) {
            playClick();
            onClose();
            return true;
        }

        List<String> filters = SoundMufflerState.getCustomFilters();
        if (!filters.isEmpty()) {
            int clearW = 58;
            int clearX = closeX - clearW - 8;
            if (isHovered(mx, my, clearX, closeY, clearW, 18)) {
                playClick();
                SoundMufflerState.clearCustomFilters();
                this.statusMessage = "Cleared custom filters";
                this.statusColor = 0xFF94A3B8;
                saveClientConfig();
                return true;
            }
        }

        int boxX = px + 16;
        int boxY = py + 50;
        int boxW = PANEL_WIDTH - 122;
        int boxH = 20;
        if (this.filterBox != null && isHovered(mx, my, boxX, boxY, boxW, boxH)) {
            this.filterBox.setFocused(true);
            this.setFocused(this.filterBox);
            this.filterBox.mouseClicked(event, doubleClick);
            return true;
        }

        int addBtnX = boxX + boxW + 8;
        if (isHovered(mx, my, addBtnX, boxY, 82, boxH)) {
            playClick();
            tryAddCurrentFilter();
            return true;
        }

        int colGap = 12;
        int colW = (PANEL_WIDTH - 32 - colGap) / 2;
        int leftColX = px + 16;
        int rightColX = leftColX + colW + colGap;
        int listTop = boxY + 40;
        int listBottom = py + PANEL_HEIGHT - 12;

        if (my >= listTop && my <= listBottom) {
            // Left column: remove custom filter
            if (mx >= leftColX && mx <= leftColX + colW) {
                int ry = (int) Math.round(listTop + 2 - this.leftScroll);
                for (int i = 0; i < filters.size(); i++) {
                    int delX = leftColX + colW - 22;
                    int delY = ry + 3;
                    if (isHovered(mx, my, delX, delY, 16, 16)) {
                        playClick();
                        String removed = filters.get(i);
                        SoundMufflerState.removeCustomFilterAt(i);
                        this.statusMessage = "Unmuted '" + removed + "'";
                        this.statusColor = 0xFF94A3B8;
                        saveClientConfig();
                        return true;
                    }
                    ry += ROW_HEIGHT + 4;
                }
            }

            // Right column: 1-click mute recent sound
            if (mx >= rightColX && mx <= rightColX + colW) {
                List<String> recent = SoundMufflerState.getRecentSounds();
                int ry = (int) Math.round(listTop + 2 - this.rightScroll);
                int btnW = 44;
                for (String soundId : recent) {
                    int muteX = rightColX + colW - btnW - 6;
                    int muteY = ry + 3;
                    if (isHovered(mx, my, muteX, muteY, btnW, 16) && !SoundMufflerState.isMatchedByCustomRule(soundId)) {
                        playClick();
                        if (SoundMufflerState.addCustomFilter(soundId)) {
                            this.statusMessage = "Muted '" + soundId + "'";
                            this.statusColor = 0xFF34D399;
                            saveClientConfig();
                        }
                        return true;
                    }
                    ry += ROW_HEIGHT + 4;
                }
            }
        }

        if (this.filterBox != null) {
            this.filterBox.setFocused(false);
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int px = (this.width - PANEL_WIDTH) / 2;
        if (mouseX < px + PANEL_WIDTH / 2.0) {
            this.leftScroll -= verticalAmount * 22.0;
        } else {
            this.rightScroll -= verticalAmount * 22.0;
        }
        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.filterBox != null && this.filterBox.isFocused()) {
                tryAddCurrentFilter();
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        saveClientConfig();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static void saveClientConfig() {
        StarClient sc = StarClient.getInstance();
        if (sc != null && sc.getConfigManager() != null) {
            sc.getConfigManager().saveConfig();
        }
    }

    private static boolean isHovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
