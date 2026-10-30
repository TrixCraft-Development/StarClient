package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.cosmetics.CapeCosmetic;
import net.starclient.cosmetics.CosmeticItem;
import net.starclient.cosmetics.CosmeticManager;
import net.starclient.cosmetics.CosmeticType;
import net.starclient.cosmetics.PresetCosmetics;
import net.starclient.module.NickToolState;
import net.starclient.rank.Rank;
import net.starclient.rank.RankManager;
import org.jspecify.annotations.NonNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Dedicated Star Client Cosmetics Studio.
 * Matches exact dimensions of ClickGUI (580x410).
 * Features fresh 3D character (no armor, arrows, or held items),
 * vanilla in-game nametag, genuine Minecraft particle previews,
 * well-spaced scrollbars, and no intrusive equip popups.
 */
public class CosmeticsScreen extends Screen {
    private static final int TARGET_WIDTH = 580;
    private static final int TARGET_HEIGHT = 410;
    private static final int HEADER_HEIGHT = 42;
    private static final int CARD_HEIGHT = 44;
    private static final int CARD_GAP = 5;
    private static final int COLUMN_GAP = 10;
    private static final float DEFAULT_SCALE = 54.0f;

    private final Screen parent;

    // Active Category Tab
    private CosmeticType activeType = CosmeticType.CAPE;

    // Search Box
    private EditBox searchBox;
    private String searchQuery = "";

    // Scrolling
    private double scroll = 0.0;
    private double targetScroll = 0.0;

    // 3D Preview Stage state
    private float previewYaw = 0.0f;
    private float previewPitch = 0.0f;
    private float previewScale = DEFAULT_SCALE;
    private boolean isDraggingPlayer = false;
    private boolean autoRotate = false;

    // Physics sliders state
    private boolean draggingSpeed = false;
    private boolean draggingAmp = false;

    // Skin Lookup for Main Menu 3D Live Preview
    private Supplier<PlayerSkin> skinLookup;

    // Toast notifications (reserved for lock warnings only)
    private String toastMessage = null;
    private long toastExpiry = 0L;
    private int toastColor = 0xFFF59E0B;

    public CosmeticsScreen(Screen parent) {
        super(Component.literal("Cosmetics Studio"));
        this.parent = parent;
    }

    public CosmeticsScreen() {
        this(null);
    }

    // Dynamic Client Theme Colors
    private int menuAccent() {
        StarClient client = StarClient.getInstance();
        if (client != null && client.getClientSettings() != null) {
            return client.getClientSettings().getMenuAccentColor();
        }
        return 0xFF38BDF8;
    }

    private int menuAccentDark() {
        StarClient client = StarClient.getInstance();
        if (client != null && client.getClientSettings() != null) {
            return client.getClientSettings().getMenuAccentDarkColor();
        }
        return 0xFF0284C7;
    }

    private int menuAccentAlpha(int alpha) {
        return (alpha << 24) | (menuAccent() & 0x00FFFFFF);
    }

    private int menuBg() {
        StarClient client = StarClient.getInstance();
        if (client != null && client.getClientSettings() != null) {
            return 0xF4000000 | (client.getClientSettings().getMenuBackgroundColor() & 0x00FFFFFF);
        }
        return 0xF40F172A;
    }

    private int menuBgDark() {
        StarClient client = StarClient.getInstance();
        if (client != null && client.getClientSettings() != null) {
            return client.getClientSettings().getMenuBackgroundDarkColor();
        }
        return 0xFF0B1120;
    }

    private int menuBgLight() {
        StarClient client = StarClient.getInstance();
        if (client != null && client.getClientSettings() != null) {
            return client.getClientSettings().getMenuBackgroundLightColor();
        }
        return 0xFF1E293B;
    }

    private int menuSurface() {
        StarClient client = StarClient.getInstance();
        if (client != null && client.getClientSettings() != null) {
            return client.getClientSettings().getMenuSurfaceColor();
        }
        return 0xFF162036;
    }

    private int menuSurfaceHover() {
        StarClient client = StarClient.getInstance();
        if (client != null && client.getClientSettings() != null) {
            return client.getClientSettings().getMenuSurfaceHoverColor();
        }
        return 0xFF1E293B;
    }

    private int menuBorder() {
        StarClient client = StarClient.getInstance();
        if (client != null && client.getClientSettings() != null) {
            return client.getClientSettings().getMenuBorderColor();
        }
        return 0xFF334155;
    }

    @Override
    protected void init() {
        super.init();

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getSkinManager() != null) {
            client.getGameProfile();
        }
        try {
            this.skinLookup = client.getSkinManager().createLookup(client.getGameProfile(), true);
        } catch (Exception ignored) {
        }

        int pX = panelX();
        int pY = panelY();
        int pW = panelWidth();

        // Search Bar in top header
        int searchW = 100;
        int searchH = 20;
        int searchX = pX + pW - searchW - 28;
        int searchY = pY + (HEADER_HEIGHT - searchH) / 2;

