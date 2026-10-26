package com.worldbetween.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.war.Battlefield;
import com.worldbetween.war.War;
import com.worldbetween.war.WarSimulation;
import com.worldbetween.war.WarState;
import com.worldbetween.war.Warband;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** {@code /wb war}: how hot each war runs, who is in the field, where they fought; and, for testing, setting it all up. */
public final class WarCommand {
	private WarCommand() {
	}

	public static LiteralArgumentBuilder<ServerCommandSource> node() {
		return CommandManager.literal("war").executes(WarCommand::status)
				.then(CommandManager.literal("bands").executes(WarCommand::bands))
				.then(CommandManager.literal("fields").executes(WarCommand::fields))
				.then(CommandManager.literal("set").requires(source -> source.hasPermissionLevel(2))
						.then(faction("a").then(faction("b").then(CommandManager.argument("level", IntegerArgumentType.integer(1, War.MAX_LEVEL))
								.executes(WarCommand::set)))))
				.then(CommandManager.literal("clash").requires(source -> source.hasPermissionLevel(2))
						.then(faction("a").then(faction("b").executes(context -> clash(context, 4, 4))
								.then(CommandManager.argument("na", IntegerArgumentType.integer(1, 40))
										.then(CommandManager.argument("nb", IntegerArgumentType.integer(1, 40))
												.executes(context -> clash(context, IntegerArgumentType.getInteger(context, "na"),
														IntegerArgumentType.getInteger(context, "nb"))))))))
				.then(CommandManager.literal("clear").requires(source -> source.hasPermissionLevel(2)).executes(WarCommand::clear))
				.then(CommandManager.literal("field").requires(source -> source.hasPermissionLevel(2))
						.then(faction("a").then(faction("b").then(CommandManager.argument("fallen", IntegerArgumentType.integer(1, 60))
								.executes(WarCommand::field)))));
	}

	private static RequiredArgumentBuilder<ServerCommandSource, String> faction(String name) {
		return CommandManager.argument(name, StringArgumentType.word())
				.suggests((context, builder) -> CommandSource.suggestMatching(Arrays.stream(FactionType.values()).map(FactionType::id), builder));
	}

	private static FactionType type(CommandContext<ServerCommandSource> context, String name) {
		FactionType type = FactionType.byId(StringArgumentType.getString(context, name));
		if (type == null) {
			context.getSource().sendError(Text.translatable("command.worldbetween.faction.unknown", StringArgumentType.getString(context, name)));
		}
		return type;
	}

	private static int status(CommandContext<ServerCommandSource> context) {
		WarState wars = WorldBetweenState.get(context.getSource().getServer()).wars();
		for (War war : wars.wars()) {
			send(context, Text.empty().append(war.a().displayName()).append(" — ").append(war.b().displayName()).append(": ")
					.append(War.levelName(war.level()).copy().formatted(war.level() >= 3 ? Formatting.RED : war.level() == 2 ? Formatting.GOLD : Formatting.YELLOW))
					.append(Text.translatable("command.worldbetween.war.line", (int) war.tension(), war.battles(),
							war.fallen(war.a()), war.fallen(war.b()), war.townsSacked()).formatted(Formatting.GRAY)));
		}
		BlockPos here = BlockPos.ofFloored(context.getSource().getPosition());
		double now = WorldBetweenState.get(context.getSource().getServer()).day();
		wars.bands().stream().min(Comparator.comparingDouble(b -> WarSimulation.distance(b.position(now), here))).ifPresent(band ->
				send(context, Text.translatable("command.worldbetween.war.nearest_band", band.faction().displayName(),
						Text.translatable("war.worldbetween.band." + band.kind().id()), band.fighters(), band.position(now).getX(),
						band.position(now).getZ(), (int) WarSimulation.distance(band.position(now), here)).formatted(Formatting.GRAY)));
		wars.fields().stream().min(Comparator.comparingDouble(f -> WarSimulation.distance(f.pos(), here))).ifPresent(field ->
				send(context, Text.translatable("command.worldbetween.war.nearest_field", field.pos().getX(), field.pos().getZ(),
						(int) WarSimulation.distance(field.pos(), here), field.fallen()).formatted(Formatting.GRAY)));
		send(context, Text.translatable("command.worldbetween.war.totals", wars.bands().size(), wars.fields().size()).formatted(Formatting.DARK_GRAY));
		return 1;
	}

