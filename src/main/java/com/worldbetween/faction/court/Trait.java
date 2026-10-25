package com.worldbetween.faction.court;

import net.minecraft.text.Text;

/**
 * Personality traits. Every noble has two; they shape the noble's bonus, behaviour and votes.
 */
public enum Trait {
	INDUSTRIOUS("industrious", true),
	WISE("wise", true),
	BUILDER("builder", true),
	THRIFTY("thrifty", true),
	BRAVE("brave", true),
	JUST("just", true),
	REMEMBERS_CHAINS("remembers_chains", true),
	CHARISMATIC("charismatic", true),
	GREEDY("greedy", false),
	LAZY("lazy", false),
	CRUEL("cruel", false),
	COWARD("coward", false),
	DRUNKARD("drunkard", false),
	PARANOID("paranoid", false),
	AMBITIOUS("ambitious", false),
	RECKLESS("reckless", false);

	private final String id;
	private final boolean virtue;

	Trait(String id, boolean virtue) {
		this.id = id;
		this.virtue = virtue;
	}

	public String id() {
		return id;
	}

	public boolean isVirtue() {
		return virtue;
	}

	public Text displayName() {
		return Text.translatable("trait.worldbetween." + id);
	}

	/** The nickname a noble with this trait earns, as a translation key. */
	public String epithetKey() {
		return "epithet.worldbetween." + id;
	}

	/** Traits that cannot sit in the same person. */
	public boolean contradicts(Trait other) {
		return opposite() == other || other.opposite() == this;
	}

	/** The trait most at odds with this one; nobles with opposite traits dislike each other. */
	public Trait opposite() {
		return switch (this) {
			case INDUSTRIOUS -> LAZY;
			case THRIFTY -> GREEDY;
			case BRAVE -> COWARD;
			case JUST -> CRUEL;
			case WISE -> RECKLESS;
			case CHARISMATIC -> PARANOID;
			case BUILDER -> DRUNKARD;
			default -> null;
		};
	}

	public static Trait byId(String id) {
		for (Trait trait : values()) {
			if (trait.id.equals(id)) {
				return trait;
			}
		}
		return null;
	}
}
