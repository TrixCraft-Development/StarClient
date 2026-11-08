package net.starclient.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.friend.FriendManager;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Base Alert module (SERVER category):
 * Automatically detects your team's bed on Bedwars match start / initial spawn,
 * tracks your base perimeter, and sends high-urgency visual, 3D, and audio alerts
 * whenever an enemy player approaches your bed!
 */
public final class BaseAlertRenderer {

    public record EnemyThreat(
            AbstractClientPlayer player,
            double distanceToBed,
            double distanceToLocalPlayer,
            String name,
            String heldItemName,
            boolean invisible,
            String directionText
    ) {
    }

    // Cached home bed state
    private static BlockPos homeBedHeadPos = null;
    private static BlockPos homeBedFootPos = null;
    private static Vec3 homeBedCenter = null;
    private static String homeBedColorName = "Unknown";
    private static int homeBedRgb = 0xFFEF4444;
    private static boolean bedLocked = false;
    private static boolean bedAlive = true;
    private static Level lastLevel = null;
    private static long lastScanTick = -100L;
    private static long lastAlertSoundTime = 0L;
    private static boolean hadEnemiesLastTick = false;

    private BaseAlertRenderer() {
    }

    public static void clear() {
        homeBedHeadPos = null;
        homeBedFootPos = null;
        homeBedCenter = null;
        homeBedColorName = "Unknown";
        homeBedRgb = 0xFFEF4444;
        bedLocked = false;
        bedAlive = true;
        lastAlertSoundTime = 0L;
        hadEnemiesLastTick = false;
    }

    public static boolean isBedLocked() {
        return bedLocked && homeBedCenter != null;
    }

    public static Vec3 getHomeBedCenter() {
        return homeBedCenter;
    }

    public static String getHomeBedColorName() {
        return homeBedColorName;
    }

    /**
     * Checks if a player entity is a server NPC, Citizens bot, or Hypixel Bedwars Shopkeeper / Upgrades NPC.
     */
    public static boolean isNpcOrShopkeeper(Minecraft client, AbstractClientPlayer otherPlayer) {
        if (otherPlayer == null) return false;

        // 1. UUID Version 2 check: Citizens and Hypixel NPC engines generate DCE Security (version 2) UUIDs.
        // Real Mojang player accounts are always Version 4 (random) or Version 3 (offline mode).
        UUID uuid = otherPlayer.getUUID();
        if (uuid != null && uuid.version() == 2) {
            return true;
        }

        // 2. Tab List / Connection PlayerInfo check:
        // In multiplayer, real players actively playing in a Bedwars match have a PlayerInfo entry in client.getConnection().
        // On Hypixel, Shopkeepers, Upgrades NPCs, and lobby NPCs are unlisted from tab or have their PlayerInfo removed.
        if (client != null && client.getConnection() != null) {
            var connection = client.getConnection();
            var playerInfo = (uuid != null) ? connection.getPlayerInfo(uuid) : null;
            if (playerInfo == null) {
                return true;
            }

            var listed = connection.getListedOnlinePlayers();
            if (listed != null && !listed.isEmpty() && !listed.contains(playerInfo)) {
                return true;
            }
        }

        // 3. Name & Display Name heuristic check
        String name = otherPlayer.getName().getString();
        String displayName = otherPlayer.getDisplayName() != null ? otherPlayer.getDisplayName().getString() : "";
        String customName = otherPlayer.hasCustomName() && otherPlayer.getCustomName() != null ? otherPlayer.getCustomName().getString() : "";
        String profileName = otherPlayer.getGameProfile() != null ? otherPlayer.getGameProfile().name() : "";

        if (matchesNpcPattern(name) || matchesNpcPattern(displayName) || matchesNpcPattern(customName) || matchesNpcPattern(profileName)) {
            return true;
        }

        // 4. Custom name check (real player entities in vanilla Minecraft never have hasCustomName() == true)
        if (otherPlayer.hasCustomName()) {
            String cleanCustom = ChatFormatting.stripFormatting(customName).toLowerCase(Locale.ROOT).trim();
            if (cleanCustom.contains("shop") || cleanCustom.contains("upgrade") || cleanCustom.contains("click")
                    || cleanCustom.contains("npc") || cleanCustom.contains("buy")) {
                return true;
            }
        }

        return false;
    }

