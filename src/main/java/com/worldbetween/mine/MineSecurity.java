package com.worldbetween.mine;

import com.worldbetween.WorldBetween;
import com.worldbetween.config.WorldBetweenConfig;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.item.Ownership;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mine guards. Miners ignore strangers, but once one is noticed, guards set out from the shaft and the
 * stranger has a few minutes to leave. Whoever is still inside when they arrive is declared a criminal.
 */
public final class MineSecurity {
	private static final int INTERVAL = 20;
	/** Ticks a watched player must stay out of the mine before the guards give up. */
	private static final int LEAVE_TICKS = 100;
	private static final int CRIMINAL_REPUTATION_LOSS = 25;
	private static final int THEFT_MESSAGE_COOLDOWN = 600;

	private static final Map<UUID, Watch> WATCHES = new HashMap<>();
	/** Highest count of each faction's goods each player has carried, so moving loot around is not theft twice. */
	private static final Map<UUID, EnumMap<FactionType, Integer>> CARRIED = new HashMap<>();
	private static final Map<UUID, Long> THEFT_MESSAGES = new HashMap<>();

	private static final class Watch {
		final MineManager.Site site;
		final long start;
		final long deadline;
		long lastInside;
		final ServerBossBar bar;
		java.util.List<java.util.UUID> guards = java.util.List.of();

		Watch(MineManager.Site site, long start, long deadline, ServerBossBar bar) {
			this.site = site;
			this.start = start;
			this.deadline = deadline;
			this.lastInside = start;
			this.bar = bar;
		}
	}

