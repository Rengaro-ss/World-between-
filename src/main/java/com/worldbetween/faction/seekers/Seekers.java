package com.worldbetween.faction.seekers;

import com.worldbetween.faction.BuildingType;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.court.Ambition;
import com.worldbetween.faction.court.Noble;
import com.worldbetween.faction.neris.NerisNames;
import com.worldbetween.sim.LocationFinder;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.town.Ground;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.List;
import java.util.UUID;

/**
 * The Seekers' work: finding places for towns, rich ground for mines, and where strangers live.
 * The kingdom only grows where a seeker has been.
 */
public final class Seekers {
	/** Days of the world between a seeker's visits to the same stranger. */
	private static final int SCOUT_INTERVAL = 4;
	private static final int MIN_SITE_SPACING = 160;

	private Seekers() {
	}

	/** Two seekers came out of the portal with the founders. */
	public static void foundParty(SimulationContext context, Faction faction, Settlement camp) {
		for (int i = 0; i < 2; i++) {
			faction.seekers().add(new Seeker(faction.nextSeekerId(), NerisNames.person(context.random()), camp.id(), camp.pos()));
		}
	}

	public static void simulateDay(SimulationContext context, Faction faction) {
		recruit(context, faction);
		double now = context.day();
		for (Seeker seeker : faction.seekers()) {
			if (!seeker.isAlive()) {
				continue;
			}
			if (!seeker.isIdle() && seeker.hasArrived(now)) {
				arrive(context, faction, seeker, now);
			}
			if (seeker.isIdle()) {
				assign(context, faction, seeker, now);
			}
		}
	}

	/** Barracks train new seekers, as many as the kingdom's size and its king's ambitions call for. */
	private static void recruit(SimulationContext context, Faction faction) {
		long alive = faction.seekers().stream().filter(Seeker::isAlive).count();
		Noble king = faction.king();
		int wanted = 2 + faction.settlements().size() / 3
				+ (king != null && (king.ambition() == Ambition.EXPANSION || king.ambition() == Ambition.WAR) ? 1 : 0);
		if (alive >= wanted || faction.resource(ResourceType.EQUIPMENT) < 2 || faction.resource(ResourceType.COINS) < 30) {
			return;
		}
		Settlement barracks = faction.settlements().stream().filter(s -> s.buildingCount(BuildingType.BARRACKS) > 0).findFirst().orElse(null);
		if (barracks == null || !faction.tryEvent("seeker_recruit", context.day(), 10)) {
			return;
		}
		faction.takeResource(ResourceType.EQUIPMENT, 2);
		faction.takeResource(ResourceType.COINS, 30);
		Seeker seeker = new Seeker(faction.nextSeekerId(), NerisNames.person(context.random()), barracks.id(), barracks.pos());
		faction.seekers().add(seeker);
		context.chronicle(faction.type(), "chronicle.worldbetween.seeker.recruited", seeker.name(), barracks.name());
	}

