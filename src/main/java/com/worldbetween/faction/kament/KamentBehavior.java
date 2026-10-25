package com.worldbetween.faction.kament;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionBehavior;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.hold.HoldLayout;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.town.Ground;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.worldbetween.faction.ResourceType.BRICKS;
import static com.worldbetween.faction.ResourceType.DIORITE;
import static com.worldbetween.faction.ResourceType.FLINT;
import static com.worldbetween.faction.ResourceType.GRAVEL;
import static com.worldbetween.faction.ResourceType.KNOWLEDGE;
import static com.worldbetween.faction.ResourceType.ORE;
import static com.worldbetween.faction.ResourceType.STONE;

/**
 * The Kaments: the stone race that was old before life began. Their wars made Hell itself, as a road to
 * move armies by; to end them, their wisest lords worked a ritual that destroyed most of the race and
 * left the calmest few in the mourning sleep. The opening of Hell wakes them.
 *
 * <p>They do not eat and are not born. They live on stone, diorite and gravel, and the flint sifted from
 * gravel is their key to every craft. New Kaments are hewn by the soul-sculptors, who pour a part of their
 * own soul into each one. Every settlement has its Ancient; the Ancients rule together in council.
 * Mountain holds are hewn into peaks; under each grows a deep city; from the deep cities long tunnels
 * reach out through the rock to the next.
 */
public class KamentBehavior implements FactionBehavior {
	private static final long[] ERA_FLINT = {0, 40, 800, 4000};
	private static final long[] ERA_MEMORY = {0, 0, 150, 1500};
	private static final int AWAKENED = 4;
	private static final int MIGRANTS = 3;
	private static final int CARVE_STONE = 80;
	private static final int CARVE_DIORITE = 20;
	private static final int CARVE_BRICKS = 10;
	private static final int CARVE_FLINT = 6;
	private static final int SOUL_PER_KAMENT = 25;
	private static final int SOUL_PER_SCULPTOR = 60;
	private static final long STORE = 3000;
	private static final int MIN_HOLD_HEIGHT = 100;

	@Override
	public int arrivalDay(SimulationContext context) {
		Faction neris = context.state().faction(FactionType.NERIS);
		if (neris.hasArrived() && !neris.settlements().isEmpty()) {
			// The opening of Hell wakes them, a day of earthquakes after.
			return (int) neris.settlements().get(0).foundedDay() + 1;
		}
		return context.config().kamentArrivalDay;
	}

	@Override
	public boolean arrive(SimulationContext context, Faction faction) {
		ServerWorld world = context.server().getOverworld();
		List<BlockPos> sites = faction.knownSites().isEmpty()
				? awakeningSites(context, world, world.getSpawnPos(), context.config().kamentAwakeningSites)
				: List.copyOf(faction.knownSites());
		return awaken(context, faction, sites);
	}

	@Override
	public boolean arriveNear(SimulationContext context, Faction faction, BlockPos center, int minDistance, int maxDistance) {
		ServerWorld world = context.server().getOverworld();
		List<BlockPos> sites = new ArrayList<>(awakeningSites(context, world, center, 1, minDistance, maxDistance));
		if (sites.isEmpty()) {
			return false;
		}
		sites.addAll(awakeningSites(context, world, center, context.config().kamentAwakeningSites - 1));
		return awaken(context, faction, sites);
	}

