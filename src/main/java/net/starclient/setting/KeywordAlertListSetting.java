package net.starclient.setting;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores user-defined chat alert keywords/phrases for the "Keyword Alerts" module.
 */
public class KeywordAlertListSetting extends Setting<List<String>> {

    public KeywordAlertListSetting(String name, String description) {
        super(name, description, new ArrayList<>());
    }

    public boolean addKeyword(String keyword) {
        if (keyword == null) return false;
        String trimmed = keyword.trim();
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
