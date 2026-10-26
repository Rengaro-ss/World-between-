package com.worldbetween.sim;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionBehavior;
import com.worldbetween.faction.FactionBehaviors;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.server.MinecraftServer;

/**
 * Drives the abstract simulation. One simulation day passes per in-game day of the overworld,
 * whether or not any player is near the factions.
 */
public final class WorldSimulation {
	private static final int CHECK_INTERVAL = 20;
	/** Upper bound on days simulated in one tick, so a large time jump cannot freeze the server. */
	private static final int MAX_DAYS_PER_TICK = 4;

	private WorldSimulation() {
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % CHECK_INTERVAL != 0) {
			return;
		}
		WorldBetweenState state = WorldBetweenState.get(server);
		long time = server.getOverworld().getTimeOfDay();
		double pace = Clock.pace();
		if (state.startTick() == WorldBetweenState.UNSET) {
			// First run in this world (or a save from before there were two clocks): history starts now.
			state.setStartTick(time - (long) (state.day() * Clock.TICKS_PER_DAY / pace));
			return;
		}
		long target = (long) Math.floor((time - state.startTick()) * pace / Clock.TICKS_PER_DAY);
		if (target < state.day()) {
			// Time was set back, or history was run ahead by hand: go on counting from here.
			state.setStartTick(time - (long) Math.ceil(state.day() * Clock.TICKS_PER_DAY / pace));
			return;
		}
		// Days of history come one at a time through the day, about every couple of minutes at a pace of ten.
		for (int i = 0; i < MAX_DAYS_PER_TICK && state.day() < target; i++) {
			simulateDay(server, state);
		}
	}

	public static void simulateDay(MinecraftServer server, WorldBetweenState state) {
		state.advanceDay();
		SimulationContext context = new SimulationContext(server, state);
		for (Faction faction : state.factions()) {
			FactionBehavior behavior = FactionBehaviors.get(faction.type());
			if (behavior == null || faction.isDestroyed()) {
				continue;
			}
			if (!faction.hasArrived()) {
				if (context.day() >= behavior.arrivalDay(context)) {
					behavior.arrive(context, faction);
				}
				continue;
			}
			behavior.simulateDay(context, faction);
		}
		com.worldbetween.war.WarSimulation.simulateDay(context);
		com.worldbetween.war.Repairs.simulateDay(context);
		for (Faction faction : state.factions()) {
			com.worldbetween.faction.projects.Projects.simulateDay(context, faction);
		}
		state.markDirty();
	}
}
