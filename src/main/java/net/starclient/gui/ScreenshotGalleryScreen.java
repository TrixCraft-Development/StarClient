package net.starclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.BetterScreenshotsState;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Glassmorphic In-Game Screenshot Gallery with lazy thumbnail decoding,
 * search filtering, resolution badges, and fullscreen lightbox viewer
 * supporting Copy to Clipboard, Open File/Folder, Rename, and Delete.
 */
public class ScreenshotGalleryScreen extends Screen {
    private static final int HEADER_H = 42;
    private static final int THUMB_W = 240;
    private static final int THUMB_H = 135;
    private static final int LIGHTBOX_MAX_W = 1920;
    private static final int LIGHTBOX_MAX_H = 1080;
    private static final int MAX_CONCURRENT_DECODES = 4;

    private static final AtomicInteger TEX_SEQ = new AtomicInteger(1);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("MMM dd, HH:mm", Locale.ROOT);
    private static final SimpleDateFormat FULL_DATE_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private static final class ScreenshotEntry {
        File file;
        String name;
        long lastModified;
        long fileSize;
        volatile int origWidth;
        volatile int origHeight;
        volatile boolean loadQueued;
        volatile boolean loadFailed;
        volatile NativeImage pendingThumb;
        Identifier thumbId;
        DynamicTexture thumbTex;

        ScreenshotEntry(File file) {
            this.file = file;
            this.name = file.getName();
            this.lastModified = file.lastModified();
            this.fileSize = file.length();
        }
    }

    private final Screen parent;
    private final List<ScreenshotEntry> allEntries = new ArrayList<>();
    private final List<ScreenshotEntry> filteredEntries = new ArrayList<>();
    private final AtomicInteger inFlightDecodes = new AtomicInteger(0);
    private final AtomicInteger loadGeneration = new AtomicInteger(0);

    private volatile boolean closed = false;
    private EditBox searchBox;
    private String searchQuery = "";
    private double gridScroll = 0.0;
    private double targetGridScroll = 0.0;
    private long lastFrameNanos = System.nanoTime();

    // Lightbox state
    private int selectedIndex = -1;
    private ScreenshotEntry lightboxTarget;
    private volatile NativeImage pendingLightboxImage;
    private volatile ScreenshotEntry pendingLightboxOwner;
    private Identifier lightboxTexId;
    private DynamicTexture lightboxTex;
    private int lightboxTexW;
    private int lightboxTexH;
    private boolean lightboxLoading;

    // Rename & Delete state
    private boolean renaming = false;
    private EditBox renameBox;
    private long deleteConfirmUntilMs = 0L;
    private String statusText = "";
    private int statusColor = 0xFF94A3B8;
    private long statusExpireMs = 0L;

    public ScreenshotGalleryScreen(Screen parent) {
        super(Component.literal("Screenshot Gallery"));
        this.parent = parent;
    }

    private File getScreenshotsDir() {
        return new File(Minecraft.getInstance().gameDirectory, "screenshots");
    }

    @Override
    protected void init() {
        super.init();
        this.closed = false;
        this.lastFrameNanos = System.nanoTime();

        String prevSearch = this.searchQuery;
        this.searchBox = new EditBox(this.font, 0, 0, 140, 14, Component.literal(""));
        this.searchBox.setMaxLength(64);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFF8FAFC);
        this.searchBox.setHint(Component.literal("Filter screenshots..."));
        this.searchBox.setValue(prevSearch);
        this.searchBox.setResponder(v -> {
            this.searchQuery = v;
            applyFilter();
        });
        addWidget(this.searchBox);

        this.renameBox = new EditBox(this.font, 0, 0, 200, 14, Component.literal(""));
        this.renameBox.setMaxLength(96);
        this.renameBox.setBordered(false);
        this.renameBox.setTextColor(0xFFF8FAFC);
        this.renameBox.setVisible(this.renaming);
        addWidget(this.renameBox);

