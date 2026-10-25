package com.worldbetween.faction.arki;

import net.minecraft.util.math.random.Random;

/** Arki names: soft and ringing, in the manner of the elves. */
public final class ArkiNames {
	private static final String[] FIRST = {"Ae", "El", "Ly", "Sil", "Tha", "Ar", "Ith", "Nim", "Fae", "Cal", "Mir", "Eli", "Gal", "Lor", "Il", "Ser", "Vae", "Ny"};
	private static final String[] MIDDLE = {"", "", "a", "e", "i", "ri", "ya", "le", "the"};
	private static final String[] LAST = {"wen", "ion", "iel", "ara", "eth", "ia", "on", "ir", "ael", "ith", "rion", "dil", "las", "wyn", "nor", "riel"};

	private ArkiNames() {
	}

	public static String person(Random random) {
		return FIRST[random.nextInt(FIRST.length)] + MIDDLE[random.nextInt(MIDDLE.length)] + LAST[random.nextInt(LAST.length)];
	}

	/** Colonies are named for the stars the Arki remember: two names joined. */
	public static String colony(Random random) {
		String a = person(random);
		return random.nextBoolean() ? a : a + "'" + LAST[random.nextInt(LAST.length)];
	}
}
