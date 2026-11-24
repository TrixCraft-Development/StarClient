package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.starclient.StarClient;
import net.starclient.gui.ToastNotificationManager;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.KeywordAlertListSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime chat scanner, Component highlighter, toast trigger, and sound chime
 * controller for the "Keyword Alerts" module.
 */
public final class KeywordAlertsState {
    private static final Pattern COLOR_STRIP = Pattern.compile("§[0-9a-fk-orxA-FK-ORX]");
    private static final long ALERT_COOLDOWN_MS = 650L;

    private static Module module;
    private static long lastAlertTimeMs = 0L;

    private KeywordAlertsState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    private static KeywordAlertListSetting getListSetting() {
        if (module == null) return null;
        Setting<?> s = module.getSetting("Keywords");
        return s instanceof KeywordAlertListSetting kls ? kls : null;
    }

    public static List<String> getKeywords() {
        KeywordAlertListSetting s = getListSetting();
        return s != null ? s.getValue() : Collections.emptyList();
    }

    public static boolean addKeyword(String keyword) {
        KeywordAlertListSetting s = getListSetting();
        return s != null && s.addKeyword(keyword);
    }

    public static void removeKeywordAt(int index) {
        KeywordAlertListSetting s = getListSetting();
        if (s != null) {
            s.removeAt(index);
        }
    }

    public static void clearKeywords() {
        KeywordAlertListSetting s = getListSetting();
        if (s != null) {
            s.getValue().clear();
        }
    }

    public static int getHighlightArgb() {
        if (getBool("Use Client Color", false)) {
            StarClient sc = StarClient.getInstance();
            if (sc != null && sc.getClientSettings() != null) {
                return sc.getClientSettings().getMenuAccentColor();
            }
        }
        if (module != null) {
            Setting<?> s = module.getSetting("Highlight Color");
            if (s instanceof ColorModeSetting cms) {
                return cms.getColor();
            }
        }
        return 0xFFFACC15;
    }

    public static Component processIncomingMessage(Component message, GuiMessageSource source) {
        if (message == null || !isEnabled() || source == GuiMessageSource.SYSTEM_CLIENT) {
            return message;
        }
        if (ChatPlusState.isSessionDivider(message)) {
            return message;
        }

        List<String> keywords = getKeywords();
        if (keywords.isEmpty()) {
            return message;
        }

        String rawPlain = COLOR_STRIP.matcher(message.getString()).replaceAll("");
        if (rawPlain.isBlank()) {
            return message;
        }

        if (getBool("Ignore Own Messages", true) && isOwnPlayerMessage(rawPlain)) {
            return message;
        }

        Pattern pattern = buildKeywordsPattern(keywords, getBool("Whole Word Only", true));
        if (pattern == null) {
            return message;
        }

        Matcher probe = pattern.matcher(rawPlain);
        if (!probe.find()) {
            return message;
        }

        String matchedText = probe.group();
        int argb = getHighlightArgb();
        int rgb = argb & 0x00FFFFFF;
        boolean bold = getBool("Bold Highlight", true);

        // Trigger Toast + Sound chime (throttled briefly against chat bursts)
        long now = System.currentTimeMillis();
        if (now - lastAlertTimeMs >= ALERT_COOLDOWN_MS) {
            lastAlertTimeMs = now;
            if (getBool("Toast Alert", true)) {
                String snippet = rawPlain.trim();
                if (snippet.length() > 48) {
                    snippet = snippet.substring(0, 45) + "...";
                }
                ToastNotificationManager.pushToast(
                        "keyword_alert_" + matchedText.toLowerCase(Locale.ROOT),
                        "🔔 Keyword: " + matchedText,
                        snippet,
                        "ALERT",
                        argb
                );
            }
            if (getBool("Sound Alert", true)) {
                playAlertSound(getMode("Sound Type", "Pling"));
            }
        }

        return highlightTree(message, pattern, rgb, bold);
    }

    public static void playPreviewSound() {
        playAlertSound(getMode("Sound Type", "Pling"));
    }

