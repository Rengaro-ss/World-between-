package com.worldbetween.faction.projects;

import com.worldbetween.faction.BuildingType;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.arki.ArkiHall;
import com.worldbetween.faction.arki.Arkis;
import com.worldbetween.faction.court.Ambition;
import com.worldbetween.faction.court.Noble;
import com.worldbetween.faction.court.Trait;
import com.worldbetween.faction.kament.KamentHall;
import com.worldbetween.faction.kament.Kaments;
import com.worldbetween.sim.SimulationContext;
import net.minecraft.util.math.random.Random;

import java.util.Comparator;
import java.util.Map;

/**
 * How the peoples find and make their great works. Study goes on every day, one work at a time (the weapon
 * first, then the deed): the kingdom studies in its libraries, the Kaments in their halls of memory, the
 * Arki in their observatories. A work found waits until everything it takes is gathered; then it is paid
 * for and made over many days. Deeds ask more: the kingdom's barons must agree to the campaign in the Nether,
 * and the Kaments give the souls of their own to wake their hero.
 */
public final class Projects {
	private static final Project[][] ORDER = {
			{Project.MANTICORES, Project.HELL_CAMPAIGN},
			{Project.QUAKE_CRYSTAL, Project.MOROK},
			{Project.COMET_STORM, Project.NIGHT_TOWER}};
	/** A manticore is bred this often while there is food and metal for it. */
	private static final int BREED_DAYS = 15;
	public static final int MAX_MANTICORES = 6;
	public static final int MAX_CHARGES = 3;
	private static final int CHARGE_DAYS = 15;
	private static final int VOTE_DAYS = 30;

	private Projects() {
	}

	public static int stage(Faction faction, Project project) {
		return (int) faction.counter(project.key("stage"));
	}

	public static boolean done(Faction faction, Project project) {
		return stage(faction, project) == Project.DONE;
	}

	public static void setStage(Faction faction, Project project, int stage) {
		faction.setCounter(project.key("stage"), stage);
	}

	/** Study gathered towards a work, out of {@link Project#study()}. */
	public static long studied(Faction faction, Project project) {
		return faction.counter(project.key("study"));
	}

	/** Days of making left. */
	public static long daysLeft(Faction faction, Project project) {
		return faction.counter(project.key("left"));
	}

	public static void simulateDay(SimulationContext context, Faction faction) {
		if (!faction.isActive()) {
			return;
		}
		Project[] order = ORDER[switch (faction.type()) {
			case NERIS -> 0;
			case KAMENT -> 1;
			case ARKI -> 2;
		}];
		study(context, faction, order);
		for (Project project : order) {
			switch (stage(faction, project)) {
				case Project.KNOWN -> gather(context, faction, project);
				case Project.MAKING -> make(context, faction, project);
				default -> {
				}
			}
		}
		upkeep(context, faction);
	}

	/** A day of study, into the first work not yet found. */
	private static void study(SimulationContext context, Faction faction, Project[] order) {
		for (Project project : order) {
			if (stage(faction, project) != Project.UNKNOWN) {
				continue;
			}
			double points = switch (faction.type()) {
				case NERIS -> 1 + 0.2 * faction.totalBuildings(BuildingType.LIBRARY);
				case KAMENT -> 1 + 0.25 * faction.settlements().stream().mapToInt(s -> Kaments.count(s, KamentHall.MEMORY)).sum();
				case ARKI -> 1 + 0.15 * faction.settlements().stream().mapToInt(s -> Arkis.count(s, ArkiHall.OBSERVATORY)).sum();
			};
			// Fractions carry over in tenths, so a slow people still gets there.
			faction.addCounter(project.key("tenths"), Math.round(points * 10));
			faction.addCounter(project.key("study"), faction.counter(project.key("tenths")) / 10);
			faction.setCounter(project.key("tenths"), faction.counter(project.key("tenths")) % 10);
			if (studied(faction, project) >= project.study()) {
				setStage(faction, project, Project.KNOWN);
				context.chronicle(faction.type(), "chronicle.worldbetween.project.found." + project.id());
			}
			return; // one work at a time
		}
	}

	/** A found work waits for everything it takes; then the people pays and sets to work. */
	private static void gather(SimulationContext context, Faction faction, Project project) {
		if (!ready(context, faction, project) || !pay(faction, project)) {
			return;
		}
		if (project.days() == 0) {
			setStage(faction, project, Project.DONE);
			context.chronicle(faction.type(), "chronicle.worldbetween.project.done." + project.id());
			return;
		}
		setStage(faction, project, Project.MAKING);
		faction.setCounter(project.key("left"), project.days());
		context.chronicle(faction.type(), "chronicle.worldbetween.project.begun." + project.id());
	}

	private static void make(SimulationContext context, Faction faction, Project project) {
		faction.addCounter(project.key("left"), -1);
		if (daysLeft(faction, project) > 0) {
			return;
		}
		setStage(faction, project, Project.DONE);
		faction.setCounter(project.key("day"), context.day());
		context.chronicle(faction.type(), "chronicle.worldbetween.project.done." + project.id(), seat(faction, project) != null ? seat(faction, project).name() : "");
		Deeds.completed(context, faction, project);
	}

