package net.starclient.setting;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores custom sound IDs or substring filters for the "Sound Muffler" module.
 */
public class MutedSoundListSetting extends Setting<List<String>> {

    public MutedSoundListSetting(String name, String description) {
        super(name, description, new ArrayList<>());
    }

    public boolean addFilter(String filter) {
        if (filter == null) return false;
        String trimmed = filter.trim().toLowerCase(java.util.Locale.ROOT);
        if (trimmed.isEmpty()) return false;
        for (String existing : getValue()) {
            if (existing.equalsIgnoreCase(trimmed)) {
                return false;
            }
        }
        getValue().add(trimmed);
        return true;
    }

    public void removeAt(int index) {
        List<String> list = getValue();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
        }
    }
}
