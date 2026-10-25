package com.worldbetween.entity.kament;

/** The rock a Kament was hewn from: the mountain's own stone up high, deepslate and tuff in the deep. */
public enum KamentMaterial {
	STONE("stone"),
	ANDESITE("andesite"),
	DIORITE("diorite"),
	GRANITE("granite"),
	TUFF("tuff"),
	DEEPSLATE("deepslate");

	private final String id;

	KamentMaterial(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static KamentMaterial byIndex(int index) {
		KamentMaterial[] all = values();
		return index >= 0 && index < all.length ? all[index] : STONE;
	}
}
