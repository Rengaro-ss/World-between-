package com.worldbetween.war;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.court.Ambition;
import com.worldbetween.faction.court.Noble;
import com.worldbetween.faction.journeys.Journey;
import com.worldbetween.faction.journeys.Journeys;
import com.worldbetween.faction.kament.Focus;
import com.worldbetween.faction.kament.Kaments;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.town.Ground;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One day of the wars. Each two peoples who share the world are always at war; only its heat changes.
 * Near borders, clashes and a great difference in strength heat it; quiet cools it. By degree they send out
 * border patrols, then raiders to lie in wait near the enemy's towns, then armies on campaign. Armies are
 * slow, and the side marched on does not wait behind its walls: it sends its own army out to meet them.
 * Every fight leaves a battlefield behind.
 */
public final class WarSimulation {
	private static final int PATROL_INTERVAL = 4;
	private static final int RAID_INTERVAL = 6;
	private static final int ARMY_INTERVAL = 14;
	/** How far apart parties may pass and still run into each other. */
	private static final double MEET = 40;
	/** How far from the road a waiting ambush strikes. */
	private static final double AMBUSH = 60;
	/** Raiders lie in wait this far short of the town. */
	private static final int RAID_STANDOFF = 70;
	private static final int RAID_DAYS = 3;
	/** Patrols only go out if the enemy is nearer than this. */
	private static final int BORDER = 900;

	private WarSimulation() {
	}

	public static void simulateDay(SimulationContext context) {
		WorldBetweenState state = context.state();
		WarState wars = state.wars();
		double now = context.day();
		for (War war : wars.wars()) {
			Faction a = state.faction(war.a());
			Faction b = state.faction(war.b());
			if (!present(a) || !present(b)) {
				continue;
			}
			if (war.level() == 0) {
				war.setLevel(1, context.day());
				context.chronicle(war.a(), "chronicle.worldbetween.war.contact", "@war.worldbetween.people." + war.a().id(),
						"@war.worldbetween.people." + war.b().id());
			}
			heat(context, war, a, b);
			int wanted = war.wantedLevel(context.day());
			if (wanted != war.level()) {
				boolean rising = wanted > war.level();
				war.setLevel(wanted, context.day());
				context.chronicle(war.a(), "chronicle.worldbetween.war." + (rising ? "rise." : "cool.") + wanted,
						"@war.worldbetween.people." + war.a().id(), "@war.worldbetween.people." + war.b().id());
			}
			dispatch(context, war, a, b);
			dispatch(context, war, b, a);
		}
		march(context, now);
		encounters(context, now);
		state.markDirty();
	}

	private static boolean present(Faction faction) {
		return faction.isActive() && faction.settlements().stream().anyMatch(s -> s.dimension() == net.minecraft.world.World.OVERWORLD);
	}

	// ---------------------------------------------------------------- heat

	/** Near borders, ambition and an easy prey heat a war; time cools it. */
	private static void heat(SimulationContext context, War war, Faction a, Faction b) {
		double gain = 0;
		double nearest = nearestDistance(a, b);
		if (nearest < 300) {
			gain += 0.7;
		} else if (nearest < 600) {
			gain += 0.4;
		} else if (nearest < 1000) {
			gain += 0.15;
		}
		double pa = Armies.power(a);
		double pb = Armies.power(b);
		double ratio = Math.max(pa, pb) / Math.max(1, Math.min(pa, pb));
		if (ratio > 3) {
			gain += 0.6;
		} else if (ratio > 1.5) {
			gain += 0.3;
		}
		gain += temper(a) + temper(b);
		if (war.involves(FactionType.NERIS) && war.involves(FactionType.KAMENT)) {
			// The kingdom's mines dig into the mountains; and the further its age of iron goes, the worse.
			Faction neris = a.type() == FactionType.NERIS ? a : b;
			Faction kaments = a.type() == FactionType.KAMENT ? a : b;
			gain += 0.1 * neris.era();
			for (Settlement town : neris.settlements()) {
				if (town.mineSite() != null && kaments.settlements().stream().anyMatch(h -> distance(h.pos(), town.mineSite()) < 220)) {
					gain += 0.4;
					break;
				}
			}
		}
		gain *= pairing(war);
		war.addTension(Math.max(0, gain) - 0.5 - 0.05 * war.tension());
	}

