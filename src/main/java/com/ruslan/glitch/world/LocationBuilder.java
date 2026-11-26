package com.ruslan.glitch.world;

import com.ruslan.glitch.GlitchMod;
import com.ruslan.glitch.block.ModBlocks;
import com.ruslan.glitch.entity.GlitchMinionEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/** Строит содержимое каждого мира Глича при первом входе. Земля (y=62) приходит из JSON измерения. */
public final class LocationBuilder {
    private static final int F = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
    private static final int GROUND = 62;
    private static final int FLOOR = GROUND + 1;

    private static final Block[] COLORS = {
            Blocks.MAGENTA_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.BLACK_CONCRETE,
            Blocks.WHITE_CONCRETE, Blocks.RED_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.ORANGE_CONCRETE
    };

    private LocationBuilder() {}

    private static void set(ServerWorld w, int x, int y, int z, Block b) {
        w.setBlockState(new BlockPos(x, y, z), b.getDefaultState(), F);
    }

    private static void fill(ServerWorld w, int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        BlockState s = b.getDefaultState();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    w.setBlockState(m.set(x, y, z), s, F);
    }

    private static Block color(Random r) {
        if (r.nextInt(5) < 2) {
            Block[] own = {ModBlocks.GLITCH_BLOCK, ModBlocks.ERROR_BLOCK, ModBlocks.CORRUPTED_STONE};
            return own[r.nextInt(own.length)];
        }
        return COLORS[r.nextInt(COLORS.length)];
    }

    public static void build(ServerWorld w, int idx) {
        Random r = Random.create(9000L + idx);
        switch (idx) {
            case 0 -> staticPlains(w, r);
            case 1 -> mirrorHall(w, r);
            case 2 -> hollowVillage(w, r);
            case 3 -> corridor(w);
            case 4 -> redWasteland(w, r);
            case 5 -> platforms(w, r);
            case 6 -> pixelDesert(w, r);
            case 7 -> library(w);
            case 8 -> blackGarden(w, r);
            default -> core(w);
        }
        addStructures(w, idx, r);
    }

    private static void addStructures(ServerWorld w, int idx, Random r) {
        String[] list = switch (idx) {
            case 0 -> new String[]{"obelisk", "obelisk", "arch"};
            case 1 -> new String[]{"ring", "ring", "obelisk"};
            case 2 -> new String[]{"ruin", "ruin", "obelisk"};
            case 4 -> new String[]{"tower", "pit", "obelisk"};
            case 6 -> new String[]{"obelisk", "arch", "pit"};
            case 8 -> new String[]{"ring", "tower", "ruin"};
            default -> new String[0];
        };
        for (String name : list) {
            double ang = r.nextDouble() * Math.PI * 2;
            int d = 14 + r.nextInt(18);
            Structures.place(w, new BlockPos((int) (Math.cos(ang) * d), FLOOR, (int) (Math.sin(ang) * d)), name, r);
        }
    }

    // 1. Цветные пятна на земле и столбы-помехи
    private static void staticPlains(ServerWorld w, Random r) {
        for (int i = 0; i < 70; i++) {
            int cx = r.nextInt(81) - 40, cz = r.nextInt(81) - 40, s = 1 + r.nextInt(3);
            fill(w, cx, GROUND, cz, cx + s, GROUND, cz + s, color(r));
        }
        for (int i = 0; i < 35; i++) {
            int x = r.nextInt(77) - 38, z = r.nextInt(77) - 38;
            if (Math.abs(x) < 4 && Math.abs(z) < 4) continue;
            fill(w, x, FLOOR, z, x, FLOOR + 2 + r.nextInt(7), z, color(r));
        }
    }

    // 2. Шахматный пол, кварцевые колонны и стеклянные "зеркала"
    private static void mirrorHall(ServerWorld w, Random r) {
        for (int x = -45; x <= 45; x++)
            for (int z = -45; z <= 45; z++)
                set(w, x, GROUND, z, ((x + z) & 1) == 0 ? Blocks.WHITE_CONCRETE : Blocks.BLACK_CONCRETE);
        for (int gx = -30; gx <= 30; gx += 10)
            for (int gz = -30; gz <= 30; gz += 10) {
                if (gx == 0 && gz == 0) continue;
                fill(w, gx, FLOOR, gz, gx, FLOOR + 6, gz, Blocks.QUARTZ_PILLAR);
            }
        for (int i = 0; i < 14; i++) {
            int x = r.nextInt(61) - 30, z = r.nextInt(61) - 30;
            if (Math.abs(x) < 5 && Math.abs(z) < 5) continue;
            if (r.nextBoolean()) fill(w, x, FLOOR, z, x + 7, FLOOR + 5, z, Blocks.GRAY_STAINED_GLASS);
            else fill(w, x, FLOOR, z, x, FLOOR + 5, z + 7, Blocks.GRAY_STAINED_GLASS);
        }
    }

