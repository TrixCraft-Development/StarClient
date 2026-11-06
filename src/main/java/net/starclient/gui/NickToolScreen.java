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
import net.starclient.module.NickToolState;
import net.starclient.setting.NickEntryListSetting.NickEntry;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class NickToolScreen extends Screen {
    private static final int PANEL_WIDTH = 480;
    private static final int PANEL_HEIGHT = 410;
    private static final int HEADER_HEIGHT = 42;

    private final Screen parent;
    private EditBox yourNickBox;
    private EditBox realBox;
    private EditBox nickBox;
    private String status = "";
    private double listScroll;

    public NickToolScreen(Screen parent) {
        super(Component.literal("Custom Nicknames"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        int selfCardX = panelX + 16;
        int selfCardY = panelY + 50;
        int selfCardW = PANEL_WIDTH - 32;
        int selfBoxX = selfCardX + 10;
        int selfBoxY = selfCardY + 24;
        int selfBoxW = selfCardW - 20;

        String prevSelf = this.yourNickBox != null ? this.yourNickBox.getValue() : NickToolState.getSelfNick();
        this.yourNickBox = new EditBox(this.font, selfBoxX + 6, selfBoxY + 5, selfBoxW - 12, 12, Component.literal(""));
        this.yourNickBox.setMaxLength(48);
        this.yourNickBox.setBordered(false);
        this.yourNickBox.setTextColor(0xFFF8FAFC);
        this.yourNickBox.setHint(Component.literal("Leave empty to keep your real username"));
        this.yourNickBox.setValue(prevSelf == null ? "" : prevSelf);
        this.yourNickBox.setResponder(NickToolState::setSelfNick);
        addWidget(this.yourNickBox);

        int addCardX = panelX + 16;
        int addCardW = PANEL_WIDTH - 32;
        int addCardY = panelY + 114;
        int realBoxX = addCardX + 10;
        int realBoxW = 152;
        int nickBoxX = addCardX + 186;
        int nickBoxW = 162;
        int fieldY = addCardY + 28;

        String prevReal = this.realBox != null ? this.realBox.getValue() : "";
        this.realBox = new EditBox(this.font, realBoxX + 6, fieldY + 6, realBoxW - 12, 12, Component.literal(""));
        this.realBox.setMaxLength(32);
        this.realBox.setBordered(false);
        this.realBox.setTextColor(0xFFF8FAFC);
        this.realBox.setHint(Component.literal("Real username..."));
        this.realBox.setValue(prevReal);
        addWidget(this.realBox);

        String prevNick = this.nickBox != null ? this.nickBox.getValue() : "";
        this.nickBox = new EditBox(this.font, nickBoxX + 6, fieldY + 6, nickBoxW - 12, 12, Component.literal(""));
        this.nickBox.setMaxLength(48);
        this.nickBox.setBordered(false);
        this.nickBox.setTextColor(0xFFF8FAFC);
        this.nickBox.setHint(Component.literal("Custom nickname..."));
        this.nickBox.setValue(prevNick);
        addWidget(this.nickBox);
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

        // Modal container with drop shadow & rounded outline
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
        GuiRenderUtils.drawCenteredText(extractor, this.font, "🏷", iconBadgeX + iconBadgeW / 2, iconBadgeY + 6, 0xFFFFFFFF);

        GuiRenderUtils.drawText(extractor, this.font, "Custom Nicknames", iconBadgeX + iconBadgeW + 8, panelY + 11, 0xFFF8FAFC, true);
        GuiRenderUtils.drawText(extractor, this.font, "Client-side display names & tags", iconBadgeX + iconBadgeW + 8, panelY + 24, 0xFF64748B);

        // Header Close Button
        int closeX = panelX + PANEL_WIDTH - 28;
        int closeY = panelY + (HEADER_HEIGHT - 18) / 2;
        boolean closeHover = hovered(closeX, closeY, 18, 18, mouseX, mouseY);
        if (closeHover) {
            GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, 18, 18, 4, 0x33EF4444);
            GuiRenderUtils.drawRoundedOutline(extractor, closeX, closeY, 18, 18, 4, 0x88EF4444);
        }
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + 9, closeY + 4, closeHover ? 0xFFF87171 : 0xFF94A3B8);

        // Active entries count badge
        List<NickEntry> entries = NickToolState.getEntries();
        String countText = entries.size() + (entries.size() == 1 ? " Player" : " Players");
        int countW = GuiRenderUtils.textWidth(this.font, countText) + 12;
        int countH = 16;
        int countX = closeX - countW - 8;
        int countY = panelY + (HEADER_HEIGHT - countH) / 2;
        GuiRenderUtils.fillRoundedRect(extractor, countX, countY, countW, countH, 5, GuiRenderUtils.withAlpha(accent, 40));
        GuiRenderUtils.drawRoundedOutline(extractor, countX, countY, countW, countH, 5, GuiRenderUtils.withAlpha(accent, 120));
        GuiRenderUtils.drawCenteredText(extractor, this.font, countText, countX + countW / 2, countY + 4, accent);

        // 1. SECTION: Your Own Display Name
        int selfCardX = panelX + 16;
        int selfCardY = panelY + 50;
        int selfCardW = PANEL_WIDTH - 32;
        int selfCardH = 56;
        GuiRenderUtils.fillRoundedRect(extractor, selfCardX, selfCardY, selfCardW, selfCardH, 6, surface);
        GuiRenderUtils.drawRoundedOutline(extractor, selfCardX, selfCardY, selfCardW, selfCardH, 6, border);

        GuiRenderUtils.drawText(extractor, this.font, "👤  Your Display Name", selfCardX + 10, selfCardY + 8, 0xFFE2E8F0);
        GuiRenderUtils.drawText(extractor, this.font, "Shown on your client only", selfCardX + selfCardW - 136, selfCardY + 8, 0xFF64748B);

        int selfBoxX = selfCardX + 10;
        int selfBoxY = selfCardY + 24;
        int selfBoxW = selfCardW - 20;
        boolean selfFocused = this.yourNickBox != null && this.yourNickBox.isFocused();
        boolean selfHover = hovered(selfBoxX, selfBoxY, selfBoxW, 22, mouseX, mouseY);
        int selfBoxBg = selfFocused ? surfaceHover : bgDark;
        int selfBoxBorder = selfFocused ? accent : (selfHover ? GuiRenderUtils.withAlpha(accent, 120) : border);
        GuiRenderUtils.fillRoundedRect(extractor, selfBoxX, selfBoxY, selfBoxW, 22, 5, selfBoxBg);
        GuiRenderUtils.drawRoundedOutline(extractor, selfBoxX, selfBoxY, selfBoxW, 22, 5, selfBoxBorder);

        if (this.yourNickBox != null) {
            this.yourNickBox.setX(selfBoxX + 6);
            this.yourNickBox.setY(selfBoxY + 5);
            this.yourNickBox.setWidth(selfBoxW - 12);
            this.yourNickBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        // 2. SECTION: Add Player Nickname
        int addCardX = panelX + 16;
        int addCardY = panelY + 114;
        int addCardW = PANEL_WIDTH - 32;
        int addCardH = 68;
        GuiRenderUtils.fillRoundedRect(extractor, addCardX, addCardY, addCardW, addCardH, 6, surface);
        GuiRenderUtils.drawRoundedOutline(extractor, addCardX, addCardY, addCardW, addCardH, 6, border);

        GuiRenderUtils.drawText(extractor, this.font, "＋  Add Player Nickname", addCardX + 10, addCardY + 8, 0xFFE2E8F0);
        GuiRenderUtils.drawText(extractor, this.font, "Replaces name in chat & tablist", addCardX + addCardW - 170, addCardY + 8, 0xFF64748B);

        int realBoxX = addCardX + 10;
        int realBoxW = 152;
        int nickBoxX = addCardX + 186;
        int nickBoxW = 162;
        int fieldY = addCardY + 28;

        // Real username field
        boolean realFocused = this.realBox != null && this.realBox.isFocused();
        boolean realHover = hovered(realBoxX, fieldY, realBoxW, 24, mouseX, mouseY);
        int realBoxBg = realFocused ? surfaceHover : bgDark;
        int realBoxBorder = realFocused ? accent : (realHover ? GuiRenderUtils.withAlpha(accent, 120) : border);
        GuiRenderUtils.fillRoundedRect(extractor, realBoxX, fieldY, realBoxW, 24, 5, realBoxBg);
        GuiRenderUtils.drawRoundedOutline(extractor, realBoxX, fieldY, realBoxW, 24, 5, realBoxBorder);

        if (this.realBox != null) {
            this.realBox.setX(realBoxX + 6);
            this.realBox.setY(fieldY + 6);
            this.realBox.setWidth(realBoxW - 12);
            this.realBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        // Arrow between inputs
        GuiRenderUtils.drawText(extractor, this.font, "➔", realBoxX + realBoxW + 7, fieldY + 7, accent);

        // Nick field
        boolean nickFocused = this.nickBox != null && this.nickBox.isFocused();
        boolean nickHover = hovered(nickBoxX, fieldY, nickBoxW, 24, mouseX, mouseY);
        int nickBoxBg = nickFocused ? surfaceHover : bgDark;
        int nickBoxBorder = nickFocused ? accent : (nickHover ? GuiRenderUtils.withAlpha(accent, 120) : border);
        GuiRenderUtils.fillRoundedRect(extractor, nickBoxX, fieldY, nickBoxW, 24, 5, nickBoxBg);
        GuiRenderUtils.drawRoundedOutline(extractor, nickBoxX, fieldY, nickBoxW, 24, 5, nickBoxBorder);

        if (this.nickBox != null) {
            this.nickBox.setX(nickBoxX + 6);
            this.nickBox.setY(fieldY + 6);
            this.nickBox.setWidth(nickBoxW - 12);
            this.nickBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        // Add Button
        int addBtnX = addCardX + addCardW - 10 - 78;
        int addBtnW = 78;
        int addBtnH = 24;
        boolean addHover = hovered(addBtnX, fieldY, addBtnW, addBtnH, mouseX, mouseY);
        int addBg = addHover ? accentDark : accent;
        int addBorder = addHover ? 0xFFFFFFFF : accent;
        GuiRenderUtils.fillRoundedRect(extractor, addBtnX, fieldY, addBtnW, addBtnH, 5, addBg);
        GuiRenderUtils.drawRoundedOutline(extractor, addBtnX, fieldY, addBtnW, addBtnH, 5, addBorder);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "＋ Add", addBtnX + addBtnW / 2, fieldY + (addBtnH - this.font.lineHeight) / 2, 0xFFFFFFFF, true);

        // 3. SECTION: Nicknames List Card
        int listCardX = panelX + 16;
        int listCardY = panelY + 190;
        int listCardW = PANEL_WIDTH - 32;
        int listCardH = (panelY + PANEL_HEIGHT - 38) - listCardY;

        GuiRenderUtils.fillRoundedRect(extractor, listCardX, listCardY, listCardW, listCardH, 6, surface);
        GuiRenderUtils.drawRoundedOutline(extractor, listCardX, listCardY, listCardW, listCardH, 6, border);

        // Table Header
        int listHeaderH = 20;
        GuiRenderUtils.fillRoundedRect(extractor, listCardX + 1, listCardY + 1, listCardW - 2, listHeaderH, 5, bgDark);
        extractor.fill(listCardX, listCardY + listHeaderH, listCardX + listCardW, listCardY + listHeaderH + 1, border);

        GuiRenderUtils.drawText(extractor, this.font, "Real Username", listCardX + 12, listCardY + 6, 0xFF94A3B8);
        GuiRenderUtils.drawText(extractor, this.font, "➔  Custom Nickname", listCardX + 186, listCardY + 6, 0xFF94A3B8);
        GuiRenderUtils.drawText(extractor, this.font, "Action", listCardX + listCardW - 52, listCardY + 6, 0xFF94A3B8);

        int viewportTop = listCardY + listHeaderH + 3;
        int viewportBottom = listCardY + listCardH - 3;
        int viewportHeight = viewportBottom - viewportTop;

        if (entries.isEmpty()) {
            int emptyCenterY = viewportTop + viewportHeight / 2;
            GuiRenderUtils.drawCenteredText(extractor, this.font, "🏷", listCardX + listCardW / 2, emptyCenterY - 18, 0xFF475569);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "No custom nicknames added yet", listCardX + listCardW / 2, emptyCenterY - 4, 0xFF94A3B8);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Enter a real username and custom nick above to get started", listCardX + listCardW / 2, emptyCenterY + 10, 0xFF64748B);
        } else {
            int itemH = 22;
            int itemGap = 3;
            int contentHeight = entries.size() * (itemH + itemGap) - itemGap;
            int maxScroll = Math.max(0, contentHeight - viewportHeight);
            listScroll = Math.clamp(listScroll, 0, maxScroll);

            extractor.enableScissor(listCardX + 2, viewportTop, listCardX + listCardW - 2, viewportBottom);
            int y = viewportTop - (int) listScroll;

            for (NickEntry entry : entries) {
                if (y + itemH >= viewportTop && y <= viewportBottom) {
                    int rowW = listCardW - (maxScroll > 0 ? 16 : 8);
                    int rowX = listCardX + 4;
                    boolean rowHover = hovered(rowX, y, rowW, itemH, mouseX, mouseY);

                    GuiRenderUtils.fillRoundedRect(extractor, rowX, y, rowW, itemH, 4, rowHover ? surfaceHover : bgDark);
                    GuiRenderUtils.drawRoundedOutline(extractor, rowX, y, rowW, itemH, 4,
                            rowHover ? GuiRenderUtils.withAlpha(accent, 100) : GuiRenderUtils.withAlpha(border, 110));

                    // Real Name
                    String realTrunc = GuiRenderUtils.truncate(this.font, entry.real(), 150);
                    GuiRenderUtils.drawText(extractor, this.font, realTrunc, rowX + 8, y + 6, 0xFFF1F5F9);

                    // Arrow
                    GuiRenderUtils.drawText(extractor, this.font, "➔", rowX + 168, y + 6, accent);

                    // Nick
                    String nickTrunc = GuiRenderUtils.truncate(this.font, entry.nick(), 160);
                    GuiRenderUtils.drawText(extractor, this.font, nickTrunc, rowX + 186, y + 6, 0xFFFFFFFF, true);

                    // Delete button
                    int delW = 18;
                    int delH = 16;
                    int delX = rowX + rowW - delW - 4;
                    int delY = y + (itemH - delH) / 2;
                    boolean delHover = hovered(delX, delY, delW, delH, mouseX, mouseY);

                    GuiRenderUtils.fillRoundedRect(extractor, delX, delY, delW, delH, 4, delHover ? 0x33EF4444 : surface);
                    GuiRenderUtils.drawRoundedOutline(extractor, delX, delY, delW, delH, 4, delHover ? 0xFFEF4444 : border);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", delX + delW / 2, delY + 4, delHover ? 0xFFF87171 : 0xFF94A3B8);
                }
                y += itemH + itemGap;
            }
            extractor.disableScissor();

            // Scrollbar
            if (maxScroll > 0) {
                int sbTrackX = listCardX + listCardW - 7;
                int sbTrackY = viewportTop + 1;
                int sbTrackH = viewportHeight - 2;
                int thumbH = Math.max(16, (int) ((float) viewportHeight / contentHeight * sbTrackH));
                int thumbY = sbTrackY + (int) ((float) listScroll / maxScroll * (sbTrackH - thumbH));

                GuiRenderUtils.fillRoundedRect(extractor, sbTrackX, sbTrackY, 3, sbTrackH, 2, bgDark);
                GuiRenderUtils.fillRoundedRect(extractor, sbTrackX, thumbY, 3, thumbH, 2, GuiRenderUtils.withAlpha(accent, 150));
            }
        }

        // 4. FOOTER: Status Notification & Done Button
        int footerY = panelY + PANEL_HEIGHT - 30;

        if (!status.isEmpty()) {
            boolean isSuccess = status.startsWith("✓");
            boolean isWarn = status.startsWith("⚠");
            int statusBg = isSuccess ? 0x2810B981 : (isWarn ? 0x28F59E0B : 0x28F43F5E);
            int statusBorder = isSuccess ? 0x8810B981 : (isWarn ? 0x88F59E0B : 0x88F43F5E);
            int statusFg = isSuccess ? 0xFF34D399 : (isWarn ? 0xFFFBBF24 : 0xFFFB7185);

            int statW = GuiRenderUtils.textWidth(this.font, status) + 14;
            int statH = 18;
            int statX = panelX + 16;
            int statY = footerY;
            GuiRenderUtils.fillRoundedRect(extractor, statX, statY, statW, statH, 4, statusBg);
            GuiRenderUtils.drawRoundedOutline(extractor, statX, statY, statW, statH, 4, statusBorder);
            GuiRenderUtils.drawText(extractor, this.font, status, statX + 7, statY + 5, statusFg);
        } else {
            GuiRenderUtils.drawText(extractor, this.font, "Tip: Press Enter in text fields to quickly add",
                    panelX + 18, footerY + 5, 0xFF64748B);
        }

        int doneX = panelX + PANEL_WIDTH - 16 - 76;
        int doneY = footerY - 2;
        int doneW = 76;
        int doneH = 22;
        boolean doneHover = hovered(doneX, doneY, doneW, doneH, mouseX, mouseY);
        int doneBg = doneHover ? accentDark : accent;
        int doneBorder = doneHover ? 0xFFFFFFFF : accent;
        GuiRenderUtils.fillRoundedRect(extractor, doneX, doneY, doneW, doneH, 5, doneBg);
        GuiRenderUtils.drawRoundedOutline(extractor, doneX, doneY, doneW, doneH, 5, doneBorder);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Done", doneX + doneW / 2, doneY + (doneH - this.font.lineHeight) / 2, 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        if (event.button() != 0) {
            return super.mouseClicked(event, action);
        }
        double mouseX = event.x();
        double mouseY = event.y();

        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;

        // Close button
        int closeX = panelX + PANEL_WIDTH - 28;
        int closeY = panelY + (HEADER_HEIGHT - 18) / 2;
        if (hovered(closeX, closeY, 18, 18, mouseX, mouseY)) {
            closeToParent();
            return true;
        }

        // Done button
        int footerY = panelY + PANEL_HEIGHT - 30;
        int doneX = panelX + PANEL_WIDTH - 16 - 76;
        int doneY = footerY - 2;
        if (hovered(doneX, doneY, 76, 22, mouseX, mouseY)) {
            closeToParent();
            return true;
        }

        // Input containers focus
        int selfCardX = panelX + 16;
        int selfCardY = panelY + 50;
        int selfCardW = PANEL_WIDTH - 32;
        int selfBoxX = selfCardX + 10;
        int selfBoxY = selfCardY + 24;
        int selfBoxW = selfCardW - 20;

        int addCardX = panelX + 16;
        int addCardW = PANEL_WIDTH - 32;
        int addCardY = panelY + 114;
        int realBoxX = addCardX + 10;
        int realBoxW = 152;
        int nickBoxX = addCardX + 186;
        int nickBoxW = 162;
        int fieldY = addCardY + 28;

        if (hovered(selfBoxX, selfBoxY, selfBoxW, 22, mouseX, mouseY)) {
            setFocusedField(this.yourNickBox);
            return true;
        }
        if (hovered(realBoxX, fieldY, realBoxW, 24, mouseX, mouseY)) {
            setFocusedField(this.realBox);
            return true;
        }
        if (hovered(nickBoxX, fieldY, nickBoxW, 24, mouseX, mouseY)) {
            setFocusedField(this.nickBox);
            return true;
        }

        // Add button
        int addBtnX = addCardX + addCardW - 10 - 78;
        if (hovered(addBtnX, fieldY, 78, 24, mouseX, mouseY)) {
            addEntryFromFields();
            return true;
        }

        // List row delete buttons
        int listCardX = panelX + 16;
        int listCardY = panelY + 190;
        int listCardW = PANEL_WIDTH - 32;
        int listCardH = (panelY + PANEL_HEIGHT - 38) - listCardY;
        int viewportTop = listCardY + 23;
        int viewportBottom = listCardY + listCardH - 3;

        if (mouseY >= viewportTop && mouseY <= viewportBottom && mouseX >= listCardX && mouseX <= listCardX + listCardW) {
            List<NickEntry> entries = NickToolState.getEntries();
            int itemH = 22;
            int itemGap = 3;
            int contentHeight = entries.size() * (itemH + itemGap) - itemGap;
            int maxScroll = Math.max(0, contentHeight - (viewportBottom - viewportTop));
            int y = viewportTop - (int) Math.clamp(listScroll, 0, maxScroll);
            int rowW = listCardW - (maxScroll > 0 ? 16 : 8);
            int rowX = listCardX + 4;

            for (NickEntry entry : entries) {
                if (y + itemH >= viewportTop && y <= viewportBottom) {
                    int delW = 18;
                    int delH = 16;
                    int delX = rowX + rowW - delW - 4;
                    int delY = y + (itemH - delH) / 2;
                    if (hovered(delX, delY, delW, delH, mouseX, mouseY)) {
                        NickToolState.removeEntry(entry.real());
                        status = "Removed: " + entry.real();
                        save();
                        return true;
                    }
                }
                y += itemH + itemGap;
            }
        }

        setFocusedField(null);
        return super.mouseClicked(event, action);
    }

    private void setFocusedField(EditBox target) {
        if (this.yourNickBox != null) this.yourNickBox.setFocused(target == this.yourNickBox);
        if (this.realBox != null) this.realBox.setFocused(target == this.realBox);
        if (this.nickBox != null) this.nickBox.setFocused(target == this.nickBox);
        this.setFocused(target);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelX = (this.width - PANEL_WIDTH) / 2;
        int panelY = (this.height - PANEL_HEIGHT) / 2;
        int listCardX = panelX + 16;
        int listCardY = panelY + 190;
        int listCardW = PANEL_WIDTH - 32;
        int listCardH = (panelY + PANEL_HEIGHT - 38) - listCardY;

        if (mouseX >= listCardX && mouseX <= listCardX + listCardW && mouseY >= listCardY && mouseY <= listCardY + listCardH) {
            listScroll -= verticalAmount * 16.0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void addEntryFromFields() {
        String real = this.realBox != null ? this.realBox.getValue().trim() : "";
        String nick = this.nickBox != null ? this.nickBox.getValue().trim() : "";
        if (real.isEmpty()) {
            status = "⚠ Enter a real username first";
            if (this.realBox != null) setFocusedField(this.realBox);
            return;
        }
        if (nick.isEmpty()) {
            status = "⚠ Enter a nickname first";
            if (this.nickBox != null) setFocusedField(this.nickBox);
            return;
        }
        NickToolState.addEntry(real, nick);
        status = "Added: " + real + " ➔ " + nick;
        if (this.realBox != null) this.realBox.setValue("");
        if (this.nickBox != null) this.nickBox.setValue("");
        setFocusedField(null);
        save();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_TAB) {
            if (this.yourNickBox != null && this.yourNickBox.isFocused()) {
                setFocusedField(this.realBox);
                return true;
            } else if (this.realBox != null && this.realBox.isFocused()) {
                setFocusedField(this.nickBox);
                return true;
            } else if (this.nickBox != null && this.nickBox.isFocused()) {
                setFocusedField(this.yourNickBox);
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.realBox != null && this.realBox.isFocused()) {
                if (this.nickBox != null && this.nickBox.getValue().trim().isEmpty()) {
                    setFocusedField(this.nickBox);
                } else {
                    addEntryFromFields();
                }
                return true;
            }
            if (this.nickBox != null && this.nickBox.isFocused()) {
                addEntryFromFields();
                return true;
            }
            if (this.yourNickBox != null && this.yourNickBox.isFocused()) {
                NickToolState.setSelfNick(this.yourNickBox.getValue());
                save();
                status = "Saved your display name";
                setFocusedField(null);
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            closeToParent();
            return true;
        }
        return super.keyPressed(event);
    }

    private void closeToParent() {
        if (this.yourNickBox != null) {
            NickToolState.setSelfNick(this.yourNickBox.getValue());
        }
        save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private void save() {
        StarClient.getInstance().getConfigManager().saveConfig();
    }

    private static boolean hovered(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public void removed() {
        if (this.yourNickBox != null) {
            NickToolState.setSelfNick(this.yourNickBox.getValue());
        }
        save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
