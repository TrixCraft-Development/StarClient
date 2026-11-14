package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.starclient.StarClient;
import net.starclient.friend.FriendManager;
import net.starclient.module.BlockOutlineState;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.NumberSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Team Health / Party HUD module (SERVER / UI):
 * Displays an RPG-style party widget showing teammates' skin avatar, live HP bars,
 * distance, absorption hearts, and damage alerts.
 */
public final class TeamHealthRenderer {

    public static final int WIDGET_WIDTH = 150;
    public static final int ROW_HEIGHT = 26;
    public static final int HEADER_HEIGHT = 16;

    // Track damage timestamps for pulse animation
    private static final Map<String, Float> lastHealthMap = new ConcurrentHashMap<>();
    private static final Map<String, Long> lastDamageTimeMap = new ConcurrentHashMap<>();

    private TeamHealthRenderer() {
    }

    public record TeammateEntry(
            AbstractClientPlayer player,
            String name,
            float health,
            float maxHealth,
            float absorption,
            double distance,
            boolean isFriend,
            boolean recentlyDamaged
    ) {
    }

    public static List<TeammateEntry> getTeammates(Minecraft client, Module mod) {
        List<TeammateEntry> list = new ArrayList<>();
        if (client == null || client.level == null || client.player == null) return list;

        boolean showSelf = mod.getSetting("Show Self") instanceof BooleanSetting bs && bs.getValue();
        boolean includeFriends = !(mod.getSetting("Include Friends") instanceof BooleanSetting bs) || bs.getValue();
        int maxTeammates = mod.getSetting("Max Teammates") instanceof NumberSetting ns ? (int) Math.round(ns.getValue()) : 4;
        long now = System.currentTimeMillis();

        for (AbstractClientPlayer p : client.level.players()) {
            if (p == client.player) {
                if (!showSelf) continue;
            } else {
                if (!p.isAlive() || p.isSpectator()) continue;
                if (BaseAlertRenderer.isNpcOrShopkeeper(client, p)) continue;

                boolean isFriend = FriendManager.isFriend(p);
                boolean isAlly = BaseAlertRenderer.isTeammate(client.player, p);
                if (!isAlly && (!includeFriends || !isFriend)) {
                    continue;
                }
            }

            String name = p.getName().getString();
            float hp = Math.max(0.0F, p.getHealth());
            float maxHp = Math.max(1.0F, p.getMaxHealth());
            float abs = Math.max(0.0F, p.getAbsorptionAmount());
            double dist = (p == client.player) ? 0.0 : client.player.distanceTo(p);
            boolean isFriend = FriendManager.isFriend(p);

            Float prevHp = lastHealthMap.get(name);
            if (prevHp != null && hp < prevHp - 0.25F) {
                lastDamageTimeMap.put(name, now);
            }
            lastHealthMap.put(name, hp);

            Long dmgTime = lastDamageTimeMap.get(name);
            boolean recentlyDamaged = (dmgTime != null && now - dmgTime < 1500L);

            list.add(new TeammateEntry(p, name, hp, maxHp, abs, dist, isFriend, recentlyDamaged));
            if (list.size() >= maxTeammates) break;
        }

        list.sort(Comparator.comparingDouble(TeammateEntry::distance));
        return list;
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod, int x, int y) {
        if (client == null || mod == null) return;

        List<TeammateEntry> teammates = getTeammates(client, mod);
        if (teammates.isEmpty()) {
            return;
        }

        renderWidget(extractor, client.font, mod, x, y, teammates, false);
    }

    public static void renderPreview(GuiGraphicsExtractor extractor, Module mod, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc != null ? mc.font : null;
        if (font == null) return;

        List<TeammateEntry> mockList = List.of(
                new TeammateEntry(mc.player, "Teammate1", 18.0F, 20.0F, 0.0F, 14.5, true, false),
                new TeammateEntry(mc.player, "Teammate2", 8.5F, 20.0F, 4.0F, 28.2, false, true)
        );

        renderWidget(extractor, font, mod, x, y, mockList, true);
    }

