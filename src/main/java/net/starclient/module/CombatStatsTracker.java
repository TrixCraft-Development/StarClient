package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

public final class CombatStatsTracker {
    private static LivingEntity lastTarget;
    private static long lastAttackTime;
    private static double lastReachDistance;
    private static int comboCount;
    private static int lastRecordedPlayerHurtTime;

    private CombatStatsTracker() {
    }

    public static void onAttack(Player attacker, Entity target) {
        if (attacker == null || target == null || target == attacker) return;
        Vec3 eye = attacker.getEyePosition();
        AABB box = target.getBoundingBox();
        double clampedX = Math.clamp(eye.x, box.minX, box.maxX);
        double clampedY = Math.clamp(eye.y, box.minY, box.maxY);
        double clampedZ = Math.clamp(eye.z, box.minZ, box.maxZ);
        double dx = eye.x - clampedX;
        double dy = eye.y - clampedY;
        double dz = eye.z - clampedZ;
        lastReachDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        long now = System.currentTimeMillis();
        if (target instanceof LivingEntity living) {
            if (lastTarget != living || (now - lastAttackTime) > 4000L) {
                comboCount = 1;
            } else if (living.hurtTime <= 4) {
                comboCount++;
            }
            lastTarget = living;
        }
        lastAttackTime = now;

        net.starclient.gui.DamageIndicatorManager.getInstance().recordPlayerAttack(attacker, target);

        if (HitParticlesState.isActive()) {
            spawnHitParticles(target);
        }
    }

    private static void spawnHitParticles(Entity target) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.particleEngine == null || client.level == null) {
            return;
        }

        String type = HitParticlesState.getParticleType();
        int multiplier = HitParticlesState.getMultiplier();
        boolean alwaysCrit = HitParticlesState.isAlwaysCrit();

        if (alwaysCrit || "Crit".equals(type) || "Both".equals(type)) {
            client.particleEngine.createTrackingEmitter(target, ParticleTypes.CRIT);
        }
        if ("Enchanted".equals(type) || "Both".equals(type)) {
            client.particleEngine.createTrackingEmitter(target, ParticleTypes.ENCHANTED_HIT);
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double cx = target.getX();
        double cy = target.getY() + target.getBbHeight() * 0.55;
        double cz = target.getZ();
        int extraCount = multiplier * 4;

        for (int i = 0; i < extraCount; i++) {
            double ox = (rng.nextDouble() - 0.5) * target.getBbWidth() * 1.2;
            double oy = (rng.nextDouble() - 0.5) * target.getBbHeight() * 0.8;
            double oz = (rng.nextDouble() - 0.5) * target.getBbWidth() * 1.2;
            double vx = (rng.nextDouble() - 0.5) * 0.35;
            double vy = (rng.nextDouble() - 0.2) * 0.25;
            double vz = (rng.nextDouble() - 0.5) * 0.35;

            switch (type) {
                case "Heart" -> client.level.addParticle(ParticleTypes.HEART, cx + ox, cy + oy, cz + oz, vx * 0.2, 0.08, vz * 0.2);
                case "Flame" -> client.level.addParticle(ParticleTypes.FLAME, cx + ox, cy + oy, cz + oz, vx * 0.15, vy * 0.15, vz * 0.15);
                case "Enchanted" -> client.level.addParticle(ParticleTypes.ENCHANTED_HIT, cx + ox, cy + oy, cz + oz, vx, vy, vz);
                case "Both" -> client.level.addParticle(
                        (i % 2 == 0) ? ParticleTypes.CRIT : ParticleTypes.ENCHANTED_HIT,
                        cx + ox, cy + oy, cz + oz, vx, vy, vz
                );
                default -> client.level.addParticle(ParticleTypes.CRIT, cx + ox, cy + oy, cz + oz, vx, vy, vz);
            }
        }
    }

    public static void updateOnTick(Player localPlayer) {
        if (localPlayer == null) {
            comboCount = 0;
            lastRecordedPlayerHurtTime = 0;
            return;
        }
        int hurtTime = localPlayer.hurtTime;
        if (hurtTime > lastRecordedPlayerHurtTime && hurtTime >= 8) {
            comboCount = 0;
        }
        lastRecordedPlayerHurtTime = hurtTime;
    }

    public static LivingEntity getLastTarget() {
        return lastTarget;
    }

    public static long getLastAttackTime() {
        return lastAttackTime;
    }

    public static double getLastReachDistance(double resetSeconds) {
        if (lastAttackTime == 0L) return 0.0;
        if (System.currentTimeMillis() - lastAttackTime > (long) (resetSeconds * 1000.0)) {
            return 0.0;
        }
        return lastReachDistance;
    }

    public static int getComboCount(double resetSeconds) {
        if (lastAttackTime == 0L || comboCount <= 0) return 0;
        if (System.currentTimeMillis() - lastAttackTime > (long) (resetSeconds * 1000.0)) {
            comboCount = 0;
            return 0;
        }
        return comboCount;
    }
}
