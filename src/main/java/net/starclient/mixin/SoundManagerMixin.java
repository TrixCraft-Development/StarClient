package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.starclient.module.OldAnimationsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {

    @Shadow
    public abstract SoundEngine.PlayResult play(SoundInstance sound);

    @Unique
    private static boolean starclient$playingDing = false;

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void onPlaySound(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (sound == null || sound.getIdentifier() == null) return;
        Identifier id = sound.getIdentifier();
        String path = id.getPath();

        // 1. Disable sweep sounds
        if (OldAnimationsState.isDisableSweep()) {
            if (path.contains("attack.sweep") || path.contains("player.attack.sweep")) {
                cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
                return;
            }
        }

        // 2. Bow Ding
        if (OldAnimationsState.isBowDing() && !starclient$playingDing) {
            if (path.contains("arrow.hit_player") || path.equals("entity.arrow.hit_player")) {
                cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
                try {
                    starclient$playingDing = true;
                    // Play classic 1.8 ding (XP orb pickup chime at pitch 0.5F)
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        this.play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F));
                    }
                } finally {
                    starclient$playingDing = false;
                }
                return;
            }
        }

        // 3. Old Hit Sounds (silence modern 1.9+ weapon attack swooshes)
        if (OldAnimationsState.isOldHitSounds()) {
            if (path.startsWith("entity.player.attack.")) {
                cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
                return;
            }
        }
    }
}
