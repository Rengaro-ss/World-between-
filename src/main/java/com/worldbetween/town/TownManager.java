package com.worldbetween.town;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.Settlement;
import com.worldbetween.mine.MineBlocks;
import com.worldbetween.world.WorldBetweenAttachments;
import com.worldbetween.world.WorldBetweenState;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds towns into the world as their settlements grow. Like mines, a chunk catches up on everything
 * built while it was unloaded, and a feature whose version changed (a house extended, a hall rebuilt
 * as a keep) is taken down and put up again.
 */
public final class TownManager {
	private static final int INTERVAL = 10;
	/** Block changes allowed per pass, so a big town never freezes the server while it catches up. */
	private static final int BUDGET = 3000;
	/** Wall-clock time allowed per pass; whatever is left waits for the next one. */
	private static final long TIME_BUDGET_NANOS = 15_000_000L;
	private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
	private static final int BOOK_INTERVAL = 600;

	private TownManager() {
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % INTERVAL != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		WorldBetweenState state = WorldBetweenState.get(server);
		int[] budget = {BUDGET};
		long deadline = System.nanoTime() + TIME_BUDGET_NANOS;
		boolean books = server.getTicks() % BOOK_INTERVAL == 0;
		for (Faction faction : state.factions()) {
			if (!faction.isActive()) {
				continue;
			}
			List<TownFeatures.Feature> extras = new java.util.ArrayList<>(TownFeatures.markers(world, faction));
			if (faction.type() == com.worldbetween.faction.FactionType.KAMENT) {
				extras.addAll(com.worldbetween.hold.HoldFeatures.tunnels(faction));
			}
			for (TownFeatures.Feature marker : extras) {
				if (budget[0] <= 0 || System.nanoTime() > deadline) {
					return;
				}
				build(world, marker, budget);
			}
			for (Settlement settlement : faction.settlements()) {
				if (settlement.dimension() != World.OVERWORLD) {
					continue;
				}
				List<TownFeatures.Feature> features = switch (faction.type()) {
					case KAMENT -> com.worldbetween.hold.HoldFeatures.of(world, faction, settlement);
					case ARKI -> com.worldbetween.colony.ColonyFeatures.of(world, faction, settlement);
					default -> TownFeatures.of(world, faction, settlement);
				};
				for (TownFeatures.Feature feature : features) {
					if (budget[0] <= 0 || System.nanoTime() > deadline) {
						return;
					}
					build(world, feature, budget);
				}
				if (books) {
					refreshBooks(world, faction, settlement, features, state);
				}
			}
		}
	}

