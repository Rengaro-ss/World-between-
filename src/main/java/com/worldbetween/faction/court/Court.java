package com.worldbetween.faction.court;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.neris.NerisNames;
import com.worldbetween.sim.SimulationContext;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The court of a kingdom: a king and the barons of its towns. Each baron oversees one part of their
 * town's life and makes it a little better; a king does the same for every town, twice as well.
 * When the king dies, the barons meet the next day and elect one of their own.
 *
 * <p>Nobles are people, not numbers: two traits and an ambition decide what they push for, whom they
 * like, how they vote, and whether they plot. That is where the kingdom's unpredictability comes from.
 */
public final class Court {
	private static final float BARON_BONUS = 0.10f;
	private static final int[] BARON_WEALTH = {300, 1000};
	private static final float TAX_PER_PERSON = 0.2f;
	private static final float SOLDIER_PAY = 0.1f;
	private static final int COINS_PER_METAL = 9;

	private Court() {
	}

	// ---------------------------------------------------------------- effects

	/** Bonus to a sphere of a town: from its own barons, and from the king for every town. */
	public static float sphereBonus(Faction faction, Settlement settlement, Sphere sphere) {
		if (sphere == null) {
			return 0f;
		}
		Noble king = faction.king();
		float bonus = 0f;
		for (Noble noble : faction.livingNobles()) {
			if (noble == king) {
				continue;
			}
			if (noble.seat() == settlement.id() && noble.sphere() == sphere) {
				bonus += BARON_BONUS * noble.diligence();
			}
		}
		if (king != null && king.sphere() == sphere) {
			bonus += 2 * BARON_BONUS * king.diligence();
		}
		return bonus;
	}

	private static List<Noble> seated(Faction faction, Settlement settlement) {
		return faction.livingNobles().stream().filter(n -> n.seat() == settlement.id()).toList();
	}

	private static boolean kingIs(Faction faction, Trait trait) {
		Noble king = faction.king();
		return king != null && king.has(trait);
	}

	private static boolean kingWants(Faction faction, Ambition ambition) {
		Noble king = faction.king();
		return king != null && king.ambition() == ambition;
	}

	/** The ambition steering a town's building: its king if he sits there, else its first baron. */
	public static Ambition ambitionOf(Faction faction, Settlement settlement) {
		Noble king = faction.king();
		if (king != null && king.seat() == settlement.id()) {
			return king.ambition();
		}
		return seated(faction, settlement).stream().findFirst().map(Noble::ambition).orElse(null);
	}

	/** Seconds a noticed stranger gets to leave this town's mine: just lords are lenient, cruel and fearful ones are not. */
	public static int graceSeconds(Faction faction, Settlement settlement, int base) {
		int seconds = base;
		List<Noble> lords = new ArrayList<>(seated(faction, settlement));
		Noble king = faction.king();
		if (king != null && !lords.contains(king)) {
			lords.add(king);
		}
		for (Noble lord : lords) {
			if (lord.has(Trait.JUST)) {
				seconds += 60;
			}
			if (lord.has(Trait.CRUEL) || lord.has(Trait.PARANOID)) {
				seconds -= 60;
			}
		}
		return Math.max(30, seconds);
	}

	/** One soldier per this many people. A warlike or cruel king takes more. */
	public static int recruitDivisor(Faction faction) {
		return kingWants(faction, Ambition.WAR) || kingWants(faction, Ambition.ARMY) || kingIs(faction, Trait.CRUEL) ? 5 : 7;
	}

	public static float knowledgeMultiplier(Faction faction) {
		return 1f + (kingWants(faction, Ambition.SCIENCE) ? 0.25f : 0f) + (kingIs(faction, Trait.WISE) ? 0.1f : 0f);
	}

	public static float raidMultiplier(Faction faction, Settlement settlement) {
		float m = Math.max(0.2f, 1f - 2 * sphereBonus(faction, settlement, Sphere.GUARD));
		if (kingWants(faction, Ambition.ORDER)) {
			m *= 0.5f;
		}
		if (seated(faction, settlement).stream().anyMatch(n -> n.has(Trait.PARANOID))) {
			m *= 0.8f;
		}
		return m;
	}

	public static float storageMultiplier(Faction faction) {
		return kingIs(faction, Trait.THRIFTY) ? 1.25f : 1f;
	}

