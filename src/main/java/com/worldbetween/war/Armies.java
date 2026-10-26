package com.worldbetween.war;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.arki.Arkis;
import com.worldbetween.faction.kament.KamentHall;
import com.worldbetween.faction.kament.Kaments;
import net.minecraft.world.World;

/**
 * How each people makes war: what one of its fighters is worth, where its fighters come from and go back
 * to, what its towns can hold out with, and who can reach whom. The Neris fight with soldiers from their
 * barracks; the Kaments with their own people, each of them hard to break and slow to replace; the Arki
 * with their magic, and from the sky, where no one else can follow.
 */
public final class Armies {
	private Armies() {
	}

	/** What one fighter is worth in battle. */
	public static double unit(Faction faction) {
		return switch (faction.type()) {
			case NERIS -> 1.0 + 0.25 * faction.era();
			case KAMENT -> 2.6;
			case ARKI -> 2.1 * (1 + 0.15 * faction.era());
		};
	}

	/** What a manticore with its rider is worth: a handful of soldiers, and from the air. */
	public static final double MANTICORE = 7;

	/** What a whole band is worth in a fight, beasts and all. */
	public static double strength(Faction faction, Warband band) {
		return band.fighters() * unit(faction) + band.beasts() * MANTICORE;
	}

	/** How many fighters the people could send out in all. */
	public static int pool(Faction faction) {
		return switch (faction.type()) {
			case NERIS -> faction.soldiers();
			case KAMENT, ARKI -> faction.settlements().stream().mapToInt(Armies::spare).sum();
		};
	}

	/** What a people could throw into a war, all told. */
	public static double power(Faction faction) {
		return pool(faction) * unit(faction) + faction.totalPopulation() * 0.05 + faction.counter("manticores") * MANTICORE;
	}

	/** Kaments and Arki take fighters from a town's own people, and leave at least three at home. */
	private static int spare(Settlement settlement) {
		return Math.max(0, (settlement.population() - 3) / 3);
	}

	/**
	 * Takes up to {@code wanted} fighters from a town (for the Neris, from the kingdom's soldiers).
	 * Returns how many actually went.
	 */
	public static int levy(Faction faction, Settlement home, int wanted) {
		if (faction.type() == FactionType.NERIS) {
			int taken = Math.min(wanted, faction.soldiers() / 2);
			faction.setSoldiers(faction.soldiers() - taken);
			return taken;
		}
		// The muster town first, then the others that can send their people out, nearest first.
		int taken = 0;
		java.util.List<Settlement> towns = new java.util.ArrayList<>(faction.settlements());
		towns.sort(java.util.Comparator.comparingDouble(t -> t == home ? -1 : t.pos().getSquaredDistance(home.pos())));
		for (Settlement town : towns) {
			if (taken >= wanted || !canMuster(faction, town) && town != home) {
				continue;
			}
			int share = Math.min(wanted - taken, spare(town));
			town.setPopulation(town.population() - share);
			taken += share;
		}
		return taken;
	}

	/** Survivors come home. */
	public static void disband(Faction faction, Warband band) {
		if (band.home() == Warband.TEST) {
			return;
		}
		if (faction.type() == FactionType.NERIS) {
			faction.setSoldiers(faction.soldiers() + band.fighters());
			faction.addCounter("manticores", band.beasts());
			return;
		}
		Settlement home = settlement(faction, band.home());
		if (home == null && !faction.settlements().isEmpty()) {
			home = faction.settlements().get(0);
		}
		if (home != null) {
			home.setPopulation(home.population() + band.fighters());
		}
	}

	/** What a town holds out with when an army comes to its walls. */
	public static double garrison(Faction faction, Settlement town, FactionType attacker) {
		return switch (faction.type()) {
			case NERIS -> {
				int total = Math.max(1, faction.totalPopulation());
				double soldiers = faction.soldiers() * (double) town.population() / total + 2 + 2 * town.tier();
				yield soldiers * unit(faction) * (1 + 0.25 * town.tier());
			}
			case KAMENT -> (1 + town.tier() + 2 * Kaments.count(town, KamentHall.WARD) + town.population() / 6.0) * unit(faction) * 1.4;
			case ARKI -> (1 + town.population() / 3.0) * unit(faction) * (attacker == FactionType.NERIS ? 1.3 : 1);
		};
	}

