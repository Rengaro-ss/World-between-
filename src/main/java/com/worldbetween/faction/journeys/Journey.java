package com.worldbetween.faction.journeys;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * People of the kingdom on the road: a patrol between two towns, a merchant caravan, settlers on their way
 * to found a town. In the simulation they move along their route at a steady pace; a player who comes
 * near meets them wherever they have got to. While they are out, they are not at home.
 */
public class Journey {
	public enum Kind {
		/** Two guards walking the road to a neighbouring town and back. */
		PATROL("patrol", 2, 300),
		/** A merchant, a carter and a guard taking goods to a neighbouring market and back. */
		CARAVAN("caravan", 3, 220),
		/** Settlers off to found a new town where a seeker has been. */
		SETTLERS("settlers", 6, 160);

		private final String id;
		private final int people;
		private final int speed;

		Kind(String id, int people, int speed) {
			this.id = id;
			this.people = people;
			this.speed = speed;
		}

		public String id() {
			return id;
		}

		public int people() {
			return people;
		}

		/** Blocks covered in a day. */
		public int speed() {
			return speed;
		}
	}

	private final int id;
	private final Kind kind;
	private final int from;
	private final int to;
	/** Settlers: the name of the town they will found. */
	private final String townName;
	private List<BlockPos> route;
	private boolean returning;
	/** Blocks along the route already behind them at time {@code departed}. */
	private double progress;
	private double departed;

	public Journey(int id, Kind kind, int from, int to, String townName, List<BlockPos> route, double now) {
		this.id = id;
		this.kind = kind;
		this.from = from;
		this.to = to;
		this.townName = townName;
		this.route = List.copyOf(route);
		this.departed = now;
	}

	public int id() {
		return id;
	}

	public Kind kind() {
		return kind;
	}

	/** The settlement they set out from, and return to. */
	public int from() {
		return from;
	}

	/** The settlement they are going to, or -1 for settlers bound for a new site. */
	public int to() {
		return to;
	}

	public String townName() {
		return townName;
	}

	public boolean isReturning() {
		return returning;
	}

	public List<BlockPos> route() {
		return route;
	}

	public BlockPos destination() {
		return route.get(route.size() - 1);
	}

	public double length() {
		double length = 0;
		for (int i = 1; i < route.size(); i++) {
			length += distance(route.get(i - 1), route.get(i));
		}
		return length;
	}

	/** How far along the route they are at time {@code now}, in blocks. */
	public double travelled(double now) {
		return Math.min(length(), progress + Math.max(0, now - departed) * kind.speed());
	}

	public BlockPos position(double now) {
		return at(travelled(now));
	}

	/** A point {@code distance} blocks along the route. */
	public BlockPos at(double distance) {
		double left = distance;
		for (int i = 1; i < route.size(); i++) {
			BlockPos a = route.get(i - 1);
			BlockPos b = route.get(i);
			double leg = distance(a, b);
			if (left <= leg || i == route.size() - 1) {
				double t = leg < 1e-6 ? 1 : Math.min(1, left / leg);
				return new BlockPos((int) Math.round(a.getX() + (b.getX() - a.getX()) * t), 0,
						(int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t));
			}
			left -= leg;
		}
		return route.get(route.size() - 1).withY(0);
	}

	public boolean hasArrived(double now) {
		return travelled(now) >= length() - 1;
	}

	/** Picks the journey up from where they actually are, after walking it in person. */
	public void resumeFrom(BlockPos at, double now) {
		double best = Double.MAX_VALUE;
		double along = 0;
		double bestAlong = progress;
		for (int i = 1; i < route.size(); i++) {
			BlockPos a = route.get(i - 1);
			BlockPos b = route.get(i);
			double leg = distance(a, b);
			double dx = b.getX() - a.getX();
			double dz = b.getZ() - a.getZ();
			double t = leg < 1e-6 ? 0 : Math.max(0, Math.min(1, ((at.getX() - a.getX()) * dx + (at.getZ() - a.getZ()) * dz) / (leg * leg)));
			double px = a.getX() + dx * t - at.getX();
			double pz = a.getZ() + dz * t - at.getZ();
			double d = px * px + pz * pz;
			if (d < best) {
				best = d;
				bestAlong = along + leg * t;
			}
			along += leg;
		}
		// Never backwards: walking in person only ever gets them further.
		progress = Math.max(travelled(now), bestAlong);
		departed = now;
	}

	/** The way home, back along the same road. */
	public void turnBack(double now) {
		List<BlockPos> reversed = new ArrayList<>(route);
		Collections.reverse(reversed);
		route = List.copyOf(reversed);
		returning = true;
		progress = 0;
		departed = now;
	}

	private static double distance(BlockPos a, BlockPos b) {
		return Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Id", id);
		nbt.putString("Kind", kind.name());
		nbt.putInt("From", from);
		nbt.putInt("To", to);
		if (townName != null) {
			nbt.putString("Town", townName);
		}
		nbt.putLongArray("Route", route.stream().mapToLong(BlockPos::asLong).toArray());
		nbt.putBoolean("Returning", returning);
		nbt.putDouble("Progress", progress);
		nbt.putDouble("Departed", departed);
		return nbt;
	}

	public static Journey fromNbt(NbtCompound nbt) {
		Kind kind;
		try {
			kind = Kind.valueOf(nbt.getString("Kind"));
		} catch (IllegalArgumentException e) {
			return null;
		}
		List<BlockPos> route = new ArrayList<>();
		for (long pos : nbt.getLongArray("Route")) {
			route.add(BlockPos.fromLong(pos));
		}
		if (route.size() < 2) {
			return null;
		}
		Journey journey = new Journey(nbt.getInt("Id"), kind, nbt.getInt("From"), nbt.getInt("To"),
				nbt.contains("Town") ? nbt.getString("Town") : null, route, nbt.getDouble("Departed"));
		journey.returning = nbt.getBoolean("Returning");
		journey.progress = nbt.getDouble("Progress");
		return journey;
	}
}
