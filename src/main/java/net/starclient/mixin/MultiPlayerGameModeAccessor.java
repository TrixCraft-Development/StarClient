package net.starclient.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Accessor("destroyDelay")
    void setDestroyDelay(int destroyDelay);

    @Accessor("destroyBlockPos")
    BlockPos getDestroyBlockPos();

    @Accessor("destroyProgress")
    float getDestroyProgress();

    @Accessor("destroyProgress")
    void setDestroyProgress(float destroyProgress);
}