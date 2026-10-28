package net.starclient.module;

public final class HitParticlesState {
    private static volatile boolean active;
    private static volatile boolean alwaysCrit = true;
    private static volatile String particleType = "Both";
    private static volatile int multiplier = 2;

    private HitParticlesState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        HitParticlesState.active = active;
    }

    public static boolean isAlwaysCrit() {
        return alwaysCrit;
    }

    public static void setAlwaysCrit(boolean alwaysCrit) {
        HitParticlesState.alwaysCrit = alwaysCrit;
    }

    public static String getParticleType() {
        return particleType;
    }

    public static void setParticleType(String particleType) {
        HitParticlesState.particleType = particleType == null ? "Both" : particleType;
    }

    public static int getMultiplier() {
        return multiplier;
    }

    public static void setMultiplier(int multiplier) {
        HitParticlesState.multiplier = Math.clamp(multiplier, 1, 10);
    }
}