	/** How readily two peoples come to blows: the kingdom and the mountain hate each other most; the Arki and the Kaments least. */
	private static double pairing(War war) {
		if (war.involves(FactionType.NERIS) && war.involves(FactionType.KAMENT)) {
			return 1.3;
		}
		if (war.involves(FactionType.ARKI) && war.involves(FactionType.KAMENT)) {
			return 0.5;
		}
		return 1.0;
	}

	/** A warlike king, or Ancients set on their wards. */
	private static double temper(Faction faction) {
		if (faction.type() == FactionType.NERIS) {
			Noble king = faction.king();
			if (king != null && (king.ambition() == Ambition.WAR || king.ambition() == Ambition.ARMY)) {
				return 0.5;
			}
		}
		if (faction.type() == FactionType.KAMENT && Kaments.focus(faction) == Focus.WARD) {
			return -0.2;
		}
		return 0;
	}

	// ---------------------------------------------------------------- sending out

	private static void dispatch(SimulationContext context, War war, Faction side, Faction enemy) {
		WarState wars = context.state().wars();
		double now = context.day();
		Random random = context.random();
		String key = war.key() + "/" + side.type().id();
		// Border patrols, from the first degree on: towards the enemy's nearest town, whatever it is.
		Pair border = front(side, enemy, true);
		if (border != null && border.distance() < BORDER && active(wars, side.type(), enemy.type(), Warband.Kind.PATROL) == 0
				&& side.tryEvent("war_patrol_" + key, context.day(), PATROL_INTERVAL)) {
			BlockPos halfway = lerp(border.own().pos(), border.enemy().pos(), 0.45);
			send(context, side, enemy, Warband.Kind.PATROL, border.own(), -1, halfway, 2 + random.nextInt(2));
		}
		Pair front = front(side, enemy, false);
		if (front == null) {
			return;
		}
		// Raiders to lie in wait near the enemy's towns.
		if (war.level() >= 2 && active(wars, side.type(), enemy.type(), Warband.Kind.RAID) == 0
				&& side.tryEvent("war_raid_" + key, context.day(), RAID_INTERVAL)) {
			Settlement prey = nearest(enemy, front.own().pos(), town -> Armies.approachable(side.type(), enemy, town));
			if (prey != null && distance(prey.pos(), front.own().pos()) < 1600) {
				BlockPos ambush = lerp(prey.pos(), front.own().pos(), RAID_STANDOFF / Math.max(RAID_STANDOFF + 1, distance(prey.pos(), front.own().pos())));
				send(context, side, enemy, Warband.Kind.RAID, front.own(), prey.id(), ambush, 3 + random.nextInt(3));
			}
		}
		// Armies on campaign: on the small towns, and at the last degree on any.
		if (war.level() >= 3 && active(wars, side.type(), enemy.type(), Warband.Kind.ARMY) == 0
				&& side.tryEvent("war_army_" + key, context.day(), ARMY_INTERVAL)) {
			Settlement target = enemy.settlements().stream()
					.filter(town -> Armies.reachable(side.type(), enemy, town, war.level()))
					.filter(town -> war.level() >= 4 || town.tier() <= 1)
					.min(Comparator.comparingDouble(town -> Armies.garrison(enemy, town, side.type()) * 60 + distance(town.pos(), front.own().pos())))
					.orElse(null);
			if (target != null && distance(target.pos(), front.own().pos()) < 2500) {
				Settlement muster = nearest(side, target.pos(), town -> Armies.canMuster(side, town));
				int wanted = side.type() == FactionType.NERIS ? Math.min(24, Math.max(6, side.soldiers() * 2 / 5)) : 10;
				if (muster != null && Armies.pool(side) >= 6) {
					Warband army = send(context, side, enemy, Warband.Kind.ARMY, muster, target.id(), target.pos(), wanted);
					if (army != null) {
						context.chronicle(side.type(), "chronicle.worldbetween.war.campaign", "@war.worldbetween.people." + side.type().id(),
								muster.name(), target.name(), army.fighters());
					}
				}
			}
		}
	}

	private static Warband send(SimulationContext context, Faction side, Faction enemy, Warband.Kind kind, Settlement home, int target,
			BlockPos to, int wanted) {
		int fighters = Armies.levy(side, home, wanted);
		if (fighters < (kind.isArmy() ? 4 : 2)) {
			Armies.disband(side, new Warband(-1, side.type(), kind, home.id(), home.pos(), enemy.type(), target, to, fighters, 0));
			return null;
		}
		WarState wars = context.state().wars();
		Warband band = new Warband(wars.nextBandId(), side.type(), kind, home.id(), home.pos(), enemy.type(), target, to, fighters, context.day());
		wars.bands().add(band);
		return band;
	}

