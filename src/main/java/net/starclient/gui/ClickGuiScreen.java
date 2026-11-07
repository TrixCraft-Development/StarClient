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
import net.starclient.config.ClientProfile;
import net.starclient.config.ProfileManager;

import net.starclient.module.ChatPlusState;
import net.starclient.module.Category;
import net.starclient.module.Module;
import net.starclient.module.NickToolState;
import net.starclient.module.QuickCommandsState;
import net.starclient.module.WaypointsState;
import net.starclient.rank.Rank;
import net.starclient.setting.BannedWordListSetting;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.MacroListSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NickEntryListSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;
import net.starclient.setting.TextSetting;
import net.starclient.setting.WaypointListSetting;
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
    private static final int SEARCH_HEIGHT = 22;
    private static final int HUD_EDITOR_BTN_WIDTH = 82;
    private static final int PROFILE_BTN_WIDTH = 96;

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
    private boolean profileDropdownOpen = false;
    private final StatsViewRenderer statsViewRenderer = new StatsViewRenderer();
    private final ProfilesViewRenderer profilesViewRenderer = new ProfilesViewRenderer();

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
        this.guiWidth = Math.clamp(this.width - 20, 480, TARGET_GUI_WIDTH);
        this.guiHeight = Math.clamp(this.height - 20, 310, TARGET_GUI_HEIGHT);
        this.lastFrameNanos = System.nanoTime();

        int startX = (this.width - guiWidth) / 2;
        int startY = (this.height - guiHeight) / 2;



        int searchX = startX + SIDEBAR_WIDTH + 12;
        int searchW = 160;
        int searchY = startY + (HEADER_HEIGHT - SEARCH_HEIGHT) / 2;

        String prevQuery = this.searchQuery;
        this.searchBox = new EditBox(this.font, searchX + 15, searchY, searchW - 31, SEARCH_HEIGHT, Component.literal(""));
        this.searchBox.setValue(prevQuery);
        this.searchBox.setResponder(value -> {
            this.searchQuery = value;
            this.targetModuleScroll = 0;
            this.moduleScroll = 0;
        });
        this.searchBox.setHint(Component.literal("Search modules or settings.."));
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
            if (isSettingsView()) {
                drawGeneralSettingsView(extractor, contentLeft, contentWidth, panelTop, panelBottom, mouseX, mouseY);
            } else if (isProfilesView()) {
                this.profilesViewRenderer.draw(extractor, this.font, contentLeft, contentWidth, panelTop, panelBottom, mouseX, mouseY, moduleScroll);
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

        // Header Profile Quick-Switcher Dropdown overlay if open
        if (profileDropdownOpen) {
            drawProfileQuickDropdown(extractor, startX, startY, mouseX, mouseY);
        }

        // Stat Card Modal overlay if active
        if (isStatsView() && this.statsViewRenderer.isShowingCardModal()) {
            this.statsViewRenderer.drawCardModal(extractor, this.font, this.width, this.height, mouseX, mouseY);
        }

        // Floating tooltip (hidden while modal or dropdown is open)
        if (pendingTooltipTitle != null && !pendingTooltipTitle.isEmpty()
                && !profileDropdownOpen
                && (!isStatsView() || !this.statsViewRenderer.isShowingCardModal())) {
            drawFloatingTooltip(extractor, pendingTooltipTitle, pendingTooltipDesc, mouseX, mouseY);
        }
    }

    private void updateAnimations(float dt) {
        float openSpeed = 14.0F * dt;
        openProgress = openProgress + (1.0F - openProgress) * Math.min(1.0F, openSpeed);

        float targetDrawer = (drawerModule != null && !isSettingsView() && !isStatsView() && !isProfilesView()) ? 1.0F : 0.0F;
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
        int searchW = 160;
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
            this.searchBox.setX(searchX + 15);
            this.searchBox.setY(searchY);
            this.searchBox.setHeight(SEARCH_HEIGHT);
            this.searchBox.setWidth(searchW - (isSearching() ? 31 : 19));
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
        int filterX = searchX + searchW + 8;
        int filterY = searchY + 1;
        int filterH = SEARCH_HEIGHT - 2;
        for (FilterMode mode : FilterMode.values()) {
            int pillW = GuiRenderUtils.textWidth(this.font, mode.label) + 12;
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

        // Edit HUD Button on far right
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

        // Profile Quick-Switcher Button to the left of Edit HUD
        int profBtnX = hudBtnX - 6 - PROFILE_BTN_WIDTH;
        boolean profBtnHover = isHovered(mouseX, mouseY, profBtnX, searchY, PROFILE_BTN_WIDTH, SEARCH_HEIGHT);
        ProfileManager pm = StarClient.getInstance().getProfileManager();
        ClientProfile activeProf = pm != null ? pm.getActiveProfile() : null;
        String profText = activeProf != null
                ? GuiRenderUtils.truncateWithEllipsis(this.font, activeProf.getIcon() + " " + activeProf.getName(), PROFILE_BTN_WIDTH - 22) + " ▾"
                : "🗂 Default ▾";
        GuiRenderUtils.fillRoundedRect(extractor, profBtnX, searchY, PROFILE_BTN_WIDTH, SEARCH_HEIGHT, 6,
                profileDropdownOpen || profBtnHover ? menuAccentDark() : menuBgDark());
        GuiRenderUtils.drawRoundedOutline(extractor, profBtnX, searchY, PROFILE_BTN_WIDTH, SEARCH_HEIGHT, 6,
                profileDropdownOpen || profBtnHover ? menuAccent() : menuBorder());
        GuiRenderUtils.drawCenteredText(extractor, this.font, profText,
                profBtnX + PROFILE_BTN_WIDTH / 2, searchY + (SEARCH_HEIGHT - 8) / 2,
                profileDropdownOpen || profBtnHover ? 0xFFFFFFFF : 0xFFE2E8F0);
        if (profBtnHover && !profileDropdownOpen) {
            setTooltip("Active Profile: " + (activeProf != null ? activeProf.getName() : "Default"),
                    "Left-click to quick-switch profiles • Right-click to open the Profiles Manager.");
        }
    }

    private void drawProfileQuickDropdown(GuiGraphicsExtractor extractor, int startX, int startY, int mouseX, int mouseY) {
        ProfileManager pm = StarClient.getInstance().getProfileManager();
        if (pm == null) return;
        List<ClientProfile> profiles = pm.getProfiles();
        String activeId = pm.getActiveProfileId();

        int hudBtnX = startX + guiWidth - 12 - HUD_EDITOR_BTN_WIDTH;
        int profBtnX = hudBtnX - 6 - PROFILE_BTN_WIDTH;
        int dropW = 148;
        int dropX = Math.min(profBtnX, startX + guiWidth - dropW - 10);
        int dropY = startY + HEADER_HEIGHT - 4;
        int rowH = 20;
        int dropH = (profiles.size() + 1) * rowH + 8;

        extractor.nextStratum();
        GuiRenderUtils.drawDropShadow(extractor, dropX, dropY, dropW, dropH, 6, 4, 140);
        GuiRenderUtils.fillRoundedRect(extractor, dropX, dropY, dropW, dropH, 6, menuSurface());
        GuiRenderUtils.drawRoundedOutline(extractor, dropX, dropY, dropW, dropH, 6, menuAccent());

        int ry = dropY + 4;
        for (ClientProfile p : profiles) {
            boolean isAct = p.getId().equals(activeId);
            boolean hov = isHovered(mouseX, mouseY, dropX + 4, ry, dropW - 8, rowH);
            if (isAct || hov) {
                GuiRenderUtils.fillRoundedRect(extractor, dropX + 4, ry, dropW - 8, rowH, 4,
                        isAct ? menuAccentDark() : menuSurfaceHover());
            }
            String label = GuiRenderUtils.truncateWithEllipsis(this.font, p.getIcon() + " " + p.getName(), dropW - 28);
            GuiRenderUtils.drawText(extractor, this.font, label, dropX + 10, ry + 6,
                    isAct ? 0xFFFFFFFF : 0xFFE2E8F0);
            if (isAct) {
                GuiRenderUtils.drawText(extractor, this.font, "", dropX + dropW - 16, ry + 6, menuAccent());
            }
            ry += rowH;
        }

        extractor.fill(dropX + 8, ry, dropX + dropW - 8, ry + 1, menuBorder());
        boolean manageHov = isHovered(mouseX, mouseY, dropX + 4, ry + 1, dropW - 8, rowH - 1);
        if (manageHov) {
            GuiRenderUtils.fillRoundedRect(extractor, dropX + 4, ry + 1, dropW - 8, rowH - 1, 4, menuSurfaceHover());
        }
        GuiRenderUtils.drawCenteredText(extractor, this.font, "⚙ Manage Profiles...",
                dropX + dropW / 2, ry + 6, manageHov ? 0xFFFFFFFF : menuAccent());
    }

    private int sidebarItemHeight() {
        return guiHeight >= 400 ? 20 : 18;
    }

    private int sidebarItemGap() {
        return 2;
    }

    private void drawSidebar(GuiGraphicsExtractor extractor, int startX, int startY, int mouseX, int mouseY) {
        int itemX = startX + 6;
        int itemW = SIDEBAR_WIDTH - 12;
        int itemH = sidebarItemHeight();
        int itemGap = sidebarItemGap();
        int curY = startY + HEADER_HEIGHT + 6;

        // 1. Quick Views: Favorites & Active
        int favCount = (int) StarClient.getInstance().getModuleManager().getModules().stream()
                .filter(Module::isFavorite).count();
        drawSidebarItem(extractor, itemX, curY, itemW, itemH, "★ Favorites",
                favCount > 0 ? String.valueOf(favCount) : null,
                activeSidebarSection == SidebarSection.FAVORITES && !isSearching(),
                0xFFFACC15, mouseX, mouseY);
        curY += itemH + itemGap;

        int activeCount = (int) StarClient.getInstance().getModuleManager().getModules().stream()
                .filter(Module::isEnabled).count();
        drawSidebarItem(extractor, itemX, curY, itemW, itemH, "● Enabled",
                String.valueOf(activeCount),
                activeSidebarSection == SidebarSection.ACTIVE && !isSearching(),
                0xFF34D399, mouseX, mouseY);
        curY += itemH + 4;

        // Divider
        extractor.fill(itemX + 4, curY, itemX + itemW - 4, curY + 1, menuBorder());
        curY += 5;

        // 2. Categories
        for (Category category : Category.values()) {
            boolean selected = !isSearching()
                    && activeSidebarSection == SidebarSection.CATEGORY
                    && selectedCategory == category;
            String badge = null;
            if (category == Category.PROFILES) {
                ProfileManager pm = StarClient.getInstance().getProfileManager();
                if (pm != null) {
                    badge = String.valueOf(pm.getProfiles().size());
                }
            } else if (category != Category.COSMETICS && category != Category.SETTINGS && category != Category.STATS) {
                List<Module> catMods = StarClient.getInstance().getModuleManager().getModulesByCategory(category);
                long enabledInCat = catMods.stream().filter(Module::isEnabled).count();
                if (enabledInCat > 0) {
                    badge = String.valueOf(enabledInCat);
                }
            }
            String label = category.getIcon() + " " + category.getName();
            drawSidebarItem(extractor, itemX, curY, itemW, itemH, label, badge, selected, menuAccent(), mouseX, mouseY);
            curY += itemH + itemGap;
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

        } else if (isSettingsView()) {
            title = Category.SETTINGS.getIcon() + " Client Settings";
            subtitle = "Global appearance, font style & integrations";
        } else if (isProfilesView()) {
            ProfileManager pm = StarClient.getInstance().getProfileManager();
            ClientProfile act = pm != null ? pm.getActiveProfile() : null;
            title = Category.PROFILES.getIcon() + " Custom Saved Profiles";
            subtitle = "Active: " + (act != null ? act.getIcon() + " " + act.getName() : "Default")
                    + " • Auto-link worlds & servers per profile";
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
        boolean experimental = module.isExperimental();
        boolean expLocked = module.isExperimentalLocked();
        boolean rankLocked = module.isRankLocked();
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
        if (rankLocked) {
            borderColor = GuiRenderUtils.withAlpha(module.getRequiredRank().getArgb(), 140);
        } else if (expLocked) {
            borderColor = tileHovered ? 0xFFF59E0B : 0x77F59E0B;
        } else if (isSelectedInDrawer) {
            borderColor = menuAccent();
        } else if (enabled) {
            borderColor = GuiRenderUtils.lerpColor(menuAccentDark(), menuAccent(), hoverAnim);
        } else if (experimental) {
            borderColor = GuiRenderUtils.lerpColor(menuBorder(), 0x88F59E0B, hoverAnim);
        } else {
            borderColor = GuiRenderUtils.lerpColor(menuBorder(), GuiRenderUtils.withAlpha(menuAccent(), 130), hoverAnim);
        }

        GuiRenderUtils.fillRoundedRect(extractor, tile.x, tile.y, tile.width, TILE_HEIGHT, 6, cardBg);
        GuiRenderUtils.drawRoundedOutline(extractor, tile.x, tile.y, tile.width, TILE_HEIGHT, 6, borderColor);

        // Top Row: Module Name + Favorite Star + Toggle Switch (or Lock Badge)
        int textLeft = tile.x + 10;
        int topRowY = tile.y + 8;

        if (locked) {
            String lockLabel;
            int lockColor;
            if (rankLocked) {
                Rank req = module.getRequiredRank();
                lockLabel = "🔒 " + req.getDisplayName();
                lockColor = req.getArgb();
            } else {
                lockLabel = "🔒 EXP";
                lockColor = 0xFFF59E0B;
            }
            int lockW = GuiRenderUtils.textWidth(this.font, lockLabel) + 8;
            int lockX = tile.x + tile.width - lockW - 8;
            GuiRenderUtils.fillRoundedRect(extractor, lockX, topRowY - 1, lockW, 13, 4, GuiRenderUtils.withAlpha(lockColor, 40));
            GuiRenderUtils.drawRoundedOutline(extractor, lockX, topRowY - 1, lockW, 13, 4, GuiRenderUtils.withAlpha(lockColor, 120));
            GuiRenderUtils.drawCenteredText(extractor, this.font, lockLabel, lockX + lockW / 2, topRowY + 1, lockColor);

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

            // Compact EXP Corner Tag to the left of the favorite star
            int rightBoundary = favX - 4;
            if (experimental) {
                int expW = GuiRenderUtils.textWidth(this.font, "EXP") + 6;
                int expH = 12;
                int expX = favX - expW - 4;
                int expY = topRowY;
                GuiRenderUtils.fillRoundedRect(extractor, expX, expY, expW, expH, 3, 0x25F59E0B);
                GuiRenderUtils.drawRoundedOutline(extractor, expX, expY, expW, expH, 3, 0x66F59E0B);
                GuiRenderUtils.drawCenteredText(extractor, this.font, "EXP", expX + expW / 2, expY + 2, 0xFFF59E0B);
                rightBoundary = expX - 4;
            }

            int maxTitleW = rightBoundary - textLeft;
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
            if (textLeft + GuiRenderUtils.textWidth(this.font, leftBadge) <= keyX - 4) {
                GuiRenderUtils.drawText(extractor, this.font, leftBadge, textLeft, botY + 2, leftColor);
            }
        } else {
            // When locked:
            if (expLocked) {
                GuiRenderUtils.drawText(extractor, this.font, "🔒 Requires Experimental Features", textLeft, botY + 2, 0xFF64748B);
            }
        }

        if (tileHovered && pendingTooltipTitle == null) {
            if (expLocked) {
                setTooltip("🔒 " + module.getName() + " (Locked)",
                        "This is an experimental module. Enable 'Experimental Features' in Settings to unlock and use it.");
            } else if (rankLocked) {
                setTooltip("🔒 " + module.getName() + " (Locked)",
                        "Requires " + module.getRequiredRank().getDisplayName() + " rank to use.");
            } else if (experimental) {
                setTooltip(module.getName() + " (Experimental)",
                        module.getDescription() + " • [Experimental Feature]");
            } else {
                setTooltip(module.getName(), module.getDescription());
            }
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

        // Toggle switch or Lock badge inside Drawer Header
        int switchW = 24;
        int switchH = 13;
        int switchX = closeX - switchW - 6;
        int switchY = drawerY + 10;
        if (drawerModule.canUse()) {
            float toggleAnim = toggleProgressMap.getOrDefault(drawerModule, drawerModule.isEnabled() ? 1.0F : 0.0F);
            GuiRenderUtils.drawToggleSwitch(extractor, switchX, switchY, switchW, switchH, toggleAnim, menuAccent(), 0xFF334155);
        } else {
            String lockLabel = drawerModule.isExperimentalLocked() ? "🔒 Exp" : "🔒 " + drawerModule.getRequiredRank().getDisplayName();
            int lockColor = drawerModule.isExperimentalLocked() ? 0xFFF59E0B : drawerModule.getRequiredRank().getArgb();
            int lockW = GuiRenderUtils.textWidth(this.font, lockLabel) + 8;
            int lockX = closeX - lockW - 6;
            GuiRenderUtils.fillRoundedRect(extractor, lockX, drawerY + 9, lockW, 14, 4, GuiRenderUtils.withAlpha(lockColor, 40));
            GuiRenderUtils.drawRoundedOutline(extractor, lockX, drawerY + 9, lockW, 14, 4, GuiRenderUtils.withAlpha(lockColor, 120));
            GuiRenderUtils.drawCenteredText(extractor, this.font, lockLabel, lockX + lockW / 2, drawerY + 12, lockColor);
        }

        // Module Title & Wrapped Description
        String title = GuiRenderUtils.truncate(this.font, drawerModule.getName(), switchX - (drawerX + 12) - 6);
        GuiRenderUtils.drawText(extractor, this.font, title, drawerX + 12, drawerY + 9, menuAccent(), true);

        String desc = GuiRenderUtils.truncate(this.font, drawerModule.getDescription(), DRAWER_WIDTH - 24);
        GuiRenderUtils.drawText(extractor, this.font, desc, drawerX + 12, drawerY + 24, 0xFF94A3B8);

        String modeInfo = (drawerModule.isHoldKey() ? "Hold Key Mode" : "Toggle Mode")
                + (drawerModule.hasHudElement() ? " • HUD Element" : "")
                + (drawerModule.isExperimental() ? " • 🧪 Experimental" : "");
        GuiRenderUtils.drawText(extractor, this.font, modeInfo, drawerX + 12, drawerY + 36, drawerModule.isExperimental() ? 0xFFF59E0B : 0xFF64748B);

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

        switch (setting) {
            case BooleanSetting booleanSetting -> {
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
            }
            case ColorModeSetting colorSetting -> {
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
            }
            case ModeSetting modeSetting -> {
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
            }
            case NumberSetting numberSetting -> {
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
            }
            case TextSetting textSetting -> {
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
            }
            case NickEntryListSetting nickEntryListSetting -> {
                GuiRenderUtils.drawText(extractor, this.font, setting.getName(), item.x + 8, item.y + 8, 0xFFE2E8F0);
                int count = NickToolState.getEntries().size();
                String label = (count == 0 ? "Manage (0)" : count + " entries") + " ✎";
                int btnW = GuiRenderUtils.textWidth(this.font, label) + 12;
                int btnX = item.x + item.width - btnW - 6;
                GuiRenderUtils.fillRoundedRect(extractor, btnX, item.y + 4, btnW, 16, 4, menuBgDark());
                GuiRenderUtils.drawRoundedOutline(extractor, btnX, item.y + 4, btnW, 16, 4, menuBorder());
                GuiRenderUtils.drawCenteredText(extractor, this.font, label, btnX + btnW / 2, item.y + 8, menuAccent());
            }
            case MacroListSetting macroListSetting -> {
                GuiRenderUtils.drawText(extractor, this.font, setting.getName(), item.x + 8, item.y + 8, 0xFFE2E8F0);
                int count = QuickCommandsState.getEntries().size();
                String label = (count == 0 ? "Manage (0)" : count + (count == 1 ? " macro" : " macros")) + " ✎";
                int btnW = GuiRenderUtils.textWidth(this.font, label) + 12;
                int btnX = item.x + item.width - btnW - 6;
                GuiRenderUtils.fillRoundedRect(extractor, btnX, item.y + 4, btnW, 16, 4, menuBgDark());
                GuiRenderUtils.drawRoundedOutline(extractor, btnX, item.y + 4, btnW, 16, 4, menuBorder());
                GuiRenderUtils.drawCenteredText(extractor, this.font, label, btnX + btnW / 2, item.y + 8, menuAccent());
            }
            case BannedWordListSetting bannedWordListSetting -> {
                GuiRenderUtils.drawText(extractor, this.font, setting.getName(), item.x + 8, item.y + 8, 0xFFE2E8F0);
                int count = ChatPlusState.getBannedWords().size();
                String label = (count == 0 ? "Manage (0)" : count + (count == 1 ? " word" : " words")) + " ✎";
                int btnW = GuiRenderUtils.textWidth(this.font, label) + 12;
                int btnX = item.x + item.width - btnW - 6;
                GuiRenderUtils.fillRoundedRect(extractor, btnX, item.y + 4, btnW, 16, 4, menuBgDark());
                GuiRenderUtils.drawRoundedOutline(extractor, btnX, item.y + 4, btnW, 16, 4, menuBorder());
                GuiRenderUtils.drawCenteredText(extractor, this.font, label, btnX + btnW / 2, item.y + 8, menuAccent());
            }
            case WaypointListSetting waypointListSetting -> {
                List<WaypointsState.Waypoint> wps = WaypointsState.getWaypointsForCurrentContext(Minecraft.getInstance());
                String title = "Waypoints (" + wps.size() + ")";
                GuiRenderUtils.drawText(extractor, this.font, title, item.x + 8, item.y + 7, 0xFFE2E8F0);

                // Action pills: "+ Here" | "+ New" | "All ✎"
                int btnH = 14;
                int btnY = item.y + 5;
                int allW = 36;
                int newW = 34;
                int hereW = 38;
                int allX = item.x + item.width - allW - 6;
                int newX = allX - newW - 4;
                int hereX = newX - hereW - 4;

                boolean hereHov = isHovered(mouseX, mouseY, hereX, btnY, hereW, btnH);
                GuiRenderUtils.fillRoundedRect(extractor, hereX, btnY, hereW, btnH, 3, hereHov ? menuAccentDark() : menuBgDark());
                GuiRenderUtils.drawRoundedOutline(extractor, hereX, btnY, hereW, btnH, 3, hereHov ? menuAccent() : menuBorder());
                GuiRenderUtils.drawCenteredText(extractor, this.font, "+ Here", hereX + hereW / 2, btnY + 3, 0xFF34D399);

                boolean newHov = isHovered(mouseX, mouseY, newX, btnY, newW, btnH);
                GuiRenderUtils.fillRoundedRect(extractor, newX, btnY, newW, btnH, 3, newHov ? menuAccentDark() : menuBgDark());
                GuiRenderUtils.drawRoundedOutline(extractor, newX, btnY, newW, btnH, 3, newHov ? menuAccent() : menuBorder());
                GuiRenderUtils.drawCenteredText(extractor, this.font, "+ New", newX + newW / 2, btnY + 3, menuAccent());

                boolean allHov = isHovered(mouseX, mouseY, allX, btnY, allW, btnH);
                GuiRenderUtils.fillRoundedRect(extractor, allX, btnY, allW, btnH, 3, allHov ? menuAccentDark() : menuBgDark());
                GuiRenderUtils.drawRoundedOutline(extractor, allX, btnY, allW, btnH, 3, allHov ? menuAccent() : menuBorder());
                GuiRenderUtils.drawCenteredText(extractor, this.font, "All ✎", allX + allW / 2, btnY + 3, 0xFFE2E8F0);

                int rowY = item.y + 23;
                if (wps.isEmpty()) {
                    GuiRenderUtils.drawText(extractor, this.font, "No waypoints in this world yet", item.x + 8, rowY + 4, 0xFF64748B);
                } else {
                    int shown = Math.min(6, wps.size());
                    for (int i = 0; i < shown; i++) {
                        WaypointsState.Waypoint wp = wps.get(i);
                        GuiRenderUtils.fillRoundedRect(extractor, item.x + 6, rowY, item.width - 12, 18, 3, menuBgDark());
                        int dotCol = 0xFF000000 | (wp.getColor() & 0x00FFFFFF);
                        GuiRenderUtils.fillRoundedRect(extractor, item.x + 10, rowY + 5, 8, 8, 2, dotCol);

                        int delX = item.x + item.width - 22;
                        int editX = delX - 17;
                        int visX = editX - 17;

                        String dimShort = WaypointsState.formatDimensionShort(wp.getDimension());
                        String wpText = wp.getName() + " [" + dimShort + "] (" + wp.getX() + ", " + wp.getY() + ", " + wp.getZ() + ")";
                        int maxLabelW = Math.max(36, visX - (item.x + 22) - 4);
                        String clipped = GuiRenderUtils.truncate(this.font, wpText, maxLabelW);
                        GuiRenderUtils.drawText(extractor, this.font, clipped, item.x + 22, rowY + 5,
                                wp.isEnabled() ? 0xFFE2E8F0 : 0xFF64748B);

                        boolean visHov = isHovered(mouseX, mouseY, visX, rowY + 2, 15, 14);
                        GuiRenderUtils.fillRoundedRect(extractor, visX, rowY + 2, 15, 14, 3,
                                wp.isEnabled() ? 0x3310B981 : (visHov ? menuBgLight() : 0x22000000));
                        GuiRenderUtils.drawCenteredText(extractor, this.font, wp.isEnabled() ? "●" : "○",
                                visX + 7, rowY + 5, wp.isEnabled() ? 0xFF34D399 : 0xFF94A3B8);

                        boolean editHov = isHovered(mouseX, mouseY, editX, rowY + 2, 15, 14);
                        GuiRenderUtils.fillRoundedRect(extractor, editX, rowY + 2, 15, 14, 3,
                                editHov ? menuAccentDark() : 0x22000000);
                        GuiRenderUtils.drawCenteredText(extractor, this.font, "✎",
                                editX + 7, rowY + 5, editHov ? 0xFFFFFFFF : menuAccent());

                        boolean delHov = isHovered(mouseX, mouseY, delX, rowY + 2, 15, 14);
                        GuiRenderUtils.fillRoundedRect(extractor, delX, rowY + 2, 15, 14, 3,
                                delHov ? 0x44EF4444 : 0x22000000);
                        GuiRenderUtils.drawCenteredText(extractor, this.font, "✕",
                                delX + 7, rowY + 5, delHov ? 0xFFF87171 : 0xFF94A3B8);

                        rowY += 20;
                    }
                    if (wps.size() > shown) {
                        String more = "+ " + (wps.size() - shown) + " more (click All ✎)";
                        GuiRenderUtils.drawText(extractor, this.font, more, item.x + 10, rowY + 2, menuAccent());
                    }
                }
            }
            default -> {
            }
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
        if (setting instanceof WaypointListSetting) {
            List<WaypointsState.Waypoint> wps = WaypointsState.getWaypointsForCurrentContext(Minecraft.getInstance());
            if (wps.isEmpty()) {
                return 44;
            }
            int shown = Math.min(6, wps.size());
            int extra = wps.size() > shown ? 14 : 0;
            return 26 + shown * 20 + extra;
        }
        return 24;
    }

    private List<DrawerItem> buildDrawerItems(Module module, int x, int width, int topY, double scroll) {
        List<DrawerItem> items = new ArrayList<>();
        int curY = (int) Math.round(topY - scroll);

        // Keybind Card
        items.add(DrawerItem.keybind(module, x, curY, width));
        curY += 28;

        // General Settings
        List<Setting<?>> general = module.getGeneralSettings();
        if (!general.isEmpty()) {
            items.add(DrawerItem.header("Module Settings", x, curY, width));
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
            items.add(DrawerItem.header("HUD Appearance", x, curY, width));
            curY += 20;
            for (Setting<?> setting : hudSettings) {
                int h = computeSettingRowHeight(setting, width);
                items.add(DrawerItem.setting(setting, x, curY, width, h));
                curY += h + 5;
            }
        }

        if (general.isEmpty() && hudSettings.isEmpty()) {
            items.add(DrawerItem.info(x, curY + 4, width));
        }

        return items;
    }

    private List<DrawerItem> buildClientSettingsItems(int x, int width, int topY, double scroll) {
        List<DrawerItem> items = new ArrayList<>();
        int curY = (int) Math.round(topY - scroll);
        ClientSettings cs = StarClient.getInstance().getClientSettings();

        items.add(DrawerItem.header("Theme & Appearance", x, curY, width));
        curY += 22;

        for (Setting<?> setting : cs.getSettings()) {
            switch (setting.getName()) {
                case "HUD Text Shadow" -> {
                    curY += 4;
                    items.add(DrawerItem.header("HUD & In-Game Visuals", x, curY, width));
                    curY += 22;
                }
                case "Hypixel API Key" -> {
                    curY += 4;
                    items.add(DrawerItem.header("Integrations & API", x, curY, width));
                    curY += 22;
                }
                case "Experimental Features" -> {
                    curY += 4;
                    items.add(DrawerItem.header("Advanced & Experimental", x, curY, width));
                    curY += 22;
                }
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
        if (isSettingsView() || isStatsView() || isProfilesView()) return 0;
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
        if (module.isExperimental() && ("experimental".contains(query) || "exp".contains(query))) return true;
        for (Setting<?> s : module.getGeneralSettings()) {
            if (s.getName().toLowerCase(Locale.ROOT).contains(query)
                    || s.getDescription().toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private double computeMaxMainScroll(int contentWidth, int viewportHeight) {
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
        if (isProfilesView()) {
            return this.profilesViewRenderer.computeMaxScroll(contentWidth, viewportHeight);
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



    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (isStatsView() && this.statsViewRenderer.isShowingCardModal()) {
            return this.statsViewRenderer.handleCardModalClick(this.width, this.height, mouseX, mouseY);
        }

        int startX = (this.width - guiWidth) / 2;
        int startY = (this.height - guiHeight) / 2;


        int searchY = startY + (HEADER_HEIGHT - SEARCH_HEIGHT) / 2;

        // 0. Check open Profile Quick-Switcher Dropdown first
        int hudBtnX = startX + guiWidth - 12 - HUD_EDITOR_BTN_WIDTH;
        int profBtnX = hudBtnX - 6 - PROFILE_BTN_WIDTH;
        if (profileDropdownOpen) {
            ProfileManager pm = StarClient.getInstance().getProfileManager();
            if (pm != null) {
                List<ClientProfile> profiles = pm.getProfiles();
                int dropW = 148;
                int dropX = Math.min(profBtnX, startX + guiWidth - dropW - 10);
                int dropY = startY + HEADER_HEIGHT - 4;
                int rowH = 20;
                int dropH = (profiles.size() + 1) * rowH + 8;

                if (isHovered(mouseX, mouseY, dropX, dropY, dropW, dropH)) {
                    int ry = dropY + 4;
                    for (ClientProfile p : profiles) {
                        if (isHovered(mouseX, mouseY, dropX + 4, ry, dropW - 8, rowH)) {
                            pm.switchProfile(p.getId());
                            profileDropdownOpen = false;
                            return true;
                        }
                        ry += rowH;
                    }
                    if (isHovered(mouseX, mouseY, dropX + 4, ry + 1, dropW - 8, rowH - 1)) {
                        profileDropdownOpen = false;
                        activeSidebarSection = SidebarSection.CATEGORY;
                        selectedCategory = Category.PROFILES;
                        clearSearchIfActive();
                        targetModuleScroll = 0;
                        drawerModule = null;
                        return true;
                    }
                    return true;
                }
            }
            if (!isHovered(mouseX, mouseY, profBtnX, searchY, PROFILE_BTN_WIDTH, SEARCH_HEIGHT)) {
                profileDropdownOpen = false;
            }
        }

        // 1. Check Edit HUD Button & Profile Quick-Switcher Button
        if (isHovered(mouseX, mouseY, hudBtnX, searchY, HUD_EDITOR_BTN_WIDTH, SEARCH_HEIGHT)) {
            Minecraft.getInstance().gui.setScreen(new HudEditorScreen(this));
            return true;
        }
        if (isHovered(mouseX, mouseY, profBtnX, searchY, PROFILE_BTN_WIDTH, SEARCH_HEIGHT)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                profileDropdownOpen = false;
                activeSidebarSection = SidebarSection.CATEGORY;
                selectedCategory = Category.PROFILES;
                clearSearchIfActive();
                targetModuleScroll = 0;
                drawerModule = null;
            } else {
                profileDropdownOpen = !profileDropdownOpen;
            }
            return true;
        }

        // 2. Check Search Box & Clear Button
        int searchX = startX + SIDEBAR_WIDTH + 12;
        int searchW = 160;
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
            this.profilesViewRenderer.clearFocus();
            this.searchBox.setFocused(true);
            this.setFocused(this.searchBox);
            return super.mouseClicked(event, action);
        } else if (this.searchBox != null && this.searchBox.isFocused()) {
            this.searchBox.setFocused(false);
            this.setFocused(null);
        }

        // 3. Check Filter Pills
        int filterX = searchX + searchW + 8;
        int filterY = searchY + 1;
        int filterH = SEARCH_HEIGHT - 2;
        for (FilterMode mode : FilterMode.values()) {
            int pillW = GuiRenderUtils.textWidth(this.font, mode.label) + 12;
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
        int itemH = sidebarItemHeight();
        int itemGap = sidebarItemGap();
        int curY = startY + HEADER_HEIGHT + 6;

        if (isHovered(mouseX, mouseY, itemX, curY, itemW, itemH)) {
            activeSidebarSection = SidebarSection.FAVORITES;
            clearSearchIfActive();
            this.profilesViewRenderer.clearFocus();
            targetModuleScroll = 0;
            return true;
        }
        curY += itemH + itemGap;

        if (isHovered(mouseX, mouseY, itemX, curY, itemW, itemH)) {
            activeSidebarSection = SidebarSection.ACTIVE;
            clearSearchIfActive();
            this.profilesViewRenderer.clearFocus();
            targetModuleScroll = 0;
            return true;
        }
        curY += itemH + 9;

        for (Category category : Category.values()) {
            if (isHovered(mouseX, mouseY, itemX, curY, itemW, itemH)) {
                if (category == Category.COSMETICS) {
                    Minecraft.getInstance().gui.setScreen(new CosmeticsScreen(this));
                    return true;
                }
                activeSidebarSection = SidebarSection.CATEGORY;
                selectedCategory = category;
                clearSearchIfActive();
                this.profilesViewRenderer.clearFocus();
                targetModuleScroll = 0;
                if (category == Category.SETTINGS || category == Category.PROFILES || category == Category.STATS) {
                    drawerModule = null;
                    expandedDropdown = null;
                }
                return true;
            }
            curY += itemH + itemGap;
        }

        // 5. Check Settings Drawer interactions if open
        if (drawerModule != null && !isSettingsView() && !isStatsView() && !isProfilesView() && drawerProgress > 0.5F) {
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
            if (isSettingsView()) {
                List<DrawerItem> items = buildClientSettingsItems(contentLeft, contentWidth, panelTop, moduleScroll);
                for (DrawerItem item : items) {
                    if (item.setting != null && item.contains(mouseX, mouseY)) {
                        handleSettingItemClick(item, mouseX, mouseY, button, false);
                        StarClient.getInstance().getConfigManager().saveConfig();
                        return true;
                    }
                }
            } else if (isProfilesView()) {
                if (this.profilesViewRenderer.mouseClicked(this.font, contentLeft, contentWidth, panelTop, moduleScroll, mouseX, mouseY, button)) {
                    return true;
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
            if ("Experimental Features".equals(booleanSetting.getName()) && !booleanSetting.getValue()) {
                for (Module mod : StarClient.getInstance().getModuleManager().getModules()) {
                    if (mod.isExperimental() && mod.isEnabled()) {
                        mod.setEnabled(false);
                    }
                }
            }
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
        } else if (setting instanceof MacroListSetting) {
            Minecraft.getInstance().gui.setScreen(new QuickCommandsScreen(this));
        } else if (setting instanceof BannedWordListSetting) {
            Minecraft.getInstance().gui.setScreen(new BannedWordsScreen(this));
        } else if (setting instanceof WaypointListSetting) {
            int btnH = 14;
            int btnY = item.y + 5;
            int allW = 36;
            int newW = 34;
            int hereW = 38;
            int allX = item.x + item.width - allW - 6;
            int newX = allX - newW - 4;
            int hereX = newX - hereW - 4;

            if (isHovered(mouseX, mouseY, hereX, btnY, hereW, btnH)) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.level != null) {
                    WaypointsState.addQuickWaypointHere(mc);
                } else {
                    mc.gui.setScreen(WaypointsScreen.openCreateAtPlayer(this));
                }
                return;
            }
            if (isHovered(mouseX, mouseY, newX, btnY, newW, btnH)) {
                Minecraft.getInstance().gui.setScreen(WaypointsScreen.openCreateAtPlayer(this));
                return;
            }
            if (isHovered(mouseX, mouseY, allX, btnY, allW, btnH)) {
                Minecraft.getInstance().gui.setScreen(new WaypointsScreen(this));
                return;
            }

            List<WaypointsState.Waypoint> wps = WaypointsState.getWaypointsForCurrentContext(Minecraft.getInstance());
            int rowY = item.y + 23;
            int shown = Math.min(6, wps.size());
            for (int i = 0; i < shown; i++) {
                WaypointsState.Waypoint wp = wps.get(i);
                int delX = item.x + item.width - 22;
                int editX = delX - 17;
                int visX = editX - 17;

                if (isHovered(mouseX, mouseY, visX, rowY + 2, 15, 14)) {
                    wp.toggleEnabled();
                    WaypointsState.save();
                    return;
                }
                if (isHovered(mouseX, mouseY, editX, rowY + 2, 15, 14)) {
                    Minecraft.getInstance().gui.setScreen(WaypointsScreen.openEdit(this, wp));
                    return;
                }
                if (isHovered(mouseX, mouseY, delX, rowY + 2, 15, 14)) {
                    WaypointsState.removeWaypoint(wp.getId());
                    return;
                }
                if (isHovered(mouseX, mouseY, item.x + 6, rowY, item.width - 12, 18)) {
                    Minecraft.getInstance().gui.setScreen(WaypointsScreen.openEdit(this, wp));
                    return;
                }
                rowY += 20;
            }
            if (wps.size() > shown && mouseY >= rowY) {
                Minecraft.getInstance().gui.setScreen(new WaypointsScreen(this));
            }
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
        if (isStatsView() && this.statsViewRenderer.isShowingCardModal()) {
            return true;
        }

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
        if (isStatsView() && this.statsViewRenderer.isShowingCardModal()) {
            return true;
        }

        int startX = (this.width - guiWidth) / 2;
        int startY = (this.height - guiHeight) / 2;

        // Check if scrolling inside the Settings Drawer
        if (drawerModule != null && !isSettingsView() && !isStatsView() && drawerProgress > 0.5F) {
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

        if (isProfilesView() && this.profilesViewRenderer.hasFocusedInput()) {
            if (this.profilesViewRenderer.keyPressed(event)) {
                return true;
            }
        }

        // Escape closes card modal first, then expanded dropdown, then open Settings Drawer, then focused search / search query, then screen
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (profileDropdownOpen) {
                profileDropdownOpen = false;
                return true;
            }
            if (isStatsView() && this.statsViewRenderer.isShowingCardModal()) {
                this.statsViewRenderer.closeCardModal();
                return true;
            }
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
        if (isProfilesView() && this.profilesViewRenderer.hasFocusedInput()) {
            return this.profilesViewRenderer.charTyped(event);
        }
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



    private void clearSearchIfActive() {
        if (isSearching() && this.searchBox != null) {
            this.searchBox.setValue("");
            this.searchQuery = "";
        }
    }

    private boolean isSearching() {
        return !searchQuery.trim().isEmpty();
    }



    private boolean isSettingsView() {
        return !isSearching()
                && activeSidebarSection == SidebarSection.CATEGORY
                && selectedCategory == Category.SETTINGS;
    }

    private boolean isProfilesView() {
        return !isSearching()
                && activeSidebarSection == SidebarSection.CATEGORY
                && selectedCategory == Category.PROFILES;
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
        static DrawerItem header(String title, int x, int y, int width) {
            return new DrawerItem(title, false, null, null, null, x, y, width, 18);
        }

        static DrawerItem keybind(Module module, int x, int y, int width) {
            return new DrawerItem(null, true, module, null, null, x, y, width, 24);
        }

        static DrawerItem setting(Setting<?> setting, int x, int y, int width, int height) {
            return new DrawerItem(null, false, null, setting, null, x, y, width, height);
        }

        static DrawerItem info(int x, int y, int width) {
            return new DrawerItem(null, false, null, null, "No additional settings for this module", x, y, width, 28);
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }
}
