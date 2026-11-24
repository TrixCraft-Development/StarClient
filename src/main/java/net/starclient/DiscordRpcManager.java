package net.starclient;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.Level;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pure-Java native Windows Discord IPC (Rich Presence) client.
 * Connects to {@code \\.\pipe\discord-ipc-0..9} on a daemon background thread
 * with zero external native libraries, automatic reconnection, and instant
 * state updates for Main Menu, Singleplayer, and Multiplayer.
 */
public final class DiscordRpcManager {
    public static final String DEFAULT_CLIENT_ID = "450485984333660181";
    private static final String STAR_LOGO_URL =
            "https://raw.githubusercontent.com/googlefonts/noto-emoji/main/png/128/emoji_u2b50.png";

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;

    private static final Gson GSON = new Gson();
    private static final long LAUNCH_EPOCH_SEC = System.currentTimeMillis() / 1000L;

    private static final AtomicBoolean workerStarted = new AtomicBoolean(false);
    private static volatile boolean shutdownRequested = false;

    // Live snapshot captured safely on the Minecraft client thread
    private static volatile boolean desiredEnabled = false;
    private static volatile String desiredClientId = DEFAULT_CLIENT_ID;
    private static volatile String desiredDetails = "In Main Menu";
    private static volatile String desiredState = "Star Client";
    private static volatile boolean desiredShowPlaytime = true;

    // Connection telemetry (for Settings UI badge)
    private static volatile boolean connected = false;
    private static volatile String connectedUser = "";
    private static volatile String lastError = "";

    private static int tickCounter = 0;

    private DiscordRpcManager() {
    }

    public static boolean isConnected() {
        return connected;
    }

    public static String getStatusText() {
        if (!desiredEnabled) {
            return "Disabled";
        }
        if (connected) {
            return (connectedUser != null && !connectedUser.isEmpty())
                    ? "Connected (" + connectedUser + ")"
                    : "Connected";
        }
        if (lastError != null && !lastError.isEmpty()) {
            return lastError;
        }
        return "Waiting for Discord...";
    }

    /**
     * Called once per client tick to snapshot game & settings state without blocking the main thread.
     */
    public static void onClientTick(Minecraft client) {
        if (shutdownRequested) return;
        if (++tickCounter < 20) return; // Snapshot once per second
        tickCounter = 0;

        StarClient sc = StarClient.getInstance();
        if (sc == null || sc.getClientSettings() == null) return;
        ClientSettings cs = sc.getClientSettings();

        boolean enabled = cs.isDiscordRpcEnabled();
        desiredEnabled = enabled;

        String customId = cs.getDiscordAppId();
        desiredClientId = isValidSnowflake(customId) ? customId : DEFAULT_CLIENT_ID;
        desiredShowPlaytime = cs.isDiscordShowPlaytime();

        String details;
        String state;
        String versionTag = "Star Client v" + StarClient.MOD_VERSION;

        if (client == null || client.level == null || client.player == null) {
            details = "In Main Menu";
            state = versionTag + " • MC " + StarClient.MC_VERSION;
        } else if (client.isLocalServer()) {
            IntegratedServer sp = client.getSingleplayerServer();
            String worldName = (sp != null && sp.getWorldData() != null)
                    ? sp.getWorldData().getLevelName()
                    : "";
            if (cs.isDiscordShowWorldName() && worldName != null && !worldName.isBlank()) {
                details = "Singleplayer • " + truncate(worldName.trim(), 96);
            } else {
                details = "Playing Singleplayer";
            }
            state = buildInGameState(client, cs.isDiscordShowDimension(), versionTag);
        } else {
            ServerData server = client.getCurrentServer();
            String ip = (server != null && server.ip != null) ? server.ip.trim() : "";
            if (cs.isDiscordShowServerIp() && !ip.isEmpty()) {
                details = "Multiplayer • " + truncate(ip, 96);
            } else {
                details = "Playing Multiplayer";
            }
            state = buildInGameState(client, cs.isDiscordShowDimension(), versionTag);
        }

        desiredDetails = details;
        desiredState = state;

        if (enabled && isWindows() && workerStarted.compareAndSet(false, true)) {
            Thread t = new Thread(DiscordRpcManager::workerLoop, "StarClient-DiscordRPC");
            t.setDaemon(true);
            t.start();
        }
    }