	/** Extra work-days of construction per day in this town. */
	public static int buildBonus(Faction faction, Settlement settlement) {
		int bonus = Math.round(sphereBonus(faction, settlement, Sphere.BUILDING) * 10);
		if (kingWants(faction, Ambition.GLORY)) {
			bonus++;
		}
		if (seated(faction, settlement).stream().anyMatch(n -> n.has(Trait.BUILDER))) {
			bonus++;
		}
		return bonus;
	}

	public static float birthMultiplier(Faction faction, Settlement settlement) {
		float m = 1f + 3 * sphereBonus(faction, settlement, Sphere.HOUSING);
		return kingIs(faction, Trait.GREEDY) ? m * 0.85f : m;
	}

	public static int expansionCooldown(Faction faction, int base) {
		return kingWants(faction, Ambition.EXPANSION) || kingIs(faction, Trait.RECKLESS) ? base * 3 / 5 : base;
	}

	public static float taxMultiplier(Faction faction, Settlement settlement) {
		float m = 1f + sphereBonus(faction, settlement, Sphere.TRADE);
		if (kingWants(faction, Ambition.WEALTH)) {
			m += 0.25f;
		}
		if (kingIs(faction, Trait.GREEDY)) {
			m += 0.3f;
		}
		return m;
	}

	// ---------------------------------------------------------------- the treasury

	/** Taxes into the towns and the crown, coins struck from metal, soldiers and barons paid. */
	public static void economy(SimulationContext context, Faction faction) {
		for (Settlement settlement : faction.settlements()) {
			long tax = Math.round(settlement.population() * TAX_PER_PERSON * taxMultiplier(faction, settlement));
			for (Noble lord : seated(faction, settlement)) {
				if (lord.has(Trait.GREEDY)) {
					long skim = Math.max(1, tax / 10);
					lord.addToPurse(skim);
					tax -= skim;
				}
				long upkeep = Math.min(settlement.coins(), 1);
				settlement.addCoins(-upkeep);
				lord.addToPurse(upkeep);
			}
			settlement.addCoins(tax / 2);
			faction.addResource(ResourceType.COINS, tax - tax / 2);
			// Keeping a town costs money too, so its treasury follows its income rather than growing forever.
			settlement.addCoins(-settlement.coins() / 50);
		}
		faction.takeResource(ResourceType.COINS, faction.resource(ResourceType.COINS) / 200);
		// The capital strikes coins from spare metal.
		faction.settlements().stream().filter(Settlement::isCapital).findFirst().ifPresent(capital -> {
			if (faction.resource(ResourceType.METAL) > 60) {
				long metal = faction.takeResource(ResourceType.METAL, 1 + capital.tier());
				faction.addResource(ResourceType.COINS, metal * COINS_PER_METAL);
			}
		});
		long pay = Math.round(faction.soldiers() * SOLDIER_PAY);
		long paid = faction.takeResource(ResourceType.COINS, pay);
		if (paid < pay) {
			int deserters = (int) Math.ceil((pay - paid) / SOLDIER_PAY / 10);
			faction.setSoldiers(faction.soldiers() - deserters);
			if (deserters > 0 && faction.tryEvent("deserters", context.day(), 15)) {
				context.chronicle(faction.type(), "chronicle.worldbetween.court.deserters", deserters);
			}
		}
	}

	// ---------------------------------------------------------------- life at court

	/** The leader who brought the people here becomes their first king. */
	public static void crownFounder(SimulationContext context, Faction faction, Settlement seat) {
		Noble founder = raise(context, faction, seat);
		faction.setKing(founder);
		context.chronicle(faction.type(), "chronicle.worldbetween.court.first_king", founder.chronicleName(), seat.name());
	}

	public static void simulateDay(SimulationContext context, Faction faction) {
		Random random = context.random();
		long day = context.day();

		for (Settlement settlement : faction.settlements()) {
			List<Noble> lords = seated(faction, settlement);
			int allowed = allowedBarons(settlement);
			if (lords.size() < allowed && random.nextFloat() < 0.2f) {
				Noble baron = raise(context, faction, settlement);
				context.chronicle(faction.type(), "chronicle.worldbetween.court.baron_rise", baron.chronicleName(), settlement.name());
			} else if (lords.size() > allowed && random.nextFloat() < 0.05f) {
				lords.stream().filter(n -> n != faction.king()).max(Comparator.comparingLong(n -> -n.age(day))).ifPresent(baron -> {
					baron.die();
					context.chronicle(faction.type(), "chronicle.worldbetween.court.baron_fall", baron.chronicleName(), settlement.name());
				});
			}
			for (Noble lord : seated(faction, settlement)) {
				if (lord.has(Trait.DRUNKARD) && random.nextFloat() < 0.01f) {
					faction.takeResource(ResourceType.FOOD, faction.resource(ResourceType.FOOD) / 20);
					settlement.addCoins(-settlement.coins() / 10);
					if (faction.tryEvent("drunk_" + lord.id(), day, 40)) {
						context.chronicle(faction.type(), "chronicle.worldbetween.court.drunk", lord.chronicleName(), settlement.name());
					}
				}
			}
		}

		for (Noble noble : faction.livingNobles()) {
			Settlement seat = seatOf(faction, noble);
			if (random.nextFloat() < deathChance(noble.age(day))) {
				die(context, faction, noble, seat, "age");
			}
		}

		plot(context, faction);

		if (faction.king() == null) {
			if (faction.councilDay() == Faction.NEVER) {
				faction.setCouncilDay(day + 1);
			}
			if (day >= faction.councilDay()) {
				elect(context, faction);
			}
		}
	}

