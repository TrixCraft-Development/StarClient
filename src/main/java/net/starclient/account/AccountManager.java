package net.starclient.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;
import net.starclient.StarClient;
import net.starclient.mixin.MinecraftAccessor;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class AccountManager {

    private final File accountsFile;
    private final Gson gson;
    private final List<MinecraftAccount> accounts = new ArrayList<>();
    private final Map<String, ResolvableProfile> skinProfileCache = new ConcurrentHashMap<>();
    private boolean initialCaptured;
    private volatile String statusMessage = "";

    public AccountManager() {
        File dir = new File(Minecraft.getInstance().gameDirectory, StarClient.CONFIG_FOLDER);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.accountsFile = new File(dir, "accounts.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        load();
    }

    public synchronized void ensureInitialAccountCaptured() {
        if (initialCaptured) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        initialCaptured = true;
        User current = client.getUser();
        String name = current.getName();
        UUID uuid = current.getProfileId();
        String token = current.getAccessToken();

        for (MinecraftAccount existing : accounts) {
            if (existing.getUuid().equals(uuid) || existing.getUsername().equalsIgnoreCase(name)) {
                if (!token.isBlank() && !"0".equals(token) && existing.getAccessToken().isBlank()) {
                    existing.setAccessToken(token);
                }
                return;
            }
        }

        boolean hasRealToken = !token.isBlank() && !"0".equals(token) && !"null".equalsIgnoreCase(token);
        MinecraftAccount.AccountType type = hasRealToken
                ? MinecraftAccount.AccountType.MICROSOFT
                : MinecraftAccount.AccountType.OFFLINE;
        accounts.addFirst(new MinecraftAccount(type, name, uuid, hasRealToken ? token : "", "", "", true));
    }

    public synchronized List<MinecraftAccount> getAccounts() {
        ensureInitialAccountCaptured();
        return List.copyOf(accounts);
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public synchronized void addOrUpdateOfflineAccount(String username) {
        String cleanName = username == null ? "" : username.trim();
        if (cleanName.isEmpty()) {
            return;
        }
        if (cleanName.length() > 16) {
            cleanName = cleanName.substring(0, 16);
        }
        UUID offlineUuid = UUIDUtil.createOfflinePlayerUUID(cleanName);

        for (MinecraftAccount existing : accounts) {
            if (!existing.isMicrosoft() && existing.getUsername().equalsIgnoreCase(cleanName)) {
                existing.setUsername(cleanName);
                existing.setUuid(offlineUuid);
                save();
                switchToAccount(existing);
                return;
            }
        }

        MinecraftAccount created = MinecraftAccount.offline(cleanName, offlineUuid);
        accounts.add(created);
        save();
        switchToAccount(created);
    }

    public synchronized void addOrUpdateMicrosoftAccount(MinecraftAccount account) {
        ensureInitialAccountCaptured();
        for (int i = 0; i < accounts.size(); i++) {
            MinecraftAccount existing = accounts.get(i);
            if (existing.getUuid().equals(account.getUuid())
                    || (existing.isMicrosoft() && existing.getUsername().equalsIgnoreCase(account.getUsername()))) {
                accounts.set(i, account);
                save();
                applySessionOnClientThread(account);
                return;
            }
        }
        accounts.add(account);
        save();
        applySessionOnClientThread(account);
    }

    public synchronized void removeAccount(MinecraftAccount account) {
        if (account == null || account.isInitialSession()) {
            return;
        }
        accounts.remove(account);
        save();
    }

    public boolean isActive(MinecraftAccount account) {
        Minecraft client = Minecraft.getInstance();
        if (account == null) {
            return false;
        }
        return account.getUsername().equalsIgnoreCase(client.getUser().getName());
    }

    public void switchToAccount(MinecraftAccount account) {
        if (account == null) {
            return;
        }
        if (account.isMicrosoft() && !account.getRefreshToken().isBlank()) {
            this.statusMessage = "Switching to " + account.getUsername() + "...";
            applySessionOnClientThread(account);
            CompletableFuture.runAsync(() -> {
                try {
                    MinecraftAccount refreshed = MicrosoftAuthService.refreshAccount(account, msg -> this.statusMessage = msg);
                    synchronized (this) {
                        account.setUsername(refreshed.getUsername());
                        account.setUuid(refreshed.getUuid());
                        account.setAccessToken(refreshed.getAccessToken());
                        if (!refreshed.getRefreshToken().isBlank()) {
                            account.setRefreshToken(refreshed.getRefreshToken());
                        }
                        save();
                    }
                    applySessionOnClientThread(account);
                    this.statusMessage = "";
                } catch (Exception e) {
                    StarClient.LOGGER.warn("Token refresh skipped/failed for {}: {}", account.getUsername(), e.getMessage());
                    this.statusMessage = "";
                }
            });
        } else {
            this.statusMessage = "";
            applySessionOnClientThread(account);
        }
    }

    private void applySessionOnClientThread(MinecraftAccount account) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            String token = account.isMicrosoft() && !account.getAccessToken().isBlank()
                    ? account.getAccessToken()
                    : "0";
            User newUser = new User(
                    account.getUsername(),
                    account.getUuid(),
                    token,
                    Optional.empty(),
                    Optional.empty()
            );

            MinecraftAccessor accessor = (MinecraftAccessor) client;
            accessor.starclient$setUser(newUser);

            if (account.isMicrosoft()) {
                CompletableFuture<ProfileResult> profileFuture = CompletableFuture.supplyAsync(
                        () -> client.services().sessionService().fetchProfile(account.getUuid(), true),
                        Util.nonCriticalIoPool()
                );
                accessor.starclient$setProfileFuture(profileFuture);
            } else {
                accessor.starclient$setProfileFuture(CompletableFuture.completedFuture(null));
            }

            UserApiService apiService = UserApiService.OFFLINE;
            accessor.starclient$setUserApiService(apiService);
            accessor.starclient$setUserPropertiesFuture(
                    CompletableFuture.completedFuture(UserApiService.OFFLINE_PROPERTIES)
            );
            accessor.starclient$setProfileKeyPairManager(
                    ProfileKeyPairManager.create(apiService, newUser, client.gameDirectory.toPath())
            );

            StarClient.LOGGER.info("Switched active Minecraft account to '{}' ({})",
                    account.getUsername(), account.getType());

            if (StarClient.getInstance() != null && StarClient.getInstance().getConfigManager() != null) {
                StarClient.getInstance().getConfigManager().saveConfig();
            }
        });
    }

    public Identifier getSkinTexture(MinecraftAccount account) {
        Minecraft client = Minecraft.getInstance();
        if (account == null) {
            return DefaultPlayerSkin.getDefaultTexture();
        }
        PlayerSkinRenderCache cache = client.playerSkinRenderCache();
        if (!account.getUsername().isBlank()) {
            String key = account.getUsername().toLowerCase(Locale.ROOT);
            ResolvableProfile resolvable = skinProfileCache.computeIfAbsent(
                    key,
                    k -> ResolvableProfile.createUnresolved(account.getUsername())
            );
            PlayerSkinRenderCache.RenderInfo info = cache.getOrDefault(resolvable);
            return info.playerSkin().body().texturePath();
        }
        return DefaultPlayerSkin.get(new GameProfile(account.getUuid(), account.getUsername())).body().texturePath();
    }

    public synchronized void load() {
        accounts.clear();
        if (!accountsFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(accountsFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("accounts") || !root.get("accounts").isJsonArray()) {
                return;
            }
            JsonArray arr = root.getAsJsonArray("accounts");
            for (JsonElement elem : arr) {
                if (!elem.isJsonObject()) continue;
                JsonObject obj = elem.getAsJsonObject();
                String typeStr = obj.has("type") ? obj.get("type").getAsString() : "OFFLINE";
                String username = obj.has("username") ? obj.get("username").getAsString() : "";
                String uuidStr = obj.has("uuid") ? obj.get("uuid").getAsString() : "";
                String accessToken = obj.has("accessToken") ? obj.get("accessToken").getAsString() : "";
                String refreshToken = obj.has("refreshToken") ? obj.get("refreshToken").getAsString() : "";
                String clientId = obj.has("clientId") ? obj.get("clientId").getAsString() : "";
                if (username.isBlank()) continue;

                UUID uuid;
                try {
                    uuid = uuidStr.isBlank()
                            ? UUIDUtil.createOfflinePlayerUUID(username)
                            : UUID.fromString(uuidStr);
                } catch (IllegalArgumentException ex) {
                    uuid = UUIDUtil.createOfflinePlayerUUID(username);
                }

                MinecraftAccount.AccountType type = "MICROSOFT".equalsIgnoreCase(typeStr)
                        ? MinecraftAccount.AccountType.MICROSOFT
                        : MinecraftAccount.AccountType.OFFLINE;
                accounts.add(new MinecraftAccount(type, username, uuid, accessToken, refreshToken, clientId, false));
            }
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to load accounts.json", e);
        }
    }

    public synchronized void save() {
        try {
            JsonObject root = new JsonObject();
            JsonArray arr = new JsonArray();
            for (MinecraftAccount acc : accounts) {
                if (acc.isInitialSession()) {
                    continue;
                }
                JsonObject obj = new JsonObject();
                obj.addProperty("type", acc.getType().name());
                obj.addProperty("username", acc.getUsername());
                obj.addProperty("uuid", acc.getUuid().toString());
                obj.addProperty("accessToken", acc.getAccessToken());
                obj.addProperty("refreshToken", acc.getRefreshToken());
                obj.addProperty("clientId", acc.getClientId());
                arr.add(obj);
            }
            root.add("accounts", arr);
            try (FileWriter writer = new FileWriter(accountsFile, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to save accounts.json", e);
        }
    }
}
