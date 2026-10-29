package net.starclient.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.starclient.StarClient;
import net.starclient.module.CombatStatsTracker;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

public class ClientStatsManager {
    private static final ClientStatsManager INSTANCE = new ClientStatsManager();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private File statsFile;

    // Client Lifecycle Metadata
    private String firstLaunchDate;
    private String lastLoginDate;
    private long timesStartedClient;
    private int loginStreak;
    private int longestLoginStreak;
    private long menuPlaytimeSeconds;
    private final Set<String> uniqueDays = new HashSet<>();
    private long lastPlaytimeUpdateTimestamp = System.currentTimeMillis();

    // Gameplay Stats
    private final GameplayStats singleplayer = new GameplayStats();
    private final GameplayStats multiplayer = new GameplayStats();

    // Transient tick tracking
    private int tickCounter;
    private int saveCounter;
    private boolean hasLastPos;
    private double lastPosX;
    private double lastPosY;
    private double lastPosZ;
    private float lastRecordedHealth = -1.0F;
    private boolean justRespawned;
    private boolean wasDead;
    private long lastDeathTimestamp;

    private ClientStatsManager() {
    }

    public static ClientStatsManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        File dir = new File(Minecraft.getInstance().gameDirectory, StarClient.CONFIG_FOLDER);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.statsFile = new File(dir, "stats.json");
        loadStats();

        LocalDate today = LocalDate.now();
        String todayStr = today.toString();

        if (this.firstLaunchDate == null || this.firstLaunchDate.isEmpty()) {
            this.firstLaunchDate = todayStr;
        }

        this.timesStartedClient++;
        this.uniqueDays.add(todayStr);

        if (this.lastLoginDate != null && !this.lastLoginDate.isEmpty()) {
            try {
                LocalDate last = LocalDate.parse(this.lastLoginDate);
                long diff = ChronoUnit.DAYS.between(last, today);
                if (diff == 1) {
                    this.loginStreak++;
                    if (this.loginStreak > this.longestLoginStreak) {
                        this.longestLoginStreak = this.loginStreak;
                    }
                } else if (diff > 1) {
                    this.loginStreak = 1;
                }
            } catch (Exception ignored) {
                this.loginStreak = 1;
            }
        } else {
            this.loginStreak = 1;
            if (this.longestLoginStreak < 1) {
                this.longestLoginStreak = 1;
            }
        }

        this.lastLoginDate = todayStr;
        saveStats();

