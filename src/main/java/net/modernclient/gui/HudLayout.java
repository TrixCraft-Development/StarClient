package net.modernclient.gui;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.modernclient.ModernClient;
import net.modernclient.module.Module;
import net.modernclient.setting.Setting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HudLayout {
    public static final String FPS = "fps";
    public static final String PING = "ping";
    public static final String COORDINATES = "coordinates";
    public static final String ARRAYLIST = "modules";
    public static final String KEYSTROKES = "keystrokes";
    public static final String ARMOR = "armor";
    public static final int AUTO = Integer.MIN_VALUE;

    public static final int FPS_WIDTH = 130;
    public static final int PING_WIDTH = 130;
    public static final int COORDS_WIDTH = 150;
    public static final int ELEMENT_HEIGHT = 18;

    private final Map<String, int[]> positions = new HashMap<>();

    public HudLayout() {
        reset();
    }

    public void reset() {
        positions.clear();
        positions.put(FPS, new int[]{AUTO, AUTO});
        positions.put(PING, new int[]{AUTO, AUTO});
        positions.put(COORDINATES, new int[]{AUTO, AUTO});
        positions.put(ARRAYLIST, new int[]{AUTO, AUTO});
        positions.put(KEYSTROKES, new int[]{AUTO, AUTO});
        positions.put(ARMOR, new int[]{AUTO, AUTO});
    }

    public boolean isAuto(String id) {
        int[] pos = positions.get(id);
        return pos == null || pos[0] == AUTO;
    }

    public int getX(String id) {
        int[] pos = positions.get(id);
        return pos == null ? AUTO : pos[0];
    }

    public int getY(String id) {
        int[] pos = positions.get(id);
        return pos == null ? AUTO : pos[1];
    }

    public void setPosition(String id, int x, int y) {
        positions.put(id, new int[]{x, y});
    }

    public int effectiveX(String id, int screenWidth, int maxModWidth) {
        switch (id) {
            case FPS:
            case PING:
                return isAuto(id) ? 8 : getX(id);
            case COORDINATES:
                return isAuto(id) ? 8 : getX(id);
            case ARRAYLIST:
                return isAuto(id) ? screenWidth - maxModWidth - 8 : getX(id);
            case KEYSTROKES:
                return isAuto(id) ? screenWidth - 80 : getX(id);
            case ARMOR:
                return isAuto(id) ? (screenWidth - armorWidth()) / 2 : getX(id);
            default:
                return getX(id);
        }
    }

    public int effectiveY(String id, int screenHeight) {
        switch (id) {
            case FPS:
                return isAuto(id) ? screenHeight - 26 : getY(id);
            case PING:
                return isAuto(id) ? screenHeight - 50 : getY(id);
            case COORDINATES:
                return isAuto(id) ? screenHeight - 74 : getY(id);
            case ARRAYLIST:
                return isAuto(id) ? 8 : getY(id);
            case KEYSTROKES:
                return isAuto(id) ? screenHeight - 85 : getY(id);
            case ARMOR:
                return isAuto(id) ? screenHeight - armorHeight() - 8 : getY(id);
            default:
                return getY(id);
        }
    }

    public boolean isArmorVertical() {
        Module module = ModernClient.getInstance().getModuleManager().getModuleByName("Armor HUD");
        if (module != null) {
            Setting<?> orientation = module.getSetting("Orientation");
            return orientation != null && "Vertical".equals(orientation.getValue());
        }
        return false;
    }

    public int armorWidth() {
        return isArmorVertical() ? 18 : 72;
    }

    public int armorHeight() {
        return isArmorVertical() ? 144 : 36;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, int[]> entry : positions.entrySet()) {
            int[] pos = entry.getValue();
            if (pos[0] == AUTO) continue;
            JsonObject o = new JsonObject();
            o.addProperty("x", pos[0]);
            o.addProperty("y", pos[1]);
            obj.add(entry.getKey(), o);
        }
        return obj;
    }

    public void fromJson(JsonObject obj) {
        reset();
        for (String id : new String[]{FPS, PING, COORDINATES, ARRAYLIST, KEYSTROKES, ARMOR}) {
            if (obj.has(id)) {
                JsonObject o = obj.getAsJsonObject(id);
                setPosition(id, o.get("x").getAsInt(), o.get("y").getAsInt());
            }
        }
    }

    public static class Rect {
        public final String id;
        public final int x1, y1, x2, y2;

        public Rect(String id, int x1, int y1, int x2, int y2) {
            this.id = id;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        public boolean contains(double x, double y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }

    public static List<Rect> computeRects(Font font, List<Module> activeModules, int screenWidth, int screenHeight, HudLayout layout) {
        List<Rect> rects = new ArrayList<>();

        int fpsX = layout.effectiveX(FPS, screenWidth, 0);
        int fpsY = layout.effectiveY(FPS, screenHeight);
        rects.add(new Rect(FPS, fpsX, fpsY, fpsX + FPS_WIDTH, fpsY + ELEMENT_HEIGHT));

        int pingX = layout.effectiveX(PING, screenWidth, 0);
        int pingY = layout.effectiveY(PING, screenHeight);
        rects.add(new Rect(PING, pingX, pingY, pingX + PING_WIDTH, pingY + ELEMENT_HEIGHT));

        int coordsX = layout.effectiveX(COORDINATES, screenWidth, 0);
        int coordsY = layout.effectiveY(COORDINATES, screenHeight);
        rects.add(new Rect(COORDINATES, coordsX, coordsY, coordsX + COORDS_WIDTH, coordsY + ELEMENT_HEIGHT));

        int maxModWidth = activeModules.stream().mapToInt(m -> font.width(m.getName()) + 12).max().orElse(0);
        int arrayLeft = layout.effectiveX(ARRAYLIST, screenWidth, maxModWidth);
        int arrayTop = layout.effectiveY(ARRAYLIST, screenHeight);
        rects.add(new Rect(ARRAYLIST, arrayLeft, arrayTop, arrayLeft + maxModWidth + 8, arrayTop + 16 * activeModules.size()));

        int keyX = layout.effectiveX(KEYSTROKES, screenWidth, 0);
        int keyY = layout.effectiveY(KEYSTROKES, screenHeight);
        rects.add(new Rect(KEYSTROKES, keyX, keyY, keyX + 80, keyY + 85));

        int armorX = layout.effectiveX(ARMOR, screenWidth, 0);
        int armorY = layout.effectiveY(ARMOR, screenHeight);
        rects.add(new Rect(ARMOR, armorX, armorY, armorX + layout.armorWidth(), armorY + layout.armorHeight()));

        return rects;
    }
}
