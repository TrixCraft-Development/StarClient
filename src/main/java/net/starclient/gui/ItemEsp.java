package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;
import org.joml.Matrix3x2fStack;

/**
 * Renders 2D/3D boxes, Chams fills, tracer lines, and floating item count tags
 * for dropped ItemEntities ("Item ESP" module), synced with Client Menu Color and
 * Client Menu Background or custom module color settings.
 */
public final class ItemEsp {

    private ItemEsp() {
    }

    public static void applyGlow(Minecraft client, double maxRange, boolean glowing) {
        if (client == null || client.level == null || client.player == null) return;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity)) continue;
            boolean inRange = entity.distanceTo(client.player) <= maxRange;
            entity.setGlowingTag(glowing && inRange && entity.isAlive());
        }
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (mod == null || client == null || client.level == null || client.player == null) return;

        String style = getMode(mod, "Style", "Box 2D");
        boolean drawBoxes = getBool(mod, "Boxes", true);
        boolean drawTracers = getBool(mod, "Tracers", true);
        boolean drawLabels = getBool(mod, "Labels", true);
        boolean showIcon = getBool(mod, "Show Item Icon", true);
        double maxRange = getNum(mod, "Range", 32.0);

        int lineColor = EntityEsp.resolveLineColor(mod) | 0xFF000000;
        int baseFillColor = EntityEsp.resolveFillColor(mod);
        int tracerColor = (lineColor & 0x00FFFFFF) | 0xCC000000;
        boolean box3D = "Box 3D".equalsIgnoreCase(style);
        boolean chams = "Chams".equalsIgnoreCase(style);
        int fillAlpha = chams ? 0x88000000 : 0x44000000;
        int fillColor = (baseFillColor & 0x00FFFFFF) | fillAlpha;

        GameRenderer gameRenderer = client.gameRenderer;
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();
        double originX = screenWidth / 2.0;
        double originY = screenHeight / 2.0;
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity itemEntity) || !itemEntity.isAlive()) continue;
            double dist = client.player.distanceTo(itemEntity);
            if (dist > maxRange) continue;

            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) continue;

            Vec3 lerped = itemEntity.getPosition(partialTick);
            Vec3 anchor = EspRenderUtil.projectToScreen(gameRenderer,
                    new Vec3(lerped.x, lerped.y + itemEntity.getBbHeight() + 0.25, lerped.z), screenWidth, screenHeight);
            if (anchor == null) continue;

            if (drawTracers) {
                EspRenderUtil.drawLine(extractor, originX, originY, anchor.x, anchor.y, 1.5f, tracerColor);
            }

            if (drawBoxes) {
                if (box3D) {
                    Vec3 delta = lerped.subtract(itemEntity.position());
                    AABB aabb = itemEntity.getBoundingBox().move(delta).inflate(0.05);
                    double[] screenBox = EntityEsp.projectCorners(gameRenderer, itemEntity, lerped, screenWidth, screenHeight);
                    if (screenBox != null) {
                        extractor.fill((int) screenBox[0], (int) screenBox[1], (int) screenBox[2], (int) screenBox[3], fillColor);
                    }
                    EspRenderUtil.drawBox3D(extractor, gameRenderer, aabb, screenWidth, screenHeight, 1.5f, lineColor);
                } else {
                    double[] box = EntityEsp.projectCorners(gameRenderer, itemEntity, lerped, screenWidth, screenHeight);
                    if (box != null) {
                        extractor.fill((int) box[0], (int) box[1], (int) box[2], (int) box[3], fillColor);
                        EspRenderUtil.drawRect(extractor, box[0], box[1], box[2], box[3], lineColor);
                    }
                }
            }

            if (drawLabels) {
                int count = stack.getCount();
                String itemName = stack.getHoverName().getString();
                String countStr = count > 1 ? (" x" + count) : "";
                String label = itemName + countStr + " (" + (int) dist + "m)";
                int tw = client.font.width(label);
                int iconExtra = showIcon ? 13 : 0;
                int pillW = tw + iconExtra + 8;
                int pillH = 13;
                int px = (int) Math.round(anchor.x) - pillW / 2;
                int py = (int) Math.round(anchor.y) - 14;

                int plateBg = (baseFillColor & 0x00FFFFFF) | 0xDC000000;
                GuiRenderUtils.fillRoundedRect(extractor, px, py, pillW, pillH, 3, plateBg);
                GuiRenderUtils.drawRoundedOutline(extractor, px, py, pillW, pillH, 3, (lineColor & 0x00FFFFFF) | 0x99000000);

                int textX = px + 4;
                if (showIcon) {
                    Matrix3x2fStack pose = extractor.pose();
                    pose.pushMatrix();
                    pose.translate(px + 3, py + 1.5f);
                    pose.scale(0.625f, 0.625f);
                    extractor.item(stack, 0, 0);
                    pose.popMatrix();
                    textX += 13;
                }
                extractor.text(client.font, label, textX, py + 2, 0xFFF8FAFC, true);
            }
        }
    }

    private static boolean getBool(Module mod, String name, boolean def) {
        Setting<?> s = mod.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }

    private static String getMode(Module mod, String name, String def) {
        Setting<?> s = mod.getSetting(name);
        return s instanceof ModeSetting ms ? ms.getValue() : def;
    }

    private static double getNum(Module mod, String name, double def) {
        Setting<?> s = mod.getSetting(name);
        return s instanceof NumberSetting ns ? ns.getValue() : def;
    }
}
