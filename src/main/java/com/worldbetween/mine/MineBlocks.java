package com.worldbetween.mine;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;

import java.util.List;
import java.util.Set;

/**
 * What miners may dig through, what they build with, and what they must never touch.
 */
public final class MineBlocks {
	/** The Neris masonry: used to wall off lava, water and loose ceilings, and to bridge caves. */
	public static final BlockState MASONRY = Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState();

	private static final List<TagKey<Block>> ORES = List.of(
			BlockTags.COAL_ORES, BlockTags.IRON_ORES, BlockTags.COPPER_ORES, BlockTags.GOLD_ORES,
			BlockTags.REDSTONE_ORES, BlockTags.LAPIS_ORES, BlockTags.DIAMOND_ORES, BlockTags.EMERALD_ORES);

	private static final Set<Block> NATURAL = Set.of(
			Blocks.GRAVEL, Blocks.CLAY, Blocks.CALCITE, Blocks.SMOOTH_BASALT, Blocks.DRIPSTONE_BLOCK,
			Blocks.POINTED_DRIPSTONE, Blocks.GLOW_LICHEN, Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET,
			Blocks.CAVE_VINES, Blocks.CAVE_VINES_PLANT, Blocks.COBWEB, Blocks.SCULK, Blocks.SCULK_VEIN,
			Blocks.MAGMA_BLOCK, Blocks.OBSIDIAN, Blocks.INFESTED_STONE, Blocks.INFESTED_DEEPSLATE,
			Blocks.POWDER_SNOW, Blocks.SNOW_BLOCK, Blocks.ICE, Blocks.PACKED_ICE,
			// Our own masonry may be cut through again by a later tunnel.
			Blocks.POLISHED_BLACKSTONE_BRICKS);

	/** Blocks the Neris furnish mines with. Later tunnels leave them alone, and they drop as Neris property. */
	private static final Set<Block> FURNISHINGS = Set.of(
			Blocks.DARK_OAK_LOG, Blocks.RAIL, Blocks.LANTERN, Blocks.WALL_TORCH, Blocks.LADDER,
			Blocks.CHEST, Blocks.BARREL, Blocks.DARK_OAK_FENCE, Blocks.CHAIN, Blocks.CRAFTING_TABLE,
			Blocks.BLAST_FURNACE, Blocks.ANVIL, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.DARK_OAK_WALL_SIGN,
			Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.SMITHING_TABLE);

	private MineBlocks() {
	}

	public static boolean isOre(BlockState state) {
		for (TagKey<Block> tag : ORES) {
			if (state.isIn(tag)) {
				return true;
			}
		}
		return false;
	}

	/** Natural ground the miners may remove. */
	public static boolean isDiggable(BlockState state) {
		return state.isAir()
				|| state.isIn(BlockTags.BASE_STONE_OVERWORLD)
				|| state.isIn(BlockTags.DIRT)
				|| state.isIn(BlockTags.SAND)
				|| state.isIn(BlockTags.LEAVES)
				|| isOre(state)
				|| NATURAL.contains(state.getBlock())
				|| state.getBlock() instanceof FluidBlock
				|| (state.isReplaceable() && !state.hasBlockEntity());
	}

	public static boolean isFurnishing(BlockState state) {
		return FURNISHINGS.contains(state.getBlock());
	}

	/**
	 * Anything that is neither natural ground nor a mine furnishing was most likely put there by a player.
	 * A tunnel that meets such a block stops and walls itself off instead of breaking in.
	 */
	public static boolean isProtected(BlockState state) {
		return !isDiggable(state) && !isFurnishing(state);
	}

	/** Stone to leave behind where an ore vein has been worked out. */
	public static BlockState wornRock(int y) {
		return y < 0 ? Blocks.DEEPSLATE.getDefaultState() : Blocks.STONE.getDefaultState();
	}
}
