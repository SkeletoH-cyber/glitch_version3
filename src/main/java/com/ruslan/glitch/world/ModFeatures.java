package com.ruslan.glitch.world;

import com.ruslan.glitch.GlitchMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class ModFeatures {
    public static final GlitchScarFeature GLITCH_SCAR = Registry.register(
            Registries.FEATURE, GlitchMod.id("glitch_scar"), new GlitchScarFeature());

    private ModFeatures() {}

    public static void register() {
        // добавляем "шрамы" во все биомы Верхнего мира (измерения Глича не затрагиваются)
        BiomeModifications.addFeature(
                BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.SURFACE_STRUCTURES,
                RegistryKey.of(RegistryKeys.PLACED_FEATURE, GlitchMod.id("glitch_scar")));
    }
}
