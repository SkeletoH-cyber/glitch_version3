package com.ruslan.glitch.world;

import com.ruslan.glitch.GlitchMod;
import com.ruslan.glitch.entity.GlitchEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Таблица из 10 миров Глича. seconds = минимальная длительность визита (к ней добавляется случайные 0-3 минуты). Измерения лежат в data/glitchmod/dimension/loc_01..loc_10.json. */
public final class Locations {
    /** glitch == null: Глича нет, только приспешники. minions == 0: только сам Глич. */
    public record Loc(String id, String name, String subtitle, int minions, GlitchEntity.Mode glitch,
                      Vec3d spawn, int seconds) {}

    private static Vec3d at(double x, double z) {
        return new Vec3d(x, 63, z);
    }

    public static final Loc[] ALL = {
            new Loc("loc_01", "The Static Plains", "Everything flickers here.", 8, null, at(0.5, 0.5), 300),
            new Loc("loc_02", "The Mirror Hall", "Someone is standing very far away.", 0, GlitchEntity.Mode.WATCH, at(0.5, 0.5), 300),
            new Loc("loc_03", "The Hollow Village", "They look like people.", 6, null, at(0.5, 0.5), 300),
            new Loc("loc_04", "The Endless Corridor", "Don't stop walking.", 0, GlitchEntity.Mode.HUNT, at(0.5, 0.5), 300),
            new Loc("loc_05", "The Red Wasteland", "The crosses are watching.", 10, null, at(0.5, 0.5), 300),
            new Loc("loc_06", "The Floating Platforms", "Don't look down.", 0, GlitchEntity.Mode.WATCH, at(0.5, 0.5), 300),
            new Loc("loc_07", "The Pixel Desert", "It will only need one hit.", 0, GlitchEntity.Mode.STRIKE, at(0.5, 0.5), 300),
            new Loc("loc_08", "The Endless Library", "Quiet. Please.", 6, GlitchEntity.Mode.WATCH, at(0.5, 0.5), 300),
            new Loc("loc_09", "The Black Garden", "Nothing grows here. Everything stares.", 12, GlitchEntity.Mode.STRIKE, at(0.5, 0.5), 300),
            new Loc("loc_10", "The Core", "This is where it lives.", 0, GlitchEntity.Mode.HUNT, at(0.5, -24.5), 420)
    };

    public static final int COUNT = ALL.length;

    private Locations() {}

    public static Loc get(int i) {
        return ALL[i];
    }

    public static RegistryKey<World> worldKey(int i) {
        return RegistryKey.of(RegistryKeys.WORLD, GlitchMod.id(ALL[i].id()));
    }
}
