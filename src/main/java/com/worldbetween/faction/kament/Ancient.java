package com.worldbetween.faction.kament;

import net.minecraft.nbt.NbtCompound;

/**
 * The Ancient of one Kament settlement. Each holds a seat in the council; there is no king.
 */
public class Ancient {
	private final int id;
	private final String name;
	private int seat;
	private final KamentTrait first;
	private final KamentTrait second;
	private boolean alive = true;

	public Ancient(int id, String name, int seat, KamentTrait first, KamentTrait second) {
		this.id = id;
		this.name = name;
		this.seat = seat;
		this.first = first;
		this.second = second;
	}

	public int id() {
		return id;
	}

	public String name() {
		return name;
	}

	/** The settlement whose Ancient this is. */
	public int seat() {
		return seat;
	}

	public void setSeat(int seat) {
		this.seat = seat;
	}

	public KamentTrait first() {
		return first;
	}

	public KamentTrait second() {
		return second;
	}

	public boolean has(KamentTrait trait) {
		return first == trait || second == trait;
	}

	public boolean isAlive() {
		return alive;
	}

	public void die() {
		alive = false;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Id", id);
		nbt.putString("Name", name);
		nbt.putInt("Seat", seat);
		nbt.putString("First", first.id());
		nbt.putString("Second", second.id());
		nbt.putBoolean("Alive", alive);
		return nbt;
	}

	public static Ancient fromNbt(NbtCompound nbt) {
		KamentTrait first = KamentTrait.byId(nbt.getString("First"));
		KamentTrait second = KamentTrait.byId(nbt.getString("Second"));
		Ancient ancient = new Ancient(nbt.getInt("Id"), nbt.getString("Name"), nbt.getInt("Seat"),
				first == null ? KamentTrait.PATIENT : first, second == null ? KamentTrait.MINDFUL : second);
		ancient.alive = nbt.getBoolean("Alive");
		return ancient;
	}
}