    private static boolean matchesNpcPattern(String rawText) {
        if (rawText == null || rawText.isBlank()) return false;
        String clean = ChatFormatting.stripFormatting(rawText).toLowerCase(Locale.ROOT).trim();
        if (clean.isEmpty()) return false;

        // Exact match keywords
        if (clean.equals("shop") || clean.equals("shopkeeper") || clean.equals("the shopkeeper")
                || clean.equals("upgrades") || clean.equals("upgrade")
                || clean.equals("item shop") || clean.equals("solo upgrades")
                || clean.equals("team upgrades") || clean.equals("solo / doubles upgrades")
                || clean.equals("merchant") || clean.equals("banker")
                || clean.equals("quest master") || clean.equals("delivery man")
                || clean.equals("npc") || clean.equals("villager")) {
            return true;
        }

        // Substring / prefix matches for common NPC patterns
        if (clean.contains("[npc]") || clean.startsWith("[npc") || clean.startsWith("npc ")
                || clean.contains("shopkeeper") || clean.contains("item shop")
                || clean.contains("team upgrade") || clean.contains("solo upgrade")
                || clean.contains("click to") || clean.contains("right click")
                || clean.contains("the keeper") || clean.contains("quick buy")) {
            return true;
        }

        return false;
    }

    /**
     * Checks if two players are on the same team (Hypixel Bedwars, scoreboard teams, or friends).
     */
    public static boolean isTeammate(Player localPlayer, Player otherPlayer) {
        if (localPlayer == null || otherPlayer == null || localPlayer == otherPlayer) return true;

        // 1. Scoreboard team alliance / match
        if (localPlayer.getTeam() != null && otherPlayer.getTeam() != null) {
            if (localPlayer.getTeam().isAlliedTo(otherPlayer.getTeam())) return true;
            if (localPlayer.getTeam().getName().equalsIgnoreCase(otherPlayer.getTeam().getName())) return true;
            if (localPlayer.getTeam().getColor() != null && localPlayer.getTeam().getColor() == otherPlayer.getTeam().getColor()) {
                return true;
            }
        }

        // 2. Client Friends system
        if (FriendManager.isFriend(otherPlayer)) {
            return true;
        }

        // 3. Name prefix / team color code (e.g. §c[R] for Red team)
        String localPrefix = getTeamColorPrefix(localPlayer);
        String otherPrefix = getTeamColorPrefix(otherPlayer);
        if (!localPrefix.isEmpty() && !otherPrefix.isEmpty() && localPrefix.equalsIgnoreCase(otherPrefix)) {
            return true;
        }

        return false;
    }

    private static String getTeamColorPrefix(Player player) {
        Component displayName = player.getDisplayName();
        if (displayName != null) {
            String str = displayName.getString();
            int idx = str.indexOf('§');
            if (idx != -1 && idx + 1 < str.length()) {
                return str.substring(idx, idx + 2);
            }
        }
        return "";
    }

    private static void playAlertSound(Minecraft client, String soundType, float pitch) {
        if (client.player == null) return;
        SoundEvent se = switch (soundType) {
            case "Bell" -> SoundEvents.NOTE_BLOCK_BELL.value();
            case "Pling" -> SoundEvents.NOTE_BLOCK_PLING.value();
            case "Bass" -> SoundEvents.NOTE_BLOCK_BASS.value();
            case "Ding" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            default -> SoundEvents.ANVIL_LAND;
        };
        client.getSoundManager().play(SimpleSoundInstance.forUI(se, pitch));
    }

