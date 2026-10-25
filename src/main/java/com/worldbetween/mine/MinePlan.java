package com.worldbetween.mine;

import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The full tunnel layout of a mine, generated deterministically from its seed.
 *
 * <p>The plan is an ordered list of segments. Digging walks through it one step (one slice of tunnel)
 * at a time, so "how far the mine has got" is a single number. Segments are ordered shaft first, then
 * level by level in breadth-first order, which makes a mine grow outward the way a real one would.
 */
public final class MinePlan {
	/** No tunnel reaches further than this from the shaft, in blocks. */
	private static final int RADIUS = 80;
	private static final int[] DEEP_LEVELS = {4, -44};

	public enum Kind {
		/** Vertical 3×3 shaft with ladders. */
		SHAFT(-1, 1, 3),
		/** Main haulage drift: 3 wide, 3 high, rails and timber supports. */
		DRIFT(-1, 1, 3),
		/** Narrow working branch that follows the ore: 2 wide, 2 high. */
		BRANCH(0, 1, 2),
		/** Deep drift with a lava channel along its left side: 5 wide. */
		LAVA_DRIFT(-2, 2, 3),
		/** Ore store next to the shaft on every level. */
		ROOM(-3, 3, 4),
		/** Underground forge at the end of the lava drift. */
		FORGE(-4, 4, 5);

		private final int minOffset;
		private final int maxOffset;
		private final int height;

		Kind(int minOffset, int maxOffset, int height) {
			this.minOffset = minOffset;
			this.maxOffset = maxOffset;
			this.height = height;
		}

		public int minOffset() {
			return minOffset;
		}

		public int maxOffset() {
			return maxOffset;
		}

		public int height() {
			return height;
		}
	}