    private static void playAlertSound(String type) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        SoundEvent sound = switch (type) {
            case "Bell" -> SoundEvents.NOTE_BLOCK_BELL.value();
            case "Ding" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case "Level Up" -> SoundEvents.PLAYER_LEVELUP;
            case "Bass" -> SoundEvents.NOTE_BLOCK_BASS.value();
            default -> SoundEvents.NOTE_BLOCK_PLING.value();
        };
        float pitch = "Level Up".equals(type) ? 1.0F : 1.35F;
        client.execute(() -> client.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch)));
    }

    private static boolean isOwnPlayerMessage(String plain) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return false;
        String myName = client.player.getGameProfile().name();
        if (myName == null || myName.isEmpty()) return false;

        String trimmed = plain.trim();
        String low = trimmed.toLowerCase(Locale.ROOT);
        String nameLow = myName.toLowerCase(Locale.ROOT);

        // Check standard chat sender prefixes: "<Name>", "<Name>:", "[Rank] Name:", "Name:", "Name »"
        int sep = findSenderEnd(trimmed);
        if (sep <= 0) return false;
        String header = low.substring(0, sep);
        Pattern nameWord = Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(nameLow) + "(?![a-z0-9_])");
        return nameWord.matcher(header).find();
    }

    private static int findSenderEnd(String plain) {
        int limit = Math.min(plain.length(), 72);
        int angle = plain.indexOf("> ");
        if (angle > 0 && angle < limit) return angle + 1;
        int colon = plain.indexOf(": ");
        if (colon > 0 && colon < limit) return colon;
        int chev = plain.indexOf(" » ");
        if (chev > 0 && chev < limit) return chev;
        return -1;
    }

    private static Pattern buildKeywordsPattern(List<String> keywords, boolean wholeWordOnly) {
        List<String> sorted = new ArrayList<>();
        for (String kw : keywords) {
            if (kw != null && !kw.trim().isEmpty()) {
                sorted.add(kw.trim());
            }
        }
        if (sorted.isEmpty()) return null;
        sorted.sort(Comparator.comparingInt(String::length).reversed());

        StringBuilder regex = new StringBuilder();
        for (String kw : sorted) {
            if (!regex.isEmpty()) regex.append('|');
            String quoted = Pattern.quote(kw);
            if (wholeWordOnly) {
                boolean startWord = Character.isLetterOrDigit(kw.charAt(0)) || kw.charAt(0) == '_';
                boolean endWord = Character.isLetterOrDigit(kw.charAt(kw.length() - 1)) || kw.charAt(kw.length() - 1) == '_';
                if (startWord) regex.append("(?<![\\p{L}\\p{N}_])");
                regex.append(quoted);
                if (endWord) regex.append("(?![\\p{L}\\p{N}_])");
            } else {
                regex.append(quoted);
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static MutableComponent highlightTree(Component comp, Pattern pattern, int rgb, boolean bold) {
        Style baseStyle = comp.getStyle();
        ComponentContents contents = comp.getContents();
        MutableComponent result;

        if (contents instanceof PlainTextContents plain) {
            String text = plain.text();
            if (text.isEmpty()) {
                result = Component.literal(text).withStyle(baseStyle);
            } else {
                int[] mapToOrig = new int[text.length()];
                StringBuilder strippedBuilder = new StringBuilder(text.length());
                for (int i = 0; i < text.length(); i++) {
                    if (text.charAt(i) == '§' && i + 1 < text.length()) {
                        char code = Character.toLowerCase(text.charAt(i + 1));
                        if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')
                                || (code >= 'k' && code <= 'o') || code == 'r' || code == 'x') {
                            i++;
                            continue;
                        }
                    }
                    mapToOrig[strippedBuilder.length()] = i;
                    strippedBuilder.append(text.charAt(i));
                }

                String stripped = strippedBuilder.toString();
                Matcher m = pattern.matcher(stripped);
                List<int[]> spans = null;
                while (m.find()) {
                    if (m.end() > m.start()) {
                        if (spans == null) spans = new ArrayList<>(2);
                        spans.add(new int[]{m.start(), m.end()});
                    }
                }

                if (spans != null) {
                    result = Component.empty().withStyle(baseStyle);
                    int lastOrigEnd = 0;
                    String activeLegacy = "";
                    for (int[] span : spans) {
                        int origStart = mapToOrig[span[0]];
                        int origEnd = mapToOrig[span[1] - 1] + 1;
                        String before = text.substring(lastOrigEnd, origStart);
                        if (!before.isEmpty()) {
                            String combined = activeLegacy + before;
                            activeLegacy = extractLegacyFormat(combined);
                            if (!COLOR_STRIP.matcher(before).replaceAll("").isEmpty()) {
                                result.append(Component.literal(combined).withStyle(baseStyle));
                            }
                        }
                        String matchedVisible = stripped.substring(span[0], span[1]);
                        Style hlStyle = baseStyle.withColor(rgb).withBold(bold);
                        result.append(Component.literal(matchedVisible).withStyle(hlStyle));
                        lastOrigEnd = origEnd;
                    }
                    if (lastOrigEnd < text.length()) {
                        String after = text.substring(lastOrigEnd);
                        if (!COLOR_STRIP.matcher(after).replaceAll("").isEmpty()) {
                            result.append(Component.literal(activeLegacy + after).withStyle(baseStyle));
                        }
                    }
                } else {
                    result = Component.literal(text).withStyle(baseStyle);
                }
            }
        } else if (contents instanceof TranslatableContents tc) {
            Object[] origArgs = tc.getArgs();
            Object[] newArgs = new Object[origArgs.length];
            for (int i = 0; i < origArgs.length; i++) {
                Object arg = origArgs[i];
                if (arg instanceof Component argComp) {
                    newArgs[i] = highlightTree(argComp, pattern, rgb, bold);
                } else if (arg instanceof String strArg) {
                    newArgs[i] = highlightTree(Component.literal(strArg), pattern, rgb, bold);
                } else {
                    newArgs[i] = arg;
                }
            }
            result = Component.translatableWithFallback(tc.getKey(), tc.getFallback(), newArgs).withStyle(baseStyle);
        } else {
            result = comp.plainCopy().withStyle(baseStyle);
        }

        for (Component sibling : comp.getSiblings()) {
            result.append(highlightTree(sibling, pattern, rgb, bold));
        }
        return result;
    }

    private static String extractLegacyFormat(String text) {
        String lastColor = "";
        StringBuilder styles = new StringBuilder();
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) == '§') {
                char c = Character.toLowerCase(text.charAt(i + 1));
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                    lastColor = "§" + c;
                    styles.setLength(0);
                    i++;
                } else if (c == 'r') {
                    lastColor = "";
                    styles.setLength(0);
                    i++;
                } else if (c >= 'k' && c <= 'o') {
                    String tag = "§" + c;
                    if (styles.indexOf(tag) < 0) styles.append(tag);
                    i++;
                }
            }
        }
        return lastColor + styles;
    }

    private static boolean getBool(String name, boolean fallback) {
        if (module == null) return fallback;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : fallback;
    }

    private static String getMode(String name, String fallback) {
        if (module == null) return fallback;
        Setting<?> s = module.getSetting(name);
        return s instanceof ModeSetting ms ? ms.getValue() : fallback;
    }
}
