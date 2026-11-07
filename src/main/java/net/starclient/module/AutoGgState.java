package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;
import net.starclient.setting.TextSetting;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * State & match-end/start trigger detection for the "Auto GG" SERVER module.
 */
public final class AutoGgState {

    private static Module module;
    private static final Pattern COLOR_STRIP = Pattern.compile("(?i)§[0-9A-FK-ORX]");

    private static final String[] END_TRIGGERS = {
            "1st killer -",
            "1st place -",
            "winner:",
            "winners:",
            "winning team -",
            "victory!",
            "game over!",
            "match summary",
            "reward summary",
            "duel summary"
    };

    private static final String[] START_TRIGGERS = {
            "the game starts in 1 second!",
            "the game has started!",
            "cages opened! fight!",
            "the duel has started!",
            "let the game begin!"
    };

    private static String pendingChat;
    private static long sendAtMs;
    private static long lastEndTriggeredMs;
    private static long lastStartTriggeredMs;

    private AutoGgState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static void onServerMessage(String rawPlain) {
        if (!isEnabled() || rawPlain == null || rawPlain.isBlank()) return;
        String clean = COLOR_STRIP.matcher(rawPlain).replaceAll("").trim();
        if (clean.isEmpty()) return;

        // Spoof guard: skip standard player chat lines ("<Player> ..." or "[Rank] Player: ...")
        if (looksLikePlayerChat(clean)) {
            return;
        }

        String lower = clean.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        long cooldownMs = (long) (getNum("Cooldown", 8.0) * 1000.0);

        // 1. Check Match End (GG)
        if (now - lastEndTriggeredMs >= cooldownMs) {
            for (String trigger : END_TRIGGERS) {
                if (lower.contains(trigger)) {
                    String msg = getText("GG Message", "gg").trim();
                    if (!msg.isEmpty()) {
                        lastEndTriggeredMs = now;
                        double delaySec = getNum("GG Delay", 1.0);
                        pendingChat = msg;
                        sendAtMs = now + Math.round(delaySec * 1000.0);
                    }
                    return;
                }
            }
        }

        // 2. Check Match Start (GLHF)
        if (getBool() && now - lastStartTriggeredMs >= cooldownMs) {
            for (String trigger : START_TRIGGERS) {
                if (lower.contains(trigger)) {
                    String msg = getText("GLHF Message", "glhf").trim();
                    if (!msg.isEmpty()) {
                        lastStartTriggeredMs = now;
                        double delaySec = getNum("GLHF Delay", 1.0);
                        pendingChat = msg;
                        sendAtMs = now + Math.round(delaySec * 1000.0);
                    }
                    return;
                }
            }
        }
    }

    public static void onClientTick(Minecraft client) {
        if (pendingChat == null) return;
        if (!isEnabled() || client == null || client.player == null) {
            if (client == null || client.player == null) {
                pendingChat = null;
            }
            return;
        }
        if (System.currentTimeMillis() >= sendAtMs) {
            String msg = pendingChat;
            pendingChat = null;
            if (msg.startsWith("/")) {
                client.player.connection.sendCommand(msg.substring(1));
            } else {
                client.player.connection.sendChat(msg);
            }
        }
    }

    private static boolean looksLikePlayerChat(String clean) {
        if (clean.startsWith("<") && clean.contains("> ")) {
            return true;
        }
        int colon = clean.indexOf(": ");
        if (colon > 0 && colon < 42) {
            String prefix = clean.substring(0, colon).toLowerCase(Locale.ROOT);
            // Allow "Winner: ..." and "Winners: ..." which use ": " legitimately
            return !prefix.equals("winner") && !prefix.equals("winners");
        }
        return false;
    }

    private static boolean getBool() {
        if (module == null) return false;
        Setting<?> s = module.getSetting("Auto GLHF");
        return s != null && s.getValue() instanceof Boolean b && b;
    }

    private static double getNum(String name, double def) {
        if (module != null && module.getSetting(name) instanceof NumberSetting ns) {
            return ns.getValue();
        }
        return def;
    }

    private static String getText(String name, String def) {
        if (module != null && module.getSetting(name) instanceof TextSetting ts) {
            return ts.getValue();
        }
        return def;
    }
}