	private static int active(WarState wars, FactionType side, FactionType enemy, Warband.Kind kind) {
		return (int) wars.bands().stream().filter(b -> b.faction() == side && b.enemy() == enemy && b.kind() == kind).count();
	}

	// ---------------------------------------------------------------- the march

	private static void march(SimulationContext context, double now) {
		WorldBetweenState state = context.state();
		WarState wars = state.wars();
		for (Warband band : List.copyOf(wars.bands())) {
			Faction side = state.faction(band.faction());
			Faction enemy = state.faction(band.enemy());
			if (band.fighters() <= 0) {
				wars.bands().remove(band);
				continue;
			}
			if (Armies.settlement(side, band.home()) == null && side.settlements().isEmpty()) {
				wars.bands().remove(band);
				continue;
			}
			switch (band.state()) {
				case RETURNING -> {
					if (band.hasArrived(now)) {
						Armies.disband(side, band);
						wars.bands().remove(band);
					}
				}
				case WAITING -> lieInWait(context, band, side, enemy, now);
				case MARCHING -> {
					if (band.kind() == Warband.Kind.ARMY) {
						callOutDefenders(context, band, side, enemy, now);
					}
					if (band.kind() == Warband.Kind.DEFENDERS) {
						chase(wars, band, now);
					}
					if (band.hasArrived(now)) {
						arrive(context, band, side, enemy, now);
					}
				}
			}
		}
	}

	private static void arrive(SimulationContext context, Warband band, Faction side, Faction enemy, double now) {
		switch (band.kind()) {
			case PATROL -> band.turnBack(now);
			case RAID -> band.waitHere(now, (long) now + RAID_DAYS);
			case ARMY -> assault(context, band, side, enemy, now);
			case DEFENDERS -> band.turnBack(now); // nobody left to meet
		}
	}

	/** Raiders in hiding: a little sabotage each night, until it is time to slip away. */
	private static void lieInWait(SimulationContext context, Warband band, Faction side, Faction enemy, double now) {
		Settlement town = Armies.settlement(enemy, band.target());
		if (town == null || now >= band.waitUntil()) {
			band.turnBack(now);
			return;
		}
		if (context.random().nextInt(2) == 0) {
			int killed = Armies.sabotage(enemy, town, context.random());
			if (!band.hasSabotaged()) {
				band.setSabotaged();
				context.chronicle(enemy.type(), killed > 0 ? "chronicle.worldbetween.war.sabotage_killed" : "chronicle.worldbetween.war.sabotage",
						"@war.worldbetween.people." + side.type().id(), town.name());
				War war = context.state().wars().war(side.type(), enemy.type());
				war.addTension(2 * pairing(war));
			}
		}
	}

	/** An army two days or so from its target: the town sends its own army out to meet it in the field. */
	private static void callOutDefenders(SimulationContext context, Warband army, Faction side, Faction enemy, double now) {
		if (army.opponent() >= 0 || army.daysLeft(now) > 2.5) {
			return;
		}
		Settlement town = Armies.settlement(enemy, army.target());
		if (town == null) {
			army.turnBack(now);
			return;
		}
		Settlement muster = Armies.canMuster(enemy, town) ? town : nearest(enemy, town.pos(), t -> Armies.canMuster(enemy, t));
		if (muster == null) {
			return;
		}
		int wanted = enemy.type() == FactionType.NERIS ? Math.min(24, Math.max(4, enemy.soldiers() * 2 / 5)) : 10;
		// Where the two will meet: a little in front of the town, on the army's road.
		BlockPos meet = lerp(town.pos(), army.position(now), 0.5);
		Warband defenders = send(context, enemy, side, Warband.Kind.DEFENDERS, muster, -1, meet, wanted);
		if (defenders == null) {
			return;
		}
		army.setOpponent(defenders.id());
		defenders.setOpponent(army.id());
		context.chronicle(enemy.type(), "chronicle.worldbetween.war.defenders", "@war.worldbetween.people." + enemy.type().id(), town.name(),
				defenders.fighters(), "@war.worldbetween.people." + side.type().id());
	}

