package net.modernclient.module;

import net.minecraft.client.Minecraft;
import net.modernclient.ModernClient;
import net.modernclient.rank.Rank;
import net.modernclient.setting.BooleanSetting;
import net.modernclient.setting.ColorModeSetting;
import net.modernclient.setting.Setting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class Module {
    private static final Map<String, Integer> BACKGROUND_COLORS = ColorModeSetting.ofPairs(
            "Dark Blue", "FF0F172A",
            "Black", "FF000000",
            "Charcoal", "FF1E293B",
            "Red", "FF7F1D1D",
            "Orange", "FF7C2D12",
            "Amber", "FF78350F",
            "Green", "FF14532D",
            "Teal", "FF134E4A",
            "Blue", "FF1E3A8A",
            "Indigo", "FF312E81",
            "Purple", "FF581C87",
            "Pink", "FF831843");

    private static final Map<String, Integer> TEXT_COLORS = ColorModeSetting.ofPairs(
            "White", "FFF1F5F9",
            "Sky Blue", "FF38BDF8",
            "Blue", "FF60A5FA",
            "Cyan", "FF22D3EE",
            "Green", "FF34D399",
            "Lime", "FFA3E635",
            "Yellow", "FFFACC15",
            "Orange", "FFFB923C",
            "Red", "FFF87171",
            "Pink", "FFF472B6",
            "Purple", "FFA78BFA");

    private final String name;
    private final String description;
    private final Category category;
    private final BooleanSetting transparentBackground;
    private final ColorModeSetting backgroundColor;
    private final ColorModeSetting textColor;
    private Rank requiredRank = Rank.USER;
    private boolean enabled;
    private int keyBind;
    private boolean keyDownLastTick;
    private final List<Setting<?>> settings = new ArrayList<>();

    protected static final Minecraft mc = Minecraft.getInstance();

    public Module(String name, String description, Category category) {
        this(name, description, category, GLFW.GLFW_KEY_UNKNOWN, Rank.USER);
    }

    public Module(String name, String description, Category category, int defaultKeyBind) {
        this(name, description, category, defaultKeyBind, Rank.USER);
    }

    public Module(String name, String description, Category category, int defaultKeyBind, Rank requiredRank) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keyBind = defaultKeyBind;
        this.requiredRank = requiredRank == null ? Rank.USER : requiredRank;
        this.enabled = false;
        this.transparentBackground = new BooleanSetting("Transparent Background",
                "Render the module's element on screen without a background, only the text", false);
        this.backgroundColor = new ColorModeSetting("Background Color",
                "The background colour of the module's element on screen", "Dark Blue", BACKGROUND_COLORS);
        this.textColor = new ColorModeSetting("Text Color",
                "The text colour of the module's element on screen", "White", TEXT_COLORS);
        addSetting(transparentBackground);
        addSetting(backgroundColor);
        addSetting(textColor);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public Rank getRequiredRank() {
        return requiredRank;
    }

    public Module setRequiredRank(Rank requiredRank) {
        this.requiredRank = requiredRank == null ? Rank.USER : requiredRank;
        return this;
    }

    /** Returns true if the local player's rank allows using this module. */
    public boolean canUse() {
        if (requiredRank == null || requiredRank == Rank.USER) {
            return true;
        }
        ModernClient client = ModernClient.getInstance();
        if (client == null || client.getRankManager() == null) {
            return false;
        }
        return client.getRankManager().hasAccess(requiredRank);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (enabled && !canUse()) {
            return;
        }
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        }
    }

    public void toggle() {
        if (!enabled && !canUse()) {
            return;
        }
        setEnabled(!enabled);
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
    }

    public boolean isKeyDownLastTick() {
        return keyDownLastTick;
    }

    public boolean isHoldKey() {
        return false;
    }

    /**
     * Whether the keybind toggles the module. Action modules such as a key-triggered lookup
     * return false and receive {@link #onKeyPress(Minecraft)} on the rising edge instead, so the
     * same keybind row in the ClickGUI still works for them.
     */
    public boolean isKeyBindToggleable() {
        return true;
    }

    /** Called on the rising edge of the keybind when the module is enabled and non-toggleable. */
    public void onKeyPress(Minecraft client) {}

    public void setKeyDownLastTick(boolean keyDownLastTick) {
        this.keyDownLastTick = keyDownLastTick;
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public Setting<?> getSetting(String name) {
        for (Setting<?> setting : settings) {
            if (setting.getName().equalsIgnoreCase(name)) {
                return setting;
            }
        }
        return null;
    }

    public void addSetting(Setting<?> setting) {
        this.settings.add(setting);
    }

    public boolean isTransparentBackground() {
        return transparentBackground.getValue();
    }

    public int getBackgroundColor() {
        return backgroundColor.getColor();
    }

    public int getTextColor() {
        return textColor.getColor();
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick(Minecraft client) {}
    public void onScroll(double amount) {}
}
