package com.worldbetween.faction.arki;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionBehavior;
import com.worldbetween.faction.FactionBehaviors;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.sim.LocationFinder;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.town.Ground;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The Arki: exiles of the sky. Their gods cast them out of space for prying into the gods' secrets and
 * took from them the power to live there; the few who escaped the gods' wrath came down to this world.
 *
 * <p>They come down on blue and violet comets, and keep coming: a colony that gathers enough starlight
 * can call more of the exiles still wandering the dark. No colony rules another. Each keeps its own
 * stores, but they honour one another and send capsules of help across the sky; there are no roads
 * between them. They live on amethyst, on what the sea gives, and on starlight, which only the night
 * gives, the more the fuller the moon.
 */
public class ArkiBehavior implements FactionBehavior {
	private static final long[] ERA_LORE = {0, 100, 800, 3000};
	private static final int FOUNDERS = 5;
	private static final int CALL_STARLIGHT = 60;
	private static final int CALLED = 2;
	private static final int MAX_COLONIES = 12;
	private static final long STORE = 2000;

	@Override
	public int arrivalDay(SimulationContext context) {
		FactionBehavior kaments = FactionBehaviors.get(FactionType.KAMENT);
		int afterKaments = kaments == null ? 0 : kaments.arrivalDay(context) + 1;
		return Math.max(context.config().arkiArrivalDay, afterKaments);
	}

	@Override
	public boolean arrive(SimulationContext context, Faction faction) {
		ServerWorld world = context.server().getOverworld();
		return arriveNear(context, faction, world.getSpawnPos(), 300, 1400);
	}

	@Override
	public boolean arriveNear(SimulationContext context, Faction faction, BlockPos center, int minDistance, int maxDistance) {
		ServerWorld world = context.server().getOverworld();
		BlockPos site = seaSite(context, world, center, minDistance, maxDistance);
		if (site == null) {
			site = seaSite(context, world, center, maxDistance, maxDistance * 3);
		}
		if (site == null) {
			return false;
		}
		// The first comet is on its way; it comes down tonight.
		queue(faction, Comet.Kind.SEA_COLONY, site, null, -1, FOUNDERS, null, 0);
		faction.setArrived(true);
		return true;
	}

	/** Open sea, deep enough for the crystal cushions, and clear of everyone. */
	private static BlockPos seaSite(SimulationContext context, ServerWorld world, BlockPos center, int min, int max) {
		for (int attempt = 0; attempt < 6; attempt++) {
			BlockPos site = LocationFinder.findSite(context, world, FactionType.ARKI, center, min, max);
			if (site != null && Ground.surface(world, site.getX(), site.getZ()) - Ground.floor(world, site.getX(), site.getZ()) >= 4) {
				return new BlockPos(site.getX(), world.getSeaLevel(), site.getZ());
			}
		}
		return null;
	}

	private static void queue(Faction faction, Comet.Kind kind, BlockPos target, BlockPos from, int colony, int people, String resource, long amount) {
		faction.comets().add(new Comet((int) faction.counter("next_comet"), kind, target, from, colony, people, resource, amount));
		faction.addCounter("next_comet", 1);
	}

	@Override
	public void simulateDay(SimulationContext context, Faction faction) {
		// Whatever did not come down in the night came down unseen.
		for (Comet comet : List.copyOf(faction.comets())) {
			land(context, faction, comet);
		}
		long day = context.day();
		for (Settlement colony : List.copyOf(faction.settlements())) {
			work(context, faction, colony, day);
			build(context, faction, colony);
			grow(context, colony);
		}
		call(context, faction);
		help(context, faction);
		expand(context, faction);
		era(context, faction);
	}

