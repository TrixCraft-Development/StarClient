package net.starclient;

import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.FriendListSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.Setting;
import net.starclient.setting.TextSetting;

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

    public static final String BG_CUSTOM = "Custom Wallpaper";
    public static final String BG_MODERN = "Modern Dark";
    public static final String BG_VANILLA = "Vanilla Panorama";

    private final List<Setting<?>> settings = new ArrayList<>();
    private final BooleanSetting hudTextShadow;
    private final BooleanSetting tabIcon;
    private final BooleanSetting removeSplash;
    private final BooleanSetting containerAnimations;
    private final BooleanSetting containerBlur;
    private final TextSetting hypixelApiKey;
    private final ColorModeSetting menuAccentColor;
    private final ColorModeSetting menuBackgroundColor;
    private final ModeSetting titleBackground;
    private final BooleanSetting modernButtons;
    private final BooleanSetting keybindNotifications;
    private final BooleanSetting toastNotifications;
    private final FriendListSetting friendList;
    private final BooleanSetting friendBadge;
    private final ModeSetting friendBadgeStyle;
    private final ColorModeSetting friendColor;
    private final BooleanSetting friendChatBadge;
    private final BooleanSetting friendNotifications;
    private final BooleanSetting friendNotificationSound;
    private final BooleanSetting middleClickFriend;
    private final BooleanSetting protectFriendsInCombat;
    private final BooleanSetting highlightFriendsInEsp;
    private final BooleanSetting experimentalFeatures;
    private final BooleanSetting paletteCrashSafeguard;

    public ClientSettings() {
        this.hudTextShadow = new BooleanSetting("HUD Text Shadow",
                "Draw a dark shadow behind all HUD text for better readability", true);
        this.tabIcon = new BooleanSetting("Show Name Icon",
                "Show the Star icon next to your name in the tab list and above your head", true);
        this.keybindNotifications = new BooleanSetting("Keybind Notifications",
                "Show a slide-in corner notification when toggling modules via keybinds", true);
        this.toastNotifications = new BooleanSetting("Toast Notifications",
                "Show slide-in corner notifications for profile switches, screenshots, friends, and client alerts", true);
        this.friendList = new FriendListSetting("Friend List",
                "Open the Friends Manager to add or remove friends");
        this.friendBadge = new BooleanSetting("Show Friend Badge",
                "Show a badge next to friends' names in the tab list and above their heads", true);
        this.friendBadgeStyle = new ModeSetting("Friend Badge Style",
                "Badge icon displayed next to friends' names", "♥ Heart",
                List.of("♥ Heart", "★ Star", "🛡 Ally", "✦ Sparkle", "[FRIEND]"));
        this.friendColor = new ColorModeSetting("Friend Color",
                "Accent color used for friend badges and ESP highlights", "Green", MENU_ACCENT_COLORS);
        this.friendChatBadge = new BooleanSetting("Friend Chat Badges",
                "Show friend badge in front of friends' names in chat messages", true);
        this.friendNotifications = new BooleanSetting("Friend Notifications",
                "Show toast notifications when friends join or leave your server", true);
        this.friendNotificationSound = new BooleanSetting("Friend Notification Sound",
                "Play a subtle chime sound when a friend joins or leaves", true);
        this.middleClickFriend = new BooleanSetting("Middle Click Friend",
                "Middle-click a player in-game to quickly add or remove them as a friend", true);
        this.protectFriendsInCombat = new BooleanSetting("Protect Friends in Combat",
                "Prevent Aimbot, Kill Aura, and Auto Clicker from attacking your friends", true);
        this.highlightFriendsInEsp = new BooleanSetting("Highlight Friends in ESP",
                "Highlight friends with your Friend Color in Entity ESP, Hitboxes, and Minimap", true);
        this.removeSplash = new BooleanSetting("Remove Splash Text",
                "Hide the random yellow splash text on the title screen", true);
        this.containerAnimations = new BooleanSetting("Container Animations",
                "Smooth pop-in scale and glide transition when opening inventories and containers", false);
        this.containerBlur = new BooleanSetting("Container Background Blur",
                "Apply Gaussian background blur behind open inventory and container screens", true);
        this.hypixelApiKey = new TextSetting("Hypixel API Key",
                "Required by Server modules that query the Hypixel API. Create a free key at hypixel.dev", "", true);
        this.menuAccentColor = new ColorModeSetting("Client Menu Color",
                "Accent colour used across the client's menu", "Sky Blue", MENU_ACCENT_COLORS);
        this.menuBackgroundColor = new ColorModeSetting("Client Menu Background",
                "Background colour of the client's menu panels", "Slate", MENU_BACKGROUND_COLORS);
        this.titleBackground = new ModeSetting("Main Menu Background",
                "Background style displayed on the main menu screen", BG_MODERN,
                List.of(BG_MODERN, BG_CUSTOM, BG_VANILLA));
        this.modernButtons = new BooleanSetting("Modern Buttons",
                "Apply StarClient's modern rounded UI styling to standard Minecraft buttons", true);
        this.experimentalFeatures = new BooleanSetting("Experimental Features",
                "Unlock experimental modules and features currently under development", false);
        this.paletteCrashSafeguard = new BooleanSetting("Palette Crash Safeguard",
                "Prevents game crashes from invalid chunk palette IDs sent by servers", true);

        settings.add(menuAccentColor);
        settings.add(menuBackgroundColor);
        settings.add(titleBackground);
        settings.add(modernButtons);
        settings.add(hudTextShadow);
        settings.add(tabIcon);
        settings.add(keybindNotifications);
        settings.add(toastNotifications);
        settings.add(removeSplash);
        settings.add(containerAnimations);
        settings.add(containerBlur);
        settings.add(friendList);
        settings.add(friendBadge);
        settings.add(friendBadgeStyle);
        settings.add(friendColor);
        settings.add(friendChatBadge);
        settings.add(friendNotifications);
        settings.add(friendNotificationSound);
        settings.add(middleClickFriend);
        settings.add(protectFriendsInCombat);
        settings.add(highlightFriendsInEsp);
        settings.add(hypixelApiKey);
        settings.add(experimentalFeatures);
        settings.add(paletteCrashSafeguard);
    }

    public List<Setting<?>> getSettings() {
        return settings;
    }

    public void resetToDefaults() {
        for (Setting<?> setting : settings) {
            if (!(setting instanceof FriendListSetting)) {
                setting.reset();
            }
        }
    }

    public boolean isHudTextShadow() {
        return hudTextShadow.getValue();
    }

    public boolean isTabIcon() {
        return tabIcon.getValue();
    }

    public boolean isKeybindNotifications() {
        return keybindNotifications == null || keybindNotifications.getValue();
    }

    public boolean isToastNotifications() {
        return toastNotifications == null || toastNotifications.getValue();
    }

    public FriendListSetting getFriendListSetting() {
        return friendList;
    }

    public boolean isFriendBadge() {
        return friendBadge == null || friendBadge.getValue();
    }

    public String getFriendBadgeStyle() {
        return friendBadgeStyle == null ? "♥ Heart" : friendBadgeStyle.getValue();
    }

    public int getFriendColor() {
        return friendColor == null ? 0xFF34D399 : friendColor.getColor();
    }

    public boolean isFriendChatBadge() {
        return friendChatBadge == null || friendChatBadge.getValue();
    }

    public boolean isFriendNotifications() {
        return friendNotifications == null || friendNotifications.getValue();
    }

    public boolean isFriendNotificationSound() {
        return friendNotificationSound == null || friendNotificationSound.getValue();
    }

    public boolean isMiddleClickFriend() {
        return middleClickFriend == null || middleClickFriend.getValue();
    }

    public boolean isProtectFriendsInCombat() {
        return protectFriendsInCombat == null || protectFriendsInCombat.getValue();
    }

    public boolean isHighlightFriendsInEsp() {
        return highlightFriendsInEsp == null || highlightFriendsInEsp.getValue();
    }

    public boolean isRemoveSplash() {
        return removeSplash.getValue();
    }

    public boolean isContainerAnimations() {
        return containerAnimations == null || containerAnimations.getValue();
    }

    public boolean isContainerBlur() {
        return containerBlur == null || containerBlur.getValue();
    }

    public String getTitleBackground() {
        return titleBackground.getValue();
    }

    public void setTitleBackground(String mode) {
        titleBackground.setValue(mode);
    }

    public ModeSetting getTitleBackgroundSetting() {
        return titleBackground;
    }

    public boolean isModernButtons() {
        return modernButtons.getValue();
    }

    public boolean isExperimentalFeatures() {
        return experimentalFeatures != null && experimentalFeatures.getValue();
    }

    public BooleanSetting getExperimentalFeaturesSetting() {
        return experimentalFeatures;
    }

    public boolean isPaletteCrashSafeguard() {
        return paletteCrashSafeguard == null || paletteCrashSafeguard.getValue();
    }

    public BooleanSetting getPaletteCrashSafeguardSetting() {
        return paletteCrashSafeguard;
    }

    public void setModernButtons(boolean enabled) {
        modernButtons.setValue(enabled);
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
