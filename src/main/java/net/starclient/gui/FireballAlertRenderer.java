package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tracks incoming fireballs in Bedwars, Skywars, and mini-games ("Fireball Alert" SERVER module).
 * Alerts the player with:
 * - High-visibility pulsing on-screen warning card with live distance, ETA, and direction arrows
 * - Directional audio warning chimes so the player never gets surprised
 * - 3D world bounding boxes, tracer lines, and floating distance tags
 * - Optional slide-in corner toast alerts
 */
public final class FireballAlertRenderer {

    public record FireballThreat(
            Entity entity,
            Vec3 position,
            double distance,
            double missDistance,
            double etaSeconds,
            String directionText,
            boolean urgent
    ) {
    }

    private static final Map<Integer, Long> ALERTED_ENTITIES = new HashMap<>();
    private static final Set<Integer> URGENT_ALERTED = new HashSet<>();
    private static long lastCleanupTime = 0L;

    private FireballAlertRenderer() {
    }

    public static void clear() {
        ALERTED_ENTITIES.clear();
        URGENT_ALERTED.clear();
    }

    public static boolean isFireball(Entity entity) {
        if (entity == null || !entity.isAlive()) return false;
        if (entity instanceof AbstractHurtingProjectile) return true;
        String name = entity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.contains("fireball") || name.contains("ghastbullet");
    }

    private static void playAlertSound(Minecraft client, String type, float pitch) {
        if (client.player == null) return;
        SoundEvent se = switch (type) {
            case "Pling" -> SoundEvents.NOTE_BLOCK_PLING.value();
            case "Ding" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case "Anvil" -> SoundEvents.ANVIL_LAND;
            case "Bass" -> SoundEvents.NOTE_BLOCK_BASS.value();
            default -> SoundEvents.NOTE_BLOCK_BELL.value();
        };
        client.getSoundManager().play(SimpleSoundInstance.forUI(se, pitch));
    }

