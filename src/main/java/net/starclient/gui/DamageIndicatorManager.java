package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DamageIndicatorManager {

    private static final DamageIndicatorManager INSTANCE = new DamageIndicatorManager();

    public static DamageIndicatorManager getInstance() {
        return INSTANCE;
    }

    private static class Indicator {
        double x, y, z;
        double vx, vy, vz;
        String text;
        int color;
        long spawnTime;
        double durationMs;
        boolean crit;

        Indicator(double x, double y, double z, double vx, double vy, double vz, String text, int color, long spawnTime, double durationMs, boolean crit) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.text = text;
            this.color = color;
            this.spawnTime = spawnTime;
            this.durationMs = durationMs;
            this.crit = crit;
        }
    }

    private static class RecentAttack {
        long timestamp;
        boolean crit;
        float estimatedDamage;

        RecentAttack(long timestamp, boolean crit, float estimatedDamage) {
            this.timestamp = timestamp;
            this.crit = crit;
            this.estimatedDamage = estimatedDamage;
        }
    }

    private final List<Indicator> indicators = new CopyOnWriteArrayList<>();
    private final Map<Integer, Float> lastHealthMap = new ConcurrentHashMap<>();
    private final Map<Integer, Long> lastSpawnTimeMap = new ConcurrentHashMap<>();
    private final Map<Integer, RecentAttack> recentAttacks = new ConcurrentHashMap<>();

    private DamageIndicatorManager() {
    }

    private static int safeGetId(Entity entity) {
        if (entity == null) return 0;
        try {
            return entity.getId();
        } catch (IllegalStateException ignored) {
            return 0; // Entity is still initializing (ID not yet assigned by server packet)
        }
    }

    public void onEntityHealthUpdate(LivingEntity entity) {
        if (entity == null || !entity.level().isClientSide()) return;
        int entityId = safeGetId(entity);
        if (entityId == 0) return;

        Module mod = getModule();
        if (mod == null || !mod.isEnabled()) return;

        Minecraft client = Minecraft.getInstance();
        if (entity == client.player && !getBool(mod, "Show Self")) {
            return;
        }

        float currentHealth = entity.getHealth();
        Float previousHealth = lastHealthMap.put(entityId, currentHealth);

        if (previousHealth != null && previousHealth > currentHealth) {
            float damage = previousHealth - currentHealth;
            if (damage >= 0.05f) {
                boolean isCrit = consumeCrit(entityId);
                spawnIndicator(entity, damage, isCrit);
                lastSpawnTimeMap.put(entityId, System.currentTimeMillis());
            }
        }
    }

    public void onEntityHurt(LivingEntity entity) {
        if (entity == null || !entity.level().isClientSide()) return;
        int entityId = safeGetId(entity);
        if (entityId == 0) return;

        Module mod = getModule();
        if (mod == null || !mod.isEnabled()) return;

        Minecraft client = Minecraft.getInstance();
        if (entity == client.player && !getBool(mod, "Show Self")) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastSpawn = lastSpawnTimeMap.get(entityId);
        if (lastSpawn != null && (now - lastSpawn) < 150L) {
            // Recently handled by exact health update
            return;
        }

        RecentAttack attack = recentAttacks.remove(entityId);
        if (attack != null && (now - attack.timestamp) < 600L) {
            spawnIndicator(entity, attack.estimatedDamage, attack.crit);
            lastSpawnTimeMap.put(entityId, now);
        }
    }

    public void recordPlayerAttack(Player attacker, Entity target) {
        if (attacker == null || !(target instanceof LivingEntity living)) return;
        int targetId = safeGetId(living);
        if (targetId == 0) return;

        Minecraft client = Minecraft.getInstance();
        if (attacker != client.player) return;

        boolean crit = attacker.fallDistance > 0.0f && !attacker.onGround() && !attacker.isInWater() && !attacker.isPassenger();

        float baseDmg = 1.0f;
        try {
            baseDmg = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        } catch (Exception ignored) {
        }

        float strength = attacker.getAttackStrengthScale(0.5f);
        float damage = baseDmg * (0.2f + strength * strength * 0.8f);
        if (crit) {
            damage *= 1.5f;
        }

        recentAttacks.put(targetId, new RecentAttack(System.currentTimeMillis(), crit, Math.max(1.0f, damage)));
    }

    private boolean consumeCrit(int entityId) {
        RecentAttack attack = recentAttacks.remove(entityId);
        if (attack != null && (System.currentTimeMillis() - attack.timestamp) < 600L) {
            return attack.crit;
        }
        return false;
    }

    public void spawnIndicator(Entity entity, float damage, boolean isCrit) {
        Module mod = getModule();
        if (mod == null || !mod.isEnabled()) return;

        String format = getString(mod, "Format", "Damage & Heart");
        boolean showCrits = getBool(mod, "Show Crits");
        double durationSec = getNum(mod, "Duration", 1.2);
        boolean showIcon = getBool(mod, "Show Heart Icon");

        String text;
        String heart = showIcon ? " ❤" : "";
        if ("Hearts Only".equals(format)) {
            float hearts = damage / 2.0f;
            text = String.format(Locale.ROOT, "-%.1f%s", hearts, heart);
        } else if ("Number Only".equals(format)) {
            text = String.format(Locale.ROOT, "-%.1f", damage);
        } else {
            text = String.format(Locale.ROOT, "-%.1f%s", damage, heart);
        }

        int color = 0xFFFF4D4D; // Vibrant clean red
        if (showCrits && isCrit) {
            text = "✧ " + text + " ✧";
            color = 0xFFFACC15; // Golden crit
        }

        double rx = (Math.random() - 0.5) * 0.45;
        double rz = (Math.random() - 0.5) * 0.45;
        double px = entity.getX() + rx;
        double py = entity.getY() + entity.getBbHeight() * 0.75 + (Math.random() - 0.5) * 0.2;
        double pz = entity.getZ() + rz;

        double vx = (Math.random() - 0.5) * 0.015;
        double vy = 0.035 + Math.random() * 0.02;
        double vz = (Math.random() - 0.5) * 0.015;

        indicators.add(new Indicator(px, py, pz, vx, vy, vz, text, color, System.currentTimeMillis(), durationSec * 1000.0, isCrit));
    }

    public void render(GuiGraphicsExtractor extractor, Minecraft client) {
        Module mod = getModule();
        if (mod == null || !mod.isEnabled()) {
            if (!indicators.isEmpty()) indicators.clear();
            return;
        }

        if (client.level == null || client.player == null) {
            indicators.clear();
            return;
        }

        GameRenderer gameRenderer = client.gameRenderer;
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();
        Font font = client.font;
        long now = System.currentTimeMillis();

        String movement = getString(mod, "Movement", "Bounce Up");
        double scaleBase = getNum(mod, "Scale", 1.0);

        for (Indicator ind : indicators) {
            long elapsed = now - ind.spawnTime;
            if (elapsed > ind.durationMs) {
                indicators.remove(ind);
                continue;
            }

            // Animate position
            if ("Bounce Up".equals(movement)) {
                ind.x += ind.vx;
                ind.y += ind.vy;
                ind.z += ind.vz;
                ind.vy -= 0.0012; // subtle downward gravity
            } else if ("Float Up".equals(movement)) {
                ind.y += 0.012;
            }

            // Project to screen
            Vec3 screen = EspRenderUtil.projectToScreen(gameRenderer, new Vec3(ind.x, ind.y, ind.z), screenWidth, screenHeight);
            if (screen == null) continue;

            // Fade alpha
            float progress = (float) elapsed / (float) ind.durationMs;
            float alpha = progress > 0.65f ? (1.0f - (progress - 0.65f) / 0.35f) : 1.0f;
            alpha = Math.clamp(alpha, 0.0f, 1.0f);
            int alphaInt = (int) (alpha * 255.0f);
            if (alphaInt <= 4) continue;

            // Pop scale on impact
            float pop = 1.0f;
            if (elapsed < 140L) {
                pop += (1.0f - (float) elapsed / 140.0f) * 0.35f;
            }
            float totalScale = (float) scaleBase * pop;

            int drawColor = (alphaInt << 24) | (ind.color & 0x00FFFFFF);
            int textWidth = font.width(ind.text);

            Matrix3x2fStack pose = extractor.pose();
            pose.pushMatrix();
            pose.translate((float) screen.x, (float) screen.y);
            pose.scale(totalScale, totalScale);

            // Centered text with shadow
            extractor.text(font, ind.text, -textWidth / 2, -font.lineHeight / 2, drawColor, true);

            pose.popMatrix();
        }
    }

    private static Module getModule() {
        if (StarClient.getInstance() == null || StarClient.getInstance().getModuleManager() == null) return null;
        return StarClient.getInstance().getModuleManager().getModuleByName("Damage Indicators");
    }

    private static boolean getBool(Module mod, String name) {
        Setting<?> s = mod.getSetting(name);
        return s == null || (boolean) s.getValue();
    }

    private static double getNum(Module mod, String name, double def) {
        return mod.getSetting(name) instanceof NumberSetting ns ? ns.getValue() : def;
    }

    private static String getString(Module mod, String name, String def) {
        Setting<?> s = mod.getSetting(name);
        return s == null ? def : (String) s.getValue();
    }
}
