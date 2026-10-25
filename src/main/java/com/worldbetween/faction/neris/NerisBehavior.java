package com.worldbetween.faction.neris;

import com.worldbetween.faction.BuildingType;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionBehavior;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Mine;
import com.worldbetween.faction.Plot;
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.mine.MineManager;
import com.worldbetween.mine.MinePlan;
import com.worldbetween.sim.LocationFinder;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.town.TownLayout;
import com.worldbetween.town.TownPlanner;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

import static com.worldbetween.faction.BuildingType.*;
import static com.worldbetween.faction.ResourceType.*;

/**
 * Kingdom of Neris: escaped slaves of the Hellsmiths. A disciplined people whose strength is
 * systematic industry. Every building has a place in a production chain:
 *
 * <pre>
 * farm → mill → bakery → food          pasture → meat → kitchen → food
 * lumber camp → logs → sawmill → wood  logs → charcoal kiln → fuel
 * quarry → stone → mason → bricks      pasture → hides → tannery → leather
 * mine → ore + fuel → smelter → metal  pasture → wool → weaver → cloth
 * metal + leather → forge → equipment  equipment + cloth + a recruit → barracks → soldier
 * library → knowledge → new eras
 * </pre>
 *
 * Civic buildings shape the whole: warehouses set how much can be stored, markets trade surplus for
 * shortages, watchtowers keep thieves and wolves off the stores, and the town hall makes everyone
 * work a little better as the settlement grows.
 */
public class NerisBehavior implements FactionBehavior {
	/** Staffing order: food first, then raw materials, then the chains that build on them. */
	private static final List<BuildingType> LABOR_PRIORITY = List.of(
			FARM, PASTURE, PIER, MILL, BAKERY, KITCHEN, LUMBER_CAMP, QUARRY, SAWMILL, CHARCOAL_KILN,
			MINE, SMELTER, TANNERY, WEAVER, FORGE, MASON, LIBRARY, BARRACKS);

	private static final int[] TIER_POPULATION = {0, 15, 30, 60};
	private static final long[] ERA_METAL = {0, 40, 200, 600};
	private static final long[] ERA_KNOWLEDGE = {0, 0, 40, 150};
	/** The fifth era, the Infernal Campaign, will require holding ground in the Nether. */
	private static final int MAX_OVERWORLD_ERA = ERA_METAL.length - 1;

	private static final int ARRIVAL_POPULATION = 8;
	private static final int EXPANSION_SETTLERS = 6;
	private static final int EXPANSION_COOLDOWN = 20;
	private static final int MAX_SETTLEMENTS = 24;
	private static final int BASE_STORAGE = 300;
	private static final int STORAGE_PER_WAREHOUSE = 400;

	@Override
	public int arrivalDay(SimulationContext context) {
		return context.config().nerisArrivalDay;
	}

	@Override
	public boolean arrive(SimulationContext context, Faction faction) {
		ServerWorld overworld = context.server().getOverworld();
		BlockPos site = LocationFinder.findSite(context, overworld, FactionType.NERIS, overworld.getSpawnPos(),
				context.config().minArrivalDistance, context.config().maxArrivalDistance);
		if (site == null) {
			return false;
		}
		Settlement camp = found(context, faction, site, World.OVERWORLD, ARRIVAL_POPULATION, -1);
		faction.addResource(FOOD, 60);
		faction.addResource(WOOD, 40);
		faction.addResource(STONE, 20);
		faction.setRelation(FactionType.KAMENT, -20);
		faction.setArrived(true);
		context.chronicle(FactionType.NERIS, "chronicle.worldbetween.neris.arrival",
				camp.name(), site.getX(), site.getZ());
		return true;
	}

	@Override
	public void simulateDay(SimulationContext context, Faction faction) {
		ServerWorld world = context.server().getOverworld();
		for (Settlement settlement : faction.settlements()) {
			TownPlanner.sync(world, settlement);
			work(context, faction, settlement);
		}
		trade(faction);
		feed(context, faction);
		guard(context, faction);
		store(faction);
		grow(context, faction);
		updateEra(context, faction);
		expand(context, faction);
	}

