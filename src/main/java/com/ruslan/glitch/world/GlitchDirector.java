package com.ruslan.glitch.world;

import com.ruslan.glitch.GlitchMod;
import com.ruslan.glitch.block.ModBlocks;
import com.ruslan.glitch.entity.GlitchEntity;
import com.ruslan.glitch.network.ScarePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** "Режиссёр": по таймеру мира запускает события Глича. Вызывается каждый тик из GlitchMod. */
public final class GlitchDirector {
    private GlitchDirector() {}

    private static final String[] STAGE_TEXT = {
            null,
            "§8§k||§r §7Something feels different...§r §8§k||",
            "§8The world trembles. Blocks vanish.",
            "§4§k|||§r §cGlitch has awakened§r §4§k|||",
            "§4Chunks are crumbling...",
            "§4§lTHE WORLD IS COLLAPSING"
    };

    private static final String[] MESSAGES = {
            "§8§k||||§r §7Are you still there?§r §8§k||||",
            "§4§kXXX§r §cThis world is not real§r §4§kXXX",
            "§8Glitch is watching you",
            "§7Error: block not found",
            "§c0x0000FFFF §8- reality fault",
            "§8§k|||§r §7Don't turn around§r §8§k|||"
    };

    private static final String[] LINES_EARLY = {"hi", "it's me", "can you hear me?", "hello?"};
    private static final String[] LINES_MID = {"i can see you", "i'm watching you", "don't look away",
            "nice base. shame if it vanished", "did you hear that?"};
    private static final String[] LINES_LATE = {"you can't leave", "this world is mine now", "i'm right behind you",
            "you can't stop me", "run", "i'm still here"};

    // ------------------------------------------------------------------ речь Глича

    public static String lineFor(int stage, Random r) {
        String[] a = stage <= 1 ? LINES_EARLY : stage <= 3 ? LINES_MID : LINES_LATE;
        return a[r.nextInt(a.length)];
    }

    /** Глич пишет в общий чат от своего имени. */
    public static void say(MinecraftServer server, String msg) {
        server.getPlayerManager().broadcast(Text.literal("<§cGlitch§r> " + msg), false);
    }

    // ------------------------------------------------------------------ главный тик

