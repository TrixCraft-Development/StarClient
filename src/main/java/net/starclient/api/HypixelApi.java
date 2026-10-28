package net.starclient.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.starclient.StarClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Thin async client for the public Hypixel API.
 * <p>
 * Requests are drained one at a time on a virtual thread with a small delay in between so the
 * client never bursts past the API's rate limit. Results are cached per UUID; failed lookups are
 * backed off so a player without Hypixel data is not retried on every frame.
 */
public final class HypixelApi {
    /**
     * The legacy v1 endpoint is tried first because it is the only one that still returns the
     * per-game stat blob (v2 answers with an empty "stats" object). It is also a superset: v1
     * carries "networkExp", so the level-only lookups resolve from it in a single request too.
     * v2 stays as the fallback for when v1 is retired.
     */
    private static final String[] ENDPOINTS = {
            "https://api.hypixel.net/player/stats",
            "https://api.hypixel.net/player",
            "https://api.hypixel.net/v2/player"
    };

    private static final String[] BEDWARS_KEYS = {"bedwars"};
    private static final String[] SKYWARS_KEYS = {"skywars"};
    private static final String[] DUELS_KEYS = {"duels"};

    private static final int[] BEDWARS_FIRST_STARS = {500, 1000, 2000, 3500};
    private static final int BEDWARS_STAR_XP = 5_000;

    private static final long REQUEST_SPACING_MILLIS = 130L;
    private static final long FAILURE_BACKOFF_MILLIS = 60_000L;
    private static final long RATE_LIMIT_BACKOFF_MILLIS = 300_000L;
    private static final int MAX_PENDING = 64;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Map<UUID, Stats> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FAILED_UNTIL = new ConcurrentHashMap<>();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final ConcurrentLinkedQueue<UUID> PENDING = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean DRAINING = new AtomicBoolean(false);
    private static volatile long rateLimitedUntil;

    /**
     * Lookups whose result a caller is actively blocked on. {@link #retainOnly} prunes the cache
     * for players that are no longer nearby, which is right for the level head's continuous polling
     * but destroys a key-triggered lookup that has not been read yet.
     */
    private static final Set<UUID> PINNED = ConcurrentHashMap.newKeySet();

    /** Diagnostics for the on-demand debug output, drained on the client thread. */
    private static final ConcurrentLinkedQueue<String> DEBUG = new ConcurrentLinkedQueue<>();
    private static final int MAX_DEBUG_LINES = 160;

    private static volatile String cacheKey = "";

    private HypixelApi() {
    }

    private static final class RateLimitedException extends IOException {
        private RateLimitedException(String message) {
            super(message);
        }
    }

    /**
     * One game's raw counters. A value of -1 means Hypixel did not report that counter at all,
     * which is common: SkyWars omits "wins" entirely for some accounts, and Duels counts bridge
     * goals separately from kills. Anything absent stays -1 so callers can print "?" rather
     * than a misleading 0.
     */
    public record GameStats(long wins, long losses, long kills, long deaths, long games,
                            long finalKills, long finalDeaths, long bedsBroken, long bedsLost,
                            long goals, long winstreak, long bestWinstreak, long assists) {
        public static final GameStats EMPTY =
                new GameStats(-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1);

        public boolean isEmpty() {
            return wins < 0 && losses < 0 && kills < 0 && deaths < 0 && games < 0
                    && finalKills < 0 && finalDeaths < 0 && bedsBroken < 0 && bedsLost < 0
                    && goals < 0 && winstreak < 0 && bestWinstreak < 0 && assists < 0;
        }

        /** Kills per death, or -1 when kills are unknown. Zero deaths counts as the kill total. */
        public double kd() {
            if (kills < 0) {
                return -1;
            }
            if (deaths <= 0) {
                return kills;
            }
            return (double) kills / deaths;
        }

        /** Win rate over decided games, or -1 when neither side of the record exists. */
        public double winRate() {
            if (wins < 0 && losses < 0) {
                return -1;
            }
            long decided = Math.max(wins, 0) + Math.max(losses, 0);
            return decided <= 0 ? -1 : Math.max(wins, 0) * 100.0 / decided;
        }

        /** Games actually played: the W/L total when known, otherwise the reported game count. */
        public long played() {
            if (wins >= 0 || losses >= 0) {
                return Math.max(wins, 0) + Math.max(losses, 0);
            }
            return games;
        }
    }