    private static void renderWidget(GuiGraphicsExtractor extractor, Font font, Module mod,
                                     int x, int y, List<TeammateEntry> teammates, boolean preview) {
        boolean showDist = !(mod.getSetting("Show Distance") instanceof BooleanSetting bs) || bs.getValue();
        boolean showHealthBar = !(mod.getSetting("Show Health Bar") instanceof BooleanSetting bs) || bs.getValue();
        boolean showHealthText = !(mod.getSetting("Show Health Text") instanceof BooleanSetting bs) || bs.getValue();
        boolean showAbsorption = !(mod.getSetting("Show Absorption") instanceof BooleanSetting bs) || bs.getValue();
        boolean damageFlash = !(mod.getSetting("Damage Flash") instanceof BooleanSetting bs) || bs.getValue();

        boolean useClientColor = !(mod.getSetting("Use Client Color") instanceof BooleanSetting bs) || bs.getValue();
        int accent;
        if (useClientColor && StarClient.getInstance() != null && StarClient.getInstance().getClientSettings() != null) {
            accent = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        } else if (mod.getSetting("Color") instanceof ColorModeSetting cms) {
            accent = cms.getColor();
        } else {
            accent = BlockOutlineState.OUTLINE_COLORS.getOrDefault("Sky Blue", 0xFF38BDF8);
        }

        int rgb = accent & 0x00FFFFFF;
        int headerColor = 0xFF000000 | rgb;
        int cardOutline = 0x33000000 | rgb;
        int avDefaultBorder = 0x44000000 | rgb;

        int totalHeight = HEADER_HEIGHT + teammates.size() * ROW_HEIGHT + 4;

        // Card background
        if (!mod.isTransparentBackground()) {
            GuiRenderUtils.drawDropShadow(extractor, x, y, WIDGET_WIDTH, totalHeight, 6, 3, 90);
            int bgColor = (mod.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
            GuiRenderUtils.fillRoundedRect(extractor, x, y, WIDGET_WIDTH, totalHeight, 6, bgColor);
        }
        GuiRenderUtils.drawRoundedOutline(extractor, x, y, WIDGET_WIDTH, totalHeight, 6, cardOutline);

        // Header
        GuiRenderUtils.drawText(extractor, font, "👥 Team (" + teammates.size() + ")", x + 8, y + 4, headerColor, true);
        extractor.fill(x + 6, y + HEADER_HEIGHT - 1, x + WIDGET_WIDTH - 6, y + HEADER_HEIGHT, 0x22FFFFFF);

        long now = System.currentTimeMillis();
        int curY = y + HEADER_HEIGHT + 2;

        for (TeammateEntry entry : teammates) {
            boolean flashing = damageFlash && entry.recentlyDamaged();
            if (flashing) {
                // Pulse soft red for damage
                float pulse = 0.5F + 0.5F * Mth.sin((now % 400L) / 400.0F * (float) Math.PI * 2.0F);
                int pulseAlpha = (int) (pulse * 60);
                GuiRenderUtils.fillRoundedRect(extractor, x + 4, curY, WIDGET_WIDTH - 8, ROW_HEIGHT - 2, 4, (pulseAlpha << 24) | 0xEF4444);
            }

            // Avatar
            int avX = x + 6;
            int avY = curY + 2;
            int avSize = 18;

            if (entry.player() != null) {
                try {
                    PlayerFaceExtractor.extractRenderState(extractor, entry.player().getSkin(), avX, avY, avSize);
                } catch (Throwable ignored) {
                    GuiRenderUtils.fillRoundedRect(extractor, avX, avY, avSize, avSize, 3, avDefaultBorder);
                }
            } else {
                GuiRenderUtils.fillRoundedRect(extractor, avX, avY, avSize, avSize, 3, avDefaultBorder);
            }
            int avBorder = entry.isFriend() ? 0xFF34D399 : (flashing ? 0xFFEF4444 : avDefaultBorder);
            GuiRenderUtils.drawRoundedOutline(extractor, avX - 1, avY - 1, avSize + 2, avSize + 2, 3, avBorder);

            // Name & Info
            int infoX = avX + avSize + 6;
            String nameText = entry.name();
            if (font.width(nameText) > 65) {
                nameText = font.plainSubstrByWidth(nameText, 58) + "…";
            }
            int nameColor = entry.isFriend() ? 0xFF34D399 : 0xFFF8FAFC;
            GuiRenderUtils.drawText(extractor, font, nameText, infoX, curY + 2, nameColor, true);

            // Distance text
            if (showDist) {
                String distStr = entry.distance() <= 0.05 ? "Self" : String.format(Locale.ROOT, "%.0fm", entry.distance());
                int distWidth = font.width(distStr);
                GuiRenderUtils.drawText(extractor, font, distStr, x + WIDGET_WIDTH - distWidth - 8, curY + 2, 0xFF94A3B8, false);
            }

            // Health Bar
            if (showHealthBar) {
                int barX = infoX;
                int barY = curY + 12;
                int barW = WIDGET_WIDTH - barX - 8;
                int barH = 7;

                GuiRenderUtils.fillRoundedRect(extractor, barX, barY, barW, barH, 2, 0x66000000);

                float hpFraction = Mth.clamp(entry.health() / entry.maxHealth(), 0.0F, 1.0F);
                int fillW = Math.max(1, (int) Math.round(barW * hpFraction));

                int barColor;
                if (hpFraction > 0.60F) {
                    barColor = 0xFF34D399; // Green
                } else if (hpFraction > 0.30F) {
                    barColor = 0xFFFACC15; // Yellow
                } else {
                    barColor = 0xFFEF4444; // Red
                }

                GuiRenderUtils.fillRoundedRect(extractor, barX, barY, fillW, barH, 2, barColor);

                // Absorption overlay
                if (showAbsorption && entry.absorption() > 0.0F) {
                    float absFraction = Mth.clamp(entry.absorption() / entry.maxHealth(), 0.0F, 1.0F);
                    int absW = (int) Math.round(barW * absFraction);
                    if (absW > 0) {
                        GuiRenderUtils.fillRoundedRect(extractor, barX, barY + barH - 2, Math.min(barW, absW), 2, 1, 0xFFFBBF24);
                    }
                }

                // Health text
                if (showHealthText) {
                    int totalHp = (int) Math.ceil(entry.health() + (showAbsorption ? entry.absorption() : 0.0F));
                    String hpText = totalHp + " HP";
                    int textX = barX + (barW - font.width(hpText)) / 2;
                    GuiRenderUtils.drawText(extractor, font, hpText, textX, barY - 1, 0xFFFFFFFF, true);
                }
            } else if (showHealthText) {
                int totalHp = (int) Math.ceil(entry.health() + (showAbsorption ? entry.absorption() : 0.0F));
                String hpText = totalHp + " HP";
                GuiRenderUtils.drawText(extractor, font, hpText, infoX, curY + 12, 0xFFFFFFFF, true);
            }

            curY += ROW_HEIGHT;
        }
    }
}
