package net.modernclient.setting;

import java.util.ArrayList;
import java.util.List;

public class NickEntryListSetting extends Setting<List<NickEntryListSetting.NickEntry>> {

    public record NickEntry(String real, String nick) {
    }

    public NickEntryListSetting(String name, String description) {
        super(name, description, new ArrayList<>());
    }

    public void addEntry(String real, String nick) {
        if (real == null || real.isBlank()) return;
        String trimmedReal = real.trim();
        String trimmedNick = nick == null ? "" : nick.trim();
        getValue().removeIf(e -> e.real().equalsIgnoreCase(trimmedReal));
        getValue().add(new NickEntry(trimmedReal, trimmedNick));
    }

    public void removeEntry(String real) {
        getValue().removeIf(e -> e.real().equalsIgnoreCase(real));
    }

    public String nickFor(String real) {
        if (real == null) return null;
        for (NickEntry entry : getValue()) {
            if (entry.real().equalsIgnoreCase(real)) {
                return entry.nick().isBlank() ? null : entry.nick();
            }
        }
        return null;
    }
}