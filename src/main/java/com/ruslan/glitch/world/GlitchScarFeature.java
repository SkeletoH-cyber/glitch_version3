package com.ruslan.glitch.world;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Мелкие "шрамы" Глича, которые генерируются вместе с миром.
 * Возле спавна их нет совсем, дальше они встречаются всё чаще: мир будто уже заражён.
 */
public class GlitchScarFeature extends Feature<DefaultFeatureConfig> {
    public GlitchScarFeature() {
        super(DefaultFeatureConfig.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess w = context.getWorld();
        BlockPos o = context.getOrigin();
        Random r = context.getRandom();

        double dist = Math.sqrt((double) o.getX() * o.getX() + (double) o.getZ() * o.getZ());
        // шанс (в процентах) на каждую попытку генерации; одна попытка на чанк
        int chance = dist < 150 ? 0 : dist < 500 ? 3 : dist < 1000 ? 6 : 10;
        if (chance == 0 || r.nextInt(100) >= chance) return false;

        BlockState below = w.getBlockState(o.down());
        if (below.isAir() || !below.getFluidState().isEmpty()) return false; // не в воде и не в воздухе

        String name = Structures.SMALL_NAMES[r.nextInt(Structures.SMALL_NAMES.length)];
        return Structures.placeRaw(w, o, name, r);
    }
}
