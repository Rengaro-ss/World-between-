package com.worldbetween.faction;

import net.minecraft.text.Text;

/**
 * Abstract resources stored in a faction's treasury. These are numbers in the simulation,
 * not items in chests.
 */
public enum ResourceType {
	/** Ready-to-eat food: bread, cooked meals, fish. */
	FOOD("food"),
	GRAIN("grain"),
	FLOUR("flour"),
	MEAT("meat"),
	LOGS("logs"),
	/** Sawn timber, the main building material. */
	WOOD("wood"),
	STONE("stone"),
	BRICKS("bricks"),
	ORE("ore"),
	FUEL("fuel"),
	METAL("metal"),
	HIDES("hides"),
	LEATHER("leather"),
	WOOL("wool"),
	CLOTH("cloth"),
	EQUIPMENT("equipment"),
	/** Gathered in libraries; needed to enter later eras. Never spoils or hits storage limits. */
	KNOWLEDGE("knowledge"),
	/** The kingdom's treasury, in iron nuggets. Never spoils. */
	COINS("coins"),
	/** The Kaments' stuff of growth: gravel, sifted for flint. */
	GRAVEL("gravel"),
	FLINT("flint"),
	/** White stone the Kaments carve their finest work from. */
	DIORITE("diorite");

	private final String id;

	ResourceType(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public Text displayName() {
		return Text.translatable("resource.worldbetween." + id);
	}

	public static ResourceType byId(String id) {
		for (ResourceType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return null;
	}
}
