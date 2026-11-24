package net.starclient.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.friend.FriendManager;
import net.starclient.module.BlockOutlineState;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Apex/Shooter-style 3D Tactical Ping Marker (WORLD module):
 * Press the configured hotkey (default Z) to cast a long-range crosshair ping up to 150+ blocks,
 * automatically identifying enemies, allies, primed TNT, beds, loot containers, ores, or locations.
 */
public final class TacticalPingRenderer {

    public enum PingCategory {
        ENEMY("⚠", 0xFFEF4444, true),
        ALLY("🛡", 0xFF34D399, false),
        MOB("⚔", 0xFFFB923C, true),
        HAZARD("🧨", 0xFFEF4444, true),
        BED("🛏", 0xFFF43F5E, false),
        LOOT("📦", 0xFFFACC15, false),
        RESOURCE("💎", 0xFF22D3EE, false),
        SPAWNER("🕷", 0xFFA78BFA, false),
        LOCATION("📍", 0xFF38BDF8, false);

        final String icon;
        final int defaultColor;
        final boolean urgentSound;

        PingCategory(String icon, int defaultColor, boolean urgentSound) {
            this.icon = icon;
            this.defaultColor = defaultColor;
            this.urgentSound = urgentSound;
        }
    }

    private static final class PingEntry {
        final PingCategory category;
        final String displayLabel;
        final String chatLabel;
        final BlockPos blockPos;
        final int trackedEntityId;
        final int fixedColor;
        Vec3 worldPos;
        AABB highlightBox;
        long createdAtMs;
        long durationMs;
        boolean entityLost;

        PingEntry(PingCategory category, String displayLabel, String chatLabel, Vec3 worldPos,
                  BlockPos blockPos, AABB highlightBox, int trackedEntityId, int fixedColor,
                  long createdAtMs, long durationMs) {
            this.category = category;
            this.displayLabel = displayLabel;
            this.chatLabel = chatLabel;
            this.worldPos = worldPos;
            this.blockPos = blockPos;
            this.highlightBox = highlightBox;
            this.trackedEntityId = trackedEntityId;
            this.fixedColor = fixedColor;
            this.createdAtMs = createdAtMs;
            this.durationMs = durationMs;
        }
    }

    private static final List<PingEntry> ACTIVE_PINGS = new ArrayList<>();

    private TacticalPingRenderer() {
    }

    public static synchronized void clear() {
        ACTIVE_PINGS.clear();
    }

    public static synchronized void onTick(Minecraft client, Module mod) {
        if (mod == null) return;

        if (mod.getSetting("Clear All Pings") instanceof BooleanSetting bs && bs.getValue()) {
            ACTIVE_PINGS.clear();
            bs.setValue(false);
        }

        if (client == null || client.level == null || client.player == null || !mod.isEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        Level level = client.level;
        Iterator<PingEntry> it = ACTIVE_PINGS.iterator();
        while (it.hasNext()) {
            PingEntry entry = it.next();
            if (now - entry.createdAtMs >= entry.durationMs) {
                it.remove();
                continue;
            }
            if (entry.trackedEntityId != -1 && !entry.entityLost) {
                Entity tracked = level.getEntity(entry.trackedEntityId);
                if (tracked != null && tracked.isAlive()) {
                    AABB box = tracked.getBoundingBox();
                    entry.worldPos = box.getCenter();
                    entry.highlightBox = box;
                } else {
                    entry.entityLost = true;
                }
            }
        }
    }

    public static synchronized void onKeyPress(Minecraft client, Module mod) {
        if (client == null || client.player == null || client.level == null || mod == null || !mod.isEnabled()) {
            return;
        }
        if (client.gui.screen() != null) {
            return;
        }

        // Sneak + Ping Key clears all active tactical pings cleanly
        if (client.player.isShiftKeyDown() && !ACTIVE_PINGS.isEmpty()) {
            ACTIVE_PINGS.clear();
            if (getBool(mod, "Sound Effect", true)) {
                client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.85F));
            }
            ToastNotificationManager.pushToast("tactical_ping", "Tactical Ping", "Cleared all active markers", "CLEAR", 0xFF94A3B8, 1600L);
            return;
        }

        double maxRange = getNum(mod, "Max Range", 150.0);
        long durationMs = Math.round(getNum(mod, "Duration (s)", 15.0) * 1000.0);
        int maxPings = Math.max(1, (int) Math.round(getNum(mod, "Max Pings", 4.0)));

