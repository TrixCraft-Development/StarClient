package net.starclient.cosmetics;

public enum CosmeticType {
    CAPE("Capes", "✦"),
    WINGS("Wings", "★"),
    HEADWEAR("Headwear", "👑"),
    TRAIL("Trails", "✨"),
    PHYSICS("Wavey Physics", "⚙");

    private final String displayName;
    private final String icon;

    CosmeticType(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }
}
