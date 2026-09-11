package net.modernclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.modernclient.config.ConfigManager;
import net.modernclient.cosmetics.CosmeticManager;
import net.modernclient.gui.ClickGuiScreen;
import net.modernclient.gui.HudLayout;
import net.modernclient.gui.HudOverlay;
import net.modernclient.mod.FabricModManager;
import net.modernclient.module.ModuleManager;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModernClient implements ClientModInitializer {
    public static final String MOD_ID = "starclient";
    public static final String CLIENT_NAME = "Star Client";
    public static final String CONFIG_FOLDER = "config/starclient";
    public static final String CLIENT_VERSION = "v" + getModVersion();
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ModernClient instance;
    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private CosmeticManager cosmeticManager;
    private FabricModManager modManager;
    private HudLayout hudLayout;
    private KeyMapping clickGuiKeyBind;
    private boolean configLoaded;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("Initializing Star Client {} for Minecraft 26.2...", CLIENT_VERSION);

        // Initialize Managers
        this.moduleManager = new ModuleManager();
        this.configManager = new ConfigManager();
        this.cosmeticManager = new CosmeticManager();
        this.modManager = new FabricModManager();
        this.hudLayout = new HudLayout();

        // Register ClickGUI Keybind (Default: RSHIFT)
        this.clickGuiKeyBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.modernclient.clickgui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "general"))
        ));

        // Save configuration on game shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> this.configManager.saveConfig()));

        // Scan installed mods in .minecraft/mods
        this.modManager.scanInstalledMods();

        // Register Client Tick Callback (config loaded on first tick when Minecraft is fully ready)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!this.configLoaded) {
                this.configLoaded = true;
                this.configManager.loadConfig();
            }
            if (client.player != null && client.level != null) {
                if (client.gui.screen() == null) {
                    this.moduleManager.handleKeybinds(client);
                }
                this.moduleManager.onTick(client);

                if (clickGuiKeyBind.consumeClick()) {
                    client.gui.setScreen(new ClickGuiScreen());
                }
            }
        });

        // Register HUD Element
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), (extractor, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && client.level != null && !client.gui.hud.isHidden()) {
                HudOverlay.render(extractor);
            }
        });

        LOGGER.info("Star Client initialized successfully!");
    }

    private static String getModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("Stable");
    }

    public static ModernClient getInstance() {
        return instance;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public CosmeticManager getCosmeticManager() {
        return cosmeticManager;
    }

    public FabricModManager getModManager() {
        return modManager;
    }

    public HudLayout getHudLayout() {
        return hudLayout;
    }
}
