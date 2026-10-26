package com.worldbetween.war;

import com.worldbetween.colony.ColonyLayout;
import com.worldbetween.entity.NerisEntity;
import com.worldbetween.entity.NerisRole;
import com.worldbetween.entity.WorldBetweenEntities;
import com.worldbetween.entity.arki.ArkiEntity;
import com.worldbetween.entity.arki.ArkiPresence;
import com.worldbetween.entity.arki.ArkiRole;
import com.worldbetween.entity.kament.KamentEntity;
import com.worldbetween.entity.kament.KamentMaterial;
import com.worldbetween.entity.kament.KamentRole;
import com.worldbetween.entity.kament.KamentSecurity;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.town.TownLayout;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The round outside every town of a people that has an army: two or three of the town's own watch walking
 * a ring about it, day and night. They are counted out of the watch, so a town shows no more guards for its
 * patrol than it has; whoever falls on the round is one fewer in the town.
 */
public final class TownPatrols {
	private static final int INTERVAL = 20;
	/** How near a player has to be for the round to be walked in person. */
	private static final double MEET = 128;
	private static final double LEAVE = 176;
	/** How far round the ring the patrol goes between waypoints. */
	private static final double STEP = Math.toRadians(20);

	private static final Map<String, UUID> SLOTS = new HashMap<>();
	/** Where along its ring each town's patrol has got to. */
	private static final Map<String, Double> ANGLE = new HashMap<>();

	private TownPatrols() {
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % INTERVAL != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		WorldBetweenState state = WorldBetweenState.get(server);
		Map<String, Boolean> seen = new HashMap<>();
		for (Faction faction : state.factions()) {
			if (!faction.isActive()) {
				continue;
			}
			for (Settlement town : faction.settlements()) {
				String prefix = faction.type().id() + ":" + town.id() + ":";
				int size = Armies.patrol(server, faction, town);
				boolean out = SLOTS.keySet().stream().anyMatch(k -> k.startsWith(prefix));
				if (size <= 0 || !near(world, town.pos(), out ? LEAVE : MEET)) {
					continue;
				}
				seen.put(prefix, true);
				walk(world, faction, town, prefix, size);
			}
		}
		for (String slot : List.copyOf(SLOTS.keySet())) {
			String prefix = slot.substring(0, slot.lastIndexOf(':') + 1);
			if (!seen.containsKey(prefix)) {
				discard(world, slot);
			}
		}
	}

