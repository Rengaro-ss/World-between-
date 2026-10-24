package com.worldbetween.faction.neris;

import net.minecraft.util.math.random.Random;

/**
 * Settlement names of the Kingdom of Neris: short, hard sounds of a people forged in fire.
 */
final class NerisNames {
	private static final String[] PREFIXES = {
			"Kar", "Ush", "Ner", "Vor", "Esh", "Mar", "Ruk", "Dan", "Ish", "Bel", "Tar", "Gah", "Sur", "Om"};
	private static final String[] SUFFIXES = {
			"un", "ar", "ost", "esh", "im", "or", "ath", "ek", "ira", "uld"};

	private NerisNames() {
	}

	static String generate(Random random) {
		String name = PREFIXES[random.nextInt(PREFIXES.length)] + SUFFIXES[random.nextInt(SUFFIXES.length)];
		if (random.nextInt(3) == 0) {
			name += "-" + PREFIXES[random.nextInt(PREFIXES.length)];
		}
		return name;
	}
}
