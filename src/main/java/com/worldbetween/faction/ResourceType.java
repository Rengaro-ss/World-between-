package com.worldbetween.faction;

import net.minecraft.text.Text;

/**
 * Abstract resources stored in a faction's treasury. These are numbers in the simulation,
 * not items in chests.
 */
public enum ResourceType {
	FOOD("food"),
	WOOD("wood"),
	STONE("stone"),
	ORE("ore"),
	METAL("metal"),
	EQUIPMENT("equipment");

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
