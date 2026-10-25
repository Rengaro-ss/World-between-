package com.worldbetween.town;

import com.worldbetween.faction.BuildingType;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Plot;
import com.worldbetween.faction.Settlement;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Everything a settlement has standing in the world, each with a version: when the settlement grows,
 * the version changes and the piece is rebuilt. Any version can be redrawn from its number alone, so
 * the old one can always be taken down, even after a restart.
 */
public final class TownFeatures {
	private static final Map<String, Canvas> CANVASES = new HashMap<>();
	private static final Map<String, LongSet> COLUMNS = new HashMap<>();
	private static final Map<String, LongSet> CHUNKS = new HashMap<>();
	private static final int MAX_CACHE = 4000;

	/** One piece of a town, drawable at any of its versions. */
	public static final class Feature {
		private final String key;
		private final int version;
		private final BuildingType type;
		private final IntFunction<Canvas> structure;
		private final IntFunction<LongSet> columns;
		private final LinearEmitter linear;
		private final int[] history;

		private Feature(String key, int version, BuildingType type, IntFunction<Canvas> structure,
				IntFunction<LongSet> columns, LinearEmitter linear, int[] history) {
			this.key = key;
			this.version = version;
			this.type = type;
			this.structure = structure;
			this.columns = columns;
			this.linear = linear;
			this.history = history;
		}

		public String key() {
			return key;
		}

		public int version() {
			return version;
		}

		public BuildingType type() {
			return type;
		}

		public boolean isStructure() {
			return structure != null;
		}

		/** The structure at its current version. */
		public Canvas canvas() {
			return canvas(version);
		}

		public Canvas canvas(int v) {
			return cached(CANVASES, key + "#" + v, () -> structure.apply(v));
		}

		/** Draws version {@code v} for the columns of one chunk. */
		public Canvas draw(int v, Area area) {
			if (isStructure()) {
				return canvas(v);
			}
			Canvas canvas = new Canvas();
			linear.emit(canvas, area, cached(COLUMNS, key + "#" + v, () -> columns.apply(v)), v);
			return canvas;
		}

		/** Every chunk this feature touches now or touched at an earlier version, so old ones get cleared. */
		public LongSet chunks() {
			return cached(CHUNKS, key + "#" + version + "#chunks", () -> {
				LongSet set = new LongOpenHashSet();
				for (int v : history) {
					if (isStructure()) {
						for (BlockPos pos : canvas(v).blocks().keySet()) {
							set.add(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
						}
					} else {
						for (long column : cached(COLUMNS, key + "#" + v, () -> columns.apply(v))) {
							set.add(ChunkPos.toLong(ChunkPos.getPackedX(column) >> 4, ChunkPos.getPackedZ(column) >> 4));
						}
					}
				}
				return set;
			});
		}
	}

	/** A structure feature for other peoples' builders (the Kaments' holds) to hand to the town builder. */
	public static Feature structure(String key, int version, IntFunction<Canvas> structure, int[] history) {
		return new Feature(key, version, null, structure, null, null, history);
	}

	public static int[] versionsUpTo(int max) {
		return upTo(max);
	}

	public interface LinearEmitter {
		void emit(Canvas canvas, Area area, LongSet columns, int version);
	}

	/** The block columns of one chunk. */
	public record Area(int minX, int maxX, int minZ, int maxZ) {
		public static Area of(ChunkPos pos) {
			return new Area(pos.getStartX(), pos.getEndX(), pos.getStartZ(), pos.getEndZ());
		}

		public boolean contains(int x, int z) {
			return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
		}

		public boolean contains(BlockPos pos) {
			return contains(pos.getX(), pos.getZ());
		}
	}

	private TownFeatures() {
	}

	/** Seeker markers of a faction, built like town features; a retired marker is taken down. */
	public static List<Feature> markers(ServerWorld world, Faction faction) {
		List<Feature> features = new ArrayList<>();
		for (com.worldbetween.faction.seekers.Marker marker : faction.markers()) {
			BlockPos pos = marker.pos();
			BlockPos origin = pos.withY(Ground.floor(world, pos.getX(), pos.getZ()));
			features.add(new Feature(faction.type().id() + "/marker_" + marker.id(), marker.retired() ? 1 : 0, null, v -> {
				Canvas canvas = new Canvas();
				if (v == 0) {
					Canvas.Local local = canvas.at(origin, Direction.NORTH);
					if (marker.type() == com.worldbetween.faction.seekers.Marker.Type.CAIRN) {
						Blueprints.cairn(local, faction.type(), marker.id(), Palette.DARK_OAK);
					} else {
						Blueprints.stake(local, faction.type(), marker.id(), Palette.DARK_OAK);
					}
				}
				return canvas;
			}, null, null, new int[]{0, 1}));
		}
		return features;
	}

