package net.starclient.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.starclient.StarClient;
import net.starclient.module.TooltipFeaturesState;

/**
 * Renders visual hover preview cards for Shulker Boxes (3x9 item grid),
 * Ender Chests (cached 3x9 grid), and Filled Maps (128x128 live map texture),
 * anchored cleanly outside the vanilla item tooltip bounding box so it never
 * overlaps the item name or lore.
 */
public final class ContainerPreviewRenderer {

    private static final int MAP_TEX_SIZE = 128;
    private static final Identifier MAP_PREVIEW_TEX_ID =
            Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "map_preview_dynamic");

    private static DynamicTexture mapTexture;
    private static NativeImage mapImage;
    private static int lastMapId = Integer.MIN_VALUE;
    private static long lastMapUploadMs;

    private static ItemStack pendingHoveredStack = ItemStack.EMPTY;

    private ContainerPreviewRenderer() {
    }

    public static void queueForNextTooltip(ItemStack stack) {
        if (!TooltipFeaturesState.isContainerPreviewEnabled() || stack == null || stack.isEmpty()) {
            pendingHoveredStack = ItemStack.EMPTY;
            return;
        }
        pendingHoveredStack = stack;
    }

    public static void clearPending() {
        pendingHoveredStack = ItemStack.EMPTY;
    }

    public static boolean hasPending() {
        return pendingHoveredStack != null && !pendingHoveredStack.isEmpty();
    }

    public static void renderPendingForTooltip(GuiGraphicsExtractor graphics,
                                               int tooltipBoxX, int tooltipBoxY,
                                               int tooltipBoxW, int tooltipBoxH,
                                               int screenW, int screenH) {
        ItemStack stack = pendingHoveredStack;
        pendingHoveredStack = ItemStack.EMPTY;
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        renderAnchoredToTooltip(graphics, client, stack, tooltipBoxX, tooltipBoxY, tooltipBoxW, tooltipBoxH, screenW, screenH);
    }

    public static void renderAnchoredToTooltip(GuiGraphicsExtractor graphics, Minecraft client,
                                               ItemStack stack,
                                               int boxX, int boxY, int boxW, int boxH,
                                               int screenW, int screenH) {
        if (!TooltipFeaturesState.isContainerPreviewEnabled() || stack == null || stack.isEmpty()) {
            return;
        }

        NonNullList<ItemStack> gridItems = null;
        int accentBorder = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        String headerTitle = stack.getHoverName().getString();

        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        boolean isShulker = stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
        if (TooltipFeaturesState.isPreviewShulkers() && (container != null || isShulker)) {
            gridItems = NonNullList.withSize(27, ItemStack.EMPTY);
            if (container != null) {
                container.copyInto(gridItems);
            }
            if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock sbb) {
                DyeColor dye = sbb.getColor();
                if (dye != null) {
                    accentBorder = 0xFF000000 | (dye.getTextColor() & 0x00FFFFFF);
                } else {
                    accentBorder = 0xFFA855F7; // Classic undyed purple shulker
                }
            }
        } else if (TooltipFeaturesState.isPreviewEnderChest()
                && stack.is(Items.ENDER_CHEST)
                && TooltipFeaturesState.hasCachedEnderChest()) {
            gridItems = TooltipFeaturesState.getCachedEnderChest();
            accentBorder = 0xFF2DD4BF; // Ender teal
            headerTitle = "Ender Chest";
        }

        MapId mapId = TooltipFeaturesState.isPreviewMaps() ? stack.get(DataComponents.MAP_ID) : null;
        MapItemSavedData mapData = (mapId != null && client.level != null) ? client.level.getMapData(mapId) : null;

        if (gridItems == null && mapData == null) {
            return;
        }

        // If Display Mode == "Hold Key" and key is not pressed, show a compact "[Hold Shift to Preview]" pill
        // directly below (or above) the vanilla tooltip box so it never hides behind the item name.
        if (!TooltipFeaturesState.isPreviewTriggerActive(client)) {
            if (TooltipFeaturesState.isHoldKeyMode()) {
                renderHoldKeyHint(graphics, client, boxX, boxY, boxW, boxH, screenW, screenH, accentBorder);
            }
            return;
        }

        graphics.nextStratum();

        if (gridItems != null) {
            renderContainerGrid(graphics, client, gridItems, headerTitle, accentBorder, boxX, boxY, boxW, boxH, screenW, screenH);
        } else {
            renderMapCard(graphics, client, mapId, mapData, headerTitle, accentBorder, boxX, boxY, boxW, boxH, screenW, screenH);
        }
    }

    private static void renderHoldKeyHint(GuiGraphicsExtractor graphics, Minecraft client,
                                          int boxX, int boxY, int boxW, int boxH,
                                          int screenW, int screenH, int accent) {
        graphics.nextStratum();
        String hint = "Hold [" + TooltipFeaturesState.getHoldKeyName() + "] to preview";
        int w = client.font.width(hint) + 12;
        int h = 15;
        int gap = 5;

        // Place directly below the vanilla tooltip box; flip above if near bottom of screen
        int x = Math.clamp(boxX, 4, Math.max(4, screenW - w - 4));
        int y = boxY + boxH + gap;
        if (y + h > screenH - 4) {
            y = boxY - h - gap;
        }
        if (y < 4) {
            // Tooltip fills vertical height — place to the right or left of tooltip
            if (boxX + boxW + gap + w <= screenW - 4) {
                x = boxX + boxW + gap;
            } else {
                x = Math.max(4, boxX - w - gap);
            }
            y = Math.clamp(boxY, 4, Math.max(4, screenH - h - 4));
        }

        GuiRenderUtils.drawDropShadow(graphics, x, y, w, h, 4, 2, 95);
        GuiRenderUtils.fillRoundedRect(graphics, x, y, w, h, 4, 0xF20B1120);
        GuiRenderUtils.drawRoundedOutline(graphics, x, y, w, h, 4, GuiRenderUtils.withAlpha(accent, 190));
        graphics.text(client.font, hint, x + 6, y + 4, 0xFFE2E8F0, false);
    }

    private static int[] placeCardOutsideTooltip(int cardW, int cardH,
                                                 int boxX, int boxY, int boxW, int boxH,
                                                 int screenW, int screenH) {
        int gap = 6;
        int x = Math.clamp(boxX, 6, Math.max(6, screenW - cardW - 6));

        // 1. Prefer directly above the vanilla tooltip
        int aboveY = boxY - cardH - gap;
        if (aboveY >= 6) {
            return new int[]{x, aboveY};
        }

        // 2. Otherwise directly below the vanilla tooltip
        int belowY = boxY + boxH + gap;
        if (belowY + cardH <= screenH - 6) {
            return new int[]{x, belowY};
        }

        // 3. Otherwise beside the vanilla tooltip (right first, then left)
        int clampedY = Math.clamp(boxY, 6, Math.max(6, screenH - cardH - 6));
        if (boxX + boxW + gap + cardW <= screenW - 6) {
            return new int[]{boxX + boxW + gap, clampedY};
        }
        if (boxX - cardW - gap >= 6) {
            return new int[]{boxX - cardW - gap, clampedY};
        }

        return new int[]{x, clampedY};
    }

    private static void renderContainerGrid(GuiGraphicsExtractor graphics, Minecraft client,
                                            NonNullList<ItemStack> items, String title, int accent,
                                            int boxX, int boxY, int boxW, int boxH,
                                            int screenW, int screenH) {
        int slotSize = 18;
        int gap = 2;
        int pad = 6;
        int headerH = 14;
        int cardW = pad * 2 + 9 * slotSize + 8 * gap; // 190px
        int cardH = pad * 2 + headerH + 3 * slotSize + 2 * gap; // 84px

        int[] pos = placeCardOutsideTooltip(cardW, cardH, boxX, boxY, boxW, boxH, screenW, screenH);
        int x = pos[0];
        int y = pos[1];

        GuiRenderUtils.drawDropShadow(graphics, x, y, cardW, cardH, 6, 4, 115);
        GuiRenderUtils.fillRoundedRect(graphics, x, y, cardW, cardH, 6, 0xF20B1120);
        GuiRenderUtils.drawRoundedOutline(graphics, x, y, cardW, cardH, 6, accent);

        String clippedTitle = GuiRenderUtils.truncate(client.font, title, cardW - 14);
        graphics.text(client.font, clippedTitle, x + pad, y + 5, 0xFFF8FAFC, true);
        graphics.fill(x + pad, y + 16, x + cardW - pad, y + 17, GuiRenderUtils.withAlpha(accent, 90));

        int gridTop = y + pad + headerH;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = row * 9 + col;
                int sx = x + pad + col * (slotSize + gap);
                int sy = gridTop + row * (slotSize + gap);

                GuiRenderUtils.fillRoundedRect(graphics, sx, sy, slotSize, slotSize, 3, 0x881E293B);
                GuiRenderUtils.drawRoundedOutline(graphics, sx, sy, slotSize, slotSize, 3, 0x26FFFFFF);

                if (idx < items.size()) {
                    ItemStack slotStack = items.get(idx);
                    if (!slotStack.isEmpty()) {
                        graphics.item(slotStack, sx + 1, sy + 1);
                        graphics.itemDecorations(client.font, slotStack, sx + 1, sy + 1);
                    }
                }
            }
        }
    }

    private static void renderMapCard(GuiGraphicsExtractor graphics, Minecraft client,
                                      MapId mapId, MapItemSavedData mapData,
                                      String title, int accent,
                                      int boxX, int boxY, int boxW, int boxH,
                                      int screenW, int screenH) {
        ensureMapTexture(client);
        long now = System.currentTimeMillis();
        if (mapId.id() != lastMapId || (now - lastMapUploadMs) > 250L) {
            lastMapId = mapId.id();
            lastMapUploadMs = now;
            byte[] colors = mapData.colors;
            for (int py = 0; py < MAP_TEX_SIZE; py++) {
                int rowOff = py * MAP_TEX_SIZE;
                for (int px = 0; px < MAP_TEX_SIZE; px++) {
                    int packed = colors[rowOff + px] & 0xFF;
                    int argb = (packed / 4 == 0)
                            ? 0xFF1E293B
                            : (MapColor.getColorFromPackedId(packed) | 0xFF000000);
                    mapImage.setPixel(px, py, argb);
                }
            }
            mapTexture.upload();
        }

        int previewSize = 104;
        int pad = 6;
        int headerH = 14;
        int cardW = previewSize + pad * 2;
        int cardH = previewSize + headerH + pad * 2;

        int[] pos = placeCardOutsideTooltip(cardW, cardH, boxX, boxY, boxW, boxH, screenW, screenH);
        int x = pos[0];
        int y = pos[1];

        GuiRenderUtils.drawDropShadow(graphics, x, y, cardW, cardH, 6, 4, 115);
        GuiRenderUtils.fillRoundedRect(graphics, x, y, cardW, cardH, 6, 0xF20B1120);
        GuiRenderUtils.drawRoundedOutline(graphics, x, y, cardW, cardH, 6, accent);

        String header = GuiRenderUtils.truncate(client.font, title + " (#" + mapId.id() + ")", cardW - 12);
        graphics.text(client.font, header, x + pad, y + 5, 0xFFF8FAFC, true);

        int mapX = x + pad;
        int mapY = y + pad + headerH;
        graphics.blit(MAP_PREVIEW_TEX_ID, mapX, mapY, mapX + previewSize, mapY + previewSize,
                0.0f, 1.0f, 0.0f, 1.0f);
        GuiRenderUtils.drawRoundedOutline(graphics, mapX - 1, mapY - 1, previewSize + 2, previewSize + 2, 2, 0x44FFFFFF);
    }

    private static void ensureMapTexture(Minecraft client) {
        if (mapTexture != null && mapImage != null) return;
        mapImage = new NativeImage(MAP_TEX_SIZE, MAP_TEX_SIZE, true);
        mapTexture = new DynamicTexture(() -> "starclient_map_preview", mapImage);
        client.getTextureManager().register(MAP_PREVIEW_TEX_ID, mapTexture);
    }
}