	private boolean awaken(SimulationContext context, Faction faction, List<BlockPos> sites) {
		if (sites.isEmpty()) {
			return false;
		}
		faction.knownSites().clear();
		for (BlockPos site : sites) {
			Settlement hold = found(context, faction, site, Kaments.MOUNTAIN, AWAKENED, -1);
			hold.setCounter("sculptors", 1);
			hold.setCounter("soul", 60);
			context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.awakening", hold.name(), site.getX(), site.getZ(),
					Kaments.ancientOf(faction, hold).name());
		}
		faction.addResource(STONE, 120);
		faction.addResource(DIORITE, 40);
		faction.addResource(GRAVEL, 40);
		faction.addResource(BRICKS, 30);
		faction.addResource(FLINT, 8);
		faction.setRelation(FactionType.NERIS, -30);
		faction.setArrived(true);
		context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.arrival", sites.size());
		com.worldbetween.entity.kament.KamentAwakening.awaken(context.server(), faction);
		return true;
	}

	/**
	 * Peaks to wake on: high, mountainous, far from everyone and from each other. The search widens until
	 * enough are found, so even a world of low hills has its Kaments somewhere.
	 */
	public static List<BlockPos> awakeningSites(SimulationContext context, ServerWorld world, BlockPos center, int count) {
		List<BlockPos> sites = new ArrayList<>();
		int[][] rings = {{300, 900}, {900, 1600}, {1600, 2600}, {2600, 4000}};
		for (int[] ring : rings) {
			if (sites.size() >= count) {
				break;
			}
			sites.addAll(awakeningSites(context, world, center, count - sites.size(), ring[0], ring[1], sites));
		}
		return sites;
	}

	private static List<BlockPos> awakeningSites(SimulationContext context, ServerWorld world, BlockPos center, int count, int min, int max) {
		return awakeningSites(context, world, center, count, min, max, List.of());
	}

	private static List<BlockPos> awakeningSites(SimulationContext context, ServerWorld world, BlockPos center, int count, int min, int max,
			List<BlockPos> taken) {
		List<BlockPos> sites = new ArrayList<>();
		Random random = context.random();
		for (int i = 0; i < 400 && sites.size() < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = MathHelper.lerp(random.nextDouble(), min, max);
			int x = center.getX() + (int) (Math.cos(angle) * distance);
			int z = center.getZ() + (int) (Math.sin(angle) * distance);
			int y = Ground.floor(world, x, z);
			if (y < MIN_HOLD_HEIGHT || !isMountain(world, x, y, z)) {
				continue;
			}
			BlockPos pos = new BlockPos(x, y, z);
			boolean clear = sites.stream().allMatch(s -> s.getSquaredDistance(pos) > 300 * 300)
					&& taken.stream().allMatch(s -> s.getSquaredDistance(pos) > 300 * 300)
					&& java.util.stream.StreamSupport.stream(context.state().factions().spliterator(), false).flatMap(f -> f.settlements().stream())
					.allMatch(s -> s.pos().withY(0).getSquaredDistance(pos.withY(0)) > 250 * 250);
			if (clear) {
				sites.add(pos);
			}
		}
		return sites;
	}

	private static boolean isMountain(ServerWorld world, int x, int y, int z) {
		RegistryEntry<Biome> biome = world.getChunkManager().getChunkGenerator().getBiomeSource().getBiome(
				BiomeCoords.fromBlock(x), BiomeCoords.fromBlock(y), BiomeCoords.fromBlock(z),
				world.getChunkManager().getNoiseConfig().getMultiNoiseSampler());
		return FactionType.KAMENT.prefersBiome(biome);
	}

	@Override
	public void simulateDay(SimulationContext context, Faction faction) {
		succession(context, faction);
		for (Settlement settlement : List.copyOf(faction.settlements())) {
			work(context, faction, settlement);
			carve(context, faction, settlement);
			build(context, faction, settlement);
			grow(context, settlement);
		}
		for (ResourceType resource : ResourceType.values()) {
			if (faction.resource(resource) > STORE) {
				faction.takeResource(resource, faction.resource(resource) - STORE);
			}
		}
		council(context, faction);
		deepCities(context, faction);
		tunnels(context, faction);
		newHolds(context, faction);
		era(context, faction);
	}

	// ---------------------------------------------------------------- work

	private void work(SimulationContext context, Faction faction, Settlement settlement) {
		int available = settlement.population();
		Focus focus = Kaments.focus(faction);
		float lead = Kaments.ancientOf(faction, settlement) == null ? 0.5f : 1f;
		// Everyone breaks a little stone and gathers a little gravel with their own hands.
		faction.addResource(STONE, available);
		faction.addResource(GRAVEL, available / 2);
		Map<KamentHall, Integer> halls = Kaments.halls(settlement);
		Map<KamentHall, Integer> staffed = new EnumMap<>(KamentHall.class);
		for (KamentHall hall : new KamentHall[]{KamentHall.QUARRY, KamentHall.GRAVEL_PIT, KamentHall.KNAPPERY, KamentHall.CARVERY,
				KamentHall.MEMORY, KamentHall.HEALER}) {
			int count = Math.min(halls.getOrDefault(hall, 0), available / Math.max(1, hall.workers()));
			staffed.put(hall, count);
			available -= count * hall.workers();
		}
		float quarry = (focus == Focus.STONE ? 1.5f : 1f) * lead;
		faction.addResource(STONE, Math.round(staffed.get(KamentHall.QUARRY) * 8 * quarry));
		faction.addResource(DIORITE, Math.round(staffed.get(KamentHall.QUARRY) * 3 * quarry));
		faction.addResource(GRAVEL, Math.round(staffed.get(KamentHall.GRAVEL_PIT) * 6 * lead));
		long gravel = faction.takeResource(GRAVEL, staffed.get(KamentHall.KNAPPERY) * 6L);
		// Sifting is slow: six measures of gravel give one of flint.
		long flint = Math.round(gravel / 6f * lead);
		faction.addResource(FLINT, flint);
		faction.addCounter("flint_made", flint);
		long stone = faction.takeResource(STONE, staffed.get(KamentHall.CARVERY) * 6L);
		faction.addResource(BRICKS, Math.round(stone * 2 / 3f * lead));
		float memory = (focus == Focus.MEMORY ? 1.5f : 1f) * lead;
		faction.addResource(KNOWLEDGE, Math.round(staffed.get(KamentHall.MEMORY) * memory));
		// The healers give strangers' metal back to the mountain, and the mountain remembers.
		long ore = faction.takeResource(ORE, staffed.get(KamentHall.HEALER) * 2L);
		settlement.addCounter("healed", ore);
		faction.addResource(KNOWLEDGE, ore / 2);
		settlement.setCounter("idle", available);

		// Diggers drive the city's tunnel on.
		for (Tunnel tunnel : faction.tunnels()) {
			if (tunnel.from() == settlement.id() && !tunnel.isThrough()) {
				int diggers = Math.min(2, Math.max(0, available));
				tunnel.dig((4 + 4 * diggers) * (focus == Focus.DEPTH ? 1.5 : 1) * lead);
			}
		}
	}

	/** The soul-sculptors hew new Kaments, each taking a part of the sculptor's soul. */
	private void carve(SimulationContext context, Faction faction, Settlement settlement) {
		long sculptors = settlement.counter("sculptors");
		if (sculptors <= 0) {
			return;
		}
		// A sculptor's soul grows back slowly: the Kaments hardly replenish their numbers.
		settlement.setCounter("soul", Math.min(100 * sculptors, settlement.counter("soul") + sculptors));
		if (Kaments.count(settlement, KamentHall.SCULPTORY) == 0) {
			return;
		}
		Ancient ancient = Kaments.ancientOf(faction, settlement);
		long left = settlement.counter("carving");
		if (left > 0) {
			int speed = 1 + (Kaments.focus(faction) == Focus.CARVING ? 1 : 0);
			settlement.setCounter("carving", Math.max(0, left - speed));
			if (left - speed > 0) {
				return;
			}
			if (settlement.counter("carving_sculptor") > 0) {
				settlement.setCounter("carving_sculptor", 0);
				settlement.addCounter("sculptors", 1);
				context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.sculptor", settlement.name());
			} else {
				settlement.setPopulation(settlement.population() + 1);
				if (faction.tryEvent("carved_" + settlement.id(), context.day(), 30)) {
					context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.carved", settlement.name(), KamentNames.generate(context.random()));
				}
			}
			return;
		}
		if (faction.resource(STONE) < CARVE_STONE || faction.resource(DIORITE) < CARVE_DIORITE
				|| faction.resource(BRICKS) < CARVE_BRICKS || faction.resource(FLINT) < CARVE_FLINT
				|| settlement.counter("soul") < SOUL_PER_KAMENT) {
			return;
		}
		faction.takeResource(STONE, CARVE_STONE);
		faction.takeResource(DIORITE, CARVE_DIORITE);
		faction.takeResource(BRICKS, CARVE_BRICKS);
		faction.takeResource(FLINT, CARVE_FLINT);
		// From the age of Renewal, every fourth time a sculptor saves its soul to hew another sculptor.
		boolean sculptor = faction.era() >= 2 && sculptors < 1 + (settlement.tier() + 1) / 2 && settlement.counter("since_sculptor") >= 3;
		if (sculptor && settlement.counter("soul") < SOUL_PER_SCULPTOR) {
			faction.addResource(STONE, CARVE_STONE);
			faction.addResource(DIORITE, CARVE_DIORITE);
			faction.addResource(BRICKS, CARVE_BRICKS);
			faction.addResource(FLINT, CARVE_FLINT);
			return;
		}
		settlement.setCounter("since_sculptor", sculptor ? 0 : settlement.counter("since_sculptor") + 1);
		settlement.addCounter("soul", -(sculptor ? SOUL_PER_SCULPTOR : SOUL_PER_KAMENT));
		settlement.setCounter("carving_sculptor", sculptor ? 1 : 0);
		int days = Math.max(4, 10 - 2 * faction.era());
		if (ancient != null && ancient.has(KamentTrait.GRIEVING)) {
			days += 2;
		}
		if (ancient != null && ancient.has(KamentTrait.SCULPTOR)) {
			days -= 2;
		}
		settlement.setCounter("carving", Math.max(3, days));
	}

	private void build(SimulationContext context, Faction faction, Settlement settlement) {
		KamentHall project = KamentHall.byOrdinal(settlement.counter("project") - 1);
		if (project == null) {
			KamentHall next = chooseProject(faction, settlement);
			if (next == null || !canAfford(faction, next)) {
				return;
			}
			faction.takeResource(STONE, next.stone());
			faction.takeResource(DIORITE, next.diorite());
			faction.takeResource(BRICKS, next.bricks());
			faction.takeResource(FLINT, next.flint());
			settlement.setCounter("project", next.ordinal() + 1);
			settlement.setCounter("progress", 0);
			return;
		}
		settlement.addCounter("progress", 1 + settlement.counter("idle") / 3);
		if (settlement.counter("progress") < project.days()) {
			return;
		}
		settlement.setCounter("project", 0);
		settlement.setCounter("progress", 0);
		if (Kaments.carveHall(settlement, project) >= 0
				&& faction.tryEvent("first_" + project.id(), context.day(), Long.MAX_VALUE)) {
			context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.first_" + project.id(), settlement.name());
		}
	}

	private static boolean canAfford(Faction faction, KamentHall hall) {
		return faction.resource(STONE) >= hall.stone() && faction.resource(DIORITE) >= hall.diorite()
				&& faction.resource(BRICKS) >= hall.bricks() && faction.resource(FLINT) >= hall.flint();
	}

	/** One of every kind first, the sculptors' hall as soon as there is a sculptor, then more of what is short. */
	private KamentHall chooseProject(Faction faction, Settlement settlement) {
		if (Kaments.hallTotal(settlement) >= Kaments.capacity(settlement)) {
			return null;
		}
		int tier = settlement.tier();
		int pop = settlement.population();
		Map<KamentHall, Integer> halls = Kaments.halls(settlement);
		java.util.function.ToIntFunction<KamentHall> n = hall -> halls.getOrDefault(hall, 0);
		Focus focus = Kaments.focus(faction);
		if (n.applyAsInt(KamentHall.QUARRY) == 0) return KamentHall.QUARRY;
		if (n.applyAsInt(KamentHall.GRAVEL_PIT) == 0) return KamentHall.GRAVEL_PIT;
		if (n.applyAsInt(KamentHall.KNAPPERY) == 0) return KamentHall.KNAPPERY;
		if (n.applyAsInt(KamentHall.CARVERY) == 0) return KamentHall.CARVERY;
		if (n.applyAsInt(KamentHall.SCULPTORY) == 0 && settlement.counter("sculptors") > 0) return KamentHall.SCULPTORY;
		if (n.applyAsInt(KamentHall.MEMORY) == 0) return KamentHall.MEMORY;
		if (tier >= 1 && n.applyAsInt(KamentHall.WARD) == 0) return KamentHall.WARD;
		if (tier >= 1 && n.applyAsInt(KamentHall.HEALER) == 0) return KamentHall.HEALER;
		if (focus == Focus.MEMORY && n.applyAsInt(KamentHall.MEMORY) < 2) return KamentHall.MEMORY;
		if (focus == Focus.WARD && n.applyAsInt(KamentHall.WARD) < 2) return KamentHall.WARD;
		if (n.applyAsInt(KamentHall.QUARRY) < 1 + pop / 6) return KamentHall.QUARRY;
		if (n.applyAsInt(KamentHall.GRAVEL_PIT) < 1 + pop / 8) return KamentHall.GRAVEL_PIT;
		if (n.applyAsInt(KamentHall.KNAPPERY) < n.applyAsInt(KamentHall.GRAVEL_PIT)) return KamentHall.KNAPPERY;
		if (n.applyAsInt(KamentHall.CARVERY) < 1 + pop / 10) return KamentHall.CARVERY;
		return null;
	}

	private void grow(SimulationContext context, Settlement settlement) {
		int tier = 0;
		for (int i = 0; i < Kaments.TIER_POPULATION.length; i++) {
			if (settlement.population() >= Kaments.TIER_POPULATION[i]) {
				tier = i;
			}
		}
		if (tier > settlement.tier()) {
			settlement.setTier(tier);
			context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament." + (Kaments.isDeep(settlement) ? "deep_" : "")
					+ "tier_" + tier, settlement.name());
		}
	}

	// ---------------------------------------------------------------- the council

	/** An Ancient fallen: a sculptor of the city takes the seat, or else the eldest of its people. */
	private void succession(SimulationContext context, Faction faction) {
		for (Settlement settlement : faction.settlements()) {
			if (Kaments.ancientOf(faction, settlement) != null) {
				continue;
			}
			if (settlement.counter("sculptors") > 0) {
				settlement.addCounter("sculptors", -1);
			} else if (settlement.population() > 1) {
				settlement.setPopulation(settlement.population() - 1);
			} else {
				continue;
			}
			Ancient ancient = newAncient(context, faction, settlement);
			context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.succession", ancient.name(), settlement.name());
		}
	}

	private static Ancient newAncient(SimulationContext context, Faction faction, Settlement settlement) {
		Random random = context.random();
		KamentTrait[] traits = KamentTrait.values();
		KamentTrait first = traits[random.nextInt(traits.length)];
		KamentTrait second;
		do {
			second = traits[random.nextInt(traits.length)];
		} while (second == first);
		Ancient ancient = new Ancient((int) faction.counter("next_ancient"), KamentNames.generate(random), settlement.id(), first, second);
		faction.addCounter("next_ancient", 1);
		faction.ancients().add(ancient);
		return ancient;
	}

	/** Every seven days the Ancients meet and decide, slowly, what the people should work at. */
	private void council(SimulationContext context, Faction faction) {
		if (!faction.tryEvent("council", context.day(), 7)) {
			return;
		}
		Map<Focus, Integer> votes = new EnumMap<>(Focus.class);
		for (Ancient ancient : faction.ancients()) {
			if (ancient.isAlive()) {
				votes.merge(ancient.first().leaning(), 2, Integer::sum);
				votes.merge(ancient.second().leaning(), 1, Integer::sum);
			}
		}
		if (votes.isEmpty()) {
			return;
		}
		int best = votes.values().stream().mapToInt(Integer::intValue).max().orElse(0);
		List<Focus> top = votes.entrySet().stream().filter(e -> e.getValue() == best).map(Map.Entry::getKey).toList();
		Focus chosen = top.get(context.random().nextInt(top.size()));
		if (chosen != Kaments.focus(faction)) {
			faction.setCounter("focus", chosen.ordinal() + 1);
			context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.council", "@focus.worldbetween." + chosen.id());
		}
	}

	// ---------------------------------------------------------------- growth

	/** Under every mountain hold that has grown into a hall, a deep city is begun, reached by the spiral stair. */
	private void deepCities(SimulationContext context, Faction faction) {
		ServerWorld world = context.server().getOverworld();
		// The deep city fills from the hold above: now and then one goes down the stair for good, and a spare sculptor with them.
		for (Settlement hold : faction.settlements()) {
			Settlement deep = com.worldbetween.hold.HoldFeatures.child(faction, hold);
			if (deep == null) {
				continue;
			}
			if (hold.population() > 6 + 2 * hold.tier() && faction.tryEvent("descent_" + hold.id(), context.day(), 10)) {
				hold.setPopulation(hold.population() - 1);
				deep.setPopulation(deep.population() + 1);
			}
			if (hold.counter("sculptors") >= 2 && deep.counter("sculptors") == 0) {
				hold.addCounter("sculptors", -1);
				deep.setCounter("sculptors", 1);
				context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.sculptor_down", hold.name(), deep.name());
			}
		}
		for (Settlement hold : List.copyOf(faction.settlements())) {
			if (Kaments.kind(hold) != Kaments.MOUNTAIN || hold.tier() < 1 || hold.counter("child") != 0 || hold.population() < MIGRANTS + 3) {
				continue;
			}
			BlockPos below = HoldLayout.of(world, hold).deepCity(world);
			hold.setPopulation(hold.population() - MIGRANTS);
			Settlement deep = found(context, faction, below, Kaments.DEEP, MIGRANTS, hold.id());
			hold.setCounter("child", deep.id() + 1);
			context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.deep_city", hold.name(), deep.name());
		}
	}

	/** Deep cities drive tunnels out through the rock; at the end of each, a new deep city is founded. */
	private void tunnels(SimulationContext context, Faction faction) {
		Random random = context.random();
		ServerWorld world = context.server().getOverworld();
		for (Tunnel tunnel : faction.tunnels()) {
			if (!tunnel.isThrough() || tunnel.to() >= 0) {
				continue;
			}
			Settlement origin = faction.settlements().stream().filter(s -> s.id() == tunnel.from()).findFirst().orElse(null);
			if (origin == null || origin.population() < MIGRANTS + 2) {
				continue;
			}
			origin.setPopulation(origin.population() - MIGRANTS);
			Settlement city = found(context, faction, tunnel.end(), Kaments.DEEP, MIGRANTS, origin.id());
			tunnel.setTo(city.id());
			context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.tunnel_through", origin.name(), city.name(),
					tunnel.end().getX(), tunnel.end().getZ());
		}
		if (faction.era() < 1) {
			return;
		}
		for (Settlement city : List.copyOf(faction.settlements())) {
			boolean digging = faction.tunnels().stream().anyMatch(t -> t.from() == city.id() && t.to() < 0);
			if (!Kaments.isDeep(city) || city.tier() < 1 || digging || faction.settlements().size() >= 16
					|| !faction.tryEvent("tunnel_" + city.id(), context.day(), 25)) {
				continue;
			}
			for (int attempt = 0; attempt < 12; attempt++) {
				double angle = random.nextDouble() * Math.PI * 2;
				double distance = 220 + random.nextInt(100);
				int x = city.pos().getX() + (int) (Math.cos(angle) * distance);
				int z = city.pos().getZ() + (int) (Math.sin(angle) * distance);
				int y = MathHelper.clamp(city.pos().getY() + random.nextInt(25) - 12, world.getBottomY() + 12, -8);
				BlockPos end = new BlockPos(x, y, z);
				boolean clear = java.util.stream.StreamSupport.stream(context.state().factions().spliterator(), false).flatMap(f -> f.settlements().stream())
						.allMatch(s -> s.pos().withY(0).getSquaredDistance(end.withY(0)) > 200 * 200)
						&& faction.tunnels().stream().allMatch(t -> t.end().getSquaredDistance(end) > 200 * 200);
				if (clear) {
					Tunnel tunnel = new Tunnel((int) faction.counter("next_tunnel"), city.id(), city.pos().up(), end, random.nextLong());
					faction.addCounter("next_tunnel", 1);
					faction.tunnels().add(tunnel);
					context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.tunnel_begun", city.name(), (int) tunnel.length());
					break;
				}
			}
		}
	}

	/** In their age of renewal the Kaments wake new peaks: a few of a big hold go out to hew another. */
	private void newHolds(SimulationContext context, Faction faction) {
		if (faction.era() < 2 || faction.settlements().size() >= 16) {
			return;
		}
		Settlement parent = faction.settlements().stream()
				.filter(s -> Kaments.kind(s) == Kaments.MOUNTAIN && s.population() >= 12)
				.findFirst().orElse(null);
		if (parent == null || !faction.tryEvent("new_hold", context.day(), 40)) {
			return;
		}
		ServerWorld world = context.server().getOverworld();
		List<BlockPos> sites = awakeningSites(context, world, parent.pos(), 1, 300, 700);
		if (sites.isEmpty()) {
			return;
		}
		parent.setPopulation(parent.population() - MIGRANTS);
		Settlement hold = found(context, faction, sites.get(0), Kaments.MOUNTAIN, MIGRANTS, parent.id());
		if (parent.counter("sculptors") > 1) {
			parent.addCounter("sculptors", -1);
			hold.setCounter("sculptors", 1);
		}
		context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.new_hold", parent.name(), hold.name(),
				hold.pos().getX(), hold.pos().getZ());
	}

	private void era(SimulationContext context, Faction faction) {
		int era = faction.era();
		if (era < ERA_FLINT.length - 1 && faction.counter("flint_made") >= ERA_FLINT[era + 1]
				&& faction.resource(KNOWLEDGE) >= ERA_MEMORY[era + 1]) {
			faction.setEra(era + 1);
			context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.era_" + (era + 1));
		}
	}

	private static Settlement found(SimulationContext context, Faction faction, BlockPos pos, int kind, int population, int parentId) {
		Settlement settlement = new Settlement(faction.nextSettlementId(), KamentNames.generate(context.random()), pos,
				World.OVERWORLD, context.day());
		settlement.setPopulation(population);
		settlement.setParentId(parentId);
		settlement.setCounter("kind", kind);
		faction.settlements().add(settlement);
		newAncient(context, faction, settlement);
		return settlement;
	}
}
