package com.worldbetween.faction.neris;

import net.minecraft.util.math.random.Random;

/**
 * Settlement names of the Kingdom of Neris: short, hard sounds of a people forged in fire.
 */
public final class NerisNames {
	private static final String[] PREFIXES = {
			"Kar", "Ush", "Ner", "Vor", "Esh", "Mar", "Ruk", "Dan", "Ish", "Bel", "Tar", "Gah", "Sur", "Om"};
	private static final String[] SUFFIXES = {
			"un", "ar", "ost", "esh", "im", "or", "ath", "ek", "ira", "uld"};

	private NerisNames() {
	}

	private static final String[] PERSON_FIRST = {
			"Kar", "Vor", "Esh", "Tar", "Gan", "Mir", "Ruk", "Sha", "Dar", "Ush", "Bel", "Ner", "Oth", "Ka", "Zur", "Ama"};
	private static final String[] PERSON_LAST = {
			"esh", "an", "ok", "ira", "un", "eth", "ar", "osh", "iel", "ul", "ad", "ena"};

	/** A Neris personal name, like "Vor-Gan" or "Mirosh". */
	public static String person(Random random) {
		String name = PERSON_FIRST[random.nextInt(PERSON_FIRST.length)] + PERSON_LAST[random.nextInt(PERSON_LAST.length)];
		if (random.nextInt(2) == 0) {
			String second = PERSON_FIRST[random.nextInt(PERSON_FIRST.length)];
			name += "-" + second;
		}
		return name;
	}

	public static String generate(Random random) {
		String name = PREFIXES[random.nextInt(PREFIXES.length)] + SUFFIXES[random.nextInt(SUFFIXES.length)];
		if (random.nextInt(3) == 0) {
			name += "-" + PREFIXES[random.nextInt(PREFIXES.length)];
		}
		return name;
	}
}
