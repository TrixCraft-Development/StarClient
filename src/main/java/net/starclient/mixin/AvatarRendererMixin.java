package net.starclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.PlayerSkin;
import net.starclient.StarClient;
import net.starclient.cosmetics.CapeCosmetic;
import net.starclient.cosmetics.CosmeticManager;
import net.starclient.cosmetics.StarCosmeticLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void starclient$addCosmeticsLayer(EntityRendererProvider.Context context, boolean slimSteve, CallbackInfo ci) {
        ((LivingEntityRendererAccessor) this).callAddLayer(new StarCosmeticLayer((AvatarRenderer<?>) (Object) this));
    }

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
        StarClient mod = StarClient.getInstance();
        if (mod == null || state.skin == null) return;
        if (entity != Minecraft.getInstance().player) return;

        CosmeticManager cosmetics = mod.getCosmeticManager();
        CapeCosmetic cape = cosmetics == null ? null : cosmetics.getEquippedCapeCosmetic();
        if (cape != null) {
            PlayerSkin skin = state.skin;
            state.skin = new PlayerSkin(
                    skin.body(),
                    new ClientAsset.ResourceTexture(cape.assetId(), cape.textureId()),
                    skin.elytra(),
                    skin.model(),
                    skin.secure()
            );
            // Hide vanilla rigid cape so StarCosmeticLayer renders fluid Wavey Cape
            state.showCape = false;
        }

        // Apply Cape Wave Physics (Restricted strictly to Rank.PREMIUM or higher)
        if (cosmetics != null && cape != null && cosmetics.hasWavePhysicsPermission()) {
            float speed = cosmetics.getCapeWaveSpeed();
            float amp = cosmetics.getCapeWaveAmplitude();
            state.capeFlap *= amp;
            state.capeLean *= amp;
            state.capeLean2 *= amp;

            if (cosmetics.isCapeWindSway()) {
                float time = (entity.tickCount + partialTicks) * 0.12f * speed;
                float sway = (float) Math.sin(time) * 4.5f * (amp * 0.6f);
                state.capeFlap += sway;
            }
        }
    }
}
