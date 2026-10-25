package com.worldbetween.war;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.court.Noble;
import com.worldbetween.faction.kament.Ancient;
import com.worldbetween.sim.SimulationContext;

/**
 * A town wiped off the map. Its people are gone and nobody tends it any more; what was built stays standing,
 * empty, a ruin for whoever comes by. Everything the people kept about the town goes with it.
 */
public final class Razing {
	private Razing() {
	}

	public static void remove(SimulationContext context, Faction faction, Settlement town) {
		int id = town.id();
		faction.settlements().remove(town);
		for (Noble noble : faction.nobles()) {
			if (noble.isAlive() && noble.seat() == id) {
				noble.die(); // the lord fell with his town
			}
		}
		for (Ancient ancient : faction.ancients()) {
			if (ancient.isAlive() && ancient.seat() == id) {
				ancient.die();
			}
		}
		faction.journeys().removeIf(journey -> journey.from() == id || journey.to() == id);
		faction.comets().removeIf(comet -> comet.colony() == id);
		context.state().wars().bands().removeIf(band -> band.faction() == faction.type() && band.home() == id
				&& band.state() == Warband.State.RETURNING);
		context.state().markDirty();
	}
}
