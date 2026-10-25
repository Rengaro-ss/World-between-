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
	private static final int TICKS_PER_DAY = 24000;
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
		long worldDay = server.getOverworld().getTimeOfDay() / TICKS_PER_DAY;
		if (state.lastWorldDay() < 0 || worldDay < state.lastWorldDay()) {
			// First run in this world, or time was set backwards: just start counting from here.
			state.setLastWorldDay(worldDay);
			return;
		}
		for (int i = 0; i < MAX_DAYS_PER_TICK && state.lastWorldDay() < worldDay; i++) {
			simulateDay(server, state);
			state.setLastWorldDay(state.lastWorldDay() + 1);
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
		state.markDirty();
	}
}
