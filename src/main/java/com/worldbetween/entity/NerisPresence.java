package com.worldbetween.entity;

import com.worldbetween.faction.BuildingType;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.court.Court;
import com.worldbetween.faction.court.Noble;
import com.worldbetween.faction.journeys.Journey;
import com.worldbetween.faction.journeys.Journeys;
import com.worldbetween.faction.seekers.Seeker;
import com.worldbetween.faction.seekers.Seekers;
import com.worldbetween.mine.MineManager;
import com.worldbetween.mine.MinePlan;
import com.worldbetween.town.Blueprints;
import com.worldbetween.town.Canvas;
import com.worldbetween.town.TownFeatures;
import com.worldbetween.town.TownLayout;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.DonkeyEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.worldbetween.faction.neris.NerisNames;
import net.minecraft.util.math.random.Random;

/**
 * Brings the people of Neris into the world around players: seekers on their journeys, the townsfolk
 * at work, nobles in their halls, guards in the mines. Everyone here stands for something that goes on
 * in the simulation anyway; when no player is near, they quietly leave again.
 */
public final class NerisPresence {
	private static final int INTERVAL = 20;
	private static final double SEEKER_MEET = 96;
	private static final double SEEKER_LEAVE = 140;
	private static final double TOWN_MEET = 80;
	private static final double TOWN_LEAVE = 112;
	private static final int MAX_WORKERS = 10;

	/** Who is standing in for what: slot key to entity. */
	private static final Map<String, UUID> SLOTS = new HashMap<>();
	private static final Map<UUID, Long> LINGERING = new HashMap<>();
	private static final Set<UUID> WARNED = new HashSet<>();

	private NerisPresence() {
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % INTERVAL != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		WorldBetweenState state = WorldBetweenState.get(server);
		Faction faction = state.faction(FactionType.NERIS);
		if (!faction.isActive()) {
			return;
		}
		double now = com.worldbetween.sim.Clock.now(server);
		seekers(world, faction, now);
		journeys(world, faction, now);
		if (SLOTS.keySet().stream().anyMatch(k -> k.startsWith("journey:") || k.startsWith("seeker:"))) {
			state.markDirty(); // progress made on the road in person is kept
		}
		for (Settlement settlement : faction.settlements()) {
			if (settlement.dimension() == World.OVERWORLD) {
				town(world, faction, settlement);
			}
		}
		lingering(world);
	}

	// ---------------------------------------------------------------- seekers

