package com.worldbetween.town;

import com.worldbetween.faction.Settlement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where everything in a Neris town goes. A paved square in the middle with the town hall on one side;
 * streets run out from the other sides, lined with plots and a second row of plots behind for fields
 * and pastures. Streets grow longer, and more plots open up, as the town grows.
 *
 * <p>No two towns are laid out alike. The hall may face any way; a street that would run into water or
 * up a cliff is not laid at all; streets wind gently; plots that would sit in water or on a steep slope
 * are left empty; and the town is built from what the surrounding land offers.
 */
public final class TownLayout {
	/** Distance from the street to each row of plots: the first row for houses and workshops, the rest for fields. */
	private static final int[] ROWS = {8, 21, 34, 47};
	private static final int PLOT_HALF = 5;
	private static final int FIRST_PLOT = 15;
	private static final int PLOT_SPACING = 12;
	private static final int PLOTS_PER_ROW = 5;
	private static final int MAX_STREET = 30 + 14 * 3;
	private static final int MAX_SLOPE = 5;

	private static final Map<String, TownLayout> CACHE = new HashMap<>();

	/** A building plot: up to 11×11 blocks, its front facing the street. */
	public record Slot(int index, int x, int z, Direction facing, boolean back, int along, Direction street, boolean buildable) {
		public boolean isOpen(int tier) {
			return buildable && along + 6 <= streetLength(tier);
		}
	}

	/** One street: its direction and how it winds. */
	public record Street(Direction direction, int amplitude, double period, double phase) {
		public int offset(int distance) {
			return (int) Math.round(amplitude * Math.sin(distance / period + phase));
		}
	}

	public record Pier(BlockPos shore, Direction direction, int length) {
	}

	private final int centerX;
	private final int centerZ;
	private final int groundY;
	private final long seed;
	private final Direction hallSide;
	private final List<Street> streets;
	private final List<Slot> slots;
	private final Pier pier;
	private final boolean octagonal;
	private final Palette palette;

