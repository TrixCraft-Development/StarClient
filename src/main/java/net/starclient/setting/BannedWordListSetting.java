package net.starclient.setting;

import java.util.ArrayList;
import java.util.List;

public class BannedWordListSetting extends Setting<List<String>> {

    public BannedWordListSetting(String name, String description) {
        super(name, description, new ArrayList<>());
    }

    public boolean addWord(String word) {
        if (word == null) return false;
        String trimmed = word.trim();
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
