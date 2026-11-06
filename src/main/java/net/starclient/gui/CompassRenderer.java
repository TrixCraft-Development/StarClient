package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ModeSetting;
import net.starclient.setting.NumberSetting;

import java.util.Locale;

public final class CompassRenderer {

    private CompassRenderer() {
    }

    public static int getWidth(Module module) {
        if (module != null && module.getSetting("Style") instanceof ModeSetting ms && "Compact".equals(ms.getValue())) {
            return HudLayout.STAT_WIDTH;
        }
        if (module != null && module.getSetting("Ribbon Width") instanceof NumberSetting ns) {
            return ns.getValue().intValue();
        }
        return 210;
    }

    public static int getHeight(Module module) {
        if (module != null && module.getSetting("Style") instanceof ModeSetting ms && "Compact".equals(ms.getValue())) {
            return HudLayout.ELEMENT_HEIGHT;
        }
        boolean showDegrees = module == null || !(module.getSetting("Show Degrees") instanceof BooleanSetting bs) || bs.getValue();
        return showDegrees ? 34 : 22;
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module module, HudLayout layout, int screenW, int screenH) {
        if (module == null || !module.isEnabled()) {
            return;
        }
        float yaw = client.player != null
                ? client.player.getViewYRot(client.getDeltaTracker().getGameTimeDeltaPartialTick(true))
                : 180.0F;
        // Convert Minecraft yaw (0 = South, 90 = West, 180 = North, -90 = East) to standard compass bearing (0 = North, 90 = East, 180 = South, 270 = West)
        float bearing = Mth.positiveModulo(yaw + 180.0F, 360.0F);

        int w = getWidth(module);
        int h = getHeight(module);
        int x = layout.effectiveX(HudLayout.COMPASS, screenW, w);
        int y = layout.effectiveY(HudLayout.COMPASS, screenH);
        x = Math.clamp(x, 2, Math.max(2, screenW - w - 2));
        y = Math.clamp(y, 2, Math.max(2, screenH - h - 2));

        boolean isCompact = module.getSetting("Style") instanceof ModeSetting ms && "Compact".equals(ms.getValue());
        int textColor = module.getTextColor();
        int accent = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        boolean textShadow = StarClient.getInstance().getClientSettings().isHudTextShadow();
        int boxBg = (module.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
        int borderCol = 0x28FFFFFF;

        if (isCompact) {
            if (!module.isTransparentBackground()) {
                GuiRenderUtils.fillRoundedRect(extractor, x, y, w, h, 4, boxBg);
                GuiRenderUtils.drawRoundedOutline(extractor, x, y, w, h, 4, borderCol);
            }
            String dir = cardinalForBearing(bearing);
            String label = String.format(Locale.US, "🧭 %s • %d°", dir, Math.round(bearing) % 360);
            extractor.text(client.font, label, x + 6, y + 5, textColor, textShadow);
            return;
        }

        int ribbonH = 22;
        if (!module.isTransparentBackground()) {
            GuiRenderUtils.fillRoundedRect(extractor, x, y, w, ribbonH, 5, boxBg);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, w, ribbonH, 5, borderCol);
        }

        boolean showInter = !(module.getSetting("Show Intercardinals") instanceof BooleanSetting bs) || bs.getValue();
        boolean showDegrees = !(module.getSetting("Show Degrees") instanceof BooleanSetting bs) || bs.getValue();

        int centerX = x + w / 2;
        float pxPerDeg = (w - 16) / 150.0F; // 150 degrees visible across ribbon

        extractor.enableScissor(x + 3, y + 1, x + w - 3, y + ribbonH - 1);
        int startDeg = Mth.floor(bearing - 80.0F);
        int endDeg = Mth.ceil(bearing + 80.0F);
        int firstStep = Math.floorDiv(startDeg, 5) * 5;

        for (int deg = firstStep; deg <= endDeg; deg += 5) {
            float diff = deg - bearing;
            int tickX = Math.round(centerX + diff * pxPerDeg);
            int normDeg = Math.floorMod(deg, 360);

            if (normDeg % 90 == 0) {
                String card = switch (normDeg) {
                    case 0 -> "N";
                    case 90 -> "E";
                    case 180 -> "S";
                    default -> "W";
                };
                int col = normDeg == 0 ? 0xFFF87171 : textColor;
                extractor.fill(tickX, y + 2, tickX + 1, y + 6, col);
                int tw = client.font.width(card);
                extractor.text(client.font, card, tickX - tw / 2, y + 8, col, true);
            } else if (normDeg % 45 == 0 && showInter) {
                String inter = switch (normDeg) {
                    case 45 -> "NE";
                    case 135 -> "SE";
                    case 225 -> "SW";
                    default -> "NW";
                };
                extractor.fill(tickX, y + 2, tickX + 1, y + 5, 0xFF94A3B8);
                int tw = client.font.width(inter);
                extractor.text(client.font, inter, tickX - tw / 2, y + 8, 0xFFCBD5E1, true);
            } else if (normDeg % 15 == 0) {
                extractor.fill(tickX, y + 2, tickX + 1, y + 6, 0x8894A3B8);
            } else {
                extractor.fill(tickX, y + 2, tickX + 1, y + 4, 0x4494A3B8);
            }
        }
        extractor.disableScissor();

        // Center pointer notch at top of ribbon
        extractor.fill(centerX - 2, y + 1, centerX + 3, y + 2, accent);
        extractor.fill(centerX - 1, y + 2, centerX + 2, y + 3, accent);
        extractor.fill(centerX, y + 3, centerX + 1, y + 4, accent);

        if (showDegrees) {
            String badge = String.format(Locale.US, "%d° %s", Math.round(bearing) % 360, cardinalForBearing(bearing));
            int bw = client.font.width(badge) + 10;
            int bh = 11;
            int bx = centerX - bw / 2;
            int by = y + ribbonH + 1;
            if (!module.isTransparentBackground()) {
                GuiRenderUtils.fillRoundedRect(extractor, bx, by, bw, bh, 3, boxBg);
                GuiRenderUtils.drawRoundedOutline(extractor, bx, by, bw, bh, 3, borderCol);
            }
            extractor.text(client.font, badge, bx + 5, by + 2, textColor, textShadow);
        }
    }

    private static String cardinalForBearing(float bearing) {
        String[] dirs = new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        float step = 360.0F / dirs.length;
        int idx = Math.floorMod(Math.round(bearing / step), dirs.length);
        return dirs[idx];
    }
}
