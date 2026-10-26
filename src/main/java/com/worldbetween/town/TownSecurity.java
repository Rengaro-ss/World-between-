package com.worldbetween.town;

import com.worldbetween.WorldBetween;
import com.worldbetween.config.WorldBetweenConfig;
import com.worldbetween.entity.NerisEntity;
import com.worldbetween.entity.NerisPresence;
import com.worldbetween.faction.BuildingType;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Plot;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.court.Court;
import com.worldbetween.faction.court.Noble;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who may go where in a town of Neris. Anyone in good standing may walk the streets and the market; a
 * stranger under a cloud is turned out; walled towns close to strangers at night; barracks and warehouses
 * are for the kingdom's own; and a baron, let alone the king, receives only those who have earned it.
 * Guards at the hall door bar the way, and whoever forces it anyway, or will not leave when told, has a
 * fight on their hands.
 */
public final class TownSecurity {
	private static final int LEAVE_TICKS = 100;
	private static final int DOOR_RANGE = 3;
	private static final int WARNINGS = 2;
	private static final int WARNING_MEMORY = 400;
	private static final int MAX_DEFENCE_PER_DAY = 5;

	/** Why the guards want a stranger gone, strictest first. */
	public enum Reason {
		THRONE("throne", 25), HALL("hall", 20), CLOSED("closed", 15), UNWELCOME("unwelcome", 15), CURFEW("curfew", 10);

		private final String id;
		private final int penalty;

		Reason(String id, int penalty) {
			this.id = id;
			this.penalty = penalty;
		}

		public String id() {
			return id;
		}

		boolean isIntrusion() {
			return this == THRONE || this == HALL || this == CLOSED;
		}
	}

	private static final class Watch {
		final Faction faction;
		final Settlement settlement;
		Reason reason;
		final long start;
		long deadline;
		long lastSeen;
		final ServerBossBar bar;
		List<UUID> guards = List.of();
		final List<UUID> borrowed = new ArrayList<>();

		Watch(Faction faction, Settlement settlement, Reason reason, long start, long deadline, ServerBossBar bar) {
			this.faction = faction;
			this.settlement = settlement;
			this.reason = reason;
			this.start = start;
			this.deadline = deadline;
			this.lastSeen = start;
			this.bar = bar;
		}
	}

	private static final Map<UUID, Watch> WATCHES = new HashMap<>();
	/** Times a stranger has been turned back at a hall door lately: count and last tick. */
	private static final Map<UUID, long[]> DOOR_WARNINGS = new HashMap<>();
	/** Hostile mobs a stranger has killed in a town today: day and count. */
	private static final Map<UUID, long[]> DEFENCE = new HashMap<>();

	private TownSecurity() {
	}

	public static void onServerTick(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		WorldBetweenState state = WorldBetweenState.get(server);
		Faction faction = state.faction(FactionType.NERIS);
		if (!faction.isActive()) {
			return;
		}
		long now = server.getTicks();
		boolean second = now % 20 == 0;
		if (now % 2 != 0) {
			return;
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			Settlement town = townAt(world, faction, player.getBlockPos());
			if (town != null) {
				door(world, faction, town, player, now);
			}
			if (!second) {
				continue;
			}
			Reason reason = town == null ? null : violation(world, faction, town, player);
			Watch watch = WATCHES.get(player.getUuid());
			if (watch != null) {
				update(player, watch, town, reason, state, now);
			} else if (reason != null) {
				start(world, player, faction, town, reason, now);
			} else if (town != null) {
				audience(world, faction, town, player, state.day());
			}
		}
	}

	/** The settlement whose town a position lies in, if any. */
	public static Settlement townAt(ServerWorld world, Faction faction, BlockPos pos) {
		for (Settlement settlement : faction.settlements()) {
			if (settlement.dimension() == World.OVERWORLD && settlement.pos().getSquaredDistance(pos.withY(settlement.pos().getY())) < 120 * 120
					&& TownLayout.of(world, settlement).contains(pos, settlement.tier())) {
				return settlement;
			}
		}
		return null;
	}

