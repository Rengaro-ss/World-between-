package com.worldbetween.sim;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * Picks places for new settlements straight from the world generator, without loading chunks.
 * This is what lets factions expand into land no player has ever visited.
 */
public final class LocationFinder {
	private static final int ATTEMPTS = 48;
	/** No two settlements of any faction may be closer than this; a walled castle town is 160 blocks across. */
	private static final int MIN_SETTLEMENT_SPACING = 200;

	private LocationFinder() {
	}

	/** Finds a spot in a ring of the given radii around {@code center}, or null. */
	public static BlockPos findSite(SimulationContext context, ServerWorld world, FactionType type,
			BlockPos center, int minDistance, int maxDistance) {
		Random random = context.random();
		ServerChunkManager chunkManager = world.getChunkManager();
		ChunkGenerator generator = chunkManager.getChunkGenerator();
		for (int i = 0; i < ATTEMPTS; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = MathHelper.lerp(random.nextDouble(), minDistance, maxDistance);
			int x = center.getX() + (int) (Math.cos(angle) * distance);
			int z = center.getZ() + (int) (Math.sin(angle) * distance);
			int y = generator.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, world, chunkManager.getNoiseConfig());
			if (y <= world.getSeaLevel() && type != FactionType.ARKI) {
				continue;
			}
			RegistryEntry<Biome> biome = generator.getBiomeSource().getBiome(
					BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(z),
					chunkManager.getNoiseConfig().getMultiNoiseSampler());
			if (!type.prefersBiome(biome)) {
				continue;
			}
			BlockPos pos = new BlockPos(x, y, z);
			if (isFarFromSettlements(context, pos)) {
				return pos;
			}
		}
		return null;
	}

	private static boolean isFarFromSettlements(SimulationContext context, BlockPos pos) {
		long minDistanceSq = (long) MIN_SETTLEMENT_SPACING * MIN_SETTLEMENT_SPACING;
		for (Faction faction : context.state().factions()) {
			for (Settlement settlement : faction.settlements()) {
				if (settlement.pos().getSquaredDistance(pos) < minDistanceSq) {
					return false;
				}
			}
		}
		return true;
	}
}