    /**
     * Snapshot of one player's Hypixel profile. Levels are kept alongside the raw counters so the
     * level-only nametag module does not have to know how the counters are shaped.
     */
    public record Stats(String name, String rank, double networkLevel, double bedwarsLevel,
                        double skywarsLevel, GameStats bedwars, GameStats skywars, GameStats duels,
                        long fetchedAt) {
        public double level(String mode) {
            return switch (mode) {
                case "Bedwars" -> bedwarsLevel;
                case "Skywars" -> skywarsLevel;
                default -> networkLevel;
            };
        }

        public GameStats game(String mode) {
            return switch (mode) {
                case "Bedwars" -> bedwars;
                case "Skywars" -> skywars;
                case "Duels" -> duels;
                default -> GameStats.EMPTY;
            };
        }

        public boolean isEmpty() {
            return networkLevel < 0 && bedwarsLevel < 0 && skywarsLevel < 0
                    && bedwars.isEmpty() && skywars.isEmpty() && duels.isEmpty();
        }

        public boolean isStale(long ttlMillis) {
            return System.currentTimeMillis() - fetchedAt >= ttlMillis;
        }
    }

    public static boolean isConfigured() {
        return !apiKey().isEmpty();
    }

    /** Cached stats for a player, or null when nothing has been fetched yet. */
    public static Stats getStats(UUID uuid) {
        return uuid == null ? null : CACHE.get(uuid);
    }

    /**
     * True when a lookup for this player cannot run right now, either because an earlier attempt
     * failed or because the whole client is backing off. Lets callers tell the user "try later"
     * instead of silently waiting for a request that will never be sent.
     */
    public static boolean isBlocked(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        return now < rateLimitedUntil || now < FAILED_UNTIL.getOrDefault(uuid, 0L);
    }

    /**
     * Queues a lookup unless the player is already cached, pending or in backoff.
     *
     * @return a short reason when nothing was queued, or null when the caller can expect a result
     */
    public static String request(UUID uuid, long ttlMillis) {
        if (uuid == null) {
            return "no player";
        }
        if (!isConfigured()) {
            return "no API key configured";
        }
        long now = System.currentTimeMillis();
        if (now < rateLimitedUntil) {
            return "client is rate limited, backing off for another "
                    + Math.max(0L, (rateLimitedUntil - now) / 1000L) + "s";
        }
        if (IN_FLIGHT.contains(uuid)) {
            return "a request for this player is already running";
        }
        if (now < FAILED_UNTIL.getOrDefault(uuid, 0L)) {
            return "an earlier lookup failed, backing off for another "
                    + Math.max(0L, (FAILED_UNTIL.get(uuid) - now) / 1000L) + "s";
        }

        Stats cached = CACHE.get(uuid);
        if (cached != null && !cached.isStale(ttlMillis)) {
            return null;
        }

        if (PENDING.size() >= MAX_PENDING) {
            return "lookup queue is full (" + PENDING.size() + "/" + MAX_PENDING + ")";
        }
        if (!IN_FLIGHT.add(uuid)) {
            return "a request for this player is already running";
        }

        debug(uuid, "queued (" + PENDING.size() + "/" + MAX_PENDING + " pending, draining=" + DRAINING.get() + ")");
        PENDING.add(uuid);
        startDrain();
        return null;
    }

    /**
     * Marks a lookup whose result is being waited on, so cache pruning cannot delete it before the
     * caller reads it. Must be paired with {@link #unpin(UUID)}.
     */
    public static void pin(UUID uuid) {
        if (uuid != null) {
            PINNED.add(uuid);
        }
    }

    public static void unpin(UUID uuid) {
        if (uuid != null) {
            PINNED.remove(uuid);
        }
    }

