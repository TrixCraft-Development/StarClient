package net.modernclient.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import net.modernclient.ModernClient;
import net.modernclient.account.AccountManager;
import net.modernclient.account.MinecraftAccount;
import net.modernclient.cosmetics.CapeCosmetic;
import net.modernclient.cosmetics.CosmeticManager;
import net.modernclient.rank.Rank;
import net.modernclient.rank.RankManager;

import java.util.List;

public final class AccountSwitcherOverlay {
    private static final int BOX_X = 8;
    private static final int BOX_Y = 8;
    private static final int BOX_H = 26;
    private static final int MIN_BOX_WIDTH = 148;
    private static final int ROW_HEIGHT = 22;
    private static final int BTN_HEIGHT = 20;

    private String cachedProfileName;
    private ResolvableProfile cachedResolvableProfile;
    private boolean dropdownOpen;
    private int lastHeaderWidth = MIN_BOX_WIDTH;

    public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return;
        ModernClient modernClient = ModernClient.getInstance();

        String playerName = client.getUser() != null && client.getUser().getName() != null
                ? client.getUser().getName()
                : "Player";
        RankManager rankManager = modernClient != null ? modernClient.getRankManager() : null;
        Rank rank = rankManager != null ? rankManager.getSelfRank() : Rank.USER;
        int rankColor = rank.getArgb();

        var cs = modernClient != null ? modernClient.getClientSettings() : null;
        int menuBg = cs != null ? cs.getMenuBackgroundColor() : 0xFF0F172A;
        int menuBgLight = cs != null ? cs.getMenuBackgroundLightColor() : 0xFF1E293B;
        int menuAccent = cs != null ? cs.getMenuAccentColor() : 0xFF38BDF8;
        int menuAccentDark = cs != null ? cs.getMenuAccentDarkColor() : 0xFF0284C7;

        int headSize = 18;
        int padX = 6;
        int gap = 6;
        String arrow = dropdownOpen ? "▲" : "▾";
        int arrowW = client.font.width(arrow);
        int textWidth = client.font.width(playerName);
        int boxW = Math.max(MIN_BOX_WIDTH, padX + headSize + gap + textWidth + 6 + arrowW + 8);
        this.lastHeaderWidth = boxW;

        boolean headerHovered = hovered(BOX_X, BOX_Y, boxW, BOX_H, mouseX, mouseY);
        int headerBg = headerHovered || dropdownOpen ? menuBgLight : menuBg;

        // Card background & subtle border
        graphics.fill(BOX_X, BOX_Y, BOX_X + boxW, BOX_Y + BOX_H, headerBg);
        graphics.fill(BOX_X, BOX_Y, BOX_X + boxW, BOX_Y + 1, 0xFF334155);
        graphics.fill(BOX_X, BOX_Y + BOX_H - 1, BOX_X + boxW, BOX_Y + BOX_H, 0xFF334155);
        graphics.fill(BOX_X, BOX_Y, BOX_X + 1, BOX_Y + BOX_H, 0xFF334155);
        graphics.fill(BOX_X + boxW - 1, BOX_Y, BOX_X + boxW, BOX_Y + BOX_H, 0xFF334155);

        // Player Head (face + outer hat layer)
        int headX = BOX_X + padX;
        int headY = BOX_Y + (BOX_H - headSize) / 2;
        Identifier skinTexture = resolveAccountSkin(client, modernClient);
        drawHead(graphics, skinTexture, headX, headY, headSize);

        // Player Name in rank color + dropdown chevron
        int textX = headX + headSize + gap;
        int textY = BOX_Y + (BOX_H - client.font.lineHeight) / 2 + 1;
        graphics.text(client.font, playerName, textX, textY, rankColor);
        graphics.text(client.font, arrow, BOX_X + boxW - arrowW - 6, textY, 0xFF94A3B8);

        if (!dropdownOpen || modernClient == null || modernClient.getAccountManager() == null) {
            return;
        }

        AccountManager accountManager = modernClient.getAccountManager();
        List<MinecraftAccount> accounts = accountManager.getAccounts();
        int dropX = BOX_X;
        int dropY = BOX_Y + BOX_H + 2;
        int dropW = boxW;
        int listH = accounts.size() * ROW_HEIGHT;
        int buttonsAreaH = 6 + BTN_HEIGHT + 4 + BTN_HEIGHT + 6;
        int dropH = listH + buttonsAreaH;

        // Dropdown panel background & border matching Client Menu Background
        graphics.fill(dropX, dropY, dropX + dropW, dropY + dropH, menuBg);
        graphics.fill(dropX, dropY, dropX + dropW, dropY + 1, 0xFF334155);
        graphics.fill(dropX, dropY + dropH - 1, dropX + dropW, dropY + dropH, 0xFF334155);
        graphics.fill(dropX, dropY, dropX + 1, dropY + dropH, 0xFF334155);
        graphics.fill(dropX + dropW - 1, dropY, dropX + dropW, dropY + dropH, 0xFF334155);

