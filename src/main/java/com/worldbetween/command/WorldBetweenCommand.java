package com.worldbetween.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.worldbetween.faction.BuildingType;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionBehavior;
import com.worldbetween.faction.FactionBehaviors;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.sim.WorldSimulation;
import com.worldbetween.world.ChronicleEntry;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * /wb: inspect and drive the simulation. Mostly a debugging tool for now.
 */
public final class WorldBetweenCommand {
	private static final DynamicCommandExceptionType UNKNOWN_FACTION = new DynamicCommandExceptionType(
			id -> Text.translatable("command.worldbetween.unknown_faction", id));

	private WorldBetweenCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("wb")
				.then(CommandManager.literal("status").executes(WorldBetweenCommand::status))
				.then(CommandManager.literal("faction")
						.then(CommandManager.argument("faction", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(
										Arrays.stream(FactionType.values()).map(FactionType::id), builder))
								.executes(WorldBetweenCommand::faction)))
				.then(CommandManager.literal("chronicle")
						.executes(context -> chronicle(context, 10))
						.then(CommandManager.argument("count", IntegerArgumentType.integer(1, 100))
								.executes(context -> chronicle(context, IntegerArgumentType.getInteger(context, "count")))))
				.then(CommandManager.literal("simulate")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.argument("days", IntegerArgumentType.integer(1, 1000))
								.executes(WorldBetweenCommand::simulate)))
				.then(CommandManager.literal("arrive")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.argument("faction", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(
										Arrays.stream(FactionType.values()).map(FactionType::id), builder))
								.executes(WorldBetweenCommand::arrive))));
	}

	private static int status(CommandContext<ServerCommandSource> context) {
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		send(context, Text.translatable("command.worldbetween.status.header", state.day()).formatted(Formatting.GOLD));
		for (Faction faction : state.factions()) {
			MutableText line = Text.literal(" • ").append(faction.type().displayName()).append(": ");
			if (FactionBehaviors.get(faction.type()) == null) {
				line.append(Text.translatable("command.worldbetween.status.dormant").formatted(Formatting.DARK_GRAY));
			} else if (!faction.hasArrived()) {
				line.append(Text.translatable("command.worldbetween.status.not_arrived").formatted(Formatting.GRAY));
			} else {
				line.append(Text.translatable("command.worldbetween.status.active",
						faction.settlements().size(), faction.totalPopulation(), faction.soldiers(),
						eraName(faction)));
			}
			send(context, line);
		}
		return 1;
	}

	private static int faction(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		FactionType type = factionArgument(context);
		Faction faction = WorldBetweenState.get(context.getSource().getServer()).faction(type);
		send(context, type.displayName().formatted(Formatting.BOLD));
		if (!faction.hasArrived()) {
			send(context, Text.translatable("command.worldbetween.status.not_arrived").formatted(Formatting.GRAY));
			return 1;
		}
		send(context, Text.translatable("command.worldbetween.faction.summary",
				eraName(faction), faction.totalPopulation(), faction.soldiers()));

		MutableText resources = Text.translatable("command.worldbetween.faction.resources");
		for (ResourceType resource : ResourceType.values()) {
			resources.append(" ").append(resource.displayName()).append(" " + faction.resource(resource));
		}
		send(context, resources);

		for (Settlement settlement : faction.settlements()) {
			send(context, Text.literal(" ▪ ").append(Text.literal(settlement.name()).formatted(Formatting.YELLOW))
					.append(" ").append(Text.translatable("tier.worldbetween." + type.id() + "." + settlement.tier()))
					.append(Text.translatable("command.worldbetween.faction.settlement",
							settlement.population(), settlement.pos().getX(), settlement.pos().getY(), settlement.pos().getZ())
							.formatted(Formatting.GRAY)));
			MutableText buildings = Text.literal("    ");
			for (Map.Entry<BuildingType, Integer> entry : settlement.buildings().entrySet()) {
				buildings.append(entry.getKey().displayName()).append(" ×" + entry.getValue() + "  ");
			}
			if (settlement.project() != null) {
				buildings.append(Text.translatable("command.worldbetween.faction.building",
						settlement.project().displayName(), settlement.projectProgress(), settlement.project().buildDays())
						.formatted(Formatting.AQUA));
			}
			send(context, buildings);
		}
		return 1;
	}

	private static int chronicle(CommandContext<ServerCommandSource> context, int count) {
		List<ChronicleEntry> chronicle = WorldBetweenState.get(context.getSource().getServer()).chronicle();
		send(context, Text.translatable("command.worldbetween.chronicle.header").formatted(Formatting.GOLD));
		if (chronicle.isEmpty()) {
			send(context, Text.translatable("command.worldbetween.chronicle.empty").formatted(Formatting.GRAY));
		}
		chronicle.subList(Math.max(0, chronicle.size() - count), chronicle.size())
				.forEach(entry -> send(context, entry.toText()));
		return chronicle.size();
	}

	private static int simulate(CommandContext<ServerCommandSource> context) {
		int days = IntegerArgumentType.getInteger(context, "days");
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		long start = System.nanoTime();
		for (int i = 0; i < days; i++) {
			WorldSimulation.simulateDay(context.getSource().getServer(), state);
		}
		long millis = (System.nanoTime() - start) / 1_000_000;
		context.getSource().sendFeedback(() -> Text.translatable("command.worldbetween.simulate.done", days, millis), true);
		return days;
	}

	private static int arrive(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		FactionType type = factionArgument(context);
		FactionBehavior behavior = FactionBehaviors.get(type);
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		Faction faction = state.faction(type);
		if (behavior == null || faction.hasArrived()) {
			context.getSource().sendError(Text.translatable("command.worldbetween.arrive.unavailable", type.displayName()));
			return 0;
		}
		if (!behavior.arrive(new SimulationContext(context.getSource().getServer(), state), faction)) {
			context.getSource().sendError(Text.translatable("command.worldbetween.arrive.no_site", type.displayName()));
			return 0;
		}
		state.markDirty();
		return 1;
	}

	private static FactionType factionArgument(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		String id = StringArgumentType.getString(context, "faction");
		FactionType type = FactionType.byId(id);
		if (type == null) {
			throw UNKNOWN_FACTION.create(id);
		}
		return type;
	}

	private static Text eraName(Faction faction) {
		return Text.translatable("era.worldbetween." + faction.type().id() + "." + faction.era());
	}

	private static void send(CommandContext<ServerCommandSource> context, Text text) {
		context.getSource().sendFeedback(() -> text, false);
	}
}