    public static void tick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD) return;
        MinecraftServer server = world.getServer();
        GlitchState st = GlitchState.get(server);
        if (st.banished || st.destroyed) return;
        List<ServerPlayerEntity> players = world.getPlayers();
        if (players.isEmpty()) return;

        long now = world.getTime();
        long day = world.getTimeOfDay();
        if (st.startTick < 0) {
            st.startTick = day;
            st.markDirty();
        }
        long elapsed = Math.max(0, day - st.startTick);

        int newStage = GlitchState.stageFor(elapsed);
        if (newStage != st.stage) {
            boolean up = newStage > st.stage;
            st.stage = newStage;
            st.markDirty();
            if (up) announceStage(server, players, newStage);
        }

        ChunkEraser.tick(world);

        if (st.ritualProgress >= GlitchState.ANCHORS_NEEDED) {
            ritual(world, st, players, now);
            return;
        }

        if (now % 20 != 0) return;
        secondly(world, st, players, now);
    }

    private static void secondly(ServerWorld world, GlitchState st, List<ServerPlayerEntity> players, long now) {
        Random r = world.random;
        ServerPlayerEntity p = players.get(r.nextInt(players.size()));
        int stage = st.stage;

        if (stage >= 5) {
            collapse(world, st, players, now);
            return;
        }
        if (stage < 1) return;

        if (st.crossesSpawned < GlitchState.MAX_CROSSES && now - st.lastCrossTick > 4000) {
            spawnCrossNear(world, st, p, now);
        }

        if (st.structuresSpawned < 14 && now - st.lastStructureTick > 5000 && r.nextInt(6) == 0) {
            spawnStructureNear(world, st, p, now);
        }

        // дрожь экрана и помехи: чем дальше от спавна, тем чаще
        double fromSpawn = Math.sqrt(p.getX() * p.getX() + p.getZ() * p.getZ());
        int wobble = stage == 1 ? 160 : stage == 2 ? 100 : 70;
        if (fromSpawn > 400) wobble /= 2;
        if (r.nextInt(wobble) == 0) {
            ServerPlayNetworking.send(p, new ScarePayload(30 + r.nextInt(50), r.nextInt(3) == 0 ? 5 : 4));
        }

        int period = switch (stage) { case 1 -> 100; case 2 -> 50; case 3 -> 35; default -> 22; };
        if (r.nextInt(period) == 0) ambientEvent(world, st, p);

        int speakPeriod = switch (stage) { case 1 -> 200; case 2 -> 120; case 3 -> 80; default -> 50; };
        if (r.nextInt(speakPeriod) == 0) say(world.getServer(), lineFor(stage, r));

        if (stage >= 3) {
            long cooldown = stage == 3 ? 7000 : 3500;
            if (now - st.lastGlitchTick > cooldown && r.nextInt(8) == 0) appear(world, st, p, now);
        }
        if (stage >= 4 && now - st.lastEventTick > 1800 && r.nextInt(10) == 0) {
            eraseNearbyChunk(world, st, p, now);
        }
    }

    // ------------------------------------------------------------------ события

    private static void announceStage(MinecraftServer server, List<ServerPlayerEntity> players, int stage) {
        if (stage >= STAGE_TEXT.length || STAGE_TEXT[stage] == null) return;
        for (ServerPlayerEntity p : players) {
            p.sendMessage(Text.literal(STAGE_TEXT[stage]), true);
            ServerPlayNetworking.send(p, new ScarePayload(15, 1));
        }
        if (stage == 2) {
            server.getPlayerManager().broadcast(Text.literal("Glitch joined the game").formatted(Formatting.YELLOW), false);
        }
    }

    private static void ambientEvent(ServerWorld world, GlitchState st, ServerPlayerEntity p) {
        Random r = world.random;
        int pool = st.stage >= 3 ? 9 : st.stage >= 2 ? 8 : 4;
        switch (r.nextInt(pool)) {
            case 0, 1 -> whisper(world, p);
            case 2 -> ServerPlayNetworking.send(p, new ScarePayload(10 + r.nextInt(10), 1));
            case 3 -> p.sendMessage(Text.literal(MESSAGES[r.nextInt(MESSAGES.length)]), true);
            case 4 -> vanishLights(world, p);
            case 5 -> vanishBlocks(world, p, st.stage >= 4 ? 4 : 2);
            case 6 -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0));
                whisper(world, p);
            }
            case 7 -> ServerPlayNetworking.send(p, new ScarePayload(3 + r.nextInt(3), 0)); // кадр-вспышка
            default -> ServerPlayNetworking.send(p, new ScarePayload(20 + r.nextInt(30), 0)); // скример
        }
    }

    private static void whisper(ServerWorld world, ServerPlayerEntity p) {
        SoundEvent[] sounds = {
                SoundEvents.ENTITY_ENDERMAN_STARE,
                SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                SoundEvents.BLOCK_PORTAL_AMBIENT,
                SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE,
                SoundEvents.ENTITY_WARDEN_NEARBY_CLOSER
        };
        Random r = world.random;
        BlockPos at = p.getBlockPos().add(r.nextInt(11) - 5, 0, r.nextInt(11) - 5);
        world.playSound(null, at, sounds[r.nextInt(sounds.length)], SoundCategory.AMBIENT,
                1.0f, 0.5f + r.nextFloat() * 0.4f);
    }

    private static boolean underPlayer(ServerPlayerEntity p, BlockPos pos) {
        return Math.abs(pos.getX() - p.getBlockX()) <= 1
                && Math.abs(pos.getZ() - p.getBlockZ()) <= 1
                && pos.getY() < p.getBlockY() && pos.getY() >= p.getBlockY() - 3;
    }

    private static boolean protectedBlock(BlockState s) {
        return s.isAir() || s.isOf(Blocks.BEDROCK) || s.isOf(Blocks.NETHERRACK) || ModBlocks.ALL.contains(s.getBlock())
                || s.isOf(Blocks.CRYING_OBSIDIAN) || s.isOf(Blocks.REDSTONE_TORCH);
    }

    private static void vanishBlocks(ServerWorld world, ServerPlayerEntity p, int radius) {
        Random r = world.random;
        BlockPos c = p.getBlockPos().add(r.nextInt(25) - 12, r.nextInt(5) - 1, r.nextInt(25) - 12);
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius + 1) continue;
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (underPlayer(p, m)) continue;
                    if (protectedBlock(world.getBlockState(m))) continue;
                    world.setBlockState(m, Blocks.AIR.getDefaultState(), ChunkEraser.FLAGS);
                }
            }
        }
        puff(world, c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
    }

    private static void vanishLights(ServerWorld world, ServerPlayerEntity p) {
        Random r = world.random;
        BlockPos.Mutable m = new BlockPos.Mutable();
        int removed = 0;
        for (int i = 0; i < 60 && removed < 2; i++) {
            m.set(p.getBlockX() + r.nextInt(21) - 10, p.getBlockY() + r.nextInt(9) - 4, p.getBlockZ() + r.nextInt(21) - 10);
            BlockState s = world.getBlockState(m);
            if (s.getLuminance() > 0 && s.getFluidState().isEmpty() && !protectedBlock(s)) {
                world.setBlockState(m, Blocks.AIR.getDefaultState(), ChunkEraser.FLAGS);
                removed++;
            }
        }
    }

    public static void puff(ServerWorld w, double x, double y, double z) {
        w.spawnParticles(ParticleTypes.PORTAL, x, y + 1, z, 40, 0.4, 0.8, 0.4, 0.6);
        w.spawnParticles(ParticleTypes.SMOKE, x, y + 1, z, 12, 0.3, 0.6, 0.3, 0.02);
    }

    // ------------------------------------------------------------------ кресты

    public static boolean spawnCrossNear(ServerWorld world, GlitchState st, ServerPlayerEntity p, long now) {
        Random r = world.random;
        for (int i = 0; i < 12; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int d = 35 + r.nextInt(35);
            int x = p.getBlockX() + (int) (Math.cos(ang) * d);
            int z = p.getBlockZ() + (int) (Math.sin(ang) * d);
            if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;
            int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockState below = world.getBlockState(new BlockPos(x, y - 1, z));
            if (below.isAir() || !below.getFluidState().isEmpty()) continue;
            buildCross(world, new BlockPos(x, y, z), r.nextBoolean());
            st.addCross(new BlockPos(x, y + 1, z));
            st.crossesSpawned++;
            st.lastCrossTick = now;
            st.markDirty();
            return true;
        }
        return false;
    }

    /** Странные постройки из блоков Глича появляются рядом с игроком (но вне его поля зрения не обязательно). */
    private static void spawnStructureNear(ServerWorld world, GlitchState st, ServerPlayerEntity p, long now) {
        Random r = world.random;
        for (int i = 0; i < 8; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int d = 45 + r.nextInt(40);
            int x = p.getBlockX() + (int) (Math.cos(ang) * d);
            int z = p.getBlockZ() + (int) (Math.sin(ang) * d);
            String name = Structures.NAMES[r.nextInt(Structures.NAMES.length)];
            if (Structures.placeAtSurface(world, x, z, name, r)) {
                st.structuresSpawned++;
                st.lastStructureTick = now;
                st.markDirty();
                return;
            }
        }
    }

    /** Перевёрнутый крест: балка 5 блоков, перекладина низко. На вершине ВСЕГДА красный (редстоуновый) факел. */
    private static void buildCross(ServerWorld world, BlockPos base, boolean axisX) {
        BlockState rack = Blocks.NETHERRACK.getDefaultState();
        for (int dy = 0; dy < 5; dy++) world.setBlockState(base.up(dy), rack, 3);
        BlockPos center = base.up(1);
        world.setBlockState(center, Blocks.CRYING_OBSIDIAN.getDefaultState(), 3);
        world.setBlockState(axisX ? center.east() : center.south(), rack, 3);
        world.setBlockState(axisX ? center.west() : center.north(), rack, 3);
        world.setBlockState(base.up(5),
                Blocks.REDSTONE_TORCH.getDefaultState().with(RedstoneTorchBlock.LIT, true), 3);
    }

    public static void eraseCross(ServerWorld world, BlockPos center) {
        for (int dy = -1; dy <= 4; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > 1) continue;
                    BlockPos p = center.add(dx, dy, dz);
                    BlockState s = world.getBlockState(p);
                    if (s.isOf(Blocks.NETHERRACK) || s.isOf(Blocks.CRYING_OBSIDIAN) || s.isOf(Blocks.REDSTONE_TORCH)) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
        }
        puff(world, center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
    }

    // ------------------------------------------------------------------ Глич

    private static void appear(ServerWorld world, GlitchState st, ServerPlayerEntity p, long now) {
        Random r = world.random;
        int roll = r.nextInt(100);
        GlitchEntity.Mode mode;
        if (st.stage == 3) {
            mode = roll < 45 ? GlitchEntity.Mode.WATCH : roll < 85 ? GlitchEntity.Mode.STRIKE : GlitchEntity.Mode.HUNT;
        } else {
            mode = roll < 20 ? GlitchEntity.Mode.WATCH : roll < 60 ? GlitchEntity.Mode.STRIKE : GlitchEntity.Mode.HUNT;
        }

        BlockPos spot;
        if (mode == GlitchEntity.Mode.WATCH) {
            spot = findSpotInFront(world, p, 28 + r.nextInt(8));
            if (spot == null) spot = findSpotAround(world, p.getBlockPos(), 20, 32);
        } else {
            spot = findSpotInFront(world, p, 7 + r.nextInt(3));
        }
        if (spot == null) return;

        int life = mode == GlitchEntity.Mode.WATCH ? 20 * 45 : 20 * (30 + r.nextInt(30));
        GlitchEntity g = spawnGlitch(world, p, spot, mode, life);
        if (g == null) return;
        st.lastGlitchTick = now;
        if (mode != GlitchEntity.Mode.WATCH && now - st.lastScareTick > 3000) {
            st.lastScareTick = now;
            ServerPlayNetworking.send(p, new ScarePayload(25 + r.nextInt(30), 0));
        }
        if (mode == GlitchEntity.Mode.WATCH) say(world.getServer(), "i'm watching you");
        else if (r.nextInt(3) == 0) say(world.getServer(), "hi");
        st.markDirty();
    }

    /** HUNT-Глич перед игроком (команды, ритуал, финал). */
    public static GlitchEntity spawnGlitchNear(ServerPlayerEntity player, int dist) {
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos spot = findSpotInFront(world, player, dist);
        if (spot == null) return null;
        return spawnGlitch(world, player, spot, GlitchEntity.Mode.HUNT, 20 * 30 + world.random.nextInt(20 * 30));
    }

    public static GlitchEntity spawnGlitch(ServerWorld world, ServerPlayerEntity player, BlockPos spot,
                                           GlitchEntity.Mode mode, int life) {
        GlitchEntity g = GlitchMod.GLITCH.create(world);
        if (g == null) return null;
        g.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
        g.lifeTicks = life;
        g.setMode(mode);
        if (mode != GlitchEntity.Mode.WATCH) g.setTarget(player);
        world.spawnEntity(g);
        g.lookAtEntity(player, 360f, 360f);
        puff(world, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        return g;
    }

    /** Когда убит игрок (или он ушёл), все Глич-сущности пропадают. */
    public static void vanishAllGlitches(MinecraftServer server) {
        for (ServerWorld w : server.getWorlds()) {
            for (GlitchEntity g : w.getEntitiesByType(TypeFilter.instanceOf(GlitchEntity.class), e -> true)) {
                puff(w, g.getX(), g.getY(), g.getZ());
                g.discard();
            }
        }
    }

    public static BlockPos findSpotInFront(ServerWorld world, ServerPlayerEntity p, int dist) {
        Random r = world.random;
        Vec3d look = p.getRotationVec(1.0f);
        double lx = look.x, lz = look.z;
        double len = Math.sqrt(lx * lx + lz * lz);
        if (len < 0.01) { lx = 1; lz = 0; len = 1; }
        lx /= len;
        lz /= len;
        BlockPos base = p.getBlockPos();
        for (int sign = 1; sign >= -1; sign -= 2) {
            for (int i = 0; i < 10; i++) {
                int d = Math.max(3, dist - i % 4);
                int x = base.getX() + (int) Math.round(lx * d * sign) + r.nextInt(3) - 1;
                int z = base.getZ() + (int) Math.round(lz * d * sign) + r.nextInt(3) - 1;
                BlockPos s = standableNear(world, x, base.getY(), z);
                if (s != null) return s;
            }
        }
        return null;
    }

    public static BlockPos findSpotAround(ServerWorld world, BlockPos center, int minD, int maxD) {
        Random r = world.random;
        for (int i = 0; i < 14; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int d = minD + r.nextInt(Math.max(1, maxD - minD + 1));
            int x = center.getX() + (int) Math.round(Math.cos(ang) * d);
            int z = center.getZ() + (int) Math.round(Math.sin(ang) * d);
            BlockPos s = standableNear(world, x, center.getY(), z);
            if (s != null) return s;
        }
        return null;
    }

    private static BlockPos standableNear(ServerWorld world, int x, int y, int z) {
        if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return null;
        int[] dys = {0, 1, -1, 2, -2, 3, -3};
        for (int dy : dys) {
            BlockPos p = new BlockPos(x, y + dy, z);
            if (world.getBlockState(p).isAir() && world.getBlockState(p.up()).isAir()
                    && world.getBlockState(p.down()).isSolidBlock(world, p.down())) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ чанки и финал

    private static void eraseNearbyChunk(ServerWorld world, GlitchState st, ServerPlayerEntity p, long now) {
        Random r = world.random;
        ChunkPos c = p.getChunkPos();
        for (int i = 0; i < 10; i++) {
            int dx = r.nextInt(9) - 4, dz = r.nextInt(9) - 4;
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            if (!world.getChunkManager().isChunkLoaded(c.x + dx, c.z + dz)) continue;
            ChunkEraser.enqueue(new ChunkPos(c.x + dx, c.z + dz));
            st.lastEventTick = now;
            st.markDirty();
            ServerPlayNetworking.send(p, new ScarePayload(20, 1));
            whisper(world, p);
            return;
        }
    }

    private static void seedChunks(ServerWorld world, List<ServerPlayerEntity> players, int radius) {
        List<ChunkPos> list = new ArrayList<>();
        for (ServerPlayerEntity p : players) {
            ChunkPos c = p.getChunkPos();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius) list.add(new ChunkPos(c.x + dx, c.z + dz));
                }
            }
        }
        list.sort(Comparator.comparingDouble((ChunkPos cp) -> -minDist2(cp, players)));
        for (ChunkPos cp : list) {
            if (world.getChunkManager().isChunkLoaded(cp.x, cp.z)) ChunkEraser.enqueue(cp);
        }
    }

    private static double minDist2(ChunkPos cp, List<ServerPlayerEntity> players) {
        double best = Double.MAX_VALUE;
        for (ServerPlayerEntity p : players) {
            ChunkPos c = p.getChunkPos();
            double dx = cp.x - c.x, dz = cp.z - c.z;
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best;
    }

    private static void collapse(ServerWorld world, GlitchState st, List<ServerPlayerEntity> players, long now) {
        if (st.collapseStart < 0) {
            st.collapseStart = now;
            st.markDirty();
            for (ServerPlayerEntity p : players) title(p, "§4§lTHE WORLD IS COLLAPSING", "§7Stop Glitch. Before it's too late.");
            say(world.getServer(), "you can't stop me");
        }
        long t = now - st.collapseStart;
        if (t % 100 == 0) {
            for (ServerPlayerEntity p : players) {
                ServerPlayNetworking.send(p, new ScarePayload(60, 2));
                whisper(world, p);
            }
        }
        if (t % 200 == 0) seedChunks(world, players, 7);
        if (t % 600 == 0) {
            for (ServerPlayerEntity p : players) spawnGlitchNear(p, 6);
        }
        boolean erased = t > 1200 && !ChunkEraser.isBusy();
        if (erased || t > 20L * 60 * 8) finishWorld(world.getServer(), st);
    }

    private static void finishWorld(MinecraftServer server, GlitchState st) {
        st.destroyed = true;
        st.markDirty();
        ChunkEraser.clear();
        try {
            server.save(false, true, true);
        } catch (Exception ignored) {
        }
        Text msg = Text.literal("§4The world crumbled. Glitch won.\n§7You can never enter it again.");
        for (ServerPlayerEntity p : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
            p.networkHandler.disconnect(msg);
        }
    }

    // ------------------------------------------------------------------ ритуал и изгнание

    private static void ritual(ServerWorld world, GlitchState st, List<ServerPlayerEntity> players, long now) {
        if (st.banishAtTick < 0) {
            st.banishAtTick = now + 200;
            st.markDirty();
            say(world.getServer(), "no");
            for (ServerPlayerEntity p : players) {
                title(p, "§5ANCHORS IN PLACE", "§7Glitch is resisting...");
                ServerPlayNetworking.send(p, new ScarePayload(200, 2));
                spawnGlitchNear(p, 7);
            }
            return;
        }
        if (now % 10 == 0) {
            for (ServerPlayerEntity p : players) puff(world, p.getX(), p.getY(), p.getZ());
        }
        if (now >= st.banishAtTick) banish(world.getServer());
    }

    /** Глич навсегда изгнан из мира. Вызывается ритуалом или командой /glitch banish. */
    public static void banish(MinecraftServer server) {
        GlitchState st = GlitchState.get(server);
        st.banished = true;
        st.markDirty();
        ChunkEraser.clear();
        vanishAllGlitches(server);
        for (ServerPlayerEntity p : server.getOverworld().getPlayers()) {
            ServerPlayNetworking.send(p, new ScarePayload(60, 3));
            title(p, "§aGLITCH BANISHED", "§7The world is stable again... for now.");
        }
        server.getPlayerManager().broadcast(Text.literal("Glitch left the game").formatted(Formatting.YELLOW), false);
    }

    public static void title(ServerPlayerEntity p, String title, String subtitle) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 70, 20));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subtitle)));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title)));
    }
}
