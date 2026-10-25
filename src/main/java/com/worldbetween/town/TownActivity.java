package com.worldbetween.town;

import com.worldbetween.faction.BuildingType;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.Settlement;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The sights and sounds of a working town. By day, with a player close enough to see, every building
 * with a crew shows its work: forges ring, saws rasp, furnaces glow and chimneys smoke, fields ripen
 * and are harvested. At night the fires go out. Buildings without a crew stand quiet, which is how a
 * player can tell a town that is short of hands.
 */
public final class TownActivity {
	private static final int INTERVAL = 20;
	private static final double WATCH_DISTANCE = 96;
	private static final int FLAGS = Block.NOTIFY_LISTENERS;

	private TownActivity() {
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % INTERVAL != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		long time = world.getTimeOfDay() % 24000L;
		boolean day = time < 12300 || time > 23500;
		WorldBetweenState state = WorldBetweenState.get(server);
		for (Faction faction : state.factions()) {
			if (!faction.isActive()) {
				continue;
			}
			for (Settlement settlement : faction.settlements()) {
				if (settlement.dimension() != World.OVERWORLD) {
					continue;
				}
				BlockPos center = settlement.pos();
				if (world.getClosestPlayer(center.getX(), center.getY(), center.getZ(), WATCH_DISTANCE, false) == null) {
					continue;
				}
				animate(world, faction, settlement, day);
			}
		}
	}

	private static void animate(ServerWorld world, Faction faction, Settlement settlement, boolean day) {
		Map<BuildingType, Integer> working = new EnumMap<>(BuildingType.class);
		for (TownFeatures.Feature feature : TownFeatures.of(world, faction, settlement)) {
			if (!feature.isStructure()) {
				continue;
			}
			BuildingType type = feature.type();
			// Only as many buildings of a kind work as the settlement could staff yesterday.
			boolean active;
			if (type == null) {
				active = true;
			} else {
				int crews = settlement.staffed().getOrDefault(type, settlement.buildingCount(type));
				int used = working.merge(type, 1, Integer::sum);
				active = used <= crews || type == BuildingType.MARKET || type == BuildingType.WATCHTOWER;
			}
			for (Canvas.WorkSpot spot : feature.canvas().workSpots()) {
				if (world.isChunkLoaded(spot.pos())) {
					work(world, spot, day && active);
				}
			}
		}
	}

