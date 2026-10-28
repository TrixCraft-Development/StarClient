package net.starclient.module;

public enum Category {
    COMBAT("Combat", "🗡"),
    CHEATS("Cheats", "⚔"),
    MOVEMENT("Movement", "🚀"),
    RENDER("Render", "👁"),
    COSMETICS("Cosmetics", "✨"),
    PLAYER("Player", "🛡"),
    WORLD("World", "🌍"),
    SERVER("Server", "🖥"),
    UI("UI & Visuals", "🎨"),
    SETTINGS("Settings", "⚙");

    private final String name;
    private final String icon;

    Category(String name, String icon) {
        this.name = name;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }
}
