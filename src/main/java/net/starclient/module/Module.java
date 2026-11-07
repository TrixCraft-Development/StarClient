package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.starclient.StarClient;
import net.starclient.rank.Rank;
import net.starclient.setting.BannedWordListSetting;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.MacroListSetting;
import net.starclient.setting.NickEntryListSetting;
import net.starclient.setting.NoteListSetting;
import net.starclient.setting.Setting;
import net.starclient.setting.WaypointListSetting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class Module {
    private static final Set<String> HUD_MODULE_NAMES = Set.of(
            "FPS Display",
            "Ping Display",
            "Coordinates",
            "Active Modules",
            "Keystrokes",
            "Armor HUD",
            "CPS Display",
            "Reach Display",
            "Speedometer",
            "Combo Counter",
            "Potion Effects",
            "Target HUD",
            "Inventory HUD",
            "Clock Display",
            "Compass Display",
            "Server Info",
            "Memory Display",
            "Minimap",
            "World Info",
            "Item Counter",
            "Block Info",
            "Pinned Notes"
    );

    private static final Set<String> MODULES_WITH_TRANSPARENT_BG = Set.of(
            "Tab",
            "Scoreboard",
            "Boss Bar",
            "Chat",
            "Nametags"
    );

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
    private Rank requiredRank;
    private boolean experimental;
    private boolean defaultEnabled;
    private boolean enabled;
    private boolean favorite;
    private final int defaultKeyBind;
    private int keyBind;
    private boolean keyDownLastTick;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final Map<String, Setting<?>> settingsByName = new HashMap<>();

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
        this.defaultKeyBind = defaultKeyBind;
        this.keyBind = defaultKeyBind;
        this.requiredRank = requiredRank == null ? Rank.USER : requiredRank;
        this.enabled = false;
        this.favorite = false;
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

    public boolean isExperimental() {
        return experimental;
    }

    public Module setExperimental(boolean experimental) {
        this.experimental = experimental;
        return this;
    }

    /** Returns true if this module is locked because Experimental Features are disabled in settings. */
    public boolean isExperimentalLocked() {
        if (!experimental) return false;
        StarClient client = StarClient.getInstance();
        return client == null || client.getClientSettings() == null || !client.getClientSettings().isExperimentalFeatures();
    }

    /** Returns true if this module is locked because of the player's rank. */
    public boolean isRankLocked() {
        if (requiredRank == null || requiredRank == Rank.USER) {
            return false;
        }
        StarClient client = StarClient.getInstance();
        if (client == null || client.getRankManager() == null) {
            return false;
        }
        return !client.getRankManager().hasAccess(requiredRank);
    }

    /** Returns true if the module is locked by any restriction (experimental or rank). */
    public boolean isLocked() {
        return isExperimentalLocked() || isRankLocked();
    }

    /** Returns true if the local player is currently allowed to use and toggle this module. */
    public boolean canUse() {
        return !isLocked();
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
            ModuleManager.markActiveModulesDirty();
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

    public int getDefaultKeyBind() {
        return defaultKeyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
    }

    public void toggleFavorite() {
        this.favorite = !this.favorite;
    }

    public boolean hasHudElement() {
        return HUD_MODULE_NAMES.contains(this.name);
    }

    public boolean isHudAppearanceSetting(Setting<?> setting) {
        if (setting == transparentBackground && MODULES_WITH_TRANSPARENT_BG.contains(this.name)) {
            return false;
        }
        return setting == transparentBackground || setting == backgroundColor || setting == textColor;
    }

    public List<Setting<?>> getGeneralSettings() {
        List<Setting<?>> result = new ArrayList<>();
        for (Setting<?> setting : settings) {
            if (!isHudAppearanceSetting(setting)) {
                result.add(setting);
            }
        }
        return result;
    }

    public List<Setting<?>> getHudAppearanceSettings() {
        if (!hasHudElement()) {
            return List.of();
        }
        return List.of(transparentBackground, backgroundColor, textColor);
    }

    public int getVisibleSettingCount() {
        return getGeneralSettings().size() + getHudAppearanceSettings().size();
    }

    public void markDefaultEnabled(boolean defaultEnabled) {
        this.defaultEnabled = defaultEnabled;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public void resetToFullDefault() {
        this.favorite = false;
        resetSettingsToDefault();
        for (Setting<?> setting : settings) {
            if (setting instanceof NickEntryListSetting nl) {
                nl.getValue().clear();
            } else if (setting instanceof MacroListSetting ml) {
                ml.getValue().clear();
            } else if (setting instanceof BannedWordListSetting bwl) {
                bwl.getValue().clear();
            } else if (setting instanceof NoteListSetting nls) {
                nls.getValue().clear();
            }
        }
        setEnabled(this.defaultEnabled);
    }

    public void resetSettingsToDefault() {
        this.keyBind = this.defaultKeyBind;
        for (Setting<?> setting : settings) {
            if (!(setting instanceof NickEntryListSetting)
                    && !(setting instanceof MacroListSetting)
                    && !(setting instanceof WaypointListSetting)
                    && !(setting instanceof BannedWordListSetting)
                    && !(setting instanceof NoteListSetting)) {
                setting.reset();
            }
        }
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
        if (name == null) {
            return null;
        }
        Setting<?> cached = settingsByName.get(name.toLowerCase(Locale.ROOT));
        if (cached != null) {
            return cached;
        }
        for (Setting<?> setting : settings) {
            if (setting.getName().equalsIgnoreCase(name)) {
                return setting;
            }
        }
        return null;
    }

    public void addSetting(Setting<?> setting) {
        this.settings.add(setting);
        if (setting != null && setting.getName() != null) {
            this.settingsByName.put(setting.getName().toLowerCase(Locale.ROOT), setting);
        }
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
