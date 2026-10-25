package com.worldbetween.town;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * A structure as a set of block placements in world coordinates, plus what has to happen after the
 * blocks are down (filling chests, hanging banners, bringing in animals) and where people work.
 *
 * <p>Blueprints draw in local coordinates: x across the front, y up, z from the front (negative) to
 * the back (positive), with the front facing north. {@link #at} turns that into world positions for a
 * building that faces any direction.
 */
public final class Canvas {
	public enum Mode {
		/** Place the block unless something built by a player is in the way. */
		SET,
		/** Place only into air, plants or water: foundations under a building on a slope. */
		FILL,
		/** Remove natural ground, plants and trees: making room for a building. */
		CLEAR,
		/** Hew out natural rock, leaving a hollow; lava and water next to it are walled off. */
		CARVE
	}

	public record Placement(BlockState state, Mode mode) {
	}

	/** What a worker does at a spot; drives the sights and sounds of work when a player is near. */
	public enum Work {
		FURNACE, CHIMNEY, ANVIL, SAW, CHISEL, GRIND, FIELD, ANIMALS, LOOM, LEATHER, BOOKS, KITCHEN,
		STORE, TRADE, DRILL, FISHING, AXE, KILN
	}

	public record WorkSpot(BlockPos pos, Work work) {
	}

	public record Hook(BlockPos pos, BiConsumer<ServerWorld, BlockPos> action, boolean firstBuildOnly) {
	}

	private final Map<BlockPos, Placement> blocks = new LinkedHashMap<>();
	private final List<Hook> hooks = new ArrayList<>();
	private final List<WorkSpot> workSpots = new ArrayList<>();

	public Map<BlockPos, Placement> blocks() {
		return blocks;
	}

	public List<Hook> hooks() {
		return hooks;
	}

	public List<WorkSpot> workSpots() {
		return workSpots;
	}

	public void put(BlockPos pos, BlockState state, Mode mode) {
		Placement existing = blocks.get(pos);
		// A clear never undoes a placement, and a fill never overrides a solid placement.
		if (existing != null && (mode == Mode.CLEAR || mode == Mode.CARVE || mode == Mode.FILL && existing.mode() == Mode.SET)) {
			return;
		}
		blocks.put(pos.toImmutable(), new Placement(state, mode));
	}

	public Local at(BlockPos origin, Direction facing) {
		return new Local(this, origin, facing, false);
	}

	/** Like {@link #at(BlockPos, Direction)}, optionally drawn mirrored left to right. */
	public Local at(BlockPos origin, Direction facing, boolean mirrored) {
		return new Local(this, origin, facing, mirrored);
	}

	/** Drawing in a building's own coordinates. */
	public static final class Local {
		private final Canvas canvas;
		private final BlockPos origin;
		private final BlockRotation rotation;
		private final Direction facing;
		private final boolean mirrored;

		private Local(Canvas canvas, BlockPos origin, Direction facing, boolean mirrored) {
			this.canvas = canvas;
			this.origin = origin;
			this.facing = facing;
			this.mirrored = mirrored;
			this.rotation = switch (facing) {
				case EAST -> BlockRotation.CLOCKWISE_90;
				case SOUTH -> BlockRotation.CLOCKWISE_180;
				case WEST -> BlockRotation.COUNTERCLOCKWISE_90;
				default -> BlockRotation.NONE;
			};
		}

		public BlockPos world(int x, int y, int z) {
			return origin.add(new BlockPos(mirrored ? -x : x, y, z).rotate(rotation));
		}

		private BlockState orient(BlockState state) {
			return (mirrored ? state.mirror(BlockMirror.FRONT_BACK) : state).rotate(rotation);
		}

		/** A local direction (north = front) turned into the world. */
		public Direction dir(Direction local) {
			if (local.getAxis().isVertical()) {
				return local;
			}
			return rotation.rotate(mirrored && local.getAxis() == Direction.Axis.X ? local.getOpposite() : local);
		}

		public Direction facing() {
			return facing;
		}

		public void set(int x, int y, int z, BlockState state) {
			canvas.put(world(x, y, z), orient(state), Mode.SET);
		}

		public void fill(int x, int y, int z, BlockState state) {
			canvas.put(world(x, y, z), orient(state), Mode.FILL);
		}

		public void clear(int x, int y, int z) {
			canvas.put(world(x, y, z), net.minecraft.block.Blocks.AIR.getDefaultState(), Mode.CLEAR);
		}

		public void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
					for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
						set(x, y, z, state);
					}
				}
			}
		}

		/** Hollow walls around a rectangle. */
		public void walls(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
			for (int y = y0; y <= y1; y++) {
				for (int x = x0; x <= x1; x++) {
					set(x, y, z0, state);
					set(x, y, z1, state);
				}
				for (int z = z0 + 1; z < z1; z++) {
					set(x0, y, z, state);
					set(x1, y, z, state);
				}
			}
		}

		/**
		 * Prepares the ground: clears room above, lays a foundation down to the terrain below,
		 * and sets the floor at y = 0.
		 */
		public void site(int x0, int z0, int x1, int z1, int height, BlockState foundation, BlockState floor) {
			for (int x = x0 - 1; x <= x1 + 1; x++) {
				for (int z = z0 - 1; z <= z1 + 1; z++) {
					for (int y = 1; y <= height + 3; y++) {
						clear(x, y, z);
					}
				}
			}
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					set(x, 0, z, floor);
					for (int y = -1; y >= -8; y--) {
						fill(x, y, z, foundation);
					}
				}
			}
		}

		/** Hews out one block of rock. */
		public void carve(int x, int y, int z) {
			canvas.put(world(x, y, z), net.minecraft.block.Blocks.AIR.getDefaultState(), Mode.CARVE);
		}

		public void carveBox(int x0, int y0, int z0, int x1, int y1, int z1) {
			for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
				for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
					for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
						carve(x, y, z);
					}
				}
			}
		}

		public void hook(int x, int y, int z, BiConsumer<ServerWorld, BlockPos> action) {
			canvas.hooks.add(new Hook(world(x, y, z), action, false));
		}

		/** Runs only the first time the building goes up, e.g. bringing in animals. */
		public void hookOnce(int x, int y, int z, BiConsumer<ServerWorld, BlockPos> action) {
			canvas.hooks.add(new Hook(world(x, y, z), action, true));
		}

		public void work(int x, int y, int z, Work work) {
			canvas.workSpots.add(new WorkSpot(world(x, y, z), work));
		}
	}
}
