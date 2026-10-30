package net.starclient.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import net.starclient.StarClient;
import net.starclient.account.AccountManager;
import net.starclient.account.MinecraftAccount;
import net.starclient.rank.Rank;
import net.starclient.rank.RankManager;

import java.util.List;

public final class AccountSwitcherOverlay {
    private static final int BOX_X = 8;
    private static final int BOX_Y = 8;
    private static final int BOX_H = 26;
    private static final int MIN_BOX_WIDTH = 152;
    private static final int ROW_HEIGHT = 24;
    private static final int BTN_HEIGHT = 20;

    private String cachedProfileName;
    private ResolvableProfile cachedResolvableProfile;
    private boolean dropdownOpen;
    private int lastHeaderWidth = MIN_BOX_WIDTH;

    public boolean isDropdownOpen() {
        return dropdownOpen;
    }

    public void closeDropdown() {
        this.dropdownOpen = false;
    }

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return;
        StarClient starClient = StarClient.getInstance();

        String playerName = client.getUser() != null && client.getUser().getName() != null
                ? client.getUser().getName()
                : "Player";
        RankManager rankManager = starClient != null ? starClient.getRankManager() : null;
        Rank rank = rankManager != null ? rankManager.getSelfRank() : Rank.USER;
        int rankColor = rank.getArgb();

        var cs = starClient != null ? starClient.getClientSettings() : null;
        int menuBg = cs != null ? (0xF2000000 | (cs.getMenuBackgroundColor() & 0x00FFFFFF)) : 0xF20F172A;
        int menuSurface = cs != null ? cs.getMenuSurfaceColor() : 0xFF162036;
        int menuSurfaceHover = cs != null ? cs.getMenuSurfaceHoverColor() : 0xFF1E293B;
        int menuBorder = cs != null ? cs.getMenuBorderColor() : 0xFF334155;
        int menuAccent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int menuAccentDark = cs != null ? cs.getMenuAccentDarkColor() : 0xFF0284C7;

        int headSize = 16;
        int padX = 7;
        int gap = 6;
        String arrow = dropdownOpen ? "▴" : "▾";
        int arrowW = client.font.width(arrow);
        int textWidth = client.font.width(playerName);
        int boxW = Math.max(MIN_BOX_WIDTH, padX + headSize + gap + textWidth + 8 + arrowW + 8);
        this.lastHeaderWidth = boxW;

        boolean headerHovered = hovered(BOX_X, BOX_Y, boxW, BOX_H, mouseX, mouseY);
        int headerBg = headerHovered || dropdownOpen ? menuSurfaceHover : menuBg;
        int headerBorder = headerHovered || dropdownOpen ? menuAccent : menuBorder;

        // Rounded card background, soft shadow & crisp outline
        GuiRenderUtils.drawDropShadow(graphics, BOX_X, BOX_Y, boxW, BOX_H, 6, 4, 90);
        GuiRenderUtils.fillRoundedRect(graphics, BOX_X, BOX_Y, boxW, BOX_H, 6, headerBg);
        GuiRenderUtils.drawRoundedOutline(graphics, BOX_X, BOX_Y, boxW, BOX_H, 6, headerBorder);

        // Player Head (face + outer hat layer)
        int headX = BOX_X + padX;
        int headY = BOX_Y + (BOX_H - headSize) / 2;
        Identifier skinTexture = resolveAccountSkin(client, starClient);
        drawHead(graphics, skinTexture, headX, headY, headSize);

        // Player Name in rank color + dropdown chevron
        int textX = headX + headSize + gap;
        int textY = BOX_Y + (BOX_H - client.font.lineHeight) / 2 + 1;
        graphics.text(client.font, playerName, textX, textY, rankColor);
        graphics.text(client.font, arrow, BOX_X + boxW - arrowW - 7, textY,
                headerHovered || dropdownOpen ? menuAccent : 0xFF94A3B8);

        if (!dropdownOpen || starClient == null || starClient.getAccountManager() == null) {
            return;
        }

        AccountManager accountManager = starClient.getAccountManager();
        List<MinecraftAccount> accounts = accountManager.getAccounts();
        int dropX = BOX_X;
        int dropY = BOX_Y + BOX_H + 4;
        int dropW = boxW;
        int listH = accounts.size() * ROW_HEIGHT + 4;
        int buttonsAreaH = 6 + BTN_HEIGHT + 4 + BTN_HEIGHT + 6;
        int dropH = listH + buttonsAreaH;

