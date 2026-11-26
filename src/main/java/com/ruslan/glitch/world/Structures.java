package com.ruslan.glitch.world;

import com.ruslan.glitch.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;

/**
 * Структуры Глича из его блоков. o = первая свободная клетка над землёй (пол постройки на y-1).
 * Имена: obelisk, ruin, arch, pit, ring, tower.
 */
public final class Structures {
    public static final String[] NAMES = {"obelisk", "ruin", "arch", "pit", "ring", "tower"};
    /** Мелкие "шрамы": генерируются вместе с миром, чем дальше от спавна, тем чаще. */
    public static final String[] SMALL_NAMES = {"shard", "patch", "cairn", "fragment", "stump", "scar"};
    public static final String[] ALL_NAMES = {"obelisk", "ruin", "arch", "pit", "ring", "tower",
            "shard", "patch", "cairn", "fragment", "stump", "scar"};
    private static final int F = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private Structures() {}

    private static void set(StructureWorldAccess w, int x, int y, int z, Block b) {
        w.setBlockState(new BlockPos(x, y, z), b.getDefaultState(), F);
    }

    private static void fill(StructureWorldAccess w, int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        BlockState s = b.getDefaultState();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    w.setBlockState(m.set(x, y, z), s, F);
    }

    /** Подпорка под постройку: заполняет пустоты и воду под полом, чтобы она не висела в воздухе. */
    private static void foundation(StructureWorldAccess w, int x1, int z1, int x2, int z2, int y) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        BlockState rock = ModBlocks.CORRUPTED_STONE.getDefaultState();
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int dy = 1; dy <= 10; dy++) {
                    m.set(x, y - dy, z);
                    BlockState s = w.getBlockState(m);
                    if (s.isAir() || !s.getFluidState().isEmpty() || s.isReplaceable()) w.setBlockState(m, rock, F);
                    else break;
                }
            }
        }
    }

    private static boolean loadedAround(StructureWorldAccess w, BlockPos o, int r) {
        return w.getChunkManager().isChunkLoaded((o.getX() - r) >> 4, (o.getZ() - r) >> 4)
                && w.getChunkManager().isChunkLoaded((o.getX() + r) >> 4, (o.getZ() - r) >> 4)
                && w.getChunkManager().isChunkLoaded((o.getX() - r) >> 4, (o.getZ() + r) >> 4)
                && w.getChunkManager().isChunkLoaded((o.getX() + r) >> 4, (o.getZ() + r) >> 4);
    }

    public static boolean place(StructureWorldAccess w, BlockPos o, String name, Random r) {
        if (!loadedAround(w, o, 10)) return false;
        return placeRaw(w, o, name, r);
    }

    /** Без проверки загруженных чанков: нужна при генерации мира (там соседние чанки ещё не "загружены"). */
    public static boolean placeRaw(StructureWorldAccess w, BlockPos o, String name, Random r) {
        switch (name) {
            case "obelisk" -> obelisk(w, o, r);
            case "ruin" -> ruin(w, o, r);
            case "arch" -> arch(w, o, r);
            case "pit" -> pit(w, o);
            case "ring" -> ring(w, o, r);
            case "tower" -> tower(w, o);
            case "shard" -> shard(w, o, r);
            case "patch" -> patch(w, o, r);
            case "cairn" -> cairn(w, o, r);
            case "fragment" -> fragment(w, o, r);
            case "stump" -> stump(w, o, r);
            case "scar" -> scar(w, o, r);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Для автоматического появления: ищет поверхность в точке x,z и ставит случайную структуру. */
    public static boolean placeAtSurface(StructureWorldAccess w, int x, int z, String name, Random r) {
        if (!w.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return false;
        int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockState below = w.getBlockState(new BlockPos(x, y - 1, z));
        if (below.isAir() || !below.getFluidState().isEmpty()) return false;
        return place(w, new BlockPos(x, y, z), name, r);
    }

    /** Для команды /glitch structure: ставит постройку перед игроком. */
    public static boolean placeInFront(ServerPlayerEntity p, String name) {
        ServerWorld w = (ServerWorld) p.getWorld();
        Vec3d look = p.getRotationVec(1.0f);
        double len = Math.max(0.01, Math.sqrt(look.x * look.x + look.z * look.z));
        int x = p.getBlockX() + (int) Math.round(look.x / len * 14);
        int z = p.getBlockZ() + (int) Math.round(look.z / len * 14);
        return placeAtSurface(w, x, z, name, w.random);
    }

    // ------------------------------------------------------------------ постройки

    private static void obelisk(StructureWorldAccess w, BlockPos o, Random r) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        fill(w, x - 2, y, z - 2, x + 2, y + 11, z + 2, Blocks.AIR);
        foundation(w, x - 2, z - 2, x + 2, z + 2, y);
        fill(w, x - 2, y - 1, z - 2, x + 2, y - 1, z + 2, ModBlocks.CORRUPTED_BRICKS);
        fill(w, x - 1, y - 1, z - 1, x + 1, y - 1, z + 1, ModBlocks.ERROR_BLOCK);
        fill(w, x, y, z, x, y + 8, z, ModBlocks.GLITCH_PILLAR);
        set(w, x, y + 9, z, ModBlocks.GLITCH_LAMP);
        for (int sx = -2; sx <= 2; sx += 4)
            for (int sz = -2; sz <= 2; sz += 4)
                fill(w, x + sx, y, z + sz, x + sx, y + 1 + r.nextInt(2), z + sz, ModBlocks.VOID_BLOCK);
    }

    private static void ruin(StructureWorldAccess w, BlockPos o, Random r) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        fill(w, x - 4, y, z - 4, x + 4, y + 6, z + 4, Blocks.AIR);
        foundation(w, x - 4, z - 4, x + 4, z + 4, y);
        fill(w, x - 4, y - 1, z - 4, x + 4, y - 1, z + 4, ModBlocks.CORRUPTED_PLANKS);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (Math.abs(dx) != 4 && Math.abs(dz) != 4) continue;
                int h = r.nextInt(100) < 25 ? 0 : 1 + r.nextInt(5);
                for (int dy = 0; dy < h; dy++) {
                    boolean window = dy == 2 && h >= 4 && (dx + dz) % 2 == 0;
                    set(w, x + dx, y + dy, z + dz, window ? ModBlocks.STATIC_GLASS : ModBlocks.CORRUPTED_BRICKS);
                }
            }
        }
        set(w, x, y, z - 4, Blocks.AIR);
        set(w, x, y + 1, z - 4, Blocks.AIR);
        set(w, x - 3, y, z - 3, ModBlocks.GLITCH_LAMP);
        for (int i = 0; i < 6; i++) {
            set(w, x - 3 + r.nextInt(7), y, z - 3 + r.nextInt(7),
                    r.nextBoolean() ? ModBlocks.ERROR_BLOCK : ModBlocks.CORRUPTED_STONE);
        }
    }

    private static void arch(StructureWorldAccess w, BlockPos o, Random r) {
        boolean axisX = r.nextBoolean();
        int x = o.getX(), y = o.getY(), z = o.getZ();
        fill(w, x - 3, y, z - 3, x + 3, y + 7, z + 3, Blocks.AIR);
        foundation(w, x - 3, z - 3, x + 3, z + 3, y);
        for (int i = -2; i <= 2; i++) {
            int px = x + (axisX ? i : 0), pz = z + (axisX ? 0 : i);
            set(w, px, y, pz, ModBlocks.VOID_BLOCK);
            set(w, px, y + 5, pz, ModBlocks.VOID_BLOCK);
            if (Math.abs(i) == 2) {
                fill(w, px, y, pz, px, y + 5, pz, ModBlocks.VOID_BLOCK);
                set(w, px, y + 6, pz, ModBlocks.GLITCH_LAMP);
            } else {
                fill(w, px, y + 1, pz, px, y + 4, pz, ModBlocks.GLITCH_BLOCK);
            }
        }
    }

    private static void pit(StructureWorldAccess w, BlockPos o) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= 16) {
                    fill(w, x + dx, y - 4, z + dz, x + dx, y + 5, z + dz, Blocks.AIR);
                    set(w, x + dx, y - 5, z + dz, ModBlocks.GLITCH_BLOCK);
                } else if (d2 <= 25) {
                    fill(w, x + dx, y, z + dz, x + dx, y + 5, z + dz, Blocks.AIR);
                    set(w, x + dx, y - 1, z + dz, ((dx + dz) & 1) == 0 ? ModBlocks.ERROR_BLOCK : ModBlocks.CORRUPTED_STONE);
                }
            }
        }
        int[][] corners = {{6, 0}, {-6, 0}, {0, 6}, {0, -6}};
        for (int[] c : corners) {
            fill(w, x + c[0], y, z + c[1], x + c[0], y + 3, z + c[1], ModBlocks.GLITCH_PILLAR);
            set(w, x + c[0], y + 4, z + c[1], ModBlocks.GLITCH_LAMP);
        }
    }

    private static void ring(StructureWorldAccess w, BlockPos o, Random r) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > 49) continue;
                fill(w, x + dx, y, z + dz, x + dx, y + 8, z + dz, Blocks.AIR);
                if (d2 <= 36) {
                    set(w, x + dx, y - 1, z + dz, d2 <= 4 ? ModBlocks.ERROR_BLOCK : ModBlocks.CORRUPTED_STONE);
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            int px = x + (int) Math.round(Math.cos(ang) * 6), pz = z + (int) Math.round(Math.sin(ang) * 6);
            int h = 4 + r.nextInt(3);
            fill(w, px, y, pz, px, y + h - 1, pz, ModBlocks.GLITCH_PILLAR);
            if (r.nextBoolean()) set(w, px, y + h, pz, ModBlocks.STATIC_GLASS);
        }
        set(w, x, y, z, ModBlocks.VOID_BLOCK);
        set(w, x, y + 1, z, ModBlocks.GLITCH_LAMP);
    }

    private static void tower(StructureWorldAccess w, BlockPos o) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        fill(w, x - 2, y, z - 2, x + 2, y + 16, z + 2, Blocks.AIR);
        foundation(w, x - 2, z - 2, x + 2, z + 2, y);
        fill(w, x - 2, y - 1, z - 2, x + 2, y - 1, z + 2, ModBlocks.CORRUPTED_BRICKS);
        for (int dy = 0; dy < 14; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) != 2 && Math.abs(dz) != 2) continue;
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    boolean window = !corner && (dy == 4 || dy == 9) && (dx == 0 || dz == 0);
                    set(w, x + dx, y + dy, z + dz,
                            corner ? ModBlocks.GLITCH_PILLAR : window ? ModBlocks.STATIC_GLASS : ModBlocks.CORRUPTED_BRICKS);
                }
            }
        }
        set(w, x, y, z - 2, Blocks.AIR);
        set(w, x, y + 1, z - 2, Blocks.AIR);
        fill(w, x - 2, y + 14, z - 2, x + 2, y + 14, z + 2, ModBlocks.CORRUPTED_BRICKS);
        set(w, x, y + 15, z, ModBlocks.GLITCH_LAMP);
        set(w, x, y, z, ModBlocks.GLITCH_LAMP);
    }

    // ------------------------------------------------------------------ мелкие "шрамы" (радиус до 3)

    private static Block pick(Random r, Block... blocks) {
        return blocks[r.nextInt(blocks.length)];
    }

    private static void clearPlants(StructureWorldAccess w, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        if (w.getBlockState(p).isReplaceable()) w.setBlockState(p, Blocks.AIR.getDefaultState(), F);
    }

    private static void shard(StructureWorldAccess w, BlockPos o, Random r) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        int h = 2 + r.nextInt(2);
        foundation(w, x, z, x, z, y);
        fill(w, x, y, z, x, y + h - 1, z, ModBlocks.GLITCH_PILLAR);
        set(w, x, y + h, z, r.nextBoolean() ? ModBlocks.GLITCH_BLOCK : ModBlocks.ERROR_BLOCK);
    }

    private static void patch(StructureWorldAccess w, BlockPos o, Random r) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5 || r.nextInt(100) >= 85) continue;
                clearPlants(w, x + dx, y, z + dz);
                set(w, x + dx, y - 1, z + dz,
                        pick(r, ModBlocks.GLITCH_BLOCK, ModBlocks.CORRUPTED_STONE, ModBlocks.ERROR_BLOCK, ModBlocks.VOID_BLOCK));
            }
        }
    }

    private static void cairn(StructureWorldAccess w, BlockPos o, Random r) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        foundation(w, x - 1, z - 1, x, z, y);
        fill(w, x - 1, y, z - 1, x, y, z, ModBlocks.CORRUPTED_STONE);
        set(w, x, y + 1, z, ModBlocks.CORRUPTED_BRICKS);
        set(w, x, y + 2, z, ModBlocks.ERROR_BLOCK);
    }

    private static void fragment(StructureWorldAccess w, BlockPos o, Random r) {
        boolean axisX = r.nextBoolean();
        int x = o.getX(), y = o.getY(), z = o.getZ();
        int glassAt = r.nextInt(5) - 2;
        for (int i = -2; i <= 2; i++) {
            int px = x + (axisX ? i : 0), pz = z + (axisX ? 0 : i);
            foundation(w, px, pz, px, pz, y);
            int h = r.nextInt(100) < 25 ? 0 : 1 + r.nextInt(3);
            for (int dy = 0; dy < h; dy++) {
                set(w, px, y + dy, pz, (i == glassAt && dy == h - 1 && h >= 2) ? ModBlocks.STATIC_GLASS : ModBlocks.CORRUPTED_BRICKS);
            }
        }
    }

    private static void stump(StructureWorldAccess w, BlockPos o, Random r) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        foundation(w, x, z, x, z, y);
        fill(w, x, y, z, x, y + 2, z, ModBlocks.VOID_BLOCK);
        if (r.nextBoolean()) {
            clearPlants(w, x + 1, y, z);
            set(w, x + 1, y, z, ModBlocks.ERROR_BLOCK);
        }
    }

    private static void scar(StructureWorldAccess w, BlockPos o, Random r) {
        int x = o.getX(), y = o.getY(), z = o.getZ();
        boolean axisX = r.nextBoolean();
        int off = 0;
        for (int i = -3; i <= 3; i++) {
            if (r.nextInt(3) == 0) off += r.nextInt(3) - 1;
            int px = x + (axisX ? i : off), pz = z + (axisX ? off : i);
            if (r.nextInt(100) < 20) continue;
            clearPlants(w, px, y, pz);
            set(w, px, y - 1, pz, r.nextBoolean() ? ModBlocks.GLITCH_BLOCK : ModBlocks.CORRUPTED_STONE);
        }
    }
}
