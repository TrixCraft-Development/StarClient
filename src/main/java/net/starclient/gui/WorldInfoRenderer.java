package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.module.WaypointsState;
import net.starclient.setting.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the draggable "World Info" HUD widget displaying current Biome,
 * In-Game Day counter, Light Level (with mob-spawn warning), Chunk coordinates, and World Seed.
 */
public final class WorldInfoRenderer {

    private static final Map<String, String> CACHED_SERVER_SEEDS = new ConcurrentHashMap<>();
    private static final Pattern BRACKET_SEED_PATTERN = Pattern.compile("\\[(-?\\d{4,20})]");

    private WorldInfoRenderer() {
    }

    private record InfoLine(String text, int overrideColor) {
    }

    /**
     * Captures the seed printed by "/seed" on Multiplayer servers (e.g. "Seed: [-123456789]")
     * so "Show Seed" can display the exact seed on servers once "/seed" has been run.
     */
    public static void captureSeedFromServerMessage(Component message) {
        if (message == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        String detected = findSeedCopyClickEvent(message);
        if (detected == null) {
            String plain = message.getString();
            String lower = plain.toLowerCase(Locale.ROOT);
            if (lower.contains("seed")) {
                Matcher m = BRACKET_SEED_PATTERN.matcher(plain);
                if (m.find()) {
                    detected = m.group(1);
                }
            }
        }

        if (detected != null && !detected.isEmpty()) {
            String wk = WaypointsState.getCurrentWorldKey(mc);
            if (wk != null) {
                CACHED_SERVER_SEEDS.put(wk, detected);
            }
        }
    }

    private static String findSeedCopyClickEvent(Component comp) {
        if (comp == null) return null;
        if (comp.getStyle().getClickEvent() instanceof ClickEvent.CopyToClipboard copy) {
            String val = copy.value();
            if (val != null && val.matches("-?\\d{4,20}") && comp.getString().contains(val)) {
                return val;
            }
        }
        for (Component sib : comp.getSiblings()) {
            String found = findSeedCopyClickEvent(sib);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Returns the known numeric world seed if playing Singleplayer or if "/seed" was captured on this server.
     */
    public static OptionalLong getKnownSeed(Minecraft client) {
        if (client == null || client.level == null) {
            return OptionalLong.empty();
        }
        IntegratedServer singleplayer = client.getSingleplayerServer();
        if (singleplayer != null) {
            ServerLevel ow = singleplayer.overworld();
            if (ow != null) {
                return OptionalLong.of(ow.getSeed());
            }
        }
        String wk = WaypointsState.getCurrentWorldKey(client);
        if (wk != null) {
            String cached = CACHED_SERVER_SEEDS.get(wk);
            if (cached != null) {
                try {
                    return OptionalLong.of(Long.parseLong(cached));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return OptionalLong.empty();
    }

    private static String resolveSeedText(Minecraft client) {
        if (client == null || client.level == null) {
            return "-4829104728193041239";
        }
        IntegratedServer singleplayer = client.getSingleplayerServer();
        if (singleplayer != null) {
            ServerLevel ow = singleplayer.overworld();
            if (ow != null) {
                return String.valueOf(ow.getSeed());
            }
        }
        String wk = WaypointsState.getCurrentWorldKey(client);
        if (wk != null) {
            String cached = CACHED_SERVER_SEEDS.get(wk);
            if (cached != null) {
                return cached;
            }
        }
        return "Hidden (/seed)";
    }

    private static List<InfoLine> collectLines(Minecraft client, Module mod) {
        boolean showBiome = getBool(mod, "Show Biome", true);
        boolean showDay = getBool(mod, "Show Day", true);
        boolean showLight = getBool(mod, "Show Light Level", true);
        boolean showChunk = getBool(mod, "Show Chunk", false);
        boolean showSeed = getBool(mod, "Show Seed", false);
        boolean compact = getBool(mod, "Compact Mode", false);

        String biomeStr = "Plains";
        long day = 42L;
        int blockLight = 12;
        int skyLight = 15;
        int chunkX = 7;
        int chunkZ = -5;
        int relX = 4;
        int relZ = 11;
        String seedStr = showSeed ? resolveSeedText(client) : "";

        if (client != null && client.level != null && client.player != null) {
            BlockPos pos = client.player.blockPosition();
            Holder<Biome> biomeHolder = client.level.getBiome(pos);
            biomeStr = biomeHolder.unwrapKey()
                    .map(key -> prettifyId(key.identifier().getPath()))
                    .orElse("Unknown");

            day = Math.max(0L, client.level.getOverworldClockTime() / 24000L);
            blockLight = client.level.getBrightness(LightLayer.BLOCK, pos);
            skyLight = client.level.getBrightness(LightLayer.SKY, pos);
            chunkX = pos.getX() >> 4;
            chunkZ = pos.getZ() >> 4;
            relX = pos.getX() & 15;
            relZ = pos.getZ() & 15;
        }

        List<InfoLine> lines = new ArrayList<>(5);
        if (compact) {
            List<String> parts = new ArrayList<>(5);
            if (showBiome) parts.add(biomeStr);
            if (showDay) parts.add("Day " + day);
            if (showLight) {
                int maxLight = Math.max(blockLight, skyLight);
                parts.add("☀ " + maxLight + (blockLight == 0 ? " ⚠" : ""));
            }
            if (showChunk) parts.add("C: " + chunkX + ", " + chunkZ);
            if (showSeed) parts.add("Seed: " + seedStr);
            if (parts.isEmpty()) {
                parts.add("World Info");
            }
            int warnCol = (showLight && blockLight == 0) ? 0xFFFBBF24 : 0;
            lines.add(new InfoLine(String.join(" • ", parts), warnCol));
            return lines;
        }

        if (showBiome) {
            lines.add(new InfoLine("Biome: " + biomeStr, 0));
        }
        if (showDay) {
            lines.add(new InfoLine("Day: " + day, 0));
        }
        if (showLight) {
            int maxLight = Math.max(blockLight, skyLight);
            String lightText = "Light: " + maxLight + " (B:" + blockLight + " S:" + skyLight + ")";
            int color = blockLight == 0 ? 0xFFF87171 : (blockLight <= 7 ? 0xFFFBBF24 : 0);
            lines.add(new InfoLine(lightText, color));
        }
        if (showChunk) {
            lines.add(new InfoLine("Chunk: " + chunkX + ", " + chunkZ + " [" + relX + ", " + relZ + "]", 0));
        }
        if (showSeed) {
            lines.add(new InfoLine("Seed: " + seedStr, 0));
        }
        if (lines.isEmpty()) {
            lines.add(new InfoLine("World Info (No lines enabled)", 0));
        }
        return lines;
    }

    public static int getWidth(Font font, Minecraft client, Module mod) {
        if (font == null) return 140;
        List<InfoLine> lines = collectLines(client, mod);
        int maxW = 0;
        for (InfoLine line : lines) {
            int w = font.width(line.text());
            if (w > maxW) maxW = w;
        }
        return Math.max(88, maxW + 14);
    }

    public static int getHeight(Minecraft client, Module mod) {
        List<InfoLine> lines = collectLines(client, mod);
        if (lines.size() <= 1) {
            return HudLayout.ELEMENT_HEIGHT;
        }
        return 8 + lines.size() * 11;
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod,
                              HudLayout layout, int screenWidth, int screenHeight) {
        if (mod == null) return;
        List<InfoLine> lines = collectLines(client, mod);
        int width = getWidth(client.font, client, mod);
        int height = getHeight(client, mod);

        int x = layout.effectiveX(HudLayout.WORLD_INFO, screenWidth, width);
        int y = layout.effectiveY(HudLayout.WORLD_INFO, screenHeight);
        x = Math.clamp(x, 2, Math.max(2, screenWidth - width - 2));
        y = Math.clamp(y, 2, Math.max(2, screenHeight - height - 2));

        if (!mod.isTransparentBackground()) {
            int bg = (mod.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
            GuiRenderUtils.fillRoundedRect(extractor, x, y, width, height, 4, bg);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, width, height, 4, 0x28FFFFFF);
        }

        boolean shadow = StarClient.getInstance().getClientSettings().isHudTextShadow();
        int baseColor = mod.getTextColor();
        int textY = (y + 5);
        for (InfoLine line : lines) {
            int col = line.overrideColor() != 0 ? line.overrideColor() : baseColor;
            extractor.text(client.font, line.text(), x + 7, textY, col, shadow);
            textY += 11;
        }
    }

    private static String prettifyId(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown";
        String[] parts = raw.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.isEmpty() ? raw : sb.toString();
    }

    private static boolean getBool(Module mod, String name, boolean def) {
        if (mod == null) return def;
        Setting<?> s = mod.getSetting(name);
        return s != null && s.getValue() instanceof Boolean b ? b : def;
    }
}
