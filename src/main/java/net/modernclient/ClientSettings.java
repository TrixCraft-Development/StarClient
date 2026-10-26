package net.modernclient;

import net.modernclient.setting.BooleanSetting;
import net.modernclient.setting.ColorModeSetting;
import net.modernclient.setting.Setting;
import net.modernclient.setting.TextSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ClientSettings {
    private static final Map<String, Integer> MENU_ACCENT_COLORS = ColorModeSetting.ofPairs(
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

    private static final Map<String, Integer> MENU_BACKGROUND_COLORS = ColorModeSetting.ofPairs(
            "Slate", "FF0F172A",
            "Midnight", "FF050D1A",
            "Near Black", "FF09090B",
            "Charcoal", "FF18181B",
            "Deep Navy", "FF101E3A",
            "Deep Purple", "FF14102A",
            "Dark Forest", "FF0F1A14",
            "Maroon", "FF1F1212",
            "Lead", "FF1C2432",
            "Slate Light", "FF1E293B");

    private final List<Setting<?>> settings = new ArrayList<>();
    private final BooleanSetting hudTextShadow;
    private final BooleanSetting tabIcon;
    private final BooleanSetting removeSplash;
    private final TextSetting hypixelApiKey;
    private final ColorModeSetting menuAccentColor;
    private final ColorModeSetting menuBackgroundColor;

    public ClientSettings() {
        this.hudTextShadow = new BooleanSetting("HUD Text Shadow",
                "Draw a dark shadow behind all HUD text for better readability", true);
        this.tabIcon = new BooleanSetting("Show Name Icon",
                "Show the Star icon next to your name in the tab list and above your head", true);
        this.removeSplash = new BooleanSetting("Remove Splash Text",
                "Hide the random yellow splash text on the title screen", true);
        this.hypixelApiKey = new TextSetting("Hypixel API Key",
                "Required by Server modules that query the Hypixel API. Create a free key at hypixel.dev", "", true);
        this.menuAccentColor = new ColorModeSetting("Client Menu Color",
                "Accent colour used across the client's menu", "Sky Blue", MENU_ACCENT_COLORS);
        this.menuBackgroundColor = new ColorModeSetting("Client Menu Background",
                "Background colour of the client's menu panels", "Slate", MENU_BACKGROUND_COLORS);

        settings.add(menuAccentColor);
        settings.add(menuBackgroundColor);
        settings.add(hudTextShadow);
        settings.add(tabIcon);
        settings.add(removeSplash);
        settings.add(hypixelApiKey);
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public boolean isHudTextShadow() {
        return hudTextShadow.getValue();
    }

    public boolean isTabIcon() {
        return tabIcon.getValue();
    }

    public boolean isRemoveSplash() {
        return removeSplash.getValue();
    }

    public String getHypixelApiKey() {
        String key = hypixelApiKey.getValue();
        return key == null ? "" : key.trim();
    }

    public int getMenuAccentColor() {
        return menuAccentColor.getColor();
    }

    public int getMenuAccentDarkColor() {
        int accent = getMenuAccentColor();
        int r = (int) ((accent >> 16 & 0xFF) * 0.75);
        int g = (int) ((accent >> 8 & 0xFF) * 0.75);
        int b = (int) ((accent & 0xFF) * 0.75);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public int getMenuBackgroundColor() {
        return menuBackgroundColor.getColor();
    }

    public int getMenuBackgroundLightColor() {
        int base = getMenuBackgroundColor();
        int r = (base >> 16 & 0xFF) + (int) ((255 - (base >> 16 & 0xFF)) * 0.09);
        int g = (base >> 8 & 0xFF) + (int) ((255 - (base >> 8 & 0xFF)) * 0.09);
        int b = (base & 0xFF) + (int) ((255 - (base & 0xFF)) * 0.09);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public int getMenuBackgroundDarkColor() {
        int base = getMenuBackgroundColor();
        int r = (int) ((base >> 16 & 0xFF) * 0.72);
        int g = (int) ((base >> 8 & 0xFF) * 0.72);
        int b = (int) ((base & 0xFF) * 0.72);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public int getMenuSurfaceColor() {
        int base = getMenuBackgroundColor();
        int r = (base >> 16 & 0xFF) + (int) ((255 - (base >> 16 & 0xFF)) * 0.05);
        int g = (base >> 8 & 0xFF) + (int) ((255 - (base >> 8 & 0xFF)) * 0.05);
        int b = (base & 0xFF) + (int) ((255 - (base & 0xFF)) * 0.05);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public int getMenuSurfaceHoverColor() {
        int base = getMenuBackgroundColor();
        int r = (base >> 16 & 0xFF) + (int) ((255 - (base >> 16 & 0xFF)) * 0.11);
        int g = (base >> 8 & 0xFF) + (int) ((255 - (base >> 8 & 0xFF)) * 0.11);
        int b = (base & 0xFF) + (int) ((255 - (base & 0xFF)) * 0.11);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public int getMenuBorderColor() {
        int base = getMenuBackgroundColor();
        int r = (base >> 16 & 0xFF) + (int) ((255 - (base >> 16 & 0xFF)) * 0.16);
        int g = (base >> 8 & 0xFF) + (int) ((255 - (base >> 8 & 0xFF)) * 0.16);
        int b = (base & 0xFF) + (int) ((255 - (base & 0xFF)) * 0.16);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
