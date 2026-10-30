package net.starclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.starclient.StarClient;
import net.starclient.changelog.ChangelogManager;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NickEntryListSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;
import net.starclient.setting.TextSetting;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {
    private static final String LEGACY_FOLDER = "config/starclient";

    private final File configFile;
    private final Gson gson;

    public ConfigManager() {
        migrateLegacyFolder(Minecraft.getInstance().gameDirectory);
        File dir = new File(Minecraft.getInstance().gameDirectory, StarClient.CONFIG_FOLDER);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.configFile = new File(dir, "client_config.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    private static void migrateLegacyFolder(File gameDir) {
        Path legacy = gameDir.toPath().resolve(LEGACY_FOLDER).normalize();
        Path target = gameDir.toPath().resolve(StarClient.CONFIG_FOLDER).normalize();
        if (legacy.equals(target) || !Files.isDirectory(legacy) || Files.exists(target)) return;
        try {
            Files.move(legacy, target);
            StarClient.LOGGER.info("Migrated {} -> {}", LEGACY_FOLDER, StarClient.CONFIG_FOLDER);
        } catch (IOException e) {
            StarClient.LOGGER.error("Failed to migrate {} to {}", LEGACY_FOLDER, target, e);
        }
    }

    public void saveConfig() {
        try {
            JsonObject root = new JsonObject();
            JsonObject modulesObj = getJsonObject();

            root.add("modules", modulesObj);
            root.add("hudLayout", StarClient.getInstance().getHudLayout().toJson());
            root.add("clientSettings", getClientSettingsJson());

            // Per-account cosmetics
            JsonObject perAccountObj = new JsonObject();
            for (java.util.Map.Entry<String, net.starclient.cosmetics.CosmeticManager.AccountCosmetics> entry :
                    StarClient.getInstance().getCosmeticManager().getAllAccountCosmetics().entrySet()) {
                JsonObject accObj = new JsonObject();
                net.starclient.cosmetics.CosmeticManager.AccountCosmetics c = entry.getValue();
                accObj.addProperty("equippedCape", c.equippedCape == null ? "" : c.equippedCape);
                accObj.addProperty("equippedWings", c.equippedWings == null ? "" : c.equippedWings);
                accObj.addProperty("equippedHeadwear", c.equippedHeadwear == null ? "" : c.equippedHeadwear);
                accObj.addProperty("equippedTrail", c.equippedTrail == null ? "" : c.equippedTrail);
                accObj.addProperty("trailMode", c.trailMode != null ? c.trailMode : "Always");
                accObj.addProperty("capeWaveSpeed", c.capeWaveSpeed);
                accObj.addProperty("capeWaveAmplitude", c.capeWaveAmplitude);
                accObj.addProperty("capeWindSway", c.capeWindSway);
                perAccountObj.add(entry.getKey(), accObj);
            }
            root.add("accountCosmetics", perAccountObj);

            String equippedCape = StarClient.getInstance().getCosmeticManager().getEquippedCape();
            root.addProperty("equippedCape", equippedCape == null ? "" : equippedCape);
            String equippedWings = StarClient.getInstance().getCosmeticManager().getEquippedWings();
            root.addProperty("equippedWings", equippedWings == null ? "" : equippedWings);
            String equippedHeadwear = StarClient.getInstance().getCosmeticManager().getEquippedHeadwear();
            root.addProperty("equippedHeadwear", equippedHeadwear == null ? "" : equippedHeadwear);
            String equippedTrail = StarClient.getInstance().getCosmeticManager().getEquippedTrail();
            root.addProperty("equippedTrail", equippedTrail == null ? "" : equippedTrail);
            root.addProperty("trailMode", StarClient.getInstance().getCosmeticManager().getTrailMode());
            root.addProperty("capeWaveSpeed", StarClient.getInstance().getCosmeticManager().getCapeWaveSpeed());
            root.addProperty("capeWaveAmplitude", StarClient.getInstance().getCosmeticManager().getCapeWaveAmplitude());
            root.addProperty("capeWindSway", StarClient.getInstance().getCosmeticManager().isCapeWindSway());
            root.addProperty("lastSeenChangelogVersion", ChangelogManager.getInstance().getLastSeenVersion());

            try (FileWriter writer = new FileWriter(configFile)) {
                gson.toJson(root, writer);
            }
            StarClient.LOGGER.info("Client configuration saved successfully.");
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to save client configuration!", e);
        }
    }

    private static @NonNull JsonObject getJsonObject() {
        JsonObject modulesObj = new JsonObject();

        for (Module module : StarClient.getInstance().getModuleManager().getModules()) {
            JsonObject modObj = new JsonObject();
            modObj.addProperty("enabled", module.isEnabled());
            modObj.addProperty("favorite", module.isFavorite());
            modObj.addProperty("keybind", module.getKeyBind());

            if (!module.getSettings().isEmpty()) {
                JsonObject settingsObj = getJsonObject(module);
                modObj.add("settings", settingsObj);
            }

            modulesObj.add(module.getName(), modObj);
        }
        return modulesObj;
    }

    private static @NonNull JsonObject getJsonObject(Module module) {
        JsonObject settingsObj = new JsonObject();
        for (Setting<?> setting : module.getSettings()) {
            if (setting instanceof BooleanSetting bs) {
                settingsObj.addProperty(setting.getName(), bs.getValue());
            } else if (setting instanceof NumberSetting ns) {
                settingsObj.addProperty(setting.getName(), ns.getValue());
            } else if (setting instanceof ModeSetting ms) {
                settingsObj.addProperty(setting.getName(), ms.getValue());
            } else if (setting instanceof TextSetting ts) {
                settingsObj.addProperty(setting.getName(), ts.getValue());
            } else if (setting instanceof NickEntryListSetting nl) {
                JsonArray entries = new JsonArray();
                for (NickEntryListSetting.NickEntry entry : nl.getValue()) {
                    JsonObject entryObj = new JsonObject();
                    entryObj.addProperty("real", entry.real());
                    entryObj.addProperty("nick", entry.nick());
                    entries.add(entryObj);
                }
                settingsObj.add(setting.getName(), entries);
            }
        }
        return settingsObj;
    }

    private static @NonNull JsonObject getClientSettingsJson() {
        JsonObject settingsObj = new JsonObject();
        for (Setting<?> setting : StarClient.getInstance().getClientSettings().getSettings()) {
            if (setting instanceof BooleanSetting bs) {
                settingsObj.addProperty(setting.getName(), bs.getValue());
            } else if (setting instanceof NumberSetting ns) {
                settingsObj.addProperty(setting.getName(), ns.getValue());
            } else if (setting instanceof ModeSetting ms) {
                settingsObj.addProperty(setting.getName(), ms.getValue());
            } else if (setting instanceof TextSetting ts) {
                settingsObj.addProperty(setting.getName(), ts.getValue());
            }
        }
        return settingsObj;
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
                for (Module module : StarClient.getInstance().getModuleManager().getModules()) {
                    if (modulesObj.has(module.getName())) {
                        JsonObject modObj = modulesObj.getAsJsonObject(module.getName());
                        if (modObj.has("enabled")) {
                            module.setEnabled(modObj.get("enabled").getAsBoolean());
                        }
                        if (modObj.has("favorite")) {
                            module.setFavorite(modObj.get("favorite").getAsBoolean());
                        }
                        if (modObj.has("keybind")) {
                            module.setKeyBind(modObj.get("keybind").getAsInt());
                        }
                        if (modObj.has("settings")) {
                            JsonObject settingsObj = modObj.getAsJsonObject("settings");
                            for (Setting<?> setting : module.getSettings()) {
                                if (settingsObj.has(setting.getName())) {
                                    switch (setting) {
                                        case BooleanSetting bs ->
                                                bs.setValue(settingsObj.get(setting.getName()).getAsBoolean());
                                        case NumberSetting ns ->
                                                ns.setValue(settingsObj.get(setting.getName()).getAsDouble());
                                        case ModeSetting ms ->
                                                ms.setValue(settingsObj.get(setting.getName()).getAsString());
                                        case TextSetting ts ->
                                                ts.setValue(settingsObj.get(setting.getName()).getAsString());
                                        case NickEntryListSetting nl -> {
                                            nl.getValue().clear();
                                            if (settingsObj.get(nl.getName()).isJsonArray()) {
                                                for (var elem : settingsObj.get(nl.getName()).getAsJsonArray()) {
                                                    JsonObject obj = elem.getAsJsonObject();
                                                    String real = obj.has("real") ? obj.get("real").getAsString() : "";
                                                    String nick = obj.has("nick") ? obj.get("nick").getAsString() : "";
                                                    nl.getValue().add(new NickEntryListSetting.NickEntry(real, nick));
                                                }
                                            }
                                        }
                                        default -> {
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (root.has("hudLayout")) {
                StarClient.getInstance().getHudLayout().fromJson(root.getAsJsonObject("hudLayout"));
            }
            if (root.has("clientSettings")) {
                JsonObject settingsObj = root.getAsJsonObject("clientSettings");
                for (Setting<?> setting : StarClient.getInstance().getClientSettings().getSettings()) {
                    if (settingsObj.has(setting.getName())) {
                        switch (setting) {
                            case BooleanSetting bs ->
                                    bs.setValue(settingsObj.get(setting.getName()).getAsBoolean());
                            case NumberSetting ns ->
                                    ns.setValue(settingsObj.get(setting.getName()).getAsDouble());
                            case ModeSetting ms ->
                                    ms.setValue(settingsObj.get(setting.getName()).getAsString());
                            case TextSetting ts ->
                                    ts.setValue(settingsObj.get(setting.getName()).getAsString());
                            default -> {
                            }
                        }
                    }
                }
            }
            if (root.has("accountCosmetics") && root.get("accountCosmetics").isJsonObject()) {
                JsonObject perAccountObj = root.getAsJsonObject("accountCosmetics");
                for (String key : perAccountObj.keySet()) {
                    if (perAccountObj.get(key).isJsonObject()) {
                        JsonObject accObj = perAccountObj.getAsJsonObject(key);
                        net.starclient.cosmetics.CosmeticManager.AccountCosmetics c = new net.starclient.cosmetics.CosmeticManager.AccountCosmetics();
                        if (accObj.has("equippedCape")) c.equippedCape = accObj.get("equippedCape").getAsString();
                        if (accObj.has("equippedWings")) c.equippedWings = accObj.get("equippedWings").getAsString();
                        if (accObj.has("equippedHeadwear")) c.equippedHeadwear = accObj.get("equippedHeadwear").getAsString();
                        if (accObj.has("equippedTrail")) c.equippedTrail = accObj.get("equippedTrail").getAsString();
                        if (accObj.has("trailMode")) c.trailMode = accObj.get("trailMode").getAsString();
                        if (accObj.has("capeWaveSpeed")) c.capeWaveSpeed = accObj.get("capeWaveSpeed").getAsFloat();
                        if (accObj.has("capeWaveAmplitude")) c.capeWaveAmplitude = accObj.get("capeWaveAmplitude").getAsFloat();
                        if (accObj.has("capeWindSway")) c.capeWindSway = accObj.get("capeWindSway").getAsBoolean();
                        StarClient.getInstance().getCosmeticManager().setAccountCosmetics(key, c);
                    }
                }
            } else if (root.has("equippedCape")) {
                net.starclient.cosmetics.CosmeticManager.AccountCosmetics c = StarClient.getInstance().getCosmeticManager().getActiveCosmetics();
                c.equippedCape = root.get("equippedCape").getAsString();
                if (root.has("equippedWings")) c.equippedWings = root.get("equippedWings").getAsString();
                if (root.has("equippedHeadwear")) c.equippedHeadwear = root.get("equippedHeadwear").getAsString();
                if (root.has("equippedTrail")) c.equippedTrail = root.get("equippedTrail").getAsString();
                if (root.has("trailMode")) c.trailMode = root.get("trailMode").getAsString();
                if (root.has("capeWaveSpeed")) c.capeWaveSpeed = root.get("capeWaveSpeed").getAsFloat();
                if (root.has("capeWaveAmplitude")) c.capeWaveAmplitude = root.get("capeWaveAmplitude").getAsFloat();
                if (root.has("capeWindSway")) c.capeWindSway = root.get("capeWindSway").getAsBoolean();
            }
            if (root.has("lastSeenChangelogVersion")) {
                ChangelogManager.getInstance().setLastSeenVersion(root.get("lastSeenChangelogVersion").getAsString());
            }
            StarClient.LOGGER.info("Client configuration loaded successfully.");
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to load client configuration!", e);
        }
    }
}
