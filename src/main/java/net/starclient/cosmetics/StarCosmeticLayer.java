package net.starclient.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

public class StarCosmeticLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private final PlayerCapeModel vanillaCapeModel;

    public StarCosmeticLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
        this.vanillaCapeModel = new PlayerCapeModel(PlayerCapeModel.createCapeLayer().bakeRoot());
    }

    @Override
    public void submit(@NonNull PoseStack poseStack, @NonNull SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;

        Minecraft client = Minecraft.getInstance();

        // Render on local player or in GUI preview
        if (client.player != null && state.id != client.player.getId() && state.id != 0) {
            return;
        }

        CosmeticManager cosmetics = CosmeticManager.getInstance();
        if (cosmetics == null) return;

        // 1. Render Cape: Wavey Cape Physics is active ONLY for Premium supporters when "Atmospheric Wind Sway" is ON.
        // For User rank OR when Wind Sway is toggled OFF, it behaves 100% identical to vanilla Minecraft.
        CapeCosmetic cape = cosmetics.getEquippedCapeCosmetic();
        if (cape != null) {
            boolean useWavey = cosmetics.hasWavePhysicsPermission() && cosmetics.isCapeWindSway();
            if (useWavey) {
                renderWaveyCape(poseStack, submitNodeCollector, packedLight, state, cosmetics, cape);
            } else {
                renderVanillaCape(poseStack, submitNodeCollector, packedLight, state, cape);
            }
        }

        // 2. Render 3D Wings
        String wingsId = cosmetics.getEquippedWings();
        if (wingsId != null && !wingsId.isEmpty() && !"none".equals(wingsId)) {
            renderWings(poseStack, submitNodeCollector, packedLight, state, wingsId);
        }

        // 3. Render 3D Headwear
        String headwearId = cosmetics.getEquippedHeadwear();
        if (headwearId != null && !headwearId.isEmpty() && !"none".equals(headwearId)) {
            renderHeadwear(poseStack, submitNodeCollector, packedLight, headwearId);
        }
    }

    private void renderWaveyCape(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight,
                                AvatarRenderState state, CosmeticManager cosmetics, CapeCosmetic cape) {
        poseStack.pushPose();
        this.getParentModel().body.translateAndRotate(poseStack);

        // Position on back at shoulders
        poseStack.translate(0.0f, 0.0f, 0.125f);

        float speed = cosmetics.getCapeWaveSpeed();
        float amp = cosmetics.getCapeWaveAmplitude();
        boolean wind = cosmetics.isCapeWindSway();

        int segments = 16;
        float segHeight = 0.0625f;

        float[] yPts = new float[segments + 1];
        float[] zPts = new float[segments + 1];
        float[] xPts = new float[segments + 1];

        yPts[0] = 0.0f;
        zPts[0] = 0.0f;
        xPts[0] = 0.0f;

        float t = (System.currentTimeMillis() % 10000000) * 0.001f;
        float lean = state.capeLean * 0.5f;
        if (state.fallFlyingScale() > 0.0f) {
            lean *= (1.0f - state.fallFlyingScale());
        }
        float flapDeg = Math.clamp(6.0f + lean + state.capeFlap * 0.6f, -10.0f, 92.0f);
        float baseAngle = (float) Math.toRadians(flapDeg);

        for (int i = 0; i < segments; i++) {
            float segRatio = i / 15.0f;
            float wave = (float) Math.sin(t * 6.0f * speed - i * 0.42f) * 0.08f * amp;
            float bend = (float) Math.pow(segRatio, 1.35) * (float) Math.toRadians(state.capeFlap * 0.55f);
            float windSway = wind ? (float) Math.sin(t * 2.8f * speed + i * 0.25f) * 0.04f * amp : 0.0f;

            float angle = baseAngle + bend + wave + windSway;
            yPts[i + 1] = yPts[i] + (float) Math.cos(angle) * segHeight;
            zPts[i + 1] = zPts[i] + (float) Math.sin(angle) * segHeight;

            xPts[i + 1] = (float) Math.sin(Math.toRadians(state.capeLean2)) * (i * 0.012f);
        }

        float halfW = 0.3125f;
        float thickness = 0.04f;

        RenderType renderType = RenderTypes.entitySolid(cape.textureId());

        submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            for (int i = 0; i < segments; i++) {
                float v0 = i / 32.0f;
                float v1 = (i + 1) / 32.0f;

                float yA = yPts[i];
                float zA = zPts[i];
                float xOffA = xPts[i];

                float yB = yPts[i + 1];
                float zB = zPts[i + 1];
                float xOffB = xPts[i + 1];

                // 1. Outer Face (visible top of cape)
                float u0Outer = 1.0f / 64.0f;
                float u1Outer = 10.0f / 64.0f;
                addDoubleSidedQuad(pose, consumer,
                        -halfW + xOffA, yA, zA, u0Outer, v0,
                        halfW + xOffA, yA, zA, u1Outer, v0,
                        halfW + xOffB, yB, zB, u1Outer, v1,
                        -halfW + xOffB, yB, zB, u0Outer, v1,
                        packedLight);

                // 2. Inner Face (underside facing player's back)
                float u0Inner = 12.0f / 64.0f;
                float u1Inner = 21.0f / 64.0f;
                addDoubleSidedQuad(pose, consumer,
                        halfW + xOffA, yA, zA - thickness, u1Inner, v0,
                        -halfW + xOffA, yA, zA - thickness, u0Inner, v0,
                        -halfW + xOffB, yB, zB - thickness, u0Inner, v1,
                        halfW + xOffB, yB, zB - thickness, u1Inner, v1,
                        packedLight);

                // 3. Left Edge Strip
                addDoubleSidedQuad(pose, consumer,
                        -halfW + xOffA, yA, zA - thickness, 0.0f, v0,
                        -halfW + xOffA, yA, zA, 1.0f / 64.0f, v0,
                        -halfW + xOffB, yB, zB, 1.0f / 64.0f, v1,
                        -halfW + xOffB, yB, zB - thickness, 0.0f, v1,
                        packedLight);

                // 4. Right Edge Strip
                addDoubleSidedQuad(pose, consumer,
                        halfW + xOffA, yA, zA, 11.0f / 64.0f, v0,
                        halfW + xOffA, yA, zA - thickness, 12.0f / 64.0f, v0,
                        halfW + xOffB, yB, zB - thickness, 12.0f / 64.0f, v1,
                        halfW + xOffB, yB, zB, 11.0f / 64.0f, v1,
                        packedLight);
            }

            // Top Cap (u: 1..10/64, v: 0..1/32)
            addDoubleSidedQuad(pose, consumer,
                    -halfW + xPts[0], yPts[0], zPts[0], 1.0f / 64.0f, 0.0f,
                    halfW + xPts[0], yPts[0], zPts[0], 10.0f / 64.0f, 0.0f,
                    halfW + xPts[0], yPts[0], zPts[0] - thickness, 10.0f / 64.0f, 1.0f / 32.0f,
                    -halfW + xPts[0], yPts[0], zPts[0] - thickness, 1.0f / 64.0f, 1.0f / 32.0f,
                    packedLight);

            // Bottom Cap (u: 11..20/64, v: 0..1/32)
            int last = segments;
            addDoubleSidedQuad(pose, consumer,
                    -halfW + xPts[last], yPts[last], zPts[last] - thickness, 11.0f / 64.0f, 0.0f,
                    halfW + xPts[last], yPts[last], zPts[last] - thickness, 20.0f / 64.0f, 0.0f,
                    halfW + xPts[last], yPts[last], zPts[last], 20.0f / 64.0f, 1.0f / 32.0f,
                    -halfW + xPts[last], yPts[last], zPts[last], 11.0f / 64.0f, 1.0f / 32.0f,
                    packedLight);
        });

        poseStack.popPose();
    }

    private void renderVanillaCape(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight,
                                  AvatarRenderState state, CapeCosmetic cape) {
        poseStack.pushPose();
        if (!state.chestEquipment.isEmpty()) {
            poseStack.translate(0.0f, -0.053125f, 0.06875f);
        }
        RenderType renderType = RenderTypes.entitySolid(cape.textureId());
        submitNodeCollector.submitModel(this.vanillaCapeModel, state, poseStack, renderType, packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        poseStack.popPose();
    }

    private void renderWings(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState state, String wingsId) {
        Identifier texture = PresetCosmetics.getOrRegisterWing(wingsId);
        RenderType renderType = "fairy_prismatic".equals(wingsId)
                ? RenderTypes.entityTranslucent(texture)
                : RenderTypes.entityCutout(texture);

        poseStack.pushPose();
        this.getParentModel().body.translateAndRotate(poseStack);

        // Position on back between shoulder blades
        poseStack.translate(0.0f, 0.15f, 0.125f);

        // Flapping animation based on system time and movement
        float time = (System.currentTimeMillis() % 1000000) * 0.005f;
        float flapSpeed = state.isFallFlying ? 2.5f : (state.walkAnimationSpeed > 0.1f ? 1.8f : 0.8f);
        float flapAngle = (float) Math.sin(time * flapSpeed) * 0.35f;

        // Base resting angle
        float baseSpread = (float) Math.toRadians(25.0f);

        // Left Wing
        poseStack.pushPose();
        poseStack.translate(-0.06f, 0.0f, 0.0f);
        poseStack.mulPose(new Quaternionf().rotateY(baseSpread + flapAngle));
        drawWing(poseStack, submitNodeCollector, renderType, packedLight, wingsId, false);
        poseStack.popPose();

        // Right Wing (mirrored)
        poseStack.pushPose();
        poseStack.translate(0.06f, 0.0f, 0.0f);
        poseStack.mulPose(new Quaternionf().rotateY(-baseSpread - flapAngle));
        drawWing(poseStack, submitNodeCollector, renderType, packedLight, wingsId, true);
        poseStack.popPose();

        poseStack.popPose();
    }

    private void drawWing(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, String wingsId, boolean mirrored) {
        switch (wingsId) {
            case "dragon_void" -> renderDragonWing(poseStack, collector, renderType, light, mirrored);
            case "demon_flame" -> renderDemonWing(poseStack, collector, renderType, light, mirrored);
            case "fairy_prismatic" -> renderFairyWing(poseStack, collector, renderType, light, mirrored);
            default -> renderAngelWing(poseStack, collector, renderType, light, mirrored);
        }
    }

    private void renderDragonWing(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, boolean mirrored) {
        drawWingGeometry(poseStack, collector, renderType, light, mirrored);
    }

    private void renderAngelWing(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, boolean mirrored) {
        drawWingGeometry(poseStack, collector, renderType, light, mirrored);
    }

    private void renderDemonWing(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, boolean mirrored) {
        drawWingGeometry(poseStack, collector, renderType, light, mirrored);
    }

    private void renderFairyWing(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, boolean mirrored) {
        drawWingGeometry(poseStack, collector, renderType, light, mirrored);
    }

    private void drawWingGeometry(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light, boolean mirrored) {
        float sign = mirrored ? 1.0f : -1.0f;

        float x0 = 0.0f;
        float y0 = 0.0f;
        float z0 = 0.0f;

        float x1 = sign * 0.45f;
        float y1 = -0.35f;
        float z1 = 0.04f;

        float x2 = sign * 0.90f;
        float y2 = -0.15f;
        float z2 = 0.08f;

        float x3 = sign * 0.75f;
        float y3 = 0.42f;
        float z3 = 0.03f;

        float x4 = sign * 0.20f;
        float y4 = 0.38f;
        float z4 = 0.0f;

        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            addDoubleSidedQuad(pose, consumer,
                    x0, y0, z0, 0.0f, 0.0f,
                    x1, y1, z1, 0.5f, 0.0f,
                    x3, y3, z3, 0.8f, 1.0f,
                    x4, y4, z4, 0.2f, 1.0f,
                    light);

            addDoubleSidedQuad(pose, consumer,
                    x1, y1, z1, 0.5f, 0.0f,
                    x2, y2, z2, 1.0f, 0.2f,
                    x3, y3, z3, 0.8f, 1.0f,
                    x1, y1 + 0.3f, z1, 0.5f, 0.8f,
                    light);
        });
    }

    private void renderHeadwear(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, String headwearId) {
        Identifier texture = PresetCosmetics.getOrRegisterHeadwear(headwearId);

        poseStack.pushPose();
        this.getParentModel().head.translateAndRotate(poseStack);

        switch (headwearId) {
            case "angel_halo" -> renderHalo(poseStack, submitNodeCollector, texture, packedLight);
            case "demon_horns" -> renderHorns(poseStack, submitNodeCollector, texture, packedLight);
            case "ninja_bandana" -> renderBandana(poseStack, submitNodeCollector, texture, packedLight);
            case "cat_ears" -> renderCatEars(poseStack, submitNodeCollector, texture, packedLight);
        }

        poseStack.popPose();
    }

    private void renderHalo(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture, int light) {
        RenderType renderType = RenderTypes.entityTranslucent(texture);

        poseStack.pushPose();
        poseStack.translate(0.0f, -0.65f, 0.0f);

        // Gentle floating bob
        float bob = (float) Math.sin((System.currentTimeMillis() % 1000000) * 0.003f) * 0.02f;
        poseStack.translate(0.0f, bob, 0.0f);

        // Horizontal ring
        poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(85.0f)));

        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            float r = 0.35f;
            addDoubleSidedQuad(pose, consumer,
                    -r, -r, 0.0f, 0.0f, 0.0f,
                    r, -r, 0.0f, 1.0f, 0.0f,
                    r, r, 0.0f, 1.0f, 1.0f,
                    -r, r, 0.0f, 0.0f, 1.0f,
                    light);
        });

        poseStack.popPose();
    }

    private void renderHorns(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture, int light) {
        RenderType renderType = RenderTypes.entitySolid(texture);

        // Left Horn
        poseStack.pushPose();
        poseStack.translate(-0.25f, -0.55f, -0.1f);
        poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-20.0f)));
        poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(-15.0f)));
        drawHorn(poseStack, collector, renderType, light);
        poseStack.popPose();

        // Right Horn
        poseStack.pushPose();
        poseStack.translate(0.25f, -0.55f, -0.1f);
        poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(20.0f)));
        poseStack.mulPose(new Quaternionf().rotateX((float) Math.toRadians(-15.0f)));
        drawHorn(poseStack, collector, renderType, light);
        poseStack.popPose();
    }

    private void drawHorn(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light) {
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            float w = 0.06f;
            float h = -0.22f;
            addDoubleSidedQuad(pose, consumer,
                    -w, 0.0f, 0.0f, 0.0f, 0.0f,
                    w, 0.0f, 0.0f, 1.0f, 0.0f,
                    0.0f, h, 0.08f, 0.5f, 1.0f,
                    0.0f, h, 0.08f, 0.5f, 1.0f,
                    light);
        });
    }

    private void renderBandana(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture, int light) {
        RenderType renderType = RenderTypes.entityCutout(texture);

        float s = 0.26f;
        float yTop = -0.42f;
        float yBottom = -0.34f;

        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            // Front headband strip
            addDoubleSidedQuad(pose, consumer,
                    -s, yTop, -s - 0.005f, 0.0f, 0.0f,
                    s, yTop, -s - 0.005f, 1.0f, 0.0f,
                    s, yBottom, -s - 0.005f, 1.0f, 0.3f,
                    -s, yBottom, -s - 0.005f, 0.0f, 0.3f,
                    light);

            // Back headband strip
            addDoubleSidedQuad(pose, consumer,
                    -s, yTop, s + 0.005f, 0.0f, 0.0f,
                    s, yTop, s + 0.005f, 1.0f, 0.0f,
                    s, yBottom, s + 0.005f, 1.0f, 0.3f,
                    -s, yBottom, s + 0.005f, 0.0f, 0.3f,
                    light);

            // Left side
            addDoubleSidedQuad(pose, consumer,
                    -s - 0.005f, yTop, -s, 0.0f, 0.0f,
                    -s - 0.005f, yTop, s, 1.0f, 0.0f,
                    -s - 0.005f, yBottom, s, 1.0f, 0.3f,
                    -s - 0.005f, yBottom, -s, 0.0f, 0.3f,
                    light);

            // Right side
            addDoubleSidedQuad(pose, consumer,
                    s + 0.005f, yTop, -s, 0.0f, 0.0f,
                    s + 0.005f, yTop, s, 1.0f, 0.0f,
                    s + 0.005f, yBottom, s, 1.0f, 0.3f,
                    s + 0.005f, yBottom, -s, 0.0f, 0.3f,
                    light);

            // Fluttering sash tails at back
            float sway = (float) Math.sin((System.currentTimeMillis() % 1000000) * 0.006f) * 0.05f;

            // Tail 1
            addDoubleSidedQuad(pose, consumer,
                    -0.08f, yTop, s + 0.01f, 0.0f, 0.0f,
                    -0.02f, yBottom, s + 0.01f, 0.2f, 0.0f,
                    -0.04f + sway, -0.10f, s + 0.08f, 0.2f, 1.0f,
                    -0.12f + sway, -0.10f, s + 0.08f, 0.0f, 1.0f,
                    light);

            // Tail 2
            addDoubleSidedQuad(pose, consumer,
                    0.02f, yTop, s + 0.01f, 0.5f, 0.0f,
                    0.08f, yBottom, s + 0.01f, 0.7f, 0.0f,
                    0.10f - sway, -0.08f, s + 0.07f, 0.7f, 1.0f,
                    0.02f - sway, -0.08f, s + 0.07f, 0.5f, 1.0f,
                    light);
        });
    }

    private void renderCatEars(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture, int light) {
        RenderType renderType = RenderTypes.entityCutout(texture);

        // Left Ear
        poseStack.pushPose();
        poseStack.translate(-0.20f, -0.52f, 0.0f);
        poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(-12.0f)));
        drawCatEar(poseStack, collector, renderType, light);
        poseStack.popPose();

        // Right Ear
        poseStack.pushPose();
        poseStack.translate(0.20f, -0.52f, 0.0f);
        poseStack.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(12.0f)));
        drawCatEar(poseStack, collector, renderType, light);
        poseStack.popPose();
    }

    private void drawCatEar(PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType, int light) {
        collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            float w = 0.08f;
            float h = -0.16f;

            addDoubleSidedQuad(pose, consumer,
                    -w, 0.0f, -0.03f, 0.0f, 0.0f,
                    w, 0.0f, -0.03f, 1.0f, 0.0f,
                    0.0f, h, 0.0f, 0.5f, 1.0f,
                    0.0f, h, 0.0f, 0.5f, 1.0f,
                    light);

            addDoubleSidedQuad(pose, consumer,
                    w, 0.0f, 0.03f, 0.0f, 0.0f,
                    -w, 0.0f, 0.03f, 1.0f, 0.0f,
                    0.0f, h, 0.0f, 0.5f, 1.0f,
                    0.0f, h, 0.0f, 0.5f, 1.0f,
                    light);
        });
    }

    private static void addDoubleSidedQuad(PoseStack.Pose pose, VertexConsumer consumer,
                                           float x0, float y0, float z0, float u0, float v0,
                                           float x1, float y1, float z1, float u1, float v1,
                                           float x2, float y2, float z2, float u2, float v2,
                                           float x3, float y3, float z3, float u3, float v3,
                                           int light) {
        // Front face
        consumer.addVertex(pose, x0, y0, z0).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0f, 1.0f, 0.0f);
        consumer.addVertex(pose, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0f, 1.0f, 0.0f);
        consumer.addVertex(pose, x2, y2, z2).setColor(255, 255, 255, 255).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0f, 1.0f, 0.0f);
        consumer.addVertex(pose, x3, y3, z3).setColor(255, 255, 255, 255).setUv(u3, v3).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0f, 1.0f, 0.0f);

        // Back face (reverse vertex order)
        consumer.addVertex(pose, x3, y3, z3).setColor(255, 255, 255, 255).setUv(u3, v3).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0f, -1.0f, 0.0f);
        consumer.addVertex(pose, x2, y2, z2).setColor(255, 255, 255, 255).setUv(u2, v2).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0f, -1.0f, 0.0f);
        consumer.addVertex(pose, x1, y1, z1).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0f, -1.0f, 0.0f);
        consumer.addVertex(pose, x0, y0, z0).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0.0f, -1.0f, 0.0f);
    }
}
