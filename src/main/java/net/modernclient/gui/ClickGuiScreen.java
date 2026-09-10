package net.modernclient.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.modernclient.ModernClient;
import net.modernclient.cosmetics.CapeCosmetic;
import net.modernclient.cosmetics.CosmeticManager;
import net.modernclient.module.Category;
import net.modernclient.module.Module;
import net.modernclient.module.ModuleManager;
import net.modernclient.setting.BooleanSetting;
import net.modernclient.setting.ModeSetting;
import net.modernclient.setting.NumberSetting;
import net.modernclient.setting.Setting;
import org.jspecify.annotations.NonNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ClickGuiScreen extends Screen {
    private static final int SIDEBAR_WIDTH = 130;
    private static final int CARD_HEIGHT = 36;
    private static final int KEYBIND_HEIGHT = 20;
    private static final int SEARCH_HEIGHT = 20;
    private static final int HEADER_HEIGHT = 36;
    private static final int PANEL_TOP_OFFSET = 68;
    private static final int COSMETIC_ROW_HEIGHT = 30;
    private static final int COSMETIC_GAP = 6;
    private static final String CAPE_NONE_ROW = "#cape_none";
    private static final String SKIN_NONE_ROW = "#skin_none";
    private static final String RELOAD_ROW = "#reload";
    private static final String HEADER_PREFIX = "#header:";

    private Category selectedCategory = Category.COMBAT;
    private final int guiWidth = 520;
    private final int guiHeight = 340;

    private String draggingElement;
    private double dragOffsetX;
    private double dragOffsetY;
    private boolean hudDragMoved;

    private static final long DOUBLE_CLICK_MILLIS = 300;
    private String lastHudClickId;
    private long lastHudClickTime;

    private Setting<?> draggingSetting;
    private Module bindingModule;
    private double moduleScroll;
    private EditBox searchBox;
    private String searchQuery = "";

    public ClickGuiScreen() {
        super(Component.literal("Star Client ClickGUI"));
    }

    @Override
    protected void init() {
        super.init();
        int startX = (this.width - guiWidth) / 2;
        int startY = (this.height - guiHeight) / 2;
        int searchX = startX + 16 + this.font.width("⭐ " + ModernClient.CLIENT_NAME + " " + ModernClient.CLIENT_VERSION) + 24;
        int searchWidth = startX + guiWidth - 16 - searchX;

        this.searchBox = new EditBox(this.font, searchX + 2, startY + (HEADER_HEIGHT - SEARCH_HEIGHT) / 2, searchWidth - 2, SEARCH_HEIGHT, Component.literal(""));
        this.searchBox.setResponder(value -> this.searchQuery = value);
        this.searchBox.setHint(Component.literal("Search modules..."));
        this.searchBox.setMaxLength(64);
        this.searchBox.setBordered(false);
        addWidget(this.searchBox);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta) {
        super.extractRenderState(extractor, mouseX, mouseY, delta);
        clampScroll();

        int startX = (this.width - guiWidth) / 2;
        int startY = (this.height - guiHeight) / 2;

        // Main Glassmorphism Window Background (Dark #0F172A with 90% opacity)
        extractor.fill(startX, startY, startX + guiWidth, startY + guiHeight, 0xF00F172A);

        // Header Title Bar
        extractor.fill(startX, startY, startX + guiWidth, startY + HEADER_HEIGHT, 0xFF1E293B);
        String headerTitle = "⭐ " + ModernClient.CLIENT_NAME + " " + ModernClient.CLIENT_VERSION;
        extractor.text(this.font, headerTitle, startX + 16, startY + 12, 0xFF38BDF8);
        drawSearchBar(extractor,
                startX + 16 + this.font.width(headerTitle) + 24,
                startY + (HEADER_HEIGHT - SEARCH_HEIGHT) / 2,
                startX + guiWidth - 16 - (startX + 16 + this.font.width(headerTitle) + 24),
                mouseX, mouseY, delta);

        // Sidebar - Category Selector
        extractor.fill(startX, startY + 36, startX + SIDEBAR_WIDTH, startY + guiHeight, 0xFF0B1120);

        int catY = startY + 48;
        for (Category category : Category.values()) {
            boolean isSelected = (category == selectedCategory);
            int catBgColor = isSelected ? 0xFF0EA5E9 : 0x00000000;
            int catTextColor = isSelected ? 0xFFFFFFFF : 0xFF94A3B8;

            if (isSelected) {
                extractor.fill(startX + 8, catY - 4, startX + SIDEBAR_WIDTH - 8, catY + 20, catBgColor);
            }

            String catText = category.getIcon() + " " + category.getName();
            extractor.text(this.font, catText, startX + 16, catY + 2, catTextColor);

            // Check category click
            if (mouseX >= startX && mouseX <= startX + SIDEBAR_WIDTH && mouseY >= catY - 4 && mouseY <= catY + 20) {
                // Hover effect
                if (!isSelected) {
                    extractor.fill(startX + 8, catY - 4, startX + SIDEBAR_WIDTH - 8, catY + 20, 0x20FFFFFF);
                }
            }

            catY += 28;
        }

        // Module Panel area
        int moduleX = startX + SIDEBAR_WIDTH + 16;
        int moduleTitleY = startY + 48;
        int panelTop = startY + PANEL_TOP_OFFSET;
        int panelBottom = startY + guiHeight - 8;
        int cardWidth = guiWidth - SIDEBAR_WIDTH - 32;

        boolean cosmeticsView = isCosmeticsView();
        String moduleTitle = isSearching()
                ? "Search Results for \"" + searchQuery.trim() + "\""
                : cosmeticsView
                        ? Category.COSMETICS.getIcon() + " " + Category.COSMETICS.getName()
                        : selectedCategory.getIcon() + " " + selectedCategory.getName() + " Modules";
        extractor.text(this.font, moduleTitle, moduleX, moduleTitleY, 0xFFE2E8F0);
        if (computeMaxScroll() > 0) {
            extractor.text(this.font, "Scroll for more", moduleX + cardWidth - this.font.width("Scroll for more"), moduleTitleY, 0xFF64748B);
        }

        extractor.enableScissor(moduleX, panelTop, moduleX + cardWidth, panelBottom);
        if (cosmeticsView) {
            for (CosmeticRow row : buildCosmeticRows(moduleScroll)) {
                drawCosmeticRow(extractor, row, mouseX, mouseY);
            }
        } else {
            List<ModuleSlot> slots = buildModuleSlots(moduleScroll);
            for (ModuleSlot slot : slots) {
                drawModuleCard(extractor, slot, mouseX, mouseY);
                for (SettingRow row : slot.rows) {
                    drawSetting(extractor, row, mouseX, mouseY);
                }
            }
        }
        extractor.disableScissor();

        // HUD elements rendered in the menu so they can be repositioned
        HudOverlay.render(extractor);

        HudLayout layout = ModernClient.getInstance().getHudLayout();
        List<HudLayout.Rect> rects = HudLayout.computeRects(this.font, getActiveModules(), this.width, this.height, layout);
        for (HudLayout.Rect rect : rects) {
            if (!isHudElementVisible(rect.id)) continue;
            boolean hovered = rect.contains(mouseX, mouseY);
            boolean dragging = rect.id.equals(draggingElement);
            int borderColor = dragging ? 0xFF38BDF8 : (hovered ? 0xAA38BDF8 : 0x5516A34A);
            drawOutline(extractor, rect.x1, rect.y1, rect.x2, rect.y2, borderColor);

            String label = "✥ " + rect.id + (dragging ? " (dragging)" : "");
            extractor.text(this.font, label, rect.x1, rect.y1 - 10, hovered || dragging ? 0xFF38BDF8 : 0xFF64748B);
        }

        if (isCosmeticsView()) {
            extractor.text(this.font, "Put .png textures into " + CosmeticManager.CAPES_FOLDER + " and " + CosmeticManager.SKINS_FOLDER + " | Client-side only", this.width / 2 - 260, this.height - 24, 0xFF94A3B8);
        } else {
            extractor.text(this.font, "Drag HUD elements to reposition them | Double-click to reset to default", this.width / 2 - 180, this.height - 24, 0xFF94A3B8);
        }
    }

    private boolean isCosmeticsView() {
        return selectedCategory == Category.COSMETICS && !isSearching();
    }

    private List<CosmeticRow> buildCosmeticRows(double scroll) {
        List<CosmeticRow> rows = new ArrayList<>();
        int x = (this.width - guiWidth) / 2 + SIDEBAR_WIDTH + 16;
        int width = guiWidth - SIDEBAR_WIDTH - 32;
        int y = (int) ((double) (this.height - guiHeight) / 2 + PANEL_TOP_OFFSET - scroll);
        CosmeticManager cosmetics = ModernClient.getInstance().getCosmeticManager();
        cosmetics.ensureLoaded();

        rows.add(new CosmeticRow(HEADER_PREFIX + "Capes", null, x, y, width));
        y += COSMETIC_ROW_HEIGHT + COSMETIC_GAP;
        rows.add(new CosmeticRow(CAPE_NONE_ROW, null, x, y, width));
        y += COSMETIC_ROW_HEIGHT + COSMETIC_GAP;
        for (CapeCosmetic cape : cosmetics.getAvailableCapes()) {
            rows.add(new CosmeticRow(cape.id(), cape, x, y, width));
            y += COSMETIC_ROW_HEIGHT + COSMETIC_GAP;
        }

        rows.add(new CosmeticRow(HEADER_PREFIX + "Skins", null, x, y, width));
        y += COSMETIC_ROW_HEIGHT + COSMETIC_GAP;
        rows.add(new CosmeticRow(SKIN_NONE_ROW, null, x, y, width));
        y += COSMETIC_ROW_HEIGHT + COSMETIC_GAP;
        for (CapeCosmetic skin : cosmetics.getAvailableSkins()) {
            rows.add(new CosmeticRow(skin.id(), skin, x, y, width));
            y += COSMETIC_ROW_HEIGHT + COSMETIC_GAP;
        }

        rows.add(new CosmeticRow(RELOAD_ROW, null, x, y, width));
        return rows;
    }

    private void drawCosmeticRow(GuiGraphicsExtractor extractor, CosmeticRow row, int mouseX, int mouseY) {
        if (row.key().startsWith(HEADER_PREFIX)) {
            extractor.fill(row.x, row.y, row.x + row.width, row.y + COSMETIC_ROW_HEIGHT, 0x00000000);
            extractor.text(this.font, row.key().substring(HEADER_PREFIX.length()), row.x + 2, row.y + 10, 0xFF64748B);
            return;
        }

        CosmeticManager cosmetics = ModernClient.getInstance().getCosmeticManager();
        boolean capeGroup = CAPE_NONE_ROW.equals(row.key)
                || (row.cape != null && cosmetics.getAvailableCapes().contains(row.cape));
        boolean isNoneRow = CAPE_NONE_ROW.equals(row.key) || SKIN_NONE_ROW.equals(row.key);
        boolean isReloadRow = RELOAD_ROW.equals(row.key);
        String equippedId = capeGroup ? cosmetics.getEquippedCape() : cosmetics.getEquippedSkin();
        boolean isEquipped = isNoneRow ? equippedId == null : row.cape != null && row.cape.id().equals(equippedId);

        boolean hovered = row.contains(mouseX, mouseY);
        int accent = isEquipped ? 0xFF34D399 : (isReloadRow ? 0xFFFBBF24 : 0xFF334155);
        if (hovered && accent == 0xFF334155) accent = 0xFF38BDF8;

        extractor.fill(row.x, row.y, row.x + row.width, row.y + COSMETIC_ROW_HEIGHT, hovered ? 0xFF1E293B : 0xFF0F172A);
        extractor.fill(row.x, row.y, row.x + 3, row.y + COSMETIC_ROW_HEIGHT, accent);

        if (isReloadRow) {
            extractor.text(this.font, "↻ Reload Cosmetics", row.x + 12, row.y + 5, 0xFFFBBF24);
            extractor.text(this.font, "Rescan the capes & skins folders", row.x + 12, row.y + 19, 0xFF64748B);
            return;
        }

        if (isNoneRow) {
            extractor.text(this.font, "None", row.x + 12, row.y + 5, 0xFFE2E8F0);
            String desc = capeGroup
                    ? "Show your vanilla Minecraft cape"
                    : "Show the skin your account provides";
            extractor.text(this.font, desc, row.x + 12, row.y + 19, 0xFF64748B);
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
                blitRegion(extractor, cosmetic.textureId(), row.x + 12, row.y + 5, 20, 20, 8 / 64.0F, 16 / 64.0F, 8 / 64.0F, 16 / 64.0F);
                blitRegion(extractor, cosmetic.textureId(), row.x + 11, row.y + 4, 22, 22, 40 / 64.0F, 48 / 64.0F, 8 / 64.0F, 16 / 64.0F);
            }
            extractor.text(this.font, cosmetic.displayName(), row.x + 50, row.y + 5, isEquipped ? 0xFF38BDF8 : 0xFFE2E8F0);
            extractor.text(this.font, "Click to " + (isEquipped ? "unequip" : "equip"), row.x + 50, row.y + 19, 0xFF64748B);
        }

        String status = isEquipped ? "[EQUIPPED]" : "[SELECT]";
        int statusColor = isEquipped ? 0xFF34D399 : 0xFF94A3B8;
        extractor.text(this.font, status, row.x + row.width - this.font.width(status) - 10, row.y + 10, statusColor);
    }

    private void drawModuleCard(GuiGraphicsExtractor extractor, ModuleSlot slot, int mouseX, int mouseY) {
        Module module = slot.module;
        int cardWidth = slot.width;

        int cardBg = module.isEnabled() ? 0xFF1E293B : 0xFF0F172A;
        int border = module.isEnabled() ? 0xFF0EA5E9 : 0xFF334155;

        extractor.fill(slot.x, slot.y, slot.x + cardWidth, slot.y + CARD_HEIGHT, cardBg);
        extractor.fill(slot.x, slot.y, slot.x + 3, slot.y + CARD_HEIGHT, border);

        int titleColor = module.isEnabled() ? 0xFF38BDF8 : 0xFFF8FAFC;
        extractor.text(this.font, module.getName(), slot.x + 12, slot.y + 6, titleColor);
        extractor.text(this.font, module.getDescription(), slot.x + 12, slot.y + 20, 0xFF64748B);

        String status = module.isEnabled() ? "ON" : "OFF";
        int statusColor = module.isEnabled() ? 0xFF34D399 : 0xFF94A3B8;
        extractor.text(this.font, "[" + status + "]", slot.x + cardWidth - 40, slot.y + 12, statusColor);

        // Key Bind Row
        int keyY = slot.y + CARD_HEIGHT;
        boolean binding = bindingModule == module;
        boolean keyHovered = mouseX >= slot.x + 4 && mouseX <= slot.x + cardWidth - 4
                && mouseY >= keyY && mouseY <= keyY + KEYBIND_HEIGHT;
        int keyBg = binding ? 0xFF0E7490 : (keyHovered ? 0xFF1E293B : 0xFF0F172A);
        extractor.fill(slot.x + 4, keyY, slot.x + cardWidth - 4, keyY + KEYBIND_HEIGHT, keyBg);

        extractor.text(this.font, "Key Bind", slot.x + 14, keyY + 4, 0xFF94A3B8);
        String keyLabel = binding ? "Press a key..." : (module.getKeyBind() == GLFW.GLFW_KEY_UNKNOWN ? "None" : keyName(module.getKeyBind()));
        int keyColor = binding ? 0xFFFFD54F : (module.getKeyBind() == GLFW.GLFW_KEY_UNKNOWN ? 0xFF64748B : 0xFF38BDF8);
        extractor.text(this.font, keyLabel, slot.x + cardWidth - 14 - this.font.width(keyLabel), keyY + 4, keyColor);
    }

    private void drawSetting(GuiGraphicsExtractor extractor, SettingRow row, int mouseX, int mouseY) {
        Setting<?> setting = row.setting;
        boolean hovered = row.contains(mouseX, mouseY);
        int bg = hovered ? 0xFF1E293B : 0xFF0F172A;
        extractor.fill(row.x, row.y, row.x + row.width, row.y + row.height, bg);

        if (setting instanceof BooleanSetting booleanSetting) {
            extractor.text(this.font, setting.getName(), row.x + 4, row.y + 4, 0xFFE2E8F0);
            String state = booleanSetting.getValue() ? "ON" : "OFF";
            int color = booleanSetting.getValue() ? 0xFF34D399 : 0xFF64748B;
            extractor.text(this.font, state, row.x + row.width - this.font.width(state) - 4, row.y + 4, color);
        } else if (setting instanceof ModeSetting modeSetting) {
            extractor.text(this.font, setting.getName(), row.x + 4, row.y + 4, 0xFFE2E8F0);
            String value = modeSetting.getValue();
            extractor.text(this.font, value, row.x + row.width - this.font.width(value) - 4, row.y + 4, 0xFF38BDF8);
        } else if (setting instanceof NumberSetting numberSetting) {
            extractor.text(this.font, setting.getName(), row.x + 4, row.y + 3, 0xFFE2E8F0);
            String value = formatValue(numberSetting.getValue());
            extractor.text(this.font, value, row.x + row.width - this.font.width(value) - 4, row.y + 3, 0xFF38BDF8);

            int trackY = row.y + row.height - 8;
            int trackHeight = 4;
            extractor.fill(row.x + 4, trackY, row.x + row.width - 4, trackY + trackHeight, 0xFF334155);

            double range = numberSetting.getMax() - numberSetting.getMin();
            double t = range <= 0 ? 0 : (numberSetting.getValue() - numberSetting.getMin()) / range;
            int fillWidth = (int) ((row.width - 8) * t);
            extractor.fill(row.x + 4, trackY, row.x + 4 + fillWidth, trackY + trackHeight, 0xFF38BDF8);
            extractor.fill(row.x + 2 + fillWidth, trackY - 2, row.x + 6 + fillWidth, trackY + trackHeight + 2, 0xFFFFFFFF);
        }
    }

    private List<ModuleSlot> buildModuleSlots(double scroll) {
        List<ModuleSlot> slots = new ArrayList<>();
        int startX = (this.width - guiWidth) / 2 + SIDEBAR_WIDTH + 16;
        int moduleY = (int) ((double) (this.height - guiHeight) / 2 + PANEL_TOP_OFFSET - scroll);
        int cardWidth = guiWidth - SIDEBAR_WIDTH - 32;

        for (Module module : getVisibleModules()) {
            ModuleSlot slot = new ModuleSlot(module, startX, moduleY, cardWidth);
            int settingY = moduleY + CARD_HEIGHT + KEYBIND_HEIGHT;
            for (Setting<?> setting : module.getSettings()) {
                int height = setting instanceof NumberSetting ? 26 : 20;
                slot.rows.add(new SettingRow(setting, startX + 10, settingY, cardWidth - 20, height));
                settingY += height;
            }
            slot.totalHeight = settingY - moduleY + 2;
            slots.add(slot);
            moduleY += slot.totalHeight + 8;
        }
        return slots;
    }

    private double computeMaxScroll() {
        int viewportHeight = guiHeight - 8 - PANEL_TOP_OFFSET;
        if (isCosmeticsView()) {
            CosmeticManager cosmetics = ModernClient.getInstance().getCosmeticManager();
            int rowCount = cosmetics.getAvailableCapes().size() + cosmetics.getAvailableSkins().size() + 5;
            int contentHeight = rowCount * (COSMETIC_ROW_HEIGHT + COSMETIC_GAP) - COSMETIC_GAP;
            return Math.max(0, contentHeight - viewportHeight);
        }
        int contentHeight = 0;
        for (ModuleSlot slot : buildModuleSlots(0)) {
            contentHeight += slot.totalHeight + 8;
        }
        return Math.max(0, contentHeight - viewportHeight);
    }

    private boolean isSearching() {
        return !searchQuery.trim().isEmpty();
    }

    private List<Module> getVisibleModules() {
        if (isSearching()) {
            String query = searchQuery.trim().toLowerCase(Locale.ROOT);
            return ModernClient.getInstance().getModuleManager().getModules().stream()
                    .filter(m -> m.getName().toLowerCase(Locale.ROOT).contains(query)
                            || m.getDescription().toLowerCase(Locale.ROOT).contains(query))
                    .collect(Collectors.toList());
        }
        return ModernClient.getInstance().getModuleManager().getModulesByCategory(selectedCategory);
    }

    private void drawSearchBar(GuiGraphicsExtractor extractor, int x, int y, int width, int mouseX, int mouseY, float delta) {
        boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + SEARCH_HEIGHT;
        extractor.fill(x, y, x + width, y + SEARCH_HEIGHT, hovered ? 0xFF1E293B : 0xFF0B1120);
        extractor.fill(x, y, x + width, y + 1, 0xFF334155);
        extractor.fill(x, y, x + 1, y + SEARCH_HEIGHT, 0xFF334155);
        extractor.fill(x + width - 1, y, x + width, y + SEARCH_HEIGHT, 0xFF334155);
        extractor.fill(x, y + SEARCH_HEIGHT - 1, x + width, y + SEARCH_HEIGHT, 0xFF334155);
        searchBox.extractRenderState(extractor, mouseX, mouseY, delta);
    }

    private void clampScroll() {
        moduleScroll = Math.clamp(moduleScroll, 0, computeMaxScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int startX = (this.width - guiWidth) / 2;
        int startY = (this.height - guiHeight) / 2;
        boolean overPanel = mouseX >= startX + SIDEBAR_WIDTH && mouseX <= startX + guiWidth
                && mouseY >= startY + 48 && mouseY <= startY + guiHeight - 8;
        if (overPanel) {
            moduleScroll -= verticalAmount * 12;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private List<Module> getActiveModules() {
        return ModernClient.getInstance().getModuleManager().getModules().stream()
                .filter(Module::isEnabled)
                .collect(Collectors.toList());
    }

    private boolean isHudElementVisible(String id) {
        ModuleManager moduleManager = ModernClient.getInstance().getModuleManager();
        Module module = moduleManager.getModuleByName(moduleNameForHudElement(id));
        return module != null && module.isEnabled();
    }

    private static String moduleNameForHudElement(String id) {
        return switch (id) {
            case HudLayout.FPS -> "FPS Display";
            case HudLayout.PING -> "Ping Display";
            case HudLayout.COORDINATES -> "Coordinates";
            case HudLayout.ARRAYLIST -> "Active Modules";
            case HudLayout.KEYSTROKES -> "Keystrokes";
            case HudLayout.ARMOR -> "Armor HUD";
            default -> "";
        };
    }

    private void handleSettingClick(SettingRow row, double mouseX) {
        Setting<?> setting = row.setting;
        if (setting instanceof BooleanSetting booleanSetting) {
            booleanSetting.toggle();
        } else if (setting instanceof ModeSetting modeSetting) {
            modeSetting.cycle();
        } else if (setting instanceof NumberSetting numberSetting) {
            draggingSetting = setting;
            applySliderValue(numberSetting, mouseX, row);
        }
    }

    private void blitRegion(GuiGraphicsExtractor extractor, Identifier textureId, int destX, int destY, int destW, int destH, float uMin, float uMax, float vMin, float vMax) {
        extractor.blit(textureId, destX, destY, destX + destW, destY + destH, uMin, uMax, vMin, vMax);
    }

    private void handleCosmeticClick(CosmeticRow row) {
        CosmeticManager cosmetics = ModernClient.getInstance().getCosmeticManager();
        if (RELOAD_ROW.equals(row.key)) {
            cosmetics.reload();
        } else if (CAPE_NONE_ROW.equals(row.key)) {
            cosmetics.setEquippedCape(null);
        } else if (SKIN_NONE_ROW.equals(row.key)) {
            cosmetics.setEquippedSkin(null);
        } else if (row.key().startsWith(HEADER_PREFIX)) {
            return;
        } else if (cosmetics.getAvailableCapes().stream().anyMatch(c -> c.id().equals(row.key))) {
            cosmetics.setEquippedCape(row.key.equals(cosmetics.getEquippedCape()) ? null : row.key);
        } else {
            cosmetics.setEquippedSkin(row.key.equals(cosmetics.getEquippedSkin()) ? null : row.key);
        }
    }

    private void applySliderValue(NumberSetting numberSetting, double mouseX, SettingRow row) {
        double trackStart = row.x + 4;
        double trackWidth = row.width - 8;
        double t = (mouseX - trackStart) / trackWidth;
        t = Math.clamp(t, 0, 1);
        numberSetting.setValue(numberSetting.getMin() + t * (numberSetting.getMax() - numberSetting.getMin()));
    }

    private String formatValue(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.1f", value);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (bindingModule != null) {
            int key = event.key();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                this.bindingModule = null;
                return true;
            }
            if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
                bindingModule.setKeyBind(GLFW.GLFW_KEY_UNKNOWN);
                this.bindingModule = null;
                ModernClient.getInstance().getConfigManager().saveConfig();
                return true;
            }
            bindingModule.setKeyBind(key);
            this.bindingModule = null;
            ModernClient.getInstance().getConfigManager().saveConfig();
            return true;
        }
        return super.keyPressed(event);
    }

    private static String keyName(int key) {
        String name = GLFW.glfwGetKeyName(key, 0);
        return name == null ? "Key " + key : name.toUpperCase(Locale.ROOT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean action) {
        clampScroll();
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        int startX = (this.width - guiWidth) / 2;
        int startY = (this.height - guiHeight) / 2;

        boolean onSearchBar = mouseX >= this.searchBox.getX() && mouseX <= this.searchBox.getX() + this.searchBox.getWidth()
                && mouseY >= this.searchBox.getY() && mouseY <= this.searchBox.getY() + SEARCH_HEIGHT;
        if (onSearchBar) {
            this.searchBox.setFocused(true);
        } else if (this.searchBox.isFocused()) {
            this.setFocused(null);
        }

        // Check Sidebar Category Clicks
        int catY = startY + 48;
        for (Category category : Category.values()) {
            if (mouseX >= startX && mouseX <= startX + SIDEBAR_WIDTH && mouseY >= catY - 4 && mouseY <= catY + 20) {
                this.selectedCategory = category;
                this.moduleScroll = 0;
                return true;
            }
            catY += 28;
        }

        // Check Cosmetics Rows
        if (isCosmeticsView()) {
            for (CosmeticRow row : buildCosmeticRows(moduleScroll)) {
                if (row.contains(mouseX, mouseY)) {
                    handleCosmeticClick(row);
                    ModernClient.getInstance().getConfigManager().saveConfig();
                    return true;
                }
            }
        }

        // Check Module Cards Toggle and Setting Interaction
        else {
            for (ModuleSlot slot : buildModuleSlots(moduleScroll)) {
            int keyY = slot.y + CARD_HEIGHT;
            if (mouseX >= slot.x + 4 && mouseX <= slot.x + slot.width - 4
                    && mouseY >= keyY && mouseY <= keyY + KEYBIND_HEIGHT) {
                this.bindingModule = (bindingModule == slot.module) ? null : slot.module;
                return true;
            }
            for (SettingRow row : slot.rows) {
                if (row.contains(mouseX, mouseY)) {
                    handleSettingClick(row, mouseX);
                    ModernClient.getInstance().getConfigManager().saveConfig();
                    return true;
                }
            }
            if (mouseX >= slot.x && mouseX <= slot.x + slot.width && mouseY >= slot.y && mouseY <= slot.y + CARD_HEIGHT) {
                slot.module.toggle();
                ModernClient.getInstance().getConfigManager().saveConfig();
                return true;
            }
            }
        }

        // Check HUD Element drag / reset
        // Grabbing a HUD element begins a potential drag. A double-click (handled on
        // release) resets the element to its default position.
        HudLayout layout = ModernClient.getInstance().getHudLayout();
        List<HudLayout.Rect> rects = HudLayout.computeRects(this.font, getActiveModules(), this.width, this.height, layout);
        String hoveredId = hudElementAt(rects, mouseX, mouseY);
        if (hoveredId != null) {
            this.draggingElement = hoveredId;
            this.hudDragMoved = false;
            int maxModWidth = getActiveModules().stream().mapToInt(m -> this.font.width(m.getName()) + 12).max().orElse(0);
            this.dragOffsetX = mouseX - layout.effectiveX(hoveredId, this.width, maxModWidth);
            this.dragOffsetY = mouseY - layout.effectiveY(hoveredId, this.height);
            return true;
        }
        this.draggingElement = null;

        return super.mouseClicked(event, action);
    }

    private String hudElementAt(List<HudLayout.Rect> rects, double mouseX, double mouseY) {
        for (HudLayout.Rect rect : rects) {
            if (!isHudElementVisible(rect.id)) continue;
            if (rect.contains(mouseX, mouseY)) {
                return rect.id;
            }
        }
        return null;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
        HudLayout layout = ModernClient.getInstance().getHudLayout();

        if (draggingSetting instanceof NumberSetting numberSetting) {
            for (ModuleSlot slot : buildModuleSlots(moduleScroll)) {
                for (SettingRow row : slot.rows) {
                    if (row.setting == draggingSetting) {
                        applySliderValue(numberSetting, event.x(), row);
                        return true;
                    }
                }
            }
            return true;
        }

        // A grabbed HUD element keeps dragging even if the cursor outpaces its
        // bounds, using the offsets latched when the drag started.
        List<HudLayout.Rect> rects = HudLayout.computeRects(this.font, getActiveModules(), this.width, this.height, layout);
        if (draggingElement != null) {
            boolean visible = false;
            for (HudLayout.Rect rect : rects) {
                if (rect.id.equals(draggingElement) && isHudElementVisible(rect.id)) {
                    visible = true;
                    break;
                }
            }
            if (visible) {
                int newX = (int) Math.round(event.x() - dragOffsetX);
                int newY = (int) Math.round(event.y() - dragOffsetY);
                newX = Math.clamp(newX, 0, this.width - 20);
                newY = Math.clamp(newY, 0, this.height - 20);
                layout.setPosition(draggingElement, newX, newY);
                this.hudDragMoved = true;
                return true;
            }
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (draggingSetting != null) {
            draggingSetting = null;
            ModernClient.getInstance().getConfigManager().saveConfig();
            return true;
        }
        if (draggingElement != null) {
            String id = draggingElement;
            boolean moved = hudDragMoved;
            draggingElement = null;
            hudDragMoved = false;

            if (!moved) {
                // No drag happened, so this release completes a plain click. Two quick
                // clicks on the same element reset it back to the default position.
                long now = System.currentTimeMillis();
                boolean isDoubleClick = id.equals(lastHudClickId) && (now - lastHudClickTime) < DOUBLE_CLICK_MILLIS;
                lastHudClickId = id;
                lastHudClickTime = now;
                if (isDoubleClick) {
                    ModernClient.getInstance().getHudLayout().setPosition(id, HudLayout.AUTO, HudLayout.AUTO);
                }
            }

            ModernClient.getInstance().getConfigManager().saveConfig();
            return true;
        }
        return super.mouseReleased(event);
    }

    private void drawOutline(GuiGraphicsExtractor extractor, int x1, int y1, int x2, int y2, int color) {
        extractor.fill(x1, y1, x2, y1 + 1, color);
        extractor.fill(x1, y2 - 1, x2, y2, color);
        extractor.fill(x1, y1, x1 + 1, y2, color);
        extractor.fill(x2 - 1, y1, x2, y2, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        ModernClient.getInstance().getConfigManager().saveConfig();
        super.removed();
    }

    @Override
    public void onClose() {
        ModernClient.getInstance().getConfigManager().saveConfig();
        super.onClose();
    }

    private static class ModuleSlot {
        final Module module;
        final int x;
        final int y;
        final int width;
        final List<SettingRow> rows = new ArrayList<>();
        int totalHeight;

        ModuleSlot(Module module, int x, int y, int width) {
            this.module = module;
            this.x = x;
            this.y = y;
            this.width = width;
        }
    }

    private record SettingRow(Setting<?> setting, int x, int y, int width, int height) {

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