	/** How the kingdom regards a player, in words: from criminal to honoured guest. */
	public static String standing(Faction faction, UUID player) {
		WorldBetweenConfig config = config();
		int reputation = faction.reputation(player);
		if (faction.isCriminal(player)) {
			return "criminal";
		}
		if (reputation < config.townEntryReputation) {
			return "hostile";
		}
		if (reputation < config.curfewReputation) {
			return "stranger";
		}
		if (reputation < config.hallReputation) {
			return "known";
		}
		return reputation < config.throneReputation ? "trusted" : "honoured";
	}

	/** Whether the king sits in this town's hall. */
	private static boolean kingSeat(Faction faction, Settlement town) {
		Noble king = faction.king();
		return king != null && king.seat() == town.id();
	}

	private static int hallReputation(Faction faction, Settlement town) {
		return kingSeat(faction, town) ? config().throneReputation : config().hallReputation;
	}

	/** What, if anything, a player is doing wrong just by being where they are. */
	private static Reason violation(ServerWorld world, Faction faction, Settlement town, ServerPlayerEntity player) {
		UUID id = player.getUuid();
		if (faction.isCriminal(id)) {
			return null; // past warnings: the guards attack on sight
		}
		int reputation = faction.reputation(id);
		BlockPos pos = player.getBlockPos();
		TownLayout layout = TownLayout.of(world, town);
		TownLayout.Hall hall = layout.hall(town.tier(), town.isCapital());
		if (hall.contains(pos) && !world.isSkyVisible(pos.up()) && reputation < hallReputation(faction, town)) {
			return kingSeat(faction, town) ? Reason.THRONE : Reason.HALL;
		}
		if (reputation < config().friendlyReputation) {
			for (Plot plot : town.plots()) {
				if (plot.type() != BuildingType.BARRACKS && plot.type() != BuildingType.WAREHOUSE) {
					continue;
				}
				TownLayout.Slot slot = layout.slot(plot.slot());
				if (slot != null && Math.abs(pos.getX() - slot.x()) <= 4 && Math.abs(pos.getZ() - slot.z()) <= 4
						&& !world.isSkyVisible(pos.up())) {
					return Reason.CLOSED;
				}
			}
		}
		if (reputation < config().townEntryReputation) {
			return Reason.UNWELCOME;
		}
		if (town.tier() >= 2 && world.isNight() && reputation < config().curfewReputation) {
			return Reason.CURFEW;
		}
		return null;
	}

	private static int grace(Faction faction, Settlement town, Reason reason) {
		return switch (reason) {
			case THRONE, HALL, CLOSED -> config().intrusionGraceSeconds;
			case UNWELCOME -> Court.graceSeconds(faction, town, config().townGraceSeconds);
			case CURFEW -> Court.graceSeconds(faction, town, config().curfewGraceSeconds);
		};
	}

	private static void start(ServerWorld world, ServerPlayerEntity player, Faction faction, Settlement town, Reason reason, long now) {
		int seconds = grace(faction, town, reason);
		ServerBossBar bar = new ServerBossBar(Text.translatable("bossbar.worldbetween.town." + reason.id(), town.name()),
				reason.isIntrusion() ? BossBar.Color.RED : BossBar.Color.YELLOW, BossBar.Style.NOTCHED_10);
		bar.addPlayer(player);
		Watch watch = new Watch(faction, town, reason, now, now + seconds * 20L, bar);
		dispatch(world, watch, player);
		WATCHES.put(player.getUuid(), watch);
		player.sendMessage(Text.translatable("message.worldbetween.town." + reason.id(), town.name(), seconds)
				.formatted(reason.isIntrusion() ? Formatting.RED : Formatting.GOLD), false);
	}

