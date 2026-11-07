package net.starclient.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.PinnedNotesState;
import net.starclient.setting.NoteListSetting;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Glassmorphic modal editor screen for adding, checking off, and deleting Pinned Notes.
 */
public class PinnedNotesScreen extends Screen {
    private static final int PANEL_WIDTH = 440;
    private static final int PANEL_HEIGHT = 340;
    private static final int HEADER_HEIGHT = 40;
    private static final int ROW_HEIGHT = 24;

    private final Screen parent;
    private EditBox noteBox;
    private String statusMessage = "";
    private int statusColor = 0xFF94A3B8;
    private double listScroll = 0.0;

    public PinnedNotesScreen(Screen parent) {
        super(Component.literal("Pinned Notes"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int px = (this.width - PANEL_WIDTH) / 2;
        int py = (this.height - PANEL_HEIGHT) / 2;

        String prev = this.noteBox != null ? this.noteBox.getValue() : "";
        int boxX = px + 16;
        int boxY = py + 50;
        int boxW = PANEL_WIDTH - 118;

        this.noteBox = new EditBox(this.font, boxX + 6, boxY + 6, boxW - 12, 12, Component.literal(""));
        this.noteBox.setMaxLength(96);
        this.noteBox.setBordered(false);
        this.noteBox.setTextColor(0xFFF8FAFC);
        this.noteBox.setHint(Component.literal("Add a note, coordinate, or build task..."));
        this.noteBox.setValue(prev);
        addWidget(this.noteBox);
        this.noteBox.setFocused(true);
        this.setFocused(this.noteBox);
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

        GuiRenderUtils.drawDropShadow(extractor, px, py, PANEL_WIDTH, PANEL_HEIGHT, 8, 8, 140);
        GuiRenderUtils.fillRoundedRect(extractor, px, py, PANEL_WIDTH, PANEL_HEIGHT, 8, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, px, py, PANEL_WIDTH, PANEL_HEIGHT, 8, border);

        // Header
        GuiRenderUtils.fillRoundedRect(extractor, px, py, PANEL_WIDTH, HEADER_HEIGHT, 8, surface);
        extractor.fill(px, py + HEADER_HEIGHT - 1, px + PANEL_WIDTH, py + HEADER_HEIGHT, border);

        GuiRenderUtils.drawText(extractor, this.font, "📌 Pinned Notes & Checklist", px + 14, py + 10, accent, true);
        GuiRenderUtils.drawText(extractor, this.font, "Click any note's checkbox to mark it complete on your HUD", px + 14, py + 23, 0xFF94A3B8);

        // Close Button
        int closeX = px + PANEL_WIDTH - 28;
        int closeY = py + 11;
        boolean closeHov = isHovered(mouseX, mouseY, closeX, closeY, 18, 18);
        GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, 18, 18, 4, closeHov ? 0x44F87171 : bgDark);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + 9, closeY + 5, closeHov ? 0xFFF87171 : 0xFF94A3B8);

        List<NoteListSetting.NoteEntry> notes = PinnedNotesState.getNotes();
        boolean hasDone = notes.stream().anyMatch(NoteListSetting.NoteEntry::isDone);

        int rightBtnCursor = closeX;
        if (!notes.isEmpty()) {
            int clearAllW = 56;
            int clearAllX = rightBtnCursor - clearAllW - 6;
            boolean caHov = isHovered(mouseX, mouseY, clearAllX, closeY, clearAllW, 18);
            GuiRenderUtils.fillRoundedRect(extractor, clearAllX, closeY, clearAllW, 18, 4, caHov ? 0x44EF4444 : bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, clearAllX, closeY, clearAllW, 18, 4, caHov ? 0xFFEF4444 : border);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Clear All", clearAllX + clearAllW / 2, closeY + 5,
                    caHov ? 0xFFF87171 : 0xFF94A3B8);
            rightBtnCursor = clearAllX;
        }