	private static void assign(SimulationContext context, Faction faction, Seeker seeker, double now) {
		Random random = context.random();
		Settlement home = home(faction, seeker);
		if (home == null) {
			return;
		}
		BlockPos at = seeker.position(now);
		MinecraftServer server = context.server();

		// Strangers first: the kingdom wants to know who walks its lands.
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (player.getServerWorld() == server.getOverworld() && !player.isSpectator()
					&& faction.seekers().stream().noneMatch(s -> s.isAlive() && s.mission() == Seeker.Mission.SCOUT && player.getUuid().equals(s.forPlayer()))
					&& faction.tryEvent("scout_" + player.getUuid(), context.day(), com.worldbetween.sim.Clock.simDays(SCOUT_INTERVAL))) {
				seeker.send(Seeker.Mission.SCOUT, at, player.getBlockPos(), now, -1, player.getUuid());
				return;
			}
		}
		for (Settlement settlement : faction.settlements()) {
			boolean taken = faction.seekers().stream().anyMatch(s -> s.isAlive() && s.mission() == Seeker.Mission.MINE && s.forSettlement() == settlement.id());
			if (settlement.tier() >= 1 && settlement.mine() == null && settlement.mineSite() == null && !taken) {
				seeker.send(Seeker.Mission.MINE, at, around(settlement.pos(), random, 105), now, settlement.id(), null);
				return;
			}
		}
		if (faction.knownSites().size() < 3) {
			List<Settlement> towns = faction.settlements();
			Settlement from = towns.get(random.nextInt(towns.size()));
			seeker.send(Seeker.Mission.SITE, at, around(from.pos(), random, 260 + random.nextInt(140)), now, -1, null);
		}
	}

	private static void arrive(SimulationContext context, Faction faction, Seeker seeker, double now) {
		ServerWorld world = context.server().getOverworld();
		Settlement home = home(faction, seeker);
		switch (seeker.mission()) {
			case SITE -> {
				BlockPos site = LocationFinder.findSite(context, world, FactionType.NERIS, seeker.target(), 0, 60);
				if (site != null && faction.knownSites().stream().allMatch(k -> k.getSquaredDistance(site) > MIN_SITE_SPACING * MIN_SITE_SPACING)) {
					faction.knownSites().add(site);
					faction.addMarker(Marker.Type.CAIRN, site.add(0, 0, 0));
					context.chronicle(faction.type(), "chronicle.worldbetween.seeker.site", seeker.name(), site.getX(), site.getZ());
				}
			}
			case MINE -> {
				Settlement settlement = faction.settlements().stream().filter(s -> s.id() == seeker.forSettlement()).findFirst().orElse(null);
				if (settlement != null && settlement.mineSite() == null) {
					BlockPos best = bestMineSite(world, context.random(), settlement);
					settlement.setMineSite(best);
					faction.addMarker(Marker.Type.STAKE, best.add(3, 0, 3));
					context.chronicle(faction.type(), "chronicle.worldbetween.seeker.mine", seeker.name(), settlement.name());
				}
			}
			case SCOUT -> scouted(context.server(), faction, seeker, seeker.target());
			case RETURN -> {
				seeker.finish();
				return;
			}
		}
		if (home != null) {
			seeker.send(Seeker.Mission.RETURN, seeker.target(), home.pos(), now, -1, null);
		} else {
			seeker.finish();
		}
	}

	/** The seeker has seen where a stranger lives and takes the news home. */
	public static void scouted(MinecraftServer server, Faction faction, Seeker seeker, BlockPos where) {
		UUID player = seeker.forPlayer();
		if (player == null) {
			return;
		}
		faction.strangers().put(player, where);
		ServerPlayerEntity online = server.getPlayerManager().getPlayer(player);
		String name = online != null ? online.getName().getString() : "?";
		new SimulationContext(server, WorldBetweenState.get(server)).chronicle(faction.type(),
				"chronicle.worldbetween.seeker.scout", seeker.name(), name, where.getX(), where.getZ());
	}

	/** Richer ground lies under rougher land: the seeker walks three candidates and picks the hilliest. */
	private static BlockPos bestMineSite(ServerWorld world, Random random, Settlement settlement) {
		BlockPos best = null;
		int bestScore = Integer.MIN_VALUE;
		for (int i = 0; i < 3; i++) {
			BlockPos candidate = around(settlement.pos(), random, 95 + random.nextInt(21));
			int min = Integer.MAX_VALUE;
			int max = Integer.MIN_VALUE;
			for (int[] o : new int[][]{{0, 0}, {-12, 0}, {12, 0}, {0, -12}, {0, 12}}) {
				int h = Ground.floor(world, candidate.getX() + o[0], candidate.getZ() + o[1]);
				min = Math.min(min, h);
				max = Math.max(max, h);
			}
			int score = max - min + random.nextInt(4) - (Ground.isWater(world, candidate.getX(), candidate.getZ()) ? 100 : 0);
			if (score > bestScore) {
				bestScore = score;
				best = new BlockPos(candidate.getX(), Ground.floor(world, candidate.getX(), candidate.getZ()), candidate.getZ());
			}
		}
		return best;
	}

	private static BlockPos around(BlockPos center, Random random, int distance) {
		double angle = random.nextDouble() * Math.PI * 2;
		return new BlockPos(center.getX() + (int) (Math.cos(angle) * distance), 0, center.getZ() + (int) (Math.sin(angle) * distance));
	}

	public static Settlement home(Faction faction, Seeker seeker) {
		return faction.settlements().stream().filter(s -> s.id() == seeker.home()).findFirst()
				.orElse(faction.settlements().isEmpty() ? null : faction.settlements().get(0));
	}

	/** A seeker fell. Killing one of the kingdom's elite is a crime. */
	public static void killed(MinecraftServer server, Faction faction, Seeker seeker, BlockPos where, ServerPlayerEntity killer) {
		seeker.die();
		WorldBetweenState state = WorldBetweenState.get(server);
		String name = killer != null ? killer.getName().getString() : "?";
		new SimulationContext(server, state).chronicle(faction.type(),
				killer != null ? "chronicle.worldbetween.seeker.killed_by" : "chronicle.worldbetween.seeker.killed",
				seeker.name(), where.getX(), where.getZ(), name);
		if (killer != null) {
			faction.changeReputation(killer.getUuid(), -30);
			faction.declareCriminal(killer.getUuid(), state.day());
		}
		state.markDirty();
	}
}
