package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.QuickCommandsState;
import net.starclient.setting.MacroListSetting.MacroEntry;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class QuickCommandsScreen extends Screen {
    private static final int PANEL_WIDTH = 490;
    private static final int PANEL_HEIGHT = 400;
    private static final int HEADER_HEIGHT = 42;
    private static final int ROW_HEIGHT = 26;
    private static final int ROW_GAP = 4;

    private static final String[] QUICK_PRESETS = {
            "/l", "/hub", "/spawn", "/warp", "/play bedwars_eight_one", "gg"
    };

    private final Screen parent;
    private EditBox commandBox;
    private int pendingNewKeyBind = GLFW.GLFW_KEY_UNKNOWN;
    private boolean bindingNewKey = false;
    private int bindingEntryIndex = -1;
    private double listScroll;
    private String statusText = "";
    private int statusColor = 0xFF34D399;

    public QuickCommandsScreen(Screen parent) {
        super(Component.literal("Quick Commands & Macros"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        int addCardX = panelX + 16;
        int addCardY = panelY + 50;
        int cmdBoxX = addCardX + 10;
        int cmdBoxY = addCardY + 26;
        int cmdBoxW = 248;

        String prevCmd = this.commandBox != null ? this.commandBox.getValue() : "";
        this.commandBox = new EditBox(this.font, cmdBoxX + 6, cmdBoxY + 6, cmdBoxW - 12, 12, Component.literal(""));
        this.commandBox.setMaxLength(128);
        this.commandBox.setBordered(false);
        this.commandBox.setTextColor(0xFFF8FAFC);
        this.commandBox.setHint(Component.literal("Type /command or chat message..."));
        this.commandBox.setValue(prevCmd);
        addWidget(this.commandBox);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs.getMenuAccentColor();
        int accentDark = cs.getMenuAccentDarkColor();
        int bg = 0xF4000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
        int bgDark = cs.getMenuBackgroundDarkColor();
        int surface = cs.getMenuSurfaceColor();
        int surfaceHover = cs.getMenuSurfaceHoverColor();
        int border = cs.getMenuBorderColor();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        GuiRenderUtils.drawDropShadow(extractor, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 8, 8, 140);
        GuiRenderUtils.fillRoundedRect(extractor, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 8, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, 8, border);

        // Header
        GuiRenderUtils.fillRoundedRect(extractor, panelX, panelY, PANEL_WIDTH, HEADER_HEIGHT, 8, surface);
        extractor.fill(panelX, panelY + HEADER_HEIGHT, panelX + PANEL_WIDTH, panelY + HEADER_HEIGHT + 1, border);

        int iconBadgeW = 22;
        int iconBadgeH = 22;
        int iconBadgeX = panelX + 12;
        int iconBadgeY = panelY + (HEADER_HEIGHT - iconBadgeH) / 2;
        GuiRenderUtils.fillRoundedRect(extractor, iconBadgeX, iconBadgeY, iconBadgeW, iconBadgeH, 5, accentDark);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "⚡", iconBadgeX + iconBadgeW / 2, iconBadgeY + 6, 0xFFFFFFFF);

        GuiRenderUtils.drawText(extractor, this.font, "Quick Commands & Keybind Macros", iconBadgeX + iconBadgeW + 8, panelY + 11, 0xFFF8FAFC, true);
        GuiRenderUtils.drawText(extractor, this.font, "Bind hotkeys to execute server commands or chat messages", iconBadgeX + iconBadgeW + 8, panelY + 24, 0xFF64748B);

        // Close button
        int closeX = panelX + PANEL_WIDTH - 28;
        int closeY = panelY + (HEADER_HEIGHT - 18) / 2;
        boolean closeHover = hovered(closeX, closeY, 18, 18, mouseX, mouseY);
        if (closeHover) {
            GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, 18, 18, 4, 0x33EF4444);
            GuiRenderUtils.drawRoundedOutline(extractor, closeX, closeY, 18, 18, 4, 0x88EF4444);
        }
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + 9, closeY + 4, closeHover ? 0xFFF87171 : 0xFF94A3B8);

        List<MacroEntry> entries = QuickCommandsState.getEntries();
        String countText = entries.size() + (entries.size() == 1 ? " Macro" : " Macros");
        int countW = GuiRenderUtils.textWidth(this.font, countText) + 12;
        int countH = 16;
        int countX = closeX - countW - 8;
        int countY = panelY + (HEADER_HEIGHT - countH) / 2;
        GuiRenderUtils.fillRoundedRect(extractor, countX, countY, countW, countH, 5, GuiRenderUtils.withAlpha(accent, 40));
        GuiRenderUtils.drawRoundedOutline(extractor, countX, countY, countW, countH, 5, GuiRenderUtils.withAlpha(accent, 120));
        GuiRenderUtils.drawCenteredText(extractor, this.font, countText, countX + countW / 2, countY + 4, accent);

        // 1. Add Macro Card
        int addCardX = panelX + 16;
        int addCardY = panelY + 50;
        int addCardW = PANEL_WIDTH - 32;
        int addCardH = 82;
        GuiRenderUtils.fillRoundedRect(extractor, addCardX, addCardY, addCardW, addCardH, 6, surface);
        GuiRenderUtils.drawRoundedOutline(extractor, addCardX, addCardY, addCardW, addCardH, 6, border);

        GuiRenderUtils.drawText(extractor, this.font, "＋  Add New Command or Chat Macro", addCardX + 10, addCardY + 8, 0xFFE2E8F0);
        GuiRenderUtils.drawText(extractor, this.font, "Prefix with / for commands", addCardX + addCardW - 142, addCardY + 8, 0xFF64748B);

        int cmdBoxX = addCardX + 10;
        int cmdBoxY = addCardY + 26;
        int cmdBoxW = 248;
        int fieldH = 24;

        boolean cmdFocused = this.commandBox != null && this.commandBox.isFocused();
        boolean cmdHover = hovered(cmdBoxX, cmdBoxY, cmdBoxW, fieldH, mouseX, mouseY);
        GuiRenderUtils.fillRoundedRect(extractor, cmdBoxX, cmdBoxY, cmdBoxW, fieldH, 5, cmdFocused ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, cmdBoxX, cmdBoxY, cmdBoxW, fieldH, 5,
                cmdFocused ? accent : (cmdHover ? GuiRenderUtils.withAlpha(accent, 120) : border));

        if (this.commandBox != null) {
            this.commandBox.setX(cmdBoxX + 6);
            this.commandBox.setY(cmdBoxY + 6);
            this.commandBox.setWidth(cmdBoxW - 12);
            this.commandBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        // Keybind selector button for new macro
        int keyBtnX = cmdBoxX + cmdBoxW + 8;
        int keyBtnW = 104;
        boolean keyBtnHover = hovered(keyBtnX, cmdBoxY, keyBtnW, fieldH, mouseX, mouseY);
        String newKeyLabel = bindingNewKey
                ? "Press key..."
                : "⌨ " + formatKey(pendingNewKeyBind);
        GuiRenderUtils.fillRoundedRect(extractor, keyBtnX, cmdBoxY, keyBtnW, fieldH, 5,
                bindingNewKey ? accentDark : (keyBtnHover ? surfaceHover : bgDark));
        GuiRenderUtils.drawRoundedOutline(extractor, keyBtnX, cmdBoxY, keyBtnW, fieldH, 5,
                bindingNewKey ? 0xFFFACC15 : (keyBtnHover ? accent : border));
        GuiRenderUtils.drawCenteredText(extractor, this.font, newKeyLabel, keyBtnX + keyBtnW / 2, cmdBoxY + 8,
                bindingNewKey ? 0xFFFACC15 : (pendingNewKeyBind == GLFW.GLFW_KEY_UNKNOWN ? 0xFF94A3B8 : accent));

        // Add button
        int addBtnX = keyBtnX + keyBtnW + 8;
        int addBtnW = addCardX + addCardW - 10 - addBtnX;
        boolean addHover = hovered(addBtnX, cmdBoxY, addBtnW, fieldH, mouseX, mouseY);
        GuiRenderUtils.fillRoundedRect(extractor, addBtnX, cmdBoxY, addBtnW, fieldH, 5, addHover ? accentDark : accent);
        GuiRenderUtils.drawRoundedOutline(extractor, addBtnX, cmdBoxY, addBtnW, fieldH, 5, addHover ? 0xFFFFFFFF : accent);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "＋ Add", addBtnX + addBtnW / 2, cmdBoxY + 8, 0xFFFFFFFF, true);

        // Quick Preset Pills
        int presetY = addCardY + 56;
        int presetX = addCardX + 10;
        GuiRenderUtils.drawText(extractor, this.font, "Presets:", presetX, presetY + 4, 0xFF64748B);
        presetX += 46;
        for (String preset : QUICK_PRESETS) {
            int pw = GuiRenderUtils.textWidth(this.font, preset) + 12;
            boolean ph = hovered(presetX, presetY, pw, 16, mouseX, mouseY);
            GuiRenderUtils.fillRoundedRect(extractor, presetX, presetY, pw, 16, 4, ph ? accentDark : bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, presetX, presetY, pw, 16, 4, ph ? accent : border);
            GuiRenderUtils.drawCenteredText(extractor, this.font, preset, presetX + pw / 2, presetY + 4, ph ? 0xFFFFFFFF : 0xFFCBD5E1);
            presetX += pw + 5;
        }

        // 2. Saved Macros List Card
        int listCardX = panelX + 16;
        int listCardY = panelY + 140;
        int listCardW = PANEL_WIDTH - 32;
        int listCardH = (panelY + PANEL_HEIGHT - 36) - listCardY;

        GuiRenderUtils.fillRoundedRect(extractor, listCardX, listCardY, listCardW, listCardH, 6, surface);
        GuiRenderUtils.drawRoundedOutline(extractor, listCardX, listCardY, listCardW, listCardH, 6, border);

        int listHeaderH = 20;
        GuiRenderUtils.fillRoundedRect(extractor, listCardX + 1, listCardY + 1, listCardW - 2, listHeaderH, 5, bgDark);
        extractor.fill(listCardX, listCardY + listHeaderH, listCardX + listCardW, listCardY + listHeaderH + 1, border);

        GuiRenderUtils.drawText(extractor, this.font, "Active", listCardX + 10, listCardY + 6, 0xFF94A3B8);
        GuiRenderUtils.drawText(extractor, this.font, "Command / Message", listCardX + 52, listCardY + 6, 0xFF94A3B8);
        GuiRenderUtils.drawText(extractor, this.font, "Keybind", listCardX + listCardW - 166, listCardY + 6, 0xFF94A3B8);
        GuiRenderUtils.drawText(extractor, this.font, "Actions", listCardX + listCardW - 72, listCardY + 6, 0xFF94A3B8);

        int viewportTop = listCardY + listHeaderH + 4;
        int viewportBottom = listCardY + listCardH - 4;
        int viewportH = viewportBottom - viewportTop;
        double maxScroll = Math.max(0, entries.size() * (ROW_HEIGHT + ROW_GAP) - viewportH);
        listScroll = Math.clamp(listScroll, 0.0, maxScroll);

        extractor.enableScissor(listCardX + 2, viewportTop, listCardX + listCardW - 2, viewportBottom);
        if (entries.isEmpty()) {
            GuiRenderUtils.drawCenteredText(extractor, this.font,
                    "No command macros added yet. Use the form above or click a preset!",
                    listCardX + listCardW / 2, viewportTop + viewportH / 2 - 4, 0xFF64748B);
        } else {
            int rowY = (int) Math.round(viewportTop - listScroll);
            for (int i = 0; i < entries.size(); i++) {
                MacroEntry entry = entries.get(i);
                if (rowY + ROW_HEIGHT >= viewportTop && rowY <= viewportBottom) {
                    int rx = listCardX + 6;
                    int rw = listCardW - 12;
                    boolean rowHover = hovered(rx, rowY, rw, ROW_HEIGHT, mouseX, mouseY);
                    GuiRenderUtils.fillRoundedRect(extractor, rx, rowY, rw, ROW_HEIGHT, 5, rowHover ? surfaceHover : bgDark);
                    GuiRenderUtils.drawRoundedOutline(extractor, rx, rowY, rw, ROW_HEIGHT, 5, border);

                    // Toggle switch
                    GuiRenderUtils.drawToggleSwitch(extractor, rx + 6, rowY + 6, 24, 14,
                            entry.isEnabled() ? 1.0F : 0.0F, accent, 0xFF334155);

                    // Command text
                    String cmd = entry.getCommand();
                    boolean isSlash = cmd.startsWith("/");
                    String shownCmd = GuiRenderUtils.truncate(this.font, cmd, rw - 225);
                    GuiRenderUtils.drawText(extractor, this.font, shownCmd, rx + 44, rowY + 9,
                            !entry.isEnabled() ? 0xFF64748B : (isSlash ? accent : 0xFFF8FAFC));

                    // Keybind button
                    boolean isBindingThis = (bindingEntryIndex == i);
                    String kText = isBindingThis ? "Press key..." : ("⌨ " + formatKey(entry.getKeyBind()));
                    int kbX = rx + rw - 166;
                    int kbW = 84;
                    int kbY = rowY + 4;
                    int kbH = 18;
                    boolean kbHover = hovered(kbX, kbY, kbW, kbH, mouseX, mouseY);
                    GuiRenderUtils.fillRoundedRect(extractor, kbX, kbY, kbW, kbH, 4,
                            isBindingThis ? accentDark : (kbHover ? surface : 0xFF0F172A));
                    GuiRenderUtils.drawRoundedOutline(extractor, kbX, kbY, kbW, kbH, 4,
                            isBindingThis ? 0xFFFACC15 : (kbHover ? accent : border));
                    GuiRenderUtils.drawCenteredText(extractor, this.font, kText, kbX + kbW / 2, kbY + 5,
                            isBindingThis ? 0xFFFACC15 : (entry.getKeyBind() == GLFW.GLFW_KEY_UNKNOWN ? 0xFF64748B : 0xFFE2E8F0));

                    // Run button
                    int runX = kbX + kbW + 6;
                    int runW = 44;
                    boolean runHover = hovered(runX, kbY, runW, kbH, mouseX, mouseY);
                    GuiRenderUtils.fillRoundedRect(extractor, runX, kbY, runW, kbH, 4, runHover ? accentDark : 0xFF0F172A);
                    GuiRenderUtils.drawRoundedOutline(extractor, runX, kbY, runW, kbH, 4, runHover ? accent : border);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, "▶ Run", runX + runW / 2, kbY + 5,
                            runHover ? 0xFFFFFFFF : accent);

                    // Delete button
                    int delX = runX + runW + 6;
                    int delW = 20;
                    boolean delHover = hovered(delX, kbY, delW, kbH, mouseX, mouseY);
                    GuiRenderUtils.fillRoundedRect(extractor, delX, kbY, delW, kbH, 4, delHover ? 0x33EF4444 : 0xFF0F172A);
                    GuiRenderUtils.drawRoundedOutline(extractor, delX, kbY, delW, kbH, 4, delHover ? 0xFFEF4444 : border);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", delX + delW / 2, kbY + 5,
                            delHover ? 0xFFF87171 : 0xFF94A3B8);
                }
                rowY += ROW_HEIGHT + ROW_GAP;
            }
        }
        extractor.disableScissor();

        // Footer status & Done button
        int footerY = panelY + PANEL_HEIGHT - 28;
        if (statusText != null && !statusText.isEmpty()) {
            GuiRenderUtils.drawText(extractor, this.font, statusText, panelX + 18, footerY + 6, statusColor);
        } else {
            GuiRenderUtils.drawText(extractor, this.font,
                    "Tip: Press Backspace or Delete while rebinding to unbind a macro key.",
                    panelX + 18, footerY + 6, 0xFF64748B);
        }

        int doneW = 68;
        int doneH = 20;
        int doneX = panelX + PANEL_WIDTH - 16 - doneW;
        boolean doneHover = hovered(doneX, footerY, doneW, doneH, mouseX, mouseY);
        GuiRenderUtils.fillRoundedRect(extractor, doneX, footerY, doneW, doneH, 5, doneHover ? accentDark : accent);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Done", doneX + doneW / 2, footerY + 6, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        // Close button or Done button
        int closeX = panelX + PANEL_WIDTH - 28;
        int closeY = panelY + (HEADER_HEIGHT - 18) / 2;
        int footerY = panelY + PANEL_HEIGHT - 28;
        int doneX = panelX + PANEL_WIDTH - 16 - 68;
        if (hovered(closeX, closeY, 18, 18, mouseX, mouseY) || hovered(doneX, footerY, 68, 20, mouseX, mouseY)) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }

        int addCardX = panelX + 16;
        int addCardY = panelY + 50;
        int addCardW = PANEL_WIDTH - 32;
        int cmdBoxX = addCardX + 10;
        int cmdBoxY = addCardY + 26;
        int cmdBoxW = 248;
        int fieldH = 24;

        if (hovered(cmdBoxX, cmdBoxY, cmdBoxW, fieldH, mouseX, mouseY)) {
            bindingNewKey = false;
            bindingEntryIndex = -1;
            this.commandBox.setFocused(true);
            this.setFocused(this.commandBox);
            return super.mouseClicked(event, action);
        } else if (this.commandBox != null) {
            this.commandBox.setFocused(false);
        }

        // New keybind button
        int keyBtnX = cmdBoxX + cmdBoxW + 8;
        int keyBtnW = 104;
        if (hovered(keyBtnX, cmdBoxY, keyBtnW, fieldH, mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                pendingNewKeyBind = GLFW.GLFW_KEY_UNKNOWN;
                bindingNewKey = false;
            } else {
                bindingNewKey = !bindingNewKey;
                bindingEntryIndex = -1;
            }
            return true;
        }

        // Add button
        int addBtnX = keyBtnX + keyBtnW + 8;
        int addBtnW = addCardX + addCardW - 10 - addBtnX;
        if (hovered(addBtnX, cmdBoxY, addBtnW, fieldH, mouseX, mouseY)) {
            submitNewMacro();
            return true;
        }

        // Preset pills
        int presetY = addCardY + 56;
        int presetX = addCardX + 10 + 46;
        for (String preset : QUICK_PRESETS) {
            int pw = GuiRenderUtils.textWidth(this.font, preset) + 12;
            if (hovered(presetX, presetY, pw, 16, mouseX, mouseY)) {
                if (this.commandBox != null) {
                    this.commandBox.setValue(preset);
                    this.commandBox.setFocused(true);
                    this.setFocused(this.commandBox);
                }
                return true;
            }
            presetX += pw + 5;
        }

        // List rows
        int listCardX = panelX + 16;
        int listCardY = panelY + 140;
        int listCardW = PANEL_WIDTH - 32;
        int listCardH = (panelY + PANEL_HEIGHT - 36) - listCardY;
        int viewportTop = listCardY + 24;
        int viewportBottom = listCardY + listCardH - 4;

        if (mouseY >= viewportTop && mouseY <= viewportBottom) {
            List<MacroEntry> entries = QuickCommandsState.getEntries();
            int rowY = (int) Math.round(viewportTop - listScroll);
            for (int i = 0; i < entries.size(); i++) {
                MacroEntry entry = entries.get(i);
                int rx = listCardX + 6;
                int rw = listCardW - 12;
                if (hovered(rx, rowY, rw, ROW_HEIGHT, mouseX, mouseY)) {
                    int kbX = rx + rw - 166;
                    int kbW = 84;
                    int kbY = rowY + 4;
                    int kbH = 18;
                    int runX = kbX + kbW + 6;
                    int runW = 44;
                    int delX = runX + runW + 6;
                    int delW = 20;

                    if (hovered(delX, kbY, delW, kbH, mouseX, mouseY)) {
                        QuickCommandsState.removeAt(i);
                        bindingEntryIndex = -1;
                        setStatus("Removed macro.", 0xFFF87171);
                        return true;
                    }
                    if (hovered(runX, kbY, runW, kbH, mouseX, mouseY)) {
                        Minecraft client = Minecraft.getInstance();
                        if (client.player != null) {
                            QuickCommandsState.executeMacro(client, entry.getCommand());
                            setStatus("Executed: " + entry.getCommand(), 0xFF34D399);
                        } else {
                            setStatus("Join a world or server first to run macros.", 0xFFF59E0B);
                        }
                        return true;
                    }
                    if (hovered(kbX, kbY, kbW, kbH, mouseX, mouseY)) {
                        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                            entry.setKeyBind(GLFW.GLFW_KEY_UNKNOWN);
                            bindingEntryIndex = -1;
                            QuickCommandsState.save();
                        } else {
                            bindingEntryIndex = (bindingEntryIndex == i) ? -1 : i;
                            bindingNewKey = false;
                        }
                        return true;
                    }
                    // Clicking toggle or row toggles enabled state
                    entry.toggleEnabled();
                    QuickCommandsState.save();
                    return true;
                }
                rowY += ROW_HEIGHT + ROW_GAP;
            }
        }

        bindingNewKey = false;
        bindingEntryIndex = -1;
        return super.mouseClicked(event, action);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.listScroll -= verticalAmount * 20.0;
        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        int key = event.key();

        if (bindingNewKey) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                bindingNewKey = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                pendingNewKeyBind = GLFW.GLFW_KEY_UNKNOWN;
                bindingNewKey = false;
                return true;
            }
            pendingNewKeyBind = key;
            bindingNewKey = false;
            return true;
        }

        if (bindingEntryIndex >= 0) {
            List<MacroEntry> entries = QuickCommandsState.getEntries();
            if (bindingEntryIndex < entries.size()) {
                MacroEntry entry = entries.get(bindingEntryIndex);
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    bindingEntryIndex = -1;
                    return true;
                }
                if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                    entry.setKeyBind(GLFW.GLFW_KEY_UNKNOWN);
                } else {
                    entry.setKeyBind(key);
                }
                QuickCommandsState.save();
            }
            bindingEntryIndex = -1;
            return true;
        }

        if (this.commandBox != null && this.commandBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                submitNewMacro();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                this.commandBox.setFocused(false);
                this.setFocused(null);
                return true;
            }
            if (this.commandBox.keyPressed(event)) {
                return true;
            }
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }

        return super.keyPressed(event);
    }

    private void submitNewMacro() {
        if (this.commandBox == null) return;
        String val = this.commandBox.getValue().trim();
        if (val.isEmpty()) {
            setStatus("Please enter a command or chat message first.", 0xFFF59E0B);
            return;
        }
        QuickCommandsState.addEntry(val, pendingNewKeyBind);
        this.commandBox.setValue("");
        this.pendingNewKeyBind = GLFW.GLFW_KEY_UNKNOWN;
        setStatus("Added macro: " + val, 0xFF34D399);
    }

    private void setStatus(String text, int color) {
        this.statusText = text;
        this.statusColor = color;
    }

    private static String formatKey(int key) {
        if (key <= 0) return "None";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isBlank()) {
            return name.toUpperCase();
        }
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_F1 -> "F1";
            case GLFW.GLFW_KEY_F2 -> "F2";
            case GLFW.GLFW_KEY_F3 -> "F3";
            case GLFW.GLFW_KEY_F4 -> "F4";
            case GLFW.GLFW_KEY_F5 -> "F5";
            case GLFW.GLFW_KEY_F6 -> "F6";
            case GLFW.GLFW_KEY_F7 -> "F7";
            case GLFW.GLFW_KEY_F8 -> "F8";
            case GLFW.GLFW_KEY_F9 -> "F9";
            case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11";
            case GLFW.GLFW_KEY_F12 -> "F12";
            default -> "KEY " + key;
        };
    }

    private static boolean hovered(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
