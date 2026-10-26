package com.worldbetween.faction.arki;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * A comet on its way down, or a capsule on its way across: decided by the simulation, it comes down at
 * night, in a blue and violet streak across the sky. Whatever has not fallen by morning falls unseen.
 */
public class Comet {
	public enum Kind {
		/** Brings exiles to found a new colony on the sea. */
		SEA_COLONY,
		/** Brings exiles to found a colony on an island in the sky. */
		SKY_COLONY,
		/** Brings more exiles to a colony that called them with its starlight. */
		ARRIVALS,
		/** A capsule from one colony to another, with what the other is short of. */
		HELP,
		/** One of a comet storm called down on an enemy town: it bursts where it falls. */
		STRIKE
	}

	private final int id;
	private final Kind kind;
	private final BlockPos target;
	private final BlockPos from;
	private final int colony;
	private final int people;
	private final String resource;
	private final long amount;

	public Comet(int id, Kind kind, BlockPos target, BlockPos from, int colony, int people, String resource, long amount) {
		this.id = id;
		this.kind = kind;
		this.target = target;
		this.from = from;
		this.colony = colony;
		this.people = people;
		this.resource = resource;
		this.amount = amount;
	}

	public int id() {
		return id;
	}

	public Kind kind() {
		return kind;
	}

	public BlockPos target() {
		return target;
	}

	/** For a capsule between colonies: where it was launched; null for a comet out of the sky. */
	public BlockPos from() {
		return from;
	}

	/** The colony it is bound for, or -1 for a new one. */
	public int colony() {
		return colony;
	}

	public int people() {
		return people;
	}

	public String resource() {
		return resource;
	}

	public long amount() {
		return amount;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Id", id);
		nbt.putString("Kind", kind.name());
		nbt.putLong("Target", target.asLong());
		if (from != null) {
			nbt.putLong("From", from.asLong());
		}
		nbt.putInt("Colony", colony);
		nbt.putInt("People", people);
		nbt.putString("Resource", resource == null ? "" : resource);
		nbt.putLong("Amount", amount);
		return nbt;
	}

	public static Comet fromNbt(NbtCompound nbt) {
		Kind kind;
		try {
			kind = Kind.valueOf(nbt.getString("Kind"));
		} catch (IllegalArgumentException e) {
			return null;
		}
		return new Comet(nbt.getInt("Id"), kind, BlockPos.fromLong(nbt.getLong("Target")),
				nbt.contains("From") ? BlockPos.fromLong(nbt.getLong("From")) : null, nbt.getInt("Colony"), nbt.getInt("People"),
				nbt.getString("Resource"), nbt.getLong("Amount"));
	}
}
