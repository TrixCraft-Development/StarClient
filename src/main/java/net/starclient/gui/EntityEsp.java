package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.friend.FriendManager;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EntityEsp {

    public static final Map<String, Integer> ESP_FILL_COLORS = ColorModeSetting.ofPairs(
            "Slate", "FF0F172A",
            "Midnight", "FF050D1A",
            "Near Black", "FF09090B",
            "Charcoal", "FF18181B",
            "Deep Navy", "FF101E3A",
            "Deep Purple", "FF14102A",
            "Dark Forest", "FF0F1A14",
            "Maroon", "FF1F1212",
            "Sky Tint", "FF0C4A6E",
            "Crimson Tint", "FF7F1D1D"
    );

    public static List<Entity> getTargets(Minecraft client, boolean playersOnly, double maxRange) {
        List<Entity> targets = new ArrayList<>();
        if (client.level == null || client.player == null) return targets;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!isTarget(client, entity, playersOnly, maxRange)) continue;
            targets.add(entity);
        }
        return targets;
    }

    private static boolean isTarget(Minecraft client, Entity entity, boolean playersOnly, double maxRange) {
        if (entity == client.player || !(entity instanceof LivingEntity) || !entity.isAlive()) return false;
        if (playersOnly && !(entity instanceof Player)) return false;
        assert client.player != null;
        return entity.distanceTo(client.player) <= maxRange;
    }

    public static void applyGlow(Minecraft client, boolean playersOnly, double maxRange, boolean glowing) {
        if (client.level == null || client.player == null) return;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player || !(entity instanceof LivingEntity)) continue;
            boolean target = (!playersOnly || entity instanceof Player) && entity.distanceTo(client.player) <= maxRange;
            entity.setGlowingTag(glowing && target && entity.isAlive());
        }
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (mod == null || client.level == null || client.player == null) return;

        boolean playersOnly = getBool(mod, "Players Only", true);
        String style = getMode(mod, "Style", "Glowing");
        boolean drawBoxes = getBool(mod, "Boxes", true);
        boolean drawTracers = getBool(mod, "Tracers", true);
        boolean drawLabels = getBool(mod, "Labels", true);
        double maxRange = getNum(mod, "Range", 32.0);

        int baseLineColor = resolveLineColor(mod);
        int baseFillColor = resolveFillColor(mod);
        renderInternal(extractor, client, playersOnly, maxRange, style, drawBoxes, drawTracers, drawLabels, baseLineColor, baseFillColor);
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, boolean playersOnly, double maxRange, String style) {
        Module mod = StarClient.getInstance() != null && StarClient.getInstance().getModuleManager() != null
                ? StarClient.getInstance().getModuleManager().getModuleByName("Entity ESP")
                : null;
        if (mod != null) {
            render(extractor, client, mod);
            return;
        }
        renderInternal(extractor, client, playersOnly, maxRange, style, true, true, true, 0xFF38BDF8, 0xFF0F172A);
    }

    private static void renderInternal(GuiGraphicsExtractor extractor, Minecraft client,
                                       boolean playersOnly, double maxRange, String style,
                                       boolean drawBoxes, boolean drawTracers, boolean drawLabels,
                                       int baseLineColor, int baseFillColor) {
        if (client.level == null || client.player == null) return;

        GameRenderer gameRenderer = client.gameRenderer;
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();
        double originX = screenWidth / 2.0;
        double originY = screenHeight / 2.0;
        boolean box3D = "Box 3D".equalsIgnoreCase(style);
        boolean chams = "Chams".equalsIgnoreCase(style);
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!isTarget(client, entity, playersOnly, maxRange)) continue;

            Vec3 lerped = entity.getPosition(partialTick);
            Vec3 head = EspRenderUtil.projectToScreen(gameRenderer,
                    new Vec3(lerped.x, lerped.y + entity.getBbHeight() + 0.15, lerped.z), screenWidth, screenHeight);
            if (head == null) continue;

            boolean isFriend = FriendManager.isHighlightFriendsInEsp() && FriendManager.isFriend(entity);
            int lineColor = isFriend ? (FriendManager.getFriendColor() | 0xFF000000) : (baseLineColor | 0xFF000000);
            int tracerColor = (lineColor & 0x00FFFFFF) | 0xCC000000;
            int fillAlpha = chams ? 0x88000000 : 0x44000000;
            int fillColor = (baseFillColor & 0x00FFFFFF) | fillAlpha;
            int dist = (int) client.player.distanceTo(entity);

            if (drawTracers) {
                EspRenderUtil.drawLine(extractor, originX, originY, head.x, head.y, 1.5f, tracerColor);
            }

            if (drawBoxes) {
                if (box3D) {
                    Vec3 delta = lerped.subtract(entity.position());
                    AABB aabb = entity.getBoundingBox().move(delta);
                    double[] screenBox = projectCorners(gameRenderer, entity, lerped, screenWidth, screenHeight);
                    if (screenBox != null) {
                        extractor.fill((int) screenBox[0], (int) screenBox[1], (int) screenBox[2], (int) screenBox[3], fillColor);
                    }
                    EspRenderUtil.drawBox3D(extractor, gameRenderer, aabb, screenWidth, screenHeight, 1.5f, lineColor);
                } else {
                    double[] box = projectCorners(gameRenderer, entity, lerped, screenWidth, screenHeight);
                    if (box != null) {
                        extractor.fill((int) box[0], (int) box[1], (int) box[2], (int) box[3], fillColor);
                        EspRenderUtil.drawRect(extractor, box[0], box[1], box[2], box[3], lineColor);
                    }
                }
            }

            if (drawLabels) {
                String prefix = isFriend ? "♥ " : "";
                String label = prefix + entity.getName().getString() + " (" + dist + "m)";
                int tw = client.font.width(label);
                int pillW = tw + 8;
                int pillH = 12;
                int px = (int) Math.round(head.x) - pillW / 2;
                int py = (int) Math.round(head.y) - 15;
                int plateBg = (baseFillColor & 0x00FFFFFF) | 0xD8000000;
                GuiRenderUtils.fillRoundedRect(extractor, px, py, pillW, pillH, 3, plateBg);
                GuiRenderUtils.drawRoundedOutline(extractor, px, py, pillW, pillH, 3, (lineColor & 0x00FFFFFF) | 0x88000000);
                extractor.text(client.font, label, px + 4, py + 2, isFriend ? lineColor : 0xFFF8FAFC, true);
            }
        }
    }

    public static int resolveLineColor(Module mod) {
        boolean useClient = getBool(mod, "Use Client Colors", true);
        if (useClient && StarClient.getInstance() != null && StarClient.getInstance().getClientSettings() != null) {
            return StarClient.getInstance().getClientSettings().getMenuAccentColor();
        }
        if (mod != null && mod.getSetting("Line Color") instanceof ColorModeSetting cms) {
            return cms.getColor();
        }
        return 0xFF38BDF8;
    }

    public static int resolveFillColor(Module mod) {
        boolean useClient = getBool(mod, "Use Client Colors", true);
        if (useClient && StarClient.getInstance() != null && StarClient.getInstance().getClientSettings() != null) {
            ClientSettings cs = StarClient.getInstance().getClientSettings();
            return cs.getMenuBackgroundColor();
        }
        if (mod != null && mod.getSetting("Fill Color") instanceof ColorModeSetting cms) {
            return cms.getColor();
        }
        return 0xFF0F172A;
    }

    static double[] projectCorners(GameRenderer gameRenderer, Entity entity, Vec3 pos, int screenWidth, int screenHeight) {
        float halfWidth = entity.getBbWidth() / 2.0f;
        float height = entity.getBbHeight();
        double cx = pos.x;
        double cy = pos.y;
        double cz = pos.z;

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (int ix = 0; ix < 2; ix++) {
            double dx = ix == 0 ? -halfWidth : halfWidth;
            for (int iy = 0; iy < 2; iy++) {
                double dy = iy == 0 ? 0.0 : height;
                for (int iz = 0; iz < 2; iz++) {
                    double dz = iz == 0 ? -halfWidth : halfWidth;
                    Vec3 p = EspRenderUtil.projectToScreen(gameRenderer,
                            new Vec3(cx + dx, cy + dy, cz + dz), screenWidth, screenHeight);
                    if (p == null) return null;
                    minX = Math.min(minX, p.x);
                    minY = Math.min(minY, p.y);
                    maxX = Math.max(maxX, p.x);
                    maxY = Math.max(maxY, p.y);
                }
            }
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    private static boolean getBool(Module mod, String name, boolean def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }

    private static String getMode(Module mod, String name, String def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s instanceof ModeSetting ms ? ms.getValue() : def;
    }

    private static double getNum(Module mod, String name, double def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s instanceof NumberSetting ns ? ns.getValue() : def;
    }
}
