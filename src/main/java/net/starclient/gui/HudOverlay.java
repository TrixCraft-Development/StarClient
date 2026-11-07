package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.starclient.StarClient;
import net.starclient.module.ChestEspState;
import net.starclient.module.CombatStatsTracker;
import net.starclient.module.DurabilityGuardState;
import net.starclient.module.Module;
import net.starclient.module.OreEspState;
import net.starclient.setting.Setting;
import org.joml.Matrix3x2fStack;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class HudOverlay {

    private static void hudText(GuiGraphicsExtractor extractor, Minecraft client, String text, int x, int y, int color) {
        extractor.text(client.font, text, x, y, color, StarClient.getInstance().getClientSettings().isHudTextShadow());
    }

    private static int translucentBoxColor(Module module) {
        return (module.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
    }

    private static int boxBorderColor() {
        return 0x28FFFFFF;
    }

    public static void render(GuiGraphicsExtractor extractor) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        updateClickCps();

        HudLayout layout = StarClient.getInstance().getHudLayout();
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();

        // 1. FPS Display (Bottom Left, toggled by "FPS Display" module)
        Module fpsMod = StarClient.getInstance().getModuleManager().getModuleByName("FPS Display");
        if (fpsMod != null && fpsMod.isEnabled()) {
            int fps = client.getFps();
            String stats = "FPS: " + fps;

            int fX = layout.effectiveX(HudLayout.FPS, screenWidth, 0);
            int fY = layout.effectiveY(HudLayout.FPS, screenHeight);
            boolean sc = layout.beginScale(extractor, fX, fY, HudLayout.FPS);
            if (!fpsMod.isTransparentBackground()) {
                GuiRenderUtils.fillRoundedRect(extractor, fX, fY, HudLayout.FPS_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, translucentBoxColor(fpsMod));
                GuiRenderUtils.drawRoundedOutline(extractor, fX, fY, HudLayout.FPS_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, boxBorderColor());
            }
            hudText(extractor, client, stats, fX + 6, fY + 5, fpsMod.getTextColor());
            layout.endScale(extractor, sc);
        }

        // 2. Ping Display (Bottom Left, toggled by "Ping Display" module)
        Module pingMod = StarClient.getInstance().getModuleManager().getModuleByName("Ping Display");
        if (pingMod != null && pingMod.isEnabled()) {
            int ping = 0;
            if (client.player.connection.getPlayerInfo(client.player.getUUID()) != null) {
                ping = Objects.requireNonNull(client.player.connection.getPlayerInfo(client.player.getUUID())).getLatency();
            }
            String stats = "Ping: " + ping + "ms";

            int pX = layout.effectiveX(HudLayout.PING, screenWidth, 0);
            int pY = layout.effectiveY(HudLayout.PING, screenHeight);
            boolean sc = layout.beginScale(extractor, pX, pY, HudLayout.PING);
            if (!pingMod.isTransparentBackground()) {
                GuiRenderUtils.fillRoundedRect(extractor, pX, pY, HudLayout.PING_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, translucentBoxColor(pingMod));
                GuiRenderUtils.drawRoundedOutline(extractor, pX, pY, HudLayout.PING_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, boxBorderColor());
            }
            hudText(extractor, client, stats, pX + 6, pY + 5, pingMod.getTextColor());
            layout.endScale(extractor, sc);
        }

        // 3. Coordinates Display (Bottom Left, toggled by "Coordinates" module)
        Module coordsMod = StarClient.getInstance().getModuleManager().getModuleByName("Coordinates");
        if (coordsMod != null && coordsMod.isEnabled()) {
            int x = (int) client.player.getX();
            int y = (int) client.player.getY();
            int z = (int) client.player.getZ();
            String stats = String.format("XYZ: %d, %d, %d", x, y, z);

            int cX = layout.effectiveX(HudLayout.COORDINATES, screenWidth, 0);
            int cY = layout.effectiveY(HudLayout.COORDINATES, screenHeight);
            boolean sc = layout.beginScale(extractor, cX, cY, HudLayout.COORDINATES);
            if (!coordsMod.isTransparentBackground()) {
                GuiRenderUtils.fillRoundedRect(extractor, cX, cY, HudLayout.COORDS_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, translucentBoxColor(coordsMod));
                GuiRenderUtils.drawRoundedOutline(extractor, cX, cY, HudLayout.COORDS_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, boxBorderColor());
            }
            hudText(extractor, client, stats, cX + 6, cY + 5, coordsMod.getTextColor());
            layout.endScale(extractor, sc);
        }

        // 3b. CPS Display
        Module cpsMod = StarClient.getInstance().getModuleManager().getModuleByName("CPS Display");
        if (cpsMod != null && cpsMod.isEnabled()) {
            Setting<?> showRightSetting = cpsMod.getSetting("Show Right Click");
            boolean showRight = showRightSetting == null || (boolean) showRightSetting.getValue();
            String stats = showRight
                    ? "CPS: " + leftClickTimes.size() + " | " + rightClickTimes.size()
                    : "CPS: " + leftClickTimes.size();

            int cpsX = layout.effectiveX(HudLayout.CPS, screenWidth, 0);
            int cpsY = layout.effectiveY(HudLayout.CPS, screenHeight);
            boolean sc = layout.beginScale(extractor, cpsX, cpsY, HudLayout.CPS);
            if (!cpsMod.isTransparentBackground()) {
                GuiRenderUtils.fillRoundedRect(extractor, cpsX, cpsY, HudLayout.STAT_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, translucentBoxColor(cpsMod));
                GuiRenderUtils.drawRoundedOutline(extractor, cpsX, cpsY, HudLayout.STAT_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, boxBorderColor());
            }
            hudText(extractor, client, stats, cpsX + 6, cpsY + 5, cpsMod.getTextColor());
            layout.endScale(extractor, sc);
        }

        // 3c. Reach Display
        Module reachMod = StarClient.getInstance().getModuleManager().getModuleByName("Reach Display");
        if (reachMod != null && reachMod.isEnabled()) {
            Setting<?> resetSetting = reachMod.getSetting("Reset Time");
            double resetSec = resetSetting == null ? 5.0 : (double) resetSetting.getValue();
            double dist = CombatStatsTracker.getLastReachDistance(resetSec);
            String stats = String.format(Locale.US, "Reach: %.2fm", dist);

            int rX = layout.effectiveX(HudLayout.REACH, screenWidth, 0);
            int rY = layout.effectiveY(HudLayout.REACH, screenHeight);
            boolean sc = layout.beginScale(extractor, rX, rY, HudLayout.REACH);
            if (!reachMod.isTransparentBackground()) {
                GuiRenderUtils.fillRoundedRect(extractor, rX, rY, HudLayout.STAT_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, translucentBoxColor(reachMod));
                GuiRenderUtils.drawRoundedOutline(extractor, rX, rY, HudLayout.STAT_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, boxBorderColor());
            }
            hudText(extractor, client, stats, rX + 6, rY + 5, reachMod.getTextColor());
            layout.endScale(extractor, sc);
        }

        // 3d. Speedometer
        Module speedMod = StarClient.getInstance().getModuleManager().getModuleByName("Speedometer");
        if (speedMod != null && speedMod.isEnabled()) {
            Setting<?> unitSetting = speedMod.getSetting("Unit");
            String unit = unitSetting == null ? "m/s" : (String) unitSetting.getValue();
            double dx = client.player.getX() - client.player.xo;
            double dz = client.player.getZ() - client.player.zo;
            double bps = Math.hypot(dx, dz) * 20.0;
            double value = "km/h".equals(unit) ? bps * 3.6 : bps;
            String stats = String.format(Locale.US, "Speed: %.2f %s", value, unit);

            int sX = layout.effectiveX(HudLayout.SPEED, screenWidth, 0);
            int sY = layout.effectiveY(HudLayout.SPEED, screenHeight);
            boolean sc = layout.beginScale(extractor, sX, sY, HudLayout.SPEED);
            if (!speedMod.isTransparentBackground()) {
                GuiRenderUtils.fillRoundedRect(extractor, sX, sY, HudLayout.STAT_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, translucentBoxColor(speedMod));
                GuiRenderUtils.drawRoundedOutline(extractor, sX, sY, HudLayout.STAT_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, boxBorderColor());
            }
            hudText(extractor, client, stats, sX + 6, sY + 5, speedMod.getTextColor());
            layout.endScale(extractor, sc);
        }

        // 3d2. Combo Counter
        Module comboMod = StarClient.getInstance().getModuleManager().getModuleByName("Combo Counter");
        if (comboMod != null && comboMod.isEnabled()) {
            Setting<?> resetSetting = comboMod.getSetting("Reset Time");
            double resetSec = resetSetting == null ? 4.0 : (double) resetSetting.getValue();
            int combo = CombatStatsTracker.getComboCount(resetSec);
            String stats = combo <= 0 ? "No Combo" : (combo + (combo == 1 ? " Hit" : " Combo"));

            int cbX = layout.effectiveX(HudLayout.COMBO, screenWidth, 0);
            int cbY = layout.effectiveY(HudLayout.COMBO, screenHeight);
            boolean sc = layout.beginScale(extractor, cbX, cbY, HudLayout.COMBO);
            if (!comboMod.isTransparentBackground()) {
                GuiRenderUtils.fillRoundedRect(extractor, cbX, cbY, HudLayout.STAT_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, translucentBoxColor(comboMod));
                GuiRenderUtils.drawRoundedOutline(extractor, cbX, cbY, HudLayout.STAT_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, boxBorderColor());
            }
            hudText(extractor, client, stats, cbX + 6, cbY + 5, comboMod.getTextColor());
            layout.endScale(extractor, sc);
        }

        // 3d3. Real-Time Clock Display
        Module clockMod = StarClient.getInstance().getModuleManager().getModuleByName("Clock Display");
        if (clockMod != null && clockMod.isEnabled()) {
            Setting<?> fmtSetting = clockMod.getSetting("Format");
            boolean is12h = fmtSetting != null && "12-Hour".equals(fmtSetting.getValue());
            Setting<?> secSetting = clockMod.getSetting("Show Seconds");
            boolean showSeconds = secSetting == null || (boolean) secSetting.getValue();
            Setting<?> onlyTimeSetting = clockMod.getSetting("Only Time");
            boolean onlyTime = onlyTimeSetting != null && (boolean) onlyTimeSetting.getValue();
            Setting<?> iconSetting = clockMod.getSetting("Show Icon");
            boolean showIcon = iconSetting == null || (boolean) iconSetting.getValue();
            Setting<?> textSetting = clockMod.getSetting("Show Text");
            boolean showText = textSetting != null && (boolean) textSetting.getValue();

            java.time.LocalTime nowTime = java.time.LocalTime.now();
            String pattern = is12h
                    ? (showSeconds ? "hh:mm:ss a" : "hh:mm a")
                    : (showSeconds ? "HH:mm:ss" : "HH:mm");
            String timeStr = nowTime.format(java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.US));
            String prefix = "";
            if (!onlyTime) {
                if (showIcon && showText) {
                    prefix = "🕒 Time: ";
                } else if (showIcon) {
                    prefix = "🕒 ";
                } else if (showText) {
                    prefix = "Time: ";
                }
            }
            String stats = prefix + timeStr;

            int clX = layout.effectiveX(HudLayout.CLOCK, screenWidth, 0);
            int clY = layout.effectiveY(HudLayout.CLOCK, screenHeight);
            boolean sc = layout.beginScale(extractor, clX, clY, HudLayout.CLOCK);
            if (!clockMod.isTransparentBackground()) {
                GuiRenderUtils.fillRoundedRect(extractor, clX, clY, HudLayout.STAT_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, translucentBoxColor(clockMod));
                GuiRenderUtils.drawRoundedOutline(extractor, clX, clY, HudLayout.STAT_WIDTH, HudLayout.ELEMENT_HEIGHT, 4, boxBorderColor());
            }
            hudText(extractor, client, stats, clX + 6, clY + 5, clockMod.getTextColor());
            layout.endScale(extractor, sc);
        }

        // 3d4. Compass Display
        Module compassMod = StarClient.getInstance().getModuleManager().getModuleByName("Compass Display");
        if (compassMod != null && compassMod.isEnabled()) {
            int compW = CompassRenderer.getWidth(compassMod);
            int compX = layout.effectiveX(HudLayout.COMPASS, screenWidth, compW);
            int compY = layout.effectiveY(HudLayout.COMPASS, screenHeight);
            boolean sc = layout.beginScale(extractor, compX, compY, HudLayout.COMPASS);
            CompassRenderer.render(extractor, client, compassMod, layout, screenWidth, screenHeight);
            layout.endScale(extractor, sc);
        }

        // 3d5. Server Info
        Module serverInfoMod = StarClient.getInstance().getModuleManager().getModuleByName("Server Info");
        if (serverInfoMod != null && serverInfoMod.isEnabled()) {
            int srvW = ServerInfoRenderer.getWidth(client.font, client, serverInfoMod);
            int srvX = layout.effectiveX(HudLayout.SERVER_INFO, screenWidth, srvW);
            int srvY = layout.effectiveY(HudLayout.SERVER_INFO, screenHeight);
            boolean sc = layout.beginScale(extractor, srvX, srvY, HudLayout.SERVER_INFO);
            ServerInfoRenderer.render(extractor, client, serverInfoMod, layout, screenWidth, screenHeight);
            layout.endScale(extractor, sc);
        }

        // 3d6. Memory Display
        Module memoryMod = StarClient.getInstance().getModuleManager().getModuleByName("Memory Display");
        if (memoryMod != null && memoryMod.isEnabled()) {
            int memW = MemoryHudRenderer.getWidth(client.font, memoryMod);
            int memX = layout.effectiveX(HudLayout.MEMORY, screenWidth, memW);
            int memY = layout.effectiveY(HudLayout.MEMORY, screenHeight);
            boolean sc = layout.beginScale(extractor, memX, memY, HudLayout.MEMORY);
            MemoryHudRenderer.render(extractor, client, memoryMod, layout, screenWidth, screenHeight);
            layout.endScale(extractor, sc);
        }

        // 3d7. Minimap
        Module minimapMod = StarClient.getInstance().getModuleManager().getModuleByName("Minimap");
        if (minimapMod != null && minimapMod.isEnabled()) {
            int mapW = MinimapRenderer.getWidth(minimapMod);
            int mapX = layout.effectiveX(HudLayout.MINIMAP, screenWidth, mapW);
            int mapY = layout.effectiveY(HudLayout.MINIMAP, screenHeight);
            boolean sc = layout.beginScale(extractor, mapX, mapY, HudLayout.MINIMAP);
            MinimapRenderer.render(extractor, client, minimapMod, layout, screenWidth, screenHeight);
            layout.endScale(extractor, sc);
        }

        // 3d8. World Info
        Module worldInfoMod = StarClient.getInstance().getModuleManager().getModuleByName("World Info");
        if (worldInfoMod != null && worldInfoMod.isEnabled()) {
            int wiW = WorldInfoRenderer.getWidth(client.font, client, worldInfoMod);
            int wiX = layout.effectiveX(HudLayout.WORLD_INFO, screenWidth, wiW);
            int wiY = layout.effectiveY(HudLayout.WORLD_INFO, screenHeight);
            boolean sc = layout.beginScale(extractor, wiX, wiY, HudLayout.WORLD_INFO);
            WorldInfoRenderer.render(extractor, client, worldInfoMod, layout, screenWidth, screenHeight);
            layout.endScale(extractor, sc);
        }

        // 3d9. Item Counter
        Module itemCounterMod = StarClient.getInstance().getModuleManager().getModuleByName("Item Counter");
        if (itemCounterMod != null && itemCounterMod.isEnabled()) {
            int icW = ItemCounterRenderer.getWidth(client.font, client, itemCounterMod);
            int icX = layout.effectiveX(HudLayout.ITEM_COUNTER, screenWidth, icW);
            int icY = layout.effectiveY(HudLayout.ITEM_COUNTER, screenHeight);
            boolean sc = layout.beginScale(extractor, icX, icY, HudLayout.ITEM_COUNTER);
            ItemCounterRenderer.render(extractor, client, itemCounterMod, layout, screenWidth, screenHeight);
            layout.endScale(extractor, sc);
        }

        // 3d10. Block Info (WAILA)
        Module blockInfoMod = StarClient.getInstance().getModuleManager().getModuleByName("Block Info");
        if (blockInfoMod != null && blockInfoMod.isEnabled()) {
            int biW = BlockInfoRenderer.getWidth(client.font, client, blockInfoMod);
            int biX = layout.effectiveX(HudLayout.BLOCK_INFO, screenWidth, biW);
            int biY = layout.effectiveY(HudLayout.BLOCK_INFO, screenHeight);
            boolean sc = layout.beginScale(extractor, biX, biY, HudLayout.BLOCK_INFO);
            BlockInfoRenderer.render(extractor, client, blockInfoMod, layout, screenWidth, screenHeight);
            layout.endScale(extractor, sc);
        }

        // 3d11. Pinned Notes
        Module pinnedNotesMod = StarClient.getInstance().getModuleManager().getModuleByName("Pinned Notes");
        if (pinnedNotesMod != null && pinnedNotesMod.isEnabled()) {
            int pnW = PinnedNotesRenderer.getWidth(client.font, client, pinnedNotesMod);
            int pnX = layout.effectiveX(HudLayout.PINNED_NOTES, screenWidth, pnW);
            int pnY = layout.effectiveY(HudLayout.PINNED_NOTES, screenHeight);
            boolean sc = layout.beginScale(extractor, pnX, pnY, HudLayout.PINNED_NOTES);
            PinnedNotesRenderer.render(extractor, client, pinnedNotesMod, layout, screenWidth, screenHeight);
            layout.endScale(extractor, sc);
        }

        // 3e. Potion Effects HUD
        Module potionsMod = StarClient.getInstance().getModuleManager().getModuleByName("Potion Effects");
        if (potionsMod != null && potionsMod.isEnabled()) {
            int potX = layout.effectiveX(HudLayout.POTIONS, screenWidth, 0);
            int potY = layout.effectiveY(HudLayout.POTIONS, screenHeight);
            boolean sc = layout.beginScale(extractor, potX, potY, HudLayout.POTIONS);
            renderPotionEffects(extractor, client, potionsMod, layout, screenWidth, screenHeight);
            layout.endScale(extractor, sc);
        }

        // 3f. Target HUD
        Module targetHudMod = StarClient.getInstance().getModuleManager().getModuleByName("Target HUD");
        if (targetHudMod != null && targetHudMod.isEnabled()) {
            int tX = layout.effectiveX(HudLayout.TARGET_HUD, screenWidth, 0);
            int tY = layout.effectiveY(HudLayout.TARGET_HUD, screenHeight);
            boolean sc = layout.beginScale(extractor, tX, tY, HudLayout.TARGET_HUD);
            renderTargetHud(extractor, client, targetHudMod, layout, screenWidth, screenHeight);
            layout.endScale(extractor, sc);
        }

        // 4. Active Modules Arraylist (Top Right, toggled by "Active Modules" module)
        Module activeModsMod = StarClient.getInstance().getModuleManager().getModuleByName("Active Modules");
        if (activeModsMod != null && activeModsMod.isEnabled()) {
            List<Module> activeModules = StarClient.getInstance().getModuleManager().getSortedActiveModules();

            boolean transparentList = activeModsMod.isTransparentBackground();
            int listBgColor = translucentBoxColor(activeModsMod);

            int maxModWidth = 0;
            for (Module m : activeModules) {
                int w = client.font.width(m.getName()) + 12;
                if (w > maxModWidth) {
                    maxModWidth = w;
                }
            }
            boolean arrayAuto = layout.isAuto(HudLayout.ARRAYLIST);
            int arrayLeft = layout.effectiveX(HudLayout.ARRAYLIST, screenWidth, maxModWidth);
            int arrayY = layout.effectiveY(HudLayout.ARRAYLIST, screenHeight);
            boolean sc = layout.beginScale(extractor, arrayAuto ? screenWidth - 8 : arrayLeft, arrayY, HudLayout.ARRAYLIST);
            int listTextColor = activeModsMod.getTextColor();
            for (Module module : activeModules) {
                String name = module.getName();
                int modWidth = client.font.width(name) + 12;
                int modX = arrayAuto ? screenWidth - modWidth - 8 : arrayLeft;

                if (!transparentList) {
                    GuiRenderUtils.fillRoundedRect(extractor, modX, arrayY, modWidth, 14, 3, listBgColor);
                    GuiRenderUtils.drawRoundedOutline(extractor, modX, arrayY, modWidth, 14, 3, 0x22FFFFFF);
                }
                hudText(extractor, client, name, modX + 6, arrayY + 3, listTextColor);

                arrayY += 16;
            }
            layout.endScale(extractor, sc);
        }

        // 5. Keystrokes Overlay (Bottom Right if enabled)
        Module keystrokesMod = StarClient.getInstance().getModuleManager().getModuleByName("Keystrokes");
        if (keystrokesMod != null && keystrokesMod.isEnabled()) {
            int kX = layout.effectiveX(HudLayout.KEYSTROKES, screenWidth, 0);
            int kY = layout.effectiveY(HudLayout.KEYSTROKES, screenHeight);
            boolean sc = layout.beginScale(extractor, kX, kY, HudLayout.KEYSTROKES);
            renderKeystrokes(extractor, client, kX, kY, keystrokesMod);
            layout.endScale(extractor, sc);
        }

        // 6. Armor HUD (Bottom Center, toggled by "Armor HUD" module)
        Module armorMod = StarClient.getInstance().getModuleManager().getModuleByName("Armor HUD");
        if (armorMod != null && armorMod.isEnabled()) {
            int aX = layout.effectiveX(HudLayout.ARMOR, screenWidth, 0);
            int aY = layout.effectiveY(HudLayout.ARMOR, screenHeight);
            boolean sc = layout.beginScale(extractor, aX, aY, HudLayout.ARMOR);
            renderArmor(extractor, client, armorMod);
            layout.endScale(extractor, sc);
        }

        // 6b. Inventory HUD (toggled by "Inventory HUD" module)
        Module invMod = StarClient.getInstance().getModuleManager().getModuleByName("Inventory HUD");
        if (invMod != null && invMod.isEnabled()) {
            InventoryHudRenderer.render(extractor, client, invMod);
        }

        // 7. Entity ESP (Tracer lines & highlights)
        Module espMod = StarClient.getInstance().getModuleManager().getModuleByName("Entity ESP");
        if (espMod != null && espMod.isEnabled()) {
            boolean playersOnly = true;
            String style = "Glowing";
            double maxRange = 100.0;
            Setting<?> playersSetting = espMod.getSetting("Players Only");
            Setting<?> styleSetting = espMod.getSetting("Style");
            Setting<?> rangeSetting = espMod.getSetting("Range");
            if (playersSetting != null) playersOnly = (boolean) playersSetting.getValue();
            if (styleSetting != null) style = (String) styleSetting.getValue();
            if (rangeSetting != null) maxRange = (double) rangeSetting.getValue();
            EntityEsp.render(extractor, client, playersOnly, maxRange, style);
        }

        // 8. Chest ESP (storage blocks highlighted through walls)
        Module chestEspMod = StarClient.getInstance().getModuleManager().getModuleByName("Chest ESP");
        if (chestEspMod != null && chestEspMod.isEnabled()) {
            boolean chestBoxes = true;
            boolean chestTracers = true;
            boolean chestLabels = true;
            Setting<?> boxSetting = chestEspMod.getSetting("Boxes");
            Setting<?> tracerSetting = chestEspMod.getSetting("Tracers");
            Setting<?> labelSetting = chestEspMod.getSetting("Labels");
            if (boxSetting != null) chestBoxes = (boolean) boxSetting.getValue();
            if (tracerSetting != null) chestTracers = (boolean) tracerSetting.getValue();
            if (labelSetting != null) chestLabels = (boolean) labelSetting.getValue();
            ChestEsp.render(extractor, client, ChestEspState.getEntries(), chestBoxes, chestTracers, chestLabels);
        }

        // 9. Ore ESP (ore blocks highlighted through walls)
        Module oreEspMod = StarClient.getInstance().getModuleManager().getModuleByName("Ore ESP");
        if (oreEspMod != null && oreEspMod.isEnabled()) {
            boolean oreBoxes = true;
            boolean oreTracers = true;
            boolean oreLabels = true;
            Setting<?> oreBoxSetting = oreEspMod.getSetting("Boxes");
            Setting<?> oreTracerSetting = oreEspMod.getSetting("Tracers");
            Setting<?> oreLabelSetting = oreEspMod.getSetting("Labels");
            if (oreBoxSetting != null) oreBoxes = (boolean) oreBoxSetting.getValue();
            if (oreTracerSetting != null) oreTracers = (boolean) oreTracerSetting.getValue();
            if (oreLabelSetting != null) oreLabels = (boolean) oreLabelSetting.getValue();
            OreEsp.render(extractor, client, OreEspState.getEntries(), oreBoxes, oreTracers, oreLabels);
        }

        // 10. Hypixel Level Head (server module, drawn above players' heads)
        Module levelHeadMod = StarClient.getInstance().getModuleManager().getModuleByName("Hypixel Level Head");
        if (levelHeadMod != null && levelHeadMod.isEnabled() && !(client.gui.screen() instanceof ClickGuiScreen)) {
            String levelType = "Network";
            String labelColor = "Gold";
            boolean showPrefix = true;
            boolean showSelf = false;
            boolean hypixelOnly = true;
            boolean showBackground = true;
            boolean distanceScale = false;
            double levelRange = 64.0;
            int yOffset = -20;
            Setting<?> typeSetting = levelHeadMod.getSetting("Level Type");
            Setting<?> colorSetting = levelHeadMod.getSetting("Level Color");
            Setting<?> prefixSetting = levelHeadMod.getSetting("Show Prefix");
            Setting<?> backgroundSetting = levelHeadMod.getSetting("Background");
            Setting<?> scaleSetting = levelHeadMod.getSetting("Distance Scale");
            Setting<?> selfSetting = levelHeadMod.getSetting("Show Self");
            Setting<?> hypixelSetting = levelHeadMod.getSetting("Hypixel Only");
            Setting<?> levelRangeSetting = levelHeadMod.getSetting("Range");
            Setting<?> offsetSetting = levelHeadMod.getSetting("Y Offset");
            if (typeSetting != null) levelType = (String) typeSetting.getValue();
            if (colorSetting != null) labelColor = (String) colorSetting.getValue();
            if (prefixSetting != null) showPrefix = (boolean) prefixSetting.getValue();
            if (backgroundSetting != null) showBackground = (boolean) backgroundSetting.getValue();
            if (scaleSetting != null) distanceScale = (boolean) scaleSetting.getValue();
            if (selfSetting != null) showSelf = (boolean) selfSetting.getValue();
            if (hypixelSetting != null) hypixelOnly = (boolean) hypixelSetting.getValue();
            if (levelRangeSetting != null) levelRange = (double) levelRangeSetting.getValue();
            if (offsetSetting != null) yOffset = (int) (double) offsetSetting.getValue();

            int color = HypixelLevelHead.LEVEL_COLORS.getOrDefault(labelColor, 0xFFFACC15);
            HypixelLevelHead.render(extractor, client, levelType, showSelf, showPrefix, hypixelOnly, levelRange,
                    yOffset, color, showBackground, distanceScale);

            String levelStatus = HypixelLevelHead.status(client, hypixelOnly);
            if (levelStatus != null) {
                extractor.text(client.font, levelStatus,
                        screenWidth / 2 - client.font.width(levelStatus) / 2,
                        4,
                        0xFFFBBF24, true);
            }
        }

        // 11. Floating Damage Numbers (RPG / Hypixel style)
        DamageIndicatorManager.getInstance().render(extractor, client);

        // 12. Custom Entity Hitboxes
        Module hitboxMod = StarClient.getInstance().getModuleManager().getModuleByName("Hitboxes");
        if (hitboxMod != null && hitboxMod.isEnabled()) {
            HitboxRenderer.render(extractor, client, hitboxMod);
        }

        // 13. Projectile Trajectories
        Module trajMod = StarClient.getInstance().getModuleManager().getModuleByName("Trajectories");
        if (trajMod != null && trajMod.isEnabled()) {
            TrajectoryRenderer.render(extractor, client, trajMod);
        }

        // 13b. Light Overlay (Mob Spawn Grid)
        Module lightOverlayMod = StarClient.getInstance().getModuleManager().getModuleByName("Light Overlay");
        if (lightOverlayMod != null && lightOverlayMod.isEnabled()) {
            LightOverlayRenderer.render(extractor, client, lightOverlayMod);
        }

        // 13c. Chunk Borders (16x16 Wireframe & Slime Chunk Detector)
        Module chunkBordersMod = StarClient.getInstance().getModuleManager().getModuleByName("Chunk Borders");
        if (chunkBordersMod != null && chunkBordersMod.isEnabled()) {
            ChunkBordersRenderer.render(extractor, client, chunkBordersMod);
        }

        // 14. 3D In-World Waypoints
        if (!(client.gui.screen() instanceof ClickGuiScreen) && !(client.gui.screen() instanceof HudEditorScreen)) {
            WaypointRenderer.render(extractor, client);
        }

        // 15. Keybind Module Toggle Corner Notifications
        ToastNotificationManager.render(extractor, client, screenWidth, screenHeight);
    }

    private static void renderArmor(GuiGraphicsExtractor extractor, Minecraft client, Module module) {
        Setting<?> orientationSetting = module.getSetting("Orientation");
        boolean vertical = orientationSetting != null && "Vertical".equals(orientationSetting.getValue());

        Setting<?> durabilitySetting = module.getSetting("Durability");
        String durabilityMode = durabilitySetting != null && durabilitySetting.getValue() instanceof String mode
                ? mode
                : "Show current & max Durability";
        boolean hidden = "Hidden".equals(durabilityMode);
        boolean onlyCurrent = "Only current".equals(durabilityMode);

        Setting<?> mirrorSetting = module.getSetting("Mirror Items");
        boolean mirror = mirrorSetting != null && Boolean.TRUE.equals(mirrorSetting.getValue());

        Setting<?> heldSetting = module.getSetting("Show Held Item");
        boolean showHeld = heldSetting != null && Boolean.TRUE.equals(heldSetting.getValue());

        Setting<?> alertSetting = module.getSetting("Low Durability Alert");
        boolean lowAlert = alertSetting == null || Boolean.TRUE.equals(alertSetting.getValue());

        boolean headFirst = vertical != mirror;
        EquipmentSlot[] order;
        if (showHeld) {
            order = headFirst
                    ? new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
                    : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.MAINHAND};
        } else {
            order = headFirst
                    ? new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
                    : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        }

        HudLayout layout = StarClient.getInstance().getHudLayout();
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();
        int aX = layout.effectiveX(HudLayout.ARMOR, screenWidth, 0);
        int aY = layout.effectiveY(HudLayout.ARMOR, screenHeight);

        boolean transparent = module.isTransparentBackground();
        int slotColor = (module.getBackgroundColor() & 0x00FFFFFF) | 0x66000000;

        int strideX = layout.armorHorizontalStride();
        int boxOffsetX = vertical ? 0 : (strideX - 18) / 2;
        int stepY = hidden ? 20 : 32;
        boolean pulseRed = (System.currentTimeMillis() / 350L) % 2L == 0L;
        boolean anyLowArmor = false;
        boolean anyLowHeld = false;

        for (int i = 0; i < order.length; i++) {
            int x = vertical ? aX : aX + i * strideX + boxOffsetX;
            int y = vertical ? aY + i * stepY : aY;

            assert client.player != null;
            ItemStack stack = client.player.getItemBySlot(order[i]);
            if (!transparent) {
                GuiRenderUtils.fillRoundedRect(extractor, x, y, 18, 18, 4, slotColor);
                GuiRenderUtils.drawRoundedOutline(extractor, x, y, 18, 18, 4, 0x28FFFFFF);
            }
            if (stack.isEmpty()) continue;

            extractor.item(stack, x + 1, y + 1);
            extractor.itemDecorations(client.font, stack, x + 1, y + 1);

            if (stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                int current = Math.max(max - stack.getDamageValue(), 0);
                float ratio = max <= 0 ? 1.0F : (float) current / max;
                float warnThreshold = DurabilityGuardState.getWarningThresholdRatio();
                if (lowAlert && ratio <= warnThreshold) {
                    if (order[i] == EquipmentSlot.MAINHAND) {
                        anyLowHeld = true;
                    } else {
                        anyLowArmor = true;
                    }
                    int warnCol = pulseRed ? 0xFFEF4444 : 0x88EF4444;
                    GuiRenderUtils.drawRoundedOutline(extractor, x, y, 18, 18, 4, warnCol);
                }
                if (!hidden) {
                    String durability = onlyCurrent ? String.valueOf(current) : (current + "/" + max);
                    int color = ratio > 0.5F ? 0xFF34D399 : (ratio > 0.2F ? 0xFFF59E0B : 0xFFEF4444);
                    hudText(extractor, client, durability, x + 9 - client.font.width(durability) / 2, y + 19, color);
                }
            }
        }

        if (anyLowArmor || anyLowHeld) {
            String warnText = anyLowArmor ? "⚠ Low Armor!" : "⚠ Low Durability!";
            int warnColor = pulseRed ? 0xFFEF4444 : 0xFFF59E0B;
            int textW = client.font.width(warnText);
            int pillW = textW + 12;
            int pillH = 14;
            int pillX = Math.clamp(aX + (layout.armorWidth() - pillW) / 2, 4, Math.max(4, screenWidth - pillW - 4));
            int pillY = (aY >= pillH + 4) ? (aY - pillH - 2) : (aY + layout.armorHeight() + 3);
            pillY = Math.clamp(pillY, 2, Math.max(2, screenHeight - pillH - 2));

            GuiRenderUtils.drawDropShadow(extractor, pillX, pillY, pillW, pillH, 4, 2, 70);
            GuiRenderUtils.fillRoundedRect(extractor, pillX, pillY, pillW, pillH, 4, 0xDD7F1D1D);
            GuiRenderUtils.drawRoundedOutline(extractor, pillX, pillY, pillW, pillH, 4, warnColor);
            hudText(extractor, client, warnText, pillX + (pillW - textW) / 2, pillY + 3, 0xFFFFFFFF);
        }
    }

    private static final ArrayDeque<Long> leftClickTimes = new ArrayDeque<>();
    private static final ArrayDeque<Long> rightClickTimes = new ArrayDeque<>();
    private static boolean prevLeftDown;
    private static boolean prevRightDown;

    private static void updateClickCps() {
        Minecraft client = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        boolean left = client.mouseHandler.isLeftPressed();
        boolean right = client.mouseHandler.isRightPressed();
        if (left && !prevLeftDown) leftClickTimes.addLast(now);
        if (right && !prevRightDown) rightClickTimes.addLast(now);
        prevLeftDown = left;
        prevRightDown = right;
        while (!leftClickTimes.isEmpty() && leftClickTimes.peekFirst() < now - 1000) leftClickTimes.pollFirst();
        while (!rightClickTimes.isEmpty() && rightClickTimes.peekFirst() < now - 1000) rightClickTimes.pollFirst();
    }

    private static void renderKeystrokes(GuiGraphicsExtractor extractor, Minecraft client, int kX, int kY, Module module) {
        boolean w = client.options.keyUp.isDown();
        boolean a = client.options.keyLeft.isDown();
        boolean s = client.options.keyDown.isDown();
        boolean d = client.options.keyRight.isDown();

        boolean transparent = module.isTransparentBackground();
        int bgColor = translucentBoxColor(module);
        int textColor = module.getTextColor();

        drawKey(extractor, client, "W", kX + 22, kY, w, transparent, bgColor, textColor);
        drawKey(extractor, client, "A", kX, kY + 22, a, transparent, bgColor, textColor);
        drawKey(extractor, client, "S", kX + 22, kY + 22, s, transparent, bgColor, textColor);
        drawKey(extractor, client, "D", kX + 44, kY + 22, d, transparent, bgColor, textColor);

        Setting<?> showMouseSetting = module.getSetting("Show Mouse");
        boolean showMouse = showMouseSetting == null || (boolean) showMouseSetting.getValue();
        if (showMouse) {
            int mbY = kY + 44;
            drawMouse(extractor, client, "LMB", leftClickTimes.size(), kX, mbY, client.mouseHandler.isLeftPressed(), transparent, bgColor, textColor);
            drawMouse(extractor, client, "RMB", rightClickTimes.size(), kX + 33, mbY, client.mouseHandler.isRightPressed(), transparent, bgColor, textColor);
        }
    }

    private static LivingEntity lastCrosshairTarget;
    private static long lastCrosshairTime;

    private static void renderTargetHud(GuiGraphicsExtractor extractor, Minecraft client, Module module,
                                        HudLayout layout, int screenWidth, int screenHeight) {
        if (client.player == null || client.level == null) return;

        Setting<?> modeSetting = module.getSetting("Target Mode");
        String mode = modeSetting == null ? "Crosshair & Attack" : (String) modeSetting.getValue();
        Setting<?> playersOnlySetting = module.getSetting("Players Only");
        boolean playersOnly = playersOnlySetting != null && (boolean) playersOnlySetting.getValue();
        Setting<?> showArmorSetting = module.getSetting("Show Armor");
        boolean showArmor = showArmorSetting == null || (boolean) showArmorSetting.getValue();
        Setting<?> holdTimeSetting = module.getSetting("Hold Time");
        long holdMillis = (long) ((holdTimeSetting == null ? 3.0 : (double) holdTimeSetting.getValue()) * 1000.0);

        long now = System.currentTimeMillis();
        if (!"Attack Only".equals(mode) && client.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living && living != client.player && living.isAlive()) {
            if (!playersOnly || living instanceof Player) {
                lastCrosshairTarget = living;
                lastCrosshairTime = now;
            }
        }

        LivingEntity target = null;
        if (!"Attack Only".equals(mode) && lastCrosshairTarget != null && lastCrosshairTarget.isAlive()
                && (now - lastCrosshairTime) <= holdMillis
                && (!playersOnly || lastCrosshairTarget instanceof Player)) {
            target = lastCrosshairTarget;
        }
        if (target == null && !"Crosshair Only".equals(mode)) {
            LivingEntity attacked = CombatStatsTracker.getLastTarget();
            if (attacked != null && attacked.isAlive()
                    && (now - CombatStatsTracker.getLastAttackTime()) <= holdMillis
                    && (!playersOnly || attacked instanceof Player)) {
                target = attacked;
            }
        }
        if (target == null && (client.gui.screen() instanceof ClickGuiScreen || client.gui.screen() instanceof HudEditorScreen)) {
            target = client.player;
        }
        if (target == null) return;

        int tX = layout.effectiveX(HudLayout.TARGET_HUD, screenWidth, 0);
        int tY = layout.effectiveY(HudLayout.TARGET_HUD, screenHeight);
        int w = HudLayout.TARGET_HUD_WIDTH;
        int h = HudLayout.TARGET_HUD_HEIGHT;

        int accent = StarClient.getInstance().getClientSettings().getMenuAccentColor();
        if (!module.isTransparentBackground()) {
            GuiRenderUtils.drawDropShadow(extractor, tX, tY, w, h, 6, 3, 90);
            GuiRenderUtils.fillRoundedRect(extractor, tX, tY, w, h, 6, translucentBoxColor(module));
            GuiRenderUtils.drawRoundedOutline(extractor, tX, tY, w, h, 6, 0x30FFFFFF);
        }

        // Render face / icon
        int avatarX = tX + 6;
        int avatarY = tY + 6;
        int avatarSize = 24;
        if (target instanceof AbstractClientPlayer targetPlayer) {
            Identifier skinTex = targetPlayer.getSkin().body().texturePath();
            extractor.blit(skinTex, avatarX, avatarY, avatarX + avatarSize, avatarY + avatarSize,
                    8 / 64.0F, 16 / 64.0F, 8 / 64.0F, 16 / 64.0F);
            extractor.blit(skinTex, avatarX, avatarY, avatarX + avatarSize, avatarY + avatarSize,
                    40 / 64.0F, 48 / 64.0F, 8 / 64.0F, 16 / 64.0F);
            GuiRenderUtils.drawRoundedOutline(extractor, avatarX - 1, avatarY - 1, avatarSize + 2, avatarSize + 2, 3, 0x33FFFFFF);
        } else {
            GuiRenderUtils.fillRoundedRect(extractor, avatarX, avatarY, avatarSize, avatarSize, 3, 0xFF1E293B);
            GuiRenderUtils.drawRoundedOutline(extractor, avatarX, avatarY, avatarSize, avatarSize, 3, 0x33FFFFFF);
            String initial = target.getName().getString().isEmpty() ? "?" : target.getName().getString().substring(0, 1).toUpperCase(Locale.ROOT);
            hudText(extractor, client, initial, avatarX + (avatarSize - client.font.width(initial)) / 2, avatarY + 8, accent);
        }

        // Name & Distance
        int infoX = avatarX + avatarSize + 6;
        String name = target.getName().getString();
        if (client.font.width(name) > 78) {
            name = client.font.plainSubstrByWidth(name, 74) + "..";
        }
        hudText(extractor, client, name, infoX, tY + 6, module.getTextColor());

        double dist = target == client.player ? 0.0 : client.player.distanceTo(target);
        String distText = String.format(Locale.US, "%.1fm", dist);
        hudText(extractor, client, distText, tX + w - client.font.width(distText) - 5, tY + 6, 0xFF94A3B8);

        // Health text & bar
        float hp = Math.max(0.0F, target.getHealth() + target.getAbsorptionAmount());
        float maxHp = Math.max(1.0F, target.getMaxHealth());
        float ratio = Math.clamp(hp / maxHp, 0.0F, 1.0F);
        int hpColor = ratio > 0.5F ? 0xFF34D399 : (ratio > 0.25F ? 0xFFF59E0B : 0xFFEF4444);

        String hpStr = String.format(Locale.US, "❤ %.1f", hp);
        hudText(extractor, client, hpStr, infoX, tY + 18, hpColor);

        int barX = tX + 6;
        int barY = tY + h - 8;
        int barW = w - 12;
        int barH = 4;
        GuiRenderUtils.fillRoundedRect(extractor, barX, barY, barW, barH, 2, 0x881E293B);
        int filledW = Math.round(barW * ratio);
        if (filledW > 0) {
            GuiRenderUtils.fillRoundedRect(extractor, barX, barY, filledW, barH, 2, hpColor);
        }

        if (showArmor) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            int itemX = infoX + client.font.width(hpStr) + 6;
            int itemY = tY + 14;
            Matrix3x2fStack pose = extractor.pose();
            for (EquipmentSlot slot : slots) {
                ItemStack stack = target.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                pose.pushMatrix();
                pose.translate(itemX, itemY);
                pose.scale(0.65F, 0.65F);
                extractor.item(stack, 0, 0);
                pose.popMatrix();
                itemX += 11;
            }
        }
    }

    private static void renderPotionEffects(GuiGraphicsExtractor extractor, Minecraft client, Module module,
                                            HudLayout layout, int screenWidth, int screenHeight) {
        if (client.player == null) return;
        Collection<MobEffectInstance> active = client.player.getActiveEffects();
        int pX = layout.effectiveX(HudLayout.POTIONS, screenWidth, 0);
        int pY = layout.effectiveY(HudLayout.POTIONS, screenHeight);
        boolean transparent = module.isTransparentBackground();
        int bgColor = translucentBoxColor(module);
        int textColor = module.getTextColor();

        if (active.isEmpty()) {
            if (client.gui.screen() instanceof ClickGuiScreen || client.gui.screen() instanceof HudEditorScreen) {
                if (!transparent) {
                    GuiRenderUtils.fillRoundedRect(extractor, pX, pY, HudLayout.POTIONS_WIDTH, 22, 4, bgColor);
                    GuiRenderUtils.drawRoundedOutline(extractor, pX, pY, HudLayout.POTIONS_WIDTH, 22, 4, boxBorderColor());
                }
                hudText(extractor, client, "Speed II", pX + 6, pY + 3, textColor);
                hudText(extractor, client, "01:30 (Preview)", pX + 6, pY + 12, textColor);
            }
            return;
        }

        int rowY = pY;
        for (MobEffectInstance effect : active) {
            if (!transparent) {
                GuiRenderUtils.fillRoundedRect(extractor, pX, rowY, HudLayout.POTIONS_WIDTH, 22, 4, bgColor);
                GuiRenderUtils.drawRoundedOutline(extractor, pX, rowY, HudLayout.POTIONS_WIDTH, 22, 4, boxBorderColor());
            }
            Identifier sprite = Hud.getMobEffectSprite(effect.getEffect());
            extractor.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, pX + 3, rowY + 2, 18, 18);

            String effectName = effect.getEffect().value().getDisplayName().getString();
            int amp = effect.getAmplifier();
            if (amp > 0 && amp < 10) {
                effectName += " " + romanNumeral(amp + 1);
            }
            hudText(extractor, client, effectName, pX + 24, rowY + 3, textColor);

            String duration = formatEffectDuration(effect);
            hudText(extractor, client, duration, pX + 24, rowY + 12, textColor);
            rowY += 24;
        }
    }

    private static String formatEffectDuration(MobEffectInstance effect) {
        if (effect.isInfiniteDuration()) return "∞";
        int totalSeconds = Math.max(0, effect.getDuration() / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static String romanNumeral(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private static void drawMouse(GuiGraphicsExtractor extractor, Minecraft client, String label, int cps, int x, int y, boolean pressed, boolean transparent, int bgColor, int textColor) {
        int labelCol = pressed ? 0xFFFFFFFF : textColor;
        int accent = StarClient.getInstance().getClientSettings().getMenuAccentColor();

        if (!transparent) {
            int bg = pressed ? accent : bgColor;
            GuiRenderUtils.fillRoundedRect(extractor, x, y, 31, 22, 4, bg);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, 31, 22, 4, pressed ? 0x88FFFFFF : 0x28FFFFFF);
        }
        hudText(extractor, client, label, x + (31 - client.font.width(label)) / 2, y + 3, labelCol);
        String cpsText = cps + " CPS";
        hudText(extractor, client, cpsText, x + (31 - client.font.width(cpsText)) / 2, y + 12, labelCol);
    }

    private static void drawKey(GuiGraphicsExtractor extractor, Minecraft client, String label, int x, int y, boolean pressed, boolean transparent, int bgColor, int textColor) {
        int textCol = pressed ? 0xFFFFFFFF : textColor;
        int accent = StarClient.getInstance().getClientSettings().getMenuAccentColor();

        if (!transparent) {
            int bg = pressed ? accent : bgColor;
            GuiRenderUtils.fillRoundedRect(extractor, x, y, 20, 20, 4, bg);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, 20, 20, 4, pressed ? 0x88FFFFFF : 0x28FFFFFF);
        }
        hudText(extractor, client, label, x + (20 - client.font.width(label)) / 2, y + (20 - 8) / 2, textCol);
    }
}