	private void work(SimulationContext context, Faction faction, Settlement settlement) {
		int available = settlement.population();
		EnumMap<BuildingType, Integer> staffed = new EnumMap<>(BuildingType.class);
		for (BuildingType type : BuildingType.values()) {
			if (type.workers() == 0) {
				staffed.put(type, settlement.buildingCount(type));
			}
		}
		for (BuildingType type : LABOR_PRIORITY) {
			int count = Math.min(settlement.buildingCount(type), available / type.workers());
			staffed.put(type, count);
			available -= count * type.workers();
		}
		settlement.staffed().clear();
		settlement.staffed().putAll(staffed);
		// The town hall organises work better as the settlement grows.
		float efficiency = 1f + 0.05f * settlement.tier();
		Production p = new Production(faction, staffed, efficiency);

		// Everyone forages a little and works raw materials by hand, which keeps a fresh camp alive.
		faction.addResource(FOOD, 2);
		faction.addResource(LOGS, 2);
		faction.addResource(STONE, 2);
		p.convert(LOGS, 4, WOOD, 4, 1);

		p.yield(FARM, GRAIN, 8);
		p.yield(PASTURE, MEAT, 3);
		p.yield(PASTURE, WOOL, 2);
		p.yield(PASTURE, HIDES, 1);
		p.yield(PIER, FOOD, 5);
		// A mill only grinds what the bakeries can bake; flour is no use to anyone on its own.
		p.convert(GRAIN, 12, FLOUR, 12, Math.min(staffed.get(MILL), staffed.get(BAKERY)));
		p.convert(BAKERY, FLOUR, 12, FOOD, 20);
		p.convert(KITCHEN, MEAT, 4, FOOD, 8);
		p.convert(KITCHEN, GRAIN, 4, FOOD, 6);
		p.yield(LUMBER_CAMP, LOGS, 6);
		p.yield(QUARRY, STONE, 6);
		p.convert(SAWMILL, LOGS, 6, WOOD, 9);
		p.convert(CHARCOAL_KILN, LOGS, 4, FUEL, 4);
		p.convert(MASON, STONE, 4, BRICKS, 3);
		p.yield(MINE, ORE, 3);
		p.yield(MINE, FUEL, 1);
		long smelted = p.smelt();
		faction.addMetalProduced(smelted);
		p.convert(TANNERY, HIDES, 2, LEATHER, 2);
		p.convert(WEAVER, WOOL, 3, CLOTH, 2);
		p.forge();
		p.yield(LIBRARY, KNOWLEDGE, 2);

		dig(context, faction, settlement, staffed.get(MINE));

		for (int i = 0; i < staffed.get(BARRACKS); i++) {
			// The army takes no more than one in seven: someone has to work the fields.
			boolean enough = faction.soldiers() * 7 >= faction.totalPopulation();
			if (enough || settlement.population() <= 20 || faction.resource(EQUIPMENT) < 1 || faction.resource(CLOTH) < 1) {
				break;
			}
			faction.takeResource(EQUIPMENT, 1);
			faction.takeResource(CLOTH, 1);
			settlement.setPopulation(settlement.population() - 1);
			faction.setSoldiers(faction.soldiers() + 1);
		}

		build(context, faction, settlement, available);
	}

	/** Applies one day of output for all staffed buildings of a kind. */
	private static final class Production {
		private final Faction faction;
		private final EnumMap<BuildingType, Integer> staffed;
		private final float efficiency;

		Production(Faction faction, EnumMap<BuildingType, Integer> staffed, float efficiency) {
			this.faction = faction;
			this.staffed = staffed;
			this.efficiency = efficiency;
		}

		void yield(BuildingType building, ResourceType resource, int perBuilding) {
			faction.addResource(resource, Math.round(staffed.get(building) * perBuilding * efficiency));
		}

		/** Each building turns up to {@code in} of one resource into {@code out} of another. */
		void convert(BuildingType building, ResourceType from, int in, ResourceType to, int out) {
			convert(from, in, to, out, staffed.get(building));
		}

		void convert(ResourceType from, int in, ResourceType to, int out, int buildings) {
			if (buildings <= 0) {
				return;
			}
			long taken = faction.takeResource(from, (long) in * buildings);
			faction.addResource(to, Math.round(taken * out / (float) in * efficiency));
		}