        // Rounded dropdown panel with soft drop shadow & border
        GuiRenderUtils.drawDropShadow(graphics, dropX, dropY, dropW, dropH, 6, 5, 110);
        GuiRenderUtils.fillRoundedRect(graphics, dropX, dropY, dropW, dropH, 6, menuBg);
        GuiRenderUtils.drawRoundedOutline(graphics, dropX, dropY, dropW, dropH, 6, menuBorder);

        int rowY = dropY + 3;
        for (MinecraftAccount acc : accounts) {
            boolean active = accountManager.isActive(acc);
            boolean rowHover = hovered(dropX + 4, rowY, dropW - 8, ROW_HEIGHT - 2, mouseX, mouseY);
            if (active || rowHover) {
                int rowBg = active
                        ? GuiRenderUtils.lerpColor(menuSurface, menuAccentDark, 0.22F)
                        : menuSurfaceHover;
                GuiRenderUtils.fillRoundedRect(graphics, dropX + 4, rowY, dropW - 8, ROW_HEIGHT - 2, 5, rowBg);
                if (active) {
                    GuiRenderUtils.drawRoundedOutline(graphics, dropX + 4, rowY, dropW - 8, ROW_HEIGHT - 2, 5,
                            GuiRenderUtils.withAlpha(menuAccent, 130));
                }
            }

            Rank accRank = rankManager != null
                    ? rankManager.getAccountRank(acc.getUuid(), acc.getUsername())
                    : Rank.USER;
            int accColor = accRank.getArgb();

            Identifier rowSkin = accountManager.getSkinTexture(acc);
            drawHead(graphics, rowSkin, dropX + 8, rowY + 4, 14);

            int nameY = rowY + (ROW_HEIGHT - 2 - client.font.lineHeight) / 2 + 1;
            int rightEdge = dropX + dropW - 8;

            if (active) {
                int dotW = client.font.width("●");
                graphics.text(client.font, "●", rightEdge - dotW - 2, nameY, 0xFF34D399);
                rightEdge -= dotW + 6;
            } else if (!acc.isInitialSession()) {
                int delW = 14;
                int delH = 14;
                int delX = rightEdge - delW;
                int delY = rowY + (ROW_HEIGHT - 2 - delH) / 2;
                boolean delHover = hovered(delX, delY, delW, delH, mouseX, mouseY);
                if (delHover) {
                    GuiRenderUtils.fillRoundedRect(graphics, delX, delY, delW, delH, 4, 0x33EF4444);
                }
                GuiRenderUtils.drawCenteredText(graphics, client.font, "✕", delX + delW / 2, delY + 3,
                        delHover ? 0xFFEF4444 : 0xFF64748B);
                rightEdge = delX - 3;
            }

            int maxNameW = Math.max(24, rightEdge - (dropX + 26) - 4);
            String displayName = acc.getUsername();
            if (client.font.width(displayName) > maxNameW) {
                displayName = client.font.plainSubstrByWidth(displayName, Math.max(8, maxNameW - 6)) + "..";
            }
            graphics.text(client.font, displayName, dropX + 26, nameY, accColor);

            rowY += ROW_HEIGHT;
        }

        // Divider line before buttons
        graphics.fill(dropX + 6, rowY + 1, dropX + dropW - 6, rowY + 2, menuBorder);

        int btnX = dropX + 6;
        int btnW = dropW - 12;
        int offlineBtnY = rowY + 5;
        int msBtnY = offlineBtnY + BTN_HEIGHT + 4;