	/** A comet or capsule comes down: a colony is founded, exiles join one, help arrives. */
	public static void land(SimulationContext context, Faction faction, Comet comet) {
		if (!faction.comets().remove(comet)) {
			return;
		}
		switch (comet.kind()) {
			case SEA_COLONY, SKY_COLONY -> {
				Settlement colony = new Settlement(faction.nextSettlementId(), ArkiNames.colony(context.random()), comet.target(),
						World.OVERWORLD, context.day());
				colony.setPopulation(comet.people());
				colony.setCounter("kind", comet.kind() == Comet.Kind.SKY_COLONY ? Arkis.SKY : Arkis.SEA);
				colony.setCounter(Arkis.AMETHYST, 40);
				colony.setCounter(Arkis.SEA_GOODS, 20);
				colony.setCounter(Arkis.STARLIGHT, 20);
				boolean first = faction.settlements().isEmpty();
				faction.settlements().add(colony);
				context.chronicle(FactionType.ARKI, first ? "chronicle.worldbetween.arki.arrival"
								: comet.kind() == Comet.Kind.SKY_COLONY ? "chronicle.worldbetween.arki.sky_colony" : "chronicle.worldbetween.arki.colony",
						colony.name(), comet.target().getX(), comet.target().getZ());
			}
			case ARRIVALS -> {
				Settlement colony = colony(faction, comet.colony());
				if (colony != null) {
					colony.setPopulation(Math.min(Arkis.housing(colony), colony.population() + comet.people()));
					if (faction.tryEvent("arrivals_" + colony.id(), context.day(), 20)) {
						context.chronicle(FactionType.ARKI, "chronicle.worldbetween.arki.arrivals", colony.name(), comet.people());
					}
				}
			}
			case HELP -> {
				Settlement colony = colony(faction, comet.colony());
				if (colony != null) {
					colony.addCounter(comet.resource(), comet.amount());
				}
			}
			case STRIKE -> com.worldbetween.war.Weapons.cometStrike(context, comet);
		}
	}

	private void work(SimulationContext context, Faction faction, Settlement colony, long day) {
		int available = colony.population();
		// Every one of them takes a little from the sea.
		colony.addCounter(Arkis.SEA_GOODS, Math.max(1, available / 2));
		Map<ArkiHall, Integer> halls = Arkis.halls(colony);
		int garden = staff(halls, ArkiHall.GARDEN, available);
		available -= garden;
		int lens = staff(halls, ArkiHall.LENS, available);
		available -= lens;
		int sea = staff(halls, ArkiHall.SEAWORKS, available);
		available -= sea;
		int observatory = staff(halls, ArkiHall.OBSERVATORY, available);
		available -= observatory;
		colony.addCounter(Arkis.AMETHYST, garden * 4L);
		// Starlight only comes by night, and with the moon.
		// Under the Eternal Night every night is the darkest, and the lenses drink deeper.
		float night = com.worldbetween.faction.projects.WorksFeatures.working(faction, com.worldbetween.faction.projects.Project.NIGHT_TOWER) ? 1.5f : 1f;
		colony.addCounter(Arkis.STARLIGHT, Math.round(lens * 9 * Arkis.moonlight(day) * (Arkis.isSky(colony) ? 1.5f : 1f) * night));
		colony.addCounter(Arkis.SEA_GOODS, sea * 5L);
		// The observatory only reads what is spare: enough starlight is always kept back to call a comet.
		long spent = Math.max(0, Math.min(colony.counter(Arkis.STARLIGHT) - CALL_STARLIGHT * 2L, observatory * 3L));
		colony.addCounter(Arkis.STARLIGHT, -spent);
		long lore = spent * 2 / 3;
		colony.addCounter(Arkis.LORE, lore);
		faction.addResource(ResourceType.KNOWLEDGE, lore);
		colony.setCounter("idle", available);
		for (String store : Arkis.STORES) {
			colony.setCounter(store, Math.min(STORE, colony.counter(store)));
		}
	}

	private static int staff(Map<ArkiHall, Integer> halls, ArkiHall hall, int available) {
		return Math.max(0, Math.min(halls.getOrDefault(hall, 0), available));
	}