		/** Smelters need ore and fuel: four ore and two fuel make two metal. */
		long smelt() {
			int smelters = staffed.get(SMELTER);
			long batches = Math.min(smelters * 2L, Math.min(faction.resource(ORE) / 2, faction.resource(FUEL)));
			faction.takeResource(ORE, batches * 2);
			faction.takeResource(FUEL, batches);
			long metal = Math.round(batches * efficiency);
			faction.addResource(METAL, metal);
			return metal;
		}

		/** Forges need metal and leather: three metal and one leather make one set of equipment. */
		void forge() {
			long sets = Math.min(staffed.get(FORGE), Math.min(faction.resource(METAL) / 3, faction.resource(LEATHER)));
			faction.takeResource(METAL, sets * 3);
			faction.takeResource(LEATHER, sets);
			faction.addResource(EQUIPMENT, sets);
		}
	}

	/** Markets trade two of whatever is most plentiful for one of whatever is scarcest. */
	private void trade(Faction faction) {
		List<ResourceType> needs = List.of(FOOD, WOOD, STONE, FUEL, BRICKS, LEATHER, CLOTH);
		List<ResourceType> goods = List.of(FOOD, GRAIN, MEAT, LOGS, WOOD, STONE, BRICKS, ORE, FUEL, WOOL, HIDES);
		for (Settlement settlement : faction.settlements()) {
			if (settlement.buildingCount(MARKET) == 0) {
				continue;
			}
			ResourceType need = needs.stream().min(Comparator.comparingLong(faction::resource)).orElseThrow();
			ResourceType surplus = goods.stream().filter(r -> r != need).max(Comparator.comparingLong(faction::resource)).orElseThrow();
			if (faction.resource(surplus) < 100 || faction.resource(surplus) < faction.resource(need) * 3) {
				continue;
			}
			long volume = 8L * (1 + settlement.tier()) * settlement.buildingCount(MARKET);
			long paid = faction.takeResource(surplus, volume * 2);
			faction.addResource(need, paid / 2);
		}
	}

	/** People eat food first, then raw meat and grain as they are. */
	private void feed(SimulationContext context, Faction faction) {
		long need = faction.totalPopulation() + faction.soldiers() / 2;
		long deficit = need - faction.takeResource(FOOD, need);
		deficit -= faction.takeResource(MEAT, deficit);
		deficit -= faction.takeResource(GRAIN, deficit);
		if (deficit <= 0) {
			return;
		}
		int losses = (int) Math.ceil(deficit / 4.0);
		Settlement largest = faction.settlements().stream().max(Comparator.comparingInt(Settlement::population)).orElseThrow();
		int fromSettlement = Math.min(losses, Math.max(0, largest.population() - 1));
		largest.setPopulation(largest.population() - fromSettlement);
		faction.setSoldiers(faction.soldiers() - (losses - fromSettlement));
		if (faction.tryEvent("famine", context.day(), 10)) {
			context.chronicle(FactionType.NERIS, "chronicle.worldbetween.neris.famine", largest.name());
		}
	}

	/** Without watchtowers, thieves and wolves take a share of the stores. */
	private void guard(SimulationContext context, Faction faction) {
		for (Settlement settlement : faction.settlements()) {
			float chance = Math.max(0f, 0.15f - 0.05f * settlement.buildingCount(WATCHTOWER));
			if (context.random().nextFloat() >= chance) {
				continue;
			}
			float share = settlement.population() / (float) Math.max(1, faction.totalPopulation());
			for (ResourceType resource : List.of(FOOD, GRAIN, MEAT, WOOD, METAL)) {
				faction.takeResource(resource, Math.round(faction.resource(resource) * 0.1f * share));
			}
			if (faction.tryEvent("raid_" + settlement.id(), context.day(), 20)) {
				context.chronicle(FactionType.NERIS, "chronicle.worldbetween.neris.raid", settlement.name());
			}
		}
	}

	/** Whatever does not fit into the warehouses spoils. */
	private void store(Faction faction) {
		long capacity = BASE_STORAGE + (long) STORAGE_PER_WAREHOUSE * faction.totalBuildings(WAREHOUSE);
		for (ResourceType resource : ResourceType.values()) {
			if (resource != KNOWLEDGE && faction.resource(resource) > capacity) {
				faction.takeResource(resource, faction.resource(resource) - capacity);
			}
		}
	}

