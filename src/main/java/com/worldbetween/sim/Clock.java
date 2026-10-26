package com.worldbetween.sim;

import com.worldbetween.WorldBetween;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.server.MinecraftServer;

/**
 * The two clocks of the mod. The world's day is the sun's; the simulation's day is a day of the peoples'
 * history, and {@link #pace()} of them pass in each day of the world, evenly, one after another. Whatever the
 * player lives through in days (arrivals, quakes, a hero's return, a day's goodwill) is told by the world's clock.
 */
public final class Clock {
	public static final int TICKS_PER_DAY = 24000;

	private Clock() {
	}

	public static double pace() {
		return Math.max(0.1, WorldBetween.config().pace);
	}

	/** So many days of the world, in days of history. */
	public static long simDays(double worldDays) {
		return Math.round(worldDays * pace());
	}

	/** Days of history since the mod began, without rounding: where everything on the road has got to now. */
	public static double now(MinecraftServer server) {
		WorldBetweenState state = WorldBetweenState.get(server);
		double since = server.getOverworld().getTimeOfDay() - state.startTick();
		double now = since * pace() / TICKS_PER_DAY;
		// Never ahead of the last day simulated, never more than a day behind it.
		return Math.max(state.day(), Math.min(state.day() + 1, now));
	}

	/** The day of the world (from 1), as a player counts them, for a day of history. */
	public static long worldDay(long simDay) {
		return (long) Math.floor(simDay / pace()) + 1;
	}

	/** Today, as a player counts it. */
	public static long worldDay(MinecraftServer server) {
		return worldDay(WorldBetweenState.get(server).day());
	}
}
