package com.worldbetween.faction;

import net.minecraft.nbt.NbtCompound;

/**
 * Where in the town a building stands. Assigned once when the building is finished, so a growing town
 * never shuffles its existing buildings around.
 */
public class Plot {
	private final BuildingType type;
	private final int slot;
	private int level;

	public Plot(BuildingType type, int slot, int level) {
		this.type = type;
		this.slot = slot;
		this.level = level;
	}

	public BuildingType type() {
		return type;
	}

	/** Index into the town layout's slots. */
	public int slot() {
		return slot;
	}

	/** Size of the building; for houses, how many people it holds. */
	public int level() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putString("Type", type.id());
		nbt.putInt("Slot", slot);
		nbt.putInt("Level", level);
		return nbt;
	}

	public static Plot fromNbt(NbtCompound nbt) {
		BuildingType type = BuildingType.byId(nbt.getString("Type"));
		return type == null ? null : new Plot(type, nbt.getInt("Slot"), nbt.getInt("Level"));
	}
}
