package com.worldbetween.war;

import com.worldbetween.entity.FactionMember;
import com.worldbetween.entity.NerisEntity;
import com.worldbetween.entity.NerisRole;
import com.worldbetween.entity.WorldBetweenEntities;
import com.worldbetween.entity.arki.ArkiEntity;
import com.worldbetween.entity.arki.ArkiRole;
import com.worldbetween.entity.kament.KamentEntity;
import com.worldbetween.entity.kament.KamentMaterial;
import com.worldbetween.entity.kament.KamentRole;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * War parties in the world around players. A band is met wherever it has got to; the few who are seen
 * stand for all of it, and as they fall the rest come up from behind. A fight seen in person is the fight
 * that happened: once it has been quiet for a while, the fallen are counted, the beaten side goes home and
 * a battlefield is left where it was fought.
 */
public final class WarPresence {
	private static final int INTERVAL = 20;
	private static final double MEET = 96;
	private static final double LEAVE = 140;
	/** A fight is over when nobody has fallen for this long. */
	private static final int QUIET_TICKS = 600;
	private static final KamentMaterial[] STONES = {KamentMaterial.STONE, KamentMaterial.ANDESITE, KamentMaterial.GRANITE, KamentMaterial.DIORITE};

	/** Slot ("band id:n") to entity. */
	private static final Map<String, UUID> SLOTS = new HashMap<>();
	private static final Map<UUID, Integer> MEMBERS = new HashMap<>();
	private static final Map<Integer, Integer> NEXT_SLOT = new HashMap<>();
	private static final List<Fight> FIGHTS = new ArrayList<>();
	/** Band members already counted as they fell, so the general death watch does not count them again. */
	private static final Set<UUID> COUNTED = new HashSet<>();

	/** A fight going on in person: where, who has fallen on each side, which bands were in it. */
	private static final class Fight {
		final BlockPos at;
		final EnumMap<FactionType, Integer> fallen = new EnumMap<>(FactionType.class);
		final Map<Integer, Integer> bandsAtStart = new HashMap<>();
		long last;

		Fight(BlockPos at, long now) {
			this.at = at;
			this.last = now;
		}
	}

	private WarPresence() {
	}

	/** Folk of the peoples that others bring into the world for the war (builders, riders, purge squads). */
	private static final Set<UUID> ADOPTED = new HashSet<>();

	public static boolean isManaged(UUID id) {
		return MEMBERS.containsKey(id) || ADOPTED.contains(id);
	}

	public static void adopt(UUID id) {
		ADOPTED.add(id);
	}

	public static void release(UUID id) {
		ADOPTED.remove(id);
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % INTERVAL != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		WorldBetweenState state = WorldBetweenState.get(server);
		WarState wars = state.wars();
		double now = com.worldbetween.sim.Clock.now(server);
		Set<Integer> alive = new HashSet<>();
		for (Warband band : List.copyOf(wars.bands())) {
			alive.add(band.id());
			show(world, state, band, now);
		}
		for (String slot : List.copyOf(SLOTS.keySet())) {
			if (!alive.contains(Integer.parseInt(slot.substring(0, slot.indexOf(':'))))) {
				discard(world, slot);
			}
		}
		NEXT_SLOT.keySet().retainAll(alive);
		long tick = server.getTicks();
		for (Fight fight : List.copyOf(FIGHTS)) {
			if (tick - fight.last > QUIET_TICKS) {
				FIGHTS.remove(fight);
				conclude(server, state, fight, now);
			}
		}
	}

