package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.changelog.ChangelogManager;
import net.starclient.changelog.ChangelogNode;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class ChangelogScreen extends Screen {
    private static final int TARGET_WIDTH = 560;
    private static final int TARGET_HEIGHT = 390;
    private static final int HEADER_HEIGHT = 44;
    private static final int FOOTER_HEIGHT = 34;

    private final Screen parent;
    private double scroll = 0.0;
    private double targetScroll = 0.0;
    private boolean draggingScrollbar = false;

    public ChangelogScreen(Screen parent) {
        super(Component.literal("Star Client Changelog"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        ChangelogManager.getInstance().reload();
        ChangelogManager.getInstance().markCurrentVersionAsRead();
        clampScroll();
    }

    private int panelWidth() {
        return Math.min(TARGET_WIDTH, this.width - 32);
    }

    private int panelHeight() {
        return Math.min(TARGET_HEIGHT, this.height - 32);
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelY() {
        return (this.height - panelHeight()) / 2;
    }

    private int contentX() {
        return panelX() + 20;
    }

    private int contentWidth() {
        return panelWidth() - 48; // room for scrollbar
    }

    private int viewportTop() {
        return panelY() + HEADER_HEIGHT + 4;
    }

    private int viewportBottom() {
        return panelY() + panelHeight() - FOOTER_HEIGHT - 4;
    }

    private int viewportHeight() {
        return Math.max(10, viewportBottom() - viewportTop());
    }

    private int computeTotalContentHeight(List<ChangelogNode> nodes) {
        int h = 0;
        for (ChangelogNode node : nodes) {
            h += node.getHeight();
        }
        return h;
    }

    private void clampScroll() {
        int accent = getAccentColor();
        List<ChangelogNode> nodes = ChangelogManager.getInstance().getNodes(this.font, contentWidth(), accent);
        int totalH = computeTotalContentHeight(nodes);
        int maxScroll = Math.max(0, totalH - viewportHeight() + 10);
        this.targetScroll = Math.clamp(this.targetScroll, 0.0, maxScroll);
        this.scroll = Math.clamp(this.scroll, 0.0, maxScroll);
    }

    private int getAccentColor() {
        StarClient sc = StarClient.getInstance();
        if (sc != null) {
            ClientSettings cs = sc.getClientSettings();
            if (cs != null) {
                return cs.getMenuAccentColor();
            }
        }
        return 0xFF38BDF8;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        // Dim blurred backdrop
        extractor.fill(0, 0, this.width, this.height, 0xBB0B1120);

        int pX = panelX();
        int pY = panelY();
        int pW = panelWidth();
        int pH = panelHeight();
        int accent = getAccentColor();

        // Drop shadow & main glass container
        GuiRenderUtils.drawDropShadow(extractor, pX, pY, pW, pH, 10, 4, 90);
        GuiRenderUtils.fillRoundedRect(extractor, pX, pY, pW, pH, 10, 0xF20F172A);
        GuiRenderUtils.drawRoundedOutline(extractor, pX, pY, pW, pH, 10, 0x3364748B);

        // Header Background
        GuiRenderUtils.fillRoundedRect(extractor, pX + 1, pY + 1, pW - 2, HEADER_HEIGHT - 1, 8, 0x221E293B);
        extractor.fill(pX, pY + HEADER_HEIGHT, pX + pW, pY + HEADER_HEIGHT + 1, 0x2264748B);

        // Header Title
        String title = "✦ Star Client Changelog";
        GuiRenderUtils.drawText(extractor, this.font, title, pX + 18, pY + 14, accent, true);

        // Version badge pill next to title
        String verBadge = "v" + StarClient.MOD_VERSION;
        int titleW = this.font.width(title);
        int badgeX = pX + 18 + titleW + 8;
        int badgeY = pY + 13;
        int badgeW = this.font.width(verBadge) + 10;
        int badgeH = 13;
        GuiRenderUtils.fillRoundedRect(extractor, badgeX, badgeY, badgeW, badgeH, 4, 0x33000000 | (accent & 0x00FFFFFF));
        GuiRenderUtils.drawRoundedOutline(extractor, badgeX, badgeY, badgeW, badgeH, 4, 0x77000000 | (accent & 0x00FFFFFF));
        GuiRenderUtils.drawCenteredText(extractor, this.font, verBadge, badgeX + badgeW / 2, badgeY + 2, 0xFFFFFFFF, true);

        // Header Close Button '✕'
        int closeBtnX = pX + pW - 32;
        int closeBtnY = pY + 11;
        int closeBtnSize = 22;
        boolean closeHover = mouseX >= closeBtnX && mouseX <= closeBtnX + closeBtnSize && mouseY >= closeBtnY && mouseY <= closeBtnY + closeBtnSize;
        if (closeHover) {
            GuiRenderUtils.fillRoundedRect(extractor, closeBtnX, closeBtnY, closeBtnSize, closeBtnSize, 4, 0x33EF4444);
            GuiRenderUtils.drawRoundedOutline(extractor, closeBtnX, closeBtnY, closeBtnSize, closeBtnSize, 4, 0xFFEF4444);
        }
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeBtnX + closeBtnSize / 2, closeBtnY + 6, closeHover ? 0xFFEF4444 : 0xFF94A3B8);

        // Smooth scroll interpolation
        this.scroll += (this.targetScroll - this.scroll) * 0.35;
        if (Math.abs(this.targetScroll - this.scroll) < 0.2) {
            this.scroll = this.targetScroll;
        }

        // Viewport clipping & node rendering
        int vTop = viewportTop();
        int vBottom = viewportBottom();
        int vHeight = viewportHeight();
        int cX = contentX();
        int cW = contentWidth();

        List<ChangelogNode> nodes = ChangelogManager.getInstance().getNodes(this.font, cW, accent);
        int totalContentHeight = computeTotalContentHeight(nodes);

        extractor.enableScissor(pX + 8, vTop, pX + pW - 8, vBottom);

        int currentY = vTop + 2 - (int) Math.round(this.scroll);
        for (ChangelogNode node : nodes) {
            int nodeH = node.getHeight();
            if (currentY + nodeH >= vTop && currentY <= vBottom) {
                node.render(extractor, this.font, cX, currentY, cW, accent);
            }
            currentY += nodeH;
        }

        extractor.disableScissor();

        // Scrollbar
        if (totalContentHeight > vHeight) {
            int scrollTrackX = pX + pW - 14;
            int scrollTrackY = vTop;
            int scrollTrackW = 4;
            int scrollTrackH = vHeight;

            // Track
            GuiRenderUtils.fillRoundedRect(extractor, scrollTrackX, scrollTrackY, scrollTrackW, scrollTrackH, 2, 0x2264748B);

            // Thumb
            int thumbH = Math.max(22, (int) ((double) vHeight / totalContentHeight * vHeight));
            double maxScroll = Math.max(1, totalContentHeight - vHeight + 10);
            int thumbY = scrollTrackY + (int) ((this.scroll / maxScroll) * (scrollTrackH - thumbH));
            boolean thumbHover = mouseX >= scrollTrackX - 3 && mouseX <= scrollTrackX + scrollTrackW + 3 && mouseY >= thumbY && mouseY <= thumbY + thumbH;

            int thumbColor = (this.draggingScrollbar || thumbHover) ? accent : 0x8864748B;
            GuiRenderUtils.fillRoundedRect(extractor, scrollTrackX, thumbY, scrollTrackW, thumbH, 2, thumbColor);
        }

        // Footer Bar
        int footerY = pY + pH - FOOTER_HEIGHT;
        extractor.fill(pX, footerY, pX + pW, footerY + 1, 0x2264748B);
        GuiRenderUtils.fillRoundedRect(extractor, pX + 1, footerY + 1, pW - 2, FOOTER_HEIGHT - 2, 0, 0x331E293B);

        // Footer Hint Text
        GuiRenderUtils.drawText(extractor, this.font, "Scroll with mouse wheel  •  ESC to close", pX + 18, footerY + 12, 0xFF64748B);

        // Footer Close Button
        int btnW = 74;
        int btnH = 20;
        int btnX = pX + pW - 18 - btnW;
        int btnY = footerY + 7;
        boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        int btnBg = btnHover ? (0x55000000 | (accent & 0x00FFFFFF)) : 0x33334155;
        int btnOutline = btnHover ? accent : 0x4464748B;

        GuiRenderUtils.fillRoundedRect(extractor, btnX, btnY, btnW, btnH, 4, btnBg);
        GuiRenderUtils.drawRoundedOutline(extractor, btnX, btnY, btnW, btnH, 4, btnOutline);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "Close", btnX + btnW / 2, btnY + 5, btnHover ? 0xFFFFFFFF : 0xFFE2E8F0);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            int pX = panelX();
            int pY = panelY();
            int pW = panelWidth();
            int pH = panelHeight();

            // Header close '✕' button
            int closeBtnX = pX + pW - 32;
            int closeBtnY = pY + 11;
            int closeBtnSize = 22;
            if (mx >= closeBtnX && mx <= closeBtnX + closeBtnSize && my >= closeBtnY && my <= closeBtnY + closeBtnSize) {
                close();
                return true;
            }

            // Footer 'Close' button
            int btnW = 74;
            int btnH = 20;
            int btnX = pX + pW - 18 - btnW;
            int btnY = pY + pH - FOOTER_HEIGHT + 7;
            if (mx >= btnX && mx <= btnX + btnW && my >= btnY && my <= btnY + btnH) {
                close();
                return true;
            }

            // Scrollbar track click or drag
            int vTop = viewportTop();
            int vHeight = viewportHeight();
            int scrollTrackX = pX + pW - 16;
            if (mx >= scrollTrackX - 4 && mx <= scrollTrackX + 12 && my >= vTop && my <= vTop + vHeight) {
                this.draggingScrollbar = true;
                updateScrollFromMouse(my);
                return true;
            }
        }
        return super.mouseClicked(event, action);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (this.draggingScrollbar) {
            updateScrollFromMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (this.draggingScrollbar) {
            this.draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void updateScrollFromMouse(double mouseY) {
        int vTop = viewportTop();
        int vHeight = viewportHeight();
        double ratio = Math.clamp((mouseY - vTop) / (double) vHeight, 0.0, 1.0);

        int accent = getAccentColor();
        List<ChangelogNode> nodes = ChangelogManager.getInstance().getNodes(this.font, contentWidth(), accent);
        int totalH = computeTotalContentHeight(nodes);
        double maxScroll = Math.max(0, totalH - vHeight + 10);

        this.targetScroll = ratio * maxScroll;
        this.scroll = this.targetScroll;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.targetScroll -= verticalAmount * 26.0;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP) {
            this.targetScroll -= 28.0;
            clampScroll();
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN) {
            this.targetScroll += 28.0;
            clampScroll();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP) {
            this.targetScroll -= viewportHeight() * 0.75;
            clampScroll();
            return true;
        }
        if (key == GLFW.GLFW_KEY_PAGE_DOWN) {
            this.targetScroll += viewportHeight() * 0.75;
            clampScroll();
            return true;
        }
        return super.keyPressed(event);
    }

    private void close() {
        Minecraft client = Minecraft.getInstance();
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (this.parent != null) {
            client.gui.setScreen(this.parent);
        } else {
            this.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
