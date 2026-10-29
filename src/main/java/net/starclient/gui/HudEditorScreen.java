package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.module.ModuleManager;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.stream.Collectors;

public class HudEditorScreen extends Screen {
    private static final int SNAP_THRESHOLD = 6;
    private static final int TOOLBAR_WIDTH = 300;
    private static final int TOOLBAR_HEIGHT = 34;
    private static final int TOOLBAR_Y = 10;
    private static final int RESET_BTN_WIDTH = 90;
    private static final int DONE_BTN_WIDTH = 68;
    private static final int BTN_HEIGHT = 20;

    private final Screen parent;
    private String draggingHudId;
    private int dragOffsetX;
    private int dragOffsetY;
    private int dragRectWidth;
    private int dragRectHeight;
    private boolean snappedCenterX;
    private boolean snappedCenterY;

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
        int accent = cs.getMenuAccentColor();
        int accentDark = cs.getMenuAccentDarkColor();
        int bg = 0xF4000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
        int surface = cs.getMenuSurfaceColor();
        int surfaceHover = cs.getMenuSurfaceHoverColor();
        int border = cs.getMenuBorderColor();

        // Subtle snapping crosshair lines
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        extractor.fill(centerX, 0, centerX + 1, this.height, snappedCenterX ? accent : 0x2264748B);
        extractor.fill(0, centerY, this.width, centerY + 1, snappedCenterY ? accent : 0x2264748B);

        // Render actual HUD elements (drawn after blur so texts stay crisp)
        HudOverlay.render(extractor);

        Minecraft client = Minecraft.getInstance();
        boolean inWorld = client.player != null && client.level != null;

        // Render rounded bounding outlines, preview cards, and pill badges for all active elements
        HudLayout layout = mod.getHudLayout();
        List<Module> activeMods = getActiveModules();
        List<HudLayout.Rect> rects = HudLayout.computeRects(this.font, activeMods, this.width, this.height, layout);

