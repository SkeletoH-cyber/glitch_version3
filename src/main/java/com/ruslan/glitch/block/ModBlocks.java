package com.ruslan.glitch.block;

import com.ruslan.glitch.GlitchMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.TransparentBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

import java.util.ArrayList;
import java.util.List;

/** Новые блоки в стиле Глича. Все они доступны в креативе (вкладка Glitch и Building Blocks). */
public final class ModBlocks {
    public static final List<Block> ALL = new ArrayList<>();

    public static final Block GLITCH_BLOCK = reg("glitch_block", new Block(
            AbstractBlock.Settings.create().strength(0.5f).luminance(s -> 8).sounds(BlockSoundGroup.AMETHYST_BLOCK)));
    public static final Block CORRUPTED_STONE = reg("corrupted_stone", new Block(
            AbstractBlock.Settings.create().strength(1.5f, 6.0f).sounds(BlockSoundGroup.STONE)));
    public static final Block CORRUPTED_BRICKS = reg("corrupted_bricks", new Block(
            AbstractBlock.Settings.create().strength(2.0f, 6.0f).sounds(BlockSoundGroup.STONE)));
    public static final Block CORRUPTED_PLANKS = reg("corrupted_planks", new Block(
            AbstractBlock.Settings.create().strength(1.5f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block VOID_BLOCK = reg("void_block", new Block(
            AbstractBlock.Settings.create().strength(50.0f, 1200.0f).sounds(BlockSoundGroup.DEEPSLATE)));
    public static final Block GLITCH_LAMP = reg("glitch_lamp", new Block(
            AbstractBlock.Settings.create().strength(0.3f).luminance(s -> 15).sounds(BlockSoundGroup.GLASS)));
    public static final Block STATIC_GLASS = reg("static_glass", new TransparentBlock(
            AbstractBlock.Settings.create().strength(0.3f).sounds(BlockSoundGroup.GLASS).nonOpaque()
                    .solidBlock((s, w, p) -> false).suffocates((s, w, p) -> false).blockVision((s, w, p) -> false)));
    public static final Block ERROR_BLOCK = reg("error_block", new Block(
            AbstractBlock.Settings.create().strength(1.0f, 3.0f).sounds(BlockSoundGroup.STONE)));
    public static final Block GLITCH_PILLAR = reg("glitch_pillar", new PillarBlock(
            AbstractBlock.Settings.create().strength(2.0f, 6.0f).sounds(BlockSoundGroup.STONE)));

    private ModBlocks() {}

    private static Block reg(String name, Block block) {
        Registry.register(Registries.BLOCK, GlitchMod.id(name), block);
        Registry.register(Registries.ITEM, GlitchMod.id(name), new BlockItem(block, new Item.Settings()));
        ALL.add(block);
        return block;
    }

    /** Вызов нужен только для того, чтобы класс загрузился и блоки зарегистрировались. */
    public static void register() {
    }
}
