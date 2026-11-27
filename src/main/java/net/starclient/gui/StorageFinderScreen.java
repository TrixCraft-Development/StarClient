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
import net.minecraft.world.item.ItemStack;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.StorageFinderManager;
import net.starclient.module.WaypointsState;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Glassmorphic Base Storage Item Finder overlay (WORLD -> "Storage Finder", Hotkey: X):
 * Live-searches across every indexed Chest, Double Chest, Barrel, Shulker Box (including
 * nested Shulker contents), Hopper, and Dispenser in your base by item name, custom name,
 * enchantment, or potion effect, then highlights matching containers in 3D through walls.
 */
public class StorageFinderScreen extends Screen {

    private static final int ROW_H = 26;
    private static final int RIGHT_ROW_H = 30;

    private final Screen parent;
    private EditBox searchBox;
    private String lastQuery = "";

    private List<StorageFinderManager.AggregatedItem> itemResults = List.of();
    private List<StorageFinderManager.ContainerMatch> containerMatches = List.of();
    private int selectedItemIdx = 0;

    private double leftScroll = 0.0;
    private double rightScroll = 0.0;

    // Layout geometry cached per frame
    private int panelX, panelY, panelW, panelH;
    private int leftX, leftW, rightX, rightW, listTop, listBottom;
    private int clearBtnX, clearBtnY, clearBtnW;
    private int trackAllBtnX, trackAllBtnY, trackAllBtnW;
    private int closeBtnX, closeBtnY;