	private static int allowedBarons(Settlement settlement) {
		if (settlement.tier() == 0 && !settlement.isCapital()) {
			return 0;
		}
		int allowed = 1;
		for (int threshold : BARON_WEALTH) {
			if (settlement.coins() >= threshold) {
				allowed++;
			}
		}
		return allowed;
	}

	/** A new baron rises from the townsfolk. */
	private static Noble raise(SimulationContext context, Faction faction, Settlement seat) {
		Random random = context.random();
		List<Trait> pool = new ArrayList<>(List.of(Trait.values()));
		Trait first = pool.get(random.nextInt(pool.size()));
		pool.removeIf(t -> t == first || t.contradicts(first));
		Trait second = pool.get(random.nextInt(pool.size()));
		Ambition ambition = pickAmbition(random, first, second);
		Set<Sphere> taken = EnumSet.noneOf(Sphere.class);
		seated(faction, seat).forEach(n -> taken.add(n.sphere()));
		Sphere sphere = ambition.sphere();
		if (taken.contains(sphere)) {
			List<Sphere> free = new ArrayList<>(List.of(Sphere.values()));
			free.removeAll(taken);
			sphere = free.isEmpty() ? sphere : free.get(random.nextInt(free.size()));
		}
		int age = 22 + random.nextInt(24);
		Noble noble = new Noble(faction.nextNobleId(), NerisNames.person(random), first, second, ambition,
				context.day() - (long) age * Noble.DAYS_PER_YEAR, seat.id(), sphere);
		faction.nobles().add(noble);
		return noble;
	}

	/** Character leans on ambition: the greedy want wealth, the brave and the cruel want war. */
	private static Ambition pickAmbition(Random random, Trait first, Trait second) {
		if (random.nextBoolean()) {
			for (Trait trait : List.of(first, second)) {
				switch (trait) {
					case GREEDY, THRIFTY -> { return Ambition.WEALTH; }
					case WISE -> { return Ambition.SCIENCE; }
					case BRAVE, RECKLESS, CRUEL -> { return random.nextBoolean() ? Ambition.WAR : Ambition.ARMY; }
					case BUILDER -> { return Ambition.GLORY; }
					case REMEMBERS_CHAINS -> { return Ambition.VENGEANCE; }
					case PARANOID, JUST -> { return Ambition.ORDER; }
					default -> { }
				}
			}
		}
		return Ambition.values()[random.nextInt(Ambition.values().length)];
	}

	private static float deathChance(int age) {
		if (age < 50) return 0.0002f;
		if (age < 60) return 0.001f;
		if (age < 70) return 0.003f;
		return 0.008f;
	}

	private static Settlement seatOf(Faction faction, Noble noble) {
		return faction.settlements().stream().filter(s -> s.id() == noble.seat()).findFirst().orElse(null);
	}

	private static void die(SimulationContext context, Faction faction, Noble noble, Settlement seat, String cause) {
		boolean king = noble == faction.king();
		noble.die();
		String place = seat == null ? "?" : seat.name();
		if (king) {
			faction.setKing(null);
			faction.setCouncilDay(context.day() + 1);
			context.chronicle(faction.type(), "chronicle.worldbetween.court.king_death_" + cause, noble.chronicleName(), place);
		} else {
			context.chronicle(faction.type(), "chronicle.worldbetween.court.baron_death_" + cause, noble.chronicleName(), place);
		}
	}

