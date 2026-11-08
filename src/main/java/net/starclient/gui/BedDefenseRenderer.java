package net.starclient.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.starclient.StarClient;
import net.starclient.module.Module;
import net.starclient.setting.BooleanSetting;
import net.starclient.setting.NumberSetting;
import net.starclient.setting.Setting;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Scans nearby beds and renders a floating glassmorphic card displaying their
 * Bedwars defense layers (Obsidian, End Stone, Wood, Clay, Glass, Wool, Water)
 * and exposed warnings ("Bed Defense" SERVER module).
 */
public final class BedDefenseRenderer {

    private record DefenseLayer(ItemStack icon, String shortName) {
    }

    private record BedEntry(
            BlockPos headPos,
            BlockPos footPos,
            Vec3 centerPos,
            String bedName,
            int bedRgb,
            boolean exposed,
            List<DefenseLayer> layers
    ) {
    }

    private static final List<BedEntry> CACHED_BEDS = new ArrayList<>();
    private static long lastScanTick = -100L;
    private static BlockPos lastPlayerPos = null;
    private static int lastRange = -1;
    private static int lastMaxLayers = -1;

    private BedDefenseRenderer() {
    }

    public static void clear() {
        CACHED_BEDS.clear();
        lastPlayerPos = null;
    }