    /** Hands the buffered worker diagnostics to the caller and clears the buffer. */
    public static List<String> drainDebug() {
        if (DEBUG.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(DEBUG);
        DEBUG.clear();
        return lines;
    }

    private static void debug(UUID uuid, String message) {
        if (DEBUG.size() >= MAX_DEBUG_LINES) {
            DEBUG.poll();
        }
        String who = uuid == null ? "-" : shortId(uuid);
        DEBUG.add(who + " " + message);
    }

    private static String shortId(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    /** Drops cached lookups for players that are no longer nearby, keeping any that are still wanted. */
    public static void retainOnly(Collection<UUID> uuids) {
        CACHE.keySet().removeIf(uuid -> !uuids.contains(uuid)
                && !PINNED.contains(uuid)
                && !IN_FLIGHT.contains(uuid)
                && !PENDING.contains(uuid));
    }

    /** Clears everything when the configured API key changes, so results never cross over. */
    public static void invalidateIfKeyChanged() {
        String key = apiKey();
        if (key.equals(cacheKey)) return;
        cacheKey = key;
        clear();
    }

    public static void clear() {
        CACHE.clear();
        FAILED_UNTIL.clear();
        PINNED.clear();
        rateLimitedUntil = 0L;

        UUID queued;
        while ((queued = PENDING.poll()) != null) {
            IN_FLIGHT.remove(queued);
        }
    }

    private static String apiKey() {
        StarClient mod = StarClient.getInstance();
        if (mod == null || mod.getClientSettings() == null) {
            return "";
        }
        return mod.getClientSettings().getHypixelApiKey();
    }

    private static void startDrain() {
        if (!DRAINING.compareAndSet(false, true)) return;
        Thread.ofVirtual().name("HypixelApi-Worker").start(HypixelApi::drain);
    }

    private static void drain() {
        try {
            while (true) {
                UUID uuid = PENDING.poll();
                if (uuid == null) return;

                try {
                    fetch(uuid);
                } finally {
                    IN_FLIGHT.remove(uuid);
                }

                if (PENDING.isEmpty()) return;
                try {
                    Thread.sleep(REQUEST_SPACING_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } finally {
            DRAINING.set(false);
            if (!PENDING.isEmpty()) {
                startDrain();
            }
        }
    }

    private static void fetch(UUID uuid) {
        String key = apiKey();
        if (key.isEmpty()) {
            debug(uuid, "worker: aborted, API key is empty");
            return;
        }

        debug(uuid, "worker: started, API key present (length " + key.length() + ", not shown), "
                + ENDPOINTS.length + " endpoint(s) to try");

        StringBuilder failures = new StringBuilder();
        boolean rateLimited = false;
        for (String endpoint : ENDPOINTS) {
            try {
                debug(uuid, "worker: GET " + shortEndpoint(endpoint));
                JsonObject root = send(endpoint, key, uuid);
                Stats stats = parse(root);

                debug(uuid, "worker:   parsed name=" + quote(stats.name())
                        + " rank=" + quote(stats.rank())
                        + " network=" + num(stats.networkLevel())
                        + " bw=" + game(stats.bedwars())
                        + " sw=" + game(stats.skywars())
                        + " duels=" + game(stats.duels()));

                if (stats.isEmpty()) {
                    record(failures, endpoint + " returned no level data");
                    debug(uuid, "worker:   rejected as empty; " + shape(root));
                    continue;
                }

                CACHE.put(uuid, stats);
                FAILED_UNTIL.remove(uuid);
                debug(uuid, "worker:   cached, size=" + CACHE.size());
                StarClient.LOGGER.info("Hypixel stats for {}: name={} rank={} bw(k/d)={}/{} sw(k/d)={}/{} duels(k/d)={}/{}",
                        uuid, stats.name(), stats.rank(),
                        stats.bedwars().kills(), stats.bedwars().deaths(),
                        stats.skywars().kills(), stats.skywars().deaths(),
                        stats.duels().kills(), stats.duels().deaths());
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                debug(uuid, "worker: interrupted");
                return;
            } catch (RateLimitedException e) {
                rateLimited = true;
                record(failures, endpoint + " -> " + e.getMessage());
                debug(uuid, "worker:   " + e.getMessage());
            } catch (Exception e) {
                record(failures, endpoint + " -> " + e.getMessage());
                debug(uuid, "worker:   failed: " + e.getMessage());
            }
        }

        if (rateLimited) {
            rateLimitedUntil = System.currentTimeMillis() + RATE_LIMIT_BACKOFF_MILLIS;
            StarClient.LOGGER.warn("Hypixel API rate limited, pausing lookups for {} seconds", RATE_LIMIT_BACKOFF_MILLIS / 1000);
        }
        FAILED_UNTIL.put(uuid, rateLimited ? rateLimitedUntil : System.currentTimeMillis() + FAILURE_BACKOFF_MILLIS);
        debug(uuid, "worker: all endpoints failed -> " + failures);
        StarClient.LOGGER.warn("Hypixel API lookup failed for {} ({})", uuid, failures);
    }

    private static String shortEndpoint(String endpoint) {
        return endpoint.substring(endpoint.indexOf("://") + 3);
    }

    private static String quote(String value) {
        return value == null || value.isEmpty() ? "<none>" : value;
    }

    private static String num(double value) {
        return value < 0 ? "?" : String.valueOf(Math.round(value * 10.0) / 10.0);
    }

    private static String game(GameStats stats) {
        return stats.isEmpty() ? "<none>" : stats.kills() + "/" + stats.deaths();
    }

    /** Which container the parser used and which game keys it actually saw. */
    private static String shape(JsonObject root) {
        JsonObject player = root.has("player") && root.get("player").isJsonObject()
                ? root.getAsJsonObject("player")
                : root;
        JsonObject stats = findStats(player);
        if (stats == null) {
            return "no game stats object found; player keys=" + player.keySet();
        }
        return "stats object keys=" + stats.keySet();
    }

    private static void record(StringBuilder failures, String failure) {
        if (!failures.isEmpty()) {
            failures.append("; ");
        }
        failures.append(failure);
    }

    private static JsonObject send(String endpoint, String key, UUID uuid) throws IOException, InterruptedException {
        String url = endpoint
                + "?uuid=" + uuid
                + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("API-Key", key)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 429) {
            throw new RateLimitedException("HTTP 429 rate limited");
        }
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " " + excerpt(response.body()));
        }

        JsonObject root;
        try {
            root = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (Exception e) {
            throw new IOException("unparseable body " + excerpt(response.body()));
        }
        if (!root.has("success") || !root.get("success").getAsBoolean()) {
            String cause = cause(root);
            throw new IOException("success=false" + (cause.isEmpty() ? "" : ": " + cause));
        }

        return root;
    }

    /** Hypixel's own error text, e.g. "Invalid API key" or "could not find player by UUID". */
    private static String cause(JsonObject root) {
        JsonElement value = root.get("cause");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return "";
        }
        return value.getAsString().trim();
    }

    /** First {@code max} characters of a response body, flattened onto one line. */
    private static String excerpt(String body) {
        if (body == null) {
            return "<empty body>";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() <= 160 ? flat : flat.substring(0, 160) + "...";
    }

    private static Stats parse(JsonObject root) {
        JsonObject player = root.has("player") && root.get("player").isJsonObject()
                ? root.getAsJsonObject("player")
                : root;
        JsonObject stats = findStats(player);

        return new Stats(
                readString(player, "displayname", "playername"),
                readString(player, "newPackageRank", "packageRank"),
                readNetworkLevel(player),
                stats == null ? -1 : readBedwarsLevel(stats),
                stats == null ? -1 : readSkywarsLevel(stats),
                readGame(stats, BEDWARS_KEYS, HypixelApi::bedwars),
                readGame(stats, SKYWARS_KEYS, HypixelApi::skywars),
                readGame(stats, DUELS_KEYS, HypixelApi::duels),
                System.currentTimeMillis());
    }

    /**
     * Hypixel keys Bedwars counters with a "_bedwars" suffix but leaves SkyWars and Duels
     * unsuffixed, and every game adds a further per-mode layer ("kills_solo", "eight_two_kills_bedwars").
     * Only the aggregate counters are of interest here, so each game gets its own candidate list
     * tried in order and nothing is matched by substring.
     */
    private static GameStats readGame(JsonObject stats, String[] games, Function<JsonObject, GameStats> reader) {
        if (stats == null) {
            return GameStats.EMPTY;
        }
        JsonObject game = gameStats(stats, games);
        return game == null ? GameStats.EMPTY : reader.apply(game);
    }

    private static GameStats bedwars(JsonObject bw) {
        return new GameStats(
                readLong(bw, "wins_bedwars"),
                readLong(bw, "losses_bedwars"),
                readLong(bw, "kills_bedwars"),
                readLong(bw, "deaths_bedwars"),
                readLong(bw, "games_played_bedwars"),
                readLong(bw, "final_kills_bedwars"),
                readLong(bw, "final_deaths_bedwars"),
                readLong(bw, "beds_broken_bedwars"),
                readLong(bw, "beds_lost_bedwars"),
                -1,
                readLong(bw, "winstreak"),
                -1,
                -1);
    }

    private static GameStats skywars(JsonObject sw) {
        return new GameStats(
                readLong(sw, "wins"),
                readLong(sw, "losses"),
                readLong(sw, "kills"),
                readLong(sw, "deaths"),
                readLong(sw, "games_played_skywars", "games"),
                -1,
                -1,
                -1,
                -1,
                -1,
                readLong(sw, "win_streak", "winstreak"),
                -1,
                readLong(sw, "assists"));
    }

    private static GameStats duels(JsonObject d) {
        // Duels reports bridge goals next to (not instead of) kills, so both are surfaced.
        return new GameStats(
                readLong(d, "wins"),
                readLong(d, "losses"),
                readLong(d, "kills"),
                readLong(d, "deaths"),
                readLong(d, "games_played_duels", "rounds_played"),
                -1,
                -1,
                -1,
                -1,
                readLong(d, "goals", "bridge_duel_goals"),
                readLong(d, "current_winstreak", "current_bridge_winstreak"),
                readLong(d, "best_overall_winstreak", "best_bridge_winstreak"),
                -1);
    }

    /**
     * Walks the response for the object that actually holds the per-game stats, wherever the API
     * decided to nest it this time. v1 and v2 differ, and Hypixel has moved the block before.
     */
    private static JsonObject findStats(JsonObject node) {
        if (containsAny(node, BEDWARS_KEYS) || containsAny(node, SKYWARS_KEYS) || containsAny(node, DUELS_KEYS)) {
            return node;
        }
        for (Map.Entry<String, JsonElement> entry : node.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject found = findStats(entry.getValue().getAsJsonObject());
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean containsAny(JsonObject object, String[] names) {
        for (String name : names) {
            if (findObject(object, name) != null) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject findObject(JsonObject object, String key) {
        String wanted = normalize(key);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue().isJsonObject() && normalize(entry.getKey()).equals(wanted)) {
                return entry.getValue().getAsJsonObject();
            }
        }
        return null;
    }

    /**
     * Hypixel keys stats by database name, and the same game shows up as "Bedwars", "Bed Wars" and
     * "BEDWARS" across revisions. Dropping every non-letter and digit makes all of those match.
     */
    private static String normalize(String key) {
        StringBuilder result = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                result.append(c);
            } else if (c >= 'A' && c <= 'Z') {
                result.append((char) (c + ('a' - 'A')));
            }
        }
        return result.toString();
    }

    private static JsonObject gameStats(JsonObject stats, String[] games) {
        for (String game : games) {
            JsonObject found = findObject(stats, game);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Double readNumber(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double number = value.getAsDouble();
        return number < 0 ? null : number;
    }

    /**
     * First of {@code keys} that holds a non-negative number, as a long, or -1 when the response
     * carries none of them. Lookups are exact-name, so "games_played_bedwars" never picks up the
     * neighbouring "games_played_bedwars_1" or the per-mode "eight_two_kills_bedwars".
     */
    private static long readLong(JsonObject object, String... keys) {
        for (String key : keys) {
            Double value = readNumber(object.get(key));
            if (value != null) {
                return (long) (double) value;
            }
        }
        return -1;
    }

    private static String readString(JsonObject object, String... keys) {
        for (String key : keys) {
            JsonElement value = object.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                continue;
            }
            String text = value.getAsString().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "";
    }

    private static double readNetworkLevel(JsonObject player) {
        Double networkExp = readNumber(player.get("networkExp"));
        if (networkExp == null) {
            return -1;
        }
        return Math.floor(Math.sqrt(networkExp / 1250.0 + 12.25) - 3.5) + 1.0;
    }

    private static double readBedwarsLevel(JsonObject stats) {
        JsonObject bedwars = gameStats(stats, BEDWARS_KEYS);
        if (bedwars == null) {
            return -1;
        }

        Double experience = readNumber(bedwars.get("Experience"));
        if (experience == null) {
            return -1;
        }

        double remaining = experience;
        int level = 0;
        while (true) {
            int starsIntoPrestige = level % 100;
            double cost = starsIntoPrestige < BEDWARS_FIRST_STARS.length
                    ? BEDWARS_FIRST_STARS[starsIntoPrestige]
                    : BEDWARS_STAR_XP;
            if (remaining < cost) {
                return level;
            }
            remaining -= cost;
            level++;
        }
    }

    private static double readSkywarsLevel(JsonObject stats) {
        JsonObject skywars = gameStats(stats, SKYWARS_KEYS);
        if (skywars == null) {
            return -1;
        }

        for (String key : new String[]{"levelFormatted", "levelFormattedWithBrackets"}) {
            JsonElement value = skywars.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                continue;
            }
            Double level = leadingNumber(stripFormattingCodes(value.getAsString()));
            if (level != null) {
                return level;
            }
        }

        Double fallback = readNumber(skywars.get("lowest_level_rewards_received"));
        return fallback == null ? -1 : fallback;
    }

    private static String stripFormattingCodes(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '§') {
                i++;
            } else {
                result.append(text.charAt(i));
            }
        }
        return result.toString();
    }

    private static Double leadingNumber(String text) {
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        return digits.isEmpty() ? null : Double.parseDouble(digits.toString());
    }
}
