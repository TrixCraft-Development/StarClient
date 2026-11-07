package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.module.ModuleManager;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class HudEditorScreen extends Screen {
    private static final int SNAP_THRESHOLD = 6;
    private static final int TOOLBAR_WIDTH = 374;
    private static final int TOOLBAR_HEIGHT = 34;
    private static final int TOOLBAR_Y = 10;
    private static final int SNAP_BTN_WIDTH = 72;
    private static final int RESET_BTN_WIDTH = 86;
    private static final int DONE_BTN_WIDTH = 64;
    private static final int BTN_HEIGHT = 20;

    private final Screen parent;
    private String draggingHudId;
    private int dragOffsetX;
    private int dragOffsetY;
    private int dragRectWidth;
    private int dragRectHeight;
    private boolean snappedCenterX;
    private boolean snappedCenterY;
    private Integer activeSnapGuideX;
    private Integer activeSnapGuideY;

    private String lastClickedId;
    private long lastClickTime;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Layout Editor"));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        StarClient mod = StarClient.getInstance();
        ClientSettings cs = mod.getClientSettings();
        HudLayout layout = mod.getHudLayout();
        int accent = cs.getMenuAccentColor();
        int accentDark = cs.getMenuAccentDarkColor();
        int bg = 0xF4000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
        int surface = cs.getMenuSurfaceColor();
        int border = cs.getMenuBorderColor();

        // Subtle snapping crosshair lines
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        extractor.fill(centerX, 0, centerX + 1, this.height, snappedCenterX ? accent : 0x2264748B);
        extractor.fill(0, centerY, this.width, centerY + 1, snappedCenterY ? accent : 0x2264748B);

        // Active magnetic widget-to-widget / edge alignment guide lines
        if (this.draggingHudId != null && layout.isSnapGuides()) {
            int guideColor = 0xCC22D3EE;
            if (activeSnapGuideX != null) {
                extractor.fill(activeSnapGuideX, 0, activeSnapGuideX + 1, this.height, guideColor);
            }
            if (activeSnapGuideY != null) {
                extractor.fill(0, activeSnapGuideY, this.width, activeSnapGuideY + 1, guideColor);
            }
        }

        // Render actual HUD elements (drawn after blur so texts stay crisp)
        HudOverlay.render(extractor);

        Minecraft client = Minecraft.getInstance();
        boolean inWorld = client.player != null && client.level != null;

        // Render Scoreboard in HUD Editor (live or preview)
        Module scoreboardMod = mod.getModuleManager().getModuleByName("Scoreboard");
        if (scoreboardMod != null && scoreboardMod.isEnabled()) {
            Objective obj = inWorld
                    ? client.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR)
                    : null;
            if (obj != null) {
                ScoreboardRenderer.render(extractor, obj, scoreboardMod);
            } else {
                ScoreboardRenderer.renderPreview(extractor, scoreboardMod);
            }
        }

        // Render Boss Bar in HUD Editor preview
        Module bossBarMod = mod.getModuleManager().getModuleByName("Boss Bar");
        if (bossBarMod != null && bossBarMod.isEnabled()) {
            BossBarRenderer.renderPreview(extractor, bossBarMod);
        }

        // Render Inventory HUD in HUD Editor preview when player is null
        Module invMod = mod.getModuleManager().getModuleByName("Inventory HUD");
        if (invMod != null && invMod.isEnabled() && isHudElementVisible(HudLayout.INVENTORY) && client.player == null) {
            InventoryHudRenderer.renderPreview(extractor, invMod);
        }

        // Render Minimap & Item Counter previews when editing from Main Menu
        if (!inWorld) {
            Module minimapMod = mod.getModuleManager().getModuleByName("Minimap");
            if (minimapMod != null && minimapMod.isEnabled()) {
                int mapW = MinimapRenderer.getWidth(minimapMod);
                int mapX = layout.effectiveX(HudLayout.MINIMAP, this.width, mapW);
                int mapY = layout.effectiveY(HudLayout.MINIMAP, this.height);
                boolean sc = layout.beginScale(extractor, mapX, mapY, HudLayout.MINIMAP);
                MinimapRenderer.renderPreview(extractor, minimapMod);
                layout.endScale(extractor, sc);
            }
            Module itemCounterMod = mod.getModuleManager().getModuleByName("Item Counter");
            if (itemCounterMod != null && itemCounterMod.isEnabled()) {
                int icW = ItemCounterRenderer.getWidth(this.font, client, itemCounterMod);
                int icX = layout.effectiveX(HudLayout.ITEM_COUNTER, this.width, icW);
                int icY = layout.effectiveY(HudLayout.ITEM_COUNTER, this.height);
                boolean sc = layout.beginScale(extractor, icX, icY, HudLayout.ITEM_COUNTER);
                ItemCounterRenderer.renderPreview(extractor, itemCounterMod);
                layout.endScale(extractor, sc);
            }
        }

        // Render rounded bounding outlines, preview cards, and pill badges for all active elements
        List<Module> activeMods = getActiveModules();
        List<HudLayout.Rect> rects = HudLayout.computeRects(this.font, activeMods, this.width, this.height, layout);

        for (HudLayout.Rect r : rects) {
            if (!isHudElementVisible(r.id())) continue;

            int rw = r.x2() - r.x1();
            int rh = r.y2() - r.y1();

            // Preview card when editing HUD from the main menu (outside a world)
            if (!inWorld
                    && !r.id().equals(HudLayout.SCOREBOARD)
                    && !r.id().equals(HudLayout.BOSS_BAR)
                    && !r.id().equals(HudLayout.INVENTORY)
                    && !r.id().equals(HudLayout.MINIMAP)
                    && !r.id().equals(HudLayout.ITEM_COUNTER)) {
                GuiRenderUtils.drawDropShadow(extractor, r.x1(), r.y1(), rw, rh, 4, 3, 80);
                GuiRenderUtils.fillRoundedRect(extractor, r.x1(), r.y1(), rw, rh, 4, 0xCC0F172A);
                GuiRenderUtils.drawRoundedOutline(extractor, r.x1(), r.y1(), rw, rh, 4, 0x4438BDF8);
                String preview = previewTextFor(r.id());
                extractor.text(this.font, preview, r.x1() + 6, r.y1() + (rh - this.font.lineHeight) / 2 + 1, 0xFFF8FAFC);
            }

            boolean hovered = r.contains(mouseX, mouseY);
            boolean dragging = r.id().equals(this.draggingHudId);

            // Soft highlight fill on hover / drag
            if (dragging || hovered) {
                int fillTint = GuiRenderUtils.withAlpha(accent, dragging ? 45 : 22);
                GuiRenderUtils.fillRoundedRect(extractor, r.x1(), r.y1(), rw, rh, 4, fillTint);
            }

            // Rounded selection border
            int borderColor = dragging ? accent : (hovered ? accent : GuiRenderUtils.withAlpha(accent, 120));
            GuiRenderUtils.drawRoundedOutline(extractor, r.x1(), r.y1(), rw, rh, 4, borderColor);

            // Floating pill badge on top of element (shows scale when != 1.0x or hovered)
            String displayName = moduleDisplayNameFor(r.id());
            float scale = layout.getScale(r.id());
            boolean showScale = Math.abs(scale - 1.0f) >= 0.01f || hovered || dragging;
            String scaleSuffix = showScale ? String.format(Locale.US, " [%.2fx]", scale) : "";
            String badgeText = "✥ " + displayName + scaleSuffix + (dragging ? " (moving)" : "");
            int badgeW = this.font.width(badgeText) + 10;
            int badgeH = 13;
            int badgeX = Math.clamp(r.x1(), 4, Math.max(4, this.width - badgeW - 4));
            int badgeY = (r.y1() >= badgeH + 4) ? (r.y1() - badgeH - 3) : (r.y2() + 3);
            badgeY = Math.clamp(badgeY, 2, Math.max(2, this.height - badgeH - 2));

            int badgeBg = (dragging || hovered) ? accentDark : bg;
            int badgeBorder = (dragging || hovered) ? accent : border;
            int badgeTextCol = (dragging || hovered) ? 0xFFFFFFFF : 0xFF94A3B8;

            GuiRenderUtils.fillRoundedRect(extractor, badgeX, badgeY, badgeW, badgeH, 3, badgeBg);
            GuiRenderUtils.drawRoundedOutline(extractor, badgeX, badgeY, badgeW, badgeH, 3, badgeBorder);
            extractor.text(this.font, badgeText, badgeX + 5, badgeY + 2, badgeTextCol);
        }

        // Floating Top Island Toolbar
        int barX = (this.width - TOOLBAR_WIDTH) / 2;
        int barY = TOOLBAR_Y;
        GuiRenderUtils.drawDropShadow(extractor, barX, barY, TOOLBAR_WIDTH, TOOLBAR_HEIGHT, 8, 6, 120);
        GuiRenderUtils.fillRoundedRect(extractor, barX, barY, TOOLBAR_WIDTH, TOOLBAR_HEIGHT, 8, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, barX, barY, TOOLBAR_WIDTH, TOOLBAR_HEIGHT, 8, border);

        // Header Title Badge
        int iconBadgeW = 20;
        int iconBadgeH = 20;
        int iconBadgeX = barX + 7;
        int iconBadgeY = barY + (TOOLBAR_HEIGHT - iconBadgeH) / 2;
        GuiRenderUtils.fillRoundedRect(extractor, iconBadgeX, iconBadgeY, iconBadgeW, iconBadgeH, 5, accentDark);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✥", iconBadgeX + iconBadgeW / 2, iconBadgeY + 5, 0xFFFFFFFF);

        extractor.text(this.font, "HUD Editor", iconBadgeX + iconBadgeW + 7, barY + 12, 0xFFF8FAFC);

        // Action Buttons on right side of Toolbar
        int btnY = barY + (TOOLBAR_HEIGHT - BTN_HEIGHT) / 2;
        int doneX = barX + TOOLBAR_WIDTH - DONE_BTN_WIDTH - 7;
        int resetX = doneX - RESET_BTN_WIDTH - 6;
        int snapX = resetX - SNAP_BTN_WIDTH - 6;

        // Snap Guides Toggle Button
        boolean snapOn = layout.isSnapGuides();
        boolean snapHover = hovered(snapX, btnY, SNAP_BTN_WIDTH, mouseX, mouseY);
        int snapBg = snapOn ? (snapHover ? accent : accentDark) : surface;
        int snapBorder = snapOn ? accent : (snapHover ? 0xFF94A3B8 : border);
        int snapTextCol = snapOn ? 0xFFFFFFFF : 0xFF94A3B8;
        GuiRenderUtils.fillRoundedRect(extractor, snapX, btnY, SNAP_BTN_WIDTH, BTN_HEIGHT, 5, snapBg);
        GuiRenderUtils.drawRoundedOutline(extractor, snapX, btnY, SNAP_BTN_WIDTH, BTN_HEIGHT, 5, snapBorder);
        GuiRenderUtils.drawCenteredText(extractor, this.font, snapOn ? "Snap: ON" : "Snap: OFF",
                snapX + SNAP_BTN_WIDTH / 2, btnY + 6, snapTextCol);

        // Reset Button
        boolean resetHover = hovered(resetX, btnY, RESET_BTN_WIDTH, mouseX, mouseY);
        int resetBg = resetHover ? 0x33F87171 : surface;
        int resetBorder = resetHover ? 0xFFF87171 : border;
        int resetTextCol = resetHover ? 0xFFF87171 : 0xFF94A3B8;
        GuiRenderUtils.fillRoundedRect(extractor, resetX, btnY, RESET_BTN_WIDTH, BTN_HEIGHT, 5, resetBg);
        GuiRenderUtils.drawRoundedOutline(extractor, resetX, btnY, RESET_BTN_WIDTH, BTN_HEIGHT, 5, resetBorder);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Reset All", resetX + RESET_BTN_WIDTH / 2, btnY + 6, resetTextCol);

        // Done Button
        boolean doneHover = hovered(doneX, btnY, DONE_BTN_WIDTH, mouseX, mouseY);
        int doneBg = doneHover ? accentDark : accent;
        int doneBorder = doneHover ? 0xFFFFFFFF : accent;
        GuiRenderUtils.fillRoundedRect(extractor, doneX, btnY, DONE_BTN_WIDTH, BTN_HEIGHT, 5, doneBg);
        GuiRenderUtils.drawRoundedOutline(extractor, doneX, btnY, DONE_BTN_WIDTH, BTN_HEIGHT, 5, doneBorder);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Done", doneX + DONE_BTN_WIDTH / 2, btnY + 6, 0xFFFFFFFF);

        // Bottom Instruction Pill
        String tipText = "Drag to move • Scroll wheel to scale • Right-click to reset • Magnetic snap guides";
        int tipW = this.font.width(tipText) + 20;
        int tipH = 18;
        int tipX = (this.width - tipW) / 2;
        int tipY = this.height - tipH - 8;
        GuiRenderUtils.drawDropShadow(extractor, tipX, tipY, tipW, tipH, 5, 3, 90);
        GuiRenderUtils.fillRoundedRect(extractor, tipX, tipY, tipW, tipH, 5, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, tipX, tipY, tipW, tipH, 5, border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, tipText, tipX + tipW / 2, tipY + 5, 0xFF94A3B8);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        int barX = (this.width - TOOLBAR_WIDTH) / 2;
        int btnY = TOOLBAR_Y + (TOOLBAR_HEIGHT - BTN_HEIGHT) / 2;
        int doneX = barX + TOOLBAR_WIDTH - DONE_BTN_WIDTH - 7;
        int resetX = doneX - RESET_BTN_WIDTH - 6;
        int snapX = resetX - SNAP_BTN_WIDTH - 6;

        HudLayout layout = StarClient.getInstance().getHudLayout();

        // Toolbar Buttons
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (hovered(snapX, btnY, SNAP_BTN_WIDTH, mouseX, mouseY)) {
                layout.setSnapGuides(!layout.isSnapGuides());
                StarClient.getInstance().getConfigManager().saveConfig();
                return true;
            }
            if (hovered(resetX, btnY, RESET_BTN_WIDTH, mouseX, mouseY)) {
                layout.reset();
                StarClient.getInstance().getConfigManager().saveConfig();
                return true;
            }
            if (hovered(doneX, btnY, DONE_BTN_WIDTH, mouseX, mouseY)) {
                Minecraft.getInstance().gui.setScreen(parent);
                return true;
            }
        }

        // Elements interaction
        List<HudLayout.Rect> rects = HudLayout.computeRects(this.font, getActiveModules(), this.width, this.height, layout);

        for (HudLayout.Rect r : rects) {
            if (!isHudElementVisible(r.id())) continue;
            if (r.contains(mouseX, mouseY)) {
                // Right-click immediately resets this individual element's position and scale
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    layout.resetElement(r.id());
                    StarClient.getInstance().getConfigManager().saveConfig();
                    return true;
                }

                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    // Double-click reset check
                    long now = System.currentTimeMillis();
                    if (r.id().equals(lastClickedId) && (now - lastClickTime) < 300) {
                        layout.resetElement(r.id());
                        StarClient.getInstance().getConfigManager().saveConfig();
                        this.lastClickedId = null;
                        this.draggingHudId = null;
                        return true;
                    }
                    this.lastClickedId = r.id();
                    this.lastClickTime = now;

                    this.draggingHudId = r.id();
                    this.dragOffsetX = (int) mouseX - r.x1();
                    this.dragOffsetY = (int) mouseY - r.y1();
                    this.dragRectWidth = r.x2() - r.x1();
                    this.dragRectHeight = r.y2() - r.y1();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, action);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (Math.abs(verticalAmount) > 0.001) {
            HudLayout layout = StarClient.getInstance().getHudLayout();
            List<HudLayout.Rect> rects = HudLayout.computeRects(this.font, getActiveModules(), this.width, this.height, layout);
            for (HudLayout.Rect r : rects) {
                if (!isHudElementVisible(r.id())) continue;
                if (r.contains(mouseX, mouseY)) {
                    // Preserve visual top-left when scaling an AUTO element for the first time
                    if (layout.isAuto(r.id())) {
                        layout.setPosition(r.id(), r.x1(), r.y1());
                    }
                    float current = layout.getScale(r.id());
                    float delta = verticalAmount > 0 ? 0.05f : -0.05f;
                    layout.setScale(r.id(), current + delta);
                    StarClient.getInstance().getConfigManager().saveConfig();
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingHudId != null) {
            HudLayout layout = StarClient.getInstance().getHudLayout();
            int newX = (int) event.x() - this.dragOffsetX;
            int newY = (int) event.y() - this.dragOffsetY;

            this.snappedCenterX = false;
            this.snappedCenterY = false;
            this.activeSnapGuideX = null;
            this.activeSnapGuideY = null;

            if (layout.isSnapGuides()) {
                // 1. Screen center & 8px margin snaps
                int centerTargetX = (this.width - this.dragRectWidth) / 2;
                int centerTargetY = (this.height - this.dragRectHeight) / 2;

                int bestDx = SNAP_THRESHOLD + 1;
                Integer snapXVal = null;
                Integer guideXVal = null;
                boolean centerXHit = false;

                if (Math.abs(newX - centerTargetX) <= SNAP_THRESHOLD) {
                    bestDx = Math.abs(newX - centerTargetX);
                    snapXVal = centerTargetX;
                    centerXHit = true;
                }
                if (Math.abs(newX - 8) < bestDx) {
                    bestDx = Math.abs(newX - 8);
                    snapXVal = 8;
                    guideXVal = 8;
                    centerXHit = false;
                }
                int rightMarginX = this.width - 8 - this.dragRectWidth;
                if (Math.abs(newX - rightMarginX) < bestDx) {
                    bestDx = Math.abs(newX - rightMarginX);
                    snapXVal = rightMarginX;
                    guideXVal = this.width - 8;
                    centerXHit = false;
                }

                int bestDy = SNAP_THRESHOLD + 1;
                Integer snapYVal = null;
                Integer guideYVal = null;
                boolean centerYHit = false;

                if (Math.abs(newY - centerTargetY) <= SNAP_THRESHOLD) {
                    bestDy = Math.abs(newY - centerTargetY);
                    snapYVal = centerTargetY;
                    centerYHit = true;
                }
                if (Math.abs(newY - 8) < bestDy) {
                    bestDy = Math.abs(newY - 8);
                    snapYVal = 8;
                    guideYVal = 8;
                    centerYHit = false;
                }
                int bottomMarginY = this.height - 8 - this.dragRectHeight;
                if (Math.abs(newY - bottomMarginY) < bestDy) {
                    bestDy = Math.abs(newY - bottomMarginY);
                    snapYVal = bottomMarginY;
                    guideYVal = this.height - 8;
                    centerYHit = false;
                }

                // 2. Magnetic widget-to-widget edge & center alignment
                List<HudLayout.Rect> rects = HudLayout.computeRects(this.font, getActiveModules(), this.width, this.height, layout);
                int myCenterOffsetX = this.dragRectWidth / 2;
                int myCenterOffsetY = this.dragRectHeight / 2;

                for (HudLayout.Rect other : rects) {
                    if (other.id().equals(this.draggingHudId) || !isHudElementVisible(other.id())) continue;
                    int otherCx = (other.x1() + other.x2()) / 2;
                    int otherCy = (other.y1() + other.y2()) / 2;

                    // X candidates: left->left, left->right(+4 gap or flush), right->right, right->left, center->center
                    int[][] xCandidates = {
                            {other.x1(), other.x1()},
                            {other.x2() - this.dragRectWidth, other.x2()},
                            {other.x2() + 4, other.x2()},
                            {other.x1() - this.dragRectWidth - 4, other.x1()},
                            {otherCx - myCenterOffsetX, otherCx}
                    };
                    for (int[] cand : xCandidates) {
                        int d = Math.abs(newX - cand[0]);
                        if (d < bestDx && d <= SNAP_THRESHOLD) {
                            bestDx = d;
                            snapXVal = cand[0];
                            guideXVal = cand[1];
                            centerXHit = false;
                        }
                    }

                    // Y candidates: top->top, bottom->bottom, top->bottom(+4 gap), bottom->top(-4 gap), center->center
                    int[][] yCandidates = {
                            {other.y1(), other.y1()},
                            {other.y2() - this.dragRectHeight, other.y2()},
                            {other.y2() + 4, other.y2()},
                            {other.y1() - this.dragRectHeight - 4, other.y1()},
                            {otherCy - myCenterOffsetY, otherCy}
                    };
                    for (int[] cand : yCandidates) {
                        int d = Math.abs(newY - cand[0]);
                        if (d < bestDy && d <= SNAP_THRESHOLD) {
                            bestDy = d;
                            snapYVal = cand[0];
                            guideYVal = cand[1];
                            centerYHit = false;
                        }
                    }
                }

                if (snapXVal != null) {
                    newX = snapXVal;
                    this.snappedCenterX = centerXHit;
                    this.activeSnapGuideX = guideXVal;
                }
                if (snapYVal != null) {
                    newY = snapYVal;
                    this.snappedCenterY = centerYHit;
                    this.activeSnapGuideY = guideYVal;
                }
            }

            newX = Math.clamp(newX, 0, Math.max(0, this.width - 20));
            newY = Math.clamp(newY, 0, Math.max(0, this.height - 16));

            // Special handling for BOSS_BAR which stores anchor relative to BASE_BOSS_BAR_WIDTH center
            if (HudLayout.BOSS_BAR.equals(this.draggingHudId)) {
                int targetCenter = newX + this.dragRectWidth / 2;
                layout.setPosition(this.draggingHudId, targetCenter - HudLayout.BASE_BOSS_BAR_WIDTH / 2, newY);
            } else {
                layout.setPosition(this.draggingHudId, newX, newY);
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (this.draggingHudId != null) {
            this.draggingHudId = null;
            this.snappedCenterX = false;
            this.snappedCenterY = false;
            this.activeSnapGuideX = null;
            this.activeSnapGuideY = null;
            StarClient.getInstance().getConfigManager().saveConfig();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    private List<Module> getActiveModules() {
        return StarClient.getInstance().getModuleManager().getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());
    }

    private static boolean isHudElementVisible(String id) {
        ModuleManager moduleManager = StarClient.getInstance().getModuleManager();
        Module module = moduleManager.getModuleByName(moduleNameForHudElement(id));
        if (module == null || !module.isEnabled()) return false;
        if (id.equals(HudLayout.INVENTORY)) {
            net.starclient.setting.Setting<?> s = module.getSetting("Show HUD Widget");
            return s == null || !Boolean.FALSE.equals(s.getValue());
        }
        return true;
    }

    public static String moduleNameForHudElement(String id) {
        return switch (id) {
            case HudLayout.FPS -> "FPS Display";
            case HudLayout.PING -> "Ping Display";
            case HudLayout.COORDINATES -> "Coordinates";
            case HudLayout.ARRAYLIST -> "Active Modules";
            case HudLayout.KEYSTROKES -> "Keystrokes";
            case HudLayout.ARMOR -> "Armor HUD";
            case HudLayout.CPS -> "CPS Display";
            case HudLayout.REACH -> "Reach Display";
            case HudLayout.SPEED -> "Speedometer";
            case HudLayout.COMBO -> "Combo Counter";
            case HudLayout.CLOCK -> "Clock Display";
            case HudLayout.COMPASS -> "Compass Display";
            case HudLayout.SERVER_INFO -> "Server Info";
            case HudLayout.MEMORY -> "Memory Display";
            case HudLayout.MINIMAP -> "Minimap";
            case HudLayout.WORLD_INFO -> "World Info";
            case HudLayout.ITEM_COUNTER -> "Item Counter";
            case HudLayout.POTIONS -> "Potion Effects";
            case HudLayout.TARGET_HUD -> "Target HUD";
            case HudLayout.SCOREBOARD -> "Scoreboard";
            case HudLayout.BOSS_BAR -> "Boss Bar";
            case HudLayout.INVENTORY -> "Inventory HUD";
            default -> "";
        };
    }

    private static String moduleDisplayNameFor(String id) {
        String name = moduleNameForHudElement(id);
        return name.isEmpty() ? id : name;
    }

    private static String previewTextFor(String id) {
        return switch (id) {
            case HudLayout.FPS -> "FPS: 144";
            case HudLayout.PING -> "Ping: 24ms";
            case HudLayout.COORDINATES -> "XYZ: 120, 64, -85";
            case HudLayout.ARRAYLIST -> "Active Modules";
            case HudLayout.KEYSTROKES -> "WASD";
            case HudLayout.ARMOR -> "Armor HUD";
            case HudLayout.CPS -> "CPS: 12 | 8";
            case HudLayout.REACH -> "Reach: 2.84 b";
            case HudLayout.SPEED -> "Speed: 5.61 m/s";
            case HudLayout.COMBO -> "Combo: 4 hits";
            case HudLayout.CLOCK -> "🕒 15:45:00";
            case HudLayout.COMPASS -> "🧭 N • 0°";
            case HudLayout.SERVER_INFO -> "Server: mc.hypixel.net";
            case HudLayout.MEMORY -> "RAM: 42%";
            case HudLayout.WORLD_INFO -> "Biome: Plains • Day 42";
            case HudLayout.POTIONS -> "Speed II (01:30)";
            case HudLayout.TARGET_HUD -> "Target HUD";
            case HudLayout.SCOREBOARD -> "Scoreboard";
            case HudLayout.BOSS_BAR -> "Boss Bar";
            case HudLayout.INVENTORY -> "Inventory HUD";
            default -> id;
        };
    }

    private static boolean hovered(int x, int y, int w, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + HudEditorScreen.BTN_HEIGHT;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
