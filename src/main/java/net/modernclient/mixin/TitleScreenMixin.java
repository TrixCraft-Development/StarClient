package net.modernclient.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
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
import net.modernclient.gui.ConfirmDeleteAccountScreen;
import net.modernclient.gui.MicrosoftLoginScreen;
import net.modernclient.gui.OfflineAccountScreen;
import net.modernclient.rank.Rank;
import net.modernclient.rank.RankManager;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = TitleScreen.class, priority = 2000)
public abstract class TitleScreenMixin {

    @Unique
    private static final int BOX_X = 8;
    @Unique
    private static final int BOX_Y = 8;
    @Unique
    private static final int BOX_H = 26;
    @Unique
    private static final int MIN_BOX_WIDTH = 148;
    @Unique
    private static final int ROW_HEIGHT = 22;
    @Unique
    private static final int BTN_HEIGHT = 20;

    @Unique
    private String starclient$cachedProfileName;

    @Unique
    private ResolvableProfile starclient$cachedResolvableProfile;

    @Unique
    private boolean starclient$accountDropdownOpen;

    @Unique
    private int starclient$lastHeaderWidth = 120;

    @ModifyArg(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"
            ),
            index = 1
    )
    private String replaceBottomLeftVersionText(String original) {
        if (original == null || !original.startsWith("Minecraft ")) {
            return original;
        }
        return "StarClient-" + ModernClient.MOD_VERSION;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderClientName(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.font == null) return;

        String name = ModernClient.CLIENT_NAME;
        float scale = 4.0f;
        int screenWidth = graphics.guiWidth();
        int scaledWidth = (int) (client.font.width(name) * scale);
        int x = (screenWidth - scaledWidth) / 2;
        int y = 72;

        int color = 0xFF38BDF8;
        ModernClient modernClient = ModernClient.getInstance();
        if (modernClient != null) {
            modernClient.ensureConfigLoaded();
            if (modernClient.getClientSettings() != null) {
                color = modernClient.getClientSettings().getMenuAccentColor();
            }
        }

        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.scale(scale, scale);
        graphics.text(client.font, name, (int) (x / scale), (int) (y / scale), color);
        pose.popMatrix();

        String version = ModernClient.CLIENT_VERSION;
        graphics.text(client.font, version, screenWidth / 2 - client.font.width(version) / 2, y + (int) (client.font.lineHeight * scale) + 4, 0xFF94A3B8);

        starclient$renderAccountDisplay(graphics, client, modernClient, mouseX, mouseY);
    }

    @Unique
    private void starclient$renderAccountDisplay(
            GuiGraphicsExtractor graphics,
            Minecraft client,
            ModernClient modernClient,
            int mouseX,
            int mouseY
    ) {
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
        String arrow = starclient$accountDropdownOpen ? "▲" : "▾";
        int arrowW = client.font.width(arrow);
        int textWidth = client.font.width(playerName);
        int boxW = Math.max(MIN_BOX_WIDTH, padX + headSize + gap + textWidth + 6 + arrowW + 8);
        this.starclient$lastHeaderWidth = boxW;

        boolean headerHovered = starclient$hovered(BOX_X, BOX_Y, boxW, BOX_H, mouseX, mouseY);
        int headerBg = headerHovered || starclient$accountDropdownOpen ? menuBgLight : menuBg;

        // Card background & subtle border
        graphics.fill(BOX_X, BOX_Y, BOX_X + boxW, BOX_Y + BOX_H, headerBg);
        graphics.fill(BOX_X, BOX_Y, BOX_X + boxW, BOX_Y + 1, 0xFF334155);
        graphics.fill(BOX_X, BOX_Y + BOX_H - 1, BOX_X + boxW, BOX_Y + BOX_H, 0xFF334155);
        graphics.fill(BOX_X, BOX_Y, BOX_X + 1, BOX_Y + BOX_H, 0xFF334155);
        graphics.fill(BOX_X + boxW - 1, BOX_Y, BOX_X + boxW, BOX_Y + BOX_H, 0xFF334155);

        // Player Head (face + outer hat layer)
        int headX = BOX_X + padX;
        int headY = BOX_Y + (BOX_H - headSize) / 2;
        Identifier skinTexture = starclient$resolveAccountSkin(client, modernClient);
        starclient$drawHead(graphics, skinTexture, headX, headY, headSize);

        // Player Name in rank color + dropdown chevron
        int textX = headX + headSize + gap;
        int textY = BOX_Y + (BOX_H - client.font.lineHeight) / 2 + 1;
        graphics.text(client.font, playerName, textX, textY, rankColor);
        graphics.text(client.font, arrow, BOX_X + boxW - arrowW - 6, textY, 0xFF94A3B8);

        if (!starclient$accountDropdownOpen || modernClient == null || modernClient.getAccountManager() == null) {
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
            boolean rowHover = starclient$hovered(dropX + 1, rowY, dropW - 2, ROW_HEIGHT, mouseX, mouseY);
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
            starclient$drawHead(graphics, rowSkin, dropX + 6, rowY + 4, 14);

            int nameY = rowY + (ROW_HEIGHT - client.font.lineHeight) / 2 + 1;
            int rightEdge = dropX + dropW - 5;

            if (!acc.isInitialSession() && !active) {
                int delW = client.font.width("x") + 4;
                int delX = rightEdge - delW;
                boolean delHover = starclient$hovered(delX - 1, rowY + 3, delW + 2, ROW_HEIGHT - 6, mouseX, mouseY);
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

        starclient$drawDropdownButton(graphics, client, "+ Offline Username", btnX, offlineBtnY, btnW, BTN_HEIGHT,
                menuBgLight, 0xFF475569, mouseX, mouseY);
        starclient$drawDropdownButton(graphics, client, "+ Microsoft Account", btnX, msBtnY, btnW, BTN_HEIGHT,
                menuAccent, menuAccentDark, mouseX, mouseY);
    }

    @Unique
    private static void starclient$drawDropdownButton(
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
        boolean hover = starclient$hovered(x, y, w, h, mouseX, mouseY);
        graphics.fill(x, y, x + w, y + h, hover ? hoverColor : baseColor);
        int textX = x + (w - client.font.width(label)) / 2;
        int textY = y + (h - client.font.lineHeight) / 2 + 1;
        graphics.text(client.font, label, textX, textY, 0xFFFFFFFF);
    }

    @Unique
    private static void starclient$drawHead(GuiGraphicsExtractor graphics, Identifier skinTexture, int x, int y, int size) {
        graphics.blit(skinTexture, x, y, x + size, y + size,
                8 / 64.0F, 16 / 64.0F, 8 / 64.0F, 16 / 64.0F);
        graphics.blit(skinTexture, x, y, x + size, y + size,
                40 / 64.0F, 48 / 64.0F, 8 / 64.0F, 16 / 64.0F);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void starclient$onMouseClicked(MouseButtonEvent event, boolean action, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 0) {
            return;
        }
        double mouseX = event.x();
        double mouseY = event.y();

        // Click on top-left Account header
        if (starclient$hovered(BOX_X, BOX_Y, this.starclient$lastHeaderWidth, BOX_H, mouseX, mouseY)) {
            this.starclient$accountDropdownOpen = !this.starclient$accountDropdownOpen;
            cir.setReturnValue(true);
            return;
        }

        if (!this.starclient$accountDropdownOpen) {
            return;
        }

        ModernClient modernClient = ModernClient.getInstance();
        if (modernClient == null || modernClient.getAccountManager() == null) {
            this.starclient$accountDropdownOpen = false;
            return;
        }

        Minecraft client = Minecraft.getInstance();
        AccountManager accountManager = modernClient.getAccountManager();
        List<MinecraftAccount> accounts = accountManager.getAccounts();

        int dropX = BOX_X;
        int dropY = BOX_Y + BOX_H + 2;
        int dropW = this.starclient$lastHeaderWidth;
        int listH = accounts.size() * ROW_HEIGHT;
        int buttonsAreaH = 6 + BTN_HEIGHT + 4 + BTN_HEIGHT + 6;
        int dropH = listH + buttonsAreaH;

        if (!starclient$hovered(dropX, dropY, dropW, dropH, mouseX, mouseY)) {
            this.starclient$accountDropdownOpen = false;
            return;
        }

        // Check clicks on account rows
        int rowY = dropY;
        for (MinecraftAccount acc : accounts) {
            if (starclient$hovered(dropX + 1, rowY, dropW - 2, ROW_HEIGHT, mouseX, mouseY)) {
                boolean active = accountManager.isActive(acc);
                if (!acc.isInitialSession() && !active && client.font != null) {
                    int delW = client.font.width("x") + 4;
                    int delX = dropX + dropW - 5 - delW;
                    if (starclient$hovered(delX - 1, rowY + 3, delW + 2, ROW_HEIGHT - 6, mouseX, mouseY)) {
                        this.starclient$accountDropdownOpen = false;
                        client.gui.setScreen(new ConfirmDeleteAccountScreen((Screen) (Object) this, acc));
                        cir.setReturnValue(true);
                        return;
                    }
                }
                accountManager.switchToAccount(acc);
                this.starclient$accountDropdownOpen = false;
                cir.setReturnValue(true);
                return;
            }
            rowY += ROW_HEIGHT;
        }

        int btnX = dropX + 6;
        int btnW = dropW - 12;
        int offlineBtnY = rowY + 6;
        int msBtnY = offlineBtnY + BTN_HEIGHT + 4;
        Screen currentScreen = (Screen) (Object) this;

        if (starclient$hovered(btnX, offlineBtnY, btnW, BTN_HEIGHT, mouseX, mouseY)) {
            this.starclient$accountDropdownOpen = false;
            client.gui.setScreen(new OfflineAccountScreen(currentScreen));
            cir.setReturnValue(true);
            return;
        }

        if (starclient$hovered(btnX, msBtnY, btnW, BTN_HEIGHT, mouseX, mouseY)) {
            this.starclient$accountDropdownOpen = false;
            client.gui.setScreen(new MicrosoftLoginScreen(currentScreen));
            cir.setReturnValue(true);
            return;
        }

        cir.setReturnValue(true);
    }

    @Unique
    private static boolean starclient$hovered(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Unique
    private Identifier starclient$resolveAccountSkin(Minecraft client, ModernClient modernClient) {
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
                if (this.starclient$cachedResolvableProfile == null || !profileName.equals(this.starclient$cachedProfileName)) {
                    this.starclient$cachedProfileName = profileName;
                    boolean hasTextures = profile.properties() != null && profile.properties().containsKey("textures");
                    this.starclient$cachedResolvableProfile = (hasTextures || profileName.isBlank())
                            ? ResolvableProfile.createResolved(profile)
                            : ResolvableProfile.createUnresolved(profileName);
                }
                PlayerSkinRenderCache.RenderInfo info = cache.getOrDefault(this.starclient$cachedResolvableProfile);
                if (info != null && info.playerSkin() != null && info.playerSkin().body() != null) {
                    return info.playerSkin().body().texturePath();
                }
            }
            return DefaultPlayerSkin.get(profile).body().texturePath();
        }

        return DefaultPlayerSkin.getDefaultTexture();
    }
}
