package com.worldbetween.faction.kament;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.Settlement;

import java.util.EnumMap;
import java.util.Map;

/**
 * Shared facts about Kament settlements: which kind each is, which halls it has carved, who its Ancient is.
 * A Kament settlement keeps these as named counters, since it has no houses, fields or plots.
 */
public final class Kaments {
	public static final int MOUNTAIN = 1;
	public static final int DEEP = 2;
	/** Population at which a hold grows into the next tier: a bed, a hall, a chamber, a stronghold. */
	public static final int[] TIER_POPULATION = {0, 6, 12, 20};

	private Kaments() {
	}

	public static int kind(Settlement settlement) {
		return (int) settlement.counter("kind");
	}

	public static boolean isDeep(Settlement settlement) {
		return kind(settlement) == DEEP;
	}

	/** How many halls a settlement has room for at its tier. */
	public static int capacity(Settlement settlement) {
		return isDeep(settlement) ? 4 + settlement.tier() : 6 + 4 * settlement.tier();
	}

	/** The hall carved into a slot, or null if the slot is still bare rock. */
	public static KamentHall hallAt(Settlement settlement, int slot) {
		long value = settlement.counter("room." + slot);
		return value == 0 ? null : KamentHall.byOrdinal(value - 1);
	}

	public static Map<KamentHall, Integer> halls(Settlement settlement) {
		Map<KamentHall, Integer> halls = new EnumMap<>(KamentHall.class);
		for (int slot = 0; slot < 20; slot++) {
			KamentHall hall = hallAt(settlement, slot);
			if (hall != null) {
				halls.merge(hall, 1, Integer::sum);
			}
		}
		return halls;
	}

	public static int count(Settlement settlement, KamentHall hall) {
		return halls(settlement).getOrDefault(hall, 0);
	}

	public static int hallTotal(Settlement settlement) {
		return halls(settlement).values().stream().mapToInt(Integer::intValue).sum();
	}

	/** Gives a finished hall the first free slot. Returns the slot, or -1 if none was free. */
	public static int carveHall(Settlement settlement, KamentHall hall) {
		for (int slot = 0; slot < capacity(settlement); slot++) {
			if (hallAt(settlement, slot) == null) {
				settlement.setCounter("room." + slot, hall.ordinal() + 1);
				return slot;
			}
		}
		return -1;
	}

	public static Ancient ancientOf(Faction faction, Settlement settlement) {
		return faction.ancients().stream().filter(a -> a.isAlive() && a.seat() == settlement.id()).findFirst().orElse(null);
	}

	public static Focus focus(Faction faction) {
		Focus[] all = Focus.values();
		long value = faction.counter("focus");
		return value > 0 && value <= all.length ? all[(int) value - 1] : null;
	}
}
