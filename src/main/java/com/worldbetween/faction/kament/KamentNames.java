package com.worldbetween.faction.kament;

import net.minecraft.util.math.random.Random;

/** Kament names: one heavy word of four or five letters, like stones knocked together. */
public final class KamentNames {
	private static final String[] ONSETS = {"B", "D", "G", "K", "T", "M", "N", "H", "Gr", "Dr", "Br", "Kh", "Gh", "Th", "Ur", "Ol", "Um", "Ar"};
	private static final String[] VOWELS = {"a", "o", "u", "e", "i"};
	private static final String[] CODAS = {"rn", "rm", "rk", "rd", "ld", "lm", "nd", "m", "n", "r", "k", "g", "th", "sk"};
	private static final String[] TAILS = {"a", "o", "um", "ar", "ek", "ol"};

	private KamentNames() {
	}

	public static String generate(Random random) {
		for (int attempt = 0; attempt < 20; attempt++) {
			String name = ONSETS[random.nextInt(ONSETS.length)] + VOWELS[random.nextInt(VOWELS.length)]
					+ CODAS[random.nextInt(CODAS.length)];
			if (name.length() < 4) {
				name += TAILS[random.nextInt(TAILS.length)];
			}
			if (name.length() >= 4 && name.length() <= 5) {
				return name;
			}
		}
		return "Gorn";
	}
}
