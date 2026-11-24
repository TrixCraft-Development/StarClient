package net.starclient.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.entity.player.Inventory;
import net.starclient.CrashGuardManager;
import net.starclient.StarClient;
import net.starclient.module.SlotLockState;
import net.starclient.module.ZoomState;
import net.starclient.stats.ClientStatsManager;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    private volatile boolean running;

    @Shadow
    private void runTick(boolean advanceGameTime) {
    }

    @Shadow
    public abstract void run();

    @Unique
    private int starclient$runDepth = 0;

    @Unique
    private int starclient$stableTitleFrames = 0;

    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void customWindowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(StarClient.CLIENT_NAME);
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void onRunTick(boolean advanceGameTime, CallbackInfo ci) {
        ClientStatsManager.getInstance().updatePlaytime((Minecraft) (Object) this);
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void starclient$preventZoomKeySmoothCameraConflict(CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        if (self.options == null || self.options.keySmoothCamera == null) return;
        int zoomKey = ZoomState.getZoomKeyBind();
        if (zoomKey != GLFW.GLFW_KEY_UNKNOWN
                && self.options.keySmoothCamera.matches(InputConstants.Type.KEYSYM.getOrCreate(zoomKey))) {
            while (self.options.keySmoothCamera.consumeClick()) {
                // Consume clicks so Zoom's hotkey cannot silently toggle vanilla Cinematic Camera
            }
        }

        if (self.player != null && SlotLockState.isEnabled()) {
            int selectedSlot = self.player.getInventory().getSelectedSlot();
            if (SlotLockState.isBlockDropEnabled() && SlotLockState.isSlotLocked(selectedSlot)
                    && self.options.keyDrop != null) {
                boolean blocked = false;
                while (self.options.keyDrop.consumeClick()) {
                    blocked = true;
                }
                if (blocked) {
                    SlotLockState.notifyBlocked(selectedSlot);
                }
            }
            if (SlotLockState.isBlockOffhandSwapEnabled()
                    && (SlotLockState.isSlotLocked(selectedSlot) || SlotLockState.isSlotLocked(Inventory.SLOT_OFFHAND))
                    && self.options.keySwapOffhand != null) {
                boolean blocked = false;
                while (self.options.keySwapOffhand.consumeClick()) {
                    blocked = true;
                }
                if (blocked) {
                    SlotLockState.notifyBlocked(SlotLockState.isSlotLocked(selectedSlot) ? selectedSlot : Inventory.SLOT_OFFHAND);
                }
            }
        }
    }

    @Inject(method = "run", at = @At("HEAD"))
    private void starclient$onRunEnter(CallbackInfo ci) {
        this.starclient$runDepth++;
    }

    @Inject(method = "run", at = @At("RETURN"))
    private void starclient$onRunReturn(CallbackInfo ci) {
        if (this.running && CrashGuardManager.consumePendingRunReentry() && this.starclient$runDepth < 4) {
            try {
                this.run();
            } finally {
                this.starclient$runDepth = Math.max(0, this.starclient$runDepth - 1);
            }
            return;
        }
        this.starclient$runDepth = Math.max(0, this.starclient$runDepth - 1);
    }

    @Redirect(
            method = "run",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runTick(Z)V")
    )
    private void starclient$guardedRunTick(Minecraft instance, boolean advanceGameTime) {
        try {
            this.runTick(advanceGameTime);
            if (instance.level == null && instance.gui != null && instance.gui.screen() instanceof TitleScreen) {
                if (++this.starclient$stableTitleFrames >= 20) {
                    CrashGuardManager.resetTitleCrashStreak();
                    this.starclient$stableTitleFrames = 20;
                }
            } else {
                this.starclient$stableTitleFrames = 0;
                if (instance.level != null) {
                    CrashGuardManager.resetTitleCrashStreak();
                }
            }
        } catch (Throwable t) {
            this.starclient$stableTitleFrames = 0;
            if (CrashGuardManager.tryHandleCrash(instance, null, t, false)) {
                return;
            }
            throw CrashGuardManager.sneakyThrow(t);
        }
    }

    @Inject(method = "emergencySaveAndCrash(Lnet/minecraft/CrashReport;)V", at = @At("HEAD"), cancellable = true)
    private void starclient$onEmergencySaveAndCrash(CrashReport report, CallbackInfo ci) {
        if (!CrashGuardManager.isEnabled()) {
            return;
        }
        Minecraft self = (Minecraft) (Object) this;
        if (!self.isSameThread()) {
            CrashGuardManager.saveCrashReportOnly(self, report);
            try {
                BlockableEventLoopAccessor.starclient$setDelayedCrash(null);
            } catch (Throwable ignored) {
            }
            self.execute(() -> CrashGuardManager.tryHandleCrash(
                    self, report, report != null ? report.getException() : null, false));
            ci.cancel();
            return;
        }
        boolean fromOuterRun = this.starclient$runDepth > 0;
        if (CrashGuardManager.tryHandleCrash(
                self, report, report != null ? report.getException() : null, fromOuterRun)) {
            ci.cancel();
        }
    }
}