	private static void build(ServerWorld world, TownFeatures.Feature feature, int[] budget) {
		LongSet chunks = feature.chunks();
		for (long packed : chunks) {
			WorldChunk chunk = world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(packed), ChunkPos.getPackedZ(packed));
			if (chunk == null) {
				continue;
			}
			Map<String, Integer> stored = chunk.getAttached(WorldBetweenAttachments.TOWN_BUILT);
			Integer built = stored == null ? null : stored.get(feature.key());
			if (built != null && built == feature.version()) {
				continue;
			}
			if (budget[0] <= 0) {
				return;
			}
			TownFeatures.Area area = TownFeatures.Area.of(chunk.getPos());
			if (built != null) {
				budget[0] -= takeDown(world, feature, built, area);
			}
			budget[0] -= put(world, feature, area, built == null);
			Map<String, Integer> data = stored == null ? new HashMap<>() : new HashMap<>(stored);
			data.put(feature.key(), feature.version());
			chunk.setAttached(WorldBetweenAttachments.TOWN_BUILT, data);
			chunk.setNeedsSaving(true);
		}
	}

	/**
	 * Removes what an older version put here: only blocks that are still exactly as placed, so anything a
	 * player has changed stays.
	 */
	private static int takeDown(ServerWorld world, TownFeatures.Feature feature, int oldVersion, TownFeatures.Area area) {
		Canvas canvas = feature.draw(oldVersion, area);
		int changed = 0;
		for (Map.Entry<BlockPos, Canvas.Placement> entry : canvas.blocks().entrySet()) {
			BlockPos pos = entry.getKey();
			Canvas.Placement placement = entry.getValue();
			if (placement.mode() == Canvas.Mode.SET && area.contains(pos) && world.getBlockState(pos).equals(placement.state())) {
				// The kingdom takes its goods along: nothing spills for a passer-by to be blamed for.
				net.minecraft.util.Clearable.clear(world.getBlockEntity(pos));
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), FLAGS);
				changed++;
			}
		}
		return changed;
	}

	private static int put(ServerWorld world, TownFeatures.Feature feature, TownFeatures.Area area, boolean firstBuild) {
		Canvas canvas = feature.draw(feature.version(), area);
		int changed = 0;
		for (Map.Entry<BlockPos, Canvas.Placement> entry : canvas.blocks().entrySet()) {
			BlockPos pos = entry.getKey();
			if (!area.contains(pos)) {
				continue;
			}
			Canvas.Placement placement = entry.getValue();
			BlockState current = world.getBlockState(pos);
			boolean place = switch (placement.mode()) {
				case SET -> isYielding(current);
				case FILL -> current.isAir() || current.isReplaceable() || !current.getFluidState().isEmpty();
				case CLEAR -> !current.isAir() && current.getFluidState().isEmpty()
						&& (MineBlocks.isDiggable(current) || current.isIn(BlockTags.LOGS));
				case CARVE -> !current.isAir() && (MineBlocks.isDiggable(current) || current.isIn(BlockTags.LOGS));
			};
			if (place && !current.equals(placement.state())) {
				world.setBlockState(pos, placement.state(), FLAGS);
				changed++;
				if (placement.mode() == Canvas.Mode.CARVE) {
					changed += seal(world, canvas, pos);
				}
				if (current.isIn(BlockTags.LOGS)) {
					changed += fellTree(world, pos);
				}
			}
		}
		for (Canvas.Hook hook : canvas.hooks()) {
			if (area.contains(hook.pos()) && (firstBuild || !hook.firstBuildOnly())) {
				hook.action().accept(world, hook.pos());
			}
		}
		return changed;
	}

	/**
	 * Walls off what would pour or fall into a freshly hewn hollow: lava and water beside it, and sand or
	 * gravel above it. The Kaments know their rock.
	 */
	private static int seal(ServerWorld world, Canvas canvas, BlockPos pos) {
		int changed = 0;
		for (net.minecraft.util.math.Direction direction : net.minecraft.util.math.Direction.values()) {
			BlockPos next = pos.offset(direction);
			if (canvas.blocks().containsKey(next) || !world.isChunkLoaded(next)) {
				continue;
			}
			BlockState state = world.getBlockState(next);
			boolean loose = direction == net.minecraft.util.math.Direction.UP && state.getBlock() instanceof net.minecraft.block.FallingBlock;
			if (!state.getFluidState().isEmpty() || loose) {
				world.setBlockState(next, MineBlocks.wornRock(next.getY()), FLAGS);
				changed++;
			}
		}
		return changed;
	}

	/**
	 * Takes down the rest of a tree whose trunk was cut, so no crown is left hanging in the air.
	 * Only natural leaves go: leaves a player placed are marked persistent and stay.
	 */
	private static int fellTree(ServerWorld world, BlockPos cut) {
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		queue.add(cut);
		seen.add(cut);
		int removed = 0;
		while (!queue.isEmpty() && removed < 400) {
			BlockPos pos = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos next = pos.add(dx, dy, dz);
						if (!seen.add(next) || Math.abs(next.getX() - cut.getX()) > 8 || Math.abs(next.getZ() - cut.getZ()) > 8
								|| next.getY() < cut.getY() - 1 || next.getY() > cut.getY() + 30 || !world.isChunkLoaded(next)) {
							continue;
						}
						BlockState state = world.getBlockState(next);
						boolean naturalLeaves = state.isIn(BlockTags.LEAVES) && state.contains(net.minecraft.block.LeavesBlock.PERSISTENT)
								&& !state.get(net.minecraft.block.LeavesBlock.PERSISTENT);
						if (state.isIn(BlockTags.LOGS) && next.getY() >= cut.getY() || naturalLeaves) {
							world.setBlockState(next, Blocks.AIR.getDefaultState(), FLAGS);
							removed++;
							queue.add(next);
						}
					}
				}
			}
		}
		return removed;
	}

	/** Natural ground, plants, trees and water make way for the town; anything else stays. */
	private static boolean isYielding(BlockState state) {
		return state.isAir() || state.isReplaceable() || MineBlocks.isDiggable(state) || state.isIn(BlockTags.LOGS)
				|| state.isIn(BlockTags.LEAVES) || !state.getFluidState().isEmpty();
	}

	private static void refreshBooks(ServerWorld world, Faction faction, Settlement settlement,
			List<TownFeatures.Feature> features, WorldBetweenState state) {
		for (TownFeatures.Feature feature : features) {
			if (feature.type() != com.worldbetween.faction.BuildingType.LIBRARY) {
				continue;
			}
			for (Canvas.WorkSpot spot : feature.canvas().workSpots()) {
				if (spot.work() == Canvas.Work.BOOKS && world.isChunkLoaded(spot.pos())
						&& world.getBlockState(spot.pos()).isOf(Blocks.LECTERN)) {
					ChronicleBooks.update(world, spot.pos(), faction, settlement, state.chronicle());
				}
			}
		}
	}
}
