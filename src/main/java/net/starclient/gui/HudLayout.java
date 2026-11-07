package net.starclient.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.Setting;
import org.joml.Matrix3x2fStack;

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
    public static final String CLOCK = "clock";
    public static final String COMPASS = "compass";
    public static final String SERVER_INFO = "server_info";
    public static final String MEMORY = "memory";
    public static final String MINIMAP = "minimap";
    public static final String WORLD_INFO = "world_info";
    public static final String ITEM_COUNTER = "item_counter";
    public static final int AUTO = Integer.MIN_VALUE;

    public static final float MIN_SCALE = 0.50f;
    public static final float MAX_SCALE = 2.00f;

    private static final String[] ALL_IDS = {
            FPS, PING, COORDINATES, ARRAYLIST, KEYSTROKES, ARMOR,
            CPS, REACH, SPEED, COMBO, CLOCK, COMPASS, SERVER_INFO,
            MEMORY, POTIONS, TARGET_HUD, SCOREBOARD, BOSS_BAR, INVENTORY,
            MINIMAP, WORLD_INFO, ITEM_COUNTER
    };

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
    private final Map<String, Float> scales = new HashMap<>();
    private boolean snapGuides = true;

    private int scoreboardWidth = 140;
    private int scoreboardHeight = 110;
    private int bossBarWidth = BASE_BOSS_BAR_WIDTH;
    private int bossBarHeight = BASE_BOSS_BAR_HEIGHT;

    public HudLayout() {
        reset();
    }

    public void reset() {
        positions.clear();
        scales.clear();
        for (String id : ALL_IDS) {
            positions.put(id, new int[]{AUTO, AUTO});
            scales.put(id, 1.0f);
        }
    }

    public void resetElement(String id) {
        positions.put(id, new int[]{AUTO, AUTO});
        scales.put(id, 1.0f);
    }

    public float getScale(String id) {
        Float s = scales.get(id);
        return s == null ? 1.0f : Math.clamp(s, MIN_SCALE, MAX_SCALE);
    }

    public void setScale(String id, float scale) {
        float rounded = Math.round(Math.clamp(scale, MIN_SCALE, MAX_SCALE) * 20.0f) / 20.0f;
        scales.put(id, rounded);
    }

    public boolean isSnapGuides() {
        return snapGuides;
    }

    public void setSnapGuides(boolean snapGuides) {
        this.snapGuides = snapGuides;
    }

    /**
     * Pushes a 2D matrix scale anchored at (anchorX, anchorY) if this widget's scale != 1.0f.
     * Returns true if a matrix was pushed and must be popped via {@link #endScale}.
     */
    public boolean beginScale(GuiGraphicsExtractor extractor, int anchorX, int anchorY, String id) {
        float scale = getScale(id);
        if (Math.abs(scale - 1.0f) < 0.001f) {
            return false;
        }
        Matrix3x2fStack pose = extractor.pose();
        pose.pushMatrix();
        pose.translate(anchorX, anchorY);
        pose.scale(scale, scale);
        pose.translate(-anchorX, -anchorY);
        return true;
    }

    public void endScale(GuiGraphicsExtractor extractor, boolean pushed) {
        if (pushed) {
            extractor.pose().popMatrix();
        }
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
            case FPS, PING, COORDINATES, CPS, REACH, SPEED, COMBO, CLOCK, SERVER_INFO, MEMORY, POTIONS, MINIMAP, WORLD_INFO ->
                    isAuto(id) ? 8 : getX(id);
            case ITEM_COUNTER -> {
                int w = maxModWidth > 0 ? maxModWidth : 110;
                yield isAuto(id) ? (screenWidth - w) / 2 : getX(id);
            }
            case COMPASS -> {
                int w = maxModWidth > 0 ? maxModWidth : 210;
                yield isAuto(id) ? (screenWidth - w) / 2 : getX(id);
            }
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
            case CLOCK -> isAuto(id) ? screenHeight - 194 : getY(id);
            case MEMORY -> isAuto(id) ? screenHeight - 222 : getY(id);
            case MINIMAP -> isAuto(id) ? 8 : getY(id);
            case WORLD_INFO -> isAuto(id) ? 132 : getY(id);
            case SERVER_INFO -> isAuto(id) ? 60 : getY(id);
            case ITEM_COUNTER -> isAuto(id) ? screenHeight - 62 : getY(id);
            case COMPASS, POTIONS, ARRAYLIST -> isAuto(id) ? 8 : getY(id);
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
        obj.addProperty("_snapGuides", snapGuides);
        for (String id : ALL_IDS) {
            int[] pos = positions.get(id);
            float scale = getScale(id);
            boolean hasPos = pos != null && pos[0] != AUTO;
            boolean hasScale = Math.abs(scale - 1.0f) >= 0.001f;
            if (!hasPos && !hasScale) continue;

            JsonObject o = new JsonObject();
            if (hasPos) {
                o.addProperty("x", pos[0]);
                o.addProperty("y", pos[1]);
            }
            if (hasScale) {
                o.addProperty("scale", scale);
            }
            obj.add(id, o);
        }
        return obj;
    }

    public void fromJson(JsonObject obj) {
        reset();
        if (obj == null) return;
        if (obj.has("_snapGuides")) {
            JsonElement el = obj.get("_snapGuides");
            if (el != null && el.isJsonPrimitive()) {
                this.snapGuides = el.getAsBoolean();
            }
        }
        for (String id : ALL_IDS) {
            if (obj.has(id) && obj.get(id).isJsonObject()) {
                JsonObject o = obj.getAsJsonObject(id);
                if (o.has("x") && o.has("y")) {
                    setPosition(id, o.get("x").getAsInt(), o.get("y").getAsInt());
                }
                if (o.has("scale")) {
                    setScale(id, o.get("scale").getAsFloat());
                }
            }
        }
    }

    public record Rect(String id, int x1, int y1, int x2, int y2) {
        public boolean contains(double x, double y) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2;
        }
    }

    private static Rect makeScaledRect(HudLayout layout, String id, int x, int y, int baseW, int baseH) {
        float s = layout.getScale(id);
        int w = Math.max(12, Math.round(baseW * s));
        int h = Math.max(10, Math.round(baseH * s));
        return new Rect(id, x, y, x + w, y + h);
    }

    public static List<Rect> computeRects(Font font, List<Module> activeModules, int screenWidth, int screenHeight, HudLayout layout) {
        List<Rect> rects = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();

        int fpsX = layout.effectiveX(FPS, screenWidth, 0);
        int fpsY = layout.effectiveY(FPS, screenHeight);
        rects.add(makeScaledRect(layout, FPS, fpsX, fpsY, FPS_WIDTH, ELEMENT_HEIGHT));

        int pingX = layout.effectiveX(PING, screenWidth, 0);
        int pingY = layout.effectiveY(PING, screenHeight);
        rects.add(makeScaledRect(layout, PING, pingX, pingY, PING_WIDTH, ELEMENT_HEIGHT));

        int coordsX = layout.effectiveX(COORDINATES, screenWidth, 0);
        int coordsY = layout.effectiveY(COORDINATES, screenHeight);
        rects.add(makeScaledRect(layout, COORDINATES, coordsX, coordsY, COORDS_WIDTH, ELEMENT_HEIGHT));

        int cpsX = layout.effectiveX(CPS, screenWidth, 0);
        int cpsY = layout.effectiveY(CPS, screenHeight);
        rects.add(makeScaledRect(layout, CPS, cpsX, cpsY, STAT_WIDTH, ELEMENT_HEIGHT));

        int reachX = layout.effectiveX(REACH, screenWidth, 0);
        int reachY = layout.effectiveY(REACH, screenHeight);
        rects.add(makeScaledRect(layout, REACH, reachX, reachY, STAT_WIDTH, ELEMENT_HEIGHT));

        int speedX = layout.effectiveX(SPEED, screenWidth, 0);
        int speedY = layout.effectiveY(SPEED, screenHeight);
        rects.add(makeScaledRect(layout, SPEED, speedX, speedY, STAT_WIDTH, ELEMENT_HEIGHT));

        int comboX = layout.effectiveX(COMBO, screenWidth, 0);
        int comboY = layout.effectiveY(COMBO, screenHeight);
        rects.add(makeScaledRect(layout, COMBO, comboX, comboY, STAT_WIDTH, ELEMENT_HEIGHT));

        int clockX = layout.effectiveX(CLOCK, screenWidth, 0);
        int clockY = layout.effectiveY(CLOCK, screenHeight);
        rects.add(makeScaledRect(layout, CLOCK, clockX, clockY, STAT_WIDTH, ELEMENT_HEIGHT));

        Module compassMod = StarClient.getInstance().getModuleManager().getModuleByName("Compass Display");
        int compW = CompassRenderer.getWidth(compassMod);
        int compH = CompassRenderer.getHeight(compassMod);
        int compX = layout.effectiveX(COMPASS, screenWidth, compW);
        int compY = layout.effectiveY(COMPASS, screenHeight);
        rects.add(makeScaledRect(layout, COMPASS, compX, compY, compW, compH));

        Module srvMod = StarClient.getInstance().getModuleManager().getModuleByName("Server Info");
        int srvW = ServerInfoRenderer.getWidth(font, mc, srvMod);
        int srvH = ServerInfoRenderer.getHeight(mc, srvMod);
        int srvX = layout.effectiveX(SERVER_INFO, screenWidth, srvW);
        int srvY = layout.effectiveY(SERVER_INFO, screenHeight);
        rects.add(makeScaledRect(layout, SERVER_INFO, srvX, srvY, srvW, srvH));

        Module memMod = StarClient.getInstance().getModuleManager().getModuleByName("Memory Display");
        int memW = MemoryHudRenderer.getWidth(font, memMod);
        int memH = MemoryHudRenderer.getHeight(memMod);
        int memX = layout.effectiveX(MEMORY, screenWidth, memW);
        int memY = layout.effectiveY(MEMORY, screenHeight);
        rects.add(makeScaledRect(layout, MEMORY, memX, memY, memW, memH));

        Module mapMod = StarClient.getInstance().getModuleManager().getModuleByName("Minimap");
        int mapW = MinimapRenderer.getWidth(mapMod);
        int mapH = MinimapRenderer.getHeight(mapMod);
        int mapX = layout.effectiveX(MINIMAP, screenWidth, mapW);
        int mapY = layout.effectiveY(MINIMAP, screenHeight);
        rects.add(makeScaledRect(layout, MINIMAP, mapX, mapY, mapW, mapH));

        Module worldInfoMod = StarClient.getInstance().getModuleManager().getModuleByName("World Info");
        int wiW = WorldInfoRenderer.getWidth(font, mc, worldInfoMod);
        int wiH = WorldInfoRenderer.getHeight(mc, worldInfoMod);
        int wiX = layout.effectiveX(WORLD_INFO, screenWidth, wiW);
        int wiY = layout.effectiveY(WORLD_INFO, screenHeight);
        rects.add(makeScaledRect(layout, WORLD_INFO, wiX, wiY, wiW, wiH));

        Module itemCounterMod = StarClient.getInstance().getModuleManager().getModuleByName("Item Counter");
        int icW = ItemCounterRenderer.getWidth(font, mc, itemCounterMod);
        int icH = ItemCounterRenderer.getHeight(mc, itemCounterMod);
        int icX = layout.effectiveX(ITEM_COUNTER, screenWidth, icW);
        int icY = layout.effectiveY(ITEM_COUNTER, screenHeight);
        rects.add(makeScaledRect(layout, ITEM_COUNTER, icX, icY, icW, icH));

        int potX = layout.effectiveX(POTIONS, screenWidth, 0);
        int potY = layout.effectiveY(POTIONS, screenHeight);
        rects.add(makeScaledRect(layout, POTIONS, potX, potY, POTIONS_WIDTH, 48));

        int targetX = layout.effectiveX(TARGET_HUD, screenWidth, 0);
        int targetY = layout.effectiveY(TARGET_HUD, screenHeight);
        rects.add(makeScaledRect(layout, TARGET_HUD, targetX, targetY, TARGET_HUD_WIDTH, TARGET_HUD_HEIGHT));

        int maxModWidth = activeModules.stream().mapToInt(m -> font.width(m.getName()) + 12).max().orElse(0);
        int arrayLeft = layout.effectiveX(ARRAYLIST, screenWidth, maxModWidth);
        int arrayTop = layout.effectiveY(ARRAYLIST, screenHeight);
        rects.add(makeScaledRect(layout, ARRAYLIST, arrayLeft, arrayTop, maxModWidth + 8, Math.max(16, 16 * activeModules.size())));

        int keyX = layout.effectiveX(KEYSTROKES, screenWidth, 0);
        int keyY = layout.effectiveY(KEYSTROKES, screenHeight);
        rects.add(makeScaledRect(layout, KEYSTROKES, keyX, keyY, layout.keystrokesWidth(), layout.keystrokesHeight()));

        int armorX = layout.effectiveX(ARMOR, screenWidth, 0);
        int armorY = layout.effectiveY(ARMOR, screenHeight);
        rects.add(makeScaledRect(layout, ARMOR, armorX, armorY, layout.armorWidth(), layout.armorHeight()));

        int sbWidth = layout.scoreboardWidth();
        int sbHeight = layout.scoreboardHeight();
        int sbX = layout.effectiveX(SCOREBOARD, screenWidth, sbWidth);
        int sbY = layout.effectiveY(SCOREBOARD, screenHeight);
        rects.add(makeScaledRect(layout, SCOREBOARD, sbX, sbY, sbWidth, sbHeight));

        int bbWidth = layout.bossBarWidth();
        int bbHeight = layout.bossBarHeight();
        int bbX = layout.effectiveX(BOSS_BAR, screenWidth, bbWidth);
        int bbY = layout.effectiveY(BOSS_BAR, screenHeight);
        rects.add(makeScaledRect(layout, BOSS_BAR, bbX, bbY, bbWidth, bbHeight));

        int invWidth = layout.inventoryWidth();
        int invHeight = layout.inventoryHeight();
        int invX = layout.effectiveX(INVENTORY, screenWidth, invWidth);
        int invY = layout.effectiveY(INVENTORY, screenHeight);
        rects.add(makeScaledRect(layout, INVENTORY, invX, invY, invWidth, invHeight));

        return rects;
    }
}