	private static void show(ServerWorld world, WorldBetweenState state, Warband band, double now) {
		String prefix = band.id() + ":";
		List<MobEntity> members = new ArrayList<>();
		for (Map.Entry<String, UUID> entry : SLOTS.entrySet()) {
			if (entry.getKey().startsWith(prefix) && world.getEntity(entry.getValue()) instanceof MobEntity mob && mob.isAlive()) {
				members.add(mob);
			}
		}
		BlockPos at = band.position(now);
		BlockPos ground = surface(world, band.faction(), at);
		if (members.isEmpty()) {
			if (!world.isChunkLoaded(ground) || !world.shouldTickEntity(ground) || nearest(world, ground, MEET) == null) {
				return;
			}
			int count = Math.min(band.kind().shown(), band.fighters());
			for (int i = 0; i < count; i++) {
				spawn(world, state, band, ground, i);
			}
			for (int i = 0; i < Math.min(2, band.beasts()); i++) {
				spawnBeast(world, band, ground, i);
			}
			return;
		}
		MobEntity leader = members.get(0);
		if (nearest(world, leader.getBlockPos(), LEAVE) == null && nearest(world, ground, LEAVE) == null) {
			SLOTS.keySet().stream().filter(k -> k.startsWith(prefix)).toList().forEach(k -> discard(world, k));
			return;
		}
		boolean fighting = members.stream().anyMatch(m -> m.getTarget() != null);
		if (fighting) {
			// Held where they fight: the march goes on from here afterwards.
			band.stopAt(leader.getBlockPos(), now);
		}
		// An enemy band in sight: they close in on it rather than keep to their road.
		BlockPos enemy = band.state() == Warband.State.RETURNING ? null : enemyNear(world, state, band, leader.getBlockPos());
		for (MobEntity member : members) {
			if (member.getTarget() != null) {
				continue;
			}
			if (!fighting && enemy == null && member.getBlockPos().getSquaredDistance(ground) > 48 * 48) {
				member.refreshPositionAndAngles(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, member.getYaw(), 0);
			}
			if (enemy != null) {
				guide(member, enemy, false);
				continue;
			}
			BlockPos ahead = band.state() == Warband.State.WAITING ? ground : surface(world, band.faction(), band.position(now + 0.02));
			guide(member, ahead, band.state() == Warband.State.WAITING);
		}
		// The rest of the band comes up from behind as the first fall.
		int wanted = Math.min(band.kind().shown(), band.fighters());
		if (members.size() < wanted && world.getServer().getTicks() % 40 == 0) {
			spawn(world, state, band, ground, NEXT_SLOT.merge(band.id(), 1, Integer::sum) + band.kind().shown());
		}
	}

	/** Where the nearest enemy fighter in the open is, within reach of the band, if any. */
	private static BlockPos enemyNear(ServerWorld world, WorldBetweenState state, Warband band, BlockPos at) {
		LivingEntity best = null;
		double bestDistance = 48 * 48;
		for (LivingEntity other : world.getEntitiesByClass(LivingEntity.class, new net.minecraft.util.math.Box(at).expand(48),
				e -> e instanceof FactionMember m && m.fights() && WarRules.enemies(world.getServer(), band.faction(), m.faction()))) {
			double d = other.getBlockPos().getSquaredDistance(at);
			if (d < bestDistance) {
				bestDistance = d;
				best = other;
			}
		}
		return best == null ? null : best.getBlockPos();
	}

	private static void guide(MobEntity member, BlockPos to, boolean waiting) {
		if (member instanceof NerisEntity neris) {
			if (waiting) {
				neris.setDestination(null);
				neris.setAnchor(to, 6);
			} else {
				neris.setDestination(to);
			}
		} else if (member instanceof KamentEntity kament) {
			kament.setDestination(waiting ? null : to);
			if (waiting) {
				kament.setAnchor(to, 5);
			}
		} else if (member instanceof ArkiEntity arki) {
			arki.setDestination(waiting ? null : to.up(6));
			if (waiting) {
				arki.setAnchor(to.up(4), 6);
			}
		} else if (member instanceof com.worldbetween.entity.ManticoreEntity beast) {
			beast.setDestination(waiting ? null : to.up(10));
			if (waiting) {
				beast.setAnchor(to.up(8), 10);
			}
		}
	}

	private static BlockPos surface(ServerWorld world, FactionType faction, BlockPos at) {
		Heightmap.Type type = faction == FactionType.KAMENT ? Heightmap.Type.OCEAN_FLOOR : Heightmap.Type.MOTION_BLOCKING_NO_LEAVES;
		int y = world.isChunkLoaded(at) ? world.getTopY(type, at.getX(), at.getZ()) : world.getSeaLevel();
		return new BlockPos(at.getX(), y + (faction == FactionType.ARKI ? 5 : 0), at.getZ());
	}