    public static void shutdown() {
        shutdownRequested = true;
        desiredEnabled = false;
    }

    private static String buildInGameState(Minecraft client, boolean showDimension, String versionTag) {
        if (showDimension && client.level != null) {
            String dim = formatDimension(client.level);
            return dim + " • " + versionTag;
        }
        return versionTag + " • MC " + StarClient.MC_VERSION;
    }

    private static String formatDimension(Level level) {
        var key = level.dimension();
        if (key == Level.OVERWORLD) return "Overworld";
        if (key == Level.NETHER) return "The Nether";
        if (key == Level.END) return "The End";
        String path = key.identifier().getPath();
        if (path == null || path.isEmpty()) return "Overworld";
        return Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
    }

    private static void workerLoop() {
        RandomAccessFile pipe = null;
        String activeClientId = null;
        String lastPayloadKey = null;
        long lastConnectAttemptMs = 0L;
        long lastSendMs = 0L;

        try {
            while (!shutdownRequested) {
                boolean enabled = desiredEnabled;
                String targetClientId = desiredClientId;

                // Disconnect cleanly if disabled or Client ID changed
                if (!enabled || (pipe != null && !Objects.equals(activeClientId, targetClientId))) {
                    if (pipe != null) {
                        clearActivityQuietly(pipe);
                        closePipeQuietly(pipe);
                        pipe = null;
                    }
                    connected = false;
                    connectedUser = "";
                    lastError = "";
                    activeClientId = null;
                    lastPayloadKey = null;

                    if (!enabled) {
                        sleepQuietly(1000L);
                        continue;
                    }
                }

                // Connect if not connected
                if (pipe == null) {
                    long now = System.currentTimeMillis();
                    if (now - lastConnectAttemptMs < 6000L) {
                        sleepQuietly(1000L);
                        continue;
                    }
                    lastConnectAttemptMs = now;

                    pipe = tryOpenDiscordPipe();
                    if (pipe == null) {
                        connected = false;
                        connectedUser = "";
                        lastError = "";
                        sleepQuietly(1000L);
                        continue;
                    }

                    if (!performHandshake(pipe, targetClientId)) {
                        closePipeQuietly(pipe);
                        pipe = null;
                        connected = false;
                        connectedUser = "";
                        sleepQuietly(2000L);
                        continue;
                    }

                    activeClientId = targetClientId;
                    connected = true;
                    lastError = "";
                    lastPayloadKey = null;
                    lastSendMs = 0L;
                }

                // Push updated activity when state changes (throttled to >= 2s per Discord guidelines)
                String details = desiredDetails;
                String state = desiredState;
                boolean showTime = desiredShowPlaytime;
                String payloadKey = details + "|" + state + "|" + showTime + "|" + activeClientId;
                long now = System.currentTimeMillis();

                if (!payloadKey.equals(lastPayloadKey) && (now - lastSendMs >= 2000L)) {
                    boolean ok = sendActivity(pipe, details, state, showTime, activeClientId);
                    if (!ok) {
                        closePipeQuietly(pipe);
                        pipe = null;
                        connected = false;
                        connectedUser = "";
                        activeClientId = null;
                        lastPayloadKey = null;
                    } else {
                        lastPayloadKey = payloadKey;
                        lastSendMs = now;
                    }
                }

                sleepQuietly(1000L);
            }
        } finally {
            if (pipe != null) {
                clearActivityQuietly(pipe);
                closePipeQuietly(pipe);
            }
            connected = false;
            connectedUser = "";
        }
    }

    private static RandomAccessFile tryOpenDiscordPipe() {
        for (int i = 0; i < 10; i++) {
            String path = "\\\\.\\pipe\\discord-ipc-" + i;
            try {
                return new RandomAccessFile(path, "rw");
            } catch (IOException ignored) {
                // Pipe slot not open; try next
            }
        }
        return null;
    }

