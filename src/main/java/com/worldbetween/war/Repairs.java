package com.worldbetween.war;

import com.worldbetween.colony.ColonyFeatures;
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
import com.worldbetween.faction.ResourceType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.arki.Arkis;
import com.worldbetween.hold.HoldFeatures;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.town.Canvas;
import com.worldbetween.town.Damage;
import com.worldbetween.town.TownFeatures;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The builders. Every people mends what war broke, a little each day, as long as it has the stuff to mend
 * it with: the kingdom stone and timber, the Kaments stone, the Arki amethyst. A player near a broken town
 * sees them at work on the broken buildings, and sees the walls close up as they go.
 */
public final class Repairs {
	private static final int MAX_BUILDERS = 3;
	private static final double MEET = 80;
	private static final Map<String, UUID> SLOTS = new HashMap<>();

	private Repairs() {
	}

	public static void simulateDay(SimulationContext context) {
		for (Faction faction : context.state().factions()) {
			for (Settlement town : faction.settlements()) {
				long damage = town.counter(Damage.DAMAGE);
				if (damage <= 0) {
					continue;
				}
				long mended = 0;
				for (int i = 0; i < 2 + town.tier() && mended < damage; i++) {
					if (!pay(faction, town)) {
						break;
					}
					mended++;
				}
				town.addCounter(Damage.DAMAGE, -mended);
				if (mended > 0 && town.counter(Damage.DAMAGE) <= 0 && town.counter("damage_peak") >= 20) {
					context.chronicle(faction.type(), "chronicle.worldbetween.war.rebuilt", town.name());
				}
				if (town.counter(Damage.DAMAGE) <= 0) {
					town.setCounter("damage_peak", 0);
				}
			}
		}
	}

	/** One point of mending, paid for. */
	private static boolean pay(Faction faction, Settlement town) {
		switch (faction.type()) {
			case NERIS -> {
				if (faction.resource(ResourceType.STONE) < 4 || faction.resource(ResourceType.WOOD) < 3) {
					return false;
				}
				faction.takeResource(ResourceType.STONE, 4);
				faction.takeResource(ResourceType.WOOD, 3);
			}
			case KAMENT -> {
				if (faction.resource(ResourceType.STONE) < 6) {
					return false;
				}
				faction.takeResource(ResourceType.STONE, 6);
			}
			case ARKI -> {
				if (town.counter(Arkis.AMETHYST) < 4) {
					return false;
				}
				town.addCounter(Arkis.AMETHYST, -4);
			}
		}
		return true;
	}

	/** Damage dealt to a town, remembered at its worst so the chronicle can tell of its rebuilding. */
	public static void damage(Settlement town, long amount) {
		Damage.add(town, amount);
		town.setCounter("damage_peak", Math.max(town.counter("damage_peak"), town.counter(Damage.DAMAGE)));
	}

	// ---------------------------------------------------------------- the builders in person

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % 40 != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		WorldBetweenState state = WorldBetweenState.get(server);
		Map<String, Boolean> wanted = new HashMap<>();
		for (Faction faction : state.factions()) {
			for (Settlement town : faction.settlements()) {
				if (town.counter(Damage.DAMAGE) <= 0 || town.dimension() != World.OVERWORLD || !near(world, town.pos())) {
					continue;
				}
				List<TownFeatures.Feature> features = switch (faction.type()) {
					case KAMENT -> HoldFeatures.of(world, faction, town);
					case ARKI -> ColonyFeatures.of(world, faction, town);
					default -> TownFeatures.of(world, faction, town);
				};
				int n = 0;
				for (TownFeatures.Feature feature : features) {
					if (n >= MAX_BUILDERS || !feature.isStructure() || Damage.level(town, feature.key()) == 0) {
						continue;
					}
					BlockPos spot = spot(world, feature.canvas());
					if (spot == null) {
						continue;
					}
					String slot = town.key(faction.type()) + "/repair:" + n++;
					wanted.put(slot, true);
					if (SLOTS.containsKey(slot) && world.getEntity(SLOTS.get(slot)) != null) {
						continue;
					}
					if (world.isChunkLoaded(spot) && world.shouldTickEntity(spot)) {
						spawn(world, faction.type(), slot, spot);
					}
				}
			}
		}
		for (String slot : List.copyOf(SLOTS.keySet())) {
			if (!wanted.containsKey(slot)) {
				UUID id = SLOTS.remove(slot);
				WarPresence.release(id);
				Entity entity = world.getEntity(id);
				if (entity != null) {
					entity.discard();
				}
			}
		}
	}

	/** Where a builder stands to mend a building: on the ground beside it. */
	private static BlockPos spot(ServerWorld world, Canvas canvas) {
		if (canvas.blocks().isEmpty()) {
			return null;
		}
		long x = 0;
		long z = 0;
		for (BlockPos pos : canvas.blocks().keySet()) {
			x += pos.getX();
			z += pos.getZ();
		}
		int cx = (int) (x / canvas.blocks().size());
		int cz = (int) (z / canvas.blocks().size());
		return new BlockPos(cx, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz), cz);
	}

	private static void spawn(ServerWorld world, FactionType faction, String slot, BlockPos at) {
		MobEntity mob = switch (faction) {
			case NERIS -> {
				NerisEntity builder = WorldBetweenEntities.NERIS.create(world);
				if (builder != null) {
					builder.setRole(NerisRole.WORKER);
					builder.setTool(Items.IRON_SHOVEL);
					builder.setWorkSpot(at);
					builder.setSpeech("speech.worldbetween.builder.neris");
				}
				yield builder;
			}
			case KAMENT -> {
				KamentEntity builder = WorldBetweenEntities.KAMENT.create(world);
				if (builder != null) {
					builder.setRole(KamentRole.WORKER);
					builder.setMaterial(KamentMaterial.STONE);
					builder.setWorkSpot(at);
					builder.setAnchor(at, 2);
					builder.setSpeech("speech.worldbetween.builder.kament");
				}
				yield builder;
			}
			case ARKI -> {
				ArkiEntity builder = WorldBetweenEntities.ARKI.create(world);
				if (builder != null) {
					builder.setRole(ArkiRole.GARDENER);
					builder.setWorkSpot(at);
					builder.setAnchor(at, 3);
					builder.setSpeech("speech.worldbetween.builder.arki");
				}
				yield builder;
			}
		};
		if (mob == null) {
			return;
		}
		mob.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360f, 0);
		world.spawnEntity(mob);
		SLOTS.put(slot, mob.getUuid());
		WarPresence.adopt(mob.getUuid());
	}

	private static boolean near(ServerWorld world, BlockPos pos) {
		for (PlayerEntity player : world.getPlayers()) {
			double dx = player.getX() - pos.getX();
			double dz = player.getZ() - pos.getZ();
			if (dx * dx + dz * dz < MEET * MEET) {
				return true;
			}
		}
		return false;
	}
}
