package net.starclient.module;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.starclient.StarClient;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.MacroListSetting;
import net.starclient.setting.MacroListSetting.MacroEntry;
import net.starclient.setting.Setting;

import java.util.Collections;
import java.util.List;

public final class QuickCommandsState {
    private static Module module;

    private QuickCommandsState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isActive() {
        return module != null && module.isEnabled() && module.canUse();
    }

    private static MacroListSetting listSetting() {
        if (module == null) return null;
        Setting<?> s = module.getSetting("Command Macros");
        return s instanceof MacroListSetting mls ? mls : null;
    }

    public static List<MacroEntry> getEntries() {
        MacroListSetting s = listSetting();
        return s == null ? Collections.emptyList() : s.getValue();
    }

    public static void addEntry(String command, int keyBind) {
        MacroListSetting s = listSetting();
        if (s != null) {
            s.addEntry(command, keyBind);
            save();
        }
    }

    public static void removeAt(int index) {
        MacroListSetting s = listSetting();
        if (s != null) {
            s.removeAt(index);
            save();
        }
    }

    public static void handleKeybinds(Minecraft client) {
        if (!isActive() || client.player == null) {
            return;
        }
        boolean screenOpen = client.gui.screen() != null;
        List<MacroEntry> entries = getEntries();
        for (MacroEntry entry : entries) {
            int key = entry.getKeyBind();
            boolean down = false;
            if (!screenOpen && entry.isEnabled() && key > 0) {
                down = InputConstants.isKeyDown(client.getWindow(), key);
            }
            if (down && !entry.isKeyDownLastTick()) {
                executeMacro(client, entry.getCommand());
            }
            entry.setKeyDownLastTick(down);
        }
    }

    public static void executeMacro(Minecraft client, String rawCommand) {
        if (client == null || client.player == null) return;
        if (rawCommand == null || rawCommand.isBlank()) return;
        String trimmed = rawCommand.trim();
        if (trimmed.startsWith("/")) {
            String cmd = trimmed.substring(1).trim();
            if (!cmd.isEmpty()) {
                client.player.connection.sendCommand(cmd);
            }
        } else {
            client.player.connection.sendChat(trimmed);
        }

        if (module != null && module.getSetting("Chat Feedback") instanceof BooleanSetting bs && bs.getValue()) {
            client.gui.hud.getChat().addClientSystemMessage(Component.literal(
                    "§b✦ Star Client §8» §7Executed macro: §f" + trimmed
            ));
        }
    }

    public static void save() {
        if (StarClient.getInstance() != null && StarClient.getInstance().getConfigManager() != null) {
            StarClient.getInstance().getConfigManager().saveConfig();
        }
    }
}
