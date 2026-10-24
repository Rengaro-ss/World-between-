package com.worldbetween.world;

import com.worldbetween.WorldBetween;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * Everything World Between remembers about a world. Saved with the overworld's data.
 */
public class WorldBetweenState extends PersistentState {
	private static final int MAX_CHRONICLE = 500;

	private static final Type<WorldBetweenState> TYPE = new Type<>(
			WorldBetweenState::new, WorldBetweenState::fromNbt, null);

	/** Number of simulated days since the mod started running in this world. */
	private long day;
	/** The overworld day number at which the last simulation step happened, or -1 if never. */
	private long lastWorldDay = -1;
	private final EnumMap<FactionType, Faction> factions = new EnumMap<>(FactionType.class);
	private final List<ChronicleEntry> chronicle = new ArrayList<>();

	public WorldBetweenState() {
		for (FactionType type : FactionType.values()) {
			factions.put(type, new Faction(type));
		}
	}

	public static WorldBetweenState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, WorldBetween.MOD_ID);
	}

	public long day() {
		return day;
	}

	public void advanceDay() {
		day++;
		markDirty();
	}

	public long lastWorldDay() {
		return lastWorldDay;
	}

	public void setLastWorldDay(long lastWorldDay) {
		this.lastWorldDay = lastWorldDay;
		markDirty();
	}

	public Faction faction(FactionType type) {
		return factions.get(type);
	}

	public Iterable<Faction> factions() {
		return factions.values();
	}

	public List<ChronicleEntry> chronicle() {
		return chronicle;
	}

	public void addChronicle(ChronicleEntry entry) {
		chronicle.add(entry);
		if (chronicle.size() > MAX_CHRONICLE) {
			chronicle.remove(0);
		}
		markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		nbt.putLong("Day", day);
		nbt.putLong("LastWorldDay", lastWorldDay);
		NbtCompound factionsNbt = new NbtCompound();
		factions.forEach((type, faction) -> factionsNbt.put(type.id(), faction.toNbt()));
		nbt.put("Factions", factionsNbt);
		NbtList chronicleNbt = new NbtList();
		chronicle.forEach(entry -> chronicleNbt.add(entry.toNbt()));
		nbt.put("Chronicle", chronicleNbt);
		return nbt;
	}

	private static WorldBetweenState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		WorldBetweenState state = new WorldBetweenState();
		state.day = nbt.getLong("Day");
		state.lastWorldDay = nbt.contains("LastWorldDay") ? nbt.getLong("LastWorldDay") : -1;
		NbtCompound factionsNbt = nbt.getCompound("Factions");
		for (FactionType type : FactionType.values()) {
			if (factionsNbt.contains(type.id())) {
				state.factions.put(type, Faction.fromNbt(type, factionsNbt.getCompound(type.id())));
			}
		}
		NbtList chronicleNbt = nbt.getList("Chronicle", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < chronicleNbt.size(); i++) {
			state.chronicle.add(ChronicleEntry.fromNbt(chronicleNbt.getCompound(i)));
		}
		return state;
	}
}
