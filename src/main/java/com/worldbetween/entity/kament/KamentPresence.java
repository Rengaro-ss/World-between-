package com.worldbetween.entity.kament;

import com.worldbetween.entity.WorldBetweenEntities;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.kament.Ancient;
import com.worldbetween.faction.kament.KamentHall;
import com.worldbetween.faction.kament.Kaments;
import com.worldbetween.hold.HoldFeatures;
import com.worldbetween.hold.HoldLayout;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Brings the Kaments out into their halls when a player comes near: the Ancient on its seat, the workers at
 * their stone, a sculptor over its unfinished work, guards at the gate. They go back into the simulation
 * when the player leaves.
 */
public final class KamentPresence {
	private static final int INTERVAL = 20;
	private static final double MEET = 64;
	private static final double LEAVE = 96;
	private static final double MEET_VERTICAL = 40;
	private static final int MAX_WORKERS = 8;

	private static final Map<String, UUID> SLOTS = new HashMap<>();
	private static final Map<UUID, Long> LINGERING = new HashMap<>();

	private KamentPresence() {
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % INTERVAL != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		Faction faction = WorldBetweenState.get(server).faction(FactionType.KAMENT);
		if (!faction.isActive()) {
			return;
		}
		for (Settlement settlement : faction.settlements()) {
			hold(world, faction, settlement);
		}
		long now = server.getTicks();
		LINGERING.entrySet().removeIf(entry -> {
			Entity entity = world.getEntity(entry.getKey());
			if (entity == null) {
				return true;
			}
			if (now > entry.getValue() && world.getClosestPlayer(entity, 32) == null) {
				entity.discard();
				SLOTS.values().remove(entry.getKey());
				return true;
			}
			return false;
		});
	}

	/** The places a player can be near a hold from: the great hall, and the gate of a mountain hold. */
	private static List<BlockPos> anchors(HoldLayout layout) {
		List<BlockPos> anchors = new ArrayList<>();
		anchors.add(layout.origin());
		if (!layout.isDeep()) {
			anchors.add(layout.local().world(0, 1, -layout.exit()));
		}
		return anchors;
	}

	private static boolean near(ServerWorld world, List<BlockPos> anchors, double distance) {
		for (PlayerEntity player : world.getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			for (BlockPos anchor : anchors) {
				double dx = player.getX() - anchor.getX();
				double dz = player.getZ() - anchor.getZ();
				if (dx * dx + dz * dz < distance * distance && Math.abs(player.getY() - anchor.getY()) < MEET_VERTICAL + distance - MEET) {
					return true;
				}
			}
		}
		return false;
	}

