package net.starclient.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.config.ClientProfile;
import net.starclient.config.ProfileManager;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class ProfilesViewRenderer {
    private static final int CREATE_BAR_HEIGHT = 36;
    private static final int CARD_BASE_HEIGHT = 54;
    private static final int CARD_GAP = 8;

    public enum FocusedInput {
        NONE,
        NEW_PROFILE_NAME,
        RENAME_PROFILE,
        CUSTOM_WORLD,
        CUSTOM_SERVER,
        BIND_PROFILE_KEY
    }

    private FocusedInput focusedInput = FocusedInput.NONE;
    private String newProfileName = "";
    private String renamingProfileId = null;
    private String renameBuffer = "";
    private String bindingProfileId = null;
    private String expandedLinkProfileId = null;
    private String customWorldInput = "";
    private String customServerInput = "";
    private String statusMessage = "";
    private int statusColor = 0xFF34D399;
    private long statusTimestamp = 0L;

    public boolean hasFocusedInput() {
        return focusedInput != FocusedInput.NONE;
    }

    public void clearFocus() {
        focusedInput = FocusedInput.NONE;
        renamingProfileId = null;
        bindingProfileId = null;
    }

    private void showStatus(String msg, int color) {
        this.statusMessage = msg;
        this.statusColor = color;
        this.statusTimestamp = System.currentTimeMillis();
    }

    public double computeMaxScroll(int contentWidth, int viewportHeight) {
        ProfileManager pm = StarClient.getInstance().getProfileManager();
        if (pm == null) return 0;

        int totalH = CREATE_BAR_HEIGHT + 12;
        for (ClientProfile profile : pm.getProfiles()) {
            totalH += getCardHeight(profile) + CARD_GAP;
        }
        totalH += 12;
        return Math.max(0, totalH - viewportHeight);
    }

    private int getCardHeight(ClientProfile profile) {
        int h = CARD_BASE_HEIGHT;
        if (profile.getId().equals(expandedLinkProfileId)) {
            ProfileManager pm = StarClient.getInstance().getProfileManager();
            int worldRows = pm != null ? Math.max(1, (pm.getDetectedSingleplayerWorlds().size() + 1) / 2) : 1;
            int serverRows = pm != null ? Math.max(1, (pm.getDetectedMultiplayerServers().size() + 1) / 2) : 1;
            int dynamicRows = Math.min(5, Math.max(worldRows, serverRows));
            boolean inGame = ProfileManager.getCurrentWorldName() != null || ProfileManager.getCurrentServerIp() != null;
            h += 88 + dynamicRows * 20 + (inGame ? 24 : 0);
        }
        return h;
    }

    public void draw(GuiGraphicsExtractor extractor, Font font, int contentLeft, int contentWidth,
                     int panelTop, int panelBottom, int mouseX, int mouseY, double scroll) {
        ProfileManager pm = StarClient.getInstance().getProfileManager();
        if (pm == null) return;

        ClientSettings cs = StarClient.getInstance().getClientSettings();
        int accent = cs.getMenuAccentColor();
        int accentDark = cs.getMenuAccentDarkColor();
        int surface = cs.getMenuSurfaceColor();
        int surfaceHover = cs.getMenuSurfaceHoverColor();
        int bgDark = cs.getMenuBackgroundDarkColor();
        int border = cs.getMenuBorderColor();

        int curY = (int) Math.round(panelTop - scroll);

        // 1. Top Create Profile Bar
        GuiRenderUtils.fillRoundedRect(extractor, contentLeft, curY, contentWidth, CREATE_BAR_HEIGHT, 6, surface);
        GuiRenderUtils.drawRoundedOutline(extractor, contentLeft, curY, contentWidth, CREATE_BAR_HEIGHT, 6, border);

        int btnImportW = 66;
        int btnBlankW = 76;
        int btnCopyW = 94;
        int btnH = 22;
        int btnY = curY + (CREATE_BAR_HEIGHT - btnH) / 2;
        int btnImportX = contentLeft + contentWidth - 8 - btnImportW;
        int btnBlankX = btnImportX - 5 - btnBlankW;
        int btnCopyX = btnBlankX - 5 - btnCopyW;

        int inputX = contentLeft + 8;
        int inputW = Math.max(90, btnCopyX - inputX - 6);
        int inputY = btnY;
        boolean inputFocused = (focusedInput == FocusedInput.NEW_PROFILE_NAME);
        boolean inputHover = isHovered(mouseX, mouseY, inputX, inputY, inputW, btnH);

        GuiRenderUtils.fillRoundedRect(extractor, inputX, inputY, inputW, btnH, 5, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, inputX, inputY, inputW, btnH, 5,
                inputFocused ? accent : (inputHover ? 0xFF64748B : border));

        String cursor = (inputFocused && (System.currentTimeMillis() / 500) % 2 == 0) ? "|" : "";
        if (newProfileName.isEmpty() && !inputFocused) {
            boolean showStatusHere = statusMessage != null && !statusMessage.isEmpty()
                    && (System.currentTimeMillis() - statusTimestamp) < 3500L;
            if (showStatusHere) {
                GuiRenderUtils.drawText(extractor, font,
                        GuiRenderUtils.truncate(font, statusMessage, inputW - 14),
                        inputX + 8, inputY + 7, statusColor);
            } else {
                GuiRenderUtils.drawText(extractor, font,
                        GuiRenderUtils.truncate(font, "New profile name (e.g. Survival, PvP)...", inputW - 14),
                        inputX + 8, inputY + 7, 0xFF64748B);
            }
        } else {
            GuiRenderUtils.drawText(extractor, font, newProfileName + cursor,
                    inputX + 8, inputY + 7, 0xFFF8FAFC);
        }

        // "+ Copy Current" button
        boolean copyHover = isHovered(mouseX, mouseY, btnCopyX, btnY, btnCopyW, btnH);
        GuiRenderUtils.fillRoundedRect(extractor, btnCopyX, btnY, btnCopyW, btnH, 5,
                copyHover ? accent : accentDark);
        GuiRenderUtils.drawRoundedOutline(extractor, btnCopyX, btnY, btnCopyW, btnH, 5, accent);
        GuiRenderUtils.drawCenteredText(extractor, font, "+ Copy Current",
                btnCopyX + btnCopyW / 2, btnY + 7, 0xFFFFFFFF);

        // "+ Fresh" button
        boolean blankHover = isHovered(mouseX, mouseY, btnBlankX, btnY, btnBlankW, btnH);
        GuiRenderUtils.fillRoundedRect(extractor, btnBlankX, btnY, btnBlankW, btnH, 5,
                blankHover ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, btnBlankX, btnY, btnBlankW, btnH, 5,
                blankHover ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, font, "+ Fresh",
                btnBlankX + btnBlankW / 2, btnY + 7, blankHover ? 0xFFFFFFFF : 0xFFCBD5E1);

        // "📥 Import" button
        boolean importHover = isHovered(mouseX, mouseY, btnImportX, btnY, btnImportW, btnH);
        GuiRenderUtils.fillRoundedRect(extractor, btnImportX, btnY, btnImportW, btnH, 5,
                importHover ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, btnImportX, btnY, btnImportW, btnH, 5,
                importHover ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, font, "📥 Import",
                btnImportX + btnImportW / 2, btnY + 7, importHover ? 0xFFFFFFFF : accent);

        curY += CREATE_BAR_HEIGHT + 10;

        // 2. Profile Cards
        List<ClientProfile> profiles = pm.getProfiles();
        String activeId = pm.getActiveProfileId();

        for (ClientProfile profile : profiles) {
            int cardH = getCardHeight(profile);
            if (curY + cardH >= panelTop && curY <= panelBottom) {
                drawProfileCard(extractor, font, pm, profile, profile.getId().equals(activeId),
                        contentLeft, curY, contentWidth, cardH, mouseX, mouseY,
                        accent, accentDark, surface, surfaceHover, bgDark, border);
            }
            curY += cardH + CARD_GAP;
        }
    }

    private void drawProfileCard(GuiGraphicsExtractor extractor, Font font, ProfileManager pm,
                                 ClientProfile profile, boolean isActive,
                                 int x, int y, int width, int cardH, int mouseX, int mouseY,
                                 int accent, int accentDark, int surface, int surfaceHover, int bgDark, int border) {
        int cardBg = isActive ? GuiRenderUtils.lerpColor(surface, accentDark, 0.18F) : surface;
        int cardBorder = isActive ? accent : border;

        GuiRenderUtils.fillRoundedRect(extractor, x, y, width, cardH, 7, cardBg);
        GuiRenderUtils.drawRoundedOutline(extractor, x, y, width, cardH, 7, cardBorder);
        if (isActive) {
            GuiRenderUtils.fillRoundedRect(extractor, x + 2, y + 6, 3, CARD_BASE_HEIGHT - 12, 1, accent);
        }

        // Icon badge (click to cycle icon)
        int iconBoxX = x + 10;
        int iconBoxY = y + 9;
        int iconBoxS = 34;
        boolean iconHover = isHovered(mouseX, mouseY, iconBoxX, iconBoxY, iconBoxS, iconBoxS);
        GuiRenderUtils.fillRoundedRect(extractor, iconBoxX, iconBoxY, iconBoxS, iconBoxS, 6,
                iconHover ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, iconBoxX, iconBoxY, iconBoxS, iconBoxS, 6,
                iconHover ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, font, profile.getIcon(),
                iconBoxX + iconBoxS / 2, iconBoxY + 13, 0xFFFFFFFF);

        int textLeft = iconBoxX + iconBoxS + 10;
        int topRowY = y + 9;

        // Profile Name or Inline Rename Field
        boolean isRenamingThis = (focusedInput == FocusedInput.RENAME_PROFILE && profile.getId().equals(renamingProfileId));
        if (isRenamingThis) {
            int renW = 130;
            int renH = 16;
            GuiRenderUtils.fillRoundedRect(extractor, textLeft, topRowY - 2, renW, renH, 4, bgDark);
            GuiRenderUtils.drawRoundedOutline(extractor, textLeft, topRowY - 2, renW, renH, 4, accent);
            String cursor = ((System.currentTimeMillis() / 500) % 2 == 0) ? "|" : "";
            GuiRenderUtils.drawText(extractor, font, renameBuffer + cursor, textLeft + 5, topRowY + 2, 0xFFF8FAFC);

            int okX = textLeft + renW + 4;
            boolean okHover = isHovered(mouseX, mouseY, okX, topRowY - 2, 22, renH);
            GuiRenderUtils.fillRoundedRect(extractor, okX, topRowY - 2, 22, renH, 4, okHover ? accent : accentDark);
            GuiRenderUtils.drawCenteredText(extractor, font, "✓", okX + 11, topRowY + 2, 0xFFFFFFFF);
        } else {
            GuiRenderUtils.drawText(extractor, font, profile.getName(), textLeft, topRowY, 0xFFF8FAFC, true);
            int badgeX = textLeft + GuiRenderUtils.textWidth(font, profile.getName()) + 8;

            if (isActive) {
                String actLabel = "● ACTIVE";
                int actW = GuiRenderUtils.textWidth(font, actLabel) + 10;
                GuiRenderUtils.fillRoundedRect(extractor, badgeX, topRowY - 1, actW, 11, 4,
                        GuiRenderUtils.withAlpha(accent, 45));
                GuiRenderUtils.drawRoundedOutline(extractor, badgeX, topRowY - 1, actW, 11, 4, accent);
                GuiRenderUtils.drawCenteredText(extractor, font, actLabel, badgeX + actW / 2, topRowY + 1, accent);
                badgeX += actW + 6;
            }

            // Linked Worlds/Servers summary pills on top row
            int wCount = profile.getLinkedWorlds().size();
            int sCount = profile.getLinkedServers().size();
            if (wCount > 0 || sCount > 0) {
                String linkBadge = (wCount > 0 ? "🌍 " + wCount : "")
                        + (wCount > 0 && sCount > 0 ? " • " : "")
                        + (sCount > 0 ? "🖥 " + sCount : "");
                int lbW = GuiRenderUtils.textWidth(font, linkBadge) + 10;
                GuiRenderUtils.fillRoundedRect(extractor, badgeX, topRowY - 1, lbW, 11, 4, 0x3334D399);
                GuiRenderUtils.drawCenteredText(extractor, font, linkBadge, badgeX + lbW / 2, topRowY + 1, 0xFF34D399);
            }
        }

        // Subtitle row: modules enabled + equipped cape + linked details
        int modCount = profile.getEnabledModuleCount(isActive);
        String capeName = profile.getEquippedCapeSummary(isActive);
        StringBuilder sub = new StringBuilder();
        sub.append(modCount).append(modCount == 1 ? " module" : " modules");
        sub.append(" • Cape: ").append(capeName);
        if (!profile.getLinkedServers().isEmpty()) {
            sub.append(" • 🖥 ").append(String.join(", ", profile.getLinkedServers()));
        } else if (!profile.getLinkedWorlds().isEmpty()) {
            sub.append(" • 🌍 ").append(String.join(", ", profile.getLinkedWorlds()));
        }
        String subStr = GuiRenderUtils.truncateWithEllipsis(font, sub.toString(), Math.max(60, width - 340));
        GuiRenderUtils.drawText(extractor, font, subStr, textLeft, y + 27, 0xFF94A3B8);

        // Action Buttons on right side of card
        int btnH = 20;
        int btnY = y + (CARD_BASE_HEIGHT - btnH) / 2;
        int rightEdge = x + width - 8;

        // 1. Delete button (🗑)
        boolean canDelete = pm.getProfiles().size() > 1;
        int delW = 20;
        int delX = rightEdge - delW;
        boolean delHover = canDelete && isHovered(mouseX, mouseY, delX, btnY, delW, btnH);
        GuiRenderUtils.fillRoundedRect(extractor, delX, btnY, delW, btnH, 5,
                delHover ? 0x44EF4444 : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, delX, btnY, delW, btnH, 5,
                delHover ? 0xFFEF4444 : border);
        GuiRenderUtils.drawCenteredText(extractor, font, "🗑", delX + delW / 2, btnY + 6,
                !canDelete ? 0xFF475569 : (delHover ? 0xFFF87171 : 0xFF94A3B8));
        rightEdge = delX - 3;

        // 2. Export to Clipboard button (📤)
        int expW = 20;
        int expX = rightEdge - expW;
        boolean expHover = isHovered(mouseX, mouseY, expX, btnY, expW, btnH);
        GuiRenderUtils.fillRoundedRect(extractor, expX, btnY, expW, btnH, 5,
                expHover ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, expX, btnY, expW, btnH, 5,
                expHover ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, font, "📤", expX + expW / 2, btnY + 6,
                expHover ? accent : 0xFF94A3B8);
        rightEdge = expX - 3;

        // 3. Duplicate button (📋)
        int dupW = 20;
        int dupX = rightEdge - dupW;
        boolean dupHover = isHovered(mouseX, mouseY, dupX, btnY, dupW, btnH);
        GuiRenderUtils.fillRoundedRect(extractor, dupX, btnY, dupW, btnH, 5,
                dupHover ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, dupX, btnY, dupW, btnH, 5,
                dupHover ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, font, "📋", dupX + dupW / 2, btnY + 6,
                dupHover ? 0xFFFFFFFF : 0xFF94A3B8);
        rightEdge = dupX - 3;

        // 4. Rename button (✏)
        int renBtnW = 20;
        int renBtnX = rightEdge - renBtnW;
        boolean renHover = isHovered(mouseX, mouseY, renBtnX, btnY, renBtnW, btnH);
        GuiRenderUtils.fillRoundedRect(extractor, renBtnX, btnY, renBtnW, btnH, 5,
                renHover ? surfaceHover : bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, renBtnX, btnY, renBtnW, btnH, 5,
                renHover ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, font, "✏", renBtnX + renBtnW / 2, btnY + 6,
                renHover ? 0xFFFFFFFF : 0xFF94A3B8);
        rightEdge = renBtnX - 3;

        // 5. Per-Profile Hotkey button (⌨ Key)
        boolean isBindingThis = (focusedInput == FocusedInput.BIND_PROFILE_KEY && profile.getId().equals(bindingProfileId));
        String keyLabel = isBindingThis
                ? "Key..."
                : "⌨ " + formatKey(profile.getKeyBind());
        int keyBtnW = Math.max(46, GuiRenderUtils.textWidth(font, keyLabel) + 10);
        int keyBtnX = rightEdge - keyBtnW;
        boolean keyHover = isHovered(mouseX, mouseY, keyBtnX, btnY, keyBtnW, btnH);
        GuiRenderUtils.fillRoundedRect(extractor, keyBtnX, btnY, keyBtnW, btnH, 5,
                isBindingThis ? accentDark : (keyHover ? surfaceHover : bgDark));
        GuiRenderUtils.drawRoundedOutline(extractor, keyBtnX, btnY, keyBtnW, btnH, 5,
                isBindingThis ? 0xFFFACC15 : (keyHover ? accent : border));
        GuiRenderUtils.drawCenteredText(extractor, font, keyLabel, keyBtnX + keyBtnW / 2, btnY + 6,
                isBindingThis ? 0xFFFACC15 : (profile.getKeyBind() == GLFW.GLFW_KEY_UNKNOWN ? 0xFF64748B : accent));
        rightEdge = keyBtnX - 3;

        // 6. Link Worlds / Servers button (🔗 Links)
        boolean linkExpanded = profile.getId().equals(expandedLinkProfileId);
        int linkBtnW = 54;
        int linkBtnX = rightEdge - linkBtnW;
        boolean linkHover = isHovered(mouseX, mouseY, linkBtnX, btnY, linkBtnW, btnH);
        GuiRenderUtils.fillRoundedRect(extractor, linkBtnX, btnY, linkBtnW, btnH, 5,
                linkExpanded ? accentDark : (linkHover ? surfaceHover : bgDark));
        GuiRenderUtils.drawRoundedOutline(extractor, linkBtnX, btnY, linkBtnW, btnH, 5,
                linkExpanded || linkHover ? accent : border);
        GuiRenderUtils.drawCenteredText(extractor, font, "🔗 Links", linkBtnX + linkBtnW / 2, btnY + 6,
                linkExpanded || linkHover ? 0xFFFFFFFF : 0xFFCBD5E1);
        rightEdge = linkBtnX - 4;

        // 7. Activate / Active button
        int actBtnW = 58;
        int actBtnX = rightEdge - actBtnW;
        boolean actHover = !isActive && isHovered(mouseX, mouseY, actBtnX, btnY, actBtnW, btnH);
        GuiRenderUtils.fillRoundedRect(extractor, actBtnX, btnY, actBtnW, btnH, 5,
                isActive ? GuiRenderUtils.withAlpha(accent, 50) : (actHover ? accent : accentDark));
        GuiRenderUtils.drawRoundedOutline(extractor, actBtnX, btnY, actBtnW, btnH, 5, accent);
        GuiRenderUtils.drawCenteredText(extractor, font, isActive ? "Active" : "Activate",
                actBtnX + actBtnW / 2, btnY + 6, 0xFFFFFFFF);

        // Expanded World & Server Linker Drawer inside card
        if (linkExpanded) {
            drawExpandedLinkPanel(extractor, font, pm, profile, x + 8, y + CARD_BASE_HEIGHT, width - 16,
                    cardH - CARD_BASE_HEIGHT - 6, mouseX, mouseY, accent, accentDark, surfaceHover, bgDark, border);
        }
    }

    private void drawExpandedLinkPanel(GuiGraphicsExtractor extractor, Font font, ProfileManager pm,
                                       ClientProfile profile, int px, int py, int pw, int ph,
                                       int mouseX, int mouseY, int accent, int accentDark,
                                       int surfaceHover, int bgDark, int border) {
        extractor.fill(px, py, px + pw, py + 1, border);
        int curY = py + 6;

        String curWorld = ProfileManager.getCurrentWorldName();
        String curServer = ProfileManager.getCurrentServerIp();
        if (curWorld != null || curServer != null) {
            boolean isWorld = (curWorld != null);
            String target = isWorld ? curWorld : curServer;
            boolean alreadyLinked = isWorld ? profile.hasWorldLinked(target) : profile.hasServerLinked(target);
            String btnText = alreadyLinked
                    ? "Linked to Current " + (isWorld ? "World: " : "Server: ") + target + " (Click to Unlink)"
                    : "+ Link Current " + (isWorld ? "World: " : "Server: ") + target;
            int qH = 18;
            boolean qHover = isHovered(mouseX, mouseY, px, curY, pw, qH);
            GuiRenderUtils.fillRoundedRect(extractor, px, curY, pw, qH, 4,
                    alreadyLinked ? 0x3334D399 : (qHover ? accentDark : bgDark));
            GuiRenderUtils.drawRoundedOutline(extractor, px, curY, pw, qH, 4,
                    alreadyLinked ? 0xFF34D399 : (qHover ? accent : border));
            GuiRenderUtils.drawCenteredText(extractor, font, btnText, px + pw / 2, curY + 5,
                    alreadyLinked ? 0xFF34D399 : 0xFFFFFFFF);
            curY += qH + 6;
        }

        int colGap = 10;
        int colW = (pw - colGap) / 2;
        int leftX = px;
        int rightX = px + colW + colGap;

        GuiRenderUtils.drawText(extractor, font, "🌍 Auto-Select on Singleplayer Worlds", leftX, curY, 0xFFE2E8F0, true);
        GuiRenderUtils.drawText(extractor, font, "🖥 Auto-Select on Multiplayer Servers", rightX, curY, 0xFFE2E8F0, true);
        curY += 13;

        int inpH = 18;
        int addBtnW = 38;
        int fieldW = colW - addBtnW - 4;

        boolean wFocused = (focusedInput == FocusedInput.CUSTOM_WORLD);
        GuiRenderUtils.fillRoundedRect(extractor, leftX, curY, fieldW, inpH, 4, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, leftX, curY, fieldW, inpH, 4, wFocused ? accent : border);
        String wCursor = (wFocused && (System.currentTimeMillis() / 500) % 2 == 0) ? "|" : "";
        if (customWorldInput.isEmpty() && !wFocused) {
            GuiRenderUtils.drawText(extractor, font, "World name...", leftX + 6, curY + 5, 0xFF64748B);
        } else {
            GuiRenderUtils.drawText(extractor, font, customWorldInput + wCursor, leftX + 6, curY + 5, 0xFFF8FAFC);
        }
        int wAddX = leftX + fieldW + 4;
        boolean wAddHover = isHovered(mouseX, mouseY, wAddX, curY, addBtnW, inpH);
        GuiRenderUtils.fillRoundedRect(extractor, wAddX, curY, addBtnW, inpH, 4, wAddHover ? accent : accentDark);
        GuiRenderUtils.drawCenteredText(extractor, font, "+ Link", wAddX + addBtnW / 2, curY + 5, 0xFFFFFFFF);

        boolean sFocused = (focusedInput == FocusedInput.CUSTOM_SERVER);
        GuiRenderUtils.fillRoundedRect(extractor, rightX, curY, fieldW, inpH, 4, bgDark);
        GuiRenderUtils.drawRoundedOutline(extractor, rightX, curY, fieldW, inpH, 4, sFocused ? accent : border);
        String sCursor = (sFocused && (System.currentTimeMillis() / 500) % 2 == 0) ? "|" : "";
        if (customServerInput.isEmpty() && !sFocused) {
            GuiRenderUtils.drawText(extractor, font, "Server IP (e.g. mc.hypixel.net)...", rightX + 6, curY + 5, 0xFF64748B);
        } else {
            GuiRenderUtils.drawText(extractor, font, customServerInput + sCursor, rightX + 6, curY + 5, 0xFFF8FAFC);
        }
        int sAddX = rightX + fieldW + 4;
        boolean sAddHover = isHovered(mouseX, mouseY, sAddX, curY, addBtnW, inpH);
        GuiRenderUtils.fillRoundedRect(extractor, sAddX, curY, addBtnW, inpH, 4, sAddHover ? accent : accentDark);
        GuiRenderUtils.drawCenteredText(extractor, font, "+ Link", sAddX + addBtnW / 2, curY + 5, 0xFFFFFFFF);

        curY += inpH + 6;

        List<String> worlds = pm.getDetectedSingleplayerWorlds();
        int pillH = 16;
        int pillGap = 4;
        int subColW = (colW - pillGap) / 2;

        if (worlds.isEmpty()) {
            GuiRenderUtils.drawText(extractor, font, "No saved worlds found — type one above.", leftX, curY + 4, 0xFF64748B);
        } else {
            int maxWorlds = Math.min(10, worlds.size());
            for (int i = 0; i < maxWorlds; i++) {
                String wName = worlds.get(i);
                int c = i % 2;
                int r = i / 2;
                int wx = leftX + c * (subColW + pillGap);
                int wy = curY + r * (pillH + pillGap);
                boolean linkedHere = profile.hasWorldLinked(wName);
                ClientProfile owner = pm.findProfileLinkedToWorld(wName);
                boolean linkedOther = (owner != null && !owner.getId().equals(profile.getId()));
                boolean hover = isHovered(mouseX, mouseY, wx, wy, subColW, pillH);

                int pBg = linkedHere ? 0x3334D399 : (hover ? surfaceHover : bgDark);
                int pBorder = linkedHere ? 0xFF34D399 : (linkedOther ? 0x88FACC15 : (hover ? accent : border));
                GuiRenderUtils.fillRoundedRect(extractor, wx, wy, subColW, pillH, 4, pBg);
                GuiRenderUtils.drawRoundedOutline(extractor, wx, wy, subColW, pillH, 4, pBorder);

                String prefix = linkedHere ? "✓ " : (linkedOther ? "⇄ " : "+ ");
                String label = GuiRenderUtils.truncateWithEllipsis(font, prefix + wName, subColW - 8);
                int fg = linkedHere ? 0xFF34D399 : (linkedOther ? 0xFFFACC15 : 0xFFCBD5E1);
                GuiRenderUtils.drawText(extractor, font, label, wx + 5, wy + 4, fg);
            }
        }

        List<ProfileManager.SavedServerInfo> servers = pm.getDetectedMultiplayerServers();
        if (servers.isEmpty()) {
            GuiRenderUtils.drawText(extractor, font, "No saved servers — type an IP above.", rightX, curY + 4, 0xFF64748B);
        } else {
            int maxServers = Math.min(10, servers.size());
            for (int i = 0; i < maxServers; i++) {
                ProfileManager.SavedServerInfo srv = servers.get(i);
                int c = i % 2;
                int r = i / 2;
                int sx = rightX + c * (subColW + pillGap);
                int sy = curY + r * (pillH + pillGap);
                boolean linkedHere = profile.hasServerLinked(srv.ip());
                ClientProfile owner = pm.findProfileLinkedToServer(srv.ip());
                boolean linkedOther = (owner != null && !owner.getId().equals(profile.getId()));
                boolean hover = isHovered(mouseX, mouseY, sx, sy, subColW, pillH);

                int pBg = linkedHere ? 0x3334D399 : (hover ? surfaceHover : bgDark);
                int pBorder = linkedHere ? 0xFF34D399 : (linkedOther ? 0x88FACC15 : (hover ? accent : border));
                GuiRenderUtils.fillRoundedRect(extractor, sx, sy, subColW, pillH, 4, pBg);
                GuiRenderUtils.drawRoundedOutline(extractor, sx, sy, subColW, pillH, 4, pBorder);

                String prefix = linkedHere ? "✓ " : (linkedOther ? "⇄ " : "+ ");
                String label = GuiRenderUtils.truncateWithEllipsis(font, prefix + srv.ip(), subColW - 8);
                int fg = linkedHere ? 0xFF34D399 : (linkedOther ? 0xFFFACC15 : 0xFFCBD5E1);
                GuiRenderUtils.drawText(extractor, font, label, sx + 5, sy + 4, fg);
            }
        }
    }

    public boolean mouseClicked(Font font, int contentLeft, int contentWidth, int panelTop,
                                double scroll, double mouseX, double mouseY, int button) {
        ProfileManager pm = StarClient.getInstance().getProfileManager();
        if (pm == null) return false;

        int curY = (int) Math.round(panelTop - scroll);

        // 1. Check Create Bar
        int btnImportW = 66;
        int btnBlankW = 76;
        int btnCopyW = 94;
        int btnH = 22;
        int btnY = curY + (CREATE_BAR_HEIGHT - btnH) / 2;
        int btnImportX = contentLeft + contentWidth - 8 - btnImportW;
        int btnBlankX = btnImportX - 5 - btnBlankW;
        int btnCopyX = btnBlankX - 5 - btnCopyW;
        int inputX = contentLeft + 8;
        int inputW = Math.max(90, btnCopyX - inputX - 6);

        if (isHovered(mouseX, mouseY, inputX, btnY, inputW, btnH)) {
            focusedInput = FocusedInput.NEW_PROFILE_NAME;
            renamingProfileId = null;
            bindingProfileId = null;
            return true;
        }
        if (isHovered(mouseX, mouseY, btnCopyX, btnY, btnCopyW, btnH)) {
            String name = newProfileName.trim().isEmpty() ? "Custom Profile" : newProfileName.trim();
            ClientProfile created = pm.createProfile(name, null, true);
            newProfileName = "";
            focusedInput = FocusedInput.NONE;
            showStatus("Created profile '" + created.getName() + "'", 0xFF34D399);
            return true;
        }
        if (isHovered(mouseX, mouseY, btnBlankX, btnY, btnBlankW, btnH)) {
            String name = newProfileName.trim().isEmpty() ? "Fresh Profile" : newProfileName.trim();
            ClientProfile created = pm.createProfile(name, null, false);
            newProfileName = "";
            focusedInput = FocusedInput.NONE;
            showStatus("Created fresh profile '" + created.getName() + "'", 0xFF34D399);
            return true;
        }
        if (isHovered(mouseX, mouseY, btnImportX, btnY, btnImportW, btnH)) {
            ClientProfile imported = pm.importProfileFromClipboard();
            focusedInput = FocusedInput.NONE;
            if (imported != null) {
                showStatus("Imported profile '" + imported.getName() + "' from clipboard!", 0xFF34D399);
            } else {
                showStatus("⚠ Clipboard does not contain a valid STAR-PROFILE code.", 0xFFF59E0B);
            }
            return true;
        }

        curY += CREATE_BAR_HEIGHT + 10;

        // 2. Check Profile Cards
        List<ClientProfile> profiles = pm.getProfiles();
        for (ClientProfile profile : profiles) {
            int cardH = getCardHeight(profile);
            if (isHovered(mouseX, mouseY, contentLeft, curY, contentWidth, cardH)) {
                return handleCardClick(font, pm, profile, contentLeft, curY, contentWidth, cardH, mouseX, mouseY, button);
            }
            curY += cardH + CARD_GAP;
        }

        clearFocus();
        return false;
    }

    private boolean handleCardClick(Font font, ProfileManager pm, ClientProfile profile,
                                    int x, int y, int width, int cardH, double mouseX, double mouseY, int button) {
        int iconBoxX = x + 10;
        int iconBoxY = y + 9;
        int iconBoxS = 34;
        if (isHovered(mouseX, mouseY, iconBoxX, iconBoxY, iconBoxS, iconBoxS)) {
            pm.cycleProfileIcon(profile.getId());
            return true;
        }

        int textLeft = iconBoxX + iconBoxS + 10;
        int topRowY = y + 9;
        if (focusedInput == FocusedInput.RENAME_PROFILE && profile.getId().equals(renamingProfileId)) {
            int okX = textLeft + 134;
            if (isHovered(mouseX, mouseY, okX, topRowY - 2, 22, 16)) {
                if (!renameBuffer.trim().isEmpty()) {
                    pm.renameProfile(profile.getId(), renameBuffer.trim());
                }
                clearFocus();
                return true;
            }
            if (isHovered(mouseX, mouseY, textLeft, topRowY - 2, 130, 16)) {
                return true;
            }
        }

        int btnH = 20;
        int btnY = y + (CARD_BASE_HEIGHT - btnH) / 2;
        int rightEdge = x + width - 8;

        // 1. Delete button
        int delW = 20;
        int delX = rightEdge - delW;
        if (isHovered(mouseX, mouseY, delX, btnY, delW, btnH)) {
            if (pm.getProfiles().size() > 1) {
                if (profile.getId().equals(expandedLinkProfileId)) {
                    expandedLinkProfileId = null;
                }
                pm.deleteProfile(profile.getId());
                showStatus("Deleted profile '" + profile.getName() + "'", 0xFFF87171);
            }
            return true;
        }
        rightEdge = delX - 3;

        // 2. Export button (📤)
        int expW = 20;
        int expX = rightEdge - expW;
        if (isHovered(mouseX, mouseY, expX, btnY, expW, btnH)) {
            if (pm.exportProfileToClipboard(profile.getId())) {
                showStatus("Copied '" + profile.getName() + "' export code to clipboard!", 0xFF34D399);
            } else {
                showStatus("⚠ Failed to export profile code.", 0xFFF87171);
            }
            return true;
        }
        rightEdge = expX - 3;

        // 3. Duplicate button (📋)
        int dupW = 20;
        int dupX = rightEdge - dupW;
        if (isHovered(mouseX, mouseY, dupX, btnY, dupW, btnH)) {
            ClientProfile copy = pm.duplicateProfile(profile.getId());
            showStatus("Duplicated as '" + copy.getName() + "'", 0xFF34D399);
            return true;
        }
        rightEdge = dupX - 3;

        // 4. Rename button (✏)
        int renBtnW = 20;
        int renBtnX = rightEdge - renBtnW;
        if (isHovered(mouseX, mouseY, renBtnX, btnY, renBtnW, btnH)) {
            if (focusedInput == FocusedInput.RENAME_PROFILE && profile.getId().equals(renamingProfileId)) {
                if (!renameBuffer.trim().isEmpty()) {
                    pm.renameProfile(profile.getId(), renameBuffer.trim());
                }
                clearFocus();
            } else {
                focusedInput = FocusedInput.RENAME_PROFILE;
                renamingProfileId = profile.getId();
                bindingProfileId = null;
                renameBuffer = profile.getName();
            }
            return true;
        }
        rightEdge = renBtnX - 3;

        // 5. Per-Profile Hotkey button (⌨ Key)
        boolean isBindingThis = (focusedInput == FocusedInput.BIND_PROFILE_KEY && profile.getId().equals(bindingProfileId));
        String keyLabel = isBindingThis
                ? "Key..."
                : "⌨ " + formatKey(profile.getKeyBind());
        int keyBtnW = Math.max(46, GuiRenderUtils.textWidth(font, keyLabel) + 10);
        int keyBtnX = rightEdge - keyBtnW;
        if (isHovered(mouseX, mouseY, keyBtnX, btnY, keyBtnW, btnH)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                pm.setProfileKeyBind(profile.getId(), GLFW.GLFW_KEY_UNKNOWN);
                clearFocus();
                showStatus("Cleared hotkey for '" + profile.getName() + "'", 0xFF94A3B8);
            } else if (isBindingThis) {
                clearFocus();
            } else {
                focusedInput = FocusedInput.BIND_PROFILE_KEY;
                bindingProfileId = profile.getId();
                renamingProfileId = null;
            }
            return true;
        }
        rightEdge = keyBtnX - 3;

        // 6. Link Worlds / Servers button (🔗 Links)
        int linkBtnW = 54;
        int linkBtnX = rightEdge - linkBtnW;
        if (isHovered(mouseX, mouseY, linkBtnX, btnY, linkBtnW, btnH)) {
            if (profile.getId().equals(expandedLinkProfileId)) {
                expandedLinkProfileId = null;
                if (focusedInput == FocusedInput.CUSTOM_WORLD || focusedInput == FocusedInput.CUSTOM_SERVER) {
                    focusedInput = FocusedInput.NONE;
                }
            } else {
                expandedLinkProfileId = profile.getId();
            }
            return true;
        }
        rightEdge = linkBtnX - 4;

        // 7. Activate button
        int actBtnW = 58;
        int actBtnX = rightEdge - actBtnW;
        if (isHovered(mouseX, mouseY, actBtnX, btnY, actBtnW, btnH)) {
            pm.switchProfile(profile.getId());
            showStatus("Switched to profile '" + profile.getName() + "'", 0xFF34D399);
            return true;
        }

        // Expanded Link Panel clicks
        if (profile.getId().equals(expandedLinkProfileId) && mouseY >= y + CARD_BASE_HEIGHT) {
            int px = x + 8;
            int py = y + CARD_BASE_HEIGHT;
            int pw = width - 16;
            int curY = py + 6;

            String curWorld = ProfileManager.getCurrentWorldName();
            String curServer = ProfileManager.getCurrentServerIp();
            if (curWorld != null || curServer != null) {
                int qH = 18;
                if (isHovered(mouseX, mouseY, px, curY, pw, qH)) {
                    if (curWorld != null) {
                        pm.toggleWorldLink(profile.getId(), curWorld);
                    } else {
                        pm.toggleServerLink(profile.getId(), curServer);
                    }
                    return true;
                }
                curY += qH + 6;
            }

            curY += 13;
            int colGap = 10;
            int colW = (pw - colGap) / 2;
            int leftX = px;
            int rightX = px + colW + colGap;
            int inpH = 18;
            int addBtnW = 38;
            int fieldW = colW - addBtnW - 4;

            if (isHovered(mouseX, mouseY, leftX, curY, fieldW, inpH)) {
                focusedInput = FocusedInput.CUSTOM_WORLD;
                return true;
            }
            if (isHovered(mouseX, mouseY, leftX + fieldW + 4, curY, addBtnW, inpH)) {
                if (!customWorldInput.trim().isEmpty()) {
                    pm.toggleWorldLink(profile.getId(), customWorldInput.trim());
                    customWorldInput = "";
                }
                return true;
            }

            if (isHovered(mouseX, mouseY, rightX, curY, fieldW, inpH)) {
                focusedInput = FocusedInput.CUSTOM_SERVER;
                return true;
            }
            if (isHovered(mouseX, mouseY, rightX + fieldW + 4, curY, addBtnW, inpH)) {
                if (!customServerInput.trim().isEmpty()) {
                    pm.toggleServerLink(profile.getId(), customServerInput.trim());
                    customServerInput = "";
                }
                return true;
            }

            curY += inpH + 6;
            int pillH = 16;
            int pillGap = 4;
            int subColW = (colW - pillGap) / 2;

            List<String> worlds = pm.getDetectedSingleplayerWorlds();
            int maxWorlds = Math.min(10, worlds.size());
            for (int i = 0; i < maxWorlds; i++) {
                int c = i % 2;
                int r = i / 2;
                int wx = leftX + c * (subColW + pillGap);
                int wy = curY + r * (pillH + pillGap);
                if (isHovered(mouseX, mouseY, wx, wy, subColW, pillH)) {
                    pm.toggleWorldLink(profile.getId(), worlds.get(i));
                    return true;
                }
            }

            List<ProfileManager.SavedServerInfo> servers = pm.getDetectedMultiplayerServers();
            int maxServers = Math.min(10, servers.size());
            for (int i = 0; i < maxServers; i++) {
                int c = i % 2;
                int r = i / 2;
                int sx = rightX + c * (subColW + pillGap);
                int sy = curY + r * (pillH + pillGap);
                if (isHovered(mouseX, mouseY, sx, sy, subColW, pillH)) {
                    pm.toggleServerLink(profile.getId(), servers.get(i).ip());
                    return true;
                }
            }
            return true;
        }

        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        if (focusedInput == FocusedInput.NONE) return false;
        ProfileManager pm = StarClient.getInstance().getProfileManager();
        int key = event.key();

        if (focusedInput == FocusedInput.BIND_PROFILE_KEY) {
            if (bindingProfileId != null && pm != null) {
                if (key == GLFW.GLFW_KEY_ESCAPE) {
                    clearFocus();
                    return true;
                }
                if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                    pm.setProfileKeyBind(bindingProfileId, GLFW.GLFW_KEY_UNKNOWN);
                    showStatus("Cleared profile hotkey.", 0xFF94A3B8);
                } else {
                    pm.setProfileKeyBind(bindingProfileId, key);
                    showStatus("Bound profile hotkey to " + formatKey(key), 0xFF34D399);
                }
            }
            clearFocus();
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            clearFocus();
            return true;
        }

        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (pm != null) {
                switch (focusedInput) {
                    case NEW_PROFILE_NAME -> {
                        if (!newProfileName.trim().isEmpty()) {
                            pm.createProfile(newProfileName.trim(), null, true);
                            newProfileName = "";
                        }
                    }
                    case RENAME_PROFILE -> {
                        if (renamingProfileId != null && !renameBuffer.trim().isEmpty()) {
                            pm.renameProfile(renamingProfileId, renameBuffer.trim());
                        }
                    }
                    case CUSTOM_WORLD -> {
                        if (expandedLinkProfileId != null && !customWorldInput.trim().isEmpty()) {
                            pm.toggleWorldLink(expandedLinkProfileId, customWorldInput.trim());
                            customWorldInput = "";
                        }
                    }
                    case CUSTOM_SERVER -> {
                        if (expandedLinkProfileId != null && !customServerInput.trim().isEmpty()) {
                            pm.toggleServerLink(expandedLinkProfileId, customServerInput.trim());
                            customServerInput = "";
                        }
                    }
                    default -> {
                    }
                }
            }
            if (focusedInput == FocusedInput.NEW_PROFILE_NAME || focusedInput == FocusedInput.RENAME_PROFILE) {
                clearFocus();
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            switch (focusedInput) {
                case NEW_PROFILE_NAME -> {
                    if (!newProfileName.isEmpty()) {
                        newProfileName = newProfileName.substring(0, newProfileName.length() - 1);
                    }
                }
                case RENAME_PROFILE -> {
                    if (!renameBuffer.isEmpty()) {
                        renameBuffer = renameBuffer.substring(0, renameBuffer.length() - 1);
                    }
                }
                case CUSTOM_WORLD -> {
                    if (!customWorldInput.isEmpty()) {
                        customWorldInput = customWorldInput.substring(0, customWorldInput.length() - 1);
                    }
                }
                case CUSTOM_SERVER -> {
                    if (!customServerInput.isEmpty()) {
                        customServerInput = customServerInput.substring(0, customServerInput.length() - 1);
                    }
                }
                default -> {
                }
            }
            return true;
        }

        return true;
    }

    public boolean charTyped(CharacterEvent event) {
        if (focusedInput == FocusedInput.NONE || focusedInput == FocusedInput.BIND_PROFILE_KEY) return false;
        String str = event.codepointAsString();
        if (str.isEmpty()) return false;
        char c = str.charAt(0);
        if (c < 32 || c == 127) return false;

        switch (focusedInput) {
            case NEW_PROFILE_NAME -> {
                if (newProfileName.length() < 28) newProfileName += str;
            }
            case RENAME_PROFILE -> {
                if (renameBuffer.length() < 28) renameBuffer += str;
            }
            case CUSTOM_WORLD -> {
                if (customWorldInput.length() < 40) customWorldInput += str;
            }
            case CUSTOM_SERVER -> {
                if (customServerInput.length() < 48) customServerInput += str;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static String formatKey(int key) {
        if (key <= 0) return "None";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isBlank()) {
            return name.toUpperCase();
        }
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DOWN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RIGHT";
            case GLFW.GLFW_KEY_F1 -> "F1";
            case GLFW.GLFW_KEY_F2 -> "F2";
            case GLFW.GLFW_KEY_F3 -> "F3";
            case GLFW.GLFW_KEY_F4 -> "F4";
            case GLFW.GLFW_KEY_F5 -> "F5";
            case GLFW.GLFW_KEY_F6 -> "F6";
            case GLFW.GLFW_KEY_F7 -> "F7";
            case GLFW.GLFW_KEY_F8 -> "F8";
            case GLFW.GLFW_KEY_F9 -> "F9";
            case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11";
            case GLFW.GLFW_KEY_F12 -> "F12";
            default -> "K" + key;
        };
    }

    private static boolean isHovered(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
