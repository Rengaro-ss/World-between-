package com.worldbetween.faction.journeys;

import com.worldbetween.faction.BuildingType;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.neris.NerisBehavior;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.town.TownFeatures;
import com.worldbetween.town.TownLayout;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends the kingdom's people out on the roads: patrols between towns, caravans between markets, settlers
 * to the places the seekers found. Whoever is on the road is not at home.
 */
public final class Journeys {
	private static final int PATROL_INTERVAL = 3;
	private static final int CARAVAN_INTERVAL = 4;
	private static final int CARAVAN_PROFIT = 4;

	private Journeys() {
	}

	public static void simulateDay(SimulationContext context, Faction faction) {
		double now = context.day();
		for (Journey journey : List.copyOf(faction.journeys())) {
			if (settlement(faction, journey.from()) == null) {
				faction.journeys().remove(journey);
			} else if (journey.hasArrived(now)) {
				arrive(context.server(), faction, journey, now);
			}
		}
		ServerWorld world = context.server().getOverworld();
		Random random = context.random();
		for (Settlement settlement : List.copyOf(faction.settlements())) {
			if (settlement.dimension() != World.OVERWORLD) {
				continue;
			}
			List<Settlement> neighbours = neighbours(faction, settlement);
			if (neighbours.isEmpty()) {
				continue;
			}
			if (settlement.tier() >= 1 && settlement.buildingCount(BuildingType.WATCHTOWER) >= 2
					&& away(faction, settlement.id(), Journey.Kind.PATROL) == 0
					&& faction.tryEvent("patrol_" + settlement.id(), context.day(), PATROL_INTERVAL)) {
				send(world, faction, Journey.Kind.PATROL, settlement, neighbours.get(random.nextInt(neighbours.size())), now);
			}
			if (settlement.buildingCount(BuildingType.MARKET) > 0
					&& away(faction, settlement.id(), Journey.Kind.CARAVAN) == 0
					&& faction.tryEvent("caravan_" + settlement.id(), context.day(), CARAVAN_INTERVAL)) {
				List<Settlement> markets = neighbours.stream().filter(s -> s.buildingCount(BuildingType.MARKET) > 0).toList();
				List<Settlement> choice = markets.isEmpty() ? neighbours : markets;
				send(world, faction, Journey.Kind.CARAVAN, settlement, choice.get(random.nextInt(choice.size())), now);
			}
		}
	}

	private static void send(ServerWorld world, Faction faction, Journey.Kind kind, Settlement from, Settlement to, double now) {
		List<BlockPos> route = TownFeatures.roadRoute(world, from, to);
		if (route != null && route.size() >= 2) {
			faction.journeys().add(new Journey(faction.nextJourneyId(), kind, from.id(), to.id(), null, route, now));
		}
	}

	/** Settlers set out from their town for a site a seeker found; there is no road yet, they go across country. */
	public static Journey sendSettlers(ServerWorld world, Faction faction, Settlement from, BlockPos site, String townName, double now) {
		List<BlockPos> route = new ArrayList<>(TownFeatures.throughStreet(TownLayout.of(world, from), from.tier(), site));
		route.add(site);
		Journey journey = new Journey(faction.nextJourneyId(), Journey.Kind.SETTLERS, from.id(), -1, townName, route, now);
		faction.journeys().add(journey);
		return journey;
	}

	/** They have got where they were going: settlers found their town, the others turn for home or are home. */
	public static void arrive(MinecraftServer server, Faction faction, Journey journey, double now) {
		if (journey.kind() == Journey.Kind.SETTLERS) {
			faction.journeys().remove(journey);
			NerisBehavior.settle(new SimulationContext(server, WorldBetweenState.get(server)), faction, journey);
			return;
		}
		if (journey.isReturning()) {
			faction.journeys().remove(journey);
			return;
		}
		if (journey.kind() == Journey.Kind.CARAVAN) {
			// Goods sold abroad: both towns' treasuries take their share.
			Settlement from = settlement(faction, journey.from());
			Settlement to = settlement(faction, journey.to());
			if (from != null) {
				from.addCoins(CARAVAN_PROFIT);
			}
			if (to != null) {
				to.addCoins(CARAVAN_PROFIT);
			}
		}
		journey.turnBack(now);
		WorldBetweenState.get(server).markDirty();
	}

	/** How many of a town's people of a kind are out on the road. */
	public static int away(Faction faction, int settlement, Journey.Kind kind) {
		return faction.journeys().stream().filter(j -> j.kind() == kind && j.from() == settlement).mapToInt(j -> kind.people()).sum();
	}

	/** Whether settlers are already on their way to somewhere near {@code pos}. */
	public static boolean settlersBound(Faction faction, BlockPos pos, int distance) {
		return faction.journeys().stream().anyMatch(j -> j.kind() == Journey.Kind.SETTLERS
				&& j.destination().withY(0).getSquaredDistance(pos.withY(0)) < (double) distance * distance);
	}

	public static List<Settlement> neighbours(Faction faction, Settlement settlement) {
		return faction.settlements().stream()
				.filter(s -> s.dimension() == World.OVERWORLD && (s.parentId() == settlement.id() || settlement.parentId() == s.id()))
				.toList();
	}

	public static Settlement settlement(Faction faction, int id) {
		return faction.settlements().stream().filter(s -> s.id() == id).findFirst().orElse(null);
	}
}