    /**
     * Scans for nearby beds and auto-locks onto the closest bed if not already locked.
     */
    private static void updateBedDetection(Minecraft client, Level level, boolean autoDetect) {
        if (client.player == null || level == null) return;

        if (level != lastLevel) {
            clear();
            lastLevel = level;
        }

        // If locked, verify if the bed block is still alive
        if (bedLocked && homeBedHeadPos != null) {
            BlockState headState = level.getBlockState(homeBedHeadPos);
            BlockState footState = (homeBedFootPos != null) ? level.getBlockState(homeBedFootPos) : null;
            boolean aliveNow = (headState.getBlock() instanceof BedBlock) || (footState != null && footState.getBlock() instanceof BedBlock);
            if (bedAlive && !aliveNow) {
                bedAlive = false;
                ToastNotificationManager.pushToast("base:broken", "⚠ Bed Destroyed!", "Your team's bed was broken!", "LOST", 0xFFEF4444);
            } else if (!bedAlive && aliveNow) {
                bedAlive = true;
            }
            return;
        }

        if (!autoDetect) return;

        long gameTime = level.getGameTime();
        if (Math.abs(gameTime - lastScanTick) < 20L) return;
        lastScanTick = gameTime;

        BlockPos playerPos = client.player.blockPosition();
        int chunkRadius = 2; // scan within ~32 blocks of player at match start
        int pChunkX = playerPos.getX() >> 4;
        int pChunkZ = playerPos.getZ() >> 4;

        BlockPos closestHead = null;
        BlockPos closestFoot = null;
        Vec3 closestCenter = null;
        double closestDistSqr = 35.0 * 35.0; // bed must be within 35 blocks of initial spawn
        BedBlock closestBlock = null;

        for (int cx = pChunkX - chunkRadius; cx <= pChunkX + chunkRadius; cx++) {
            for (int cz = pChunkZ - chunkRadius; cz <= pChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;

                LevelChunkSection[] sections = chunk.getSections();
                for (int secIdx = 0; secIdx < sections.length; secIdx++) {
                    LevelChunkSection section = sections[secIdx];
                    if (section == null || section.hasOnlyAir()) continue;
                    if (!section.maybeHas(st -> st.getBlock() instanceof BedBlock)) continue;

                    int baseBlockY = chunk.getSectionYFromSectionIndex(secIdx) << 4;
                    for (int ly = 0; ly < 16; ly++) {
                        int wy = baseBlockY + ly;
                        for (int lx = 0; lx < 16; lx++) {
                            int wx = (cx << 4) + lx;
                            for (int lz = 0; lz < 16; lz++) {
                                int wz = (cz << 4) + lz;
                                BlockState state = section.getBlockState(lx, ly, lz);
                                if (!(state.getBlock() instanceof BedBlock bedBlock)) continue;
                                if (state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) != BedPart.HEAD) {
                                    continue;
                                }

                                BlockPos headPos = new BlockPos(wx, wy, wz);
                                double distSqr = client.player.distanceToSqr(wx + 0.5, wy + 0.5, wz + 0.5);
                                if (distSqr < closestDistSqr) {
                                    closestDistSqr = distSqr;
                                    closestHead = headPos;
                                    closestBlock = bedBlock;
                                    Direction facing = state.hasProperty(BedBlock.FACING) ? state.getValue(BedBlock.FACING) : Direction.NORTH;
                                    closestFoot = headPos.relative(facing.getOpposite());
                                    closestCenter = new Vec3(
                                            (headPos.getX() + closestFoot.getX()) * 0.5 + 0.5,
                                            headPos.getY() + 0.5625,
                                            (headPos.getZ() + closestFoot.getZ()) * 0.5 + 0.5
                                    );
                                }
                            }
                        }
                    }
                }
            }
        }

