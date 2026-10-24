package com.worldbetween.faction.neris;

import com.worldbetween.faction.BuildingType;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionBehavior;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.sim.LocationFinder;
import com.worldbetween.sim.SimulationContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

/**
 * Kingdom of Neris: escaped slaves of the Hellsmiths. A disciplined people whose strength is
 * systematic industry: mines feed smelters, smelters feed forges, forges arm the army.
 */
public class NerisBehavior implements FactionBehavior {
	/** Staffing order: food first, then raw materials, then the industrial chain. */
	private static final List<BuildingType> LABOR_PRIORITY = List.of(
			BuildingType.FARM, BuildingType.LUMBER_CAMP, BuildingType.QUARRY, BuildingType.MINE,
			BuildingType.SMELTER, BuildingType.FORGE, BuildingType.BARRACKS);

	private static final int[] TIER_POPULATION = {0, 15, 30, 60};
	private static final long[] ERA_METAL = {0, 40, 200, 600};
	/** The fifth era, the Infernal Campaign, will require holding ground in the Nether. */
	private static final int MAX_OVERWORLD_ERA = ERA_METAL.length - 1;

	private static final int ARRIVAL_POPULATION = 8;
	private static final int EXPANSION_SETTLERS = 6;
	private static final int EXPANSION_COOLDOWN = 8;
	private static final int MAX_SETTLEMENTS = 24;

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
		Settlement camp = found(context, faction, site, World.OVERWORLD, ARRIVAL_POPULATION);
		faction.addResource(ResourceType.FOOD, 60);
		faction.addResource(ResourceType.WOOD, 40);
		faction.addResource(ResourceType.STONE, 20);
		faction.setRelation(FactionType.KAMENT, -20);
		faction.setArrived(true);
		context.chronicle(FactionType.NERIS, "chronicle.worldbetween.neris.arrival",
				camp.name(), site.getX(), site.getZ());
		return true;
	}

	@Override
	public void simulateDay(SimulationContext context, Faction faction) {
		for (Settlement settlement : faction.settlements()) {
			work(context, faction, settlement);
		}
		feed(context, faction);
		grow(context, faction);
		updateEra(context, faction);
		expand(context, faction);
	}

	private void work(SimulationContext context, Faction faction, Settlement settlement) {
		int available = settlement.population();
		EnumMap<BuildingType, Integer> staffed = new EnumMap<>(BuildingType.class);
		for (BuildingType type : LABOR_PRIORITY) {
			int count = Math.min(settlement.buildingCount(type), available / type.workers());
			staffed.put(type, count);
			available -= count * type.workers();
		}

		// Everyone scavenges a little, which is what keeps a fresh camp alive.
		faction.addResource(ResourceType.FOOD, 3 + staffed.get(BuildingType.FARM) * 8);
		faction.addResource(ResourceType.WOOD, 2 + staffed.get(BuildingType.LUMBER_CAMP) * 6);
		faction.addResource(ResourceType.STONE, 2 + staffed.get(BuildingType.QUARRY) * 6);
		faction.addResource(ResourceType.ORE, staffed.get(BuildingType.MINE) * 3);

		long ore = faction.takeResource(ResourceType.ORE, staffed.get(BuildingType.SMELTER) * 4L);
		long metal = ore / 2;
		faction.addResource(ResourceType.ORE, ore % 2);
		faction.addResource(ResourceType.METAL, metal);
		faction.addMetalProduced(metal);

		long forged = faction.takeResource(ResourceType.METAL, staffed.get(BuildingType.FORGE) * 3L);
		faction.addResource(ResourceType.METAL, forged % 3);
		faction.addResource(ResourceType.EQUIPMENT, forged / 3);

		for (int i = 0; i < staffed.get(BuildingType.BARRACKS); i++) {
			if (settlement.population() <= ARRIVAL_POPULATION || faction.takeResource(ResourceType.EQUIPMENT, 1) == 0) {
				break;
			}
			settlement.setPopulation(settlement.population() - 1);
			faction.setSoldiers(faction.soldiers() + 1);
		}

		build(context, faction, settlement, available);
	}

	private void build(SimulationContext context, Faction faction, Settlement settlement, int idleWorkers) {
		if (settlement.project() == null) {
			BuildingType next = chooseProject(faction, settlement);
			if (next == null || !faction.canAfford(next)) {
				return;
			}
			faction.pay(next);
			settlement.startProject(next);
		}
		BuildingType project = settlement.project();
		if (settlement.advanceProject(1 + idleWorkers / 4)
				&& faction.tryEvent("first_" + project.id(), context.day(), Long.MAX_VALUE)
				&& project != BuildingType.HOUSE) {
			context.chronicle(FactionType.NERIS, "chronicle.worldbetween.neris.first_" + project.id(), settlement.name());
		}
	}

	/** The Neris plan like engineers: fix the tightest bottleneck first. */
	private BuildingType chooseProject(Faction faction, Settlement settlement) {
		if (settlement.totalBuildings() >= 12 + settlement.tier() * 10) {
			return null;
		}
		int pop = settlement.population();
		int farms = settlement.buildingCount(BuildingType.FARM);
		int mines = settlement.buildingCount(BuildingType.MINE);
		int smelters = settlement.buildingCount(BuildingType.SMELTER);
		int forges = settlement.buildingCount(BuildingType.FORGE);
		int soldierShare = faction.soldiers() / Math.max(1, faction.settlements().size());

		if (3 + farms * 8 < pop * 1.3 + soldierShare * 2) {
			return BuildingType.FARM;
		}
		if (pop >= housing(settlement) - 2) {
			return BuildingType.HOUSE;
		}
		if (settlement.buildingCount(BuildingType.LUMBER_CAMP) < 1 + pop / 15) {
			return BuildingType.LUMBER_CAMP;
		}
		if (settlement.buildingCount(BuildingType.QUARRY) < 1 + pop / 20) {
			return BuildingType.QUARRY;
		}
		if (settlement.tier() >= 1 && mines < 1 + pop / 25) {
			return BuildingType.MINE;
		}
		if (mines > 0 && smelters * 4 < mines * 3) {
			return BuildingType.SMELTER;
		}
		if (smelters > 0 && forges * 3 < smelters * 2) {
			return BuildingType.FORGE;
		}
		if (settlement.tier() >= 2 && forges > 0 && settlement.buildingCount(BuildingType.BARRACKS) < 1 + settlement.tier() / 2) {
			return BuildingType.BARRACKS;
		}
		return BuildingType.HOUSE;
	}

	private void feed(SimulationContext context, Faction faction) {
		long need = faction.totalPopulation() + faction.soldiers() / 2;
		long deficit = need - faction.takeResource(ResourceType.FOOD, need);
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

	private void grow(SimulationContext context, Faction faction) {
		for (Settlement settlement : faction.settlements()) {
			int pop = settlement.population();
			if (pop >= housing(settlement) || faction.resource(ResourceType.FOOD) < faction.totalPopulation()) {
				continue;
			}
			int births = Math.max(1, pop / 25);
			if (context.random().nextFloat() < 0.6f) {
				births = Math.min(births, housing(settlement) - pop);
				settlement.setPopulation(pop + births);
				faction.takeResource(ResourceType.FOOD, births * 2L);
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
		if (era < MAX_OVERWORLD_ERA && faction.metalProduced() >= ERA_METAL[era + 1]) {
			faction.setEra(era + 1);
			context.chronicle(FactionType.NERIS, "chronicle.worldbetween.neris.era_" + (era + 1));
		}
	}

	private void expand(SimulationContext context, Faction faction) {
		if (faction.settlements().size() >= MAX_SETTLEMENTS
				|| context.day() - faction.lastExpansionDay() < EXPANSION_COOLDOWN
				|| faction.resource(ResourceType.WOOD) < 60
				|| faction.resource(ResourceType.STONE) < 60
				|| faction.resource(ResourceType.FOOD) < 60) {
			return;
		}
		List<Settlement> parents = faction.settlements().stream()
				.filter(s -> s.tier() >= 2 && s.population() > EXPANSION_SETTLERS * 2 && s.dimension() == World.OVERWORLD)
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
		faction.takeResource(ResourceType.WOOD, 60);
		faction.takeResource(ResourceType.STONE, 60);
		faction.takeResource(ResourceType.FOOD, 30);
		parent.setPopulation(parent.population() - EXPANSION_SETTLERS);
		Settlement settlement = found(context, faction, site, World.OVERWORLD, EXPANSION_SETTLERS);
		faction.setLastExpansionDay(context.day());
		context.chronicle(FactionType.NERIS, "chronicle.worldbetween.neris.expansion",
				parent.name(), settlement.name(), site.getX(), site.getZ());
	}

	private Settlement found(SimulationContext context, Faction faction, BlockPos pos,
			RegistryKey<World> dimension, int population) {
		Settlement settlement = new Settlement(faction.nextSettlementId(), NerisNames.generate(context.random()),
				pos, dimension, context.day());
		settlement.setPopulation(population);
		faction.settlements().add(settlement);
		return settlement;
	}

	private static int housing(Settlement settlement) {
		return 6 + settlement.buildingCount(BuildingType.HOUSE) * 5;
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
