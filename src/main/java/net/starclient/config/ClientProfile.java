package net.starclient.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.starclient.StarClient;
import net.starclient.cosmetics.CosmeticItem;
import net.starclient.cosmetics.CosmeticManager;
import net.starclient.module.Module;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClientProfile {
    public static final String[] ICONS = {
            "⭐", "⚔", "🌲", "🏰", "🚀", "💎", "🔥", "🛡", "🎯", "👑"
    };

    private final String id;
    private String name;
    private String icon;
    private int keyBind = GLFW.GLFW_KEY_UNKNOWN;
    private transient boolean keyDownLastTick;
    private final List<String> linkedWorlds = new ArrayList<>();
    private final List<String> linkedServers = new ArrayList<>();
    private JsonObject data = new JsonObject();

    public ClientProfile(String id, String name, String icon) {
        this.id = id;
        this.name = name != null && !name.isBlank() ? name.trim() : "Profile";
        this.icon = icon != null && !icon.isBlank() ? icon : "⭐";
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name.trim();
        }
    }

    public String getIcon() {
        return icon != null && !icon.isEmpty() ? icon : "⭐";
    }

    public void setIcon(String icon) {
        this.icon = icon != null && !icon.isEmpty() ? icon : "⭐";
    }

    public void cycleIcon() {
        int idx = 0;
        for (int i = 0; i < ICONS.length; i++) {
            if (ICONS[i].equals(this.icon)) {
                idx = (i + 1) % ICONS.length;
                break;
            }
        }
        this.icon = ICONS[idx];
    }

    public int getKeyBind() {
        return keyBind;
    }

    public void setKeyBind(int keyBind) {
        this.keyBind = keyBind <= 0 ? GLFW.GLFW_KEY_UNKNOWN : keyBind;
    }

    public boolean isKeyDownLastTick() {
        return keyDownLastTick;
    }

    public void setKeyDownLastTick(boolean keyDownLastTick) {
        this.keyDownLastTick = keyDownLastTick;
    }

    public List<String> getLinkedWorlds() {
        return linkedWorlds;
    }

    public List<String> getLinkedServers() {
        return linkedServers;
    }

    public JsonObject getData() {
        return data;
    }

    public void setData(JsonObject data) {
        this.data = data != null ? data.deepCopy() : new JsonObject();
    }

    public boolean hasWorldLinked(String worldName) {
        if (worldName == null || worldName.isBlank()) return false;
        String needle = worldName.trim().toLowerCase(Locale.ROOT);
        for (String w : linkedWorlds) {
            if (w.trim().toLowerCase(Locale.ROOT).equals(needle)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasServerLinked(String serverIp) {
        if (serverIp == null || serverIp.isBlank()) return false;
        String norm = normalizeServerIp(serverIp);
        for (String s : linkedServers) {
            if (normalizeServerIp(s).equals(norm)) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesServerAddress(String serverIp) {
        if (serverIp == null || serverIp.isBlank()) return false;
        String normJoined = normalizeServerIp(serverIp);
        for (String s : linkedServers) {
            String normLinked = normalizeServerIp(s);
            if (normLinked.isEmpty()) continue;
            if (normJoined.equals(normLinked)
                    || normJoined.endsWith("." + normLinked)
                    || normLinked.endsWith("." + normJoined)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeServerIp(String ip) {
        if (ip == null) return "";
        String s = ip.trim().toLowerCase(Locale.ROOT);
        if (s.endsWith(":25565")) {
            s = s.substring(0, s.length() - 6);
        }
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public int getEnabledModuleCount(boolean isActiveProfile) {
        if (isActiveProfile && StarClient.getInstance() != null && StarClient.getInstance().getModuleManager() != null) {
            return (int) StarClient.getInstance().getModuleManager().getModules().stream()
                    .filter(Module::isEnabled).count();
        }
        if (data != null && data.has("modules") && data.get("modules").isJsonObject()) {
            JsonObject mods = data.getAsJsonObject("modules");
            int count = 0;
            for (String key : mods.keySet()) {
                JsonElement el = mods.get(key);
                if (el.isJsonObject() && el.getAsJsonObject().has("enabled")
                        && el.getAsJsonObject().get("enabled").getAsBoolean()) {
                    count++;
                }
            }
            return count;
        }
        return 0;
    }

    public String getEquippedCapeSummary(boolean isActiveProfile) {
        String capeId = null;
        if (isActiveProfile && StarClient.getInstance() != null && StarClient.getInstance().getCosmeticManager() != null) {
            capeId = StarClient.getInstance().getCosmeticManager().getEquippedCape();
        } else if (data != null) {
            String accKey = CosmeticManager.getActiveAccountKey();
            if (data.has("accountCosmetics") && data.get("accountCosmetics").isJsonObject()) {
                JsonObject accs = data.getAsJsonObject("accountCosmetics");
                if (accs.has(accKey) && accs.get(accKey).isJsonObject()) {
                    JsonObject acc = accs.getAsJsonObject(accKey);
                    if (acc.has("equippedCape")) {
                        capeId = acc.get("equippedCape").getAsString();
                    }
                }
            }
            if ((capeId == null || capeId.isEmpty()) && data.has("equippedCape")) {
                capeId = data.get("equippedCape").getAsString();
            }
        }
        if (capeId == null || capeId.isBlank() || "none".equalsIgnoreCase(capeId)) {
            return "None";
        }
        if (StarClient.getInstance() != null && StarClient.getInstance().getCosmeticManager() != null) {
            CosmeticItem item = StarClient.getInstance().getCosmeticManager().getCosmeticById(capeId);
            if (item != null) {
                return item.displayName();
            }
        }
        return capeId;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("name", name);
        obj.addProperty("icon", getIcon());
        obj.addProperty("keyBind", keyBind);

        JsonArray worldsArr = new JsonArray();
        for (String w : linkedWorlds) {
            if (w != null && !w.isBlank()) worldsArr.add(w.trim());
        }
        obj.add("linkedWorlds", worldsArr);

        JsonArray serversArr = new JsonArray();
        for (String s : linkedServers) {
            if (s != null && !s.isBlank()) serversArr.add(normalizeServerIp(s));
        }
        obj.add("linkedServers", serversArr);

        obj.add("data", data != null ? data.deepCopy() : new JsonObject());
        return obj;
    }

    public static ClientProfile fromJson(JsonObject obj) {
        String id = obj.has("id") ? obj.get("id").getAsString() : "profile_" + System.currentTimeMillis();
        String name = obj.has("name") ? obj.get("name").getAsString() : "Profile";
        String icon = obj.has("icon") ? obj.get("icon").getAsString() : "⭐";
        ClientProfile profile = new ClientProfile(id, name, icon);
        if (obj.has("keyBind")) {
            profile.setKeyBind(obj.get("keyBind").getAsInt());
        }

        if (obj.has("linkedWorlds") && obj.get("linkedWorlds").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("linkedWorlds")) {
                String w = el.getAsString().trim();
                if (!w.isEmpty() && !profile.hasWorldLinked(w)) {
                    profile.linkedWorlds.add(w);
                }
            }
        }

        if (obj.has("linkedServers") && obj.get("linkedServers").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("linkedServers")) {
                String s = normalizeServerIp(el.getAsString());
                if (!s.isEmpty() && !profile.hasServerLinked(s)) {
                    profile.linkedServers.add(s);
                }
            }
        }

        if (obj.has("data") && obj.get("data").isJsonObject()) {
            profile.setData(obj.getAsJsonObject("data"));
        }
        return profile;
    }
}