	public static List<Feature> of(ServerWorld world, Faction faction, Settlement settlement) {
		TownLayout layout = TownLayout.of(world, settlement);
		FactionType owner = faction.type();
		Palette palette = layout.palette();
		int tier = settlement.tier();
		String base = settlement.key(owner) + "/";
		long seed = layout.seed();
		List<Feature> features = new ArrayList<>();
		BlockPos center = layout.center();

		features.add(new Feature(base + "plaza", tier, null, v -> plaza(layout, owner, palette, v), null, null, upTo(tier)));

		for (TownLayout.Street street : layout.streets()) {
			features.add(new Feature(base + "street_" + street.direction().asString(), tier, null, null,
					v -> streetColumns(layout, street, v),
					(canvas, area, columns, v) -> paveColumns(canvas, world, area, columns, v, seed ^ street.direction().ordinal()),
					upTo(tier)));
		}

		boolean capital = settlement.isCapital();
		features.add(new Feature(base + "hall", tier * 2 + (capital && tier >= 3 ? 1 : 0), null, v -> {
			int hallTier = v / 2;
			boolean throne = v % 2 == 1;
			int depth = Blueprints.townHallDepth(hallTier, throne);
			BlockPos hallCenter = center.offset(layout.hallSide(), TownLayout.plazaHalf(hallTier) + 1 + (depth + 1) / 2);
			Canvas canvas = new Canvas();
			Blueprints.townHall(canvas.at(hallCenter, layout.hallSide().getOpposite()), owner, seed, palette, hallTier, throne);
			return canvas;
		}, null, null, upTo(tier * 2 + 1)));

		for (Plot plot : settlement.plots()) {
			TownLayout.Slot slot = layout.slot(plot.slot());
			if (slot == null || !slot.buildable()) {
				continue;
			}
			int level = plot.type() == BuildingType.WAREHOUSE ? (tier >= 2 ? 1 : 0) : plot.level();
			BlockPos origin = new BlockPos(slot.x(), layout.groundAt(world, slot.x(), slot.z()), slot.z());
			long plotSeed = seed ^ (plot.slot() * 7919L);
			boolean mirrored = Random.create(plotSeed).nextBoolean();
			int version = plot.type().ordinal() * 10 + level;
			features.add(new Feature(base + "plot_" + plot.slot(), version, plot.type(), v -> {
				Canvas canvas = new Canvas();
				Blueprints.building(canvas.at(origin, slot.facing(), mirrored), owner, plotSeed, palette,
						BuildingType.values()[v / 10], v % 10);
				return canvas;
			}, null, null, new int[]{version}));
		}

		if (settlement.buildingCount(BuildingType.MARKET) > 0) {
			for (int i = 0; i < 2 + 2 * tier; i++) {
				int index = i;
				features.add(new Feature(base + "stall_" + i, tier, BuildingType.MARKET, v -> {
					Canvas canvas = new Canvas();
					List<BlockPos> stalls = layout.stallSpots(v);
					if (index < Math.min(stalls.size(), 2 + 2 * v)) {
						BlockPos origin = stalls.get(index).withY(center.getY());
						Blueprints.stall(canvas.at(origin, facingToward(origin, center)), owner, seed + index, palette, index);
					}
					return canvas;
				}, null, null, upTo(tier)));
			}
		}

		int towerCount = settlement.buildingCount(BuildingType.WATCHTOWER);
		for (int i = 0; i < Math.min(towerCount, layout.towerSpots(tier).size()); i++) {
			int index = i;
			features.add(new Feature(base + "tower_" + i, tier * 2 + (tier >= 2 ? 1 : 0), BuildingType.WATCHTOWER, v -> {
				int towerTier = v / 2;
				BlockPos spot = layout.towerSpots(towerTier).get(index);
				boolean inside = index >= layout.innerTowerStart();
				BlockPos origin = spot.withY(inside ? center.getY() : Ground.floor(world, spot.getX(), spot.getZ()));
				Canvas canvas = new Canvas();
				Blueprints.watchtower(canvas.at(origin, facingToward(origin, center)), owner, seed, palette, v % 2 == 1);
				return canvas;
			}, null, null, upTo(tier * 2 + 1)));
		}

		if (tier >= 2) {
			features.add(new Feature(base + "wall", tier, null, null,
					v -> v >= 2 ? wallColumns(center, TownLayout.wallHalf(v)) : new LongOpenHashSet(),
					(canvas, area, columns, v) -> wall(canvas, world, layout, TownLayout.wallHalf(v), v, area, columns),
					new int[]{2, 3}));
		}

		if (settlement.buildingCount(BuildingType.PIER) > 0 && layout.hasPier()) {
			TownLayout.Pier pier = layout.pier();
			BlockPos start = pier.shore().offset(pier.direction(), 2);
			BlockPos origin = start.withY(Ground.surface(world, start.getX(), start.getZ()));
			features.add(new Feature(base + "pier", 0, BuildingType.PIER, v -> {
				Canvas canvas = new Canvas();
				Blueprints.pier(canvas.at(origin, pier.direction()), owner, seed, palette, pier.length());
				return canvas;
			}, null, null, new int[]{0}));
		}

		if (settlement.isCapital() && faction.portal() != null) {
			BlockPos portal = faction.portal();
			BlockPos origin = portal.withY(Ground.floor(world, portal.getX(), portal.getZ()));
			features.add(new Feature(base + "portal", 0, null, v -> {
				Canvas canvas = new Canvas();
				Blueprints.deadPortal(canvas.at(origin, facingToward(origin, center)), owner, seed, palette);
				return canvas;
			}, null, null, new int[]{0}));
		}

		Settlement parent = faction.settlements().stream().filter(s -> s.id() == settlement.parentId()).findFirst().orElse(null);
		if (parent != null) {
			TownLayout parentLayout = TownLayout.of(world, parent);
			int version = Math.min(parent.tier(), tier) * 16 + parent.tier() * 4 + tier;
			features.add(new Feature(base + "road", version, null, null,
					v -> roadColumns(seed, parentLayout, (v / 4) % 4, layout, v % 4),
					(canvas, area, columns, v) -> paveColumns(canvas, world, area, columns, v / 16, seed * 17),
					new int[]{version}));
		}
		return features;
	}

