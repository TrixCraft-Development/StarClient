package net.starclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.starclient.module.OldAnimationsState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {

    @Unique
    private static final ThreadLocal<Boolean> starclient$redArmor = ThreadLocal.withInitial(() -> false);

    @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At("HEAD"))
    private <S> void onRenderLayersHead(EquipmentClientInfo.LayerType layerType,
                                       ResourceKey<EquipmentAsset> assetKey,
                                       Model<? super S> model,
                                       S state,
                                       ItemStack stack,
                                       PoseStack poseStack,
                                       SubmitNodeCollector submitNodeCollector,
                                       int light,
                                       Identifier texture,
                                       int color,
                                       int order,
                                       CallbackInfo ci) {
        if (OldAnimationsState.isRedArmorFlash() && state instanceof LivingEntityRenderState living && living.hasRedOverlay) {
            starclient$redArmor.set(true);
        } else {
            starclient$redArmor.set(false);
        }
    }

    @Inject(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At("RETURN"))
    private <S> void onRenderLayersReturn(EquipmentClientInfo.LayerType layerType,
                                         ResourceKey<EquipmentAsset> assetKey,
                                         Model<? super S> model,
                                         S state,
                                         ItemStack stack,
                                         PoseStack poseStack,
                                         SubmitNodeCollector submitNodeCollector,
                                         int light,
                                         Identifier texture,
                                         int color,
                                         int order,
                                         CallbackInfo ci) {
        starclient$redArmor.set(false);
    }

    @ModifyArg(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"),
               index = 5)
    private int modifyArmorOverlay(int overlay) {
        if (starclient$redArmor.get()) {
            return OverlayTexture.pack(0.0F, true);
        }
        return overlay;
    }
}
