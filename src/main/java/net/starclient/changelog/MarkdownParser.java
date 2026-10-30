package net.starclient.changelog;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MarkdownParser {
    private static final Pattern INLINE_PATTERN = Pattern.compile(
            "(\\*\\*(.+?)\\*\\*)|(__([^_]+)__)|(\\*([^*]+)\\*)|(_([^_]+)_)|(~~(.+?)~~)|(`([^`]+)`)|(\\[([^\\]]+)\\]\\(([^)]+)\\))"
    );

    private MarkdownParser() {
    }

    public static List<ChangelogNode> parse(List<String> rawLines, Font font, int contentWidth, int accentColor) {
        List<ChangelogNode> nodes = new ArrayList<>();
        if (rawLines == null || rawLines.isEmpty()) {
            return nodes;
        }

        int usableWidth = Math.max(100, contentWidth);

        for (String raw : rawLines) {
            String line = raw == null ? "" : raw;
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                nodes.add(new ChangelogNode.SpacerNode(6));
                continue;
            }

            if (trimmed.equals("---") || trimmed.equals("***") || trimmed.equals("___")) {
                nodes.add(new ChangelogNode.HorizontalRuleNode());
                continue;
            }

            if (trimmed.startsWith("# ")) {
                String text = trimmed.substring(2).trim();
                MutableComponent comp = parseInline(text, accentColor).withStyle(s -> s.withBold(true));
                List<FormattedCharSequence> lines = font.split(comp, usableWidth);
                int height = lines.size() * (font.lineHeight + 2) + 12;
                nodes.add(new ChangelogNode.HeaderNode(1, text, lines, height));
                continue;
            }

            if (trimmed.startsWith("## ")) {
                String text = trimmed.substring(3).trim();
                MutableComponent comp = parseInline(text, 0xFFF1F5F9).withStyle(s -> s.withBold(true));
                List<FormattedCharSequence> lines = font.split(comp, usableWidth - 14);
                int height = lines.size() * (font.lineHeight + 2) + 10;
                nodes.add(new ChangelogNode.HeaderNode(2, text, lines, height));
                continue;
            }

            if (trimmed.startsWith("### ")) {
                String text = trimmed.substring(4).trim();
                MutableComponent comp = parseInline(text, accentColor).withStyle(s -> s.withBold(true));
                List<FormattedCharSequence> lines = font.split(comp, usableWidth - 14);
                int height = lines.size() * (font.lineHeight + 2) + 6;
                nodes.add(new ChangelogNode.HeaderNode(3, text, lines, height));
                continue;
            }

            if (trimmed.startsWith("> ")) {
                String text = trimmed.substring(2).trim();
                MutableComponent comp = parseInline(text, 0xFFCBD5E1).withStyle(s -> s.withItalic(true));
                List<FormattedCharSequence> lines = font.split(comp, usableWidth - 20);
                int height = lines.size() * (font.lineHeight + 2) + 10;
                nodes.add(new ChangelogNode.BlockquoteNode(lines, height));
                continue;
            }

            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                int indent = 0;
                if (line.startsWith("    ") || line.startsWith("\t\t")) {
                    indent = 22;
                } else if (line.startsWith("  ") || line.startsWith("\t")) {
                    indent = 12;
                }

                String content = trimmed.substring(2).trim();
                String badge = null;
                int badgeColor = 0;

                if (content.startsWith("[NEW]")) {
                    badge = "NEW";
                    badgeColor = 0xFF10B981;
                    content = content.substring(5).trim();
                } else if (content.startsWith("[FIX]")) {
                    badge = "FIX";
                    badgeColor = 0xFFF43F5E;
                    content = content.substring(5).trim();
                } else if (content.startsWith("[IMPROVED]")) {
                    badge = "IMPROVED";
                    badgeColor = 0xFF0EA5E9;
                    content = content.substring(10).trim();
                } else if (content.startsWith("[UPDATE]")) {
                    badge = "UPDATE";
                    badgeColor = 0xFF0EA5E9;
                    content = content.substring(8).trim();
                } else if (content.startsWith("[REMOVED]")) {
                    badge = "REMOVED";
                    badgeColor = 0xFFEF4444;
                    content = content.substring(9).trim();
                } else if (content.startsWith("[NOTE]")) {
                    badge = "NOTE";
                    badgeColor = 0xFFA855F7;
                    content = content.substring(6).trim();
                }

                int badgeWidth = (badge != null) ? font.width(badge) + 14 : 0;
                int itemAvailW = Math.max(60, usableWidth - indent - 10 - badgeWidth);
                MutableComponent comp = parseInline(content, 0xFFE2E8F0);
                List<FormattedCharSequence> lines = font.split(comp, itemAvailW);
                int height = lines.size() * (font.lineHeight + 2) + 3;
                nodes.add(new ChangelogNode.ListItemNode(indent, badge, badgeColor, lines, height));
                continue;
            }

            // Regular paragraph
            MutableComponent comp = parseInline(trimmed, 0xFFCBD5E1);
            List<FormattedCharSequence> lines = font.split(comp, usableWidth);
            int height = lines.size() * (font.lineHeight + 2) + 4;
            nodes.add(new ChangelogNode.ParagraphNode(lines, height));
        }

        return nodes;
    }

    public static MutableComponent parseInline(String text, int defaultColor) {
        MutableComponent root = Component.empty();
        if (text == null || text.isEmpty()) {
            return root;
        }

        Matcher matcher = INLINE_PATTERN.matcher(text);
        int lastIndex = 0;

        while (matcher.find()) {
            if (matcher.start() > lastIndex) {
                String plain = text.substring(lastIndex, matcher.start());
                root.append(Component.literal(plain).withStyle(s -> s.withColor(defaultColor)));
            }

            if (matcher.group(2) != null) {
                // **bold**
                root.append(Component.literal(matcher.group(2)).withStyle(s -> s.withColor(defaultColor).withBold(true)));
            } else if (matcher.group(4) != null) {
                // __bold__
                root.append(Component.literal(matcher.group(4)).withStyle(s -> s.withColor(defaultColor).withBold(true)));
            } else if (matcher.group(6) != null) {
                // *italic*
                root.append(Component.literal(matcher.group(6)).withStyle(s -> s.withColor(defaultColor).withItalic(true)));
            } else if (matcher.group(8) != null) {
                // _italic_
                root.append(Component.literal(matcher.group(8)).withStyle(s -> s.withColor(defaultColor).withItalic(true)));
            } else if (matcher.group(10) != null) {
                // ~~strikethrough~~
                root.append(Component.literal(matcher.group(10)).withStyle(s -> s.withColor(0xFF94A3B8).withStrikethrough(true)));
            } else if (matcher.group(12) != null) {
                // `code`
                root.append(Component.literal(matcher.group(12)).withStyle(s -> s.withColor(0xFFFDE047)));
            } else if (matcher.group(14) != null) {
                // [text](url)
                root.append(Component.literal(matcher.group(14)).withStyle(s -> s.withColor(0xFF38BDF8).withUnderlined(true)));
            }

            lastIndex = matcher.end();
        }

        if (lastIndex < text.length()) {
            String remaining = text.substring(lastIndex);
            root.append(Component.literal(remaining).withStyle(s -> s.withColor(defaultColor)));
        }

        return root;
    }
}
