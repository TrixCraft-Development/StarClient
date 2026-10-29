package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import net.starclient.cosmetics.CapeCosmetic;
import net.starclient.cosmetics.CosmeticManager;
import net.starclient.module.Category;
import net.starclient.module.Module;
import net.starclient.module.NickToolState;
import net.starclient.rank.Rank;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NickEntryListSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;
import net.starclient.setting.TextSetting;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class ClickGuiScreen extends Screen {
    private static final int TARGET_GUI_WIDTH = 650;
    private static final int TARGET_GUI_HEIGHT = 410;
    private static final int SIDEBAR_WIDTH = 138;
    private static final int HEADER_HEIGHT = 42;
    private static final int SUBHEADER_HEIGHT = 30;
    private static final int PANEL_TOP_OFFSET = HEADER_HEIGHT + SUBHEADER_HEIGHT;
    private static final int DRAWER_WIDTH = 248;

    private static final int TILE_HEIGHT = 56;
    private static final int TILE_GAP = 8;
    private static final int COSMETIC_ROW_HEIGHT = 34;
    private static final int COSMETIC_GAP = 6;
    private static final int SEARCH_HEIGHT = 22;
    private static final int HUD_EDITOR_BTN_WIDTH = 82;

    private static final String CAPE_NONE_ROW = "#cape_none";
    private static final String SKIN_NONE_ROW = "#skin_none";
    private static final String RELOAD_ROW = "#reload";
    private static final String HEADER_PREFIX = "#header:";

    private enum SidebarSection {
        FAVORITES,
        ACTIVE,
        CATEGORY
    }

    private enum FilterMode {
        ALL("All"),
        ENABLED("● Active"),
        FAVORITES("★ Favs");

        final String label;

        FilterMode(String label) {
            this.label = label;
        }
    }

    // Persistent UI state across openings
    private static SidebarSection activeSidebarSection = SidebarSection.CATEGORY;
    private static Category selectedCategory = Category.COMBAT;
    private static FilterMode selectedFilter = FilterMode.ALL;
    private static double savedMainScroll = 0.0;

    // Animations
    private final Map<Module, Float> toggleProgressMap = new HashMap<>();
    private final Map<Module, Float> hoverProgressMap = new HashMap<>();
    private final Map<Setting<?>, Float> boolSettingAnimMap = new HashMap<>();
    private float openProgress = 0.0F;
    private float drawerProgress = 0.0F;
    private long lastFrameNanos = System.nanoTime();

    // Window & scroll state
    private int guiWidth = TARGET_GUI_WIDTH;
    private int guiHeight = TARGET_GUI_HEIGHT;
    private double moduleScroll = savedMainScroll;
    private double targetModuleScroll = savedMainScroll;
    private double drawerScroll = 0.0;
    private double targetDrawerScroll = 0.0;

    // Interactive state
    private Module drawerModule;
    private Module bindingModule;
    private Setting<?> draggingSetting;
    private boolean draggingSettingInDrawer;
    private ModeSetting expandedDropdown;
    private boolean draggingMainScrollbar;
    private boolean draggingDrawerScrollbar;
    private double scrollbarDragOffsetY;

    private EditBox searchBox;
    private String searchQuery = "";
    private String pendingTooltipTitle;
    private String pendingTooltipDesc;
    private final StatsViewRenderer statsViewRenderer = new StatsViewRenderer();

    private final Screen parent;

    public ClickGuiScreen() {
        this(null);
    }

    public ClickGuiScreen(Screen parent) {
        super(Component.literal("Star Client ClickGUI"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.guiWidth = Math.max(480, Math.min(TARGET_GUI_WIDTH, this.width - 20));
        this.guiHeight = Math.max(310, Math.min(TARGET_GUI_HEIGHT, this.height - 20));
        this.lastFrameNanos = System.nanoTime();

        int startX = (this.width - guiWidth) / 2;
        int startY = (this.height - guiHeight) / 2;

        int searchX = startX + SIDEBAR_WIDTH + 12;
        int searchW = 190;
        int searchY = startY + (HEADER_HEIGHT - SEARCH_HEIGHT) / 2;

        String prevQuery = this.searchQuery;
        this.searchBox = new EditBox(this.font, searchX + 20, searchY, searchW - 36, SEARCH_HEIGHT, Component.literal(""));
        this.searchBox.setValue(prevQuery);
        this.searchBox.setResponder(value -> {
            this.searchQuery = value;
            this.targetModuleScroll = 0;
            this.moduleScroll = 0;
        });
        this.searchBox.setHint(Component.literal("Search modules or settings..."));
        this.searchBox.setMaxLength(64);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFF8FAFC);
        addWidget(this.searchBox);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);

        // Render normal in-game HUD underneath the ClickGUI (no editor boxes here; those belong to HudEditorScreen)
        HudOverlay.render(extractor);

        long now = System.nanoTime();
        float dt = Math.clamp((now - lastFrameNanos) / 1_000_000_000.0F, 0.001F, 0.1F);
        lastFrameNanos = now;

        updateAnimations(dt);

        pendingTooltipTitle = null;
        pendingTooltipDesc = null;

        int startX = (this.width - guiWidth) / 2;
        int startY = (this.height - guiHeight) / 2;

        // Soft outer drop shadow & main rounded window
        GuiRenderUtils.drawDropShadow(extractor, startX, startY, guiWidth, guiHeight, 8, 8, (int) (130 * openProgress));
        GuiRenderUtils.fillRoundedRect(extractor, startX, startY, guiWidth, guiHeight, 8, menuBg());

        // Sidebar background (left column)
        GuiRenderUtils.fillRoundedRect(extractor, startX, startY + HEADER_HEIGHT, SIDEBAR_WIDTH, guiHeight - HEADER_HEIGHT, 8, menuBgDark());
        extractor.fill(startX + SIDEBAR_WIDTH - 1, startY + HEADER_HEIGHT, startX + SIDEBAR_WIDTH, startY + guiHeight, menuBorder());

        // Header bar
        GuiRenderUtils.fillRoundedRect(extractor, startX, startY, guiWidth, HEADER_HEIGHT, 8, menuSurface());
        extractor.fill(startX, startY + HEADER_HEIGHT - 1, startX + guiWidth, startY + HEADER_HEIGHT, menuBorder());

        drawHeader(extractor, startX, startY, mouseX, mouseY, delta);
        drawSidebar(extractor, startX, startY, mouseX, mouseY);
        drawSubheader(extractor, startX, startY);

        // Main content area
        int contentLeft = startX + SIDEBAR_WIDTH + 12;
        int drawerSpace = drawerVisibleWidth();
        int contentRight = startX + guiWidth - 12 - drawerSpace;
        int contentWidth = Math.max(120, contentRight - contentLeft);
        int panelTop = startY + PANEL_TOP_OFFSET;
        int panelBottom = startY + guiHeight - 10;

        int statsHeaderH = isStatsView() ? 38 : 0;
        int viewportH = panelBottom - panelTop - statsHeaderH;

        clampMainScroll(contentWidth, viewportH);
        savedMainScroll = targetModuleScroll;

        if (isStatsView()) {
            this.statsViewRenderer.draw(extractor, this.font, contentLeft, contentWidth, panelTop, panelBottom, mouseX, mouseY, moduleScroll);
        } else {
            extractor.enableScissor(contentLeft - 2, panelTop, contentRight + 2, panelBottom);
            if (isCosmeticsView()) {
                for (CosmeticRow row : buildCosmeticRows(contentLeft, contentWidth, panelTop, moduleScroll)) {
                    drawCosmeticRow(extractor, row, mouseX, mouseY);
                }
            } else if (isSettingsView()) {
                drawGeneralSettingsView(extractor, contentLeft, contentWidth, panelTop, panelBottom, mouseX, mouseY);
            } else {
                List<ModuleTile> tiles = buildModuleTiles(contentLeft, contentWidth, panelTop, moduleScroll);
                if (tiles.isEmpty()) {
                    String emptyMsg = isSearching()
                            ? "No modules or settings match \"" + searchQuery.trim() + "\""
                            : activeSidebarSection == SidebarSection.FAVORITES
                                    ? "No favorite modules yet — click ☆ on any module tile to pin it here!"
                                    : activeSidebarSection == SidebarSection.ACTIVE
                                            ? "No modules are currently enabled."
                                            : "No modules match the selected filter.";
                    GuiRenderUtils.drawCenteredText(extractor, this.font, emptyMsg,
                            contentLeft + contentWidth / 2, panelTop + (panelBottom - panelTop) / 2 - 6, 0xFF64748B);
                } else {
                    for (ModuleTile tile : tiles) {
                        if (tile.y + TILE_HEIGHT < panelTop || tile.y > panelBottom) continue;
                        drawModuleTile(extractor, tile, mouseX, mouseY);
                    }
                }
            }
            extractor.disableScissor();
        }

        int sbTop = panelTop + statsHeaderH;
        drawScrollbar(extractor, contentRight + 4, sbTop, panelBottom, moduleScroll,
                computeMaxMainScroll(contentWidth, panelBottom - sbTop), draggingMainScrollbar, mouseX, mouseY);

        // Slide-in Settings Drawer on the right
        if (drawerModule != null || drawerProgress > 0.01F) {
            drawSettingsDrawer(extractor, startX, startY, mouseX, mouseY);
        }

        // Outer window rounded border
        GuiRenderUtils.drawRoundedOutline(extractor, startX, startY, guiWidth, guiHeight, 8, menuBorder());

        // Floating tooltip
        if (pendingTooltipTitle != null && !pendingTooltipTitle.isEmpty()) {
            drawFloatingTooltip(extractor, pendingTooltipTitle, pendingTooltipDesc, mouseX, mouseY);
        }
    }

    private void updateAnimations(float dt) {
        float openSpeed = 14.0F * dt;
        openProgress = openProgress + (1.0F - openProgress) * Math.min(1.0F, openSpeed);

        float targetDrawer = (drawerModule != null && !isCosmeticsView() && !isSettingsView() && !isStatsView()) ? 1.0F : 0.0F;
        drawerProgress = drawerProgress + (targetDrawer - drawerProgress) * Math.min(1.0F, 16.0F * dt);
        if (targetDrawer == 0.0F && drawerProgress < 0.01F) {
            drawerProgress = 0.0F;
        }

        // Smooth scroll interpolation
        moduleScroll = moduleScroll + (targetModuleScroll - moduleScroll) * Math.min(1.0F, 18.0F * dt);
        if (Math.abs(targetModuleScroll - moduleScroll) < 0.25) {
            moduleScroll = targetModuleScroll;
        }
        drawerScroll = drawerScroll + (targetDrawerScroll - drawerScroll) * Math.min(1.0F, 18.0F * dt);
        if (Math.abs(targetDrawerScroll - drawerScroll) < 0.25) {
            drawerScroll = targetDrawerScroll;
        }

        for (Module mod : StarClient.getInstance().getModuleManager().getModules()) {
            float targetToggle = mod.isEnabled() ? 1.0F : 0.0F;
            float currentToggle = toggleProgressMap.getOrDefault(mod, targetToggle);
            float nextToggle = currentToggle + (targetToggle - currentToggle) * Math.min(1.0F, 14.0F * dt);
            toggleProgressMap.put(mod, nextToggle);
        }
    }

    private void drawHeader(GuiGraphicsExtractor extractor, int startX, int startY, int mouseX, int mouseY, float delta) {
        // Brand logo + version badge in left header section
        String brandTitle = "⭐ " + StarClient.CLIENT_NAME;
        GuiRenderUtils.drawText(extractor, this.font, brandTitle, startX + 12, startY + 11, menuAccent(), true);
        String verText = StarClient.CLIENT_VERSION + " ● " + "MC-" + StarClient.MC_VERSION;
        GuiRenderUtils.drawText(extractor, this.font, verText, startX + 14, startY + 24, 0xFF64748B);

        // Search Box
        int searchX = startX + SIDEBAR_WIDTH + 12;
        int searchW = 190;
        int searchY = startY + (HEADER_HEIGHT - SEARCH_HEIGHT) / 2;
        int searchIconY = searchY + (SEARCH_HEIGHT - 8) / 2;
        boolean searchHovered = isHovered(mouseX, mouseY, searchX, searchY, searchW, SEARCH_HEIGHT);
        boolean searchFocused = this.searchBox != null && this.searchBox.isFocused();

        GuiRenderUtils.fillRoundedRect(extractor, searchX, searchY, searchW, SEARCH_HEIGHT, 6,
                searchFocused || searchHovered ? menuBgLight() : menuBgDark());
        GuiRenderUtils.drawRoundedOutline(extractor, searchX, searchY, searchW, SEARCH_HEIGHT, 6,
                searchFocused ? menuAccent() : menuBorder());
        GuiRenderUtils.drawText(extractor, this.font, "🔍", searchX + 6, searchIconY,
                searchFocused ? menuAccent() : 0xFF64748B);

        if (this.searchBox != null) {
            this.searchBox.setX(searchX + 20);
            this.searchBox.setY(searchY);
            this.searchBox.setHeight(SEARCH_HEIGHT);
            this.searchBox.setWidth(searchW - (isSearching() ? 36 : 24));
            this.searchBox.extractRenderState(extractor, mouseX, mouseY, delta);
        }

        if (isSearching()) {
            int clearX = searchX + searchW - 16;
            int clearY = searchIconY;
            boolean clearHover = isHovered(mouseX, mouseY, clearX - 3, clearY - 2, 14, 14);
            GuiRenderUtils.drawText(extractor, this.font, "✕", clearX, clearY,
                    clearHover ? 0xFFF87171 : 0xFF94A3B8);
        }

        // Filter Pills (All / Active / Favs)
        int filterX = searchX + searchW + 10;
        int filterY = searchY + 1;
        int filterH = SEARCH_HEIGHT - 2;
        for (FilterMode mode : FilterMode.values()) {
            int pillW = GuiRenderUtils.textWidth(this.font, mode.label) + 14;
            boolean active = (selectedFilter == mode);
            boolean hover = isHovered(mouseX, mouseY, filterX, filterY, pillW, filterH);
            int bg = active ? menuAccentDark() : (hover ? menuSurfaceHover() : menuBgDark());
            GuiRenderUtils.fillRoundedRect(extractor, filterX, filterY, pillW, filterH, 5, bg);
            if (active) {
                GuiRenderUtils.drawRoundedOutline(extractor, filterX, filterY, pillW, filterH, 5, menuAccent());
            }
            GuiRenderUtils.drawCenteredText(extractor, this.font, mode.label,
                    filterX + pillW / 2, filterY + (filterH - 8) / 2,
                    active ? 0xFFFFFFFF : (hover ? 0xFFE2E8F0 : 0xFF94A3B8));
            filterX += pillW + 4;
        }

        // Edit HUD Button on right
        int hudBtnX = startX + guiWidth - 12 - HUD_EDITOR_BTN_WIDTH;
        boolean hudBtnHover = isHovered(mouseX, mouseY, hudBtnX, searchY, HUD_EDITOR_BTN_WIDTH, SEARCH_HEIGHT);
        GuiRenderUtils.fillRoundedRect(extractor, hudBtnX, searchY, HUD_EDITOR_BTN_WIDTH, SEARCH_HEIGHT, 6,
                hudBtnHover ? menuAccentDark() : menuBgDark());
        GuiRenderUtils.drawRoundedOutline(extractor, hudBtnX, searchY, HUD_EDITOR_BTN_WIDTH, SEARCH_HEIGHT, 6,
                hudBtnHover ? menuAccent() : menuBorder());
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✥ Edit HUD",
                hudBtnX + HUD_EDITOR_BTN_WIDTH / 2, searchY + (SEARCH_HEIGHT - 8) / 2,
                hudBtnHover ? 0xFFFFFFFF : menuAccent());
        if (hudBtnHover) {
            setTooltip("HUD Layout Editor", "Open the dedicated HUD Editor to drag, snap, and reset on-screen HUD elements.");
        }
    }

    private void drawSidebar(GuiGraphicsExtractor extractor, int startX, int startY, int mouseX, int mouseY) {
        int itemX = startX + 6;
        int itemW = SIDEBAR_WIDTH - 12;
        int itemH = 22;
        int curY = startY + HEADER_HEIGHT + 8;

        // 1. Quick Views: Favorites & Active
        int favCount = (int) StarClient.getInstance().getModuleManager().getModules().stream()
                .filter(Module::isFavorite).count();
        drawSidebarItem(extractor, itemX, curY, itemW, itemH, "★ Favorites",
                favCount > 0 ? String.valueOf(favCount) : null,
                activeSidebarSection == SidebarSection.FAVORITES && !isSearching(),
                0xFFFACC15, mouseX, mouseY);
        curY += itemH + 3;

        int activeCount = (int) StarClient.getInstance().getModuleManager().getModules().stream()
                .filter(Module::isEnabled).count();
        drawSidebarItem(extractor, itemX, curY, itemW, itemH, "● Enabled",
                String.valueOf(activeCount),
                activeSidebarSection == SidebarSection.ACTIVE && !isSearching(),
                0xFF34D399, mouseX, mouseY);
        curY += itemH + 6;

        // Divider
        extractor.fill(itemX + 4, curY, itemX + itemW - 4, curY + 1, menuBorder());
        curY += 6;

        // 2. Categories
        for (Category category : Category.values()) {
            boolean selected = !isSearching()
                    && activeSidebarSection == SidebarSection.CATEGORY
                    && selectedCategory == category;
            String badge = null;
            if (category != Category.COSMETICS && category != Category.SETTINGS && category != Category.STATS) {
                List<Module> catMods = StarClient.getInstance().getModuleManager().getModulesByCategory(category);
                long enabledInCat = catMods.stream().filter(Module::isEnabled).count();
                if (enabledInCat > 0) {
                    badge = String.valueOf(enabledInCat);
                }
            }
            String label = category.getIcon() + " " + category.getName();
            drawSidebarItem(extractor, itemX, curY, itemW, itemH, label, badge, selected, menuAccent(), mouseX, mouseY);
            curY += itemH + 3;
        }
    }

    private void drawSidebarItem(GuiGraphicsExtractor extractor, int x, int y, int width, int height,
                                 String label, String badge, boolean selected, int indicatorColor,
                                 int mouseX, int mouseY) {
        boolean hovered = isHovered(mouseX, mouseY, x, y, width, height);
        if (selected) {
            GuiRenderUtils.fillRoundedRect(extractor, x, y, width, height, 5, GuiRenderUtils.withAlpha(indicatorColor, 42));
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, width, height, 5, GuiRenderUtils.withAlpha(indicatorColor, 120));
            GuiRenderUtils.fillRoundedRect(extractor, x + 2, y + 4, 3, height - 8, 1, indicatorColor);
        } else if (hovered) {
            GuiRenderUtils.fillRoundedRect(extractor, x, y, width, height, 5, menuSurfaceHover());
        }

        int textColor = selected ? 0xFFFFFFFF : (hovered ? 0xFFE2E8F0 : 0xFF94A3B8);
        GuiRenderUtils.drawText(extractor, this.font, label, x + 9, y + (height - 8) / 2, textColor);

        if (badge != null && !badge.isEmpty()) {
            int badgeW = Math.max(14, GuiRenderUtils.textWidth(this.font, badge) + 8);
            int badgeH = 12;
            int badgeX = x + width - badgeW - 5;
            int badgeY = y + (height - badgeH) / 2;
            int badgeBg = selected ? indicatorColor : GuiRenderUtils.withAlpha(indicatorColor, 45);
            int badgeFg = selected ? 0xFF0F172A : indicatorColor;
            GuiRenderUtils.fillRoundedRect(extractor, badgeX, badgeY, badgeW, badgeH, 6, badgeBg);
            GuiRenderUtils.drawCenteredText(extractor, this.font, badge, badgeX + badgeW / 2, badgeY + 2, badgeFg);
        }
    }

    private void drawSubheader(GuiGraphicsExtractor extractor, int startX, int startY) {
        int subX = startX + SIDEBAR_WIDTH + 12;
        int subY = startY + HEADER_HEIGHT + 9;
        int drawerSpace = drawerVisibleWidth();
        int subRight = startX + guiWidth - 12 - drawerSpace;

        String title;
        String subtitle;
        if (isSearching()) {
            List<Module> visible = getVisibleModules();
            title = "🔍 Search Results for \"" + searchQuery.trim() + "\"";
            subtitle = visible.size() + (visible.size() == 1 ? " module found" : " modules found");
        } else if (activeSidebarSection == SidebarSection.FAVORITES) {
            List<Module> visible = getVisibleModules();
            title = "★ Favorite Modules";
            subtitle = visible.size() + " pinned";
        } else if (activeSidebarSection == SidebarSection.ACTIVE) {
            List<Module> visible = getVisibleModules();
            title = "● Currently Enabled Modules";
            subtitle = visible.size() + " active";
        } else if (isCosmeticsView()) {
            title = Category.COSMETICS.getIcon() + " Capes & Skins";
            subtitle = "Client-side textures in " + CosmeticManager.CAPES_FOLDER;
        } else if (isSettingsView()) {
            title = Category.SETTINGS.getIcon() + " Client Settings";
            subtitle = "Global appearance, font style & integrations";
        } else if (isStatsView()) {
            title = Category.STATS.getIcon() + " Player Statistics";
            subtitle = "Lifetime gameplay tracking & client achievements";
        } else {
            List<Module> visible = getVisibleModules();
            long enabledCount = visible.stream().filter(Module::isEnabled).count();
            title = selectedCategory.getIcon() + " " + selectedCategory.getName() + " Modules";
            subtitle = enabledCount + " of " + visible.size() + " enabled • Left-click to toggle, Right-click or ⚙ for settings";
        }

        GuiRenderUtils.drawText(extractor, this.font, title, subX, subY, 0xFFF8FAFC, true);
        int subtitleW = GuiRenderUtils.textWidth(this.font, subtitle);
        if (subX + GuiRenderUtils.textWidth(this.font, title) + 16 + subtitleW < subRight) {
            GuiRenderUtils.drawText(extractor, this.font, subtitle, subRight - subtitleW, subY, 0xFF64748B);
        }
    }

    private void drawModuleTile(GuiGraphicsExtractor extractor, ModuleTile tile, int mouseX, int mouseY) {
        Module module = tile.module;
        boolean locked = !module.canUse();
        boolean enabled = module.isEnabled();
        boolean isSelectedInDrawer = (drawerModule == module);
        boolean tileHovered = tile.contains(mouseX, mouseY);

        float hoverTarget = tileHovered ? 1.0F : 0.0F;
        float hoverAnim = hoverProgressMap.getOrDefault(module, 0.0F);
        hoverAnim += (hoverTarget - hoverAnim) * 0.2F;
        hoverProgressMap.put(module, hoverAnim);

        int baseBg = enabled ? GuiRenderUtils.lerpColor(menuSurface(), menuAccentDark(), 0.16F) : menuSurface();
        int cardBg = GuiRenderUtils.lerpColor(baseBg, menuSurfaceHover(), hoverAnim * 0.75F);
        int borderColor;
        if (locked) {
            borderColor = GuiRenderUtils.withAlpha(module.getRequiredRank().getArgb(), 140);
        } else if (isSelectedInDrawer) {
            borderColor = menuAccent();
        } else if (enabled) {
            borderColor = GuiRenderUtils.lerpColor(menuAccentDark(), menuAccent(), hoverAnim);
        } else {
            borderColor = GuiRenderUtils.lerpColor(menuBorder(), GuiRenderUtils.withAlpha(menuAccent(), 130), hoverAnim);
        }

        GuiRenderUtils.fillRoundedRect(extractor, tile.x, tile.y, tile.width, TILE_HEIGHT, 6, cardBg);
        GuiRenderUtils.drawRoundedOutline(extractor, tile.x, tile.y, tile.width, TILE_HEIGHT, 6, borderColor);

        // Top Row: Module Name + Favorite Star + Toggle Switch (or Lock Badge)
        int textLeft = tile.x + 10;
        int topRowY = tile.y + 8;

        if (locked) {
            Rank req = module.getRequiredRank();
            String lockLabel = "🔒 " + req.getDisplayName();
            int lockW = GuiRenderUtils.textWidth(this.font, lockLabel) + 10;
            int lockX = tile.x + tile.width - lockW - 8;
            GuiRenderUtils.fillRoundedRect(extractor, lockX, topRowY - 1, lockW, 13, 4, GuiRenderUtils.withAlpha(req.getArgb(), 40));
            GuiRenderUtils.drawCenteredText(extractor, this.font, lockLabel, lockX + lockW / 2, topRowY + 1, req.getArgb());

            String title = GuiRenderUtils.truncate(this.font, module.getName(), lockX - textLeft - 6);
            GuiRenderUtils.drawText(extractor, this.font, title, textLeft, topRowY + 1, 0xFF94A3B8);
        } else {
            // Pill Toggle Switch on top-right
            int switchW = 24;
            int switchH = 13;
            int switchX = tile.x + tile.width - switchW - 8;
            int switchY = topRowY;
            float toggleAnim = toggleProgressMap.getOrDefault(module, enabled ? 1.0F : 0.0F);
            GuiRenderUtils.drawToggleSwitch(extractor, switchX, switchY, switchW, switchH, toggleAnim, menuAccent(), 0xFF334155);

            // Favorite Star button to the left of the switch
            int favX = switchX - 16;
            int favY = topRowY + 1;
            boolean favHover = isHovered(mouseX, mouseY, favX - 2, favY - 2, 14, 14);
            String starIcon = module.isFavorite() ? "★" : "☆";
            int starColor = module.isFavorite() ? 0xFFFACC15 : (favHover ? 0xFFE2E8F0 : 0xFF475569);
            GuiRenderUtils.drawText(extractor, this.font, starIcon, favX, favY, starColor);
            if (favHover) {
                setTooltip(module.isFavorite() ? "Unpin Favorite" : "Pin to Favorites",
                        "Click to " + (module.isFavorite() ? "remove from" : "pin this module to") + " your ★ Favorites tab.");
            }

            int maxTitleW = favX - textLeft - 4;
            String title = GuiRenderUtils.truncate(this.font, module.getName(), maxTitleW);
            int titleColor = enabled ? 0xFFFFFFFF : 0xFFE2E8F0;
            GuiRenderUtils.drawText(extractor, this.font, title, textLeft, topRowY + 1, titleColor, enabled);
        }

        // Middle Row: Shortened description
        String desc = GuiRenderUtils.truncate(this.font, module.getDescription(), tile.width - 20);
        GuiRenderUtils.drawText(extractor, this.font, desc, textLeft, tile.y + 23, 0xFF64748B);

        // Bottom Row: Category Pill (when searching/favs/active), Keybind Badge, and Settings Gear Button
        int botY = tile.y + TILE_HEIGHT - 17;

        if (!locked) {
            // Settings Gear Button on bottom-right
            int settingCount = module.getVisibleSettingCount();
            String gearLabel = settingCount > 0 ? "⚙ " + settingCount : "⚙";
            int gearW = GuiRenderUtils.textWidth(this.font, gearLabel) + 10;
            int gearH = 13;
            int gearX = tile.x + tile.width - gearW - 8;
            boolean gearHover = isHovered(mouseX, mouseY, gearX, botY, gearW, gearH);
            int gearBg = isSelectedInDrawer ? menuAccentDark() : (gearHover ? menuBgLight() : menuBgDark());
            GuiRenderUtils.fillRoundedRect(extractor, gearX, botY, gearW, gearH, 4, gearBg);
            GuiRenderUtils.drawRoundedOutline(extractor, gearX, botY, gearW, gearH, 4,
                    isSelectedInDrawer || gearHover ? menuAccent() : menuBorder());
            GuiRenderUtils.drawCenteredText(extractor, this.font, gearLabel,
                    gearX + gearW / 2, botY + 2,
                    isSelectedInDrawer || gearHover ? 0xFFFFFFFF : 0xFF94A3B8);
            if (gearHover) {
                setTooltip(module.getName() + " Settings", "Click (or right-click anywhere on the tile) to configure settings & keybind.");
            }

            // Keybind Pill Badge to the left of the gear button
            boolean isBindingThis = (bindingModule == module);
            String keyLabel = isBindingThis
                    ? "Press key..."
                    : (module.getKeyBind() == GLFW.GLFW_KEY_UNKNOWN ? "⌨ None" : "⌨ " + keyName(module.getKeyBind()));
            int keyW = GuiRenderUtils.textWidth(this.font, keyLabel) + 10;
            int keyX = gearX - keyW - 4;
            boolean keyHover = isHovered(mouseX, mouseY, keyX, botY, keyW, gearH);
            int keyBg = isBindingThis ? menuAccentDark() : (keyHover ? menuBgLight() : menuBgDark());
            GuiRenderUtils.fillRoundedRect(extractor, keyX, botY, keyW, gearH, 4, keyBg);
            GuiRenderUtils.drawRoundedOutline(extractor, keyX, botY, keyW, gearH, 4,
                    isBindingThis ? 0xFFFACC15 : (keyHover ? menuAccent() : menuBorder()));
            int keyFg = isBindingThis ? 0xFFFACC15 : (module.getKeyBind() != GLFW.GLFW_KEY_UNKNOWN ? menuAccent() : 0xFF64748B);
            GuiRenderUtils.drawCenteredText(extractor, this.font, keyLabel, keyX + keyW / 2, botY + 2, keyFg);
            if (keyHover) {
                setTooltip("Quick Keybind", "Click to bind a hotkey for " + module.getName() + " (Backspace/Delete to clear).");
            }

            // Status / Category badge on bottom-left
            String leftBadge = (isSearching() || activeSidebarSection != SidebarSection.CATEGORY)
                    ? module.getCategory().getIcon() + " " + module.getCategory().getName()
                    : (module.hasHudElement() ? "✥ HUD" : (enabled ? "● Active" : "○ Off"));
            int leftColor = enabled ? menuAccent() : 0xFF64748B;
            GuiRenderUtils.drawText(extractor, this.font, leftBadge, textLeft, botY + 2, leftColor);
        }

        if (tileHovered && pendingTooltipTitle == null) {
            setTooltip(module.getName(), module.getDescription());
        }
    }

    private void drawSettingsDrawer(GuiGraphicsExtractor extractor, int startX, int startY, int mouseX, int mouseY) {
        if (drawerModule == null) return;
        int animOffset = Math.round((1.0F - drawerProgress) * DRAWER_WIDTH);
        int drawerX = startX + guiWidth - DRAWER_WIDTH + animOffset;
        int drawerY = startY + HEADER_HEIGHT;
        int drawerH = guiHeight - HEADER_HEIGHT;

        extractor.enableScissor(startX + SIDEBAR_WIDTH, drawerY, startX + guiWidth, startY + guiHeight);

        // Soft shadow to the left of the drawer
        for (int i = 1; i <= 6; i++) {
            int alpha = (int) ((32 - i * 4) * drawerProgress);
            if (alpha > 0) {
                extractor.fill(drawerX - i, drawerY, drawerX - i + 1, drawerY + drawerH, alpha << 24);
            }
        }

        // Drawer Background & Left Border
        GuiRenderUtils.fillRoundedRect(extractor, drawerX, drawerY, DRAWER_WIDTH, drawerH, 8, menuBgDark());
        extractor.fill(drawerX, drawerY, drawerX + 1, drawerY + drawerH, menuBorder());

        // Drawer Header
        int headerH = 50;
        extractor.fill(drawerX + 1, drawerY, drawerX + DRAWER_WIDTH, drawerY + headerH, menuSurface());
        extractor.fill(drawerX + 1, drawerY + headerH - 1, drawerX + DRAWER_WIDTH, drawerY + headerH, menuBorder());

        // Close Button (✕)
        int closeX = drawerX + DRAWER_WIDTH - 24;
        int closeY = drawerY + 8;
        boolean closeHover = isHovered(mouseX, mouseY, closeX, closeY, 16, 16);
        GuiRenderUtils.fillRoundedRect(extractor, closeX, closeY, 16, 16, 4, closeHover ? 0x44F87171 : menuBgDark());
        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕", closeX + 8, closeY + 4, closeHover ? 0xFFF87171 : 0xFF94A3B8);

        // Toggle switch inside Drawer Header
        int switchW = 24;
        int switchH = 13;
        int switchX = closeX - switchW - 6;
        int switchY = drawerY + 10;
        float toggleAnim = toggleProgressMap.getOrDefault(drawerModule, drawerModule.isEnabled() ? 1.0F : 0.0F);
        GuiRenderUtils.drawToggleSwitch(extractor, switchX, switchY, switchW, switchH, toggleAnim, menuAccent(), 0xFF334155);

        // Module Title & Wrapped Description
        String title = GuiRenderUtils.truncate(this.font, drawerModule.getName(), switchX - (drawerX + 12) - 6);
        GuiRenderUtils.drawText(extractor, this.font, title, drawerX + 12, drawerY + 9, menuAccent(), true);

        String desc = GuiRenderUtils.truncate(this.font, drawerModule.getDescription(), DRAWER_WIDTH - 24);
        GuiRenderUtils.drawText(extractor, this.font, desc, drawerX + 12, drawerY + 24, 0xFF94A3B8);

        String modeInfo = (drawerModule.isHoldKey() ? "Hold Key Mode" : "Toggle Mode")
                + (drawerModule.hasHudElement() ? " • HUD Element" : "");
        GuiRenderUtils.drawText(extractor, this.font, modeInfo, drawerX + 12, drawerY + 36, 0xFF64748B);

        // Drawer Footer (Reset Defaults button + optional HUD Editor shortcut)
        int footerH = 30;
        int footerY = drawerY + drawerH - footerH;
        int bodyTop = drawerY + headerH + 4;
        int bodyBottom = footerY - 4;
        int innerX = drawerX + 10;
        int innerW = DRAWER_WIDTH - 22;

        clampDrawerScroll(innerW, bodyBottom - bodyTop);

        extractor.enableScissor(drawerX + 2, bodyTop, drawerX + DRAWER_WIDTH - 2, bodyBottom);
        List<DrawerItem> items = buildDrawerItems(drawerModule, innerX, innerW, bodyTop, drawerScroll);
        for (DrawerItem item : items) {
            if (item.y + item.height < bodyTop || item.y > bodyBottom) continue;
            drawDrawerItem(extractor, item, mouseX, mouseY);
        }
        extractor.disableScissor();

        drawScrollbar(extractor, drawerX + DRAWER_WIDTH - 6, bodyTop, bodyBottom, drawerScroll,
                computeMaxDrawerScroll(innerW, bodyBottom - bodyTop), draggingDrawerScrollbar, mouseX, mouseY);

        // Footer bar
        extractor.fill(drawerX + 1, footerY, drawerX + DRAWER_WIDTH, drawerY + drawerH, menuSurface());
        extractor.fill(drawerX + 1, footerY, drawerX + DRAWER_WIDTH, footerY + 1, menuBorder());

        int resetBtnX = drawerX + 10;
        int resetBtnY = footerY + 6;
        int resetBtnW = drawerModule.hasHudElement() ? (DRAWER_WIDTH - 26) / 2 : DRAWER_WIDTH - 20;
        int resetBtnH = 18;
        boolean resetHover = isHovered(mouseX, mouseY, resetBtnX, resetBtnY, resetBtnW, resetBtnH);
        GuiRenderUtils.fillRoundedRect(extractor, resetBtnX, resetBtnY, resetBtnW, resetBtnH, 5,
                resetHover ? 0x33F87171 : menuBgDark());
        GuiRenderUtils.drawRoundedOutline(extractor, resetBtnX, resetBtnY, resetBtnW, resetBtnH, 5,
                resetHover ? 0xFFF87171 : menuBorder());
        GuiRenderUtils.drawCenteredText(extractor, this.font, "↺ Reset Defaults",
                resetBtnX + resetBtnW / 2, resetBtnY + 5, resetHover ? 0xFFF87171 : 0xFF94A3B8);

        if (drawerModule.hasHudElement()) {
            int posBtnX = resetBtnX + resetBtnW + 6;
            int posBtnW = DRAWER_WIDTH - 20 - resetBtnW - 6;
            boolean posHover = isHovered(mouseX, mouseY, posBtnX, resetBtnY, posBtnW, resetBtnH);
            GuiRenderUtils.fillRoundedRect(extractor, posBtnX, resetBtnY, posBtnW, resetBtnH, 5,
                    posHover ? menuAccentDark() : menuBgDark());
            GuiRenderUtils.drawRoundedOutline(extractor, posBtnX, resetBtnY, posBtnW, resetBtnH, 5,
                    posHover ? menuAccent() : menuBorder());
            GuiRenderUtils.drawCenteredText(extractor, this.font, "✥ Move HUD",
                    posBtnX + posBtnW / 2, resetBtnY + 5, posHover ? 0xFFFFFFFF : menuAccent());
        }

        extractor.disableScissor();
    }

    private void drawGeneralSettingsView(GuiGraphicsExtractor extractor, int contentLeft, int contentWidth,
                                         int panelTop, int panelBottom, int mouseX, int mouseY) {
        List<DrawerItem> items = buildClientSettingsItems(contentLeft, contentWidth, panelTop, moduleScroll);
        for (DrawerItem item : items) {
            if (item.y + item.height < panelTop || item.y > panelBottom) continue;
            drawDrawerItem(extractor, item, mouseX, mouseY);
        }
    }

    private void drawDrawerItem(GuiGraphicsExtractor extractor, DrawerItem item, int mouseX, int mouseY) {
        if (item.sectionHeader != null) {
            GuiRenderUtils.drawText(extractor, this.font, item.sectionHeader.toUpperCase(Locale.ROOT),
                    item.x + 2, item.y + 6, menuAccent(), true);
            extractor.fill(item.x, item.y + item.height - 2, item.x + item.width, item.y + item.height - 1, menuBorder());
            return;
        }

        if (item.isKeybindRow && item.module != null) {
            Module mod = item.module;
            boolean isBinding = (bindingModule == mod);
            boolean hovered = item.contains(mouseX, mouseY);
            GuiRenderUtils.fillRoundedRect(extractor, item.x, item.y, item.width, item.height, 6,
                    isBinding ? menuAccentDark() : (hovered ? menuSurfaceHover() : menuSurface()));
            GuiRenderUtils.drawRoundedOutline(extractor, item.x, item.y, item.width, item.height, 6,
                    isBinding ? 0xFFFACC15 : (hovered ? menuAccent() : menuBorder()));

            String label = mod.isHoldKey() ? "Hotkey (Hold)" : "Hotkey (Toggle)";
            GuiRenderUtils.drawText(extractor, this.font, label, item.x + 8, item.y + 8, 0xFFE2E8F0);

            String keyStr = isBinding
                    ? "Press any key... (Esc / Del)"
                    : (mod.getKeyBind() == GLFW.GLFW_KEY_UNKNOWN ? "None" : keyName(mod.getKeyBind()));
            int pillW = GuiRenderUtils.textWidth(this.font, keyStr) + 12;
            int pillX = item.x + item.width - pillW - 6;
            int pillY = item.y + 4;
            GuiRenderUtils.fillRoundedRect(extractor, pillX, pillY, pillW, item.height - 8, 4, menuBgDark());
            GuiRenderUtils.drawCenteredText(extractor, this.font, keyStr, pillX + pillW / 2, pillY + 4,
                    isBinding ? 0xFFFACC15 : (mod.getKeyBind() == GLFW.GLFW_KEY_UNKNOWN ? 0xFF64748B : menuAccent()));
            return;
        }

        if (item.infoMessage != null) {
            GuiRenderUtils.fillRoundedRect(extractor, item.x, item.y, item.width, item.height, 6, menuSurface());
            GuiRenderUtils.drawCenteredText(extractor, this.font, item.infoMessage,
                    item.x + item.width / 2, item.y + (item.height - 8) / 2, 0xFF64748B);
            return;
        }

        Setting<?> setting = item.setting;
        if (setting == null) return;

        boolean hovered = item.contains(mouseX, mouseY);
        GuiRenderUtils.fillRoundedRect(extractor, item.x, item.y, item.width, item.height, 6,
                hovered ? menuSurfaceHover() : menuSurface());
        GuiRenderUtils.drawRoundedOutline(extractor, item.x, item.y, item.width, item.height, 6,
                hovered ? GuiRenderUtils.withAlpha(menuAccent(), 110) : menuBorder());

        if (hovered && pendingTooltipTitle == null) {
            setTooltip(setting.getName(), setting.getDescription());
        }

        if (setting instanceof BooleanSetting booleanSetting) {
            GuiRenderUtils.drawText(extractor, this.font, setting.getName(), item.x + 8, item.y + 8, 0xFFE2E8F0);
            float target = booleanSetting.getValue() ? 1.0F : 0.0F;
            float anim = boolSettingAnimMap.getOrDefault(setting, target);
            anim += (target - anim) * 0.25F;
            boolSettingAnimMap.put(setting, anim);

            int swW = 26;
            int swH = 14;
            int swX = item.x + item.width - swW - 8;
            int swY = item.y + (item.height - swH) / 2;
            GuiRenderUtils.drawToggleSwitch(extractor, swX, swY, swW, swH, anim, menuAccent(), 0xFF334155);
        } else if (setting instanceof ColorModeSetting colorSetting) {
            GuiRenderUtils.drawText(extractor, this.font, setting.getName(), item.x + 8, item.y + 7, 0xFFE2E8F0);
            String curVal = colorSetting.getValue();
            GuiRenderUtils.drawText(extractor, this.font, curVal,
                    item.x + item.width - GuiRenderUtils.textWidth(this.font, curVal) - 8, item.y + 7, menuAccent());

            List<String> modes = colorSetting.getModes();
            int swatchSize = 14;
            int swatchGap = 4;
            int cols = Math.max(1, (item.width - 16 + swatchGap) / (swatchSize + swatchGap));
            for (int i = 0; i < modes.size(); i++) {
                String modeName = modes.get(i);
                int col = i % cols;
                int row = i / cols;
                int sx = item.x + 8 + col * (swatchSize + swatchGap);
                int sy = item.y + 22 + row * (swatchSize + swatchGap);
                int color = colorSetting.getColorFor(modeName);
                boolean selected = modeName.equalsIgnoreCase(curVal);
                boolean swatchHover = isHovered(mouseX, mouseY, sx, sy, swatchSize, swatchSize);

                GuiRenderUtils.fillRoundedRect(extractor, sx, sy, swatchSize, swatchSize, 4, color);
                if (selected) {
                    GuiRenderUtils.drawRoundedOutline(extractor, sx - 1, sy - 1, swatchSize + 2, swatchSize + 2, 5, 0xFFFFFFFF);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, "•", sx + swatchSize / 2, sy + 3, 0xFFFFFFFF);
                } else if (swatchHover) {
                    GuiRenderUtils.drawRoundedOutline(extractor, sx - 1, sy - 1, swatchSize + 2, swatchSize + 2, 5, menuAccent());
                    setTooltip(setting.getName() + ": " + modeName, "Click to select " + modeName);
                } else {
                    GuiRenderUtils.drawRoundedOutline(extractor, sx, sy, swatchSize, swatchSize, 4, 0x66000000);
                }
            }
        } else if (setting instanceof ModeSetting modeSetting) {
            List<String> modes = modeSetting.getModes();
            if (isCompactSegmentedMode(modeSetting, item.width)) {
                GuiRenderUtils.drawText(extractor, this.font, setting.getName(), item.x + 8, item.y + 6, 0xFFE2E8F0);
                int segY = item.y + 20;
                int segH = 16;
                int totalW = item.width - 16;
                int segGap = 4;
                int segW = (totalW - segGap * (modes.size() - 1)) / modes.size();
                for (int i = 0; i < modes.size(); i++) {
                    String opt = modes.get(i);
                    int sx = item.x + 8 + i * (segW + segGap);
                    boolean active = opt.equalsIgnoreCase(modeSetting.getValue());
                    boolean segHover = isHovered(mouseX, mouseY, sx, segY, segW, segH);
                    int bg = active ? menuAccentDark() : (segHover ? menuBgLight() : menuBgDark());
                    GuiRenderUtils.fillRoundedRect(extractor, sx, segY, segW, segH, 4, bg);
                    GuiRenderUtils.drawRoundedOutline(extractor, sx, segY, segW, segH, 4,
                            active ? menuAccent() : menuBorder());
                    String label = GuiRenderUtils.truncate(this.font, opt, segW - 6);
                    GuiRenderUtils.drawCenteredText(extractor, this.font, label, sx + segW / 2, segY + 4,
                            active ? 0xFFFFFFFF : (segHover ? 0xFFE2E8F0 : 0xFF94A3B8));
                }
            } else {
                // Expandable dropdown mode selector
                GuiRenderUtils.drawText(extractor, this.font, setting.getName(), item.x + 8, item.y + 8, 0xFFE2E8F0);
                boolean isExpanded = (expandedDropdown == modeSetting);
                String valLabel = modeSetting.getValue() + (isExpanded ? " ▴" : " ▾");
                int pillW = Math.min(item.width / 2, GuiRenderUtils.textWidth(this.font, valLabel) + 12);
                int pillX = item.x + item.width - pillW - 6;
                int pillY = item.y + 4;
                GuiRenderUtils.fillRoundedRect(extractor, pillX, pillY, pillW, 16, 4,
                        isExpanded ? menuAccentDark() : menuBgDark());
                GuiRenderUtils.drawRoundedOutline(extractor, pillX, pillY, pillW, 16, 4,
                        isExpanded ? menuAccent() : menuBorder());
                GuiRenderUtils.drawCenteredText(extractor, this.font,
                        GuiRenderUtils.truncate(this.font, valLabel, pillW - 6),
                        pillX + pillW / 2, pillY + 4, isExpanded ? 0xFFFFFFFF : menuAccent());

                if (isExpanded) {
                    int optY = item.y + 24;
                    for (String opt : modes) {
                        boolean active = opt.equalsIgnoreCase(modeSetting.getValue());
                        boolean optHover = isHovered(mouseX, mouseY, item.x + 6, optY, item.width - 12, 16);
                        GuiRenderUtils.fillRoundedRect(extractor, item.x + 6, optY, item.width - 12, 16, 4,
                                active ? menuAccentDark() : (optHover ? menuBgLight() : menuBgDark()));
                        String prefix = active ? "● " : "  ";
                        GuiRenderUtils.drawText(extractor, this.font, prefix + opt, item.x + 12, optY + 4,
                                active ? 0xFFFFFFFF : (optHover ? 0xFFE2E8F0 : 0xFF94A3B8));
                        optY += 18;
                    }
                }
            }
        } else if (setting instanceof NumberSetting numberSetting) {
            GuiRenderUtils.drawText(extractor, this.font, setting.getName(), item.x + 8, item.y + 6, 0xFFE2E8F0);
            String valStr = formatValue(numberSetting.getValue());
            int badgeW = GuiRenderUtils.textWidth(this.font, valStr) + 10;
            int badgeX = item.x + item.width - badgeW - 6;
            GuiRenderUtils.fillRoundedRect(extractor, badgeX, item.y + 4, badgeW, 12, 4, menuBgDark());
            GuiRenderUtils.drawCenteredText(extractor, this.font, valStr, badgeX + badgeW / 2, item.y + 6, menuAccent());

            int trackX = item.x + 8;
            int trackW = item.width - 16;
            int trackY = item.y + item.height - 10;
            int trackH = 4;
            GuiRenderUtils.fillRoundedRect(extractor, trackX, trackY, trackW, trackH, 2, 0xFF334155);

            double range = numberSetting.getMax() - numberSetting.getMin();
            double t = range <= 0 ? 0 : (numberSetting.getValue() - numberSetting.getMin()) / range;
            t = Math.clamp(t, 0.0, 1.0);
            int fillW = Math.max(4, (int) Math.round(trackW * t));
            GuiRenderUtils.fillRoundedRect(extractor, trackX, trackY, fillW, trackH, 2, menuAccent());

            int knobX = trackX + (int) Math.round((trackW - 8) * t);
            GuiRenderUtils.fillRoundedRect(extractor, knobX, trackY - 2, 8, 8, 4, 0xFFFFFFFF);
            GuiRenderUtils.drawRoundedOutline(extractor, knobX, trackY - 2, 8, 8, 4, menuAccent());
        } else if (setting instanceof TextSetting textSetting) {
            GuiRenderUtils.drawText(extractor, this.font, setting.getName(), item.x + 8, item.y + 8, 0xFFE2E8F0);
            String value = textSetting.getValue();
            boolean blank = value == null || value.isBlank();
            String label = blank ? "Edit ✎" : GuiRenderUtils.truncate(this.font,
                    (textSetting.isSecret() ? mask(value) : value) + " ✎", item.width / 2);
            int btnW = GuiRenderUtils.textWidth(this.font, label) + 12;
            int btnX = item.x + item.width - btnW - 6;
            GuiRenderUtils.fillRoundedRect(extractor, btnX, item.y + 4, btnW, 16, 4, menuBgDark());
            GuiRenderUtils.drawRoundedOutline(extractor, btnX, item.y + 4, btnW, 16, 4, menuBorder());
            GuiRenderUtils.drawCenteredText(extractor, this.font, label, btnX + btnW / 2, item.y + 8,
                    blank ? 0xFF94A3B8 : menuAccent());
        } else if (setting instanceof NickEntryListSetting) {
            GuiRenderUtils.drawText(extractor, this.font, setting.getName(), item.x + 8, item.y + 8, 0xFFE2E8F0);
            int count = NickToolState.getEntries().size();
            String label = (count == 0 ? "Manage (0)" : count + " entries") + " ✎";
            int btnW = GuiRenderUtils.textWidth(this.font, label) + 12;
            int btnX = item.x + item.width - btnW - 6;
            GuiRenderUtils.fillRoundedRect(extractor, btnX, item.y + 4, btnW, 16, 4, menuBgDark());
            GuiRenderUtils.drawRoundedOutline(extractor, btnX, item.y + 4, btnW, 16, 4, menuBorder());
            GuiRenderUtils.drawCenteredText(extractor, this.font, label, btnX + btnW / 2, item.y + 8, menuAccent());
        }
    }

    private void drawFloatingTooltip(GuiGraphicsExtractor extractor, String title, String description, int mouseX, int mouseY) {
        int maxW = 210;
        List<String> lines = GuiRenderUtils.wrapLines(this.font, description, maxW - 16);
        int boxW = GuiRenderUtils.textWidth(this.font, title) + 16;
        for (String line : lines) {
            boxW = Math.max(boxW, GuiRenderUtils.textWidth(this.font, line) + 16);
        }
        boxW = Math.clamp(boxW, 90, maxW);
        int boxH = 18 + lines.size() * 11;

        int tx = mouseX + 10;
        int ty = mouseY + 12;
        if (tx + boxW > this.width - 6) {
            tx = mouseX - boxW - 8;
        }
        if (ty + boxH > this.height - 6) {
            ty = mouseY - boxH - 8;
        }
        tx = Math.max(6, tx);
        ty = Math.max(6, ty);

        GuiRenderUtils.drawDropShadow(extractor, tx, ty, boxW, boxH, 5, 4, 120);
        GuiRenderUtils.fillRoundedRect(extractor, tx, ty, boxW, boxH, 5, 0xF40B1120);
        GuiRenderUtils.drawRoundedOutline(extractor, tx, ty, boxW, boxH, 5, menuAccent());

        GuiRenderUtils.drawText(extractor, this.font, title, tx + 8, ty + 5, menuAccent(), true);
        int ly = ty + 16;
        for (String line : lines) {
            GuiRenderUtils.drawText(extractor, this.font, line, tx + 8, ly, 0xFFCBD5E1);
            ly += 11;
        }
    }

    private void setTooltip(String title, String description) {
        this.pendingTooltipTitle = title;
        this.pendingTooltipDesc = description;
    }

    private boolean isCompactSegmentedMode(ModeSetting setting, int availableWidth) {
        List<String> modes = setting.getModes();
        if (modes.size() < 2 || modes.size() > 3) return false;
        int totalTextW = 0;
        for (String m : modes) {
            totalTextW += GuiRenderUtils.textWidth(this.font, m) + 14;
        }
        return totalTextW <= availableWidth - 16;
    }

    private int computeSettingRowHeight(Setting<?> setting, int width) {
        if (setting instanceof ColorModeSetting colorSetting) {
            int swatchSize = 14;
            int swatchGap = 4;
            int cols = Math.max(1, (width - 16 + swatchGap) / (swatchSize + swatchGap));
            int rows = (colorSetting.getModes().size() + cols - 1) / cols;
            return 24 + rows * (swatchSize + swatchGap) + 2;
        }
        if (setting instanceof ModeSetting modeSetting) {
            if (isCompactSegmentedMode(modeSetting, width)) {
                return 40;
            }
            if (expandedDropdown == modeSetting) {
                return 26 + modeSetting.getModes().size() * 18;
            }
            return 24;
        }
        if (setting instanceof NumberSetting) {
            return 32;
        }
        return 24;
    }

    private List<DrawerItem> buildDrawerItems(Module module, int x, int width, int topY, double scroll) {
        List<DrawerItem> items = new ArrayList<>();
        int curY = (int) Math.round(topY - scroll);

        // Keybind Card
        items.add(DrawerItem.keybind(module, x, curY, width, 24));
        curY += 28;

        // General Settings
        List<Setting<?>> general = module.getGeneralSettings();
        if (!general.isEmpty()) {
            items.add(DrawerItem.header("Module Settings", x, curY, width, 18));
            curY += 20;
            for (Setting<?> setting : general) {
                int h = computeSettingRowHeight(setting, width);
                items.add(DrawerItem.setting(setting, x, curY, width, h));
                curY += h + 5;
            }
        }

        // HUD Appearance Settings (ONLY for modules that render a HUD element)
        List<Setting<?>> hudSettings = module.getHudAppearanceSettings();
        if (!hudSettings.isEmpty()) {
            curY += 2;
            items.add(DrawerItem.header("HUD Appearance", x, curY, width, 18));
            curY += 20;
            for (Setting<?> setting : hudSettings) {
                int h = computeSettingRowHeight(setting, width);
                items.add(DrawerItem.setting(setting, x, curY, width, h));
                curY += h + 5;
            }
        }

        if (general.isEmpty() && hudSettings.isEmpty()) {
            items.add(DrawerItem.info("No additional settings for this module", x, curY + 4, width, 28));
        }

        return items;
    }

    private List<DrawerItem> buildClientSettingsItems(int x, int width, int topY, double scroll) {
        List<DrawerItem> items = new ArrayList<>();
        int curY = (int) Math.round(topY - scroll);
        ClientSettings cs = StarClient.getInstance().getClientSettings();

        items.add(DrawerItem.header("Theme & Appearance", x, curY, width, 18));
        curY += 22;

        for (Setting<?> setting : cs.getSettings()) {
            if (setting.getName().equals("HUD Text Shadow")) {
                curY += 4;
                items.add(DrawerItem.header("HUD & In-Game Visuals", x, curY, width, 18));
                curY += 22;
            } else if (setting.getName().equals("Hypixel API Key")) {
                curY += 4;
                items.add(DrawerItem.header("Integrations & API", x, curY, width, 18));
                curY += 22;
            }
            int h = computeSettingRowHeight(setting, width);
            items.add(DrawerItem.setting(setting, x, curY, width, h));
            curY += h + 6;
        }
        return items;
    }

    private List<ModuleTile> buildModuleTiles(int contentLeft, int contentWidth, int panelTop, double scroll) {
        List<ModuleTile> tiles = new ArrayList<>();
        List<Module> modules = getVisibleModules();
        if (modules.isEmpty()) return tiles;

        int cols = computeGridColumns(contentWidth);
        int tileWidth = (contentWidth - TILE_GAP * (cols - 1)) / cols;
        int baseY = (int) Math.round(panelTop - scroll);

        for (int i = 0; i < modules.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int tx = contentLeft + col * (tileWidth + TILE_GAP);
            int ty = baseY + row * (TILE_HEIGHT + TILE_GAP);
            tiles.add(new ModuleTile(modules.get(i), tx, ty, tileWidth));
        }
        return tiles;
    }

    private int computeGridColumns(int contentWidth) {
        if (contentWidth >= 430) return 3;
        if (contentWidth >= 230) return 2;
        return 1;
    }

    private int drawerVisibleWidth() {
        if (isCosmeticsView() || isSettingsView() || isStatsView()) return 0;
        if (drawerModule == null && drawerProgress <= 0.01F) return 0;
        return Math.round(DRAWER_WIDTH * drawerProgress);
    }

    private List<Module> getVisibleModules() {
        List<Module> all = StarClient.getInstance().getModuleManager().getModules();
        List<Module> base;

        if (isSearching()) {
            String query = searchQuery.trim().toLowerCase(Locale.ROOT);
            base = all.stream()
                    .filter(m -> matchesSearch(m, query))
                    .collect(Collectors.toList());
        } else if (activeSidebarSection == SidebarSection.FAVORITES) {
            base = all.stream().filter(Module::isFavorite).collect(Collectors.toList());
        } else if (activeSidebarSection == SidebarSection.ACTIVE) {
            base = all.stream().filter(Module::isEnabled).collect(Collectors.toList());
        } else {
            base = new ArrayList<>(StarClient.getInstance().getModuleManager().getModulesByCategory(selectedCategory));
        }

        // Apply filter pill
        if (selectedFilter == FilterMode.ENABLED) {
            base = base.stream().filter(Module::isEnabled).collect(Collectors.toList());
        } else if (selectedFilter == FilterMode.FAVORITES) {
            base = base.stream().filter(Module::isFavorite).collect(Collectors.toList());
        }

        // Sort usable modules before rank-locked modules, and favorites first when in All view
        base.sort(Comparator.comparing((Module m) -> !m.canUse())
                .thenComparing(m -> !m.isFavorite()));
        return base;
    }

    private boolean matchesSearch(Module module, String query) {
        if (module.getName().toLowerCase(Locale.ROOT).contains(query)) return true;
        if (module.getDescription().toLowerCase(Locale.ROOT).contains(query)) return true;
        for (Setting<?> s : module.getGeneralSettings()) {
            if (s.getName().toLowerCase(Locale.ROOT).contains(query)
                    || s.getDescription().toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private double computeMaxMainScroll(int contentWidth, int viewportHeight) {
        if (isCosmeticsView()) {
            CosmeticManager cosmetics = StarClient.getInstance().getCosmeticManager();
            int rowCount = cosmetics.getAvailableCapes().size() + cosmetics.getAvailableSkins().size() + 5;
            int contentHeight = rowCount * (COSMETIC_ROW_HEIGHT + COSMETIC_GAP);
            return Math.max(0, contentHeight - viewportHeight);
        }
        if (isSettingsView()) {
            List<DrawerItem> items = buildClientSettingsItems(0, contentWidth, 0, 0);
            if (items.isEmpty()) return 0;
            DrawerItem last = items.getLast();
            int contentHeight = last.y + last.height + 8;
            return Math.max(0, contentHeight - viewportHeight);
        }
        if (isStatsView()) {
            return this.statsViewRenderer.computeMaxScroll(contentWidth, viewportHeight);
        }
        List<Module> visible = getVisibleModules();
        int cols = computeGridColumns(contentWidth);
        int rows = (visible.size() + cols - 1) / cols;
        int contentHeight = rows * (TILE_HEIGHT + TILE_GAP);
        return Math.max(0, contentHeight - viewportHeight);
    }

    private double computeMaxDrawerScroll(int innerW, int viewportHeight) {
        if (drawerModule == null) return 0;
        List<DrawerItem> items = buildDrawerItems(drawerModule, 0, innerW, 0, 0);
        if (items.isEmpty()) return 0;
        DrawerItem last = items.getLast();
        int contentHeight = last.y + last.height + 8;
        return Math.max(0, contentHeight - viewportHeight);
    }

    private void clampMainScroll(int contentWidth, int viewportHeight) {
        double max = computeMaxMainScroll(contentWidth, viewportHeight);
        targetModuleScroll = Math.clamp(targetModuleScroll, 0, max);
        moduleScroll = Math.clamp(moduleScroll, 0, max);
    }

    private void clampDrawerScroll(int innerW, int viewportHeight) {
        double max = computeMaxDrawerScroll(innerW, viewportHeight);
        targetDrawerScroll = Math.clamp(targetDrawerScroll, 0, max);
        drawerScroll = Math.clamp(drawerScroll, 0, max);
    }

    private void drawScrollbar(GuiGraphicsExtractor extractor, int sbX, int sbTop, int sbBottom,
                               double scroll, double maxScroll, boolean dragging, int mouseX, int mouseY) {
        if (maxScroll <= 0) return;
        int sbWidth = 4;
        int trackHeight = sbBottom - sbTop;
        GuiRenderUtils.fillRoundedRect(extractor, sbX, sbTop, sbWidth, trackHeight, 2, menuBgDark());

        double totalContentHeight = trackHeight + maxScroll;
        int thumbHeight = Math.max(24, (int) Math.round((double) trackHeight * trackHeight / totalContentHeight));
        int scrollableTrack = Math.max(1, trackHeight - thumbHeight);
        int thumbY = sbTop + (int) Math.round((scroll / maxScroll) * scrollableTrack);

        boolean hovered = isHovered(mouseX, mouseY, sbX - 3, sbTop, sbWidth + 6, trackHeight);
        int thumbColor = (dragging || hovered) ? menuAccent() : 0xFF475569;
        GuiRenderUtils.fillRoundedRect(extractor, sbX, thumbY, sbWidth, thumbHeight, 2, thumbColor);
    }

    private List<CosmeticRow> buildCosmeticRows(int x, int width, int topY, double scroll) {
        List<CosmeticRow> rows = new ArrayList<>();
        int y = (int) Math.round(topY - scroll);
        CosmeticManager cosmetics = StarClient.getInstance().getCosmeticManager();
        cosmetics.ensureLoaded();

        rows.add(new CosmeticRow(RELOAD_ROW, null, x, y, width));
        y += COSMETIC_ROW_HEIGHT + COSMETIC_GAP;

        rows.add(new CosmeticRow(HEADER_PREFIX + "Capes", null, x, y, width));
        y += 22;
        rows.add(new CosmeticRow(CAPE_NONE_ROW, null, x, y, width));
        y += COSMETIC_ROW_HEIGHT + COSMETIC_GAP;
        for (CapeCosmetic cape : cosmetics.getAvailableCapes()) {
            rows.add(new CosmeticRow(cape.id(), cape, x, y, width));
            y += COSMETIC_ROW_HEIGHT + COSMETIC_GAP;
        }

        rows.add(new CosmeticRow(HEADER_PREFIX + "Skins", null, x, y, width));
        y += 22;
        rows.add(new CosmeticRow(SKIN_NONE_ROW, null, x, y, width));
        y += COSMETIC_ROW_HEIGHT + COSMETIC_GAP;
        for (CapeCosmetic skin : cosmetics.getAvailableSkins()) {
            rows.add(new CosmeticRow(skin.id(), skin, x, y, width));
            y += COSMETIC_ROW_HEIGHT + COSMETIC_GAP;
        }
        return rows;
    }

    private void drawCosmeticRow(GuiGraphicsExtractor extractor, CosmeticRow row, int mouseX, int mouseY) {
        if (row.key().startsWith(HEADER_PREFIX)) {
            GuiRenderUtils.drawText(extractor, this.font,
                    row.key().substring(HEADER_PREFIX.length()).toUpperCase(Locale.ROOT),
                    row.x + 2, row.y + 6, menuAccent(), true);
            return;
        }

        CosmeticManager cosmetics = StarClient.getInstance().getCosmeticManager();
        boolean capeGroup = CAPE_NONE_ROW.equals(row.key)
                || (row.cape != null && cosmetics.getAvailableCapes().contains(row.cape));
        boolean isNoneRow = CAPE_NONE_ROW.equals(row.key) || SKIN_NONE_ROW.equals(row.key);
        boolean isReloadRow = RELOAD_ROW.equals(row.key);
        String equippedId = capeGroup ? cosmetics.getEquippedCape() : cosmetics.getEquippedSkin();
        boolean isEquipped = isNoneRow ? equippedId == null : row.cape != null && row.cape.id().equals(equippedId);

        boolean hovered = row.contains(mouseX, mouseY);
        int border = isEquipped ? 0xFF34D399 : (isReloadRow ? 0xFFFBBF24 : (hovered ? menuAccent() : menuBorder()));

        GuiRenderUtils.fillRoundedRect(extractor, row.x, row.y, row.width, COSMETIC_ROW_HEIGHT, 6,
                hovered ? menuSurfaceHover() : menuSurface());
        GuiRenderUtils.drawRoundedOutline(extractor, row.x, row.y, row.width, COSMETIC_ROW_HEIGHT, 6, border);

        if (isReloadRow) {
            GuiRenderUtils.drawText(extractor, this.font, "↻ Reload Cosmetics Folder", row.x + 12, row.y + 7, 0xFFFBBF24, true);
            GuiRenderUtils.drawText(extractor, this.font, "Rescan " + CosmeticManager.CAPES_FOLDER + " and " + CosmeticManager.SKINS_FOLDER,
                    row.x + 12, row.y + 19, 0xFF64748B);
            return;
        }

        if (isNoneRow) {
            GuiRenderUtils.drawText(extractor, this.font, "None (Default)", row.x + 12, row.y + 7, 0xFFE2E8F0);
            String desc = capeGroup ? "Show your vanilla Minecraft cape" : "Show the skin your account provides";
            GuiRenderUtils.drawText(extractor, this.font, desc, row.x + 12, row.y + 19, 0xFF64748B);
        } else {
            CapeCosmetic cosmetic = row.cape;
            if (capeGroup) {
                int[] data = cosmetics.getPreviewData(cosmetic.textureId());
                float texW = data != null ? data[0] : 64.0F;
                float texH = data != null ? data[1] : 64.0F;
                int srcX = data != null ? data[2] : 0;
                int srcY = data != null ? data[3] : 0;
                int srcW = data != null ? data[4] : (int) texW;
                int srcH = data != null ? data[5] : (int) texH;
                double scale = Math.min(26.0 / srcW, 26.0 / srcH);
                int drawW = Math.max(1, (int) (srcW * scale));
                int drawH = Math.max(1, (int) (srcH * scale));
                int drawX = row.x + 12 + (26 - drawW) / 2;
                int drawY = row.y + (COSMETIC_ROW_HEIGHT - drawH) / 2;
                blitRegion(extractor, cosmetic.textureId(), drawX, drawY, drawW, drawH,
                        srcX / texW, (srcX + srcW) / texW, srcY / texH, (srcY + srcH) / texH);
            } else {
                blitRegion(extractor, cosmetic.textureId(), row.x + 12, row.y + 7, 20, 20, 8 / 64.0F, 16 / 64.0F, 8 / 64.0F, 16 / 64.0F);
                blitRegion(extractor, cosmetic.textureId(), row.x + 11, row.y + 6, 22, 22, 40 / 64.0F, 48 / 64.0F, 8 / 64.0F, 16 / 64.0F);
            }
            GuiRenderUtils.drawText(extractor, this.font, cosmetic.displayName(), row.x + 48, row.y + 7,
                    isEquipped ? 0xFF34D399 : 0xFFE2E8F0, isEquipped);
            GuiRenderUtils.drawText(extractor, this.font, "Click to " + (isEquipped ? "unequip" : "equip"),
                    row.x + 48, row.y + 19, 0xFF64748B);
        }

        String status = isEquipped ? "● Equipped" : "Select";
        int statusW = GuiRenderUtils.textWidth(this.font, status) + 12;
        int statusX = row.x + row.width - statusW - 8;
        int statusY = row.y + (COSMETIC_ROW_HEIGHT - 16) / 2;
        GuiRenderUtils.fillRoundedRect(extractor, statusX, statusY, statusW, 16, 4,
                isEquipped ? 0x3334D399 : menuBgDark());
        GuiRenderUtils.drawCenteredText(extractor, this.font, status, statusX + statusW / 2, statusY + 4,
                isEquipped ? 0xFF34D399 : 0xFF94A3B8);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        int startX = (this.width - guiWidth) / 2;
        int startY = (this.height - guiHeight) / 2;
        int searchY = startY + (HEADER_HEIGHT - SEARCH_HEIGHT) / 2;

        // 1. Check Edit HUD Button
        int hudBtnX = startX + guiWidth - 12 - HUD_EDITOR_BTN_WIDTH;
        if (isHovered(mouseX, mouseY, hudBtnX, searchY, HUD_EDITOR_BTN_WIDTH, SEARCH_HEIGHT)) {
            Minecraft.getInstance().gui.setScreen(new HudEditorScreen(this));
            return true;
        }

        // 2. Check Search Box & Clear Button
        int searchX = startX + SIDEBAR_WIDTH + 12;
        int searchW = 190;
        if (isSearching()) {
            int clearX = searchX + searchW - 16;
            int clearY = searchY + (SEARCH_HEIGHT - 8) / 2;
            if (isHovered(mouseX, mouseY, clearX - 3, clearY - 2, 14, 14)) {
                this.searchBox.setValue("");
                this.searchQuery = "";
                return true;
            }
        }
        if (isHovered(mouseX, mouseY, searchX, searchY, searchW, SEARCH_HEIGHT)) {
            this.searchBox.setFocused(true);
            this.setFocused(this.searchBox);
            return super.mouseClicked(event, action);
        } else if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
            this.setFocused(null);
        }

        // 3. Check Filter Pills
        int filterX = searchX + searchW + 10;
        int filterY = searchY + 1;
        int filterH = SEARCH_HEIGHT - 2;
        for (FilterMode mode : FilterMode.values()) {
            int pillW = GuiRenderUtils.textWidth(this.font, mode.label) + 14;
            if (isHovered(mouseX, mouseY, filterX, filterY, pillW, filterH)) {
                selectedFilter = mode;
                targetModuleScroll = 0;
                return true;
            }
            filterX += pillW + 4;
        }

        // 4. Check Sidebar Items
        int itemX = startX + 6;
        int itemW = SIDEBAR_WIDTH - 12;
        int itemH = 22;
        int curY = startY + HEADER_HEIGHT + 8;

        if (isHovered(mouseX, mouseY, itemX, curY, itemW, itemH)) {
            activeSidebarSection = SidebarSection.FAVORITES;
            clearSearchIfActive();
            targetModuleScroll = 0;
            return true;
        }
        curY += itemH + 3;

        if (isHovered(mouseX, mouseY, itemX, curY, itemW, itemH)) {
            activeSidebarSection = SidebarSection.ACTIVE;
            clearSearchIfActive();
            targetModuleScroll = 0;
            return true;
        }
        curY += itemH + 12;

        for (Category category : Category.values()) {
            if (isHovered(mouseX, mouseY, itemX, curY, itemW, itemH)) {
                activeSidebarSection = SidebarSection.CATEGORY;
                selectedCategory = category;
                clearSearchIfActive();
                targetModuleScroll = 0;
                if (category == Category.COSMETICS || category == Category.SETTINGS) {
                    drawerModule = null;
                    expandedDropdown = null;
                }
                return true;
            }
            curY += itemH + 3;
        }

        // 5. Check Settings Drawer interactions if open
        if (drawerModule != null && !isCosmeticsView() && !isSettingsView() && !isStatsView() && drawerProgress > 0.5F) {
            int drawerX = startX + guiWidth - DRAWER_WIDTH;
            int drawerY = startY + HEADER_HEIGHT;
            int drawerH = guiHeight - HEADER_HEIGHT;
            if (isHovered(mouseX, mouseY, drawerX, drawerY, DRAWER_WIDTH, drawerH)) {
                return handleDrawerClick(mouseX, mouseY, button, drawerX, drawerY, drawerH);
            }
        }

        // 6. Check Main Scrollbar
        int contentLeft = startX + SIDEBAR_WIDTH + 12;
        int contentRight = startX + guiWidth - 12 - drawerVisibleWidth();
        int contentWidth = Math.max(120, contentRight - contentLeft);
        int panelTop = startY + PANEL_TOP_OFFSET;
        int panelBottom = startY + guiHeight - 10;
        int sbX = contentRight + 4;
        int sbTop = panelTop + (isStatsView() ? 38 : 0);
        double maxMainScroll = computeMaxMainScroll(contentWidth, panelBottom - sbTop);
        if (maxMainScroll > 0 && isHovered(mouseX, mouseY, sbX - 4, sbTop, 12, panelBottom - sbTop)) {
            startScrollbarDrag(mouseY, sbTop, panelBottom, moduleScroll, maxMainScroll, false);
            return true;
        }

        // 7. Check Main Content Area clicks
        if (isHovered(mouseX, mouseY, contentLeft, panelTop, contentWidth, panelBottom - panelTop)) {
            if (isCosmeticsView()) {
                for (CosmeticRow row : buildCosmeticRows(contentLeft, contentWidth, panelTop, moduleScroll)) {
                    if (row.contains(mouseX, mouseY)) {
                        handleCosmeticClick(row);
                        StarClient.getInstance().getConfigManager().saveConfig();
                        return true;
                    }
                }
            } else if (isSettingsView()) {
                List<DrawerItem> items = buildClientSettingsItems(contentLeft, contentWidth, panelTop, moduleScroll);
                for (DrawerItem item : items) {
                    if (item.setting != null && item.contains(mouseX, mouseY)) {
                        handleSettingItemClick(item, mouseX, mouseY, button, false);
                        StarClient.getInstance().getConfigManager().saveConfig();
                        return true;
                    }
                }
            } else if (isStatsView()) {
                if (this.statsViewRenderer.mouseClicked(contentLeft, contentWidth, panelTop, mouseX, mouseY)) {
                    this.targetModuleScroll = 0;
                    this.moduleScroll = 0;
                    return true;
                }
            } else {
                List<ModuleTile> tiles = buildModuleTiles(contentLeft, contentWidth, panelTop, moduleScroll);
                for (ModuleTile tile : tiles) {
                    if (tile.contains(mouseX, mouseY)) {
                        handleTileClick(tile, mouseX, mouseY, button);
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(event, action);
    }

    private void handleTileClick(ModuleTile tile, double mouseX, double mouseY, int button) {
        Module module = tile.module;
        if (!module.canUse()) {
            return;
        }

        // Right-click anywhere on tile opens/closes its Settings Drawer
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            openDrawerFor(module);
            return;
        }

        int topRowY = tile.y + 8;
        int switchW = 24;
        int switchX = tile.x + tile.width - switchW - 8;
        int favX = switchX - 16;
        int favY = topRowY + 1;

        // Favorite star click
        if (isHovered(mouseX, mouseY, favX - 2, favY - 2, 14, 14)) {
            module.toggleFavorite();
            StarClient.getInstance().getConfigManager().saveConfig();
            return;
        }

        int botY = tile.y + TILE_HEIGHT - 17;
        int settingCount = module.getVisibleSettingCount();
        String gearLabel = settingCount > 0 ? "⚙ " + settingCount : "⚙";
        int gearW = GuiRenderUtils.textWidth(this.font, gearLabel) + 10;
        int gearH = 13;
        int gearX = tile.x + tile.width - gearW - 8;

        // Gear button click
        if (isHovered(mouseX, mouseY, gearX, botY, gearW, gearH)) {
            openDrawerFor(module);
            return;
        }

        // Keybind badge click
        boolean isBindingThis = (bindingModule == module);
        String keyLabel = isBindingThis
                ? "Press key..."
                : (module.getKeyBind() == GLFW.GLFW_KEY_UNKNOWN ? "⌨ None" : "⌨ " + keyName(module.getKeyBind()));
        int keyW = GuiRenderUtils.textWidth(this.font, keyLabel) + 10;
        int keyX = gearX - keyW - 4;
        if (isHovered(mouseX, mouseY, keyX, botY, keyW, gearH)) {
            bindingModule = (bindingModule == module) ? null : module;
            return;
        }

        // Left-clicking anywhere else on the tile toggles the module!
        module.toggle();
        StarClient.getInstance().getConfigManager().saveConfig();
    }

    private void openDrawerFor(Module module) {
        if (drawerModule == module) {
            drawerModule = null;
        } else {
            drawerModule = module;
            drawerScroll = 0;
            targetDrawerScroll = 0;
        }
        expandedDropdown = null;
    }

    private boolean handleDrawerClick(double mouseX, double mouseY, int button, int drawerX, int drawerY, int drawerH) {
        // Close button
        int closeX = drawerX + DRAWER_WIDTH - 24;
        int closeY = drawerY + 8;
        if (isHovered(mouseX, mouseY, closeX, closeY, 16, 16)) {
            drawerModule = null;
            expandedDropdown = null;
            return true;
        }

        // Header toggle switch
        int switchW = 24;
        int switchH = 13;
        int switchX = closeX - switchW - 6;
        int switchY = drawerY + 10;
        if (isHovered(mouseX, mouseY, switchX, switchY, switchW, switchH)) {
            drawerModule.toggle();
            StarClient.getInstance().getConfigManager().saveConfig();
            return true;
        }

        // Footer buttons (Reset Defaults / Move HUD)
        int footerH = 30;
        int footerY = drawerY + drawerH - footerH;
        int resetBtnX = drawerX + 10;
        int resetBtnY = footerY + 6;
        int resetBtnW = drawerModule.hasHudElement() ? (DRAWER_WIDTH - 26) / 2 : DRAWER_WIDTH - 20;
        int resetBtnH = 18;
        if (isHovered(mouseX, mouseY, resetBtnX, resetBtnY, resetBtnW, resetBtnH)) {
            drawerModule.resetSettingsToDefault();
            StarClient.getInstance().getConfigManager().saveConfig();
            return true;
        }
        if (drawerModule.hasHudElement()) {
            int posBtnX = resetBtnX + resetBtnW + 6;
            int posBtnW = DRAWER_WIDTH - 20 - resetBtnW - 6;
            if (isHovered(mouseX, mouseY, posBtnX, resetBtnY, posBtnW, resetBtnH)) {
                Minecraft.getInstance().gui.setScreen(new HudEditorScreen(this));
                return true;
            }
        }

        // Drawer Scrollbar
        int headerH = 50;
        int bodyTop = drawerY + headerH + 4;
        int bodyBottom = footerY - 4;
        int innerX = drawerX + 10;
        int innerW = DRAWER_WIDTH - 22;
        double maxDrawerScroll = computeMaxDrawerScroll(innerW, bodyBottom - bodyTop);
        if (maxDrawerScroll > 0 && isHovered(mouseX, mouseY, drawerX + DRAWER_WIDTH - 10, bodyTop, 10, bodyBottom - bodyTop)) {
            startScrollbarDrag(mouseY, bodyTop, bodyBottom, drawerScroll, maxDrawerScroll, true);
            return true;
        }

        // Drawer Setting Items
        if (mouseY >= bodyTop && mouseY <= bodyBottom) {
            List<DrawerItem> items = buildDrawerItems(drawerModule, innerX, innerW, bodyTop, drawerScroll);
            for (DrawerItem item : items) {
                if (!item.contains(mouseX, mouseY)) continue;
                if (item.isKeybindRow && item.module != null) {
                    bindingModule = (bindingModule == item.module) ? null : item.module;
                    return true;
                }
                if (item.setting != null) {
                    handleSettingItemClick(item, mouseX, mouseY, button, true);
                    StarClient.getInstance().getConfigManager().saveConfig();
                    return true;
                }
            }
        }
        return true;
    }

    private void handleSettingItemClick(DrawerItem item, double mouseX, double mouseY, int button, boolean inDrawer) {
        Setting<?> setting = item.setting;
        if (setting instanceof BooleanSetting booleanSetting) {
            booleanSetting.toggle();
        } else if (setting instanceof ColorModeSetting colorSetting) {
            List<String> modes = colorSetting.getModes();
            int swatchSize = 14;
            int swatchGap = 4;
            int cols = Math.max(1, (item.width - 16 + swatchGap) / (swatchSize + swatchGap));
            for (int i = 0; i < modes.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int sx = item.x + 8 + col * (swatchSize + swatchGap);
                int sy = item.y + 22 + row * (swatchSize + swatchGap);
                if (isHovered(mouseX, mouseY, sx, sy, swatchSize, swatchSize)) {
                    colorSetting.setValue(modes.get(i));
                    return;
                }
            }
            colorSetting.cycle();
        } else if (setting instanceof ModeSetting modeSetting) {
            List<String> modes = modeSetting.getModes();
            if (isCompactSegmentedMode(modeSetting, item.width)) {
                int segY = item.y + 20;
                int segH = 16;
                int totalW = item.width - 16;
                int segGap = 4;
                int segW = (totalW - segGap * (modes.size() - 1)) / modes.size();
                for (int i = 0; i < modes.size(); i++) {
                    int sx = item.x + 8 + i * (segW + segGap);
                    if (isHovered(mouseX, mouseY, sx, segY, segW, segH)) {
                        modeSetting.setValue(modes.get(i));
                        return;
                    }
                }
                modeSetting.cycle();
            } else {
                if (expandedDropdown == modeSetting && mouseY >= item.y + 24) {
                    int optY = item.y + 24;
                    for (String opt : modes) {
                        if (isHovered(mouseX, mouseY, item.x + 6, optY, item.width - 12, 16)) {
                            modeSetting.setValue(opt);
                            expandedDropdown = null;
                            return;
                        }
                        optY += 18;
                    }
                }
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    modeSetting.cycle();
                } else {
                    expandedDropdown = (expandedDropdown == modeSetting) ? null : modeSetting;
                }
            }
        } else if (setting instanceof NumberSetting numberSetting) {
            draggingSetting = setting;
            draggingSettingInDrawer = inDrawer;
            applySliderValue(numberSetting, mouseX, item);
        } else if (setting instanceof TextSetting textSetting) {
            Minecraft.getInstance().gui.setScreen(new TextSettingScreen(textSetting, this));
        } else if (setting instanceof NickEntryListSetting) {
            Minecraft.getInstance().gui.setScreen(new NickToolScreen(this));
        }
    }

    private void startScrollbarDrag(double mouseY, int top, int bottom, double currentScroll, double maxScroll, boolean drawer) {
        int trackHeight = bottom - top;
        double totalContentHeight = trackHeight + maxScroll;
        int thumbHeight = Math.max(24, (int) Math.round((double) trackHeight * trackHeight / totalContentHeight));
        int scrollableTrack = Math.max(1, trackHeight - thumbHeight);
        int thumbY = top + (int) Math.round((currentScroll / maxScroll) * scrollableTrack);

        if (drawer) {
            this.draggingDrawerScrollbar = true;
        } else {
            this.draggingMainScrollbar = true;
        }

        if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
            this.scrollbarDragOffsetY = mouseY - thumbY;
        } else {
            this.scrollbarDragOffsetY = thumbHeight / 2.0;
            double ratio = Math.clamp((mouseY - this.scrollbarDragOffsetY - top) / (double) scrollableTrack, 0.0, 1.0);
            if (drawer) {
                this.drawerScroll = ratio * maxScroll;
                this.targetDrawerScroll = this.drawerScroll;
            } else {
                this.moduleScroll = ratio * maxScroll;
                this.targetModuleScroll = this.moduleScroll;
            }
        }
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
        int startX = (this.width - guiWidth) / 2;
        int startY = (this.height - guiHeight) / 2;

        if (draggingMainScrollbar) {
            int contentLeft = startX + SIDEBAR_WIDTH + 12;
            int contentRight = startX + guiWidth - 12 - drawerVisibleWidth();
            int contentWidth = Math.max(120, contentRight - contentLeft);
            int panelTop = startY + PANEL_TOP_OFFSET;
            int panelBottom = startY + guiHeight - 10;
            double maxScroll = computeMaxMainScroll(contentWidth, panelBottom - panelTop);
            if (maxScroll > 0) {
                int trackHeight = panelBottom - panelTop;
                double totalContentHeight = trackHeight + maxScroll;
                int thumbHeight = Math.max(24, (int) Math.round((double) trackHeight * trackHeight / totalContentHeight));
                int scrollableTrack = Math.max(1, trackHeight - thumbHeight);
                double ratio = Math.clamp((event.y() - scrollbarDragOffsetY - panelTop) / (double) scrollableTrack, 0.0, 1.0);
                this.moduleScroll = ratio * maxScroll;
                this.targetModuleScroll = this.moduleScroll;
            }
            return true;
        }

        if (draggingDrawerScrollbar && drawerModule != null) {
            int drawerY = startY + HEADER_HEIGHT;
            int drawerH = guiHeight - HEADER_HEIGHT;
            int bodyTop = drawerY + 54;
            int bodyBottom = drawerY + drawerH - 34;
            int innerW = DRAWER_WIDTH - 22;
            double maxScroll = computeMaxDrawerScroll(innerW, bodyBottom - bodyTop);
            if (maxScroll > 0) {
                int trackHeight = bodyBottom - bodyTop;
                double totalContentHeight = trackHeight + maxScroll;
                int thumbHeight = Math.max(24, (int) Math.round((double) trackHeight * trackHeight / totalContentHeight));
                int scrollableTrack = Math.max(1, trackHeight - thumbHeight);
                double ratio = Math.clamp((event.y() - scrollbarDragOffsetY - bodyTop) / (double) scrollableTrack, 0.0, 1.0);
                this.drawerScroll = ratio * maxScroll;
                this.targetDrawerScroll = this.drawerScroll;
            }
            return true;
        }

        if (draggingSetting instanceof NumberSetting numberSetting) {
            if (draggingSettingInDrawer && drawerModule != null) {
                int drawerX = startX + guiWidth - DRAWER_WIDTH;
                int drawerY = startY + HEADER_HEIGHT;
                int bodyTop = drawerY + 54;
                int innerX = drawerX + 10;
                int innerW = DRAWER_WIDTH - 22;
                for (DrawerItem item : buildDrawerItems(drawerModule, innerX, innerW, bodyTop, drawerScroll)) {
                    if (item.setting == draggingSetting) {
                        applySliderValue(numberSetting, event.x(), item);
                        return true;
                    }
                }
            } else if (isSettingsView()) {
                int contentLeft = startX + SIDEBAR_WIDTH + 12;
                int contentRight = startX + guiWidth - 12;
                int contentWidth = Math.max(120, contentRight - contentLeft);
                int panelTop = startY + PANEL_TOP_OFFSET;
                for (DrawerItem item : buildClientSettingsItems(contentLeft, contentWidth, panelTop, moduleScroll)) {
                    if (item.setting == draggingSetting) {
                        applySliderValue(numberSetting, event.x(), item);
                        return true;
                    }
                }
            }
            return true;
        }

        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (draggingMainScrollbar || draggingDrawerScrollbar) {
            draggingMainScrollbar = false;
            draggingDrawerScrollbar = false;
            return true;
        }
        if (draggingSetting != null) {
            draggingSetting = null;
            StarClient.getInstance().getConfigManager().saveConfig();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int startX = (this.width - guiWidth) / 2;
        int startY = (this.height - guiHeight) / 2;

        // Check if scrolling inside the Settings Drawer
        if (drawerModule != null && !isCosmeticsView() && !isSettingsView() && !isStatsView() && drawerProgress > 0.5F) {
            int drawerX = startX + guiWidth - DRAWER_WIDTH;
            int drawerY = startY + HEADER_HEIGHT;
            int drawerH = guiHeight - HEADER_HEIGHT;
            if (isHovered(mouseX, mouseY, drawerX, drawerY, DRAWER_WIDTH, drawerH)) {
                // Fine-tune NumberSetting with mouse wheel when Shift is held or hovering slider directly
                int bodyTop = drawerY + 54;
                int innerX = drawerX + 10;
                int innerW = DRAWER_WIDTH - 22;
                for (DrawerItem item : buildDrawerItems(drawerModule, innerX, innerW, bodyTop, drawerScroll)) {
                    if (item.setting instanceof NumberSetting ns && isHovered(mouseX, mouseY, item.x, item.y + 18, item.width, 14)) {
                        double step = ns.getIncrement() > 0 ? ns.getIncrement() : 0.1;
                        ns.setValue(ns.getValue() + (verticalAmount > 0 ? step : -step));
                        StarClient.getInstance().getConfigManager().saveConfig();
                        return true;
                    }
                }
                targetDrawerScroll -= verticalAmount * 22;
                clampDrawerScroll(innerW, (drawerY + drawerH - 34) - bodyTop);
                return true;
            }
        }

        // Scroll main content panel
        if (isHovered(mouseX, mouseY, startX + SIDEBAR_WIDTH, startY + HEADER_HEIGHT, guiWidth - SIDEBAR_WIDTH, guiHeight - HEADER_HEIGHT)) {
            int contentLeft = startX + SIDEBAR_WIDTH + 12;
            int contentRight = startX + guiWidth - 12 - drawerVisibleWidth();
            int contentWidth = Math.max(120, contentRight - contentLeft);
            int panelTop = startY + PANEL_TOP_OFFSET;
            int panelBottom = startY + guiHeight - 10;
            targetModuleScroll -= verticalAmount * 26;
            clampMainScroll(contentWidth, panelBottom - panelTop - (isStatsView() ? 38 : 0));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        int key = event.key();

        if (bindingModule != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                this.bindingModule = null;
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                bindingModule.setKeyBind(GLFW.GLFW_KEY_UNKNOWN);
                this.bindingModule = null;
                StarClient.getInstance().getConfigManager().saveConfig();
                return true;
            }
            bindingModule.setKeyBind(key);
            this.bindingModule = null;
            StarClient.getInstance().getConfigManager().saveConfig();
            return true;
        }

        // Escape closes expanded dropdown first, then open Settings Drawer, then focused search / search query, then screen
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (expandedDropdown != null) {
                expandedDropdown = null;
                return true;
            }
            if (drawerModule != null) {
                drawerModule = null;
                return true;
            }
            if (this.searchBox != null && (this.searchBox.isFocused() || isSearching())) {
                this.searchBox.setValue("");
                this.searchQuery = "";
                this.searchBox.setFocused(false);
                this.setFocused(null);
                return true;
            }
        }

        // Ctrl+F (or Strg+F) focuses the search bar
        if (key == GLFW.GLFW_KEY_F && (event.hasControlDown() || (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) && this.searchBox != null) {
            this.searchBox.setFocused(true);
            this.setFocused(this.searchBox);
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

    private void applySliderValue(NumberSetting numberSetting, double mouseX, DrawerItem item) {
        double trackStart = item.x + 8;
        double trackWidth = item.width - 16;
        double t = Math.clamp((mouseX - trackStart) / trackWidth, 0.0, 1.0);
        numberSetting.setValue(numberSetting.getMin() + t * (numberSetting.getMax() - numberSetting.getMin()));
    }

    private void handleCosmeticClick(CosmeticRow row) {
        CosmeticManager cosmetics = StarClient.getInstance().getCosmeticManager();
        if (RELOAD_ROW.equals(row.key)) {
            cosmetics.reload();
        } else if (CAPE_NONE_ROW.equals(row.key)) {
            cosmetics.setEquippedCape(null);
        } else if (SKIN_NONE_ROW.equals(row.key)) {
            cosmetics.setEquippedSkin(null);
        } else if (row.key().startsWith(HEADER_PREFIX)) {
        } else if (cosmetics.getAvailableCapes().stream().anyMatch(c -> c.id().equals(row.key))) {
            cosmetics.setEquippedCape(row.key.equals(cosmetics.getEquippedCape()) ? null : row.key);
        } else {
            cosmetics.setEquippedSkin(row.key.equals(cosmetics.getEquippedSkin()) ? null : row.key);
        }
    }

    private void clearSearchIfActive() {
        if (isSearching() && this.searchBox != null) {
            this.searchBox.setValue("");
            this.searchQuery = "";
        }
    }

    private boolean isSearching() {
        return !searchQuery.trim().isEmpty();
    }

    private boolean isCosmeticsView() {
        return !isSearching()
                && activeSidebarSection == SidebarSection.CATEGORY
                && selectedCategory == Category.COSMETICS;
    }

    private boolean isSettingsView() {
        return !isSearching()
                && activeSidebarSection == SidebarSection.CATEGORY
                && selectedCategory == Category.SETTINGS;
    }

    private boolean isStatsView() {
        return !isSearching()
                && activeSidebarSection == SidebarSection.CATEGORY
                && selectedCategory == Category.STATS;
    }

    private int menuAccent() {
        return StarClient.getInstance().getClientSettings().getMenuAccentColor();
    }

    private int menuAccentDark() {
        return StarClient.getInstance().getClientSettings().getMenuAccentDarkColor();
    }

    private int menuBg() {
        return 0xF4000000 | (StarClient.getInstance().getClientSettings().getMenuBackgroundColor() & 0x00FFFFFF);
    }

    private int menuBgLight() {
        return StarClient.getInstance().getClientSettings().getMenuBackgroundLightColor();
    }

    private int menuBgDark() {
        return StarClient.getInstance().getClientSettings().getMenuBackgroundDarkColor();
    }

    private int menuSurface() {
        return StarClient.getInstance().getClientSettings().getMenuSurfaceColor();
    }

    private int menuSurfaceHover() {
        return StarClient.getInstance().getClientSettings().getMenuSurfaceHoverColor();
    }

    private int menuBorder() {
        return StarClient.getInstance().getClientSettings().getMenuBorderColor();
    }

    private static boolean isHovered(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private String formatValue(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static String mask(String value) {
        String trimmed = value.strip();
        if (trimmed.length() <= 4) return "*".repeat(trimmed.length());
        return "****" + trimmed.substring(trimmed.length() - 4);
    }

    private static String keyName(int key) {
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null) return name.toUpperCase(Locale.ROOT);
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS";
            case GLFW.GLFW_KEY_SPACE -> "SPACE";
            default -> "KEY " + key;
        };
    }

    private void blitRegion(GuiGraphicsExtractor extractor, Identifier textureId, int destX, int destY, int destW, int destH, float uMin, float uMax, float vMin, float vMax) {
        extractor.blit(textureId, destX, destY, destX + destW, destY + destH, uMin, uMax, vMin, vMax);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        StarClient.getInstance().getConfigManager().saveConfig();
        super.removed();
    }

    @Override
    public void onClose() {
        StarClient.getInstance().getConfigManager().saveConfig();
        Minecraft client = Minecraft.getInstance();
        if (parent != null) {
            client.gui.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    private record ModuleTile(Module module, int x, int y, int width) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + TILE_HEIGHT;
        }
    }

    private record DrawerItem(
            String sectionHeader,
            boolean isKeybindRow,
            Module module,
            Setting<?> setting,
            String infoMessage,
            int x,
            int y,
            int width,
            int height
    ) {
        static DrawerItem header(String title, int x, int y, int width, int height) {
            return new DrawerItem(title, false, null, null, null, x, y, width, height);
        }

        static DrawerItem keybind(Module module, int x, int y, int width, int height) {
            return new DrawerItem(null, true, module, null, null, x, y, width, height);
        }

        static DrawerItem setting(Setting<?> setting, int x, int y, int width, int height) {
            return new DrawerItem(null, false, null, setting, null, x, y, width, height);
        }

        static DrawerItem info(String msg, int x, int y, int width, int height) {
            return new DrawerItem(null, false, null, null, msg, x, y, width, height);
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    private record CosmeticRow(String key, CapeCosmetic cape, int x, int y, int width) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + COSMETIC_ROW_HEIGHT;
        }
    }
}