	/** The defenders make for where the army will be. */
	private static void chase(WarState wars, Warband defenders, double now) {
		Warband army = wars.band(defenders.opponent());
		if (army == null || army.state() != Warband.State.MARCHING) {
			defenders.turnBack(now);
			return;
		}
		BlockPos ahead = army.position(now + 0.3);
		if (distance(ahead, defenders.destination()) > 30) {
			defenders.redirect(ahead, now);
		}
	}

	/** An army at the walls. Strong enough, it takes the town; otherwise it breaks on them. */
	private static void assault(SimulationContext context, Warband army, Faction side, Faction enemy, double now) {
		WarState wars = context.state().wars();
		War war = wars.war(side.type(), enemy.type());
		Settlement town = Armies.settlement(enemy, army.target());
		if (town == null) {
			army.turnBack(now);
			return;
		}
		Random random = context.random();
		double attack = army.fighters() * Armies.unit(side) * (0.8 + random.nextDouble() * 0.4);
		double defense = Armies.garrison(enemy, town, side.type()) * (0.8 + random.nextDouble() * 0.4);
		BlockPos field = lerp(town.pos(), army.homePos(), 30 / Math.max(31, distance(town.pos(), army.homePos())));
		int fallenAttackers;
		int fallenDefenders;
		if (attack > defense) {
			fallenAttackers = (int) Math.round(army.fighters() * Math.min(0.6, defense / attack * 0.4));
			int before = town.population();
			Armies.sack(enemy, town, side);
			fallenDefenders = Math.max(1, before - town.population());
			war.addTownSacked();
			war.addTension(12 * pairing(war));
			if (war.level() >= 4 && raze(context, enemy, town)) {
				context.chronicle(enemy.type(), "chronicle.worldbetween.war.razed", town.name(), "@war.worldbetween.people." + side.type().id());
			} else {
				context.chronicle(enemy.type(), "chronicle.worldbetween.war.sacked", town.name(), "@war.worldbetween.people." + side.type().id());
			}
		} else {
			fallenAttackers = (int) Math.round(army.fighters() * (0.5 + random.nextDouble() * 0.4));
			fallenDefenders = (int) Math.round(Math.max(1, attack / Armies.unit(enemy) * 0.4));
			loseDefenders(enemy, town, fallenDefenders);
			war.addTension(6 * pairing(war));
			context.chronicle(enemy.type(), "chronicle.worldbetween.war.repelled", town.name(), "@war.worldbetween.people." + side.type().id());
		}
		army.setFighters(army.fighters() - fallenAttackers);
		war.recordBattle(side.type(), fallenAttackers, enemy.type(), fallenDefenders);
		addField(context, Battlefield.Kind.SIEGE, field, side.type(), fallenAttackers, enemy.type(), fallenDefenders);
		army.turnBack(now);
	}

	private static void loseDefenders(Faction faction, Settlement town, int fallen) {
		if (faction.type() == FactionType.NERIS) {
			faction.setSoldiers(Math.max(0, faction.soldiers() - fallen));
		} else {
			town.setPopulation(Math.max(1, town.population() - fallen));
		}
	}

	/**
	 * At the last degree of war a fallen town may be wiped from the map: its people gone, its walls left
	 * to the weather. Never a capital, and never one of a people's last two: ending a people altogether
	 * takes more than an army.
	 */
	private static boolean raze(SimulationContext context, Faction faction, Settlement town) {
		if (town.isCapital() || faction.settlements().size() <= 2 || town.tier() > 1 || town.population() > 12) {
			return false;
		}
		Razing.remove(context, faction, town);
		return true;
	}

	// ---------------------------------------------------------------- meetings in the field

	/** Hostile parties whose ways crossed today fight; raiders strike at the enemy's people on the road. */
	private static void encounters(SimulationContext context, double now) {
		WorldBetweenState state = context.state();
		WarState wars = state.wars();
		List<Warband> bands = new ArrayList<>(wars.bands());
		for (int i = 0; i < bands.size(); i++) {
			for (int j = i + 1; j < bands.size(); j++) {
				Warband x = bands.get(i);
				Warband y = bands.get(j);
				if (x.faction() == y.faction() || x.fighters() <= 0 || y.fighters() <= 0
						|| !WarRules.enemies(context.server(), x.faction(), y.faction())
						|| x.foughtLive() >= (long) now - 1 || y.foughtLive() >= (long) now - 1) {
					continue;
				}
				BlockPos met = meeting(x, y, now);
				if (met != null) {
					battle(context, x, y, met, now);
				}
			}
		}
		// Ambushes on the kingdom's roads.
		Faction neris = state.faction(FactionType.NERIS);
		for (Warband band : List.copyOf(wars.bands())) {
			if (band.enemy() != FactionType.NERIS || band.kind() != Warband.Kind.RAID || band.fighters() <= 0) {
				continue;
			}
			for (Journey journey : List.copyOf(neris.journeys())) {
				if (journey.kind() == Journey.Kind.SETTLERS && context.random().nextInt(2) == 0) {
					continue;
				}
				if (distance(journey.position(now), band.position(now)) < AMBUSH) {
					ambush(context, band, neris, journey, now);
					break;
				}
			}
		}
	}

