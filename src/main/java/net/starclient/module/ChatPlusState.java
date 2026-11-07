package net.starclient.module;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import net.starclient.setting.BannedWordListSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * State & formatting helpers for the "Chat" UI module:
 * Timestamps, Anti-Spam Compact Chat, Click-to-Copy (Player + Server/Mod Info),
 * Transparent Background, Extended History, Save Chat History Across Servers/Worlds,
 * Banned Words Censor Filter, and Name Mention Color Highlight + Sound Ping.
 */
public final class ChatPlusState {

    public static final Map<String, Integer> MENTION_COLORS = ColorModeSetting.ofPairs(
            "Yellow", "FFFACC15",
            "Gold", "FFF59E0B",
            "Sky Blue", "FF38BDF8",
            "Cyan", "FF22D3EE",
            "Green", "FF34D399",
            "Lime", "FFA3E635",
            "Orange", "FFFB923C",
            "Red", "FFF87171",
            "Pink", "FFF472B6",
            "Purple", "FFA78BFA",
            "White", "FFFFFFFF"
    );

    private static Module module;

    public record CompactTrack(String key, int count) {
    }

    private static final WeakHashMap<Object, CompactTrack> COMPACT_TRACKER = new WeakHashMap<>();
    private static final Set<Component> SESSION_DIVIDERS = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Pattern COLOR_STRIP = Pattern.compile("(?i)§[0-9A-FK-ORX]");
    private static final Pattern FORMAT_SPEC_PATTERN = Pattern.compile("%(?:(\\d+)\\$)?([A-Za-z%]|$)");

