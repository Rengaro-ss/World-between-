package com.worldbetween.faction.kament;

import net.minecraft.text.Text;

/**
 * The halls the Kaments carve out of their mountains and the deep. Every one does real work.
 * Costs are in stone, diorite, dressed stone and flint; the Kaments need no wood and no metal.
 */
public enum KamentHall {
	/** Cuts stone and diorite out of the mountain: the hall itself grows as they quarry. */
	QUARRY("quarry", 30, 0, 0, 0, 1, 3, 0),
	/** Digs gravel, the stuff of growth. */
	GRAVEL_PIT("gravel_pit", 30, 0, 0, 0, 1, 3, 0),
	/** Sifts gravel for flint: the Kaments' key to every craft. */
	KNAPPERY("knappery", 40, 10, 0, 0, 1, 4, 0),
	/** Dresses rough stone into blocks fit to build with. */
	CARVERY("carvery", 40, 10, 0, 4, 1, 4, 0),
	/** Where the soul-sculptors carve new Kaments and pour a part of their soul into them. */
	SCULPTORY("sculptory", 60, 30, 20, 10, 0, 8, 0),
	/** Keeps the memory of the people: the Kaments' chronicle and learning. */
	MEMORY("memory", 50, 40, 30, 10, 1, 8, 0),
	/** Heals worked-out veins: the metal taken from strangers goes back into the mountain. */
	HEALER("healer", 60, 20, 30, 20, 1, 8, 1),
	/** A guard hall: more guards keep watch over the city. */
	WARD("ward", 50, 20, 20, 10, 0, 6, 1);

	private final String id;
	private final int stone;
	private final int diorite;
	private final int bricks;
	private final int flint;
	private final int workers;
	private final int days;
	private final int minTier;

	KamentHall(String id, int stone, int diorite, int bricks, int flint, int workers, int days, int minTier) {
		this.id = id;
		this.stone = stone;
		this.diorite = diorite;
		this.bricks = bricks;
		this.flint = flint;
		this.workers = workers;
		this.days = days;
		this.minTier = minTier;
	}

	public String id() {
		return id;
	}

	public int stone() {
		return stone;
	}

	public int diorite() {
		return diorite;
	}

	public int bricks() {
		return bricks;
	}

	public int flint() {
		return flint;
	}

	public int workers() {
		return workers;
	}

	public int days() {
		return days;
	}

	public int minTier() {
		return minTier;
	}

	public Text displayName() {
		return Text.translatable("kament_hall.worldbetween." + id);
	}

	public static KamentHall byOrdinal(long ordinal) {
		KamentHall[] halls = values();
		return ordinal >= 0 && ordinal < halls.length ? halls[(int) ordinal] : null;
	}
}