	/** The town a work stands in: the capital, or (for the Arki's tower) the colony highest in the sky. */
	public static Settlement seat(Faction faction, Project project) {
		long chosen = faction.counter(project.key("seat")) - 1;
		for (Settlement settlement : faction.settlements()) {
			if (settlement.id() == chosen) {
				return settlement;
			}
		}
		return null;
	}

	private static Settlement chooseSeat(Faction faction, Project project) {
		Settlement seat = switch (project) {
			case NIGHT_TOWER -> faction.settlements().stream()
					.max(Comparator.comparingInt((Settlement s) -> Arkis.isSky(s) ? 1 : 0).thenComparingInt(Settlement::population)).orElse(null);
			case QUAKE_CRYSTAL, MOROK -> faction.settlements().stream().filter(s -> !Kaments.isDeep(s))
					.max(Comparator.comparingInt(Settlement::population)).orElse(null);
			default -> faction.settlements().stream().filter(Settlement::isCapital).findFirst()
					.orElse(faction.settlements().isEmpty() ? null : faction.settlements().get(0));
		};
		if (seat != null) {
			faction.setCounter(project.key("seat"), seat.id() + 1);
		}
		return seat;
	}

	/** Whatever beyond resources a work waits on: the age, the army, the barons, the souls. */
	private static boolean ready(SimulationContext context, Faction faction, Project project) {
		return switch (project) {
			case MANTICORES -> faction.seekers().size() >= 2;
			case QUAKE_CRYSTAL -> faction.totalPopulation() >= 30;
			case COMET_STORM -> true;
			case HELL_CAMPAIGN -> faction.era() >= 3 && faction.soldiers() >= 60 && barons(context, faction);
			case NIGHT_TOWER -> faction.era() >= 2 && faction.settlements().size() >= 6;
			case MOROK -> faction.era() >= 2 && faction.totalPopulation() >= 40
					&& faction.settlements().stream().mapToLong(s -> s.counter("sculptors")).sum() >= 4;
		};
	}

	/**
	 * The barons are asked whether the kingdom goes to war in the Nether. The warlike and the bold say yes,
	 * the timid and the idle no, the rest as the mood takes them; a majority carries it. Asked again a month
	 * after a no.
	 */
	private static boolean barons(SimulationContext context, Faction faction) {
		if (faction.counter("hell.approved") > 0) {
			return true;
		}
		if (!faction.tryEvent("hell_vote", context.day(), VOTE_DAYS)) {
			return false;
		}
		Random random = context.random();
		int yes = 0;
		int no = 0;
		for (Noble noble : faction.livingNobles()) {
			int lean = 0;
			Ambition ambition = noble.ambition();
			if (ambition == Ambition.WAR || ambition == Ambition.VENGEANCE || ambition == Ambition.ARMY || ambition == Ambition.GLORY) {
				lean += 2;
			}
			if (ambition == Ambition.WEALTH || ambition == Ambition.ORDER) {
				lean -= 1;
			}
			if (noble.has(Trait.BRAVE) || noble.has(Trait.RECKLESS) || noble.has(Trait.REMEMBERS_CHAINS)) {
				lean += 1;
			}
			if (noble.has(Trait.COWARD) || noble.has(Trait.LAZY)) {
				lean -= 2;
			}
			if (lean + random.nextInt(3) - 1 > 0) {
				yes++;
			} else {
				no++;
			}
		}
		boolean carried = yes > no;
		context.chronicle(FactionType.NERIS, carried ? "chronicle.worldbetween.hell.vote_yes" : "chronicle.worldbetween.hell.vote_no", yes, no);
		if (carried) {
			faction.setCounter("hell.approved", 1);
		}
		return carried;
	}

