package com.ruslan.glitch.item;

import com.ruslan.glitch.GlitchMod;
import com.ruslan.glitch.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;

public final class ModItems {
    private ModItems() {}

    public static final Item REALITY_ANCHOR = Registry.register(
            Registries.ITEM,
            GlitchMod.id("reality_anchor"),
            new RealityAnchorItem(new Item.Settings().maxCount(16).rarity(Rarity.EPIC)));

    public static final Item GLITCH_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            GlitchMod.id("glitch_spawn_egg"),
            new SpawnEggItem(GlitchMod.GLITCH, 0x00FFFF, 0xFF00FF, new Item.Settings()));

    public static final Item MINION_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            GlitchMod.id("glitch_minion_spawn_egg"),
            new SpawnEggItem(GlitchMod.MINION, 0x1A1A22, 0xFFFFFF, new Item.Settings()));

    /** Отдельная вкладка креатива со всем содержимым мода. */
    public static final ItemGroup GLITCH_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            GlitchMod.id("glitch"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModBlocks.GLITCH_BLOCK))
                    .displayName(Text.translatable("itemGroup.glitchmod.glitch"))
                    .entries((ctx, entries) -> {
                        entries.add(REALITY_ANCHOR);
                        entries.add(GLITCH_SPAWN_EGG);
                        entries.add(MINION_SPAWN_EGG);
                        for (Block b : ModBlocks.ALL) entries.add(b);
                    })
                    .build());

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(REALITY_ANCHOR));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> {
            entries.add(GLITCH_SPAWN_EGG);
            entries.add(MINION_SPAWN_EGG);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
            for (Block b : ModBlocks.ALL) entries.add(b);
        });
    }
}