	private TownLayout(ServerWorld world, Settlement settlement) {
		this.centerX = settlement.pos().getX();
		this.centerZ = settlement.pos().getZ();
		this.groundY = Ground.floor(world, centerX, centerZ);
		this.seed = settlement.pos().asLong() * 6364136223846793005L + settlement.id();
		Random random = Random.create(seed);
		this.hallSide = Direction.Type.HORIZONTAL.random(random);
		this.octagonal = random.nextBoolean();
		this.palette = Palette.choose(world, centerX, groundY, centerZ, seed);

		List<Street> candidates = new ArrayList<>();
		for (Direction direction : Direction.Type.HORIZONTAL) {
			if (direction != hallSide) {
				candidates.add(new Street(direction, random.nextInt(4), 12 + random.nextInt(14), random.nextDouble() * Math.PI * 2));
			}
		}
		// Leave out the street that fares worst against the land, if it runs into water or up a cliff.
		candidates.sort(Comparator.comparingInt(street -> roughness(world, street)));
		if (roughness(world, candidates.get(candidates.size() - 1)) > 3) {
			candidates.remove(candidates.size() - 1);
		}
		this.streets = List.copyOf(candidates);

		// Candidate plots, nearest first; a plot is kept only if it clears the square, the hall, every
		// street and every plot already kept, so neighbouring streets never fight over a corner.
		record Candidate(int x, int z, Direction facing, int row, int along, Direction street) {
		}
		List<Candidate> candidates2 = new ArrayList<>();
		for (Street street : streets) {
			Direction dir = street.direction();
			Direction right = dir.rotateYClockwise();
			for (int k = 0; k < PLOTS_PER_ROW; k++) {
				int along = FIRST_PLOT + PLOT_SPACING * k + random.nextInt(3) - 1;
				int bend = street.offset(along);
				for (Direction side : new Direction[]{right, right.getOpposite()}) {
					for (int row = 0; row < ROWS.length; row++) {
						int lateral = ROWS[row] + random.nextInt(2);
						int across = lateral * (side == right ? 1 : -1) + bend;
						int x = centerX + dir.getOffsetX() * along + right.getOffsetX() * across;
						int z = centerZ + dir.getOffsetZ() * along + right.getOffsetZ() * across;
						candidates2.add(new Candidate(x, z, side.getOpposite(), row, along, dir));
					}
				}
			}
		}
		candidates2.sort(Comparator.comparingInt((Candidate c) -> c.row()).thenComparingInt(Candidate::along));
		List<int[]> taken = new ArrayList<>();
		List<Slot> list = new ArrayList<>();
		for (Candidate c : candidates2) {
			if (!isClear(c.x(), c.z(), taken)) {
				continue;
			}
			taken.add(new int[]{c.x(), c.z()});
			list.add(new Slot(0, c.x(), c.z(), c.facing(), c.row() > 0, c.along(), c.street(), isBuildable(world, c.x(), c.z())));
		}
		list.sort(Comparator.comparingInt(Slot::along).thenComparing(Slot::back));
		List<Slot> indexed = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			Slot s = list.get(i);
			indexed.add(new Slot(i, s.x(), s.z(), s.facing(), s.back(), s.along(), s.street(), s.buildable()));
		}
		this.slots = List.copyOf(indexed);
		this.pier = findPier(world);
	}

	public static TownLayout of(ServerWorld world, Settlement settlement) {
		String key = settlement.pos().asLong() + "/" + settlement.id();
		synchronized (CACHE) {
			return CACHE.computeIfAbsent(key, k -> new TownLayout(world, settlement));
		}
	}

	public static int plazaHalf(int tier) {
		return 5 + tier;
	}

	public static int streetLength(int tier) {
		return 30 + 14 * tier;
	}

	/** Half the side of the square the town wall runs along. */
	public static int wallHalf(int tier) {
		return Math.max(streetLength(tier), 34) + 8;
	}

	public BlockPos center() {
		return new BlockPos(centerX, groundY, centerZ);
	}

	public long seed() {
		return seed;
	}

	public Direction hallSide() {
		return hallSide;
	}

	public List<Street> streets() {
		return streets;
	}

	public boolean isOctagonal() {
		return octagonal;
	}

	public Palette palette() {
		return palette;
	}

	public List<Slot> slots() {
		return slots;
	}

	public Slot slot(int index) {
		return index >= 0 && index < slots.size() ? slots.get(index) : null;
	}

	public boolean hasPier() {
		return pier != null;
	}

	public Pier pier() {
		return pier;
	}

	/** Ground level for a plot: the natural terrain at its centre. */
	public int groundAt(ServerWorld world, int x, int z) {
		return Ground.floor(world, x, z);
	}

	/** A point on a street's centre line, {@code distance} blocks out from the square. */
	public BlockPos streetPoint(Street street, int distance) {
		Direction dir = street.direction();
		Direction right = dir.rotateYClockwise();
		int across = street.offset(distance);
		return new BlockPos(centerX + dir.getOffsetX() * distance + right.getOffsetX() * across, 0,
				centerZ + dir.getOffsetZ() * distance + right.getOffsetZ() * across);
	}

	/** Watch posts: first at the ends of the streets, then behind the town hall, then in the square. */
	public List<BlockPos> towerSpots(int tier) {
		List<BlockPos> spots = new ArrayList<>();
		int end = streetLength(tier) + 3;
		for (Street street : streets) {
			spots.add(streetPoint(street, end).offset(street.direction().rotateYClockwise(), 4));
		}
		spots.add(center().offset(hallSide, plazaHalf(tier) + 24).withY(0));
		int corner = plazaHalf(tier) - 1;
		for (int[] c : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
			spots.add(new BlockPos(centerX + c[0] * corner, 0, centerZ + c[1] * corner));
		}
		return spots;
	}

	/** Index of the first square-corner tower in {@link #towerSpots}. */
	public int innerTowerStart() {
		return streets.size() + 1;
	}

	/** Market stalls around the edge of the square, clear of the hall door, the street openings and the corner towers. */
	public List<BlockPos> stallSpots(int tier) {
		int p = plazaHalf(tier);
		int reach = p - 5;
		List<BlockPos> spots = new ArrayList<>();
		for (Direction edge : Direction.Type.HORIZONTAL) {
			Integer mouth = edge == hallSide ? Integer.valueOf(0) : streets.stream()
					.filter(s -> s.direction() == edge).map(s -> s.offset(p + 1)).findFirst().orElse(null);
			Direction along = edge.rotateYClockwise();
			for (int a : reach >= 3 ? new int[]{-reach, 0, reach} : reach >= 2 ? new int[]{-reach, reach} : new int[]{0}) {
				if (mouth != null && Math.abs(a - mouth) < 3) {
					continue;
				}
				spots.add(center().offset(edge, p - 2).offset(along, a).withY(0));
			}
		}
		return spots;
	}

	/** Whether an 11×11 plot centred here (plus a path around it) clears everything already laid out. */
	private boolean isClear(int x, int z, List<int[]> taken) {
		int reach = PLOT_HALF + 1;
		int plaza = plazaHalf(3) + 1;
		if (Math.abs(x - centerX) <= plaza + reach && Math.abs(z - centerZ) <= plaza + reach) {
			return false;
		}
		// The hall, up to the capital's throne hall, stands on its side of the square.
		BlockPos hallNear = center().offset(hallSide, plaza);
		BlockPos hallFar = center().offset(hallSide, plaza + 22);
		int hallMinX = Math.min(hallNear.getX(), hallFar.getX()) - 8;
		int hallMaxX = Math.max(hallNear.getX(), hallFar.getX()) + 8;
		int hallMinZ = Math.min(hallNear.getZ(), hallFar.getZ()) - 8;
		int hallMaxZ = Math.max(hallNear.getZ(), hallFar.getZ()) + 8;
		if (hallSide.getAxis() == Direction.Axis.X) {
			hallMinX = Math.min(hallNear.getX(), hallFar.getX());
			hallMaxX = Math.max(hallNear.getX(), hallFar.getX());
		} else {
			hallMinZ = Math.min(hallNear.getZ(), hallFar.getZ());
			hallMaxZ = Math.max(hallNear.getZ(), hallFar.getZ());
		}
		if (x + reach >= hallMinX && x - reach <= hallMaxX && z + reach >= hallMinZ && z - reach <= hallMaxZ) {
			return false;
		}
		for (Street street : streets) {
			for (int d = 0; d <= MAX_STREET + 12; d++) {
				BlockPos p = streetPoint(street, d);
				if (Math.abs(p.getX() - x) <= reach + 1 && Math.abs(p.getZ() - z) <= reach + 1) {
					return false;
				}
			}
		}
		for (int[] other : taken) {
			if (Math.abs(other[0] - x) < 2 * PLOT_HALF + 2 && Math.abs(other[1] - z) < 2 * PLOT_HALF + 2) {
				return false;
			}
		}
		return true;
	}

	/** How badly a street would fare: steps of water or steep ground along its full length. */
	private int roughness(ServerWorld world, Street street) {
		int bad = 0;
		int previous = Integer.MIN_VALUE;
		for (int d = 10; d <= MAX_STREET; d += 6) {
			BlockPos p = streetPoint(street, d);
			if (Ground.isWater(world, p.getX(), p.getZ())) {
				bad += 2;
				continue;
			}
			int height = Ground.floor(world, p.getX(), p.getZ());
			if (previous != Integer.MIN_VALUE && Math.abs(height - previous) > 3) {
				bad++;
			}
			previous = height;
		}
		return bad;
	}

	/** A plot is no good in water or on a slope steeper than a few blocks across its width. */
	private static boolean isBuildable(ServerWorld world, int x, int z) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int[] o : new int[][]{{0, 0}, {-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
			if (Ground.isWater(world, x + o[0], z + o[1])) {
				return false;
			}
			int h = Ground.floor(world, x + o[0], z + o[1]);
			min = Math.min(min, h);
			max = Math.max(max, h);
		}
		return max - min <= MAX_SLOPE;
	}

	/** The first water within reach of the square, for a pier, or null. */
	private Pier findPier(ServerWorld world) {
		Pier best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Direction direction : Direction.Type.HORIZONTAL) {
			if (direction == hallSide) {
				continue;
			}
			int previous = 0;
			for (int d = 12; d <= 70; d += 2) {
				int x = centerX + direction.getOffsetX() * d;
				int z = centerZ + direction.getOffsetZ() * d;
				if (Ground.isWater(world, x, z) && Ground.surface(world, x, z) - Ground.floor(world, x, z) >= 2) {
					if (d < bestDistance && previous > 0) {
						int sx = centerX + direction.getOffsetX() * previous;
						int sz = centerZ + direction.getOffsetZ() * previous;
						best = new Pier(new BlockPos(sx, Ground.surface(world, sx, sz), sz), direction, 12);
						bestDistance = d;
					}
					break;
				}
				previous = d;
			}
		}
		return best;
	}
}
