package com.worldbetween.hold;

import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.kament.Kaments;
import com.worldbetween.town.Canvas;
import com.worldbetween.town.Ground;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;

/**
 * Where everything of a Kament hold lies. A mountain hold is hewn into the rock: a gate in the steepest
 * slope, a gallery into the mountain, the great hall, and behind it an inner gallery with the working halls
 * on either side; beside the great hall a spiral stair goes down to the deep city. A deep city is a domed
 * cavern with its halls around the rim.
 *
 * <p>Local coordinates as everywhere: the gate side is north (negative z), the great hall's centre is the origin.
 */
public final class HoldLayout {
	/** Where the inner gallery begins: behind the largest great hall, so halls never move as a hold grows. */
	public static final int INNER_START = 13;
	/** The spiral stair stands this far to the side of the great hall. */
	public static final int SHAFT_X = 16;
	private static final int[] HALL_HALF_WIDTH = {5, 6, 8, 10};
	private static final int[] HALL_HALF_DEPTH = {6, 8, 10, 12};
	private static final int[] HALL_HEIGHT = {6, 8, 10, 12};
	private static final int[] DEEP_ANGLES = {0, 180, 90, 270, 45, 225, 135, 315};

	private static final Map<String, HoldLayout> CACHE = new HashMap<>();

	private final boolean deep;
	private final BlockPos origin;
	private final Direction facing;
	private final int exit;
	/** Heights of the ground in front of the gate, for the steps down. */
	private final int[] apron;

	private HoldLayout(ServerWorld world, Settlement settlement) {
		this.deep = Kaments.isDeep(settlement);
		BlockPos site = settlement.pos();
		if (deep) {
			this.origin = site;
			this.facing = Direction.NORTH;
			this.exit = 0;
			this.apron = new int[0];
			return;
		}
		int x = site.getX();
		int z = site.getZ();
		int top = Ground.floor(world, x, z);
		// The gate goes where the mountain falls away most steeply.
		Direction best = Direction.NORTH;
		double lowest = Double.MAX_VALUE;
		for (Direction direction : Direction.Type.HORIZONTAL) {
			double sum = 0;
			for (int d : new int[]{24, 36, 48}) {
				sum += Ground.floor(world, x + direction.getOffsetX() * d, z + direction.getOffsetZ() * d);
			}
			if (sum < lowest) {
				lowest = sum;
				best = direction;
			}
		}
		this.facing = best;
		int floor = top - 16;
		int found = exitDistance(world, x, z, best, floor);
		if (found < 0) {
			// A broad mountain: the hall goes down to the level of the lowest ground ahead.
			int low = Integer.MAX_VALUE;
			for (int d = 20; d <= 90; d++) {
				low = Math.min(low, height(world, x, z, best, d));
			}
			floor = low;
			found = exitDistance(world, x, z, best, floor);
		}
		floor = Math.max(floor, world.getSeaLevel() + 1);
		found = exitDistance(world, x, z, best, floor);
		this.exit = Math.max(HALL_HALF_DEPTH[3] + 3, found < 0 ? HALL_HALF_DEPTH[3] + 8 : found);
		this.origin = new BlockPos(x, floor, z);
		this.apron = new int[16];
		for (int k = 0; k < apron.length; k++) {
			apron[k] = height(world, x, z, best, exit + 1 + k);
		}
	}

	private static int height(ServerWorld world, int x, int z, Direction direction, int d) {
		return Ground.floor(world, x + direction.getOffsetX() * d, z + direction.getOffsetZ() * d);
	}

	private static int exitDistance(ServerWorld world, int x, int z, Direction direction, int floor) {
		for (int d = HALL_HALF_DEPTH[3] + 3; d <= 90; d++) {
			if (height(world, x, z, direction, d) <= floor + 1) {
				return d;
			}
		}
		return -1;
	}

	public static HoldLayout of(ServerWorld world, Settlement settlement) {
		String key = settlement.pos().asLong() + "/" + settlement.id() + "/" + Kaments.kind(settlement);
		synchronized (CACHE) {
			return CACHE.computeIfAbsent(key, k -> new HoldLayout(world, settlement));
		}
	}

	public boolean isDeep() {
		return deep;
	}

	/** The centre of the great hall (or the cavern), at floor level. */
	public BlockPos origin() {
		return origin;
	}

	public Direction facing() {
		return facing;
	}

	/** Distance from the great hall's centre to the gate. */
	public int exit() {
		return exit;
	}

	public int apron(int k) {
		return k < apron.length ? apron[k] : origin.getY();
	}

	public Canvas.Local local(Canvas canvas) {
		return canvas.at(origin, facing);
	}

	public Canvas.Local local() {
		return local(new Canvas());
	}

	public static int hallHalfWidth(int tier) {
		return HALL_HALF_WIDTH[Math.min(3, tier)];
	}

	public static int hallHalfDepth(int tier) {
		return HALL_HALF_DEPTH[Math.min(3, tier)];
	}

	public static int hallHeight(int tier) {
		return HALL_HEIGHT[Math.min(3, tier)];
	}

	/** Rows of halls along the inner gallery, two to a row. */
	public static int rows(int tier) {
		return 3 + 2 * Math.min(3, tier);
	}

	public static int innerEnd(int tier) {
		return INNER_START + 2 + 10 * rows(tier);
	}

	/** Radius of a deep city's cavern, and the height of its dome. */
	public static int cavernRadius(int tier) {
		return 9 + 3 * Math.min(3, tier);
	}

	public static int cavernHeight(int tier) {
		return 5 + Math.min(3, tier);
	}

	/** Centre of the hall in a slot, in local coordinates (x, z). */
	public int[] slot(int index, int tier) {
		if (!deep) {
			int row = index / 2;
			int side = index % 2 == 0 ? -1 : 1;
			return new int[]{side * 8, INNER_START + 7 + 10 * row};
		}
		double angle = Math.toRadians(DEEP_ANGLES[index % DEEP_ANGLES.length]);
		int distance = cavernRadius(3) + 10;
		return new int[]{(int) Math.round(Math.sin(angle) * distance), (int) Math.round(-Math.cos(angle) * distance)};
	}

	/** Direction from the slot's hall back to where it is entered from, in local terms. */
	public double slotAngle(int index) {
		return Math.toRadians(DEEP_ANGLES[index % DEEP_ANGLES.length]);
	}

	/** Top of the spiral stair down to the deep city, in world coordinates. */
	public BlockPos shaftTop() {
		return local().world(SHAFT_X, 0, 0);
	}

	/** Where the deep city below a mountain hold goes: straight under the spiral stair. */
	public BlockPos deepCity(ServerWorld world) {
		BlockPos top = shaftTop();
		return new BlockPos(top.getX(), Math.max(world.getBottomY() + 12, -30), top.getZ());
	}
}
