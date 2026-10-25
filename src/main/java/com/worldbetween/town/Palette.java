package com.worldbetween.town;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.BiomeCoords;

/**
 * The materials a town is built from. The Neris build with what the land gives them: spruce and stone
 * in the cold, acacia and mud brick on the savanna, dark oak and black stone in the forests.
 */
public record Palette(Block planks, Block log, Block stairs, Block slab, Block fence, Block gate, Block door,
		BlockState brick, BlockState stone, BlockState tile, Block tileStairs, Block tileSlab, BlockState floor) {

	private static Palette wood(Block planks, Block log, Block stairs, Block slab, Block fence, Block gate, Block door,
			BlockState brick, BlockState stone, BlockState tile, Block tileStairs, Block tileSlab, BlockState floor) {
		return new Palette(planks, log, stairs, slab, fence, gate, door, brick, stone, tile, tileStairs, tileSlab, floor);
	}

	public static final Palette DARK_OAK = wood(Blocks.DARK_OAK_PLANKS, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS,
			Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE_GATE, Blocks.DARK_OAK_DOOR,
			Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Blocks.STONE_BRICKS.getDefaultState(),
			Blocks.DEEPSLATE_TILES.getDefaultState(), Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB,
			Blocks.SPRUCE_PLANKS.getDefaultState());

	public static final Palette DARK_OAK_DEEPSLATE = wood(Blocks.DARK_OAK_PLANKS, Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DARK_OAK_STAIRS,
			Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE_GATE, Blocks.DARK_OAK_DOOR,
			Blocks.DEEPSLATE_BRICKS.getDefaultState(), Blocks.POLISHED_DEEPSLATE.getDefaultState(),
			Blocks.DEEPSLATE_TILES.getDefaultState(), Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB,
			Blocks.OAK_PLANKS.getDefaultState());

	public static final Palette SPRUCE = wood(Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_STAIRS,
			Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE_GATE, Blocks.SPRUCE_DOOR,
			Blocks.STONE_BRICKS.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(),
			Blocks.DEEPSLATE_TILES.getDefaultState(), Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB,
			Blocks.DARK_OAK_PLANKS.getDefaultState());

	public static final Palette ACACIA = wood(Blocks.ACACIA_PLANKS, Blocks.STRIPPED_ACACIA_LOG, Blocks.ACACIA_STAIRS,
			Blocks.ACACIA_SLAB, Blocks.ACACIA_FENCE, Blocks.ACACIA_FENCE_GATE, Blocks.ACACIA_DOOR,
			Blocks.MUD_BRICKS.getDefaultState(), Blocks.PACKED_MUD.getDefaultState(),
			Blocks.TERRACOTTA.getDefaultState(), Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB,
			Blocks.ACACIA_PLANKS.getDefaultState());

	public static final Palette CHERRY = wood(Blocks.CHERRY_PLANKS, Blocks.STRIPPED_CHERRY_LOG, Blocks.CHERRY_STAIRS,
			Blocks.CHERRY_SLAB, Blocks.CHERRY_FENCE, Blocks.CHERRY_FENCE_GATE, Blocks.CHERRY_DOOR,
			Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Blocks.STONE_BRICKS.getDefaultState(),
			Blocks.DEEPSLATE_TILES.getDefaultState(), Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB,
			Blocks.DARK_OAK_PLANKS.getDefaultState());

	public static Palette choose(ServerWorld world, int x, int y, int z, long seed) {
		var chunkManager = world.getChunkManager();
		RegistryEntry<Biome> biome = chunkManager.getChunkGenerator().getBiomeSource().getBiome(
				BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(z),
				chunkManager.getNoiseConfig().getMultiNoiseSampler());
		Random random = Random.create(seed);
		if (biome.matchesKey(BiomeKeys.CHERRY_GROVE)) {
			return CHERRY;
		}
		if (biome.isIn(BiomeTags.IS_SAVANNA) || biome.isIn(BiomeTags.IS_BADLANDS)) {
			return ACACIA;
		}
		if (biome.isIn(BiomeTags.IS_TAIGA) || biome.value().getTemperature() < 0.3f) {
			return SPRUCE;
		}
		if (biome.isIn(BiomeTags.IS_FOREST)) {
			return random.nextBoolean() ? DARK_OAK : DARK_OAK_DEEPSLATE;
		}
		return switch (random.nextInt(3)) {
			case 0 -> DARK_OAK;
			case 1 -> DARK_OAK_DEEPSLATE;
			default -> SPRUCE;
		};
	}
}