	private static void seekers(ServerWorld world, Faction faction, double now) {
		int resting = 0;
		for (Seeker seeker : faction.seekers()) {
			String slot = "seeker:" + seeker.id();
			NerisEntity entity = entity(world, slot);
			// Only a couple of the seekers resting at home show on the square at a time.
			boolean idle = seeker.isIdle() && (resting++ >= 2 || Arrival.isPlaying(seeker.home()));
			if (!seeker.isAlive() || idle) {
				discard(world, slot);
				continue;
			}
			if (entity != null) {
				PlayerEntity near = world.getClosestPlayer(entity, SEEKER_LEAVE);
				if (near == null) {
					seeker.resumeFrom(entity.getBlockPos(), now);
					discard(world, slot);
					continue;
				}
				guide(world, faction, seeker, entity, now);
				continue;
			}
			BlockPos at = seeker.position(now);
			if (!world.isChunkLoaded(at) || nearestPlayer(world, at, SEEKER_MEET) == null) {
				continue;
			}
			// A seeker at home waits on the town square; one on the road is wherever the road has got to.
			Settlement home = Seekers.home(faction, seeker);
			BlockPos spawnAt;
			if (home != null && home.pos().getSquaredDistance(at.withY(home.pos().getY())) < 30 * 30) {
				spawnAt = standable(world, TownLayout.of(world, home).center().up(), 5);
			} else {
				spawnAt = new BlockPos(at.getX(), world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
			}
			if (spawnAt == null) {
				continue;
			}
			NerisEntity spawned = spawn(world, slot, NerisRole.SEEKER, spawnAt);
			if (spawned == null) {
				continue;
			}
			spawned.setCustomName(Text.translatable("entity.worldbetween.seeker_named", seeker.name()));
			spawned.setOnDeath(source -> {
				Seekers.killed(world.getServer(), faction, seeker, spawned.getBlockPos(), NerisEntity.killer(source));
				SLOTS.remove(slot);
			});
			guide(world, faction, seeker, spawned, now);
		}
	}

	/** Points a seeker in person along its journey, and lets a scout take a close look at its stranger. */
	private static void guide(ServerWorld world, Faction faction, Seeker seeker, NerisEntity entity, double now) {
		entity.setDestination(seeker.target().withY(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
				seeker.target().getX(), seeker.target().getZ())));
		String mission = seeker.mission() == null ? "idle" : seeker.mission().name().toLowerCase();
		entity.setSpeech("speech.worldbetween.seeker." + mission);
		if (seeker.mission() == Seeker.Mission.SCOUT) {
			ServerPlayerEntity stranger = world.getServer().getPlayerManager().getPlayer(seeker.forPlayer());
			if (stranger != null && stranger.getServerWorld() == world && entity.squaredDistanceTo(stranger) < 64 * 64) {
				entity.setQuarry(stranger.getUuid());
				if (entity.squaredDistanceTo(stranger) < 36 && entity.age > 200) {
					// Seen enough: the scout takes the news home.
					Seekers.scouted(world.getServer(), faction, seeker, stranger.getBlockPos());
					Settlement home = Seekers.home(faction, seeker);
					if (home != null) {
						seeker.send(Seeker.Mission.RETURN, entity.getBlockPos(), home.pos(), now, -1, null);
					} else {
						seeker.finish();
					}
					entity.setQuarry(null);
				}
				return;
			}
		}
		entity.setQuarry(null);
		if (entity.getBlockPos().getSquaredDistance(seeker.target().withY(entity.getBlockY())) < 16) {
			seeker.resumeFrom(seeker.target(), now);
		}
	}

	// ---------------------------------------------------------------- towns