        String prev = this.searchQuery;
        this.searchBox = new EditBox(this.font, searchX + 16, searchY + 1, searchW - 26, searchH - 2, Component.literal(""));
        this.searchBox.setValue(prev);
        this.searchBox.setResponder(val -> {
            this.searchQuery = val;
            this.targetScroll = 0;
            this.scroll = 0;
        });
        this.searchBox.setHint(Component.literal("Search..."));
        this.searchBox.setMaxLength(32);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFF8FAFC);
        addWidget(this.searchBox);
    }

    private int panelWidth() {
        return Math.max(480, Math.min(TARGET_WIDTH, this.width - 20));
    }

    private int panelHeight() {
        return Math.max(340, Math.min(TARGET_HEIGHT, this.height - 20));
    }

    private int panelX() {
        return (this.width - panelWidth()) / 2;
    }

    private int panelY() {
        return (this.height - panelHeight()) / 2;
    }

    private int stageWidth() {
        return 185;
    }

    private int libraryWidth() {
        return panelWidth() - stageWidth() - 24 - COLUMN_GAP;
    }

    private int contentTop() {
        return panelY() + HEADER_HEIGHT + 8;
    }

    private int contentBottom() {
        return panelY() + panelHeight() - 10;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        // 1. Dark semi-transparent blurred backdrop
        extractor.fill(0, 0, this.width, this.height, 0xCC090D16);

        int pX = panelX();
        int pY = panelY();
        int pW = panelWidth();
        int pH = panelHeight();

        // 2. Main Glassmorphic Studio Panel (Dynamic Client Theme Color matching ClickGUI)
        GuiRenderUtils.drawDropShadow(extractor, pX, pY, pW, pH, 8, 4, 100);
        GuiRenderUtils.fillRoundedRect(extractor, pX, pY, pW, pH, 8, menuBg());
        GuiRenderUtils.drawRoundedOutline(extractor, pX, pY, pW, pH, 8, menuBorder());

        // Header Background
        GuiRenderUtils.fillRoundedRect(extractor, pX + 1, pY + 1, pW - 2, HEADER_HEIGHT, 7, menuSurface());
        extractor.fill(pX, pY + HEADER_HEIGHT, pX + pW, pY + HEADER_HEIGHT + 1, menuBorder());

        // 3. Draw Header Contents
        drawHeader(extractor, pX, pY, pW, mouseX, mouseY, delta);

        // 4. Update Auto-rotation
        if (autoRotate && !isDraggingPlayer) {
            previewYaw = (previewYaw + 0.45f) % 360.0f;
        }

        // 5. Draw Left Column (Cosmetic Library Cards or Physics Panel)
        int libX = pX + 12;
        int libW = libraryWidth();
        int topY = contentTop();
        int botY = contentBottom();

        CosmeticManager cosmetics = CosmeticManager.getInstance();
        if (cosmetics != null) {
            if (activeType == CosmeticType.PHYSICS) {
                drawPhysicsPanel(extractor, cosmetics, libX, libW, topY, botY, mouseX, mouseY);
            } else {
                drawCosmeticLibrary(extractor, cosmetics, libX, libW, topY, botY, mouseX, mouseY);
            }
        }

        // 6. Draw Right Column (3D Interactive Player Stage) with 10px spacing
        int stgX = pX + pW - stageWidth() - 12;
        int stgW = stageWidth();
        int stgY = topY;
        int stgH = botY - topY;

        drawPreviewStage(extractor, cosmetics, stgX, stgY, stgW, stgH, mouseX, mouseY);

        // 7. Toast Notification overlay (only for lock notices)
        drawToast(extractor, pX, pW, pY);
    }

    // --- HEADER RENDERING ---

    private void drawHeader(GuiGraphicsExtractor extractor, int pX, int pY, int pW, int mouseX, int mouseY, float delta) {
        // [← Back] Button on the Left
        int backBtnW = 50;
        int backBtnH = 20;
        int backBtnX = pX + 10;
        int backBtnY = pY + (HEADER_HEIGHT - backBtnH) / 2;
        boolean backHover = isHovered(mouseX, mouseY, backBtnX, backBtnY, backBtnW, backBtnH);

        GuiRenderUtils.fillRoundedRect(extractor, backBtnX, backBtnY, backBtnW, backBtnH, 4, backHover ? menuAccentDark() : menuSurface());
        GuiRenderUtils.drawRoundedOutline(extractor, backBtnX, backBtnY, backBtnW, backBtnH, 4, backHover ? menuAccent() : menuBorder());
        GuiRenderUtils.drawCenteredText(extractor, this.font, "← Back", backBtnX + backBtnW / 2, backBtnY + 5, backHover ? 0xFFFFFFFF : 0xFFCBD5E1);

        // Category Tabs Bar
        CosmeticType[] types = CosmeticType.values();
        String[] shortTabNames = {"✦ Capes", "★ Wings", "👑 Head", "✨ Trails", "⚙ Physics"};
        int tabH = 20;
        int[] tabWidths = new int[types.length];
        for (int i = 0; i < types.length; i++) {
            tabWidths[i] = this.font.width(shortTabNames[i]) + 12;
        }

        int searchBoxW = 100;
        int searchX = pX + pW - searchBoxW - 28;
        int tabStartX = backBtnX + backBtnW + 8;
        int curTabX = tabStartX;
        int tabY = pY + (HEADER_HEIGHT - tabH) / 2;

        for (int i = 0; i < types.length; i++) {
            CosmeticType type = types[i];
            int tw = tabWidths[i];
            boolean selected = (type == activeType);
            boolean hovered = isHovered(mouseX, mouseY, curTabX, tabY, tw, tabH);

            int tabBg = selected ? menuAccentAlpha(0x35) : (hovered ? menuSurfaceHover() : menuSurface());
            int tabBorder = selected ? menuAccent() : (hovered ? menuAccentAlpha(0x80) : menuBorder());

            GuiRenderUtils.fillRoundedRect(extractor, curTabX, tabY, tw, tabH, 4, tabBg);
            GuiRenderUtils.drawRoundedOutline(extractor, curTabX, tabY, tw, tabH, 4, tabBorder);

            if (selected) {
                GuiRenderUtils.fillRoundedRect(extractor, curTabX + 3, tabY + tabH - 2, tw - 6, 2, 1, menuAccent());
            }

            int textColor = selected ? 0xFFFFFFFF : (hovered ? 0xFFF1F5F9 : 0xFF94A3B8);
            GuiRenderUtils.drawCenteredText(extractor, this.font, shortTabNames[i], curTabX + tw / 2, tabY + 5, textColor);

            curTabX += tw + 3;
        }

        // Search Bar container & input
        int searchH = 20;
        int searchY = pY + (HEADER_HEIGHT - searchH) / 2;
        boolean searchFocused = this.searchBox != null && this.searchBox.isFocused();
        boolean searchHover = isHovered(mouseX, mouseY, searchX, searchY, searchBoxW, searchH);

        GuiRenderUtils.fillRoundedRect(extractor, searchX, searchY, searchBoxW, searchH, 4, menuBgDark());
        GuiRenderUtils.drawRoundedOutline(extractor, searchX, searchY, searchBoxW, searchH, 4,
                searchFocused ? menuAccent() : (searchHover ? menuAccentAlpha(0x80) : menuBorder()));

        extractor.text(this.font, "🔍", searchX + 4, searchY + 5, searchFocused ? menuAccent() : 0xFF64748B);

        if (this.searchBox != null) {
            this.searchBox.setX(searchX + 16);
            this.searchBox.setY(searchY + 1);
            this.searchBox.setWidth(searchBoxW - (searchQuery.isEmpty() ? 20 : 30));
            this.searchBox.setHeight(searchH - 2);
            this.searchBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        // Search Clear '✕' Button
        if (!searchQuery.isEmpty()) {
            int clearX = searchX + searchBoxW - 14;
            int clearY = searchY + 5;
            boolean clearHover = isHovered(mouseX, mouseY, clearX - 2, clearY - 2, 12, 12);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", clearX + 3, clearY, clearHover ? 0xFFEF4444 : 0xFF94A3B8);
        }

        // Top-Right Screen Close Button '✕'
        int closeX = pX + pW - 22;
        int closeY = pY + (HEADER_HEIGHT - 18) / 2;
        int closeSize = 18;
        boolean closeHover = isHovered(mouseX, mouseY, closeX, closeY, closeSize, closeSize);
        if (closeHover) {
            GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, closeSize, closeSize, 3, 0x33EF4444);
            GuiRenderUtils.drawRoundedOutline(extractor, closeX, closeY, closeSize, closeSize, 3, 0xFFEF4444);
        }
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + closeSize / 2, closeY + 4, closeHover ? 0xFFEF4444 : 0xFF94A3B8);
    }

    // --- COSMETIC LIBRARY COLUMN ---

    private void drawCosmeticLibrary(GuiGraphicsExtractor extractor, CosmeticManager cosmetics,
                                     int x, int width, int topY, int bottomY, int mouseX, int mouseY) {
        List<CosmeticItem> items = getFilteredItems(cosmetics);
        String subhead = activeType.getDisplayName() + " (" + items.size() + ")";
        extractor.text(this.font, subhead, x + 2, topY + 1, 0xFF94A3B8);

        if (activeType == CosmeticType.TRAIL) {
            int modeW = 86;
            int modeH = 16;
            int modeX = x + width - modeW;
            int modeY = topY - 1;
            String curMode = cosmetics.getTrailMode();
            boolean modeHover = isHovered(mouseX, mouseY, modeX, modeY, modeW, modeH);
            GuiRenderUtils.fillRoundedRect(extractor, modeX, modeY, modeW, modeH, 3, modeHover ? menuAccentDark() : menuAccentAlpha(0x35));
            GuiRenderUtils.drawRoundedOutline(extractor, modeX, modeY, modeW, modeH, 3, menuAccent());
            GuiRenderUtils.drawCenteredText(extractor, this.font, "⚡ " + curMode, modeX + modeW / 2, modeY + 3, 0xFFFFFFFF);
        } else {
            boolean hasEquippedInCat = hasEquippedItemInType(cosmetics, activeType);
            if (hasEquippedInCat) {
                int unequipW = 68;
                int unequipH = 16;
                int unequipX = x + width - unequipW;
                int unequipY = topY - 1;
                boolean unequipHover = isHovered(mouseX, mouseY, unequipX, unequipY, unequipW, unequipH);
                GuiRenderUtils.fillRoundedRect(extractor, unequipX, unequipY, unequipW, unequipH, 3, unequipHover ? 0x50EF4444 : menuSurface());
                GuiRenderUtils.drawRoundedOutline(extractor, unequipX, unequipY, unequipW, unequipH, 3, unequipHover ? 0xFFEF4444 : menuBorder());
                GuiRenderUtils.drawCenteredText(extractor, this.font, "✕ Unequip", unequipX + unequipW / 2, unequipY + 3, unequipHover ? 0xFFFFFFFF : 0xFF94A3B8);
            }
        }

        int listTop = topY + 18;
        int listH = bottomY - listTop;
        int cardW = width - 12; // Leaves room for scrollbar with zero overlap

        int totalContentH = items.size() * (CARD_HEIGHT + CARD_GAP);
        double maxScroll = Math.max(0, totalContentH - listH);
        targetScroll = Math.clamp(targetScroll, 0.0, maxScroll);
        scroll += (targetScroll - scroll) * 0.35;
        if (Math.abs(targetScroll - scroll) < 0.1) scroll = targetScroll;

        extractor.enableScissor(x - 2, listTop, x + cardW + 2, bottomY);

        int renderY = listTop - (int) Math.round(scroll);
        for (CosmeticItem item : items) {
            if (renderY + CARD_HEIGHT >= listTop && renderY <= bottomY) {
                drawCosmeticCard(extractor, cosmetics, item, x, renderY, cardW, mouseX, mouseY);
            }
            renderY += CARD_HEIGHT + CARD_GAP;
        }

        if (items.isEmpty()) {
            GuiRenderUtils.drawCenteredText(extractor, this.font, "No cosmetics found", x + cardW / 2, listTop + listH / 2 - 5, 0xFF64748B);
        }

        extractor.disableScissor();

        // Scrollbar positioned in the gutter with clear separation from the cards
        if (maxScroll > 0) {
            int sbX = x + width - 4;
            int sbW = 3;
            int sbH = Math.max(18, (int) ((double) listH / totalContentH * listH));
            int sbY = listTop + (int) ((scroll / maxScroll) * (listH - sbH));
            GuiRenderUtils.fillRoundedRect(extractor, sbX, listTop, sbW, listH, 1, 0x18FFFFFF);
            GuiRenderUtils.fillRoundedRect(extractor, sbX, sbY, sbW, sbH, 1, menuAccent());
        }
    }

    private boolean hasEquippedItemInType(CosmeticManager cosmetics, CosmeticType type) {
        return switch (type) {
            case CAPE -> cosmetics.getEquippedCape() != null;
            case WINGS -> cosmetics.getEquippedWings() != null;
            case HEADWEAR -> cosmetics.getEquippedHeadwear() != null;
            case TRAIL -> cosmetics.getEquippedTrail() != null;
            default -> false;
        };
    }

    private void drawCosmeticCard(GuiGraphicsExtractor extractor, CosmeticManager cosmetics,
                                  CosmeticItem item, int x, int y, int width, int mouseX, int mouseY) {
        boolean equipped = isItemEquipped(cosmetics, item);
        boolean canEquip = cosmetics.canEquip(item);
        boolean hovered = isHovered(mouseX, mouseY, x, y, width, CARD_HEIGHT);

        int bg = equipped ? menuAccentAlpha(0x28) : (hovered ? menuSurfaceHover() : menuSurface());
        int border = equipped ? menuAccent() : (hovered ? menuAccentAlpha(0x80) : menuBorder());
        GuiRenderUtils.fillRoundedRect(extractor, x, y, width, CARD_HEIGHT, 5, bg);
        GuiRenderUtils.drawRoundedOutline(extractor, x, y, width, CARD_HEIGHT, 5, border);

        // Visual Display Preview Box on Left of Card
        int previewBoxW = 34;
        int previewBoxH = 36;
        int previewBoxX = x + 5;
        int previewBoxY = y + (CARD_HEIGHT - previewBoxH) / 2;

        GuiRenderUtils.fillRoundedRect(extractor, previewBoxX, previewBoxY, previewBoxW, previewBoxH, 4, menuBgDark());
        GuiRenderUtils.drawRoundedOutline(extractor, previewBoxX, previewBoxY, previewBoxW, previewBoxH, 4, equipped ? menuAccent() : menuBorder());

        // Render Miniature Preview by type
        if (item.type() == CosmeticType.CAPE) {
            // Cape Preview: outer face of cape texture
            Identifier capeTex = PresetCosmetics.getOrRegisterCape(item.id());
            int capeW = 18;
            int capeH = 28;
            int capeX = previewBoxX + (previewBoxW - capeW) / 2;
            int capeY = previewBoxY + (previewBoxH - capeH) / 2;
            extractor.blit(capeTex, capeX, capeY, capeX + capeW, capeY + capeH,
                    1.0f / 64.0f, 11.0f / 64.0f, 1.0f / 32.0f, 17.0f / 32.0f);
        } else if (item.type() == CosmeticType.WINGS) {
            // Wings Preview: mirrored spread wings from texture
            Identifier wingTex = PresetCosmetics.getOrRegisterWing(item.id());
            int wHalf = 13;
            int wH = 24;
            int midX = previewBoxX + previewBoxW / 2;
            int topW = previewBoxY + (previewBoxH - wH) / 2;
            extractor.blit(wingTex, midX - wHalf, topW, midX, topW + wH, 0.0f, 0.85f, 0.0f, 0.85f);
            extractor.blit(wingTex, midX, topW, midX + wHalf, topW + wH, 0.85f, 0.0f, 0.0f, 0.85f);
        } else if (item.type() == CosmeticType.HEADWEAR) {
            // Headwear Preview: generated texture
            Identifier headTex = PresetCosmetics.getOrRegisterHeadwear(item.id());
            int hwSize = 22;
            int hwX = previewBoxX + (previewBoxW - hwSize) / 2;
            int hwY = previewBoxY + (previewBoxH - hwSize) / 2;
            extractor.blit(headTex, hwX, hwY, hwX + hwSize, hwY + hwSize, 0.0f, 1.0f, 0.0f, 1.0f);
        } else if (item.type() == CosmeticType.TRAIL) {
            // Trail Preview: Genuine Minecraft particle texture
            Identifier partTex = resolveParticleTexture(item.id());
            int pSize = 18;
            int pX = previewBoxX + (previewBoxW - pSize) / 2;
            int pY = previewBoxY + (previewBoxH - pSize) / 2;
            extractor.blit(partTex, pX, pY, pX + pSize, pY + pSize, 0.0f, 1.0f, 0.0f, 1.0f);
        }

        // Title and animated tag
        int textX = previewBoxX + previewBoxW + 8;
        int titleY = y + 7;
        int titleColor = equipped ? menuAccent() : 0xFFF8FAFC;
        extractor.text(this.font, item.displayName(), textX, titleY, titleColor);

        if (item.isAnimated()) {
            int titleW = this.font.width(item.displayName());
            int animTagX = textX + titleW + 5;
            String tagText = "ANIMATED";
            int tagW = this.font.width(tagText) + 8;
            GuiRenderUtils.fillRoundedRect(extractor, animTagX, titleY - 1, tagW, 10, 2, 0x40A855F7);
            GuiRenderUtils.drawRoundedOutline(extractor, animTagX, titleY - 1, tagW, 10, 2, 0xFFA855F7);
            GuiRenderUtils.drawCenteredText(extractor, this.font, tagText, animTagX + tagW / 2, titleY, 0xFFE9D5FF);
        }

        // Description (truncated if needed)
        String desc = item.description() != null ? item.description() : "Cosmetic";
        int maxDescW = width - (textX - x) - 72;
        String truncDesc = GuiRenderUtils.truncate(this.font, desc, maxDescW);
        extractor.text(this.font, truncDesc, textX, y + 23, 0xFF94A3B8);

        // Status Button on Right
        int btnW = 66;
        int btnH = 20;
        int btnX = x + width - btnW - 6;
        int btnY = y + (CARD_HEIGHT - btnH) / 2;

        if (!canEquip) {
            boolean lockHover = isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
            int lockBg = lockHover ? 0x50F59E0B : 0x25F59E0B;
            GuiRenderUtils.fillRoundedRect(extractor, btnX, btnY, btnW, btnH, 4, lockBg);
            GuiRenderUtils.drawRoundedOutline(extractor, btnX, btnY, btnW, btnH, 4, 0xFFF59E0B);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "★ PREMIUM", btnX + btnW / 2, btnY + 5, 0xFFFCD34D);
        } else if (equipped) {
            boolean eqHover = isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
            int eqBg = eqHover ? 0x50EF4444 : menuAccentDark();
            int eqBorder = eqHover ? 0xFFEF4444 : menuAccent();
            String eqText = eqHover ? "UNEQUIP" : "EQUIPPED";
            GuiRenderUtils.fillRoundedRect(extractor, btnX, btnY, btnW, btnH, 4, eqBg);
            GuiRenderUtils.drawRoundedOutline(extractor, btnX, btnY, btnW, btnH, 4, eqBorder);
            GuiRenderUtils.drawCenteredText(extractor, this.font, eqText, btnX + btnW / 2, btnY + 5, 0xFFFFFFFF);
        } else {
            boolean btnHover = isHovered(mouseX, mouseY, btnX, btnY, btnW, btnH);
            int btnBg = btnHover ? menuAccentAlpha(0x40) : menuBgDark();
            GuiRenderUtils.fillRoundedRect(extractor, btnX, btnY, btnW, btnH, 4, btnBg);
            GuiRenderUtils.drawRoundedOutline(extractor, btnX, btnY, btnW, btnH, 4, btnHover ? menuAccent() : menuBorder());
            GuiRenderUtils.drawCenteredText(extractor, this.font, "EQUIP", btnX + btnW / 2, btnY + 5, btnHover ? 0xFFFFFFFF : 0xFFCBD5E1);
        }
    }

    private static Identifier resolveParticleTexture(String trailId) {
        return switch (trailId) {
            case "stardust" -> Identifier.fromNamespaceAndPath("minecraft", "textures/particle/glitter_0.png");
            case "hearts" -> Identifier.fromNamespaceAndPath("minecraft", "textures/particle/heart.png");
            case "portal" -> Identifier.fromNamespaceAndPath("minecraft", "textures/particle/spark_0.png");
            case "chroma" -> Identifier.fromNamespaceAndPath("minecraft", "textures/particle/enchanted_hit.png");
            case "snow" -> Identifier.fromNamespaceAndPath("minecraft", "textures/particle/generic_0.png");
            default -> Identifier.fromNamespaceAndPath("minecraft", "textures/particle/flame.png");
        };
    }

    // --- PHYSICS SETTINGS PANEL ---

    private void drawPhysicsPanel(GuiGraphicsExtractor extractor, CosmeticManager cosmetics,
                                  int x, int width, int topY, int bottomY, int mouseX, int mouseY) {
        boolean hasPermission = cosmetics.hasWavePhysicsPermission();
        Rank selfRank = StarClient.getInstance().getRankManager().getSelfRank();

        if (!hasPermission) {
            int cardH = 150;
            GuiRenderUtils.fillRoundedRect(extractor, x, topY + 4, width, cardH, 8, menuSurface());
            GuiRenderUtils.drawRoundedOutline(extractor, x, topY + 4, width, cardH, 8, 0x60F59E0B);

            GuiRenderUtils.drawCenteredText(extractor, this.font, "★ PREMIUM SUPPORTER FEATURE", x + width / 2, topY + 18, 0xFFF59E0B);
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Multi-Segment Wavey Cape Physics", x + width / 2, topY + 34, 0xFFF8FAFC);

            String d1 = "Fine-tune 16-segment aerodynamic wave speed,";
            String d2 = "running flap amplitude, and wind sway in real time.";
            String d3 = "Exclusively reserved for Star Client Premium supporters.";
            GuiRenderUtils.drawCenteredText(extractor, this.font, d1, x + width / 2, topY + 56, 0xFF94A3B8);
            GuiRenderUtils.drawCenteredText(extractor, this.font, d2, x + width / 2, topY + 68, 0xFF94A3B8);
            GuiRenderUtils.drawCenteredText(extractor, this.font, d3, x + width / 2, topY + 82, 0xFFFCD34D);

            int rankBadgeW = 120;
            int rankBadgeX = x + (width - rankBadgeW) / 2;
            int rankBadgeY = topY + 108;
            GuiRenderUtils.fillRoundedRect(extractor, rankBadgeX, rankBadgeY, rankBadgeW, 22, 4, menuBgDark());
            GuiRenderUtils.drawRoundedOutline(extractor, rankBadgeX, rankBadgeY, rankBadgeW, 22, 4, menuBorder());
            String rName = selfRank != null ? selfRank.getDisplayName() : "User";
            int rCol = selfRank != null ? (0xFF000000 | selfRank.getArgb()) : 0xFF38BDF8;
            GuiRenderUtils.drawCenteredText(extractor, this.font, "Your Rank: " + rName, rankBadgeX + rankBadgeW / 2, rankBadgeY + 6, rCol);
            return;
        }

        int curY = topY + 4;
        extractor.text(this.font, "WAVEY CAPE PHYSICS CONTROLS", x + 4, curY, menuAccent());
        extractor.text(this.font, "Aerodynamic wave equations & atmospheric sway", x + 4, curY + 12, 0xFF94A3B8);
        curY += 32;

        // 1. Wave Speed Slider
        float speed = cosmetics.getCapeWaveSpeed();
        drawSlider(extractor, "Wave Flap Speed", String.format(Locale.ROOT, "%.1fx", speed),
                x, curY, width, (speed - 0.5f) / 2.0f);
        curY += 46;

        // 2. Wave Amplitude Slider
        float amp = cosmetics.getCapeWaveAmplitude();
        drawSlider(extractor, "Wave Flap Amplitude", String.format(Locale.ROOT, "%.1fx", amp),
                x, curY, width, (amp - 0.5f) / 2.0f);
        curY += 46;

        // 3. Wind Flutter Toggle Switch
        boolean wind = cosmetics.isCapeWindSway();
        int toggleH = 30;
        boolean toggleHover = isHovered(mouseX, mouseY, x, curY, width, toggleH);
        GuiRenderUtils.fillRoundedRect(extractor, x, curY, width, toggleH, 4, toggleHover ? menuSurfaceHover() : menuSurface());
        GuiRenderUtils.drawRoundedOutline(extractor, x, curY, width, toggleH, 4, wind ? menuAccent() : menuBorder());
        extractor.text(this.font, "Atmospheric Wind Sway", x + 8, curY + 5, 0xFFF8FAFC);
        extractor.text(this.font, "Natural subtle air turbulence", x + 8, curY + 16, 0xFF64748B);

        int pillW = 36;
        int pillH = 16;
        int pillX = x + width - pillW - 8;
        int pillY = curY + (toggleH - pillH) / 2;
        int pillBg = wind ? menuAccent() : 0xFF334155;
        GuiRenderUtils.fillRoundedRect(extractor, pillX, pillY, pillW, pillH, 8, pillBg);
        int thumbX = wind ? pillX + pillW - 14 : pillX + 2;
        GuiRenderUtils.fillRoundedRect(extractor, thumbX, pillY + 2, 12, 12, 6, 0xFFFFFFFF);

        curY += toggleH + 14;

        // Reset to Defaults Button
        int rstW = 120;
        int rstH = 22;
        int rstX = x + 4;
        boolean rstHover = isHovered(mouseX, mouseY, rstX, curY, rstW, rstH);
        GuiRenderUtils.fillRoundedRect(extractor, rstX, curY, rstW, rstH, 4, rstHover ? menuSurfaceHover() : menuSurface());
        GuiRenderUtils.drawRoundedOutline(extractor, rstX, curY, rstW, rstH, 4, rstHover ? menuAccent() : menuBorder());
        GuiRenderUtils.drawCenteredText(extractor, this.font, "↺ Reset Defaults", rstX + rstW / 2, curY + 6, rstHover ? 0xFFFFFFFF : 0xFF94A3B8);
    }

    private void drawSlider(GuiGraphicsExtractor extractor, String label, String valueStr,
                            int x, int y, int width, float progress) {
        extractor.text(this.font, label, x + 4, y, 0xFFF8FAFC);
        int valW = this.font.width(valueStr);
        extractor.text(this.font, valueStr, x + width - valW - 4, y, menuAccent());

        int trackY = y + 14;
        int trackH = 7;
        GuiRenderUtils.fillRoundedRect(extractor, x + 4, trackY, width - 8, trackH, 3, menuBgDark());

        int fillW = (int) ((width - 8) * Math.clamp(progress, 0.0f, 1.0f));
        if (fillW > 0) {
            GuiRenderUtils.fillRoundedRect(extractor, x + 4, trackY, fillW, trackH, 3, menuAccent());
        }

        int thumbX = x + 4 + fillW - 4;
        GuiRenderUtils.fillRoundedRect(extractor, thumbX, trackY - 3, 8, 13, 3, 0xFFFFFFFF);
    }

    // --- 3D INTERACTIVE PREVIEW STAGE ---

    private void drawPreviewStage(GuiGraphicsExtractor extractor, CosmeticManager cosmetics,
                                  int x, int y, int width, int height, int mouseX, int mouseY) {
        // Stage Glassmorphic Container
        GuiRenderUtils.fillRoundedRect(extractor, x, y, width, height, 7, menuBgDark());
        GuiRenderUtils.drawRoundedOutline(extractor, x, y, width, height, 7, menuBorder());

        // Header Title in Client Accent
        extractor.text(this.font, "✦ 3D PREVIEW", x + 8, y + 8, menuAccent());

        // Controls on Top Right: Reset Angle (↺) and Auto-Spin (▶ / ⏸)
        int btnSize = 16;
        int resetX = x + width - btnSize - 6;
        int resetY = y + 6;
        boolean resetHover = isHovered(mouseX, mouseY, resetX, resetY, btnSize, btnSize);
        GuiRenderUtils.fillRoundedRect(extractor, resetX, resetY, btnSize, btnSize, 3, resetHover ? menuSurfaceHover() : menuSurface());
        GuiRenderUtils.drawRoundedOutline(extractor, resetX, resetY, btnSize, btnSize, 3, resetHover ? menuAccent() : menuBorder());
        GuiRenderUtils.drawCenteredText(extractor, this.font, "↺", resetX + btnSize / 2, resetY + 3, resetHover ? 0xFFFFFFFF : 0xFF94A3B8);

        int spinX = resetX - btnSize - 3;
        boolean spinHover = isHovered(mouseX, mouseY, spinX, resetY, btnSize, btnSize);
        GuiRenderUtils.fillRoundedRect(extractor, spinX, resetY, btnSize, btnSize, 3, spinHover ? menuSurfaceHover() : menuSurface());
        GuiRenderUtils.drawRoundedOutline(extractor, spinX, resetY, btnSize, btnSize, 3, spinHover ? menuAccent() : menuBorder());
        GuiRenderUtils.drawCenteredText(extractor, this.font, autoRotate ? "⏸" : "▶", spinX + btnSize / 2, resetY + 3, spinHover ? 0xFFFFFFFF : 0xFF94A3B8);

        // Glowing Pedestal at player's feet
        int pedCenterX = x + width / 2;
        int pedY = y + height - 50;
        GuiRenderUtils.fillRoundedRect(extractor, pedCenterX - 42, pedY, 84, 8, 4, menuAccentAlpha(0x35));
        GuiRenderUtils.fillRoundedRect(extractor, pedCenterX - 30, pedY + 2, 60, 4, 2, menuAccentAlpha(0x65));

        Minecraft client = Minecraft.getInstance();
        int playerViewportY = y + 22;
        int playerViewportH = height - 76;

        // Render fresh 3D player with in-game vanilla nametag (works in-game and in main menu)
        render3DPlayer(extractor, x + 4, playerViewportY, width - 8, playerViewportH,
                previewScale, previewYaw, previewPitch, client);

        // Subtitle hint at bottom
        String hint = "Drag • Scroll to zoom";
        GuiRenderUtils.drawCenteredText(extractor, this.font, hint, pedCenterX, y + height - 36, 0xFF64748B);

        // Equipped Summary Bar
        int barY = y + height - 20;
        String equippedSummary = buildEquippedSummary(cosmetics);
        GuiRenderUtils.drawCenteredText(extractor, this.font, equippedSummary, pedCenterX, barY, menuAccent());
    }

    private PlayerSkin resolvePlayerSkin(Minecraft client) {
        if (client.player != null) {
            try {
                PlayerSkin s = client.player.getSkin();
                if (s != null) return s;
            } catch (Exception ignored) {}
        }
        if (this.skinLookup == null) {
            client.getGameProfile();
            try {
                this.skinLookup = client.getSkinManager().createLookup(client.getGameProfile(), true);
            } catch (Exception ignored) {
            }
        }
        if (this.skinLookup != null) {
            try {
                PlayerSkin s = this.skinLookup.get();
                if (s != null) return s;
            } catch (Exception ignored) {}
        }
        client.getGameProfile();
        try {
            return DefaultPlayerSkin.get(client.getGameProfile());
        } catch (Exception ignored) {
        }
        return DefaultPlayerSkin.getDefaultSkin();
    }

    private AvatarRenderState buildAvatarRenderState(Minecraft client, float yaw) {
        AvatarRenderState state;
        if (client.player != null) {
            try {
                EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
                EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(client.player);
                EntityRenderState raw = renderer.createRenderState(client.player, 1.0f);
                if (raw instanceof AvatarRenderState as) {
                    state = as;
                } else {
                    state = new AvatarRenderState();
                }
            } catch (Exception ignored) {
                state = new AvatarRenderState();
            }
        } else {
            state = new AvatarRenderState();
        }

        PlayerSkin skin = resolvePlayerSkin(client);

        // Apply equipped Cape cosmetic texture to PlayerSkin
        CosmeticManager cosmetics = CosmeticManager.getInstance();
        CapeCosmetic cape = cosmetics != null ? cosmetics.getEquippedCapeCosmetic() : null;
        if (cape != null && skin != null) {
            skin = new PlayerSkin(
                    skin.body(),
                    new ClientAsset.ResourceTexture(cape.assetId(), cape.textureId()),
                    skin.elytra(),
                    skin.model(),
                    skin.secure()
            );
        }
        assert skin != null;
        state.skin = skin;

        // Clean slate: strip armor, arrows, stingers, held items for a pristine wardrobe preview
        state.arrowCount = 0;
        state.stingerCount = 0;
        state.id = 0; // Local/preview marker for StarCosmeticLayer

        state.headEquipment = ItemStack.EMPTY;
        state.chestEquipment = ItemStack.EMPTY;
        state.legsEquipment = ItemStack.EMPTY;
        state.feetEquipment = ItemStack.EMPTY;

        state.rightHandItemStack = ItemStack.EMPTY;
        state.leftHandItemStack = ItemStack.EMPTY;
        state.rightHandItemState.clear();
        state.leftHandItemState.clear();
        state.headItem.clear();
        state.heldOnHead.clear();
        state.wornHeadType = null;
        state.wornHeadProfile = null;

        // Clean default standing poses
        state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        state.mainArm = HumanoidArm.RIGHT;
        state.attackArm = HumanoidArm.RIGHT;
        state.isCrouching = false;
        state.isFallFlying = false;
        state.isVisuallySwimming = false;
        state.isPassenger = false;
        state.isUsingItem = false;
        state.swimAmount = 0.0f;
        state.elytraRotX = 0.0f;
        state.elytraRotY = 0.0f;
        state.elytraRotZ = 0.0f;

        // Ensure all player skin outer layers (hat, jacket, pants, sleeves) are visible
        state.showHat = true;
        state.showJacket = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
        state.showCape = false; // StarCosmeticLayer renders fluid Wavey Cape

        state.capeFlap = 16.0f;
        state.capeLean = 0.0f;
        state.capeLean2 = 0.0f;

        state.bodyRot = 180.0f + yaw;
        state.yRot = 0.0f; // Head always looks straight forward relative to the model
        state.xRot = 0.0f; // Head remains level with the torso
        state.deathTime = 0.0f;
        state.walkAnimationPos = 0.0f;
        state.walkAnimationSpeed = 0.0f;
        state.hasRedOverlay = false;
        state.isFullyFrozen = false;
        state.isInWater = false;
        state.pose = Pose.STANDING;

        state.scale = 1.0f;
        state.ageScale = 1.0f;
        state.boundingBoxWidth = 0.6f;
        state.boundingBoxHeight = 1.8f;
        state.eyeHeight = 1.62f;

        state.shadowPieces.clear();
        state.outlineColor = 0;

        // Nametag: ⭐ <PlayerName> (matching in-game billboarding tag)
        RankManager rankManager = StarClient.getInstance() != null ? StarClient.getInstance().getRankManager() : null;
        Rank rank = rankManager != null ? rankManager.getSelfRank() : Rank.USER;
        String username = "Player";
        if (client.player != null) {
            username = client.player.getGameProfile().name();
        } else {
            username = client.getUser().getName();
        }

        Component nameComp = Component.literal(username);
        try {
            nameComp = NickToolState.rewriteNames(nameComp);
        } catch (Exception ignored) {}

        Component icon = Component.literal("⭐").withStyle(style -> style.withColor(rank != null ? rank.getRgb() : 0xFF38BDF8));
        state.nameTag = Component.literal("").append(icon).append(Component.literal(" ")).append(nameComp);
        state.nameTagAttachment = new Vec3(0.0, 1.8 + 0.5, 0.0);

        return state;
    }

    private void render3DPlayer(GuiGraphicsExtractor extractor, int x, int y, int width, int height,
                                float scale, float yaw, float pitch, Minecraft client) {
        if (client == null) return;
        AvatarRenderState state = buildAvatarRenderState(client, yaw);

        // Flip upside down for GUI, and set camera angle for billboard nametag
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf overrideCameraAngle = new Quaternionf().rotateX((float) Math.toRadians(pitch));
        rotation.mul(overrideCameraAngle);

        Vector3f translation = new Vector3f(0.0f, state.boundingBoxHeight / 2.0f, 0.0f);
        extractor.entity(state, scale, translation, rotation, overrideCameraAngle, x, y, x + width, y + height);
    }

    private String buildEquippedSummary(CosmeticManager cosmetics) {
        if (cosmetics == null) return "No cosmetics";
        StringBuilder sb = new StringBuilder();
        if (cosmetics.getEquippedCape() != null) sb.append("✦ Cape ");
        if (cosmetics.getEquippedWings() != null) sb.append("★ Wings ");
        if (cosmetics.getEquippedHeadwear() != null) sb.append("👑 Head ");
        if (cosmetics.getEquippedTrail() != null) sb.append("✨ Trail ");
        return !sb.isEmpty() ? sb.toString().trim() : "None equipped";
    }

    // --- TOAST NOTIFICATIONS ---

    private void drawToast(GuiGraphicsExtractor extractor, int pX, int pW, int pY) {
        if (toastMessage == null || System.currentTimeMillis() > toastExpiry) {
            toastMessage = null;
            return;
        }

        int toastW = this.font.width(toastMessage) + 20;
        int toastH = 20;
        int toastX = pX + (pW - toastW) / 2;
        int toastY = pY + 6;

        GuiRenderUtils.fillRoundedRect(extractor, toastX, toastY, toastW, toastH, 5, menuBgDark());
        GuiRenderUtils.drawRoundedOutline(extractor, toastX, toastY, toastW, toastH, 5, toastColor);
        GuiRenderUtils.drawCenteredText(extractor, this.font, toastMessage, toastX + toastW / 2, toastY + 5, toastColor);
    }

    public void showToast(String message, int color) {
        this.toastMessage = message;
        this.toastExpiry = System.currentTimeMillis() + 3200L;
        this.toastColor = color;
    }

    // --- INPUT & EVENT HANDLING ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();
            int pX = panelX();
            int pY = panelY();
            int pW = panelWidth();

            // 1. Back button
            int backBtnW = 50;
            int backBtnH = 20;
            int backBtnX = pX + 10;
            int backBtnY = pY + (HEADER_HEIGHT - backBtnH) / 2;
            if (isHovered(mx, my, backBtnX, backBtnY, backBtnW, backBtnH)) {
                close();
                return true;
            }

            // Close '✕' button
            int closeX = pX + pW - 22;
            int closeY = pY + (HEADER_HEIGHT - 18) / 2;
            int closeSize = 18;
            if (isHovered(mx, my, closeX, closeY, closeSize, closeSize)) {
                close();
                return true;
            }

            // Search clear '✕' button
            int searchBoxW = 100;
            int searchH = 20;
            int searchX = pX + pW - searchBoxW - 28;
            int searchY = pY + (HEADER_HEIGHT - searchH) / 2;
            if (!searchQuery.isEmpty()) {
                int clearX = searchX + searchBoxW - 14;
                int clearY = searchY + 5;
                if (isHovered(mx, my, clearX - 2, clearY - 2, 12, 12)) {
                    if (this.searchBox != null) this.searchBox.setValue("");
                    this.searchQuery = "";
                    return true;
                }
            }

            // Category Tabs
            CosmeticType[] types = CosmeticType.values();
            String[] shortTabNames = {"✦ Capes", "★ Wings", "👑 Head", "✨ Trails", "⚙ Physics"};
            int tabH = 20;
            int[] tabWidths = new int[types.length];
            for (int i = 0; i < types.length; i++) {
                tabWidths[i] = this.font.width(shortTabNames[i]) + 12;
            }
            int tabStartX = backBtnX + backBtnW + 8;
            int curTabX = tabStartX;
            int tabY = pY + (HEADER_HEIGHT - tabH) / 2;

            for (int i = 0; i < types.length; i++) {
                int tw = tabWidths[i];
                if (isHovered(mx, my, curTabX, tabY, tw, tabH)) {
                    this.activeType = types[i];
                    this.scroll = 0;
                    this.targetScroll = 0;
                    if (this.minecraft != null) {
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    return true;
                }
                curTabX += tw + 3;
            }

            // 2. Stage Click & Controls
            int stgX = pX + pW - stageWidth() - 12;
            int stgW = stageWidth();
            int stgY = contentTop();
            int stgH = contentBottom() - contentTop();

            int btnSize = 16;
            int resetX = stgX + stgW - btnSize - 6;
            int resetY = stgY + 6;
            if (isHovered(mx, my, resetX, resetY, btnSize, btnSize)) {
                this.previewYaw = 0.0f;
                this.previewPitch = 0.0f;
                this.previewScale = DEFAULT_SCALE;
                return true;
            }

            int spinX = resetX - btnSize - 3;
            if (isHovered(mx, my, spinX, resetY, btnSize, btnSize)) {
                this.autoRotate = !this.autoRotate;
                return true;
            }

            if (isHovered(mx, my, stgX, stgY, stgW, stgH)) {
                this.isDraggingPlayer = true;
                return true;
            }

            // 3. Center Column clicks (Library or Physics)
            CosmeticManager cosmetics = CosmeticManager.getInstance();
            if (cosmetics != null) {
                int libX = pX + 12;
                int libW = libraryWidth();
                int topY = contentTop();
                int botY = contentBottom();

                if (activeType == CosmeticType.PHYSICS) {
                    if (handlePhysicsClick(cosmetics, libX, libW, topY, mx, my)) return true;
                } else {
                    if (handleLibraryClick(cosmetics, libX, libW, topY, botY, mx, my)) return true;
                }
            }
        }

        return super.mouseClicked(event, action);
    }

    private boolean handleLibraryClick(CosmeticManager cosmetics, int x, int width, int topY, int bottomY,
                                       double mx, double my) {
        if (activeType == CosmeticType.TRAIL) {
            int modeW = 86;
            int modeH = 16;
            int modeX = x + width - modeW;
            int modeY = topY - 1;
            if (isHovered(mx, my, modeX, modeY, modeW, modeH)) {
                String cur = cosmetics.getTrailMode();
                if ("Always".equals(cur)) cosmetics.setTrailMode("Only Sprinting");
                else if ("Only Sprinting".equals(cur)) cosmetics.setTrailMode("Only Airborne");
                else cosmetics.setTrailMode("Always");
                StarClient.getInstance().getConfigManager().saveConfig();
                return true;
            }
        } else {
            if (hasEquippedItemInType(cosmetics, activeType)) {
                int unequipW = 68;
                int unequipH = 16;
                int unequipX = x + width - unequipW;
                int unequipY = topY - 1;
                if (isHovered(mx, my, unequipX, unequipY, unequipW, unequipH)) {
                    unequipActiveCategory(cosmetics);
                    StarClient.getInstance().getConfigManager().saveConfig();
                    return true;
                }
            }
        }

        int listTop = topY + 18;
        if (my < listTop || my > bottomY) return false;

        List<CosmeticItem> items = getFilteredItems(cosmetics);
        int renderY = listTop - (int) Math.round(scroll);
        int cardW = width - 12;

        for (CosmeticItem item : items) {
            if (isHovered(mx, my, x, renderY, cardW, CARD_HEIGHT)) {
                if (!cosmetics.canEquip(item)) {
                    showToast("★ Premium Rank Required to unlock!", 0xFFF59E0B);
                    return true;
                }

                toggleEquip(cosmetics, item);
                StarClient.getInstance().getConfigManager().saveConfig();
                return true;
            }
            renderY += CARD_HEIGHT + CARD_GAP;
        }

        return false;
    }

    private void unequipActiveCategory(CosmeticManager cosmetics) {
        switch (activeType) {
            case CAPE -> cosmetics.setEquippedCape(null);
            case WINGS -> cosmetics.setEquippedWings(null);
            case HEADWEAR -> cosmetics.setEquippedHeadwear(null);
            case TRAIL -> cosmetics.setEquippedTrail(null);
            default -> {}
        }
        if (StarClient.getInstance() != null && StarClient.getInstance().getConfigManager() != null) {
            StarClient.getInstance().getConfigManager().saveConfig();
        }
    }

    private void toggleEquip(CosmeticManager cosmetics, CosmeticItem item) {
        boolean currentlyEquipped = isItemEquipped(cosmetics, item);
        String newId = currentlyEquipped ? null : item.id();

        switch (item.type()) {
            case CAPE -> cosmetics.setEquippedCape(newId);
            case WINGS -> cosmetics.setEquippedWings(newId);
            case HEADWEAR -> cosmetics.setEquippedHeadwear(newId);
            case TRAIL -> cosmetics.setEquippedTrail(newId);
            default -> {}
        }
        if (StarClient.getInstance() != null && StarClient.getInstance().getConfigManager() != null) {
            StarClient.getInstance().getConfigManager().saveConfig();
        }
    }

    private boolean isItemEquipped(CosmeticManager cosmetics, CosmeticItem item) {
        if (item == null || item.id() == null) return false;
        return switch (item.type()) {
            case CAPE -> item.id().equals(cosmetics.getEquippedCape());
            case WINGS -> item.id().equals(cosmetics.getEquippedWings());
            case HEADWEAR -> item.id().equals(cosmetics.getEquippedHeadwear());
            case TRAIL -> item.id().equals(cosmetics.getEquippedTrail());
            default -> false;
        };
    }

    private boolean handlePhysicsClick(CosmeticManager cosmetics, int x, int width, int topY,
                                       double mx, double my) {
        if (!cosmetics.hasWavePhysicsPermission()) return false;

        int curY = topY + 36;

        // Wave Speed slider
        if (isHovered(mx, my, x + 4, curY + 10, width - 8, 14)) {
            draggingSpeed = true;
            updateSpeedSlider(cosmetics, x, width, mx);
            return true;
        }
        curY += 46;

        // Wave Amplitude slider
        if (isHovered(mx, my, x + 4, curY + 10, width - 8, 14)) {
            draggingAmp = true;
            updateAmpSlider(cosmetics, x, width, mx);
            return true;
        }
        curY += 46;

        // Wind Sway toggle
        if (isHovered(mx, my, x, curY, width, 30)) {
            cosmetics.setCapeWindSway(!cosmetics.isCapeWindSway());
            StarClient.getInstance().getConfigManager().saveConfig();
            return true;
        }
        curY += 44;

        // Reset to Defaults
        if (isHovered(mx, my, x + 4, curY, 120, 22)) {
            cosmetics.setCapeWaveSpeed(1.0f);
            cosmetics.setCapeWaveAmplitude(1.0f);
            cosmetics.setCapeWindSway(true);
            StarClient.getInstance().getConfigManager().saveConfig();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (isDraggingPlayer) {
            previewYaw += (float) (dx * 1.2);
            previewPitch = Math.clamp(previewPitch + (float) (dy * 0.8), -35.0f, 35.0f);
            return true;
        }

        CosmeticManager cosmetics = CosmeticManager.getInstance();
        if (cosmetics != null && cosmetics.hasWavePhysicsPermission()) {
            int libX = panelX() + 12;
            int libW = libraryWidth();
            if (draggingSpeed) {
                updateSpeedSlider(cosmetics, libX, libW, event.x());
                return true;
            }
            if (draggingAmp) {
                updateAmpSlider(cosmetics, libX, libW, event.x());
                return true;
            }
        }

        return super.mouseDragged(event, dx, dy);
    }

    private void updateSpeedSlider(CosmeticManager cosmetics, int x, int width, double mouseX) {
        float t = (float) Math.clamp((mouseX - (x + 4)) / (width - 8), 0.0, 1.0);
        float speed = Math.round((0.5f + t * 2.0f) * 10.0f) / 10.0f;
        cosmetics.setCapeWaveSpeed(speed);
        StarClient.getInstance().getConfigManager().saveConfig();
    }

    private void updateAmpSlider(CosmeticManager cosmetics, int x, int width, double mouseX) {
        float t = (float) Math.clamp((mouseX - (x + 4)) / (width - 8), 0.0, 1.0);
        float amp = Math.round((0.5f + t * 2.0f) * 10.0f) / 10.0f;
        cosmetics.setCapeWaveAmplitude(amp);
        StarClient.getInstance().getConfigManager().saveConfig();
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        this.isDraggingPlayer = false;
        this.draggingSpeed = false;
        this.draggingAmp = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int stgX = panelX() + panelWidth() - stageWidth() - 12;
        int stgW = stageWidth();
        int stgY = contentTop();
        int stgH = contentBottom() - contentTop();

        if (isHovered(mouseX, mouseY, stgX, stgY, stgW, stgH)) {
            previewScale = Math.clamp(previewScale + (float) (verticalAmount * 3.5), 38.0f, 80.0f);
            return true;
        }

        int libX = panelX() + 12;
        int libW = libraryWidth();
        if (isHovered(mouseX, mouseY, libX, stgY, libW, stgH)) {
            targetScroll -= verticalAmount * 24.0;
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return this.searchBox.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (StarClient.getInstance() != null && StarClient.getInstance().getConfigManager() != null) {
            StarClient.getInstance().getConfigManager().saveConfig();
        }
        super.onClose();
    }

    private void close() {
        if (StarClient.getInstance() != null && StarClient.getInstance().getConfigManager() != null) {
            StarClient.getInstance().getConfigManager().saveConfig();
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            if (this.parent != null) {
                client.gui.setScreen(this.parent);
            } else {
                this.onClose();
            }
        }
    }

    private List<CosmeticItem> getFilteredItems(CosmeticManager cosmetics) {
        List<CosmeticItem> list = new ArrayList<>();
        for (CosmeticItem item : cosmetics.getPresetsByType(activeType)) {
            if (matchesSearch(item.displayName()) || matchesSearch(item.description())) {
                list.add(item);
            }
        }
        return list;
    }

    private boolean matchesSearch(String text) {
        if (searchQuery == null || searchQuery.trim().isEmpty()) return true;
        if (text == null) return false;
        return text.toLowerCase(Locale.ROOT).contains(searchQuery.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isHovered(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
