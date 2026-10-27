package net.modernclient.module;

import net.minecraft.client.Minecraft;

public final class ToggleSneakState {
    private static volatile boolean active;
    private static volatile boolean sneaking;
    private static volatile boolean unsneakInContainers = true;

    private ToggleSneakState() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean active) {
        ToggleSneakState.active = active;
        if (!active) {
            sneaking = false;
        }
    }

    public static boolean isSneaking() {
        return sneaking;
    }

    public static void setSneaking(boolean sneaking) {
        ToggleSneakState.sneaking = sneaking;
    }

    public static boolean isUnsneakInContainers() {
        return unsneakInContainers;
    }

    public static void setUnsneakInContainers(boolean unsneakInContainers) {
        ToggleSneakState.unsneakInContainers = unsneakInContainers;
    }

    public static void reset() {
        active = true;
        sneaking = false;
    }

    public static void releaseSneak(Minecraft client) {
        active = false;
        sneaking = false;
        if (client != null) {
            client.options.keyShift.setDown(false);
        }
    }

    public static void updateOnTick(Minecraft client) {
        if (client == null || client.player == null || client.options == null) return;
        active = true;

        if (client.gui.screen() != null) {
            // Drain any shift presses inside screens (e.g. shift-clicking items)
            while (client.options.keyShift.consumeClick()) {
                // ignore
            }
            if (unsneakInContainers) {
                client.options.keyShift.setDown(false);
            } else {
                client.options.keyShift.setDown(sneaking);
            }
            return;
        }

        boolean toggledThisTick = false;
        while (client.options.keyShift.consumeClick()) {
            toggledThisTick = true;
        }
        if (toggledThisTick) {
            sneaking = !sneaking;
        }
        client.options.keyShift.setDown(sneaking);
    }
}
