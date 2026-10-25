package com.worldbetween.faction;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumMap;
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
		return settlement;
	}
}