	private static void spawn(ServerWorld world, WorldBetweenState state, Warband band, BlockPos at, int index) {
		BlockPos pos = at.add((index % 4) - 2 + world.random.nextInt(2), 0, (index / 4) % 4 - 2);
		pos = surface(world, band.faction(), pos);
		MobEntity mob = switch (band.faction()) {
			case NERIS -> {
				NerisEntity soldier = WorldBetweenEntities.NERIS.create(world);
				if (soldier != null) {
					soldier.setRole(NerisRole.GUARD);
					soldier.setArcher(index % 3 == 2); // every third of them a bowman
					soldier.setSpeech("speech.worldbetween.war.neris." + band.kind().id());
				}
				yield soldier;
			}
			case KAMENT -> {
				KamentEntity kament = WorldBetweenEntities.KAMENT.create(world);
				if (kament != null) {
					kament.setRole(KamentRole.GUARD);
					kament.setMaterial(STONES[(band.id() + index) % STONES.length]);
					kament.setSpeech("speech.worldbetween.war.kament." + band.kind().id());
				}
				yield kament;
			}
			case ARKI -> {
				ArkiEntity arki = WorldBetweenEntities.ARKI.create(world);
				if (arki != null) {
					arki.setRole(ArkiRole.KEEPER);
					arki.setSpeech("speech.worldbetween.war.arki." + band.kind().id());
				}
				yield arki;
			}
		};
		if (mob == null) {
			return;
		}
		mob.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0);
		String slot = band.id() + ":" + index;
		int bandId = band.id();
		java.util.function.Consumer<DamageSource> onDeath = source -> fell(world.getServer(), bandId, slot, mob, source);
		if (mob instanceof NerisEntity neris) {
			neris.setOnDeath(onDeath);
		} else if (mob instanceof KamentEntity kament) {
			kament.setOnDeath(onDeath);
		} else if (mob instanceof ArkiEntity arki) {
			arki.setOnDeath(onDeath);
		}
		world.spawnEntity(mob);
		SLOTS.put(slot, mob.getUuid());
		MEMBERS.put(mob.getUuid(), bandId);
	}

	/** A manticore of the band with a seeker on its back, bow in hand. */
	private static void spawnBeast(ServerWorld world, Warband band, BlockPos at, int index) {
		com.worldbetween.entity.ManticoreEntity beast = WorldBetweenEntities.MANTICORE.create(world);
		NerisEntity rider = WorldBetweenEntities.NERIS.create(world);
		if (beast == null || rider == null) {
			return;
		}
		BlockPos pos = at.up(8).east(index * 4);
		beast.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0);
		rider.setRole(NerisRole.SEEKER);
		rider.setArcher(true);
		rider.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, beast.getYaw(), 0);
		rider.setSpeech("speech.worldbetween.war.neris.rider");
		String slot = band.id() + ":b" + index;
		String riderSlot = band.id() + ":r" + index;
		int bandId = band.id();
		beast.setOnDeath(source -> {
			UUID id = SLOTS.remove(slot);
			if (id != null) {
				MEMBERS.remove(id);
			}
			WorldBetweenState state = WorldBetweenState.get(world.getServer());
			Warband owner = state.wars().band(bandId);
			if (owner != null) {
				owner.setBeasts(owner.beasts() - 1);
				Fight fight = fightAt(beast.getBlockPos(), world.getServer().getTicks());
				fight.fallen.merge(FactionType.NERIS, 1, Integer::sum);
				fight.bandsAtStart.putIfAbsent(owner.id(), owner.fighters());
				state.markDirty();
			}
		});
		rider.setOnDeath(source -> {
			UUID id = SLOTS.remove(riderSlot);
			if (id != null) {
				MEMBERS.remove(id);
				COUNTED.add(id);
			}
		});
		world.spawnEntity(beast);
		world.spawnEntity(rider);
		rider.startRiding(beast, true);
		SLOTS.put(slot, beast.getUuid());
		MEMBERS.put(beast.getUuid(), bandId);
		SLOTS.put(riderSlot, rider.getUuid());
		MEMBERS.put(rider.getUuid(), bandId);
	}

	/** One of a band has fallen in person: the band is one fewer, and the fight is remembered. */
	private static void fell(MinecraftServer server, int bandId, String slot, MobEntity mob, DamageSource source) {
		WorldBetweenState state = WorldBetweenState.get(server);
		UUID id = SLOTS.remove(slot);
		if (id != null) {
			MEMBERS.remove(id);
			COUNTED.add(id);
		}
		Warband band = state.wars().band(bandId);
		if (band == null) {
			return;
		}
		band.setFighters(band.fighters() - 1);
		Fight fight = fightAt(mob.getBlockPos(), server.getTicks());
		fight.fallen.merge(band.faction(), 1, Integer::sum);
		fight.bandsAtStart.putIfAbsent(band.id(), band.fighters() + 1);
		Entity attacker = source.getAttacker();
		if (attacker instanceof FactionMember enemy) {
			Integer enemyBand = MEMBERS.get(attacker.getUuid());
			if (enemyBand != null) {
				Warband other = state.wars().band(enemyBand);
				if (other != null) {
					fight.bandsAtStart.putIfAbsent(other.id(), other.fighters());
				}
			}
			fight.fallen.putIfAbsent(enemy.faction(), 0);
		}
		if (attacker instanceof ServerPlayerEntity player) {
			// Whoever kills a people's fighter is its enemy; its enemies take note of the help.
			Faction victims = state.faction(band.faction());
			victims.changeReputation(player.getUuid(), -10);
			victims.declareCriminal(player.getUuid(), state.day());
			Faction helped = state.faction(band.enemy());
			helped.changeReputation(player.getUuid(), 3);
		}
		state.markDirty();
	}

	/**
	 * Anyone of a people killed by the enemy's folk in person, not of a band (a guard at a gate, a caravan's
	 * merchant, an Arki at its lens): a death in the fight all the same.
	 */
	public static void onDeath(LivingEntity dead, DamageSource source) {
		if (COUNTED.remove(dead.getUuid())) {
			return;
		}
		if (!(dead instanceof FactionMember member) || MEMBERS.containsKey(dead.getUuid()) || !(source.getAttacker() instanceof FactionMember killer)
				|| killer.faction() == member.faction() || !(dead.getWorld() instanceof ServerWorld world)) {
			return;
		}
		Fight fight = fightAt(dead.getBlockPos(), world.getServer().getTicks());
		fight.fallen.merge(member.faction(), 1, Integer::sum);
		Integer band = MEMBERS.get(source.getAttacker().getUuid());
		WorldBetweenState state = WorldBetweenState.get(world.getServer());
		if (band != null && state.wars().band(band) != null) {
			fight.bandsAtStart.putIfAbsent(band, state.wars().band(band).fighters());
		}
		// The town or the colony is one fewer.
		Faction faction = state.faction(member.faction());
		Settlement home = faction.settlements().stream()
				.min(java.util.Comparator.comparingDouble(s -> s.pos().getSquaredDistance(dead.getBlockPos()))).orElse(null);
		if (home != null && home.pos().getSquaredDistance(dead.getBlockPos()) < 200 * 200 && home.population() > 1) {
			home.setPopulation(home.population() - 1);
			state.markDirty();
		}
	}

	private static Fight fightAt(BlockPos pos, long tick) {
		for (Fight fight : FIGHTS) {
			if (fight.at.getSquaredDistance(pos) < 64 * 64) {
				fight.last = tick;
				return fight;
			}
		}
		Fight fight = new Fight(pos, tick);
		FIGHTS.add(fight);
		return fight;
	}

	/** The fight is over: count the fallen, send the beaten home, leave the field. */
	private static void conclude(MinecraftServer server, WorldBetweenState state, Fight fight, double now) {
		List<FactionType> sides = new ArrayList<>(fight.fallen.keySet());
		boolean big = false;
		for (Map.Entry<Integer, Integer> entry : fight.bandsAtStart.entrySet()) {
			Warband band = state.wars().band(entry.getKey());
			if (band == null) {
				continue;
			}
			if (!sides.contains(band.faction())) {
				sides.add(band.faction());
			}
			if (sides.size() < 2 && !sides.contains(band.enemy())) {
				sides.add(band.enemy()); // cut down by town guards or a stranger: still a fight with that people
			}
			big |= band.kind().isArmy();
			band.setFoughtLive((long) now);
			int lost = entry.getValue() - band.fighters();
			// Lost half or more: the band breaks and goes home.
			if (band.fighters() <= 0) {
				state.wars().bands().remove(band);
			} else if (lost * 2 >= entry.getValue() && band.state() != Warband.State.RETURNING) {
				band.turnBack(now);
			}
		}
		if (sides.size() < 2) {
			return;
		}
		FactionType a = sides.get(0);
		FactionType b = sides.get(1);
		int fa = fight.fallen.getOrDefault(a, 0);
		int fb = fight.fallen.getOrDefault(b, 0);
		War war = state.wars().war(a, b);
		if (war != null) {
			war.recordBattle(a, fa, b, fb);
			war.addTension(big ? 7 : 4);
		}
		SimulationContext context = new SimulationContext(server, state);
		WarSimulation.addField(context, big ? Battlefield.Kind.BATTLE : Battlefield.Kind.SKIRMISH, fight.at, a, fa, b, fb);
		FactionType winner = fa <= fb ? a : b;
		context.chronicle(winner, "chronicle.worldbetween.war.seen", "@war.worldbetween.people." + a.id(), "@war.worldbetween.people." + b.id(), fa + fb);
		state.markDirty();
	}

	private static PlayerEntity nearest(ServerWorld world, BlockPos at, double distance) {
		for (PlayerEntity player : world.getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			double dx = player.getX() - at.getX();
			double dz = player.getZ() - at.getZ();
			if (dx * dx + dz * dz < distance * distance) {
				return player;
			}
		}
		return null;
	}

	private static void discard(ServerWorld world, String slot) {
		UUID id = SLOTS.remove(slot);
		if (id != null) {
			MEMBERS.remove(id);
			Entity entity = world.getEntity(id);
			if (entity != null) {
				entity.discard();
			}
		}
	}

	/** Every band member a player can see right now, for the command that shows them. */
	public static int shown() {
		return MEMBERS.size();
	}
}
