package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.client.particle.HugeExplosionParticle;
import net.minecraft.client.particle.HugeExplosionSeedParticle;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.blockentity.state.SignRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.NumberSetting;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Central state and fast evaluation helpers for the {@code Performance} module.
 */
public final class PerformanceState {
    private static Module module;

    private PerformanceState() {}

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    /**
     * Evaluates whether an entity should be skipped before model extraction / rendering.
     */
    public static boolean shouldSkipEntity(Entity entity, double camX, double camY, double camZ) {
        if (!isEnabled() || entity == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player || entity == mc.getCameraEntity()) {
            return false;
        }
        // Never cull bosses
        if (entity instanceof EnderDragon || entity instanceof WitherBoss) {
            return false;
        }

        double dx = entity.getX() - camX;
        double dy = entity.getY() - camY;
        double dz = entity.getZ() - camZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        // 1. Dropped Items & XP Orbs
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
            if (getBoolean("Cull Dropped Items", true)) {
                double maxDist = getNumber("Dropped Item Distance", 32.0);
                return distSq > maxDist * maxDist;
            }
            return false;
        }

        // 2. Armor Stands (with optional preservation of custom-named server holograms)
        if (entity instanceof ArmorStand) {
            if (getBoolean("Cull Armor Stands", true)) {
                if (getBoolean("Keep Named Holograms", true) && entity.hasCustomName()) {
                    return false;
                }
                double maxDist = getNumber("Armor Stand Distance", 32.0);
                return distSq > maxDist * maxDist;
            }
            return false;
        }

        // 3. Item Frames & Glow Item Frames
        if (entity instanceof ItemFrame) {
            if (getBoolean("Cull Item Frames", true)) {
                double maxDist = getNumber("Item Frame Distance", 24.0);
                return distSq > maxDist * maxDist;
            }
            return false;
        }

        // 4. General Entity Distance Culling
        if (getBoolean("Cull Distant Entities", true)) {
            if (entity instanceof Player && !getBoolean("Cull Distant Players", false)) {
                return false;
            }
            double maxDist = getNumber("Max Entity Distance", 64.0);
            return distSq > maxDist * maxDist;
        }

