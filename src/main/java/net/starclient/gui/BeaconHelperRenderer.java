package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Beacon Range & Pyramid Helper ("Beacon Helper" WORLD module):
 * 1. Renders a glassmorphic side panel inside BeaconScreen showing exact pyramid tier (0-4),
 *    effect radius (20/30/40/50m), exact mineral blocks needed for the next tier, sky-beam checks,
 *    and active primary/secondary effects.
 * 2. Scans nearby loaded Beacons in the world and draws glowing 3D coverage boundaries and
 *    floating Inside/Outside status badges.
 */
public final class BeaconHelperRenderer {

    private static final int PANEL_W = 172;
    private static final int[] LAYER_TOTALS = {0, 9, 25, 49, 81};
    private static final int FULL_PYRAMID_BLOCKS = 164;

    private record BeaconScan(BlockPos pos, int tier, int radius, boolean beamActive) {
    }

    private record PyramidInfo(int tier, int nextLayerHave, int nextLayerTotal, int totalHave, boolean beamActive) {
    }

    private static Module module;
    private static final List<BeaconScan> CACHED_BEACONS = new ArrayList<>();
    private static long lastScanTick = -100L;
    private static BlockPos lastPlayerPos = null;
    private static int lastRange = -1;

    private BeaconHelperRenderer() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static void clear() {
        CACHED_BEACONS.clear();
        lastPlayerPos = null;
        lastScanTick = -100L;
        lastRange = -1;
    }

    // -------------------------------------------------------------------------
    // 1. BeaconScreen Side Panel
    // -------------------------------------------------------------------------

