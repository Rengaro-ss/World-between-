package com.worldbetween.entity.arki;

/** What an Arki does in its colony; each glows in its own colours of the sky. */
public enum ArkiRole {
	/** Takes from the sea and tends the landing pads. */
	GATHERER("gatherer"),
	/** Grows the amethyst. */
	GARDENER("gardener"),
	/** Gathers starlight and reads the gods' secrets in it. */
	ASTRONOMER("astronomer"),
	/** Watches over the colony. */
	KEEPER("keeper");

	private final String id;

	ArkiRole(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** Colours of the motes around them, red-green-blue from 0 to 1: first the glow, then the spark. */
	public float[][] motes() {
		return switch (this) {
			case GATHERER -> new float[][]{{0.25f, 0.75f, 0.95f}, {0.55f, 0.95f, 1f}};
			case GARDENER -> new float[][]{{0.7f, 0.35f, 0.95f}, {0.95f, 0.7f, 1f}};
			case ASTRONOMER -> new float[][]{{0.2f, 0.25f, 0.9f}, {0.85f, 0.85f, 1f}};
			case KEEPER -> new float[][]{{0.85f, 0.85f, 1f}, {0.6f, 0.4f, 1f}};
		};
	}

	public static ArkiRole byIndex(int index) {
		ArkiRole[] all = values();
		return index >= 0 && index < all.length ? all[index] : GATHERER;
	}
}
