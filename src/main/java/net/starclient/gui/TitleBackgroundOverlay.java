package net.starclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class TitleBackgroundOverlay {
    private static final int BOX_Y = 8;
    private static final int BOX_H = 26;
    private static final int DROPDOWN_WIDTH = 176;
    private static final int ROW_HEIGHT = 26;

    private static final Identifier CUSTOM_WALLPAPER_ID =
            Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "textures/gui/title_background");
    private static boolean wallpaperRegistered;

    private record Star(float rx, float ry, int size, float speed, float phase, int color) {
    }

    private static final List<Star> STARS = new ArrayList<>();
    static {
        Random rand = new Random(133742L);
        int[] palette = {0xFFFFFFFF, 0xFFE0F2FE, 0xFFBAE6FD, 0xFF7DD3FC, 0xFFF8FAFC};
        for (int i = 0; i < 75; i++) {
            float rx = rand.nextFloat();
            float ry = rand.nextFloat();
            int size = rand.nextFloat() > 0.85F ? 2 : 1;
            float speed = 1.2F + rand.nextFloat() * 2.5F;
            float phase = rand.nextFloat() * ((float) Math.PI * 2.0F);
            int color = palette[rand.nextInt(palette.length)];
            STARS.add(new Star(rx, ry, size, speed, phase, color));
        }
    }

    private record BackgroundOption(String mode, String label, String icon) {}

    private static final List<BackgroundOption> OPTIONS = List.of(
            new BackgroundOption(ClientSettings.BG_MODERN, "Modern Dark", "✨"),
            new BackgroundOption(ClientSettings.BG_CUSTOM, "Custom Wallpaper", "🖼"),
            new BackgroundOption(ClientSettings.BG_VANILLA, "Vanilla Panorama", "🌐")
    );

    private boolean dropdownOpen;
    private int lastBoxX;
    private int lastBoxW;

    public boolean isDropdownOpen() {
        return dropdownOpen;
    }

    public void closeDropdown() {
        this.dropdownOpen = false;
    }

    public static void renderBackground(GuiGraphicsExtractor graphics, int width, int height, float delta, String mode) {
        if (ClientSettings.BG_CUSTOM.equals(mode)) {
            renderCustomWallpaper(graphics, width, height);
        } else if (ClientSettings.BG_MODERN.equals(mode)) {
            renderModernDark(graphics, width, height);
        }
    }

    private static void renderCustomWallpaper(GuiGraphicsExtractor graphics, int width, int height) {
        ensureWallpaperRegistered();

        // 16:9 Cover Aspect Ratio Scaling
        float texAspect = 1376.0F / 768.0F;
        float screenAspect = (float) width / (float) height;
        float u1 = 0.0F, u2 = 1.0F, v1 = 0.0F, v2 = 1.0F;

        if (screenAspect > texAspect) {
            float hRatio = texAspect / screenAspect;
            v1 = (1.0F - hRatio) / 2.0F;
            v2 = 1.0F - v1;
        } else {
            float wRatio = screenAspect / texAspect;
            u1 = (1.0F - wRatio) / 2.0F;
            u2 = 1.0F - u1;
        }

        graphics.blit(CUSTOM_WALLPAPER_ID, 0, 0, width, height, u1, u2, v1, v2);

        // Soft dark wash & subtle vignettes for UI contrast
        graphics.fill(0, 0, width, height, 0x30000000);
        GuiRenderUtils.fillGradient(graphics, 0, 0, width, 70, 0x66000000, 0x00000000);
        GuiRenderUtils.fillGradient(graphics, 0, height - 120, width, height, 0x00000000, 0x88000000);
    }

    private static void renderModernDark(GuiGraphicsExtractor graphics, int width, int height) {
        // Deep space night gradient
        GuiRenderUtils.fillGradient(graphics, 0, 0, width, height, 0xFF0B132B, 0xFF050811);

        // Twinkling stars
        renderTwinklingStars(graphics, width, height);

        // Top & bottom subtle shadows
        GuiRenderUtils.fillGradient(graphics, 0, 0, width, 70, 0x55000000, 0x00000000);
        GuiRenderUtils.fillGradient(graphics, 0, height - 110, width, height, 0x00000000, 0x66000000);
    }

    private static void renderTwinklingStars(GuiGraphicsExtractor graphics, int width, int height) {
        long now = System.currentTimeMillis();
        float t = (now % 1_000_000L) / 1000.0F;

        for (Star s : STARS) {
            int sx = (int) (s.rx * width);
            int sy = (int) (s.ry * height);
            float pulse = (float) (0.5 + 0.5 * Math.sin(t * s.speed + s.phase));
            int alpha = (int) (50 + 205 * pulse);
            int col = (alpha << 24) | (s.color & 0x00FFFFFF);

            graphics.fill(sx, sy, sx + s.size, sy + s.size, col);
            if (s.size > 1 && pulse > 0.65F) {
                int glowCol = ((alpha / 3) << 24) | (s.color & 0x00FFFFFF);
                graphics.fill(sx - 1, sy, sx + s.size + 1, sy + s.size, glowCol);
                graphics.fill(sx, sy - 1, sx + s.size, sy + s.size + 1, glowCol);
            }
        }
    }

    private static void ensureWallpaperRegistered() {
        if (wallpaperRegistered) return;
        String resourcePath = "/assets/" + StarClient.MOD_ID + "/textures/gui/title_background.png";
        try (InputStream stream = StarClient.class.getResourceAsStream(resourcePath)) {
            if (stream != null) {
                NativeImage image = NativeImage.read(stream);
                DynamicTexture texture = new DynamicTexture(() -> StarClient.MOD_ID + "_title_bg", image);
                Minecraft.getInstance().getTextureManager().register(CUSTOM_WALLPAPER_ID, texture);
                wallpaperRegistered = true;
            }
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to load title background texture", e);
        }
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        StarClient starClient = StarClient.getInstance();
        ClientSettings cs = starClient != null ? starClient.getClientSettings() : null;
        if (cs == null) return;

        int menuBg = 0xF2000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
        int menuSurfaceHover = cs.getMenuSurfaceHoverColor();
        int menuBorder = cs.getMenuBorderColor();
        int menuAccent = cs.getMenuAccentColor();

        String currentMode = cs.getTitleBackground();
        String currentLabel = toShortName(currentMode);
        String arrow = dropdownOpen ? "▴" : "▾";
        String buttonText = "🎨 " + currentLabel + " " + arrow;

        int boxW = client.font.width(buttonText) + 18;
        int boxX = graphics.guiWidth() - boxW - 8;
        this.lastBoxX = boxX;
        this.lastBoxW = boxW;

        boolean headerHovered = hovered(boxX, BOX_Y, boxW, BOX_H, mouseX, mouseY);
        int headerBg = headerHovered || dropdownOpen ? menuSurfaceHover : menuBg;
        int headerBorder = headerHovered || dropdownOpen ? menuAccent : menuBorder;

        // Header button
        GuiRenderUtils.drawDropShadow(graphics, boxX, BOX_Y, boxW, BOX_H, 6, 4, 90);
        GuiRenderUtils.fillRoundedRect(graphics, boxX, BOX_Y, boxW, BOX_H, 6, headerBg);
        GuiRenderUtils.drawRoundedOutline(graphics, boxX, BOX_Y, boxW, BOX_H, 6, headerBorder);

        int textX = boxX + 9;
        int textY = BOX_Y + (BOX_H - client.font.lineHeight) / 2 + 1;
        graphics.text(client.font, "🎨 " + currentLabel, textX, textY, 0xFFFFFFFF);
        int arrowX = boxX + boxW - client.font.width(arrow) - 9;
        graphics.text(client.font, arrow, arrowX, textY, headerHovered || dropdownOpen ? menuAccent : 0xFF94A3B8);

        // Dropdown card
        if (this.dropdownOpen) {
            int dropW = DROPDOWN_WIDTH;
            int dropX = graphics.guiWidth() - dropW - 8;
            int dropY = BOX_Y + BOX_H + 4;
            int dropH = OPTIONS.size() * ROW_HEIGHT + 8;

            GuiRenderUtils.drawDropShadow(graphics, dropX, dropY, dropW, dropH, 8, 5, 120);
            GuiRenderUtils.fillRoundedRect(graphics, dropX, dropY, dropW, dropH, 6, menuBg);
            GuiRenderUtils.drawRoundedOutline(graphics, dropX, dropY, dropW, dropH, 6, menuBorder);

            int itemY = dropY + 4;
            for (BackgroundOption opt : OPTIONS) {
                boolean active = opt.mode.equals(currentMode);
                boolean itemHovered = hovered(dropX + 4, itemY, dropW - 8, ROW_HEIGHT, mouseX, mouseY);

                if (itemHovered || active) {
                    int rowBg = itemHovered ? menuSurfaceHover : 0x2238BDF8;
                    GuiRenderUtils.fillRoundedRect(graphics, dropX + 4, itemY, dropW - 8, ROW_HEIGHT, 4, rowBg);
                    if (itemHovered) {
                        GuiRenderUtils.drawRoundedOutline(graphics, dropX + 4, itemY, dropW - 8, ROW_HEIGHT, 4, menuAccent);
                    }
                }

                int iconX = dropX + 10;
                int rowTextY = itemY + (ROW_HEIGHT - client.font.lineHeight) / 2 + 1;
                graphics.text(client.font, opt.icon, iconX, rowTextY, 0xFFFFFFFF);
                graphics.text(client.font, opt.label, iconX + 16, rowTextY, active ? menuAccent : 0xFFF1F5F9);

                if (active) {
                    graphics.text(client.font, "✓", dropX + dropW - 18, rowTextY, menuAccent);
                }

                itemY += ROW_HEIGHT;
            }
        }
    }

    public boolean mouseClicked(@NonNull MouseButtonEvent event) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        double mouseX = event.x();
        double mouseY = event.y();

        int boxX = this.lastBoxX;
        int boxW = Math.max(this.lastBoxW, 70);

        // Header click
        if (hovered(boxX, BOX_Y, boxW, BOX_H, mouseX, mouseY)) {
            this.dropdownOpen = !this.dropdownOpen;
            return true;
        }

        // Dropdown clicks
        if (this.dropdownOpen) {
            Minecraft client = Minecraft.getInstance();
            int screenW = client.getWindow().getGuiScaledWidth();
            int dropW = DROPDOWN_WIDTH;
            int dropX = screenW - dropW - 8;
            int dropY = BOX_Y + BOX_H + 4;
            int dropH = OPTIONS.size() * ROW_HEIGHT + 8;

            if (hovered(dropX, dropY, dropW, dropH, mouseX, mouseY)) {
                int itemY = dropY + 4;
                for (BackgroundOption opt : OPTIONS) {
                    if (hovered(dropX + 4, itemY, dropW - 8, ROW_HEIGHT, mouseX, mouseY)) {
                        StarClient.getInstance().getClientSettings().setTitleBackground(opt.mode);
                        StarClient.getInstance().getConfigManager().saveConfig();
                        this.dropdownOpen = false;
                        return true;
                    }
                    itemY += ROW_HEIGHT;
                }
                return true;
            }

            // Clicked outside dropdown
            this.dropdownOpen = false;
            return true;
        }

        return false;
    }

    private static String toShortName(String mode) {
        if (ClientSettings.BG_CUSTOM.equals(mode)) return "Wallpaper";
        if (ClientSettings.BG_MODERN.equals(mode)) return "Modern Dark";
        if (ClientSettings.BG_VANILLA.equals(mode)) return "Panorama";
        return mode;
    }

    private static boolean hovered(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