	/**
	 * One straight piece of tunnel. Slice {@code s} of a horizontal segment is centred on
	 * {@code origin + dir * s} at floor level; for the shaft it is the 3×3 layer at {@code origin.y - s}.
	 */
	public record Segment(int index, Kind kind, BlockPos origin, Direction dir, int length,
			int parent, int parentStep, int level, int depth, int startStep) {
		public int endStep() {
			return startStep + length;
		}

		public boolean isShaft() {
			return kind == Kind.SHAFT;
		}

		/** Horizontal direction across the tunnel; offsets are measured along it. */
		public Direction across() {
			return isShaft() ? Direction.EAST : dir.rotateYClockwise();
		}

		public BlockPos sliceCenter(int s) {
			return isShaft() ? origin.down(s) : origin.offset(dir, s);
		}

		/** Every block inside slice {@code s}. */
		public List<BlockPos> sliceBlocks(int s) {
			List<BlockPos> blocks = new ArrayList<>();
			BlockPos center = sliceCenter(s);
			if (isShaft()) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						blocks.add(center.add(dx, 0, dz));
					}
				}
				return blocks;
			}
			Direction across = across();
			for (int o = kind.minOffset(); o <= kind.maxOffset(); o++) {
				for (int y = 0; y < kind.height(); y++) {
					blocks.add(center.offset(across, o).up(y));
				}
			}
			return blocks;
		}

		/** Bounding box of slices {@code [from, to)}, grown by one block for walls. */
		public BlockBox box(int from, int to) {
			BlockPos a = sliceCenter(from);
			BlockPos b = sliceCenter(Math.max(from, to - 1));
			int minX = Math.min(a.getX(), b.getX()) - 5;
			int maxX = Math.max(a.getX(), b.getX()) + 5;
			int minZ = Math.min(a.getZ(), b.getZ()) - 5;
			int maxZ = Math.max(a.getZ(), b.getZ()) + 5;
			int minY = Math.min(a.getY(), b.getY()) - 2;
			int maxY = Math.max(a.getY(), b.getY()) + kind.height() + 1;
			return new BlockBox(minX, minY, minZ, maxX, maxY, maxZ);
		}

		/** Sideways offset of {@code pos} from the tunnel's centre line. */
		public int offsetAt(BlockPos pos) {
			Direction across = across();
			return (pos.getX() - origin.getX()) * across.getOffsetX() + (pos.getZ() - origin.getZ()) * across.getOffsetZ();
		}

		/** Which slice of this segment {@code pos} lies in, or -1 if it is outside the tunnel. */
		public int sliceAt(BlockPos pos) {
			if (isShaft()) {
				int s = origin.getY() - pos.getY();
				boolean inside = Math.abs(pos.getX() - origin.getX()) <= 1 && Math.abs(pos.getZ() - origin.getZ()) <= 1;
				return inside && s >= 0 && s < length ? s : -1;
			}
			int dx = pos.getX() - origin.getX();
			int dz = pos.getZ() - origin.getZ();
			int along = dx * dir.getOffsetX() + dz * dir.getOffsetZ();
			Direction across = across();
			int offset = dx * across.getOffsetX() + dz * across.getOffsetZ();
			int dy = pos.getY() - origin.getY();
			boolean inside = along >= 0 && along < length
					&& offset >= kind.minOffset() && offset <= kind.maxOffset()
					&& dy >= 0 && dy < kind.height();
			return inside ? along : -1;
		}
	}

	private final List<Segment> segments;
	private final int[] levelEnds;
	private final int[] levelFloors;
	private final Map<ChunkPos, Integer> chunkFirstStep;
	private final BlockBox bounds;

	private MinePlan(List<Segment> segments, int[] levelEnds, int[] levelFloors) {
		this.segments = List.copyOf(segments);
		this.levelEnds = levelEnds;
		this.levelFloors = levelFloors;
		Map<ChunkPos, Integer> chunks = new LinkedHashMap<>();
		BlockBox all = null;
		for (Segment segment : segments) {
			BlockBox box = segment.box(0, segment.length());
			all = all == null ? box : all.encompass(box);
			for (int cx = box.getMinX() >> 4; cx <= box.getMaxX() >> 4; cx++) {
				for (int cz = box.getMinZ() >> 4; cz <= box.getMaxZ() >> 4; cz++) {
					chunks.putIfAbsent(new ChunkPos(cx, cz), segment.startStep());
				}
			}
		}
		this.chunkFirstStep = Collections.unmodifiableMap(chunks);
		this.bounds = all;
	}

	public List<Segment> segments() {
		return segments;
	}

	public int totalSteps() {
		return segments.isEmpty() ? 0 : segments.get(segments.size() - 1).endStep();
	}

	/** Last step of level {@code level} (0-based); digging deeper needs a later era. */
	public int levelEnd(int level) {
		return levelEnds[Math.min(level, levelEnds.length - 1)];
	}

	public int levelFloor(int level) {
		return levelFloors[level];
	}

	public int levelCount() {
		return levelFloors.length;
	}

	/** Chunks the mine passes through, with the first step that touches each. */
	public Map<ChunkPos, Integer> chunks() {
		return chunkFirstStep;
	}

	public BlockBox bounds() {
		return bounds;
	}

	/** The segment that step {@code step} belongs to, or null past the end. */
	public Segment segmentAt(int step) {
		int lo = 0;
		int hi = segments.size() - 1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			Segment segment = segments.get(mid);
			if (step < segment.startStep()) {
				hi = mid - 1;
			} else if (step >= segment.endStep()) {
				lo = mid + 1;
			} else {
				return segment;
			}
		}
		return null;
	}

	/** The working face: where the miners are digging at {@code step}. */
	public BlockPos faceAt(int step) {
		Segment segment = segmentAt(Math.max(0, Math.min(step, totalSteps() - 1)));
		return segment == null ? null : segment.sliceCenter(step - segment.startStep()).up();
	}

	public static MinePlan generate(long seed, BlockPos entrance) {
		Random random = Random.create(seed);
		Builder builder = new Builder();
		int top = entrance.getY();
		int firstLevel = Math.max(DEEP_LEVELS[0] + 16, Math.min(top - 24, 44));
		int[] floors = {firstLevel, DEEP_LEVELS[0], DEEP_LEVELS[1]};
		int[] ends = new int[floors.length];
		int shaftTop = top;
		BlockPos axis = entrance;

		for (int level = 0; level < floors.length; level++) {
			int floor = floors[level];
			builder.add(Kind.SHAFT, new BlockPos(axis.getX(), shaftTop, axis.getZ()), Direction.DOWN,
					shaftTop - floor + 1, -1, 0, level, 0);
			shaftTop = floor - 1;
			BlockPos center = new BlockPos(axis.getX(), floor, axis.getZ());

			List<Direction> directions = new ArrayList<>(Direction.Type.HORIZONTAL.stream().toList());
			Collections.shuffle(directions, new java.util.Random(random.nextLong()));
			Direction roomDirection = directions.remove(0);
			builder.add(Kind.ROOM, center.offset(roomDirection, 2), roomDirection, 7, -1, 0, level, 0);

			Deque<Segment> queue = new ArrayDeque<>();
			for (int i = 0; i < directions.size(); i++) {
				Direction direction = directions.get(i);
				Kind kind = level == floors.length - 1 && i == 0 ? Kind.LAVA_DRIFT : Kind.DRIFT;
				Segment drift = builder.addClipped(kind, center.offset(direction, 2), direction,
						24 + random.nextInt(17), -1, 0, level, 0, axis);
				if (drift != null) {
					queue.add(drift);
				}
			}

			while (!queue.isEmpty()) {
				Segment drift = queue.poll();
				int half = drift.kind().maxOffset();
				boolean right = random.nextBoolean();
				for (int s = 5 + random.nextInt(4); s < drift.length() - 3; s += 6 + random.nextInt(5)) {
					// Never open a branch across the lava channel, which runs along the left wall.
					if (drift.kind() == Kind.LAVA_DRIFT) {
						right = true;
					}
					Direction side = right ? drift.dir().rotateYClockwise() : drift.dir().rotateYCounterclockwise();
					right = !right;
					builder.addClipped(Kind.BRANCH, drift.sliceCenter(s).offset(side, half + 1), side,
							6 + random.nextInt(9), drift.index(), s, level, drift.depth() + 1, axis);
				}
				if (drift.kind() == Kind.LAVA_DRIFT) {
					builder.add(Kind.FORGE, drift.sliceCenter(drift.length()), drift.dir(), 9,
							drift.index(), drift.length() - 1, level, drift.depth() + 1);
				} else if (drift.depth() < 2 && random.nextFloat() < 0.65f) {
					Direction turn = random.nextBoolean() ? drift.dir().rotateYClockwise() : drift.dir().rotateYCounterclockwise();
					Segment next = builder.addClipped(Kind.DRIFT, drift.sliceCenter(drift.length() - 1).offset(turn, 2), turn,
							16 + random.nextInt(17), drift.index(), drift.length() - 1, level, drift.depth() + 1, axis);
					if (next != null) {
						queue.add(next);
					}
				}
			}
			ends[level] = builder.step;
		}
		return new MinePlan(builder.segments, ends, floors);
	}

	private static final class Builder {
		private final List<Segment> segments = new ArrayList<>();
		private int step;

		Segment add(Kind kind, BlockPos origin, Direction dir, int length, int parent, int parentStep, int level, int depth) {
			Segment segment = new Segment(segments.size(), kind, origin, dir, length, parent, parentStep, level, depth, step);
			segments.add(segment);
			step += length;
			return segment;
		}

		/** Like {@link #add}, but shortens the segment so it stays within {@link #RADIUS} of the shaft. */
		Segment addClipped(Kind kind, BlockPos origin, Direction dir, int length, int parent, int parentStep,
				int level, int depth, BlockPos axis) {
			int clipped = 0;
			while (clipped < length) {
				BlockPos pos = origin.offset(dir, clipped);
				if (Math.abs(pos.getX() - axis.getX()) > RADIUS || Math.abs(pos.getZ() - axis.getZ()) > RADIUS) {
					break;
				}
				clipped++;
			}
			return clipped < 4 ? null : add(kind, origin, dir, clipped, parent, parentStep, level, depth);
		}
	}
}
