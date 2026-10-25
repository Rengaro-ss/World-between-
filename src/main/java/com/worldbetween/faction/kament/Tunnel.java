package com.worldbetween.faction.kament;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * A deep tunnel the Kaments are driving from one underground city towards the place of the next.
 * It winds and rises and falls with the rock; the stretch already dug is real, and grows day by day.
 * When it is through, a new city is founded at its end.
 */
public class Tunnel {
	/** Distance between the points the tunnel bends at. */
	public static final int STEP = 8;

	private final int id;
	private final int from;
	private final BlockPos start;
	private final BlockPos end;
	private final long seed;
	private int to = -1;
	private double dug;
	private List<BlockPos> path;

	public Tunnel(int id, int from, BlockPos start, BlockPos end, long seed) {
		this.id = id;
		this.from = from;
		this.start = start;
		this.end = end;
		this.seed = seed;
	}

	public int id() {
		return id;
	}

	public int from() {
		return from;
	}

	public BlockPos start() {
		return start;
	}

	public BlockPos end() {
		return end;
	}

	public long seed() {
		return seed;
	}

	/** The city at the far end, once it is founded; -1 while digging. */
	public int to() {
		return to;
	}

	public void setTo(int to) {
		this.to = to;
	}

	public boolean isThrough() {
		return dug >= length();
	}

	public double dug() {
		return dug;
	}

	public void dig(double blocks) {
		dug = Math.min(length(), dug + blocks);
	}

	/** The bends of the tunnel, start to end; worked out from the seed, so always the same. */
	public List<BlockPos> path() {
		if (path == null) {
			Random random = Random.create(seed);
			List<BlockPos> points = new ArrayList<>();
			points.add(start);
			double x = start.getX();
			double y = start.getY();
			double z = start.getZ();
			double wander = 0;
			for (int i = 0; i < 400; i++) {
				double dx = end.getX() - x;
				double dz = end.getZ() - z;
				double remaining = Math.hypot(dx, dz);
				if (remaining < STEP * 1.5) {
					break;
				}
				// Head for the end, but never straight: the old rock decides the way.
				wander = Math.max(-0.9, Math.min(0.9, wander + (random.nextDouble() - 0.5) * 0.5));
				double angle = Math.atan2(dz, dx) + wander;
				x += Math.cos(angle) * STEP;
				z += Math.sin(angle) * STEP;
				double steps = remaining / STEP;
				y += (end.getY() - y) / Math.max(1, steps) + (random.nextInt(3) - 1);
				points.add(new BlockPos((int) Math.round(x), (int) Math.round(y), (int) Math.round(z)));
			}
			points.add(end);
			path = points;
		}
		return path;
	}

	public double length() {
		List<BlockPos> points = path();
		double length = 0;
		for (int i = 1; i < points.size(); i++) {
			length += Math.sqrt(points.get(i - 1).getSquaredDistance(points.get(i)));
		}
		return length;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Id", id);
		nbt.putInt("From", from);
		nbt.putInt("To", to);
		nbt.putLong("Start", start.asLong());
		nbt.putLong("End", end.asLong());
		nbt.putLong("Seed", seed);
		nbt.putDouble("Dug", dug);
		return nbt;
	}

	public static Tunnel fromNbt(NbtCompound nbt) {
		Tunnel tunnel = new Tunnel(nbt.getInt("Id"), nbt.getInt("From"), BlockPos.fromLong(nbt.getLong("Start")),
				BlockPos.fromLong(nbt.getLong("End")), nbt.getLong("Seed"));
		tunnel.to = nbt.getInt("To");
		tunnel.dug = nbt.getDouble("Dug");
		return tunnel;
	}
}
