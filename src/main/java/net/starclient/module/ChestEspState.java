package net.starclient.module;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ChestEspState {
    public record Entry(BlockPos pos, String label, int color) {
    }

    private static final List<Entry> entries = new ArrayList<>();

    private ChestEspState() {
    }

    public static List<Entry> getEntries() {
        return entries;
    }

    public static void clear() {
        entries.clear();
    }

    public static void update(Minecraft client, double range) {
        if (client.player == null || client.level == null) return;
        entries.clear();

        int chunkRadius = (int) (range / 16.0) + 1;
        int pcx = client.player.chunkPosition().x();
        int pcz = client.player.chunkPosition().z();
        double rangeSqr = range * range;

        for (int cx = -chunkRadius; cx <= chunkRadius; cx++) {
            for (int cz = -chunkRadius; cz <= chunkRadius; cz++) {
                LevelChunk chunk = client.level.getChunk(pcx + cx, pcz + cz);
                for (Map.Entry<BlockPos, BlockEntity> blockEntity : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = blockEntity.getKey();
                    double distSqr = client.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distSqr > rangeSqr) continue;

                    BlockEntity be = blockEntity.getValue();
                    String label = null;
                    int color = 0;
                    if (be instanceof ChestBlockEntity) {
                        label = "Chest";
                        color = 0xFFFACC15;
                    } else if (be instanceof BarrelBlockEntity) {
                        label = "Barrel";
                        color = 0xFFF59E0B;
                    } else if (be instanceof EnderChestBlockEntity) {
                        label = "Ender Chest";
                        color = 0xFFA855F7;
                    } else if (be instanceof ShulkerBoxBlockEntity) {
                        label = "Shulker Box";
                        color = 0xFFEC4899;
                    }
                    if (label == null) continue;

                    entries.add(new Entry(pos, label, color));
                }
            }
        }
    }
}