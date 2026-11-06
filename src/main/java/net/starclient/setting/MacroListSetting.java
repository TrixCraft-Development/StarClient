package net.starclient.setting;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class MacroListSetting extends Setting<List<MacroListSetting.MacroEntry>> {

    public static class MacroEntry {
        private String command;
        private int keyBind;
        private boolean enabled;
        private transient boolean keyDownLastTick;

        public MacroEntry(String command, int keyBind, boolean enabled) {
            this.command = command == null ? "" : command.trim();
            this.keyBind = keyBind;
            this.enabled = enabled;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command == null ? "" : command.trim();
        }

        public int getKeyBind() {
            return keyBind;
        }

        public void setKeyBind(int keyBind) {
            this.keyBind = keyBind;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void toggleEnabled() {
            this.enabled = !this.enabled;
        }

        public boolean isKeyDownLastTick() {
            return keyDownLastTick;
        }

        public void setKeyDownLastTick(boolean keyDownLastTick) {
            this.keyDownLastTick = keyDownLastTick;
        }
    }

    public MacroListSetting(String name, String description) {
        super(name, description, new ArrayList<>());
    }

    public void addEntry(String command, int keyBind) {
        if (command == null || command.isBlank()) return;
        getValue().add(new MacroEntry(command.trim(), keyBind <= 0 ? GLFW.GLFW_KEY_UNKNOWN : keyBind, true));
    }

    public void removeAt(int index) {
        List<MacroEntry> list = getValue();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
        }
    }
}
