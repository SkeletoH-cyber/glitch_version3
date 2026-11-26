package com.ruslan.glitch.world;

import com.ruslan.glitch.GlitchMod;
import com.ruslan.glitch.entity.GlitchEntity;
import com.ruslan.glitch.entity.GlitchMinionEntity;
import com.ruslan.glitch.network.ScarePayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Сны": после сна игрока затягивает в один из 10 миров Глича.
 * Через пару минут игрок просыпается в своей кровати. Каждый мир посещается один раз.
 */
public final class Dreams {
    private record Visit(long returnAt, BlockPos returnPos) {}

    private record Delayed(UUID player, ServerWorld world, GlitchEntity.Mode mode, long at, int life) {}

    /** true: "смерть" в мире Глича не настоящая: игрок просыпается дома, вещи и опыт целы. false: обычная смерть. */
    public static final boolean DREAM_DEATH_IS_SAFE = true;

    private static final Map<UUID, Long> wakeQueue = new HashMap<>();
    private static final Map<UUID, Long> pendingSleep = new HashMap<>();
    private static final Map<UUID, BlockPos> sleepPos = new HashMap<>();
    private static final Map<UUID, Visit> visits = new HashMap<>();
    private static final List<Delayed> delayed = new ArrayList<>();

    private Dreams() {}

    public static boolean isGlitchWorld(World w) {
        return w.getRegistryKey().getValue().getNamespace().equals(GlitchMod.MOD_ID);
    }

    public static void register() {
        EntitySleepEvents.START_SLEEPING.register(Dreams::onStartSleep);
        EntitySleepEvents.ALLOW_SLEEPING.register(Dreams::allowSleep);
        ServerTickEvents.END_SERVER_TICK.register(Dreams::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register(Dreams::afterDeath);
        ServerLivingEntityEvents.ALLOW_DEATH.register(Dreams::allowDeath);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            if (isGlitchWorld(p.getWorld()) && !visits.containsKey(p.getUuid())) {
                // игрок вышел из игры, пока был в мире Глича: вернём его домой
                visits.put(p.getUuid(), new Visit(server.getOverworld().getTime() + 100, null));
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            wakeQueue.clear();
            pendingSleep.clear();
            sleepPos.clear();
            visits.clear();
            delayed.clear();
            GlitchFx.clear();
            ChunkEraser.clear();
        });
    }

    // ------------------------------------------------------------------ сон

    private static void onStartSleep(LivingEntity entity, BlockPos pos) {
        if (!(entity instanceof ServerPlayerEntity p)) return;
        ServerWorld w = (ServerWorld) p.getWorld();
        if (w.getRegistryKey() != World.OVERWORLD) return;
        GlitchState st = GlitchState.get(w.getServer());
        if (st.banished || st.destroyed || st.stage < 1 || st.stage >= 5 || st.visited >= Locations.COUNT) return;
        long day = w.getTimeOfDay() / GlitchState.DAY;
        if (day <= st.lastVisitDay) return; // не чаще одного раза за игровой день
        pendingSleep.put(p.getUuid(), w.getTime() + 90);
        sleepPos.put(p.getUuid(), pos);
    }

    private static PlayerEntity.SleepFailureReason allowSleep(PlayerEntity player, BlockPos pos) {
        if (isGlitchWorld(player.getWorld())) {
            player.sendMessage(Text.literal("§8You can't sleep here."), true);
            return PlayerEntity.SleepFailureReason.OTHER_PROBLEM;
        }
        return null;
    }

    // ------------------------------------------------------------------ вход и выход

    private static void startNaturalVisit(MinecraftServer server, ServerPlayerEntity p, BlockPos bed, long now) {
        GlitchState st = GlitchState.get(server);
        ServerWorld ow = server.getOverworld();
        if (st.visited >= Locations.COUNT) return;
        long day = ow.getTimeOfDay() / GlitchState.DAY;
        if (enter(server, p, st.visited, bed, now)) {
            st.visited++;
            st.lastVisitDay = day;
            st.markDirty();
        } else {
            st.lastVisitDay = day;
            st.markDirty();
            p.sendMessage(Text.literal("§8Something tried to pull you in, but there is nowhere to go. Create a NEW world with this mod."), true);
        }
    }

