package net.starclient.mixin;

import net.minecraft.core.IdMap;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.starclient.ClientSettings;
import net.starclient.StarClient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(GlobalPalette.class)
public abstract class GlobalPaletteMixin<T> {

    @Shadow
    @Final
    private IdMap<T> registry;

    @Unique
    private static final Set<Integer> STARCLIENT$LOGGED_BAD_IDS = ConcurrentHashMap.newKeySet();

    @Inject(method = "valueFor", at = @At("HEAD"), cancellable = true)
    private void safeguardMissingPaletteEntry(int id, CallbackInfoReturnable<T> cir) {
        ClientSettings settings = StarClient.getInstance() != null ? StarClient.getInstance().getClientSettings() : null;
        if (settings != null && !settings.isPaletteCrashSafeguard()) {
            return;
        }

        T val = this.registry.byId(id);
        if (val == null) {
            if (STARCLIENT$LOGGED_BAD_IDS.add(id)) {
                StarClient.LOGGER.warn("Caught invalid chunk palette entry index {} (missing in registry). Defaulted to fallback palette entry (air) to prevent crash.", id);
            }
            T fallback = this.registry.byId(0);
            cir.setReturnValue(fallback);
        }
    }
}