    // 3. Деревня без жителей: дома, колодец, фонари
    private static void hollowVillage(ServerWorld w, Random r) {
        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI / 3 + 0.3;
            int hx = (int) Math.round(Math.cos(ang) * 20), hz = (int) Math.round(Math.sin(ang) * 20);
            // стены и крыша
            fill(w, hx - 3, FLOOR, hz - 3, hx + 3, FLOOR + 3, hz + 3, Blocks.OAK_PLANKS);
            fill(w, hx - 2, FLOOR, hz - 2, hx + 2, FLOOR + 2, hz + 2, Blocks.AIR);
            fill(w, hx - 3, FLOOR + 4, hz - 3, hx + 3, FLOOR + 4, hz + 3, Blocks.SPRUCE_PLANKS);
            // вход в сторону центра
            if (Math.abs(hx) >= Math.abs(hz)) {
                int wx = hx + (hx > 0 ? -3 : 3);
                fill(w, wx, FLOOR, hz, wx, FLOOR + 1, hz, Blocks.AIR);
            } else {
                int wz = hz + (hz > 0 ? -3 : 3);
                fill(w, hx, FLOOR, wz, hx, FLOOR + 1, wz, Blocks.AIR);
            }
            set(w, hx + 1, FLOOR + 3, hz + 3, Blocks.GLASS);
        }
        // колодец
        fill(w, 6, FLOOR, 6, 8, FLOOR + 1, 8, Blocks.COBBLESTONE);
        set(w, 7, FLOOR, 7, Blocks.AIR);
        set(w, 7, FLOOR + 1, 7, Blocks.AIR);
        // фонари
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            int x = (int) Math.round(Math.cos(ang) * 12), z = (int) Math.round(Math.sin(ang) * 12);
            fill(w, x, FLOOR, z, x, FLOOR + 2, z, Blocks.OAK_FENCE);
            set(w, x, FLOOR + 3, z, Blocks.SOUL_LANTERN);
        }
    }

    // 4. Бесконечный коридор длиной 120 блоков
    private static void corridor(ServerWorld w) {
        fill(w, -3, GROUND, -4, 3, GROUND, 124, Blocks.STONE_BRICKS);
        fill(w, -3, FLOOR, -4, 3, FLOOR + 5, 124, Blocks.STONE_BRICKS);
        fill(w, -2, FLOOR, -3, 2, FLOOR + 4, 123, Blocks.AIR);
        for (int z = 6; z < 122; z += 12) {
            set(w, 0, FLOOR + 5, z, Blocks.GLOWSTONE);
        }
        for (int z = 20; z < 120; z += 17) { // сломанные участки стены
            set(w, 2, FLOOR + 1, z, Blocks.CRACKED_STONE_BRICKS);
            set(w, -2, FLOOR + 2, z + 3, Blocks.CRACKED_STONE_BRICKS);
        }
    }

    // 5. Красная пустошь: множество перевёрнутых крестов
    private static void redWasteland(ServerWorld w, Random r) {
        for (int i = 0; i < 14; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int d = 18 + r.nextInt(28);
            int x = (int) (Math.cos(ang) * d), z = (int) (Math.sin(ang) * d);
            bigCross(w, x, z, 7, 2, 2, r.nextBoolean());
        }
        for (int i = 0; i < 40; i++) {
            int x = r.nextInt(81) - 40, z = r.nextInt(81) - 40;
            if (Math.abs(x) < 3 && Math.abs(z) < 3) continue;
            fill(w, x, FLOOR, z, x + r.nextInt(2), FLOOR + r.nextInt(2), z + r.nextInt(2), Blocks.REDSTONE_BLOCK);
        }
    }

    private static void bigCross(ServerWorld w, int x, int z, int height, int barY, int barHalf, boolean axisX) {
        fill(w, x, FLOOR, z, x, FLOOR + height - 1, z, Blocks.NETHERRACK);
        for (int d = -barHalf; d <= barHalf; d++) {
            if (axisX) set(w, x + d, FLOOR + barY, z, Blocks.NETHERRACK);
            else set(w, x, FLOOR + barY, z + d, Blocks.NETHERRACK);
        }
        set(w, x, FLOOR + barY, z, Blocks.CRYING_OBSIDIAN);
    }

    // 6. Парящие платформы над пустотой
    private static void platforms(ServerWorld w, Random r) {
        for (int i = 0; i < 10; i++) {
            int cx = (i % 3 - 1) * 4 * (i == 0 ? 0 : 1), cz = i * 10;
            int rad = i == 0 ? 6 : 4 + r.nextInt(2);
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++)
                    if (dx * dx + dz * dz <= rad * rad)
                        set(w, cx + dx, GROUND, cz + dz, Blocks.OBSIDIAN);
            set(w, cx, GROUND, cz, Blocks.CRYING_OBSIDIAN);
        }
    }

    // 7. Пиксельная пустыня: монолиты из цветного бетона
    private static void pixelDesert(ServerWorld w, Random r) {
        for (int i = 0; i < 28; i++) {
            int x = r.nextInt(81) - 40, z = r.nextInt(81) - 40;
            if (Math.abs(x) < 4 && Math.abs(z) < 4) continue;
            if (r.nextBoolean()) fill(w, x, FLOOR, z, x + 2, FLOOR + 2, z + 2, color(r));
            else fill(w, x, FLOOR, z, x, FLOOR + 3 + r.nextInt(9), z, color(r));
        }
        for (int i = 0; i < 30; i++) {
            int x = r.nextInt(81) - 40, z = r.nextInt(81) - 40;
            fill(w, x, GROUND, z, x + 2, GROUND, z + 2, color(r));
        }
    }

    // 8. Бесконечная библиотека: зал 41x41 с рядами полок
    private static void library(ServerWorld w) {
        fill(w, -20, GROUND, -20, 20, GROUND, 20, Blocks.DARK_OAK_PLANKS);
        fill(w, -20, FLOOR, -20, 20, FLOOR + 10, 20, Blocks.DARK_OAK_PLANKS);
        fill(w, -19, FLOOR, -19, 19, FLOOR + 9, 19, Blocks.AIR);
        for (int z : new int[]{-12, -6, 6, 12}) {
            for (int x = -16; x <= 16; x++) {
                if (Math.abs(x) <= 1) continue;
                fill(w, x, FLOOR, z, x, FLOOR + 4, z, Blocks.BOOKSHELF);
            }
        }
        for (int x = -16; x <= 16; x += 8)
            for (int z = -16; z <= 16; z += 8)
                set(w, x, FLOOR + 10, z, Blocks.SEA_LANTERN);
    }

    // 9. Чёрный сад: мёртвые деревья с плачущим обсидианом
    private static void blackGarden(ServerWorld w, Random r) {
        for (int i = 0; i < 34; i++) {
            int x = r.nextInt(81) - 40, z = r.nextInt(81) - 40;
            if (Math.abs(x) < 4 && Math.abs(z) < 4) continue;
            int h = 5 + r.nextInt(5);
            fill(w, x, FLOOR, z, x, FLOOR + h - 1, z, Blocks.DARK_OAK_LOG);
            set(w, x + 1, FLOOR + h - 3, z, Blocks.DARK_OAK_LOG);
            set(w, x - 1, FLOOR + h - 2, z, Blocks.DARK_OAK_LOG);
            set(w, x, FLOOR + h - 4, z + 1, Blocks.DARK_OAK_LOG);
            set(w, x, FLOOR + h, z, Blocks.CRYING_OBSIDIAN);
        }
    }

    // 10. Ядро: круглая арена, колонны и огромный крест
    private static void core(ServerWorld w) {
        for (int dx = -30; dx <= 30; dx++)
            for (int dz = -30; dz <= 30; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 <= 900) set(w, dx, GROUND, dz, d2 >= 784 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN);
            }
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4;
            int x = (int) Math.round(Math.cos(ang) * 22), z = (int) Math.round(Math.sin(ang) * 22);
            fill(w, x, FLOOR, z, x, FLOOR + 11, z, Blocks.OBSIDIAN);
            set(w, x, FLOOR + 12, z, Blocks.CRYING_OBSIDIAN);
        }
        bigCross(w, 0, 10, 15, 4, 4, true);
        fill(w, -1, FLOOR + 15, 10, 1, FLOOR + 15, 10, Blocks.REDSTONE_BLOCK);
    }

    // ------------------------------------------------------------------ население

    public static void populate(ServerWorld lw, int idx, ServerPlayerEntity p, long visitTicks) {
        Locations.Loc loc = Locations.get(idx);
        for (int i = 0; i < loc.minions(); i++) {
            BlockPos s = GlitchDirector.findSpotAround(lw, new BlockPos(0, FLOOR, 0), 8, 34);
            if (s == null) continue;
            GlitchMinionEntity m = GlitchMod.MINION.create(lw);
            if (m == null) continue;
            m.refreshPositionAndAngles(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, lw.random.nextFloat() * 360f, 0f);
            lw.spawnEntity(m);
        }
        if (loc.glitch() != null) {
            long delay = loc.glitch() == com.ruslan.glitch.entity.GlitchEntity.Mode.WATCH ? 40 : 100 + lw.random.nextInt(100);
            Dreams.scheduleGlitch(p, lw, loc.glitch(), delay, (int) visitTicks + 200);
        }
    }
}