    private static final DateTimeFormatter FMT_24H = DateTimeFormatter.ofPattern("HH:mm", Locale.US);
    private static final DateTimeFormatter FMT_24H_SEC = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US);
    private static final DateTimeFormatter FMT_12H = DateTimeFormatter.ofPattern("hh:mm a", Locale.US);

    // Tracks whether the player disconnected / switched worlds since the last login
    // (avoids posting duplicate dividers on internal BungeeCord/Velocity sub-server switches).
    private static boolean disconnectedWithSavedHistory = false;

    private ChatPlusState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static boolean isTimestamps() {
        return isEnabled() && getBool("Timestamps", true);
    }

    public static boolean isCompactChat() {
        return isEnabled() && getBool("Compact Chat", true);
    }

    public static boolean isClickToCopy() {
        return isEnabled() && getBool("Click to Copy", true);
    }

    public static boolean isCopyServerAndModInfo() {
        return isEnabled() && getBool("Copy Server & Mod Info", true);
    }

    public static boolean isSaveChatHistory() {
        return isEnabled() && getBool("Save Chat History", true);
    }

    public static boolean isWordFilterEnabled() {
        return isEnabled() && getBool("Word Filter", true);
    }

    public static boolean isTransparentBackground() {
        return isEnabled() && module.isTransparentBackground();
    }

    public static boolean isMentionHighlight() {
        return isEnabled() && getBool("Mention Highlight", true);
    }

    public static boolean isMentionSound() {
        return isEnabled() && getBool("Mention Sound", true);
    }

    public static int getMentionColor() {
        if (module != null && module.getSetting("Mention Color") instanceof ColorModeSetting cms) {
            return cms.getColor() & 0x00FFFFFF;
        }
        return 0xFACC15;
    }

    public static int getMaxHistory() {
        if (!isEnabled()) return 100;
        if (module.getSetting("Extended History") instanceof NumberSetting ns) {
            return Math.clamp((int) Math.round(ns.getValue()), 100, 5000);
        }
        return 1000;
    }

    // -------------------------------------------------------------------------
    // Banned Words Management
    // -------------------------------------------------------------------------

    public static BannedWordListSetting getBannedWordsSetting() {
        if (module != null && module.getSetting("Banned Words") instanceof BannedWordListSetting bw) {
            return bw;
        }
        return null;
    }

    public static List<String> getBannedWords() {
        BannedWordListSetting bw = getBannedWordsSetting();
        return bw != null ? bw.getValue() : List.of();
    }

    public static boolean addBannedWord(String word) {
        BannedWordListSetting bw = getBannedWordsSetting();
        return bw != null && bw.addWord(word);
    }

    public static void removeBannedWordAt(int index) {
        BannedWordListSetting bw = getBannedWordsSetting();
        if (bw != null) {
            bw.removeAt(index);
        }
    }

    public static void clearBannedWords() {
        BannedWordListSetting bw = getBannedWordsSetting();
        if (bw != null) {
            bw.getValue().clear();
        }
    }

    /**
     * Replaces each non-whitespace character in {@code text} with {@code '*'}.
     */
    public static String maskWord(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(Character.isWhitespace(c) ? c : '*');
        }
        return sb.toString();
    }

    private static Pattern buildBannedWordsPattern() {
        List<String> words = getBannedWords();
        if (words.isEmpty()) return null;
        List<String> sorted = new ArrayList<>(words);
        sorted.removeIf(w -> w == null || w.isBlank());
        if (sorted.isEmpty()) return null;
        // Match longer phrases/words first so "badword" censors all 7 letters before "bad"
        sorted.sort(Comparator.comparingInt(String::length).reversed());
        StringBuilder regex = new StringBuilder("(?iu)(");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) regex.append('|');
            regex.append(Pattern.quote(sorted.get(i)));
        }
        regex.append(')');
        return Pattern.compile(regex.toString());
    }

    // -------------------------------------------------------------------------
    // Cross-Server / Cross-World Chat History
    // -------------------------------------------------------------------------

    public static void onClientDisconnected(boolean hadMessages) {
        if (hadMessages) {
            disconnectedWithSavedHistory = true;
        }
    }

    public static void onManualChatCleared() {
        disconnectedWithSavedHistory = false;
    }

    public static boolean isSessionDivider(Component comp) {
        return comp != null && SESSION_DIVIDERS.contains(comp);
    }

    public static void onSessionJoined(Minecraft client) {
        if (client == null || client.gui == null) return;
        if (!disconnectedWithSavedHistory) {
            return;
        }
        disconnectedWithSavedHistory = false;

        if (!isSaveChatHistory()) {
            return;
        }

        String sessionLabel = resolveSessionLabel(client);
        MutableComponent divider = Component.empty()
                .append(Component.literal("────────── ").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH))
                .append(Component.literal("Joined ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(sessionLabel).withStyle(Style.EMPTY.withColor(0xF8FAFC).withBold(true)))
                .append(Component.literal(" ──────────").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH));

        SESSION_DIVIDERS.add(divider);
        client.gui.hud.getChat().addClientSystemMessage(divider);
    }

    private static String resolveSessionLabel(Minecraft client) {
        if (client.isLocalServer()) {
            IntegratedServer sp = client.getSingleplayerServer();
            String worldName = (sp != null && sp.getWorldData() != null)
                    ? sp.getWorldData().getLevelName()
                    : "Singleplayer";
            return "World: " + worldName;
        }
        ServerData sd = client.getCurrentServer();
        if (sd != null) {
            String ip = sd.ip != null ? sd.ip.trim() : "";
            String name = sd.name != null ? sd.name.trim() : "";
            if (!name.isEmpty()
                    && !name.equalsIgnoreCase("Minecraft Server")
                    && !name.equalsIgnoreCase(ip)) {
                return ip.isEmpty() ? name : (name + " (" + ip + ")");
            }
            if (!ip.isEmpty()) {
                return ip;
            }
        }
        return "Multiplayer Server";
    }

    // -------------------------------------------------------------------------
    // Compact Chat & Decoration Pipeline
    // -------------------------------------------------------------------------

    public static void recordCompactTrack(Object guiMessage, String key, int count) {
        if (guiMessage != null && key != null) {
            COMPACT_TRACKER.put(guiMessage, new CompactTrack(key, count));
        }
    }

    public static CompactTrack getCompactTrack(Object guiMessage) {
        return COMPACT_TRACKER.get(guiMessage);
    }

    public static String normalizeKey(Component comp) {
        if (comp == null) return "";
        String raw = comp.getString();
        return COLOR_STRIP.matcher(raw).replaceAll("").trim();
    }

    /**
     * Applies Banned Word Censoring, Mention Name Color Highlight (+ optional sound ping),
     * Timestamps, Compact count suffix, and Click-to-Copy.
     */
    public static Component decorateMessage(Component base, String rawPlain, int repeatCount, GuiMessageSource source) {
        if (base == null || !isEnabled() || isSessionDivider(base)) {
            return base;
        }

        boolean isClientSystem = (source == GuiMessageSource.SYSTEM_CLIENT);
        boolean isPlayerSource = (source == GuiMessageSource.PLAYER);

        Minecraft mc = Minecraft.getInstance();
        MutableComponent working = base.copy();
        String effectivePlain = rawPlain;

        // 0. Banned Words Filter (replaces each letter of banned words with '*')
        if (isWordFilterEnabled()) {
            Pattern censorPattern = buildBannedWordsPattern();
            if (censorPattern != null && censorPattern.matcher(effectivePlain).find()) {
                working = censorComponentTree(working, censorPattern);
                effectivePlain = censorPlainText(effectivePlain, censorPattern);
            }
        }

        // 1. Name Mention Highlight (only colors mentions inside the message body, never the sender prefix)
        if (!isClientSystem && isMentionHighlight() && mc.player != null) {
            String myName = mc.player.getGameProfile().name();
            if (myName != null && !myName.isEmpty()) {
                Pattern mentionPattern = buildMentionWordPattern(myName);
                String untrimmedPlain = COLOR_STRIP.matcher(working.getString()).replaceAll("");
                if (hasBodyMention(working, untrimmedPlain, mentionPattern)) {
                    int mentionRgb = getMentionColor();
                    working = highlightNameInTree(working, mentionPattern, untrimmedPlain, mentionRgb);
                    if (isMentionSound()) {
                        mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.45f, 1.35f);
                    }
                }
            }
        }

        // 2. Timestamp Prefix
        if (isTimestamps() && !effectivePlain.isBlank()) {
            String fmtMode = getMode();
            DateTimeFormatter dtf = switch (fmtMode) {
                case "24h (HH:mm:ss)" -> FMT_24H_SEC;
                case "12h (hh:mm a)" -> FMT_12H;
                default -> FMT_24H;
            };
            String timeText = "[" + LocalTime.now().format(dtf) + "] ";
            MutableComponent ts = Component.literal(timeText)
                    .withStyle(Style.EMPTY.withColor(0x94A3B8));
            working = Component.empty().append(ts).append(working);
        }

        // 3. Compact Chat Repeat Counter [x2], [x3], ...
        if (repeatCount > 1) {
            MutableComponent suffix = Component.literal(" [x" + repeatCount + "]")
                    .withStyle(Style.EMPTY.withColor(0x38BDF8).withBold(false));
            working = working.append(suffix);
        }

        // 4. Click-to-Copy (Player messages + optionally Server & Mod info messages)
        boolean allowCopyForSource = isPlayerSource
                || looksLikePlayerChat(effectivePlain)
                || isCopyServerAndModInfo();
        if (isClickToCopy() && allowCopyForSource && !effectivePlain.isBlank()) {
            ClickEvent copyEvt = new ClickEvent.CopyToClipboard(effectivePlain);
            HoverEvent hoverEvt = new HoverEvent.ShowText(
                    Component.literal("Click to copy message").withStyle(ChatFormatting.GRAY));

            int appliedSpans = applyFallbackCopyStyle(working, copyEvt, hoverEvt);

            if (appliedSpans == 0 && !effectivePlain.contains("[Copy]")) {
                MutableComponent copyChip = Component.literal(" [Copy]")
                        .withStyle(Style.EMPTY
                                .withColor(ChatFormatting.DARK_GRAY)
                                .withClickEvent(copyEvt)
                                .withHoverEvent(hoverEvt));
                working = working.append(copyChip);
            }
        }

        return working;
    }

    private static String censorPlainText(String text, Pattern pattern) {
        if (text == null || text.isEmpty()) return text;
        Matcher m = pattern.matcher(text);
        if (!m.find()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        int last = 0;
        do {
            sb.append(text, last, m.start());
            sb.append(maskWord(m.group()));
            last = m.end();
        } while (m.find());
        sb.append(text, last, text.length());
        return sb.toString();
    }

    /**
     * Censors banned words across PlainTextContents, TranslatableContents args (and resolved
     * translatable templates if a banned word originates from a translation template), and siblings.
     */
    private static MutableComponent censorComponentTree(Component comp, Pattern pattern) {
        Style baseStyle = comp.getStyle();
        ComponentContents contents = comp.getContents();
        MutableComponent result;

        if (contents instanceof PlainTextContents plain) {
            String text = plain.text();
            String censored = censorLegacyAwareString(text, pattern);
            result = Component.literal(censored).withStyle(baseStyle);
        } else if (contents instanceof TranslatableContents tc) {
            Object[] origArgs = tc.getArgs();
            Object[] newArgs = new Object[origArgs.length];
            for (int i = 0; i < origArgs.length; i++) {
                if (origArgs[i] instanceof Component argComp) {
                    newArgs[i] = censorComponentTree(argComp, pattern);
                } else if (origArgs[i] instanceof String strArg) {
                    newArgs[i] = censorLegacyAwareString(strArg, pattern);
                } else {
                    newArgs[i] = origArgs[i];
                }
            }
            MutableComponent rebuilt = Component.translatableWithFallback(tc.getKey(), tc.getFallback(), newArgs)
                    .withStyle(baseStyle);
            // If the translation template text itself still contains a banned word, flatten & censor its text
            String resolvedCheck = rebuilt.getString();
            if (pattern.matcher(COLOR_STRIP.matcher(resolvedCheck).replaceAll("")).find()) {
                result = Component.literal(censorLegacyAwareString(resolvedCheck, pattern)).withStyle(baseStyle);
            } else {
                result = rebuilt;
            }
        } else {
            result = comp.plainCopy().withStyle(baseStyle);
        }

        for (Component sibling : comp.getSiblings()) {
            result.append(censorComponentTree(sibling, pattern));
        }
        return result;
    }

    /**
     * Censors banned words inside a string even if legacy § color codes appear before/around words,
     * preserving any § formatting sequences intact while replacing only visible letters with '*'.
     */
    private static String censorLegacyAwareString(String text, Pattern pattern) {
        if (text == null || text.isEmpty()) return text;
        if (text.indexOf('§') < 0) {
            return censorPlainText(text, pattern);
        }
        // Build mapping from stripped-char index -> original index
        int[] mapToOrig = new int[text.length()];
        StringBuilder stripped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '§' && i + 1 < text.length()) {
                i++; // skip § and format char
                continue;
            }
            mapToOrig[stripped.length()] = i;
            stripped.append(text.charAt(i));
        }
        Matcher m = pattern.matcher(stripped);
        if (!m.find()) return text;

        char[] chars = text.toCharArray();
        do {
            for (int si = m.start(); si < m.end(); si++) {
                int oi = mapToOrig[si];
                if (!Character.isWhitespace(chars[oi])) {
                    chars[oi] = '*';
                }
            }
        } while (m.find());
        return new String(chars);
    }

    private static int applyFallbackCopyStyle(MutableComponent comp, ClickEvent copyEvt, HoverEvent hoverEvt) {
        if (comp == null) return 0;

        Style curStyle = comp.getStyle();
        if (curStyle.getClickEvent() != null) {
            return 0;
        }

        int count = 0;
        ComponentContents contents = comp.getContents();

        if (contents instanceof TranslatableContents tc) {
            boolean hasPlainTemplate = tc.getFallback() == null || !tc.getFallback().isBlank();
            if (hasPlainTemplate) {
                count++;
            }
            for (Object arg : tc.getArgs()) {
                if (arg instanceof MutableComponent argMutable) {
                    count += applyFallbackCopyStyle(argMutable, copyEvt, hoverEvt);
                }
            }
        } else if (contents instanceof PlainTextContents pt && !pt.text().isBlank()) {
            count++;
        }

        Style updated = curStyle.withClickEvent(copyEvt);
        if (updated.getHoverEvent() == null) {
            updated = updated.withHoverEvent(hoverEvt);
        }
        comp.setStyle(updated);

        for (Component sib : comp.getSiblings()) {
            if (sib instanceof MutableComponent ms) {
                count += applyFallbackCopyStyle(ms, copyEvt, hoverEvt);
            }
        }
        return count;
    }

    private static Pattern buildMentionWordPattern(String targetName) {
        return Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(targetName) + "(?![A-Za-z0-9_])");
    }

    private static boolean isKnownChatEnvelopeKey(String key) {
        if (key == null) return false;
        return switch (key) {
            case "chat.type.text",
                 "chat.type.emote",
                 "chat.type.announcement",
                 "chat.type.team.text",
                 "chat.type.team.sent",
                 "commands.message.display.incoming",
                 "commands.message.display.outgoing" -> true;
            default -> false;
        };
    }

    private static boolean hasBodyMention(Component comp, String untrimmedPlain, Pattern wordPattern) {
        if (comp == null || untrimmedPlain == null || untrimmedPlain.isEmpty()) return false;

        if (comp.getContents() instanceof TranslatableContents tc
                && isKnownChatEnvelopeKey(tc.getKey())
                && tc.getArgs().length >= 2) {
            Object bodyArg = tc.getArgs()[tc.getArgs().length - 1];
            String bodyStr = (bodyArg instanceof Component c)
                    ? COLOR_STRIP.matcher(c.getString()).replaceAll("")
                    : COLOR_STRIP.matcher(String.valueOf(bodyArg)).replaceAll("");
            if (wordPattern.matcher(bodyStr).find()) {
                return true;
            }
            for (Component sib : comp.getSiblings()) {
                String sibStr = COLOR_STRIP.matcher(sib.getString()).replaceAll("");
                if (wordPattern.matcher(sibStr).find()) {
                    return true;
                }
            }
            return false;
        }

        int bodyStart = findMessageBodyStart(untrimmedPlain);
        Matcher m = wordPattern.matcher(untrimmedPlain);
        while (m.find()) {
            if (m.start() >= bodyStart) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the character index where the actual message body starts, skipping sender/username headers
     * such as {@code <Name>: }, {@code <Name> }, {@code [Rank] Name: }, {@code [From -> To] }, or {@code Name » }.
     */
    private static int findMessageBodyStart(String plain) {
        if (plain == null || plain.isEmpty()) return 0;
        int len = plain.length();
        int lead = 0;
        while (lead < len && plain.charAt(lead) == ' ') {
            lead++;
        }
        int scanLimit = Math.min(len, lead + 80);

        // 1. "<Name>: message"
        int angleColon = plain.indexOf(">: ", lead);
        if (angleColon > lead && angleColon < scanLimit) {
            int openAngle = plain.lastIndexOf('<', angleColon);
            if (openAngle >= lead) {
                return angleColon + 3;
            }
        }

        // 2. "<Name> message" or "[Team] <Name> message"
        int openAngle = plain.indexOf('<', lead);
        if (openAngle >= lead && openAngle < lead + 32) {
            int angleSpace = plain.indexOf("> ", openAngle + 2);
            if (angleSpace > openAngle && angleSpace < scanLimit) {
                return angleSpace + 2;
            }
        }

        // 3. "[Sender -> Receiver] message" (bracketed whisper header without colon)
        if (lead < len && plain.charAt(lead) == '[') {
            int closeBracket = plain.indexOf("] ", lead + 1);
            if (closeBracket > lead && closeBracket < lead + 52) {
                String inside = plain.substring(lead + 1, closeBracket);
                if (inside.contains("->") || inside.contains("➡") || inside.contains("»")) {
                    return closeBracket + 2;
                }
            }
        }

        // 4. "[Rank] Name: message" or "Name: message"
        int colonSpace = plain.indexOf(": ", lead);
        if (colonSpace > lead && colonSpace < scanLimit) {
            return colonSpace + 2;
        }

        // 5. Arrow / chevron chat separators: "Name » message", "Name › message", "Name -> message"
        String[] separators = {" » ", "» ", " › ", "› ", " -> ", " ➡ ", " »"};
        int bestSepEnd = -1;
        int bestSepIdx = Integer.MAX_VALUE;
        for (String sep : separators) {
            int idx = plain.indexOf(sep, lead);
            if (idx > lead && idx < scanLimit && idx < bestSepIdx) {
                bestSepIdx = idx;
                bestSepEnd = idx + sep.length();
            }
        }
        if (bestSepEnd > 0) {
            while (bestSepEnd < len && plain.charAt(bestSepEnd) == ' ') {
                bestSepEnd++;
            }
            return bestSepEnd;
        }

        // 6. Plain-text "/me" emote: "* Name action..."
        if (plain.startsWith("* ", lead) && len > lead + 3) {
            int nextSpace = plain.indexOf(' ', lead + 2);
            if (nextSpace > lead + 2 && nextSpace < lead + 32) {
                return nextSpace + 1;
            }
        }

        return 0;
    }

    private static MutableComponent highlightNameInTree(Component comp, Pattern wordPattern, String untrimmedPlain, int rgbColor) {
        if (comp.getContents() instanceof TranslatableContents tc
                && isKnownChatEnvelopeKey(tc.getKey())
                && tc.getArgs().length >= 2) {
            Style baseStyle = comp.getStyle();
            Object[] origArgs = tc.getArgs();
            Object[] newArgs = new Object[origArgs.length];
            int lastIdx = origArgs.length - 1;
            System.arraycopy(origArgs, 0, newArgs, 0, lastIdx);
            if (origArgs[lastIdx] instanceof Component argComp) {
                newArgs[lastIdx] = rewriteHighlightNode(argComp, wordPattern, rgbColor, 0, new int[]{0});
            } else if (origArgs[lastIdx] instanceof String strArg) {
                newArgs[lastIdx] = rewriteHighlightNode(Component.literal(strArg), wordPattern, rgbColor, 0, new int[]{0});
            } else {
                newArgs[lastIdx] = origArgs[lastIdx];
            }
            MutableComponent result = Component.translatableWithFallback(tc.getKey(), tc.getFallback(), newArgs)
                    .withStyle(baseStyle);
            for (Component sibling : comp.getSiblings()) {
                result.append(rewriteHighlightNode(sibling, wordPattern, rgbColor, 0, new int[]{0}));
            }
            return result;
        }

        int bodyStart = findMessageBodyStart(untrimmedPlain);
        return rewriteHighlightNode(comp, wordPattern, rgbColor, bodyStart, new int[]{0});
    }

    private static MutableComponent rewriteHighlightNode(Component comp, Pattern pattern, int rgbColor, int bodyStart, int[] cursor) {
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
                int nodeStart = cursor[0];
                cursor[0] += stripped.length();

                Matcher m = pattern.matcher(stripped);
                List<int[]> validSpans = null;
                while (m.find()) {
                    if (nodeStart + m.start() >= bodyStart && m.end() > m.start()) {
                        if (validSpans == null) {
                            validSpans = new ArrayList<>(2);
                        }
                        validSpans.add(new int[]{m.start(), m.end()});
                    }
                }

                if (validSpans != null) {
                    result = Component.empty().withStyle(baseStyle);
                    int lastOrigEnd = 0;
                    String activeLegacyCodes = "";
                    for (int[] span : validSpans) {
                        int origStart = mapToOrig[span[0]];
                        int origEnd = mapToOrig[span[1] - 1] + 1;
                        String before = text.substring(lastOrigEnd, origStart);
                        if (!before.isEmpty()) {
                            String combinedBefore = activeLegacyCodes + before;
                            activeLegacyCodes = extractActiveLegacyFormat(combinedBefore);
                            if (!COLOR_STRIP.matcher(before).replaceAll("").isEmpty()) {
                                result.append(Component.literal(combinedBefore).withStyle(baseStyle));
                            }
                        }
                        String matchedVisible = stripped.substring(span[0], span[1]);
                        Style highlightStyle = baseStyle.withColor(rgbColor);
                        result.append(Component.literal(matchedVisible).withStyle(highlightStyle));
                        lastOrigEnd = origEnd;
                    }
                    if (lastOrigEnd < text.length()) {
                        String after = text.substring(lastOrigEnd);
                        if (!COLOR_STRIP.matcher(after).replaceAll("").isEmpty()) {
                            result.append(Component.literal(activeLegacyCodes + after).withStyle(baseStyle));
                        }
                    }
                } else {
                    result = Component.literal(text).withStyle(baseStyle);
                }
            }
        } else if (contents instanceof TranslatableContents tc) {
            Object[] origArgs = tc.getArgs();
            Object[] newArgs = new Object[origArgs.length];
            System.arraycopy(origArgs, 0, newArgs, 0, origArgs.length);
            boolean[] processedArg = new boolean[origArgs.length];

            Language lang = Language.getInstance();
            String template = tc.getFallback() != null
                    ? lang.getOrDefault(tc.getKey(), tc.getFallback())
                    : lang.getOrDefault(tc.getKey());

            Matcher fm = FORMAT_SPEC_PATTERN.matcher(template);
            int seqIdx = 0;
            int lastTemplatePos = 0;
            while (fm.find()) {
                if (fm.start() > lastTemplatePos) {
                    String lit = template.substring(lastTemplatePos, fm.start());
                    cursor[0] += COLOR_STRIP.matcher(lit).replaceAll("").length();
                }
                String type = fm.group(2);
                if ("%".equals(type)) {
                    cursor[0] += 1;
                } else if ("s".equals(type)) {
                    String posGroup = fm.group(1);
                    int argIdx = posGroup != null ? (Integer.parseInt(posGroup) - 1) : seqIdx++;
                    if (argIdx >= 0 && argIdx < origArgs.length) {
                        Object arg = origArgs[argIdx];
                        if (!processedArg[argIdx]) {
                            processedArg[argIdx] = true;
                            if (arg instanceof Component argComp) {
                                newArgs[argIdx] = rewriteHighlightNode(argComp, pattern, rgbColor, bodyStart, cursor);
                            } else if (arg instanceof String strArg) {
                                newArgs[argIdx] = rewriteHighlightNode(Component.literal(strArg), pattern, rgbColor, bodyStart, cursor);
                            } else {
                                cursor[0] += COLOR_STRIP.matcher(String.valueOf(arg)).replaceAll("").length();
                            }
                        } else {
                            String argStr = (arg instanceof Component c)
                                    ? COLOR_STRIP.matcher(c.getString()).replaceAll("")
                                    : COLOR_STRIP.matcher(String.valueOf(arg)).replaceAll("");
                            cursor[0] += argStr.length();
                        }
                    }
                }
                lastTemplatePos = fm.end();
            }
            if (lastTemplatePos < template.length()) {
                String tail = template.substring(lastTemplatePos);
                cursor[0] += COLOR_STRIP.matcher(tail).replaceAll("").length();
            }

            result = Component.translatableWithFallback(tc.getKey(), tc.getFallback(), newArgs)
                    .withStyle(baseStyle);
        } else {
            String selfText = comp.plainCopy().getString();
            cursor[0] += COLOR_STRIP.matcher(selfText).replaceAll("").length();
            result = comp.plainCopy().withStyle(baseStyle);
        }

        for (Component sibling : comp.getSiblings()) {
            result.append(rewriteHighlightNode(sibling, pattern, rgbColor, bodyStart, cursor));
        }
        return result;
    }

    private static String extractActiveLegacyFormat(String s) {
        String colorCode = "";
        StringBuilder formats = new StringBuilder();
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) == '§') {
                char c = Character.toLowerCase(s.charAt(i + 1));
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
                    colorCode = "§" + c;
                    formats.setLength(0);
                    i++;
                } else if (c == 'r') {
                    colorCode = "§r";
                    formats.setLength(0);
                    i++;
                } else if (c >= 'k' && c <= 'o') {
                    formats.append('§').append(c);
                    i++;
                }
            }
        }
        return colorCode + formats;
    }

    private static boolean looksLikePlayerChat(String plain) {
        if (plain == null || plain.isEmpty()) return false;
        if (plain.startsWith("<") && (plain.indexOf("> ") > 1 && plain.indexOf("> ") < 28
                || plain.indexOf(">: ") > 1 && plain.indexOf(">: ") < 28)) {
            return true;
        }
        int colon = plain.indexOf(": ");
        return colon > 1 && colon < 42;
    }

    private static boolean getBool(String name, boolean def) {
        if (module == null) return def;
        Setting<?> s = module.getSetting(name);
        return s != null && s.getValue() instanceof Boolean b ? b : def;
    }

    private static String getMode() {
        if (module == null) return "24h (HH:mm)";
        Setting<?> s = module.getSetting("Time Format");
        return s != null && s.getValue() instanceof String str ? str : "24h (HH:mm)";
    }
}
