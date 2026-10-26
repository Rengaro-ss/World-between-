package com.worldbetween.faction.projects;

import com.worldbetween.entity.NerisEntity;
import com.worldbetween.entity.NerisRole;
import com.worldbetween.entity.WorldBetweenEntities;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.war.WarPresence;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The kingdom's war in the Nether, once the Great Portal stands. On the far side of the portal the kingdom
 * holds a bridgehead; from it purge squads run out and fall on everything alive: the beasts of the Nether,
 * and any stranger who came without the king's writ. Day by day the Nether is cleared, and fewer of its
 * creatures come back. A broken portal stops it all until the builders have mended it.
 */
public final class NetherFront {
	private static final int SQUAD = 5;
	private static final int SQUAD_EVERY_TICKS = 2400;
	private static final double FRONT = 420;
	private static final double LEAVE = 160;
	/** At most this share of the Nether's creatures fails to spawn once it has been cleared for long enough. */
	private static final double MAX_CLEARED = 0.6;
	private static final Map<UUID, List<UUID>> SQUADS = new HashMap<>();
	private static final Map<UUID, Long> LAST_SQUAD = new HashMap<>();

	private NetherFront() {
	}

	public static boolean active(Faction neris) {
		return WorksFeatures.working(neris, Project.HELL_CAMPAIGN);
	}

	/** Where the bridgehead stands in the Nether: opposite the Great Portal, as portals are. */
	public static BlockPos bridgehead(MinecraftServer server, Faction neris) {
		Settlement seat = Projects.seat(neris, Project.HELL_CAMPAIGN);
		if (seat == null) {
			return null;
		}
		BlockPos portal = WorksFeatures.origin(server.getOverworld(), neris, seat, Project.HELL_CAMPAIGN);
		return portal == null ? null : new BlockPos(Math.floorDiv(portal.getX(), 8), 70, Math.floorDiv(portal.getZ(), 8));
	}

	/** A day of the war in the Nether: ground cleared, soldiers lost, spoils sent home. */
	public static void simulateDay(SimulationContext context, Faction neris) {
		if (!active(neris)) {
			return;
		}
		neris.addCounter("hell.cleared", 1);
		neris.addResource(ResourceType.METAL, 3);
		if (context.day() % 3 == 0 && neris.soldiers() > 0) {
			neris.setSoldiers(neris.soldiers() - 1);
		}
		if (neris.counter("hell.cleared") % 50 == 0) {
			context.chronicle(FactionType.NERIS, "chronicle.worldbetween.hell.cleared", Math.round(cleared(neris) * 100));
		}
	}

	/** How much of the Nether the kingdom has cleared: the share of its creatures that no longer come. */
	public static double cleared(Faction neris) {
		return Math.min(MAX_CLEARED, neris.counter("hell.cleared") / 600.0);
	}

