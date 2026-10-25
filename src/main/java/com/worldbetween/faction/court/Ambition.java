package com.worldbetween.faction.court;

import net.minecraft.text.Text;

/**
 * What a noble wants most. It steers what gets built in the noble's town and, for a king,
 * the policy of the whole kingdom.
 */
public enum Ambition {
	WEALTH("wealth", Sphere.TRADE),
	ARMY("army", Sphere.METAL),
	SCIENCE("science", Sphere.LEARNING),
	WAR("war", Sphere.GUARD),
	GLORY("glory", Sphere.BUILDING),
	EXPANSION("expansion", Sphere.HOUSING),
	VENGEANCE("vengeance", Sphere.METAL),
	ORDER("order", Sphere.GUARD);

	private final String id;
	private final Sphere sphere;

	Ambition(String id, Sphere sphere) {
		this.id = id;
		this.sphere = sphere;
	}

	public String id() {
		return id;
	}

	/** The part of town a noble with this ambition would rather oversee. */
	public Sphere sphere() {
		return sphere;
	}

	public Text displayName() {
		return Text.translatable("ambition.worldbetween." + id);
	}

	public static Ambition byId(String id) {
		for (Ambition ambition : values()) {
			if (ambition.id.equals(id)) {
				return ambition;
			}
		}
		return null;
	}
}