        if (hasDone) {
            int clearDoneW = 66;
            int clearDoneX = rightBtnCursor - clearDoneW - 6;
            boolean cdHov = isHovered(mouseX, mouseY, clearDoneX, closeY, clearDoneW, 18);
            GuiRenderUtils.fillRoundedRect(extractor, clearDoneX, closeY, clearDoneW, 18, 4, cdHov ? surfaceHover : bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, clearDoneX, closeY, clearDoneW, 18, 4, cdHov ? accent : border);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Clear Done", clearDoneX + clearDoneW / 2, closeY + 5,
                    cdHov ? accent : 0xFF94A3B8);
        }

        // Input Row
        int boxX = px + 16;
        int boxY = py + 50;
        int boxW = PANEL_WIDTH - 118;
        int boxH = 20;

        GuiRenderUtils.fillRoundedRect(extractor, boxX, boxY, boxW, boxH, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, boxX, boxY, boxW, boxH, 5,
                this.noteBox != null && this.noteBox.isFocused() ? accent : border);
        if (this.noteBox != null) {
            this.noteBox.setPosition(boxX + 6, boxY + 6);
            this.noteBox.setWidth(boxW - 12);
            this.noteBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        int addBtnX = boxX + boxW + 8;
        int addBtnW = 78;
        boolean addHov = isHovered(mouseX, mouseY, addBtnX, boxY, addBtnW, boxH);
        GuiRenderUtils.fillRoundedRect(extractor, addBtnX, boxY, addBtnW, boxH, 5, addHov ? accentDark : accent);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "+ Add Note", addBtnX + addBtnW / 2, boxY + 6, 0xFFFFFFFF);

        // Subheader
        int subY = boxY + 26;
        long doneCount = notes.stream().filter(NoteListSetting.NoteEntry::isDone).count();
        String countLabel = "Pinned Notes (" + notes.size() + " total • " + doneCount + " done)";
        GuiRenderUtils.drawText(extractor, this.font, countLabel, px + 16, subY, 0xFFE2E8F0);
        if (!statusMessage.isEmpty()) {
            int sw = this.font.width(statusMessage);
            GuiRenderUtils.drawText(extractor, this.font, statusMessage, px + PANEL_WIDTH - 16 - sw, subY, statusColor);
        }

        // Notes List
        int listTop = subY + 14;
        int listBottom = py + PANEL_HEIGHT - 12;
        int maxScroll = Math.max(0, notes.size() * (ROW_HEIGHT + 4) - (listBottom - listTop));
        this.listScroll = Math.clamp(this.listScroll, 0.0, maxScroll);

        extractor.enableScissor(px + 12, listTop, px + PANEL_WIDTH - 12, listBottom);
        if (notes.isEmpty()) {
            GuiRenderUtils.drawCenteredText(extractor, this.font,
                    "No notes pinned yet — type a goal or coordinate above and press Enter",
                    px + PANEL_WIDTH / 2, listTop + (listBottom - listTop) / 2 - 4, 0xFF64748B);
        } else {
            int ry = (int) Math.round(listTop - listScroll);
            int rx = px + 16;
            int rw = PANEL_WIDTH - 32;
            for (NoteListSetting.NoteEntry note : notes) {
                if (ry + ROW_HEIGHT >= listTop && ry <= listBottom) {
                    boolean rowHov = isHovered(mouseX, mouseY, rx, ry, rw, ROW_HEIGHT);
                    GuiRenderUtils.fillRoundedRect(extractor, rx, ry, rw, ROW_HEIGHT, 5, rowHov ? surfaceHover : surface);
                    GuiRenderUtils.drawRoundedOutline(extractor, rx, ry, rw, ROW_HEIGHT, 5, border);

                    // Checkbox
                    int cbX = rx + 7;
                    int cbY = ry + 5;
                    boolean done = note.isDone();
                    GuiRenderUtils.fillRoundedRect(extractor, cbX, cbY, 14, 14, 3, done ? accent : bgDark);
                    GuiRenderUtils.drawRoundedOutline(extractor, cbX, cbY, 14, 14, 3, done ? accent : border);
                    if (done) {
                        GuiRenderUtils.drawCenteredText(extractor, this.font, "✓", cbX + 7, cbY + 3, 0xFFFFFFFF);
                    }

                    // Note Text
                    String clipped = GuiRenderUtils.truncate(this.font, note.getText(), rw - 58);
                    if (done) {
                        extractor.text(this.font,
                                Component.literal(clipped).withStyle(ChatFormatting.STRIKETHROUGH),
                                rx + 28, ry + 8, 0xFF64748B, false);
                    } else {
                        GuiRenderUtils.drawText(extractor, this.font, clipped, rx + 28, ry + 8, 0xFFF8FAFC);
                    }

                    // Delete Button
                    int delX = rx + rw - 24;
                    int delY = ry + 4;
                    boolean delHov = isHovered(mouseX, mouseY, delX, delY, 16, 16);
                    GuiRenderUtils.fillRoundedRect(extractor, delX, delY, 16, 16, 4, delHov ? 0x44EF4444 : bgDark);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", delX + 8, delY + 4,
                            delHov ? 0xFFF87171 : 0xFF94A3B8);
                }
                ry += ROW_HEIGHT + 4;
            }
        }
        extractor.disableScissor();
    }

    private void tryAddCurrentNote() {
        if (this.noteBox == null) return;
        String input = this.noteBox.getValue().trim();
        if (input.isEmpty()) {
            this.statusMessage = "Enter a note first";
            this.statusColor = 0xFFF87171;
            return;
        }
        if (PinnedNotesState.addNote(input)) {
            this.noteBox.setValue("");
            this.statusMessage = "Pinned note added";
            this.statusColor = 0xFF34D399;
            saveClientConfig();
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
            onClose();
            return true;
        }

        List<NoteListSetting.NoteEntry> notes = PinnedNotesState.getNotes();
        boolean hasDone = notes.stream().anyMatch(NoteListSetting.NoteEntry::isDone);
        int rightBtnCursor = closeX;

        if (!notes.isEmpty()) {
            int clearAllW = 56;
            int clearAllX = rightBtnCursor - clearAllW - 6;
            if (isHovered(mx, my, clearAllX, closeY, clearAllW, 18)) {
                PinnedNotesState.clearAll();
                this.statusMessage = "Cleared all notes";
                this.statusColor = 0xFF94A3B8;
                saveClientConfig();
                return true;
            }
            rightBtnCursor = clearAllX;
        }

        if (hasDone) {
            int clearDoneW = 66;
            int clearDoneX = rightBtnCursor - clearDoneW - 6;
            if (isHovered(mx, my, clearDoneX, closeY, clearDoneW, 18)) {
                PinnedNotesState.clearCompleted();
                this.statusMessage = "Cleared completed notes";
                this.statusColor = 0xFF34D399;
                saveClientConfig();
                return true;
            }
        }

        int boxX = px + 16;
        int boxY = py + 50;
        int boxW = PANEL_WIDTH - 118;
        int boxH = 20;
        if (this.noteBox != null && isHovered(mx, my, boxX, boxY, boxW, boxH)) {
            this.noteBox.setFocused(true);
            this.setFocused(this.noteBox);
            this.noteBox.mouseClicked(event, doubleClick);
            return true;
        }

        int addBtnX = boxX + boxW + 8;
        int addBtnW = 78;
        if (isHovered(mx, my, addBtnX, boxY, addBtnW, boxH)) {
            tryAddCurrentNote();
            return true;
        }

        int listTop = boxY + 40;
        int listBottom = py + PANEL_HEIGHT - 12;
        if (my >= listTop && my <= listBottom) {
            int ry = (int) Math.round(listTop - listScroll);
            int rx = px + 16;
            int rw = PANEL_WIDTH - 32;
            for (int i = 0; i < notes.size(); i++) {
                int delX = rx + rw - 24;
                int delY = ry + 4;
                if (isHovered(mx, my, delX, delY, 16, 16)) {
                    PinnedNotesState.removeNoteAt(i);
                    this.statusMessage = "Removed note";
                    this.statusColor = 0xFF94A3B8;
                    saveClientConfig();
                    return true;
                }
                if (isHovered(mx, my, rx, ry, rw - 28, ROW_HEIGHT)) {
                    PinnedNotesState.toggleNoteAt(i);
                    saveClientConfig();
                    return true;
                }
                ry += ROW_HEIGHT + 4;
            }
        }

        if (this.noteBox != null) {
            this.noteBox.setFocused(false);
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.listScroll -= verticalAmount * 20.0;
        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.noteBox != null && this.noteBox.isFocused()) {
                tryAddCurrentNote();
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
