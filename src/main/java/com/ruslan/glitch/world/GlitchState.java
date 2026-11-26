package com.ruslan.glitch.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.List;

/**
 * Сохраняемое состояние мира (файл data/glitchmod_state.dat).
 * Стадии: 0 тишина, 1 первые странности, 2 исчезают блоки, 3 Глич появляется,
 * 4 исчезают чанки, 5 финал: мир рушится.
 */
public class GlitchState extends PersistentState {
    private static final String KEY = "glitchmod_state";

    public static final long DAY = 24000L;
    /** Сколько якорей нужно поставить на кресты, чтобы изгнать Глича. */
    public static final int ANCHORS_NEEDED = 4;
    /** Сколько всего крестов Глич создаёт за игру (с запасом). */
    public static final int MAX_CROSSES = 8;
    /** Когда (в тиках от старта) начинается каждая стадия. Меняй под себя. */
    public static final long[] STAGE_START = {0, DAY / 2, 2 * DAY, 5 * DAY, 8 * DAY, 12 * DAY};

    public long startTick = -1;
    public int stage = 0;
    public boolean banished = false;
    public boolean destroyed = false;
    public int ritualProgress = 0;
    public long banishAtTick = -1;
    public long collapseStart = -1;
    public long lastEventTick = 0;
    public long lastScareTick = -100000;
    public long lastGlitchTick = 0;
    public long lastCrossTick = 0;
    public int crossesSpawned = 0;
    /** Сколько Glitch-миров уже посещено (0..10), в каком дне был последний визит и какие миры уже построены. */
    public int visited = 0;
    public int structuresSpawned = 0;
    public long lastStructureTick = 0;
    public long lastVisitDay = -1;
    public int built = 0;
    public final List<Long> crosses = new ArrayList<>();

    private static final PersistentState.Type<GlitchState> TYPE =
            new PersistentState.Type<>(GlitchState::new, GlitchState::fromNbt, null);

    public static GlitchState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    public static int stageFor(long elapsed) {
        for (int i = STAGE_START.length - 1; i >= 0; i--) {
            if (elapsed >= STAGE_START[i]) return i;
        }
        return 0;
    }

    public static GlitchState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        GlitchState s = new GlitchState();
        s.startTick = nbt.getLong("startTick");
        s.stage = nbt.getInt("stage");
        s.banished = nbt.getBoolean("banished");
        s.destroyed = nbt.getBoolean("destroyed");
        s.ritualProgress = nbt.getInt("ritualProgress");
        s.banishAtTick = nbt.getLong("banishAtTick");
        s.collapseStart = nbt.getLong("collapseStart");
        s.lastEventTick = nbt.getLong("lastEventTick");
        s.lastScareTick = nbt.getLong("lastScareTick");
        s.lastGlitchTick = nbt.getLong("lastGlitchTick");
        s.lastCrossTick = nbt.getLong("lastCrossTick");
        s.crossesSpawned = nbt.getInt("crossesSpawned");
        s.visited = nbt.getInt("visited");
        s.structuresSpawned = nbt.getInt("structuresSpawned");
        s.lastStructureTick = nbt.getLong("lastStructureTick");
        s.lastVisitDay = nbt.contains("lastVisitDay") ? nbt.getLong("lastVisitDay") : -1;
        s.built = nbt.getInt("built");
        for (long l : nbt.getLongArray("crosses")) s.crosses.add(l);
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putLong("startTick", startTick);
        nbt.putInt("stage", stage);
        nbt.putBoolean("banished", banished);
        nbt.putBoolean("destroyed", destroyed);
        nbt.putInt("ritualProgress", ritualProgress);
        nbt.putLong("banishAtTick", banishAtTick);
        nbt.putLong("collapseStart", collapseStart);
        nbt.putLong("lastEventTick", lastEventTick);
        nbt.putLong("lastScareTick", lastScareTick);
        nbt.putLong("lastGlitchTick", lastGlitchTick);
        nbt.putLong("lastCrossTick", lastCrossTick);
        nbt.putInt("crossesSpawned", crossesSpawned);
        nbt.putInt("visited", visited);
        nbt.putInt("structuresSpawned", structuresSpawned);
        nbt.putLong("lastStructureTick", lastStructureTick);
        nbt.putLong("lastVisitDay", lastVisitDay);
        nbt.putInt("built", built);
        long[] arr = new long[crosses.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = crosses.get(i);
        nbt.putLongArray("crosses", arr);
        return nbt;
    }

    public void addCross(BlockPos center) {
        crosses.add(center.asLong());
        markDirty();
    }

    public boolean removeCross(BlockPos center) {
        boolean r = crosses.remove(Long.valueOf(center.asLong()));
        if (r) markDirty();
        return r;
    }

    public BlockPos nearestCross(BlockPos from) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (long l : crosses) {
            BlockPos p = BlockPos.fromLong(l);
            double d = p.getSquaredDistance(from);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    /** Для команды /glitch stage N: сдвигает "часы" так, будто стадия N только началась. */
    public void jumpToStage(MinecraftServer server, int n) {
        long day = server.getOverworld().getTimeOfDay();
        startTick = day - STAGE_START[n];
        stage = n;
        if (n < 5) collapseStart = -1;
        markDirty();
    }

    public void reset() {
        startTick = -1; stage = 0; banished = false; destroyed = false;
        ritualProgress = 0; banishAtTick = -1; collapseStart = -1;
        lastEventTick = 0; lastScareTick = -100000; lastGlitchTick = 0;
        lastCrossTick = 0; crossesSpawned = 0; crosses.clear();
        visited = 0; lastVisitDay = -1; built = 0; structuresSpawned = 0; lastStructureTick = 0;
        markDirty();
    }
}