	private static void town(ServerWorld world, Faction faction, Settlement settlement) {
		String prefix = settlement.key(faction.type()) + "/";
		BlockPos center = settlement.pos();
		PlayerEntity near = nearestPlayer(world, center, TOWN_MEET);
		if (near == null || Arrival.isPlaying(settlement.id())) {
			if (nearestPlayer(world, center, TOWN_LEAVE) == null) {
				SLOTS.keySet().stream().filter(k -> k.startsWith(prefix)).toList().forEach(k -> discard(world, k));
			}
			return;
		}
		boolean day = world.isDay();
		TownLayout layout = TownLayout.of(world, settlement);
		Map<String, Spawner> wanted = new HashMap<>();
		int plaza = TownLayout.plazaHalf(settlement.tier());
		BlockPos square = layout.center().up();

		// Nobles in their hall; the king on his throne.
		Noble king = faction.king();
		BlockPos hall = hallSpot(layout, settlement, king != null && king.seat() == settlement.id());
		for (Noble noble : faction.livingNobles()) {
			if (noble.seat() != settlement.id()) {
				continue;
			}
			boolean isKing = noble == king;
			wanted.put(prefix + "noble:" + noble.id(), new Spawner(isKing ? NerisRole.KING : NerisRole.BARON, hall, isKing ? 1 : 3, entity -> {
				entity.setCustomName(Text.translatable(isKing ? "entity.worldbetween.king_named" : "entity.worldbetween.baron_named",
						noble.displayName()));
				entity.setSpeech(isKing ? "speech.worldbetween.king" : "speech.worldbetween.baron." + noble.ambition().id(), noble.displayName());
				entity.setOnDeath(source -> Court.slain(world.getServer(), faction, noble, NerisEntity.killer(source)));
			}));
		}

		// Workers at the buildings that have a crew, by day.
		if (day) {
			Map<BuildingType, Integer> used = new HashMap<>();
			int workers = 0;
			for (TownFeatures.Feature feature : TownFeatures.of(world, faction, settlement)) {
				if (!feature.isStructure() || feature.type() == null) {
					continue;
				}
				BuildingType type = feature.type();
				// Merchants keep the market stalls whatever else is going on; the other crews are capped.
				if (type != BuildingType.MARKET && workers >= MAX_WORKERS) {
					continue;
				}
				int crews = type == BuildingType.MARKET ? 2 + settlement.tier() : settlement.staffed().getOrDefault(type, settlement.buildingCount(type));
				if (used.merge(type, 1, Integer::sum) > crews || type == BuildingType.HOUSE) {
					continue;
				}
				List<Canvas.WorkSpot> spots = feature.canvas().workSpots();
				if (spots.isEmpty()) {
					continue;
				}
				Canvas.WorkSpot spot = spots.get(0);
				Item tool = toolFor(spot.work());
				boolean merchant = type == BuildingType.MARKET;
				if (!merchant) {
					workers++;
				}
				wanted.put(prefix + "work:" + feature.key(), new Spawner(merchant ? NerisRole.MERCHANT : NerisRole.WORKER, spot.pos(), 0, entity -> {
					entity.setWorkSpot(spot.pos());
					entity.setAnchor(spot.pos(), 2);
					if (merchant) {
						entity.setMarket(settlement.id());
						entity.setCustomName(Text.translatable("entity.worldbetween.merchant_named", NerisNames.person(Random.create(feature.key().hashCode() * 0x9E3779B97F4A7C15L))));
					} else if (tool != null) {
						entity.setTool(tool);
					}
				}));
			}
		}

		// Townsfolk about the square: more by day.
		int citizens = day ? Math.max(2, Math.min(8, settlement.population() / 12)) : 2;
		for (int i = 0; i < citizens; i++) {
			wanted.put(prefix + "citizen:" + i, new Spawner(NerisRole.CITIZEN, square, plaza + 8,
					entity -> entity.setAnchor(square, plaza + 8)));
		}

		// The watch, as the simulation counts it: the hall door first, then the towers, then the square;
		// those out on the round outside the town are not at their posts.
		int watch = Math.max(0, com.worldbetween.war.Armies.watch(faction, settlement)
				- com.worldbetween.war.Armies.patrol(world.getServer(), faction, settlement));
		int doors = Math.min(watch, settlement.tier() == 0 ? 1 : 2);
		watch -= doors;
		// Guards at the watchtowers, and on the square of a bigger town.
		List<BlockPos> towers = layout.towerSpots(settlement.tier());
		// Guards out on patrol are not at their posts.
		int towerGuards = Math.max(0, Math.min(4, Math.min(towers.size(), settlement.buildingCount(BuildingType.WATCHTOWER)))
				- Journeys.away(faction, settlement.id(), Journey.Kind.PATROL));
		towerGuards = Math.min(towerGuards, watch);
		watch -= towerGuards;
		for (int i = 0; i < towerGuards; i++) {
			BlockPos tower = towers.get(i);
			BlockPos post = tower.withY(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, tower.getX(), tower.getZ() - 2));
			// The tower watch carries bows.
			wanted.put(prefix + "guard:" + i, new Spawner(NerisRole.GUARD, post.north(2), 3, entity -> {
				entity.setAnchor(post.north(2), 3);
				entity.setArcher(true);
			}));
		}
		// Guards at the hall door, who decide who goes in.
		TownLayout.Hall door = layout.hall(settlement.tier(), settlement.isCapital());
		for (int i = 0; i < doors; i++) {
			BlockPos post = door.outside((i == 0 ? -1 : 1) * (door.doorHalf() + 1), 1);
			wanted.put(prefix + "door:" + i, new Spawner(NerisRole.GUARD, post, 0, entity -> {
				entity.setAnchor(post, 0);
				entity.setSpeech("speech.worldbetween.guard.post");
			}));
		}
		if (settlement.tier() >= 2) {
			for (int i = 0; i < Math.min(2, watch); i++) {
				wanted.put(prefix + "watch:" + i, new Spawner(NerisRole.GUARD, square, plaza, entity -> entity.setAnchor(square, plaza)));
			}
		}

