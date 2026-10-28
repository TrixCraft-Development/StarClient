package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public final class OreEspState {
    public record Entry(BlockPos pos, String label, int color) {
    }

    public enum Ore {
        DIAMOND("Diamond", 0xFF22D3EE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
        IRON("Iron", 0xFFFB923C, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
        GOLD("Gold", 0xFFFACC15, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE),
        COAL("Coal", 0xFF64748B, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
        REDSTONE("Redstone", 0xFFEF4444, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
        LAPIS("Lapis", 0xFF3B82F6, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
        COPPER("Copper", 0xFFF97316, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
        EMERALD("Emerald", 0xFF34D399, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
        ANCIENT_DEBRIS("Ancient Debris", 0xFF92400E, Blocks.ANCIENT_DEBRIS);

        final String label;
        final int color;
        final Block[] blocks;

        Ore(String label, int color, Block... blocks) {
            this.label = label;
            this.color = color;
            this.blocks = blocks;
        }

        static Ore forBlock(Block block) {
            for (Ore ore : values()) {
                for (Block candidate : ore.blocks) {
                    if (candidate == block) return ore;
                }
            }
            return null;
        }
    }

    private static final int MAX_ENTRIES = 512;
    private static final List<Entry> entries = new ArrayList<>();

    private OreEspState() {
    }

    public static List<Entry> getEntries() {
        return entries;
    }

    public static void clear() {
        entries.clear();
    }

    public static void update(Minecraft client, double range, boolean[] enabled) {
        entries.clear();
        if (client.player == null || client.level == null) return;

        double rangeSqr = range * range;
        BlockPos center = client.player.blockPosition();
        int r = (int) Math.ceil(range);
        BlockPos start = center.offset(-r, -r, -r);
        BlockPos end = center.offset(r, r, r);

        for (BlockPos pos : BlockPos.betweenClosed(start, end)) {
            if (!client.level.isLoaded(pos)) continue;
            if (client.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSqr) continue;
            Ore ore = Ore.forBlock(client.level.getBlockState(pos).getBlock());
            if (ore == null || !enabled[ore.ordinal()]) continue;
            entries.add(new Entry(pos.immutable(), ore.label, ore.color));
            if (entries.size() >= MAX_ENTRIES) return;
        }
    }
}