package net.modernclient.rank;

import java.util.Locale;

public enum Rank {
    USER("Normal User", 0xFFFFFF, "user", "normal", "normal_user", "default"),
    PREMIUM("Premium / Plus", 0xFF55FF, "premium", "plus", "premium_plus", "premium / plus"),
    PARTNER("Partner / VIP", 0xAA00AA, "partner", "vip", "partner_vip", "partner / vip"),
    QA("QA", 0xFFFF55, "qa"),
    MODERATOR("Moderator", 0x00AAAA, "moderator", "mod"),
    DEVELOPER("Developer", 0x55FFFF, "developer", "dev"),
    ADMIN("Admin", 0xFF5555, "admin"),
    OWNER("Owner", 0xAA0000, "owner");

    private final String displayName;
    private final int rgb;
    private final String[] aliases;

    Rank(String displayName, int rgb, String... aliases) {
        this.displayName = displayName;
        this.rgb = rgb & 0x00FFFFFF;
        this.aliases = aliases;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 24-bit RGB color suitable for {@code Style.withColor(int)}. */
    public int getRgb() {
        return rgb;
    }

    /** 32-bit opaque ARGB color suitable for HUD/GUI rendering. */
    public int getArgb() {
        return 0xFF000000 | rgb;
    }

    /** Returns true if this rank is equal to or higher than {@code required}. */
    public boolean hasAtLeast(Rank required) {
        if (required == null) {
            return true;
        }
        return this.ordinal() >= required.ordinal();
    }

    public static Rank fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (Rank rank : values()) {
            if (rank.name().toLowerCase(Locale.ROOT).equals(normalized)
                    || rank.displayName.toLowerCase(Locale.ROOT).equals(normalized)) {
                return rank;
            }
            for (String alias : rank.aliases) {
                if (alias.equals(normalized)) {
                    return rank;
                }
            }
        }
        return null;
    }
}
