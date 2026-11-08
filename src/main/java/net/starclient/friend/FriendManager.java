package net.starclient.friend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.gui.ToastNotificationManager;
import net.starclient.setting.FriendListSetting;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages the Star Client Friends System: global friends persistence, per-friend notes/tags,
 * customizable badge styles, join/leave toast notifications, chat name highlighting,
 * middle-click toggling, combat protection, and color helpers.
 */
public final class FriendManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<FriendEntry> friendEntries = new CopyOnWriteArrayList<>();

    private static boolean middleMouseDownLastTick = false;
    private static final Set<String> currentOnlineFriends = new HashSet<>();
    private static boolean initialOnlineScanDone = false;
    private static String lastServerAddress = null;
    private static int tickCounter = 0;

    private FriendManager() {
    }

    public static List<FriendEntry> getFriendEntries() {
        return Collections.unmodifiableList(friendEntries);
    }

    public static List<String> getFriends() {
        List<String> list = new ArrayList<>(friendEntries.size());
        for (FriendEntry fe : friendEntries) {
            list.add(fe.getName());
        }
        return list;
    }

    public static FriendEntry getFriendEntry(String username) {
        if (username == null || username.isBlank()) return null;
        String clean = username.trim();
        for (FriendEntry fe : friendEntries) {
            if (fe.getName().equalsIgnoreCase(clean)) {
                return fe;
            }
        }
        return null;
    }

    public static String getFriendNote(String username) {
        FriendEntry fe = getFriendEntry(username);
        return fe != null ? fe.getNote() : "";
    }

    public static boolean setFriendNote(String username, String note) {
        FriendEntry fe = getFriendEntry(username);
        if (fe != null) {
            fe.setNote(note);
            save();
            return true;
        }
        return false;
    }

    public static boolean isFriend(String username) {
        if (username == null || username.isBlank()) return false;
        String clean = username.trim();
        for (FriendEntry fe : friendEntries) {
            if (fe.getName().equalsIgnoreCase(clean)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isFriend(Entity entity) {
        if (!(entity instanceof Player player)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && player == mc.player) return false;
        String profileName = player.getGameProfile().name();
        if (isFriend(profileName)) return true;
        return isFriend(player.getName().getString());
    }

    public static boolean addFriend(String username) {
        return addFriend(username, "");
    }

    public static boolean addFriend(String username, String note) {
        if (username == null) return false;
        String cleanName = username.trim();
        if (cleanName.isBlank()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            String self = mc.player.getGameProfile().name();
            if (self != null && self.equalsIgnoreCase(cleanName)) {
                return false;
            }
        }

        String cleanNote = note != null ? note.trim() : "";
        for (FriendEntry fe : friendEntries) {
            if (fe.getName().equalsIgnoreCase(cleanName)) {
                if (!fe.getNote().equals(cleanNote)) {
                    fe.setNote(cleanNote);
                    save();
                    return true;
                }
                return false;
            }
        }

        FriendEntry newEntry = new FriendEntry(cleanName, cleanNote);
        friendEntries.add(newEntry);
        save();

        String badge = getFriendBadgeSymbol();
        String toastSub = badge + " " + cleanName + (cleanNote.isEmpty() ? "" : " • " + cleanNote);
        ToastNotificationManager.pushToast(
                "friend:" + cleanName.toLowerCase(Locale.ROOT),
                "Friend Added",
                toastSub,
                "FRIEND",
                getFriendColor()
        );
        return true;
    }

    public static boolean removeFriend(String username) {
        if (username == null) return false;
        String cleanName = username.trim();
        FriendEntry removed = null;
        for (FriendEntry fe : friendEntries) {
            if (fe.getName().equalsIgnoreCase(cleanName)) {
                removed = fe;
                break;
            }
        }
        if (removed != null) {
            friendEntries.remove(removed);
            save();
            ToastNotificationManager.pushToast(
                    "friend:" + cleanName.toLowerCase(Locale.ROOT),
                    "Friend Removed",
                    cleanName,
                    "REMOVED",
                    0xFFF87171
            );
            return true;
        }
        return false;
    }

    public static void removeFriendAt(int index) {
        if (index >= 0 && index < friendEntries.size()) {
            FriendEntry removed = friendEntries.remove(index);
            save();
            if (removed != null) {
                ToastNotificationManager.pushToast(
                        "friend:" + removed.getName().toLowerCase(Locale.ROOT),
                        "Friend Removed",
                        removed.getName(),
                        "REMOVED",
                        0xFFF87171
                );
            }
        }
    }

    public static void clearFriends() {
        friendEntries.clear();
        save();
    }

    private static File getFriendsFile() {
        File dir = new File(Minecraft.getInstance().gameDirectory, StarClient.CONFIG_FOLDER);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "friends.json");
    }

    public static void load() {
        File file = getFriendsFile();
        if (!file.exists()) {
            StarClient sc = StarClient.getInstance();
            if (sc != null && sc.getClientSettings() != null) {
                FriendListSetting fls = sc.getClientSettings().getFriendListSetting();
                if (fls != null && !fls.getValue().isEmpty()) {
                    for (String name : fls.getValue()) {
                        if (name != null && !name.isBlank()) {
                            friendEntries.add(new FriendEntry(name.trim()));
                        }
                    }
                    save();
                }
            }
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<FriendEntry>>() {}.getType();
            List<FriendEntry> loaded = GSON.fromJson(reader, type);
            friendEntries.clear();
            if (loaded != null) {
                for (FriendEntry fe : loaded) {
                    if (fe != null && !fe.getName().isBlank()) {
                        friendEntries.add(fe);
                    }
                }
            }
            StarClient.LOGGER.info("Loaded {} friends globally from {}", friendEntries.size(), file.getName());
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to load friends from {}", file.getName(), e);
        }
    }

    public static void save() {
        File file = getFriendsFile();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(friendEntries, writer);
            }
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to save friends to {}", file.getName(), e);
        }
    }

    public static boolean isShowFriendBadge() {
        StarClient sc = StarClient.getInstance();
        return sc != null && sc.getClientSettings() != null && sc.getClientSettings().isFriendBadge();
    }

    public static String getFriendBadgeSymbol() {
        StarClient sc = StarClient.getInstance();
        if (sc == null || sc.getClientSettings() == null) return "♥";
        String style = sc.getClientSettings().getFriendBadgeStyle();
        if (style == null) return "♥";
        return switch (style) {
            case "★ Star" -> "★";
            case "🛡 Ally" -> "🛡";
            case "✦ Sparkle" -> "✦";
            case "[FRIEND]" -> "[FRIEND]";
            default -> "♥";
        };
    }

    public static String getFriendBadgePrefix() {
        return getFriendBadgeSymbol() + " ";
    }

    public static boolean isHighlightFriendsInEsp() {
        StarClient sc = StarClient.getInstance();
        return sc != null && sc.getClientSettings() != null && sc.getClientSettings().isHighlightFriendsInEsp();
    }

    public static boolean shouldProtectInCombat(Entity entity) {
        StarClient sc = StarClient.getInstance();
        if (sc == null || sc.getClientSettings() == null || !sc.getClientSettings().isProtectFriendsInCombat()) {
            return false;
        }
        return isFriend(entity);
    }

    public static int getFriendColor() {
        StarClient sc = StarClient.getInstance();
        if (sc != null && sc.getClientSettings() != null) {
            return sc.getClientSettings().getFriendColor();
        }
        return 0xFF34D399;
    }

    public static int getFriendRgb() {
        return getFriendColor() & 0x00FFFFFF;
    }

    public static void onClientTick(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            middleMouseDownLastTick = false;
            currentOnlineFriends.clear();
            initialOnlineScanDone = false;
            lastServerAddress = null;
            return;
        }

        StarClient sc = StarClient.getInstance();
        if (sc == null || sc.getClientSettings() == null) return;
        ClientSettings cs = sc.getClientSettings();

        // 1. Middle-click player in-game to add/remove friend
        boolean middleDown = client.gui.screen() == null
                && GLFW.glfwGetMouseButton(client.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;

        if (middleDown && !middleMouseDownLastTick && cs.isMiddleClickFriend()) {
            if (client.hitResult instanceof EntityHitResult ehr
                    && ehr.getEntity() instanceof Player targetPlayer
                    && targetPlayer != client.player) {
                String name = targetPlayer.getGameProfile().name();
                if (name == null || name.isBlank()) {
                    name = targetPlayer.getName().getString();
                }
                if (isFriend(name)) {
                    removeFriend(name);
                    client.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.65f, 0.9f);
                } else {
                    addFriend(name);
                    client.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.75f, 1.4f);
                }
            }
        }
        middleMouseDownLastTick = middleDown;

        // 2. Tab-list Friend Join / Leave Toast notifications
        tickCounter++;
        if (tickCounter % 10 != 0) return;

        if (client.getConnection() == null) {
            currentOnlineFriends.clear();
            initialOnlineScanDone = false;
            lastServerAddress = null;
            return;
        }

        String currentServer = client.getCurrentServer() != null ? client.getCurrentServer().ip : "Singleplayer";
        if (!Objects.equals(currentServer, lastServerAddress)) {
            lastServerAddress = currentServer;
            currentOnlineFriends.clear();
            initialOnlineScanDone = false;
        }

        var listed = client.getConnection().getListedOnlinePlayers();
        if (listed == null) return;

        Set<String> nowOnline = new HashSet<>();
        for (PlayerInfo info : listed) {
            if (info == null || info.getProfile() == null) continue;
            String pName = info.getProfile().name();
            if (pName != null && isFriend(pName)) {
                nowOnline.add(pName.toLowerCase(Locale.ROOT));
            }
        }

        if (!initialOnlineScanDone) {
            currentOnlineFriends.clear();
            currentOnlineFriends.addAll(nowOnline);
            initialOnlineScanDone = true;
            return;
        }

        boolean notify = cs.isFriendNotifications();
        boolean sound = cs.isFriendNotificationSound();
        String badge = getFriendBadgeSymbol();
        int friendCol = getFriendColor();

        // Check for friend joins
        for (String lower : nowOnline) {
            if (!currentOnlineFriends.contains(lower)) {
                currentOnlineFriends.add(lower);
                FriendEntry entry = getFriendEntry(lower);
                String displayName = entry != null ? entry.getName() : lower;
                String note = entry != null ? entry.getNote() : "";
                if (notify) {
                    String toastSub = badge + " " + displayName + (note.isEmpty() ? "" : " • " + note);
                    ToastNotificationManager.pushToast(
                            "friend_join:" + lower,
                            "Friend Joined",
                            toastSub,
                            "ONLINE",
                            friendCol
                    );
                    if (sound && client.player != null) {
                        client.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.75f, 1.4f);
                    }
                }
            }
        }

        // Check for friend leaves
        Iterator<String> it = currentOnlineFriends.iterator();
        while (it.hasNext()) {
            String lower = it.next();
            if (!nowOnline.contains(lower)) {
                it.remove();
                FriendEntry entry = getFriendEntry(lower);
                String displayName = entry != null ? entry.getName() : lower;
                String note = entry != null ? entry.getNote() : "";
                if (notify) {
                    String toastSub = displayName + (note.isEmpty() ? "" : " • " + note);
                    ToastNotificationManager.pushToast(
                            "friend_leave:" + lower,
                            "Friend Left",
                            toastSub,
                            "OFFLINE",
                            0xFF94A3B8
                    );
                    if (sound && client.player != null) {
                        client.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.55f, 0.75f);
                    }
                }
            }
        }
    }

    /**
     * Decorates chat messages with friend badges in front of friends' names.
     */
    public static Component decorateChat(Component message) {
        if (message == null) return null;
        StarClient sc = StarClient.getInstance();
        if (sc == null || sc.getClientSettings() == null || !sc.getClientSettings().isFriendChatBadge()) {
            return message;
        }
        if (friendEntries.isEmpty()) {
            return message;
        }

        String plain = message.getString();
        boolean hasAny = false;
        for (FriendEntry fe : friendEntries) {
            if (!fe.getName().isBlank() && containsWordIgnoreCase(plain, fe.getName())) {
                hasAny = true;
                break;
            }
        }
        if (!hasAny) {
            return message;
        }

        return rewriteChatNode(message);
    }

    private static Component rewriteChatNode(Component comp) {
        if (comp == null) return null;
        Style style = comp.getStyle();
        ComponentContents contents = comp.getContents();

        MutableComponent result;
        if (contents instanceof PlainTextContents plain) {
            result = insertFriendBadgesIntoString(plain.text(), style);
        } else if (contents instanceof TranslatableContents tc) {
            Object[] args = tc.getArgs();
            Object[] newArgs = new Object[args.length];
            boolean changed = false;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component argComp) {
                    Component rewritten = rewriteChatNode(argComp);
                    if (rewritten != argComp) {
                        newArgs[i] = rewritten;
                        changed = true;
                        continue;
                    }
                } else if (args[i] instanceof String argStr) {
                    if (containsAnyFriend(argStr)) {
                        newArgs[i] = insertFriendBadgesIntoString(argStr, style);
                        changed = true;
                        continue;
                    }
                }
                newArgs[i] = args[i];
            }
            result = Component.translatableWithFallback(tc.getKey(), tc.getFallback(), changed ? newArgs : args)
                    .withStyle(style);
        } else {
            result = comp.copy();
        }

        for (Component sibling : comp.getSiblings()) {
            result.append(rewriteChatNode(sibling));
        }
        return result;
    }

    private static MutableComponent insertFriendBadgesIntoString(String text, Style baseStyle) {
        if (text == null || text.isEmpty()) {
            return Component.literal("").withStyle(baseStyle);
        }

        Pattern pattern = getFriendChatPattern();
        if (pattern == null) {
            return Component.literal(text).withStyle(baseStyle);
        }

        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return Component.literal(text).withStyle(baseStyle);
        }

        MutableComponent out = Component.empty();
        int last = 0;
        int friendCol = getFriendRgb();
        String badge = getFriendBadgePrefix();

        do {
            int start = matcher.start();
            int end = matcher.end();
            if (start > last) {
                out.append(Component.literal(text.substring(last, start)).withStyle(baseStyle));
            }

            out.append(Component.literal(badge).withStyle(Style.EMPTY.withColor(friendCol)));
            out.append(Component.literal(text.substring(start, end)).withStyle(baseStyle));

            last = end;
        } while (matcher.find());

        if (last < text.length()) {
            out.append(Component.literal(text.substring(last)).withStyle(baseStyle));
        }

        return out;
    }

    private static Pattern getFriendChatPattern() {
        List<String> names = new ArrayList<>();
        for (FriendEntry fe : friendEntries) {
            if (!fe.getName().isBlank()) {
                names.add(Pattern.quote(fe.getName()));
            }
        }
        if (names.isEmpty()) return null;

        String joined = String.join("|", names);
        return Pattern.compile("(?<![♥★🛡✦\\[\\w])(?<![♥★🛡✦]\\s)\\b(" + joined + ")\\b", Pattern.CASE_INSENSITIVE);
    }

    private static boolean containsAnyFriend(String text) {
        if (text == null || text.isEmpty()) return false;
        for (FriendEntry fe : friendEntries) {
            if (!fe.getName().isBlank() && containsWordIgnoreCase(text, fe.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWordIgnoreCase(String text, String word) {
        if (text == null || word == null || text.isEmpty() || word.isEmpty()) return false;
        Pattern p = Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE);
        return p.matcher(text).find();
    }
}
