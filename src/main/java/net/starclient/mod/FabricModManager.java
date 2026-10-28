package net.starclient.mod;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.starclient.StarClient;

import java.util.ArrayList;
import java.util.List;

public class FabricModManager {

    public record InstalledModInfo(String id, String name, String version, String description) {}

    private final List<InstalledModInfo> installedMods = new ArrayList<>();

    public void scanInstalledMods() {
        installedMods.clear();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            String name = mod.getMetadata().getName();
            String version = mod.getMetadata().getVersion().getFriendlyString();
            String description = mod.getMetadata().getDescription();

            installedMods.add(new InstalledModInfo(id, name, version, description));
        }
        StarClient.LOGGER.info("Fabric Mod Manager detected {} loaded mods.", installedMods.size());
    }

    public List<InstalledModInfo> getInstalledMods() {
        return installedMods;
    }
}
