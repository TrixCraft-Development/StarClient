package net.starclient.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.starclient.StarClient;
import net.starclient.module.Module;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ProfileManager {
    private final List<ClientProfile> profiles = new ArrayList<>();
    private String activeProfileId = "default";

    public record SavedServerInfo(String name, String ip) {}

    public ProfileManager() {
        ClientProfile def = new ClientProfile("default", "Default", "⭐");
        profiles.add(def);
    }

    public List<ClientProfile> getProfiles() {
        return Collections.unmodifiableList(profiles);
    }

    public ClientProfile getProfileById(String id) {
        if (id == null) return null;
        for (ClientProfile p : profiles) {
            if (p.getId().equals(id)) {
                return p;
            }
        }
        return null;
    }

    public ClientProfile getActiveProfile() {
        ClientProfile active = getProfileById(activeProfileId);
        if (active == null) {
            if (profiles.isEmpty()) {
                ClientProfile def = new ClientProfile("default", "Default", "⭐");
                profiles.add(def);
            }
            active = profiles.getFirst();
            activeProfileId = active.getId();
        }
        return active;
    }

    public String getActiveProfileId() {
        return getActiveProfile().getId();
    }

    public void syncActiveProfileSnapshot() {
        ConfigManager cm = StarClient.getInstance().getConfigManager();
        if (cm != null) {
            getActiveProfile().setData(cm.captureStateJson());
        }
    }

    public void switchProfile(String profileId) {
        switchProfile(profileId, false);
    }

    public void switchProfile(String profileId, boolean silent) {
        ClientProfile target = getProfileById(profileId);
        if (target == null) return;

        ClientProfile current = getActiveProfile();
        ConfigManager cm = StarClient.getInstance().getConfigManager();
        if (cm != null) {
            // Save outgoing profile state first
            current.setData(cm.captureStateJson());
        }

        this.activeProfileId = target.getId();

        if (cm != null) {
            cm.applyStateJson(target.getData());
            cm.saveConfig();
        }

        if (!silent) {
            StarClient.LOGGER.info("Switched active profile to '{}' ({})", target.getName(), target.getId());
        }
    }

    public ClientProfile createProfile(String name, String icon, boolean copyCurrent) {
        String cleanName = (name == null || name.isBlank()) ? "New Profile" : name.trim();
        String baseSlug = cleanName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        if (baseSlug.isBlank()) baseSlug = "profile";
        String id = baseSlug;
        int counter = 2;
        while (getProfileById(id) != null) {
            id = baseSlug + "_" + counter++;
        }

        ConfigManager cm = StarClient.getInstance().getConfigManager();
        if (cm != null) {
            // Persist current profile before creating & switching
            getActiveProfile().setData(cm.captureStateJson());
        }

        String chosenIcon = (icon == null || icon.isBlank()) ? pickSuggestedIcon(cleanName) : icon;
        ClientProfile created = new ClientProfile(id, cleanName, chosenIcon);

        if (copyCurrent && cm != null) {
            created.setData(cm.captureStateJson());
        } else if (cm != null) {
            // Reset live state to defaults, capture it, and make it the new profile's data
            resetLiveClientToDefaults();
            created.setData(cm.captureStateJson());
        }

        profiles.add(created);
        this.activeProfileId = created.getId();

        if (cm != null) {
            cm.applyStateJson(created.getData());
            cm.saveConfig();
        }
        return created;
    }

    public ClientProfile duplicateProfile(String sourceId) {
        syncActiveProfileSnapshot();
        ClientProfile src = getProfileById(sourceId);
        if (src == null) src = getActiveProfile();

        String newName = src.getName() + " Copy";
        String baseSlug = newName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        String id = baseSlug;
        int counter = 2;
        while (getProfileById(id) != null) {
            id = baseSlug + "_" + counter++;
        }

        ClientProfile copy = new ClientProfile(id, newName, src.getIcon());
        copy.setData(src.getData());
        profiles.add(copy);

        ConfigManager cm = StarClient.getInstance().getConfigManager();
        if (cm != null) {
            cm.saveConfig();
        }
        return copy;
    }

    public void deleteProfile(String profileId) {
        if (profiles.size() <= 1) return;
        ClientProfile target = getProfileById(profileId);
        if (target == null) return;

        boolean wasActive = target.getId().equals(getActiveProfileId());
        profiles.remove(target);

        ConfigManager cm = StarClient.getInstance().getConfigManager();
        if (wasActive) {
            ClientProfile next = profiles.getFirst();
            this.activeProfileId = next.getId();
            if (cm != null) {
                cm.applyStateJson(next.getData());
            }
        }
        if (cm != null) {
            cm.saveConfig();
        }
    }

    public void renameProfile(String profileId, String newName) {
        ClientProfile p = getProfileById(profileId);
        if (p != null && newName != null && !newName.isBlank()) {
            p.setName(newName.trim());
            ConfigManager cm = StarClient.getInstance().getConfigManager();
            if (cm != null) cm.saveConfig();
        }
    }

    public void cycleProfileIcon(String profileId) {
        ClientProfile p = getProfileById(profileId);
        if (p != null) {
            p.cycleIcon();
            ConfigManager cm = StarClient.getInstance().getConfigManager();
            if (cm != null) cm.saveConfig();
        }
    }

    public void setProfileKeyBind(String profileId, int keyBind) {
        ClientProfile p = getProfileById(profileId);
        if (p != null) {
            p.setKeyBind(keyBind);
            ConfigManager cm = StarClient.getInstance().getConfigManager();
            if (cm != null) cm.saveConfig();
        }
    }

    public void handleHotkeys(Minecraft client) {
        if (client == null) return;
        boolean screenOpen = client.gui.screen() != null;
        for (ClientProfile p : profiles) {
            int key = p.getKeyBind();
            boolean down = false;
            if (!screenOpen && key > 0) {
                down = InputConstants.isKeyDown(client.getWindow(), key);
            }
            if (down && !p.isKeyDownLastTick()) {
                if (!p.getId().equals(getActiveProfileId())) {
                    switchProfile(p.getId(), false);
                    notifyAutoSwitch(p, "Hotkey");
                }
            }
            p.setKeyDownLastTick(down);
        }
    }

    public boolean exportProfileToClipboard(String profileId) {
        syncActiveProfileSnapshot();
        ClientProfile p = getProfileById(profileId);
        if (p == null) return false;
        try {
            String jsonStr = p.toJson().toString();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
                gzip.write(jsonStr.getBytes(StandardCharsets.UTF_8));
            }
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray());
            String code = "STAR-PROFILE:" + encoded;
            Minecraft.getInstance().keyboardHandler.setClipboard(code);
            return true;
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to export profile to clipboard", e);
            return false;
        }
    }

    public ClientProfile importProfileFromClipboard() {
        try {
            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clip.isBlank()) return null;
            String trimmed = clip.trim();
            String jsonStr;
            if (trimmed.startsWith("STAR-PROFILE:")) {
                String b64 = trimmed.substring("STAR-PROFILE:".length()).trim();
                byte[] compressed = Base64.getUrlDecoder().decode(b64);
                try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                    jsonStr = new String(gis.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else if (trimmed.startsWith("{")) {
                jsonStr = trimmed;
            } else {
                return null;
            }

            JsonObject obj = JsonParser.parseString(jsonStr).getAsJsonObject();
            ClientProfile parsed = ClientProfile.fromJson(obj);

            String cleanName = parsed.getName();
            if (cleanName == null || cleanName.isBlank()) cleanName = "Imported Profile";
            String baseName = cleanName;
            int nameCount = 2;
            while (hasProfileWithName(cleanName)) {
                cleanName = baseName + " (" + nameCount++ + ")";
            }

            String baseSlug = cleanName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
            if (baseSlug.isBlank()) baseSlug = "imported_profile";
            String id = baseSlug;
            int idCount = 2;
            while (getProfileById(id) != null) {
                id = baseSlug + "_" + idCount++;
            }

            ClientProfile imported = new ClientProfile(id, cleanName, parsed.getIcon());
            imported.setKeyBind(parsed.getKeyBind());
            imported.getLinkedWorlds().addAll(parsed.getLinkedWorlds());
            imported.getLinkedServers().addAll(parsed.getLinkedServers());
            imported.setData(parsed.getData());

            syncActiveProfileSnapshot();
            profiles.add(imported);
            ConfigManager cm = StarClient.getInstance().getConfigManager();
            if (cm != null) {
                cm.saveConfig();
            }
            return imported;
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to import profile from clipboard", e);
            return null;
        }
    }

    private boolean hasProfileWithName(String name) {
        for (ClientProfile p : profiles) {
            if (p.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public void toggleWorldLink(String profileId, String worldName) {
        if (worldName == null || worldName.isBlank()) return;
        ClientProfile target = getProfileById(profileId);
        if (target == null) return;
        String clean = worldName.trim();

        if (target.hasWorldLinked(clean)) {
            target.getLinkedWorlds().removeIf(w -> w.trim().equalsIgnoreCase(clean));
        } else {
            // Ensure a world is only linked to one profile at a time
            for (ClientProfile other : profiles) {
                other.getLinkedWorlds().removeIf(w -> w.trim().equalsIgnoreCase(clean));
            }
            target.getLinkedWorlds().add(clean);
        }

        ConfigManager cm = StarClient.getInstance().getConfigManager();
        if (cm != null) cm.saveConfig();
    }

    public void toggleServerLink(String profileId, String serverIp) {
        if (serverIp == null || serverIp.isBlank()) return;
        ClientProfile target = getProfileById(profileId);
        if (target == null) return;
        String norm = ClientProfile.normalizeServerIp(serverIp);
        if (norm.isEmpty()) return;

        if (target.hasServerLinked(norm)) {
            target.getLinkedServers().removeIf(s -> ClientProfile.normalizeServerIp(s).equals(norm));
        } else {
            // Ensure a server IP is only linked to one profile at a time
            for (ClientProfile other : profiles) {
                other.getLinkedServers().removeIf(s -> ClientProfile.normalizeServerIp(s).equals(norm));
            }
            target.getLinkedServers().add(norm);
        }

        ConfigManager cm = StarClient.getInstance().getConfigManager();
        if (cm != null) cm.saveConfig();
    }

    public ClientProfile findProfileLinkedToWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return null;
        for (ClientProfile p : profiles) {
            if (p.hasWorldLinked(worldName)) {
                return p;
            }
        }
        return null;
    }

    public ClientProfile findProfileLinkedToServer(String serverIp) {
        if (serverIp == null || serverIp.isBlank()) return null;
        for (ClientProfile p : profiles) {
            if (p.hasServerLinked(serverIp)) {
                return p;
            }
        }
        return null;
    }

    public void onWorldJoined(String levelName, String folderName) {
        ClientProfile matched = null;
        for (ClientProfile p : profiles) {
            if ((levelName != null && p.hasWorldLinked(levelName))
                    || (folderName != null && p.hasWorldLinked(folderName))) {
                matched = p;
                break;
            }
        }
        if (matched != null && !matched.getId().equals(getActiveProfileId())) {
            switchProfile(matched.getId(), false);
            notifyAutoSwitch(matched, levelName != null ? levelName : folderName);
        }
    }

    public void onServerJoined(String serverIp, String serverName) {
        ClientProfile matched = null;
        for (ClientProfile p : profiles) {
            if (p.matchesServerAddress(serverIp)
                    || (serverName != null && !serverName.isBlank() && p.hasServerLinked(serverName))) {
                matched = p;
                break;
            }
        }
        if (matched != null && !matched.getId().equals(getActiveProfileId())) {
            switchProfile(matched.getId(), false);
            notifyAutoSwitch(matched, serverIp != null ? serverIp : serverName);
        }
    }

    private void notifyAutoSwitch(ClientProfile profile, String targetLabel) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.gui.hud.getChat().addClientSystemMessage(Component.literal(
                "§b✦ Star Client §8» §7Auto-switched to profile §f" + profile.getIcon() + " " + profile.getName()
                        + " §8(" + targetLabel + ")"
        )));
    }

    public static String getCurrentWorldName() {
        Minecraft client = Minecraft.getInstance();
        if (client.isLocalServer() && client.getSingleplayerServer() != null) {
            IntegratedServer server = client.getSingleplayerServer();
            server.getWorldData().getLevelName();
            if (!server.getWorldData().getLevelName().isBlank()) {
                return server.getWorldData().getLevelName().trim();
            }
            try {
                Path root = server.getWorldPath(LevelResource.ROOT).normalize();
                if (root.getFileName() != null) {
                    return root.getFileName().toString();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static String getCurrentServerIp() {
        Minecraft client = Minecraft.getInstance();
        if (!client.isLocalServer() && client.getCurrentServer() != null) {
            ServerData sd = client.getCurrentServer();
            if (!sd.ip.isBlank()) {
                return ClientProfile.normalizeServerIp(sd.ip);
            }
        }
        return null;
    }

    public List<String> getDetectedSingleplayerWorlds() {
        Set<String> worlds = new LinkedHashSet<>();
        String current = getCurrentWorldName();
        if (current != null && !current.isBlank()) {
            worlds.add(current);
        }
        Minecraft client = Minecraft.getInstance();
        File savesDir = new File(client.gameDirectory, "saves");
        if (savesDir.isDirectory()) {
            File[] children = savesDir.listFiles(File::isDirectory);
            if (children != null) {
                for (File dir : children) {
                    if (new File(dir, "level.dat").exists()) {
                        worlds.add(dir.getName());
                    }
                }
            }
        }
        for (ClientProfile p : profiles) {
            worlds.addAll(p.getLinkedWorlds());
        }
        return new ArrayList<>(worlds);
    }

    public List<SavedServerInfo> getDetectedMultiplayerServers() {
        List<SavedServerInfo> result = new ArrayList<>();
        Set<String> seenIps = new LinkedHashSet<>();

        String currentIp = getCurrentServerIp();
        if (currentIp != null && !currentIp.isBlank()) {
            seenIps.add(currentIp);
            result.add(new SavedServerInfo("Current Server", currentIp));
        }

        Minecraft client = Minecraft.getInstance();
        try {
            ServerList list = new ServerList(client);
            list.load();
            for (int i = 0; i < list.size(); i++) {
                ServerData sd = list.get(i);
                if (!sd.ip.isBlank()) {
                    String norm = ClientProfile.normalizeServerIp(sd.ip);
                    if (!norm.isEmpty() && seenIps.add(norm)) {
                        String label = !sd.name.isBlank() ? sd.name.trim() : norm;
                        result.add(new SavedServerInfo(label, norm));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        for (ClientProfile p : profiles) {
            for (String ip : p.getLinkedServers()) {
                String norm = ClientProfile.normalizeServerIp(ip);
                if (!norm.isEmpty() && seenIps.add(norm)) {
                    result.add(new SavedServerInfo(norm, norm));
                }
            }
        }
        return result;
    }

    private static String pickSuggestedIcon(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("pvp") || lower.contains("combat") || lower.contains("duel") || lower.contains("fight")) return "⚔";
        if (lower.contains("surv") || lower.contains("smp") || lower.contains("world") || lower.contains("chill")) return "🌲";
        if (lower.contains("hypixel") || lower.contains("bedwars") || lower.contains("skywars")) return "🏰";
        if (lower.contains("skyblock") || lower.contains("rich") || lower.contains("mine")) return "💎";
        if (lower.contains("speed") || lower.contains("parkour") || lower.contains("move")) return "🚀";
        if (lower.contains("nether") || lower.contains("fire") || lower.contains("uhc")) return "🔥";
        return "⭐";
    }

    private void resetLiveClientToDefaults() {
        StarClient sc = StarClient.getInstance();
        if (sc == null) return;
        if (sc.getModuleManager() != null) {
            for (Module m : sc.getModuleManager().getModules()) {
                m.resetToFullDefault();
            }
        }
        if (sc.getHudLayout() != null) {
            sc.getHudLayout().reset();
        }
        if (sc.getClientSettings() != null) {
            sc.getClientSettings().resetToDefaults();
        }
        if (sc.getCosmeticManager() != null) {
            sc.getCosmeticManager().clearAllAccountCosmetics();
        }
    }

    public JsonArray toJsonArray() {
        syncActiveProfileSnapshot();
        JsonArray arr = new JsonArray();
        for (ClientProfile p : profiles) {
            arr.add(p.toJson());
        }
        return arr;
    }

    public void loadFromJson(JsonObject root, JsonObject fallbackStateJson) {
        profiles.clear();
        if (root.has("profiles") && root.get("profiles").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("profiles")) {
                if (el.isJsonObject()) {
                    ClientProfile p = ClientProfile.fromJson(el.getAsJsonObject());
                    if (getProfileById(p.getId()) == null) {
                        profiles.add(p);
                    }
                }
            }
        }

        if (profiles.isEmpty()) {
            ClientProfile def = new ClientProfile("default", "Default", "⭐");
            def.setData(fallbackStateJson);
            profiles.add(def);
        }

        if (root.has("activeProfileId")) {
            String savedId = root.get("activeProfileId").getAsString();
            if (getProfileById(savedId) != null) {
                this.activeProfileId = savedId;
            } else {
                this.activeProfileId = profiles.getFirst().getId();
            }
        } else {
            this.activeProfileId = profiles.getFirst().getId();
        }
    }
}
