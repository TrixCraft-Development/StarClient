package net.starclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.friend.FriendManager;
import net.starclient.module.WaypointsState;
import net.starclient.module.WorldMapManager;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fullscreen interactive World Map (WORLD -> "World Map", default Hotkey: M):
 * Smooth pan & cursor-anchored zoom across explored terrain, chunk grid overlay,
 * live player & friend markers, Waypoint pins, cursor coordinates & biome readout,
 * and right-click context menu for creating Waypoints or copying coordinates.
 */
public class WorldMapScreen extends Screen {

    private static final int TEX_W = 512;
    private static final int TEX_H = 288;
    private static final Identifier WORLDMAP_TEX_ID =
            Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "worldmap_fullscreen_dynamic");

    private static DynamicTexture dynamicTexture;
    private static NativeImage nativeImage;

    private final Screen parent;

    private double centerX;
    private double centerZ;
    private double zoom = 1.5; // pixels per block in screen space (0.25 .. 8.0)

    private boolean dragging = false;
    private boolean showWaypointsLocal;
    private boolean showPlayersLocal;

    // Context menu state on Right-Click
    private boolean contextOpen = false;
    private int ctxX, ctxY;
    private int ctxWorldX, ctxWorldY, ctxWorldZ;
    private WaypointsState.Waypoint ctxWaypoint = null;

    // Rasterization cache signature
    private double lastRasterCenterX = Double.NaN;
    private double lastRasterCenterZ = Double.NaN;
    private double lastRasterZoom = Double.NaN;
    private int lastRasterViewW = -1;
    private int lastRasterViewH = -1;
    private boolean lastRasterGrid = false;
    private long lastRasterRev = -1L;

    // Toolbar bounds
    private int tbCenterX, tbGridX, tbWpX, tbZoomOutX, tbZoomInX, tbCloseX, tbY;

    private String statusBanner = "";
    private long statusBannerUntil = 0L;

    public WorldMapScreen(Screen parent) {
        super(Component.literal("World Map"));
        this.parent = parent;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.centerX = mc.player.getX();
            this.centerZ = mc.player.getZ();
        }
        this.showWaypointsLocal = WorldMapManager.isShowWaypoints();
        this.showPlayersLocal = WorldMapManager.isShowPlayers();
    }

    @Override
    protected void init() {
        super.init();
        Minecraft mc = Minecraft.getInstance();
        ensureTexture(mc);
        WorldMapManager.scanLoadedNow(mc);
        invalidateRaster();
    }

    private static void ensureTexture(Minecraft client) {
        if (dynamicTexture != null && nativeImage != null) return;
        nativeImage = new NativeImage(TEX_W, TEX_H, true);
        dynamicTexture = new DynamicTexture(() -> "starclient_worldmap", nativeImage);
        client.getTextureManager().register(WORLDMAP_TEX_ID, dynamicTexture);
    }

    private void invalidateRaster() {
        lastRasterRev = -1L;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        WorldMapManager.saveAllDirtyAsync();
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            this.onClose();
            return;
        }
        ensureTexture(mc);

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int bg = cs != null ? (0xEA000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xEA0F172A;
        int surface = cs != null ? cs.getMenuSurfaceColor() : 0xFF162032;
        int surfaceHov = cs != null ? cs.getMenuSurfaceHoverColor() : 0xFF1E293B;
        int border = cs != null ? cs.getMenuBorderColor() : 0xFF334155;

        int viewX = 0;
        int viewY = 0;
        int viewW = this.width;
        int viewH = this.height;

        Map<Long, WorldMapManager.ChunkTile> tiles = WorldMapManager.getCurrentTiles(mc);
        boolean chunkGrid = WorldMapManager.isShowChunkGrid();

        rasterizeIfNeeded(tiles, viewW, viewH, chunkGrid);

        // Fullscreen terrain blit
        graphics.blit(WORLDMAP_TEX_ID, viewX, viewY, viewX + viewW, viewY + viewH, 0.0f, 1.0f, 0.0f, 1.0f);

        // Subtle vignette edge border
        graphics.fill(0, 0, viewW, 1, GuiRenderUtils.withAlpha(accent, 90));
        graphics.fill(0, viewH - 1, viewW, viewH, GuiRenderUtils.withAlpha(accent, 90));

        // 1. Render Waypoints
        WaypointsState.ResolvedWaypoint hoveredWaypoint = null;
        if (this.showWaypointsLocal) {
            List<WaypointsState.ResolvedWaypoint> wps = WaypointsState.getRenderableWaypoints(mc);
            for (WaypointsState.ResolvedWaypoint rwp : wps) {
                int sx = worldToScreenX(rwp.x(), viewW);
                int sy = worldToScreenZ(rwp.z(), viewH);
                if (sx < -24 || sx > viewW + 24 || sy < -24 || sy > viewH + 24) continue;

                boolean hov = !contextOpen && Math.hypot(mouseX - sx, mouseY - sy) <= 9.0;
                if (hov) hoveredWaypoint = rwp;

                int wpCol = 0xFF000000 | (rwp.waypoint().getColor() & 0x00FFFFFF);
                int r = hov ? 7 : 5;
                GuiRenderUtils.drawDropShadow(graphics, sx - r, sy - r, r * 2, r * 2, r, 2, 110);
                GuiRenderUtils.fillRoundedRect(graphics, sx - r, sy - r, r * 2, r * 2, r, 0xEE0B1120);
                GuiRenderUtils.drawRoundedOutline(graphics, sx - r, sy - r, r * 2, r * 2, r, wpCol);
                graphics.fill(sx - 2, sy - 2, sx + 2, sy + 2, wpCol);

                String name = rwp.waypoint().getName();
                if (rwp.netherTranslated()) {
                    name = name + " (8:1)";
                }
                int nw = this.font.width(name);
                int tagW = nw + 8;
                int tagX = sx - tagW / 2;
                int tagY = sy + r + 3;
                GuiRenderUtils.fillRoundedRect(graphics, tagX, tagY, tagW, 12, 3, hov ? 0xF00B1120 : 0xCC0B1120);
                GuiRenderUtils.drawRoundedOutline(graphics, tagX, tagY, tagW, 12, 3,
                        GuiRenderUtils.withAlpha(wpCol, hov ? 220 : 120));
                graphics.text(this.font, name, tagX + 4, tagY + 2, hov ? 0xFFFFFFFF : 0xFFE2E8F0, true);
            }
        }

        // 2. Render Other Loaded Players
        if (this.showPlayersLocal) {
            for (Player other : mc.level.players()) {
                if (other == mc.player || !other.isAlive()) continue;
                int px = worldToScreenX(other.getX(), viewW);
                int py = worldToScreenZ(other.getZ(), viewH);
                if (px < -16 || px > viewW + 16 || py < -16 || py > viewH + 16) continue;

                boolean isFriend = FriendManager.isFriend(other);
                int col = isFriend ? FriendManager.getFriendColor() : 0xFF38BDF8;
                GuiRenderUtils.fillRoundedRect(graphics, px - 4, py - 4, 8, 8, 4, 0xEE0B1120);
                GuiRenderUtils.fillRoundedRect(graphics, px - 2, py - 2, 4, 4, 2, col);
                GuiRenderUtils.drawRoundedOutline(graphics, px - 4, py - 4, 8, 8, 4, col);

                String pName = other.getName().getString();
                int pw = this.font.width(pName) + 8;
                GuiRenderUtils.fillRoundedRect(graphics, px - pw / 2, py + 6, pw, 11, 3, 0xCC0B1120);
                graphics.text(this.font, pName, px - pw / 2 + 4, py + 8, col, false);
            }
        }

        // 3. Render Self Player Directional Marker
        int selfX = worldToScreenX(mc.player.getX(), viewW);
        int selfY = worldToScreenZ(mc.player.getZ(), viewH);
        if (selfX >= -20 && selfX <= viewW + 20 && selfY >= -20 && selfY <= viewH + 20) {
            float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 260.0));
            int ringR = 8 + Math.round(pulse * 2.0f);
            GuiRenderUtils.drawRoundedOutline(graphics, selfX - ringR, selfY - ringR, ringR * 2, ringR * 2,
                    ringR, GuiRenderUtils.withAlpha(accent, Math.round(80 + 90 * pulse)));

            double headRad = Math.toRadians(mc.player.getYRot());
            double dirX = -Math.sin(headRad);
            double dirZ = Math.cos(headRad);
            int tipX = (int) Math.round(selfX + dirX * 9.0);
            int tipY = (int) Math.round(selfY + dirZ * 9.0);
            EspRenderUtil.drawLine(graphics, selfX, selfY, tipX, tipY, 2.0f, accent);

            GuiRenderUtils.fillRoundedRect(graphics, selfX - 4, selfY - 4, 8, 8, 4, 0xFF0B1120);
            GuiRenderUtils.fillRoundedRect(graphics, selfX - 3, selfY - 3, 6, 6, 3, 0xFFFFFFFF);
            GuiRenderUtils.drawRoundedOutline(graphics, selfX - 4, selfY - 4, 8, 8, 4, accent);
        }

        // 4. Compass Rose indicator (North-Up) in top-left below header
        int roseX = 24;
        int roseY = 48;
        GuiRenderUtils.fillRoundedRect(graphics, roseX - 12, roseY - 12, 24, 24, 12, 0xCC0B1120);
        GuiRenderUtils.drawRoundedOutline(graphics, roseX - 12, roseY - 12, 24, 24, 12, border);
        GuiRenderUtils.drawCenteredText(graphics, this.font, "N", roseX, roseY - 9, 0xFFF87171);
        graphics.fill(roseX, roseY - 1, roseX + 1, roseY + 7, 0xFF94A3B8);

        // 5. Top-Left Title Pill
        String dimShort = WaypointsState.formatDimensionShort(WaypointsState.getCurrentDimension(mc));
        String titleStr = "🗺 World Map  •  " + dimShort + "  •  " + tiles.size() + " chunks";
        int titleW = this.font.width(titleStr) + 18;
        GuiRenderUtils.drawDropShadow(graphics, 10, 10, titleW, 22, 6, 3, 110);
        GuiRenderUtils.fillRoundedRect(graphics, 10, 10, titleW, 22, 6, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, 10, 10, titleW, 22, 6, border);
        GuiRenderUtils.drawText(graphics, this.font, titleStr, 19, 17, 0xFFF8FAFC, true);

        // 6. Top-Right Interactive Toolbar
        this.tbY = 10;
        int btnH = 22;
        int closeW = 24;
        int zoomBtnW = 22;
        int zoomLabelW = 36;
        int wpW = 68;
        int gridW = 48;
        int centerW = 58;
        int gap = 4;

        this.tbCloseX = viewW - 10 - closeW;
        this.tbZoomInX = this.tbCloseX - gap - zoomBtnW;
        int zoomLblX = this.tbZoomInX - 2 - zoomLabelW;
        this.tbZoomOutX = zoomLblX - 2 - zoomBtnW;
        this.tbWpX = this.tbZoomOutX - gap - wpW;
        this.tbGridX = this.tbWpX - gap - gridW;
        this.tbCenterX = this.tbGridX - gap - centerW;

        drawToolbarButton(graphics, this.tbCenterX, this.tbY, centerW, btnH, "🎯 Center", false, mouseX, mouseY, bg, surfaceHov, border, accent);
        drawToolbarButton(graphics, this.tbGridX, this.tbY, gridW, btnH, "# Grid", chunkGrid, mouseX, mouseY, bg, surfaceHov, border, accent);
        drawToolbarButton(graphics, this.tbWpX, this.tbY, wpW, btnH, "📍 Pins", this.showWaypointsLocal, mouseX, mouseY, bg, surfaceHov, border, accent);
        drawToolbarButton(graphics, this.tbZoomOutX, this.tbY, zoomBtnW, btnH, "−", false, mouseX, mouseY, bg, surfaceHov, border, accent);

        GuiRenderUtils.fillRoundedRect(graphics, zoomLblX, this.tbY, zoomLabelW, btnH, 5, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, zoomLblX, this.tbY, zoomLabelW, btnH, 5, border);
        String zoomStr = String.format(Locale.US, "%.2gx", this.zoom);
        GuiRenderUtils.drawCenteredText(graphics, this.font, zoomStr, zoomLblX + zoomLabelW / 2, this.tbY + 7, accent);

        drawToolbarButton(graphics, this.tbZoomInX, this.tbY, zoomBtnW, btnH, "+", false, mouseX, mouseY, bg, surfaceHov, border, accent);
        drawToolbarButton(graphics, this.tbCloseX, this.tbY, closeW, btnH, "✕", false, mouseX, mouseY, bg, 0xAAEF4444, border, 0xFFEF4444);

        // 7. Bottom Status Bar (Cursor coords, Biome, Distance, Nether/OW conversion & Controls)
        if (WorldMapManager.isShowCoordsAndBiome()) {
            int hoverWX = Mth.floor(screenToWorldX(mouseX, viewW));
            int hoverWZ = Mth.floor(screenToWorldZ(mouseY, viewH));
            WorldMapManager.ChunkTile hoverTile = WorldMapManager.getTile(tiles, hoverWX >> 4, hoverWZ >> 4);
            String biomeStr = hoverTile != null ? hoverTile.biomeName : "Unexplored";
            int hoverWY = hoverTile != null
                    ? hoverTile.heights[((hoverWZ & 15) << 4) | (hoverWX & 15)]
                    : Mth.floor(mc.player.getY());
            int distM = (int) Math.round(Math.hypot(hoverWX - mc.player.getX(), hoverWZ - mc.player.getZ()));

            String curDim = WaypointsState.getCurrentDimension(mc);
            String extraDim = "";
            if (WaypointsState.DIM_OVERWORLD.equals(curDim)) {
                extraDim = String.format(Locale.US, "  •  Nether: %d, %d", Math.floorDiv(hoverWX, 8), Math.floorDiv(hoverWZ, 8));
            } else if (WaypointsState.DIM_NETHER.equals(curDim)) {
                extraDim = String.format(Locale.US, "  •  OW: %d, %d", hoverWX * 8, hoverWZ * 8);
            }

            String infoLine = String.format(Locale.US, "Cursor: %d, %d, %d  •  %s  •  %dm away%s",
                    hoverWX, hoverWY, hoverWZ, biomeStr, distM, extraDim);
            String hintLine = "Left-Drag: Pan  •  Scroll: Zoom  •  Right-Click: Waypoint / Copy  •  Space: Center";
            if (!statusBanner.isEmpty() && System.currentTimeMillis() < statusBannerUntil) {
                hintLine = statusBanner;
            }

            int barW = Math.min(viewW - 24, Math.max(this.font.width(infoLine), this.font.width(hintLine)) + 24);
            int barH = 30;
            int barX = (viewW - barW) / 2;
            int barY = viewH - barH - 10;

            GuiRenderUtils.drawDropShadow(graphics, barX, barY, barW, barH, 6, 4, 120);
            GuiRenderUtils.fillRoundedRect(graphics, barX, barY, barW, barH, 6, bg);
            GuiRenderUtils.drawRoundedOutline(graphics, barX, barY, barW, barH, 6, border);

            GuiRenderUtils.drawCenteredText(graphics, this.font,
                    GuiRenderUtils.truncate(this.font, infoLine, barW - 14),
                    barX + barW / 2, barY + 5, 0xFFF8FAFC);
            boolean bannerActive = !statusBanner.isEmpty() && System.currentTimeMillis() < statusBannerUntil;
            GuiRenderUtils.drawCenteredText(graphics, this.font,
                    GuiRenderUtils.truncate(this.font, hintLine, barW - 14),
                    barX + barW / 2, barY + 17, bannerActive ? 0xFF34D399 : 0xFF94A3B8);
        }

        // 8. Hovered Waypoint Tooltip
        if (hoveredWaypoint != null && !contextOpen) {
            WaypointsState.Waypoint wp = hoveredWaypoint.waypoint();
            int dist = (int) Math.round(Math.hypot(hoveredWaypoint.x() - mc.player.getX(), hoveredWaypoint.z() - mc.player.getZ()));
            String tip = wp.getName() + "  (" + wp.getX() + ", " + wp.getY() + ", " + wp.getZ() + " • " + dist + "m)";
            int tw = this.font.width(tip) + 12;
            int tx = Math.clamp(mouseX + 10, 6, Math.max(6, viewW - tw - 6));
            int ty = Math.clamp(mouseY - 22, 6, Math.max(6, viewH - 22));
            GuiRenderUtils.fillRoundedRect(graphics, tx, ty, tw, 16, 4, 0xF20B1120);
            GuiRenderUtils.drawRoundedOutline(graphics, tx, ty, tw, 16, 4, wp.getColor());
            graphics.text(this.font, tip, tx + 6, ty + 4, 0xFFF8FAFC, false);
        }

        // 9. Right-Click Context Menu
        if (contextOpen) {
            renderContextMenu(graphics, mouseX, mouseY, bg, surface, surfaceHov, border, accent);
        }
    }

    private void renderContextMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   int bg, int surface, int surfaceHov, int border, int accent) {
        List<String> items = buildContextItems();
        int menuW = 166;
        int rowH = 20;
        int headerH = 18;
        int menuH = headerH + items.size() * rowH + 6;
        int mx = Math.clamp(this.ctxX, 6, Math.max(6, this.width - menuW - 6));
        int my = Math.clamp(this.ctxY, 6, Math.max(6, this.height - menuH - 6));

        graphics.nextStratum();
        GuiRenderUtils.drawDropShadow(graphics, mx, my, menuW, menuH, 6, 5, 145);
        GuiRenderUtils.fillRoundedRect(graphics, mx, my, menuW, menuH, 6, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, mx, my, menuW, menuH, 6, accent);

        GuiRenderUtils.fillRoundedRect(graphics, mx, my, menuW, headerH, 6, surface);
        String header = ctxWorldX + ", " + ctxWorldY + ", " + ctxWorldZ;
        GuiRenderUtils.drawCenteredText(graphics, this.font, header, mx + menuW / 2, my + 5, accent);

        int ry = my + headerH + 3;
        for (int i = 0; i < items.size(); i++) {
            String label = items.get(i);
            boolean hov = hovered(mouseX, mouseY, mx + 4, ry, menuW - 8, rowH - 2);
            boolean danger = label.startsWith("🗑");
            if (hov) {
                GuiRenderUtils.fillRoundedRect(graphics, mx + 4, ry, menuW - 8, rowH - 2, 4,
                        danger ? 0x55EF4444 : surfaceHov);
            }
            graphics.text(this.font, GuiRenderUtils.truncate(this.font, label, menuW - 18),
                    mx + 9, ry + 5, danger ? 0xFFFCA5A5 : (hov ? 0xFFFFFFFF : 0xFFE2E8F0), false);
            ry += rowH;
        }
    }

    private List<String> buildContextItems() {
        List<String> items = new ArrayList<>();
        items.add("📍 Create Waypoint Here...");
        items.add("⚡ Quick Save Waypoint");
        items.add("📋 Copy Coordinates");
        items.add("🎯 Center Map Here");
        if (this.ctxWaypoint != null) {
            items.add("✏ Edit \"" + this.ctxWaypoint.getName() + "\"");
            items.add("🗑 Delete \"" + this.ctxWaypoint.getName() + "\"");
        }
        return items;
    }

    private void drawToolbarButton(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                                   String text, boolean active, int mouseX, int mouseY,
                                   int bg, int hovBg, int border, int accent) {
        boolean hov = hovered(mouseX, mouseY, x, y, w, h);
        GuiRenderUtils.drawDropShadow(graphics, x, y, w, h, 5, 2, 95);
        int fill = active ? GuiRenderUtils.withAlpha(accent, 65) : (hov ? hovBg : bg);
        GuiRenderUtils.fillRoundedRect(graphics, x, y, w, h, 5, fill);
        GuiRenderUtils.drawRoundedOutline(graphics, x, y, w, h, 5, (active || hov) ? accent : border);
        GuiRenderUtils.drawCenteredText(graphics, this.font, text, x + w / 2, y + 7,
                (active || hov) ? 0xFFFFFFFF : 0xFFE2E8F0);
    }

    private void rasterizeIfNeeded(Map<Long, WorldMapManager.ChunkTile> tiles, int viewW, int viewH, boolean chunkGrid) {
        long rev = WorldMapManager.getRevision();
        if (rev == lastRasterRev
                && viewW == lastRasterViewW
                && viewH == lastRasterViewH
                && chunkGrid == lastRasterGrid
                && Math.abs(this.centerX - lastRasterCenterX) < 1e-4
                && Math.abs(this.centerZ - lastRasterCenterZ) < 1e-4
                && Math.abs(this.zoom - lastRasterZoom) < 1e-4) {
            return;
        }

        lastRasterRev = rev;
        lastRasterViewW = viewW;
        lastRasterViewH = viewH;
        lastRasterGrid = chunkGrid;
        lastRasterCenterX = this.centerX;
        lastRasterCenterZ = this.centerZ;
        lastRasterZoom = this.zoom;

        double blocksSpanW = viewW / Math.max(0.1, this.zoom);
        double blocksSpanH = viewH / Math.max(0.1, this.zoom);
        double startX = this.centerX - blocksSpanW * 0.5;
        double startZ = this.centerZ - blocksSpanH * 0.5;
        double stepX = blocksSpanW / TEX_W;
        double stepZ = blocksSpanH / TEX_H;

        int lastCx = Integer.MIN_VALUE;
        int lastCz = Integer.MIN_VALUE;
        WorldMapManager.ChunkTile cachedTile = null;

        for (int ty = 0; ty < TEX_H; ty++) {
            int wz = Mth.floor(startZ + (ty + 0.5) * stepZ);
            int cz = wz >> 4;
            int lz = wz & 15;
            boolean zBorder = chunkGrid && lz == 0;
            boolean zBgLine = (wz & 31) == 0;

            for (int tx = 0; tx < TEX_W; tx++) {
                int wx = Mth.floor(startX + (tx + 0.5) * stepX);
                int cx = wx >> 4;
                int lx = wx & 15;

                if (cx != lastCx || cz != lastCz) {
                    cachedTile = WorldMapManager.getTile(tiles, cx, cz);
                    lastCx = cx;
                    lastCz = cz;
                }

                int argb;
                if (cachedTile != null) {
                    argb = cachedTile.colors[(lz << 4) | lx];
                    if (zBorder || (chunkGrid && lx == 0)) {
                        argb = blendDark(argb, 0xFF000000, 0.32f);
                    }
                } else {
                    // Subtle unexplored dark cartographic backdrop
                    boolean gridLine = zBgLine || ((wx & 31) == 0);
                    argb = gridLine ? 0xFF10192B : 0xFF090F1D;
                }
                nativeImage.setPixel(tx, ty, argb);
            }
        }
        dynamicTexture.upload();
    }

    private static int blendDark(int base, int overlay, float alpha) {
        int r = (base >> 16) & 0xFF;
        int g = (base >> 8) & 0xFF;
        int b = base & 0xFF;
        float inv = 1.0f - alpha;
        int nr = Math.round(r * inv + ((overlay >> 16) & 0xFF) * alpha);
        int ng = Math.round(g * inv + ((overlay >> 8) & 0xFF) * alpha);
        int nb = Math.round(b * inv + (overlay & 0xFF) * alpha);
        return 0xFF000000 | (nr << 16) | (ng << 8) | nb;
    }

    // -------------------------------------------------------------------------
    // Input Handling (Pan, Zoom to Cursor, Toolbar, Right-Click Context Menu)
    // -------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        Minecraft mc = Minecraft.getInstance();

        // Handle open context menu clicks first
        if (this.contextOpen) {
            List<String> items = buildContextItems();
            int menuW = 166;
            int rowH = 20;
            int headerH = 18;
            int menuH = headerH + items.size() * rowH + 6;
            int cmx = Math.clamp(this.ctxX, 6, Math.max(6, this.width - menuW - 6));
            int cmy = Math.clamp(this.ctxY, 6, Math.max(6, this.height - menuH - 6));

            if (hovered(mx, my, cmx, cmy, menuW, menuH)) {
                int ry = cmy + headerH + 3;
                for (int i = 0; i < items.size(); i++) {
                    if (hovered(mx, my, cmx + 4, ry, menuW - 8, rowH - 2)) {
                        executeContextAction(i, mc);
                        this.contextOpen = false;
                        return true;
                    }
                    ry += rowH;
                }
                return true;
            }
            this.contextOpen = false;
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return true;
            }
        }

        // Toolbar button clicks
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && my >= this.tbY && my <= this.tbY + 22) {
            if (hovered(mx, my, this.tbCenterX, this.tbY, 58, 22)) {
                playClick();
                if (mc.player != null) {
                    this.centerX = mc.player.getX();
                    this.centerZ = mc.player.getZ();
                }
                return true;
            }
            if (hovered(mx, my, this.tbGridX, this.tbY, 48, 22)) {
                playClick();
                WorldMapManager.toggleChunkGrid();
                return true;
            }
            if (hovered(mx, my, this.tbWpX, this.tbY, 68, 22)) {
                playClick();
                this.showWaypointsLocal = !this.showWaypointsLocal;
                return true;
            }
            if (hovered(mx, my, this.tbZoomOutX, this.tbY, 22, 22)) {
                playClick();
                applyZoom(this.zoom / 1.35, this.width / 2.0, this.height / 2.0);
                return true;
            }
            if (hovered(mx, my, this.tbZoomInX, this.tbY, 22, 22)) {
                playClick();
                applyZoom(this.zoom * 1.35, this.width / 2.0, this.height / 2.0);
                return true;
            }
            if (hovered(mx, my, this.tbCloseX, this.tbY, 24, 22)) {
                playClick();
                this.onClose();
                return true;
            }
        }

        // Right-click opens context menu at clicked world position
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            playClick();
            this.ctxX = (int) Math.round(mx);
            this.ctxY = (int) Math.round(my);
            this.ctxWorldX = Mth.floor(screenToWorldX(mx, this.width));
            this.ctxWorldZ = Mth.floor(screenToWorldZ(my, this.height));
            Map<Long, WorldMapManager.ChunkTile> tiles = WorldMapManager.getCurrentTiles(mc);
            WorldMapManager.ChunkTile tile = WorldMapManager.getTile(tiles, this.ctxWorldX >> 4, this.ctxWorldZ >> 4);
            this.ctxWorldY = tile != null
                    ? tile.heights[((this.ctxWorldZ & 15) << 4) | (this.ctxWorldX & 15)]
                    : (mc.player != null ? Mth.floor(mc.player.getY()) : 64);
            this.ctxWaypoint = findNearestWaypointAtScreen(mc, mx, my);
            this.contextOpen = true;
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.dragging = true;
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void executeContextAction(int index, Minecraft mc) {
        playClick();
        String curDim = WaypointsState.getCurrentDimension(mc);
        String wk = WaypointsState.getCurrentWorldKey(mc);
        switch (index) {
            case 0 -> { // Create Waypoint Here (opens full editor pre-filled)
                mc.gui.setScreen(WaypointsScreen.openCreateAt(this, ctxWorldX, ctxWorldY, ctxWorldZ, curDim));
            }
            case 1 -> { // Quick Save Waypoint
                int nextNum = WaypointsState.getWaypointsForCurrentContext(mc).size() + 1;
                int col = WaypointsState.PALETTE[(nextNum - 1) % WaypointsState.PALETTE.length];
                WaypointsState.Waypoint wp = WaypointsState.addWaypoint(
                        "Map Pin #" + nextNum, ctxWorldX, ctxWorldY, ctxWorldZ, curDim, wk, col);
                if (wp != null) {
                    setStatus("✓ Saved waypoint \"" + wp.getName() + "\" at " + ctxWorldX + ", " + ctxWorldY + ", " + ctxWorldZ);
                    ToastNotificationManager.pushToast("worldmap_wp", "Saved " + wp.getName(),
                            ctxWorldX + ", " + ctxWorldY + ", " + ctxWorldZ, "WORLD MAP", col);
                }
            }
            case 2 -> { // Copy Coordinates
                String coords = ctxWorldX + " " + ctxWorldY + " " + ctxWorldZ;
                mc.keyboardHandler.setClipboard(coords);
                setStatus("✓ Copied coordinates: " + coords);
            }
            case 3 -> { // Center Map Here
                this.centerX = ctxWorldX + 0.5;
                this.centerZ = ctxWorldZ + 0.5;
            }
            case 4 -> { // Edit Waypoint
                if (this.ctxWaypoint != null) {
                    mc.gui.setScreen(WaypointsScreen.openEdit(this, this.ctxWaypoint));
                }
            }
            case 5 -> { // Delete Waypoint
                if (this.ctxWaypoint != null) {
                    String name = this.ctxWaypoint.getName();
                    WaypointsState.removeWaypoint(this.ctxWaypoint.getId());
                    setStatus("🗑 Deleted waypoint \"" + name + "\"");
                }
            }
        }
    }

    private WaypointsState.Waypoint findNearestWaypointAtScreen(Minecraft mc, double mx, double my) {
        if (!this.showWaypointsLocal) return null;
        WaypointsState.Waypoint best = null;
        double bestDist = 14.0;
        for (WaypointsState.ResolvedWaypoint rwp : WaypointsState.getRenderableWaypoints(mc)) {
            double sx = worldToScreenX(rwp.x(), this.width);
            double sy = worldToScreenZ(rwp.z(), this.height);
            double d = Math.hypot(mx - sx, my - sy);
            if (d < bestDist) {
                bestDist = d;
                best = rwp.waypoint();
            }
        }
        return best;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.dragging = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.centerX -= dx / Math.max(0.1, this.zoom);
            this.centerZ -= dy / Math.max(0.1, this.zoom);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (Math.abs(verticalAmount) > 0.001) {
            double factor = verticalAmount > 0 ? 1.22 : (1.0 / 1.22);
            applyZoom(this.zoom * factor, mouseX, mouseY);
            this.contextOpen = false;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void applyZoom(double newZoom, double anchorScreenX, double anchorScreenY) {
        double clamped = Math.clamp(newZoom, 0.25, 8.0);
        double wxBefore = screenToWorldX(anchorScreenX, this.width);
        double wzBefore = screenToWorldZ(anchorScreenY, this.height);
        this.zoom = clamped;
        double wxAfter = screenToWorldX(anchorScreenX, this.width);
        double wzAfter = screenToWorldZ(anchorScreenY, this.height);
        this.centerX += (wxBefore - wxAfter);
        this.centerZ += (wzBefore - wzAfter);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (this.contextOpen) {
                this.contextOpen = false;
                return true;
            }
            this.onClose();
            return true;
        }
        if (WorldMapManager.getModule() != null && key == WorldMapManager.getModule().getKeyBind()) {
            this.onClose();
            return true;
        }
        double panStep = 36.0 / Math.max(0.25, this.zoom);
        switch (key) {
            case GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP -> {
                this.centerZ -= panStep;
                return true;
            }
            case GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN -> {
                this.centerZ += panStep;
                return true;
            }
            case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT -> {
                this.centerX -= panStep;
                return true;
            }
            case GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT -> {
                this.centerX += panStep;
                return true;
            }
            case GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_C -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    this.centerX = mc.player.getX();
                    this.centerZ = mc.player.getZ();
                }
                return true;
            }
            case GLFW.GLFW_KEY_G -> {
                WorldMapManager.toggleChunkGrid();
                return true;
            }
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> {
                applyZoom(this.zoom * 1.25, this.width / 2.0, this.height / 2.0);
                return true;
            }
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> {
                applyZoom(this.zoom / 1.25, this.width / 2.0, this.height / 2.0);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    private int worldToScreenX(double wx, int viewW) {
        return (int) Math.round(viewW * 0.5 + (wx - this.centerX) * this.zoom);
    }

    private int worldToScreenZ(double wz, int viewH) {
        return (int) Math.round(viewH * 0.5 + (wz - this.centerZ) * this.zoom);
    }

    private double screenToWorldX(double sx, int viewW) {
        return this.centerX + (sx - viewW * 0.5) / Math.max(0.1, this.zoom);
    }

    private double screenToWorldZ(double sy, int viewH) {
        return this.centerZ + (sy - viewH * 0.5) / Math.max(0.1, this.zoom);
    }

    private void setStatus(String msg) {
        this.statusBanner = msg;
        this.statusBannerUntil = System.currentTimeMillis() + 3000L;
    }

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static boolean hovered(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