	private static boolean near(ServerWorld world, BlockPos at, double distance) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			double dx = player.getX() - at.getX();
			double dz = player.getZ() - at.getZ();
			if (!player.isSpectator() && dx * dx + dz * dz < distance * distance) {
				return true;
			}
		}
		return false;
	}

	public static boolean isManaged(UUID id) {
		return SLOTS.containsValue(id);
	}

	private static int ring(FactionType type, Settlement town) {
		return switch (type) {
			case NERIS -> TownLayout.zoneHalf(town.tier()) + 10;
			case KAMENT -> 22 + 4 * town.tier();
			case ARKI -> ColonyLayout.centralRadius(town.tier()) + 10;
		};
	}

	private static BlockPos point(ServerWorld world, FactionType type, Settlement town, double angle) {
		int r = ring(type, town);
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		// The kingdom's towns are square inside their walls: the round goes along the outside of them.
		double scale = type == FactionType.NERIS ? r / Math.max(Math.abs(cos), Math.abs(sin)) : r;
		int x = town.pos().getX() + (int) Math.round(cos * scale);
		int z = town.pos().getZ() + (int) Math.round(sin * scale);
		if (type == FactionType.ARKI) {
			return new BlockPos(x, town.pos().getY() + 5, z);
		}
		int y = world.isChunkLoaded(new BlockPos(x, 0, z)) ? world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) : town.pos().getY();
		return new BlockPos(x, y, z);
	}

	private static void walk(ServerWorld world, Faction faction, Settlement town, String prefix, int size) {
		FactionType type = faction.type();
		double angle = ANGLE.computeIfAbsent(prefix, k -> world.random.nextDouble() * Math.PI * 2);
		BlockPos waypoint = point(world, type, town, angle);
		List<MobEntity> members = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			String slot = prefix + i;
			MobEntity mob = SLOTS.containsKey(slot) && world.getEntity(SLOTS.get(slot)) instanceof MobEntity m && m.isAlive() ? m : null;
			if (mob == null) {
				SLOTS.remove(slot);
				if (!world.isChunkLoaded(waypoint) || !world.shouldTickEntity(waypoint)) {
					continue;
				}
				mob = spawn(world, faction, town, slot, waypoint.add(i % 2 * 2 - 1, 0, i / 2 * 2 - 1), i);
				if (mob == null) {
					continue;
				}
			}
			members.add(mob);
		}
		// Those the town can no longer spare go back in.
		for (int i = size; SLOTS.containsKey(prefix + i); i++) {
			discard(world, prefix + i);
		}
		if (members.isEmpty()) {
			return;
		}
		MobEntity leader = members.get(0);
		boolean fighting = members.stream().anyMatch(m -> m.getTarget() != null);
		if (!fighting && leader.squaredDistanceTo(waypoint.getX() + 0.5, leader.getY(), waypoint.getZ() + 0.5) < 5 * 5) {
			angle += STEP;
			ANGLE.put(prefix, angle);
			waypoint = point(world, type, town, angle);
		}
		for (int i = 0; i < members.size(); i++) {
			MobEntity mob = members.get(i);
			if (mob.getTarget() != null) {
				continue;
			}
			// Stuck on a cliff or in water: they are a patrol, not a siege; on to the next point of the ring.
			if (mob.squaredDistanceTo(waypoint.toCenterPos()) > 60 * 60) {
				mob.refreshPositionAndAngles(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5, mob.getYaw(), 0);
			}
			BlockPos to = i == 0 ? waypoint : leader.getBlockPos();
			if (mob instanceof NerisEntity neris) {
				neris.setDestination(to);
			} else if (mob instanceof KamentEntity kament) {
				kament.setDestination(to);
			} else if (mob instanceof ArkiEntity arki) {
				arki.setDestination(to);
			}
		}
	}

	private static MobEntity spawn(ServerWorld world, Faction faction, Settlement town, String slot, BlockPos at, int index) {
		MobEntity mob = switch (faction.type()) {
			case NERIS -> {
				NerisEntity guard = WorldBetweenEntities.NERIS.create(world);
				if (guard != null) {
					guard.setRole(NerisRole.GUARD);
					guard.setArcher(index == 2);
					guard.setSpeech("speech.worldbetween.patrol.neris", town.name());
					guard.setOnDeath(source -> {
						ServerPlayerEntity killer = NerisEntity.killer(source);
						if (killer != null) {
							faction.changeReputation(killer.getUuid(), -10);
							faction.declareCriminal(killer.getUuid(), WorldBetweenState.get(world.getServer()).day());
						}
						Armies.watchFallen(town);
						fallen(world, slot);
					});
				}
				yield guard;
			}
			case KAMENT -> {
				KamentEntity guard = WorldBetweenEntities.KAMENT.create(world);
				if (guard != null) {
					guard.setRole(KamentRole.GUARD);
					KamentMaterial[] stones = KamentMaterial.values();
					guard.setMaterial(stones[(town.id() + index) % stones.length]);
					guard.setSpeech("speech.worldbetween.patrol.kament", town.name());
					guard.setOnDeath(source -> {
						KamentSecurity.kamentSlain(world.getServer(), faction, town, KamentEntity.killer(source));
						fallen(world, slot);
					});
				}
				yield guard;
			}
			case ARKI -> {
				ArkiEntity keeper = WorldBetweenEntities.ARKI.create(world);
				if (keeper != null) {
					keeper.setRole(ArkiRole.KEEPER);
					keeper.setSpeech("speech.worldbetween.patrol.arki");
					keeper.setOnDeath(source -> {
						ArkiPresence.slain(world.getServer(), faction, town, ArkiEntity.killer(source));
						fallen(world, slot);
					});
				}
				yield keeper;
			}
		};
		if (mob == null) {
			return null;
		}
		mob.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360, 0);
		world.spawnEntity(mob);
		SLOTS.put(slot, mob.getUuid());
		return mob;
	}

	private static void fallen(ServerWorld world, String slot) {
		SLOTS.remove(slot);
		WorldBetweenState.get(world.getServer()).markDirty();
	}

	private static void discard(ServerWorld world, String slot) {
		UUID id = SLOTS.remove(slot);
		Entity entity = id == null ? null : world.getEntity(id);
		if (entity != null) {
			entity.discard();
		}
	}
}
