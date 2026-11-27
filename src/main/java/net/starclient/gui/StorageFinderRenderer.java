package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.module.StorageFinderManager;
import net.starclient.setting.ColorModeSetting;

import java.util.List;
import java.util.Locale;

/**
 * Renders glowing 3D container highlight boxes, optional tracers, floating item count badges,
 * and an active search HUD banner for matching containers in "Storage Finder".
 */
public final class StorageFinderRenderer {

    private StorageFinderRenderer() {
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (client == null || client.level == null || client.player == null || mod == null) return;
        if (!StorageFinderManager.hasActiveHighlight()) return;

        List<StorageFinderManager.ContainerMatch> matches = StorageFinderManager.getActiveMatches(client);
        int sw = extractor.guiWidth();
        int sh = extractor.guiHeight();
        GameRenderer gr = client.gameRenderer;

        boolean showBoxes = StorageFinderManager.getBool("Show 3D Boxes", true);
        boolean showLabels = StorageFinderManager.getBool("Show Labels", true);
        boolean showTracers = StorageFinderManager.getBool("Tracers", false);
        boolean useClientCol = StorageFinderManager.getBool("Use Client Color", true);
        float thickness = (float) StorageFinderManager.getNum("Line Thickness", 2.0);
        double maxRange = StorageFinderManager.getNum("Max Range", 128.0);
        double maxRangeSq = maxRange * maxRange;

        int accent = 0xFF38BDF8;
        if (useClientCol && StarClient.getInstance() != null && StarClient.getInstance().getClientSettings() != null) {
            accent = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        } else if (mod.getSetting("Color") instanceof ColorModeSetting cms) {
            accent = cms.getColor();
        }
        int bgColor = StarClient.getInstance() != null && StarClient.getInstance().getClientSettings() != null
                ? StarClient.getInstance().getClientSettings().getMenuBackgroundColor()
                : 0xFF0F172A;

        float pulse = (float) (0.65 + 0.35 * Math.sin(System.currentTimeMillis() / 220.0));
        int boxAlpha = Math.clamp(Math.round(180 + 75 * pulse), 0, 255);
        int boxCol = (accent & 0x00FFFFFF) | (boxAlpha << 24);
        int tracerCol = (accent & 0x00FFFFFF) | 0xBB000000;

        double originX = sw / 2.0;
        double originY = sh / 2.0;
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        int visibleCount = 0;
        int totalItems = 0;
        double nearestDist = Double.MAX_VALUE;
        ItemStack bannerIcon = StorageFinderManager.getActiveItemId() != null
                ? StorageFinderManager.resolveIcon(StorageFinderManager.getActiveItemId())
                : ItemStack.EMPTY;

        for (StorageFinderManager.ContainerMatch m : matches) {
            StorageFinderManager.IndexedContainer c = m.container();
            BlockPos p1 = c.pos;
            BlockPos p2 = c.secondPos;

            double cx = p2 != null ? (p1.getX() + p2.getX() + 1.0) * 0.5 : (p1.getX() + 0.5);
            double cy = p1.getY() + 0.5;
            double cz = p2 != null ? (p1.getZ() + p2.getZ() + 1.0) * 0.5 : (p1.getZ() + 0.5);

            double dx = cx - px;
            double dy = cy - py;
            double dz = cz - pz;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxRangeSq) continue;

            double dist = Math.sqrt(distSq);
            visibleCount++;
            totalItems += m.matchedCount();
            if (dist < nearestDist) nearestDist = dist;

            if (showBoxes) {
                double minX = p2 != null ? Math.min(p1.getX(), p2.getX()) : p1.getX();
                double minY = p1.getY();
                double minZ = p2 != null ? Math.min(p1.getZ(), p2.getZ()) : p1.getZ();
                double maxX = (p2 != null ? Math.max(p1.getX(), p2.getX()) : p1.getX()) + 1.0;
                double maxY = p1.getY() + 1.0;
                double maxZ = (p2 != null ? Math.max(p1.getZ(), p2.getZ()) : p1.getZ()) + 1.0;
                AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.012);
                EspRenderUtil.drawBox3D(extractor, gr, box, sw, sh, thickness, boxCol);
            }

            Vec3 screenPos = EspRenderUtil.projectToScreen(gr, new Vec3(cx, p1.getY() + 1.25, cz), sw, sh);
            if (screenPos == null) continue;

            if (showTracers) {
                EspRenderUtil.drawLine(extractor, originX, originY, screenPos.x, screenPos.y, 1.5f, tracerCol);
            }

            if (showLabels) {
                String label = "×" + m.matchedCount()
                        + (m.anyInShulker() ? " 📦" : "")
                        + String.format(Locale.US, " • %.0fm", dist);
                int tw = client.font.width(label);
                int pillW = tw + 10;
                int pillH = 13;
                int lx = (int) Math.round(screenPos.x) - pillW / 2;
                int ly = (int) Math.round(screenPos.y) - pillH / 2;

                GuiRenderUtils.fillRoundedRect(extractor, lx, ly, pillW, pillH, 3, (bgColor & 0x00FFFFFF) | 0xE0000000);
                GuiRenderUtils.drawRoundedOutline(extractor, lx, ly, pillW, pillH, 3, (accent & 0x00FFFFFF) | 0xCC000000);
                extractor.text(client.font, label, lx + 5, ly + 2, 0xFFF8FAFC, true);
            }
        }

        // Top-Center Active Storage Search Status Pill
        String target = StorageFinderManager.getActiveLabel();
        String bannerText = visibleCount > 0
                ? String.format(Locale.US, "🔍 %s • %d %s (×%d) • Nearest %.0fm",
                        target, visibleCount, visibleCount == 1 ? "chest" : "chests", totalItems, nearestDist)
                : ("🔍 " + target + " • No chests in range");

        int iconPad = bannerIcon.isEmpty() ? 0 : 16;
        int bw = client.font.width(bannerText) + 14 + iconPad;
        int bh = 18;
        int bx = (sw - bw) / 2;
        int by = 8;

        GuiRenderUtils.drawDropShadow(extractor, bx, by, bw, bh, 5, 3, 95);
        GuiRenderUtils.fillRoundedRect(extractor, bx, by, bw, bh, 5, (bgColor & 0x00FFFFFF) | 0xDC000000);
        GuiRenderUtils.drawRoundedOutline(extractor, bx, by, bw, bh, 5, (accent & 0x00FFFFFF) | 0xBB000000);
        if (!bannerIcon.isEmpty()) {
            extractor.pose().pushMatrix();
            extractor.pose().translate(bx + 5, by + 2);
            extractor.pose().scale(0.85f, 0.85f);
            extractor.item(bannerIcon, 0, 0);
            extractor.pose().popMatrix();
        }
        extractor.text(client.font, bannerText, bx + 7 + iconPad, by + 5, 0xFFF8FAFC, true);
    }
}
