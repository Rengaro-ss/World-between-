package com.worldbetween.war;

import com.worldbetween.faction.FactionType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * The way a war party of the Neris or the Kaments went: their builder lays a path where there was none,
 * bridges the water and cuts steps up the slopes. It stays after them: whoever comes across it can tell that
 * someone has been this way.
 */
public record Trail(int id, FactionType faction, BlockPos from, BlockPos to, int width, long day) {
	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Id", id);
		nbt.putString("Faction", faction.id());
		nbt.putLong("From", from.asLong());
		nbt.putLong("To", to.asLong());
		nbt.putInt("Width", width);
		nbt.putLong("Day", day);
		return nbt;
	}

	public static Trail fromNbt(NbtCompound nbt) {
		FactionType faction = FactionType.byId(nbt.getString("Faction"));
		if (faction == null) {
			return null;
		}
		return new Trail(nbt.getInt("Id"), faction, BlockPos.fromLong(nbt.getLong("From")), BlockPos.fromLong(nbt.getLong("To")),
				nbt.getInt("Width"), nbt.getLong("Day"));
	}
}
