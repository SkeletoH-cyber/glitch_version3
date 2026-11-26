package com.ruslan.glitch.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Стирает чанки постепенно (слой за слоем сверху вниз), чтобы сервер не зависал. */
public final class ChunkEraser {
    public static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
    /** Сколько чанков стираются одновременно. */
    public static final int MAX_ACTIVE = 4;
    /** Сколько слоёв (16x16) стирается за тик в каждом чанке. */
    public static final int LAYERS_PER_TICK = 3;

    private static final ArrayDeque<ChunkPos> queue = new ArrayDeque<>();
    private static final Set<Long> seen = new HashSet<>();
    private static final List<Job> active = new ArrayList<>();

    private static final class Job {
        final ChunkPos pos;
        int y;
        Job(ChunkPos pos, int y) { this.pos = pos; this.y = y; }
    }

    private ChunkEraser() {}

    public static void enqueue(ChunkPos p) {
        if (seen.add(p.toLong())) queue.add(p);
    }

    public static boolean isBusy() {
        return !queue.isEmpty() || !active.isEmpty();
    }

    public static void clear() {
        queue.clear();
        active.clear();
        seen.clear();
    }

    public static void tick(ServerWorld world) {
        while (active.size() < MAX_ACTIVE && !queue.isEmpty()) {
            ChunkPos p = queue.poll();
            if (world.getChunkManager().isChunkLoaded(p.x, p.z)) {
                active.add(new Job(p, world.getTopY() - 1));
            }
        }
        if (active.isEmpty()) return;
        BlockPos.Mutable m = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();
        int bottom = world.getBottomY();
        Iterator<Job> it = active.iterator();
        while (it.hasNext()) {
            Job j = it.next();
            for (int i = 0; i < LAYERS_PER_TICK && j.y >= bottom; i++, j.y--) {
                for (int dx = 0; dx < 16; dx++) {
                    for (int dz = 0; dz < 16; dz++) {
                        m.set(j.pos.getStartX() + dx, j.y, j.pos.getStartZ() + dz);
                        if (!world.getBlockState(m).isAir()) {
                            world.setBlockState(m, air, FLAGS);
                        }
                    }
                }
            }
            if (j.y < bottom) it.remove();
        }
    }
}