	/** Takes what a work costs, if it is all there. */
	private static boolean pay(Faction faction, Project project) {
		Settlement seat = seat(faction, project) != null ? seat(faction, project) : chooseSeat(faction, project);
		if (seat == null) {
			return false;
		}
		switch (project) {
			case MANTICORES -> {
				return take(faction, Map.of(ResourceType.EQUIPMENT, 150L, ResourceType.FOOD, 600L, ResourceType.COINS, 300L));
			}
			case HELL_CAMPAIGN -> {
				if (!take(faction, Map.of(ResourceType.EQUIPMENT, 300L, ResourceType.FUEL, 400L, ResourceType.STONE, 600L, ResourceType.COINS, 500L))) {
					return false;
				}
				faction.setSoldiers(faction.soldiers() - 20); // the first host goes through with the portal
				return true;
			}
			case QUAKE_CRYSTAL -> {
				// The crystal is grown from the souls of four Kaments, given up of their own will.
				if (souls(faction) < 4 || !take(faction, Map.of(ResourceType.STONE, 800L, ResourceType.BRICKS, 400L,
						ResourceType.DIORITE, 400L, ResourceType.FLINT, 300L))) {
					return false;
				}
				giveSouls(faction, 4);
				return true;
			}
			case MOROK -> {
				if (souls(faction) < 6 || !take(faction, Map.of(ResourceType.STONE, 1500L, ResourceType.BRICKS, 800L, ResourceType.FLINT, 600L))) {
					return false;
				}
				// Six Kaments and two soul-sculptors pour themselves into the hero.
				giveSouls(faction, 6);
				int sculptors = 2;
				for (Settlement settlement : faction.settlements()) {
					while (sculptors > 0 && settlement.counter("sculptors") > 1) {
						settlement.addCounter("sculptors", -1);
						sculptors--;
					}
				}
				return true;
			}
			case COMET_STORM -> {
				return true; // found, it costs nothing until it is called down
			}
			case NIGHT_TOWER -> {
				long amethyst = faction.settlements().stream().mapToLong(s -> s.counter(Arkis.AMETHYST)).sum();
				long starlight = faction.settlements().stream().mapToLong(s -> s.counter(Arkis.STARLIGHT)).sum();
				if (amethyst < 1500 || starlight < 3000) {
					return false;
				}
				drain(faction, Arkis.AMETHYST, 1500);
				drain(faction, Arkis.STARLIGHT, 3000);
				return true;
			}
		}
		return false;
	}

	/** How many Kaments could give up their souls: all but three in every town. */
	private static int souls(Faction faction) {
		return faction.settlements().stream().mapToInt(s -> Math.max(0, s.population() - 3)).sum();
	}

	/** Takes the souls from the fullest towns first. */
	private static void giveSouls(Faction faction, int count) {
		int left = count;
		for (Settlement town : faction.settlements().stream().sorted(Comparator.comparingInt((Settlement s) -> -s.population())).toList()) {
			int share = Math.min(left, Math.max(0, town.population() - 3));
			town.setPopulation(town.population() - share);
			left -= share;
			if (left <= 0) {
				return;
			}
		}
	}

	private static boolean take(Faction faction, Map<ResourceType, Long> cost) {
		for (Map.Entry<ResourceType, Long> entry : cost.entrySet()) {
			if (faction.resource(entry.getKey()) < entry.getValue()) {
				return false;
			}
		}
		cost.forEach(faction::takeResource);
		return true;
	}

	/** Takes an amount from the colonies' stores, the richest first. */
	public static boolean drain(Faction faction, String store, long amount) {
		long total = faction.settlements().stream().mapToLong(s -> s.counter(store)).sum();
		if (total < amount) {
			return false;
		}
		long left = amount;
		for (Settlement colony : faction.settlements().stream().sorted(Comparator.comparingLong((Settlement s) -> -s.counter(store))).toList()) {
			long share = Math.min(left, colony.counter(store));
			colony.addCounter(store, -share);
			left -= share;
			if (left <= 0) {
				break;
			}
		}
		return true;
	}

	/** Weapons made go on being fed: manticores bred, the crystal charged; deeds done go on working. */
	private static void upkeep(SimulationContext context, Faction faction) {
		switch (faction.type()) {
			case NERIS -> NetherFront.simulateDay(context, faction);
			case KAMENT -> Morok.simulateDay(context, faction);
			case ARKI -> {
			}
		}
		if (done(faction, Project.MANTICORES) && faction.counter("manticores") < MAX_MANTICORES
				&& faction.resource(ResourceType.FOOD) >= 150 && faction.resource(ResourceType.EQUIPMENT) >= 10
				&& faction.tryEvent("breed_manticore", context.day(), BREED_DAYS)) {
			faction.takeResource(ResourceType.FOOD, 150);
			faction.takeResource(ResourceType.EQUIPMENT, 10);
			faction.addCounter("manticores", 1);
			context.chronicle(FactionType.NERIS, "chronicle.worldbetween.project.manticore_bred", faction.counter("manticores"));
		}
		if (done(faction, Project.QUAKE_CRYSTAL) && faction.counter("crystal.charges") < MAX_CHARGES
				&& faction.tryEvent("charge_crystal", context.day(), CHARGE_DAYS)) {
			faction.addCounter("crystal.charges", 1);
		}
	}

	/** For testing: finds, pays for and finishes a work at once. */
	public static void grant(SimulationContext context, Faction faction, Project project) {
		if (seat(faction, project) == null) {
			chooseSeat(faction, project);
		}
		faction.setCounter(project.key("study"), project.study());
		setStage(faction, project, Project.DONE);
		faction.setCounter(project.key("day"), context.day());
		if (project == Project.HELL_CAMPAIGN) {
			faction.setCounter("hell.approved", 1);
		}
		context.chronicle(faction.type(), "chronicle.worldbetween.project.done." + project.id(), seat(faction, project) != null ? seat(faction, project).name() : "");
		Deeds.completed(context, faction, project);
	}
}