	/** Whether an attacker's army can get at a town at all. */
	public static boolean reachable(FactionType attacker, Faction defender, Settlement town, int level) {
		if (town.dimension() != World.OVERWORLD) {
			return false;
		}
		return switch (defender.type()) {
			case NERIS -> true;
			// The deep cities lie under the rock: only the hold above leads down to them.
			case KAMENT -> !Kaments.isDeep(town);
			// The sky is the Arki's alone; the sea is open only to the boats of the Neris, and stone sinks.
			case ARKI -> !Arkis.isSky(town) && attacker == FactionType.NERIS && level >= 3;
		};
	}

	/** Whether a people's border patrols and raiders can get near a town (the sea and the sky hold off most). */
	public static boolean approachable(FactionType attacker, Faction defender, Settlement town) {
		if (town.dimension() != World.OVERWORLD) {
			return false;
		}
		return switch (defender.type()) {
			case NERIS -> true;
			case KAMENT -> !Kaments.isDeep(town);
			case ARKI -> !Arkis.isSky(town) && attacker != FactionType.KAMENT;
		};
	}

	/** Where a people's war parties set out from. The deep cities send theirs up through the rock. */
	public static boolean canMuster(Faction faction, Settlement town) {
		return town.dimension() == World.OVERWORLD;
	}

	/** A raid on a town: some of its stores go up in smoke, and now and then someone dies. Returns the people lost. */
	public static int sabotage(Faction faction, Settlement town, net.minecraft.util.math.random.Random random) {
		switch (faction.type()) {
			case NERIS -> {
				faction.takeResource(ResourceType.GRAIN, faction.resource(ResourceType.GRAIN) / 10);
				faction.takeResource(ResourceType.FOOD, faction.resource(ResourceType.FOOD) / 12);
				town.addCoins(-town.coins() / 10);
			}
			case KAMENT -> {
				faction.takeResource(ResourceType.BRICKS, faction.resource(ResourceType.BRICKS) / 10);
				faction.takeResource(ResourceType.FLINT, faction.resource(ResourceType.FLINT) / 12);
			}
			case ARKI -> {
				town.addCounter(Arkis.STARLIGHT, -town.counter(Arkis.STARLIGHT) / 6);
				town.addCounter(Arkis.AMETHYST, -town.counter(Arkis.AMETHYST) / 10);
			}
		}
		if (random.nextInt(3) == 0 && town.population() > 3) {
			town.setPopulation(town.population() - 1);
			return 1;
		}
		return 0;
	}

	/** A town taken by storm: its people and stores suffer, and the victors carry off what suits them. */
	public static void sack(Faction loser, Settlement town, Faction victor) {
		int lost = (int) Math.ceil(town.population() * (loser.type() == FactionType.KAMENT ? 0.15 : 0.25));
		town.setPopulation(Math.max(1, town.population() - lost));
		switch (loser.type()) {
			case NERIS -> {
				long metal = loser.takeResource(ResourceType.METAL, loser.resource(ResourceType.METAL) / 5);
				long food = loser.takeResource(ResourceType.FOOD, loser.resource(ResourceType.FOOD) / 6);
				loser.setSoldiers((int) (loser.soldiers() * 0.85));
				long coins = town.coins() / 2;
				town.addCoins(-coins);
				// The Kaments take the metal back to the mountain; the Arki take the books.
				if (victor.type() == FactionType.KAMENT) {
					victor.addCounter("reclaimed_metal", metal);
				} else if (victor.type() == FactionType.ARKI) {
					victor.settlements().stream().findFirst().ifPresent(c -> c.addCounter(Arkis.LORE, 20));
				}
				victor.addResource(ResourceType.FOOD, food / 2);
			}
			case KAMENT -> {
				loser.takeResource(ResourceType.BRICKS, loser.resource(ResourceType.BRICKS) / 5);
				loser.takeResource(ResourceType.FLINT, loser.resource(ResourceType.FLINT) / 5);
				if (victor.type() == FactionType.NERIS) {
					victor.addResource(ResourceType.STONE, 40);
					victor.addResource(ResourceType.ORE, 20);
				}
			}
			case ARKI -> {
				for (String store : Arkis.STORES) {
					town.addCounter(store, -town.counter(store) * 2 / 5);
				}
			}
		}
	}

	public static Settlement settlement(Faction faction, int id) {
		for (Settlement settlement : faction.settlements()) {
			if (settlement.id() == id) {
				return settlement;
			}
		}
		return null;
	}
}