	/** Where two parties' ways came within reach of each other in the last day, if they did. */
	private static BlockPos meeting(Warband x, Warband y, double now) {
		for (int k = 0; k <= 6; k++) {
			double t = now - 1 + k / 6.0;
			BlockPos a = x.position(t);
			BlockPos b = y.position(t);
			if (distance(a, b) < MEET) {
				return lerp(a, b, 0.5);
			}
		}
		return null;
	}

	/** Two parties fight. The weaker breaks; its survivors run for home. */
	private static void battle(SimulationContext context, Warband x, Warband y, BlockPos at, double now) {
		WorldBetweenState state = context.state();
		Faction fx = state.faction(x.faction());
		Faction fy = state.faction(y.faction());
		Random random = context.random();
		double sx = x.fighters() * Armies.unit(fx) * (0.75 + random.nextDouble() * 0.5) * home(state, fx, at);
		double sy = y.fighters() * Armies.unit(fy) * (0.75 + random.nextDouble() * 0.5) * home(state, fy, at);
		Warband winner = sx >= sy ? x : y;
		Warband loser = winner == x ? y : x;
		double ratio = Math.min(sx, sy) / Math.max(1e-6, Math.max(sx, sy));
		int loserFallen = (int) Math.ceil(loser.fighters() * (0.6 + random.nextDouble() * 0.4));
		int winnerFallen = (int) Math.round(winner.fighters() * ratio * 0.5);
		resolve(context, winner, winnerFallen, loser, loserFallen, at, now);
	}

	/** Records the outcome of a fight, however it was fought: in the simulation or in person. */
	public static void resolve(SimulationContext context, Warband winner, int winnerFallen, Warband loser, int loserFallen, BlockPos at, double now) {
		WarState wars = context.state().wars();
		War war = wars.war(winner.faction(), loser.faction());
		winner.setFighters(winner.fighters() - winnerFallen);
		loser.setFighters(loser.fighters() - loserFallen);
		boolean big = winner.kind().isArmy() || loser.kind().isArmy();
		war.recordBattle(winner.faction(), winnerFallen, loser.faction(), loserFallen);
		war.addTension((big ? 7 : 4) * pairing(war));
		if (winnerFallen + loserFallen > 0) {
			addField(context, big ? Battlefield.Kind.BATTLE : Battlefield.Kind.SKIRMISH, at, winner.faction(), winnerFallen, loser.faction(), loserFallen);
		}
		if (loser.fighters() <= 0) {
			wars.bands().remove(loser);
		} else {
			loser.turnBack(now);
		}
		if (winner.fighters() <= 0) {
			wars.bands().remove(winner);
		} else if (winner.kind() == Warband.Kind.DEFENDERS) {
			winner.turnBack(now);
		}
		if (big || winner.faction() != FactionType.NERIS && loser.faction() != FactionType.NERIS
				|| context.state().faction(winner.faction()).tryEvent("war_skirmish_note", context.day(), 3)) {
			context.chronicle(winner.faction(), big ? "chronicle.worldbetween.war.battle" : "chronicle.worldbetween.war.skirmish",
					"@war.worldbetween.people." + winner.faction().id(), "@war.worldbetween.people." + loser.faction().id(),
					winnerFallen + loserFallen, Ground.isWater(context.server().getOverworld(), at.getX(), at.getZ()) ? "@war.worldbetween.at_sea" : "@war.worldbetween.on_land");
		}
		context.state().markDirty();
	}