        Player player = client.player;
        ClientLevel level = client.level;
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 maxEnd = eyePos.add(lookVec.scale(maxRange));

        // 1. Block raycast along look vector
        BlockHitResult blockHit = level.clip(new ClipContext(
                eyePos, maxEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 limitEnd = maxEnd;
        double closestDistSqr = maxRange * maxRange;
        if (blockHit.getType() != HitResult.Type.MISS) {
            limitEnd = blockHit.getLocation();
            closestDistSqr = eyePos.distanceToSqr(limitEnd);
        }

        // 2. Entity raycast up to block obstruction
        Entity hitEntity = null;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == null || !entity.isAlive() || entity == player || entity.isSpectator()) continue;
            if (entity instanceof ArmorStand) continue;
            if (entity instanceof AbstractClientPlayer acp && BaseAlertRenderer.isNpcOrShopkeeper(client, acp)) {
                continue;
            }
            AABB paddedBox = entity.getBoundingBox().inflate(0.30);
            Optional<Vec3> clip = paddedBox.clip(eyePos, limitEnd);
            if (clip.isPresent()) {
                double d2 = eyePos.distanceToSqr(clip.get());
                if (d2 < closestDistSqr) {
                    closestDistSqr = d2;
                    hitEntity = entity;
                }
            }
        }

        long now = System.currentTimeMillis();
        PingEntry created;
        if (hitEntity != null) {
            created = createEntityPing(player, hitEntity, now, durationMs);
        } else if (blockHit.getType() == HitResult.Type.BLOCK) {
            created = createBlockPing(level, blockHit.getBlockPos(), now, durationMs);
        } else {
            return;
        }

        // Refresh if re-pinging the exact same tracked target or block
        for (int i = 0; i < ACTIVE_PINGS.size(); i++) {
            PingEntry existing = ACTIVE_PINGS.get(i);
            boolean sameEntity = created.trackedEntityId != -1 && created.trackedEntityId == existing.trackedEntityId;
            boolean sameBlock = created.blockPos != null && created.blockPos.equals(existing.blockPos);
            if (sameEntity || sameBlock) {
                ACTIVE_PINGS.set(i, created);
                playPingEffects(client, mod, created);
                return;
            }
        }