	/** Guards come for the stranger: from the hall door for an intruder, from the nearest watch post otherwise. */
	private static void dispatch(ServerWorld world, Watch watch, ServerPlayerEntity player) {
		TownLayout layout = TownLayout.of(world, watch.settlement);
		Settlement town = watch.settlement;
		BlockPos from;
		if (watch.reason == Reason.THRONE || watch.reason == Reason.HALL) {
			from = layout.hall(town.tier(), town.isCapital()).outside(0, 1);
		} else {
			from = layout.center().up();
			int towers = Math.min(town.buildingCount(BuildingType.WATCHTOWER), layout.towerSpots(town.tier()).size());
			double best = player.getBlockPos().getSquaredDistance(from);
			for (int i = 0; i < towers; i++) {
				BlockPos spot = layout.towerSpots(town.tier()).get(i);
				BlockPos ground = spot.withY(Ground.floor(world, spot.getX(), spot.getZ()) + 1).north(2);
				if (ground.getSquaredDistance(player.getBlockPos()) < best) {
					best = ground.getSquaredDistance(player.getBlockPos());
					from = ground;
				}
			}
		}
		watch.guards = NerisPresence.dispatchGuards(world, from, player, "speech.worldbetween.guard." + watch.reason.id());
		for (NerisEntity guard : NerisPresence.doorGuards(world, faction(world), town)) {
			if (watch.reason == Reason.THRONE || watch.reason == Reason.HALL) {
				guard.setQuarry(player.getUuid());
				watch.borrowed.add(guard.getUuid());
			}
		}
	}

	private static void update(ServerPlayerEntity player, Watch watch, Settlement town, Reason reason, WorldBetweenState state, long now) {
		if (reason != null && town == watch.settlement) {
			watch.lastSeen = now;
			if (reason.ordinal() < watch.reason.ordinal()) {
				// Worse than before: the deadline comes sooner.
				watch.reason = reason;
				watch.deadline = Math.min(watch.deadline, now + grace(watch.faction, town, reason) * 20L);
				watch.bar.setName(Text.translatable("bossbar.worldbetween.town." + reason.id(), town.name()));
				watch.bar.setColor(reason.isIntrusion() ? BossBar.Color.RED : BossBar.Color.YELLOW);
				player.sendMessage(Text.translatable("message.worldbetween.town." + reason.id(), town.name(),
						(watch.deadline - now) / 20).formatted(Formatting.RED), false);
			}
		}
		if (now - watch.lastSeen > LEAVE_TICKS) {
			player.sendMessage(Text.translatable("message.worldbetween.town.left", watch.settlement.name()).formatted(Formatting.GRAY), false);
			clear(player);
			return;
		}
		long remaining = watch.deadline - now;
		if (remaining > 0) {
			watch.bar.setPercent(Math.max(0f, remaining / (float) (watch.deadline - watch.start)));
			return;
		}
		outlaw(player, watch.faction, watch.settlement, watch.reason, state);
		NerisPresence.linger(player.getServer(), watch.guards, 1200);
		watch.guards = List.of();
		clear(player);
	}

	/** The stranger would not listen: from now on the kingdom's guards treat them as a criminal. */
	private static void outlaw(ServerPlayerEntity player, Faction faction, Settlement town, Reason reason, WorldBetweenState state) {
		faction.declareCriminal(player.getUuid(), state.day());
		faction.changeReputation(player.getUuid(), -reason.penalty);
		state.markDirty();
		player.sendMessage(Text.translatable("message.worldbetween.outlaw", faction.type().displayName()).formatted(Formatting.RED), false);
		new SimulationContext(player.getServer(), state).chronicle(faction.type(),
				"chronicle.worldbetween.neris.outlaw_" + reason.id(), player.getName().getString(), town.name());
	}

	public static void onDisconnect(ServerPlayerEntity player) {
		clear(player);
	}

