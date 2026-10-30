package net.starclient.cosmetics;

import net.minecraft.resources.Identifier;
import net.starclient.StarClient;
import net.starclient.rank.Rank;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CosmeticManager {
    private final Map<String, CosmeticItem> presetItems = new LinkedHashMap<>();

    public static class AccountCosmetics {
        public String equippedCape;
        public String equippedWings;
        public String equippedHeadwear;
        public String equippedTrail;
        public String trailMode = "Always";
        public float capeWaveSpeed = 1.0f;
        public float capeWaveAmplitude = 1.0f;
        public boolean capeWindSway = true;

        public AccountCosmetics() {}

        public AccountCosmetics(AccountCosmetics other) {
            if (other != null) {
                this.equippedCape = other.equippedCape;
                this.equippedWings = other.equippedWings;
                this.equippedHeadwear = other.equippedHeadwear;
                this.equippedTrail = other.equippedTrail;
                this.trailMode = other.trailMode;
                this.capeWaveSpeed = other.capeWaveSpeed;
                this.capeWaveAmplitude = other.capeWaveAmplitude;
                this.capeWindSway = other.capeWindSway;
            }
        }
    }

    private final Map<String, AccountCosmetics> accountCosmetics = new java.util.concurrent.ConcurrentHashMap<>();

    public CosmeticManager() {
        initPresets();
    }

    public static CosmeticManager getInstance() {
        StarClient client = StarClient.getInstance();
        return client != null ? client.getCosmeticManager() : null;
    }

    private void initPresets() {
        presetItems.clear();

        // 1. Capes (Official Star Client collection)
        registerPreset(new CosmeticItem("starclient_official", "Celestial Star", CosmeticType.CAPE,
                Rank.USER, false, "Official Star Client celestial star cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("galaxy_nebula", "Galaxy Nebula", CosmeticType.CAPE,
                Rank.USER, false, "Deep space nebula cloud with twinkling stars", "🌌", null, null, true));
        registerPreset(new CosmeticItem("cyber_neon", "Cyber Neon", CosmeticType.CAPE,
                Rank.USER, false, "Futuristic neon cyan & magenta cyber grid", "⚡", null, null, true));
        registerPreset(new CosmeticItem("crimson_flame", "Crimson Flame", CosmeticType.CAPE,
                Rank.USER, false, "Rising fiery embers and magma plumes", "🔥", null, null, true));
        registerPreset(new CosmeticItem("sakura_blossom", "Sakura Blossom", CosmeticType.CAPE,
                Rank.USER, false, "Delicate Japanese cherry blossom petals", "🌸", null, null, true));
        registerPreset(new CosmeticItem("void_abyssal", "Void Abyssal", CosmeticType.CAPE,
                Rank.USER, false, "Mystic Ender void rift with teal energy", "👁", null, null, true));
        registerPreset(new CosmeticItem("emerald_matrix", "Emerald Matrix", CosmeticType.CAPE,
                Rank.USER, false, "Cascading digital green cyber code", "🟩", null, null, true));
        // --- Official Capes Collection (82 capes) ---
        registerPreset(new CosmeticItem("1mill", "1 Million", CosmeticType.CAPE, Rank.USER, false, "Official 1 Million cape", "🎟", null, null, true));
        registerPreset(new CosmeticItem("2011", "Minecon 2011", CosmeticType.CAPE, Rank.USER, false, "Official Minecon 2011 cape", "🎟", null, null, true));
        registerPreset(new CosmeticItem("2012", "Minecon 2012", CosmeticType.CAPE, Rank.USER, false, "Official Minecon 2012 cape", "🎟", null, null, true));
        registerPreset(new CosmeticItem("2013", "Minecon 2013", CosmeticType.CAPE, Rank.USER, false, "Official Minecon 2013 cape", "🎟", null, null, true));
        registerPreset(new CosmeticItem("2015", "Minecon 2015", CosmeticType.CAPE, Rank.USER, false, "Official Minecon 2015 cape", "🎟", null, null, true));
        registerPreset(new CosmeticItem("2016", "Minecon 2016", CosmeticType.CAPE, Rank.USER, false, "Official Minecon 2016 cape", "🎟", null, null, true));
        registerPreset(new CosmeticItem("8bit_blood_lunar", "8-Bit Blood Lunar", CosmeticType.CAPE, Rank.USER, false, "Official 8-Bit Blood Lunar cape", "🌙", null, null, true));
        registerPreset(new CosmeticItem("alpha", "Alpha", CosmeticType.CAPE, Rank.USER, false, "Official Alpha cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("aurora_cape", "Aurora Cape", CosmeticType.CAPE, Rank.USER, false, "Official Aurora Cape cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("bacon", "Bacon Cape", CosmeticType.CAPE, Rank.USER, false, "Official Bacon Cape cape", "🍪", null, null, true));
        registerPreset(new CosmeticItem("black_optfine_cape", "Black OptiFine", CosmeticType.CAPE, Rank.USER, false, "Official Black OptiFine cape", "⚡", null, null, true));
        registerPreset(new CosmeticItem("black_sword", "Black Sword", CosmeticType.CAPE, Rank.USER, false, "Official Black Sword cape", "⚔", null, null, true));
        registerPreset(new CosmeticItem("blue_new_developer", "Blue Developer", CosmeticType.CAPE, Rank.USER, false, "Official Blue Developer cape", "🛠", null, null, true));
        registerPreset(new CosmeticItem("bugtracker", "Bug Tracker", CosmeticType.CAPE, Rank.USER, false, "Official Bug Tracker cape", "🛠", null, null, true));
        registerPreset(new CosmeticItem("byjoes_minecon_cape", "ByJoes Minecon", CosmeticType.CAPE, Rank.USER, false, "Official ByJoes Minecon cape", "🎟", null, null, true));
        registerPreset(new CosmeticItem("calvin_blue_lunar", "Calvin Blue Lunar", CosmeticType.CAPE, Rank.USER, false, "Official Calvin Blue Lunar cape", "🌙", null, null, true));
        registerPreset(new CosmeticItem("cobalt", "Cobalt Cape", CosmeticType.CAPE, Rank.USER, false, "Official Cobalt Cape cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("cookie_cape", "Cookie Cape", CosmeticType.CAPE, Rank.USER, false, "Official Cookie Cape cape", "🍪", null, null, true));
        registerPreset(new CosmeticItem("coppercape", "Copper Cape", CosmeticType.CAPE, Rank.USER, false, "Official Copper Cape cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("cubed_cape_2025", "Cubed 2025", CosmeticType.CAPE, Rank.USER, false, "Official Cubed 2025 cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("cubedcon", "CubedCon", CosmeticType.CAPE, Rank.USER, false, "Official CubedCon cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("dannybstyle", "DannyBstyle", CosmeticType.CAPE, Rank.USER, false, "Official DannyBstyle cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("dark_gold", "Dark Gold", CosmeticType.CAPE, Rank.USER, false, "Official Dark Gold cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("dark_purple", "Dark Purple", CosmeticType.CAPE, Rank.USER, false, "Official Dark Purple cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("forest_dusk", "Forest Dusk", CosmeticType.CAPE, Rank.USER, false, "Official Forest Dusk cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("frogcape", "Frog Cape", CosmeticType.CAPE, Rank.USER, false, "Official Frog Cape cape", "🐢", null, null, true));
        registerPreset(new CosmeticItem("galaxy_migrator", "Galaxy Migrator", CosmeticType.CAPE, Rank.USER, false, "Official Galaxy Migrator cape", "🌌", null, null, true));
        registerPreset(new CosmeticItem("galaxy", "Galaxy Cape", CosmeticType.CAPE, Rank.USER, false, "Official Galaxy Cape cape", "🌌", null, null, true));
        registerPreset(new CosmeticItem("gr8_escape", "Great Escape", CosmeticType.CAPE, Rank.USER, false, "Official Great Escape cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("halloween", "Halloween", CosmeticType.CAPE, Rank.USER, false, "Official Halloween cape", "🎉", null, null, true));
        registerPreset(new CosmeticItem("hero", "Hero Cape", CosmeticType.CAPE, Rank.USER, false, "Official Hero Cape cape", "⚔", null, null, true));
        registerPreset(new CosmeticItem("hypixel", "Hypixel Cape", CosmeticType.CAPE, Rank.USER, false, "Official Hypixel Cape cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("jckt_optifine_cape", "Jckt OptiFine", CosmeticType.CAPE, Rank.USER, false, "Official Jckt OptiFine cape", "⚡", null, null, true));
        registerPreset(new CosmeticItem("jiingys_custom_of", "Jiingy Custom OF", CosmeticType.CAPE, Rank.USER, false, "Official Jiingy Custom OF cape", "⚡", null, null, true));
        registerPreset(new CosmeticItem("julianclark", "Julian Clark", CosmeticType.CAPE, Rank.USER, false, "Official Julian Clark cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("lazuli_developer", "Lazuli Developer", CosmeticType.CAPE, Rank.USER, false, "Official Lazuli Developer cape", "🛠", null, null, true));
        registerPreset(new CosmeticItem("mcd_sinister_cape", "MCD Sinister", CosmeticType.CAPE, Rank.USER, false, "Official MCD Sinister cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("migratorplus_cape", "Migrator Plus", CosmeticType.CAPE, Rank.USER, false, "Official Migrator Plus cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("minecraftexperiencevillagerrescue", "Villager Rescue", CosmeticType.CAPE, Rank.USER, false, "Official Villager Rescue cape", "⚔", null, null, true));
        registerPreset(new CosmeticItem("minecraftmarketplace", "Marketplace Cape", CosmeticType.CAPE, Rank.USER, false, "Official Marketplace Cape cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("mojangstudios", "Mojang Studios", CosmeticType.CAPE, Rank.USER, false, "Official Mojang Studios cape", "🛠", null, null, true));
        registerPreset(new CosmeticItem("moonlight_trail", "Moonlight Trail", CosmeticType.CAPE, Rank.USER, false, "Official Moonlight Trail cape", "🌌", null, null, true));
        registerPreset(new CosmeticItem("mrmessiah", "MrMessiah", CosmeticType.CAPE, Rank.USER, false, "Official MrMessiah cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("newmojang", "New Mojang", CosmeticType.CAPE, Rank.USER, false, "Official New Mojang cape", "🛠", null, null, true));
        registerPreset(new CosmeticItem("newyears", "New Years", CosmeticType.CAPE, Rank.USER, false, "Official New Years cape", "🎉", null, null, true));
        registerPreset(new CosmeticItem("notnico_sunset_cape", "NotNico Sunset", CosmeticType.CAPE, Rank.USER, false, "Official NotNico Sunset cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("oldmojang", "Old Mojang", CosmeticType.CAPE, Rank.USER, false, "Official Old Mojang cape", "🛠", null, null, true));
        registerPreset(new CosmeticItem("omega_cape_recolor", "Omega Recolor", CosmeticType.CAPE, Rank.USER, false, "Official Omega Recolor cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("omega", "Omega Cape", CosmeticType.CAPE, Rank.USER, false, "Official Omega Cape cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("oxeye", "Oxeye Daisy", CosmeticType.CAPE, Rank.USER, false, "Official Oxeye Daisy cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("pastel_aesthetic", "Pastel Aesthetic", CosmeticType.CAPE, Rank.USER, false, "Official Pastel Aesthetic cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("pi", "Pi Cape", CosmeticType.CAPE, Rank.USER, false, "Official Pi Cape cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("pride_cape", "Pride Cape", CosmeticType.CAPE, Rank.USER, false, "Official Pride Cape cape", "🌈", null, null, true));
        registerPreset(new CosmeticItem("prismarine", "Prismarine Cape", CosmeticType.CAPE, Rank.USER, false, "Official Prismarine Cape cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("progresspridecape", "Progress Pride", CosmeticType.CAPE, Rank.USER, false, "Official Progress Pride cape", "🌈", null, null, true));
        registerPreset(new CosmeticItem("pumpkin_cape", "Pumpkin Cape", CosmeticType.CAPE, Rank.USER, false, "Official Pumpkin Cape cape", "🍪", null, null, true));
        registerPreset(new CosmeticItem("purple_migration_v2", "Purple Migration V2", CosmeticType.CAPE, Rank.USER, false, "Official Purple Migration V2 cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("rainbow_creeper", "Rainbow Creeper", CosmeticType.CAPE, Rank.USER, false, "Official Rainbow Creeper cape", "🌈", null, null, true));
        registerPreset(new CosmeticItem("realms", "Realms Cape", CosmeticType.CAPE, Rank.USER, false, "Official Realms Cape cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("red_omega", "Red Omega", CosmeticType.CAPE, Rank.USER, false, "Official Red Omega cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("scrolls", "Scrolls Cape", CosmeticType.CAPE, Rank.USER, false, "Official Scrolls Cape cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("snailcape", "Snail Cape", CosmeticType.CAPE, Rank.USER, false, "Official Snail Cape cape", "🐢", null, null, true));
        registerPreset(new CosmeticItem("sniffercape", "Sniffer Cape", CosmeticType.CAPE, Rank.USER, false, "Official Sniffer Cape cape", "🐢", null, null, true));
        registerPreset(new CosmeticItem("speedsilverpurplunar", "SpeedSilver Purple", CosmeticType.CAPE, Rank.USER, false, "Official SpeedSilver Purple cape", "🌙", null, null, true));
        registerPreset(new CosmeticItem("standard_optfine_cape", "Standard OptiFine", CosmeticType.CAPE, Rank.USER, false, "Official Standard OptiFine cape", "⚡", null, null, true));
        registerPreset(new CosmeticItem("starry_sunset", "Starry Sunset", CosmeticType.CAPE, Rank.USER, false, "Official Starry Sunset cape", "🌌", null, null, true));
        registerPreset(new CosmeticItem("sword_cape", "Sword Cape", CosmeticType.CAPE, Rank.USER, false, "Official Sword Cape cape", "⚔", null, null, true));
        registerPreset(new CosmeticItem("translator_chinese", "Chinese Translator", CosmeticType.CAPE, Rank.USER, false, "Official Chinese Translator cape", "🛠", null, null, true));
        registerPreset(new CosmeticItem("translator_crowdin", "Crowdin Translator", CosmeticType.CAPE, Rank.USER, false, "Official Crowdin Translator cape", "🛠", null, null, true));
        registerPreset(new CosmeticItem("translator_japan", "Japanese Translator", CosmeticType.CAPE, Rank.USER, false, "Official Japanese Translator cape", "🛠", null, null, true));
        registerPreset(new CosmeticItem("turk_bayragi", "Turk Bayragi", CosmeticType.CAPE, Rank.USER, false, "Official Turk Bayragi cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("turtle", "Turtle Cape", CosmeticType.CAPE, Rank.USER, false, "Official Turtle Cape cape", "🐢", null, null, true));
        registerPreset(new CosmeticItem("twisted", "Twisted Cape", CosmeticType.CAPE, Rank.USER, false, "Official Twisted Cape cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("unused2011_1", "Unused Minecon 2011 #1", CosmeticType.CAPE, Rank.USER, false, "Official Unused Minecon 2011 #1 cape", "🎟", null, null, true));
        registerPreset(new CosmeticItem("unused2011_2", "Unused Minecon 2011 #2", CosmeticType.CAPE, Rank.USER, false, "Official Unused Minecon 2011 #2 cape", "🎟", null, null, true));
        registerPreset(new CosmeticItem("unused2011_3", "Unused Minecon 2011 #3", CosmeticType.CAPE, Rank.USER, false, "Official Unused Minecon 2011 #3 cape", "🎟", null, null, true));
        registerPreset(new CosmeticItem("valentinecape", "Valentine Cape", CosmeticType.CAPE, Rank.USER, false, "Official Valentine Cape cape", "🎉", null, null, true));
        registerPreset(new CosmeticItem("veteran_cape_grass", "Veteran Grass", CosmeticType.CAPE, Rank.USER, false, "Official Veteran Grass cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("xboxbirthday", "Xbox Birthday", CosmeticType.CAPE, Rank.USER, false, "Official Xbox Birthday cape", "🎉", null, null, true));
        registerPreset(new CosmeticItem("xmas", "Christmas Cape", CosmeticType.CAPE, Rank.USER, false, "Official Christmas Cape cape", "🎉", null, null, true));
        registerPreset(new CosmeticItem("xnestorio1", "xNestorio Cape", CosmeticType.CAPE, Rank.USER, false, "Official xNestorio Cape cape", "✦", null, null, true));
        registerPreset(new CosmeticItem("zombie_horse", "Zombie Horse", CosmeticType.CAPE, Rank.USER, false, "Official Zombie Horse cape", "🐢", null, null, true));


        // Animated Capes (Restricted to Rank.PREMIUM or higher)
        registerPreset(new CosmeticItem("animated_chroma", "Animated Chroma", CosmeticType.CAPE,
                Rank.PREMIUM, true, "Flowing smooth RGB rainbow spectrum", "🌈", null, null, true));
        registerPreset(new CosmeticItem("animated_cosmic", "Animated Cosmic", CosmeticType.CAPE,
                Rank.PREMIUM, true, "Pulsing star shimmer and celestial core", "✨", null, null, true));

        // 2. Wings
        registerPreset(new CosmeticItem("dragon_void", "Dragon Void", CosmeticType.WINGS,
                Rank.USER, false, "Massive dark obsidian wings with violet energy", "🐉", null, null, true));
        registerPreset(new CosmeticItem("angel_light", "Archangel Light", CosmeticType.WINGS,
                Rank.USER, false, "Radiant feathery white wings with celestial glow", "🪽", null, null, true));
        registerPreset(new CosmeticItem("demon_flame", "Demonic Flame", CosmeticType.WINGS,
                Rank.USER, false, "Charcoal bat wings with crackling molten lava", "🦇", null, null, true));
        registerPreset(new CosmeticItem("fairy_prismatic", "Prismatic Fairy", CosmeticType.WINGS,
                Rank.USER, false, "Delicate iridescent wings with shifting pastel hue", "🧚", null, null, true));

        // 3. Headwear
        registerPreset(new CosmeticItem("angel_halo", "Golden Halo", CosmeticType.HEADWEAR,
                Rank.USER, false, "Floating radiant divine halo ring above head", "👑", null, null, true));
        registerPreset(new CosmeticItem("demon_horns", "Infernal Horns", CosmeticType.HEADWEAR,
                Rank.USER, false, "Curved obsidian & crimson demonic horns", "😈", null, null, true));
        registerPreset(new CosmeticItem("ninja_bandana", "Shinobi Bandana", CosmeticType.HEADWEAR,
                Rank.USER, false, "Sleek midnight headband with fluttering sash tails", "🥷", null, null, true));
        registerPreset(new CosmeticItem("cat_ears", "Neko Ears", CosmeticType.HEADWEAR,
                Rank.USER, false, "Cute stylish cat ears perched on top of head", "🐱", null, null, true));

        // 4. Trails
        registerPreset(new CosmeticItem("stardust", "Stardust Sparkles", CosmeticType.TRAIL,
                Rank.USER, false, "Drifting celestial end rod sparkles", "⭐", null, null, true));
        registerPreset(new CosmeticItem("flame", "Blazing Flame", CosmeticType.TRAIL,
                Rank.USER, false, "Fiery sparks and embers left in your wake", "🔥", null, null, true));
        registerPreset(new CosmeticItem("hearts", "Love Aura", CosmeticType.TRAIL,
                Rank.USER, false, "Floating passionate hearts fluttering around you", "💖", null, null, true));
        registerPreset(new CosmeticItem("portal", "Void Rift", CosmeticType.TRAIL,
                Rank.USER, false, "Mystic purple portal rift particles swirling behind", "🌀", null, null, true));
        registerPreset(new CosmeticItem("chroma", "Runic Chroma", CosmeticType.TRAIL,
                Rank.USER, false, "Enchanted galactic magic runes emitting softly", "🔮", null, null, true));
        registerPreset(new CosmeticItem("snow", "Winter Snowflake", CosmeticType.TRAIL,
                Rank.USER, false, "Crisp arctic snowflakes gently drifting down", "❄", null, null, true));
    }

    private void registerPreset(CosmeticItem item) {
        presetItems.put(item.id(), item);
    }

    public CosmeticItem getCosmeticById(String id) {
        if (id == null) return null;
        return presetItems.get(id);
    }

    public List<CosmeticItem> getPresetsByType(CosmeticType type) {
        List<CosmeticItem> list = new ArrayList<>();
        for (CosmeticItem item : presetItems.values()) {
            if (item.type() == type) {
                list.add(item);
            }
        }
        return list;
    }

    public boolean canEquip(CosmeticItem item) {
        if (item == null) return false;
        Rank selfRank = StarClient.getInstance().getRankManager().getSelfRank();
        if (item.requiresPremium()) {
            return selfRank != null && selfRank.hasAtLeast(Rank.PREMIUM);
        }
        return true;
    }

    public boolean hasWavePhysicsPermission() {
        Rank selfRank = StarClient.getInstance().getRankManager().getSelfRank();
        return selfRank != null && selfRank.hasAtLeast(Rank.PREMIUM);
    }

    public static String getActiveAccountKey() {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (client != null && client.getUser() != null) {
            java.util.UUID uuid = client.getUser().getProfileId();
            if (uuid != null) {
                return uuid.toString();
            }
            String name = client.getUser().getName();
            if (name != null && !name.isBlank()) {
                return name.toLowerCase(java.util.Locale.ROOT);
            }
        }
        return "default";
    }

    public synchronized AccountCosmetics getActiveCosmetics() {
        String key = getActiveAccountKey();
        return accountCosmetics.computeIfAbsent(key, k -> new AccountCosmetics());
    }

    public synchronized AccountCosmetics getCosmeticsForAccount(String accountKey) {
        if (accountKey == null || accountKey.isBlank()) return getActiveCosmetics();
        return accountCosmetics.computeIfAbsent(accountKey, k -> new AccountCosmetics());
    }

    public synchronized Map<String, AccountCosmetics> getAllAccountCosmetics() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(accountCosmetics));
    }

    public synchronized void setAccountCosmetics(String key, AccountCosmetics cosmetics) {
        if (key != null && cosmetics != null) {
            accountCosmetics.put(key, cosmetics);
        }
    }

    public synchronized CapeCosmetic getEquippedCapeCosmetic() {
        String capeId = getEquippedCape();
        if (capeId == null || capeId.isEmpty() || "none".equals(capeId)) return null;
        CosmeticItem preset = getCosmeticById(capeId);
        if (preset != null && preset.type() == CosmeticType.CAPE) {
            Identifier tex = PresetCosmetics.getOrRegisterCape(preset.id());
            Identifier asset = Identifier.fromNamespaceAndPath(StarClient.MOD_ID, "capes/preset_" + preset.id());
            return new CapeCosmetic(preset.displayName(), preset.id(), asset, tex);
        }
        return null;
    }

    // --- GETTERS & SETTERS (PER ACTIVE ACCOUNT) ---

    public String getEquippedCape() {
        String cape = getActiveCosmetics().equippedCape;
        return cape == null || cape.isEmpty() ? null : cape;
    }

    public synchronized void setEquippedCape(String capeId) {
        getActiveCosmetics().equippedCape = capeId == null || capeId.isEmpty() || "none".equals(capeId) ? null : capeId;
    }

    public String getEquippedWings() {
        String wings = getActiveCosmetics().equippedWings;
        return wings == null || wings.isEmpty() ? null : wings;
    }

    public synchronized void setEquippedWings(String wingsId) {
        getActiveCosmetics().equippedWings = wingsId == null || wingsId.isEmpty() || "none".equals(wingsId) ? null : wingsId;
    }

    public String getEquippedHeadwear() {
        String hw = getActiveCosmetics().equippedHeadwear;
        return hw == null || hw.isEmpty() ? null : hw;
    }

    public synchronized void setEquippedHeadwear(String headwearId) {
        getActiveCosmetics().equippedHeadwear = headwearId == null || headwearId.isEmpty() || "none".equals(headwearId) ? null : headwearId;
    }

    public String getEquippedTrail() {
        String tr = getActiveCosmetics().equippedTrail;
        return tr == null || tr.isEmpty() ? null : tr;
    }

    public synchronized void setEquippedTrail(String trailId) {
        getActiveCosmetics().equippedTrail = trailId == null || trailId.isEmpty() || "none".equals(trailId) ? null : trailId;
    }

    public String getTrailMode() {
        String mode = getActiveCosmetics().trailMode;
        return mode != null ? mode : "Always";
    }

    public synchronized void setTrailMode(String mode) {
        getActiveCosmetics().trailMode = (mode != null && !mode.isEmpty()) ? mode : "Always";
    }

    public float getCapeWaveSpeed() {
        return getActiveCosmetics().capeWaveSpeed;
    }

    public synchronized void setCapeWaveSpeed(float speed) {
        getActiveCosmetics().capeWaveSpeed = Math.clamp(speed, 0.5f, 2.5f);
    }

    public float getCapeWaveAmplitude() {
        return getActiveCosmetics().capeWaveAmplitude;
    }

    public synchronized void setCapeWaveAmplitude(float amplitude) {
        getActiveCosmetics().capeWaveAmplitude = Math.clamp(amplitude, 0.5f, 2.5f);
    }

    public boolean isCapeWindSway() {
        return getActiveCosmetics().capeWindSway;
    }

    public synchronized void setCapeWindSway(boolean capeWindSway) {
        getActiveCosmetics().capeWindSway = capeWindSway;
    }
}
