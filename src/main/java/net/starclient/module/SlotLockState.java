package net.starclient.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.starclient.StarClient;
import net.starclient.gui.GuiRenderUtils;
import net.starclient.gui.ToastNotificationManager;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.ColorModeSetting;
import net.starclient.setting.Setting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * State, protection hooks, and crisp pixel-art padlock overlay renderer
 * for the "Slot Lock" PLAYER module.
 *
 * Player inventory slot indices (invariant across all container screens):
 * - 0..8:   Hotbar
 * - 9..35:  Main inventory
 * - 36..39: Armor (boots, leggings, chestplate, helmet)
 * - 40:     Offhand
 */
public final class SlotLockState {
    private static final int MIN_SLOT = 0;
    private static final int MAX_SLOT = Inventory.SLOT_OFFHAND; // 40
    private static final long FLASH_DURATION_MS = 450L;
    private static final long BLOCK_SOUND_COOLDOWN_MS = 180L;

    private static Module module;
    private static final Set<Integer> LOCKED_SLOTS = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, Long> BLOCKED_FLASH_UNTIL = new ConcurrentHashMap<>();
    private static long lastBlockedSoundMs = 0L;

    private SlotLockState() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static int getLockKeyBind() {
        if (module == null) return GLFW.GLFW_KEY_L;
        int key = module.getKeyBind();
        return key == GLFW.GLFW_KEY_UNKNOWN ? GLFW.GLFW_KEY_L : key;
    }

    public static boolean isBlockDropEnabled() {
        return isEnabled() && getBool("Block Dropping (Q)", true);
    }

    public static boolean isBlockMoveAndSwapEnabled() {
        return isEnabled() && getBool("Block Moving & Swapping", true);
    }

    public static boolean isBlockOffhandSwapEnabled() {
        return isEnabled() && getBool("Block Offhand Swap (F)", true);
    }

    public static boolean isShowOnHotbar() {
        return isEnabled() && getBool("Show on Hotbar", true);
    }

    public static boolean isPlayerInventorySlot(Slot slot) {
        if (slot == null || !(slot.container instanceof Inventory)) return false;
        int idx = slot.getContainerSlot();
        return idx >= MIN_SLOT && idx <= MAX_SLOT;
    }

    public static boolean isSlotLocked(int invIndex) {
        return isEnabled() && LOCKED_SLOTS.contains(invIndex);
    }

    public static boolean isSlotLocked(Slot slot) {
        return isPlayerInventorySlot(slot) && isSlotLocked(slot.getContainerSlot());
    }

