package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.module.BlockOutlineState;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TrajectoryRenderer {

    private record ProjectileParams(float velocity, float gravity, float drag, float pitchOffset) {
    }

    private TrajectoryRenderer() {
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module module) {
        if (client.level == null || client.player == null || module == null || !module.isEnabled()) {
            return;
        }

        Player player = client.player;
        boolean showUncharged = !(module.getSetting("Preview Uncharged Bow") instanceof BooleanSetting bs) || bs.getValue();
        ProjectileParams params = resolveProjectile(player, player.getMainHandItem(), showUncharged);
        if (params == null) {
            params = resolveProjectile(player, player.getOffhandItem(), showUncharged);
        }
        if (params == null) {
            return;
        }

        boolean useClientColor = !(module.getSetting("Use Client Color") instanceof BooleanSetting bs) || bs.getValue();
        int baseRgb;
        if (useClientColor && StarClient.getInstance() != null) {
            baseRgb = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        } else if (module.getSetting("Color") instanceof ColorModeSetting cms) {
            baseRgb = cms.getColor();
        } else {
            baseRgb = BlockOutlineState.OUTLINE_COLORS.getOrDefault("Sky Blue", 0xFF38BDF8);
        }

        boolean showLandingBox = !(module.getSetting("Landing Box") instanceof BooleanSetting bs) || bs.getValue();
        boolean highlightEntity = !(module.getSetting("Highlight Hit Entity") instanceof BooleanSetting bs) || bs.getValue();
        boolean showDistance = !(module.getSetting("Show Distance") instanceof BooleanSetting bs) || bs.getValue();
        float thickness = module.getSetting("Thickness") instanceof NumberSetting ns ? ns.getValue().floatValue() : 2.0F;

        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 eyePos = player.getEyePosition(partialTick);
        float yaw = player.getViewYRot(partialTick);
        float pitch = player.getViewXRot(partialTick);

        // Initial velocity vector matching Projectile.shootFromRotation
        float effectivePitch = pitch + params.pitchOffset();
        float f = -Mth.sin(yaw * ((float) Math.PI / 180F)) * Mth.cos(effectivePitch * ((float) Math.PI / 180F));
        float f1 = -Mth.sin(effectivePitch * ((float) Math.PI / 180F));
        float f2 = Mth.cos(yaw * ((float) Math.PI / 180F)) * Mth.cos(effectivePitch * ((float) Math.PI / 180F));
        Vec3 motion = new Vec3(f, f1, f2).normalize().scale(params.velocity());

        Vec3 playerVel = player.getDeltaMovement();
        motion = motion.add(playerVel.x, player.onGround() ? 0.0 : playerVel.y, playerVel.z);

        GameRenderer gameRenderer = client.gameRenderer;
        int screenW = extractor.guiWidth();
        int screenH = extractor.guiHeight();
        boolean firstPerson = client.options.getCameraType().isFirstPerson();

        Entity hitEntity = null;
        Vec3 landingPos = null;
        Vec3 simPos = eyePos.add(0, -0.1, 0);
        Vec3 camPos = gameRenderer.mainCamera().position();
        Vec3 originDelta = firstPerson ? camPos.subtract(simPos) : Vec3.ZERO;

        List<Vec3> pathPoints = new ArrayList<>(120);
        if (!firstPerson) {
            pathPoints.add(simPos);
        }

        for (int step = 0; step < 140; step++) {
            Vec3 nextSimPos = simPos.add(motion);

            // Check block collision
            BlockHitResult blockHit = client.level.clip(new ClipContext(
                    simPos, nextSimPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (blockHit.getType() != HitResult.Type.MISS) {
                nextSimPos = blockHit.getLocation();
                landingPos = nextSimPos;
            }

            // Check entity collision along segment
            AABB segBox = new AABB(simPos, nextSimPos).inflate(0.5);
            double closestDist = simPos.distanceToSqr(nextSimPos);
            for (Entity entity : client.level.getEntities(player, segBox,
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player)) {
                AABB entityBox = entity.getBoundingBox().inflate(0.3);
                Optional<Vec3> clip = entityBox.clip(simPos, nextSimPos);
                if (clip.isPresent()) {
                    double d = simPos.distanceToSqr(clip.get());
                    if (d < closestDist) {
                        closestDist = d;
                        hitEntity = entity;
                        landingPos = clip.get();
                        nextSimPos = landingPos;
                    }
                }
            }

            if (landingPos != null) {
                pathPoints.add(landingPos);
                break;
            }

            double blend = firstPerson ? Math.max(0.0, 1.0 - (step + 1) / 8.0) : 0.0;
            pathPoints.add(nextSimPos.add(originDelta.scale(blend)));

            simPos = nextSimPos;
            motion = motion.scale(params.drag()).subtract(0.0, params.gravity(), 0.0);
        }

        int lineColor = (0xE0 << 24) | (hitEntity != null ? 0x34D399 : (baseRgb & 0x00FFFFFF));
        if (firstPerson && !pathPoints.isEmpty()) {
            // Draw the first segment directly from the Crosshair center to the first projected trajectory point
            Vec3 firstProjected = EspRenderUtil.projectToScreen(gameRenderer, pathPoints.getFirst(), screenW, screenH);
            if (firstProjected != null) {
                EspRenderUtil.drawLine(extractor, screenW * 0.5, screenH * 0.5, firstProjected.x, firstProjected.y, thickness, lineColor);
            }
        }
        for (int i = 0; i < pathPoints.size() - 1; i++) {
            EspRenderUtil.drawSegment3D(extractor, gameRenderer,
                    pathPoints.get(i), pathPoints.get(i + 1), screenW, screenH, thickness, lineColor);
        }

        if (landingPos != null) {
            if (hitEntity != null && highlightEntity) {
                Vec3 lerped = hitEntity.getPosition(partialTick);
                AABB eBox = hitEntity.getBoundingBox().move(lerped.subtract(hitEntity.position()));
                EspRenderUtil.drawBox3D(extractor, gameRenderer, eBox, screenW, screenH, thickness + 0.5F, 0xFF34D399);
            } else if (showLandingBox) {
                double r = 0.28;
                AABB landBox = new AABB(landingPos.x - r, landingPos.y - r, landingPos.z - r,
                        landingPos.x + r, landingPos.y + r, landingPos.z + r);
                EspRenderUtil.drawBox3D(extractor, gameRenderer, landBox, screenW, screenH, thickness, lineColor);
            }

            if (showDistance) {
                Vec3 screenLanding = EspRenderUtil.projectToScreen(gameRenderer, landingPos, screenW, screenH);
                if (screenLanding != null) {
                    double dist = eyePos.distanceTo(landingPos);
                    String label = (hitEntity != null ? hitEntity.getName().getString() + " • " : "")
                            + String.format(Locale.US, "%.1fm", dist);
                    int textW = client.font.width(label);
                    int bx = (int) Math.round(screenLanding.x) - textW / 2 - 4;
                    int by = (int) Math.round(screenLanding.y) - 16;
                    GuiRenderUtils.fillRoundedRect(extractor, bx, by, textW + 8, 12, 3, 0xCC0F172A);
                    GuiRenderUtils.drawRoundedOutline(extractor, bx, by, textW + 8, 12, 3, lineColor);
                    extractor.text(client.font, label, bx + 4, by + 2, 0xFFF8FAFC, true);
                }
            }
        }
    }

    private static ProjectileParams resolveProjectile(Player player, ItemStack stack, boolean showUnchargedBow) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();

        switch (item) {
            case BowItem bowItem -> {
                boolean activelyDrawingThis = player.isUsingItem() && player.getUseItem() == stack;
                float power = 1.0F;
                if (activelyDrawingThis) {
                    int usedTicks = stack.getUseDuration(player) - player.getUseItemRemainingTicks();
                    power = BowItem.getPowerForTime(usedTicks);
                    if (power < 0.1F) return null;
                } else if (!showUnchargedBow) {
                    return null;
                }
                return new ProjectileParams(power * 3.0F, 0.05F, 0.99F, 0.0F);
            }
            case CrossbowItem crossbowItem -> {
                ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
                if (charged == null || charged.isEmpty()) return null;
                boolean firework = charged.contains(Items.FIREWORK_ROCKET);
                return new ProjectileParams(firework ? 1.6F : 3.15F, firework ? 0.0F : 0.05F, 0.99F, 0.0F);
            }
            case TridentItem tridentItem -> {
                if (player.isUsingItem() && player.getUseItem() == stack) {
                    int usedTicks = stack.getUseDuration(player) - player.getUseItemRemainingTicks();
                    if (usedTicks < 5) return null;
                } else if (!showUnchargedBow) {
                    return null;
                }
                return new ProjectileParams(2.5F, 0.05F, 0.99F, 0.0F);
            }
            default -> {
            }
        }

        if (item instanceof EnderpearlItem || item instanceof SnowballItem || item instanceof EggItem) {
            return new ProjectileParams(1.5F, 0.03F, 0.99F, 0.0F);
        }

        return switch (item) {
            case WindChargeItem windChargeItem -> new ProjectileParams(1.5F, 0.0F, 0.99F, 0.0F);
            case ThrowablePotionItem throwablePotionItem -> new ProjectileParams(0.5F, 0.05F, 0.99F, -20.0F);
            case ExperienceBottleItem experienceBottleItem -> new ProjectileParams(0.7F, 0.07F, 0.99F, -20.0F);
            default -> null;
        };

    }
}