    private static String getRelativeDirection(Vec3 playerEye, Vec3 fireballPos, float playerYaw) {
        double dx = fireballPos.x - playerEye.x;
        double dz = fireballPos.z - playerEye.z;
        float angleToFireball = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float relAngle = Mth.wrapDegrees(angleToFireball - playerYaw);

        if (relAngle >= -30.0F && relAngle <= 30.0F) {
            return "▲ AHEAD";
        } else if (relAngle > 30.0F && relAngle < 70.0F) {
            return "↗ RIGHT";
        } else if (relAngle >= 70.0F && relAngle <= 110.0F) {
            return "▶ RIGHT";
        } else if (relAngle > 110.0F && relAngle < 150.0F) {
            return "↘ BEHIND";
        } else if (relAngle <= -150.0F || relAngle >= 150.0F) {
            return "▼ BEHIND";
        } else if (relAngle < -110.0F && relAngle > -150.0F) {
            return "↙ LEFT";
        } else if (relAngle <= -70.0F && relAngle >= -110.0F) {
            return "◀ LEFT";
        } else {
            return "↖ LEFT";
        }
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (client == null || client.level == null || client.player == null || mod == null) return;

        double maxRange = mod.getSetting("Detection Range") instanceof NumberSetting ns ? ns.getValue() : 60.0;
        boolean directThreatOnly = !(mod.getSetting("Direct Threat Only") instanceof BooleanSetting bs) || bs.getValue();
        boolean soundAlert = !(mod.getSetting("Sound Alert") instanceof BooleanSetting bs) || bs.getValue();
        String soundType = mod.getSetting("Sound Type") instanceof ModeSetting ms ? ms.getValue() : "Bell";
        boolean screenWarning = !(mod.getSetting("Screen Warning") instanceof BooleanSetting bs) || bs.getValue();
        boolean directionArrow = !(mod.getSetting("Direction Arrow") instanceof BooleanSetting bs) || bs.getValue();
        boolean worldBox = !(mod.getSetting("World Box") instanceof BooleanSetting bs) || bs.getValue();
        boolean tracerLine = !(mod.getSetting("Tracer Line") instanceof BooleanSetting bs) || bs.getValue();
        boolean toastAlert = mod.getSetting("Toast Alert") instanceof BooleanSetting bs && bs.getValue();

        Vec3 playerEye = client.player.getEyePosition();
        float playerYaw = client.player.getYRot();
        List<FireballThreat> threats = new ArrayList<>();

        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > 2000L) {
            ALERTED_ENTITIES.entrySet().removeIf(e -> now - e.getValue() > 8000L);
            lastCleanupTime = now;
        }

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!isFireball(entity)) continue;

            Vec3 fireballPos = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            double dist = playerEye.distanceTo(fireballPos);
            if (dist > maxRange) continue;

            Vec3 vel = entity.getDeltaMovement();
            double velLenSqr = vel.lengthSqr();
            boolean isMoving = velLenSqr > 0.0001;

            Vec3 toPlayer = playerEye.subtract(fireballPos);
            double t = isMoving ? (toPlayer.dot(vel) / velLenSqr) : 0.0;
            boolean headingTowards = t > 0.0;
            double missDistance = 999.0;
            double etaSeconds = 0.0;

            if (isMoving && headingTowards) {
                Vec3 closest = fireballPos.add(vel.scale(t));
                missDistance = playerEye.distanceTo(closest);
                double speedPerSec = Math.sqrt(velLenSqr) * 20.0;
                etaSeconds = (speedPerSec > 0.1) ? (dist / speedPerSec) : (dist / 20.0);
            }

            boolean isThreat = (!directThreatOnly && headingTowards)
                    || (directThreatOnly && headingTowards && missDistance <= 8.5)
                    || (dist <= 8.0);

            if (!isThreat) continue;

            boolean urgent = dist < 16.0 || (headingTowards && etaSeconds < 1.3);
            String dirText = getRelativeDirection(playerEye, fireballPos, playerYaw);

            threats.add(new FireballThreat(entity, fireballPos, dist, missDistance, etaSeconds, dirText, urgent));

            int id = entity.getId();
            if (!ALERTED_ENTITIES.containsKey(id)) {
                ALERTED_ENTITIES.put(id, now);
                if (soundAlert) {
                    playAlertSound(client, soundType, 1.0F);
                }
                if (toastAlert) {
                    ToastNotificationManager.pushToast(
                            "fireball:" + id,
                            "🔥 Fireball Detected!",
                            String.format(Locale.ROOT, "%.0fm away · %s", dist, dirText),
                            "ALERT",
                            0xFFEF4444
                    );
                }
            } else if (urgent && !URGENT_ALERTED.contains(id)) {
                URGENT_ALERTED.add(id);
                if (soundAlert) {
                    playAlertSound(client, soundType, 1.4F);
                }
            }
        }

        if (threats.isEmpty()) return;

        threats.sort(Comparator.comparingDouble(FireballThreat::distance));
        FireballThreat primary = threats.get(0);

        GameRenderer gr = client.gameRenderer;
        int sw = extractor.guiWidth();
        int sh = extractor.guiHeight();

        // 1. 3D World Highlights
        for (FireballThreat threat : threats) {
            int threatColor = threat.urgent() ? 0xFFEF4444 : 0xFFF97316;

            if (worldBox) {
                AABB box = threat.entity().getBoundingBox().inflate(0.18);
                EspRenderUtil.drawBox3D(extractor, gr, box, sw, sh, threat.urgent() ? 2.5F : 1.8F, threatColor);
            }

            Vec3 screenPos = EspRenderUtil.projectToScreen(gr, threat.position(), sw, sh);
            if (screenPos != null) {
                if (tracerLine) {
                    EspRenderUtil.drawLine(extractor, sw * 0.5, sh * 0.5, screenPos.x, screenPos.y, threat.urgent() ? 2.2F : 1.5F, threatColor | 0xD0000000);
                }

                // In-world floating tag
                String tag = String.format(Locale.ROOT, "🔥 %.1fm (%.1fs)", threat.distance(), threat.etaSeconds());
                int tagW = client.font.width(tag) + 8;
                int tagX = (int) Math.round(screenPos.x) - tagW / 2;
                int tagY = (int) Math.round(screenPos.y) - 18;

                GuiRenderUtils.fillRoundedRect(extractor, tagX, tagY, tagW, 13, 3, 0xD00F172A);
                GuiRenderUtils.drawRoundedOutline(extractor, tagX, tagY, tagW, 13, 3, threatColor);
                extractor.text(client.font, tag, tagX + 4, tagY + 3, 0xFFFFFFFF, true);
            }
        }

        // 2. On-Screen Warning Card
        if (screenWarning) {
            float pulseSin = (float) Math.sin((System.currentTimeMillis() % 1000) / 1000.0 * Math.PI * 2.0);
            int pulseAlpha = 180 + (int) (60 * Math.abs(pulseSin));

            int accentCol = primary.urgent() ? 0xFFEF4444 : 0xFFF97316;
            int borderCol = (accentCol & 0x00FFFFFF) | (pulseAlpha << 24);

            String title = primary.urgent() ? "⚠ DANGER: FIREBALL INCOMING!" : "🔥 INCOMING FIREBALL!";
            String stats = String.format(Locale.ROOT, "%.1fm  •  %.1fs ETA", primary.distance(), primary.etaSeconds());
            if (directionArrow) {
                stats += "  •  [" + primary.directionText() + "]";
            }

            int titleW = client.font.width(title);
            int statsW = client.font.width(stats);
            int bannerW = Math.max(titleW, statsW) + 24;
            int bannerH = 30;

            int bannerX = (sw - bannerW) / 2;
            int bannerY = 46;

            int bgDark = 0xE60F172A;
            GuiRenderUtils.fillRoundedRect(extractor, bannerX, bannerY, bannerW, bannerH, 6, bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, bannerX, bannerY, bannerW, bannerH, 6, borderCol);

            // Subdued red/orange top accent bar
            GuiRenderUtils.fillRoundedRect(extractor, bannerX + 4, bannerY + 2, bannerW - 8, 2, 1, borderCol);

            int titleX = bannerX + (bannerW - titleW) / 2;
            extractor.text(client.font, title, titleX, bannerY + 6, primary.urgent() ? 0xFFFCA5A5 : 0xFFFED7AA, true);

            int statsX = bannerX + (bannerW - statsW) / 2;
            extractor.text(client.font, stats, statsX, bannerY + 18, 0xFFF1F5F9, true);
        }
    }
}
