package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

public final class ServerSessionTracker {
    private static long sessionStartMillis = System.currentTimeMillis();
    private static long lastTimePacketNanos = 0L;
    private static long lastServerGameTime = -1L;
    private static double estimatedTps = 20.0;
    private static ServerData lastServerData;

    private ServerSessionTracker() {
    }

    public static void onSessionJoined(ServerData serverData) {
        sessionStartMillis = System.currentTimeMillis();
        lastTimePacketNanos = 0L;
        lastServerGameTime = -1L;
        estimatedTps = 20.0;
        if (serverData != null) {
            lastServerData = serverData;
        }
    }

    public static void setLastServerData(ServerData serverData) {
        if (serverData != null) {
            lastServerData = serverData;
        }
    }

    public static ServerData getLastServerData() {
        return lastServerData;
    }

    public static void onTimePacket(long gameTime) {
        long now = System.nanoTime();
        if (lastTimePacketNanos > 0L && lastServerGameTime >= 0L) {
            long deltaTicks = gameTime - lastServerGameTime;
            double deltaSec = (now - lastTimePacketNanos) / 1_000_000_000.0;
            if (deltaTicks > 0 && deltaTicks <= 100 && deltaSec > 0.05 && deltaSec < 10.0) {
                double sampleTps = Math.clamp(deltaTicks / deltaSec, 0.0, 20.0);
                estimatedTps = estimatedTps * 0.7 + sampleTps * 0.3;
            }
        }
        lastTimePacketNanos = now;
        lastServerGameTime = gameTime;
    }

    public static double getEstimatedTps(Minecraft client) {
        if (client != null && client.isLocalServer()) {
            return 20.0;
        }
        if (lastTimePacketNanos > 0L) {
            double sinceLastSec = (System.nanoTime() - lastTimePacketNanos) / 1_000_000_000.0;
            if (sinceLastSec > 3.0) {
                // Server hasn't sent a time packet in >3s (lag spike)
                return Math.clamp(20.0 / sinceLastSec, 0.0, 20.0);
            }
        }
        return Math.clamp(estimatedTps, 0.0, 20.0);
    }

    public static String getFormattedSessionDuration() {
        long elapsedSec = Math.max(0L, (System.currentTimeMillis() - sessionStartMillis) / 1000L);
        long hours = elapsedSec / 3600L;
        long minutes = (elapsedSec % 3600L) / 60L;
        long seconds = elapsedSec % 60L;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }
}