        if (this.allEntries.isEmpty()) {
            refreshScreenshots();
        } else {
            applyFilter();
        }
    }

    private void refreshScreenshots() {
        releaseAllTextures();
        this.loadGeneration.incrementAndGet();
        this.allEntries.clear();
        this.filteredEntries.clear();
        this.selectedIndex = -1;
        this.renaming = false;
        this.deleteConfirmUntilMs = 0L;
        this.gridScroll = 0.0;
        this.targetGridScroll = 0.0;

        File dir = getScreenshotsDir();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
                for (File f : files) {
                    if (f.isFile()) {
                        this.allEntries.add(new ScreenshotEntry(f));
                    }
                }
            }
        }
        applyFilter();
    }

    private void applyFilter() {
        this.filteredEntries.clear();
        String q = this.searchQuery == null ? "" : this.searchQuery.trim().toLowerCase(Locale.ROOT);
        for (ScreenshotEntry entry : this.allEntries) {
            if (q.isEmpty() || entry.name.toLowerCase(Locale.ROOT).contains(q)) {
                this.filteredEntries.add(entry);
            }
        }
        this.targetGridScroll = 0.0;
        this.gridScroll = 0.0;
        if (this.selectedIndex >= this.filteredEntries.size()) {
            closeLightbox();
        }
    }

    private void queueThumbDecode(ScreenshotEntry entry) {
        if (entry.loadQueued || entry.thumbId != null || entry.loadFailed) return;
        if (inFlightDecodes.get() >= MAX_CONCURRENT_DECODES) return;
        entry.loadQueued = true;
        final int gen = this.loadGeneration.get();
        inFlightDecodes.incrementAndGet();

        Util.ioPool().execute(() -> {
            try {
                if (closed || loadGeneration.get() != gen || !entry.file.isFile()) {
                    entry.loadFailed = true;
                    return;
                }
                try (InputStream in = Files.newInputStream(entry.file.toPath());
                     NativeImage src = NativeImage.read(in)) {
                    int sw = src.getWidth();
                    int sh = src.getHeight();
                    entry.origWidth = sw;
                    entry.origHeight = sh;
                    if (sw <= 0 || sh <= 0) {
                        entry.loadFailed = true;
                        return;
                    }
                    double scale = Math.min((double) THUMB_W / sw, (double) THUMB_H / sh);
                    int tw = Math.max(2, Math.min(THUMB_W, (int) Math.round(sw * scale)));
                    int th = Math.max(2, Math.min(THUMB_H, (int) Math.round(sh * scale)));
                    NativeImage thumb = new NativeImage(src.format(), tw, th, false);
                    src.resizeSubRectTo(0, 0, sw, sh, thumb);

                    if (closed || loadGeneration.get() != gen) {
                        thumb.close();
                        return;
                    }
                    entry.pendingThumb = thumb;
                }
            } catch (Throwable t) {
                entry.loadFailed = true;
            } finally {
                inFlightDecodes.decrementAndGet();
            }
        });
    }

    private void uploadReadyTextures() {
        Minecraft client = Minecraft.getInstance();
        int uploaded = 0;
        for (ScreenshotEntry entry : this.filteredEntries) {
            if (uploaded >= 2) break;
            NativeImage ready = entry.pendingThumb;
            if (ready != null) {
                entry.pendingThumb = null;
                if (!ready.isClosed()) {
                    try {
                        Identifier id = Identifier.fromNamespaceAndPath(StarClient.MOD_ID,
                                "screenshot_thumb_" + TEX_SEQ.getAndIncrement());
                        DynamicTexture tex = new DynamicTexture(() -> "starclient_ss_thumb", ready);
                        client.getTextureManager().register(id, tex);
                        entry.thumbId = id;
                        entry.thumbTex = tex;
                        uploaded++;
                    } catch (Throwable t) {
                        ready.close();
                        entry.loadFailed = true;
                    }
                }
            }
        }

        // Upload pending lightbox full-res texture
        NativeImage lbReady = this.pendingLightboxImage;
        ScreenshotEntry lbOwner = this.pendingLightboxOwner;
        if (lbReady != null) {
            this.pendingLightboxImage = null;
            this.pendingLightboxOwner = null;
            if (lbOwner == this.lightboxTarget && this.selectedIndex >= 0 && !lbReady.isClosed()) {
                releaseLightboxTexture();
                try {
                    this.lightboxTexW = lbReady.getWidth();
                    this.lightboxTexH = lbReady.getHeight();
                    Identifier id = Identifier.fromNamespaceAndPath(StarClient.MOD_ID,
                            "screenshot_full_" + TEX_SEQ.getAndIncrement());
                    DynamicTexture tex = new DynamicTexture(() -> "starclient_ss_full", lbReady);
                    client.getTextureManager().register(id, tex);
                    this.lightboxTexId = id;
                    this.lightboxTex = tex;
                } catch (Throwable t) {
                    lbReady.close();
                }
            } else if (!lbReady.isClosed()) {
                lbReady.close();
            }
            this.lightboxLoading = false;
        }
    }

    private void openLightbox(int index) {
        if (index < 0 || index >= this.filteredEntries.size()) return;
        this.selectedIndex = index;
        this.renaming = false;
        if (this.renameBox != null) {
            this.renameBox.setVisible(false);
            this.renameBox.setFocused(false);
        }
        if (this.searchBox != null) {
            this.searchBox.setFocused(false);
        }
        this.deleteConfirmUntilMs = 0L;

        ScreenshotEntry entry = this.filteredEntries.get(index);
        this.lightboxTarget = entry;
        releaseLightboxTexture();
        this.lightboxLoading = true;
        final int gen = this.loadGeneration.get();

        Util.ioPool().execute(() -> {
            try {
                if (closed || loadGeneration.get() != gen || this.lightboxTarget != entry || !entry.file.isFile()) {
                    return;
                }
                try (InputStream in = Files.newInputStream(entry.file.toPath());
                     NativeImage src = NativeImage.read(in)) {
                    int sw = src.getWidth();
                    int sh = src.getHeight();
                    entry.origWidth = sw;
                    entry.origHeight = sh;

                    NativeImage out;
                    if (sw > LIGHTBOX_MAX_W || sh > LIGHTBOX_MAX_H) {
                        double scale = Math.min((double) LIGHTBOX_MAX_W / sw, (double) LIGHTBOX_MAX_H / sh);
                        int nw = Math.max(2, (int) Math.round(sw * scale));
                        int nh = Math.max(2, (int) Math.round(sh * scale));
                        out = new NativeImage(src.format(), nw, nh, false);
                        src.resizeSubRectTo(0, 0, sw, sh, out);
                    } else {
                        out = new NativeImage(src.format(), sw, sh, false);
                        out.copyFrom(src);
                    }

                    if (closed || loadGeneration.get() != gen || this.lightboxTarget != entry) {
                        out.close();
                        return;
                    }
                    NativeImage old = this.pendingLightboxImage;
                    this.pendingLightboxOwner = entry;
                    this.pendingLightboxImage = out;
                    if (old != null && !old.isClosed()) {
                        old.close();
                    }
                }
            } catch (Throwable t) {
                this.lightboxLoading = false;
            }
        });
    }

    private void closeLightbox() {
        this.selectedIndex = -1;
        this.lightboxTarget = null;
        this.lightboxLoading = false;
        this.renaming = false;
        this.deleteConfirmUntilMs = 0L;
        if (this.renameBox != null) {
            this.renameBox.setVisible(false);
            this.renameBox.setFocused(false);
        }
        releaseLightboxTexture();
    }

    private void releaseLightboxTexture() {
        NativeImage pend = this.pendingLightboxImage;
        this.pendingLightboxImage = null;
        this.pendingLightboxOwner = null;
        if (pend != null && !pend.isClosed()) {
            pend.close();
        }
        if (this.lightboxTexId != null) {
            Minecraft.getInstance().getTextureManager().release(this.lightboxTexId);
            this.lightboxTexId = null;
        }
        if (this.lightboxTex != null) {
            this.lightboxTex.close();
            this.lightboxTex = null;
        }
    }

    private void releaseAllTextures() {
        releaseLightboxTexture();
        Minecraft client = Minecraft.getInstance();
        for (ScreenshotEntry entry : this.allEntries) {
            NativeImage p = entry.pendingThumb;
            entry.pendingThumb = null;
            if (p != null && !p.isClosed()) {
                p.close();
            }
            if (entry.thumbId != null) {
                client.getTextureManager().release(entry.thumbId);
                entry.thumbId = null;
            }
            if (entry.thumbTex != null) {
                entry.thumbTex.close();
                entry.thumbTex = null;
            }
        }
    }

    private void setStatus(String text, int color) {
        this.statusText = text;
        this.statusColor = color;
        this.statusExpireMs = System.currentTimeMillis() + 3500L;
    }

    private int panelW() {
        return Math.clamp(this.width - 36, 480, 760);
    }

    private int panelH() {
        return Math.clamp(this.height - 32, 310, 480);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        uploadReadyTextures();

        long now = System.nanoTime();
        float dt = Math.clamp((now - lastFrameNanos) / 1_000_000_000.0F, 0.001F, 0.1F);
        lastFrameNanos = now;
        this.gridScroll += (this.targetGridScroll - this.gridScroll) * Math.min(1.0F, 16.0F * dt);
        if (Math.abs(this.targetGridScroll - this.gridScroll) < 0.25) {
            this.gridScroll = this.targetGridScroll;
        }

        if (this.deleteConfirmUntilMs > 0L && System.currentTimeMillis() > this.deleteConfirmUntilMs) {
            this.deleteConfirmUntilMs = 0L;
        }

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs.getMenuAccentColor();
        int accentDark = cs.getMenuAccentDarkColor();
        int bg = 0xF4000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF);
        int bgDark = cs.getMenuBackgroundDarkColor();
        int surface = cs.getMenuSurfaceColor();
        int surfaceHover = cs.getMenuSurfaceHoverColor();
        int border = cs.getMenuBorderColor();

        int pw = panelW();
        int ph = panelH();
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;

        // Subtle backdrop dim
        extractor.fill(0, 0, this.width, this.height, 0x88040812);

        // Main panel
        GuiRenderUtils.drawDropShadow(extractor, px, py, pw, ph, 8, 8, 145);
        GuiRenderUtils.fillRoundedRect(extractor, px, py, pw, ph, 8, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, px, py, pw, ph, 8, border);

        // Header
        GuiRenderUtils.fillRoundedRect(extractor, px, py, pw, HEADER_H, 8, surface);
        extractor.fill(px, py + HEADER_H - 1, px + pw, py + HEADER_H, border);

        GuiRenderUtils.drawText(extractor, this.font, "📷 Screenshot Gallery", px + 14, py + 10, accent, true);
        String countSub = this.filteredEntries.size() + " of " + this.allEntries.size()
                + (this.allEntries.size() == 1 ? " screenshot" : " screenshots");
        GuiRenderUtils.drawText(extractor, this.font, countSub, px + 14, py + 24, 0xFF94A3B8);

        // Header controls (right-to-left)
        int btnY = py + 11;
        int btnH = 20;

        int closeX = px + pw - 30;
        boolean closeHov = this.selectedIndex < 0 && isHovered(mouseX, mouseY, closeX, btnY, 20, btnH);
        GuiRenderUtils.fillRoundedRect(extractor, closeX, btnY, 20, btnH, 5, closeHov ? 0x44EF4444 : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, closeX, btnY, 20, btnH, 5, closeHov ? 0xFFEF4444 : border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + 10, btnY + 6, closeHov ? 0xFFF87171 : 0xFF94A3B8);

        int folderW = 68;
        int folderX = closeX - folderW - 6;
        boolean folderHov = this.selectedIndex < 0 && isHovered(mouseX, mouseY, folderX, btnY, folderW, btnH);
        GuiRenderUtils.fillRoundedRect(extractor, folderX, btnY, folderW, btnH, 5, folderHov ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, folderX, btnY, folderW, btnH, 5, folderHov ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "📂 Folder", folderX + folderW / 2, btnY + 6,
                folderHov ? 0xFFFFFFFF : 0xFFE2E8F0);

        int refreshW = 24;
        int refreshX = folderX - refreshW - 6;
        boolean refreshHov = this.selectedIndex < 0 && isHovered(mouseX, mouseY, refreshX, btnY, refreshW, btnH);
        GuiRenderUtils.fillRoundedRect(extractor, refreshX, btnY, refreshW, btnH, 5, refreshHov ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, refreshX, btnY, refreshW, btnH, 5, refreshHov ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "↻", refreshX + refreshW / 2, btnY + 6,
                refreshHov ? accent : 0xFFE2E8F0);

        int searchW = Math.min(165, Math.max(96, refreshX - (px + 150) - 10));
        int searchX = refreshX - searchW - 8;
        boolean searchFoc = this.searchBox != null && this.searchBox.isFocused();
        GuiRenderUtils.fillRoundedRect(extractor, searchX, btnY, searchW, btnH, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, searchX, btnY, searchW, btnH, 5, searchFoc ? accent : border);
        GuiRenderUtils.drawText(extractor, this.font, "🔍", searchX + 6, btnY + 6, searchFoc ? accent : 0xFF64748B);
        if (this.searchBox != null) {
            this.searchBox.setPosition(searchX + 18, btnY + 6);
            this.searchBox.setWidth(searchW - 24);
            this.searchBox.setVisible(this.selectedIndex < 0);
            if (this.selectedIndex < 0) {
                this.searchBox.extractRenderState(extractor, mouseX, mouseY, delta);
            }
        }

        // Grid area
        int gridLeft = px + 14;
        int gridRight = px + pw - 14;
        int gridTop = py + HEADER_H + 10;
        int gridBottom = py + ph - 12;
        int gridW = gridRight - gridLeft;
        int gridH = gridBottom - gridTop;

        int cols = pw >= 660 ? 4 : 3;
        int gap = 10;
        int cardW = (gridW - gap * (cols - 1)) / cols;
        int thumbDrawH = Math.max(54, (int) Math.round((cardW - 8) * 9.0 / 16.0));
        int cardH = thumbDrawH + 34;

        int rows = (this.filteredEntries.size() + cols - 1) / cols;
        int totalH = Math.max(0, rows * (cardH + gap) - gap);
        int maxScroll = Math.max(0, totalH - gridH);
        this.targetGridScroll = Math.clamp(this.targetGridScroll, 0.0, maxScroll);
        this.gridScroll = Math.clamp(this.gridScroll, 0.0, maxScroll);

        extractor.enableScissor(gridLeft - 2, gridTop, gridRight + 2, gridBottom);
        if (this.filteredEntries.isEmpty()) {
            String emptyMsg = this.allEntries.isEmpty()
                    ? "No screenshots found yet — press F2 in-game to capture one!"
                    : "No screenshots match \"" + this.searchQuery.trim() + "\"";
            GuiRenderUtils.drawCenteredText(extractor, this.font, emptyMsg,
                    px + pw / 2, gridTop + gridH / 2 - 4, 0xFF64748B);
        } else {
            for (int i = 0; i < this.filteredEntries.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int cx = gridLeft + col * (cardW + gap);
                int cy = (int) Math.round(gridTop + row * (cardH + gap) - this.gridScroll);

                // Queue thumbnails lazily when within 1 row of viewport
                if (cy + cardH >= gridTop - cardH && cy <= gridBottom + cardH) {
                    queueThumbDecode(this.filteredEntries.get(i));
                }
                if (cy + cardH < gridTop || cy > gridBottom) continue;

                ScreenshotEntry entry = this.filteredEntries.get(i);
                boolean cardHov = this.selectedIndex < 0 && isHovered(mouseX, mouseY, cx, cy, cardW, cardH)
                        && mouseY >= gridTop && mouseY <= gridBottom;

                GuiRenderUtils.fillRoundedRect(extractor, cx, cy, cardW, cardH, 6, cardHov ? surfaceHover : surface);
                GuiRenderUtils.drawRoundedOutline(extractor, cx, cy, cardW, cardH, 6, cardHov ? accent : border);

                int tx = cx + 4;
                int ty = cy + 4;
                int tw = cardW - 8;
                int th = thumbDrawH;

                GuiRenderUtils.fillRoundedRect(extractor, tx, ty, tw, th, 4, bgDark);
                if (entry.thumbId != null) {
                    extractor.blit(entry.thumbId, tx, ty, tx + tw, ty + th, 0.0F, 1.0F, 0.0F, 1.0F);
                } else if (entry.loadFailed) {
                    GuiRenderUtils.drawCenteredText(extractor, this.font, "⚠ Broken PNG", tx + tw / 2, ty + th / 2 - 4, 0xFFF87171);
                } else {
                    float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 240.0 + i * 0.7));
                    int shimmer = GuiRenderUtils.lerpColor(0xFF1E293B, 0xFF334155, pulse);
                    GuiRenderUtils.fillRoundedRect(extractor, tx + 2, ty + 2, tw - 4, th - 4, 3, shimmer);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, "Loading...", tx + tw / 2, ty + th / 2 - 4, 0xFF64748B);
                }

                // Resolution badge in top-right of thumbnail
                if (entry.origWidth > 0 && entry.origHeight > 0) {
                    String badge = BetterScreenshotsState.formatResolutionBadge(entry.origWidth, entry.origHeight);
                    int bw = this.font.width(badge) + 8;
                    int bh = 12;
                    int bx = tx + tw - bw - 3;
                    int by = ty + 3;
                    int badgeColor = ("4K".equals(badge) || "8K".equals(badge)) ? 0xFFFBBF24 : accent;
                    GuiRenderUtils.fillRoundedRect(extractor, bx, by, bw, bh, 3, 0xCC090D16);
                    GuiRenderUtils.drawRoundedOutline(extractor, bx, by, bw, bh, 3, GuiRenderUtils.withAlpha(badgeColor, 180));
                    GuiRenderUtils.drawCenteredText(extractor, this.font, badge, bx + bw / 2, by + 2, badgeColor);
                }

                // Filename & Metadata
                int metaY = ty + th + 5;
                String shortName = GuiRenderUtils.truncate(this.font, entry.name, tw);
                GuiRenderUtils.drawText(extractor, this.font, shortName, tx + 1, metaY,
                        cardHov ? 0xFFFFFFFF : 0xFFE2E8F0);
                String sub = DATE_FMT.format(new Date(entry.lastModified)) + " • " + formatFileSize(entry.fileSize);
                GuiRenderUtils.drawText(extractor, this.font, GuiRenderUtils.truncate(this.font, sub, tw),
                        tx + 1, metaY + 11, 0xFF94A3B8);
            }
        }
        extractor.disableScissor();

        // Scrollbar indicator
        if (maxScroll > 0) {
            int sbX = px + pw - 7;
            int thumbHeight = Math.max(24, (int) ((double) gridH * gridH / Math.max(1, totalH)));
            int thumbY = gridTop + (int) ((gridH - thumbHeight) * (this.gridScroll / maxScroll));
            GuiRenderUtils.fillRoundedRect(extractor, sbX, gridTop, 3, gridH, 1, 0x33FFFFFF);
            GuiRenderUtils.fillRoundedRect(extractor, sbX, thumbY, 3, thumbHeight, 1, accent);
        }

        // Lightbox Overlay
        if (this.selectedIndex >= 0 && this.selectedIndex < this.filteredEntries.size()) {
            renderLightbox(extractor, mouseX, mouseY, delta, accent, accentDark, bg, bgDark, surface, surfaceHover, border);
        }
    }

    private void renderLightbox(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta,
                                int accent, int accentDark, int bg, int bgDark, int surface, int surfaceHover, int border) {
        ScreenshotEntry entry = this.filteredEntries.get(this.selectedIndex);
        extractor.nextStratum();
        extractor.fill(0, 0, this.width, this.height, 0xCC030712);

        int lw = Math.clamp(this.width - 40, 440, 820);
        int lh = Math.clamp(this.height - 32, 290, 500);
        int lx = (this.width - lw) / 2;
        int ly = (this.height - lh) / 2;

        GuiRenderUtils.drawDropShadow(extractor, lx, ly, lw, lh, 8, 8, 170);
        GuiRenderUtils.fillRoundedRect(extractor, lx, ly, lw, lh, 8, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, lx, ly, lw, lh, 8, border);

        // Top bar
        int topH = 36;
        GuiRenderUtils.fillRoundedRect(extractor, lx, ly, lw, topH, 8, surface);
        extractor.fill(lx, ly + topH - 1, lx + lw, ly + topH, border);

        String titleStr = GuiRenderUtils.truncate(this.font, entry.name, lw - 210);
        GuiRenderUtils.drawText(extractor, this.font, titleStr, lx + 14, ly + 8, 0xFFFFFFFF, true);

        String dimsStr = entry.origWidth > 0
                ? entry.origWidth + "×" + entry.origHeight + " (" + BetterScreenshotsState.formatResolutionBadge(entry.origWidth, entry.origHeight) + ")"
                : "Loading size...";
        String metaStr = dimsStr + "  •  " + formatFileSize(entry.fileSize) + "  •  " + FULL_DATE_FMT.format(new Date(entry.lastModified));
        GuiRenderUtils.drawText(extractor, this.font, GuiRenderUtils.truncate(this.font, metaStr, lw - 140), lx + 14, ly + 21, 0xFF94A3B8);

        // Index pill & Close button
        String idxLabel = (this.selectedIndex + 1) + " / " + this.filteredEntries.size();
        int idxW = this.font.width(idxLabel) + 12;
        int backBtnW = 62;
        int backBtnX = lx + lw - backBtnW - 10;
        int topBtnY = ly + 8;
        boolean backHov = isHovered(mouseX, mouseY, backBtnX, topBtnY, backBtnW, 20);
        GuiRenderUtils.fillRoundedRect(extractor, backBtnX, topBtnY, backBtnW, 20, 5, backHov ? 0x44EF4444 : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, backBtnX, topBtnY, backBtnW, 20, 5, backHov ? 0xFFEF4444 : border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕ Grid", backBtnX + backBtnW / 2, topBtnY + 6,
                backHov ? 0xFFF87171 : 0xFFE2E8F0);

        int idxX = backBtnX - idxW - 8;
        GuiRenderUtils.fillRoundedRect(extractor, idxX, topBtnY + 2, idxW, 16, 4, bgDark);
        GuiRenderUtils.drawCenteredText(extractor, this.font, idxLabel, idxX + idxW / 2, topBtnY + 6, accent);

        // Image Viewport
        int footH = 44;
        int vpX = lx + 42;
        int vpY = ly + topH + 10;
        int vpW = lw - 84;
        int vpH = lh - topH - footH - 20;
        GuiRenderUtils.fillRoundedRect(extractor, vpX, vpY, vpW, vpH, 6, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, vpX, vpY, vpW, vpH, 6, border);

        Identifier displayId = this.lightboxTexId != null ? this.lightboxTexId : entry.thumbId;
        int imgW = this.lightboxTexId != null ? this.lightboxTexW : Math.max(1, entry.origWidth);
        int imgH = this.lightboxTexId != null ? this.lightboxTexH : Math.max(1, entry.origHeight);
        if (imgW <= 0 || imgH <= 0) {
            imgW = 16;
            imgH = 9;
        }

        if (displayId != null) {
            double scale = Math.min((double) (vpW - 8) / imgW, (double) (vpH - 8) / imgH);
            int dw = Math.max(2, (int) Math.round(imgW * scale));
            int dh = Math.max(2, (int) Math.round(imgH * scale));
            int dx = vpX + (vpW - dw) / 2;
            int dy = vpY + (vpH - dh) / 2;
            extractor.blit(displayId, dx, dy, dx + dw, dy + dh, 0.0F, 1.0F, 0.0F, 1.0F);
        } else if (this.lightboxLoading) {
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Loading full resolution preview...",
                    vpX + vpW / 2, vpY + vpH / 2 - 4, 0xFF94A3B8);
        }

        // Prev / Next arrows
        int arrowW = 26;
        int arrowH = 44;
        int arrowY = vpY + (vpH - arrowH) / 2;
        boolean hasPrev = this.selectedIndex > 0;
        boolean hasNext = this.selectedIndex < this.filteredEntries.size() - 1;

        int prevX = lx + 8;
        boolean prevHov = hasPrev && isHovered(mouseX, mouseY, prevX, arrowY, arrowW, arrowH);
        GuiRenderUtils.fillRoundedRect(extractor, prevX, arrowY, arrowW, arrowH, 6, prevHov ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, prevX, arrowY, arrowW, arrowH, 6, prevHov ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "◀", prevX + arrowW / 2, arrowY + 18,
                hasPrev ? (prevHov ? accent : 0xFFFFFFFF) : 0xFF475569);

        int nextX = lx + lw - arrowW - 8;
        boolean nextHov = hasNext && isHovered(mouseX, mouseY, nextX, arrowY, arrowW, arrowH);
        GuiRenderUtils.fillRoundedRect(extractor, nextX, arrowY, arrowW, arrowH, 6, nextHov ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, nextX, arrowY, arrowW, arrowH, 6, nextHov ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, this.font, "▶", nextX + arrowW / 2, arrowY + 18,
                hasNext ? (nextHov ? accent : 0xFFFFFFFF) : 0xFF475569);

        // Footer bar
        int footY = ly + lh - footH;
        GuiRenderUtils.fillRoundedRect(extractor, lx, footY, lw, footH, 8, surface);
        extractor.fill(lx, footY, lx + lw, footY + 1, border);

        int actY = footY + 11;
        int actH = 22;

        if (this.renaming) {
            int rbX = lx + 14;
            int rbW = lw - 184;
            GuiRenderUtils.fillRoundedRect(extractor, rbX, actY, rbW, actH, 5, bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, rbX, actY, rbW, actH, 5, accent);
            if (this.renameBox != null) {
                this.renameBox.setVisible(true);
                this.renameBox.setPosition(rbX + 6, actY + 7);
                this.renameBox.setWidth(rbW - 12);
                this.renameBox.extractRenderState(extractor, mouseX, mouseY, delta);
            }

            int saveX = rbX + rbW + 8;
            int saveW = 72;
            boolean saveHov = isHovered(mouseX, mouseY, saveX, actY, saveW, actH);
            GuiRenderUtils.fillRoundedRect(extractor, saveX, actY, saveW, actH, 5, saveHov ? accentDark : accent);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "✓ Rename", saveX + saveW / 2, actY + 7, 0xFFFFFFFF);

            int cancelX = saveX + saveW + 6;
            int cancelW = 68;
            boolean cancelHov = isHovered(mouseX, mouseY, cancelX, actY, cancelW, actH);
            GuiRenderUtils.fillRoundedRect(extractor, cancelX, actY, cancelW, actH, 5, cancelHov ? surfaceHover : bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, cancelX, actY, cancelW, actH, 5, border);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Cancel", cancelX + cancelW / 2, actY + 7, 0xFFE2E8F0);
        } else {
            int curX = lx + 14;
            // Copy to Clipboard
            int copyW = 94;
            boolean copyHov = isHovered(mouseX, mouseY, curX, actY, copyW, actH);
            GuiRenderUtils.fillRoundedRect(extractor, curX, actY, copyW, actH, 5, copyHov ? accentDark : accent);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "📋 Copy Image", curX + copyW / 2, actY + 7, 0xFFFFFFFF);
            curX += copyW + 6;

            // Open File
            int openW = 78;
            boolean openHov = isHovered(mouseX, mouseY, curX, actY, openW, actH);
            GuiRenderUtils.fillRoundedRect(extractor, curX, actY, openW, actH, 5, openHov ? surfaceHover : bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, curX, actY, openW, actH, 5, openHov ? accent : border);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "📂 Open File", curX + openW / 2, actY + 7, 0xFFE2E8F0);
            curX += openW + 6;

            // Rename
            int renW = 70;
            boolean renHov = isHovered(mouseX, mouseY, curX, actY, renW, actH);
            GuiRenderUtils.fillRoundedRect(extractor, curX, actY, renW, actH, 5, renHov ? surfaceHover : bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, curX, actY, renW, actH, 5, renHov ? accent : border);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "✏ Rename", curX + renW / 2, actY + 7, 0xFFE2E8F0);
            curX += renW + 6;

            // Delete (two-step confirmation)
            boolean confirmingDel = System.currentTimeMillis() < this.deleteConfirmUntilMs;
            String delLabel = confirmingDel ? "⚠ Confirm?" : "🗑 Delete";
            int delW = confirmingDel ? 84 : 70;
            boolean delHov = isHovered(mouseX, mouseY, curX, actY, delW, actH);
            int delBg = confirmingDel ? 0xFFDC2626 : (delHov ? 0x44EF4444 : bgDark);
            int delBorder = confirmingDel || delHov ? 0xFFEF4444 : border;
            GuiRenderUtils.fillRoundedRect(extractor, curX, actY, delW, actH, 5, delBg);
            GuiRenderUtils.drawRoundedOutline(extractor, curX, actY, delW, actH, 5, delBorder);
            GuiRenderUtils.drawCenteredText(extractor, this.font, delLabel, curX + delW / 2, actY + 7,
                    confirmingDel ? 0xFFFFFFFF : (delHov ? 0xFFF87171 : 0xFFE2E8F0));

            // Status text on right side of footer
            if (!this.statusText.isEmpty() && System.currentTimeMillis() < this.statusExpireMs) {
                int sw = this.font.width(this.statusText);
                int avail = (lx + lw - 14) - (curX + delW + 10);
                if (avail > 40) {
                    String clipped = GuiRenderUtils.truncate(this.font, this.statusText, avail);
                    int cw = this.font.width(clipped);
                    GuiRenderUtils.drawText(extractor, this.font, clipped, lx + lw - 14 - cw, actY + 7, this.statusColor);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = event.x();
        double my = event.y();

        if (this.selectedIndex >= 0 && this.selectedIndex < this.filteredEntries.size()) {
            return handleLightboxClick(mx, my, event, doubleClick);
        }
        return handleGridClick(mx, my, event, doubleClick);
    }

    private boolean handleGridClick(double mx, double my, MouseButtonEvent event, boolean doubleClick) {
        int pw = panelW();
        int ph = panelH();
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        int btnY = py + 11;
        int btnH = 20;

        int closeX = px + pw - 30;
        if (isHovered(mx, my, closeX, btnY, 20, btnH)) {
            playClick();
            onClose();
            return true;
        }

        int folderW = 68;
        int folderX = closeX - folderW - 6;
        if (isHovered(mx, my, folderX, btnY, folderW, btnH)) {
            playClick();
            File dir = getScreenshotsDir();
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            Util.getPlatform().openFile(dir);
            return true;
        }

        int refreshW = 24;
        int refreshX = folderX - refreshW - 6;
        if (isHovered(mx, my, refreshX, btnY, refreshW, btnH)) {
            playClick();
            refreshScreenshots();
            return true;
        }

        int searchW = Math.min(165, Math.max(96, refreshX - (px + 150) - 10));
        int searchX = refreshX - searchW - 8;
        if (this.searchBox != null && isHovered(mx, my, searchX, btnY, searchW, btnH)) {
            this.searchBox.setFocused(true);
            this.setFocused(this.searchBox);
            this.searchBox.mouseClicked(event, doubleClick);
            return true;
        }
        if (this.searchBox != null) {
            this.searchBox.setFocused(false);
        }

        int gridLeft = px + 14;
        int gridRight = px + pw - 14;
        int gridTop = py + HEADER_H + 10;
        int gridBottom = py + ph - 12;
        if (mx >= gridLeft && mx <= gridRight && my >= gridTop && my <= gridBottom) {
            int cols = pw >= 660 ? 4 : 3;
            int gap = 10;
            int gridW = gridRight - gridLeft;
            int cardW = (gridW - gap * (cols - 1)) / cols;
            int thumbDrawH = Math.max(54, (int) Math.round((cardW - 8) * 9.0 / 16.0));
            int cardH = thumbDrawH + 34;

            for (int i = 0; i < this.filteredEntries.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int cx = gridLeft + col * (cardW + gap);
                int cy = (int) Math.round(gridTop + row * (cardH + gap) - this.gridScroll);
                if (isHovered(mx, my, cx, cy, cardW, cardH)) {
                    playClick();
                    openLightbox(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleLightboxClick(double mx, double my, MouseButtonEvent event, boolean doubleClick) {
        ScreenshotEntry entry = this.filteredEntries.get(this.selectedIndex);
        int lw = Math.clamp(this.width - 40, 440, 820);
        int lh = Math.clamp(this.height - 32, 290, 500);
        int lx = (this.width - lw) / 2;
        int ly = (this.height - lh) / 2;

        // Click outside closes lightbox
        if (!isHovered(mx, my, lx, ly, lw, lh)) {
            playClick();
            closeLightbox();
            return true;
        }

        int topH = 36;
        int backBtnW = 62;
        int backBtnX = lx + lw - backBtnW - 10;
        if (isHovered(mx, my, backBtnX, ly + 8, backBtnW, 20)) {
            playClick();
            closeLightbox();
            return true;
        }

        // Prev / Next arrows
        int footH = 44;
        int vpY = ly + topH + 10;
        int vpH = lh - topH - footH - 20;
        int arrowW = 26;
        int arrowH = 44;
        int arrowY = vpY + (vpH - arrowH) / 2;

        if (this.selectedIndex > 0 && isHovered(mx, my, lx + 8, arrowY, arrowW, arrowH)) {
            playClick();
            openLightbox(this.selectedIndex - 1);
            return true;
        }
        if (this.selectedIndex < this.filteredEntries.size() - 1 && isHovered(mx, my, lx + lw - arrowW - 8, arrowY, arrowW, arrowH)) {
            playClick();
            openLightbox(this.selectedIndex + 1);
            return true;
        }

        int footY = ly + lh - footH;
        int actY = footY + 11;
        int actH = 22;

        if (this.renaming) {
            int rbX = lx + 14;
            int rbW = lw - 184;
            if (this.renameBox != null && isHovered(mx, my, rbX, actY, rbW, actH)) {
                this.renameBox.setFocused(true);
                this.setFocused(this.renameBox);
                this.renameBox.mouseClicked(event, doubleClick);
                return true;
            }
            int saveX = rbX + rbW + 8;
            if (isHovered(mx, my, saveX, actY, 72, actH)) {
                playClick();
                commitRename();
                return true;
            }
            int cancelX = saveX + 78;
            if (isHovered(mx, my, cancelX, actY, 68, actH)) {
                playClick();
                cancelRename();
                return true;
            }
            return true;
        }

        int curX = lx + 14;
        // Copy
        int copyW = 94;
        if (isHovered(mx, my, curX, actY, copyW, actH)) {
            playClick();
            setStatus("Copying...", 0xFF94A3B8);
            BetterScreenshotsState.copyFileToClipboardAsync(entry.file, ok -> {
                if (ok) {
                    setStatus("✓ Copied to clipboard!", 0xFF34D399);
                } else {
                    setStatus("⚠ Copy failed", 0xFFF87171);
                }
            });
            return true;
        }
        curX += copyW + 6;

        // Open File
        int openW = 78;
        if (isHovered(mx, my, curX, actY, openW, actH)) {
            playClick();
            Util.getPlatform().openFile(entry.file);
            return true;
        }
        curX += openW + 6;

        // Rename
        int renW = 70;
        if (isHovered(mx, my, curX, actY, renW, actH)) {
            playClick();
            startRename(entry);
            return true;
        }
        curX += renW + 6;

        // Delete
        boolean confirmingDel = System.currentTimeMillis() < this.deleteConfirmUntilMs;
        int delW = confirmingDel ? 84 : 70;
        if (isHovered(mx, my, curX, actY, delW, actH)) {
            playClick();
            if (!confirmingDel) {
                this.deleteConfirmUntilMs = System.currentTimeMillis() + 4000L;
                setStatus("Click again to confirm delete", 0xFFFBBF24);
            } else {
                deleteCurrentEntry();
            }
            return true;
        }

        return true;
    }

    private void startRename(ScreenshotEntry entry) {
        this.renaming = true;
        this.deleteConfirmUntilMs = 0L;
        String base = entry.name;
        if (base.toLowerCase(Locale.ROOT).endsWith(".png")) {
            base = base.substring(0, base.length() - 4);
        }
        if (this.renameBox != null) {
            this.renameBox.setVisible(true);
            this.renameBox.setValue(base);
            this.renameBox.setFocused(true);
            this.setFocused(this.renameBox);
        }
    }

    private void cancelRename() {
        this.renaming = false;
        if (this.renameBox != null) {
            this.renameBox.setVisible(false);
            this.renameBox.setFocused(false);
        }
    }

    private void commitRename() {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.filteredEntries.size() || this.renameBox == null) {
            cancelRename();
            return;
        }
        ScreenshotEntry entry = this.filteredEntries.get(this.selectedIndex);
        String raw = this.renameBox.getValue().trim();
        if (raw.isEmpty()) {
            setStatus("Name cannot be empty", 0xFFF87171);
            return;
        }
        // Sanitize illegal filename chars
        String safe = raw.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (!safe.toLowerCase(Locale.ROOT).endsWith(".png")) {
            safe = safe + ".png";
        }
        if (safe.equals(entry.name)) {
            cancelRename();
            return;
        }
        File target = new File(entry.file.getParentFile(), safe);
        if (target.exists()) {
            setStatus("File already exists!", 0xFFF87171);
            return;
        }
        if (entry.file.renameTo(target)) {
            entry.file = target;
            entry.name = target.getName();
            cancelRename();
            setStatus("✓ Renamed to " + entry.name, 0xFF34D399);
        } else {
            setStatus("⚠ Rename failed", 0xFFF87171);
        }
    }

    private void deleteCurrentEntry() {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.filteredEntries.size()) return;
        ScreenshotEntry entry = this.filteredEntries.get(this.selectedIndex);
        this.deleteConfirmUntilMs = 0L;

        boolean deleted = entry.file.delete();
        if (!deleted && entry.file.exists()) {
            setStatus("⚠ Could not delete file", 0xFFF87171);
            return;
        }

        if (entry.thumbId != null) {
            Minecraft.getInstance().getTextureManager().release(entry.thumbId);
            entry.thumbId = null;
        }
        if (entry.thumbTex != null) {
            entry.thumbTex.close();
            entry.thumbTex = null;
        }
        this.allEntries.remove(entry);
        this.filteredEntries.remove(this.selectedIndex);

        ToastNotificationManager.pushToast("screenshot_delete", "Screenshot Deleted", entry.name, "DELETED", 0xFFEF4444);

        if (this.filteredEntries.isEmpty()) {
            closeLightbox();
        } else {
            int nextIdx = Math.min(this.selectedIndex, this.filteredEntries.size() - 1);
            openLightbox(nextIdx);
            setStatus("Deleted " + entry.name, 0xFF94A3B8);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.selectedIndex < 0) {
            this.targetGridScroll -= verticalAmount * 36.0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        int key = event.key();
        if (this.selectedIndex >= 0) {
            if (this.renaming) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                    commitRename();
                    return true;
                }
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    cancelRename();
                    return true;
                }
                return super.keyPressed(event);
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closeLightbox();
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT && this.selectedIndex > 0) {
                playClick();
                openLightbox(this.selectedIndex - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT && this.selectedIndex < this.filteredEntries.size() - 1) {
                playClick();
                openLightbox(this.selectedIndex + 1);
                return true;
            }
            return true;
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
    public void removed() {
        this.closed = true;
        releaseAllTextures();
        super.removed();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return String.format(Locale.ROOT, "%.0f KB", kb);
        return String.format(Locale.ROOT, "%.1f MB", kb / 1024.0);
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static boolean isHovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