		SLOTS.keySet().stream().filter(k -> k.startsWith(prefix) && !wanted.containsKey(k)).toList().forEach(k -> discard(world, k));
		for (Map.Entry<String, Spawner> entry : wanted.entrySet()) {
			if (entity(world, entry.getKey()) != null) {
				continue;
			}
			Spawner spawner = entry.getValue();
			BlockPos at = standable(world, spawner.at(), spawner.spread());
			if (at == null || !world.isChunkLoaded(at)) {
				continue;
			}
			NerisEntity entity = spawn(world, entry.getKey(), spawner.role(), at);
			if (entity != null) {
				spawner.setup().accept(entity);
				if (entity.role() != NerisRole.KING && entity.role() != NerisRole.BARON) {
					String key = entry.getKey();
					entity.setOnDeath(source -> {
						ServerPlayerEntity killer = NerisEntity.killer(source);
						if (killer != null) {
							faction.changeReputation(killer.getUuid(), -10);
							faction.declareCriminal(killer.getUuid(), WorldBetweenState.get(world.getServer()).day());
						}
						// Whoever or whatever killed them, they are gone from the town's count.
						if (entity.role() == NerisRole.GUARD) {
							com.worldbetween.war.Armies.watchFallen(settlement);
						} else {
							settlement.setPopulation(Math.max(1, settlement.population() - 1));
						}
						WorldBetweenState.get(world.getServer()).markDirty();
						SLOTS.remove(key);
					});
				}
			}
		}
	}

	/** Where the town's nobles receive visitors: the throne in the capital's hall, else the hall floor. */
	private static BlockPos hallSpot(TownLayout layout, Settlement settlement, boolean kingHere) {
		TownLayout.Hall hall = layout.hall(settlement.tier(), settlement.isCapital());
		if (hall.throne() && kingHere) {
			return hall.local().world(0, 3, 6);
		}
		return hall.local().world(0, 1, Math.max(0, hall.halfDepth() - 2));
	}

	private static Item toolFor(Canvas.Work work) {
		return switch (work) {
			case ANVIL, CHISEL, DRILL -> Items.IRON_PICKAXE;
			case SAW, AXE -> Items.IRON_AXE;
			case FIELD -> Items.IRON_HOE;
			case FISHING -> Items.FISHING_ROD;
			case BOOKS -> Items.BOOK;
			case KITCHEN, FURNACE -> Items.COAL;
			case LEATHER, LOOM -> Items.SHEARS;
			case STORE, TRADE -> Items.IRON_NUGGET;
			default -> null;
		};
	}

	private record Spawner(NerisRole role, BlockPos at, int spread, java.util.function.Consumer<NerisEntity> setup) {
	}

	// ---------------------------------------------------------------- mine guards

	/** Guards set out after a stranger noticed in a mine: they come up to the stranger's level by the shaft. */
	public static List<UUID> dispatchMineGuards(ServerWorld world, MineManager.Site site, ServerPlayerEntity player) {
		List<UUID> guards = new ArrayList<>();
		MinePlan plan = site.plan();
		int level = 0;
		for (int i = 0; i < plan.levelCount(); i++) {
			if (Math.abs(plan.levelFloor(i) - player.getBlockY()) < Math.abs(plan.levelFloor(level) - player.getBlockY())) {
				level = i;
			}
		}
		BlockPos entrance = site.mine().entrance();
		BlockPos landing = new BlockPos(entrance.getX(), plan.levelFloor(level), entrance.getZ());
		for (int i = 0; i < 2; i++) {
			String slot = "mineguard:" + player.getUuid() + ":" + i;
			NerisEntity guard = spawn(world, slot, NerisRole.GUARD, landing.add(i == 0 ? -1 : 1, 0, 1));
			if (guard != null) {
				guard.setQuarry(player.getUuid());
				guard.setSpeech("speech.worldbetween.guard.mine");
				guards.add(guard.getUuid());
			}
		}
		return guards;
	}

	/** Guards stay a while after a stranger turns criminal, to deal with them; then they go back. */
	public static void linger(MinecraftServer server, List<UUID> guards, long ticks) {
		long until = server.getTicks() + ticks;
		guards.forEach(id -> LINGERING.put(id, until));
	}

	public static void recall(ServerWorld world, List<UUID> guards) {
		for (UUID id : guards) {
			Entity entity = world.getEntity(id);
			if (entity != null) {
				entity.discard();
			}
			SLOTS.values().remove(id);
		}
	}

	private static void lingering(ServerWorld world) {
		long now = world.getServer().getTicks();
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
		// A guard who reaches the stranger says what they came to say.
		for (UUID id : List.copyOf(SLOTS.values())) {
			if (world.getEntity(id) instanceof NerisEntity guard && guard.role() == NerisRole.GUARD && guard.quarry() != null
					&& !WARNED.contains(id)) {
				PlayerEntity player = world.getPlayerByUuid(guard.quarry());
				if (player != null && guard.squaredDistanceTo(player) < 25) {
					WARNED.add(id);
					player.sendMessage(Text.empty().append(Text.translatable("entity.worldbetween.guard").formatted(Formatting.GOLD))
							.append(Text.literal(": ").formatted(Formatting.GRAY))
							.append(Text.translatable(guard.speechKey().isEmpty() ? "speech.worldbetween.guard.mine" : guard.speechKey())), false);
				}
			}
		}
	}

	// ---------------------------------------------------------------- town guards

	/** Two guards sent after a stranger in a town, from a watch post or the hall door. */
	public static List<UUID> dispatchGuards(ServerWorld world, BlockPos from, ServerPlayerEntity player, String speech) {
		List<UUID> guards = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			String slot = "townguard:" + player.getUuid() + ":" + i;
			BlockPos at = standable(world, from, 2);
			NerisEntity guard = spawn(world, slot, NerisRole.GUARD, at == null ? from : at);
			if (guard != null) {
				guard.setQuarry(player.getUuid());
				guard.setSpeech(speech);
				guards.add(guard.getUuid());
				WARNED.remove(guard.getUuid());
			}
		}
		return guards;
	}

	/** The guards standing at a town's hall door right now. */
	public static List<NerisEntity> doorGuards(ServerWorld world, Faction faction, Settlement settlement) {
		String prefix = settlement.key(faction.type()) + "/door:";
		List<NerisEntity> guards = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			NerisEntity guard = entity(world, prefix + i);
			if (guard != null) {
				guards.add(guard);
			}
		}
		return guards;
	}

	/** A noble in person, if they are out in their hall right now. */
	public static NerisEntity noble(ServerWorld world, Faction faction, Settlement settlement, Noble noble) {
		return noble == null ? null : entity(world, settlement.key(faction.type()) + "/noble:" + noble.id());
	}

	// ---------------------------------------------------------------- journeys

	private static final String TRANSIENT = "worldbetween.transient";
	private static final Map<String, UUID> ANIMALS = new HashMap<>();

	/** Patrols, caravans and settlers, met wherever they have got to on the road. */
	private static void journeys(ServerWorld world, Faction faction, double now) {
		Set<String> active = new HashSet<>();
		for (Journey journey : List.copyOf(faction.journeys())) {
			String prefix = "journey:" + journey.id() + ":";
			active.add(prefix);
			int size = journey.kind().people();
			List<NerisEntity> members = new ArrayList<>();
			for (int i = 0; i < size; i++) {
				NerisEntity member = entity(world, prefix + i);
				if (member != null) {
					members.add(member);
				}
			}
			if (!members.isEmpty()) {
				NerisEntity leader = members.get(0);
				if (world.getClosestPlayer(leader, SEEKER_LEAVE) == null) {
					journey.resumeFrom(leader.getBlockPos(), now);
					discardGroup(world, prefix, size);
					continue;
				}
				lead(world, faction, journey, prefix, size, members, now);
				continue;
			}
			BlockPos at = journey.position(now);
			if (!world.isChunkLoaded(at) || nearestPlayer(world, at, SEEKER_MEET) == null) {
				continue;
			}
			spawnGroup(world, faction, journey, prefix, now);
		}
		// Groups whose journey ended while someone watched: they have gone in, or settled.
		for (String slot : List.copyOf(SLOTS.keySet())) {
			if (slot.startsWith("journey:") && !active.contains(slot.substring(0, slot.lastIndexOf(':') + 1))) {
				discard(world, slot);
			}
		}
		for (String key : List.copyOf(ANIMALS.keySet())) {
			if (!active.contains(key)) {
				discardAnimal(world, key);
			}
		}
	}

	private static void spawnGroup(ServerWorld world, Faction faction, Journey journey, String prefix, double now) {
		double travelled = journey.travelled(now);
		Settlement home = Journeys.settlement(faction, journey.from());
		for (int i = 0; i < journey.kind().people(); i++) {
			BlockPos p = journey.at(Math.max(0, travelled - 2.5 * i));
			BlockPos at = new BlockPos(p.getX(), world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
			NerisRole role = switch (journey.kind()) {
				case PATROL -> NerisRole.GUARD;
				case CARAVAN -> i == 0 ? NerisRole.MERCHANT : i == 1 ? NerisRole.WORKER : NerisRole.GUARD;
				case SETTLERS -> i % 3 == 0 ? NerisRole.WORKER : NerisRole.CITIZEN;
			};
			NerisEntity member = spawn(world, prefix + i, role, at);
			if (member == null) {
				continue;
			}
			member.setSpeech("speech.worldbetween.journey." + journey.kind().id() + (role == NerisRole.GUARD ? ".guard" : ""));
			member.setPace(journey.kind() == Journey.Kind.CARAVAN ? 0.2 : journey.kind() == Journey.Kind.SETTLERS ? 0.24 : 0.27);
			if (role == NerisRole.MERCHANT && home != null) {
				member.setMarket(home.id());
				member.setCustomName(Text.translatable("entity.worldbetween.merchant_named", NerisNames.person(Random.create((journey.id() + 1) * 0x9E3779B97F4A7C15L))));
			}
			if (role == NerisRole.WORKER) {
				member.setTool(journey.kind() == Journey.Kind.SETTLERS ? Items.IRON_AXE : Items.LEAD);
			}
			String slot = prefix + i;
			member.setOnDeath(source -> {
				ServerPlayerEntity killer = NerisEntity.killer(source);
				if (killer != null) {
					faction.changeReputation(killer.getUuid(), -10);
					faction.declareCriminal(killer.getUuid(), WorldBetweenState.get(world.getServer()).day());
					if (home != null) {
						home.setPopulation(home.population() - 1);
					}
				}
				SLOTS.remove(slot);
			});
			if (journey.kind() == Journey.Kind.CARAVAN && i == 1) {
				packAnimal(world, prefix, member);
			}
		}
	}

	/** Keeps a group on its road: they all make for a point a little way ahead of the leader. */
	private static void lead(ServerWorld world, Faction faction, Journey journey, String prefix, int size, List<NerisEntity> members, double now) {
		NerisEntity leader = members.get(0);
		journey.resumeFrom(leader.getBlockPos(), now);
		if (journey.hasArrived(now) || leader.getBlockPos().withY(0).getSquaredDistance(journey.destination().withY(0)) < 16) {
			Journeys.arrive(world.getServer(), faction, journey, now);
			if (!faction.journeys().contains(journey)) {
				discardGroup(world, prefix, size);
				return;
			}
		}
		BlockPos next = journey.at(journey.travelled(now) + 14);
		BlockPos ahead = new BlockPos(next.getX(), world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, next.getX(), next.getZ()), next.getZ());
		for (NerisEntity member : members) {
			member.setDestination(ahead);
		}
		UUID animal = ANIMALS.get(prefix);
		if (animal != null && world.getEntity(animal) instanceof DonkeyEntity donkey && members.size() > 1) {
			NerisEntity carter = entity(world, prefix + 1);
			if (carter != null) {
				double distance = donkey.squaredDistanceTo(carter);
				if (distance > 144) {
					donkey.refreshPositionAndAngles(carter.getX(), carter.getY(), carter.getZ(), donkey.getYaw(), 0);
				} else if (distance > 9) {
					donkey.getNavigation().startMovingTo(carter, 1.6);
				}
				if (!donkey.isLeashed()) {
					donkey.attachLeash(carter, true);
				}
			}
		}
	}

	/** The caravan's donkey, led by the carter. It never outlives the encounter. */
	private static void packAnimal(ServerWorld world, String prefix, NerisEntity carter) {
		DonkeyEntity donkey = EntityType.DONKEY.create(world);
		if (donkey == null) {
			return;
		}
		donkey.refreshPositionAndAngles(carter.getX() - 1, carter.getY(), carter.getZ() - 1, carter.getYaw(), 0);
		donkey.setHasChest(true);
		donkey.setPersistent();
		world.spawnEntity(donkey);
		// Tagged only once it is in the world, so a copy that was ever saved is dropped when it loads.
		donkey.addCommandTag(TRANSIENT);
		donkey.attachLeash(carter, true);
		ANIMALS.put(prefix, donkey.getUuid());
	}

	/** Drops any pack animal left over from an earlier session as soon as it is loaded. */
	public static void onEntityLoad(Entity entity) {
		if (entity.getCommandTags().contains(TRANSIENT) && !ANIMALS.containsValue(entity.getUuid())) {
			entity.discard();
		}
	}

	private static void discardGroup(ServerWorld world, String prefix, int size) {
		for (int i = 0; i < size; i++) {
			discard(world, prefix + i);
		}
		discardAnimal(world, prefix);
	}

	private static void discardAnimal(ServerWorld world, String prefix) {
		UUID id = ANIMALS.remove(prefix);
		if (id != null && world.getEntity(id) instanceof DonkeyEntity donkey) {
			donkey.detachLeash(true, false);
			donkey.discard();
		}
	}

	// ---------------------------------------------------------------- helpers

	static NerisEntity entity(ServerWorld world, String slot) {
		UUID id = SLOTS.get(slot);
		if (id == null) {
			return null;
		}
		Entity entity = world.getEntity(id);
		if (entity instanceof NerisEntity neris && neris.isAlive()) {
			return neris;
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

	static NerisEntity spawn(ServerWorld world, String slot, NerisRole role, BlockPos at) {
		// Only where entities are already up and running: one added to a chunk still loading is lost track of.
		if (!world.shouldTickEntity(at)) {
			return null;
		}
		NerisEntity entity = WorldBetweenEntities.NERIS.create(world);
		if (entity == null) {
			return null;
		}
		entity.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360f, 0f);
		entity.setRole(role);
		world.spawnEntity(entity);
		SLOTS.put(slot, entity.getUuid());
		return entity;
	}

	/** A spot near {@code pos} with solid ground and room to stand. */
	private static BlockPos standable(ServerWorld world, BlockPos pos, int spread) {
		if (spread > 2) {
			for (int i = 0; i < 8; i++) {
				int x = pos.getX() + world.random.nextInt(spread * 2 + 1) - spread;
				int z = pos.getZ() + world.random.nextInt(spread * 2 + 1) - spread;
				if (!world.isChunkLoaded(new BlockPos(x, 0, z))) {
					continue;
				}
				int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos at = new BlockPos(x, y, z);
				// Only at ground level near the anchor: never on a roof or up a tower.
				if (Math.abs(y - pos.getY()) <= 3 && world.getBlockState(at.down()).isSolidBlock(world, at.down())
						&& world.getFluidState(at.down()).isEmpty()) {
					return at;
				}
			}
			return null;
		}
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos at = pos.add(dx, dy, dz);
					BlockState below = world.getBlockState(at.down());
					if (world.getBlockState(at).isAir() && world.getBlockState(at.up()).isAir()
							&& below.isSolidBlock(world, at.down()) && at.getSquaredDistance(pos) < bestDistance) {
						best = at;
						bestDistance = at.getSquaredDistance(pos);
					}
				}
			}
		}
		return best;
	}

	private static PlayerEntity nearestPlayer(ServerWorld world, BlockPos pos, double distance) {
		PlayerEntity best = null;
		double bestSq = distance * distance;
		for (PlayerEntity player : world.getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			double dx = player.getX() - pos.getX();
			double dz = player.getZ() - pos.getZ();
			double sq = dx * dx + dz * dz;
			if (sq < bestSq) {
				best = player;
				bestSq = sq;
			}
		}
		return best;
	}

	/** Whether someone is still standing in for something; anyone who is not has no business being out. */
	public static boolean isManaged(UUID id) {
		return SLOTS.containsValue(id) || LINGERING.containsKey(id);
	}

	/** Used by the test harness: who is out right now. */
	public static int count() {
		return SLOTS.size();
	}
}
