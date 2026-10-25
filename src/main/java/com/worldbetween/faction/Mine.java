package com.worldbetween.faction;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * A faction mine. Its tunnels are generated from {@link #seed()}, so only the seed and the number
 * of dug steps are stored; the actual geometry is rebuilt on demand.
 */
public class Mine {
	private final long seed;
	/** The ground block above the shaft. */
	private final BlockPos entrance;
	/** Steps dug as of the start of the current day. */
	private int previous;
	/** Steps that will be dug by the end of the current day. */
	private int target;
	/** Segments that ran into something they must not destroy, mapped to the step where they stopped. */
	private final Map<Integer, Integer> stops = new HashMap<>();

	public Mine(long seed, BlockPos entrance) {
		this.seed = seed;
		this.entrance = entrance;
	}

	public long seed() {
		return seed;
	}

	public BlockPos entrance() {
		return entrance;
	}

	public int previous() {
		return previous;
	}

	public int target() {
		return target;
	}

	/** Schedules {@code steps} more digging for the coming day, never past {@code limit}. */
	public void dig(int steps, int limit) {
		previous = target;
		target = Math.max(target, Math.min(limit, target + steps));
	}

	/** How far the miners have got right now, given how much of the day has passed. */
	public int progress(float dayFraction) {
		return previous + Math.round((target - previous) * Math.min(1f, Math.max(0f, dayFraction)));
	}

	public Integer stopOf(int segment) {
		return stops.get(segment);
	}

	public Map<Integer, Integer> stops() {
		return java.util.Collections.unmodifiableMap(stops);
	}

	public void stop(int segment, int step) {
		stops.merge(segment, step, Math::min);
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putLong("Seed", seed);
		nbt.putLong("Entrance", entrance.asLong());
		nbt.putInt("Previous", previous);
		nbt.putInt("Target", target);
		NbtCompound stopsNbt = new NbtCompound();
		stops.forEach((segment, step) -> stopsNbt.putInt(Integer.toString(segment), step));
		nbt.put("Stops", stopsNbt);
		return nbt;
	}

	public static Mine fromNbt(NbtCompound nbt) {
		Mine mine = new Mine(nbt.getLong("Seed"), BlockPos.fromLong(nbt.getLong("Entrance")));
		mine.previous = nbt.getInt("Previous");
		mine.target = nbt.getInt("Target");
		NbtCompound stopsNbt = nbt.getCompound("Stops");
		for (String key : stopsNbt.getKeys()) {
			try {
				mine.stops.put(Integer.parseInt(key), stopsNbt.getInt(key));
			} catch (NumberFormatException ignored) {
				// Skip malformed entries rather than failing the whole load.
			}
		}
		return mine;
	}
}