        drawDropdownButton(graphics, client, "+ Offline Username", btnX, offlineBtnY, btnW, BTN_HEIGHT,
                menuSurface, menuSurfaceHover, menuBorder, mouseX, mouseY);
        drawDropdownButton(graphics, client, "+ Microsoft Account", btnX, msBtnY, btnW, BTN_HEIGHT,
                menuAccentDark, menuAccent, menuAccent, mouseX, mouseY);
    }

    public boolean mouseClicked(Screen parentScreen, MouseButtonEvent event) {
        if (event.button() != 0) {
            return false;
        }
        double mouseX = event.x();
        double mouseY = event.y();

        if (hovered(BOX_X, BOX_Y, this.lastHeaderWidth, BOX_H, mouseX, mouseY)) {
            this.dropdownOpen = !this.dropdownOpen;
            return true;
        }

        if (!this.dropdownOpen) {
            return false;
        }

        StarClient starClient = StarClient.getInstance();
        if (starClient == null || starClient.getAccountManager() == null) {
            this.dropdownOpen = false;
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        AccountManager accountManager = starClient.getAccountManager();
        List<MinecraftAccount> accounts = accountManager.getAccounts();

        int dropX = BOX_X;
        int dropY = BOX_Y + BOX_H + 4;
        int dropW = this.lastHeaderWidth;
        int listH = accounts.size() * ROW_HEIGHT + 4;
        int buttonsAreaH = 6 + BTN_HEIGHT + 4 + BTN_HEIGHT + 6;
        int dropH = listH + buttonsAreaH;

        if (!hovered(dropX, dropY, dropW, dropH, mouseX, mouseY)) {
            this.dropdownOpen = false;
            return false;
        }

        int rowY = dropY + 3;
        for (MinecraftAccount acc : accounts) {
            if (hovered(dropX + 4, rowY, dropW - 8, ROW_HEIGHT - 2, mouseX, mouseY)) {
                boolean active = accountManager.isActive(acc);
                if (!acc.isInitialSession() && !active && client.font != null) {
                    int delW = 14;
                    int delH = 14;
                    int delX = dropX + dropW - 8 - delW;
                    int delY = rowY + (ROW_HEIGHT - 2 - delH) / 2;
                    if (hovered(delX, delY, delW, delH, mouseX, mouseY)) {
                        this.dropdownOpen = false;
                        client.gui.setScreen(new ConfirmDeleteAccountScreen(parentScreen, acc));
                        return true;
                    }
                }
                accountManager.switchToAccount(acc);
                this.dropdownOpen = false;
                return true;
            }
            rowY += ROW_HEIGHT;
        }

        int btnX = dropX + 6;
        int btnW = dropW - 12;
        int offlineBtnY = rowY + 5;
        int msBtnY = offlineBtnY + BTN_HEIGHT + 4;

        if (hovered(btnX, offlineBtnY, btnW, BTN_HEIGHT, mouseX, mouseY)) {
            this.dropdownOpen = false;
            client.gui.setScreen(new OfflineAccountScreen(parentScreen));
            return true;
        }

        if (hovered(btnX, msBtnY, btnW, BTN_HEIGHT, mouseX, mouseY)) {
            this.dropdownOpen = false;
            client.gui.setScreen(new MicrosoftLoginScreen(parentScreen));
            return true;
        }

        return true;
    }

    private static void drawDropdownButton(
            GuiGraphicsExtractor graphics,
            Minecraft client,
            String label,
            int x,
            int y,
            int w,
            int h,
            int baseColor,
            int hoverColor,
            int borderColor,
            int mouseX,
            int mouseY
    ) {
        boolean hover = hovered(x, y, w, h, mouseX, mouseY);
        GuiRenderUtils.fillRoundedRect(graphics, x, y, w, h, 5, hover ? hoverColor : baseColor);
        GuiRenderUtils.drawRoundedOutline(graphics, x, y, w, h, 5, borderColor);
        int textX = x + (w - client.font.width(label)) / 2;
        int textY = y + (h - client.font.lineHeight) / 2 + 1;
        graphics.text(client.font, label, textX, textY, 0xFFFFFFFF);
    }

    private static void drawHead(GuiGraphicsExtractor graphics, Identifier skinTexture, int x, int y, int size) {
        graphics.blit(skinTexture, x, y, x + size, y + size,
                8 / 64.0F, 16 / 64.0F, 8 / 64.0F, 16 / 64.0F);
        graphics.blit(skinTexture, x, y, x + size, y + size,
                40 / 64.0F, 48 / 64.0F, 8 / 64.0F, 16 / 64.0F);
    }

    private static boolean hovered(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private Identifier resolveAccountSkin(Minecraft client, StarClient starClient) {
        GameProfile profile = client.getGameProfile();
        if (profile != null) {
            PlayerSkinRenderCache cache = client.playerSkinRenderCache();
            if (cache != null) {
                String profileName = profile.name() != null ? profile.name() : "";
                if (this.cachedResolvableProfile == null || !profileName.equals(this.cachedProfileName)) {
                    this.cachedProfileName = profileName;
                    boolean hasTextures = profile.properties() != null && profile.properties().containsKey("textures");
                    this.cachedResolvableProfile = (hasTextures || profileName.isBlank())
                            ? ResolvableProfile.createResolved(profile)
                            : ResolvableProfile.createUnresolved(profileName);
                }
                PlayerSkinRenderCache.RenderInfo info = cache.getOrDefault(this.cachedResolvableProfile);
                if (info != null && info.playerSkin() != null && info.playerSkin().body() != null) {
                    return info.playerSkin().body().texturePath();
                }
            }
            return DefaultPlayerSkin.get(profile).body().texturePath();
        }

        return DefaultPlayerSkin.getDefaultTexture();
    }
}
