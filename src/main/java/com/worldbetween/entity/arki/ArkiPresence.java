package com.worldbetween.entity.arki;

import com.worldbetween.colony.ColonyFeatures;
import com.worldbetween.colony.ColonyLayout;
import com.worldbetween.entity.WorldBetweenEntities;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.arki.ArkiHall;
import com.worldbetween.faction.arki.ArkiNames;
import com.worldbetween.faction.arki.Arkis;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.town.Canvas;
import com.worldbetween.town.TownFeatures;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Brings the Arki out over their discs when a player comes near: gardeners over the crystal beds,
 * astronomers at the lenses (more of them by night), gatherers at the pools and pads, keepers round the
 * capsule they came down in.
 */
public final class ArkiPresence {
	private static final double MEET = 72;
	private static final double LEAVE = 104;
	private static final Map<String, UUID> SLOTS = new HashMap<>();

	private ArkiPresence() {
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % 20 != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		Faction faction = WorldBetweenState.get(server).faction(FactionType.ARKI);
		if (!faction.isActive()) {
			return;
		}
		for (Settlement colony : faction.settlements()) {
			colony(world, faction, colony);
		}
	}

	private static boolean near(ServerWorld world, BlockPos origin, double distance) {
		for (PlayerEntity player : world.getPlayers()) {
			double dx = player.getX() - origin.getX();
			double dz = player.getZ() - origin.getZ();
			if (!player.isSpectator() && dx * dx + dz * dz < distance * distance && Math.abs(player.getY() - origin.getY()) < distance) {
				return true;
			}
		}
		return false;
	}

	private static void colony(ServerWorld world, Faction faction, Settlement colony) {
		String prefix = colony.key(faction.type()) + "/";
		BlockPos origin = ColonyLayout.origin(world, colony);
		if (!near(world, origin, MEET)) {
			if (!near(world, origin, LEAVE)) {
				SLOTS.keySet().stream().filter(k -> k.startsWith(prefix)).toList().forEach(k -> discard(world, k));
			}
			return;
		}
		boolean night = world.isNight();
		Map<String, Spot> wanted = new HashMap<>();
		int people = colony.population();
		int out = 0;
		for (TownFeatures.Feature feature : ColonyFeatures.of(world, faction, colony)) {
			int at = feature.key().lastIndexOf("/disc_");
			if (at < 0) {
				continue;
			}
			String tail = feature.key().substring(at + 6);
			int slot = Integer.parseInt(tail.substring(0, tail.indexOf('.')));
			ArkiHall hall = Arkis.hallAt(colony, slot);
			if (hall == null || out >= people) {
				continue;
			}
			java.util.List<Canvas.WorkSpot> spots = feature.canvas().workSpots();
			BlockPos spot = spots.isEmpty() ? null : spots.get(0).pos();
			ArkiRole role = switch (hall) {
				case GARDEN -> ArkiRole.GARDENER;
				case LENS, OBSERVATORY -> ArkiRole.ASTRONOMER;
				default -> ArkiRole.GATHERER;
			};
			int count = hall == ArkiHall.LENS && night ? 2 : 1;
			for (int i = 0; i < count && out < people; i++) {
				out++;
				BlockPos anchor = spot != null ? spot : feature.canvas().blocks().keySet().iterator().next();
				wanted.put(prefix + "disc:" + slot + ":" + i, new Spot(role, anchor, i == 0 ? spot : null, "speech.worldbetween.arki.work." + hall.id()));
			}
		}
		// Keepers round the capsule, and the rest drifting over the central disc.
		int keepers = Math.min(people - out, 1 + colony.tier());
		for (int i = 0; i < keepers; i++) {
			wanted.put(prefix + "keeper:" + i, new Spot(ArkiRole.KEEPER, origin, null, ""));
			out++;
		}
		for (int i = 0; i < Math.min(4, people - out); i++) {
			wanted.put(prefix + "idle:" + i, new Spot(i % 2 == 0 ? ArkiRole.GATHERER : ArkiRole.GARDENER, origin, null, ""));
		}
		SLOTS.keySet().stream().filter(k -> k.startsWith(prefix) && !wanted.containsKey(k)).toList().forEach(k -> discard(world, k));
		Random random = Random.create(colony.pos().asLong());
		for (Map.Entry<String, Spot> entry : wanted.entrySet()) {
			if (entity(world, entry.getKey()) != null) {
				continue;
			}
			Spot spot = entry.getValue();
			BlockPos at = spot.anchor().up(2);
			if (!world.shouldTickEntity(at) || !world.getBlockState(at).isAir()) {
				continue;
			}
			ArkiEntity arki = WorldBetweenEntities.ARKI.create(world);
			if (arki == null) {
				continue;
			}
			arki.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
			arki.setRole(spot.role());
			arki.setAnchor(spot.anchor(), spot.work() == null ? ColonyLayout.centralRadius(colony.tier()) - 1 : 2);
			arki.setWorkSpot(spot.work());
			arki.setSpeech(spot.speech());
			arki.setCustomName(Text.literal(ArkiNames.person(Random.create(entry.getKey().hashCode() * 0x9E3779B97F4A7C15L + random.nextInt(3)))));
			String key = entry.getKey();
			arki.setOnDeath(source -> {
				slain(world.getServer(), faction, colony, ArkiEntity.killer(source));
				SLOTS.remove(key);
			});
			world.spawnEntity(arki);
			SLOTS.put(key, arki.getUuid());
		}
	}

	private record Spot(ArkiRole role, BlockPos anchor, BlockPos work, String speech) {
	}

	private static void slain(MinecraftServer server, Faction faction, Settlement colony, ServerPlayerEntity killer) {
		WorldBetweenState state = WorldBetweenState.get(server);
		colony.setPopulation(colony.population() - 1);
		if (killer != null) {
			faction.changeReputation(killer.getUuid(), -15);
			if (faction.tryEvent("slain_" + colony.id(), state.day(), 5)) {
				new SimulationContext(server, state).chronicle(FactionType.ARKI, "chronicle.worldbetween.arki.slain", colony.name(),
						killer.getName().getString());
			}
		}
		state.markDirty();
	}

	public static boolean isManaged(UUID id) {
		return SLOTS.containsValue(id);
	}

	private static ArkiEntity entity(ServerWorld world, String slot) {
		UUID id = SLOTS.get(slot);
		if (id == null) {
			return null;
		}
		if (world.getEntity(id) instanceof ArkiEntity arki && arki.isAlive()) {
			return arki;
		}
		SLOTS.remove(slot);
		return null;
	}

	private static void discard(ServerWorld world, String slot) {
		UUID id = SLOTS.remove(slot);
		if (id != null) {
			Entity entity = world.getEntity(id);
			if (entity != null) {
				entity.discard();
			}
		}
	}
}
