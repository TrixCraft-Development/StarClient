package net.starclient.module;

import net.starclient.setting.BooleanSetting;
import net.starclient.setting.NoteListSetting;
import net.starclient.setting.Setting;
import net.starclient.setting.TextSetting;

import java.util.Collections;
import java.util.List;

/**
 * State helper for the "Pinned Notes" HUD checklist & sticky-notes module.
 */
public final class PinnedNotesState {

    private static Module module;

    private PinnedNotesState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static Module getModule() {
        return module;
    }

    public static List<NoteListSetting.NoteEntry> getNotes() {
        NoteListSetting setting = getListSetting();
        return setting != null ? setting.getValue() : Collections.emptyList();
    }

    public static boolean addNote(String text) {
        NoteListSetting setting = getListSetting();
        return setting != null && setting.addNote(text);
    }

    public static void toggleNoteAt(int index) {
        NoteListSetting setting = getListSetting();
        if (setting != null) {
            setting.toggleDoneAt(index);
        }
    }

    public static void removeNoteAt(int index) {
        NoteListSetting setting = getListSetting();
        if (setting != null) {
            setting.removeAt(index);
        }
    }

    public static void clearCompleted() {
        NoteListSetting setting = getListSetting();
        if (setting != null) {
            setting.clearCompleted();
        }
    }

    public static void clearAll() {
        NoteListSetting setting = getListSetting();
        if (setting != null) {
            setting.getValue().clear();
        }
    }

    public static String getTitle() {
        if (module != null && module.getSetting("Title") instanceof TextSetting ts) {
            String t = ts.getValue();
            if (t != null && !t.trim().isEmpty()) {
                return t.trim();
            }
        }
        return "Pinned Notes";
    }

    public static boolean isShowCheckboxes() {
        return getBool("Show Checkboxes", true);
    }

    public static boolean isShowCompleted() {
        return getBool("Show Completed", true);
    }

    public static boolean isHideWhenEmpty() {
        return getBool("Hide When Empty", false);
    }

    private static NoteListSetting getListSetting() {
        if (module == null) return null;
        Setting<?> s = module.getSetting("Notes List");
        return s instanceof NoteListSetting nls ? nls : null;
    }

    private static boolean getBool(String name, boolean def) {
        if (module == null) return def;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }
}