	private static int bands(CommandContext<ServerCommandSource> context) {
		WarState wars = WorldBetweenState.get(context.getSource().getServer()).wars();
		double now = WorldBetweenState.get(context.getSource().getServer()).day();
		for (Warband band : wars.bands()) {
			BlockPos at = band.position(now);
			send(context, Text.literal(" ▪ #" + band.id() + " ").append(band.faction().displayName()).append(" ")
					.append(Text.translatable("war.worldbetween.band." + band.kind().id())).append(" → ").append(band.enemy().displayName())
					.append(Text.literal(" ×" + band.fighters() + " " + band.state().name().toLowerCase() + " (" + at.getX() + ", " + at.getZ() + ")"))
					.formatted(Formatting.GRAY));
		}
		return wars.bands().size();
	}

	private static int fields(CommandContext<ServerCommandSource> context) {
		List<Battlefield> fields = WorldBetweenState.get(context.getSource().getServer()).wars().fields();
		for (Battlefield field : fields.subList(Math.max(0, fields.size() - 12), fields.size())) {
			send(context, Text.literal(" ▪ #" + field.id() + " " + field.kind().name().toLowerCase() + ", day " + field.day() + " ")
					.append(field.a().displayName()).append(" " + field.fallenA() + " / ").append(field.b().displayName())
					.append(" " + field.fallenB() + " (" + field.pos().getX() + ", " + field.pos().getZ() + ")").formatted(Formatting.GRAY));
		}
		return fields.size();
	}

	private static int set(CommandContext<ServerCommandSource> context) {
		FactionType a = type(context, "a");
		FactionType b = type(context, "b");
		if (a == null || b == null || a == b) {
			return 0;
		}
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		War war = state.wars().war(a, b);
		int level = IntegerArgumentType.getInteger(context, "level");
		war.setLevel(level, state.day());
		war.setTension(new double[]{5, 30, 55, 80}[level - 1]);
		state.markDirty();
		send(context, Text.empty().append(a.displayName()).append(" — ").append(b.displayName()).append(": ").append(War.levelName(level)));
		return 1;
	}

	/** Two hostile patrols, waiting thirty blocks apart either side of the player. */
	private static int clear(CommandContext<ServerCommandSource> context) {
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		int count = state.wars().bands().size();
		state.wars().bands().clear();
		state.markDirty();
		send(context, Text.literal("Bands cleared: " + count));
		return count;
	}

	private static int clash(CommandContext<ServerCommandSource> context, int na, int nb) {
		FactionType a = type(context, "a");
		FactionType b = type(context, "b");
		if (a == null || b == null || a == b) {
			return 0;
		}
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		BlockPos here = BlockPos.ofFloored(context.getSource().getPosition());
		if (state.wars().level(a, b) == 0) {
			state.wars().war(a, b).setLevel(1, state.day());
		}
		double now = com.worldbetween.sim.Clock.now(context.getSource().getServer());
		int first = place(state, a, b, here.add(-15, 0, 0), now, na);
		int second = place(state, b, a, here.add(15, 0, 0), now, nb);
		state.markDirty();
		send(context, Text.translatable("command.worldbetween.war.clash").append(" #" + first + " #" + second));
		return 1;
	}

	/** A test band: it belongs to no town, so whoever survives is not added to anyone's people. */
	private static int place(WorldBetweenState state, FactionType side, FactionType enemy, BlockPos at, double now, int fighters) {
		Warband band = new Warband(state.wars().nextBandId(), side, fighters > 4 ? Warband.Kind.ARMY : Warband.Kind.PATROL, Warband.TEST,
				at, enemy, -1, at, fighters, now);
		band.stationAt(at, (long) now + 1);
		if (side == FactionType.NERIS) {
			band.setBeasts((int) Math.min(2, state.faction(side).counter("manticores")));
		}
		state.wars().bands().add(band);
		return band.id();
	}

	private static int field(CommandContext<ServerCommandSource> context) {
		FactionType a = type(context, "a");
		FactionType b = type(context, "b");
		if (a == null || b == null || a == b) {
			return 0;
		}
		WorldBetweenState state = WorldBetweenState.get(context.getSource().getServer());
		int fallen = IntegerArgumentType.getInteger(context, "fallen");
		BlockPos here = BlockPos.ofFloored(context.getSource().getPosition());
		state.wars().addField(fallen > 20 ? Battlefield.Kind.BATTLE : Battlefield.Kind.SKIRMISH, here, state.day(), a, (fallen + 1) / 2, b, fallen / 2);
		state.markDirty();
		send(context, Text.translatable("command.worldbetween.war.field", here.getX(), here.getZ()));
		return 1;
	}

	private static void send(CommandContext<ServerCommandSource> context, Text text) {
		context.getSource().sendFeedback(() -> text, false);
	}
}
