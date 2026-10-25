package com.worldbetween.mine;

import com.worldbetween.faction.Mine;
import com.worldbetween.item.Ownership;
import com.worldbetween.mine.MinePlan.Kind;
import com.worldbetween.mine.MinePlan.Segment;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.RailShape;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import java.util.List;

/**
 * Turns a mine plan into blocks, one chunk at a time.
 */
public final class MineCarver {
	/** Send changes to clients but skip neighbour updates, so sand, rails and lava stay as placed. */
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

	private MineCarver() {
	}

	/**
	 * Carves steps {@code [from, to)} that fall inside {@code chunk}.
	 *
	 * @param budget remaining block changes allowed this tick; decreased as blocks are changed
	 * @return the step reached; less than {@code to} if the budget ran out
	 */
	public static int carve(ServerWorld world, WorldChunk chunk, MineManager.Site site, int from, int to, int[] budget) {
		MinePlan plan = site.plan();
		Segment first = plan.segmentAt(from);
		if (first == null) {
			return to;
		}
		ChunkBounds bounds = new ChunkBounds(chunk.getPos());
		for (int i = first.index(); i < plan.segments().size(); i++) {
			Segment segment = plan.segments().get(i);
			if (segment.startStep() >= to) {
				break;
			}
			if (!isReachable(site, segment)) {
				continue;
			}
			int sliceFrom = Math.max(0, from - segment.startStep());
			int sliceTo = Math.min(segment.length(), to - segment.startStep());
			Integer stop = site.mine().stopOf(segment.index());
			if (stop != null) {
				sliceTo = Math.min(sliceTo, stop);
			}
			if (sliceFrom >= sliceTo || !bounds.intersects(segment.box(sliceFrom, sliceTo))) {
				continue;
			}
			for (int s = sliceFrom; s < sliceTo; s++) {
				if (budget[0] <= 0) {
					return segment.startStep() + s;
				}
				if (!bounds.intersects(segment.box(s, s + 1))) {
					continue;
				}
				int changed = carveSlice(world, bounds, site, segment, s);
				budget[0] -= changed;
				if (changed < 0) {
					break; // the segment hit something protected and stopped
				}
			}
		}
		return to;
	}

	/** A segment is cut off if any of its ancestors stopped before reaching the point it branches from. */
	private static boolean isReachable(MineManager.Site site, Segment segment) {
		Segment current = segment;
		while (current.parent() >= 0) {
			Segment parent = site.plan().segments().get(current.parent());
			Integer stop = site.mine().stopOf(parent.index());
			if (stop != null && stop <= current.parentStep()) {
				return false;
			}
			current = parent;
		}
		Integer own = site.mine().stopOf(segment.index());
		return own == null || own > 0;
	}

