package net.starclient.module;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.starclient.StarClient;
import net.starclient.gui.StorageFinderScreen;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Indexes and persists the contents of opened storage containers (Chests, Double Chests,
 * Barrels, Shulker Boxes, Hoppers, Dispensers, Droppers — including items nested inside
 * Shulker Boxes!) per world/server and dimension for the "Storage Finder" WORLD module.
 */
public final class StorageFinderManager {

    public record StoredItem(
            String itemId,
            String displayName,
            String searchTokens,
            int count,
            boolean insideShulker
    ) {
    }

    public static final class IndexedContainer {
        public final BlockPos pos;
        public final BlockPos secondPos; // Non-null for Double Chests
        public final String containerItemId;
        public String label;
        public List<StoredItem> items;
        public long updatedMs;

        public IndexedContainer(BlockPos pos, BlockPos secondPos, String containerItemId,
                                String label, List<StoredItem> items, long updatedMs) {
            this.pos = pos.immutable();
            this.secondPos = secondPos != null ? secondPos.immutable() : null;
            this.containerItemId = containerItemId != null ? containerItemId : "minecraft:chest";
            this.label = (label != null && !label.isBlank()) ? label : "Chest";
            this.items = items != null ? items : List.of();
            this.updatedMs = updatedMs;
        }
    }

    public record AggregatedItem(
            String itemId,
            String displayName,
            String subtitle,
            int totalCount,
            int containerCount,
            boolean anyInShulker
    ) {
    }

    public record ContainerMatch(
            IndexedContainer container,
            int matchedCount,
            boolean anyInShulker
    ) {
    }

    private static Module module;

    // contextKey ("worldKey|dimension") -> (packedPosKey -> IndexedContainer)
    private static final Map<String, Map<Long, IndexedContainer>> STORAGE_INDEX = new ConcurrentHashMap<>();
    private static volatile boolean loadedFromDisk = false;
    private static volatile boolean dirty = false;

    // Pending interaction -> active open container binding
    private static BlockPos pendingPos;
    private static BlockPos pendingSecondPos;
    private static String pendingContainerItemId;
    private static String pendingContainerLabel;
    private static long pendingInteractMs;

    private static BlockPos boundPos;
    private static BlockPos boundSecondPos;
    private static String boundContainerItemId;
    private static String boundContainerLabel;
    private static AbstractContainerMenu boundMenu;
    private static long lastCaptureMs;

    // Active 3D in-world highlight state
    private static String activeQuery = "";
    private static String activeItemId = null;     // Optional exact item ID filter
    private static String activeLabel = "";        // Human-readable banner label
    private static BlockPos activeFocusPos = null; // Optional single-container pin focus
    private static long highlightUntilMs = 0L;     // 0 = indefinite until cleared

    private StorageFinderManager() {
    }

    public static void setModule(Module mod) {
        module = mod;
    }

    public static Module getModule() {
        return module;
    }

    public static boolean isEnabled() {
        return module != null && module.isEnabled() && module.canUse();
    }

    public static String getCurrentContextKey(Minecraft client) {
        if (client == null || client.level == null) return null;
        String wk = WaypointsState.getCurrentWorldKey(client);
        String dim = WaypointsState.getCurrentDimension(client);
        if (wk == null) return null;
        return wk + "|" + dim;
    }

    // -------------------------------------------------------------------------
    // 1. Interaction & Container Lifecycle Hooks
    // -------------------------------------------------------------------------

    public static void onUseBlock(Level level, BlockPos pos) {
        if (!isEnabled() || level == null || pos == null) return;
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (!isIndexableContainer(block)) {
            clearPendingInteraction();
            return;
        }

        BlockPos primary = pos.immutable();
        BlockPos secondary = null;
        String label = block.getName().getString();

        if (block instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                Direction connected = ChestBlock.getConnectedDirection(state);
                BlockPos other = pos.relative(connected).immutable();
                if (comparePos(other, primary) < 0) {
                    secondary = primary;
                    primary = other;
                } else {
                    secondary = other;
                }
                label = "Double " + label;
            }
        }

        Item blockItem = block.asItem();
        String itemId = blockItem != Items.AIR
                ? BuiltInRegistries.ITEM.getKey(blockItem).toString()
                : "minecraft:chest";

