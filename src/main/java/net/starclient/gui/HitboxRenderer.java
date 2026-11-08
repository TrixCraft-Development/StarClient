package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.friend.FriendManager;
import net.starclient.module.BlockOutlineState;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.NumberSetting;

public final class HitboxRenderer {

    private HitboxRenderer() {
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module module) {
        if (client.level == null || client.player == null || module == null || !module.isEnabled()) {
            return;
        }

        boolean useClientColor = module.getSetting("Use Client Color") instanceof BooleanSetting bs && bs.getValue();
        int baseRgb;
        if (useClientColor && StarClient.getInstance() != null) {
            baseRgb = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        } else if (module.getSetting("Color") instanceof ColorModeSetting cms) {
            baseRgb = cms.getColor();
        } else {
            baseRgb = BlockOutlineState.OUTLINE_COLORS.getOrDefault("White", 0xFFFFFFFF);
        }

        boolean showEyeLine = !(module.getSetting("Show Eye Line") instanceof BooleanSetting bs) || bs.getValue();
        boolean showLookVec = !(module.getSetting("Show Look Vector") instanceof BooleanSetting bs) || bs.getValue();
        boolean highlightTarget = !(module.getSetting("Highlight Target") instanceof BooleanSetting bs) || bs.getValue();
        boolean playersOnly = module.getSetting("Players Only") instanceof BooleanSetting bs && bs.getValue();
        boolean includeMisc = !(module.getSetting("Include Projectiles & Items") instanceof BooleanSetting bs) || bs.getValue();
        float thickness = module.getSetting("Thickness") instanceof NumberSetting ns ? ns.getValue().floatValue() : 1.5F;
        double opacity = module.getSetting("Opacity") instanceof NumberSetting ns ? ns.getValue() : 0.9;
        double maxRange = module.getSetting("Range") instanceof NumberSetting ns ? ns.getValue() : 48.0;

        int alpha = Math.clamp((int) Math.round(opacity * 255.0), 25, 255);
        int boxColor = (alpha << 24) | (baseRgb & 0x00FFFFFF);
        int targetColor = (alpha << 24) | 0xFFFACC15;
        int eyeColor = (alpha << 24) | 0xFFEF4444;
        int lookColor = (alpha << 24) | 0xFF3B82F6;

        Entity crosshairEntity = (client.hitResult instanceof EntityHitResult ehr) ? ehr.getEntity() : null;
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        GameRenderer gameRenderer = client.gameRenderer;
        int screenW = extractor.guiWidth();
        int screenH = extractor.guiHeight();
        double maxRangeSqr = maxRange * maxRange;
        boolean firstPerson = client.options.getCameraType().isFirstPerson();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!entity.isAlive() || entity.isSpectator()) continue;
            if (entity == client.player && firstPerson) continue;
            if (client.player.distanceToSqr(entity) > maxRangeSqr) continue;

            if (playersOnly) {
                if (!(entity instanceof Player)) continue;
            } else if (!includeMisc) {
                if (!(entity instanceof LivingEntity)) continue;
            } else {
                if (!(entity instanceof LivingEntity) && !(entity instanceof Projectile) && !(entity instanceof ItemEntity)) {
                    continue;
                }
            }

            Vec3 lerpedPos = entity.getPosition(partialTick);
            Vec3 delta = lerpedPos.subtract(entity.position());
            AABB box = entity.getBoundingBox().move(delta);

            int col;
            if (highlightTarget && entity == crosshairEntity) {
                col = targetColor;
            } else if (FriendManager.isHighlightFriendsInEsp() && FriendManager.isFriend(entity)) {
                col = (alpha << 24) | (FriendManager.getFriendColor() & 0x00FFFFFF);
            } else {
                col = boxColor;
            }
            EspRenderUtil.drawBox3D(extractor, gameRenderer, box, screenW, screenH, thickness, col);

            if (entity instanceof LivingEntity) {
                double eyeY = lerpedPos.y + entity.getEyeHeight();
                if (showEyeLine) {
                    AABB eyeBox = new AABB(box.minX, eyeY - 0.01, box.minZ, box.maxX, eyeY + 0.01, box.maxZ);
                    EspRenderUtil.drawBox3D(extractor, gameRenderer, eyeBox, screenW, screenH, Math.max(1.0F, thickness - 0.5F), eyeColor);
                }
                if (showLookVec) {
                    Vec3 eyeOrigin = new Vec3(lerpedPos.x, eyeY, lerpedPos.z);
                    Vec3 look = entity.getViewVector(partialTick);
                    Vec3 lookEnd = eyeOrigin.add(look.scale(2.0));
                    EspRenderUtil.drawSegment3D(extractor, gameRenderer, eyeOrigin, lookEnd, screenW, screenH, thickness, lookColor);
                }
            }
        }
    }
}