	/** Returns the number of blocks changed, or -1 if the segment stopped here. */
	private static int carveSlice(ServerWorld world, ChunkBounds bounds, MineManager.Site site, Segment segment, int s) {
		List<BlockPos> inner = segment.sliceBlocks(s);
		for (BlockPos pos : inner) {
			if (bounds.contains(pos) && MineBlocks.isProtected(world.getBlockState(pos))) {
				site.mine().stop(segment.index(), s);
				site.markDirty();
				wallOff(world, bounds, site, segment, s);
				return -1;
			}
		}
		int changed = 1;
		for (BlockPos pos : inner) {
			if (!bounds.contains(pos)) {
				continue;
			}
			BlockState state = world.getBlockState(pos);
			// Furnishings left by earlier tunnels stay; masonry put up ahead of the face is cut through.
			if (!state.isAir() && (!MineBlocks.isFurnishing(state) || state.equals(MineBlocks.MASONRY))) {
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), FLAGS);
				changed++;
			}
		}
		changed += shore(world, bounds, segment, s, inner);
		changed += furnish(world, bounds, site, segment, s);
		return changed;
	}

	/**
	 * Makes the tunnel safe and leaves the miners' mark on its walls: holds back lava, water and loose
	 * ceilings, bridges caves under the floor, and takes the ore out of the walls.
	 */
	private static int shore(ServerWorld world, ChunkBounds bounds, Segment segment, int s, List<BlockPos> inner) {
		int changed = 0;
		int floorY = segment.sliceCenter(s).getY();
		boolean hasFloor = !segment.isShaft() || s == segment.length() - 1;
		for (BlockPos pos : inner) {
			for (Direction side : Direction.values()) {
				BlockPos next = pos.offset(side);
				// Walls are shored across chunk borders too, or water from a neighbour that has not
				// caught up yet would leak in.
				if (!world.isChunkLoaded(next)) {
					continue;
				}
				int slice = segment.sliceAt(next);
				if (slice >= 0 && slice <= s) {
					continue; // already part of the tunnel
				}
				BlockState state = world.getBlockState(next);
				if (!MineBlocks.isDiggable(state) || state.isOf(Blocks.LAVA) && isOwnLava(segment, next)) {
					continue;
				}
				BlockState replacement = null;
				if (!state.getFluidState().isEmpty()) {
					// Wall in the sources; a trickle that already ran into the tunnels is just cleared away.
					replacement = state.getFluidState().isStill() ? MineBlocks.MASONRY : Blocks.AIR.getDefaultState();
				} else if (slice >= 0) {
					continue; // next slice: it will be dug soon, leave it
				} else if (next.getY() < floorY && hasFloor && (state.isAir() || state.isReplaceable())) {
					replacement = MineBlocks.MASONRY;
				} else if (side == Direction.UP && state.getBlock() instanceof FallingBlock) {
					replacement = MineBlocks.MASONRY;
				} else if (MineBlocks.isOre(state) && !segment.isShaft()) {
					replacement = MineBlocks.wornRock(next.getY());
				}
				if (replacement != null) {
					world.setBlockState(next, replacement, FLAGS);
					changed++;
				}
			}
		}
		return changed;
	}

	/** Whether {@code pos} is part of this segment's own lava channel or forge pool. */
	private static boolean isOwnLava(Segment segment, BlockPos pos) {
		BlockPos above = pos.up();
		int slice = segment.sliceAt(above);
		if (slice < 0 || above.getY() != segment.sliceCenter(slice).getY()) {
			return false;
		}
		int offset = segment.offsetAt(above);
		return switch (segment.kind()) {
			case LAVA_DRIFT -> offset == -2;
			case FORGE -> Math.abs(offset) <= 1 && slice >= 3 && slice <= 5;
			default -> false;
		};
	}

	private static int furnish(ServerWorld world, ChunkBounds bounds, MineManager.Site site, Segment segment, int s) {
		Placer place = new Placer(world, bounds);
		BlockPos c = segment.sliceCenter(s);
		Direction across = segment.across();
		Direction.Axis along = segment.dir().getAxis();
		switch (segment.kind()) {
			case SHAFT -> {
				if (isSolid(world, c.north(2))) {
					place.air(c.north(), Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.SOUTH));
				}
				if (s % 8 == 4 && isSolid(world, c.east(2))) {
					place.air(c.east(), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, Direction.WEST));
				}
				// The top of each deeper shaft is the floor of the landing above: deck it over, leaving
				// only the ladder hole, so nobody walking off the rails falls down the shaft.
				if (s == 0 && segment.level() > 0) {
					for (BlockPos pos : segment.sliceBlocks(s)) {
						if (!pos.equals(c.north())) {
							place.air(pos, MineBlocks.MASONRY);
						}
					}
				}
			}
			case DRIFT, LAVA_DRIFT -> {
				boolean lava = segment.kind() == Kind.LAVA_DRIFT;
				int railOffset = lava ? 1 : 0;
				if (s % 4 == 2) {
					int left = -1;
					int right = lava ? 2 : 1;
					for (int y = 0; y < 2; y++) {
						place.air(c.offset(across, left).up(y), log(Direction.Axis.Y));
						place.air(c.offset(across, right).up(y), log(Direction.Axis.Y));
					}
					for (int o = segment.kind().minOffset(); o <= segment.kind().maxOffset(); o++) {
						place.air(c.offset(across, o).up(2), log(across.getAxis()));
					}
				} else if (s % 8 == 6 && isSolid(world, c.up(3))) {
					place.air(c.up(2), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
				}
				place.air(c.offset(across, railOffset), Blocks.RAIL.getDefaultState()
						.with(RailBlock.SHAPE, along == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH));
				if (lava) {
					lavaAt(world, bounds, c.offset(across, -2).down(), place);
				}
			}
			case BRANCH -> {
				if (s % 7 == 3) {
					place.air(c.offset(across, 1), Blocks.LANTERN.getDefaultState());
				}
			}
			case ROOM -> {
				if (s == 1 || s == 5) {
					container(site, place, c.offset(across, -3), Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, across), segment);
					container(site, place, c.offset(across, 3), Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, across.getOpposite()), segment);
				} else if (s == 3) {
					container(site, place, c.offset(across, -3), Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, Direction.UP), segment);
					container(site, place, c.offset(across, 3), Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, Direction.UP), segment);
				} else if (s == 6) {
					place.air(c.offset(across, -2), Blocks.CRAFTING_TABLE.getDefaultState());
					container(site, place, c, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, segment.dir().getOpposite()), segment);
					container(site, place, c.offset(across, 2), Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, Direction.UP), segment);
				}
				if ((s == 2 || s == 5) && isSolid(world, c.up(4))) {
					place.air(c.up(3), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
				}
			}
			case FORGE -> {
				if (s >= 3 && s <= 5) {
					for (int o = -1; o <= 1; o++) {
						lavaAt(world, bounds, c.offset(across, o).down(), place);
					}
				}
				BlockState furnaceLeft = Blocks.BLAST_FURNACE.getDefaultState().with(AbstractFurnaceBlock.FACING, across);
				BlockState furnaceRight = Blocks.BLAST_FURNACE.getDefaultState().with(AbstractFurnaceBlock.FACING, across.getOpposite());
				if (s == 1 || s == 7) {
					place.air(c.offset(across, -4), furnaceLeft);
					place.air(c.offset(across, 4), furnaceRight);
				} else if (s == 2 || s == 6) {
					place.air(c.offset(across, -4), Blocks.ANVIL.getDefaultState().with(AnvilBlock.FACING, segment.dir()));
					place.air(c.offset(across, 4), Blocks.ANVIL.getDefaultState().with(AnvilBlock.FACING, segment.dir()));
					for (int o : new int[]{-2, 2}) {
						if (isSolid(world, c.offset(across, o).up(5))) {
							place.air(c.offset(across, o).up(4), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
						}
					}
				} else if (s == 4) {
					place.air(c.offset(across, -4), Blocks.SMITHING_TABLE.getDefaultState());
					container(site, place, c.offset(across, 4), Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, across.getOpposite()), segment);
				} else if (s == 8) {
					BlockState back = Blocks.BLAST_FURNACE.getDefaultState().with(AbstractFurnaceBlock.FACING, segment.dir().getOpposite());
					for (int o : new int[]{-2, 0, 2}) {
						place.air(c.offset(across, o), back);
					}
				}
			}
		}
		return place.count;
	}

	/** Places a lava source and walls in every side it could spill from. */
	private static void lavaAt(ServerWorld world, ChunkBounds bounds, BlockPos pos, Placer place) {
		if (!bounds.contains(pos) || MineBlocks.isProtected(world.getBlockState(pos))) {
			return;
		}
		for (Direction side : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			BlockPos next = pos.offset(side);
			if (!world.isChunkLoaded(next)) {
				continue;
			}
			BlockState state = world.getBlockState(next);
			if (!state.isOf(Blocks.LAVA) && (state.isAir() || state.isReplaceable() || !state.getFluidState().isEmpty())) {
				world.setBlockState(next, MineBlocks.MASONRY, FLAGS);
			}
		}
		place.force(pos, Blocks.LAVA.getDefaultState());
	}

	private static void container(MineManager.Site site, Placer place, BlockPos pos, BlockState state, Segment segment) {
		if (!place.air(pos, state)) {
			return;
		}
		BlockEntity entity = place.world.getBlockEntity(pos);
		if (!(entity instanceof Inventory inventory)) {
			return;
		}
		Random random = Random.create(site.mine().seed() ^ pos.asLong());
		List<Loot> table = segment.kind() == Kind.FORGE ? FORGE_LOOT : LEVEL_LOOT.get(Math.min(segment.level(), LEVEL_LOOT.size() - 1));
		int stacks = 3 + random.nextInt(4);
		for (int i = 0; i < stacks; i++) {
			Loot loot = table.get(random.nextInt(table.size()));
			int count = loot.min() + random.nextInt(loot.max() - loot.min() + 1);
			if (count > 0) {
				inventory.setStack(random.nextInt(inventory.size()), Ownership.mark(new ItemStack(loot.item(), count), site.owner()));
			}
		}
		entity.markDirty();
	}

	/** Seals a tunnel that ran into a player's build, and leaves a sign saying whose tunnel it is. */
	private static void wallOff(ServerWorld world, ChunkBounds bounds, MineManager.Site site, Segment segment, int s) {
		for (BlockPos pos : segment.sliceBlocks(s)) {
			if (bounds.contains(pos)) {
				BlockState state = world.getBlockState(pos);
				if (MineBlocks.isDiggable(state)) {
					world.setBlockState(pos, MineBlocks.MASONRY, FLAGS);
				}
			}
		}
		if (s == 0 || segment.isShaft()) {
			return;
		}
		BlockPos signPos = segment.sliceCenter(s - 1).up();
		if (!bounds.contains(signPos) || !world.getBlockState(signPos).isAir() || !isSolid(world, segment.sliceCenter(s).up())) {
			return;
		}
		BlockState sign = Blocks.DARK_OAK_WALL_SIGN.getDefaultState().with(WallSignBlock.FACING, segment.dir().getOpposite());
		world.setBlockState(signPos, sign, FLAGS);
		if (world.getBlockEntity(signPos) instanceof SignBlockEntity entity) {
			String prefix = "sign.worldbetween." + site.owner().id() + ".border.";
			SignText text = new SignText();
			for (int line = 0; line < 4; line++) {
				text = text.withMessage(line, Text.translatable(prefix + (line + 1)));
			}
			entity.setText(text, true);
			entity.markDirty();
			world.updateListeners(signPos, sign, sign, Block.NOTIFY_ALL);
		}
	}

	/** Builds the headframe over the shaft and grows the spoil heap; returns true if anything changed. */
	public static boolean buildSurface(ServerWorld world, WorldChunk chunk, MineManager.Site site, int progress,
			java.util.Map<String, Integer> data) {
		ChunkBounds bounds = new ChunkBounds(chunk.getPos());
		Mine mine = site.mine();
		BlockPos e = mine.entrance();
		boolean changed = false;
		String headKey = site.key() + "#head";
		if (progress > 0 && !data.containsKey(headKey) && bounds.intersects(new BlockBox(e).expand(3))) {
			headframe(world, bounds, e);
			data.put(headKey, 1);
			changed = true;
		}
		BlockPos heapCenter = heapCenter(mine);
		int radius = heapRadius(progress);
		String heapKey = site.key() + "#heap";
		if (radius > data.getOrDefault(heapKey, 0) && bounds.intersects(new BlockBox(heapCenter).expand(radius + 1))) {
			heap(world, bounds, heapCenter, radius, mine.seed());
			data.put(heapKey, radius);
			changed = true;
		}
		return changed;
	}

	private static void headframe(ServerWorld world, ChunkBounds bounds, BlockPos ground) {
		Placer place = new Placer(world, bounds);
		BlockPos base = ground.up();
		// Clear the site first: no tree may hang over the shaft.
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int y = 0; y <= 12; y++) {
					BlockPos pos = base.add(dx, y, dz);
					BlockState state = world.getBlockState(pos);
					if (bounds.contains(pos) && (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.LOGS_THAT_BURN)
							|| (state.isReplaceable() && state.getFluidState().isEmpty() && !state.isAir()))) {
						place.force(pos, Blocks.AIR.getDefaultState());
					}
				}
			}
		}
		for (int dx : new int[]{-2, 2}) {
			for (int dz : new int[]{-2, 2}) {
				for (int y = 0; y < 6; y++) {
					place.open(base.add(dx, y, dz), log(Direction.Axis.Y));
				}
			}
		}
		for (int d = -2; d <= 2; d++) {
			place.open(base.add(d, 6, -2), log(Direction.Axis.X));
			place.open(base.add(d, 6, 2), log(Direction.Axis.X));
			place.open(base.add(d, 6, 0), log(Direction.Axis.X));
		}
		for (int d = -1; d <= 1; d++) {
			place.open(base.add(-2, 6, d), log(Direction.Axis.Z));
			place.open(base.add(2, 6, d), log(Direction.Axis.Z));
		}
		for (int y = 1; y <= 5; y++) {
			place.open(base.add(0, y, 0), Blocks.CHAIN.getDefaultState());
		}
		place.open(base.add(-1, 5, -2), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
		place.open(base.add(1, 5, 2), Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true));
		BlockState fenceNS = Blocks.DARK_OAK_FENCE.getDefaultState().with(FenceBlock.NORTH, true).with(FenceBlock.SOUTH, true);
		BlockState fenceEW = Blocks.DARK_OAK_FENCE.getDefaultState().with(FenceBlock.EAST, true).with(FenceBlock.WEST, true);
		for (int d = -1; d <= 1; d++) {
			place.open(base.add(-2, 0, d), fenceNS);
			place.open(base.add(2, 0, d), fenceNS);
			place.open(base.add(d, 0, 2), fenceEW);
		}
	}

	private static BlockPos heapCenter(Mine mine) {
		Direction direction = Direction.Type.HORIZONTAL.random(Random.create(mine.seed()));
		return mine.entrance().offset(direction, 9);
	}

	/** The spoil heap grows with the mine: a big heap means a deep, busy mine below. */
	private static int heapRadius(int progress) {
		return progress < 30 ? 0 : Math.min(7, 1 + (int) (Math.sqrt(progress) / 6));
	}

	private static void heap(ServerWorld world, ChunkBounds bounds, BlockPos center, int radius, long seed) {
		Placer place = new Placer(world, bounds);
		var generator = world.getChunkManager().getChunkGenerator();
		var noise = world.getChunkManager().getNoiseConfig();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				double distance = Math.sqrt(dx * dx + dz * dz);
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				if (distance > radius + 0.3 || !bounds.containsColumn(x, z)) {
					continue;
				}
				int height = Math.max(1, (int) Math.round((radius - distance) * 0.9));
				int ground = generator.getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, world, noise);
				for (int y = 0; y < height; y++) {
					BlockPos pos = new BlockPos(x, ground + y, z);
					int roll = Math.floorMod((int) (pos.asLong() * 31 + seed), 20);
					BlockState spoil = roll < 8 ? Blocks.GRAVEL.getDefaultState()
							: roll < 15 ? Blocks.COBBLESTONE.getDefaultState()
							: roll < 18 ? Blocks.TUFF.getDefaultState() : Blocks.STONE.getDefaultState();
					place.open(pos, spoil);
				}
			}
		}
	}

	private static BlockState log(Direction.Axis axis) {
		return Blocks.DARK_OAK_LOG.getDefaultState().with(PillarBlock.AXIS, axis);
	}

	private static boolean isSolid(ServerWorld world, BlockPos pos) {
		return world.getBlockState(pos).isSolidBlock(world, pos);
	}

	private record Loot(Item item, int min, int max) {
	}

	private static final List<List<Loot>> LEVEL_LOOT = List.of(
			List.of(new Loot(Items.COAL, 8, 24), new Loot(Items.RAW_COPPER, 8, 24), new Loot(Items.RAW_IRON, 4, 12)),
			List.of(new Loot(Items.RAW_IRON, 8, 24), new Loot(Items.RAW_GOLD, 2, 8), new Loot(Items.REDSTONE, 4, 16), new Loot(Items.COAL, 8, 16)),
			List.of(new Loot(Items.RAW_IRON, 8, 16), new Loot(Items.RAW_GOLD, 4, 10), new Loot(Items.DIAMOND, 1, 3), new Loot(Items.REDSTONE, 8, 16)));

	private static final List<Loot> FORGE_LOOT = List.of(
			new Loot(Items.IRON_INGOT, 8, 20), new Loot(Items.GOLD_INGOT, 2, 8), new Loot(Items.DIAMOND, 0, 2),
			new Loot(Items.IRON_SWORD, 1, 1), new Loot(Items.IRON_PICKAXE, 1, 1));

	/** Places blocks inside one chunk only, and counts them. */
	private static final class Placer {
		private final ServerWorld world;
		private final ChunkBounds bounds;
		private int count;

		Placer(ServerWorld world, ChunkBounds bounds) {
			this.world = world;
			this.bounds = bounds;
		}

		/** Places only into air, so nothing already there is overwritten. */
		boolean air(BlockPos pos, BlockState state) {
			if (!bounds.contains(pos) || !world.getBlockState(pos).isAir()) {
				return false;
			}
			return force(pos, state);
		}

		/** Places into air, plants and leaves, for building on the surface. */
		boolean open(BlockPos pos, BlockState state) {
			if (!bounds.contains(pos)) {
				return false;
			}
			BlockState current = world.getBlockState(pos);
			if (!current.isAir() && !current.isReplaceable() && !current.isIn(BlockTags.LEAVES)) {
				return false;
			}
			return force(pos, state);
		}

		boolean force(BlockPos pos, BlockState state) {
			if (!bounds.contains(pos)) {
				return false;
			}
			world.setBlockState(pos, state, FLAGS);
			count++;
			return true;
		}
	}

	private record ChunkBounds(int minX, int maxX, int minZ, int maxZ) {
		ChunkBounds(ChunkPos pos) {
			this(pos.getStartX(), pos.getEndX(), pos.getStartZ(), pos.getEndZ());
		}

		boolean contains(BlockPos pos) {
			return containsColumn(pos.getX(), pos.getZ());
		}

		boolean containsColumn(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
		}

		boolean intersects(BlockBox box) {
			return box.getMaxX() >= minX && box.getMinX() <= maxX && box.getMaxZ() >= minZ && box.getMinZ() <= maxZ;
		}
	}
}
