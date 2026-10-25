package com.worldbetween.faction.kament;

import net.minecraft.text.Text;

/**
 * What survived in an Ancient of the old race. The ritual left only the calmest of them, but even stone
 * keeps a grain: one remembers every slight, another grieves, another longs for the deep.
 */
public enum KamentTrait {
	/** Never forgets: whatever a stranger does weighs half as much again. */
	MINDFUL("mindful", Focus.MEMORY),
	/** Gives a stranger more time before the guards turn on them. */
	PATIENT("patient", Focus.STONE),
	/** Gives a stranger less time. */
	WRATHFUL("wrathful", Focus.WARD),
	/** Still in mourning: carves slowly, remembers much. */
	GRIEVING("grieving", Focus.MEMORY),
	/** Longs for the deep: pushes the tunnels on. */
	DEEP("deep", Focus.DEPTH),
	/** Pours itself into the stone: new Kaments come sooner. */
	SCULPTOR("sculptor", Focus.CARVING),
	/** Cold to gifts: helping the Kaments earns little here. */
	COLD("cold", Focus.WARD),
	/** Curious about the short-lived: helping the Kaments earns more here. */
	CURIOUS("curious", Focus.DEPTH);

	private final String id;
	private final Focus leaning;

	KamentTrait(String id, Focus leaning) {
		this.id = id;
		this.leaning = leaning;
	}

	public String id() {
		return id;
	}

	/** What an Ancient with this trait argues for in the council. */
	public Focus leaning() {
		return leaning;
	}

	public Text displayName() {
		return Text.translatable("kament_trait.worldbetween." + id);
	}

	public static KamentTrait byId(String id) {
		for (KamentTrait trait : values()) {
			if (trait.id.equals(id)) {
				return trait;
			}
		}
		return null;
	}
}