	private void build(SimulationContext context, Faction faction, Settlement colony) {
		ArkiHall project = ArkiHall.byOrdinal(colony.counter("project") - 1);
		if (project == null) {
			ArkiHall next = chooseProject(colony);
			if (next == null || colony.counter(Arkis.AMETHYST) < next.amethyst() || colony.counter(Arkis.SEA_GOODS) < next.sea()
					|| colony.counter(Arkis.STARLIGHT) < next.starlight()) {
				return;
			}
			colony.addCounter(Arkis.AMETHYST, -next.amethyst());
			colony.addCounter(Arkis.SEA_GOODS, -next.sea());
			colony.addCounter(Arkis.STARLIGHT, -next.starlight());
			colony.setCounter("project", next.ordinal() + 1);
			colony.setCounter("progress", 0);
			return;
		}
		colony.addCounter("progress", 1 + colony.counter("idle") / 2);
		if (colony.counter("progress") < project.days()) {
			return;
		}
		colony.setCounter("project", 0);
		colony.setCounter("progress", 0);
		if (Arkis.place(colony, project) >= 0 && faction.tryEvent("first_" + project.id(), context.day(), Long.MAX_VALUE)) {
			context.chronicle(FactionType.ARKI, "chronicle.worldbetween.arki.first_" + project.id(), colony.name());
		}
	}

	private ArkiHall chooseProject(Settlement colony) {
		if (Arkis.hallTotal(colony) >= Arkis.capacity(colony)) {
			return null;
		}
		int pop = colony.population();
		Map<ArkiHall, Integer> halls = Arkis.halls(colony);
		java.util.function.ToIntFunction<ArkiHall> n = hall -> halls.getOrDefault(hall, 0);
		if (n.applyAsInt(ArkiHall.GARDEN) == 0) return ArkiHall.GARDEN;
		if (n.applyAsInt(ArkiHall.SEAWORKS) == 0) return ArkiHall.SEAWORKS;
		if (n.applyAsInt(ArkiHall.LENS) == 0) return ArkiHall.LENS;
		if (pop >= Arkis.housing(colony) - 2) return ArkiHall.DWELLING;
		if (n.applyAsInt(ArkiHall.OBSERVATORY) == 0) return ArkiHall.OBSERVATORY;
		if (n.applyAsInt(ArkiHall.PAD) == 0) return ArkiHall.PAD;
		if (n.applyAsInt(ArkiHall.LENS) < 1 + pop / 6) return ArkiHall.LENS;
		if (n.applyAsInt(ArkiHall.GARDEN) < 1 + pop / 6) return ArkiHall.GARDEN;
		if (n.applyAsInt(ArkiHall.SEAWORKS) < 1 + pop / 8) return ArkiHall.SEAWORKS;
		if (n.applyAsInt(ArkiHall.OBSERVATORY) < 1 + pop / 10) return ArkiHall.OBSERVATORY;
		return null;
	}

	private void grow(SimulationContext context, Settlement colony) {
		int tier = 0;
		for (int i = 0; i < Arkis.TIER_POPULATION.length; i++) {
			if (colony.population() >= Arkis.TIER_POPULATION[i]) {
				tier = i;
			}
		}
		if (tier > colony.tier()) {
			colony.setTier(tier);
			context.chronicle(FactionType.ARKI, "chronicle.worldbetween.arki.tier_" + tier, colony.name());
		}
	}

	/** A colony with starlight to spare calls more of the exiles out of the dark. */
	private void call(SimulationContext context, Faction faction) {
		for (Settlement colony : faction.settlements()) {
			boolean coming = faction.comets().stream().anyMatch(c -> c.kind() == Comet.Kind.ARRIVALS && c.colony() == colony.id());
			if (coming || colony.population() + CALLED > Arkis.housing(colony) || colony.counter(Arkis.STARLIGHT) < CALL_STARLIGHT
					|| !faction.tryEvent("call_" + colony.id(), context.day(), 12)) {
				continue;
			}
			colony.addCounter(Arkis.STARLIGHT, -CALL_STARLIGHT);
			queue(faction, Comet.Kind.ARRIVALS, colony.pos(), null, colony.id(), CALLED, null, 0);
		}
	}

