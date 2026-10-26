package com.worldbetween.town;

import com.worldbetween.faction.Settlement;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.Map;

/**
 * Damage to a town. A town carries one figure, how much of it lies broken (0 to 100), and which of its
 * buildings that hits follows from their names, so the same ones stay broken from one day to the next and
 * mend in turn as the builders work. A broken building loses blocks, its upper parts most, and rubble lies
 * at its feet.
 */
public final class Damage {
	public static final String DAMAGE = "damage";

	private Damage() {
	}

	/** How badly a building of a town is broken: 0 whole, 1 to 3. */
	public static int level(Settlement settlement, String key) {
		long damage = settlement.counter(DAMAGE);
		if (damage <= 0 || key.contains("/street_") || key.contains("/road")) {
			return 0;
		}
		double u = Math.floorMod((key.hashCode() * 0x9E3779B97F4A7C15L) ^ settlement.counter("damage_seed"), 10_000L) / 10_000.0;
		return (u * 100 < damage ? 1 : 0) + (u * 250 < damage ? 1 : 0) + (u * 500 < damage ? 1 : 0);
	}

	/** The broken version of a building's canvas. */
	static Canvas broken(Canvas whole, int level, String key) {
		Canvas canvas = new Canvas();
		Random random = Random.create(key.hashCode() * 31L + level);
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (Map.Entry<BlockPos, Canvas.Placement> entry : whole.blocks().entrySet()) {
			if (entry.getValue().mode() == Canvas.Mode.SET && !entry.getValue().state().isAir()) {
				minY = Math.min(minY, entry.getKey().getY());
				maxY = Math.max(maxY, entry.getKey().getY());
			}
		}
		double height = Math.max(1, maxY - minY);
		boolean colony = key.startsWith("arki/");
		for (Map.Entry<BlockPos, Canvas.Placement> entry : whole.blocks().entrySet()) {
			BlockPos pos = entry.getKey();
			Canvas.Placement placement = entry.getValue();
			if (placement.mode() == Canvas.Mode.SET && !placement.state().isAir() && pos.getY() > minY) {
				double chance = level * 0.12 + (pos.getY() - minY) / height * level * 0.2;
				if (random.nextDouble() < chance) {
					if (random.nextDouble() < 0.12 && pos.getY() > minY + 1) {
						BlockState rubble = colony ? Blocks.CALCITE.getDefaultState()
								: random.nextBoolean() ? Blocks.COBBLESTONE.getDefaultState() : Blocks.GRAVEL.getDefaultState();
						canvas.put(new BlockPos(pos.getX(), minY + 1, pos.getZ()), rubble, Canvas.Mode.FILL);
					}
					continue;
				}
			}
			canvas.put(pos, placement.state(), placement.mode());
		}
		canvas.hooks().addAll(whole.hooks());
		canvas.workSpots().addAll(whole.workSpots());
		return canvas;
	}

	/** Adds damage to a town (a quake, a comet, a storming). */
	public static void add(Settlement settlement, long amount) {
		if (settlement.counter("damage_seed") == 0) {
			settlement.setCounter("damage_seed", 1 + (settlement.pos().asLong() & 0xFFFFFF));
		}
		settlement.setCounter(DAMAGE, Math.min(90, settlement.counter(DAMAGE) + amount));
	}
}
