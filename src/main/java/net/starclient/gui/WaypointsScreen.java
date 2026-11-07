package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.WaypointsState;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

public class WaypointsScreen extends Screen {
    private static final int PANEL_WIDTH = 440;
    private static final int PANEL_HEIGHT = 320;
    private static final int ROW_HEIGHT = 26;

    private static final String[] DIMENSIONS = {
            WaypointsState.DIM_OVERWORLD,
            WaypointsState.DIM_NETHER,
            WaypointsState.DIM_END
    };

    private final Screen parent;
    private boolean showAllWorlds = false;
    private boolean bindingQuickKey = false;
    private double listScroll = 0.0;

    // Editor state
    private boolean editorOpen = false;
    private WaypointsState.Waypoint editingWaypoint = null;
    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private String selectedDimension = WaypointsState.DIM_OVERWORLD;
    private int selectedColor = WaypointsState.PALETTE[0];
    private String initialName = "";
    private String initialX = "0";
    private String initialY = "64";
    private String initialZ = "0";

    public WaypointsScreen(Screen parent) {
        super(Component.literal("Waypoints Manager"));
        this.parent = parent;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            BlockPos p = mc.player.blockPosition();
            this.initialX = String.valueOf(p.getX());
            this.initialY = String.valueOf(p.getY());
            this.initialZ = String.valueOf(p.getZ());
            this.selectedDimension = WaypointsState.getCurrentDimension(mc);
        }
    }

    public static WaypointsScreen openCreateAtPlayer(Screen parent) {
        WaypointsScreen screen = new WaypointsScreen(parent);
        screen.editorOpen = true;
        screen.editingWaypoint = null;
        Minecraft mc = Minecraft.getInstance();
        int nextNum = WaypointsState.getWaypointsForCurrentContext(mc).size() + 1;
        screen.initialName = "Waypoint #" + nextNum;
        screen.selectedColor = WaypointsState.PALETTE[(nextNum - 1) % WaypointsState.PALETTE.length];
        return screen;
    }

    public static WaypointsScreen openEdit(Screen parent, WaypointsState.Waypoint waypoint) {
        WaypointsScreen screen = new WaypointsScreen(parent);
        if (waypoint != null) {
            screen.editorOpen = true;
            screen.editingWaypoint = waypoint;
            screen.initialName = waypoint.getName();
            screen.initialX = String.valueOf(waypoint.getX());
            screen.initialY = String.valueOf(waypoint.getY());
            screen.initialZ = String.valueOf(waypoint.getZ());
            screen.selectedDimension = waypoint.getDimension();
            screen.selectedColor = waypoint.getColor();
        }
        return screen;
    }

    @Override
    protected void init() {
        super.init();
        int px = (this.width - PANEL_WIDTH) / 2;
        int py = (this.height - PANEL_HEIGHT) / 2;

        String prevName = this.nameBox != null ? this.nameBox.getValue() : this.initialName;
        String prevX = this.xBox != null ? this.xBox.getValue() : this.initialX;
        String prevY = this.yBox != null ? this.yBox.getValue() : this.initialY;
        String prevZ = this.zBox != null ? this.zBox.getValue() : this.initialZ;

        this.nameBox = createBox(px + 18, py + 58, 150, prevName, "Waypoint name..");
        this.xBox = createBox(px + 178, py + 58, 52, prevX, "X");
        this.yBox = createBox(px + 236, py + 58, 46, prevY, "Y");
        this.zBox = createBox(px + 288, py + 58, 52, prevZ, "Z");

        addWidget(this.nameBox);
        addWidget(this.xBox);
        addWidget(this.yBox);
        addWidget(this.zBox);

        if (this.editorOpen) {
            this.nameBox.setFocused(true);
            this.setFocused(this.nameBox);
        }
    }

    private EditBox createBox(int x, int y, int w, String val, String hint) {
        EditBox box = new EditBox(this.font, x + 4, y + 4, w - 8, 16 - 4, Component.literal(""));
        box.setMaxLength(40);
        box.setBordered(false);
        box.setTextColor(0xFFF8FAFC);
        box.setHint(Component.literal(hint));
        box.setValue(val);
        return box;
    }

    private List<WaypointsState.Waypoint> getDisplayedWaypoints() {
        Minecraft mc = Minecraft.getInstance();
        if (this.showAllWorlds || mc.level == null) {
            return WaypointsState.getAllWaypoints();
        }
        return WaypointsState.getWaypointsForCurrentContext(mc);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs.getMenuAccentColor();
        int accentDark = cs.getMenuAccentDarkColor();
        int bg = 0xF6000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
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
        GuiRenderUtils.fillRoundedRect(extractor, px, py, PANEL_WIDTH, 36, 8, surface);
        extractor.fill(px, py + 35, px + PANEL_WIDTH, py + 36, border);
        GuiRenderUtils.drawText(extractor, this.font, "📍 Waypoints", px + 14, py + 13, accent, true);

        // Close Button
        int closeX = px + PANEL_WIDTH - 26;
        int closeY = py + 9;
        boolean closeHover = isHovered(mouseX, mouseY, closeX, closeY, 18, 18);
        GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, 18, 18, 4, closeHover ? 0x44F87171 : bgDark);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + 9, closeY + 5, closeHover ? 0xFFF87171 : 0xFF94A3B8);

        // New Waypoint Button
        int newBtnW = 86;
        int newBtnX = closeX - newBtnW - 8;
        int newBtnY = py + 9;
        boolean newHover = isHovered(mouseX, mouseY, newBtnX, newBtnY, newBtnW, 18);
        GuiRenderUtils.fillRoundedRect(extractor, newBtnX, newBtnY, newBtnW, 18, 5, newHover ? accentDark : accent);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "+ Add Waypoint", newBtnX + newBtnW / 2, newBtnY + 5, 0xFFFFFFFF);

        // Filter World Toggle Button
        String scopeLabel = showAllWorlds ? "🌐 All Worlds" : "🗺 Current World";
        int scopeW = this.font.width(scopeLabel) + 14;
        int scopeX = newBtnX - scopeW - 6;
        boolean scopeHover = isHovered(mouseX, mouseY, scopeX, newBtnY, scopeW, 18);
        GuiRenderUtils.fillRoundedRect(extractor, scopeX, newBtnY, scopeW, 18, 5, scopeHover ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, scopeX, newBtnY, scopeW, 18, 5, border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, scopeLabel, scopeX + scopeW / 2, newBtnY + 5, 0xFFE2E8F0);

        // Quick-Add Keybind Button
        String keyStr = bindingQuickKey
                ? "Press key.."
                : "⌨ Add: " + (WaypointsState.getQuickAddKey() <= 0 ? "None" : keyName(WaypointsState.getQuickAddKey()));
        int keyBtnW = this.font.width(keyStr) + 12;
        int keyBtnX = scopeX - keyBtnW - 6;
        boolean keyHover = isHovered(mouseX, mouseY, keyBtnX, newBtnY, keyBtnW, 18);
        GuiRenderUtils.fillRoundedRect(extractor, keyBtnX, newBtnY, keyBtnW, 18, 5, bindingQuickKey ? accentDark : (keyHover ? surfaceHover : bgDark));
        GuiRenderUtils.drawRoundedOutline(extractor, keyBtnX, newBtnY, keyBtnW, 18, 5, bindingQuickKey ? 0xFFFACC15 : border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, keyStr, keyBtnX + keyBtnW / 2, newBtnY + 5, bindingQuickKey ? 0xFFFACC15 : accent);

        int listTop = py + 44;

        // Editor Panel if open
        if (this.editorOpen) {
            int edX = px + 10;
            int edY = py + 42;
            int edW = PANEL_WIDTH - 20;
            int edH = 74;
            GuiRenderUtils.fillRoundedRect(extractor, edX, edY, edW, edH, 6, surface);
            GuiRenderUtils.drawRoundedOutline(extractor, edX, edY, edW, edH, 6, accent);

            String edTitle = editingWaypoint == null ? "Create Waypoint" : "Edit Waypoint: " + editingWaypoint.getName();
            GuiRenderUtils.drawText(extractor, this.font, edTitle, edX + 8, edY + 6, accent, true);

            // Input Boxes row
            int boxY = edY + 19;
            drawInputField(extractor, this.nameBox, edX + 8, boxY, 144, accent, bgDark, border, delta, mouseX, mouseY);
            drawInputField(extractor, this.xBox, edX + 156, boxY, 54, accent, bgDark, border, delta, mouseX, mouseY);
            drawInputField(extractor, this.yBox, edX + 214, boxY, 48, accent, bgDark, border, delta, mouseX, mouseY);
            drawInputField(extractor, this.zBox, edX + 266, boxY, 54, accent, bgDark, border, delta, mouseX, mouseY);

            // "My Pos" button
            int hereX = edX + 324;
            int hereW = edW - 332;
            boolean hereHover = isHovered(mouseX, mouseY, hereX, boxY, hereW, 18);
            GuiRenderUtils.fillRoundedRect(extractor, hereX, boxY, hereW, 18, 4, hereHover ? surfaceHover : bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, hereX, boxY, hereW, 18, 4, hereHover ? accent : border);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "📍 My Pos", hereX + hereW / 2, boxY + 5, 0xFFE2E8F0);

            // Bottom editor row: Dimension selector + Color swatches + Save / Cancel
            int botEdY = edY + 46;
            String dimLabel = "Dim: " + WaypointsState.formatDimensionShort(selectedDimension);
            int dimW = 78;
            boolean dimHover = isHovered(mouseX, mouseY, edX + 8, botEdY, dimW, 18);
            GuiRenderUtils.fillRoundedRect(extractor, edX + 8, botEdY, dimW, 18, 4, dimHover ? surfaceHover : bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, edX + 8, botEdY, dimW, 18, 4, border);
            GuiRenderUtils.drawCenteredText(extractor, this.font, dimLabel, edX + 8 + dimW / 2, botEdY + 5, accent);

            // Swatches
            int swX = edX + 94;
            for (int col : WaypointsState.PALETTE) {
                boolean sel = (col | 0xFF000000) == (selectedColor | 0xFF000000);
                GuiRenderUtils.fillRoundedRect(extractor, swX, botEdY + 2, 14, 14, 3, col);
                if (sel) {
                    GuiRenderUtils.drawRoundedOutline(extractor, swX - 1, botEdY + 1, 16, 16, 4, 0xFFFFFFFF);
                }
                swX += 17;
            }

            // Cancel & Save buttons
            int saveW = 54;
            int saveX = edX + edW - saveW - 8;
            boolean saveHover = isHovered(mouseX, mouseY, saveX, botEdY, saveW, 18);
            GuiRenderUtils.fillRoundedRect(extractor, saveX, botEdY, saveW, 18, 4, saveHover ? accentDark : accent);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Save", saveX + saveW / 2, botEdY + 5, 0xFFFFFFFF);

            int cancelW = 52;
            int cancelX = saveX - cancelW - 6;
            boolean cancelHover = isHovered(mouseX, mouseY, cancelX, botEdY, cancelW, 18);
            GuiRenderUtils.fillRoundedRect(extractor, cancelX, botEdY, cancelW, 18, 4, cancelHover ? surfaceHover : bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, cancelX, botEdY, cancelW, 18, 4, border);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Cancel", cancelX + cancelW / 2, botEdY + 5, 0xFF94A3B8);

            listTop = edY + edH + 8;
        }

        // Waypoints List Area
        int listBottom = py + PANEL_HEIGHT - 10;
        List<WaypointsState.Waypoint> items = getDisplayedWaypoints();
        int maxScroll = Math.max(0, items.size() * (ROW_HEIGHT + 4) - (listBottom - listTop));
        this.listScroll = Math.clamp(this.listScroll, 0.0, maxScroll);

        extractor.enableScissor(px + 8, listTop, px + PANEL_WIDTH - 8, listBottom);
        if (items.isEmpty()) {
            GuiRenderUtils.drawCenteredText(extractor, this.font,
                    "No waypoints saved for this world yet — click '+ Add Waypoint' above!",
                    px + PANEL_WIDTH / 2, listTop + (listBottom - listTop) / 2 - 4, 0xFF64748B);
        } else {
            Minecraft mc = Minecraft.getInstance();
            int ry = (int) Math.round(listTop - listScroll);
            int rx = px + 10;
            int rw = PANEL_WIDTH - 20;
            for (WaypointsState.Waypoint wp : items) {
                if (ry + ROW_HEIGHT >= listTop && ry <= listBottom) {
                    boolean hov = isHovered(mouseX, mouseY, rx, ry, rw, ROW_HEIGHT);
                    GuiRenderUtils.fillRoundedRect(extractor, rx, ry, rw, ROW_HEIGHT, 5, hov ? surfaceHover : surface);
                    GuiRenderUtils.drawRoundedOutline(extractor, rx, ry, rw, ROW_HEIGHT, 5,
                            editingWaypoint == wp ? accent : border);

                    // Color dot
                    GuiRenderUtils.fillRoundedRect(extractor, rx + 8, ry + 7, 12, 12, 3, wp.getColor());

                    // Name & Coordinates
                    int nameCol = wp.isEnabled() ? 0xFFF8FAFC : 0xFF64748B;
                    String nameText = GuiRenderUtils.truncate(this.font, wp.getName(), 125);
                    GuiRenderUtils.drawText(extractor, this.font, nameText, rx + 26, ry + 9, nameCol, wp.isEnabled());

                    String coordStr = String.format("%d, %d, %d (%s)",
                            wp.getX(), wp.getY(), wp.getZ(), WaypointsState.formatDimensionShort(wp.getDimension()));
                    if (mc.player != null && mc.level != null && WaypointsState.getCurrentDimension(mc).equals(wp.getDimension())) {
                        int dist = (int) Math.round(Math.sqrt(mc.player.distanceToSqr(wp.getX() + 0.5, wp.getY() + 0.5, wp.getZ() + 0.5)));
                        coordStr += " • " + dist + "m";
                    }
                    GuiRenderUtils.drawText(extractor, this.font, coordStr, rx + 158, ry + 9, 0xFF94A3B8);

                    // Delete Button (✕)
                    int delX = rx + rw - 24;
                    int btnY = ry + 5;
                    boolean delHov = isHovered(mouseX, mouseY, delX, btnY, 16, 16);
                    GuiRenderUtils.fillRoundedRect(extractor, delX, btnY, 16, 16, 4, delHov ? 0x44F87171 : bgDark);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", delX + 8, btnY + 4, delHov ? 0xFFF87171 : 0xFF94A3B8);

                    // Edit Button (✎)
                    int editX = delX - 22;
                    boolean editHov = isHovered(mouseX, mouseY, editX, btnY, 18, 16);
                    GuiRenderUtils.fillRoundedRect(extractor, editX, btnY, 18, 16, 4, editHov ? accentDark : bgDark);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, "✎", editX + 9, btnY + 4, editHov ? 0xFFFFFFFF : accent);

                    // Visibility Toggle (ON/OFF)
                    int visW = 34;
                    int visX = editX - visW - 6;
                    boolean visHov = isHovered(mouseX, mouseY, visX, btnY, visW, 16);
                    GuiRenderUtils.fillRoundedRect(extractor, visX, btnY, visW, 16, 4,
                            wp.isEnabled() ? GuiRenderUtils.withAlpha(0xFF34D399, 45) : (visHov ? surfaceHover : bgDark));
                    GuiRenderUtils.drawRoundedOutline(extractor, visX, btnY, visW, 16, 4,
                            wp.isEnabled() ? 0xFF34D399 : border);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, wp.isEnabled() ? "ON" : "OFF",
                            visX + visW / 2, btnY + 4, wp.isEnabled() ? 0xFF34D399 : 0xFF64748B);
                }
                ry += ROW_HEIGHT + 4;
            }
        }
        extractor.disableScissor();
    }

    private void drawInputField(GuiGraphicsExtractor extractor, EditBox box, int x, int y, int w,
                                int accent, int bgDark, int border, float delta, int mouseX, int mouseY) {
        if (box == null) return;
        box.setPosition(x + 4, y + 5);
        box.setWidth(w - 8);
        GuiRenderUtils.fillRoundedRect(extractor, x, y, w, 18, 4, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, x, y, w, 18, 4, box.isFocused() ? accent : border);
        box.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int px = (this.width - PANEL_WIDTH) / 2;
        int py = (this.height - PANEL_HEIGHT) / 2;

        // Header buttons
        int closeX = px + PANEL_WIDTH - 26;
        int closeY = py + 9;
        if (isHovered(mx, my, closeX, closeY, 18, 18)) {
            onClose();
            return true;
        }

        int newBtnW = 86;
        int newBtnX = closeX - newBtnW - 8;
        int newBtnY = py + 9;
        if (isHovered(mx, my, newBtnX, newBtnY, newBtnW, 18)) {
            openBlankCreator();
            return true;
        }

        String scopeLabel = showAllWorlds ? "🌐 All Worlds" : "🗺 Current World";
        int scopeW = this.font.width(scopeLabel) + 14;
        int scopeX = newBtnX - scopeW - 6;
        if (isHovered(mx, my, scopeX, newBtnY, scopeW, 18)) {
            showAllWorlds = !showAllWorlds;
            listScroll = 0;
            return true;
        }

        String keyStr = bindingQuickKey
                ? "Press key.."
                : "⌨ Add: " + (WaypointsState.getQuickAddKey() <= 0 ? "None" : keyName(WaypointsState.getQuickAddKey()));
        int keyBtnW = this.font.width(keyStr) + 12;
        int keyBtnX = scopeX - keyBtnW - 6;
        if (isHovered(mx, my, keyBtnX, newBtnY, keyBtnW, 18)) {
            bindingQuickKey = !bindingQuickKey;
            return true;
        }

        int listTop = py + 44;

        if (this.editorOpen) {
            int edX = px + 10;
            int edY = py + 42;
            int edW = PANEL_WIDTH - 20;
            int edH = 74;
            int boxY = edY + 19;

            if (focusIfClicked(this.nameBox, mx, my, edX + 8, boxY, 144, event, doubleClick)) return true;
            if (focusIfClicked(this.xBox, mx, my, edX + 156, boxY, 54, event, doubleClick)) return true;
            if (focusIfClicked(this.yBox, mx, my, edX + 214, boxY, 48, event, doubleClick)) return true;
            if (focusIfClicked(this.zBox, mx, my, edX + 266, boxY, 54, event, doubleClick)) return true;

            int hereX = edX + 324;
            int hereW = edW - 332;
            if (isHovered(mx, my, hereX, boxY, hereW, 18)) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.level != null) {
                    BlockPos pos = mc.player.blockPosition();
                    this.xBox.setValue(String.valueOf(pos.getX()));
                    this.yBox.setValue(String.valueOf(pos.getY()));
                    this.zBox.setValue(String.valueOf(pos.getZ()));
                    this.selectedDimension = WaypointsState.getCurrentDimension(mc);
                }
                return true;
            }

            int botEdY = edY + 46;
            if (isHovered(mx, my, edX + 8, botEdY, 78, 18)) {
                int idx = 0;
                for (int i = 0; i < DIMENSIONS.length; i++) {
                    if (DIMENSIONS[i].equals(selectedDimension)) {
                        idx = (i + 1) % DIMENSIONS.length;
                        break;
                    }
                }
                selectedDimension = DIMENSIONS[idx];
                return true;
            }

            int swX = edX + 94;
            for (int col : WaypointsState.PALETTE) {
                if (isHovered(mx, my, swX, botEdY + 2, 14, 14)) {
                    selectedColor = col;
                    return true;
                }
                swX += 17;
            }

            int saveW = 54;
            int saveX = edX + edW - saveW - 8;
            if (isHovered(mx, my, saveX, botEdY, saveW, 18)) {
                saveEditorWaypoint();
                return true;
            }

            int cancelW = 52;
            int cancelX = saveX - cancelW - 6;
            if (isHovered(mx, my, cancelX, botEdY, cancelW, 18)) {
                this.editorOpen = false;
                this.editingWaypoint = null;
                clearBoxFocus();
                return true;
            }

            listTop = edY + edH + 8;
        }

        // List clicks
        int listBottom = py + PANEL_HEIGHT - 10;
        if (my >= listTop && my <= listBottom) {
            List<WaypointsState.Waypoint> items = getDisplayedWaypoints();
            int ry = (int) Math.round(listTop - listScroll);
            int rx = px + 10;
            int rw = PANEL_WIDTH - 20;
            for (WaypointsState.Waypoint wp : items) {
                if (isHovered(mx, my, rx, ry, rw, ROW_HEIGHT)) {
                    int delX = rx + rw - 24;
                    int btnY = ry + 5;
                    if (isHovered(mx, my, delX, btnY, 16, 16)) {
                        if (editingWaypoint == wp) {
                            editingWaypoint = null;
                            editorOpen = false;
                        }
                        WaypointsState.removeWaypoint(wp.getId());
                        return true;
                    }
                    int editX = delX - 22;
                    if (isHovered(mx, my, editX, btnY, 18, 16)) {
                        loadIntoEditor(wp);
                        return true;
                    }
                    int visW = 34;
                    int visX = editX - visW - 6;
                    if (isHovered(mx, my, visX, btnY, visW, 16)) {
                        wp.toggleEnabled();
                        WaypointsState.save();
                        return true;
                    }
                    loadIntoEditor(wp);
                    return true;
                }
                ry += ROW_HEIGHT + 4;
            }
        }

        clearBoxFocus();
        return super.mouseClicked(event, doubleClick);
    }

    private boolean focusIfClicked(EditBox box, double mx, double my, int x, int y, int w,
                                   MouseButtonEvent event, boolean doubleClick) {
        if (box != null && isHovered(mx, my, x, y, w, 18)) {
            clearBoxFocus();
            box.setFocused(true);
            this.setFocused(box);
            box.mouseClicked(event, doubleClick);
            return true;
        }
        return false;
    }

    private void clearBoxFocus() {
        if (nameBox != null) nameBox.setFocused(false);
        if (xBox != null) xBox.setFocused(false);
        if (yBox != null) yBox.setFocused(false);
        if (zBox != null) zBox.setFocused(false);
        this.setFocused(null);
    }

    private void openBlankCreator() {
        Minecraft mc = Minecraft.getInstance();
        int nextNum = WaypointsState.getWaypointsForCurrentContext(mc).size() + 1;
        this.editingWaypoint = null;
        this.editorOpen = true;
        this.nameBox.setValue("Waypoint #" + nextNum);
        if (mc.player != null && mc.level != null) {
            BlockPos pos = mc.player.blockPosition();
            this.xBox.setValue(String.valueOf(pos.getX()));
            this.yBox.setValue(String.valueOf(pos.getY()));
            this.zBox.setValue(String.valueOf(pos.getZ()));
            this.selectedDimension = WaypointsState.getCurrentDimension(mc);
        }
        this.selectedColor = WaypointsState.PALETTE[(nextNum - 1) % WaypointsState.PALETTE.length];
        clearBoxFocus();
        this.nameBox.setFocused(true);
        this.setFocused(this.nameBox);
    }

    private void loadIntoEditor(WaypointsState.Waypoint wp) {
        this.editingWaypoint = wp;
        this.editorOpen = true;
        this.nameBox.setValue(wp.getName());
        this.xBox.setValue(String.valueOf(wp.getX()));
        this.yBox.setValue(String.valueOf(wp.getY()));
        this.zBox.setValue(String.valueOf(wp.getZ()));
        this.selectedDimension = wp.getDimension();
        this.selectedColor = wp.getColor();
        clearBoxFocus();
        this.nameBox.setFocused(true);
        this.setFocused(this.nameBox);
    }

    private void saveEditorWaypoint() {
        String name = this.nameBox.getValue().trim();
        if (name.isEmpty()) name = "Waypoint";
        int x = parseIntSafe(this.xBox.getValue(), 0);
        int y = parseIntSafe(this.yBox.getValue(), 64);
        int z = parseIntSafe(this.zBox.getValue(), 0);

        if (this.editingWaypoint != null) {
            this.editingWaypoint.setName(name);
            this.editingWaypoint.setX(x);
            this.editingWaypoint.setY(y);
            this.editingWaypoint.setZ(z);
            this.editingWaypoint.setDimension(this.selectedDimension);
            this.editingWaypoint.setColor(this.selectedColor);
            WaypointsState.save();
        } else {
            Minecraft mc = Minecraft.getInstance();
            String wk = WaypointsState.getCurrentWorldKey(mc);
            if (wk == null) wk = "global";
            WaypointsState.addWaypoint(name, x, y, z, this.selectedDimension, wk, this.selectedColor);
        }
        this.editorOpen = false;
        this.editingWaypoint = null;
        clearBoxFocus();
    }

    private static int parseIntSafe(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.listScroll -= verticalAmount * 20.0;
        return true;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        int key = event.key();
        if (bindingQuickKey) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                bindingQuickKey = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                WaypointsState.setQuickAddKey(GLFW.GLFW_KEY_UNKNOWN);
                bindingQuickKey = false;
                return true;
            }
            WaypointsState.setQuickAddKey(key);
            bindingQuickKey = false;
            return true;
        }

        if (this.editorOpen && key == GLFW.GLFW_KEY_ENTER) {
            saveEditorWaypoint();
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (this.editorOpen) {
                this.editorOpen = false;
                this.editingWaypoint = null;
                clearBoxFocus();
                return true;
            }
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
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean isHovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String keyName(int key) {
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase(Locale.ROOT);
        return switch (key) {
            case GLFW.GLFW_KEY_INSERT -> "INS";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_PAGE_UP -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN";
            default -> "K" + key;
        };
    }
}
