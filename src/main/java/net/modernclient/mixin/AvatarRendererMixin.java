package net.modernclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import net.modernclient.ModernClient;
import net.modernclient.cosmetics.CapeCosmetic;
import net.modernclient.cosmetics.CosmeticManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
    private void showOwnNametag(Avatar entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && entity == client.player) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL")
    )
    private void applyCosmeticCape(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
        ModernClient mod = ModernClient.getInstance();
        if (mod == null || state.skin == null) return;
        if (entity != Minecraft.getInstance().player) return;

        CosmeticManager cosmetics = mod.getCosmeticManager();
        CapeCosmetic cape = cosmetics == null ? null : cosmetics.getEquippedCapeCosmetic();
        CapeCosmetic skinOverride = cosmetics == null ? null : cosmetics.getEquippedSkinCosmetic();
        if (cape == null && skinOverride == null) return;

        PlayerSkin skin = state.skin;
        state.skin = new PlayerSkin(
                skinOverride == null
                        ? skin.body()
                        : new ClientAsset.ResourceTexture(skinOverride.assetId(), skinOverride.textureId()),
                cape == null
                        ? skin.cape()
                        : new ClientAsset.ResourceTexture(cape.assetId(), cape.textureId()),
                skin.elytra(),
                skin.model(),
                skin.secure()
        );
        if (cape != null) {
            state.showCape = true;
        }
    }
}