	/**
	 * Real tunnels grow with the abstract mines. Each era opens a deeper level:
	 * copper and iron in Exile, gold from the Bronze Age, diamonds and lava forges from the Steel Age.
	 */
	private void dig(SimulationContext context, Faction faction, Settlement settlement, int staffedMines) {
		if (settlement.mine() == null) {
			if (settlement.buildingCount(MINE) == 0 || settlement.dimension() != World.OVERWORLD) {
				return;
			}
			settlement.setMine(new Mine(context.random().nextLong(), mineEntrance(context, settlement)));
		}
		Mine mine = settlement.mine();
		MinePlan plan = MineManager.plan(mine);
		int deepest = faction.era() >= 3 ? 2 : faction.era() >= 1 ? 1 : 0;
		int before = mine.target();
		mine.dig(staffedMines * context.config().mineStepsPerDay, plan.levelEnd(deepest));
		for (int level = 1; level < plan.levelCount(); level++) {
			int levelStart = plan.levelEnd(level - 1);
			if (before <= levelStart && mine.target() > levelStart
					&& faction.tryEvent("mine_level_" + level, context.day(), Long.MAX_VALUE)) {
				context.chronicle(FactionType.NERIS, "chronicle.worldbetween.neris.mine_level_" + level, settlement.name());
			}
		}
	}

