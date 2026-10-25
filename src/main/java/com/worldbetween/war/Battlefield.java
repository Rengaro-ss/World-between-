package com.worldbetween.war;

import com.worldbetween.faction.FactionType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Where a fight was. The fallen lie where they fell: what they carried is there for whoever comes to pick
 * it over. Built into the world once, like a town, when its chunk is loaded.
 */
public record Battlefield(int id, BlockPos pos, long day, FactionType a, int fallenA, FactionType b, int fallenB, Kind kind) {
	public enum Kind {
		/** Two patrols, an ambush on the road. */
		SKIRMISH,
		/** Two armies in the field. */
		BATTLE,
		/** Under the walls of a town. */
		SIEGE
	}

	public int fallen() {
		return fallenA + fallenB;
	}

	public int fallen(FactionType side) {
		return side == a ? fallenA : side == b ? fallenB : 0;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Id", id);
		nbt.putLong("Pos", pos.asLong());
		nbt.putLong("Day", day);
		nbt.putString("A", a.id());
		nbt.putInt("FallenA", fallenA);
		nbt.putString("B", b.id());
		nbt.putInt("FallenB", fallenB);
		nbt.putString("Kind", kind.name());
		return nbt;
	}

	public static Battlefield fromNbt(NbtCompound nbt) {
		FactionType a = FactionType.byId(nbt.getString("A"));
		FactionType b = FactionType.byId(nbt.getString("B"));
		Kind kind;
		try {
			kind = Kind.valueOf(nbt.getString("Kind"));
		} catch (IllegalArgumentException e) {
			return null;
		}
		if (a == null || b == null) {
			return null;
		}
		return new Battlefield(nbt.getInt("Id"), BlockPos.fromLong(nbt.getLong("Pos")), nbt.getLong("Day"), a, nbt.getInt("FallenA"),
				b, nbt.getInt("FallenB"), kind);
	}
}
