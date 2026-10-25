package net.modernclient.module;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CombatStatsTracker {
    private static LivingEntity lastTarget;
    private static long lastAttackTime;
    private static double lastReachDistance;

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
        lastAttackTime = System.currentTimeMillis();
        if (target instanceof LivingEntity living) {
            lastTarget = living;
        }
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
}