    /** Телепортирует игрока в мир номер idx (0..9). Возвращает false, если такого измерения нет. */
    public static boolean enter(MinecraftServer server, ServerPlayerEntity p, int idx, BlockPos ret, long now) {
        Locations.Loc loc = Locations.get(idx);
        ServerWorld lw = server.getWorld(Locations.worldKey(idx));
        if (lw == null) return false;
        GlitchState st = GlitchState.get(server);
        if ((st.built & (1 << idx)) == 0) {
            LocationBuilder.build(lw, idx);
            st.built |= (1 << idx);
            st.markDirty();
        }
        if (p.isSleeping()) p.wakeUp(false, true);
        Vec3d sp = loc.spawn();
        p.teleport(lw, sp.x, sp.y, sp.z, 0f, 0f);
        GlitchDirector.title(p, "§k#§r §5" + loc.name() + " §r§k#", "§7" + loc.subtitle());
        ServerPlayNetworking.send(p, new ScarePayload(40, 1));
        long visitTicks = (loc.seconds() + lw.random.nextInt(181)) * 20L; // 5-10 минут
        LocationBuilder.populate(lw, idx, p, visitTicks);
        visits.put(p.getUuid(), new Visit(now + visitTicks, ret));
        return true;
    }

    private static void leave(MinecraftServer server, ServerPlayerEntity p, Visit v) {
        leave(server, p, v, null);
    }

