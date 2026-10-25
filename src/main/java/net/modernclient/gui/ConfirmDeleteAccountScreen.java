package net.modernclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.modernclient.ClientSettings;
import net.modernclient.ModernClient;
import net.modernclient.account.AccountManager;
import net.modernclient.account.MinecraftAccount;
import net.modernclient.rank.Rank;
import net.modernclient.rank.RankManager;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

public class ConfirmDeleteAccountScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 160;
    private static final int HEADER_HEIGHT = 36;
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 24;

    private final Screen parent;
    private final MinecraftAccount account;

    public ConfirmDeleteAccountScreen(Screen parent, MinecraftAccount account) {
        super(Component.literal("Confirm Account Removal"));
        this.parent = parent;
        this.account = account;
    }

    private int panelX() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_HEIGHT) / 2;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        ModernClient mod = ModernClient.getInstance();
        ClientSettings cs = mod.getClientSettings();
        int pX = panelX();
        int pY = panelY();

        extractor.fill(pX, pY, pX + PANEL_WIDTH, pY + PANEL_HEIGHT, cs.getMenuBackgroundColor());
        extractor.fill(pX, pY, pX + PANEL_WIDTH, pY + HEADER_HEIGHT, cs.getMenuBackgroundLightColor());
        extractor.fill(pX, pY, pX + PANEL_WIDTH, pY + 2, 0xFFEF4444);

        extractor.text(this.font, "★ Remove Saved Account", pX + 16, pY + 13, 0xFFEF4444);

        String prompt = "Are you sure you want to remove this account?";
        extractor.text(this.font, prompt, pX + (PANEL_WIDTH - this.font.width(prompt)) / 2, pY + 50, 0xFFCBD5E1);

        // Account preview pill (Player Head + Rank-colored Name)
        AccountManager accountManager = mod.getAccountManager();
        RankManager rankManager = mod.getRankManager();
        Rank rank = rankManager != null
                ? rankManager.getAccountRank(account.getUuid(), account.getUsername())
                : Rank.USER;
        int nameColor = rank.getArgb();

        int headSize = 18;
        int gap = 8;
        int nameW = this.font.width(account.getUsername());
        int pillW = 10 + headSize + gap + nameW + 10;
        int pillH = 26;
        int pillX = pX + (PANEL_WIDTH - pillW) / 2;
        int pillY = pY + 70;

        extractor.fill(pillX, pillY, pillX + pillW, pillY + pillH, cs.getMenuBackgroundLightColor());
        extractor.fill(pillX, pillY, pillX + pillW, pillY + 1, 0xFF334155);
        extractor.fill(pillX, pillY + pillH - 1, pillX + pillW, pillY + pillH, 0xFF334155);

        Identifier skin = accountManager.getSkinTexture(account);
        int headX = pillX + 10;
        int headY = pillY + (pillH - headSize) / 2;
        extractor.blit(skin, headX, headY, headX + headSize, headY + headSize,
                8 / 64.0F, 16 / 64.0F, 8 / 64.0F, 16 / 64.0F);
        extractor.blit(skin, headX, headY, headX + headSize, headY + headSize,
                40 / 64.0F, 48 / 64.0F, 8 / 64.0F, 16 / 64.0F);

        extractor.text(this.font, account.getUsername(), headX + headSize + gap,
                pillY + (pillH - this.font.lineHeight) / 2 + 1, nameColor);

        int btnY = pY + PANEL_HEIGHT - 36;
        drawButton(extractor, "Cancel", pX + 18, btnY, 0xFF475569, mouseX, mouseY);
        drawButton(extractor, "Remove", pX + PANEL_WIDTH - 18 - BUTTON_WIDTH, btnY, 0xFFEF4444, mouseX, mouseY);
    }

    private void drawButton(GuiGraphicsExtractor extractor, String label, int x, int y, int color, int mouseX, int mouseY) {
        boolean isHovered = hovered(x, y, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY);
        int bg = isHovered ? darken(color) : color;
        extractor.fill(x, y, x + BUTTON_WIDTH, y + BUTTON_HEIGHT, bg);
        extractor.text(this.font, label, x + (BUTTON_WIDTH - this.font.width(label)) / 2, y + 8, 0xFFFFFFFF);
    }

    private static int darken(int color) {
        int r = (int) ((color >> 16 & 0xFF) * 0.8);
        int g = (int) ((color >> 8 & 0xFF) * 0.8);
        int b = (int) ((color & 0xFF) * 0.8);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            int pX = panelX();
            int pY = panelY();
            int btnY = pY + PANEL_HEIGHT - 36;

            if (hovered(pX + 18, btnY, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
                Minecraft.getInstance().gui.setScreen(parent);
                return true;
            }
            if (hovered(pX + PANEL_WIDTH - 18 - BUTTON_WIDTH, btnY, BUTTON_WIDTH, BUTTON_HEIGHT, mouseX, mouseY)) {
                confirmDelete();
                return true;
            }
        }
        return super.mouseClicked(event, action);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            confirmDelete();
            return true;
        }
        return super.keyPressed(event);
    }

    private void confirmDelete() {
        ModernClient.getInstance().getAccountManager().removeAccount(account);
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private static boolean hovered(int x, int y, int w, int h, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
