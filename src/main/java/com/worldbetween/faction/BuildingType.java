package com.worldbetween.faction;

import net.minecraft.text.Text;

/**
 * Abstract building kinds. Every one of them has a job in the economy; a settlement holds a count of
 * each, and each finished building gets a plot in the town where it is built in the world.
 */
public enum BuildingType {
	HOUSE("house", 20, 10, 0, 0, 3),
	FARM("farm", 15, 5, 0, 2, 2),
	PASTURE("pasture", 12, 0, 0, 2, 2),
	PIER("pier", 25, 0, 0, 2, 3),
	MILL("mill", 25, 15, 0, 2, 4),
	BAKERY("bakery", 15, 20, 0, 2, 3),
	KITCHEN("kitchen", 15, 15, 0, 1, 3),
	LUMBER_CAMP("lumber_camp", 10, 10, 0, 2, 2),
	SAWMILL("sawmill", 20, 10, 0, 2, 3),
	QUARRY("quarry", 20, 0, 0, 2, 3),
	MASON("mason", 10, 25, 0, 2, 3),
	CHARCOAL_KILN("charcoal_kiln", 10, 20, 0, 1, 2),
	MINE("mine", 25, 20, 0, 3, 4),
	SMELTER("smelter", 15, 40, 0, 2, 4),
	TANNERY("tannery", 15, 10, 0, 1, 3),
	WEAVER("weaver", 20, 10, 0, 2, 3),
	FORGE("forge", 20, 40, 0, 2, 5),
	LIBRARY("library", 30, 20, 20, 1, 6),
	BARRACKS("barracks", 40, 40, 20, 2, 6),
	WAREHOUSE("warehouse", 30, 30, 0, 0, 5),
	MARKET("market", 20, 10, 0, 0, 4),
	WATCHTOWER("watchtower", 15, 20, 0, 0, 3),
	/** Not a building of its own: enlarges the smallest house so it holds more people. */
	HOUSE_EXTENSION("house_extension", 15, 10, 0, 0, 3);

	private final String id;
	private final int woodCost;
	private final int stoneCost;
	private final int brickCost;
	private final int workers;
	private final int buildDays;

	BuildingType(String id, int woodCost, int stoneCost, int brickCost, int workers, int buildDays) {
		this.id = id;
		this.woodCost = woodCost;
		this.stoneCost = stoneCost;
		this.brickCost = brickCost;
		this.workers = workers;
		this.buildDays = buildDays;
	}

	public String id() {
		return id;
	}

	public int woodCost() {
		return woodCost;
	}

	public int stoneCost() {
		return stoneCost;
	}

	public int brickCost() {
		return brickCost;
	}

	/** Workers needed to staff one building of this kind; 0 means it runs without a crew. */
	public int workers() {
		return workers;
	}

	/** Work-days needed to finish construction. */
	public int buildDays() {
		return buildDays;
	}

	public Text displayName() {
		return Text.translatable("building.worldbetween." + id);
	}

	public static BuildingType byId(String id) {
		for (BuildingType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return null;
	}
}