	/** An ambitious baron who despises the king may try to take his life. */
	private static void plot(SimulationContext context, Faction faction) {
		Noble king = faction.king();
		if (king == null) {
			return;
		}
		for (Noble baron : faction.livingNobles()) {
			if (baron == king || !baron.has(Trait.AMBITIOUS) || opinion(baron, king) >= -10) {
				continue;
			}
			if (context.random().nextFloat() >= 0.004f) {
				continue;
			}
			Settlement seat = seatOf(faction, baron);
			if (context.random().nextBoolean()) {
				context.chronicle(faction.type(), "chronicle.worldbetween.court.plot_success",
						baron.chronicleName(), king.chronicleName(), seat == null ? "?" : seat.name());
				die(context, faction, king, seatOf(faction, king), "plot");
			} else {
				context.chronicle(faction.type(), "chronicle.worldbetween.court.plot_failed",
						baron.chronicleName(), king.chronicleName(), seat == null ? "?" : seat.name());
				baron.die();
			}
			return;
		}
	}

	/**
	 * What one noble thinks of another: kindred traits and shared goals draw them together, opposite
	 * traits drive them apart, and old favours and grudges count too.
	 */
	public static int opinion(Noble from, Noble to) {
		if (from == to) {
			return 50;
		}
		int opinion = from.regard(to.id());
		for (Trait a : from.traits()) {
			for (Trait b : to.traits()) {
				if (a == b) {
					opinion += 8;
				} else if (a.contradicts(b)) {
					opinion -= 12;
				}
			}
		}
		if (from.ambition() == to.ambition()) {
			opinion += 5;
		}
		if (from.has(Trait.PARANOID)) {
			opinion -= 5;
		}
		return opinion;
	}

	/** The council of barons meets and elects a king from among themselves. */
	private static void elect(SimulationContext context, Faction faction) {
		List<Noble> barons = faction.livingNobles();
		if (barons.isEmpty()) {
			if (faction.tryEvent("regency", context.day(), 30)) {
				context.chronicle(faction.type(), "chronicle.worldbetween.court.regency");
			}
			return;
		}
		Random random = context.random();
		Map<Noble, Integer> votes = new HashMap<>();
		int cast = 0;
		for (Noble voter : barons) {
			if (voter.has(Trait.DRUNKARD) && random.nextFloat() < 0.3f) {
				continue; // too drunk to come to the council
			}
			Noble choice = barons.stream().max(Comparator.comparingDouble(candidate -> score(faction, voter, candidate))).orElseThrow();
			votes.merge(choice, 1, Integer::sum);
			cast++;
			if (choice != voter) {
				choice.changeRegard(voter.id(), 5);
			}
		}
		long day = context.day();
		Noble winner = votes.entrySet().stream()
				.max(Comparator.<Map.Entry<Noble, Integer>>comparingInt(Map.Entry::getValue)
						.thenComparingInt(e -> e.getKey().age(day)))
				.map(Map.Entry::getKey).orElse(barons.get(0));
		faction.setKing(winner);
		faction.setCouncilDay(Faction.NEVER);
		// Rivals who wanted the crown resent the one who got it.
		for (Noble baron : barons) {
			if (baron != winner && baron.has(Trait.AMBITIOUS)) {
				baron.changeRegard(winner.id(), -15);
			}
		}
		Settlement seat = seatOf(faction, winner);
		if (barons.size() == 1) {
			context.chronicle(faction.type(), "chronicle.worldbetween.court.acclaimed", winner.chronicleName(), seat == null ? "?" : seat.name());
		} else {
			context.chronicle(faction.type(), "chronicle.worldbetween.court.elected", winner.chronicleName(),
					seat == null ? "?" : seat.name(), votes.getOrDefault(winner, 0), cast);
		}
	}

	private static double score(Faction faction, Noble voter, Noble candidate) {
		// Nobody is their own friend on the council; only ambition makes a baron vote for himself.
		double score = voter == candidate ? 0 : opinion(voter, candidate);
		Settlement seat = seatOf(faction, candidate);
		if (seat != null) {
			score += seat.tier() * 4 + Math.log1p(seat.coins()) * 2 + (seat.isCapital() ? 5 : 0);
		}
		if (candidate.has(Trait.CHARISMATIC)) {
			score += 10;
		}
		if (voter == candidate) {
			score += voter.has(Trait.AMBITIOUS) ? 30 : 6;
		}
		if (voter.has(Trait.GREEDY)) {
			score += candidate.purse() / 40.0;
		}
		return score;
	}

	/** Kills the king, for testing the council. */
	public static boolean killKing(SimulationContext context, Faction faction) {
		Noble king = faction.king();
		if (king == null) {
			return false;
		}
		die(context, faction, king, seatOf(faction, king), "illness");
		return true;
	}
}