	/** The shaft is sunk a short walk outside the town, at ground level. */
	private static BlockPos mineEntrance(SimulationContext context, Settlement settlement) {
		ServerWorld world = context.server().getOverworld();
		double angle = context.random().nextDouble() * Math.PI * 2;
		double distance = MathHelper.lerp(context.random().nextDouble(), 95, 115);
		int x = settlement.pos().getX() + (int) (Math.cos(angle) * distance);
		int z = settlement.pos().getZ() + (int) (Math.sin(angle) * distance);
		int y = world.getChunkManager().getChunkGenerator().getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG,
				world, world.getChunkManager().getNoiseConfig()) - 1;
		return new BlockPos(x, y, z);
	}

	private void build(SimulationContext context, Faction faction, Settlement settlement, int idleWorkers) {
		if (settlement.project() == null) {
			BuildingType next = chooseProject(context, faction, settlement);
			if (next == null || !faction.canAfford(next)) {
				return;
			}
			faction.pay(next);
			settlement.startProject(next);
		}
		BuildingType project = settlement.project();
		if (!settlement.advanceProject(1 + idleWorkers / 4)) {
			return;
		}
		ServerWorld world = context.server().getOverworld();
		if (project == HOUSE_EXTENSION) {
			// The extension was counted as a building by advanceProject; turn it into a bigger house instead.
			settlement.buildings().merge(HOUSE_EXTENSION, -1, Integer::sum);
			settlement.buildings().remove(HOUSE_EXTENSION, 0);
			TownPlanner.extendHouse(world, settlement);
			return;
		}
		TownPlanner.assign(world, settlement, project);
		if (project != HOUSE && faction.tryEvent("first_" + project.id(), context.day(), Long.MAX_VALUE)) {
			context.chronicle(FactionType.NERIS, "chronicle.worldbetween.neris.first_" + project.id(), settlement.name());
		}
	}

	/** The Neris plan like engineers: fix the tightest bottleneck first. */
	private BuildingType chooseProject(SimulationContext context, Faction faction, Settlement settlement) {
		int tier = settlement.tier();
		int pop = settlement.population();
		int soldierShare = faction.soldiers() / Math.max(1, faction.settlements().size());
		int farms = count(settlement, FARM);
		int pastures = count(settlement, PASTURE);
		int mills = count(settlement, MILL);
		int bakeries = count(settlement, BAKERY);
		int kitchens = count(settlement, KITCHEN);
		long need = pop + soldierShare * 2L;
		long grain = farms * 8L;
		long baked = Math.min(grain, Math.min(mills, bakeries) * 12L);
		long foodSupply = 2 + 5L * count(settlement, PIER) + baked * 20 / 12 + (grain - baked)
				+ Math.min(pastures * 3L, kitchens * 4L) * 2 + Math.max(0, pastures * 3L - kitchens * 4L);

		// Food first: nobody builds anything on an empty stomach.
		if (foodSupply < need * 1.1) {
			if (pop >= 12 && farms >= 2 && mills == 0) return MILL;
			if (mills > bakeries) return BAKERY;
			if (tier >= 1 && pastures > kitchens * 2) return KITCHEN;
			if (count(settlement, PIER) == 0 && tier >= 1 && TownLayout.of(context.server().getOverworld(), settlement).hasPier()) return PIER;
			if (farms >= 3 && pastures < 1 + farms / 3) return PASTURE;
			if (mills > 0 && mills < farms / 3) return MILL;
			return FARM;
		}
		// Fields and kitchens are never held back; everything else must fit in the town.
		if (settlement.totalBuildings() >= 20 + tier * 14) {
			return pop >= housing(settlement) - 2 && TownPlanner.canExtendHouse(context.server().getOverworld(), settlement) ? HOUSE_EXTENSION : null;
		}
		// Then building materials, without which nothing else can go up.
		if (faction.resource(WOOD) < 40) {
			if (count(settlement, LUMBER_CAMP) == 0) return LUMBER_CAMP;
			if (count(settlement, SAWMILL) == 0) return SAWMILL;
		}
		// Then room for more people, once they can be fed.
		if (pop >= housing(settlement) - 2) {
			boolean sprawl = settlement.buildingCount(HOUSE) >= 4 + tier * 3;
			return sprawl && TownPlanner.canExtendHouse(context.server().getOverworld(), settlement) ? HOUSE_EXTENSION : HOUSE;
		}
		// One of every link in the chain first, then the library, and only then more of the same.
		int mines = count(settlement, MINE);
		int smelters = count(settlement, SMELTER);
		if (count(settlement, LUMBER_CAMP) == 0) return LUMBER_CAMP;
		if (count(settlement, SAWMILL) == 0) return SAWMILL;
		if (count(settlement, QUARRY) == 0) return QUARRY;
		if (count(settlement, WATCHTOWER) < 1 + tier) return WATCHTOWER;
		if (tier >= 1 && count(settlement, WAREHOUSE) < 1 + tier / 2) return WAREHOUSE;
		if (tier >= 1 && count(settlement, MARKET) < 1) return MARKET;
		if (tier >= 1 && count(settlement, MASON) == 0) return MASON;
		if (tier >= 1 && mines == 0) return MINE;
		if (mines > 0 && count(settlement, CHARCOAL_KILN) == 0) return CHARCOAL_KILN;
		if (mines > 0 && smelters == 0) return SMELTER;
		if (tier >= 1 && pastures == 0) return PASTURE;
		if (smelters > 0 && count(settlement, TANNERY) == 0) return TANNERY;
		if (smelters > 0 && count(settlement, FORGE) == 0) return FORGE;
		if (tier >= 2 && count(settlement, LIBRARY) == 0) return LIBRARY;
		if (tier >= 2 && count(settlement, WEAVER) == 0) return WEAVER;
		if (tier >= 2 && count(settlement, FORGE) > 0 && count(settlement, BARRACKS) < 1 + tier / 2) return BARRACKS;
		if (count(settlement, LUMBER_CAMP) < 1 + pop / 40) return LUMBER_CAMP;
		if (count(settlement, SAWMILL) < (count(settlement, LUMBER_CAMP) + 1) / 2) return SAWMILL;
		if (count(settlement, QUARRY) < 1 + pop / 50) return QUARRY;
		if (tier >= 1 && mines < 1 + pop / 40) return MINE;
		if (smelters * 4 < mines * 3) return SMELTER;
		if (count(settlement, CHARCOAL_KILN) < (smelters + 1) / 2) return CHARCOAL_KILN;
		if (smelters > 0 && count(settlement, FORGE) * 3 < smelters * 2) return FORGE;
		if (tier >= 1 && kitchens < 1 + pop / 40) return KITCHEN;
		return null;
	}

	private static int count(Settlement settlement, BuildingType type) {
		return settlement.buildingCount(type);
	}

	private void grow(SimulationContext context, Faction faction) {
		for (Settlement settlement : faction.settlements()) {
			int pop = settlement.population();
			if (pop >= housing(settlement) || faction.resource(FOOD) + faction.resource(MEAT) + faction.resource(GRAIN) < 2L * faction.totalPopulation()) {
				continue;
			}
			int births = Math.max(1, pop / 25);
			if (context.random().nextFloat() < 0.6f) {
				births = Math.min(births, housing(settlement) - pop);
				settlement.setPopulation(pop + births);
				long fed = faction.takeResource(FOOD, births * 2L);
				faction.takeResource(GRAIN, births * 2L - fed);
			}
			int tier = tierFor(settlement.population());
			if (tier > settlement.tier()) {
				settlement.setTier(tier);
				context.chronicle(FactionType.NERIS, "chronicle.worldbetween.neris.tier_" + tier, settlement.name());
			}
		}
	}

	private void updateEra(SimulationContext context, Faction faction) {
		int era = faction.era();
		if (era < MAX_OVERWORLD_ERA && faction.metalProduced() >= ERA_METAL[era + 1]
				&& faction.resource(KNOWLEDGE) >= ERA_KNOWLEDGE[era + 1]) {
			faction.setEra(era + 1);
			context.chronicle(FactionType.NERIS, "chronicle.worldbetween.neris.era_" + (era + 1));
		}
	}

	private void expand(SimulationContext context, Faction faction) {
		if (faction.settlements().size() >= MAX_SETTLEMENTS
				|| context.day() - faction.lastExpansionDay() < EXPANSION_COOLDOWN
				|| faction.resource(WOOD) < 60
				|| faction.resource(STONE) < 60
				|| faction.resource(FOOD) + faction.resource(GRAIN) + faction.resource(MEAT) < 60) {
			return;
		}
		List<Settlement> parents = faction.settlements().stream()
				.filter(s -> s.tier() >= 2 && s.population() > (s.isCapital() ? 90 : 40) && s.dimension() == World.OVERWORLD)
				.toList();
		if (parents.isEmpty()) {
			return;
		}
		Settlement parent = parents.get(context.random().nextInt(parents.size()));
		ServerWorld overworld = context.server().getOverworld();
		BlockPos site = LocationFinder.findSite(context, overworld, FactionType.NERIS, parent.pos(),
				context.config().minExpansionDistance, context.config().maxExpansionDistance);
		// Try again in a few days rather than every day, searching is not free.
		faction.setLastExpansionDay(context.day() - EXPANSION_COOLDOWN / 2);
		if (site == null) {
			return;
		}
		faction.takeResource(WOOD, 60);
		faction.takeResource(STONE, 60);
		long carried = faction.takeResource(FOOD, 30);
		faction.takeResource(GRAIN, 30 - carried);
		parent.setPopulation(parent.population() - EXPANSION_SETTLERS);
		Settlement settlement = found(context, faction, site, World.OVERWORLD, EXPANSION_SETTLERS, parent.id());
		faction.setLastExpansionDay(context.day());
		context.chronicle(FactionType.NERIS, "chronicle.worldbetween.neris.expansion",
				parent.name(), settlement.name(), site.getX(), site.getZ());
	}

	private Settlement found(SimulationContext context, Faction faction, BlockPos pos, RegistryKey<World> dimension,
			int population, int parentId) {
		Settlement settlement = new Settlement(faction.nextSettlementId(), NerisNames.generate(context.random()),
				pos, dimension, context.day());
		settlement.setPopulation(population);
		settlement.setParentId(parentId);
		faction.settlements().add(settlement);
		return settlement;
	}

	/** Each house holds more people the bigger it has grown. */
	private static int housing(Settlement settlement) {
		int housed = 6;
		int withPlots = 0;
		for (Plot plot : settlement.plots()) {
			if (plot.type() == HOUSE) {
				housed += TownPlanner.houseCapacity(plot.level());
				withPlots++;
			}
		}
		return housed + Math.max(0, settlement.buildingCount(HOUSE) - withPlots) * TownPlanner.houseCapacity(0);
	}

	private static int tierFor(int population) {
		int tier = 0;
		for (int i = 0; i < TIER_POPULATION.length; i++) {
			if (population >= TIER_POPULATION[i]) {
				tier = i;
			}
		}
		return tier;
	}
}