    private static void refreshIfNeeded(Minecraft client, Module mod) {
        Level level = client.level;
        if (level == null || client.player == null) {
            CACHED_BEDS.clear();
            return;
        }

        int range = (int) Math.round(getNum(mod, "Range", 48.0));
        int maxLayers = (int) Math.round(getNum(mod, "Max Layers", 3.0));
        BlockPos playerPos = client.player.blockPosition();
        long gameTime = level.getGameTime();

        if (playerPos.equals(lastPlayerPos)
                && Math.abs(gameTime - lastScanTick) < 10L
                && range == lastRange
                && maxLayers == lastMaxLayers) {
            return;
        }

        lastPlayerPos = playerPos.immutable();
        lastScanTick = gameTime;
        lastRange = range;
        lastMaxLayers = maxLayers;
        CACHED_BEDS.clear();

        int chunkRadius = (range >> 4) + 1;
        int pcx = client.player.chunkPosition().x();
        int pcz = client.player.chunkPosition().z();
        double rangeSqr = (double) range * range;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                int chunkX = pcx + cx;
                int chunkZ = pcz + cz;
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                LevelChunkSection[] sections = chunk.getSections();
                int baseBlockX = chunkX << 4;
                int baseBlockZ = chunkZ << 4;

                for (int secIdx = 0; secIdx < sections.length; secIdx++) {
                    LevelChunkSection section = sections[secIdx];
                    if (section == null || section.hasOnlyAir()) continue;
                    if (!section.maybeHas(st -> st.getBlock() instanceof BedBlock)) continue;

                    int baseBlockY = chunk.getSectionYFromSectionIndex(secIdx) << 4;
                    for (int ly = 0; ly < 16; ly++) {
                        int wy = baseBlockY + ly;
                        for (int lx = 0; lx < 16; lx++) {
                            int wx = baseBlockX + lx;
                            for (int lz = 0; lz < 16; lz++) {
                                BlockState state = section.getBlockState(lx, ly, lz);
                                if (!(state.getBlock() instanceof BedBlock bedBlock)) continue;
                                if (state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) != BedPart.HEAD) {
                                    continue;
                                }
                                int wz = baseBlockZ + lz;
                                double distSqr = client.player.distanceToSqr(wx + 0.5, wy + 0.5, wz + 0.5);
                                if (distSqr > rangeSqr) continue;

                                mpos.set(wx, wy, wz);
                                BlockPos pos = mpos.immutable();
                                Direction facing = state.hasProperty(BedBlock.FACING) ? state.getValue(BedBlock.FACING) : Direction.NORTH;
                                BlockPos footPos = pos.relative(facing.getOpposite());
                                Vec3 center = new Vec3(
                                        (pos.getX() + footPos.getX()) * 0.5 + 0.5,
                                        pos.getY() + 0.5625,
                                        (pos.getZ() + footPos.getZ()) * 0.5 + 0.5
                                );

                                String bedTitle = bedBlock.getName().getString();
                                int bedColor = bedBlock.getColor().getTextColor() | 0xFF000000;

                                boolean exposed = isBedExposed(level, pos, footPos);
                                List<DefenseLayer> layers = analyzeDefenseLayers(level, pos, footPos, maxLayers);
                                CACHED_BEDS.add(new BedEntry(pos, footPos, center, bedTitle, bedColor, exposed, layers));
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isBedExposed(Level level, BlockPos head, BlockPos foot) {
        Direction[] checkDirs = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (BlockPos part : new BlockPos[]{head, foot}) {
            for (Direction dir : checkDirs) {
                BlockPos neighbor = part.relative(dir);
                if (neighbor.equals(head) || neighbor.equals(foot)) continue;
                BlockState st = level.getBlockState(neighbor);
                if (!isDefenseBlock(st)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<DefenseLayer> analyzeDefenseLayers(Level level, BlockPos head, BlockPos foot, int maxLayers) {
        List<DefenseLayer> result = new ArrayList<>(maxLayers);
        for (int layer = 1; layer <= maxLayers; layer++) {
            Map<Block, Integer> counts = new LinkedHashMap<>();
            // Sample blocks directly above and around the bed at shell distance == layer
            BlockPos[] samples = {
                    head.above(layer),
                    foot.above(layer),
                    head.north(layer),
                    head.south(layer),
                    head.east(layer),
                    head.west(layer),
                    foot.north(layer),
                    foot.south(layer),
                    foot.east(layer),
                    foot.west(layer)
            };
            for (BlockPos sample : samples) {
                if (sample.equals(head) || sample.equals(foot)) continue;
                BlockState st = level.getBlockState(sample);
                if (!isDefenseBlock(st)) continue;
                counts.merge(st.getBlock(), 1, Integer::sum);
            }
            if (counts.isEmpty()) break;

            // Pick highest-priority / most-common defense block in this shell
            Block bestBlock = null;
            int bestScore = -1;
            for (Map.Entry<Block, Integer> e : counts.entrySet()) {
                int score = e.getValue() * 10 + defensePriorityBonus(e.getKey());
                if (score > bestScore) {
                    bestScore = score;
                    bestBlock = e.getKey();
                }
            }
            if (bestBlock != null) {
                result.add(toDefenseLayer(bestBlock));
            }
        }
        return result;
    }

    private static boolean isDefenseBlock(BlockState state) {
        if (state.isAir() || state.getBlock() instanceof BedBlock) return false;
        if (state.is(Blocks.WATER)) return true;
        return !state.getCollisionShape(Minecraft.getInstance().level, BlockPos.ZERO).isEmpty();
    }

    private static int defensePriorityBonus(Block block) {
        if (block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN) return 50;
        if (block == Blocks.END_STONE) return 40;
        if (block.defaultBlockState().is(BlockTags.PLANKS) || block.defaultBlockState().is(BlockTags.LOGS)) return 30;
        if (block.defaultBlockState().is(BlockTags.TERRACOTTA)) return 25;
        if (block == Blocks.WATER) return 20;
        return 0;
    }

    private static DefenseLayer toDefenseLayer(Block block) {
        if (block == Blocks.WATER) {
            return new DefenseLayer(new ItemStack(Items.WATER_BUCKET), "Water");
        }
        if (block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN) {
            return new DefenseLayer(new ItemStack(Items.OBSIDIAN), "Obsidian");
        }
        if (block == Blocks.END_STONE) {
            return new DefenseLayer(new ItemStack(Items.END_STONE), "End Stone");
        }
        BlockState def = block.defaultBlockState();
        if (def.is(BlockTags.PLANKS) || def.is(BlockTags.LOGS)) {
            return new DefenseLayer(new ItemStack(block.asItem()), "Wood");
        }
        if (def.is(BlockTags.TERRACOTTA)) {
            return new DefenseLayer(new ItemStack(block.asItem()), "Clay");
        }
        if (def.is(BlockTags.WOOL)) {
            return new DefenseLayer(new ItemStack(block.asItem()), "Wool");
        }
        String rawName = block.getName().getString();
        if (rawName.toLowerCase(Locale.ROOT).contains("glass")) {
            return new DefenseLayer(new ItemStack(block.asItem()), "Glass");
        }
        ItemStack icon = block.asItem() != Items.AIR ? new ItemStack(block.asItem()) : new ItemStack(Items.BARRIER);
        return new DefenseLayer(icon, rawName);
    }

    public static void render(GuiGraphicsExtractor extractor, Minecraft client, Module mod) {
        if (client == null || client.level == null || client.player == null || mod == null) return;

        refreshIfNeeded(client, mod);
        if (CACHED_BEDS.isEmpty()) return;

        GameRenderer gr = client.gameRenderer;
        int sw = extractor.guiWidth();
        int sh = extractor.guiHeight();

        boolean showBox = getBool(mod, "Highlight Bed", true);
        boolean showIcons = getBool(mod, "Show Block Icons", true);
        boolean showNames = getBool(mod, "Show Layer Names", true);
        boolean showExposed = getBool(mod, "Show Exposed Alert", true);

        int bgColor = StarClient.getInstance() != null && StarClient.getInstance().getClientSettings() != null
                ? StarClient.getInstance().getClientSettings().getMenuBackgroundColor()
                : 0xFF0F172A;

        for (BedEntry bed : CACHED_BEDS) {
            if (showBox) {
                int minX = Math.min(bed.headPos().getX(), bed.footPos().getX());
                int minY = bed.headPos().getY();
                int minZ = Math.min(bed.headPos().getZ(), bed.footPos().getZ());
                int maxX = Math.max(bed.headPos().getX(), bed.footPos().getX()) + 1;
                int maxZ = Math.max(bed.headPos().getZ(), bed.footPos().getZ()) + 1;
                AABB bedBox = new AABB(minX, minY, minZ, maxX, minY + 0.5625, maxZ).inflate(0.01);
                EspRenderUtil.drawBox3D(extractor, gr, bedBox, sw, sh, 1.5f, bed.bedRgb());
            }

            Vec3 screenPos = EspRenderUtil.projectToScreen(gr,
                    new Vec3(bed.centerPos().x, bed.centerPos().y + 0.9, bed.centerPos().z), sw, sh);
            if (screenPos == null) continue;

            int dist = (int) Math.round(client.player.position().distanceTo(bed.centerPos()));
            String header = "🛏 " + bed.bedName() + " (" + dist + "m)";
            String layerSummary;
            if (bed.layers().isEmpty()) {
                layerSummary = "No Defense (Open)";
            } else {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < bed.layers().size(); i++) {
                    if (i > 0) sb.append(" → ");
                    sb.append(bed.layers().get(i).shortName());
                }
                layerSummary = sb.toString();
            }
            if (showExposed && bed.exposed() && !bed.layers().isEmpty()) {
                layerSummary += " • ⚠ Exposed";
            }

            int headerW = client.font.width(header);
            int subW = showNames ? client.font.width(layerSummary) : 0;
            int iconsW = (showIcons && !bed.layers().isEmpty()) ? (bed.layers().size() * 16 - 2) : 0;

            int cardW = Math.max(Math.max(headerW, subW), iconsW) + 12;
            int cardH = 14 + (showNames ? 10 : 0) + (iconsW > 0 ? 15 : 0);

            int x = (int) Math.round(screenPos.x) - cardW / 2;
            int y = (int) Math.round(screenPos.y) - cardH;

            int borderCol = (showExposed && bed.exposed()) ? 0xCCF87171 : ((bed.bedRgb() & 0x00FFFFFF) | 0xBB000000);
            GuiRenderUtils.fillRoundedRect(extractor, x, y, cardW, cardH, 4, (bgColor & 0x00FFFFFF) | 0xDC000000);
            GuiRenderUtils.drawRoundedOutline(extractor, x, y, cardW, cardH, 4, borderCol);

            int curY = y + 3;
            extractor.text(client.font, header, x + (cardW - headerW) / 2, curY, 0xFFF8FAFC, true);
            curY += 10;

            if (showNames) {
                int subCol = (bed.layers().isEmpty() || (showExposed && bed.exposed())) ? 0xFFF87171 : 0xFFCBD5E1;
                extractor.text(client.font, layerSummary, x + (cardW - subW) / 2, curY, subCol, true);
                curY += 10;
            }

            if (iconsW > 0) {
                int iconX = x + (cardW - iconsW) / 2;
                Matrix3x2fStack pose = extractor.pose();
                for (DefenseLayer layer : bed.layers()) {
                    GuiRenderUtils.fillRoundedRect(extractor, iconX, curY, 14, 14, 3, 0x55000000);
                    pose.pushMatrix();
                    pose.translate(iconX + 1, curY + 1);
                    pose.scale(0.75f, 0.75f);
                    extractor.item(layer.icon(), 0, 0);
                    pose.popMatrix();
                    iconX += 16;
                }
            }
        }
    }

    private static boolean getBool(Module mod, String name, boolean def) {
        Setting<?> s = mod.getSetting(name);
        return s instanceof BooleanSetting bs ? bs.getValue() : def;
    }

    private static double getNum(Module mod, String name, double def) {
        Setting<?> s = mod.getSetting(name);
        return s instanceof NumberSetting ns ? ns.getValue() : def;
    }
}