	/** Raiders fall on a party of the kingdom on the road. */
	private static void ambush(SimulationContext context, Warband band, Faction neris, Journey journey, double now) {
		Faction side = context.state().faction(band.faction());
		int guards = journey.kind() == Journey.Kind.PATROL ? 2 : journey.kind() == Journey.Kind.CARAVAN ? 1 : 0;
		double defense = (guards + 0.3 * journey.kind().people()) * Armies.unit(neris);
		double attack = band.fighters() * Armies.unit(side) * (0.8 + context.random().nextDouble() * 0.4);
		BlockPos at = journey.position(now);
		WarState wars = context.state().wars();
		War war = wars.war(band.faction(), FactionType.NERIS);
		Settlement home = Journeys.settlement(neris, journey.from());
		if (attack > defense) {
			neris.journeys().remove(journey);
			int killed = journey.kind().people();
			if (home != null) {
				home.setPopulation(Math.max(1, home.population() - killed));
			}
			int fallen = guards > 0 ? (int) Math.round(band.fighters() * defense / attack * 0.4) : 0;
			band.setFighters(band.fighters() - fallen);
			war.recordBattle(band.faction(), fallen, FactionType.NERIS, killed);
			addField(context, Battlefield.Kind.SKIRMISH, at, band.faction(), fallen, FactionType.NERIS, killed);
			context.chronicle(FactionType.NERIS, "chronicle.worldbetween.war.ambush." + journey.kind().id(),
					"@war.worldbetween.people." + band.faction().id(), home != null ? home.name() : "?");
		} else {
			int fallen = (int) Math.ceil(band.fighters() * 0.7);
			band.setFighters(band.fighters() - fallen);
			war.recordBattle(band.faction(), fallen, FactionType.NERIS, 1);
			addField(context, Battlefield.Kind.SKIRMISH, at, band.faction(), fallen, FactionType.NERIS, 1);
			context.chronicle(FactionType.NERIS, "chronicle.worldbetween.war.ambush_beaten",
					"@war.worldbetween.people." + band.faction().id(), home != null ? home.name() : "?");
			band.turnBack(now);
		}
		war.addTension(4 * pairing(war));
		if (band.fighters() <= 0) {
			wars.bands().remove(band);
		}
	}

	/** Fighting on one's own ground counts for something; for the Kaments, on their mountain, a great deal. */
	private static double home(WorldBetweenState state, Faction faction, BlockPos at) {
		Settlement near = nearest(faction, at, town -> true);
		if (near == null || distance(near.pos(), at) > 250) {
			return 1;
		}
		return faction.type() == FactionType.KAMENT ? 1.3 : 1.15;
	}

	public static void addField(SimulationContext context, Battlefield.Kind kind, BlockPos at, FactionType a, int fallenA, FactionType b, int fallenB) {
		if (fallenA + fallenB <= 0) {
			return;
		}
		context.state().wars().addField(kind, at, context.day(), a, fallenA, b, fallenB);
	}

	// ---------------------------------------------------------------- geometry

	private record Pair(Settlement own, Settlement enemy, double distance) {
	}

	/** The two towns nearest each other, one of each side, from which the side can send parties out. */
	private static Pair front(Faction side, Faction enemy, boolean anyTown) {
		Pair best = null;
		for (Settlement own : side.settlements()) {
			if (!Armies.canMuster(side, own)) {
				continue;
			}
			for (Settlement other : enemy.settlements()) {
				if (!anyTown && !Armies.approachable(side.type(), enemy, other) || other.dimension() != own.dimension()) {
					continue;
				}
				double d = distance(own.pos(), other.pos());
				if (best == null || d < best.distance()) {
					best = new Pair(own, other, d);
				}
			}
		}
		return best;
	}

	private static double nearestDistance(Faction a, Faction b) {
		double best = Double.MAX_VALUE;
		for (Settlement x : a.settlements()) {
			for (Settlement y : b.settlements()) {
				if (x.dimension() == y.dimension()) {
					best = Math.min(best, distance(x.pos(), y.pos()));
				}
			}
		}
		return best;
	}

	private static Settlement nearest(Faction faction, BlockPos to, java.util.function.Predicate<Settlement> filter) {
		return faction.settlements().stream().filter(filter)
				.min(Comparator.comparingDouble(s -> distance(s.pos(), to))).orElse(null);
	}

	public static double distance(BlockPos a, BlockPos b) {
		return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
	}

	public static BlockPos lerp(BlockPos a, BlockPos b, double t) {
		return new BlockPos((int) Math.round(a.getX() + (b.getX() - a.getX()) * t), 0, (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t));
	}

}
