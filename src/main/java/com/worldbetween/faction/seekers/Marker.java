package com.worldbetween.faction.seekers;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * A sign left by a seeker in the world: a cairn with the kingdom's banner where a town could stand,
 * a survey stake where a mine should be dug.
 */
public record Marker(int id, Type type, BlockPos pos, boolean retired) {
	public enum Type {
		CAIRN, STAKE
	}

	public Marker(int id, Type type, BlockPos pos) {
		this(id, type, pos, false);
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Id", id);
		nbt.putString("Type", type.name());
		nbt.putLong("Pos", pos.asLong());
		nbt.putBoolean("Retired", retired);
		return nbt;
	}

	public static Marker fromNbt(NbtCompound nbt) {
		try {
			return new Marker(nbt.getInt("Id"), Type.valueOf(nbt.getString("Type")), BlockPos.fromLong(nbt.getLong("Pos")),
					nbt.getBoolean("Retired"));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
