package net.modernclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.modernclient.ModernClient;
import net.modernclient.module.ChestEspState;
import net.modernclient.module.Module;
import net.modernclient.setting.Setting;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class HudOverlay {

    public static void render(GuiGraphicsExtractor extractor) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

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
            extractor.fill(fX, fY, fX + HudLayout.FPS_WIDTH, fY + HudLayout.ELEMENT_HEIGHT, 0xCC0F172A);
            extractor.text(client.font, stats, fX + 6, fY + 5, 0xFFF1F5F9);
        }

        // 2. Ping Display (Bottom Left, toggled by "Ping Display" module)
        Module pingMod = ModernClient.getInstance().getModuleManager().getModuleByName("Ping Display");
        if (pingMod != null && pingMod.isEnabled()) {
            if (client.player.connection != null && client.player.connection.getPlayerInfo(client.player.getUUID()) != null) {
                int ping = Objects.requireNonNull(client.player.connection.getPlayerInfo(client.player.getUUID())).getLatency();
                String stats = "Ping: " + ping + "ms";

                int pX = layout.effectiveX(HudLayout.PING, screenWidth, 0);
                int pY = layout.effectiveY(HudLayout.PING, screenHeight);
                extractor.fill(pX, pY, pX + HudLayout.PING_WIDTH, pY + HudLayout.ELEMENT_HEIGHT, 0xCC0F172A);
                extractor.text(client.font, stats, pX + 6, pY + 5, 0xFFF1F5F9);
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
            extractor.fill(cX, cY, cX + HudLayout.COORDS_WIDTH, cY + HudLayout.ELEMENT_HEIGHT, 0xCC0F172A);
            extractor.text(client.font, stats, cX + 6, cY + 5, 0xFFF1F5F9);
        }

        // 4. Active Modules Arraylist (Top Right, toggled by "Active Modules" module)
        Module activeModsMod = ModernClient.getInstance().getModuleManager().getModuleByName("Active Modules");
        if (activeModsMod != null && activeModsMod.isEnabled()) {
            List<Module> activeModules = ModernClient.getInstance().getModuleManager().getModules().stream()
                    .filter(Module::isEnabled)
                    .sorted((a, b) -> Integer.compare(client.font.width(b.getName()), client.font.width(a.getName())))
                    .collect(Collectors.toList());

            int maxModWidth = activeModules.stream().mapToInt(m -> client.font.width(m.getName()) + 12).max().orElse(0);
            boolean arrayAuto = layout.isAuto(HudLayout.ARRAYLIST);
            int arrayLeft = layout.effectiveX(HudLayout.ARRAYLIST, screenWidth, maxModWidth);
            int arrayY = layout.effectiveY(HudLayout.ARRAYLIST, screenHeight);
            for (Module module : activeModules) {
                String name = module.getName();
                int modWidth = client.font.width(name) + 12;
                int modX = arrayAuto ? screenWidth - modWidth - 8 : arrayLeft;

                extractor.fill(modX, arrayY, modX + modWidth, arrayY + 14, 0xCC0F172A);
                extractor.fill(modX + modWidth - 2, arrayY, modX + modWidth, arrayY + 14, 0xFF38BDF8); // Accent bar
                extractor.text(client.font, name, modX + 6, arrayY + 3, 0xFF38BDF8);

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
    }

    private static void renderArmor(GuiGraphicsExtractor extractor, Minecraft client, Module module) {
        Setting<?> orientationSetting = module.getSetting("Orientation");
        boolean vertical = orientationSetting != null && "Vertical".equals(orientationSetting.getValue());

        EquipmentSlot[] order = vertical
                ? new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
                : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

        HudLayout layout = ModernClient.getInstance().getHudLayout();
        int screenWidth = extractor.guiWidth();
        int screenHeight = extractor.guiHeight();
        int aX = layout.effectiveX(HudLayout.ARMOR, screenWidth, 0);
        int aY = layout.effectiveY(HudLayout.ARMOR, screenHeight);

        for (int i = 0; i < order.length; i++) {
            int x = vertical ? aX : aX + i * 18;
            int y = vertical ? aY + i * 36 : aY;

            assert client.player != null;
            ItemStack stack = client.player.getItemBySlot(order[i]);
            extractor.fill(x, y, x + 18, y + 18, 0x660F172A);
            if (stack.isEmpty()) continue;

            extractor.item(stack, x + 1, y + 1);
            extractor.itemDecorations(client.font, stack, x + 1, y + 1);

            if (stack.isDamageableItem()) {
                int max = stack.getMaxDamage();
                int current = Math.max(max - stack.getDamageValue(), 0);
                String durability = current + "/" + max;
                float ratio = max <= 0 ? 1.0F : (float) current / max;
                int color = ratio > 0.5F ? 0xFF34D399 : (ratio > 0.2F ? 0xFFF59E0B : 0xFFEF4444);
                extractor.text(client.font, durability, x + 1 + (16 - client.font.width(durability)) / 2, y + 19, color);
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

        drawKey(extractor, client, "W", kX + 22, kY, 20, 20, w);
        drawKey(extractor, client, "A", kX, kY + 22, 20, 20, a);
        drawKey(extractor, client, "S", kX + 22, kY + 22, 20, 20, s);
        drawKey(extractor, client, "D", kX + 44, kY + 22, 20, 20, d);

        Setting<?> showMouseSetting = module.getSetting("Show Mouse");
        boolean showMouse = showMouseSetting == null || (boolean) showMouseSetting.getValue();
        if (showMouse) {
            updateClickCps();
            int mbY = kY + 44;
            drawMouse(extractor, client, "LMB", leftClickTimes.size(), kX, mbY, 33, 22, client.mouseHandler.isLeftPressed());
            drawMouse(extractor, client, "RMB", rightClickTimes.size(), kX + 33, mbY, 33, 22, client.mouseHandler.isRightPressed());
        }
    }

    private static void drawMouse(GuiGraphicsExtractor extractor, Minecraft client, String label, int cps, int x, int y, int w, int h, boolean pressed) {
        int bg = pressed ? 0xFF0EA5E9 : 0xCC0F172A;
        int labelCol = pressed ? 0xFFFFFFFF : 0xFF94A3B8;

        extractor.fill(x, y, x + w, y + h, bg);
        extractor.text(client.font, label, x + (w - client.font.width(label)) / 2, y + 3, labelCol);
        String cpsText = cps + " CPS";
        extractor.text(client.font, cpsText, x + (w - client.font.width(cpsText)) / 2, y + 12, 0xFF38BDF8);
    }

    private static void drawKey(GuiGraphicsExtractor extractor, Minecraft client, String label, int x, int y, int w, int h, boolean pressed) {
        int bg = pressed ? 0xFF0EA5E9 : 0xCC0F172A;
        int textCol = pressed ? 0xFFFFFFFF : 0xFF94A3B8;

        extractor.fill(x, y, x + w, y + h, bg);
        extractor.text(client.font, label, x + (w - client.font.width(label)) / 2, y + (h - 8) / 2, textCol);
    }
}