	private static void hold(ServerWorld world, Faction faction, Settlement settlement) {
		String prefix = settlement.key(faction.type()) + "/";
		HoldLayout layout = HoldLayout.of(world, settlement);
		List<BlockPos> anchors = anchors(layout);
		if (!near(world, anchors, MEET)) {
			if (!near(world, anchors, LEAVE)) {
				SLOTS.keySet().stream().filter(k -> k.startsWith(prefix)).toList().forEach(k -> discard(world, k));
			}
			return;
		}
		Canvas.Local local = layout.local();
		int tier = settlement.tier();
		boolean deep = layout.isDeep();
		Random random = Random.create(settlement.pos().asLong());
		KamentMaterial[] stones = deep ? new KamentMaterial[]{KamentMaterial.DEEPSLATE, KamentMaterial.TUFF, KamentMaterial.DEEPSLATE}
				: new KamentMaterial[]{KamentMaterial.STONE, KamentMaterial.ANDESITE, KamentMaterial.DIORITE, KamentMaterial.GRANITE};
		Map<String, Spawner> wanted = new HashMap<>();

		// The Ancient on its seat.
		Ancient ancient = Kaments.ancientOf(faction, settlement);
		if (ancient != null) {
			BlockPos seat = deep ? local.world(0, 2, HoldLayout.cavernRadius(tier) / 2) : local.world(0, 2, HoldLayout.hallHalfDepth(tier) - 2);
			KamentMaterial material = deep ? KamentMaterial.DEEPSLATE : KamentMaterial.DIORITE;
			wanted.put(prefix + "ancient", new Spawner(KamentRole.ANCIENT, material, seat, entity -> {
				entity.setAnchor(seat, 0);
				entity.setCustomName(Text.translatable("entity.worldbetween.ancient_named", ancient.name()));
				entity.setSpeech("speech.worldbetween.kament.ancient." + ancient.first().id(), ancient.name());
				entity.setOnDeath(source -> KamentSecurity.ancientSlain(world.getServer(), faction, ancient, settlement, KamentEntity.killer(source)));
			}));
		}

		// Workers at their halls, a sculptor over its work.
		int workers = 0;
		boolean sculptor = settlement.counter("sculptors") > 0;
		int wards = 0;
		for (TownFeatures.Feature feature : HoldFeatures.of(world, faction, settlement)) {
			if (!feature.key().contains("/room_")) {
				continue;
			}
			String tail = feature.key().substring(feature.key().lastIndexOf("/room_") + 6);
			int slot = Integer.parseInt(tail.contains(".") ? tail.substring(0, tail.indexOf('.')) : tail);
			KamentHall hall = Kaments.hallAt(settlement, slot);
			if (hall == KamentHall.WARD) {
				wards++;
			}
			List<Canvas.WorkSpot> spots = feature.canvas().workSpots();
			if (hall == null || spots.isEmpty() || hall == KamentHall.WARD) {
				continue;
			}
			BlockPos spot = spots.get(0).pos();
			KamentRole role;
			if (hall == KamentHall.SCULPTORY) {
				if (!sculptor) {
					continue;
				}
				role = KamentRole.SCULPTOR;
			} else {
				if (workers >= Math.min(MAX_WORKERS, settlement.population())) {
					continue;
				}
				workers++;
				role = KamentRole.WORKER;
			}
			KamentMaterial material = stones[random.nextInt(stones.length)];
			wanted.put(prefix + "work:" + slot, new Spawner(role, material, spot, entity -> {
				entity.setWorkSpot(spot);
				entity.setAnchor(spot, 1);
				entity.setSpeech("speech.worldbetween.kament.work." + hall.id());
			}));
		}

		// Those with no hall to work in yet stand in the niches they slept in.
		int idle = Math.min(4, settlement.population() - workers);
		if (!deep) {
			int hd = HoldLayout.hallHalfDepth(tier);
			int hw = HoldLayout.hallHalfWidth(tier);
			for (int i = 0; i < idle; i++) {
				int z = -hd + 2 + 4 * (i / 2);
				BlockPos niche = local.world((i % 2 == 0 ? -1 : 1) * (hw + 1), 1, z);
				KamentMaterial material = stones[random.nextInt(stones.length)];
				wanted.put(prefix + "idle:" + i, new Spawner(KamentRole.WORKER, material, niche, entity -> {
					entity.setAnchor(niche, 0);
					entity.setSpeech("speech.worldbetween.kament.idle");
				}));
			}
		}

		// Guards: at the gate of a mountain hold, round the rim of a deep city's cavern.
		int guards = Math.min(6, 1 + tier + 2 * wards);
		for (int i = 0; i < guards; i++) {
			BlockPos post;
			if (!deep) {
				int z = i < 2 ? -layout.exit() + 2 : -HoldLayout.hallHalfDepth(tier) + 1 + (i / 2 - 1) * 3;
				post = local.world(i % 2 == 0 ? -2 : 2, 1, z);
			} else {
				double angle = Math.toRadians(i * 360.0 / guards + 22.5);
				int r = HoldLayout.cavernRadius(tier) - 2;
				post = local.world((int) Math.round(Math.sin(angle) * r), 1, (int) Math.round(-Math.cos(angle) * r));
			}
			KamentMaterial material = stones[random.nextInt(stones.length)];
			BlockPos at = post;
			int line = i % 3;
			wanted.put(prefix + "guard:" + i, new Spawner(KamentRole.GUARD, material, at, entity -> {
				entity.setAnchor(at, 0);
				entity.setSpeech("speech.worldbetween.kament.guard." + line);
			}));
		}

		SLOTS.keySet().stream().filter(k -> k.startsWith(prefix) && !wanted.containsKey(k)).toList().forEach(k -> discard(world, k));
		for (Map.Entry<String, Spawner> entry : wanted.entrySet()) {
			if (entity(world, entry.getKey()) != null) {
				continue;
			}
			Spawner spawner = entry.getValue();
			KamentEntity entity = spawn(world, entry.getKey(), spawner.role(), spawner.material(), spawner.at());
			if (entity == null) {
				continue;
			}
			spawner.setup().accept(entity);
			if (spawner.role() != KamentRole.ANCIENT) {
				String key = entry.getKey();
				entity.setOnDeath(source -> {
					KamentSecurity.kamentSlain(world.getServer(), faction, settlement, KamentEntity.killer(source));
					SLOTS.remove(key);
				});
			}
		}
	}

	private record Spawner(KamentRole role, KamentMaterial material, BlockPos at, Consumer<KamentEntity> setup) {
	}

	/** The Kaments who saw a player, for the guards that come for a stranger. */
	public static List<KamentEntity> near(ServerWorld world, ServerPlayerEntity player, double distance) {
		List<KamentEntity> found = new ArrayList<>();
		for (UUID id : SLOTS.values()) {
			if (world.getEntity(id) instanceof KamentEntity kament && kament.squaredDistanceTo(player) < distance * distance) {
				found.add(kament);
			}
		}
		return found;
	}

	/** Every Kament out right now. */
	public static List<KamentEntity> all(ServerWorld world) {
		List<KamentEntity> found = new ArrayList<>();
		for (UUID id : SLOTS.values()) {
			if (world.getEntity(id) instanceof KamentEntity kament) {
				found.add(kament);
			}
		}
		return found;
	}

	public static void linger(MinecraftServer server, UUID id, long ticks) {
		LINGERING.put(id, server.getTicks() + ticks);
	}

	public static boolean isManaged(UUID id) {
		return SLOTS.containsValue(id) || LINGERING.containsKey(id);
	}

	static KamentEntity entity(ServerWorld world, String slot) {
		UUID id = SLOTS.get(slot);
		if (id == null) {
			return null;
		}
		if (world.getEntity(id) instanceof KamentEntity kament && kament.isAlive()) {
			return kament;
		}
		SLOTS.remove(slot);
		return null;
	}

	static void discard(ServerWorld world, String slot) {
		UUID id = SLOTS.remove(slot);
		if (id != null) {
			Entity entity = world.getEntity(id);
			if (entity != null) {
				entity.discard();
			}
		}
	}

	static KamentEntity spawn(ServerWorld world, String slot, KamentRole role, KamentMaterial material, BlockPos at) {
		if (!world.shouldTickEntity(at) || !world.getBlockState(at).isAir() && !world.getBlockState(at).isReplaceable()) {
			return null;
		}
		KamentEntity entity = WorldBetweenEntities.KAMENT.create(world);
		if (entity == null) {
			return null;
		}
		entity.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
		entity.setRole(role);
		entity.setMaterial(material);
		world.spawnEntity(entity);
		SLOTS.put(slot, entity.getUuid());
		return entity;
	}

	public static int count() {
		return SLOTS.size();
	}
}
