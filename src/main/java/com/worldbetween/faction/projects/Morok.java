package com.worldbetween.faction.projects;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.journeys.Journey;
import com.worldbetween.faction.journeys.Journeys;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.town.Ground;
import com.worldbetween.war.Battlefield;
import com.worldbetween.war.WarSimulation;
import com.worldbetween.war.Warband;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * The Ancient Morok, hero of the Kaments from before the first war, woken by their deed. He walks the upper
 * world without rest, keeping to the high stone, and falls on every traveller he meets: the kingdom's
 * caravans and patrols, any war party that is not of the mountain, strangers the mountain does not trust.
 * Struck down, he rises again three days later on his seat, and will go on rising for as long as the
 * Kaments' capital stands.
 */
public final class Morok {
	/** How far he walks in a day. */
	private static final int STRIDE = 420;
	/** How far off he smells a traveller. */
	private static final int HUNT = 160;
	public static final int REVIVE_DAYS = 3;

	private Morok() {
	}

	public static boolean awake(Faction kaments) {
		return kaments.counter("morok.alive") > 0;
	}

	public static BlockPos position(Faction kaments) {
		return new BlockPos((int) kaments.counter("morok.x"), 0, (int) kaments.counter("morok.z"));
	}

	public static void setPosition(Faction kaments, BlockPos pos) {
		kaments.setCounter("morok.x", pos.getX());
		kaments.setCounter("morok.z", pos.getZ());
	}

	/** Where he rises: his seat before the gate of the hold that woke him. */
	public static BlockPos seat(ServerWorld world, Faction kaments) {
		Settlement hold = Projects.seat(kaments, Project.MOROK);
		if (hold == null) {
			return null;
		}
		return WorksFeatures.origin(world, kaments, hold, Project.MOROK);
	}

	public static void wake(SimulationContext context, Faction kaments) {
		BlockPos at = seat(context.server().getOverworld(), kaments);
		if (at == null) {
			return;
		}
		setPosition(kaments, at);
		kaments.setCounter("morok.alive", 1);
		kaments.setCounter("morok.dead_until", 0);
	}

	/** Whether the Kaments' capital still stands: while it does, Morok comes back. */
	public static boolean capitalStands(Faction kaments) {
		return kaments.settlements().stream().anyMatch(Settlement::isCapital);
	}

	public static void slain(SimulationContext context, Faction kaments, String by) {
		kaments.setCounter("morok.alive", 0);
		if (capitalStands(kaments)) {
			kaments.setCounter("morok.dead_until", context.day() + com.worldbetween.sim.Clock.simDays(REVIVE_DAYS));
			context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.morok.slain", by);
		} else {
			kaments.setCounter("morok.dead_until", -1);
			context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.morok.gone", by);
		}
	}

	public static void simulateDay(SimulationContext context, Faction kaments) {
		if (!Projects.done(kaments, Project.MOROK)) {
			return;
		}
		ServerWorld world = context.server().getOverworld();
		if (!awake(kaments)) {
			long until = kaments.counter("morok.dead_until");
			if (until > 0 && context.day() >= until && capitalStands(kaments)) {
				wake(context, kaments);
				context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.morok.risen");
			}
			return;
		}
		BlockPos here = position(kaments);
		BlockPos prey = prey(context, kaments, here);
		BlockPos goal = prey != null ? prey : wander(world, kaments, here, context.random());
		double distance = WarSimulation.distance(here, goal);
		BlockPos next = distance <= STRIDE ? goal : WarSimulation.lerp(here, goal, STRIDE / distance);
		setPosition(kaments, next);
		if (prey != null && WarSimulation.distance(next, prey) < 40) {
			strike(context, kaments, next);
		}
	}

	/** The nearest traveller of another people within his reach: a war party or one of the kingdom's journeys. */
	private static BlockPos prey(SimulationContext context, Faction kaments, BlockPos here) {
		BlockPos best = null;
		double bestDistance = STRIDE + HUNT;
		double now = context.day();
		for (Warband band : context.state().wars().bands()) {
			if (band.faction() == FactionType.KAMENT) {
				continue;
			}
			BlockPos at = band.position(now);
			double d = WarSimulation.distance(here, at);
			if (d < bestDistance) {
				bestDistance = d;
				best = at;
			}
		}
		for (Journey journey : context.state().faction(FactionType.NERIS).journeys()) {
			BlockPos at = journey.position(now);
			double d = WarSimulation.distance(here, at);
			if (d < bestDistance) {
				bestDistance = d;
				best = at;
			}
		}
		return best;
	}

	/** He falls on whoever is there: they do not get away. */
	private static void strike(SimulationContext context, Faction kaments, BlockPos at) {
		double now = context.day();
		for (Warband band : List.copyOf(context.state().wars().bands())) {
			if (band.faction() != FactionType.KAMENT && WarSimulation.distance(band.position(now), at) < 60) {
				context.state().wars().bands().remove(band);
				WarSimulation.addField(context, Battlefield.Kind.SKIRMISH, at, band.faction(), band.fighters() + band.beasts(), FactionType.KAMENT, 0);
				context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.morok.band", "@war.worldbetween.people." + band.faction().id());
			}
		}
		Faction neris = context.state().faction(FactionType.NERIS);
		for (Journey journey : List.copyOf(neris.journeys())) {
			if (WarSimulation.distance(journey.position(now), at) < 60) {
				neris.journeys().remove(journey);
				Settlement home = Journeys.settlement(neris, journey.from());
				if (home != null) {
					home.setPopulation(Math.max(1, home.population() - journey.kind().people()));
				}
				WarSimulation.addField(context, Battlefield.Kind.SKIRMISH, at, FactionType.NERIS, journey.kind().people(), FactionType.KAMENT, 0);
				context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.morok.journey", home != null ? home.name() : "?");
			}
		}
	}

	/** Nobody about: on through the high stone, towards a hold of his people or a peak. */
	private static BlockPos wander(ServerWorld world, Faction kaments, BlockPos here, Random random) {
		BlockPos best = null;
		int bestHeight = -1;
		for (int i = 0; i < 8; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int x = here.getX() + (int) (Math.cos(angle) * STRIDE);
			int z = here.getZ() + (int) (Math.sin(angle) * STRIDE);
			int height = Ground.floor(world, x, z);
			if (!Ground.isWater(world, x, z) && height > bestHeight) {
				bestHeight = height;
				best = new BlockPos(x, 0, z);
			}
		}
		// Now and then back to his own people.
		if (best == null || random.nextInt(5) == 0) {
			Settlement hold = kaments.settlements().stream().filter(s -> !com.worldbetween.faction.kament.Kaments.isDeep(s))
					.skip(random.nextInt(Math.max(1, kaments.settlements().size()))).findFirst().orElse(null);
			if (hold != null) {
				return hold.pos().withY(0);
			}
		}
		return best == null ? here : best;
	}
}