	/** Fewer of the Nether's creatures come into the world the more of it the kingdom has cleared. */
	public static void onEntityLoad(Entity entity, ServerWorld world) {
		if (world.getRegistryKey() != World.NETHER || entity.age > 0 || !(entity instanceof MobEntity mob) || !(entity instanceof Monster)
				|| mob.isPersistent() || mob instanceof NerisEntity) {
			return;
		}
		Faction neris = WorldBetweenState.get(world.getServer()).faction(FactionType.NERIS);
		if (active(neris) && world.random.nextDouble() < cleared(neris)) {
			entity.discard();
		}
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % 40 != 0) {
			return;
		}
		ServerWorld nether = server.getWorld(World.NETHER);
		Faction neris = WorldBetweenState.get(server).faction(FactionType.NERIS);
		if (nether == null || !active(neris)) {
			return;
		}
		BlockPos head = bridgehead(server, neris);
		if (head == null) {
			return;
		}
		if (neris.counter("hell.outpost") == 0) {
			// Built straight away, before anyone steps through, so the portal leads to it and nowhere else.
			nether.getChunk(head.getX() >> 4, head.getZ() >> 4);
			buildBridgehead(nether, head);
			neris.setCounter("hell.outpost", 1);
			WorldBetweenState.get(server).markDirty();
		}
		long now = server.getTicks();
		for (ServerPlayerEntity player : nether.getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			double distance = Math.sqrt(player.getBlockPos().getSquaredDistance(head));
			List<UUID> squad = SQUADS.computeIfAbsent(player.getUuid(), k -> new ArrayList<>());
			squad.removeIf(id -> {
				Entity member = nether.getEntity(id);
				if (member == null || !member.isAlive()) {
					WarPresence.release(id);
					return true;
				}
				if (member.squaredDistanceTo(player) > LEAVE * LEAVE) {
					member.discard();
					WarPresence.release(id);
					return true;
				}
				return false;
			});
			if (distance < FRONT && squad.isEmpty() && now - LAST_SQUAD.getOrDefault(player.getUuid(), -SQUAD_EVERY_TICKS * 1L) > SQUAD_EVERY_TICKS) {
				LAST_SQUAD.put(player.getUuid(), now);
				BlockPos from = distance < 60 ? head.up() : ground(nether, player.getBlockPos().add(
						(int) Math.signum(head.getX() - player.getX()) * 24, 0, (int) Math.signum(head.getZ() - player.getZ()) * 24));
				if (from != null) {
					spawnSquad(nether, from, player, squad);
				}
			}
		}
	}

	private static void spawnSquad(ServerWorld nether, BlockPos at, ServerPlayerEntity player, List<UUID> squad) {
		boolean writ = player.getInventory().contains(new net.minecraft.item.ItemStack(com.worldbetween.item.WorldBetweenItems.ROYAL_WRIT));
		for (int i = 0; i < SQUAD; i++) {
			NerisEntity soldier = WorldBetweenEntities.NERIS.create(nether);
			if (soldier == null) {
				continue;
			}
			soldier.setRole(i == 0 ? NerisRole.SEEKER : NerisRole.GUARD);
			soldier.setArcher(i % 3 == 2);
			soldier.setPurge(true);
			soldier.setSpeech(writ ? "speech.worldbetween.hell.writ" : "speech.worldbetween.hell.squad");
			soldier.refreshPositionAndAngles(at.getX() + 0.5 + i % 3, at.getY(), at.getZ() + 0.5 + i / 3, nether.random.nextFloat() * 360f, 0);
			soldier.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, -1, 0, false, false));
			soldier.setDestination(player.getBlockPos());
			nether.spawnEntity(soldier);
			WarPresence.adopt(soldier.getUuid());
			squad.add(soldier.getUuid());
		}
		player.sendMessage(Text.translatable(writ ? "message.worldbetween.hell.squad_writ" : "message.worldbetween.hell.squad")
				.formatted(writ ? Formatting.GOLD : Formatting.RED), true);
	}

	/** Solid ground with room to stand, at or below a point in the Nether. */
	private static BlockPos ground(ServerWorld nether, BlockPos near) {
		if (!nether.isChunkLoaded(near)) {
			return null;
		}
		for (int y = Math.min(120, near.getY() + 8); y > 32; y--) {
			BlockPos pos = new BlockPos(near.getX(), y, near.getZ());
			if (nether.getBlockState(pos.down()).isSolidBlock(nether, pos.down()) && nether.getBlockState(pos).isAir() && nether.getBlockState(pos.up()).isAir()) {
				return pos;
			}
		}
		return null;
	}

	/** The bridgehead: a blackstone platform hewn into the Nether round the far end of the Great Portal. */
	private static void buildBridgehead(ServerWorld nether, BlockPos head) {
		int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
		for (int x = -8; x <= 8; x++) {
			for (int z = -6; z <= 6; z++) {
				for (int y = 0; y <= 9; y++) {
					nether.setBlockState(head.add(x, y, z), Blocks.AIR.getDefaultState(), flags);
				}
				nether.setBlockState(head.add(x, -1, z), (x + z) % 4 == 0 ? Blocks.GILDED_BLACKSTONE.getDefaultState()
						: Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), flags);
				for (int y = -2; y >= -4; y--) {
					BlockState below = nether.getBlockState(head.add(x, y, z));
					if (below.isAir() || !below.getFluidState().isEmpty()) {
						nether.setBlockState(head.add(x, y, z), Blocks.BLACKSTONE.getDefaultState(), flags);
					}
				}
			}
		}
		for (int x = -4; x <= 4; x++) {
			for (int y = 0; y <= 7; y++) {
				if (x == -4 || x == 4 || y == 0 || y == 7) {
					nether.setBlockState(head.add(x, y, 0), Blocks.OBSIDIAN.getDefaultState(), flags);
				}
			}
		}
		for (int side = -1; side <= 1; side += 2) {
			nether.setBlockState(head.add(side * 7, 0, -5), Blocks.SOUL_LANTERN.getDefaultState(), flags);
			nether.setBlockState(head.add(side * 7, 0, 5), Blocks.SOUL_LANTERN.getDefaultState(), flags);
			nether.setBlockState(head.add(side * 6, 0, -1), Blocks.BLACK_BANNER.getDefaultState(), flags);
		}
		WorksFeatures.light(nether, head.add(-3, 1, 0));
	}
}
