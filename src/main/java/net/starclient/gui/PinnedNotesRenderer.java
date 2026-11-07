package net.starclient.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.module.PinnedNotesState;
import net.starclient.setting.NoteListSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the draggable "Pinned Notes" HUD checklist card.
 */
public final class PinnedNotesRenderer {

    private PinnedNotesRenderer() {
    }

    private static List<NoteListSetting.NoteEntry> collectVisibleNotes(boolean editorPreview) {
        List<NoteListSetting.NoteEntry> all = PinnedNotesState.getNotes();
        boolean showCompleted = PinnedNotesState.isShowCompleted();
        List<NoteListSetting.NoteEntry> visible = new ArrayList<>(all.size());
        for (NoteListSetting.NoteEntry n : all) {
            if (showCompleted || !n.isDone()) {
                visible.add(n);
            }
        }
        if (visible.isEmpty() && editorPreview) {
            visible.add(new NoteListSetting.NoteEntry("Portal at -240, 68, 512", false));
            visible.add(new NoteListSetting.NoteEntry("Gather 12 Stacks Quartz", true));
        }
        return visible;
    }

    public static int getWidth(Font font, Minecraft client, Module mod) {
        if (font == null) return 148;
        boolean editorMode = client == null || client.player == null
                || client.gui.screen() instanceof ClickGuiScreen
                || client.gui.screen() instanceof HudEditorScreen;
        List<NoteListSetting.NoteEntry> visible = collectVisibleNotes(editorMode);
        String header = "📌 " + PinnedNotesState.getTitle();
        int maxW = font.width(header);
        boolean checkboxes = PinnedNotesState.isShowCheckboxes();
        int prefixExtra = checkboxes ? 14 : 8;

        if (visible.isEmpty()) {
            maxW = Math.max(maxW, font.width("No notes pinned"));
        } else {
            for (NoteListSetting.NoteEntry e : visible) {
                maxW = Math.max(maxW, prefixExtra + font.width(e.getText()));
            }
        }
        return Math.clamp(maxW + 16, 110, 260);
    }

    public static int getHeight(Minecraft client, Module mod) {
        boolean editorMode = client == null || client.player == null
                || client.gui.screen() instanceof ClickGuiScreen
                || client.gui.screen() instanceof HudEditorScreen;
        List<NoteListSetting.NoteEntry> visible = collectVisibleNotes(editorMode);
        int rows = Math.max(1, visible.size());
        return 20 + rows * 12 + 4;
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod,
                              HudLayout layout, int screenWidth, int screenHeight) {
        if (mod == null || client == null || client.font == null) return;
        boolean editorMode = client.gui.screen() instanceof ClickGuiScreen
                || client.gui.screen() instanceof HudEditorScreen;

        List<NoteListSetting.NoteEntry> visible = collectVisibleNotes(editorMode);
        if (visible.isEmpty() && !editorMode && PinnedNotesState.isHideWhenEmpty()) {
            return;
        }

        renderCard(extractor, client, mod, layout, screenWidth, screenHeight, visible);
    }

    public static void renderPreview(GuiGraphicsExtractor extractor, Module mod) {
        Minecraft client = Minecraft.getInstance();
        if (mod == null || client.font == null) return;
        List<NoteListSetting.NoteEntry> visible = collectVisibleNotes(true);
        HudLayout layout = StarClient.getInstance().getHudLayout();
        renderCard(extractor, client, mod, layout, extractor.guiWidth(), extractor.guiHeight(), visible);
    }

    private static void renderCard(GuiGraphicsExtractor extractor, Minecraft client, Module mod,
                                   HudLayout layout, int screenWidth, int screenHeight,
                                   List<NoteListSetting.NoteEntry> visible) {
        int width = getWidth(client.font, client, mod);
        int height = 20 + Math.max(1, visible.size()) * 12 + 4;

        int x = layout.effectiveX(HudLayout.PINNED_NOTES, screenWidth, width);
        int y = layout.effectiveY(HudLayout.PINNED_NOTES, screenHeight);
        x = Math.clamp(x, 2, Math.max(2, screenWidth - width - 2));
        y = Math.clamp(y, 2, Math.max(2, screenHeight - height - 2));

        int baseTextCol = mod.getTextColor();
        boolean shadow = StarClient.getInstance().getClientSettings().isHudTextShadow();

        if (!mod.isTransparentBackground()) {
            int bg = (mod.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
            GuiRenderUtils.fillRoundedRect(extractor, x, y, width, height, 5, bg);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, width, height, 5, 0x28FFFFFF);
            extractor.fill(x + 6, y + 16, x + width - 6, y + 17, 0x24FFFFFF);
        }

        String header = "📌 " + PinnedNotesState.getTitle();
        extractor.text(client.font, header, x + 7, y + 5, baseTextCol, shadow);

        int rowY = y + 20;
        if (visible.isEmpty()) {
            extractor.text(client.font, "No notes pinned", x + 7, rowY, 0xFF94A3B8, shadow);
            return;
        }

        boolean checkboxes = PinnedNotesState.isShowCheckboxes();
        int maxTextWidth = width - (checkboxes ? 26 : 16);

        for (NoteListSetting.NoteEntry note : visible) {
            boolean done = note.isDone();
            int textX = x + 7;
            if (checkboxes) {
                int boxCol = done ? 0xFF34D399 : 0x5594A3B8;
                GuiRenderUtils.fillRoundedRect(extractor, x + 7, rowY, 8, 8, 2, done ? 0x4434D399 : 0x440F172A);
                GuiRenderUtils.drawRoundedOutline(extractor, x + 7, rowY, 8, 8, 2, boxCol);
                if (done) {
                    extractor.fill(x + 9, rowY + 2, x + 13, rowY + 6, 0xFF34D399);
                }
                textX = x + 19;
            } else {
                extractor.text(client.font, "•", x + 7, rowY, baseTextCol, shadow);
                textX = x + 14;
            }

            String clipped = GuiRenderUtils.truncate(client.font, note.getText(), maxTextWidth);
            if (done) {
                extractor.text(client.font,
                        Component.literal(clipped).withStyle(ChatFormatting.STRIKETHROUGH),
                        textX, rowY, 0xFF64748B, shadow);
            } else {
                extractor.text(client.font, clipped, textX, rowY, baseTextCol, shadow);
            }
            rowY += 12;
        }
    }
}
