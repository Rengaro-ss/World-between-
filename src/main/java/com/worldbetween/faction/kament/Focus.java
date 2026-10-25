package com.worldbetween.faction.kament;

import net.minecraft.text.Text;

/** What the council of Ancients has resolved the people should work at, until it meets again. */
public enum Focus {
	/** Quarries yield half as much again. */
	STONE("stone"),
	/** New Kaments are carved sooner. */
	CARVING("carving"),
	/** Tunnels are driven half as fast again. */
	DEPTH("depth"),
	/** The halls of memory remember half as much again. */
	MEMORY("memory"),
	/** More guards keep watch, and strangers get less time. */
	WARD("ward");

	private final String id;

	Focus(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public Text displayName() {
		return Text.translatable("focus.worldbetween." + id);
	}

	public static Focus byId(String id) {
		for (Focus focus : values()) {
			if (focus.id.equals(id)) {
				return focus;
			}
		}
		return null;
	}
}
