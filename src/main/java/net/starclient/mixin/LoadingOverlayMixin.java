package net.starclient.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.starclient.StarClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.IntSupplier;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    @Unique
    private static final Identifier STAR_CLIENT_TITLE_TEXTURE =
            Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "textures/gui/loading_title");
    @Unique
    private static final int TEXTURE_WIDTH = 416;
    @Unique
    private static final int TEXTURE_HEIGHT = 64;
    @Unique
    private static boolean titleTextureRegistered;

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/IntSupplier;getAsInt()I"
            )
    )
    private int customBrandBackground(IntSupplier supplier) {
        StarClient client = StarClient.getInstance();
        if (client != null) {
            client.ensureConfigLoaded();
            if (client.getClientSettings() != null) {
                return client.getClientSettings().getMenuBackgroundColor();
            }
        }
        return 0xFF0F172A;
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"
            )
    )
    private void replaceMojangLogo(
            GuiGraphicsExtractor graphics,
            RenderPipeline renderPipeline,
            Identifier texture,
            int x,
            int y,
            float u,
            float v,
            int width,
            int height,
            int srcWidth,
            int srcHeight,
            int textureWidth,
            int textureHeight,
            int color
    ) {
        if (v != 0.0F) {
            return;
        }
        if (!ensureTitleTextureRegistered()) {
            return;
        }

        int accent = 0xFF38BDF8;
        StarClient client = StarClient.getInstance();
        if (client != null) {
            client.ensureConfigLoaded();
            if (client.getClientSettings() != null) {
                accent = client.getClientSettings().getMenuAccentColor();
            }
        }

        int alpha = (color >>> 24) & 0xFF;
        int tintedColor = (alpha << 24) | (accent & 0x00FFFFFF);

        int drawW = 208;
        int drawH = 32;
        int drawX = (graphics.guiWidth() - drawW) / 2;
        int drawY = (graphics.guiHeight() - drawH) / 2;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                STAR_CLIENT_TITLE_TEXTURE,
                drawX,
                drawY,
                0.0F,
                0.0F,
                drawW,
                drawH,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                tintedColor
        );
    }

    @Unique
    private static boolean ensureTitleTextureRegistered() {
        if (titleTextureRegistered) {
            return true;
        }
        String resourcePath = "assets/" + StarClient.MOD_ID + "/loading_title.png";
        try (InputStream stream = openResourceStream(resourcePath)) {
            if (stream == null) {
                return false;
            }
            NativeImage image = NativeImage.read(stream);
            DynamicTexture texture = new DynamicTexture(() -> StarClient.MOD_ID + "_loading_title", image);
            Minecraft.getInstance().getTextureManager().register(STAR_CLIENT_TITLE_TEXTURE, texture);
            titleTextureRegistered = true;
            return true;
        } catch (Exception e) {
            StarClient.LOGGER.error("Failed to load loading screen title texture", e);
            return false;
        }
    }

    @Unique
    private static InputStream openResourceStream(String resourcePath) throws Exception {
        InputStream stream = StarClient.class.getResourceAsStream("/" + resourcePath);
        if (stream != null) {
            return stream;
        }
        Optional<Path> modPath = FabricLoader.getInstance()
                .getModContainer(StarClient.MOD_ID)
                .flatMap(container -> container.findPath(resourcePath));
        if (modPath.isPresent()) {
            return Files.newInputStream(modPath.get());
        }
        return null;
    }
}