    private static boolean performHandshake(RandomAccessFile pipe, String clientId) {
        try {
            JsonObject handshake = new JsonObject();
            handshake.addProperty("v", 1);
            handshake.addProperty("client_id", clientId);
            writeFrame(pipe, OP_HANDSHAKE, GSON.toJson(handshake));

            Frame reply = readFrame(pipe);
            if (reply == null || reply.opcode == OP_CLOSE) {
                lastError = "Discord rejected App ID";
                return false;
            }

            JsonObject obj = JsonParser.parseString(reply.json).getAsJsonObject();
            String evt = obj.has("evt") && !obj.get("evt").isJsonNull() ? obj.get("evt").getAsString() : "";
            if ("READY".equalsIgnoreCase(evt)) {
                if (obj.has("data") && obj.get("data").isJsonObject()) {
                    JsonObject data = obj.getAsJsonObject("data");
                    if (data.has("user") && data.get("user").isJsonObject()) {
                        JsonObject user = data.getAsJsonObject("user");
                        String name = user.has("global_name") && !user.get("global_name").isJsonNull()
                                ? user.get("global_name").getAsString()
                                : (user.has("username") ? user.get("username").getAsString() : "");
                        connectedUser = name != null ? name : "";
                    }
                }
                return true;
            }
            lastError = "Handshake failed";
            return false;
        } catch (Throwable t) {
            StarClient.LOGGER.debug("Discord IPC handshake failed", t);
            return false;
        }
    }

    private static boolean sendActivity(RandomAccessFile pipe, String details, String state,
                                        boolean showPlaytime, String activeClientId) {
        try {
            JsonObject activity = new JsonObject();
            activity.addProperty("details", details);
            activity.addProperty("state", state);

            if (showPlaytime) {
                JsonObject timestamps = new JsonObject();
                timestamps.addProperty("start", LAUNCH_EPOCH_SEC);
                activity.add("timestamps", timestamps);
            }

            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", STAR_LOGO_URL);
            assets.addProperty("large_text", "Star Client v" + StarClient.MOD_VERSION + " (MC " + StarClient.MC_VERSION + ")");
            activity.add("assets", assets);

            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            args.add("activity", activity);

            JsonObject root = new JsonObject();
            root.addProperty("cmd", "SET_ACTIVITY");
            root.add("args", args);
            root.addProperty("nonce", UUID.randomUUID().toString());

            writeFrame(pipe, OP_FRAME, GSON.toJson(root));
            Frame ack = readFrame(pipe);
            return ack != null && ack.opcode != OP_CLOSE;
        } catch (Throwable t) {
            StarClient.LOGGER.debug("Discord RPC SET_ACTIVITY failed", t);
            return false;
        }
    }

    private static void clearActivityQuietly(RandomAccessFile pipe) {
        try {
            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());

            JsonObject root = new JsonObject();
            root.addProperty("cmd", "SET_ACTIVITY");
            root.add("args", args);
            root.addProperty("nonce", UUID.randomUUID().toString());
            writeFrame(pipe, OP_FRAME, GSON.toJson(root));
            writeFrame(pipe, OP_CLOSE, "{}");
        } catch (Throwable ignored) {
        }
    }

    private static void writeFrame(RandomAccessFile pipe, int opcode, String json) throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(opcode);
        buf.putInt(payload.length);
        buf.put(payload);
        pipe.write(buf.array());
    }

    private record Frame(int opcode, String json) {
    }

    private static Frame readFrame(RandomAccessFile pipe) throws IOException {
        byte[] header = new byte[8];
        readExact(pipe, header);
        ByteBuffer hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = hb.getInt();
        int len = hb.getInt();
        if (len < 0 || len > 131072) {
            throw new IOException("Invalid Discord IPC frame length: " + len);
        }
        byte[] body = new byte[len];
        if (len > 0) {
            readExact(pipe, body);
        }
        return new Frame(opcode, new String(body, StandardCharsets.UTF_8));
    }

    private static void readExact(RandomAccessFile pipe, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = pipe.read(buf, off, buf.length - off);
            if (n < 0) {
                throw new EOFException("Discord IPC pipe closed");
            }
            off += n;
        }
    }

    private static void closePipeQuietly(RandomAccessFile pipe) {
        try {
            pipe.close();
        } catch (Throwable ignored) {
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean isValidSnowflake(String s) {
        if (s == null) return false;
        String trimmed = s.trim();
        if (trimmed.length() < 15 || trimmed.length() > 22) return false;
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) return false;
        }
        return true;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
