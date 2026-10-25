package net.modernclient.rank;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.modernclient.ModernClient;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RankManager {
    private static final String RANKS_RESOURCE_PATH = "assets/" + ModernClient.MOD_ID + "/ranks.json";

    private final Map<String, Rank> ranksByUuid = new HashMap<>();
    private final Map<String, Rank> ranksByName = new HashMap<>();

    public RankManager() {
        loadRanks();
    }

    public final void loadRanks() {
        ranksByUuid.clear();
        ranksByName.clear();

        try (InputStream stream = openResourceStream(RANKS_RESOURCE_PATH)) {
            if (stream == null) {
                ModernClient.LOGGER.warn("Could not find shipped ranks config at {}", RANKS_RESOURCE_PATH);
                return;
            }
            JsonElement parsed = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return;
            }
            JsonObject root = parsed.getAsJsonObject();

            // Format 1: "ranks": { "OWNER": ["uuidOrName", ...], "ADMIN": [...], ... }
            if (root.has("ranks") && root.get("ranks").isJsonObject()) {
                JsonObject ranksObj = root.getAsJsonObject("ranks");
                for (Map.Entry<String, JsonElement> entry : ranksObj.entrySet()) {
                    Rank rank = Rank.fromString(entry.getKey());
                    if (rank == null || !entry.getValue().isJsonArray()) {
                        continue;
                    }
                    JsonArray array = entry.getValue().getAsJsonArray();
                    for (JsonElement element : array) {
                        registerConfigEntry(element, rank);
                    }
                }
            }

            // Format 2: "players": { "uuidOrName": "OWNER", ... }
            if (root.has("players") && root.get("players").isJsonObject()) {
                JsonObject playersObj = root.getAsJsonObject("players");
                for (Map.Entry<String, JsonElement> entry : playersObj.entrySet()) {
                    if (!entry.getValue().isJsonPrimitive()) {
                        continue;
                    }
                    Rank rank = Rank.fromString(entry.getValue().getAsString());
                    if (rank != null) {
                        registerIdentifier(entry.getKey(), rank);
                    }
                }
            }

            ModernClient.LOGGER.info("Loaded {} UUID rank(s) and {} username rank(s) from {}",
                    ranksByUuid.size(), ranksByName.size(), RANKS_RESOURCE_PATH);
        } catch (Exception e) {
            ModernClient.LOGGER.error("Failed to load shipped ranks config from {}", RANKS_RESOURCE_PATH, e);
        }
    }

    private void registerConfigEntry(JsonElement element, Rank rank) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            registerIdentifier(element.getAsString(), rank);
        } else if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("uuid") && obj.get("uuid").isJsonPrimitive()) {
                registerIdentifier(obj.get("uuid").getAsString(), rank);
            }
            if (obj.has("name") && obj.get("name").isJsonPrimitive()) {
                registerIdentifier(obj.get("name").getAsString(), rank);
            }
        }
    }

    private void registerIdentifier(String rawIdentifier, Rank rank) {
        if (rawIdentifier == null) {
            return;
        }
        String trimmed = rawIdentifier.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (looksLikeUuid(trimmed)) {
            String normalizedUuid = normalizeUuid(trimmed);
            ranksByUuid.merge(normalizedUuid, rank, (oldRank, newRank) ->
                    newRank.ordinal() >= oldRank.ordinal() ? newRank : oldRank);
        } else {
            String normalizedName = trimmed.toLowerCase(Locale.ROOT);
            ranksByName.merge(normalizedName, rank, (oldRank, newRank) ->
                    newRank.ordinal() >= oldRank.ordinal() ? newRank : oldRank);
        }
    }

    /**
     * Returns the explicitly configured rank for the given UUID or username from {@code ranks.json},
     * or {@code null} if the player is not listed.
     */
    public Rank getConfiguredRank(UUID uuid, String username) {
        Rank byUuid = null;
        if (uuid != null) {
            byUuid = ranksByUuid.get(normalizeUuid(uuid.toString()));
        }
        Rank byName = null;
        if (username != null && !username.isBlank()) {
            byName = ranksByName.get(username.trim().toLowerCase(Locale.ROOT));
        }
        if (byUuid != null && byName != null) {
            return byUuid.ordinal() >= byName.ordinal() ? byUuid : byName;
        }
        return byUuid != null ? byUuid : byName;
    }

    /**
     * Resolves the rank of the local client user. Defaults to {@link Rank#USER} if not listed in {@code ranks.json}.
     */
    public Rank getSelfRank() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return Rank.USER;
        }

        Rank best = null;
        User user = client.getUser();
        if (user != null) {
            best = maxRank(best, getConfiguredRank(user.getProfileId(), user.getName()));
        }
        if (client.player != null) {
            String profileName = client.player.getGameProfile() != null ? client.player.getGameProfile().name() : null;
            best = maxRank(best, getConfiguredRank(client.player.getUUID(), profileName));
        }
        return best != null ? best : Rank.USER;
    }

    /**
     * Returns the rank to display in Tab list / Nametag for a player:
     * <ul>
     *   <li>For the local player ({@code isSelf == true}), always returns their rank (at least {@link Rank#USER}).</li>
     *   <li>For other players, returns their configured rank if they are listed in {@code ranks.json}, or {@code null} otherwise.</li>
     * </ul>
     */
    public Rank getDisplayRank(UUID uuid, String username, boolean isSelf) {
        Rank configured = getConfiguredRank(uuid, username);
        if (configured != null) {
            return isSelf ? maxRank(configured, getSelfRank()) : configured;
        }
        return isSelf ? getSelfRank() : null;
    }

    /**
     * Resolves the rank of a specific account by its UUID/username, defaulting to {@link Rank#USER}
     * without falling back to the currently active client user's rank.
     */
    public Rank getAccountRank(UUID uuid, String username) {
        Rank configured = getConfiguredRank(uuid, username);
        return configured != null ? configured : Rank.USER;
    }

    /** Checks whether the local player has sufficient rank to use a feature requiring {@code requiredRank}. */
    public boolean hasAccess(Rank requiredRank) {
        return getSelfRank().hasAtLeast(requiredRank);
    }

    private static Rank maxRank(Rank a, Rank b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    private static boolean looksLikeUuid(String s) {
        String hex = s.replace("-", "");
        if (hex.length() != 32) {
            return false;
        }
        for (int i = 0; i < hex.length(); i++) {
            char c = hex.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!isHex) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeUuid(String uuid) {
        return uuid.replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static InputStream openResourceStream(String resourcePath) throws Exception {
        InputStream stream = ModernClient.class.getResourceAsStream("/" + resourcePath);
        if (stream != null) {
            return stream;
        }
        Optional<Path> modPath = FabricLoader.getInstance()
                .getModContainer(ModernClient.MOD_ID)
                .flatMap(container -> container.findPath(resourcePath));
        if (modPath.isPresent()) {
            return Files.newInputStream(modPath.get());
        }
        return null;
    }
}
