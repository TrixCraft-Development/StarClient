package net.modernclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.modernclient.ClientSettings;
import net.modernclient.ModernClient;
import net.modernclient.module.NickToolState;
import net.modernclient.setting.NickEntryListSetting.NickEntry;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class NickToolScreen extends Screen {
    private static final int PANEL_WIDTH = 460;
    private static final int PANEL_HEIGHT = 380;
    private static final int HEADER_HEIGHT = 40;
    private static final int FIELD_HEIGHT = 20;
    private static final int ROW_HEIGHT = 24;

    private final Screen parent;
    private EditBox yourNickBox;
    private EditBox realBox;
    private EditBox nickBox;
    private String status = "";
    private double listScroll;

    public NickToolScreen(Screen parent) {
        super(Component.literal("Nick-Tool"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int fieldX = panelX + 16;
        int fieldW = PANEL_WIDTH - 32;

        this.yourNickBox = new EditBox(this.font, fieldX, panelY + 74, fieldW, FIELD_HEIGHT, Component.literal(""));
        this.yourNickBox.setMaxLength(48);
        this.yourNickBox.setHint(Component.literal("Leave empty to keep your real name"));
        String self = NickToolState.getSelfNick();
        this.yourNickBox.setValue(self == null ? "" : self);
        addWidget(this.yourNickBox);

        this.realBox = new EditBox(this.font, fieldX, panelY + 128, fieldW, FIELD_HEIGHT, Component.literal(""));
        this.realBox.setMaxLength(32);
        this.realBox.setHint(Component.literal("e.g. Steve2001"));
        addWidget(this.realBox);

        this.nickBox = new EditBox(this.font, fieldX, panelY + 182, fieldW, FIELD_HEIGHT, Component.literal(""));
        this.nickBox.setMaxLength(48);
        this.nickBox.setHint(Component.literal("e.g. ProPlayer"));
        addWidget(this.nickBox);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        ClientSettings cs = ModernClient.getInstance().getClientSettings();
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        extractor.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, cs.getMenuBackgroundColor());
        extractor.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + HEADER_HEIGHT, cs.getMenuBackgroundLightColor());
        extractor.text(this.font, "★ Nick-Tool", panelX + 16, panelY + 12, cs.getMenuAccentColor());

        int fieldX = panelX + 16;
        int fieldW = PANEL_WIDTH - 32;

        extractor.text(this.font, "Your display name (shown to you)", fieldX, panelY + 58, 0xFF94A3B8);
        this.yourNickBox.extractRenderState(extractor, mouseX, mouseY, delta);

        extractor.text(this.font, "Real username", fieldX, panelY + 112, 0xFF94A3B8);
        this.realBox.extractRenderState(extractor, mouseX, mouseY, delta);

        extractor.text(this.font, "Nickname", fieldX, panelY + 166, 0xFF94A3B8);
        this.nickBox.extractRenderState(extractor, mouseX, mouseY, delta);

        int addX = fieldX;
        int addY = panelY + 208;
        boolean addHovered = mouseX >= addX && mouseX <= addX + 130 && mouseY >= addY && mouseY <= addY + 22;
        extractor.fill(addX, addY, addX + 130, addY + 22, addHovered ? cs.getMenuAccentDarkColor() : cs.getMenuAccentColor());
        extractor.text(this.font, "＋ Add Entry", addX + 12, addY + 5, 0xFFFFFFFF);

        extractor.text(this.font, "Nick list:", fieldX, panelY + 244, 0xFF94A3B8);
        int listTop = panelY + 262;
        int listBottom = panelY + PANEL_HEIGHT - 52;
        List<NickEntry> entries = NickToolState.getEntries();
        int contentHeight = entries.isEmpty() ? 0 : (int) Math.ceil(entries.size() * (ROW_HEIGHT + 2.0)) - 2;
        int maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
        listScroll = Math.clamp(listScroll, 0, maxScroll);
        extractor.enableScissor(fieldX, listTop, fieldX + fieldW, listBottom);
        int y = listTop - (int) listScroll;
        for (NickEntry entry : entries) {
            if (y + ROW_HEIGHT > listBottom) break;
            boolean hovered = mouseY >= y && mouseY <= y + ROW_HEIGHT;
            extractor.fill(fieldX, y, fieldX + fieldW, y + ROW_HEIGHT, hovered ? cs.getMenuBackgroundLightColor() : cs.getMenuBackgroundDarkColor());
            String line = "Real: " + entry.real() + "   →   Nick: " + entry.nick();
            line = shorten(line, fieldW - 48);
            extractor.text(this.font, line, fieldX + 6, y + 6, 0xFFE2E8F0);
            boolean delHovered = mouseX >= fieldX + fieldW - 26 && mouseX <= fieldX + fieldW - 6 && mouseY >= y + 4 && mouseY <= y + ROW_HEIGHT - 4;
            extractor.text(this.font, "✕", fieldX + fieldW - 20, y + 6, delHovered ? 0xFFFF6B6B : 0xFF94A3B8);
            y += ROW_HEIGHT + 2;
        }
        extractor.disableScissor();

        int doneX = panelX + PANEL_WIDTH - 106;
        int doneY = panelY + PANEL_HEIGHT - 44;
        boolean doneHovered = mouseX >= doneX && mouseX <= doneX + 90 && mouseY >= doneY && mouseY <= doneY + 24;
        extractor.fill(doneX, doneY, doneX + 90, doneY + 24, doneHovered ? cs.getMenuAccentDarkColor() : cs.getMenuAccentColor());
        extractor.text(this.font, "Done", doneX + 12, doneY + 6, 0xFFFFFFFF);

        if (!status.isEmpty()) {
            extractor.text(this.font, status, fieldX, panelY + PANEL_HEIGHT - 18, 0xFFFBBF24);
        }
    }

    private String shorten(String value, int maxWidth) {
        if (this.font.width(value) <= maxWidth) return value;
        String suffix = "...";
        while (this.font.width(value + suffix) > maxWidth && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value + suffix;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        if (event.button() != 0) {
            return super.mouseClicked(event, action);
        }
        double mouseX = event.x();
        double mouseY = event.y();
        ClientSettings cs = ModernClient.getInstance().getClientSettings();
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int fieldX = panelX + 16;
        int fieldW = PANEL_WIDTH - 32;

        if (within(yourNickBox.getX(), yourNickBox.getY(), yourNickBox.getWidth(), yourNickBox.getHeight(), mouseX, mouseY)) {
            this.setFocused(this.yourNickBox);
            this.yourNickBox.setFocused(true);
            this.realBox.setFocused(false);
            this.nickBox.setFocused(false);
            return true;
        }
        if (within(realBox.getX(), realBox.getY(), realBox.getWidth(), realBox.getHeight(), mouseX, mouseY)) {
            this.setFocused(this.realBox);
            this.yourNickBox.setFocused(false);
            this.realBox.setFocused(true);
            this.nickBox.setFocused(false);
            return true;
        }
        if (within(nickBox.getX(), nickBox.getY(), nickBox.getWidth(), nickBox.getHeight(), mouseX, mouseY)) {
            this.setFocused(this.nickBox);
            this.yourNickBox.setFocused(false);
            this.realBox.setFocused(false);
            this.nickBox.setFocused(true);
            return true;
        }
        this.yourNickBox.setFocused(false);
        this.realBox.setFocused(false);
        this.nickBox.setFocused(false);
        this.setFocused(null);

        int addX = fieldX;
        int addY = panelY + 208;
        if (mouseX >= addX && mouseX <= addX + 130 && mouseY >= addY && mouseY <= addY + 22) {
            addEntryFromFields();
            return true;
        }

        int doneX = panelX + PANEL_WIDTH - 106;
        int doneY = panelY + PANEL_HEIGHT - 44;
        if (mouseX >= doneX && mouseX <= doneX + 90 && mouseY >= doneY && mouseY <= doneY + 24) {
            closeToParent();
            return true;
        }

        int listTop = panelY + 262;
        int listBottom = panelY + PANEL_HEIGHT - 52;
        int y = listTop - (int) listScroll;
        for (NickEntry entry : NickToolState.getEntries()) {
            if (y + ROW_HEIGHT > panelY + PANEL_HEIGHT - 52) break;
            if (mouseX >= fieldX + fieldW - 26 && mouseX <= fieldX + fieldW - 6 && mouseY >= y + 4 && mouseY <= y + ROW_HEIGHT - 4) {
                NickToolState.removeEntry(entry.real());
                status = "Removed: " + entry.real();
                save();
                return true;
            }
            y += ROW_HEIGHT + 2;
        }
        return super.mouseClicked(event, action);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int listTop = panelY + 262;
        int listBottom = panelY + PANEL_HEIGHT - 52;
        int fieldX = panelX + 16;
        int fieldW = PANEL_WIDTH - 32;
        if (mouseX >= fieldX && mouseX <= fieldX + fieldW && mouseY >= listTop && mouseY <= listBottom) {
            listScroll -= verticalAmount * 12;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void addEntryFromFields() {
        String real = realBox.getValue().trim();
        String nick = nickBox.getValue().trim();
        if (real.isEmpty()) {
            status = "Enter a real username first";
            return;
        }
        if (nick.isEmpty()) {
            status = "Enter a nickname first";
            return;
        }
        NickToolState.addEntry(real, nick);
        status = "Added: " + real + " → " + nick;
        realBox.setValue("");
        nickBox.setValue("");
        this.realBox.setFocused(false);
        this.nickBox.setFocused(false);
        save();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && (realBox.isFocused() || nickBox.isFocused())) {
            addEntryFromFields();
            return true;
        }
        NickToolState.setSelfNick(this.yourNickBox.getValue());
        return super.keyPressed(event);
    }

    private void closeToParent() {
        NickToolState.setSelfNick(this.yourNickBox.getValue());
        save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private void save() {
        ModernClient.getInstance().getConfigManager().saveConfig();
    }

    private static boolean within(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public void removed() {
        NickToolState.setSelfNick(this.yourNickBox == null ? "" : this.yourNickBox.getValue());
        save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}