    public static void renderBeaconPanel(GuiGraphicsExtractor graphics, BeaconMenu menu,
                                         int screenW, int leftPos, int topPos, int imageWidth, int imageHeight) {
        if (!isEnabled() || !getBool(module, "Show Beacon UI Panel", true) || menu == null) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        BlockPos beaconPos = findActiveBeacon(client);
        PyramidInfo info;
        if (beaconPos != null) {
            info = inspectPyramid(client.level, beaconPos);
        } else {
            int menuTier = Math.clamp(menu.getLevels(), 0, 4);
            int nextNeed = menuTier < 4 ? LAYER_TOTALS[menuTier + 1] : 0;
            int approxTotal = 0;
            for (int l = 1; l <= menuTier; l++) approxTotal += LAYER_TOTALS[l];
            info = new PyramidInfo(menuTier, 0, nextNeed, approxTotal, menuTier > 0);
        }

        int tier = Math.max(info.tier(), Math.clamp(menu.getLevels(), 0, 4));
        int radius = tier > 0 ? (tier * 10 + 10) : 0;

        Holder<MobEffect> primary = menu.getPrimaryEffect();
        Holder<MobEffect> secondary = menu.getSecondaryEffect();

        Font font = client.font;
        StarClient sc = StarClient.getInstance();
        ClientSettings cs = sc != null ? sc.getClientSettings() : null;
        int accent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int bg = cs != null ? (0xEE000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xEE0F172A;
        int surface = cs != null ? cs.getMenuSurfaceColor() : 0xFF162032;
        int border = cs != null ? cs.getMenuBorderColor() : 0xFF334155;

        int panelX = leftPos + imageWidth + 6;
        if (panelX + PANEL_W > screenW - 4) {
            panelX = Math.max(4, leftPos - PANEL_W - 6);
        }
        int panelY = topPos;

        boolean skyBlocked = beaconPos != null && tier > 0 && !info.beamActive();
        int panelH = 26 + 38 + (skyBlocked ? 18 : 0) + (tier < 4 ? 44 : 26) + 34 + 6;
        panelH = Math.min(panelH, Math.max(imageHeight, 180));

        GuiRenderUtils.drawDropShadow(graphics, panelX, panelY, PANEL_W, panelH, 6, 4, 110);
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, PANEL_W, panelH, 6, bg);
        GuiRenderUtils.drawRoundedOutline(graphics, panelX, panelY, PANEL_W, panelH, 6, border);

        // Header
        GuiRenderUtils.fillRoundedRect(graphics, panelX, panelY, PANEL_W, 20, 6, surface);
        graphics.fill(panelX, panelY + 19, panelX + PANEL_W, panelY + 20, border);
        GuiRenderUtils.drawText(graphics, font, "⚡ Beacon Helper", panelX + 7, panelY + 6, accent, true);

        int y = panelY + 26;

        // Tier & Radius Card
        int tierCol = tier == 4 ? 0xFF34D399 : (tier > 0 ? 0xFF38BDF8 : 0xFFF59E0B);
        GuiRenderUtils.fillRoundedRect(graphics, panelX + 6, y, PANEL_W - 12, 32, 4, 0x990F172A);
        GuiRenderUtils.drawRoundedOutline(graphics, panelX + 6, y, PANEL_W - 12, 32, 4, (tierCol & 0x00FFFFFF) | 0x66000000);

        String tierText = "Pyramid Tier " + tier + " / 4";
        GuiRenderUtils.drawText(graphics, font, tierText, panelX + 11, y + 5, tierCol, true);
        String radiusBadge = radius > 0 ? ("±" + radius + "m") : "Inactive";
        int rbW = font.width(radiusBadge) + 8;
        int rbX = panelX + PANEL_W - 11 - rbW;
        GuiRenderUtils.fillRoundedRect(graphics, rbX, y + 4, rbW, 11, 3, (tierCol & 0x00FFFFFF) | 0x28000000);
        GuiRenderUtils.drawText(graphics, font, radiusBadge, rbX + 4, y + 6, tierCol, false);

        String durationHint = switch (tier) {
            case 1 -> "Range: 20m • Duration: 11s";
            case 2 -> "Range: 30m • Duration: 13s";
            case 3 -> "Range: 40m • Duration: 15s";
            case 4 -> "Range: 50m • Tier II / Regen";
            default -> "Place 3x3 base below Beacon";
        };
        GuiRenderUtils.drawText(graphics, font, durationHint, panelX + 11, y + 19, 0xFF94A3B8, false);
        y += 38;

        // Sky-access warning if pyramid exists but beam is obstructed
        if (skyBlocked) {
            GuiRenderUtils.fillRoundedRect(graphics, panelX + 6, y, PANEL_W - 12, 14, 3, 0x55EF4444);
            GuiRenderUtils.drawRoundedOutline(graphics, panelX + 6, y, PANEL_W - 12, 14, 3, 0xAAEF4444);
            GuiRenderUtils.drawText(graphics, font, "⚠ Sky beam is obstructed!", panelX + 11, y + 3, 0xFFFCA5A5, false);
            y += 18;
        }

        // Next Tier Progress or Max Tier Completed
        if (tier < 4) {
            int nextLayer = tier + 1;
            int nextNeed = LAYER_TOTALS[nextLayer];
            int have = Math.min(nextNeed, info.nextLayerHave());
            int missing = Math.max(0, nextNeed - have);
            int size = nextLayer * 2 + 1;
            float ratio = nextNeed > 0 ? Math.clamp((float) have / nextNeed, 0.0f, 1.0f) : 0.0f;

            GuiRenderUtils.fillRoundedRect(graphics, panelX + 6, y, PANEL_W - 12, 38, 4, 0x88162032);
            GuiRenderUtils.drawRoundedOutline(graphics, panelX + 6, y, PANEL_W - 12, 38, 4, border);

            String layerLbl = "Layer " + nextLayer + " (" + size + "x" + size + "):";
            String countLbl = have + "/" + nextNeed;
            GuiRenderUtils.drawText(graphics, font, layerLbl, panelX + 11, y + 4, 0xFFE2E8F0, false);
            GuiRenderUtils.drawText(graphics, font, countLbl, panelX + PANEL_W - 11 - font.width(countLbl), y + 4, accent, false);

            int barX = panelX + 11;
            int barW = PANEL_W - 22;
            int barY = y + 16;
            GuiRenderUtils.fillRoundedRect(graphics, barX, barY, barW, 5, 2, 0xFF0A101E);
            int fillW = Math.round(barW * ratio);
            if (fillW > 0) {
                GuiRenderUtils.fillRoundedRect(graphics, barX, barY, fillW, 5, 2, accent);
            }

            int remainingForMax = Math.max(missing, FULL_PYRAMID_BLOCKS - info.totalHave());
            String needStr = tier < 3
                    ? ("Need " + missing + " more (" + remainingForMax + " to T4)")
                    : ("Need " + missing + " more for Max T4");
            GuiRenderUtils.drawText(graphics, font, font.plainSubstrByWidth(needStr, PANEL_W - 22), panelX + 11, y + 25, 0xFFFBBF24, false);
            y += 44;
        } else {
            GuiRenderUtils.fillRoundedRect(graphics, panelX + 6, y, PANEL_W - 12, 20, 4, 0x3310B981);
            GuiRenderUtils.drawRoundedOutline(graphics, panelX + 6, y, PANEL_W - 12, 20, 4, 0x8834D399);
            GuiRenderUtils.drawText(graphics, font, "✓ Max Tier 4 Complete (164/164)", panelX + 11, y + 6, 0xFF6EE7B7, false);
            y += 26;
        }

        // Active Effects Summary
        if (y + 30 <= panelY + panelH - 2) {
            GuiRenderUtils.fillRoundedRect(graphics, panelX + 6, y, PANEL_W - 12, 30, 4, 0x880F172A);
            GuiRenderUtils.drawRoundedOutline(graphics, panelX + 6, y, PANEL_W - 12, 30, 4, border);

            String primaryStr = "None";
            String secondaryStr = "None";
            if (primary != null) {
                String pName = primary.value().getDisplayName().getString();
                if (secondary != null && primary.equals(secondary)) {
                    primaryStr = pName + " II";
                    secondaryStr = "Boosted Primary";
                } else {
                    primaryStr = pName + " I";
                    if (secondary != null) {
                        secondaryStr = secondary.value().getDisplayName().getString() + " I";
                    }
                }
            } else if (secondary != null) {
                secondaryStr = secondary.value().getDisplayName().getString() + " I";
            }

            GuiRenderUtils.drawText(graphics, font, "Primary: ", panelX + 11, y + 5, 0xFF94A3B8, false);
            GuiRenderUtils.drawText(graphics, font, font.plainSubstrByWidth(primaryStr, PANEL_W - 64),
                    panelX + 11 + font.width("Primary: "), y + 5, primary != null ? 0xFF38BDF8 : 0xFF64748B, false);

            GuiRenderUtils.drawText(graphics, font, "Secondary: ", panelX + 11, y + 17, 0xFF94A3B8, false);
            GuiRenderUtils.drawText(graphics, font, font.plainSubstrByWidth(secondaryStr, PANEL_W - 76),
                    panelX + 11 + font.width("Secondary: "), y + 17, secondary != null ? 0xFFA78BFA : 0xFF64748B, false);
        }
    }

