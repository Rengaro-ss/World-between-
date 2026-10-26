package com.worldbetween.war;

import com.worldbetween.faction.FactionType;
import com.worldbetween.town.Canvas;
import com.worldbetween.town.Ground;
import com.worldbetween.town.TownFeatures;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the war parties' trails into the world. The kingdom's are trodden earth, bridged over water with
 * packed mud; the Kaments' are cobblestone, bridges and all. Both climb steep ground on steps heaped up for
 * the purpose, and both leave signs of where they camped: a dead campfire, a little cairn of stones.
 */
public final class TrailFeatures {
	/** A camp every so many blocks along the way. */
	private static final int CAMP_EVERY = 150;

	private TrailFeatures() {
	}

	public static List<TownFeatures.Feature> of(ServerWorld world) {
		List<TownFeatures.Feature> features = new ArrayList<>();
		for (Trail trail : WorldBetweenState.get(world.getServer()).wars().trails()) {
			features.add(TownFeatures.structure("war/trail_" + trail.id(), 0, v -> draw(world, trail), new int[]{0}));
		}
		return features;
	}

	private static Canvas draw(ServerWorld world, Trail trail) {
		Canvas canvas = new Canvas();
		boolean neris = trail.faction() == FactionType.NERIS;
		Random random = Random.create(trail.id() * 0x9E3779B97F4A7C15L);
		double dx = trail.to().getX() - trail.from().getX();
		double dz = trail.to().getZ() - trail.from().getZ();
		double length = Math.hypot(dx, dz);
		if (length < 8) {
			return canvas;
		}
		double ux = dx / length;
		double uz = dz / length;
		int previous = Integer.MIN_VALUE;
		// Keep clear of the towns at either end: the trail begins where the streets give out.
		for (int step = 40; step < length - 30; step++) {
			int cx = (int) Math.round(trail.from().getX() + ux * step);
			int cz = (int) Math.round(trail.from().getZ() + uz * step);
			for (int w = 0; w < trail.width(); w++) {
				int x = cx + (int) Math.round(-uz * w);
				int z = cz + (int) Math.round(ux * w);
				boolean water = Ground.isWater(world, x, z);
				if (water) {
					int y = Ground.surface(world, x, z);
					canvas.put(new BlockPos(x, y, z), neris ? Blocks.PACKED_MUD.getDefaultState() : Blocks.COBBLESTONE.getDefaultState(), Canvas.Mode.SET);
					continue;
				}
				int y = Ground.floor(world, x, z);
				if (w == 0 && previous != Integer.MIN_VALUE && y > previous + 1) {
					// Steps up a slope too steep to walk: earth or stone heaped where the last step ends.
					for (int up = previous + 1; up < y; up++) {
						canvas.put(new BlockPos(x - (int) Math.round(ux), up, z - (int) Math.round(uz)),
								neris ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.COBBLESTONE.getDefaultState(), Canvas.Mode.FILL);
					}
				}
				if (random.nextInt(7) == 0) {
					continue; // a trail, not a highway: worn in places, grassed over in others
				}
				canvas.put(new BlockPos(x, y, z), surface(neris, random), Canvas.Mode.SET);
				canvas.put(new BlockPos(x, y + 1, z), Blocks.AIR.getDefaultState(), Canvas.Mode.CLEAR);
				canvas.put(new BlockPos(x, y + 2, z), Blocks.AIR.getDefaultState(), Canvas.Mode.CLEAR);
			}
			previous = Ground.isWater(world, cx, cz) ? Integer.MIN_VALUE : Ground.floor(world, cx, cz);
			if (step % CAMP_EVERY == CAMP_EVERY / 2) {
				camp(world, canvas, neris, cx + (int) Math.round(-uz * (trail.width() + 2)), cz + (int) Math.round(ux * (trail.width() + 2)), random);
			}
		}
		return canvas;
	}

	private static BlockState surface(boolean neris, Random random) {
		if (neris) {
			return switch (random.nextInt(6)) {
				case 0 -> Blocks.COARSE_DIRT.getDefaultState();
				case 1 -> Blocks.MUD.getDefaultState();
				default -> Blocks.DIRT_PATH.getDefaultState();
			};
		}
		return switch (random.nextInt(6)) {
			case 0 -> Blocks.MOSSY_COBBLESTONE.getDefaultState();
			case 1 -> Blocks.GRAVEL.getDefaultState();
			default -> Blocks.COBBLESTONE.getDefaultState();
		};
	}

	/** Where the party stopped for the night. */
	private static void camp(ServerWorld world, Canvas canvas, boolean neris, int x, int z, Random random) {
		if (Ground.isWater(world, x, z)) {
			return;
		}
		BlockPos at = new BlockPos(x, Ground.floor(world, x, z) + 1, z);
		if (neris) {
			canvas.put(at, Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.LIT, false), Canvas.Mode.FILL);
			canvas.put(at.east(), Blocks.OAK_LOG.getDefaultState().with(net.minecraft.block.PillarBlock.AXIS, net.minecraft.util.math.Direction.Axis.Z), Canvas.Mode.FILL);
			if (random.nextBoolean()) {
				canvas.put(at.west().down(), Blocks.COARSE_DIRT.getDefaultState(), Canvas.Mode.SET);
			}
		} else {
			canvas.put(at, Blocks.COBBLESTONE.getDefaultState(), Canvas.Mode.FILL);
			canvas.put(at.up(), Blocks.COBBLESTONE_WALL.getDefaultState(), Canvas.Mode.FILL);
			canvas.put(at.north(), Blocks.MOSSY_COBBLESTONE_SLAB.getDefaultState(), Canvas.Mode.FILL);
		}
	}
}