	/** No colony rules another, but none lets another go short: a capsule of help goes across the sky. */
	private void help(SimulationContext context, Faction faction) {
		if (faction.settlements().size() < 2 || !faction.tryEvent("help", context.day(), 3)) {
			return;
		}
		for (String store : Arkis.STORES) {
			Settlement poorest = faction.settlements().stream().min(Comparator.comparingLong(s -> s.counter(store))).orElse(null);
			Settlement richest = faction.settlements().stream()
					.filter(s -> Arkis.count(s, ArkiHall.PAD) > 0)
					.max(Comparator.comparingLong(s -> s.counter(store))).orElse(null);
			if (poorest == null || richest == null || poorest == richest || poorest.counter(store) >= 20 || richest.counter(store) < 150) {
				continue;
			}
			long amount = Math.min(80, richest.counter(store) - 100);
			richest.addCounter(store, -amount);
			queue(faction, Comet.Kind.HELP, poorest.pos(), richest.pos(), poorest.id(), 0, store, amount);
			if (faction.tryEvent("help_told_" + poorest.id(), context.day(), 15)) {
				context.chronicle(FactionType.ARKI, "chronicle.worldbetween.arki.help", richest.name(), poorest.name(),
						"@arki_store.worldbetween." + store);
			}
			return;
		}
	}

	/** A grown colony with a landing pad sends for exiles to found another, on the sea or, later, in the sky. */
	private void expand(SimulationContext context, Faction faction) {
		boolean founding = faction.comets().stream().anyMatch(c -> c.kind() == Comet.Kind.SEA_COLONY || c.kind() == Comet.Kind.SKY_COLONY);
		if (founding || faction.settlements().size() >= MAX_COLONIES) {
			return;
		}
		Settlement parent = faction.settlements().stream()
				.filter(s -> Arkis.count(s, ArkiHall.PAD) > 0 && s.population() >= 8
						&& s.counter(Arkis.STARLIGHT) >= 150 && s.counter(Arkis.AMETHYST) >= 100)
				.findFirst().orElse(null);
		if (parent == null || !faction.tryEvent("expand", context.day(), 40)) {
			return;
		}
		ServerWorld world = context.server().getOverworld();
		Random random = context.random();
		boolean sky = faction.era() >= 1 && random.nextFloat() < 0.4f;
		BlockPos site;
		if (sky) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = MathHelper.lerp(random.nextDouble(), 300, 800);
			int x = parent.pos().getX() + (int) (Math.cos(angle) * distance);
			int z = parent.pos().getZ() + (int) (Math.sin(angle) * distance);
			int ground = Ground.floor(world, x, z);
			site = new BlockPos(x, Math.max(200, Math.min(world.getTopY() - 40, ground + 60)) + random.nextInt(30), z);
			BlockPos candidate = site;
			boolean clear = java.util.stream.StreamSupport.stream(context.state().factions().spliterator(), false)
					.flatMap(f -> f.settlements().stream())
					.allMatch(s -> s.pos().withY(0).getSquaredDistance(candidate.withY(0)) > 200 * 200);
			if (!clear) {
				return;
			}
		} else {
			site = seaSite(context, world, parent.pos(), 300, 900);
		}
		if (site == null) {
			return;
		}
		parent.addCounter(Arkis.STARLIGHT, -150);
		parent.addCounter(Arkis.AMETHYST, -100);
		queue(faction, sky ? Comet.Kind.SKY_COLONY : Comet.Kind.SEA_COLONY, site, parent.pos(), -1, 4, null, 0);
		context.chronicle(FactionType.ARKI, "chronicle.worldbetween.arki.summon", parent.name());
	}

	private void era(SimulationContext context, Faction faction) {
		int era = faction.era();
		if (era < ERA_LORE.length - 1 && faction.resource(ResourceType.KNOWLEDGE) >= ERA_LORE[era + 1]) {
			faction.setEra(era + 1);
			context.chronicle(FactionType.ARKI, "chronicle.worldbetween.arki.era_" + (era + 1));
		}
	}

	public static Settlement colony(Faction faction, int id) {
		return faction.settlements().stream().filter(s -> s.id() == id).findFirst().orElse(null);
	}
}
