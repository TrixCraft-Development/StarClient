package net.starclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.starclient.account.AccountManager;
import net.starclient.config.ClientProfile;
import net.starclient.config.ConfigManager;
import net.starclient.config.ProfileManager;
import net.starclient.cosmetics.CosmeticManager;
import net.starclient.cosmetics.CosmeticTrailManager;
import net.starclient.cosmetics.PresetCosmetics;
import net.starclient.gui.ClickGuiScreen;
import net.starclient.gui.HudEditorScreen;
import net.starclient.gui.HudLayout;
import net.starclient.gui.HudOverlay;
import net.starclient.mod.FabricModManager;
import net.starclient.module.ModuleManager;
import net.starclient.rank.RankManager;
import net.starclient.stats.ClientStatsManager;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StarClient implements ClientModInitializer {
    public static final String MOD_ID = "starclient";
    public static final String CLIENT_NAME = "Star Client";
    public static final String CONFIG_FOLDER = "config/starclient";
    public static final String MOD_VERSION = getModVersion();
    public static final String CLIENT_VERSION = "v" + MOD_VERSION;
    public static final String MC_VERSION = Minecraft.getInstance().getLaunchedVersion();
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static StarClient instance;
    private RankManager rankManager;
    private AccountManager accountManager;
    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private ProfileManager profileManager;
    private CosmeticManager cosmeticManager;
    private FabricModManager modManager;
    private HudLayout hudLayout;
    private ClientSettings clientSettings;
    private KeyMapping clickGuiKeyBind;
    private boolean configLoaded;
    private boolean clickGuiKeyHeld;

    @Override
    public void onInitializeClient() {
        instance = this;
        LOGGER.info("Initializing Star Client {} for Minecraft 26.2...", CLIENT_VERSION);

        // Initialize Managers
        this.rankManager = new RankManager();
        this.accountManager = new AccountManager();
        this.moduleManager = new ModuleManager();
        this.profileManager = new ProfileManager();
        this.configManager = new ConfigManager();
        this.cosmeticManager = new CosmeticManager();
        net.starclient.cosmetics.PresetCosmetics.exportDefaultTexturesIfMissing();
        this.modManager = new FabricModManager();
        this.hudLayout = new HudLayout();
        this.clientSettings = new ClientSettings();
        ClientStatsManager.getInstance().init();

        // Eagerly preload GUI and Stats classes into memory
        try {
            Class.forName(net.starclient.gui.StatsViewRenderer.class.getName());
            Class.forName(net.starclient.gui.StatsViewRenderer.StatSection.class.getName());
            Class.forName(net.starclient.gui.StatsViewRenderer.StatCard.class.getName());
            Class.forName(net.starclient.gui.StatsViewRenderer.ModalStat.class.getName());
            Class.forName(net.starclient.gui.StatsViewRenderer.StatsTab.class.getName());
            Class.forName(net.starclient.gui.ProfilesViewRenderer.class.getName());
            Class.forName(net.starclient.gui.ProfilesViewRenderer.FocusedInput.class.getName());
            Class.forName(ClientProfile.class.getName());
            Class.forName(ProfileManager.class.getName());
            Class.forName(ProfileManager.SavedServerInfo.class.getName());
            Class.forName(net.starclient.gui.CosmeticsScreen.class.getName());
            Class.forName(net.starclient.gui.ClickGuiScreen.class.getName());
        } catch (Throwable t) {
            LOGGER.warn("Failed to eagerly preload GUI classes", t);
        }

        // Register ClickGUI Keybind (Default: RSHIFT)
        this.clickGuiKeyBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.starclient.clickgui",
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
            ensureConfigLoaded();
            ClientStatsManager.getInstance().onTick(client);
            net.starclient.module.AutoRespawnReconnectState.onClientTick(client);
            boolean inGame = client.player != null && client.level != null;
            if (inGame) {
                if (client.gui.screen() == null) {
                    this.moduleManager.handleKeybinds(client);
                }
                this.moduleManager.onTick(client);
                CosmeticTrailManager.tick(client);
            }
            PresetCosmetics.tickAnimations(System.currentTimeMillis() / 50);

            boolean clickGuiKeyDown = clickGuiKeyBind.isDown()
                    || InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
            if (clickGuiKeyDown && !this.clickGuiKeyHeld && !(client.gui.screen() instanceof ClickGuiScreen)) {
                LOGGER.info("Opening ClickGUI from screen: {}", client.gui.screen());
                client.gui.setScreen(new ClickGuiScreen(client.gui.screen()));
            }
            this.clickGuiKeyHeld = clickGuiKeyDown;
        });

        // Register HUD Element
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), (extractor, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && client.level != null && !client.gui.hud.isHidden()
                    && !(client.gui.screen() instanceof ClickGuiScreen)
                    && !(client.gui.screen() instanceof HudEditorScreen)) {
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

    public static StarClient getInstance() {
        return instance;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public AccountManager getAccountManager() {
        return accountManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
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

    public ClientSettings getClientSettings() {
        return clientSettings;
    }

    public void ensureConfigLoaded() {
        if (!this.configLoaded && this.configManager != null) {
            this.configLoaded = true;
            this.configManager.loadConfig();
        }
    }
}