        for (HudLayout.Rect r : rects) {
            if (!isHudElementVisible(r.id())) continue;

            int rw = r.x2() - r.x1();
            int rh = r.y2() - r.y1();

            // Preview card when editing HUD from the main menu (outside a world)
            if (!inWorld) {
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

            // Floating pill badge on top of element
            String displayName = moduleDisplayNameFor(r.id());
            String badgeText = "✥ " + displayName + (dragging ? " (moving)" : "");
            int badgeW = this.font.width(badgeText) + 10;
            int badgeH = 13;
            int badgeX = r.x1();
            int badgeY = Math.max(2, r.y1() - badgeH - 3);

            // Keep badge within screen horizontally
            if (badgeX + badgeW > this.width - 4) {
                badgeX = this.width - badgeW - 4;
            }

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

        // Reset Button
        boolean resetHover = hovered(resetX, btnY, RESET_BTN_WIDTH, BTN_HEIGHT, mouseX, mouseY);
        int resetBg = resetHover ? 0x33F87171 : surface;
        int resetBorder = resetHover ? 0xFFF87171 : border;
        int resetTextCol = resetHover ? 0xFFF87171 : 0xFF94A3B8;
        GuiRenderUtils.fillRoundedRect(extractor, resetX, btnY, RESET_BTN_WIDTH, BTN_HEIGHT, 5, resetBg);
        GuiRenderUtils.drawRoundedOutline(extractor, resetX, btnY, RESET_BTN_WIDTH, BTN_HEIGHT, 5, resetBorder);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Reset All", resetX + RESET_BTN_WIDTH / 2, btnY + 6, resetTextCol);

        // Done Button
        boolean doneHover = hovered(doneX, btnY, DONE_BTN_WIDTH, BTN_HEIGHT, mouseX, mouseY);
        int doneBg = doneHover ? accentDark : accent;
        int doneBorder = doneHover ? 0xFFFFFFFF : accent;
        GuiRenderUtils.fillRoundedRect(extractor, doneX, btnY, DONE_BTN_WIDTH, BTN_HEIGHT, 5, doneBg);
        GuiRenderUtils.drawRoundedOutline(extractor, doneX, btnY, DONE_BTN_WIDTH, BTN_HEIGHT, 5, doneBorder);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Done", doneX + DONE_BTN_WIDTH / 2, btnY + 6, 0xFFFFFFFF);

        // Bottom Instruction Pill
        String tipText = "Drag to move • Right-click element to reset • Snaps to center & edges • Esc to save";
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
        int barY = TOOLBAR_Y;
        int btnY = barY + (TOOLBAR_HEIGHT - BTN_HEIGHT) / 2;
        int doneX = barX + TOOLBAR_WIDTH - DONE_BTN_WIDTH - 7;
        int resetX = doneX - RESET_BTN_WIDTH - 6;

        // Toolbar Buttons
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (hovered(resetX, btnY, RESET_BTN_WIDTH, BTN_HEIGHT, mouseX, mouseY)) {
                StarClient.getInstance().getHudLayout().reset();
                StarClient.getInstance().getConfigManager().saveConfig();
                return true;
            }
            if (hovered(doneX, btnY, DONE_BTN_WIDTH, BTN_HEIGHT, mouseX, mouseY)) {
                Minecraft.getInstance().gui.setScreen(parent);
                return true;
            }
        }

        // Elements interaction
        HudLayout layout = StarClient.getInstance().getHudLayout();
        List<HudLayout.Rect> rects = HudLayout.computeRects(this.font, getActiveModules(), this.width, this.height, layout);

        for (HudLayout.Rect r : rects) {
            if (!isHudElementVisible(r.id())) continue;
            if (r.contains(mouseX, mouseY)) {
                // Right-click immediately resets this individual element to its default position
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    layout.setPosition(r.id(), HudLayout.AUTO, HudLayout.AUTO);
                    StarClient.getInstance().getConfigManager().saveConfig();
                    return true;
                }

                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    // Double-click reset check
                    long now = System.currentTimeMillis();
                    if (r.id().equals(lastClickedId) && (now - lastClickTime) < 300) {
                        layout.setPosition(r.id(), HudLayout.AUTO, HudLayout.AUTO);
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
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingHudId != null) {
            int newX = (int) event.x() - this.dragOffsetX;
            int newY = (int) event.y() - this.dragOffsetY;

            // Snap to horizontal / vertical center or 8px margins
            int centerTargetX = (this.width - this.dragRectWidth) / 2;
            int centerTargetY = (this.height - this.dragRectHeight) / 2;
            this.snappedCenterX = Math.abs(newX - centerTargetX) <= SNAP_THRESHOLD;
            this.snappedCenterY = Math.abs(newY - centerTargetY) <= SNAP_THRESHOLD;

            if (this.snappedCenterX) {
                newX = centerTargetX;
            } else if (Math.abs(newX - 8) <= SNAP_THRESHOLD) {
                newX = 8;
            } else if (Math.abs((newX + this.dragRectWidth) - (this.width - 8)) <= SNAP_THRESHOLD) {
                newX = this.width - 8 - this.dragRectWidth;
            }

            if (this.snappedCenterY) {
                newY = centerTargetY;
            } else if (Math.abs(newY - 8) <= SNAP_THRESHOLD) {
                newY = 8;
            } else if (Math.abs((newY + this.dragRectHeight) - (this.height - 8)) <= SNAP_THRESHOLD) {
                newY = this.height - 8 - this.dragRectHeight;
            }

            newX = Math.clamp(newX, 0, Math.max(0, this.width - 20));
            newY = Math.clamp(newY, 0, Math.max(0, this.height - 16));
            StarClient.getInstance().getHudLayout().setPosition(this.draggingHudId, newX, newY);
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
        return module != null && module.isEnabled();
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
            case HudLayout.POTIONS -> "Potion Effects";
            case HudLayout.TARGET_HUD -> "Target HUD";
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
            case HudLayout.POTIONS -> "Speed II (01:30)";
            case HudLayout.TARGET_HUD -> "Target HUD";
            default -> id;
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
