package com.worldbetween.faction.court;

import com.worldbetween.faction.BuildingType;
import net.minecraft.text.Text;

/**
 * The part of a town's life a baron oversees, and gives a bonus to.
 */
public enum Sphere {
	/** Fields, pastures, mills, bakeries, kitchens, piers. */
	FARMS("farms"),
	/** Timber, stone, leather and cloth. */
	CRAFTS("crafts"),
	/** Mines, charcoal, smelters, forges. */
	METAL("metal"),
	/** Taxes, markets and the treasury. */
	TRADE("trade"),
	/** Watchtowers, barracks, recruits. */
	GUARD("guard"),
	/** Libraries and knowledge. */
	LEARNING("learning"),
	/** Speed of construction. */
	BUILDING("building"),
	/** Growth of the population. */
	HOUSING("housing");

	private final String id;

	Sphere(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public Text displayName() {
		return Text.translatable("sphere.worldbetween." + id);
	}

	/** Which sphere a building's output falls under, or null if none. */
	public static Sphere of(BuildingType type) {
		return switch (type) {
			case FARM, PASTURE, PIER, MILL, BAKERY, KITCHEN -> FARMS;
			case LUMBER_CAMP, SAWMILL, QUARRY, MASON, TANNERY, WEAVER, CHARCOAL_KILN -> CRAFTS;
			case MINE, SMELTER, FORGE -> METAL;
			case MARKET, WAREHOUSE -> TRADE;
			case BARRACKS, WATCHTOWER -> GUARD;
			case LIBRARY -> LEARNING;
			default -> null;
		};
	}

	public static Sphere byId(String id) {
		for (Sphere sphere : values()) {
			if (sphere.id.equals(id)) {
				return sphere;
			}
		}
		return null;
	}
}
