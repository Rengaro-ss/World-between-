package com.worldbetween.town;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

/**
 * Terrain height as the world generator made it, so towns sit on the natural ground even where
 * players or the town itself have since built on top.
 */
public final class Ground {
	private static final Long2IntOpenHashMap SURFACE = new Long2IntOpenHashMap();
	private static final Long2IntOpenHashMap FLOOR = new Long2IntOpenHashMap();
	private static final int MAX_CACHE = 200_000;

	private Ground() {
	}

	/** The top natural block (not the air above it), ignoring water: the land or the sea bed. */
	public static int floor(ServerWorld world, int x, int z) {
		return lookup(FLOOR, world, x, z, Heightmap.Type.OCEAN_FLOOR_WG);
	}

	/** The top block including water. */
	public static int surface(ServerWorld world, int x, int z) {
		return lookup(SURFACE, world, x, z, Heightmap.Type.WORLD_SURFACE_WG);
	}

	public static boolean isWater(ServerWorld world, int x, int z) {
		return surface(world, x, z) > floor(world, x, z);
	}

	private static int lookup(Long2IntOpenHashMap cache, ServerWorld world, int x, int z, Heightmap.Type type) {
		long key = ChunkPos.toLong(x, z);
		synchronized (cache) {
			if (cache.containsKey(key)) {
				return cache.get(key);
			}
		}
		int height = world.getChunkManager().getChunkGenerator().getHeight(x, z, type, world,
				world.getChunkManager().getNoiseConfig()) - 1;
		synchronized (cache) {
			if (cache.size() > MAX_CACHE) {
				cache.clear();
			}
			cache.put(key, height);
		}
		return height;
	}
}
