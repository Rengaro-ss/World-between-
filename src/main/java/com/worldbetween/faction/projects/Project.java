package com.worldbetween.faction.projects;

import com.worldbetween.faction.FactionType;
import net.minecraft.text.Text;

/**
 * The great works of the peoples: each one's weapon of war, and each one's legendary deed. A work is first
 * found (long study, whatever the age), then made (a great deal of resources and time). Weapons are used
 * again and again; a deed is done once and changes the world.
 */
public enum Project {
	/** Neris: a menagerie where lions are crossed with great bats; seekers ride the beasts into war. */
	MANTICORES("manticores", FactionType.NERIS, false, 260, 40),
	/** Kaments: a crystal grown from the souls of several Kaments that shakes down the enemy's walls from afar. */
	QUAKE_CRYSTAL("quake_crystal", FactionType.KAMENT, false, 240, 30),
	/** Arki: a ritual that calls a swarm of comets down on one place. */
	COMET_STORM("comet_storm", FactionType.ARKI, false, 280, 0),
	/** Neris, deed: the Great Portal of Invasion, and the war in the Nether. */
	HELL_CAMPAIGN("hell_campaign", FactionType.NERIS, true, 360, 45),
	/** Arki, deed: the Tower of Eternal Night. */
	NIGHT_TOWER("night_tower", FactionType.ARKI, true, 420, 60),
	/** Kaments, deed: the Ancient Morok, their hero of old, wakes and walks the world. */
	MOROK("morok", FactionType.KAMENT, true, 400, 40);

	/** Not yet known. */
	public static final int UNKNOWN = 0;
	/** Found: the people knows how, and gathers what it takes. */
	public static final int KNOWN = 1;
	/** Being made. */
	public static final int MAKING = 2;
	/** Made: the weapon is ready, the deed is done. */
	public static final int DONE = 3;

	private final String id;
	private final FactionType owner;
	private final boolean deed;
	private final int study;
	private final int days;

	Project(String id, FactionType owner, boolean deed, int study, int days) {
		this.id = id;
		this.owner = owner;
		this.deed = deed;
		this.study = study;
		this.days = days;
	}

	public String id() {
		return id;
	}

	public FactionType owner() {
		return owner;
	}

	public boolean isDeed() {
		return deed;
	}

	/** Points of study it takes to find. */
	public int study() {
		return study;
	}

	/** Days of work to make, once everything is gathered. */
	public int days() {
		return days;
	}

	public Text displayName() {
		return Text.translatable("project.worldbetween." + id);
	}

	public String key(String what) {
		return "proj." + id + "." + what;
	}
}
