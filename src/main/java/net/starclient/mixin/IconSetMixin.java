package net.starclient.mixin;

import com.mojang.blaze3d.platform.IconSet;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import net.starclient.StarClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Mixin(IconSet.class)
public abstract class IconSetMixin {

    @Inject(method = "getStandardIcons", at = @At("HEAD"), cancellable = true)
    private void customStandardIcons(PackResources resources, CallbackInfoReturnable<List<IoSupplier<InputStream>>> cir) {
        cir.setReturnValue(List.of(
                getStarIcon("icon_16x16.png"),
                getStarIcon("icon_32x32.png"),
                getStarIcon("icon_48x48.png"),
                getStarIcon("icon_128x128.png"),
                getStarIcon("icon_256x256.png")
        ));
    }

    @Unique
    private static IoSupplier<InputStream> getStarIcon(String fileName) {
        String resourcePath = "assets/" + StarClient.MOD_ID + "/icons/" + fileName;
        return () -> {
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
            throw new FileNotFoundException(resourcePath);
        };
    }
}
