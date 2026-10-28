package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.starclient.setting.NickEntryListSetting;
import net.starclient.setting.NickEntryListSetting.NickEntry;
import net.starclient.setting.Setting;
import net.starclient.setting.TextSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NickToolState {
    private static Module nickModule;

    private NickToolState() {
    }

    public static void setModule(Module module) {
        nickModule = module;
    }

    public static boolean isActive() {
        return nickModule != null && nickModule.isEnabled();
    }

    public static String getSelfNick() {
        if (nickModule == null) return null;
        Setting<?> setting = nickModule.getSetting("Your Nick");
        if (setting instanceof TextSetting text) {
            String nick = text.getValue();
            return nick == null || nick.isBlank() ? null : nick.trim();
        }
        return null;
    }

    public static void setSelfNick(String nick) {
        if (nickModule == null) return;
        Setting<?> setting = nickModule.getSetting("Your Nick");
        if (setting instanceof TextSetting text) {
            text.setValue(nick == null ? "" : nick.trim());
        }
    }

    private static NickEntryListSetting listSetting() {
        if (nickModule == null) return null;
        Setting<?> setting = nickModule.getSetting("Nick List");
        return setting instanceof NickEntryListSetting list ? list : null;
    }

    public static List<NickEntry> getEntries() {
        NickEntryListSetting list = listSetting();
        return list == null ? List.of() : list.getValue();
    }

    public static String nickFor(String realName) {
        NickEntryListSetting list = listSetting();
        return list == null ? null : list.nickFor(realName);
    }

    public static void addEntry(String real, String nick) {
        NickEntryListSetting list = listSetting();
        if (list != null) {
            list.addEntry(real, nick);
        }
    }

    public static void removeEntry(String real) {
        NickEntryListSetting list = listSetting();
        if (list != null) {
            list.removeEntry(real);
        }
    }

    public static Component rewriteNames(Component component) {
        if (!isActive() || component == null) return component;
        List<Replacement> replacements = new ArrayList<>();
        String self = getSelfNick();
        if (self != null) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                String selfReal = client.player.getGameProfile().name();
                if (selfReal != null && !selfReal.isBlank()) {
                    replacements.add(new Replacement(selfReal, self));
                }
            }
        }
        for (NickEntry entry : getEntries()) {
            if (!entry.nick().isBlank()) {
                replacements.add(new Replacement(entry.real(), entry.nick()));
            }
        }
        if (replacements.isEmpty()) return component;
        if (!needsRewrite(component, replacements)) return component;
        return rewrite(component, replacements);
    }

    private record Replacement(String real, String nick) {
    }

    private static long lastDebugLog;

    public static void debug(String message) {
        long now = System.currentTimeMillis();
        if (now - lastDebugLog > 1000) {
            lastDebugLog = now;
            System.out.println("[Nick-Tool] " + message);
        }
    }

    private static boolean needsRewrite(Component component, List<Replacement> replacements) {
        if (!(component instanceof MutableComponent)) return false;
        ComponentContents contents = component.getContents();
        if (contents instanceof PlainTextContents plain && !plain.text().isEmpty()) {
            String lower = plain.text().toLowerCase(java.util.Locale.ROOT);
            for (Replacement r : replacements) {
                if (lower.contains(r.real().toLowerCase(java.util.Locale.ROOT))) return true;
            }
        } else if (contents instanceof TranslatableContents translatable) {
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component argComponent && needsRewrite(argComponent, replacements)) return true;
            }
        }
        for (Component child : component.getSiblings()) {
            if (needsRewrite(child, replacements)) return true;
        }
        return false;
    }

    private static Component rewrite(Component component, List<Replacement> replacements) {
        if (!(component instanceof MutableComponent mutable)) return component;
        ComponentContents contents = component.getContents();

        MutableComponent base;
        if (contents instanceof PlainTextContents plain) {
            String text = plain.text();
            for (Replacement r : replacements) {
                if (text.equals(r.real())) {
                    text = r.nick();
                } else {
                    text = replaceCaseInsensitive(text, r.real(), r.nick());
                }
            }
            base = Component.literal(text).withStyle(component.getStyle());
        } else if (contents instanceof TranslatableContents translatable) {
            Object[] args = translatable.getArgs();
            Object[] newArgs = args.clone();
            boolean argChanged = false;
            for (int i = 0; i < newArgs.length; i++) {
                if (newArgs[i] instanceof Component argComponent) {
                    Component rewrittenArg = rewrite(argComponent, replacements);
                    if (rewrittenArg != argComponent) {
                        newArgs[i] = rewrittenArg;
                        argChanged = true;
                    }
                }
            }
            base = Component.translatable(translatable.getKey(), argChanged ? newArgs : args).withStyle(component.getStyle());
        } else {
            return component;
        }

        for (Component child : component.getSiblings()) {
            base.append(rewrite(child, replacements));
        }
        return base;
    }

    private static String replaceCaseInsensitive(String input, String literal, String replacement) {
        if (input.isEmpty() || literal.isEmpty()) return input;
        Pattern pattern = Pattern.compile(Pattern.quote(literal), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);
        return matcher.replaceAll(Matcher.quoteReplacement(replacement));
    }
}