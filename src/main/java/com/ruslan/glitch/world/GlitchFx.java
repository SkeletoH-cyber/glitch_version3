package com.ruslan.glitch.world;

import com.ruslan.glitch.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Визуальные "глюки": блоки рядом с игроком на мгновение меняют цвет, потом возвращаются. */
public final class GlitchFx {
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    private static final Block[] GLITCH_BLOCKS = {
            Blocks.MAGENTA_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.BLACK_CONCRETE,
            Blocks.WHITE_CONCRETE, Blocks.RED_CONCRETE, Blocks.PURPLE_CONCRETE,
            ModBlocks.GLITCH_BLOCK, ModBlocks.ERROR_BLOCK
    };

    private record Entry(ServerWorld world, BlockPos pos, BlockState original, long expire) {}

    private static final List<Entry> entries = new ArrayList<>();

    private GlitchFx() {}

    public static void clear() {
        entries.clear();
    }

    private static boolean isGlitchBlock(BlockState s) {
        for (Block b : GLITCH_BLOCKS) if (s.isOf(b)) return true;
        return false;
    }

    public static void spawn(ServerWorld w, ServerPlayerEntity p, int count) {
        if (entries.size() > 500) return;
        Random r = w.random;
        for (int i = 0; i < count; i++) {
            BlockPos pos = new BlockPos(p.getBlockX() + r.nextInt(25) - 12, p.getBlockY() + r.nextInt(6) - 4,
                    p.getBlockZ() + r.nextInt(25) - 12);
            if (!w.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            BlockState s = w.getBlockState(pos);
            if (s.isAir() || s.isOf(Blocks.BEDROCK) || !s.getFluidState().isEmpty()
                    || s.hasBlockEntity() || isGlitchBlock(s) || !s.isFullCube(w, pos)) continue;
            entries.add(new Entry(w, pos, s, w.getTime() + 20 + r.nextInt(60)));
            w.setBlockState(pos, GLITCH_BLOCKS[r.nextInt(GLITCH_BLOCKS.length)].getDefaultState(), FLAGS);
        }
    }

    public static void tick() {
        if (entries.isEmpty()) return;
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.world().getTime() >= e.expire()) {
                if (isGlitchBlock(e.world().getBlockState(e.pos()))) {
                    e.world().setBlockState(e.pos(), e.original(), FLAGS);
                }
                it.remove();
            }
        }
    }
}
