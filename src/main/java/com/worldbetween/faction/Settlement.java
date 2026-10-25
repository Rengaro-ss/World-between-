package com.worldbetween.faction;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A single settlement of a faction: its location, people and buildings.
 */
public class Settlement {
	private final int id;
	private final String name;
	private final BlockPos pos;
	private final RegistryKey<World> dimension;
	private final long foundedDay;
	private int population;
	private int tier;
	private final EnumMap<BuildingType, Integer> buildings = new EnumMap<>(BuildingType.class);
	private BuildingType project;
	private int projectProgress;
	private Mine mine;
	/** The settlement this one was founded from, or -1 for the first one. Roads run between them. */
	private int parentId = -1;
	private final List<Plot> plots = new ArrayList<>();
	/** The town's own treasury in coins, which decides how many barons it can keep. */
	private long coins;
	/** Where a seeker found the best ground for this town's mine, or null. */
	private BlockPos mineSite;
	/** Named numbers a faction keeps about a settlement of its own kind (the Kaments' halls, sculptors, souls). */
	private final Map<String, Long> counters = new java.util.HashMap<>();
	/** Buildings that had a full crew on the last simulated day; not saved, rebuilt every day. */
	private final EnumMap<BuildingType, Integer> staffed = new EnumMap<>(BuildingType.class);

	public Settlement(int id, String name, BlockPos pos, RegistryKey<World> dimension, long foundedDay) {
		this.id = id;
		this.name = name;
		this.pos = pos;
		this.dimension = dimension;
		this.foundedDay = foundedDay;
	}

	public int id() {
		return id;
	}

	public String name() {
		return name;
	}

	public BlockPos pos() {
		return pos;
	}

	public RegistryKey<World> dimension() {
		return dimension;
	}

	public long foundedDay() {
		return foundedDay;
	}

	public int population() {
		return population;
	}

	public void setPopulation(int population) {
		this.population = Math.max(0, population);
	}

	public int tier() {
		return tier;
	}

	public void setTier(int tier) {
		this.tier = tier;
	}

	public int buildingCount(BuildingType type) {
		return buildings.getOrDefault(type, 0);
	}

	public int totalBuildings() {
		return buildings.values().stream().mapToInt(Integer::intValue).sum();
	}

	public void addBuilding(BuildingType type) {
		buildings.merge(type, 1, Integer::sum);
	}

	public Map<BuildingType, Integer> buildings() {
		return buildings;
	}

	public BuildingType project() {
		return project;
	}

	public int projectProgress() {
		return projectProgress;
	}

	public void startProject(BuildingType type) {
		this.project = type;
		this.projectProgress = 0;
	}

	/** Advances the current project; returns true when it is finished. */
	public boolean advanceProject(int work) {
		if (project == null) {
			return false;
		}
		projectProgress += work;
		if (projectProgress >= project.buildDays()) {
			addBuilding(project);
			project = null;
			projectProgress = 0;
			return true;
		}
		return false;
	}

	/** The settlement's mine, or null until its first mine is built. */
	public Mine mine() {
		return mine;
	}

	public void setMine(Mine mine) {
		this.mine = mine;
	}

	public int parentId() {
		return parentId;
	}

	public void setParentId(int parentId) {
		this.parentId = parentId;
	}

	public List<Plot> plots() {
		return plots;
	}

	public BlockPos mineSite() {
		return mineSite;
	}

	public void setMineSite(BlockPos mineSite) {
		this.mineSite = mineSite;
	}

	public long coins() {
		return coins;
	}

	public void addCoins(long amount) {
		coins = Math.max(0, coins + amount);
	}

	public Map<BuildingType, Integer> staffed() {
		return staffed;
	}

	public long counter(String key) {
		return counters.getOrDefault(key, 0L);
	}

	public void setCounter(String key, long value) {
		if (value == 0) {
			counters.remove(key);
		} else {
			counters.put(key, value);
		}
	}

	public void addCounter(String key, long delta) {
		setCounter(key, counter(key) + delta);
	}

	public Map<String, Long> counters() {
		return counters;
	}

	/** Whether the faction treats this settlement as its capital: the first one it founded. */
	public boolean isCapital() {
		return id == 0;
	}

	/** Key that identifies this settlement across the mod, e.g. in chunk data. */
	public String key(FactionType faction) {
		return faction.id() + ":" + id;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Id", id);
		nbt.putString("Name", name);
		nbt.putLong("Pos", pos.asLong());
		nbt.putString("Dimension", dimension.getValue().toString());
		nbt.putLong("Founded", foundedDay);
		nbt.putInt("Population", population);
		nbt.putInt("Tier", tier);
		NbtCompound buildingsNbt = new NbtCompound();
		buildings.forEach((type, count) -> buildingsNbt.putInt(type.id(), count));
		nbt.put("Buildings", buildingsNbt);
		if (project != null) {
			nbt.putString("Project", project.id());
			nbt.putInt("ProjectProgress", projectProgress);
		}
		if (mine != null) {
			nbt.put("Mine", mine.toNbt());
		}
		nbt.putInt("Parent", parentId);
		nbt.putLong("Coins", coins);
		if (mineSite != null) {
			nbt.putLong("MineSite", mineSite.asLong());
		}
		NbtList plotsNbt = new NbtList();
		plots.forEach(plot -> plotsNbt.add(plot.toNbt()));
		nbt.put("Plots", plotsNbt);
		NbtCompound countersNbt = new NbtCompound();
		counters.forEach(countersNbt::putLong);
		nbt.put("Counters", countersNbt);
		return nbt;
	}

	public static Settlement fromNbt(NbtCompound nbt) {
		Settlement settlement = new Settlement(
				nbt.getInt("Id"),
				nbt.getString("Name"),
				BlockPos.fromLong(nbt.getLong("Pos")),
				RegistryKey.of(RegistryKeys.WORLD, Identifier.of(nbt.getString("Dimension"))),
				nbt.getLong("Founded"));
		settlement.population = nbt.getInt("Population");
		settlement.tier = nbt.getInt("Tier");
		NbtCompound buildingsNbt = nbt.getCompound("Buildings");
		for (String key : buildingsNbt.getKeys()) {
			BuildingType type = BuildingType.byId(key);
			if (type != null) {
				settlement.buildings.put(type, buildingsNbt.getInt(key));
			}
		}
		if (nbt.contains("Project")) {
			settlement.project = BuildingType.byId(nbt.getString("Project"));
			settlement.projectProgress = nbt.getInt("ProjectProgress");
		}
		if (nbt.contains("Mine")) {
			settlement.mine = Mine.fromNbt(nbt.getCompound("Mine"));
		}
		settlement.parentId = nbt.contains("Parent") ? nbt.getInt("Parent") : -1;
		settlement.coins = nbt.getLong("Coins");
		if (nbt.contains("MineSite")) {
			settlement.mineSite = BlockPos.fromLong(nbt.getLong("MineSite"));
		}
		NbtList plotsNbt = nbt.getList("Plots", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < plotsNbt.size(); i++) {
			Plot plot = Plot.fromNbt(plotsNbt.getCompound(i));
			if (plot != null) {
				settlement.plots.add(plot);
			}
		}
		NbtCompound countersNbt = nbt.getCompound("Counters");
		for (String key : countersNbt.getKeys()) {
			settlement.counters.put(key, countersNbt.getLong(key));
		}
		return settlement;
	}
}