    public StorageFinderScreen(Screen parent) {
        super(Component.literal("Base Storage Item Finder"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        StorageFinderManager.ensureLoaded();

        this.panelW = Math.min(540, this.width - 28);
        this.panelH = Math.min(340, this.height - 28);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        String prev = this.searchBox != null ? this.searchBox.getValue()
                : (StorageFinderManager.hasActiveHighlight() ? StorageFinderManager.getActiveLabel() : "");
        this.searchBox = new EditBox(this.font, this.panelX + 28, this.panelY + 36, this.panelW - 140, 14, Component.literal(""));
        this.searchBox.setMaxLength(64);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFF8FAFC);
        this.searchBox.setHint(Component.literal("Search items, enchants (mending), or potions..."));
        this.searchBox.setValue(prev);
        this.searchBox.setResponder(v -> {
            this.leftScroll = 0.0;
            this.rightScroll = 0.0;
            this.selectedItemIdx = 0;
            refreshResults();
        });
        this.addWidget(this.searchBox);
        this.setFocused(this.searchBox);
        this.searchBox.setFocused(true);

        refreshResults();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void refreshResults() {
        Minecraft mc = Minecraft.getInstance();
        String q = this.searchBox != null ? this.searchBox.getValue() : "";
        this.lastQuery = q;
        this.itemResults = StorageFinderManager.searchAggregatedItems(mc, q);
        if (this.itemResults.isEmpty()) {
            this.selectedItemIdx = -1;
            this.containerMatches = List.of();
        } else {
            this.selectedItemIdx = Math.clamp(this.selectedItemIdx, 0, this.itemResults.size() - 1);
            StorageFinderManager.AggregatedItem sel = this.itemResults.get(this.selectedItemIdx);
            this.containerMatches = StorageFinderManager.findMatchingContainers(
                    mc, q, sel.itemId(), sel.displayName(), null);
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            this.onClose();
            return;
        }

        // Subtle backdrop dim
        graphics.fill(0, 0, this.width, this.height, 0x88060A14);

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int bg = cs != null ? (0xF2000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xF20F172A;
        int bgDark = cs != null ? cs.getMenuBackgroundDarkColor() : 0xFF0A101E;
        int surface = cs != null ? cs.getMenuSurfaceColor() : 0xFF162032;
        int surfaceHov = cs != null ? cs.getMenuSurfaceHoverColor() : 0xFF1E293B;
        int border = cs != null ? cs.getMenuBorderColor() : 0xFF334155;

        this.panelW = Math.min(540, this.width - 28);
        this.panelH = Math.min(340, this.height - 28);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        GuiRenderUtils.drawDropShadow(graphics, panelX, panelY, panelW, panelH, 8, 8, 150);
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, panelW, panelH, 8, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, panelX, panelY, panelW, panelH, 8, border);

        // Header Bar
        int headerH = 26;
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, panelW, headerH, 8, surface);
        graphics.fill(panelX, panelY + headerH - 1, panelX + panelW, panelY + headerH, border);

        int indexedCount = StorageFinderManager.getContainersForCurrentContext(mc).size();
        String title = "📦 Storage Finder";
        GuiRenderUtils.drawText(graphics, this.font, title, panelX + 10, panelY + 9, accent, true);

        String statsPill = indexedCount + (indexedCount == 1 ? " container" : " containers")
                + " • " + this.itemResults.size() + " items";
        int statsX = panelX + 14 + this.font.width(title);
        int statsW = this.font.width(statsPill) + 10;
        GuiRenderUtils.fillRoundedRect(graphics, statsX, panelY + 6, statsW, 14, 4, bgDark);
        GuiRenderUtils.drawText(graphics, this.font, statsPill, statsX + 5, panelY + 9, 0xFF94A3B8, false);

        // Close Button [✕]
        this.closeBtnX = panelX + panelW - 22;
        this.closeBtnY = panelY + 5;
        boolean closeHov = hovered(mouseX, mouseY, closeBtnX, closeBtnY, 16, 16);
        GuiRenderUtils.fillRoundedRect(graphics, closeBtnX, closeBtnY, 16, 16, 4, closeHov ? 0x66EF4444 : bgDark);
        GuiRenderUtils.drawCenteredText(graphics, this.font, "✕", closeBtnX + 8, closeBtnY + 4, closeHov ? 0xFFFCA5A5 : 0xFF94A3B8);

        // Clear Active Highlight button in header (when an active 3D search is running)
        this.clearBtnW = 0;
        if (StorageFinderManager.hasActiveHighlight()) {
            String clrTxt = "✕ Clear 3D Highlight";
            this.clearBtnW = this.font.width(clrTxt) + 12;
            this.clearBtnX = closeBtnX - clearBtnW - 6;
            this.clearBtnY = panelY + 5;
            boolean clrHov = hovered(mouseX, mouseY, clearBtnX, clearBtnY, clearBtnW, 16);
            GuiRenderUtils.fillRoundedRect(graphics, clearBtnX, clearBtnY, clearBtnW, 16, 4, clrHov ? 0x66EF4444 : 0x33EF4444);
            GuiRenderUtils.drawRoundedOutline(graphics, clearBtnX, clearBtnY, clearBtnW, 16, 4, 0xAAEF4444);
            GuiRenderUtils.drawCenteredText(graphics, this.font, clrTxt, clearBtnX + clearBtnW / 2, clearBtnY + 4, 0xFFFCA5A5);
        }

        // Search Row
        int searchY = panelY + headerH + 8;
        int searchH = 20;
        String trackLabel = "✦ Highlight Matches";
        this.trackAllBtnW = this.font.width(trackLabel) + 14;
        this.trackAllBtnX = panelX + panelW - 10 - this.trackAllBtnW;
        this.trackAllBtnY = searchY;

        int searchBoxX = panelX + 10;
        int searchBoxW = this.trackAllBtnX - searchBoxX - 6;

        GuiRenderUtils.fillRoundedRect(graphics, searchBoxX, searchY, searchBoxW, searchH, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(graphics, searchBoxX, searchY, searchBoxW, searchH, 5,
                this.searchBox.isFocused() ? accent : border);
        GuiRenderUtils.drawText(graphics, this.font, "🔍", searchBoxX + 6, searchY + 6, accent, false);

        this.searchBox.setPosition(searchBoxX + 20, searchY + 6);
        this.searchBox.setWidth(Math.max(40, searchBoxW - 26));
        this.searchBox.extractRenderState(graphics, mouseX, mouseY, a);

        boolean canHighlight = !this.itemResults.isEmpty();
        boolean trackHov = canHighlight && hovered(mouseX, mouseY, trackAllBtnX, trackAllBtnY, trackAllBtnW, searchH);
        GuiRenderUtils.fillRoundedRect(graphics, trackAllBtnX, trackAllBtnY, trackAllBtnW, searchH, 5,
                canHighlight ? (trackHov ? GuiRenderUtils.withAlpha(accent, 90) : GuiRenderUtils.withAlpha(accent, 55)) : bgDark);
        GuiRenderUtils.drawRoundedOutline(graphics, trackAllBtnX, trackAllBtnY, trackAllBtnW, searchH, 5,
                canHighlight ? accent : border);
        GuiRenderUtils.drawCenteredText(graphics, this.font, trackLabel, trackAllBtnX + trackAllBtnW / 2, trackAllBtnY + 6,
                canHighlight ? 0xFFFFFFFF : 0xFF64748B);

        // Two-Column Body
        this.listTop = searchY + searchH + 10;
        this.listBottom = panelY + panelH - 22;
        int bodyH = Math.max(60, listBottom - listTop);
        int innerW = panelW - 20;
        this.leftX = panelX + 10;
        this.leftW = (int) (innerW * 0.54);
        this.rightX = this.leftX + this.leftW + 8;
        this.rightW = panelX + panelW - 10 - this.rightX;

        // Left Pane: Indexed Items
        GuiRenderUtils.fillRoundedRect(graphics, leftX, listTop, leftW, bodyH, 6, bgDark);
        GuiRenderUtils.drawRoundedOutline(graphics, leftX, listTop, leftW, bodyH, 6, border);

        // Right Pane: Matching Containers
        GuiRenderUtils.fillRoundedRect(graphics, rightX, listTop, rightW, bodyH, 6, bgDark);
        GuiRenderUtils.drawRoundedOutline(graphics, rightX, listTop, rightW, bodyH, 6, border);

        if (indexedCount == 0) {
            String empty1 = "No storage containers indexed yet!";
            String empty2 = "Open any Chest, Barrel, or Shulker Box in your base";
            String empty3 = "to automatically index its items for instant search.";
            GuiRenderUtils.drawCenteredText(graphics, this.font, empty1, panelX + panelW / 2, listTop + bodyH / 2 - 16, accent);
            GuiRenderUtils.drawCenteredText(graphics, this.font, empty2, panelX + panelW / 2, listTop + bodyH / 2 - 2, 0xFFCBD5E1);
            GuiRenderUtils.drawCenteredText(graphics, this.font, empty3, panelX + panelW / 2, listTop + bodyH / 2 + 11, 0xFF94A3B8);
        } else {
            renderLeftItemList(graphics, mouseX, mouseY, bodyH, accent, surface, surfaceHov, border);
            renderRightContainerList(graphics, mc, mouseX, mouseY, bodyH, accent, surface, surfaceHov, border);
        }

        // Bottom Help Footer
        String footer = "Click item to highlight in 3D  •  Enter: Highlight Search  •  📍 Pin Waypoint  •  ESC: Close";
        GuiRenderUtils.drawCenteredText(graphics, this.font,
                GuiRenderUtils.truncate(this.font, footer, panelW - 20),
                panelX + panelW / 2, panelY + panelH - 14, 0xFF64748B);
    }

    private void renderLeftItemList(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int bodyH,
                                    int accent, int surface, int surfaceHov, int border) {
        int totalH = Math.max(0, this.itemResults.size() * (ROW_H + 3) - 3);
        int innerH = bodyH - 8;
        int maxScroll = Math.max(0, totalH - innerH);
        this.leftScroll = Math.clamp(this.leftScroll, 0.0, maxScroll);

        graphics.enableScissor(leftX + 3, listTop + 3, leftX + leftW - 3, listBottom - 3);
        if (this.itemResults.isEmpty()) {
            GuiRenderUtils.drawCenteredText(graphics, this.font, "No matching items found",
                    leftX + leftW / 2, listTop + bodyH / 2 - 4, 0xFF64748B);
        } else {
            int ry = (int) Math.round(listTop + 5 - this.leftScroll);
            int rx = leftX + 5;
            int rw = leftW - (maxScroll > 0 ? 14 : 10);
            for (int i = 0; i < this.itemResults.size(); i++) {
                StorageFinderManager.AggregatedItem item = this.itemResults.get(i);
                if (ry + ROW_H >= listTop && ry <= listBottom) {
                    boolean selected = (i == this.selectedItemIdx);
                    boolean hov = mouseY >= listTop + 3 && mouseY <= listBottom - 3
                            && hovered(mouseX, mouseY, rx, ry, rw, ROW_H);
                    int fill = selected ? GuiRenderUtils.withAlpha(accent, 42) : (hov ? surfaceHov : surface);
                    GuiRenderUtils.fillRoundedRect(graphics, rx, ry, rw, ROW_H, 4, fill);
                    if (selected || hov) {
                        GuiRenderUtils.drawRoundedOutline(graphics, rx, ry, rw, ROW_H, 4,
                                selected ? accent : GuiRenderUtils.withAlpha(accent, 110));
                    }

                    ItemStack icon = StorageFinderManager.resolveIcon(item.itemId());
                    graphics.item(icon, rx + 4, ry + 5);

                    String countStr = formatCount(item.totalCount());
                    int cw = this.font.width(countStr);
                    String nameClip = GuiRenderUtils.truncate(this.font, item.displayName(), rw - 32 - cw);
                    graphics.text(this.font, nameClip, rx + 24, ry + 4, selected ? 0xFFFFFFFF : 0xFFE2E8F0, false);
                    graphics.text(this.font, countStr, rx + rw - cw - 6, ry + 4, accent, true);

                    String sub = item.containerCount() + (item.containerCount() == 1 ? " container" : " containers")
                            + (item.anyInShulker() ? " • 📦 Shulker" : "");
                    graphics.text(this.font, GuiRenderUtils.truncate(this.font, sub, rw - 30), rx + 24, ry + 15, 0xFF94A3B8, false);
                }
                ry += ROW_H + 3;
            }
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int sbX = leftX + leftW - 5;
            int thumbH = Math.max(16, (int) ((double) innerH * innerH / Math.max(1, totalH)));
            int thumbY = listTop + 4 + (int) ((innerH - thumbH) * (this.leftScroll / maxScroll));
            GuiRenderUtils.fillRoundedRect(graphics, sbX, listTop + 4, 2, innerH, 1, 0x22FFFFFF);
            GuiRenderUtils.fillRoundedRect(graphics, sbX, thumbY, 2, thumbH, 1, accent);
        }
    }

    private void renderRightContainerList(GuiGraphicsExtractor graphics, Minecraft mc, int mouseX, int mouseY, int bodyH,
                                          int accent, int surface, int surfaceHov, int border) {
        // Right pane sub-header
        int subH = 18;
        GuiRenderUtils.fillRoundedRect(graphics, rightX, listTop, rightW, subH, 6, surface);
        graphics.fill(rightX, listTop + subH - 1, rightX + rightW, listTop + subH, border);
        String hdr = this.selectedItemIdx >= 0 && this.selectedItemIdx < this.itemResults.size()
                ? ("Locations (" + this.containerMatches.size() + ")")
                : "Locations";
        GuiRenderUtils.drawText(graphics, this.font, hdr, rightX + 7, listTop + 5, 0xFFCBD5E1, false);

        int cTop = listTop + subH + 3;
        int innerH = Math.max(20, listBottom - cTop - 4);
        int totalH = Math.max(0, this.containerMatches.size() * (RIGHT_ROW_H + 3) - 3);
        int maxScroll = Math.max(0, totalH - innerH);
        this.rightScroll = Math.clamp(this.rightScroll, 0.0, maxScroll);

        graphics.enableScissor(rightX + 3, cTop, rightX + rightW - 3, listBottom - 3);
        if (this.containerMatches.isEmpty()) {
            GuiRenderUtils.drawCenteredText(graphics, this.font, "Select an item",
                    rightX + rightW / 2, cTop + innerH / 2 - 4, 0xFF64748B);
        } else {
            int ry = (int) Math.round(cTop + 2 - this.rightScroll);
            int rx = rightX + 5;
            int rw = rightW - (maxScroll > 0 ? 14 : 10);
            for (StorageFinderManager.ContainerMatch m : this.containerMatches) {
                if (ry + RIGHT_ROW_H >= cTop && ry <= listBottom) {
                    StorageFinderManager.IndexedContainer c = m.container();
                    boolean hov = mouseY >= cTop && mouseY <= listBottom - 3
                            && hovered(mouseX, mouseY, rx, ry, rw, RIGHT_ROW_H);
                    GuiRenderUtils.fillRoundedRect(graphics, rx, ry, rw, RIGHT_ROW_H, 4, hov ? surfaceHov : surface);
                    if (hov) {
                        GuiRenderUtils.drawRoundedOutline(graphics, rx, ry, rw, RIGHT_ROW_H, 4, GuiRenderUtils.withAlpha(accent, 140));
                    }

                    graphics.item(StorageFinderManager.resolveIcon(c.containerItemId), rx + 4, ry + 7);

                    int pinX = rx + rw - 17;
                    int pinY = ry + 7;
                    boolean pinHov = hovered(mouseX, mouseY, pinX, pinY, 14, 14);
                    GuiRenderUtils.fillRoundedRect(graphics, pinX, pinY, 14, 14, 3, pinHov ? GuiRenderUtils.withAlpha(accent, 70) : 0x440B1120);
                    GuiRenderUtils.drawCenteredText(graphics, this.font, "📍", pinX + 7, pinY + 3, pinHov ? 0xFFFFFFFF : 0xFF94A3B8);

                    String qty = "×" + m.matchedCount() + (m.anyInShulker() ? " 📦" : "");
                    int qw = this.font.width(qty);
                    String lbl = GuiRenderUtils.truncate(this.font, c.label, rw - 44 - qw);
                    graphics.text(this.font, lbl, rx + 23, ry + 5, 0xFFF8FAFC, false);
                    graphics.text(this.font, qty, pinX - qw - 5, ry + 5, 0xFF34D399, true);

                    double dist = Math.sqrt(c.pos.distToCenterSqr(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
                    String loc = String.format(Locale.US, "%d, %d, %d  •  %.0fm",
                            c.pos.getX(), c.pos.getY(), c.pos.getZ(), dist);
                    graphics.text(this.font, GuiRenderUtils.truncate(this.font, loc, rw - 44), rx + 23, ry + 17, 0xFF94A3B8, false);
                }
                ry += RIGHT_ROW_H + 3;
            }
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int sbX = rightX + rightW - 5;
            int thumbH = Math.max(16, (int) ((double) innerH * innerH / Math.max(1, totalH)));
            int thumbY = cTop + (int) ((innerH - thumbH) * (this.rightScroll / maxScroll));
            GuiRenderUtils.fillRoundedRect(graphics, sbX, cTop, 2, innerH, 1, 0x22FFFFFF);
            GuiRenderUtils.fillRoundedRect(graphics, sbX, thumbY, 2, thumbH, 1, accent);
        }
    }

    // -------------------------------------------------------------------------
    // Input Handling
    // -------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        Minecraft mc = Minecraft.getInstance();

        if (hovered(mx, my, closeBtnX, closeBtnY, 16, 16)) {
            playClick();
            this.onClose();
            return true;
        }
        if (this.clearBtnW > 0 && hovered(mx, my, clearBtnX, clearBtnY, clearBtnW, 16)) {
            playClick();
            StorageFinderManager.clearActiveHighlight();
            return true;
        }
        if (!this.itemResults.isEmpty() && hovered(mx, my, trackAllBtnX, trackAllBtnY, trackAllBtnW, 20)) {
            playClick();
            highlightCurrentSelection(mc);
            return true;
        }

        // Click on Left Item List -> select on single click, highlight & close on click if already selected or doubleClick!
        if (hovered(mx, my, leftX, listTop, leftW, listBottom - listTop)) {
            int ry = (int) Math.round(listTop + 5 - this.leftScroll);
            int rx = leftX + 5;
            int rw = leftW - 10;
            for (int i = 0; i < this.itemResults.size(); i++) {
                if (hovered(mx, my, rx, ry, rw, ROW_H)) {
                    playClick();
                    StorageFinderManager.AggregatedItem clicked = this.itemResults.get(i);
                    if (i == this.selectedItemIdx || doubleClick) {
                        activateItemHighlight(mc, clicked, null);
                    } else {
                        this.selectedItemIdx = i;
                        this.rightScroll = 0.0;
                        this.containerMatches = StorageFinderManager.findMatchingContainers(
                                mc, this.lastQuery, clicked.itemId(), clicked.displayName(), null);
                    }
                    return true;
                }
                ry += ROW_H + 3;
            }
        }

        // Click on Right Container List -> Pin Waypoint or Focus single container in 3D
        int cTop = listTop + 21;
        if (hovered(mx, my, rightX, cTop, rightW, listBottom - cTop)
                && this.selectedItemIdx >= 0 && this.selectedItemIdx < this.itemResults.size()) {
            StorageFinderManager.AggregatedItem sel = this.itemResults.get(this.selectedItemIdx);
            int ry = (int) Math.round(cTop + 2 - this.rightScroll);
            int rx = rightX + 5;
            int rw = rightW - 10;
            for (StorageFinderManager.ContainerMatch m : this.containerMatches) {
                if (hovered(mx, my, rx, ry, rw, RIGHT_ROW_H)) {
                    playClick();
                    int pinX = rx + rw - 17;
                    int pinY = ry + 7;
                    if (hovered(mx, my, pinX, pinY, 14, 14)) {
                        String wk = WaypointsState.getCurrentWorldKey(mc);
                        String dim = WaypointsState.getCurrentDimension(mc);
                        int col = StarClient.getInstance().getClientSettings().getMenuAccentColor();
                        WaypointsState.addWaypoint(sel.displayName() + " Chest",
                                m.container().pos.getX(), m.container().pos.getY(), m.container().pos.getZ(),
                                dim, wk, col);
                        ToastNotificationManager.pushToast("storage_wp", "Waypoint Saved",
                                sel.displayName() + " @ " + m.container().pos.getX() + ", " + m.container().pos.getY() + ", " + m.container().pos.getZ(),
                                "STORAGE", col);
                        return true;
                    }
                    activateItemHighlight(mc, sel, m.container().pos);
                    return true;
                }
                ry += RIGHT_ROW_H + 3;
            }
        }

        if (this.searchBox != null && this.searchBox.mouseClicked(event, doubleClick)) {
            this.setFocused(this.searchBox);
            this.searchBox.setFocused(true);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void highlightCurrentSelection(Minecraft mc) {
        String q = this.searchBox != null ? this.searchBox.getValue().trim() : "";
        if (!q.isEmpty() && this.itemResults.size() > 1) {
            StorageFinderManager.setActiveSearchHighlight(q, null, q, null);
            int totalBoxes = StorageFinderManager.getActiveMatches(mc).size();
            int accent = StarClient.getInstance().getClientSettings().getMenuAccentColor();
            ToastNotificationManager.pushToast("storage_find", "Highlighting \"" + q + "\"",
                    totalBoxes + (totalBoxes == 1 ? " container found" : " containers found"),
                    "STORAGE", accent);
            this.onClose();
        } else if (this.selectedItemIdx >= 0 && this.selectedItemIdx < this.itemResults.size()) {
            activateItemHighlight(mc, this.itemResults.get(this.selectedItemIdx), null);
        }
    }

    private void activateItemHighlight(Minecraft mc, StorageFinderManager.AggregatedItem item, net.minecraft.core.BlockPos focusPos) {
        StorageFinderManager.setActiveSearchHighlight(item.displayName(), item.itemId(), item.displayName(), focusPos);
        int count = focusPos != null ? 1 : item.containerCount();
        int accent = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        ToastNotificationManager.pushToast("storage_find", "Tracking " + item.displayName(),
                count + (count == 1 ? " container highlighted" : " containers highlighted"),
                "STORAGE", accent);
        this.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (hovered(mouseX, mouseY, leftX, listTop, leftW, listBottom - listTop)) {
            this.leftScroll -= verticalAmount * 22.0;
            return true;
        }
        if (hovered(mouseX, mouseY, rightX, listTop, rightW, listBottom - listTop)) {
            this.rightScroll -= verticalAmount * 22.0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (!this.itemResults.isEmpty()) {
                playClick();
                highlightCurrentSelection(Minecraft.getInstance());
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_DOWN && !this.itemResults.isEmpty()) {
            this.selectedItemIdx = Math.min(this.itemResults.size() - 1, this.selectedItemIdx + 1);
            StorageFinderManager.AggregatedItem sel = this.itemResults.get(this.selectedItemIdx);
            this.containerMatches = StorageFinderManager.findMatchingContainers(
                    Minecraft.getInstance(), this.lastQuery, sel.itemId(), sel.displayName(), null);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP && !this.itemResults.isEmpty()) {
            this.selectedItemIdx = Math.max(0, this.selectedItemIdx - 1);
            StorageFinderManager.AggregatedItem sel = this.itemResults.get(this.selectedItemIdx);
            this.containerMatches = StorageFinderManager.findMatchingContainers(
                    Minecraft.getInstance(), this.lastQuery, sel.itemId(), sel.displayName(), null);
            return true;
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (this.searchBox.keyPressed(event)) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return this.searchBox.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    private static String formatCount(int count) {
        if (count >= 64) {
            int stacks = count / 64;
            int rem = count % 64;
            return rem == 0 ? ("×" + count + " (" + stacks + "st)") : ("×" + count);
        }
        return "×" + count;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static boolean hovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