    private static void leave(MinecraftServer server, ServerPlayerEntity p, Visit v, String message) {
        ServerWorld ow = server.getOverworld();
        if (p.getWorld() instanceof ServerWorld from && isGlitchWorld(from)) {
            for (GlitchEntity g : from.getEntitiesByType(TypeFilter.instanceOf(GlitchEntity.class), e -> true)) {
                g.discard();
            }
        }
        BlockPos r = (v != null && v.returnPos() != null) ? v.returnPos().up() : ow.getSpawnPos();
        p.teleport(ow, r.getX() + 0.5, r.getY(), r.getZ() + 0.5, p.getYaw(), p.getPitch());
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0));
        p.sendMessage(Text.literal(message != null ? message : "§7You wake up in a cold sweat. Was it a dream?"), true);
    }

    public static void scheduleGlitch(ServerPlayerEntity p, ServerWorld lw, GlitchEntity.Mode mode, long delayTicks, int life) {
        delayed.add(new Delayed(p.getUuid(), lw, mode, lw.getServer().getOverworld().getTime() + delayTicks, life));
    }

    private static void placeGlitch(ServerWorld lw, ServerPlayerEntity p, GlitchEntity.Mode mode, int life) {
        BlockPos spot;
        if (mode == GlitchEntity.Mode.WATCH) {
            spot = GlitchDirector.findSpotInFront(lw, p, 28);
            if (spot == null) spot = GlitchDirector.findSpotAround(lw, p.getBlockPos(), 18, 30);
        } else {
            spot = GlitchDirector.findSpotInFront(lw, p, 14);
            if (spot == null) spot = GlitchDirector.findSpotAround(lw, p.getBlockPos(), 8, 16);
        }
        if (spot == null) spot = GlitchDirector.findSpotAround(lw, p.getBlockPos(), 4, 10);
        if (spot == null) return;
        GlitchDirector.spawnGlitch(lw, p, spot, mode, life);
        if (mode != GlitchEntity.Mode.WATCH) {
            ServerPlayNetworking.send(p, new ScarePayload(25 + lw.random.nextInt(25), 0));
        }
    }

    // ------------------------------------------------------------------ тик

    private static void tick(MinecraftServer server) {
        ServerWorld ow = server.getOverworld();
        long now = ow.getTime();
        GlitchFx.tick();

        List<UUID> sleepDue = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : pendingSleep.entrySet()) {
            if (now >= e.getValue()) sleepDue.add(e.getKey());
        }
        for (UUID id : sleepDue) {
            pendingSleep.remove(id);
            BlockPos bed = sleepPos.remove(id);
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null && p.isSleeping()) startNaturalVisit(server, p, bed, now);
        }

        List<UUID> returnDue = new ArrayList<>();
        for (Map.Entry<UUID, Visit> e : visits.entrySet()) {
            if (now >= e.getValue().returnAt()) returnDue.add(e.getKey());
        }
        for (UUID id : returnDue) {
            Visit v = visits.remove(id);
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null && isGlitchWorld(p.getWorld())) leave(server, p, v);
        }

        List<UUID> wakeDue = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : wakeQueue.entrySet()) {
            if (now >= e.getValue()) wakeDue.add(e.getKey());
        }
        for (UUID id : wakeDue) {
            wakeQueue.remove(id);
            Visit v = visits.remove(id);
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
            if (p != null && isGlitchWorld(p.getWorld())) {
                leave(server, p, v, "§7You wake up in your bed, shaken. It felt real.");
            }
            GlitchDirector.vanishAllGlitches(server);
        }

        Iterator<Delayed> di = delayed.iterator();
        List<Delayed> ready = new ArrayList<>();
        while (di.hasNext()) {
            Delayed d = di.next();
            if (now >= d.at()) {
                ready.add(d);
                di.remove();
            }
        }
        for (Delayed d : ready) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(d.player());
            if (p != null && p.getWorld() == d.world()) placeGlitch(d.world(), p, d.mode(), d.life());
        }

        if (now % 20 == 0) perSecond(server);
    }

    private static void perSecond(MinecraftServer server) {
        GlitchState st = GlitchState.get(server);
        long nowTicks = server.getOverworld().getTime();
        for (ServerPlayerEntity p : new ArrayList<>(server.getPlayerManager().getPlayerList())) {
            if (!isGlitchWorld(p.getWorld())) continue;
            ServerWorld w = (ServerWorld) p.getWorld();
            if (p.getY() < 8) { // упал в пустоту: просыпается дома
                Visit v = visits.remove(p.getUuid());
                leave(server, p, v);
                continue;
            }
            Visit vis = visits.get(p.getUuid());
            if (vis != null) {
                long remaining = vis.returnAt() - nowTicks;
                if (remaining > 580 && remaining <= 600) {
                    p.sendMessage(Text.literal("§8The dream is fading..."), true);
                } else if (remaining > 80 && remaining <= 100) {
                    p.sendMessage(Text.literal("§8You feel yourself waking up..."), true);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0));
                }
            }
            Random r = w.random;
            GlitchFx.spawn(w, p, 6);
            if (r.nextInt(25) == 0) GlitchDirector.say(server, GlitchDirector.lineFor(Math.max(2, st.stage), r));
            if (r.nextInt(18) == 0) ServerPlayNetworking.send(p, new ScarePayload(8 + r.nextInt(14), 1));
            if (r.nextInt(70) == 0) ServerPlayNetworking.send(p, new ScarePayload(20 + r.nextInt(30), 0));
        }
    }

    // ------------------------------------------------------------------ смерть игрока

    /** Смертельный урон в мире Глича отменяется: игрок "умирает во сне" и просыпается в обычном мире. */
    private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity p)) return true;
        if (!DREAM_DEATH_IS_SAFE || !isGlitchWorld(p.getWorld())) return true;
        MinecraftServer server = p.getServer();
        if (server == null) return true;
        p.setHealth(6.0f);
        if (wakeQueue.containsKey(p.getUuid())) return false; // уже просыпается
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 4));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0));
        String why = source.getAttacker() instanceof GlitchEntity ? "§4Glitch caught you."
                : source.getAttacker() instanceof GlitchMinionEntity ? "§4The minions caught you." : "§4You black out.";
        GlitchDirector.title(p, why, "§7...and you wake up.");
        ServerPlayNetworking.send(p, new ScarePayload(35, 0));
        wakeQueue.put(p.getUuid(), server.getOverworld().getTime() + 30);
        return false;
    }

    private static void afterDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayerEntity p)) return;
        wakeQueue.remove(p.getUuid());
        visits.remove(p.getUuid());
        pendingSleep.remove(p.getUuid());
        sleepPos.remove(p.getUuid());
        MinecraftServer server = p.getServer();
        if (server != null) GlitchDirector.vanishAllGlitches(server);
    }
}
