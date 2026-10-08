package net.modernclient.module;

import net.minecraft.client.Minecraft;
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

    private final String name;
    private final String description;
    private final Category category;
    private final BooleanSetting transparentBackground;
    private final ColorModeSetting backgroundColor;
    private boolean enabled;
    private int keyBind;
    private boolean keyDownLastTick;
    private final List<Setting<?>> settings = new ArrayList<>();

    protected static final Minecraft mc = Minecraft.getInstance();

    public Module(String name, String description, Category category) {
        this(name, description, category, GLFW.GLFW_KEY_UNKNOWN);
    }

    public Module(String name, String description, Category category, int defaultKeyBind) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.keyBind = defaultKeyBind;
        this.enabled = false;
        this.transparentBackground = new BooleanSetting("Transparent Background",
                "Render the module's element on screen without a background, only the text", false);
        this.backgroundColor = new ColorModeSetting("Background Color",
                "The background colour of the module's element on screen", "Dark Blue", BACKGROUND_COLORS);
        addSetting(transparentBackground);
        addSetting(backgroundColor);
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
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

    public void onEnable() {}
    public void onDisable() {}
    public void onTick(Minecraft client) {}
    public void onScroll(double amount) {}
}