        if (closestHead != null && closestCenter != null && closestBlock != null) {
            homeBedHeadPos = closestHead;
            homeBedFootPos = closestFoot;
            homeBedCenter = closestCenter;
            homeBedColorName = closestBlock.getColor().getName().replace('_', ' ');
            homeBedColorName = Character.toUpperCase(homeBedColorName.charAt(0)) + homeBedColorName.substring(1);
            homeBedRgb = closestBlock.getColor().getTextColor() | 0xFF000000;
            bedLocked = true;
            bedAlive = true;

            ToastNotificationManager.pushToast(
                    "base:locked",
                    "🛏 Home Bed Detected!",
                    "Locked onto " + homeBedColorName + " Team Bed",
                    "BASE",
                    homeBedRgb
            );
        }
    }

    private static String getRelativeDirection(Vec3 playerEye, Vec3 targetPos, float playerYaw) {
        double dx = targetPos.x - playerEye.x;
        double dz = targetPos.z - playerEye.z;
        float angleToTarget = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float relAngle = Mth.wrapDegrees(angleToTarget - playerYaw);

        if (relAngle >= -30.0F && relAngle <= 30.0F) return "▲ AHEAD";
        if (relAngle > 30.0F && relAngle < 70.0F) return "↗ RIGHT";
        if (relAngle >= 70.0F && relAngle <= 110.0F) return "▶ RIGHT";
        if (relAngle > 110.0F && relAngle < 150.0F) return "↘ BEHIND";
        if (relAngle <= -150.0F || relAngle >= 150.0F) return "▼ BEHIND";
        if (relAngle < -110.0F && relAngle > -150.0F) return "↙ LEFT";
        if (relAngle <= -70.0F && relAngle >= -110.0F) return "◀ LEFT";
        return "↖ LEFT";
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (client == null || client.level == null || client.player == null || mod == null) return;

        boolean autoDetect = !(mod.getSetting("Auto-Detect Bed") instanceof BooleanSetting bs) || bs.getValue();
        updateBedDetection(client, client.level, autoDetect);

        if (homeBedCenter == null) return;

        double alertRadius = mod.getSetting("Alert Radius") instanceof NumberSetting ns ? ns.getValue() : 25.0;
        boolean ignoreNpcs = !(mod.getSetting("Ignore NPCs") instanceof BooleanSetting bs) || bs.getValue();
        boolean soundAlert = !(mod.getSetting("Sound Alert") instanceof BooleanSetting bs) || bs.getValue();
        String soundType = mod.getSetting("Sound Type") instanceof ModeSetting ms ? ms.getValue() : "Anvil";
        boolean screenWarning = !(mod.getSetting("Screen Warning") instanceof BooleanSetting bs) || bs.getValue();
        boolean showHeldItem = !(mod.getSetting("Show Held Item") instanceof BooleanSetting bs) || bs.getValue();
        boolean tracerToEnemy = !(mod.getSetting("Tracer to Enemy") instanceof BooleanSetting bs) || bs.getValue();
        boolean highlightEnemy = !(mod.getSetting("Highlight Enemy") instanceof BooleanSetting bs) || bs.getValue();
        boolean perimeterRing = !(mod.getSetting("Perimeter Ring") instanceof BooleanSetting bs) || bs.getValue();
        boolean bedStatusTag = !(mod.getSetting("Bed Status Tag") instanceof BooleanSetting bs) || bs.getValue();
        boolean toastNotification = mod.getSetting("Toast Notification") instanceof BooleanSetting bs && bs.getValue();

        Vec3 playerEye = client.player.getEyePosition();
        float playerYaw = client.player.getYRot();
        List<EnemyThreat> threats = new ArrayList<>();

        for (AbstractClientPlayer otherPlayer : client.level.players()) {
            if (otherPlayer == client.player || !otherPlayer.isAlive() || otherPlayer.isSpectator()) continue;
            if (ignoreNpcs && isNpcOrShopkeeper(client, otherPlayer)) continue;
            if (isTeammate(client.player, otherPlayer)) continue;

            double distToBed = otherPlayer.position().distanceTo(homeBedCenter);
            if (distToBed <= alertRadius) {
                double distToMe = client.player.distanceTo(otherPlayer);
                String name = otherPlayer.getName().getString();
                boolean invis = otherPlayer.isInvisible();

                ItemStack mainHand = otherPlayer.getMainHandItem();
                String heldName = (!mainHand.isEmpty()) ? mainHand.getHoverName().getString() : "Empty Hand";

                String dirText = getRelativeDirection(playerEye, otherPlayer.position(), playerYaw);
                threats.add(new EnemyThreat(otherPlayer, distToBed, distToMe, name, heldName, invis, dirText));
            }
        }

        threats.sort(Comparator.comparingDouble(EnemyThreat::distanceToBed));

        GameRenderer gr = client.gameRenderer;
        int sw = extractor.guiWidth();
        int sh = extractor.guiHeight();

        long now = System.currentTimeMillis();
        boolean hasThreats = !threats.isEmpty() && bedAlive;

        // Audio Alarm
        if (hasThreats && soundAlert) {
            EnemyThreat primary = threats.get(0);
            boolean urgent = primary.distanceToBed() <= 10.0;
            long cooldown = urgent ? 1500L : 2800L;

            if (!hadEnemiesLastTick || (now - lastAlertSoundTime >= cooldown)) {
                lastAlertSoundTime = now;
                playAlertSound(client, soundType, urgent ? 1.4F : 1.0F);

                if (toastNotification && !hadEnemiesLastTick) {
                    ToastNotificationManager.pushToast(
                            "base:alert",
                            "⚠ Base Under Attack!",
                            String.format(Locale.ROOT, "%s is %.0fm from your bed!", primary.name(), primary.distanceToBed()),
                            "DEFEND",
                            0xFFEF4444
                    );
                }
            }
        }
        hadEnemiesLastTick = hasThreats;

        // 1. 3D Perimeter Ring around Home Bed
        if (perimeterRing && bedAlive) {
            int ringSegments = 32;
            int ringColor = hasThreats ? 0xCCEF4444 : ((homeBedRgb & 0x00FFFFFF) | 0x80000000);
            double py = homeBedCenter.y - 0.5;

            Vec3 lastScreenPos = null;
            Vec3 firstScreenPos = null;

            for (int i = 0; i <= ringSegments; i++) {
                double angle = (i % ringSegments) * (Math.PI * 2.0 / ringSegments);
                double rx = homeBedCenter.x + alertRadius * Math.cos(angle);
                double rz = homeBedCenter.z + alertRadius * Math.sin(angle);
                Vec3 worldPoint = new Vec3(rx, py, rz);
                Vec3 sPos = EspRenderUtil.projectToScreen(gr, worldPoint, sw, sh);

                if (lastScreenPos != null && sPos != null) {
                    EspRenderUtil.drawLine(extractor, lastScreenPos.x, lastScreenPos.y, sPos.x, sPos.y, 1.8F, ringColor);
                }
                if (i == 0) firstScreenPos = sPos;
                lastScreenPos = sPos;
            }
            if (lastScreenPos != null && firstScreenPos != null) {
                EspRenderUtil.drawLine(extractor, lastScreenPos.x, lastScreenPos.y, firstScreenPos.x, firstScreenPos.y, 1.8F, ringColor);
            }
        }

        // 2. 3D In-World Tag above Bed
        if (bedStatusTag) {
            Vec3 bedTagPos = new Vec3(homeBedCenter.x, homeBedCenter.y + 0.9, homeBedCenter.z);
            Vec3 screenPos = EspRenderUtil.projectToScreen(gr, bedTagPos, sw, sh);
            if (screenPos != null) {
                String bedText;
                int tagBorder;
                if (!bedAlive) {
                    bedText = "🛏 " + homeBedColorName + " Bed: [DESTROYED]";
                    tagBorder = 0xFF64748B;
                } else if (hasThreats) {
                    bedText = String.format(Locale.ROOT, "⚠ BASE ATTACK! (%d Enemy%s)", threats.size(), threats.size() > 1 ? "s" : "");
                    tagBorder = 0xFFEF4444;
                } else {
                    int distFromPlayer = (int) Math.round(client.player.position().distanceTo(homeBedCenter));
                    bedText = "🛏 " + homeBedColorName + " Bed (" + distFromPlayer + "m): [SAFE]";
                    tagBorder = (homeBedRgb & 0x00FFFFFF) | 0xDD000000;
                }

                int tagW = client.font.width(bedText) + 12;
                int tagX = (int) Math.round(screenPos.x) - tagW / 2;
                int tagY = (int) Math.round(screenPos.y) - 14;

                GuiRenderUtils.fillRoundedRect(extractor, tagX, tagY, tagW, 14, 4, 0xD90F172A);
                GuiRenderUtils.drawRoundedOutline(extractor, tagX, tagY, tagW, 14, 4, tagBorder);
                extractor.text(client.font, bedText, tagX + 6, tagY + 3, hasThreats ? 0xFFFCA5A5 : 0xFFF1F5F9, true);
            }
        }

        // 3. Highlight Enemies near Bed (World Box & Tracer)
        for (EnemyThreat threat : threats) {
            int threatColor = threat.distanceToBed() <= 10.0 ? 0xFFEF4444 : 0xFFF97316;

            if (highlightEnemy) {
                AABB box = threat.player().getBoundingBox().inflate(0.1);
                EspRenderUtil.drawBox3D(extractor, gr, box, sw, sh, 2.0F, threatColor);
            }

            if (tracerToEnemy) {
                Vec3 enemyScreenPos = EspRenderUtil.projectToScreen(gr, threat.player().getEyePosition(), sw, sh);
                if (enemyScreenPos != null) {
                    EspRenderUtil.drawLine(extractor, sw * 0.5, sh * 0.5, enemyScreenPos.x, enemyScreenPos.y, 2.0F, threatColor | 0xD0000000);
                }
            }
        }

        // 4. On-Screen Attack Warning Banner
        if (screenWarning && hasThreats) {
            EnemyThreat closest = threats.get(0);
            boolean veryClose = closest.distanceToBed() <= 10.0;

            float pulse = (float) Math.sin((System.currentTimeMillis() % 800) / 800.0 * Math.PI * 2.0);
            int pulseAlpha = 200 + (int) (55 * Math.abs(pulse));
            int bannerBorder = (veryClose ? 0x00EF4444 : 0x00F97316) | (pulseAlpha << 24);

            String title = veryClose
                    ? String.format(Locale.ROOT, "⚠ CRITICAL: ENEMY AT BED! (%.1fm)", closest.distanceToBed())
                    : String.format(Locale.ROOT, "⚠ BASE ALERT: ENEMY NEAR BED! (%.1fm)", closest.distanceToBed());

            String sub = closest.invisible()
                    ? String.format(Locale.ROOT, "👻 Invisible Player '%s' · %s", closest.name(), closest.directionText())
                    : String.format(Locale.ROOT, "Player '%s' · %s", closest.name(), closest.directionText());

            if (showHeldItem && !closest.heldItemName().equals("Empty Hand")) {
                sub += " · Holding: " + closest.heldItemName();
            }

            int titleW = client.font.width(title);
            int subW = client.font.width(sub);
            int bannerW = Math.max(titleW, subW) + 24;
            int bannerH = 32;

            int bannerX = (sw - bannerW) / 2;
            int bannerY = 80;

            GuiRenderUtils.fillRoundedRect(extractor, bannerX, bannerY, bannerW, bannerH, 6, 0xEE0F172A);
            GuiRenderUtils.drawRoundedOutline(extractor, bannerX, bannerY, bannerW, bannerH, 6, bannerBorder);

            // Top accent stripe
            GuiRenderUtils.fillRoundedRect(extractor, bannerX + 4, bannerY + 2, bannerW - 8, 2, 1, bannerBorder);

            int titleX = bannerX + (bannerW - titleW) / 2;
            extractor.text(client.font, title, titleX, bannerY + 6, veryClose ? 0xFFFCA5A5 : 0xFFFED7AA, true);

            int subX = bannerX + (bannerW - subW) / 2;
            extractor.text(client.font, sub, subX, bannerY + 19, 0xFFF1F5F9, true);
        }
    }
}
