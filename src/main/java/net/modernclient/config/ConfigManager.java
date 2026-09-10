package net.modernclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.modernclient.ModernClient;
import net.modernclient.module.Module;
import net.modernclient.setting.BooleanSetting;
import net.modernclient.setting.ModeSetting;
import net.modernclient.setting.NumberSetting;
import net.modernclient.setting.Setting;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final String LEGACY_FOLDER = "config/modernclient";

    private final File configFile;
    private final Gson gson;

    public ConfigManager() {
        migrateLegacyFolder(Minecraft.getInstance().gameDirectory);
        File dir = new File(Minecraft.getInstance().gameDirectory, ModernClient.CONFIG_FOLDER);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.configFile = new File(dir, "client_config.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    private static void migrateLegacyFolder(File gameDir) {
        Path legacy = gameDir.toPath().resolve(LEGACY_FOLDER).normalize();
        Path target = gameDir.toPath().resolve(ModernClient.CONFIG_FOLDER).normalize();
        if (legacy.equals(target) || !Files.isDirectory(legacy) || Files.exists(target)) return;
        try {
            Files.move(legacy, target);
            ModernClient.LOGGER.info("Migrated {} -> {}", LEGACY_FOLDER, ModernClient.CONFIG_FOLDER);
        } catch (IOException e) {
            ModernClient.LOGGER.error("Failed to migrate {} to {}", LEGACY_FOLDER, target, e);
        }
    }

    public void saveConfig() {
        try {
            JsonObject root = new JsonObject();
            JsonObject modulesObj = new JsonObject();

            for (Module module : ModernClient.getInstance().getModuleManager().getModules()) {
                JsonObject modObj = new JsonObject();
                modObj.addProperty("enabled", module.isEnabled());
                modObj.addProperty("keybind", module.getKeyBind());

                if (!module.getSettings().isEmpty()) {
                    JsonObject settingsObj = new JsonObject();
                    for (Setting<?> setting : module.getSettings()) {
                        if (setting instanceof BooleanSetting bs) {
                            settingsObj.addProperty(setting.getName(), bs.getValue());
                        } else if (setting instanceof NumberSetting ns) {
                            settingsObj.addProperty(setting.getName(), ns.getValue());
                        } else if (setting instanceof ModeSetting ms) {
                            settingsObj.addProperty(setting.getName(), ms.getValue());
                        }
                    }
                    modObj.add("settings", settingsObj);
                }

                modulesObj.add(module.getName(), modObj);
            }

            root.add("modules", modulesObj);
            root.add("hudLayout", ModernClient.getInstance().getHudLayout().toJson());
            String equippedCape = ModernClient.getInstance().getCosmeticManager().getEquippedCape();
            root.addProperty("equippedCape", equippedCape == null ? "" : equippedCape);
            String equippedSkin = ModernClient.getInstance().getCosmeticManager().getEquippedSkin();
            root.addProperty("equippedSkin", equippedSkin == null ? "" : equippedSkin);

            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(root, writer);
            }
            ModernClient.LOGGER.info("Client configuration saved successfully.");
        } catch (Exception e) {
            ModernClient.LOGGER.error("Failed to save client configuration!", e);
        }
    }

    public void loadConfig() {
        if (!configFile.exists()) {
            saveConfig();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("modules")) {
                JsonObject modulesObj = root.getAsJsonObject("modules");
                for (Module module : ModernClient.getInstance().getModuleManager().getModules()) {
                    if (modulesObj.has(module.getName())) {
                        JsonObject modObj = modulesObj.getAsJsonObject(module.getName());
                        if (modObj.has("enabled")) {
                            module.setEnabled(modObj.get("enabled").getAsBoolean());
                        }
                        if (modObj.has("keybind")) {
                            module.setKeyBind(modObj.get("keybind").getAsInt());
                        }
                        if (modObj.has("settings")) {
                            JsonObject settingsObj = modObj.getAsJsonObject("settings");
                            for (Setting<?> setting : module.getSettings()) {
                                if (settingsObj.has(setting.getName())) {
                                    if (setting instanceof BooleanSetting bs) {
                                        bs.setValue(settingsObj.get(setting.getName()).getAsBoolean());
                                    } else if (setting instanceof NumberSetting ns) {
                                        ns.setValue(settingsObj.get(setting.getName()).getAsDouble());
                                    } else if (setting instanceof ModeSetting ms) {
                                        ms.setValue(settingsObj.get(setting.getName()).getAsString());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (root.has("hudLayout")) {
                ModernClient.getInstance().getHudLayout().fromJson(root.getAsJsonObject("hudLayout"));
            }
            if (root.has("equippedCape")) {
                String equippedCape = root.get("equippedCape").getAsString();
                ModernClient.getInstance().getCosmeticManager().setEquippedCape(equippedCape);
            }
            if (root.has("equippedSkin")) {
                String equippedSkin = root.get("equippedSkin").getAsString();
                ModernClient.getInstance().getCosmeticManager().setEquippedSkin(equippedSkin);
            }
            ModernClient.LOGGER.info("Client configuration loaded successfully.");
        } catch (Exception e) {
            ModernClient.LOGGER.error("Failed to load client configuration!", e);
        }
    }
}
