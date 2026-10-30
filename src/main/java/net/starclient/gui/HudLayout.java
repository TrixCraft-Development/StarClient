package net.starclient.gui;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.Setting;

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
    public static final String CPS = "cps";
    public static final String REACH = "reach";
    public static final String SPEED = "speed";
    public static final String COMBO = "combo";
    public static final String POTIONS = "potions";
    public static final String TARGET_HUD = "target_hud";
    public static final String SCOREBOARD = "scoreboard";
    public static final String BOSS_BAR = "boss_bar";
    public static final String INVENTORY = "inventory_hud";
    public static final int AUTO = Integer.MIN_VALUE;

    public static final int FPS_WIDTH = 130;
    public static final int PING_WIDTH = 130;
    public static final int COORDS_WIDTH = 150;
    public static final int STAT_WIDTH = 130;
    public static final int POTIONS_WIDTH = 140;
    public static final int TARGET_HUD_WIDTH = 152;
    public static final int TARGET_HUD_HEIGHT = 48;
    public static final int ELEMENT_HEIGHT = 18;

    public static final int BASE_BOSS_BAR_WIDTH = 162;
    public static final int BASE_BOSS_BAR_HEIGHT = 20;

    private final Map<String, int[]> positions = new HashMap<>();
    private int scoreboardWidth = 140;
    private int scoreboardHeight = 110;
    private int bossBarWidth = BASE_BOSS_BAR_WIDTH;
    private int bossBarHeight = BASE_BOSS_BAR_HEIGHT;

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
        positions.put(CPS, new int[]{AUTO, AUTO});
        positions.put(REACH, new int[]{AUTO, AUTO});
        positions.put(SPEED, new int[]{AUTO, AUTO});
        positions.put(COMBO, new int[]{AUTO, AUTO});
        positions.put(POTIONS, new int[]{AUTO, AUTO});
        positions.put(TARGET_HUD, new int[]{AUTO, AUTO});
        positions.put(SCOREBOARD, new int[]{AUTO, AUTO});
        positions.put(BOSS_BAR, new int[]{AUTO, AUTO});
        positions.put(INVENTORY, new int[]{AUTO, AUTO});
    }

    public void setBossBarSize(int width, int height) {
        if (width > 0) this.bossBarWidth = width;
        if (height > 0) this.bossBarHeight = height;
    }

    public int bossBarWidth() {
        return bossBarWidth;
    }

    public int bossBarHeight() {
        return bossBarHeight;
    }

    public void setScoreboardSize(int width, int height) {
        if (width > 0) this.scoreboardWidth = width;
        if (height > 0) this.scoreboardHeight = height;
    }

    public int scoreboardWidth() {
        return scoreboardWidth;
    }

    public int scoreboardHeight() {
        return scoreboardHeight;
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
        return switch (id) {
            case FPS, PING, COORDINATES, CPS, REACH, SPEED, COMBO, POTIONS -> isAuto(id) ? 8 : getX(id);
            case ARRAYLIST -> isAuto(id) ? screenWidth - maxModWidth - 8 : getX(id);
            case KEYSTROKES -> isAuto(id) ? screenWidth - keystrokesWidth() - 8 : getX(id);
            case ARMOR -> isAuto(id) ? (screenWidth - armorWidth()) / 2 : getX(id);
            case TARGET_HUD -> isAuto(id) ? screenWidth / 2 + 20 : getX(id);
            case SCOREBOARD -> isAuto(id) ? screenWidth - scoreboardWidth() - 6 : getX(id);
            case BOSS_BAR -> {
                int w = maxModWidth > 0 ? maxModWidth : bossBarWidth();
                if (isAuto(id)) {
                    yield (screenWidth - w) / 2;
                }
                int savedX = getX(id);
                int anchorCenterX = savedX + BASE_BOSS_BAR_WIDTH / 2;
                if (Math.abs(anchorCenterX - screenWidth / 2) <= 16) {
                    anchorCenterX = screenWidth / 2;
                }
                yield anchorCenterX - w / 2;
            }
            case INVENTORY -> isAuto(id) ? screenWidth - inventoryWidth() - 8 : getX(id);
            default -> getX(id);
        };
    }

    public int effectiveY(String id, int screenHeight) {
        return switch (id) {
            case FPS -> isAuto(id) ? screenHeight - 26 : getY(id);
            case PING -> isAuto(id) ? screenHeight - 50 : getY(id);
            case COORDINATES -> isAuto(id) ? screenHeight - 74 : getY(id);
            case CPS -> isAuto(id) ? screenHeight - 98 : getY(id);
            case REACH -> isAuto(id) ? screenHeight - 122 : getY(id);
            case SPEED -> isAuto(id) ? screenHeight - 146 : getY(id);
            case COMBO -> isAuto(id) ? screenHeight - 170 : getY(id);
            case POTIONS, ARRAYLIST -> isAuto(id) ? 8 : getY(id);
            case KEYSTROKES -> isAuto(id) ? screenHeight - keystrokesHeight() - 8 : getY(id);
            case ARMOR -> isAuto(id) ? screenHeight - armorHeight() - 8 : getY(id);
            case TARGET_HUD -> isAuto(id) ? screenHeight / 2 + 12 : getY(id);
            case SCOREBOARD -> isAuto(id) ? (screenHeight - scoreboardHeight()) / 2 : getY(id);
            case BOSS_BAR -> isAuto(id) ? 12 : getY(id);
            case INVENTORY -> isAuto(id) ? screenHeight - inventoryHeight() - 28 : getY(id);
            default -> getY(id);
        };
    }

    public boolean isKeystrokesShowMouse() {
        Module module = StarClient.getInstance().getModuleManager().getModuleByName("Keystrokes");
        if (module != null) {
            Setting<?> showMouse = module.getSetting("Show Mouse");
            return showMouse == null || (boolean) showMouse.getValue();
        }
        return true;
    }

    public int keystrokesWidth() {
        return 64;
    }

    public int keystrokesHeight() {
        return isKeystrokesShowMouse() ? 66 : 42;
    }

    public boolean isArmorVertical() {
        Module module = StarClient.getInstance().getModuleManager().getModuleByName("Armor HUD");
        if (module != null) {
            Setting<?> orientation = module.getSetting("Orientation");
            return orientation != null && "Vertical".equals(orientation.getValue());
        }
        return false;
    }

    public boolean isArmorShowHeldItem() {
        Module module = StarClient.getInstance().getModuleManager().getModuleByName("Armor HUD");
        if (module != null) {
            Setting<?> held = module.getSetting("Show Held Item");
            return held != null && Boolean.TRUE.equals(held.getValue());
        }
        return false;
    }

    public int armorSlotCount() {
        return isArmorShowHeldItem() ? 5 : 4;
    }

    public String armorDurabilityMode() {
        Module module = StarClient.getInstance().getModuleManager().getModuleByName("Armor HUD");
        if (module != null) {
            Setting<?> durability = module.getSetting("Durability");
            if (durability != null && durability.getValue() instanceof String mode) {
                return mode;
            }
        }
        return "Show current & max Durability";
    }

    public int armorHorizontalStride() {
        String mode = armorDurabilityMode();
        if ("Hidden".equals(mode)) {
            return 20;
        }
        if ("Only current".equals(mode)) {
            return 26;
        }
        return 48;
    }

    public int armorWidth() {
        return isArmorVertical() ? 18 : armorHorizontalStride() * armorSlotCount();
    }

    public int armorHeight() {
        boolean hidden = "Hidden".equals(armorDurabilityMode());
        int count = armorSlotCount();
        if (isArmorVertical()) {
            return hidden ? (count * 20 - 2) : (count * 32);
        }
        return hidden ? 18 : 30;
    }

    public boolean isInventoryIncludeHotbar() {
        Module module = StarClient.getInstance().getModuleManager().getModuleByName("Inventory HUD");
        if (module != null) {
            Setting<?> s = module.getSetting("Include Hotbar");
            return s != null && Boolean.TRUE.equals(s.getValue());
        }
        return false;
    }

    public boolean isInventoryShowOffhand() {
        Module module = StarClient.getInstance().getModuleManager().getModuleByName("Inventory HUD");
        if (module != null) {
            Setting<?> s = module.getSetting("Show Offhand");
            return s == null || Boolean.TRUE.equals(s.getValue());
        }
        return true;
    }

    public boolean isInventoryShowArmor() {
        Module module = StarClient.getInstance().getModuleManager().getModuleByName("Inventory HUD");
        if (module != null) {
            Setting<?> s = module.getSetting("Show Armor");
            return s != null && Boolean.TRUE.equals(s.getValue());
        }
        return false;
    }

    public boolean isInventoryShowTitle() {
        Module module = StarClient.getInstance().getModuleManager().getModuleByName("Inventory HUD");
        if (module != null) {
            Setting<?> s = module.getSetting("Show Title");
            return s != null && Boolean.TRUE.equals(s.getValue());
        }
        return false;
    }

    public int inventoryWidth() {
        int baseW = 188;
        if (isInventoryShowArmor()) baseW += 21;
        if (isInventoryShowOffhand()) baseW += 21;
        return baseW;
    }

    public int inventoryHeight() {
        int h = (isInventoryIncludeHotbar() || isInventoryShowArmor()) ? 90 : 68;
        if (isInventoryShowTitle()) h += 12;
        return h;
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
        for (String id : new String[]{FPS, PING, COORDINATES, ARRAYLIST, KEYSTROKES, ARMOR, CPS, REACH, SPEED, COMBO, POTIONS, TARGET_HUD, SCOREBOARD, BOSS_BAR, INVENTORY}) {
            if (obj.has(id)) {
                JsonObject o = obj.getAsJsonObject(id);
                setPosition(id, o.get("x").getAsInt(), o.get("y").getAsInt());
            }
        }
    }

    public record Rect(String id, int x1, int y1, int x2, int y2) {
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

        int cpsX = layout.effectiveX(CPS, screenWidth, 0);
        int cpsY = layout.effectiveY(CPS, screenHeight);
        rects.add(new Rect(CPS, cpsX, cpsY, cpsX + STAT_WIDTH, cpsY + ELEMENT_HEIGHT));

        int reachX = layout.effectiveX(REACH, screenWidth, 0);
        int reachY = layout.effectiveY(REACH, screenHeight);
        rects.add(new Rect(REACH, reachX, reachY, reachX + STAT_WIDTH, reachY + ELEMENT_HEIGHT));

        int speedX = layout.effectiveX(SPEED, screenWidth, 0);
        int speedY = layout.effectiveY(SPEED, screenHeight);
        rects.add(new Rect(SPEED, speedX, speedY, speedX + STAT_WIDTH, speedY + ELEMENT_HEIGHT));

        int comboX = layout.effectiveX(COMBO, screenWidth, 0);
        int comboY = layout.effectiveY(COMBO, screenHeight);
        rects.add(new Rect(COMBO, comboX, comboY, comboX + STAT_WIDTH, comboY + ELEMENT_HEIGHT));

        int potX = layout.effectiveX(POTIONS, screenWidth, 0);
        int potY = layout.effectiveY(POTIONS, screenHeight);
        rects.add(new Rect(POTIONS, potX, potY, potX + POTIONS_WIDTH, potY + 48));

        int targetX = layout.effectiveX(TARGET_HUD, screenWidth, 0);
        int targetY = layout.effectiveY(TARGET_HUD, screenHeight);
        rects.add(new Rect(TARGET_HUD, targetX, targetY, targetX + TARGET_HUD_WIDTH, targetY + TARGET_HUD_HEIGHT));

        int maxModWidth = activeModules.stream().mapToInt(m -> font.width(m.getName()) + 12).max().orElse(0);
        int arrayLeft = layout.effectiveX(ARRAYLIST, screenWidth, maxModWidth);
        int arrayTop = layout.effectiveY(ARRAYLIST, screenHeight);
        rects.add(new Rect(ARRAYLIST, arrayLeft, arrayTop, arrayLeft + maxModWidth + 8, arrayTop + 16 * activeModules.size()));

        int keyX = layout.effectiveX(KEYSTROKES, screenWidth, 0);
        int keyY = layout.effectiveY(KEYSTROKES, screenHeight);
        rects.add(new Rect(KEYSTROKES, keyX, keyY, keyX + layout.keystrokesWidth(), keyY + layout.keystrokesHeight()));

        int armorX = layout.effectiveX(ARMOR, screenWidth, 0);
        int armorY = layout.effectiveY(ARMOR, screenHeight);
        rects.add(new Rect(ARMOR, armorX, armorY, armorX + layout.armorWidth(), armorY + layout.armorHeight()));

        int sbWidth = layout.scoreboardWidth();
        int sbHeight = layout.scoreboardHeight();
        int sbX = layout.effectiveX(SCOREBOARD, screenWidth, sbWidth);
        int sbY = layout.effectiveY(SCOREBOARD, screenHeight);
        rects.add(new Rect(SCOREBOARD, sbX, sbY, sbX + sbWidth, sbY + sbHeight));

        int bbWidth = layout.bossBarWidth();
        int bbHeight = layout.bossBarHeight();
        int bbX = layout.effectiveX(BOSS_BAR, screenWidth, bbWidth);
        int bbY = layout.effectiveY(BOSS_BAR, screenHeight);
        rects.add(new Rect(BOSS_BAR, bbX, bbY, bbX + bbWidth, bbY + bbHeight));

        int invWidth = layout.inventoryWidth();
        int invHeight = layout.inventoryHeight();
        int invX = layout.effectiveX(INVENTORY, screenWidth, invWidth);
        int invY = layout.effectiveY(INVENTORY, screenHeight);
        rects.add(new Rect(INVENTORY, invX, invY, invX + invWidth, invY + invHeight));

        return rects;
    }
}
