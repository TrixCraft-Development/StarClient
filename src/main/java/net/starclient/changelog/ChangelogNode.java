package net.starclient.changelog;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;
import net.starclient.gui.GuiRenderUtils;

import java.util.List;

public sealed interface ChangelogNode permits
        ChangelogNode.HeaderNode,
        ChangelogNode.ListItemNode,
        ChangelogNode.BlockquoteNode,
        ChangelogNode.HorizontalRuleNode,
        ChangelogNode.ParagraphNode,
        ChangelogNode.SpacerNode {

    int getHeight();

    void render(GuiGraphicsExtractor extractor, Font font, int x, int y, int contentWidth, int accentColor);

    record HeaderNode(int level, String rawText, List<FormattedCharSequence> lines, int totalHeight) implements ChangelogNode {
        @Override
        public int getHeight() {
            return totalHeight;
        }

        @Override
        public void render(GuiGraphicsExtractor extractor, Font font, int x, int y, int contentWidth, int accentColor) {
            int curY = y;
            if (level == 1) {
                // H1: Top title
                for (FormattedCharSequence line : lines) {
                    extractor.text(font, line, x, curY, accentColor, true);
                    curY += font.lineHeight + 2;
                }
            } else if (level == 2) {
                // H2: Major Section (e.g. What's New)
                // Decorative left accent pill
                GuiRenderUtils.fillRoundedRect(extractor, x, curY + 1, 3, font.lineHeight + 1, 1, accentColor);
                for (FormattedCharSequence line : lines) {
                    extractor.text(font, line, x + 8, curY, 0xFFF1F5F9, true);
                    curY += font.lineHeight + 2;
                }
                // Subtle divider underneath
                extractor.fill(x + 8, curY + 1, x + contentWidth, curY + 2, 0x2264748B);
            } else {
                // H3: Sub-section (e.g. Inventory HUD)
                GuiRenderUtils.drawText(extractor, font, "◆", x, curY, accentColor);
                for (FormattedCharSequence line : lines) {
                    extractor.text(font, line, x + 10, curY, accentColor, true);
                    curY += font.lineHeight + 2;
                }
            }
        }
    }

    record ListItemNode(int indent, String badge, int badgeColor, List<FormattedCharSequence> lines, int totalHeight) implements ChangelogNode {
        @Override
        public int getHeight() {
            return totalHeight;
        }

        @Override
        public void render(GuiGraphicsExtractor extractor, Font font, int x, int y, int contentWidth, int accentColor) {
            int itemX = x + indent;
            int curY = y;

            // Draw bullet dot
            GuiRenderUtils.drawCenteredText(extractor, font, "•", itemX + 3, curY, accentColor);

            int textOffsetX = itemX + 10;
            if (badge != null && !badge.isEmpty()) {
                int badgeW = font.width(badge) + 8;
                int badgeH = 11;
                int badgeY = curY - 1;
                GuiRenderUtils.fillRoundedRect(extractor, textOffsetX, badgeY, badgeW, badgeH, 3, 0x33000000 | (badgeColor & 0x00FFFFFF));
                GuiRenderUtils.drawRoundedOutline(extractor, textOffsetX, badgeY, badgeW, badgeH, 3, badgeColor);
                GuiRenderUtils.drawCenteredText(extractor, font, badge, textOffsetX + badgeW / 2, badgeY + 1, badgeColor);
                textOffsetX += badgeW + 6;
            }

            for (int i = 0; i < lines.size(); i++) {
                FormattedCharSequence line = lines.get(i);
                int lineX = (i == 0) ? textOffsetX : (itemX + 10);
                extractor.text(font, line, lineX, curY, 0xFFE2E8F0, true);
                curY += font.lineHeight + 2;
            }
        }
    }

    record BlockquoteNode(List<FormattedCharSequence> lines, int totalHeight) implements ChangelogNode {
        @Override
        public int getHeight() {
            return totalHeight;
        }

        @Override
        public void render(GuiGraphicsExtractor extractor, Font font, int x, int y, int contentWidth, int accentColor) {
            int boxH = totalHeight - 4;
            // Tinted background
            GuiRenderUtils.fillRoundedRect(extractor, x, y, contentWidth, boxH, 4, 0x1838BDF8);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, contentWidth, boxH, 4, 0x3338BDF8);
            // Left vertical accent stripe
            GuiRenderUtils.fillRoundedRect(extractor, x, y, 3, boxH, 1, accentColor);

            int curY = y + 5;
            for (FormattedCharSequence line : lines) {
                extractor.text(font, line, x + 10, curY, 0xFFCBD5E1, true);
                curY += font.lineHeight + 2;
            }
        }
    }

    record HorizontalRuleNode() implements ChangelogNode {
        @Override
        public int getHeight() {
            return 12;
        }

        @Override
        public void render(GuiGraphicsExtractor extractor, Font font, int x, int y, int contentWidth, int accentColor) {
            int lineY = y + 5;
            extractor.fill(x, lineY, x + contentWidth, lineY + 1, 0x2594A3B8);
        }
    }

    record ParagraphNode(List<FormattedCharSequence> lines, int totalHeight) implements ChangelogNode {
        @Override
        public int getHeight() {
            return totalHeight;
        }

        @Override
        public void render(GuiGraphicsExtractor extractor, Font font, int x, int y, int contentWidth, int accentColor) {
            int curY = y;
            for (FormattedCharSequence line : lines) {
                extractor.text(font, line, x, curY, 0xFFCBD5E1, true);
                curY += font.lineHeight + 2;
            }
        }
    }

    record SpacerNode(int spaceHeight) implements ChangelogNode {
        @Override
        public int getHeight() {
            return spaceHeight;
        }

        @Override
        public void render(GuiGraphicsExtractor extractor, Font font, int x, int y, int contentWidth, int accentColor) {
            // Empty space
        }
    }
}
