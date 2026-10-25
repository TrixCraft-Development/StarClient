package net.modernclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.modernclient.ModernClient;
import net.modernclient.module.ChestEspState;
import net.modernclient.module.CombatStatsTracker;
import net.modernclient.module.Module;
import net.modernclient.module.OreEspState;
import net.modernclient.setting.Setting;
import org.joml.Matrix3x2fStack;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class HudOverlay {

    private static void hudText(GuiGraphicsExtractor extractor, Minecraft client, String text, int x, int y, int color) {
        extractor.text(client.font, text, x, y, color, ModernClient.getInstance().getClientSettings().isHudTextShadow());
    }

    private static int translucentBoxColor(Module module) {
        return (module.getBackgroundColor() & 0x00FFFFFF) | 0xCC000000;
    }

    public static void render(GuiGraphicsExtractor extractor) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        updateClickCps();

        HudLayout layout = ModernClient.getInstance().getHudLayout();
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();

        // 1. FPS Display (Bottom Left, toggled by "FPS Display" module)
        Module fpsMod = ModernClient.getInstance().getModuleManager().getModuleByName("FPS Display");
        if (fpsMod != null && fpsMod.isEnabled()) {
            int fps = client.getFps();
            String stats = "FPS: " + fps;

            int fX = layout.effectiveX(HudLayout.FPS, screenWidth, 0);
            int fY = layout.effectiveY(HudLayout.FPS, screenHeight);
            if (!fpsMod.isTransparentBackground()) {
                extractor.fill(fX, fY, fX + HudLayout.FPS_WIDTH, fY + HudLayout.ELEMENT_HEIGHT, translucentBoxColor(fpsMod));
            }
            hudText(extractor, client, stats, fX + 6, fY + 5, fpsMod.getTextColor());
        }

        // 2. Ping Display (Bottom Left, toggled by "Ping Display" module)
        Module pingMod = ModernClient.getInstance().getModuleManager().getModuleByName("Ping Display");
        if (pingMod != null && pingMod.isEnabled()) {
            if (client.player.connection != null && client.player.connection.getPlayerInfo(client.player.getUUID()) != null) {
                int ping = Objects.requireNonNull(client.player.connection.getPlayerInfo(client.player.getUUID())).getLatency();
                String stats = "Ping: " + ping + "ms";

                int pX = layout.effectiveX(HudLayout.PING, screenWidth, 0);
                int pY = layout.effectiveY(HudLayout.PING, screenHeight);
                if (!pingMod.isTransparentBackground()) {
                    extractor.fill(pX, pY, pX + HudLayout.PING_WIDTH, pY + HudLayout.ELEMENT_HEIGHT, translucentBoxColor(pingMod));
                }
                hudText(extractor, client, stats, pX + 6, pY + 5, pingMod.getTextColor());
            }
        }

        // 3. Coordinates Display (Bottom Left, toggled by "Coordinates" module)
        Module coordsMod = ModernClient.getInstance().getModuleManager().getModuleByName("Coordinates");
        if (coordsMod != null && coordsMod.isEnabled()) {
            int x = (int) client.player.getX();
            int y = (int) client.player.getY();
            int z = (int) client.player.getZ();
            String stats = String.format("XYZ: %d, %d, %d", x, y, z);

            int cX = layout.effectiveX(HudLayout.COORDINATES, screenWidth, 0);
            int cY = layout.effectiveY(HudLayout.COORDINATES, screenHeight);
            if (!coordsMod.isTransparentBackground()) {
                extractor.fill(cX, cY, cX + HudLayout.COORDS_WIDTH, cY + HudLayout.ELEMENT_HEIGHT, translucentBoxColor(coordsMod));
            }
            hudText(extractor, client, stats, cX + 6, cY + 5, coordsMod.getTextColor());
        }

        // 3b. CPS Display
        Module cpsMod = ModernClient.getInstance().getModuleManager().getModuleByName("CPS Display");
        if (cpsMod != null && cpsMod.isEnabled()) {
            Setting<?> showRightSetting = cpsMod.getSetting("Show Right Click");
            boolean showRight = showRightSetting == null || (boolean) showRightSetting.getValue();
            String stats = showRight
                    ? "CPS: " + leftClickTimes.size() + " | " + rightClickTimes.size()
                    : "CPS: " + leftClickTimes.size();

            int cpsX = layout.effectiveX(HudLayout.CPS, screenWidth, 0);
            int cpsY = layout.effectiveY(HudLayout.CPS, screenHeight);
            if (!cpsMod.isTransparentBackground()) {
                extractor.fill(cpsX, cpsY, cpsX + HudLayout.STAT_WIDTH, cpsY + HudLayout.ELEMENT_HEIGHT, translucentBoxColor(cpsMod));
            }
            hudText(extractor, client, stats, cpsX + 6, cpsY + 5, cpsMod.getTextColor());
        }

        // 3c. Reach Display
        Module reachMod = ModernClient.getInstance().getModuleManager().getModuleByName("Reach Display");
        if (reachMod != null && reachMod.isEnabled()) {
            Setting<?> resetSetting = reachMod.getSetting("Reset Time");
            double resetSec = resetSetting == null ? 5.0 : (double) resetSetting.getValue();
            double dist = CombatStatsTracker.getLastReachDistance(resetSec);
            String stats = String.format(Locale.US, "Reach: %.2fm", dist);

            int rX = layout.effectiveX(HudLayout.REACH, screenWidth, 0);
            int rY = layout.effectiveY(HudLayout.REACH, screenHeight);
            if (!reachMod.isTransparentBackground()) {
                extractor.fill(rX, rY, rX + HudLayout.STAT_WIDTH, rY + HudLayout.ELEMENT_HEIGHT, translucentBoxColor(reachMod));
            }
            hudText(extractor, client, stats, rX + 6, rY + 5, reachMod.getTextColor());
        }

        // 3d. Speedometer
        Module speedMod = ModernClient.getInstance().getModuleManager().getModuleByName("Speedometer");
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
            if (!speedMod.isTransparentBackground()) {
                extractor.fill(sX, sY, sX + HudLayout.STAT_WIDTH, sY + HudLayout.ELEMENT_HEIGHT, translucentBoxColor(speedMod));
            }
            hudText(extractor, client, stats, sX + 6, sY + 5, speedMod.getTextColor());
        }

        // 3e. Potion Effects HUD
        Module potionsMod = ModernClient.getInstance().getModuleManager().getModuleByName("Potion Effects");
        if (potionsMod != null && potionsMod.isEnabled()) {
            renderPotionEffects(extractor, client, potionsMod, layout, screenWidth, screenHeight);
        }

        // 3f. Target HUD
        Module targetHudMod = ModernClient.getInstance().getModuleManager().getModuleByName("Target HUD");
        if (targetHudMod != null && targetHudMod.isEnabled()) {
            renderTargetHud(extractor, client, targetHudMod, layout, screenWidth, screenHeight);
        }

        // 4. Active Modules Arraylist (Top Right, toggled by "Active Modules" module)
        Module activeModsMod = ModernClient.getInstance().getModuleManager().getModuleByName("Active Modules");
        if (activeModsMod != null && activeModsMod.isEnabled()) {
            List<Module> activeModules = ModernClient.getInstance().getModuleManager().getModules().stream()
                    .filter(Module::isEnabled)
                    .sorted(Comparator.comparing(m -> m.getName().toLowerCase()))
                    .collect(Collectors.toList());

            boolean transparentList = activeModsMod.isTransparentBackground();
            int listBgColor = translucentBoxColor(activeModsMod);

            int maxModWidth = activeModules.stream().mapToInt(m -> client.font.width(m.getName()) + 12).max().orElse(0);
            boolean arrayAuto = layout.isAuto(HudLayout.ARRAYLIST);
            int arrayLeft = layout.effectiveX(HudLayout.ARRAYLIST, screenWidth, maxModWidth);
            int arrayY = layout.effectiveY(HudLayout.ARRAYLIST, screenHeight);
            int listTextColor = activeModsMod.getTextColor();
            for (Module module : activeModules) {
                String name = module.getName();
                int modWidth = client.font.width(name) + 12;
                int modX = arrayAuto ? screenWidth - modWidth - 8 : arrayLeft;

                if (!transparentList) {
                    extractor.fill(modX, arrayY, modX + modWidth, arrayY + 14, listBgColor);
                    extractor.fill(modX + modWidth - 2, arrayY, modX + modWidth, arrayY + 14, listTextColor);
                }
                hudText(extractor, client, name, modX + 6, arrayY + 3, listTextColor);

                arrayY += 16;
            }
        }

        // 5. Keystrokes Overlay (Bottom Right if enabled)
        Module keystrokesMod = ModernClient.getInstance().getModuleManager().getModuleByName("Keystrokes");
        if (keystrokesMod != null && keystrokesMod.isEnabled()) {
            int kX = layout.effectiveX(HudLayout.KEYSTROKES, screenWidth, 0);
            int kY = layout.effectiveY(HudLayout.KEYSTROKES, screenHeight);
            renderKeystrokes(extractor, client, kX, kY, keystrokesMod);
        }

        // 6. Armor HUD (Bottom Center, toggled by "Armor HUD" module)
        Module armorMod = ModernClient.getInstance().getModuleManager().getModuleByName("Armor HUD");
        if (armorMod != null && armorMod.isEnabled()) {
            renderArmor(extractor, client, armorMod);
        }

        // 7. Entity ESP (Tracer lines & highlights)
        Module espMod = ModernClient.getInstance().getModuleManager().getModuleByName("Entity ESP");
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
        Module chestEspMod = ModernClient.getInstance().getModuleManager().getModuleByName("Chest ESP");
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
        Module oreEspMod = ModernClient.getInstance().getModuleManager().getModuleByName("Ore ESP");
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
        Module levelHeadMod = ModernClient.getInstance().getModuleManager().getModuleByName("Hypixel Level Head");
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

        boolean headFirst = vertical != mirror;
        EquipmentSlot[] order = headFirst
                ? new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
                : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

        HudLayout layout = ModernClient.getInstance().getHudLayout();
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();
        int aX = layout.effectiveX(HudLayout.ARMOR, screenWidth, 0);
        int aY = layout.effectiveY(HudLayout.ARMOR, screenHeight);

        boolean transparent = module.isTransparentBackground();
        int slotColor = (module.getBackgroundColor() & 0x00FFFFFF) | 0x66000000;

        int strideX = layout.armorHorizontalStride();
        int boxOffsetX = vertical ? 0 : (strideX - 18) / 2;
        int stepY = hidden ? 20 : 32;

        for (int i = 0; i < order.length; i++) {
            int x = vertical ? aX : aX + i * strideX + boxOffsetX;
            int y = vertical ? aY + i * stepY : aY;

            assert client.player != null;
            ItemStack stack = client.player.getItemBySlot(order[i]);
            if (!transparent) {
                extractor.fill(x, y, x + 18, y + 18, slotColor);
            }
            if (stack.isEmpty()) continue;

            extractor.item(stack, x + 1, y + 1);
            extractor.itemDecorations(client.font, stack, x + 1, y + 1);

            if (!hidden && stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                int current = Math.max(max - stack.getDamageValue(), 0);
                String durability = onlyCurrent ? String.valueOf(current) : (current + "/" + max);
                float ratio = max <= 0 ? 1.0F : (float) current / max;
                int color = ratio > 0.5F ? 0xFF34D399 : (ratio > 0.2F ? 0xFFF59E0B : 0xFFEF4444);
                hudText(extractor, client, durability, x + 9 - client.font.width(durability) / 2, y + 19, color);
            }
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

        drawKey(extractor, client, "W", kX + 22, kY, 20, 20, w, transparent, bgColor, textColor);
        drawKey(extractor, client, "A", kX, kY + 22, 20, 20, a, transparent, bgColor, textColor);
        drawKey(extractor, client, "S", kX + 22, kY + 22, 20, 20, s, transparent, bgColor, textColor);
        drawKey(extractor, client, "D", kX + 44, kY + 22, 20, 20, d, transparent, bgColor, textColor);

        Setting<?> showMouseSetting = module.getSetting("Show Mouse");
        boolean showMouse = showMouseSetting == null || (boolean) showMouseSetting.getValue();
        if (showMouse) {
            int mbY = kY + 44;
            drawMouse(extractor, client, "LMB", leftClickTimes.size(), kX, mbY, 33, 22, client.mouseHandler.isLeftPressed(), transparent, bgColor, textColor);
            drawMouse(extractor, client, "RMB", rightClickTimes.size(), kX + 33, mbY, 33, 22, client.mouseHandler.isRightPressed(), transparent, bgColor, textColor);
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
        if (target == null && client.gui.screen() instanceof ClickGuiScreen) {
            target = client.player;
        }
        if (target == null) return;

        int tX = layout.effectiveX(HudLayout.TARGET_HUD, screenWidth, 0);
        int tY = layout.effectiveY(HudLayout.TARGET_HUD, screenHeight);
        int w = HudLayout.TARGET_HUD_WIDTH;
        int h = HudLayout.TARGET_HUD_HEIGHT;

        int accent = ModernClient.getInstance().getClientSettings().getMenuAccentColor();
        if (!module.isTransparentBackground()) {
            extractor.fill(tX, tY, tX + w, tY + h, translucentBoxColor(module));
            extractor.fill(tX, tY, tX + 2, tY + h, accent);
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
        } else {
            extractor.fill(avatarX, avatarY, avatarX + avatarSize, avatarY + avatarSize, 0xFF1E293B);
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
        extractor.fill(barX, barY, barX + barW, barY + barH, 0x881E293B);
        extractor.fill(barX, barY, barX + Math.round(barW * ratio), barY + barH, hpColor);

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
        int accent = ModernClient.getInstance().getClientSettings().getMenuAccentColor();

        if (active.isEmpty()) {
            if (client.gui.screen() instanceof ClickGuiScreen) {
                if (!transparent) {
                    extractor.fill(pX, pY, pX + HudLayout.POTIONS_WIDTH, pY + 22, bgColor);
                }
                hudText(extractor, client, "Speed II", pX + 6, pY + 3, module.getTextColor());
                hudText(extractor, client, "01:30 (Preview)", pX + 6, pY + 12, 0xFF94A3B8);
            }
            return;
        }

        int rowY = pY;
        for (MobEffectInstance effect : active) {
            if (!transparent) {
                extractor.fill(pX, rowY, pX + HudLayout.POTIONS_WIDTH, rowY + 22, bgColor);
            }
            Identifier sprite = Hud.getMobEffectSprite(effect.getEffect());
            extractor.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, pX + 3, rowY + 2, 18, 18);

            String effectName = effect.getEffect().value().getDisplayName().getString();
            int amp = effect.getAmplifier();
            if (amp > 0 && amp < 10) {
                effectName += " " + romanNumeral(amp + 1);
            }
            hudText(extractor, client, effectName, pX + 24, rowY + 3, module.getTextColor());

            String duration = formatEffectDuration(effect);
            hudText(extractor, client, duration, pX + 24, rowY + 12, accent);
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

    private static void drawMouse(GuiGraphicsExtractor extractor, Minecraft client, String label, int cps, int x, int y, int w, int h, boolean pressed, boolean transparent, int bgColor, int textColor) {
        int labelCol = pressed ? 0xFFFFFFFF : textColor;

        if (!transparent) {
            extractor.fill(x, y, x + w, y + h, pressed ? 0xFF0EA5E9 : bgColor);
        }
        hudText(extractor, client, label, x + (w - client.font.width(label)) / 2, y + 3, labelCol);
        String cpsText = cps + " CPS";
        hudText(extractor, client, cpsText, x + (w - client.font.width(cpsText)) / 2, y + 12, labelCol);
    }

    private static void drawKey(GuiGraphicsExtractor extractor, Minecraft client, String label, int x, int y, int w, int h, boolean pressed, boolean transparent, int bgColor, int textColor) {
        int textCol = pressed ? 0xFFFFFFFF : textColor;

        if (!transparent) {
            extractor.fill(x, y, x + w, y + h, pressed ? 0xFF0EA5E9 : bgColor);
        }
        hudText(extractor, client, label, x + (w - client.font.width(label)) / 2, y + (h - 8) / 2, textCol);
    }
}