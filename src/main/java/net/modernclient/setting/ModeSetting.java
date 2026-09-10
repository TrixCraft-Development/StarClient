package net.modernclient.setting;

import java.util.List;

public class ModeSetting extends Setting<String> {
    private final List<String> modes;
    private int index;

    public ModeSetting(String name, String description, String defaultMode, List<String> modes) {
        super(name, description, defaultMode);
        this.modes = modes;
        this.index = Math.max(0, modes.indexOf(defaultMode));
    }

    public List<String> getModes() {
        return modes;
    }

    public void cycle() {
        if (modes.isEmpty()) return;
        index = (index + 1) % modes.size();
        setValue(modes.get(index));
    }

    @Override
    public void setValue(String value) {
        int newIndex = modes.indexOf(value);
        if (newIndex >= 0) {
            this.index = newIndex;
        }
        super.setValue(value);
    }
}