	// ---------------------------------------------------------------- pieces

	private static int[] upTo(int max) {
		int[] versions = new int[max + 1];
		for (int i = 0; i <= max; i++) {
			versions[i] = i;
		}
		return versions;
	}

	private static <T> T cached(Map<String, T> cache, String key, java.util.function.Supplier<T> make) {
		synchronized (cache) {
			T value = cache.get(key);
			if (value == null) {
				if (cache.size() > MAX_CACHE) {
					cache.clear();
				}
				value = make.get();
				cache.put(key, value);
			}
			return value;
		}
	}

	private static Canvas plaza(TownLayout layout, FactionType owner, Palette palette, int tier) {
		Canvas canvas = new Canvas();
		BlockPos center = layout.center();
		int half = TownLayout.plazaHalf(tier);
		Random random = Random.create(layout.seed());
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				if (layout.isOctagonal() && Math.abs(dx) + Math.abs(dz) > half + half / 2) {
					continue;
				}
				BlockPos pos = center.add(dx, 0, dz);
				canvas.put(pos, paving(tier, random), Canvas.Mode.SET);
				for (int y = 1; y <= 5; y++) {
					canvas.put(pos.up(y), Blocks.AIR.getDefaultState(), Canvas.Mode.CLEAR);
				}
				for (int y = 1; y <= 6; y++) {
					canvas.put(pos.down(y), Blueprints.FOUNDATION, Canvas.Mode.FILL);
				}
			}
		}
		Blueprints.plazaCenter(canvas.at(center, layout.hallSide().getOpposite()), owner, layout.seed(), palette, tier);
		return canvas;
	}

	private static LongSet streetColumns(TownLayout layout, TownLayout.Street street, int tier) {
		LongSet columns = new LongLinkedOpenHashSet();
		int end = tier >= 2 ? TownLayout.wallHalf(tier) + 3 : TownLayout.streetLength(tier) + 1;
		Direction right = street.direction().rotateYClockwise();
		for (int d = TownLayout.plazaHalf(tier) - 1; d <= end; d++) {
			BlockPos point = layout.streetPoint(street, d);
			int shift = street.offset(d + 1) - street.offset(d);
			for (int w = -1; w <= 1; w++) {
				columns.add(ChunkPos.toLong(point.getX() + right.getOffsetX() * w, point.getZ() + right.getOffsetZ() * w));
				// Where the street bends, pave the corner too so it stays three wide.
				if (shift != 0) {
					columns.add(ChunkPos.toLong(point.getX() + right.getOffsetX() * (w + shift), point.getZ() + right.getOffsetZ() * (w + shift)));
				}
			}
		}
		return columns;
	}

	private static void paveColumns(Canvas canvas, ServerWorld world, Area area, LongSet columns, int tier, long seed) {
		Random random = Random.create(seed);
		for (long column : columns) {
			int x = ChunkPos.getPackedX(column);
			int z = ChunkPos.getPackedZ(column);
			BlockState surface = paving(tier, random);
			if (area.contains(x, z)) {
				road(canvas, world, x, z, surface);
			}
		}
	}

	/** Paving gets better as the town grows: beaten earth, gravel, cobbles, dressed stone. */
	private static BlockState paving(int tier, Random random) {
		int roll = random.nextInt(10);
		return switch (tier) {
			case 0 -> (roll < 7 ? Blocks.DIRT_PATH : Blocks.COARSE_DIRT).getDefaultState();
			case 1 -> (roll < 6 ? Blocks.GRAVEL : roll < 9 ? Blocks.DIRT_PATH : Blocks.COBBLESTONE).getDefaultState();
			case 2 -> (roll < 7 ? Blocks.COBBLESTONE : roll < 9 ? Blocks.STONE_BRICKS : Blocks.MOSSY_COBBLESTONE).getDefaultState();
			default -> (roll < 6 ? Blocks.STONE_BRICKS : roll < 8 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.CRACKED_STONE_BRICKS).getDefaultState();
		};
	}

	/** One column of road: on the ground, or a plank bridge on water. */
	private static void road(Canvas canvas, ServerWorld world, int x, int z, BlockState surface) {
		if (Ground.isWater(world, x, z)) {
			canvas.put(new BlockPos(x, Ground.surface(world, x, z), z), Blocks.DARK_OAK_PLANKS.getDefaultState(), Canvas.Mode.SET);
			return;
		}
		int y = Ground.floor(world, x, z);
		canvas.put(new BlockPos(x, y, z), surface, Canvas.Mode.SET);
		for (int dy = 1; dy <= 4; dy++) {
			canvas.put(new BlockPos(x, y + dy, z), Blocks.AIR.getDefaultState(), Canvas.Mode.CLEAR);
		}
	}

	private static LongSet wallColumns(BlockPos center, int half) {
		LongSet columns = new LongLinkedOpenHashSet();
		for (int d = -half; d <= half; d++) {
			columns.add(ChunkPos.toLong(center.getX() + d, center.getZ() - half));
			columns.add(ChunkPos.toLong(center.getX() + d, center.getZ() + half));
			columns.add(ChunkPos.toLong(center.getX() - half, center.getZ() + d));
			columns.add(ChunkPos.toLong(center.getX() + half, center.getZ() + d));
		}
		return columns;
	}

	/** A palisade around a fort, a stone wall with battlements around a castle town; gates where the streets leave. */
	private static void wall(Canvas canvas, ServerWorld world, TownLayout layout, int half, int tier, Area area, LongSet columns) {
		boolean stone = tier >= 3;
		int height = stone ? 5 : 4;
		BlockPos center = layout.center();
		Palette palette = layout.palette();
		List<BlockPos> gates = new ArrayList<>();
		for (TownLayout.Street street : layout.streets()) {
			gates.add(layout.streetPoint(street, half));
		}
		for (long column : columns) {
			int x = ChunkPos.getPackedX(column);
			int z = ChunkPos.getPackedZ(column);
			if (!area.contains(x, z)) {
				continue;
			}
			int dx = x - center.getX();
			int dz = z - center.getZ();
			int gateDistance = Integer.MAX_VALUE;
			for (BlockPos gate : gates) {
				if (Math.abs(gate.getX() - x) <= 3 && Math.abs(gate.getZ() - z) <= 3) {
					gateDistance = Math.min(gateDistance, Math.max(Math.abs(gate.getX() - x), Math.abs(gate.getZ() - z)));
				}
			}
			boolean corner = Math.abs(dx) >= half - 1 && Math.abs(dz) >= half - 1;
			boolean water = Ground.isWater(world, x, z);
			int ground = Ground.floor(world, x, z);
			if (water && Ground.surface(world, x, z) - ground > 6) {
				continue;
			}
			int base = water ? Ground.surface(world, x, z) : ground;
			for (int y = 0; y < 6 && water; y++) {
				canvas.put(new BlockPos(x, base - y, z), Blueprints.FOUNDATION, Canvas.Mode.FILL);
			}
			if (gateDistance <= 1) {
				// The gateway itself: an arch over the road.
				canvas.put(new BlockPos(x, base + height + 1, z), stone ? palette.brick() : palette.log().getDefaultState(), Canvas.Mode.SET);
				continue;
			}
			if (gateDistance == 2) {
				for (int y = 1; y <= height + 2; y++) {
					canvas.put(new BlockPos(x, base + y, z), palette.brick(), Canvas.Mode.SET);
				}
				canvas.put(new BlockPos(x, base + height + 3, z), Blocks.LANTERN.getDefaultState(), Canvas.Mode.SET);
				continue;
			}
			int top = corner ? height + 3 : height;
			for (int y = 1; y <= top; y++) {
				BlockState block = stone ? (y == 1 ? Blueprints.FOUNDATION : palette.brick()) : palette.log().getDefaultState();
				canvas.put(new BlockPos(x, base + y, z), block, Canvas.Mode.SET);
			}
			if (stone && Math.floorMod(dx + dz, 2) == 0) {
				canvas.put(new BlockPos(x, base + top + 1, z), palette.brick(), Canvas.Mode.SET);
			} else if (!stone) {
				canvas.put(new BlockPos(x, base + top + 1, z), palette.fence().getDefaultState(), Canvas.Mode.SET);
			}
			for (int y = 0; y < 4; y++) {
				canvas.put(new BlockPos(x, base - y, z), Blueprints.FOUNDATION, Canvas.Mode.FILL);
			}
		}
	}

	/** A road between two towns, from the nearest gates, winding a little across the land. */
	private static LongSet roadColumns(long seed, TownLayout fromTown, int fromTier, TownLayout toTown, int toTier) {
		BlockPos fromCenter = fromTown.center();
		BlockPos toCenter = toTown.center();
		LongSet columns = new LongLinkedOpenHashSet();
		List<double[]> points = roadPoints(seed, fromTown, fromTier, toTown, toTier);
		int fromHalf = TownLayout.wallHalf(fromTier);
		int toHalf = TownLayout.wallHalf(toTier);
		for (int i = 0; i < points.size() - 1; i++) {
			double[] a = points.get(i);
			double[] b = points.get(i + 1);
			double distance = Math.max(1e-6, Math.hypot(b[0] - a[0], b[1] - a[1]));
			boolean alongX = Math.abs(b[0] - a[0]) > Math.abs(b[1] - a[1]);
			for (double s = 0; s <= distance; s += 0.5) {
				int x = (int) Math.round(a[0] + (b[0] - a[0]) * s / distance);
				int z = (int) Math.round(a[1] + (b[1] - a[1]) * s / distance);
				for (int w = -1; w <= 1; w++) {
					int cx = alongX ? x : x + w;
					int cz = alongX ? z + w : z;
					boolean insideFrom = Math.abs(cx - fromCenter.getX()) <= fromHalf && Math.abs(cz - fromCenter.getZ()) <= fromHalf;
					boolean insideTo = Math.abs(cx - toCenter.getX()) <= toHalf && Math.abs(cz - toCenter.getZ()) <= toHalf;
					if (!insideFrom && !insideTo) {
						columns.add(ChunkPos.toLong(cx, cz));
					}
				}
			}
		}
		return columns;
	}

	/** The bends of the road between two towns, gate to gate. */
	private static List<double[]> roadPoints(long seed, TownLayout fromTown, int fromTier, TownLayout toTown, int toTier) {
		BlockPos from = nearestGate(fromTown, fromTier, toTown.center());
		BlockPos to = nearestGate(toTown, toTier, fromTown.center());
		List<double[]> points = new ArrayList<>();
		if (from == null || to == null) {
			return points;
		}
		Random random = Random.create(seed * 31 + 7);
		double length = Math.sqrt(from.getSquaredDistance(to));
		int segments = Math.max(1, (int) (length / 40));
		double nx = -(to.getZ() - from.getZ()) / Math.max(1, length);
		double nz = (to.getX() - from.getX()) / Math.max(1, length);
		for (int i = 0; i <= segments; i++) {
			double t = i / (double) segments;
			double wobble = i == 0 || i == segments ? 0 : (random.nextDouble() - 0.5) * 20;
			points.add(new double[]{from.getX() + (to.getX() - from.getX()) * t + nx * wobble,
					from.getZ() + (to.getZ() - from.getZ()) * t + nz * wobble});
		}
		return points;
	}

	/**
	 * The way between two neighbouring towns, square to square: out along a street, down the road, in along
	 * the other town's street. Null if no road joins them.
	 */
	public static List<BlockPos> roadRoute(ServerWorld world, Settlement a, Settlement b) {
		Settlement child = a.parentId() == b.id() ? a : b.parentId() == a.id() ? b : null;
		if (child == null) {
			return null;
		}
		Settlement parent = child == a ? b : a;
		TownLayout parentLayout = TownLayout.of(world, parent);
		TownLayout childLayout = TownLayout.of(world, child);
		List<double[]> road = roadPoints(childLayout.seed(), parentLayout, parent.tier(), childLayout, child.tier());
		if (road.isEmpty()) {
			return null;
		}
		List<BlockPos> route = new ArrayList<>(throughStreet(parentLayout, parent.tier(), childLayout.center()));
		for (double[] p : road) {
			route.add(new BlockPos((int) Math.round(p[0]), 0, (int) Math.round(p[1])));
		}
		List<BlockPos> end = new ArrayList<>(throughStreet(childLayout, child.tier(), parentLayout.center()));
		java.util.Collections.reverse(end);
		route.addAll(end);
		if (child == a) {
			java.util.Collections.reverse(route);
		}
		return route;
	}

	/** From the square out along the street that leads towards {@code toward}, to its gate. */
	public static List<BlockPos> throughStreet(TownLayout town, int tier, BlockPos toward) {
		List<BlockPos> points = new ArrayList<>();
		points.add(town.center().withY(0));
		TownLayout.Street street = gateStreet(town, tier, toward);
		if (street == null) {
			return points;
		}
		int end = TownLayout.wallHalf(tier) + 3;
		for (int d = TownLayout.plazaHalf(tier) + 2; d < end; d += 6) {
			points.add(town.streetPoint(street, d));
		}
		points.add(town.streetPoint(street, end));
		return points;
	}

	private static TownLayout.Street gateStreet(TownLayout town, int tier, BlockPos toward) {
		int distance = TownLayout.wallHalf(tier) + 3;
		TownLayout.Street best = null;
		for (TownLayout.Street street : town.streets()) {
			if (best == null || town.streetPoint(street, distance).getSquaredDistance(toward.withY(0))
					< town.streetPoint(best, distance).getSquaredDistance(toward.withY(0))) {
				best = street;
			}
		}
		return best;
	}

	private static BlockPos nearestGate(TownLayout town, int tier, BlockPos toward) {
		int distance = TownLayout.wallHalf(tier) + 3;
		BlockPos best = null;
		for (TownLayout.Street street : town.streets()) {
			BlockPos gate = town.streetPoint(street, distance);
			if (best == null || gate.getSquaredDistance(toward) < best.getSquaredDistance(toward)) {
				best = gate;
			}
		}
		return best;
	}

	public static Direction facingToward(BlockPos from, BlockPos to) {
		int dx = to.getX() - from.getX();
		int dz = to.getZ() - from.getZ();
		if (Math.abs(dx) > Math.abs(dz)) {
			return dx > 0 ? Direction.EAST : Direction.WEST;
		}
		return dz > 0 ? Direction.SOUTH : Direction.NORTH;
	}
}
