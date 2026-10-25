package com.worldbetween.faction.arki;

import com.worldbetween.faction.Settlement;

import java.util.EnumMap;
import java.util.Map;

/**
 * Shared facts about Arki colonies. Every colony keeps its own stores: no colony rules another, though
 * they honour one another and send help across the sky.
 */
public final class Arkis {
	public static final int SEA = 1;
	public static final int SKY = 2;
	public static final int[] TIER_POPULATION = {0, 5, 10, 16};
	/** A colony's own stores, kept on the colony. */
	public static final String AMETHYST = "amethyst";
	public static final String STARLIGHT = "starlight";
	public static final String SEA_GOODS = "sea";
	public static final String LORE = "lore";
	public static final String[] STORES = {AMETHYST, STARLIGHT, SEA_GOODS};
	private static final int MAX_SLOTS = 12;

	private Arkis() {
	}

	public static int kind(Settlement colony) {
		return (int) colony.counter("kind");
	}

	public static boolean isSky(Settlement colony) {
		return kind(colony) == SKY;
	}

	/** How many discs a colony has room for at its tier. */
	public static int capacity(Settlement colony) {
		return Math.min(MAX_SLOTS, 6 + 3 * colony.tier());
	}

	public static ArkiHall hallAt(Settlement colony, int slot) {
		long value = colony.counter("room." + slot);
		return value == 0 ? null : ArkiHall.byOrdinal(value - 1);
	}

	public static Map<ArkiHall, Integer> halls(Settlement colony) {
		Map<ArkiHall, Integer> halls = new EnumMap<>(ArkiHall.class);
		for (int slot = 0; slot < MAX_SLOTS; slot++) {
			ArkiHall hall = hallAt(colony, slot);
			if (hall != null) {
				halls.merge(hall, 1, Integer::sum);
			}
		}
		return halls;
	}

	public static int count(Settlement colony, ArkiHall hall) {
		return halls(colony).getOrDefault(hall, 0);
	}

	public static int hallTotal(Settlement colony) {
		return halls(colony).values().stream().mapToInt(Integer::intValue).sum();
	}

	public static int place(Settlement colony, ArkiHall hall) {
		for (int slot = 0; slot < capacity(colony); slot++) {
			if (hallAt(colony, slot) == null) {
				colony.setCounter("room." + slot, hall.ordinal() + 1);
				return slot;
			}
		}
		return -1;
	}

	/** People a colony has room for: its capsule, and every dwelling. */
	public static int housing(Settlement colony) {
		return 6 + 5 * count(colony, ArkiHall.DWELLING);
	}

	/** How bright the moon is on a day: full at phase 0, all but dark at phase 4. */
	public static float moonlight(long day) {
		float[] light = {1f, 0.85f, 0.6f, 0.35f, 0.1f, 0.35f, 0.6f, 0.85f};
		return light[(int) Math.floorMod(day, 8L)];
	}
}
