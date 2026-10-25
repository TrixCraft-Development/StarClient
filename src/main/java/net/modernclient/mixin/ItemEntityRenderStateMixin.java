package net.modernclient.mixin;

import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.modernclient.module.ItemEntityRenderStateDuck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements ItemEntityRenderStateDuck {
    @Unique
    private boolean starclient$onGround;

    @Override
    public boolean starclient$isOnGround() {
        return starclient$onGround;
    }

    @Override
    public void starclient$setOnGround(boolean onGround) {
        this.starclient$onGround = onGround;
    }
}
