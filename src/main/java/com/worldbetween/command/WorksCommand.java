package com.worldbetween.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.projects.Morok;
import com.worldbetween.faction.projects.MorokPresence;
import com.worldbetween.faction.projects.NetherFront;
import com.worldbetween.faction.projects.NightSky;
import com.worldbetween.faction.projects.Project;
import com.worldbetween.faction.projects.Projects;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.town.Damage;
import com.worldbetween.war.Repairs;
import com.worldbetween.war.Weapons;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.Comparator;

/** {@code /wb works}: the great works of the peoples, how far each has got; and, for testing, hurrying them on. */
public final class WorksCommand {
	private WorksCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> node() {
		return CommandManager.literal("works").executes(WorksCommand::status)
				.then(CommandManager.literal("grant").requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.argument("project", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(Arrays.stream(Project.values()).map(Project::id), builder))
								.executes(WorksCommand::grant)))
				.then(CommandManager.literal("morok").requires(source -> source.hasPermissionLevel(2)).executes(WorksCommand::morok))
				.then(CommandManager.literal("quake").requires(source -> source.hasPermissionLevel(2)).executes(context -> strike(context, true)))
				.then(CommandManager.literal("storm").requires(source -> source.hasPermissionLevel(2)).executes(context -> strike(context, false)))
				.then(CommandManager.literal("damage").requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 90)).executes(WorksCommand::damage)));
	}

	private static int status(CommandContext<ServerCommandSource> context) {
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		for (Project project : Project.values()) {
			Faction faction = state.faction(project.owner());
			int stage = Projects.stage(faction, project);
			MutableText line = Text.empty().append(project.owner().displayName()).append(": ").append(project.displayName().copy().formatted(Formatting.YELLOW))
					.append(" — ").append(Text.translatable("project.worldbetween.stage." + stage));
			if (stage == Project.UNKNOWN) {
				line.append(" " + Projects.studied(faction, project) + "/" + project.study());
			} else if (stage == Project.MAKING) {
				line.append(Text.translatable("command.worldbetween.works.left", Projects.daysLeft(faction, project)));
			}
			Settlement seat = Projects.seat(faction, project);
			if (seat != null) {
				line.append(" · " + seat.name() + (seat.counter(Damage.DAMAGE) > 0 ? " (" + seat.counter(Damage.DAMAGE) + "%)" : ""));
				if (stage >= Project.MAKING && project != Project.COMET_STORM) {
					BlockPos at = com.worldbetween.faction.projects.WorksFeatures.origin(context.getSource().getServer().getOverworld(), faction, seat, project);
					if (at != null) {
						line.append(" @ " + at.getX() + " " + at.getY() + " " + at.getZ());
					}
				}
			}
			send(context, line.formatted(Formatting.GRAY));
		}
		Faction neris = state.faction(FactionType.NERIS);
		Faction kaments = state.faction(FactionType.KAMENT);
		send(context, Text.translatable("command.worldbetween.works.extras", neris.counter("manticores"), kaments.counter("crystal.charges"),
				Math.round(NetherFront.cleared(neris) * 100), NightSky.eternal(context.getSource().getServer()) ? "+" : "−",
				Morok.awake(kaments) ? Morok.position(kaments).getX() + ", " + Morok.position(kaments).getZ() : "—").formatted(Formatting.DARK_GRAY));
		return 1;
	}

	private static int grant(CommandContext<ServerCommandSource> context) {
		String id = StringArgumentType.getString(context, "project");
		Project project = Arrays.stream(Project.values()).filter(p -> p.id().equals(id)).findFirst().orElse(null);
		if (project == null) {
			context.getSource().sendError(Text.literal("?"));
			return 0;
		}
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		Faction faction = state.faction(project.owner());
		Projects.grant(new SimulationContext(context.getSource().getServer(), state), faction, project);
		if (project == Project.MANTICORES) {
			faction.setCounter("manticores", Math.max(3, faction.counter("manticores")));
		}
		if (project == Project.QUAKE_CRYSTAL) {
			faction.setCounter("crystal.charges", Projects.MAX_CHARGES);
		}
		state.markDirty();
		send(context, Text.empty().append(project.displayName()).append(" ✔"));
		return 1;
	}

	private static int morok(CommandContext<ServerCommandSource> context) {
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		BlockPos here = BlockPos.ofFloored(context.getSource().getPosition());
		MorokPresence.place(state.faction(FactionType.KAMENT), here.add(24, 0, 0));
		state.markDirty();
		send(context, Text.literal("Morok → " + (here.getX() + 24) + ", " + here.getZ()));
		return 1;
	}

	private static Settlement nearest(WorldBetweenState state, BlockPos here, FactionType not, Faction[] owner) {
		Settlement best = null;
		for (Faction faction : state.factions()) {
			if (faction.type() == not) {
				continue;
			}
			for (Settlement settlement : faction.settlements()) {
				if (best == null || settlement.pos().getSquaredDistance(here) < best.pos().getSquaredDistance(here)) {
					best = settlement;
					owner[0] = faction;
				}
			}
		}
		return best;
	}

	private static int strike(CommandContext<ServerCommandSource> context, boolean quake) {
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		BlockPos here = BlockPos.ofFloored(context.getSource().getPosition());
		Faction[] owner = new Faction[1];
		Settlement town = nearest(state, here, quake ? FactionType.KAMENT : FactionType.ARKI, owner);
		if (town == null) {
			return 0;
		}
		SimulationContext sim = new SimulationContext(context.getSource().getServer(), state);
		if (quake) {
			Weapons.quakeNow(sim, owner[0], town);
		} else {
			Weapons.queueStorm(context.getSource().getServer(), state.faction(FactionType.ARKI), owner[0].type(), town, sim.random());
		}
		state.markDirty();
		send(context, Text.literal((quake ? "Quake → " : "Storm → ") + town.name()));
		return 1;
	}

	private static int damage(CommandContext<ServerCommandSource> context) {
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		BlockPos here = BlockPos.ofFloored(context.getSource().getPosition());
		Faction[] owner = new Faction[1];
		Settlement town = nearest(state, here, null, owner);
		if (town == null) {
			return 0;
		}
		int amount = IntegerArgumentType.getInteger(context, "amount");
		town.setCounter(Damage.DAMAGE, 0);
		if (amount > 0) {
			Repairs.damage(town, amount);
		}
		state.markDirty();
		send(context, Text.literal(town.name() + ": " + town.counter(Damage.DAMAGE) + "%"));
		return 1;
	}

	private static void send(CommandContext<ServerCommandSource> context, Text text) {
		context.getSource().sendFeedback(() -> text, false);
	}
}