	private static void work(ServerWorld world, Canvas.WorkSpot spot, boolean working) {
		BlockPos pos = spot.pos();
		Random random = world.random;
		switch (spot.work()) {
			case FURNACE, KITCHEN -> {
				setLit(world, pos, working);
				if (working && random.nextInt(3) == 0) {
					sound(world, pos, spot.work() == Canvas.Work.KITCHEN ? SoundEvents.BLOCK_SMOKER_SMOKE : SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, 0.6f);
					particles(world, ParticleTypes.SMOKE, pos.up(), 3);
				}
			}
			case CHIMNEY, KILN -> {
				setLit(world, pos, working || spot.work() == Canvas.Work.KILN);
				if (working && spot.work() == Canvas.Work.KILN) {
					particles(world, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.up(), 1);
				}
			}
			case ANVIL -> {
				if (working && random.nextInt(2) == 0) {
					sound(world, pos, SoundEvents.BLOCK_ANVIL_USE, 0.35f);
					particles(world, ParticleTypes.ELECTRIC_SPARK, pos.up(), 6);
				}
			}
			case SAW, AXE -> {
				if (working && random.nextInt(2) == 0) {
					sound(world, pos, spot.work() == Canvas.Work.SAW ? SoundEvents.UI_STONECUTTER_TAKE_RESULT : SoundEvents.ITEM_AXE_STRIP, 0.7f);
					particles(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(pos.down()).isAir()
							? net.minecraft.block.Blocks.DARK_OAK_PLANKS.getDefaultState() : world.getBlockState(pos.down())), pos, 8);
				}
			}
			case CHISEL, DRILL -> {
				if (working && random.nextInt(2) == 0) {
					sound(world, pos, spot.work() == Canvas.Work.DRILL ? SoundEvents.BLOCK_STONE_BREAK : SoundEvents.BLOCK_STONE_HIT, 0.8f);
					particles(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.STONE.getDefaultState()), pos.up(), 8);
				}
			}
			case GRIND -> {
				if (working && random.nextInt(3) == 0) {
					sound(world, pos, SoundEvents.BLOCK_GRINDSTONE_USE, 0.5f);
				}
			}
			case FIELD -> {
				if (working) {
					tendField(world, pos, random);
				}
			}
			case ANIMALS -> {
				if (working && random.nextInt(60) == 0) {
					restock(world, pos);
				}
			}
			case LOOM -> {
				if (working && random.nextInt(3) == 0) {
					sound(world, pos, SoundEvents.UI_LOOM_TAKE_RESULT, 0.6f);
				}
			}
			case LEATHER -> {
				if (working && random.nextInt(3) == 0) {
					sound(world, pos, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value(), 0.7f);
					particles(world, ParticleTypes.SPLASH, pos.up(), 6);
				}
			}
			case BOOKS -> {
				if (working && random.nextInt(4) == 0) {
					sound(world, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, 0.8f);
				}
			}
			case STORE, TRADE -> {
				if (working && random.nextInt(4) == 0) {
					sound(world, pos, random.nextBoolean() ? SoundEvents.BLOCK_BARREL_OPEN : SoundEvents.BLOCK_BARREL_CLOSE, 0.6f);
				}
			}
			case FISHING -> {
				if (working && random.nextInt(3) == 0) {
					sound(world, pos, SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, 0.6f);
					particles(world, ParticleTypes.FISHING, pos.down().offset(net.minecraft.util.math.Direction.Type.HORIZONTAL.random(random), 2), 6);
				}
			}
		}
	}

	/** Crops near the spot ripen; ripe ones are harvested and replanted. */
	private static void tendField(ServerWorld world, BlockPos center, Random random) {
		for (int i = 0; i < 3; i++) {
			BlockPos pos = center.add(random.nextInt(5) - 2, 0, random.nextInt(9) - 4);
			BlockState state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof CropBlock crop)) {
				continue;
			}
			int age = crop.getAge(state);
			if (age < crop.getMaxAge()) {
				world.setBlockState(pos, crop.withAge(age + 1), FLAGS);
				particles(world, ParticleTypes.HAPPY_VILLAGER, pos, 2);
			} else {
				world.setBlockState(pos, crop.withAge(0), FLAGS);
				sound(world, pos, SoundEvents.BLOCK_CROP_BREAK, 0.7f);
				particles(world, new BlockStateParticleEffect(ParticleTypes.BLOCK, state), pos, 10);
			}
		}
	}

	/** A pasture keeps its herd: lost animals are replaced over time. */
	private static void restock(ServerWorld world, BlockPos pos) {
		List<MobEntity> animals = world.getEntitiesByClass(MobEntity.class, new Box(pos).expand(5, 3, 5),
				mob -> mob instanceof net.minecraft.entity.passive.AnimalEntity);
		if (animals.isEmpty() || animals.size() >= 3) {
			return;
		}
		MobEntity parent = animals.get(0);
		MobEntity calf = (MobEntity) parent.getType().create(world);
		if (calf != null) {
			calf.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
			calf.setPersistent();
			world.spawnEntity(calf);
			particles(world, ParticleTypes.HEART, pos.up(), 3);
		}
	}

	private static void setLit(ServerWorld world, BlockPos pos, boolean lit) {
		BlockState state = world.getBlockState(pos);
		if (state.contains(Properties.LIT) && state.get(Properties.LIT) != lit) {
			world.setBlockState(pos, state.with(Properties.LIT, lit), FLAGS);
		}
	}

	private static void sound(ServerWorld world, BlockPos pos, SoundEvent sound, float volume) {
		world.playSound(null, pos, sound, SoundCategory.BLOCKS, volume, 0.85f + world.random.nextFloat() * 0.3f);
	}

	private static void particles(ServerWorld world, ParticleEffect effect, BlockPos pos, int count) {
		world.spawnParticles(effect, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, count, 0.3, 0.3, 0.3, 0.02);
	}
}