        Runtime.getRuntime().addShutdownHook(new Thread(this::saveStats));
    }

    public synchronized void loadStats() {
        if (this.statsFile == null || !this.statsFile.exists()) return;
        try (FileReader reader = new FileReader(this.statsFile)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            if (root.has("firstLaunchDate")) this.firstLaunchDate = root.get("firstLaunchDate").getAsString();
            if (root.has("lastLoginDate")) this.lastLoginDate = root.get("lastLoginDate").getAsString();
            if (root.has("timesStartedClient")) this.timesStartedClient = root.get("timesStartedClient").getAsLong();
            if (root.has("loginStreak")) this.loginStreak = root.get("loginStreak").getAsInt();
            if (root.has("longestLoginStreak")) this.longestLoginStreak = root.get("longestLoginStreak").getAsInt();
            if (root.has("uniqueDays") && root.get("uniqueDays").isJsonArray()) {
                this.uniqueDays.clear();
                root.getAsJsonArray("uniqueDays").forEach(el -> this.uniqueDays.add(el.getAsString()));
            }

            if (root.has("singleplayer")) {
                loadGameplayStats(root.getAsJsonObject("singleplayer"), this.singleplayer);
            }
            if (root.has("multiplayer")) {
                loadGameplayStats(root.getAsJsonObject("multiplayer"), this.multiplayer);
            }

            if (root.has("menuPlaytimeSeconds")) {
                this.menuPlaytimeSeconds = root.get("menuPlaytimeSeconds").getAsLong();
            } else if (root.has("totalClientPlaytimeSeconds")) {
                long total = root.get("totalClientPlaytimeSeconds").getAsLong();
                long inGame = this.singleplayer.playtimeSeconds + this.multiplayer.playtimeSeconds;
                if (total > inGame) {
                    this.menuPlaytimeSeconds = total - inGame;
                }
            }
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to load client stats from {}", this.statsFile, e);
        }
    }

    private void loadGameplayStats(JsonObject obj, GameplayStats stats) {
        if (obj == null || stats == null) return;
        if (obj.has("playtimeSeconds")) stats.playtimeSeconds = obj.get("playtimeSeconds").getAsLong();
        if (obj.has("blocksPlaced")) stats.blocksPlaced = obj.get("blocksPlaced").getAsLong();
        if (obj.has("blocksMined")) stats.blocksMined = obj.get("blocksMined").getAsLong();
        if (obj.has("playerKills")) stats.playerKills = obj.get("playerKills").getAsLong();
        if (obj.has("mobKills")) stats.mobKills = obj.get("mobKills").getAsLong();
        if (obj.has("deaths")) stats.deaths = obj.get("deaths").getAsLong();
        if (obj.has("heartsLost")) stats.heartsLost = obj.get("heartsLost").getAsDouble();
        if (obj.has("heartsRegenerated")) stats.heartsRegenerated = obj.get("heartsRegenerated").getAsDouble();
        if (obj.has("timesEaten")) stats.timesEaten = obj.get("timesEaten").getAsLong();
        if (obj.has("worldsJoined")) stats.worldsJoined = obj.get("worldsJoined").getAsLong();
        if (obj.has("serversJoined")) stats.serversJoined = obj.get("serversJoined").getAsLong();
        if (obj.has("chatMessagesSent")) stats.chatMessagesSent = obj.get("chatMessagesSent").getAsLong();
        if (obj.has("itemsCrafted")) stats.itemsCrafted = obj.get("itemsCrafted").getAsLong();
        if (obj.has("itemsBroken")) stats.itemsBroken = obj.get("itemsBroken").getAsLong();
        if (obj.has("blocksTraveled")) stats.blocksTraveled = obj.get("blocksTraveled").getAsDouble();
        if (obj.has("worldsCreated")) stats.worldsCreated = obj.get("worldsCreated").getAsLong();
    }

    public synchronized void updatePlaytime(Minecraft mc) {
        long now = System.currentTimeMillis();
        long elapsed = now - this.lastPlaytimeUpdateTimestamp;
        if (elapsed < 0) {
            this.lastPlaytimeUpdateTimestamp = now;
            return;
        }

        // Avoid adding huge jumps if computer was in sleep/hibernation
        if (elapsed > 300_000L) {
            this.lastPlaytimeUpdateTimestamp = now;
            return;
        }

        if (elapsed >= 1000L) {
            long seconds = elapsed / 1000L;
            this.lastPlaytimeUpdateTimestamp += seconds * 1000L;

            boolean inWorld = mc != null && mc.player != null && mc.level != null;
            boolean paused = inWorld && mc.isLocalServer() && mc.isPaused();

            if (inWorld && !paused) {
                if (mc.isLocalServer()) {
                    this.singleplayer.playtimeSeconds += seconds;
                } else {
                    this.multiplayer.playtimeSeconds += seconds;
                }
            } else {
                // In menu, pause screen, ClickGUI, or world selection
                this.menuPlaytimeSeconds += seconds;
            }
        }
    }

    public synchronized void saveStats() {
        if (this.statsFile == null) return;
        updatePlaytime(Minecraft.getInstance());
        try (FileWriter writer = new FileWriter(this.statsFile)) {
            JsonObject root = new JsonObject();
            root.addProperty("firstLaunchDate", this.firstLaunchDate != null ? this.firstLaunchDate : "");
            root.addProperty("lastLoginDate", this.lastLoginDate != null ? this.lastLoginDate : "");
            root.addProperty("timesStartedClient", this.timesStartedClient);
            root.addProperty("loginStreak", this.loginStreak);
            root.addProperty("longestLoginStreak", this.longestLoginStreak);
            root.addProperty("menuPlaytimeSeconds", this.menuPlaytimeSeconds);
            root.addProperty("totalClientPlaytimeSeconds", this.singleplayer.playtimeSeconds + this.multiplayer.playtimeSeconds + this.menuPlaytimeSeconds);
            root.add("uniqueDays", this.gson.toJsonTree(this.uniqueDays));

            root.add("singleplayer", this.gson.toJsonTree(this.singleplayer));
            root.add("multiplayer", this.gson.toJsonTree(this.multiplayer));

            this.gson.toJson(root, writer);
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to save client stats to {}", this.statsFile, e);
        }
    }

    public GameplayStats getActiveStats() {
        Minecraft mc = Minecraft.getInstance();
        return (mc != null && mc.isLocalServer()) ? this.singleplayer : this.multiplayer;
    }

    public GameplayStats getSingleplayerStats() {
        return this.singleplayer;
    }

    public GameplayStats getMultiplayerStats() {
        return this.multiplayer;
    }

    public GameplayStats getGlobalStats() {
        GameplayStats global = new GameplayStats();
        global.add(this.singleplayer);
        global.add(this.multiplayer);
        global.playtimeSeconds = this.singleplayer.playtimeSeconds + this.multiplayer.playtimeSeconds + this.menuPlaytimeSeconds;
        return global;
    }

    public long getMenuPlaytimeSeconds() {
        return this.menuPlaytimeSeconds;
    }

    public String getFirstLaunchDate() {
        return this.firstLaunchDate != null ? this.firstLaunchDate : LocalDate.now().toString();
    }

    public long getTimesStartedClient() {
        return this.timesStartedClient;
    }

    public int getLoginStreak() {
        return this.loginStreak;
    }

    public int getLongestLoginStreak() {
        return this.longestLoginStreak;
    }

    public int getUniqueDaysJoined() {
        return Math.max(1, this.uniqueDays.size());
    }

    // --- Tick Handler ---
    public void onTick(Minecraft client) {
        if (client == null) return;

        // 1. Update playtime
        updatePlaytime(client);

        // 2. Periodic auto-save every 60 seconds (1200 ticks) - runs in menus & in-game
        this.saveCounter++;
        if (this.saveCounter >= 1200) {
            this.saveCounter = 0;
            saveStats();
        }

        // In-world only tracking (movement, health diffs, combat)
        if (client.player == null || client.level == null) {
            this.hasLastPos = false;
            this.lastRecordedHealth = -1.0F;
            return;
        }

        GameplayStats current = getActiveStats();

        // 3. Blocks traveled
        if (!client.player.isDeadOrDying()) {
            double px = client.player.getX();
            double py = client.player.getY();
            double pz = client.player.getZ();

            if (this.hasLastPos) {
                double dx = px - this.lastPosX;
                double dy = py - this.lastPosY;
                double dz = pz - this.lastPosZ;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                // Filter out small jitter and teleport/dimension jumps (> 80 blocks/tick)
                if (dist > 0.02 && dist < 80.0) {
                    current.blocksTraveled += dist;
                }
            }
            this.lastPosX = px;
            this.lastPosY = py;
            this.lastPosZ = pz;
            this.hasLastPos = true;
        } else {
            this.hasLastPos = false;
        }

        // 4. Hearts lost & regenerated
        float health = client.player.getHealth();
        if (client.player.isDeadOrDying()) {
            if (!this.wasDead) {
                onPlayerDeath();
                this.wasDead = true;
            }
            this.justRespawned = true;
        } else {
            if (this.wasDead) {
                this.wasDead = false;
                this.justRespawned = true;
            }
            if (this.lastRecordedHealth >= 0.0F) {
                float diff = health - this.lastRecordedHealth;
                if (diff < -0.01F) {
                    // Lost hearts
                    current.heartsLost += (-diff) / 2.0;
                } else if (diff > 0.01F && !this.justRespawned) {
                    // Regenerated hearts
                    current.heartsRegenerated += diff / 2.0;
                }
            }
            this.justRespawned = false;
            this.lastRecordedHealth = health;
        }
    }

    // --- Gameplay Events ---
    public void onBlockPlaced() {
        getActiveStats().blocksPlaced++;
    }

    public void onBlockMined() {
        getActiveStats().blocksMined++;
    }

    public void onPlayerKill() {
        getActiveStats().playerKills++;
        saveStats();
    }

    public void onMobKill() {
        getActiveStats().mobKills++;
    }

    public void onPlayerDeath() {
        long now = System.currentTimeMillis();
        if (now - this.lastDeathTimestamp < 2000L) return;
        this.lastDeathTimestamp = now;
        getActiveStats().deaths++;
        saveStats();
    }

    public void onEntityDied(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (entity == mc.player) {
            onPlayerDeath();
            return;
        }

        LivingEntity lastTarget = CombatStatsTracker.getLastTarget();
        if (entity == lastTarget) {
            long elapsed = System.currentTimeMillis() - CombatStatsTracker.getLastAttackTime();
            if (elapsed <= 5000L) {
                if (entity instanceof Player) {
                    onPlayerKill();
                } else {
                    onMobKill();
                }
            }
        }
    }

    public void onEat() {
        getActiveStats().timesEaten++;
    }

    public void onWorldJoined() {
        this.singleplayer.worldsJoined++;
        saveStats();
    }

    public void onServerJoined() {
        this.multiplayer.serversJoined++;
        saveStats();
    }

    public void onChatSent() {
        getActiveStats().chatMessagesSent++;
    }

    public void onItemCrafted(int count) {
        if (count <= 0) count = 1;
        getActiveStats().itemsCrafted += count;
    }

    public void onItemBroken() {
        getActiveStats().itemsBroken++;
        saveStats();
    }

    public void onWorldCreated() {
        this.singleplayer.worldsCreated++;
        saveStats();
    }
}