        return false;
    }

    /**
     * Evaluates whether a BlockEntity (chest, sign, skull, banner, shulker box, etc.)
     * should be skipped before render-state extraction.
     */
    public static boolean shouldSkipBlockEntity(BlockEntity blockEntity, Vec3 cameraPos) {
        if (!isEnabled() || blockEntity == null || cameraPos == null) {
            return false;
        }
        if (!getBoolean("Cull Block Entities", true)) {
            return false;
        }
        double maxDist = getNumber("Max Block Entity Distance", 48.0);
        BlockPos pos = blockEntity.getBlockPos();
        double dx = (pos.getX() + 0.5) - cameraPos.x;
        double dy = (pos.getY() + 0.5) - cameraPos.y;
        double dz = (pos.getZ() + 0.5) - cameraPos.z;
        return (dx * dx + dy * dy + dz * dz) > (maxDist * maxDist);
    }

    /**
     * Evaluates whether text layout/rendering on a sign should be skipped based on distance,
     * while still rendering the wooden sign board itself.
     */
    public static boolean shouldSkipSignText(SignRenderState renderState) {
        if (!isEnabled() || renderState == null) {
            return false;
        }
        if (!getBoolean("Hide Distant Sign Text", true)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        Entity camEntity = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
        if (camEntity == null) {
            return false;
        }
        double camX = FreeCamState.isActive() ? FreeCamState.getX() : camEntity.getX();
        double camY = FreeCamState.isActive() ? FreeCamState.getY() : camEntity.getEyeY();
        double camZ = FreeCamState.isActive() ? FreeCamState.getZ() : camEntity.getZ();
        double maxDist = getNumber("Sign Text Distance", 16.0);
        BlockPos pos = renderState.blockPos;
        double dx = (pos.getX() + 0.5) - camX;
        double dy = (pos.getY() + 0.5) - camY;
        double dz = (pos.getZ() + 0.5) - camZ;
        return (dx * dx + dy * dy + dz * dz) > (maxDist * maxDist);
    }

    /**
     * Evaluates whether a newly spawned particle should be skipped.
     */
    public static boolean shouldSkipParticle(ParticleOptions options) {
        if (!isEnabled() || options == null) {
            return false;
        }
        ParticleType<?> type = options.getType();

        if (getBoolean("Disable Explosion Particles", false)) {
            if (type == ParticleTypes.EXPLOSION
                    || type == ParticleTypes.EXPLOSION_EMITTER
                    || type == ParticleTypes.GUST) {
                return true;
            }
        }

        if (getBoolean("Disable Firework Particles", false)) {
            if (type == ParticleTypes.FIREWORK || type == ParticleTypes.FLASH) {
                return true;
            }
        }

        if (getBoolean("Disable Block Break Particles", false)) {
            if (type == ParticleTypes.BLOCK || type == ParticleTypes.BLOCK_CRUMBLE) {
                return true;
            }
        }

        if (getBoolean("Disable Potion Particles", false)) {
            if (type == ParticleTypes.ENTITY_EFFECT) {
                return true;
            }
        }

        if (getBoolean("Disable Weather Particles", true)) {
            if (type == ParticleTypes.RAIN
                    || type == ParticleTypes.DRIPPING_WATER
                    || type == ParticleTypes.FALLING_WATER) {
                return true;
            }
        }

        double reduction = getNumber("Particle Reduction", 25.0);
        if (reduction >= 100.0) {
            return true;
        }
        if (reduction > 0.0) {
            return ThreadLocalRandom.current().nextDouble(100.0) < reduction;
        }
        return false;
    }

    /**
     * Evaluates whether a particle passed directly to {@code ParticleEngine.add(Particle)}
     * (bypassing {@code createParticle(ParticleOptions, ...)}) should be skipped.
     */
    public static boolean shouldSkipDirectParticle(Particle particle) {
        if (!isEnabled() || particle == null) {
            return false;
        }
        if (particle instanceof TerrainParticle) {
            if (getBoolean("Disable Block Break Particles", false)) {
                return true;
            }
        } else if (particle instanceof HugeExplosionParticle || particle instanceof HugeExplosionSeedParticle) {
            if (getBoolean("Disable Explosion Particles", false)) {
                return true;
            }
        } else if (particle instanceof FireworkParticles.Starter) {
            // Keep Starter alive unless firework particles are completely disabled, so blast sounds still play
            return getBoolean("Disable Firework Particles", false);
        } else if (particle.getClass().getEnclosingClass() == FireworkParticles.class) {
            if (getBoolean("Disable Firework Particles", false)) {
                return true;
            }
        }

        if (particle instanceof NoRenderParticle) {
            return false;
        }

        double reduction = getNumber("Particle Reduction", 25.0);
        if (reduction >= 100.0) {
            return true;
        }
        if (reduction > 0.0) {
            return ThreadLocalRandom.current().nextDouble(100.0) < reduction;
        }
        return false;
    }

    public static boolean isDisableBeaconBeams() {
        return isEnabled() && getBoolean("Disable Beacon Beams", false);
    }

    public static boolean isDisableWeatherRender() {
        return isEnabled() && getBoolean("Disable Weather Rendering", false);
    }

    public static boolean isDisableClouds() {
        return isEnabled() && getBoolean("Disable Clouds", false);
    }

    public static boolean isLimitUnfocusedFps() {
        return isEnabled() && getBoolean("Limit Unfocused FPS", true);
    }

    public static int getUnfocusedFpsLimit() {
        return (int) Math.round(getNumber("Unfocused Max FPS", 15.0));
    }

    private static boolean getBoolean(String name, boolean fallback) {
        if (module == null) return fallback;
        return module.getSetting(name) instanceof BooleanSetting bs ? bs.getValue() : fallback;
    }

    private static double getNumber(String name, double fallback) {
        if (module == null) return fallback;
        return module.getSetting(name) instanceof NumberSetting ns ? ns.getValue() : fallback;
    }
}