        pendingPos = primary;
        pendingSecondPos = secondary;
        pendingContainerItemId = itemId;
        pendingContainerLabel = label;
        pendingInteractMs = System.currentTimeMillis();
    }

    public static void onContainerOpened(String screenTitle, AbstractContainerMenu menu) {
        if (!isEnabled() || menu == null || !isSupportedMenu(menu)) {
            boundMenu = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (pendingPos == null || (now - pendingInteractMs) > 2500L) {
            boundMenu = null;
            return;
        }

        boundPos = pendingPos;
        boundSecondPos = pendingSecondPos;
        boundContainerItemId = pendingContainerItemId;
        boundContainerLabel = resolveContainerLabel(screenTitle, pendingContainerLabel);
        boundMenu = menu;
        lastCaptureMs = 0L;
        clearPendingInteraction();

        // Auto-clear highlight if user opened a highlighted container and Auto-Clear is enabled
        if (hasActiveHighlight() && getBool("Auto-Clear When Opened", false)) {
            Minecraft mc = Minecraft.getInstance();
            List<ContainerMatch> matches = getActiveMatches(mc);
            for (ContainerMatch m : matches) {
                if (m.container().pos.equals(boundPos)
                        || (m.container().secondPos != null && m.container().secondPos.equals(boundPos))) {
                    clearActiveHighlight();
                    break;
                }
            }
        }
    }

    public static void onContainerRenderTick(AbstractContainerMenu menu) {
        if (!isEnabled() || menu == null || menu != boundMenu || boundPos == null) return;
        long now = System.currentTimeMillis();
        if (now - lastCaptureMs < 250L) return;
        lastCaptureMs = now;
        captureOpenContainer(menu);
    }

    public static void onContainerClosed(AbstractContainerMenu menu) {
        if (menu != null && menu == boundMenu && boundPos != null) {
            captureOpenContainer(menu);
            saveAsync();
        }
        boundMenu = null;
        boundPos = null;
        boundSecondPos = null;
    }

    public static void onBlockDestroyed(BlockPos pos) {
        if (pos == null) return;
        Minecraft mc = Minecraft.getInstance();
        String ctx = getCurrentContextKey(mc);
        if (ctx == null) return;
        ensureLoaded();
        Map<Long, IndexedContainer> map = STORAGE_INDEX.get(ctx);
        if (map == null || map.isEmpty()) return;

        boolean removed = map.remove(pos.asLong()) != null;
        if (!removed) {
            // Check if the destroyed block was the second half of a double chest
            Long toRemove = null;
            for (Map.Entry<Long, IndexedContainer> entry : map.entrySet()) {
                if (pos.equals(entry.getValue().secondPos)) {
                    toRemove = entry.getKey();
                    break;
                }
            }
            if (toRemove != null) {
                map.remove(toRemove);
                removed = true;
            }
        }
        if (removed) {
            dirty = true;
            saveAsync();
        }
    }

    public static void onClientTick(Minecraft client) {
        if (module == null) return;

        if (module.getSetting("Open Storage Finder") instanceof BooleanSetting openBtn && openBtn.getValue()) {
            openBtn.setValue(false);
            if (client != null && client.level != null && client.player != null) {
                client.gui.setScreen(new StorageFinderScreen(null));
            }
        }
        if (module.getSetting("Clear Active Highlight") instanceof BooleanSetting clearHl && clearHl.getValue()) {
            clearHl.setValue(false);
            clearActiveHighlight();
        }
        if (module.getSetting("Clear Storage Cache") instanceof BooleanSetting clearCache && clearCache.getValue()) {
            clearCache.setValue(false);
            clearCurrentContextCache(client);
        }

        if (hasActiveHighlight() && highlightUntilMs > 0L && System.currentTimeMillis() >= highlightUntilMs) {
            clearActiveHighlight();
        }
    }

    private static void captureOpenContainer(AbstractContainerMenu menu) {
        Minecraft mc = Minecraft.getInstance();
        String ctx = getCurrentContextKey(mc);
        if (ctx == null || boundPos == null) return;
        ensureLoaded();

        int containerSlots = Math.max(0, menu.slots.size() - 36);
        if (containerSlots == 0) return;

        boolean indexShulkers = getBool("Index Nested Shulkers", true);
        Map<String, MutableItemAgg> merged = new LinkedHashMap<>();

        for (int i = 0; i < containerSlots && i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;

            addItemToAgg(merged, stack, false);

            if (indexShulkers && isShulkerStack(stack)) {
                ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
                if (contents != null) {
                    NonNullList<ItemStack> inner = NonNullList.withSize(27, ItemStack.EMPTY);
                    contents.copyInto(inner);
                    for (ItemStack innerStack : inner) {
                        if (!innerStack.isEmpty()) {
                            addItemToAgg(merged, innerStack, true);
                        }
                    }
                }
            }
        }

        List<StoredItem> list = new ArrayList<>(merged.size());
        for (MutableItemAgg agg : merged.values()) {
            list.add(new StoredItem(agg.itemId, agg.displayName, agg.searchTokens, agg.count, agg.insideShulker));
        }

        Map<Long, IndexedContainer> map = STORAGE_INDEX.computeIfAbsent(ctx, k -> new ConcurrentHashMap<>());
        // Remove any stale single-chest entry at secondPos if a chest was just turned into a double chest
        if (boundSecondPos != null) {
            map.remove(boundSecondPos.asLong());
        }
        IndexedContainer indexed = new IndexedContainer(
                boundPos,
                boundSecondPos,
                boundContainerItemId,
                boundContainerLabel,
                list,
                System.currentTimeMillis()
        );
        map.put(boundPos.asLong(), indexed);
        dirty = true;
    }

    private static final class MutableItemAgg {
        final String itemId;
        final String displayName;
        final String searchTokens;
        final boolean insideShulker;
        int count;

        MutableItemAgg(String itemId, String displayName, String searchTokens, boolean insideShulker, int count) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.searchTokens = searchTokens;
            this.insideShulker = insideShulker;
            this.count = count;
        }
    }

    private static void addItemToAgg(Map<String, MutableItemAgg> map, ItemStack stack, boolean insideShulker) {
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String hoverName = stripFormatting(stack.getHoverName().getString());
        String tokens = buildSearchTokens(stack, itemId, hoverName);
        String mergeKey = itemId + "|" + hoverName + "|" + tokens + "|" + insideShulker;

        MutableItemAgg existing = map.get(mergeKey);
        if (existing != null) {
            existing.count += stack.getCount();
        } else {
            map.put(mergeKey, new MutableItemAgg(itemId, hoverName, tokens, insideShulker, stack.getCount()));
        }
    }

    private static String buildSearchTokens(ItemStack stack, String itemId, String hoverName) {
        StringBuilder sb = new StringBuilder();
        sb.append(hoverName.toLowerCase(Locale.ROOT));
        int colon = itemId.indexOf(':');
        String path = colon >= 0 ? itemId.substring(colon + 1) : itemId;
        sb.append(' ').append(path.replace('_', ' '));

        appendEnchantTokens(sb, stack.get(DataComponents.ENCHANTMENTS));
        appendEnchantTokens(sb, stack.get(DataComponents.STORED_ENCHANTMENTS));

        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            for (MobEffectInstance eff : potion.getAllEffects()) {
                sb.append(' ').append(eff.getEffect().value().getDisplayName().getString().toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    private static void appendEnchantTokens(StringBuilder sb, ItemEnchantments enchs) {
        if (enchs == null || enchs.isEmpty()) return;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchs.entrySet()) {
            String desc = entry.getKey().value().description().getString().toLowerCase(Locale.ROOT);
            sb.append(' ').append(desc).append(' ').append(roman(entry.getIntValue()).toLowerCase(Locale.ROOT));
        }
    }

    // -------------------------------------------------------------------------
    // 2. Query & Search API
    // -------------------------------------------------------------------------

    public static List<IndexedContainer> getContainersForCurrentContext(Minecraft client) {
        ensureLoaded();
        String ctx = getCurrentContextKey(client);
        if (ctx == null) return List.of();
        Map<Long, IndexedContainer> map = STORAGE_INDEX.get(ctx);
        if (map == null || map.isEmpty()) return List.of();
        return new ArrayList<>(map.values());
    }

    public static List<AggregatedItem> searchAggregatedItems(Minecraft client, String rawQuery) {
        List<IndexedContainer> containers = getContainersForCurrentContext(client);
        String q = normalizeQuery(rawQuery);

        Map<String, int[]> counts = new LinkedHashMap<>(); // [totalCount, containerCount, anyShulker]
        Map<String, String[]> meta = new LinkedHashMap<>(); // [itemId, displayName, subtitle]

        for (IndexedContainer c : containers) {
            Map<String, Integer> perContainer = new LinkedHashMap<>();
            Map<String, Boolean> perContainerShulker = new LinkedHashMap<>();
            for (StoredItem item : c.items) {
                if (!matchesQuery(item, q)) continue;
                String key = item.itemId() + "|" + item.displayName();
                perContainer.merge(key, item.count(), Integer::sum);
                if (item.insideShulker()) {
                    perContainerShulker.put(key, Boolean.TRUE);
                }
                meta.putIfAbsent(key, new String[]{item.itemId(), item.displayName(), extractExtraHint(item)});
            }
            for (Map.Entry<String, Integer> entry : perContainer.entrySet()) {
                String key = entry.getKey();
                int[] arr = counts.computeIfAbsent(key, k -> new int[3]);
                arr[0] += entry.getValue();
                arr[1] += 1;
                if (Boolean.TRUE.equals(perContainerShulker.get(key))) {
                    arr[2] = 1;
                }
            }
        }

        List<AggregatedItem> results = new ArrayList<>(counts.size());
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            String[] m = meta.get(entry.getKey());
            int[] arr = entry.getValue();
            results.add(new AggregatedItem(m[0], m[1], m[2], arr[0], arr[1], arr[2] == 1));
        }
        results.sort(Comparator.comparingInt(AggregatedItem::totalCount).reversed()
                .thenComparing(AggregatedItem::displayName, String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    public static List<ContainerMatch> findMatchingContainers(Minecraft client, String rawQuery, String exactItemId,
                                                            String exactDisplayName, BlockPos focusPos) {
        List<IndexedContainer> containers = getContainersForCurrentContext(client);
        String q = normalizeQuery(rawQuery);
        List<ContainerMatch> out = new ArrayList<>();

        for (IndexedContainer c : containers) {
            if (focusPos != null && !focusPos.equals(c.pos)) continue;

            // Prune missing/broken containers automatically when chunk is loaded
            if (client != null && client.level != null && client.level.hasChunk(c.pos.getX() >> 4, c.pos.getZ() >> 4)) {
                BlockState st = client.level.getBlockState(c.pos);
                if (!isIndexableContainer(st.getBlock())) {
                    removeContainer(client, c.pos);
                    continue;
                }
            }

            int sum = 0;
            boolean anyShulker = false;
            for (StoredItem item : c.items) {
                boolean match;
                if (exactItemId != null) {
                    match = exactItemId.equals(item.itemId())
                            && (exactDisplayName == null || exactDisplayName.equals(item.displayName()));
                } else {
                    match = matchesQuery(item, q);
                }
                if (match) {
                    sum += item.count();
                    if (item.insideShulker()) anyShulker = true;
                }
            }
            if (sum > 0) {
                out.add(new ContainerMatch(c, sum, anyShulker));
            }
        }

        if (client != null && client.player != null) {
            double px = client.player.getX();
            double py = client.player.getY();
            double pz = client.player.getZ();
            out.sort(Comparator.comparingDouble(m -> m.container().pos.distToCenterSqr(px, py, pz)));
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // 3. Active In-World 3D Highlight Control
    // -------------------------------------------------------------------------

    public static void setActiveSearchHighlight(String query, String exactItemId, String label, BlockPos focusPos) {
        activeQuery = query != null ? query.trim() : "";
        activeItemId = exactItemId;
        activeLabel = (label != null && !label.isBlank()) ? label : activeQuery;
        activeFocusPos = focusPos != null ? focusPos.immutable() : null;

        double durSec = getNum("Highlight Duration (s)", 90.0);
        highlightUntilMs = durSec <= 0.5 ? 0L : (System.currentTimeMillis() + (long) (durSec * 1000.0));
    }

    public static void clearActiveHighlight() {
        activeQuery = "";
        activeItemId = null;
        activeLabel = "";
        activeFocusPos = null;
        highlightUntilMs = 0L;
    }

    public static boolean hasActiveHighlight() {
        return !activeQuery.isEmpty() || activeItemId != null;
    }

    public static String getActiveLabel() {
        return activeLabel;
    }

    public static String getActiveItemId() {
        return activeItemId;
    }

    public static List<ContainerMatch> getActiveMatches(Minecraft client) {
        if (!hasActiveHighlight()) return List.of();
        return findMatchingContainers(client, activeQuery, activeItemId, activeItemId != null ? activeLabel : null, activeFocusPos);
    }

    public static void clearCurrentContextCache(Minecraft client) {
        ensureLoaded();
        String ctx = getCurrentContextKey(client);
        if (ctx != null) {
            STORAGE_INDEX.remove(ctx);
            dirty = true;
            saveAsync();
        }
        clearActiveHighlight();
    }

    private static void removeContainer(Minecraft client, BlockPos pos) {
        String ctx = getCurrentContextKey(client);
        if (ctx == null) return;
        Map<Long, IndexedContainer> map = STORAGE_INDEX.get(ctx);
        if (map != null && map.remove(pos.asLong()) != null) {
            dirty = true;
        }
    }

    public static ItemStack resolveIcon(String itemId) {
        if (itemId == null || itemId.isBlank()) return new ItemStack(Items.CHEST);
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) return new ItemStack(Items.CHEST);
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item != Items.AIR ? new ItemStack(item) : new ItemStack(Items.CHEST);
    }

    // -------------------------------------------------------------------------
    // 4. JSON Persistence (.minecraft/config/starclient/storage_finder.json)
    // -------------------------------------------------------------------------

    public static synchronized void ensureLoaded() {
        if (loadedFromDisk) return;
        loadedFromDisk = true;
        File file = getStorageFile();
        if (file == null || !file.isFile()) return;
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return;
            JsonObject root = parsed.getAsJsonObject();
            for (Map.Entry<String, JsonElement> ctxEntry : root.entrySet()) {
                if (!ctxEntry.getValue().isJsonArray()) continue;
                Map<Long, IndexedContainer> ctxMap = new ConcurrentHashMap<>();
                for (JsonElement elem : ctxEntry.getValue().getAsJsonArray()) {
                    if (!elem.isJsonObject()) continue;
                    JsonObject obj = elem.getAsJsonObject();
                    BlockPos pos = new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt());
                    BlockPos pos2 = obj.has("x2")
                            ? new BlockPos(obj.get("x2").getAsInt(), obj.get("y2").getAsInt(), obj.get("z2").getAsInt())
                            : null;
                    String cid = obj.has("cid") ? obj.get("cid").getAsString() : "minecraft:chest";
                    String label = obj.has("label") ? obj.get("label").getAsString() : "Chest";
                    long updated = obj.has("t") ? obj.get("t").getAsLong() : 0L;
                    List<StoredItem> items = new ArrayList<>();
                    if (obj.has("items") && obj.get("items").isJsonArray()) {
                        for (JsonElement ie : obj.getAsJsonArray("items")) {
                            if (!ie.isJsonObject()) continue;
                            JsonObject io = ie.getAsJsonObject();
                            items.add(new StoredItem(
                                    io.get("id").getAsString(),
                                    io.get("n").getAsString(),
                                    io.get("tok").getAsString(),
                                    io.get("c").getAsInt(),
                                    io.has("sh") && io.get("sh").getAsBoolean()
                            ));
                        }
                    }
                    ctxMap.put(pos.asLong(), new IndexedContainer(pos, pos2, cid, label, items, updated));
                }
                STORAGE_INDEX.put(ctxEntry.getKey(), ctxMap);
            }
        } catch (Throwable t) {
            StarClient.LOGGER.warn("Failed to load storage_finder.json", t);
        }
    }

    public static void saveAsync() {
        if (!dirty) return;
        Thread t = new Thread(StorageFinderManager::saveSync, "StarClient-StorageFinder-Save");
        t.setDaemon(true);
        t.start();
    }

    public static synchronized void saveSync() {
        if (!dirty) return;
        dirty = false;
        File file = getStorageFile();
        if (file == null) return;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JsonObject root = new JsonObject();
            for (Map.Entry<String, Map<Long, IndexedContainer>> ctxEntry : STORAGE_INDEX.entrySet()) {
                JsonArray arr = new JsonArray();
                for (IndexedContainer c : ctxEntry.getValue().values()) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("x", c.pos.getX());
                    obj.addProperty("y", c.pos.getY());
                    obj.addProperty("z", c.pos.getZ());
                    if (c.secondPos != null) {
                        obj.addProperty("x2", c.secondPos.getX());
                        obj.addProperty("y2", c.secondPos.getY());
                        obj.addProperty("z2", c.secondPos.getZ());
                    }
                    obj.addProperty("cid", c.containerItemId);
                    obj.addProperty("label", c.label);
                    obj.addProperty("t", c.updatedMs);
                    JsonArray itemsArr = new JsonArray();
                    for (StoredItem si : c.items) {
                        JsonObject io = new JsonObject();
                        io.addProperty("id", si.itemId());
                        io.addProperty("n", si.displayName());
                        io.addProperty("tok", si.searchTokens());
                        io.addProperty("c", si.count());
                        if (si.insideShulker()) io.addProperty("sh", true);
                        itemsArr.add(io);
                    }
                    obj.add("items", itemsArr);
                    arr.add(obj);
                }
                root.add(ctxEntry.getKey(), arr);
            }
            Gson gson = new GsonBuilder().create();
            File tmp = new File(parent, file.getName() + ".tmp");
            try (FileWriter writer = new FileWriter(tmp, StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }
            if (file.exists()) file.delete();
            tmp.renameTo(file);
        } catch (Throwable t) {
            StarClient.LOGGER.warn("Failed to save storage_finder.json", t);
        }
    }

    private static File getStorageFile() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) return null;
        return new File(new File(mc.gameDirectory, StarClient.CONFIG_FOLDER), "storage_finder.json");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void clearPendingInteraction() {
        pendingPos = null;
        pendingSecondPos = null;
        pendingContainerItemId = null;
        pendingContainerLabel = null;
        pendingInteractMs = 0L;
    }

    private static boolean isIndexableContainer(Block block) {
        if (block instanceof EnderChestBlock) return false;
        return block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof HopperBlock
                || block instanceof DispenserBlock
                || block instanceof DropperBlock;
    }

    private static boolean isSupportedMenu(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu
                || menu instanceof ShulkerBoxMenu
                || menu instanceof HopperMenu
                || menu instanceof DispenserMenu;
    }

    private static boolean isShulkerStack(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private static String resolveContainerLabel(String screenTitle, String fallback) {
        if (screenTitle != null) {
            String clean = stripFormatting(screenTitle).trim();
            if (!clean.isEmpty() && !clean.equalsIgnoreCase("Chest") && !clean.equalsIgnoreCase("Large Chest")) {
                return clean;
            }
        }
        return fallback != null ? fallback : "Chest";
    }

    private static String normalizeQuery(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesQuery(StoredItem item, String normalizedQuery) {
        if (normalizedQuery.isEmpty()) return true;
        String[] parts = normalizedQuery.split("\\s+");
        String hay = item.searchTokens();
        for (String p : parts) {
            if (!p.isEmpty() && !hay.contains(p)) return false;
        }
        return true;
    }

    private static String extractExtraHint(StoredItem item) {
        if (!item.insideShulker()) return "";
        return "In Shulker";
    }

    private static int comparePos(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) return Integer.compare(a.getX(), b.getX());
        if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
        return Integer.compare(a.getZ(), b.getZ());
    }

    private static String stripFormatting(String s) {
        return s == null ? "" : s.replaceAll("§[0-9a-fk-orxA-FK-ORX]", "");
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    public static boolean getBool(String name, boolean def) {
        if (module == null) return def;
        Setting<?> s = module.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }

    public static double getNum(String name, double def) {
        if (module == null) return def;
        Setting<?> s = module.getSetting(name);
        return s instanceof NumberSetting ns ? ns.getValue() : def;
    }
}
