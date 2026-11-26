package com.ruslan.glitch;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.ruslan.glitch.entity.GlitchEntity;
import com.ruslan.glitch.entity.GlitchMinionEntity;
import com.ruslan.glitch.block.ModBlocks;
import com.ruslan.glitch.world.ModFeatures;
import com.ruslan.glitch.item.ModItems;
import com.ruslan.glitch.network.ScarePayload;
import com.ruslan.glitch.world.Dreams;
import com.ruslan.glitch.world.GlitchDirector;
import com.ruslan.glitch.world.GlitchState;
import com.ruslan.glitch.world.Locations;
import com.ruslan.glitch.world.Structures;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class GlitchMod implements ModInitializer {
    public static final String MOD_ID = "glitchmod";

    public static final EntityType<GlitchEntity> GLITCH = Registry.register(
            Registries.ENTITY_TYPE,
            id("glitch"),
            EntityType.Builder.create(GlitchEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.95f)
                    .makeFireImmune()
                    .build("glitch"));

    public static final EntityType<GlitchMinionEntity> MINION = Registry.register(
            Registries.ENTITY_TYPE,
            id("glitch_minion"),
            EntityType.Builder.create(GlitchMinionEntity::new, SpawnGroup.MONSTER)
                    .dimensions(0.6f, 1.95f)
                    .build("glitch_minion"));

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(ScarePayload.ID, ScarePayload.CODEC);
        FabricDefaultAttributeRegistry.register(GLITCH, GlitchEntity.createGlitchAttributes());
        FabricDefaultAttributeRegistry.register(MINION, GlitchMinionEntity.createMinionAttributes());
        ModBlocks.register();
        ModFeatures.register();
        ModItems.register();
        Dreams.register();

        // /glitch structure <obelisk|ruin|arch|pit|ring|tower|shard|patch|cairn|fragment|stump|scar>: ставит постройку перед тобой
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var node = CommandManager.literal("structure");
            for (String n : Structures.ALL_NAMES) {
                node.then(CommandManager.literal(n).executes(ctx -> {
                    boolean ok = Structures.placeInFront(ctx.getSource().getPlayerOrThrow(), n);
                    ctx.getSource().sendFeedback(() -> Text.literal(ok ? "Built: " + n : "No room here (chunks not loaded?)"), false);
                    return 1;
                }));
            }
            dispatcher.register(CommandManager.literal("glitch").requires(s -> s.hasPermissionLevel(2)).then(node));
        });

        ServerTickEvents.END_WORLD_TICK.register(GlitchDirector::tick);

        // A world destroyed by Glitch can no longer be opened.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (GlitchState.get(server).destroyed) {
                handler.disconnect(Text.literal("§4This world was destroyed by Glitch.\n§7You can never enter it again."));
            }
        });

        // Test commands (op needed): /glitch stage N | spawn [hunt|strike|watch] | cross | dream N | banish | status | reset
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("glitch")
                        .requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.literal("stage")
                                .then(CommandManager.argument("n", IntegerArgumentType.integer(0, 5))
                                        .executes(ctx -> {
                                            int n = IntegerArgumentType.getInteger(ctx, "n");
                                            GlitchState.get(ctx.getSource().getServer()).jumpToStage(ctx.getSource().getServer(), n);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Glitch stage: " + n), false);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("spawn")
                                .executes(ctx -> spawn(ctx.getSource().getPlayerOrThrow(), GlitchEntity.Mode.HUNT))
                                .then(CommandManager.literal("hunt").executes(ctx -> spawn(ctx.getSource().getPlayerOrThrow(), GlitchEntity.Mode.HUNT)))
                                .then(CommandManager.literal("strike").executes(ctx -> spawn(ctx.getSource().getPlayerOrThrow(), GlitchEntity.Mode.STRIKE)))
                                .then(CommandManager.literal("watch").executes(ctx -> spawn(ctx.getSource().getPlayerOrThrow(), GlitchEntity.Mode.WATCH))))
                        .then(CommandManager.literal("cross").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            ServerWorld world = (ServerWorld) p.getWorld();
                            GlitchState st = GlitchState.get(ctx.getSource().getServer());
                            boolean ok = GlitchDirector.spawnCrossNear(world, st, p, world.getTime());
                            ctx.getSource().sendFeedback(() -> Text.literal(ok ? "Cross created" : "No room found"), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("dream")
                                .then(CommandManager.argument("n", IntegerArgumentType.integer(1, Locations.COUNT))
                                        .executes(ctx -> {
                                            int n = IntegerArgumentType.getInteger(ctx, "n");
                                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                            var server = ctx.getSource().getServer();
                                            boolean ok = Dreams.enter(server, p, n - 1, p.getBlockPos().down(), server.getOverworld().getTime());
                                            if (!ok) ctx.getSource().sendFeedback(() -> Text.literal("That world does not exist. Create a NEW world with the mod."), false);
                                            return 1;
                                        })))
                        .then(CommandManager.literal("banish").executes(ctx -> {
                            GlitchDirector.banish(ctx.getSource().getServer());
                            return 1;
                        }))
                        .then(CommandManager.literal("status").executes(ctx -> {
                            GlitchState st = GlitchState.get(ctx.getSource().getServer());
                            String msg = "Stage " + st.stage + ", anchors " + st.ritualProgress + "/" + GlitchState.ANCHORS_NEEDED
                                    + ", crosses in world " + st.crosses.size() + ", dreams visited " + st.visited + "/" + Locations.COUNT
                                    + ", banished: " + st.banished;
                            ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("reset").executes(ctx -> {
                            GlitchState.get(ctx.getSource().getServer()).reset();
                            ctx.getSource().sendFeedback(() -> Text.literal("Glitch reset."), false);
                            return 1;
                        }))));
    }

    private static int spawn(ServerPlayerEntity p, GlitchEntity.Mode mode) {
        ServerWorld world = (ServerWorld) p.getWorld();
        int dist = mode == GlitchEntity.Mode.WATCH ? 28 : 8;
        var spot = GlitchDirector.findSpotInFront(world, p, dist);
        if (spot == null) return 0;
        GlitchDirector.spawnGlitch(world, p, spot, mode, 20 * 45);
        if (mode != GlitchEntity.Mode.WATCH) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new ScarePayload(40, 0));
        }
        return 1;
    }
}
