package com.worldbetween.faction.arki;

import net.minecraft.text.Text;

/**
 * What an Arki colony grows on its crystal discs. Each disc floats on its own cushion of amethyst; none
 * is joined to another, for the Arki fly. Costs are in amethyst and the gifts of the sea.
 */
public enum ArkiHall {
	/** Amethyst grown from budding crystal: what everything of theirs is made of. */
	GARDEN("garden", 20, 10, 0, 1, 3),
	/** Gathers starlight, by night only, more under a full moon. */
	LENS("lens", 30, 10, 0, 1, 4),
	/** Takes from the sea: prismarine, kelp, the light of sea lanterns. */
	SEAWORKS("seaworks", 20, 0, 0, 1, 3),
	/** Turns starlight into knowledge of the gods' secrets: what cost them the sky. */
	OBSERVATORY("observatory", 40, 30, 20, 1, 6),
	/** Room for more of them. */
	DWELLING("dwelling", 30, 20, 0, 0, 4),
	/** A landing pad for comet capsules: help to and from the other colonies, and new colonies. */
	PAD("pad", 50, 40, 40, 0, 6);

	private final String id;
	private final int amethyst;
	private final int sea;
	private final int starlight;
	private final int workers;
	private final int days;

	ArkiHall(String id, int amethyst, int sea, int starlight, int workers, int days) {
		this.id = id;
		this.amethyst = amethyst;
		this.sea = sea;
		this.starlight = starlight;
		this.workers = workers;
		this.days = days;
	}

	public String id() {
		return id;
	}

	public int amethyst() {
		return amethyst;
	}

	public int sea() {
		return sea;
	}

	public int starlight() {
		return starlight;
	}

	public int workers() {
		return workers;
	}

	public int days() {
		return days;
	}

	public Text displayName() {
		return Text.translatable("arki_hall.worldbetween." + id);
	}

	public static ArkiHall byOrdinal(long ordinal) {
		ArkiHall[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[(int) ordinal] : null;
	}
}
