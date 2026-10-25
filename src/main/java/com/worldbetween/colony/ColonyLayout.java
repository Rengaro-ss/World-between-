package com.worldbetween.colony;

import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.arki.Arkis;
import com.worldbetween.town.Canvas;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Where an Arki colony's discs float. The central disc holds the capsule the founders came down in; the
 * others ring it, each on its own cushion of amethyst, none joined to any other: the Arki fly.
 */
public final class ColonyLayout {
	private ColonyLayout() {
	}

	/** The top of the central disc. */
	public static BlockPos origin(ServerWorld world, Settlement colony) {
		BlockPos pos = colony.pos();
		return Arkis.isSky(colony) ? pos : new BlockPos(pos.getX(), world.getSeaLevel() + 1, pos.getZ());
	}

	public static int centralRadius(int tier) {
		return 6 + Math.min(3, tier);
	}

	/** Centre of a hall's disc, relative to the origin: six round the centre, then six further out. */
	public static int[] slot(int index) {
		boolean outer = index >= 6;
		double angle = Math.toRadians((index % 6) * 60 + (outer ? 30 : 0));
		int distance = outer ? 31 : 19;
		return new int[]{(int) Math.round(Math.sin(angle) * distance), (int) Math.round(-Math.cos(angle) * distance)};
	}

	/** A disc floats a little higher or lower than its neighbours: they are not built, they are grown. */
	public static int slotHeight(Settlement colony, int index) {
		long seed = colony.pos().asLong() * 31 + index * 7919L;
		return (int) Math.floorMod(seed, 3L) - (Arkis.isSky(colony) ? 4 : 0) + (Arkis.isSky(colony) ? (int) Math.floorMod(seed >> 3, 9L) : 0);
	}

	public static Canvas.Local local(Canvas canvas, BlockPos origin) {
		return canvas.at(origin, Direction.NORTH);
	}
}
