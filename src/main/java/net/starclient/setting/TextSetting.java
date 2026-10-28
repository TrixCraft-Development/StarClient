package net.starclient.setting;

public class TextSetting extends Setting<String> {
    private final boolean secret;

    public TextSetting(String name, String description, String defaultValue) {
        this(name, description, defaultValue, false);
    }

    public TextSetting(String name, String description, String defaultValue, boolean secret) {
        super(name, description, defaultValue);
        this.secret = secret;
    }

    public boolean isSecret() {
        return secret;
    }
}