	private static void clear(ServerPlayerEntity player) {
		Watch watch = WATCHES.remove(player.getUuid());
		if (watch != null) {
			watch.bar.clearPlayers();
			ServerWorld world = player.getServerWorld();
			NerisPresence.recall(world, watch.guards);
			for (UUID id : watch.borrowed) {
				if (world.getEntity(id) instanceof NerisEntity guard) {
					guard.setQuarry(null);
				}
			}
		}
	}

	/**
	 * The guards at the hall door. A stranger who has not earned the right is turned back, twice with a
	 * warning; the third time they have chosen to fight.
	 */
	private static void door(ServerWorld world, Faction faction, Settlement town, ServerPlayerEntity player, long now) {
		TownLayout.Hall hall = TownLayout.of(world, town).hall(town.tier(), town.isCapital());
		BlockPos door = hall.door();
		Vec3d doorway = Vec3d.ofBottomCenter(door);
		if (Math.abs(player.getY() - doorway.y) > 2 || player.getPos().squaredDistanceTo(doorway.x, player.getY(), doorway.z) > 36) {
			return;
		}
		UUID id = player.getUuid();
		if (faction.isCriminal(id) || faction.reputation(id) >= hallReputation(faction, town)) {
			return;
		}
		List<NerisEntity> guards = NerisPresence.doorGuards(world, faction, town);
		if (guards.isEmpty()) {
			return; // nobody at the door to stop them
		}
		Direction out = hall.facing();
		// Distance from the door plane, outwards, and how far to the side of the doorway.
		double ahead = (player.getX() - doorway.x) * out.getOffsetX() + (player.getZ() - doorway.z) * out.getOffsetZ();
		Direction side = out.rotateYClockwise();
		double across = Math.abs((player.getX() - doorway.x) * side.getOffsetX() + (player.getZ() - doorway.z) * side.getOffsetZ());
		if (ahead > DOOR_RANGE || ahead < -1.5 || across > hall.doorHalf() + 1.5) {
			return;
		}
		long[] warnings = DOOR_WARNINGS.computeIfAbsent(id, k -> new long[]{0, 0});
		if (now - warnings[1] < 60) {
			// Just pushed back: keep them off the step, but do not count it twice.
			shove(player, out);
			return;
		}
		if (now - warnings[1] > WARNING_MEMORY) {
			warnings[0] = 0;
		}
		warnings[0]++;
		warnings[1] = now;
		NerisEntity guard = guards.get(0);
		guards.forEach(g -> g.getLookControl().lookAt(player));
		shove(player, out);
		guard.swingHand(net.minecraft.util.Hand.MAIN_HAND);
		boolean king = kingSeat(faction, town);
		if (warnings[0] <= WARNINGS) {
			String key = "speech.worldbetween.guard.door." + (king ? "king" : "baron") + (warnings[0] == WARNINGS ? ".last" : "");
			player.sendMessage(Text.empty().append(Text.translatable("entity.worldbetween.guard").formatted(Formatting.GOLD))
					.append(Text.literal(": ").formatted(Formatting.GRAY))
					.append(Text.translatable(key, hallReputation(faction, town)).formatted(Formatting.WHITE)), false);
			return;
		}
		// The third time is not a mistake.
		DOOR_WARNINGS.remove(id);
		player.sendMessage(Text.empty().append(Text.translatable("entity.worldbetween.guard").formatted(Formatting.GOLD))
				.append(Text.literal(": ").formatted(Formatting.GRAY))
				.append(Text.translatable("speech.worldbetween.guard.door.fight").formatted(Formatting.RED)), false);
		clear(player);
		outlaw(player, faction, town, king ? Reason.THRONE : Reason.HALL, WorldBetweenState.get(world.getServer()));
		guards.forEach(g -> g.setTarget(player));
	}

	private static void shove(ServerPlayerEntity player, Direction out) {
		player.setVelocity(out.getOffsetX() * 0.7, 0.3, out.getOffsetZ() * 0.7);
		player.velocityModified = true;
	}