    public static boolean toggleLock(int invIndex) {
        if (invIndex < MIN_SLOT || invIndex > MAX_SLOT) return false;
        boolean nowLocked;
        if (LOCKED_SLOTS.contains(invIndex)) {
            LOCKED_SLOTS.remove(invIndex);
            nowLocked = false;
        } else {
            LOCKED_SLOTS.add(invIndex);
            nowLocked = true;
        }

        if (getBool("Sound Feedback", true)) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.getSoundManager().play(SimpleSoundInstance.forUI(
                        SoundEvents.NOTE_BLOCK_PLING.value(), nowLocked ? 1.45F : 0.95F));
            }
        }

        ToastNotificationManager.pushToast(
                "slot_lock",
                nowLocked ? "Slot Locked" : "Slot Unlocked",
                formatSlotLabel(invIndex),
                nowLocked ? "LOCKED" : "OPEN",
                nowLocked ? resolveLockColor() : 0xFF94A3B8
        );

        StarClient sc = StarClient.getInstance();
        if (sc != null && sc.getConfigManager() != null) {
            sc.getConfigManager().saveConfig();
        }
        return nowLocked;
    }

    public static void clearAllLocks() {
        if (LOCKED_SLOTS.isEmpty()) return;
        LOCKED_SLOTS.clear();
        BLOCKED_FLASH_UNTIL.clear();
        ToastNotificationManager.pushToast("slot_lock", "Slot Lock", "Cleared all locked slots", "CLEARED", 0xFF94A3B8);
        StarClient sc = StarClient.getInstance();
        if (sc != null && sc.getConfigManager() != null) {
            sc.getConfigManager().saveConfig();
        }
    }

    public static void onTick(Module mod) {
        if (mod == null) return;
        Setting<?> clearSetting = mod.getSetting("Clear All Locks");
        if (clearSetting instanceof BooleanSetting bs && bs.getValue()) {
            bs.setValue(false);
            clearAllLocks();
        }
    }

    public static void onHeldHotbarKeyPress(Minecraft client) {
        if (!isEnabled() || client == null || client.player == null) return;
        int selected = client.player.getInventory().getSelectedSlot();
        toggleLock(selected);
    }

    public static void notifyBlocked(int invIndex) {
        long now = System.currentTimeMillis();
        BLOCKED_FLASH_UNTIL.put(invIndex, now + FLASH_DURATION_MS);

        if (getBool("Sound Feedback", true) && (now - lastBlockedSoundMs >= BLOCK_SOUND_COOLDOWN_MS)) {
            lastBlockedSoundMs = now;
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.72F));
            }
        }
    }

    /**
     * Evaluates whether a container click / keypress should be cancelled to protect a locked slot.
     */
    public static boolean shouldBlockContainerClick(Slot slot, int button, ContainerInput input) {
        if (!isEnabled() || input == null) return false;

        boolean slotLocked = isSlotLocked(slot);
        int slotIdx = isPlayerInventorySlot(slot) ? slot.getContainerSlot() : -1;

        switch (input) {
            case THROW -> {
                if (isBlockDropEnabled() && slotLocked) {
                    notifyBlocked(slotIdx);
                    return true;
                }
            }
            case QUICK_MOVE, PICKUP, PICKUP_ALL, QUICK_CRAFT -> {
                if (isBlockMoveAndSwapEnabled() && slotLocked) {
                    notifyBlocked(slotIdx);
                    return true;
                }
            }
            case SWAP -> {
                if (isBlockMoveAndSwapEnabled()) {
                    if (slotLocked) {
                        notifyBlocked(slotIdx);
                        return true;
                    }
                    if (button >= 0 && button <= 8 && isSlotLocked(button)) {
                        notifyBlocked(button);
                        return true;
                    }
                    if (button == Inventory.SLOT_OFFHAND && isSlotLocked(Inventory.SLOT_OFFHAND)) {
                        notifyBlocked(Inventory.SLOT_OFFHAND);
                        return true;
                    }
                }
            }
            default -> {
            }
        }
        return false;
    }

    public static void renderContainerSlotOverlay(GuiGraphicsExtractor graphics, Slot slot) {
        if (!isEnabled() || !isPlayerInventorySlot(slot)) return;
        int invIndex = slot.getContainerSlot();
        if (!LOCKED_SLOTS.contains(invIndex)) return;
        renderLockBadge(graphics, slot.x, slot.y, invIndex, false);
    }

    public static void renderHotbarOverlays(GuiGraphicsExtractor graphics, Player player) {
        if (!isShowOnHotbar() || player == null || LOCKED_SLOTS.isEmpty()) return;

        int midX = graphics.guiWidth() / 2;
        int slotY = graphics.guiHeight() - 19;

        for (int i = 0; i < 9; i++) {
            if (LOCKED_SLOTS.contains(i)) {
                int slotX = midX - 88 + i * 20;
                renderLockBadge(graphics, slotX, slotY, i, true);
            }
        }

        if (LOCKED_SLOTS.contains(Inventory.SLOT_OFFHAND) && !player.getOffhandItem().isEmpty()) {
            HumanoidArm offhandArm = player.getMainArm().getOpposite();
            int offX = (offhandArm == HumanoidArm.LEFT) ? (midX - 91 - 26) : (midX + 91 + 10);
            renderLockBadge(graphics, offX, slotY, Inventory.SLOT_OFFHAND, true);
        }
    }

    private static void renderLockBadge(GuiGraphicsExtractor graphics, int sx, int sy, int invIndex, boolean onHotbar) {
        long now = System.currentTimeMillis();
        Long flashUntil = BLOCKED_FLASH_UNTIL.get(invIndex);
        boolean flashing = flashUntil != null && now < flashUntil;

        int lockColor = flashing ? 0xFFEF4444 : resolveLockColor();
        if (getBool("Show Slot Border", true) || flashing) {
            int borderAlpha = flashing ? 235 : (onHotbar ? 120 : 160);
            int borderCol = GuiRenderUtils.withAlpha(lockColor, borderAlpha);
            if (flashing) {
                GuiRenderUtils.fillRoundedRect(graphics, sx, sy, 16, 16, 2, 0x44EF4444);
            }
            GuiRenderUtils.drawRoundedOutline(graphics, sx, sy, 16, 16, 2, borderCol);
        }

        // Crisp 5x6 procedural pixel padlock in top-left corner (sx + 1, sy + 1)
        int lx = sx + 1;
        int ly = sy + 1;
        // Subtle 1px drop backdrop for contrast on bright items
        graphics.fill(lx, ly + 1, lx + 6, ly + 7, 0x88000000);
        // Silver shackle arch (top 3px + two side posts)
        int shackle = flashing ? 0xFFFCA5A5 : 0xFFE2E8F0;
        graphics.fill(lx + 1, ly, lx + 4, ly + 1, shackle);
        graphics.fill(lx + 1, ly + 1, lx + 2, ly + 2, shackle);
        graphics.fill(lx + 3, ly + 1, lx + 4, ly + 2, shackle);
        // Solid lock body (5x4)
        graphics.fill(lx, ly + 2, lx + 5, ly + 6, lockColor);
        // Keyhole dot (1x2)
        graphics.fill(lx + 2, ly + 3, lx + 3, ly + 5, 0xFF0F172A);
    }

    public static JsonArray toJsonArray() {
        List<Integer> sorted = new ArrayList<>(LOCKED_SLOTS);
        Collections.sort(sorted);
        JsonArray arr = new JsonArray();
        for (int idx : sorted) {
            arr.add(idx);
        }
        return arr;
    }

    public static void fromJsonArray(JsonArray arr) {
        LOCKED_SLOTS.clear();
        BLOCKED_FLASH_UNTIL.clear();
        if (arr == null) return;
        for (JsonElement el : arr) {
            if (el != null && el.isJsonPrimitive()) {
                try {
                    int idx = el.getAsInt();
                    if (idx >= MIN_SLOT && idx <= MAX_SLOT) {
                        LOCKED_SLOTS.add(idx);
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String formatSlotLabel(int invIndex) {
        if (invIndex >= 0 && invIndex <= 8) return "Hotbar Slot " + (invIndex + 1);
        if (invIndex >= 9 && invIndex <= 35) return "Inventory Slot " + (invIndex - 8);
        return switch (invIndex) {
            case 36 -> "Boots Slot";
            case 37 -> "Leggings Slot";
            case 38 -> "Chestplate Slot";
            case 39 -> "Helmet Slot";
            case Inventory.SLOT_OFFHAND -> "Offhand Slot";
            default -> "Slot " + invIndex;
        };
    }

    private static int resolveLockColor() {
        if (getBool("Use Client Color", false)) {
            StarClient sc = StarClient.getInstance();
            if (sc != null && sc.getClientSettings() != null) {
                return sc.getClientSettings().getMenuAccentColor();
            }
        }
        if (module != null && module.getSetting("Lock Color") instanceof ColorModeSetting cms) {
            return cms.getColor();
        }
        return 0xFFFACC15;
    }

    private static boolean getBool(String name, boolean fallback) {
        if (module == null) return fallback;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : fallback;
    }
}
