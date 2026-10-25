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
import com.worldbetween.mine.MineManager;
import com.worldbetween.mine.MinePlan;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.sim.WorldSimulation;
import com.worldbetween.world.ChronicleEntry;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.Comparator;
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
				.then(CommandManager.literal("mine")
						.executes(WorldBetweenCommand::mine)
						.then(CommandManager.literal("map")
								.requires(source -> source.hasPermissionLevel(2))
								.then(CommandManager.argument("level", IntegerArgumentType.integer(1, 3))
										.executes(context -> mineMap(context, 32))
										.then(CommandManager.argument("radius", IntegerArgumentType.integer(4, 64))
												.executes(context -> mineMap(context, IntegerArgumentType.getInteger(context, "radius")))))))
				.then(CommandManager.literal("town").executes(WorldBetweenCommand::town))
				.then(CommandManager.literal("seekers")
						.executes(WorldBetweenCommand::seekers)
						.then(CommandManager.literal("scout")
								.requires(source -> source.hasPermissionLevel(2))
								.executes(WorldBetweenCommand::scoutMe)))
				.then(CommandManager.literal("court")
						.executes(WorldBetweenCommand::court)
						.then(CommandManager.literal("kill")
								.requires(source -> source.hasPermissionLevel(2))
								.executes(WorldBetweenCommand::killKing)))
				.then(CommandManager.literal("arrive")
						.requires(source -> source.hasPermissionLevel(2))
						.then(CommandManager.argument("faction", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(
										Arrays.stream(FactionType.values()).map(FactionType::id), builder))
								.executes(context -> arrive(context, false))
								.then(CommandManager.literal("here").executes(context -> arrive(context, true)))
								.then(CommandManager.literal("replay").executes(WorldBetweenCommand::replayArrival))))
				.then(CommandManager.literal("journeys").executes(WorldBetweenCommand::journeys))
				.then(CommandManager.literal("hold").executes(WorldBetweenCommand::hold))
				.then(CommandManager.literal("colony").executes(WorldBetweenCommand::colony))
				.then(CommandManager.literal("comet")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(context -> {
							if (!com.worldbetween.entity.arki.ArkiSky.fireNow(context.getSource().getServer(),
									BlockPos.ofFloored(context.getSource().getPosition()))) {
								context.getSource().sendError(Text.translatable("command.worldbetween.comet.none"));
								return 0;
							}
							return 1;
						}))
				.then(CommandManager.literal("council").executes(WorldBetweenCommand::council))
				.then(CommandManager.literal("quake")
						.requires(source -> source.hasPermissionLevel(2))
						.executes(context -> {
							com.worldbetween.entity.kament.KamentAwakening.quakeNow(context.getSource().getServer(), 1f);
							return 1;
						}))
				.then(CommandManager.literal("reputation")
						.executes(context -> reputation(context, FactionType.NERIS))
						.then(CommandManager.argument("value", IntegerArgumentType.integer(Faction.MIN_REPUTATION, Faction.MAX_REPUTATION))
								.requires(source -> source.hasPermissionLevel(2))
								.executes(context -> setReputation(context, FactionType.NERIS)))
						.then(CommandManager.argument("faction", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(
										Arrays.stream(FactionType.values()).map(FactionType::id), builder))
								.executes(context -> reputation(context, factionArgument(context)))
								.then(CommandManager.argument("value", IntegerArgumentType.integer(Faction.MIN_REPUTATION, Faction.MAX_REPUTATION))
										.requires(source -> source.hasPermissionLevel(2))
										.executes(context -> setReputation(context, factionArgument(context)))))));
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
			if (type == FactionType.KAMENT) {
				com.worldbetween.faction.kament.Kaments.halls(settlement).forEach((hall, count) ->
						buildings.append(hall.displayName()).append(" ×" + count + "  "));
				buildings.append(Text.literal("· sculptors " + settlement.counter("sculptors") + ", soul " + settlement.counter("soul")
						+ (com.worldbetween.faction.kament.Kaments.isDeep(settlement) ? ", deep" : "")).formatted(Formatting.DARK_GRAY));
			}
			if (type == FactionType.ARKI) {
				com.worldbetween.faction.arki.Arkis.halls(settlement).forEach((hall, count) ->
						buildings.append(hall.displayName()).append(" ×" + count + "  "));
				buildings.append(Text.literal("· " + (com.worldbetween.faction.arki.Arkis.isSky(settlement) ? "sky" : "sea")
						+ ", starlight " + settlement.counter("starlight")).formatted(Formatting.DARK_GRAY));
			}
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

	private static int hold(CommandContext<ServerCommandSource> context) {
		Vec3d from = context.getSource().getPosition();
		Faction faction = WorldBetweenState.get(context.getSource().getServer()).faction(FactionType.KAMENT);
		ServerWorld world = context.getSource().getServer().getOverworld();
		Settlement nearest = faction.settlements().stream()
				.min(Comparator.comparingDouble(s -> com.worldbetween.hold.HoldLayout.of(world, s).origin().getSquaredDistance(from)))
				.orElse(null);
		if (nearest == null) {
			context.getSource().sendError(Text.translatable("command.worldbetween.hold.none"));
			return 0;
		}
		com.worldbetween.hold.HoldLayout layout = com.worldbetween.hold.HoldLayout.of(world, nearest);
		BlockPos origin = layout.origin();
		BlockPos gate = layout.isDeep() ? origin : layout.local().world(0, 1, -layout.exit());
		com.worldbetween.faction.kament.Ancient ancient = com.worldbetween.faction.kament.Kaments.ancientOf(faction, nearest);
		send(context, Text.literal(nearest.name()).formatted(Formatting.GRAY).append(" ")
				.append(Text.translatable("tier.worldbetween.kament." + (layout.isDeep() ? "deep." : "") + nearest.tier()))
				.append(Text.translatable("command.worldbetween.hold.summary", nearest.population(), nearest.counter("sculptors"),
						nearest.counter("soul"), origin.getX(), origin.getY(), origin.getZ(), gate.getX(), gate.getY(), gate.getZ())));
		if (ancient != null) {
			send(context, Text.translatable("command.worldbetween.hold.ancient", ancient.name(), ancient.first().displayName(),
					ancient.second().displayName()));
		}
		MutableText halls = Text.translatable("command.worldbetween.hold.halls");
		com.worldbetween.faction.kament.Kaments.halls(nearest).forEach((hall, count) ->
				halls.append(Text.literal(" ▪ ").append(hall.displayName()).append(" ×" + count)));
		send(context, halls);
		if (nearest.counter("carving") > 0) {
			send(context, Text.translatable("command.worldbetween.hold.carving", nearest.counter("carving")));
		}
		return 1;
	}

	private static int colony(CommandContext<ServerCommandSource> context) {
		Vec3d from = context.getSource().getPosition();
		Faction faction = WorldBetweenState.get(context.getSource().getServer()).faction(FactionType.ARKI);
		ServerWorld world = context.getSource().getServer().getOverworld();
		send(context, Text.translatable("command.worldbetween.colony.comets", faction.comets().size()).formatted(Formatting.LIGHT_PURPLE));
		for (com.worldbetween.faction.arki.Comet comet : faction.comets()) {
			send(context, Text.literal(" ▪ " + comet.kind().name().toLowerCase() + " → " + comet.target().toShortString()).formatted(Formatting.DARK_GRAY));
		}
		Settlement nearest = faction.settlements().stream()
				.min(Comparator.comparingDouble(s -> com.worldbetween.colony.ColonyLayout.origin(world, s).getSquaredDistance(from)))
				.orElse(null);
		if (nearest == null) {
			return 0;
		}
		BlockPos origin = com.worldbetween.colony.ColonyLayout.origin(world, nearest);
		send(context, Text.literal(nearest.name()).formatted(Formatting.LIGHT_PURPLE).append(" ")
				.append(Text.translatable("tier.worldbetween.arki." + nearest.tier()))
				.append(Text.translatable("command.worldbetween.colony.summary", nearest.population(),
						com.worldbetween.faction.arki.Arkis.housing(nearest), origin.getX(), origin.getY(), origin.getZ(),
						nearest.counter("amethyst"), nearest.counter("sea"), nearest.counter("starlight"), nearest.counter("lore"))));
		MutableText halls = Text.translatable("command.worldbetween.hold.halls");
		com.worldbetween.faction.arki.Arkis.halls(nearest).forEach((hall, count) ->
				halls.append(Text.literal(" ▪ ").append(hall.displayName()).append(" ×" + count)));
		send(context, halls);
		return 1;
	}

	private static int council(CommandContext<ServerCommandSource> context) {
		Faction faction = WorldBetweenState.get(context.getSource().getServer()).faction(FactionType.KAMENT);
		com.worldbetween.faction.kament.Focus focus = com.worldbetween.faction.kament.Kaments.focus(faction);
		send(context, Text.translatable("command.worldbetween.council.header",
				focus == null ? Text.literal("—") : focus.displayName()).formatted(Formatting.GRAY));
		for (com.worldbetween.faction.kament.Ancient ancient : faction.ancients()) {
			Settlement seat = faction.settlements().stream().filter(s -> s.id() == ancient.seat()).findFirst().orElse(null);
			send(context, Text.literal(ancient.isAlive() ? " ▪ " : " † ").append(Text.literal(ancient.name()).formatted(Formatting.WHITE))
					.append(Text.literal(" — " + (seat == null ? "?" : seat.name()) + ", "))
					.append(ancient.first().displayName()).append(", ").append(ancient.second().displayName()));
		}
		for (com.worldbetween.faction.kament.Tunnel tunnel : faction.tunnels()) {
			send(context, Text.translatable("command.worldbetween.council.tunnel", tunnel.id(), (int) tunnel.dug(), (int) tunnel.length(),
					tunnel.end().getX(), tunnel.end().getY(), tunnel.end().getZ()));
		}
		return faction.ancients().size();
	}

	private static int replayArrival(CommandContext<ServerCommandSource> context) {
		Faction faction = WorldBetweenState.get(context.getSource().getServer()).faction(FactionType.NERIS);
		BlockPos portal = com.worldbetween.entity.Arrival.replay(context.getSource().getServer(), faction);
		if (portal == null) {
			context.getSource().sendError(Text.translatable("command.worldbetween.arrive.unavailable", FactionType.NERIS.displayName()));
			return 0;
		}
		send(context, Text.translatable("command.worldbetween.arrive.replay", portal.getX(), portal.getY(), portal.getZ()));
		return 1;
	}

	private static int journeys(CommandContext<ServerCommandSource> context) {
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		Faction faction = state.faction(FactionType.NERIS);
		double now = state.day() + (context.getSource().getServer().getOverworld().getTimeOfDay() % 24000L) / 24000.0;
		send(context, Text.translatable("command.worldbetween.journeys.header", faction.journeys().size()).formatted(Formatting.GOLD));
		Vec3d here = context.getSource().getPosition();
		for (com.worldbetween.faction.journeys.Journey journey : faction.journeys()) {
			Settlement from = com.worldbetween.faction.journeys.Journeys.settlement(faction, journey.from());
			Settlement to = com.worldbetween.faction.journeys.Journeys.settlement(faction, journey.to());
			BlockPos at = journey.position(now);
			String target = to != null ? to.name() : journey.townName() != null ? journey.townName() : "?";
			send(context, Text.translatable("command.worldbetween.journeys.line",
					Text.translatable("journey.worldbetween." + journey.kind().id()), from == null ? "?" : from.name(), target,
					at.getX(), at.getZ(), (int) Math.sqrt(at.getSquaredDistance(new BlockPos((int) here.x, 0, (int) here.z))),
					journey.isReturning() ? Text.translatable("command.worldbetween.journeys.returning") : Text.empty())
					.append(Text.literal(String.format(" [#%d %.0f/%.0f %s→%s]", journey.id(), journey.travelled(now), journey.length(),
							journey.route().get(0).toShortString(), journey.destination().toShortString()))
							.formatted(Formatting.DARK_GRAY)));
		}
		return faction.journeys().size();
	}

	private static int reputation(CommandContext<ServerCommandSource> context, FactionType type) throws CommandSyntaxException {
		net.minecraft.server.network.ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		Faction faction = WorldBetweenState.get(context.getSource().getServer()).faction(type);
		int value = faction.reputation(player.getUuid());
		send(context, Text.translatable("command.worldbetween.reputation", type.displayName(), value,
				Text.translatable("reputation.worldbetween." + com.worldbetween.town.TownSecurity.standing(faction, player.getUuid()))));
		return value;
	}

	private static int setReputation(CommandContext<ServerCommandSource> context, FactionType type) throws CommandSyntaxException {
		net.minecraft.server.network.ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		Faction faction = state.faction(type);
		int value = IntegerArgumentType.getInteger(context, "value");
		faction.changeReputation(player.getUuid(), value - faction.reputation(player.getUuid()));
		if (value >= 0) {
			faction.pardon(player.getUuid());
		}
		state.markDirty();
		return reputation(context, type);
	}

	private static int arrive(CommandContext<ServerCommandSource> context, boolean here) throws CommandSyntaxException {
		FactionType type = factionArgument(context);
		FactionBehavior behavior = FactionBehaviors.get(type);
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		Faction faction = state.faction(type);
		if (behavior == null || faction.hasArrived()) {
			context.getSource().sendError(Text.translatable("command.worldbetween.arrive.unavailable", type.displayName()));
			return 0;
		}
		SimulationContext simulation = new SimulationContext(context.getSource().getServer(), state);
		BlockPos center = BlockPos.ofFloored(context.getSource().getPosition());
		if (!(here ? behavior.arriveNear(simulation, faction, center, 140, 200) : behavior.arrive(simulation, faction))) {
			context.getSource().sendError(Text.translatable("command.worldbetween.arrive.no_site", type.displayName()));
			return 0;
		}
		state.markDirty();
		return 1;
	}

	private static MineManager.Site nearestMine(CommandContext<ServerCommandSource> context) {
		Vec3d from = context.getSource().getPosition();
		return MineManager.sites(WorldBetweenState.get(context.getSource().getServer())).stream()
				.min(Comparator.comparingDouble(site -> site.mine().entrance().getSquaredDistance(from)))
				.orElse(null);
	}

	private static int mine(CommandContext<ServerCommandSource> context) {
		MineManager.Site site = nearestMine(context);
		if (site == null) {
			context.getSource().sendError(Text.translatable("command.worldbetween.mine.none"));
			return 0;
		}
		ServerWorld world = context.getSource().getServer().getOverworld();
		MinePlan plan = site.plan();
		int progress = site.progress(world);
		BlockPos entrance = site.mine().entrance();
		BlockPos face = plan.faceAt(progress);
		send(context, Text.translatable("command.worldbetween.mine.header", site.owner().displayName(), site.settlement().name())
				.formatted(Formatting.GOLD));
		send(context, Text.translatable("command.worldbetween.mine.entrance", entrance.getX(), entrance.getY(), entrance.getZ()));
		send(context, Text.translatable("command.worldbetween.mine.progress", progress, site.mine().target(), plan.totalSteps(),
				plan.segments().size()));
		for (int level = 0; level < plan.levelCount(); level++) {
			int start = level == 0 ? 0 : plan.levelEnd(level - 1);
			int end = plan.levelEnd(level);
			int percent = (int) (100L * Math.max(0, Math.min(progress, end) - start) / Math.max(1, end - start));
			send(context, Text.translatable("command.worldbetween.mine.level", level + 1, plan.levelFloor(level), percent));
		}
		if (face != null) {
			send(context, Text.translatable("command.worldbetween.mine.face", face.getX(), face.getY(), face.getZ()));
		}
		site.mine().stops().forEach((index, step) -> {
			MinePlan.Segment segment = plan.segments().get(index);
			BlockPos at = segment.sliceCenter(step);
			send(context, Text.translatable("command.worldbetween.mine.stop", segment.kind().name(), at.getX(), at.getY(), at.getZ())
					.formatted(Formatting.GRAY));
		});
		return 1;
	}

	/** A top-down map of one mine level, read from the actual blocks in the world. */
	private static int mineMap(CommandContext<ServerCommandSource> context, int radius) {
		MineManager.Site site = nearestMine(context);
		if (site == null) {
			context.getSource().sendError(Text.translatable("command.worldbetween.mine.none"));
			return 0;
		}
		int level = IntegerArgumentType.getInteger(context, "level") - 1;
		ServerWorld world = context.getSource().getServer().getOverworld();
		BlockPos entrance = site.mine().entrance();
		int y = site.plan().levelFloor(level);
		StringBuilder map = new StringBuilder();
		for (int dz = -radius; dz <= radius; dz++) {
			map.append('\n');
			for (int dx = -radius; dx <= radius; dx++) {
				BlockPos pos = new BlockPos(entrance.getX() + dx, y, entrance.getZ() + dz);
				map.append(mapChar(world.getBlockState(pos), world.getBlockState(pos.up()), world.getBlockState(pos.down())));
			}
		}
		send(context, Text.translatable("command.worldbetween.mine.map", level + 1, y).formatted(Formatting.GOLD)
				.append(Text.literal(map.toString()).formatted(Formatting.WHITE)));
		return 1;
	}

	private static char mapChar(BlockState floor, BlockState head, BlockState below) {
		if (floor.isOf(Blocks.RAIL)) return '=';
		if (floor.isOf(Blocks.DARK_OAK_LOG)) return '#';
		if (floor.isOf(Blocks.CHEST) || floor.isOf(Blocks.BARREL)) return 'C';
		if (floor.isOf(Blocks.BLAST_FURNACE) || floor.isOf(Blocks.ANVIL) || floor.isOf(Blocks.SMITHING_TABLE) || floor.isOf(Blocks.CRAFTING_TABLE)) return 'F';
		if (floor.isOf(Blocks.LADDER)) return 'H';
		if (floor.isOf(Blocks.LANTERN) || floor.isOf(Blocks.WALL_TORCH)) return 'L';
		if (floor.isOf(Blocks.POLISHED_BLACKSTONE_BRICKS)) return 'B';
		if (floor.isAir() && below.isOf(Blocks.LAVA)) return '~';
		if (floor.isOf(Blocks.LAVA)) return '~';
		if (floor.isOf(Blocks.WATER)) return 'w';
		if (floor.isAir() || floor.isOf(Blocks.CAVE_AIR)) return head.isAir() ? '.' : ',';
		return ' ';
	}

	private static int court(CommandContext<ServerCommandSource> context) {
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		Faction faction = state.faction(FactionType.NERIS);
		long day = state.day();
		send(context, Text.translatable("command.worldbetween.court.header", faction.type().displayName(),
				faction.resource(ResourceType.COINS)).formatted(Formatting.GOLD));
		com.worldbetween.faction.court.Noble king = faction.king();
		if (king == null) {
			send(context, Text.translatable("command.worldbetween.court.no_king", faction.councilDay()).formatted(Formatting.RED));
		}
		for (Settlement settlement : faction.settlements()) {
			for (com.worldbetween.faction.court.Noble noble : faction.livingNobles()) {
				if (noble.seat() != settlement.id()) {
					continue;
				}
				MutableText line = Text.literal(noble == king ? " ♛ " : " ▪ ").formatted(noble == king ? Formatting.GOLD : Formatting.GRAY)
						.append(noble.displayName().formatted(noble == king ? Formatting.YELLOW : Formatting.WHITE))
						.append(Text.translatable("command.worldbetween.court.noble", noble.age(day), settlement.name(),
								noble.traits().get(0).displayName(), noble.traits().get(1).displayName(),
								noble.ambition().displayName(), noble.sphere().displayName()).formatted(Formatting.GRAY));
				if (king != null && noble != king) {
					line.append(Text.translatable("command.worldbetween.court.opinion",
							com.worldbetween.faction.court.Court.opinion(noble, king)).formatted(Formatting.DARK_GRAY));
				}
				send(context, line);
			}
		}
		return 1;
	}

	private static int seekers(CommandContext<ServerCommandSource> context) {
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		Faction faction = state.faction(FactionType.NERIS);
		ServerWorld world = context.getSource().getServer().getOverworld();
		double now = state.day() + (world.getTimeOfDay() % 24000L) / 24000.0;
		send(context, Text.translatable("command.worldbetween.seekers.header", faction.knownSites().size()).formatted(Formatting.GOLD));
		for (BlockPos site : faction.knownSites()) {
			send(context, Text.translatable("command.worldbetween.seekers.site", site.getX(), site.getZ()).formatted(Formatting.GRAY));
		}
		for (com.worldbetween.faction.seekers.Seeker seeker : faction.seekers()) {
			if (!seeker.isAlive()) {
				continue;
			}
			BlockPos at = seeker.position(now);
			String mission = seeker.mission() == null ? "idle" : seeker.mission().name().toLowerCase();
			send(context, Text.literal(" ▪ " + seeker.name() + " ").append(Text.translatable("command.worldbetween.seekers.mission." + mission))
					.append(Text.literal(String.format(" (%d, %d) → (%d, %d)", at.getX(), at.getZ(), seeker.target().getX(), seeker.target().getZ()))
							.formatted(Formatting.GRAY)));
		}
		return 1;
	}

	/** Sends an idle (or the first) seeker to scout the player running the command, for testing. */
	private static int scoutMe(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
		net.minecraft.server.network.ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		Faction faction = state.faction(FactionType.NERIS);
		ServerWorld world = context.getSource().getServer().getOverworld();
		double now = state.day() + (world.getTimeOfDay() % 24000L) / 24000.0;
		com.worldbetween.faction.seekers.Seeker seeker = faction.seekers().stream().filter(com.worldbetween.faction.seekers.Seeker::isAlive)
				.findFirst().orElse(null);
		if (seeker == null) {
			return 0;
		}
		BlockPos start = player.getBlockPos().add(48, 0, 0);
		seeker.send(com.worldbetween.faction.seekers.Seeker.Mission.SCOUT, start, player.getBlockPos(), now, -1, player.getUuid());
		state.markDirty();
		return 1;
	}

	private static int killKing(CommandContext<ServerCommandSource> context) {
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		boolean killed = com.worldbetween.faction.court.Court.killKing(
				new SimulationContext(context.getSource().getServer(), state), state.faction(FactionType.NERIS));
		state.markDirty();
		return killed ? 1 : 0;
	}

	private static int town(CommandContext<ServerCommandSource> context) {
		Vec3d from = context.getSource().getPosition();
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		Faction owner = null;
		Settlement nearest = null;
		for (Faction faction : state.factions()) {
			if (faction.type() != FactionType.NERIS) {
				continue;
			}
			for (Settlement settlement : faction.settlements()) {
				if (nearest == null || settlement.pos().getSquaredDistance(from) < nearest.pos().getSquaredDistance(from)) {
					nearest = settlement;
					owner = faction;
				}
			}
		}
		if (nearest == null) {
			context.getSource().sendError(Text.translatable("command.worldbetween.town.none"));
			return 0;
		}
		ServerWorld world = context.getSource().getServer().getOverworld();
		com.worldbetween.town.TownLayout layout = com.worldbetween.town.TownLayout.of(world, nearest);
		BlockPos center = layout.center();
		send(context, Text.literal(nearest.name()).formatted(Formatting.GOLD).append(" ")
				.append(Text.translatable("tier.worldbetween." + owner.type().id() + "." + nearest.tier()))
				.append(Text.translatable("command.worldbetween.town.summary", nearest.population(), center.getX(), center.getY(), center.getZ(),
						layout.streets().size(), layout.hallSide().asString())));
		for (com.worldbetween.faction.Plot plot : nearest.plots()) {
			com.worldbetween.town.TownLayout.Slot slot = layout.slot(plot.slot());
			if (slot == null) {
				continue;
			}
			send(context, Text.literal(" ▪ ").append(plot.type().displayName())
					.append(Text.literal(" L" + plot.level() + " (" + slot.x() + ", " + slot.z() + ")"
							+ (slot.buildable() ? "" : " ✗")).formatted(Formatting.GRAY)));
		}
		MutableText crews = Text.translatable("command.worldbetween.town.crews");
		for (Map.Entry<BuildingType, Integer> entry : nearest.staffed().entrySet()) {
			int built = nearest.buildingCount(entry.getKey());
			if (built > 0) {
				crews.append(" ").append(entry.getKey().displayName()).append(" " + entry.getValue() + "/" + built);
			}
		}
		send(context, crews);
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