	/** A guest in good standing walks into the hall: the lord receives them, once a day. */
	private static void audience(ServerWorld world, Faction faction, Settlement town, ServerPlayerEntity player, long day) {
		TownLayout.Hall hall = TownLayout.of(world, town).hall(town.tier(), town.isCapital());
		if (!hall.contains(player.getBlockPos()) || world.isSkyVisible(player.getBlockPos().up())) {
			return;
		}
		boolean king = kingSeat(faction, town);
		Noble lord = king ? faction.king() : faction.livingNobles().stream().filter(n -> n.seat() == town.id()).findFirst().orElse(null);
		NerisEntity present = NerisPresence.noble(world, faction, town, lord);
		if (lord == null || present == null || !faction.tryEvent("audience_" + player.getUuid() + "_" + town.id(), day, 1)) {
			return;
		}
		present.getLookControl().lookAt(player);
		player.sendMessage(Text.empty().append(present.getDisplayName().copy().formatted(Formatting.GOLD))
				.append(Text.literal(": ").formatted(Formatting.GRAY))
				.append(Text.translatable("speech.worldbetween." + (king ? "king" : "baron") + ".audience." + lord.ambition().id(),
						player.getName()).formatted(Formatting.WHITE)), false);
		// While the kingdom wars in the Nether, the king gives a friend his writ: the purge squads will let them be.
		if (king && com.worldbetween.faction.projects.Projects.done(faction, com.worldbetween.faction.projects.Project.HELL_CAMPAIGN)
				&& faction.reputation(player.getUuid()) >= 30
				&& !player.getInventory().contains(new net.minecraft.item.ItemStack(com.worldbetween.item.WorldBetweenItems.ROYAL_WRIT))) {
			player.giveItemStack(new net.minecraft.item.ItemStack(com.worldbetween.item.WorldBetweenItems.ROYAL_WRIT));
			player.sendMessage(Text.translatable("message.worldbetween.hell.writ_given").formatted(Formatting.GOLD), false);
		}
		if (king && !faction.hasEventHappened("audience_ever_" + player.getUuid())) {
			faction.tryEvent("audience_ever_" + player.getUuid(), day, Long.MAX_VALUE);
			faction.changeReputation(player.getUuid(), 5);
			new SimulationContext(world.getServer(), WorldBetweenState.get(world.getServer())).chronicle(faction.type(),
					"chronicle.worldbetween.neris.audience", player.getName().getString(), lord.chronicleName());
		}
	}

	/** A stranger who kills the monsters at a town's gates earns a little thanks. */
	public static void onKill(LivingEntity killed, DamageSource source) {
		if (!(killed instanceof HostileEntity) || !(source.getAttacker() instanceof ServerPlayerEntity player)
				|| player.getServerWorld() != player.getServer().getOverworld()) {
			return;
		}
		ServerWorld world = player.getServerWorld();
		WorldBetweenState state = WorldBetweenState.get(world.getServer());
		Faction faction = state.faction(FactionType.NERIS);
		if (!faction.isActive() || faction.isCriminal(player.getUuid()) || townAt(world, faction, killed.getBlockPos()) == null) {
			return;
		}
		long[] defence = DEFENCE.computeIfAbsent(player.getUuid(), k -> new long[]{state.day(), 0});
		if (defence[0] != state.day()) {
			defence[0] = state.day();
			defence[1] = 0;
		}
		if (defence[1]++ < MAX_DEFENCE_PER_DAY) {
			faction.changeReputation(player.getUuid(), 1);
			state.markDirty();
			if (defence[1] == 1) {
				player.sendMessage(Text.translatable("message.worldbetween.town.defence", faction.type().displayName())
						.formatted(Formatting.GREEN), true);
			}
		}
	}

	private static Faction faction(ServerWorld world) {
		return WorldBetweenState.get(world.getServer()).faction(FactionType.NERIS);
	}

	private static WorldBetweenConfig config() {
		return WorldBetween.config();
	}
}