        ACTIVE_PINGS.add(created);
        while (ACTIVE_PINGS.size() > maxPings) {
            ACTIVE_PINGS.removeFirst();
        }
        playPingEffects(client, mod, created);
    }

    private static PingEntry createEntityPing(Player localPlayer, Entity target, long now, long durationMs) {
        AABB box = target.getBoundingBox();
        Vec3 center = box.getCenter();
        int entityId = target.getId();

        if (target instanceof AbstractClientPlayer otherPlayer) {
            String name = ChatFormatting.stripFormatting(otherPlayer.getName().getString());
            if (name == null || name.isBlank()) name = "Player";
            boolean friend = FriendManager.isFriend(otherPlayer);
            boolean ally = BaseAlertRenderer.isTeammate(localPlayer, otherPlayer);
            if (friend) {
                return new PingEntry(PingCategory.ALLY, "♥ Friend: " + name, "Friend: " + name,
                        center, null, box, entityId, 0xFF34D399, now, durationMs);
            }
            if (ally) {
                return new PingEntry(PingCategory.ALLY, "🛡 Ally: " + name, "Ally: " + name,
                        center, null, box, entityId, 0xFF34D399, now, durationMs);
            }
            return new PingEntry(PingCategory.ENEMY, "⚠ Enemy: " + name, "Enemy: " + name,
                    center, null, box, entityId, 0xFFEF4444, now, durationMs);
        }

        if (target instanceof PrimedTnt) {
            return new PingEntry(PingCategory.HAZARD, "🧨 Primed TNT", "Primed TNT",
                    center, null, box, entityId, 0xFFEF4444, now, durationMs);
        }

        if (target instanceof EndCrystal || FireballAlertRenderer.isFireball(target)) {
            String hazardName = target instanceof EndCrystal ? "End Crystal" : "Fireball";
            return new PingEntry(PingCategory.HAZARD, "🔥 " + hazardName, hazardName,
                    center, null, box, entityId, 0xFFEF4444, now, durationMs);
        }

        if (target instanceof ItemEntity itemEntity) {
            String itemName = itemEntity.getItem().getHoverName().getString();
            return new PingEntry(PingCategory.LOOT, "📦 " + itemName, "Item: " + itemName,
                    center, null, box, entityId, 0xFFFACC15, now, durationMs);
        }

        String entityName = ChatFormatting.stripFormatting(target.getName().getString());
        if (entityName == null || entityName.isBlank()) entityName = "Target";
        PingCategory cat = (target instanceof LivingEntity) ? PingCategory.MOB : PingCategory.LOCATION;
        return new PingEntry(cat, cat.icon + " " + entityName, entityName,
                center, null, box, entityId, cat.defaultColor, now, durationMs);
    }

    private static PingEntry createBlockPing(Level level, BlockPos pos, long now, long durationMs) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        String rawName = block.getName().getString();
        Vec3 center = Vec3.atCenterOf(pos);
        AABB box = new AABB(pos).inflate(0.01);

        if (block instanceof BedBlock bedBlock) {
            int rawRgb = bedBlock.getColor().getTextColor();
            int bedColor = brightenDarkColor(rawRgb);
            boolean exposed = isBlockExposedToAir(level, pos);
            String title = "🛏 " + rawName + (exposed ? " (Exposed)" : "");
            String chatTitle = rawName + (exposed ? " (Exposed)" : "");
            return new PingEntry(PingCategory.BED, title, chatTitle, center, pos.immutable(), box, -1, bedColor, now, durationMs);
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                || be instanceof EnderChestBlockEntity || be instanceof ShulkerBoxBlockEntity) {
            return new PingEntry(PingCategory.LOOT, "📦 " + rawName, rawName,
                    center, pos.immutable(), box, -1, 0xFFFACC15, now, durationMs);
        }

        if (be instanceof SpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity || be instanceof VaultBlockEntity) {
            return new PingEntry(PingCategory.SPAWNER, "🕷 " + rawName, rawName,
                    center, pos.immutable(), box, -1, 0xFFA78BFA, now, durationMs);
        }

        if (isValuableResourceBlock(block)) {
            return new PingEntry(PingCategory.RESOURCE, "💎 " + rawName, rawName,
                    center, pos.immutable(), box, -1, 0xFF22D3EE, now, durationMs);
        }

        return new PingEntry(PingCategory.LOCATION, "📍 " + rawName, rawName,
                center, pos.immutable(), box, -1, 0xFF38BDF8, now, durationMs);
    }

    private static boolean isValuableResourceBlock(Block block) {
        if (block == Blocks.DIAMOND_BLOCK || block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
                || block == Blocks.EMERALD_BLOCK || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
                || block == Blocks.GOLD_BLOCK || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
                || block == Blocks.IRON_BLOCK || block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.ANCIENT_DEBRIS || block == Blocks.NETHERITE_BLOCK || block == Blocks.END_PORTAL_FRAME) {
            return true;
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
        if (id == null) return false;
        String path = id.getPath();
        return path.endsWith("_ore") || path.equals("ancient_debris");
    }

    private static boolean isBlockExposedToAir(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockState adj = level.getBlockState(pos.relative(dir));
            if (adj.isAir()) return true;
        }
        return false;
    }

    private static int brightenDarkColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int luminance = (r * 3 + g * 6 + b) / 10;
        if (luminance < 75) {
            r = Math.min(255, r + 90);
            g = Math.min(255, g + 90);
            b = Math.min(255, b + 90);
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void playPingEffects(Minecraft client, Module mod, PingEntry entry) {
        if (getBool(mod, "Sound Effect", true)) {
            if (entry.category.urgentSound) {
                client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.45F));
            } else {
                client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.15F));
            }
        }

        String calloutMode = getString(mod, "Chat Callout", "Off");
        if (!"Off".equalsIgnoreCase(calloutMode) && client.player != null) {
            int bx = (int) Math.floor(entry.worldPos.x);
            int by = (int) Math.floor(entry.worldPos.y);
            int bz = (int) Math.floor(entry.worldPos.z);
            int dist = (int) Math.round(client.player.position().distanceTo(entry.worldPos));
            String chatText = "[Ping] " + entry.chatLabel + " at XYZ " + bx + ", " + by + ", " + bz + " (" + dist + "m)";

            if ("Send Chat Message".equalsIgnoreCase(calloutMode)) {
                client.player.connection.sendChat(chatText);
            } else if ("Copy to Clipboard".equalsIgnoreCase(calloutMode)) {
                client.keyboardHandler.setClipboard(chatText);
                ToastNotificationManager.pushToast("ping_copy", "Ping Copied", chatText, "COPY", resolvePingColor(mod, entry), 2200L);
            }
        }
    }

    public static synchronized void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (client == null || client.level == null || client.player == null || mod == null) return;
        if (ACTIVE_PINGS.isEmpty()) return;

        GameRenderer gr = client.gameRenderer;
        int sw = extractor.guiWidth();
        int sh = extractor.guiHeight();
        Font font = client.font;
        boolean shadow = StarClient.getInstance().getClientSettings().isHudTextShadow();
        long now = System.currentTimeMillis();

        boolean showBeam = getBool(mod, "Show Beam", true);
        boolean showCoords = getBool(mod, "Show Coordinates", false);
        boolean offscreenArrows = getBool(mod, "Off-Screen Arrows", true);

        for (PingEntry entry : ACTIVE_PINGS) {
            long ageMs = Math.max(0L, now - entry.createdAtMs);
            long remainingMs = Math.max(0L, entry.durationMs - ageMs);
            float lifeFraction = entry.durationMs > 0 ? Mth.clamp(remainingMs / (float) entry.durationMs, 0.0F, 1.0F) : 1.0F;
            float fadeOutAlpha = remainingMs < 600L ? Mth.clamp(remainingMs / 600.0F, 0.0F, 1.0F) : 1.0F;
            if (fadeOutAlpha <= 0.03F) continue;

            int baseColor = resolvePingColor(mod, entry);
            Vec3 center = entry.worldPos;
            double dist = client.player.position().distanceTo(center);
            int distMeters = (int) Math.round(dist);

            // 1. Expanding pulse ring on placement (first 550ms)
            if (ageMs < 550L) {
                float p = ageMs / 550.0F;
                double ringRadius = 0.45 + p * 1.45;
                int ringAlpha = Math.round((1.0F - p) * 210 * fadeOutAlpha);
                renderHorizontalCircle(extractor, gr, center.x,
                        (entry.highlightBox != null ? entry.highlightBox.minY : center.y) + 0.05,
                        center.z, ringRadius, sw, sh, 2.0F, GuiRenderUtils.withAlpha(baseColor, ringAlpha));
            }

            // 2. Vertical 3D beacon beam
            if (showBeam) {
                int beamOuter = GuiRenderUtils.withAlpha(baseColor, Math.round(70 * fadeOutAlpha));
                int beamCore = GuiRenderUtils.withAlpha(baseColor, Math.round(165 * fadeOutAlpha));
                double minY = entry.highlightBox != null ? entry.highlightBox.minY : center.y;
                Vec3 bottom = new Vec3(center.x, minY, center.z);
                Vec3 top = new Vec3(center.x, Math.min(320.0, minY + 42.0), center.z);
                EspRenderUtil.drawSegment3D(extractor, gr, bottom, top, sw, sh, 3.0F, beamOuter);
                EspRenderUtil.drawSegment3D(extractor, gr, bottom, top, sw, sh, 1.2F, beamCore);
            }

            // 3. 3D Highlight Box around target block/entity
            if (entry.highlightBox != null) {
                int boxColor = GuiRenderUtils.withAlpha(baseColor, Math.round(210 * fadeOutAlpha));
                EspRenderUtil.drawBox3D(extractor, gr, entry.highlightBox, sw, sh, 1.6F, boxColor);
            }

            // 4. Floating In-World Pill Tag or Off-Screen Edge Chevron
            double boxHeight = entry.highlightBox != null ? (entry.highlightBox.maxY - entry.highlightBox.minY) : 1.0;
            Vec3 anchorWorld = new Vec3(center.x, center.y + boxHeight * 0.5 + 0.48, center.z);
            Vec3 projected = EspRenderUtil.projectToScreen(gr, anchorWorld, sw, sh);

            boolean inViewport = projected != null
                    && projected.x >= 10 && projected.x <= sw - 10
                    && projected.y >= 10 && projected.y <= sh - 10;

            if (inViewport) {
                renderOnScreenBadge(extractor, font, entry, center, distMeters, projected,
                        baseColor, ageMs, lifeFraction, fadeOutAlpha, showCoords, shadow);
            } else if (offscreenArrows) {
                renderOffScreenArrow(extractor, client, gr, font, entry, center, distMeters,
                        sw, sh, baseColor, fadeOutAlpha, shadow);
            }
        }
    }

    private static void renderOnScreenBadge(GuiGraphicsExtractor extractor, Font font, PingEntry entry, Vec3 center,
                                            int distMeters, Vec3 projected, int baseColor,
                                            long ageMs, float lifeFraction, float fadeOutAlpha,
                                            boolean showCoords, boolean shadow) {
        String distText = distMeters + "m";
        String title = entry.displayLabel + (entry.entityLost ? " (Last Seen)" : "");
        String coordsLine = showCoords
                ? String.format(Locale.ROOT, "[%d, %d, %d]", (int) Math.floor(center.x), (int) Math.floor(center.y), (int) Math.floor(center.z))
                : null;

        int titleW = font.width(title);
        int distW = font.width(distText);
        int topRowW = titleW + 6 + distW;
        int coordsW = coordsLine != null ? font.width(coordsLine) : 0;
        int pillW = Math.max(topRowW, coordsW) + 16;
        int pillH = coordsLine != null ? 26 : 16;

        float popScale = ageMs < 180L
                ? (0.78F + 0.22F * (1.0F - (1.0F - ageMs / 180.0F) * (1.0F - ageMs / 180.0F)))
                : 1.0F;

        Matrix3x2fStack pose = extractor.pose();
        pose.pushMatrix();
        pose.translate((float) projected.x, (float) projected.y);
        pose.scale(popScale, popScale);

        int left = -pillW / 2;
        int top = -pillH - 5;
        int bgCol = GuiRenderUtils.withAlpha(0xFF0B1120, Math.round(212 * fadeOutAlpha));
        int outlineCol = GuiRenderUtils.withAlpha(baseColor, Math.round(215 * fadeOutAlpha));
        int textAlpha = Math.round(255 * fadeOutAlpha);

        // Pointer stem diamond beneath tag
        extractor.fill(-2, -4, 2, 0, outlineCol);
        extractor.fill(-1, -5, 1, 1, GuiRenderUtils.withAlpha(0xFFFFFFFF, textAlpha));

        GuiRenderUtils.fillRoundedRect(extractor, left, top, pillW, pillH, 4, bgCol);
        GuiRenderUtils.drawRoundedOutline(extractor, left, top, pillW, pillH, 4, outlineCol);

        // Left colored marker bar
        GuiRenderUtils.fillRoundedRect(extractor, left + 3, top + 3, 3, pillH - 6, 1, outlineCol);

        // Title + Distance
        extractor.text(font, title, left + 9, top + 4, GuiRenderUtils.withAlpha(0xFFF8FAFC, textAlpha), shadow);
        extractor.text(font, distText, left + pillW - distW - 5, top + 4, outlineCol, shadow);

        if (coordsLine != null) {
            extractor.text(font, coordsLine, left + 9, top + 14, GuiRenderUtils.withAlpha(0xFF94A3B8, textAlpha), shadow);
        }

        // Countdown expiry bar at bottom edge
        int barW = Math.max(1, Math.round((pillW - 8) * lifeFraction));
        extractor.fill(left + 4, top + pillH - 2, left + 4 + barW, top + pillH - 1, outlineCol);

        pose.popMatrix();
    }

    private static void renderOffScreenArrow(GuiGraphicsExtractor extractor, Minecraft client, GameRenderer gr, Font font,
                                             PingEntry entry, Vec3 worldCenter, int distMeters,
                                             int sw, int sh, int baseColor, float fadeOutAlpha, boolean shadow) {
        Camera camera = gr.mainCamera();
        Vec3 camPos = camera.position();
        Vector3fc fwd = camera.forwardVector();

        double fx = fwd.x();
        double fy = fwd.y();
        double fz = fwd.z();

        double rx = -fz;
        double rz = fx;
        double rLen = Math.hypot(rx, rz);
        if (rLen < 1e-4) {
            double yawRad = Math.toRadians(client.player.getYRot());
            rx = -Math.cos(yawRad);
            rz = -Math.sin(yawRad);
            rLen = Math.hypot(rx, rz);
        }
        rx /= rLen;
        rz /= rLen;

        double ux = -rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy;
        double uLen = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (uLen > 1e-4) {
            ux /= uLen;
            uy /= uLen;
            uz /= uLen;
        }

        double tx = worldCenter.x - camPos.x;
        double ty = worldCenter.y - camPos.y;
        double tz = worldCenter.z - camPos.z;
        double tLen = Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tLen > 1e-4) {
            tx /= tLen;
            ty /= tLen;
            tz /= tLen;
        }

        double sxDir = tx * rx + tz * rz;
        double syDir = -(tx * ux + ty * uy + tz * uz);
        if (Math.hypot(sxDir, syDir) < 1e-4) {
            syDir = 1.0;
        }

        double halfW = Math.max(24.0, sw * 0.5 - 38.0);
        double halfH = Math.max(20.0, sh * 0.5 - 24.0);
        double scaleX = Math.abs(sxDir) > 1e-5 ? halfW / Math.abs(sxDir) : Double.MAX_VALUE;
        double scaleY = Math.abs(syDir) > 1e-5 ? halfH / Math.abs(syDir) : Double.MAX_VALUE;
        double scale = Math.min(scaleX, scaleY);

        int px = (int) Math.round(sw * 0.5 + sxDir * scale);
        int py = (int) Math.round(sh * 0.5 + syDir * scale);

        String arrow;
        if (Math.abs(sxDir) > Math.abs(syDir) * 1.4) {
            arrow = sxDir > 0 ? "▶" : "◀";
        } else if (Math.abs(syDir) > Math.abs(sxDir) * 1.4) {
            arrow = syDir > 0 ? "▼" : "▲";
        } else if (sxDir > 0) {
            arrow = syDir > 0 ? "↘" : "↗";
        } else {
            arrow = syDir > 0 ? "↙" : "↖";
        }

        String label = arrow + " " + entry.category.icon + " " + distMeters + "m";
        int lw = font.width(label) + 10;
        int lh = 14;
        int bx = Math.clamp(px - lw / 2, 6, Math.max(6, sw - lw - 6));
        int by = Math.clamp(py - lh / 2, 6, Math.max(6, sh - lh - 6));

        int bgCol = GuiRenderUtils.withAlpha(0xFF0B1120, Math.round(200 * fadeOutAlpha));
        int borderCol = GuiRenderUtils.withAlpha(baseColor, Math.round(220 * fadeOutAlpha));
        int textAlpha = Math.round(255 * fadeOutAlpha);

        GuiRenderUtils.fillRoundedRect(extractor, bx, by, lw, lh, 4, bgCol);
        GuiRenderUtils.drawRoundedOutline(extractor, bx, by, lw, lh, 4, borderCol);
        extractor.text(font, label, bx + 5, by + 3, GuiRenderUtils.withAlpha(0xFFF8FAFC, textAlpha), shadow);
    }

    private static void renderHorizontalCircle(GuiGraphicsExtractor extractor, GameRenderer gr,
                                               double cx, double cy, double cz, double r,
                                               int sw, int sh, float thickness, int color) {
        int segments = 24;
        Vec3 first = null;
        Vec3 prev = null;
        for (int i = 0; i <= segments; i++) {
            double angle = (i % segments) * (Math.PI * 2.0 / segments);
            Vec3 worldPt = new Vec3(cx + Math.cos(angle) * r, cy, cz + Math.sin(angle) * r);
            Vec3 sPos = EspRenderUtil.projectToScreen(gr, worldPt, sw, sh);
            if (prev != null && sPos != null) {
                EspRenderUtil.drawLine(extractor, prev.x, prev.y, sPos.x, sPos.y, thickness, color);
            }
            if (i == 0) first = sPos;
            prev = sPos;
        }
        if (prev != null && first != null) {
            EspRenderUtil.drawLine(extractor, prev.x, prev.y, first.x, first.y, thickness, color);
        }
    }

    private static int resolvePingColor(Module mod, PingEntry entry) {
        if (entry.category == PingCategory.LOCATION && getBool(mod, "Use Client Color", false)) {
            if (StarClient.getInstance() != null && StarClient.getInstance().getClientSettings() != null) {
                return StarClient.getInstance().getClientSettings().getMenuAccentColor();
            }
        }
        if (entry.category == PingCategory.LOCATION && mod.getSetting("Color") instanceof ColorModeSetting cms) {
            return cms.getColor();
        }
        return entry.fixedColor;
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

    private static String getString(Module mod, String name, String def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s instanceof ModeSetting ms ? ms.getValue() : def;
    }
}
