package com.worldbetween.town;

import com.worldbetween.faction.BuildingType;
import com.worldbetween.faction.Plot;
import com.worldbetween.faction.Settlement;
import net.minecraft.server.world.ServerWorld;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Gives every finished building its place in town, and grows houses.
 */
public final class TownPlanner {
	/** Buildings that stand in the back row with room around them. */
	private static final Set<BuildingType> BACK_ROW = Set.of(
			BuildingType.FARM, BuildingType.PASTURE, BuildingType.LUMBER_CAMP, BuildingType.QUARRY, BuildingType.CHARCOAL_KILN);
	/** Buildings that are not plots: they have places of their own, or no place in town at all. */
	private static final Set<BuildingType> NO_PLOT = Set.of(
			BuildingType.MINE, BuildingType.MARKET, BuildingType.WATCHTOWER, BuildingType.PIER, BuildingType.HOUSE_EXTENSION);

	private TownPlanner() {
	}

	public static int houseCapacity(int level) {
		return 5 + 2 * level;
	}

	/**
	 * How big a house may grow: up to the settlement's tier, and by where it stands. Town houses crowd the
	 * square, two-storey houses line the middle of the streets, and the outskirts keep their cottages.
	 */
	private static int maxLevel(TownLayout layout, Settlement settlement, Plot plot) {
		TownLayout.Slot slot = layout.slot(plot.slot());
		int byPlace = slot == null || slot.back() ? 1 : slot.along() <= 28 ? 3 : slot.along() <= 40 ? 2 : 1;
		// A little variety: now and then a plot stays a size smaller than its neighbours.
		if (Math.floorMod(plot.slot() * 31 + settlement.id(), 4) == 0) {
			byPlace = Math.max(0, byPlace - 1);
		}
		return Math.min(Math.min(3, settlement.tier()), byPlace);
	}

	public static boolean canExtendHouse(ServerWorld world, Settlement settlement) {
		TownLayout layout = TownLayout.of(world, settlement);
		return settlement.plots().stream().anyMatch(p -> p.type() == BuildingType.HOUSE && p.level() < maxLevel(layout, settlement, p));
	}

	public static void extendHouse(ServerWorld world, Settlement settlement) {
		TownLayout layout = TownLayout.of(world, settlement);
		settlement.plots().stream()
				.filter(p -> p.type() == BuildingType.HOUSE && p.level() < maxLevel(layout, settlement, p))
				.min(Comparator.comparingInt(Plot::level).thenComparingInt(Plot::slot))
				.ifPresent(p -> p.setLevel(p.level() + 1));
	}

	/** Places a newly finished building, if a plot is free for it. */
	public static void assign(ServerWorld world, Settlement settlement, BuildingType type) {
		if (NO_PLOT.contains(type)) {
			return;
		}
		TownLayout layout = TownLayout.of(world, settlement);
		Set<Integer> taken = new HashSet<>();
		settlement.plots().forEach(p -> taken.add(p.slot()));
		boolean wantBack = BACK_ROW.contains(type);
		TownLayout.Slot chosen = null;
		for (int pass = 0; pass < 2 && chosen == null; pass++) {
			boolean back = pass == 0 ? wantBack : !wantBack;
			for (TownLayout.Slot slot : layout.slots()) {
				if (slot.back() == back && slot.isOpen(settlement.tier()) && !taken.contains(slot.index())) {
					chosen = slot;
					break;
				}
			}
		}
		if (chosen != null) {
			settlement.plots().add(new Plot(type, chosen.index(), 0));
		}
	}

	/** Gives plots to buildings that did not get one yet, e.g. because the town was too small. */
	public static void sync(ServerWorld world, Settlement settlement) {
		for (BuildingType type : BuildingType.values()) {
			if (NO_PLOT.contains(type)) {
				continue;
			}
			long placed = settlement.plots().stream().filter(p -> p.type() == type).count();
			for (long i = placed; i < settlement.buildingCount(type); i++) {
				int before = settlement.plots().size();
				assign(world, settlement, type);
				if (settlement.plots().size() == before) {
					return; // no room left
				}
			}
		}
	}
}
