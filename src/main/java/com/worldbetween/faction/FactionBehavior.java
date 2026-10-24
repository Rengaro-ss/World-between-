package com.worldbetween.faction;

import com.worldbetween.sim.SimulationContext;

/**
 * The unique logic of one faction. Each people grows, gathers and expands in its own way.
 */
public interface FactionBehavior {
	/** Day on which the faction enters the world. */
	int arrivalDay(SimulationContext context);

	/** Brings the faction into the world. Returns false if no place could be found yet. */
	boolean arrive(SimulationContext context, Faction faction);

	/** Runs one abstract simulation day. */
	void simulateDay(SimulationContext context, Faction faction);
}