	private MineSecurity() {
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % INTERVAL != 0) {
			return;
		}
		WorldBetweenState state = WorldBetweenState.get(server);
		long now = server.getTicks();
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			checkTheft(player, state, now);
			if (player.isSpectator() || player.isCreative()) {
				continue;
			}
			ServerWorld world = player.getServerWorld();
			MineManager.Site site = MineManager.siteContaining(world, player.getBlockPos());
			Watch watch = WATCHES.get(player.getUuid());
			if (watch != null) {
				updateWatch(player, watch, site, state, now);
			} else if (site != null) {
				Faction faction = state.faction(site.owner());
				if (!faction.isCriminal(player.getUuid())
						&& faction.reputation(player.getUuid()) < config().friendlyReputation
						&& world.random.nextFloat() < config().noticeChancePerSecond) {
					notice(player, site, "trespass");
				}
			}
		}
	}

	private static void updateWatch(ServerPlayerEntity player, Watch watch, MineManager.Site site, WorldBetweenState state, long now) {
		if (site != null && site.key().equals(watch.site.key())) {
			watch.lastInside = now;
		}
		Text factionName = watch.site.owner().displayName();
		if (now - watch.lastInside > LEAVE_TICKS) {
			player.sendMessage(Text.translatable("message.worldbetween.left", factionName).formatted(Formatting.GRAY));
			clear(player);
			return;
		}
		long remaining = watch.deadline - now;
		if (remaining > 0) {
			watch.bar.setPercent(Math.max(0f, remaining / (float) (watch.deadline - watch.start)));
			return;
		}
		Faction faction = state.faction(watch.site.owner());
		faction.declareCriminal(player.getUuid(), state.day());
		faction.changeReputation(player.getUuid(), -CRIMINAL_REPUTATION_LOSS);
		state.markDirty();
		player.sendMessage(Text.translatable("message.worldbetween.criminal", factionName).formatted(Formatting.RED));
		// The guards are here now, and the stranger is a criminal: they stay to deal with it.
		com.worldbetween.entity.NerisPresence.linger(player.getServer(), watch.guards, 1200);
		watch.guards = java.util.List.of();
		new SimulationContext(player.getServer(), state).chronicle(watch.site.owner(),
				"chronicle.worldbetween." + watch.site.owner().id() + ".criminal",
				player.getName().getString(), watch.site.settlement().name());
		clear(player);
	}

	/** The miners have noticed the player: guards set out from the shaft. */
	public static void notice(ServerPlayerEntity player, MineManager.Site site, String reason) {
		if (WATCHES.containsKey(player.getUuid()) || player.isCreative() || player.isSpectator()) {
			return;
		}
		Faction faction = WorldBetweenState.get(player.getServer()).faction(site.owner());
		if (faction.isCriminal(player.getUuid())) {
			return; // already wanted; guards will deal with criminals once they exist as creatures
		}
		int seconds = com.worldbetween.faction.court.Court.graceSeconds(faction, site.settlement(), config().trespassGraceSeconds);
		ServerBossBar bar = new ServerBossBar(Text.translatable("bossbar.worldbetween.guards", site.owner().displayName()),
				BossBar.Color.RED, BossBar.Style.NOTCHED_10);
		bar.addPlayer(player);
		long now = player.getServer().getTicks();
		Watch watch = new Watch(site, now, now + seconds * 20L, bar);
		watch.guards = com.worldbetween.entity.NerisPresence.dispatchMineGuards(player.getServerWorld(), site, player);
		WATCHES.put(player.getUuid(), watch);
		player.sendMessage(Text.translatable("message.worldbetween.noticed." + reason,
				site.owner().displayName(), Math.max(1, Math.round(seconds / 60f))).formatted(Formatting.GOLD));
	}

	/** Breaking a mine's furnishings is vandalism, and the miners notice. */
	public static void onBlockBroken(ServerPlayerEntity player, ServerWorld world, BlockPos pos, net.minecraft.block.BlockState state) {
		MineManager.Site site = MineManager.siteOfFurnishing(world, pos, state);
		if (site == null) {
			return;
		}
		WorldBetweenState worldState = WorldBetweenState.get(world.getServer());
		worldState.faction(site.owner()).changeReputation(player.getUuid(), -1);
		worldState.markDirty();
		notice(player, site, "vandalism");
	}

	public static void onDisconnect(ServerPlayerEntity player) {
		clear(player);
	}

	private static void clear(ServerPlayerEntity player) {
		Watch watch = WATCHES.remove(player.getUuid());
		if (watch != null) {
			watch.bar.clearPlayers();
			com.worldbetween.entity.NerisPresence.recall(player.getServerWorld(), watch.guards);
		}
	}

	/** Picking up a faction's goods is theft; if it happens in their mine, the miners see it. */
	private static void checkTheft(ServerPlayerEntity player, WorldBetweenState state, long now) {
		EnumMap<FactionType, Integer> counts = new EnumMap<>(FactionType.class);
		PlayerInventory inventory = player.getInventory();
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			FactionType owner = Ownership.ownerOf(stack);
			if (owner != null) {
				counts.merge(owner, stack.getCount(), Integer::sum);
			}
		}
		EnumMap<FactionType, Integer> carried = CARRIED.get(player.getUuid());
		if (carried == null) {
			CARRIED.put(player.getUuid(), counts);
			return;
		}
		for (Map.Entry<FactionType, Integer> entry : counts.entrySet()) {
			FactionType owner = entry.getKey();
			int stolen = entry.getValue() - carried.getOrDefault(owner, 0);
			if (stolen <= 0) {
				continue;
			}
			carried.put(owner, entry.getValue());
			state.faction(owner).changeReputation(player.getUuid(), -Math.min(10, stolen));
			state.markDirty();
			Long last = THEFT_MESSAGES.get(player.getUuid());
			if (last == null || now - last > THEFT_MESSAGE_COOLDOWN) {
				THEFT_MESSAGES.put(player.getUuid(), now);
				player.sendMessage(Text.translatable("message.worldbetween.theft", owner.displayName()).formatted(Formatting.YELLOW));
			}
			MineManager.Site site = MineManager.siteContaining(player.getServerWorld(), player.getBlockPos());
			if (site != null && site.owner() == owner) {
				notice(player, site, "theft");
			}
		}
	}

	private static WorldBetweenConfig config() {
		return WorldBetween.config();
	}
}
