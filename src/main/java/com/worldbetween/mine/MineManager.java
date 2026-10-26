package com.worldbetween.mine;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Mine;
import com.worldbetween.faction.Settlement;
import com.worldbetween.world.WorldBetweenAttachments;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps faction mines growing in the world: carves loaded chunks up to the miners' current progress,
 * catches up chunks that load after being away, and answers "whose mine is this?".
 */
public final class MineManager {
	private static final int INTERVAL = 10;
	/** Block changes allowed per pass, so catching up a large mine never freezes the server. */
	private static final int BUDGET = 4000;
	private static final int SOUND_INTERVAL = 30;
	private static final double HEARING_DISTANCE = 24;

	private static final Map<String, MinePlan> PLANS = new HashMap<>();

	public record Site(FactionType owner, Settlement settlement, Mine mine, MinePlan plan, String key, WorldBetweenState state) {
		public void markDirty() {
			state.markDirty();
		}

		/** How far the miners have got right now. */
		public int progress(ServerWorld world) {
			return Math.min(plan.totalSteps(), mine.progress((float) (com.worldbetween.sim.Clock.now(world.getServer()) - WorldBetweenState.get(world.getServer()).day())));
		}

		/** Whether {@code pos} lies inside a part of the tunnel that has already been dug. */
		public boolean containsDug(BlockPos pos, int progress) {
			if (!plan.bounds().contains(pos)) {
				return false;
			}
			for (MinePlan.Segment segment : plan.segments()) {
				if (segment.startStep() >= progress) {
					break;
				}
				int slice = segment.sliceAt(pos);
				if (slice < 0 || segment.startStep() + slice >= progress) {
					continue;
				}
				Integer stop = mine.stopOf(segment.index());
				if (stop == null || slice < stop) {
					return true;
				}
			}
			return false;
		}
	}

	private MineManager() {
	}

	public static MinePlan plan(Mine mine) {
		return PLANS.computeIfAbsent(mine.seed() + "@" + mine.entrance().asLong(),
				key -> MinePlan.generate(mine.seed(), mine.entrance()));
	}

	public static List<Site> sites(WorldBetweenState state) {
		List<Site> sites = new ArrayList<>();
		for (Faction faction : state.factions()) {
			if (!faction.isActive()) {
				continue;
			}
			for (Settlement settlement : faction.settlements()) {
				Mine mine = settlement.mine();
				if (mine != null && settlement.dimension() == World.OVERWORLD) {
					sites.add(new Site(faction.type(), settlement, mine, plan(mine), settlement.key(faction.type()), state));
				}
			}
		}
		return sites;
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % INTERVAL != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		WorldBetweenState state = WorldBetweenState.get(server);
		int[] budget = {BUDGET};
		long deadline = System.nanoTime() + 10_000_000L;
		for (Site site : sites(state)) {
			if (System.nanoTime() > deadline) {
				break;
			}
			int progress = site.progress(world);
			for (Map.Entry<ChunkPos, Integer> entry : site.plan().chunks().entrySet()) {
				ChunkPos pos = entry.getKey();
				WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.x, pos.z);
				if (chunk == null) {
					continue;
				}
				Map<String, Integer> stored = chunk.getAttached(WorldBetweenAttachments.MINE_PROGRESS);
				Map<String, Integer> data = stored == null ? new HashMap<>() : new HashMap<>(stored);
				boolean changed = false;
				int done = data.getOrDefault(site.key(), 0);
				if (done < progress && entry.getValue() < progress && budget[0] > 0) {
					data.put(site.key(), MineCarver.carve(world, chunk, site, done, progress, budget));
					changed = true;
				}
				changed |= MineCarver.buildSurface(world, chunk, site, progress, data);
				if (changed) {
					chunk.setAttached(WorldBetweenAttachments.MINE_PROGRESS, data);
					chunk.setNeedsSaving(true);
				}
			}
			if (server.getTicks() % SOUND_INTERVAL == 0) {
				playPickaxes(world, site, progress);
			}
		}
	}

	/** You can hear the miners through the rock before you break into their tunnel. */
	private static void playPickaxes(ServerWorld world, Site site, int progress) {
		Mine mine = site.mine();
		if (mine.target() <= mine.previous() || progress >= mine.target()) {
			return;
		}
		BlockPos face = site.plan().faceAt(progress);
		if (face == null || !world.isChunkLoaded(face) || world.getClosestPlayer(face.getX(), face.getY(), face.getZ(), HEARING_DISTANCE, false) == null) {
			return;
		}
		float pitch = 0.75f + world.random.nextFloat() * 0.3f;
		world.playSound(null, face, face.getY() < 0 ? SoundEvents.BLOCK_DEEPSLATE_HIT : SoundEvents.BLOCK_STONE_HIT,
				SoundCategory.BLOCKS, 1.6f, pitch);
		if (world.random.nextInt(4) == 0) {
			world.playSound(null, face, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.4f, pitch);
		}
	}

	/** The mine whose dug tunnels contain {@code pos}, or null. */
	public static Site siteContaining(ServerWorld world, BlockPos pos) {
		if (world.getRegistryKey() != World.OVERWORLD) {
			return null;
		}
		for (Site site : sites(WorldBetweenState.get(world.getServer()))) {
			int progress = site.progress(world);
			if (site.containsDug(pos, progress) || site.containsDug(pos.up(), progress)) {
				return site;
			}
		}
		return null;
	}

	/** The mine a furnishing block at {@code pos} belongs to, or null if it is not a faction's. */
	public static Site siteOfFurnishing(ServerWorld world, BlockPos pos, BlockState state) {
		if (world.getRegistryKey() != World.OVERWORLD || !MineBlocks.isFurnishing(state)) {
			return null;
		}
		for (Site site : sites(WorldBetweenState.get(world.getServer()))) {
			BlockPos e = site.mine().entrance();
			if (new BlockBox(e.getX() - 3, e.getY(), e.getZ() - 3, e.getX() + 3, e.getY() + 8, e.getZ() + 3).contains(pos)) {
				return site;
			}
			if (!site.plan().bounds().contains(pos)) {
				continue;
			}
			for (MinePlan.Segment segment : site.plan().segments()) {
				if (segment.box(0, segment.length()).contains(pos)) {
					return site;
				}
			}
		}
		return null;
	}
}
