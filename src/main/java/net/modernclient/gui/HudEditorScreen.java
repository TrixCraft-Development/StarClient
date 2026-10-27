package net.modernclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.modernclient.ClientSettings;
import net.modernclient.ModernClient;
import net.modernclient.module.Module;
import net.modernclient.module.ModuleManager;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.stream.Collectors;

public class HudEditorScreen extends Screen {
    private static final int SNAP_THRESHOLD = 6;

    private final Screen parent;
    private String draggingHudId;
    private int dragOffsetX;
    private int dragOffsetY;
    private int dragRectWidth;
    private int dragRectHeight;
    private boolean snappedCenterX;
    private boolean snappedCenterY;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD Layout Editor"));
        this.parent = parent;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        ModernClient mod = ModernClient.getInstance();
        ClientSettings cs = mod.getClientSettings();
        int accent = cs.getMenuAccentColor();
        int bg = cs.getMenuBackgroundColor();
        int bgLight = cs.getMenuBackgroundLightColor();

        // Subtle alignment grid lines
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        extractor.fill(centerX, 0, centerX + 1, this.height, snappedCenterX ? accent : 0x3364748B);
        extractor.fill(0, centerY, this.width, centerY + 1, snappedCenterY ? accent : 0x3364748B);

        // Render HUD elements AFTER Screen's background blur so module texts are completely crisp
        HudOverlay.render(extractor);

        Minecraft client = Minecraft.getInstance();
        boolean inWorld = client.player != null && client.level != null;

        // Draw outlines around all visible HUD elements
        HudLayout layout = mod.getHudLayout();
        List<Module> activeMods = getActiveModules();
        for (HudLayout.Rect r : HudLayout.computeRects(this.font, activeMods, this.width, this.height, layout)) {
            if (!isHudElementVisible(r.id())) continue;
            if (!inWorld) {
                // Render preview card & text when editing HUD from the main menu
                extractor.fill(r.x1(), r.y1(), r.x2(), r.y2(), 0xCC0F172A);
                String preview = previewTextFor(r.id());
                extractor.text(this.font, preview, r.x1() + 6, r.y1() + 5, 0xFFF8FAFC);
            }
            boolean hovered = r.contains(mouseX, mouseY);
            boolean dragging = r.id().equals(this.draggingHudId);
            int borderColor = dragging ? accent : (hovered ? accent : 0x8838BDF8);
            drawRectOutline(extractor, r.x1(), r.y1(), r.x2(), r.y2(), borderColor);
            String label = "✥ " + r.id() + (dragging ? " (dragging)" : "");
            extractor.text(this.font, label, r.x1(), Math.max(2, r.y1() - 10), hovered || dragging ? accent : 0xFF94A3B8);
        }

        // Floating top toolbar
        int barW = 280;
        int barH = 30;
        int barX = (this.width - barW) / 2;
        int barY = 10;
        extractor.fill(barX, barY, barX + barW, barY + barH, bg);
        extractor.fill(barX, barY, barX + barW, barY + 2, accent);
        extractor.text(this.font, "✥ HUD Editor", barX + 10, barY + 11, accent);

        int resetW = 92;
        int doneW = 70;
        int resetX = barX + barW - doneW - 6 - resetW - 8;
        int doneX = barX + barW - doneW - 8;
        int btnY = barY + 6;
        int btnH = 18;

        boolean resetHover = hovered(resetX, btnY, resetW, btnH, mouseX, mouseY);
        extractor.fill(resetX, btnY, resetX + resetW, btnY + btnH, resetHover ? 0xFF475569 : bgLight);
        extractor.text(this.font, "Reset Layout", resetX + (resetW - this.font.width("Reset Layout")) / 2, btnY + 5, 0xFFE2E8F0);

        boolean doneHover = hovered(doneX, btnY, doneW, btnH, mouseX, mouseY);
        extractor.fill(doneX, btnY, doneX + doneW, btnY + btnH, doneHover ? cs.getMenuAccentDarkColor() : accent);
        extractor.text(this.font, "Done", doneX + (doneW - this.font.width("Done")) / 2, btnY + 5, 0xFFFFFFFF);
    }

    private static void drawRectOutline(GuiGraphicsExtractor extractor, int x1, int y1, int x2, int y2, int color) {
        extractor.fill(x1, y1, x2, y1 + 1, color);
        extractor.fill(x1, y2 - 1, x2, y2, color);
        extractor.fill(x1, y1, x1 + 1, y2, color);
        extractor.fill(x2 - 1, y1, x2, y2, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();

            int barW = 280;
            int barX = (this.width - barW) / 2;
            int barY = 10;
            int resetW = 92;
            int doneW = 70;
            int resetX = barX + barW - doneW - 6 - resetW - 8;
            int doneX = barX + barW - doneW - 8;
            int btnY = barY + 6;
            int btnH = 18;

            if (hovered(resetX, btnY, resetW, btnH, mouseX, mouseY)) {
                ModernClient.getInstance().getHudLayout().reset();
                ModernClient.getInstance().getConfigManager().saveConfig();
                return true;
            }
            if (hovered(doneX, btnY, doneW, btnH, mouseX, mouseY)) {
                Minecraft.getInstance().gui.setScreen(parent);
                return true;
            }

            HudLayout layout = ModernClient.getInstance().getHudLayout();
            for (HudLayout.Rect r : HudLayout.computeRects(this.font, getActiveModules(), this.width, this.height, layout)) {
                if (!isHudElementVisible(r.id())) continue;
                if (r.contains(mouseX, mouseY)) {
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
            ModernClient.getInstance().getHudLayout().setPosition(this.draggingHudId, newX, newY);
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
            ModernClient.getInstance().getConfigManager().saveConfig();
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
        return ModernClient.getInstance().getModuleManager().getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());
    }

    private static boolean isHudElementVisible(String id) {
        ModuleManager moduleManager = ModernClient.getInstance().getModuleManager();
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
