package com.worldbetween.war;

import com.worldbetween.faction.FactionType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * A band's builder at work, seen: the ground under their feet beaten into a path, a bridge of mud or
 * cobblestone run out over the water ahead, a step cut up a ledge too high to walk. What they lay stays.
 */
public final class Trailwork {
	private Trailwork() {
	}

	public static void lay(ServerWorld world, FactionType faction, MobEntity builder, BlockPos toward) {
		boolean neris = faction == FactionType.NERIS;
		BlockPos feet = builder.getBlockPos();
		BlockPos under = feet.down();
		BlockState ground = world.getBlockState(under);
		if (isSoft(ground)) {
			world.setBlockState(under, path(neris, world));
		}
		// Ahead, along the way they are going.
		double dx = toward.getX() - feet.getX();
		double dz = toward.getZ() - feet.getZ();
		if (dx * dx + dz * dz < 4) {
			return;
		}
		Direction facing = Direction.getFacing(dx, 0, dz);
		BlockState bridge = neris ? Blocks.PACKED_MUD.getDefaultState() : Blocks.COBBLESTONE.getDefaultState();
		boolean built = false;
		for (int i = 0; i <= 3; i++) {
			BlockPos at = under.offset(facing, i);
			BlockState there = world.getBlockState(at);
			boolean wet = there.getFluidState().isIn(FluidTags.WATER) || there.getFluidState().isIn(FluidTags.LAVA);
			boolean gap = there.isAir() && world.getBlockState(at.down()).isAir();
			if (wet || gap) {
				world.setBlockState(at, bridge);
				built = true;
			}
		}
		// A ledge more than a step high: a block to climb by.
		BlockPos front = feet.offset(facing);
		if (!world.getBlockState(front).isAir() && !world.getBlockState(front).isReplaceable()
				&& world.getBlockState(front.up()).isSolidBlock(world, front.up())
				&& world.getBlockState(feet.up(2)).isAir() && world.getBlockState(feet).isAir()) {
			world.setBlockState(feet, neris ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.COBBLESTONE.getDefaultState());
			builder.refreshPositionAndAngles(builder.getX(), feet.getY() + 1, builder.getZ(), builder.getYaw(), builder.getPitch());
			built = true;
		}
		if (built) {
			builder.swingHand(net.minecraft.util.Hand.MAIN_HAND);
			world.playSound(null, feet, neris ? net.minecraft.sound.SoundEvents.BLOCK_MUD_PLACE : net.minecraft.sound.SoundEvents.BLOCK_STONE_PLACE,
					SoundCategory.NEUTRAL, 0.8f, 0.9f);
		}
	}

	private static boolean isSoft(BlockState state) {
		return state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.DIRT) || state.isOf(Blocks.PODZOL) || state.isOf(Blocks.MYCELIUM)
				|| state.isOf(Blocks.ROOTED_DIRT) || state.isIn(BlockTags.SAND) && !state.isOf(Blocks.SUSPICIOUS_SAND);
	}

	private static BlockState path(boolean neris, ServerWorld world) {
		if (neris) {
			return world.random.nextInt(5) == 0 ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.DIRT_PATH.getDefaultState();
		}
		return world.random.nextInt(6) == 0 ? Blocks.GRAVEL.getDefaultState() : Blocks.COBBLESTONE.getDefaultState();
	}
}