        int rowY = dropY;
        for (MinecraftAccount acc : accounts) {
            boolean active = accountManager.isActive(acc);
            boolean rowHover = hovered(dropX + 1, rowY, dropW - 2, ROW_HEIGHT, mouseX, mouseY);
            if (rowHover || active) {
                graphics.fill(dropX + 1, rowY, dropX + dropW - 1, rowY + ROW_HEIGHT, menuBgLight);
            }

            Rank accRank = rankManager != null
                    ? rankManager.getAccountRank(acc.getUuid(), acc.getUsername())
                    : Rank.USER;
            int accColor = accRank.getArgb();

            int statusLineColor = active ? 0xFF34D399 : 0xFF64748B;
            graphics.fill(dropX + 1, rowY + 2, dropX + 3, rowY + ROW_HEIGHT - 2, statusLineColor);

            Identifier rowSkin = accountManager.getSkinTexture(acc);
            drawHead(graphics, rowSkin, dropX + 6, rowY + 4, 14);

            int nameY = rowY + (ROW_HEIGHT - client.font.lineHeight) / 2 + 1;
            int rightEdge = dropX + dropW - 5;

            if (!acc.isInitialSession() && !active) {
                int delW = client.font.width("x") + 4;
                int delX = rightEdge - delW;
                boolean delHover = hovered(delX - 1, rowY + 3, delW + 2, ROW_HEIGHT - 6, mouseX, mouseY);
                graphics.text(client.font, "x", delX + 2, nameY, delHover ? 0xFFEF4444 : 0xFF64748B);
                rightEdge = delX - 2;
            }

            int maxNameW = Math.max(24, rightEdge - (dropX + 24) - 4);
            String displayName = acc.getUsername();
            if (client.font.width(displayName) > maxNameW) {
                displayName = client.font.plainSubstrByWidth(displayName, Math.max(8, maxNameW - 6)) + "..";
            }
            graphics.text(client.font, displayName, dropX + 24, nameY, accColor);

            rowY += ROW_HEIGHT;
        }

        // Divider line before buttons
        graphics.fill(dropX + 6, rowY + 2, dropX + dropW - 6, rowY + 3, 0xFF334155);

        int btnX = dropX + 6;
        int btnW = dropW - 12;
        int offlineBtnY = rowY + 6;
        int msBtnY = offlineBtnY + BTN_HEIGHT + 4;

        drawDropdownButton(graphics, client, "+ Offline Username", btnX, offlineBtnY, btnW, BTN_HEIGHT,
                menuBgLight, 0xFF475569, mouseX, mouseY);
        drawDropdownButton(graphics, client, "+ Microsoft Account", btnX, msBtnY, btnW, BTN_HEIGHT,
                menuAccent, menuAccentDark, mouseX, mouseY);
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

        ModernClient modernClient = ModernClient.getInstance();
        if (modernClient == null || modernClient.getAccountManager() == null) {
            this.dropdownOpen = false;
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        AccountManager accountManager = modernClient.getAccountManager();
        List<MinecraftAccount> accounts = accountManager.getAccounts();

        int dropX = BOX_X;
        int dropY = BOX_Y + BOX_H + 2;
        int dropW = this.lastHeaderWidth;
        int listH = accounts.size() * ROW_HEIGHT;
        int buttonsAreaH = 6 + BTN_HEIGHT + 4 + BTN_HEIGHT + 6;
        int dropH = listH + buttonsAreaH;

        if (!hovered(dropX, dropY, dropW, dropH, mouseX, mouseY)) {
            this.dropdownOpen = false;
            return false;
        }

        int rowY = dropY;
        for (MinecraftAccount acc : accounts) {
            if (hovered(dropX + 1, rowY, dropW - 2, ROW_HEIGHT, mouseX, mouseY)) {
                boolean active = accountManager.isActive(acc);
                if (!acc.isInitialSession() && !active && client.font != null) {
                    int delW = client.font.width("x") + 4;
                    int delX = dropX + dropW - 5 - delW;
                    if (hovered(delX - 1, rowY + 3, delW + 2, ROW_HEIGHT - 6, mouseX, mouseY)) {
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
        int offlineBtnY = rowY + 6;
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
            int mouseX,
            int mouseY
    ) {
        boolean hover = hovered(x, y, w, h, mouseX, mouseY);
        graphics.fill(x, y, x + w, y + h, hover ? hoverColor : baseColor);
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

    private Identifier resolveAccountSkin(Minecraft client, ModernClient modernClient) {
        if (modernClient != null && modernClient.getCosmeticManager() != null) {
            CosmeticManager cosmetics = modernClient.getCosmeticManager();
            CapeCosmetic skinOverride = cosmetics.getEquippedSkinCosmetic();
            if (skinOverride != null) {
                return skinOverride.textureId();
            }
        }

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
