package com.worldbetween.faction;

import net.minecraft.text.Text;

/**
 * Abstract building kinds. A settlement holds a count of each; later each kind gets
 * a structure template per faction so it can be materialized in the world.
 */
public enum BuildingType {
	HOUSE("house", 20, 10, 0, 3),
	FARM("farm", 15, 5, 3, 2),
	LUMBER_CAMP("lumber_camp", 10, 10, 3, 2),
	QUARRY("quarry", 20, 0, 3, 3),
	MINE("mine", 25, 20, 3, 4),
	SMELTER("smelter", 15, 40, 3, 4),
	FORGE("forge", 20, 40, 3, 5),
	BARRACKS("barracks", 40, 40, 2, 6);

	private final String id;
	private final int woodCost;
	private final int stoneCost;
	private final int workers;
	private final int buildDays;

	BuildingType(String id, int woodCost, int stoneCost, int workers, int buildDays) {
		this.id = id;
		this.woodCost = woodCost;
		this.stoneCost = stoneCost;
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

	/** Workers needed to staff one building of this kind. */
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
