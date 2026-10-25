package com.worldbetween.war;

import com.worldbetween.faction.FactionType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything the world remembers about its wars: the heat between each two peoples, who is in the field, where they fought. */
public class WarState {
	/** Old battlefields are forgotten (their remains stay in the world) once there are more than this. */
	private static final int MAX_FIELDS = 64;

	private final Map<String, War> wars = new LinkedHashMap<>();
	private final List<Warband> bands = new ArrayList<>();
	private final List<Battlefield> fields = new ArrayList<>();
	private int nextBand;
	private int nextField;

	public WarState() {
		FactionType[] all = FactionType.values();
		for (int i = 0; i < all.length; i++) {
			for (int j = i + 1; j < all.length; j++) {
				War war = new War(all[i], all[j]);
				wars.put(war.key(), war);
			}
		}
	}

	public War war(FactionType a, FactionType b) {
		return a == b ? null : wars.get(War.key(a, b));
	}

	public Iterable<War> wars() {
		return wars.values();
	}

	/** How hot the war between two peoples runs: 0 if they have not met (or are the same people). */
	public int level(FactionType a, FactionType b) {
		War war = war(a, b);
		return war == null ? 0 : war.level();
	}

	public List<Warband> bands() {
		return bands;
	}

	public Warband band(int id) {
		for (Warband band : bands) {
			if (band.id() == id) {
				return band;
			}
		}
		return null;
	}

	public int nextBandId() {
		return nextBand++;
	}

	public List<Battlefield> fields() {
		return fields;
	}

	public Battlefield addField(Battlefield.Kind kind, net.minecraft.util.math.BlockPos pos, long day, FactionType a, int fallenA,
			FactionType b, int fallenB) {
		Battlefield field = new Battlefield(nextField++, pos.withY(0), day, a, fallenA, b, fallenB, kind);
		fields.add(field);
		if (fields.size() > MAX_FIELDS) {
			fields.remove(0);
		}
		return field;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		NbtList warList = new NbtList();
		wars.values().forEach(war -> warList.add(war.toNbt()));
		nbt.put("Wars", warList);
		NbtList bandList = new NbtList();
		bands.forEach(band -> bandList.add(band.toNbt()));
		nbt.put("Bands", bandList);
		NbtList fieldList = new NbtList();
		fields.forEach(field -> fieldList.add(field.toNbt()));
		nbt.put("Fields", fieldList);
		nbt.putInt("NextBand", nextBand);
		nbt.putInt("NextField", nextField);
		return nbt;
	}

	public static WarState fromNbt(NbtCompound nbt) {
		WarState state = new WarState();
		NbtList warList = nbt.getList("Wars", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < warList.size(); i++) {
			War war = War.fromNbt(warList.getCompound(i));
			if (war != null) {
				state.wars.put(war.key(), war);
			}
		}
		NbtList bandList = nbt.getList("Bands", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < bandList.size(); i++) {
			Warband band = Warband.fromNbt(bandList.getCompound(i));
			if (band != null) {
				state.bands.add(band);
			}
		}
		NbtList fieldList = nbt.getList("Fields", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < fieldList.size(); i++) {
			Battlefield field = Battlefield.fromNbt(fieldList.getCompound(i));
			if (field != null) {
				state.fields.add(field);
			}
		}
		state.nextBand = nbt.getInt("NextBand");
		state.nextField = nbt.getInt("NextField");
		return state;
	}
}