    // -------------------------------------------------------------------------
    // 2. In-World 3D Beacon Coverage Boundary & Floating Labels
    // -------------------------------------------------------------------------

    public static void renderWorld(GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (client == null || client.level == null || client.player == null || mod == null) return;
        boolean showRange = getBool(mod, "Show 3D Range Box", true);
        boolean showLabel = getBool(mod, "Show Beacon Label", true);
        if (!showRange && !showLabel) return;

        refreshBeacons(client, mod);
        if (CACHED_BEACONS.isEmpty()) return;

        GameRenderer gr = client.gameRenderer;
        int sw = extractor.guiWidth();
        int sh = extractor.guiHeight();

        boolean useClientCol = getBool(mod, "Use Client Color", true);
        float thickness = (float) getNum(mod, "Line Thickness", 1.5);

        int baseCol = 0xFF38BDF8;
        if (useClientCol && StarClient.getInstance() != null && StarClient.getInstance().getClientSettings() != null) {
            baseCol = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        } else if (mod.getSetting("Color") instanceof ColorModeSetting cms) {
            baseCol = cms.getColor();
        }
        int bgColor = StarClient.getInstance() != null && StarClient.getInstance().getClientSettings() != null
                ? StarClient.getInstance().getClientSettings().getMenuBackgroundColor()
                : 0xFF0F172A;

        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();

        for (BeaconScan beacon : CACHED_BEACONS) {
            BlockPos pos = beacon.pos();
            int tier = beacon.tier();
            int radius = beacon.radius();

            boolean inside = tier > 0 && beacon.beamActive()
                    && px >= pos.getX() - radius && px <= pos.getX() + 1 + radius
                    && pz >= pos.getZ() - radius && pz <= pos.getZ() + 1 + radius
                    && py >= pos.getY() - radius;

            int stateCol = !beacon.beamActive() && tier > 0 ? 0xFFF87171
                    : (inside ? 0xFF34D399 : baseCol);
            int lineCol = (baseCol & 0x00FFFFFF) | 0xCC000000;
            int blockBoxCol = (stateCol & 0x00FFFFFF) | 0xEE000000;

            if (showRange) {
                // Beacon block highlight box
                AABB blockBox = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0).inflate(0.006);
                EspRenderUtil.drawBox3D(extractor, gr, blockBox, sw, sh, thickness, blockBoxCol);

                if (tier > 0 && radius > 0) {
                    double minX = pos.getX() - radius;
                    double maxX = pos.getX() + 1.0 + radius;
                    double minZ = pos.getZ() - radius;
                    double maxZ = pos.getZ() + 1.0 + radius;

                    // Horizontal perimeter at beacon elevation
                    double beaconRingY = pos.getY() + 0.06;
                    drawSubdividedSquare(extractor, gr, minX, maxX, minZ, maxZ, beaconRingY, sw, sh, thickness, lineCol);

                    // Corner vertical guide posts (3 blocks tall)
                    int cornerCol = (stateCol & 0x00FFFFFF) | 0xBB000000;
                    drawCornerPost(extractor, gr, minX, beaconRingY, minZ, 3.0, sw, sh, thickness, cornerCol);
                    drawCornerPost(extractor, gr, maxX, beaconRingY, minZ, 3.0, sw, sh, thickness, cornerCol);
                    drawCornerPost(extractor, gr, maxX, beaconRingY, maxZ, 3.0, sw, sh, thickness, cornerCol);
                    drawCornerPost(extractor, gr, minX, beaconRingY, maxZ, 3.0, sw, sh, thickness, cornerCol);

                    // Second faint perimeter at player elevation if player is well above or below beacon
                    if (Math.abs(py - pos.getY()) > 3.5 && py >= pos.getY() - radius) {
                        int playerRingCol = (stateCol & 0x00FFFFFF) | 0x77000000;
                        drawSubdividedSquare(extractor, gr, minX, maxX, minZ, maxZ, py + 0.08, sw, sh, Math.max(1.0f, thickness - 0.3f), playerRingCol);
                    }
                }
            }

            if (showLabel) {
                Vec3 labelPos = EspRenderUtil.projectToScreen(gr, new Vec3(pos.getX() + 0.5, pos.getY() + 1.45, pos.getZ() + 0.5), sw, sh);
                if (labelPos == null) continue;

                String text;
                if (tier == 0) {
                    text = "⚡ Beacon T0 • No Pyramid";
                } else if (!beacon.beamActive()) {
                    text = "⚡ Beacon T" + tier + " • ⚠ Sky Blocked";
                } else {
                    text = "⚡ Beacon T" + tier + " • ±" + radius + "m • " + (inside ? "Inside" : "Outside");
                }

                int tw = client.font.width(text);
                int pillW = tw + 10;
                int pillH = 13;
                int lx = (int) Math.round(labelPos.x) - pillW / 2;
                int ly = (int) Math.round(labelPos.y) - pillH / 2;

                GuiRenderUtils.fillRoundedRect(extractor, lx, ly, pillW, pillH, 3, (bgColor & 0x00FFFFFF) | 0xDC000000);
                GuiRenderUtils.drawRoundedOutline(extractor, lx, ly, pillW, pillH, 3, (stateCol & 0x00FFFFFF) | 0xAA000000);
                extractor.text(client.font, text, lx + 5, ly + 2, inside ? 0xFFECFDF5 : 0xFFE2E8F0, true);
            }
        }
    }

    private static void drawSubdividedSquare(GuiGraphicsExtractor extractor, GameRenderer gr,
                                             double minX, double maxX, double minZ, double maxZ, double y,
                                             int sw, int sh, float thickness, int color) {
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            double t0 = (double) i / steps;
            double t1 = (double) (i + 1) / steps;
            double x0 = minX + (maxX - minX) * t0;
            double x1 = minX + (maxX - minX) * t1;
            double z0 = minZ + (maxZ - minZ) * t0;
            double z1 = minZ + (maxZ - minZ) * t1;

            EspRenderUtil.drawSegment3D(extractor, gr, new Vec3(x0, y, minZ), new Vec3(x1, y, minZ), sw, sh, thickness, color);
            EspRenderUtil.drawSegment3D(extractor, gr, new Vec3(x0, y, maxZ), new Vec3(x1, y, maxZ), sw, sh, thickness, color);
            EspRenderUtil.drawSegment3D(extractor, gr, new Vec3(minX, y, z0), new Vec3(minX, y, z1), sw, sh, thickness, color);
            EspRenderUtil.drawSegment3D(extractor, gr, new Vec3(maxX, y, z0), new Vec3(maxX, y, z1), sw, sh, thickness, color);
        }
    }

    private static void drawCornerPost(GuiGraphicsExtractor extractor, GameRenderer gr,
                                       double x, double y, double z, double height,
                                       int sw, int sh, float thickness, int color) {
        EspRenderUtil.drawSegment3D(extractor, gr, new Vec3(x, y, z), new Vec3(x, y + height, z), sw, sh, thickness, color);
    }

    private static void refreshBeacons(Minecraft client, Module mod) {
        Level level = client.level;
        if (level == null || client.player == null) {
            CACHED_BEACONS.clear();
            return;
        }

        int range = (int) Math.round(getNum(mod, "Scan Range", 96.0));
        BlockPos playerPos = client.player.blockPosition();
        long gameTime = level.getGameTime();

        if (playerPos.equals(lastPlayerPos)
                && Math.abs(gameTime - lastScanTick) < 15L
                && range == lastRange) {
            return;
        }

        lastPlayerPos = playerPos.immutable();
        lastScanTick = gameTime;
        lastRange = range;
        CACHED_BEACONS.clear();

        int chunkRadius = (range >> 4) + 1;
        int pcx = client.player.chunkPosition().x();
        int pcz = client.player.chunkPosition().z();
        double rangeSqr = (double) range * range;

        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                LevelChunk chunk = level.getChunk(pcx + cx, pcz + cz);
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (!(entry.getValue() instanceof BeaconBlockEntity)) continue;
                    BlockPos pos = entry.getKey();
                    double distSqr = client.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distSqr > rangeSqr) continue;

                    PyramidInfo info = inspectPyramid(level, pos);
                    int radius = info.tier() > 0 ? (info.tier() * 10 + 10) : 0;
                    CACHED_BEACONS.add(new BeaconScan(pos.immutable(), info.tier(), radius, info.beamActive()));
                }
            }
        }
    }

    private static BlockPos findActiveBeacon(Minecraft client) {
        if (client.level == null || client.player == null) return null;
        if (client.hitResult instanceof BlockHitResult bhr) {
            BlockPos hitPos = bhr.getBlockPos();
            if (client.level.getBlockState(hitPos).is(Blocks.BEACON)) {
                return hitPos;
            }
        }
        BlockPos center = client.player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -6; dx <= 6; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (client.level.getBlockState(p).is(Blocks.BEACON)) {
                        double d = p.distToCenterSqr(client.player.getX(), client.player.getY(), client.player.getZ());
                        if (d < bestDist) {
                            bestDist = d;
                            best = p.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    private static PyramidInfo inspectPyramid(Level level, BlockPos beaconPos) {
        int completeTier = 0;
        int totalHave = 0;
        int nextHave = 0;
        int nextTotal = LAYER_TOTALS[1];
        boolean stoppedContiguous = false;

        for (int layer = 1; layer <= 4; layer++) {
            int y = beaconPos.getY() - layer;
            int layerCount = 0;
            int needed = LAYER_TOTALS[layer];
            if (y >= level.getMinY()) {
                for (int dx = -layer; dx <= layer; dx++) {
                    for (int dz = -layer; dz <= layer; dz++) {
                        BlockState st = level.getBlockState(beaconPos.offset(dx, -layer, dz));
                        if (st.is(BlockTags.BEACON_BASE_BLOCKS)) {
                            layerCount++;
                        }
                    }
                }
            }
            totalHave += layerCount;
            if (!stoppedContiguous) {
                if (layerCount == needed) {
                    completeTier = layer;
                } else {
                    nextHave = layerCount;
                    nextTotal = needed;
                    stoppedContiguous = true;
                }
            }
        }

        if (completeTier == 4) {
            nextHave = LAYER_TOTALS[4];
            nextTotal = LAYER_TOTALS[4];
        }

        boolean beamActive = false;
        BlockEntity be = level.getBlockEntity(beaconPos);
        if (be instanceof BeaconBlockEntity bbe) {
            beamActive = !bbe.getBeamSections().isEmpty();
        }
        if (!beamActive && completeTier > 0) {
            // Fallback sky-check when client hasn't ticked beam sections yet
            beamActive = level.canSeeSky(beaconPos.above());
        }

        return new PyramidInfo(completeTier, nextHave, nextTotal, totalHave, beamActive);
    }

    private static boolean getBool(Module mod, String name, boolean def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }

    private static double getNum(Module mod, String name, double def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s instanceof NumberSetting ns ? ns.getValue() : def;
    }
}
