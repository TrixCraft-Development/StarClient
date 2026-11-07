package net.starclient.setting;

import java.util.ArrayList;
import java.util.List;

public class NoteListSetting extends Setting<List<NoteListSetting.NoteEntry>> {

    public static final class NoteEntry {
        private String text;
        private boolean done;

        public NoteEntry(String text, boolean done) {
            this.text = text == null ? "" : text.trim();
            this.done = done;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text == null ? "" : text.trim();
        }

        public boolean isDone() {
            return done;
        }

        public void setDone(boolean done) {
            this.done = done;
        }

        public void toggleDone() {
            this.done = !this.done;
        }
    }

    public NoteListSetting(String name, String description) {
        super(name, description, new ArrayList<>());
    }

    public boolean addNote(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return false;
        getValue().add(new NoteEntry(trimmed, false));
        return true;
    }

    public void toggleDoneAt(int index) {
        List<NoteEntry> list = getValue();
        if (index >= 0 && index < list.size()) {
            list.get(index).toggleDone();
        }
    }

    public void removeAt(int index) {
        List<NoteEntry> list = getValue();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
        }
    }

    public void clearCompleted() {
        getValue().removeIf(NoteEntry::isDone);
    }
}
