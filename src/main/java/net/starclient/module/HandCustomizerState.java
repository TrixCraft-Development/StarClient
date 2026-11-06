package net.starclient.module;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

public final class HandCustomizerState {
    private static boolean active;
    private static double mainX = 0.0;
    private static double mainY = 0.0;
    private static double mainZ = 0.0;
    private static double mainScale = 1.0;
    private static double mainPitch = 0.0;
    private static double mainYaw = 0.0;
    private static double mainRoll = 0.0;

    private static boolean mirrorOffhand = true;
    private static double offX = 0.0;
    private static double offY = 0.0;
    private static double offZ = 0.0;
    private static double offScale = 1.0;
    private static double swingSpeed = 1.0;

    private HandCustomizerState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    public static void update(double mx, double my, double mz, double mScale,
                              double mPitch, double mYaw, double mRoll,
                              boolean mirrorOff, double ox, double oy, double oz, double oScale,
                              double swing) {
        mainX = mx;
        mainY = my;
        mainZ = mz;
        mainScale = mScale;
        mainPitch = mPitch;
        mainYaw = mYaw;
        mainRoll = mRoll;
        mirrorOffhand = mirrorOff;
        offX = ox;
        offY = oy;
        offZ = oz;
        offScale = oScale;
        swingSpeed = swing;
    }

    public static double getSwingSpeed() {
        return swingSpeed;
    }

    public static void applyTransform(PoseStack poseStack, boolean isMainHand) {
        if (isMainHand) {
            poseStack.translate((float) mainX, (float) mainY, (float) mainZ);
            if (Math.abs(mainYaw) > 0.01) {
                poseStack.mulPose(Axis.YP.rotationDegrees((float) mainYaw));
            }
            if (Math.abs(mainPitch) > 0.01) {
                poseStack.mulPose(Axis.XP.rotationDegrees((float) mainPitch));
            }
            if (Math.abs(mainRoll) > 0.01) {
                poseStack.mulPose(Axis.ZP.rotationDegrees((float) mainRoll));
            }
            if (Math.abs(mainScale - 1.0) > 0.001) {
                float s = (float) mainScale;
                poseStack.scale(s, s, s);
            }
        } else {
            double tx = mirrorOffhand ? -mainX : offX;
            double ty = mirrorOffhand ? mainY : offY;
            double tz = mirrorOffhand ? mainZ : offZ;
            double sc = mirrorOffhand ? mainScale : offScale;
            poseStack.translate((float) tx, (float) ty, (float) tz);
            if (mirrorOffhand) {
                if (Math.abs(mainYaw) > 0.01) {
                    poseStack.mulPose(Axis.YP.rotationDegrees((float) -mainYaw));
                }
                if (Math.abs(mainPitch) > 0.01) {
                    poseStack.mulPose(Axis.XP.rotationDegrees((float) mainPitch));
                }
                if (Math.abs(mainRoll) > 0.01) {
                    poseStack.mulPose(Axis.ZP.rotationDegrees((float) -mainRoll));
                }
            }
            if (Math.abs(sc - 1.0) > 0.001) {
                float s = (float) sc;
                poseStack.scale(s, s, s);
            }
        }
    }
}
