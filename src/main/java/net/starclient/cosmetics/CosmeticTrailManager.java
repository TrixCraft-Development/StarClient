package net.starclient.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;

import java.util.Random;

public final class CosmeticTrailManager {
    private static final Random RANDOM = new Random();

    private CosmeticTrailManager() {
    }

    public static void tick(Minecraft client) {
        if (client == null || client.player == null || client.level == null) return;
        Player player = client.player;

        CosmeticManager cosmetics = CosmeticManager.getInstance();
        if (cosmetics == null) return;

        String trailId = cosmetics.getEquippedTrail();
        if (trailId == null || trailId.isEmpty() || "none".equals(trailId)) return;

        // Only spawn if moving
        double vx = player.getDeltaMovement().x;
        double vy = player.getDeltaMovement().y;
        double vz = player.getDeltaMovement().z;
        double speedSq = vx * vx + vy * vy + vz * vz;

        boolean isMoving = speedSq > 0.002;
        if (!isMoving && player.onGround()) return;

        String mode = cosmetics.getTrailMode();
        if ("Only Sprinting".equals(mode) && !player.isSprinting()) return;
        if ("Only Airborne".equals(mode) && player.onGround()) return;

        ParticleOptions particle = switch (trailId) {
            case "stardust" -> ParticleTypes.END_ROD;
            case "flame" -> ParticleTypes.FLAME;
            case "hearts" -> ParticleTypes.HEART;
            case "portal" -> ParticleTypes.PORTAL;
            case "chroma" -> ParticleTypes.ENCHANT;
            case "snow" -> ParticleTypes.SNOWFLAKE;
            default -> null;
        };

        if (particle == null) return;

        double px = player.getX() + (RANDOM.nextDouble() - 0.5) * 0.45;
        double py = player.getY() + 0.1 + RANDOM.nextDouble() * 0.4;
        double pz = player.getZ() + (RANDOM.nextDouble() - 0.5) * 0.45;

        double pvx = -vx * 0.2 + (RANDOM.nextDouble() - 0.5) * 0.02;
        double pvy = 0.02 + RANDOM.nextDouble() * 0.03;
        double pvz = -vz * 0.2 + (RANDOM.nextDouble() - 0.5) * 0.02;

        client.level.addParticle(particle, px, py, pz, pvx, pvy, pvz);
    }
}
