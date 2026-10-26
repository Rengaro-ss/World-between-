package com.worldbetween.visit;

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
import com.worldbetween.item.Ownership;
import com.worldbetween.item.WorldBetweenItems;
import com.worldbetween.sim.Clock;
import com.worldbetween.war.WarPresence;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The peoples come to the stranger's door. Whoever has done them a little wrong gets a word from a couple of
 * their soldiers; whoever has done them real harm wakes in the night to a warning they will not forget, and a
 * sign saying what comes next; whoever they have come to hate gets a war party that spares neither them nor
 * their house nor their chests. And a friend gets gifts. Each comes again, now and then, while things stay as
 * they are.
 */
public final class Visits {
	public enum Kind {
		WARN("warn", 3, 2),
		THREAT("threat", 4, 4),
		RAZE("raze", 7, 6),
		THANKS("thanks", 5, 2);

		private final String id;
		/** World days before the same people comes on the same errand again. */
		private final double gap;
		private final int party;

		Kind(String id, double gap, int party) {
			this.id = id;
			this.gap = gap;
			this.party = party;
		}

		public String id() {
			return id;
		}

		public static Kind byId(String id) {
			for (Kind kind : values()) {
				if (kind.id.equals(id)) {
					return kind;
				}
			}
			return null;
		}
	}

	/** Who comes for what, by the stranger's standing with them. */
	public static final int WARN_BELOW = -3;
	public static final int THREAT_BELOW = -20;
	public static final int RAZE_BELOW = -60;
	public static final int THANKS_FROM = 50;
	/** How far from their lands they come. */
	private static final int REACH = 3000;
	/** How far from home the stranger can be for the party to find them there. */
	private static final int AT_HOME = 48;
	private static final int INTERVAL = 20;
	private static final int TIMEOUT = 20 * 60 * 6;
	private static final int RAZE_BLOCKS = 160;

	private enum Stage { COMING, BUSY, LEAVING }

	private static final class Visit {
		final UUID player;
		final FactionType faction;
		final Kind kind;
		final BlockPos home;
		final String town;
		final List<UUID> party = new ArrayList<>();
		final long started;
		Stage stage = Stage.COMING;
		long stageTick;
		BlockPos away;
		int broken;

		Visit(UUID player, FactionType faction, Kind kind, BlockPos home, String town, long now) {
			this.player = player;
			this.faction = faction;
			this.kind = kind;
			this.home = home;
			this.town = town;
			this.started = now;
			this.stageTick = now;
		}
	}

	private static final Map<UUID, Visit> ACTIVE = new HashMap<>();

	private Visits() {
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % INTERVAL != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		long tick = server.getTicks();
		for (Visit visit : List.copyOf(ACTIVE.values())) {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(visit.player);
			if (player == null || player.getWorld() != world || tick - visit.started > TIMEOUT) {
				finish(world, visit);
				continue;
			}
			run(world, visit, player, tick);
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isSpectator() || ACTIVE.containsKey(player.getUuid())) {
				continue;
			}
			// Come to the sleeper at once; by day, after a while.
			if (player.isSleeping() ? player.getSleepTimer() >= 10 : tick % 200 == 0 && world.random.nextInt(4) == 0) {
				maybeStart(world, player);
			}
		}
	}

	/** Where the stranger lives: the bed they sleep in. */
	public static BlockPos home(ServerPlayerEntity player) {
		if (player.getSpawnPointPosition() == null || player.getSpawnPointDimension() != World.OVERWORLD) {
			return null;
		}
		return player.getSpawnPointPosition();
	}

	public static Kind kindFor(int reputation) {
		if (reputation <= RAZE_BELOW) {
			return Kind.RAZE;
		}
		if (reputation <= THREAT_BELOW) {
			return Kind.THREAT;
		}
		if (reputation <= WARN_BELOW) {
			return Kind.WARN;
		}
		return reputation >= THANKS_FROM ? Kind.THANKS : null;
	}

	private static void maybeStart(ServerWorld world, ServerPlayerEntity player) {
		BlockPos home = home(player);
		if (home == null) {
			return;
		}
		WorldBetweenState state = WorldBetweenState.get(world.getServer());
		long day = (long) Clock.now(world.getServer());
		String event = "visit_" + player.getUuid();
		// One people's visit a day at most, whoever they are.
		for (FactionType type : FactionType.values()) {
			Long last = state.faction(type).lastEvent(event);
			if (last != null && day - last < Clock.simDays(1)) {
				return;
			}
		}
		List<FactionType> types = new ArrayList<>(List.of(FactionType.values()));
		java.util.Collections.shuffle(types, new java.util.Random(world.random.nextLong()));
		for (FactionType type : types) {
			Faction faction = state.faction(type);
			Kind kind = kindFor(faction.reputation(player.getUuid()));
			if (kind == null || (kind == Kind.THREAT) != player.isSleeping()) {
				continue;
			}
			double distance = player.getBlockPos().getSquaredDistance(home);
			if (kind == Kind.THREAT ? distance > 12 * 12 : distance > AT_HOME * AT_HOME) {
				continue;
			}
			Settlement town = nearest(faction, home);
			if (town == null) {
				continue;
			}
			Long last = faction.lastEvent(event + "_" + kind.id());
			if (last != null && day - last < Clock.simDays(kind.gap)) {
				continue;
			}
			faction.tryEvent(event, day, 0);
			faction.tryEvent(event + "_" + kind.id(), day, 0);
			state.markDirty();
			start(world, player, type, kind, home, town.name(), town.pos());
			return;
		}
	}

	private static Settlement nearest(Faction faction, BlockPos home) {
		Settlement best = null;
		double bestDistance = (double) REACH * REACH;
		for (Settlement settlement : faction.settlements()) {
			if (settlement.dimension() != World.OVERWORLD) {
				continue;
			}
			double d = settlement.pos().withY(home.getY()).getSquaredDistance(home);
			if (d < bestDistance) {
				bestDistance = d;
				best = settlement;
			}
		}
		return best;
	}

	/** For testing: a people comes now on the given errand, whatever the stranger's standing. */
	public static boolean force(ServerPlayerEntity player, FactionType type, Kind kind) {
		ServerWorld world = player.getServerWorld();
		BlockPos home = home(player);
		if (home == null) {
			home = player.getBlockPos();
		}
		Faction faction = WorldBetweenState.get(world.getServer()).faction(type);
		Settlement town = faction.settlements().isEmpty() ? null : faction.settlements().get(0);
		Visit old = ACTIVE.get(player.getUuid());
		if (old != null) {
			finish(world, old);
		}
		start(world, player, type, kind, home, town == null ? "?" : town.name(), town == null ? home.add(200, 0, 0) : town.pos());
		return true;
	}

	private static void start(ServerWorld world, ServerPlayerEntity player, FactionType type, Kind kind, BlockPos home, String town, BlockPos from) {
		Visit visit = new Visit(player.getUuid(), type, kind, home, town, world.getServer().getTicks());
		// They come up the way from their own lands.
		double dx = from.getX() - home.getX();
		double dz = from.getZ() - home.getZ();
		double length = Math.max(1, Math.hypot(dx, dz));
		int distance = kind == Kind.THREAT ? 8 : kind == Kind.RAZE ? 36 : 24;
		BlockPos start = ground(world, home.add((int) (dx / length * distance), 0, (int) (dz / length * distance)), type);
		visit.away = ground(world, home.add((int) (dx / length * 64), 0, (int) (dz / length * 64)), type);
		Random random = world.random;
		int count = kind.party + (kind == Kind.RAZE && type == FactionType.NERIS ? 2 : 0);
		for (int i = 0; i < count; i++) {
			MobEntity mob = member(world, type, kind, i);
			if (mob == null) {
				continue;
			}
			BlockPos at = ground(world, start.add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2), type);
			mob.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
			WarPresence.adopt(mob.getUuid());
			world.spawnEntity(mob);
			visit.party.add(mob.getUuid());
		}
		ACTIVE.put(player.getUuid(), visit);
		if (kind == Kind.THREAT) {
			threaten(world, visit, player);
		} else if (kind == Kind.RAZE) {
			player.sendMessage(Text.translatable("visit.worldbetween." + type.id() + ".raze.coming", town).formatted(Formatting.RED), false);
			world.playSound(null, start, type == FactionType.KAMENT ? SoundEvents.EVENT_RAID_HORN.value() : SoundEvents.EVENT_RAID_HORN.value(),
					SoundCategory.HOSTILE, 4f, type == FactionType.KAMENT ? 0.6f : type == FactionType.ARKI ? 1.4f : 1f);
		}
	}

	private static MobEntity member(ServerWorld world, FactionType type, Kind kind, int index) {
		String talk = "visit.worldbetween." + type.id() + "." + kind.id() + ".talk";
		switch (type) {
			case NERIS -> {
				NerisEntity neris = WorldBetweenEntities.NERIS.create(world);
				if (neris == null) {
					return null;
				}
				if (kind == Kind.THANKS) {
					neris.setRole(index == 0 ? NerisRole.BARON : NerisRole.CITIZEN);
				} else {
					neris.setRole(NerisRole.GUARD);
					neris.setArcher(kind == Kind.RAZE && index % 3 == 2);
				}
				neris.setSpeech(talk);
				return neris;
			}
			case KAMENT -> {
				KamentEntity kament = WorldBetweenEntities.KAMENT.create(world);
				if (kament == null) {
					return null;
				}
				kament.setRole(kind == Kind.THANKS ? (index == 0 ? KamentRole.SCULPTOR : KamentRole.WORKER) : KamentRole.GUARD);
				KamentMaterial[] stones = KamentMaterial.values();
				kament.setMaterial(stones[(index * 3 + 1) % stones.length]);
				kament.setSpeech(talk);
				return kament;
			}
			case ARKI -> {
				ArkiEntity arki = WorldBetweenEntities.ARKI.create(world);
				if (arki == null) {
					return null;
				}
				arki.setRole(kind == Kind.THANKS ? (index == 0 ? ArkiRole.ASTRONOMER : ArkiRole.GARDENER) : ArkiRole.KEEPER);
				arki.setSpeech(talk);
				return arki;
			}
		}
		return null;
	}

	private static void run(ServerWorld world, Visit visit, ServerPlayerEntity player, long tick) {
		List<MobEntity> party = party(world, visit);
		if (party.isEmpty()) {
			if (visit.kind == Kind.RAZE && visit.stage != Stage.LEAVING) {
				player.sendMessage(Text.translatable("visit.worldbetween.raze.beaten").formatted(Formatting.GRAY), false);
			}
			finish(world, visit);
			return;
		}
		MobEntity leader = party.get(0);
		boolean home = !player.isSpectator() && player.getBlockPos().getSquaredDistance(visit.home) < AT_HOME * AT_HOME * 4;
		BlockPos goal = home ? player.getBlockPos() : visit.home;
		switch (visit.stage) {
			case COMING -> {
				party.forEach(mob -> guide(mob, goal));
				if (leader.squaredDistanceTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5) < (visit.faction == FactionType.ARKI ? 81 : 36)) {
					arrive(world, visit, player, leader);
					visit.stage = visit.kind == Kind.RAZE ? Stage.BUSY : Stage.LEAVING;
					visit.stageTick = tick;
				} else if (tick - visit.stageTick > 20 * 90) {
					// They cannot get there: they shout, and leave it there.
					arrive(world, visit, player, leader);
					visit.stage = visit.kind == Kind.RAZE ? Stage.BUSY : Stage.LEAVING;
					visit.stageTick = tick;
				}
			}
			case BUSY -> raze(world, visit, player, party, tick);
			case LEAVING -> {
				// A word before going, for whoever stands there.
				if (tick - visit.stageTick < 20 * 6) {
					party.forEach(mob -> {
						guide(mob, null);
						mob.getLookControl().lookAt(player);
					});
					return;
				}
				party.forEach(mob -> guide(mob, visit.away));
				boolean gone = party.stream().allMatch(mob -> mob.squaredDistanceTo(player) > 40 * 40);
				if (gone || tick - visit.stageTick > 20 * 60) {
					finish(world, visit);
				}
			}
		}
	}

	private static void arrive(ServerWorld world, Visit visit, ServerPlayerEntity player, MobEntity leader) {
		switch (visit.kind) {
			case WARN -> {
				say(player, leader, "visit.worldbetween." + visit.faction.id() + ".warn.0", visit.town);
				say(player, leader, "visit.worldbetween." + visit.faction.id() + ".warn.1");
			}
			case THANKS -> {
				say(player, leader, "visit.worldbetween." + visit.faction.id() + ".thanks.0", visit.town);
				BlockPos at = gifts(world, visit, player);
				if (at != null) {
					say(player, leader, "visit.worldbetween." + visit.faction.id() + ".thanks.1");
				}
			}
			case RAZE -> say(player, leader, "visit.worldbetween." + visit.faction.id() + ".raze.0", visit.town);
			default -> {
			}
		}
	}

	private static void say(ServerPlayerEntity player, Entity speaker, String key, Object... args) {
		Text name = speaker.getCustomName() != null ? speaker.getCustomName() : speaker.getName();
		player.sendMessage(Text.empty().append(name.copy().formatted(Formatting.GOLD)).append(Text.literal(": ").formatted(Formatting.GRAY))
				.append(Text.translatable(key, args).formatted(Formatting.WHITE)), false);
	}

	private static void guide(MobEntity mob, BlockPos to) {
		if (mob instanceof NerisEntity neris) {
			neris.setDestination(to);
		} else if (mob instanceof KamentEntity kament) {
			kament.setDestination(to);
		} else if (mob instanceof ArkiEntity arki) {
			arki.setDestination(to == null ? null : to.up(3));
		}
		if (to == null) {
			mob.getNavigation().stop();
		}
	}

	private static List<MobEntity> party(ServerWorld world, Visit visit) {
		List<MobEntity> party = new ArrayList<>();
		for (UUID id : visit.party) {
			if (world.getEntity(id) instanceof MobEntity mob && mob.isAlive()) {
				party.add(mob);
			}
		}
		return party;
	}

	private static void finish(ServerWorld world, Visit visit) {
		ACTIVE.remove(visit.player);
		for (UUID id : visit.party) {
			Entity entity = world.getEntity(id);
			if (entity != null && entity.isAlive()) {
				entity.discard();
			}
			WarPresence.release(id);
		}
	}

	private static BlockPos ground(ServerWorld world, BlockPos at, FactionType type) {
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
		return new BlockPos(at.getX(), y + (type == FactionType.ARKI ? 3 : 0), at.getZ());
	}

	// ---------------------------------------------------------------- the threat, while the stranger sleeps

	private static void threaten(ServerWorld world, Visit visit, ServerPlayerEntity player) {
		BlockPos bed = player.getBlockPos();
		String type = visit.faction.id();
		switch (visit.faction) {
			case NERIS -> burn(world, bed);
			case KAMENT -> dome(world, bed);
			case ARKI -> crash(world, bed);
		}
		sign(world, visit, bed);
		player.wakeUp(false, true);
		player.sendMessage(Text.translatable("visit.worldbetween." + type + ".threat.wake").formatted(Formatting.RED, Formatting.ITALIC), true);
		player.sendMessage(Text.translatable("visit.worldbetween." + type + ".threat.wake").formatted(Formatting.RED), false);
		// And they go, quickly, without a word.
		visit.stage = Stage.LEAVING;
		visit.stageTick = world.getServer().getTicks() - 20 * 6;
	}

	/**
	 * The kingdom's soldiers set the house alight from outside, in a few places, and let the smoke wake the
	 * sleeper: whoever is quick puts it out.
	 */
	private static void burn(ServerWorld world, BlockPos bed) {
		Random random = world.random;
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos pos : BlockPos.iterate(bed.add(-9, -2, -9), bed.add(9, 6, 9))) {
			BlockState state = world.getBlockState(pos);
			if (!isWooden(state) || pos.getSquaredDistance(bed) < 9) {
				continue;
			}
			for (Direction side : Direction.values()) {
				BlockPos air = pos.offset(side);
				if (world.getBlockState(air).isAir() && air.getSquaredDistance(bed) >= 9 && world.isSkyVisible(air)) {
					spots.add(air.toImmutable());
					break;
				}
			}
		}
		if (spots.isEmpty()) {
			// No wood to burn: the grass around the house, then.
			for (int i = 0; i < 40 && spots.size() < 4; i++) {
				BlockPos pos = ground(world, bed.add(random.nextInt(13) - 6, 0, random.nextInt(13) - 6), FactionType.NERIS);
				if (pos.getSquaredDistance(bed) >= 9 && world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isOpaqueFullCube(world, pos.down())) {
					spots.add(pos);
				}
			}
		}
		java.util.Collections.shuffle(spots, new java.util.Random(random.nextLong()));
		for (BlockPos spot : spots.subList(0, Math.min(2 + random.nextInt(2), spots.size()))) {
			world.setBlockState(spot, AbstractFireBlock.getState(world, spot));
		}
		world.playSound(null, bed, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1f, 1f);
	}

	private static boolean isWooden(BlockState state) {
		return state.isIn(BlockTags.PLANKS) || state.isIn(BlockTags.LOGS_THAT_BURN) || state.isIn(BlockTags.WOODEN_STAIRS)
				|| state.isIn(BlockTags.WOODEN_SLABS) || state.isIn(BlockTags.WOODEN_FENCES) || state.isOf(Blocks.BOOKSHELF);
	}

	/** The mountain's soldiers wall the sleeper in under a dome of stone, house and all. */
	private static void dome(ServerWorld world, BlockPos bed) {
		Random random = world.random;
		double radius = 4.5;
		for (BlockPos pos : BlockPos.iterate(bed.add(-5, -1, -5), bed.add(5, 5, 5))) {
			double dx = pos.getX() - bed.getX();
			double dy = Math.max(0, pos.getY() - bed.getY());
			double dz = pos.getZ() - bed.getZ();
			double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
			boolean shell = r >= radius - 1 && r < radius;
			boolean floor = pos.getY() == bed.getY() - 1 && r < radius;
			if (!shell && !floor) {
				continue;
			}
			BlockState there = world.getBlockState(pos);
			if (there.isAir() || there.isReplaceable()) {
				world.setBlockState(pos, random.nextInt(5) == 0 ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : Blocks.COBBLESTONE.getDefaultState());
			}
		}
		world.playSound(null, bed, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 2f, 0.5f);
		world.spawnParticles(ParticleTypes.POOF, bed.getX() + 0.5, bed.getY() + 1, bed.getZ() + 0.5, 30, 2, 1, 2, 0.02);
	}

	/** The Arki come down on the roof in a comet capsule, and are off again before the sleeper is up. */
	private static void crash(ServerWorld world, BlockPos bed) {
		Random random = world.random;
		// Where the capsule comes down: beside the bed, through whatever roof there is.
		BlockPos landing = null;
		for (Direction side : Direction.Type.HORIZONTAL.getShuffled(random)) {
			BlockPos at = bed.offset(side, 2);
			if (world.getBlockState(at).isReplaceable() && !world.getBlockState(at.down()).isReplaceable()) {
				landing = at;
				break;
			}
		}
		if (landing == null) {
			landing = bed.offset(Direction.Type.HORIZONTAL.random(random), 2);
		}
		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, landing.getX(), landing.getZ());
		for (int y = landing.getY() + 2; y <= Math.min(top, landing.getY() + 16); y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos pos = new BlockPos(landing.getX() + dx, y, landing.getZ() + dz);
					BlockState state = world.getBlockState(pos);
					if (!state.isAir() && state.getHardness(world, pos) >= 0 && (Math.abs(dx) + Math.abs(dz) < 2 || random.nextBoolean())) {
						world.breakBlock(pos, true);
					}
				}
			}
		}
		// The capsule itself, spent: a shell of amethyst and calcite, still warm.
		world.setBlockState(landing, Blocks.AMETHYST_BLOCK.getDefaultState());
		world.setBlockState(landing.up(), Blocks.PURPLE_STAINED_GLASS.getDefaultState());
		world.setBlockState(landing.up(2), Blocks.AMETHYST_CLUSTER.getDefaultState());
		for (Direction side : Direction.Type.HORIZONTAL) {
			if (world.getBlockState(landing.offset(side)).isReplaceable() && random.nextInt(3) == 0) {
				world.setBlockState(landing.offset(side), Blocks.CALCITE.getDefaultState());
			}
		}
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, landing.getX() + 0.5, landing.getY() + 2, landing.getZ() + 0.5, 1, 0, 0, 0, 0);
		world.spawnParticles(ParticleTypes.END_ROD, landing.getX() + 0.5, landing.getY() + 8, landing.getZ() + 0.5, 60, 0.4, 8, 0.4, 0.02);
		world.playSound(null, landing, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 3f, 0.8f);
		world.playSound(null, landing, SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.BLOCKS, 3f, 0.6f);
	}

	/** The warning, in so many words, beside the bed. */
	private static void sign(ServerWorld world, Visit visit, BlockPos bed) {
		BlockPos at = null;
		for (BlockPos pos : BlockPos.iterate(bed.add(-2, -1, -2), bed.add(2, 1, 2))) {
			if (pos.getSquaredDistance(bed) >= 2 && world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())
					&& !world.getBlockState(pos.up()).isOf(Blocks.FIRE)) {
				at = pos.toImmutable();
				break;
			}
		}
		if (at == null) {
			return;
		}
		BlockState sign = switch (visit.faction) {
			case NERIS -> Blocks.OAK_SIGN.getDefaultState();
			case KAMENT -> Blocks.SPRUCE_SIGN.getDefaultState();
			case ARKI -> Blocks.CHERRY_SIGN.getDefaultState();
		};
		float yaw = (float) (Math.toDegrees(Math.atan2(bed.getX() - at.getX(), at.getZ() - bed.getZ())));
		world.setBlockState(at, sign.with(SignBlock.ROTATION, MathHelper.floor((180 + yaw) * 16 / 360 + 0.5) & 15));
		BlockEntity entity = world.getBlockEntity(at);
		if (entity instanceof SignBlockEntity board) {
			String key = "visit.worldbetween." + visit.faction.id() + ".sign.";
			Text[] lines = {Text.translatable(key + 0), Text.translatable(key + 1), Text.translatable(key + 2), Text.literal("— " + visit.town)};
			Text[] empty = {Text.empty(), Text.empty(), Text.empty(), Text.empty()};
			board.setText(new SignText(lines, lines, visit.faction == FactionType.NERIS ? DyeColor.RED : visit.faction == FactionType.KAMENT ? DyeColor.BLACK : DyeColor.PURPLE,
					visit.faction == FactionType.ARKI), true);
			board.setText(new SignText(empty, empty, DyeColor.BLACK, false), false);
			board.setWaxed(true);
			board.markDirty();
			world.updateListeners(at, world.getBlockState(at), world.getBlockState(at), 3);
		}
	}

	// ---------------------------------------------------------------- the war party

	private static void raze(ServerWorld world, Visit visit, ServerPlayerEntity player, List<MobEntity> party, long tick) {
		boolean hunted = !player.isCreative() && !player.isSpectator() && player.isAlive() && player.squaredDistanceTo(visit.home.toCenterPos()) < 64 * 64;
		Random random = world.random;
		for (int i = 0; i < party.size(); i++) {
			MobEntity mob = party.get(i);
			if (hunted && (i % 2 == 0 || mob.squaredDistanceTo(player) < 16 * 16)) {
				// Half of them for the stranger, the rest for the house.
				mob.setTarget(player);
				continue;
			}
			mob.setTarget(null);
			if (visit.broken >= RAZE_BLOCKS) {
				guide(mob, player.getBlockPos());
				continue;
			}
			BlockPos near = mob.getBlockPos();
			if (near.getSquaredDistance(visit.home) > 18 * 18) {
				guide(mob, visit.home);
				continue;
			}
			// Whatever the stranger made or keeps: break it.
			BlockPos found = null;
			for (int t = 0; t < 24 && found == null; t++) {
				BlockPos pos = near.add(random.nextInt(9) - 4, random.nextInt(6) - 1, random.nextInt(9) - 4);
				if (isPlayerMade(world.getBlockState(pos))) {
					found = pos;
				}
			}
			if (found == null) {
				BlockPos wander = visit.home.add(random.nextInt(21) - 10, 0, random.nextInt(21) - 10);
				guide(mob, ground(world, wander, visit.faction));
				continue;
			}
			guide(mob, found);
			if (mob.squaredDistanceTo(found.toCenterPos()) < 4.5 * 4.5) {
				mob.swingHand(net.minecraft.util.Hand.MAIN_HAND);
				if (visit.faction == FactionType.ARKI) {
					world.spawnParticles(ParticleTypes.END_ROD, found.getX() + 0.5, found.getY() + 0.5, found.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
				}
				world.breakBlock(found, true, mob);
				visit.broken++;
			}
		}
		if (!player.isAlive() && visit.broken >= RAZE_BLOCKS) {
			visit.stage = Stage.LEAVING;
			visit.stageTick = tick;
		}
	}

	/** What a stranger builds and keeps, as against what grows: planks, glass, doors, beds, chests, furnaces... */
	private static boolean isPlayerMade(BlockState state) {
		if (state.isAir() || state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.LEAVES)) {
			return false;
		}
		return state.isIn(BlockTags.PLANKS) || state.isIn(BlockTags.WOODEN_STAIRS) || state.isIn(BlockTags.WOODEN_SLABS)
				|| state.isIn(BlockTags.WOODEN_FENCES) || state.isIn(BlockTags.FENCE_GATES) || state.isIn(BlockTags.DOORS)
				|| state.isIn(BlockTags.TRAPDOORS) || state.isIn(BlockTags.BEDS) || state.isIn(BlockTags.WOOL) || state.isIn(BlockTags.WOOL_CARPETS)
				|| state.isIn(BlockTags.IMPERMEABLE) || state.isIn(BlockTags.ALL_SIGNS) || state.isIn(BlockTags.SHULKER_BOXES)
				|| state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST) || state.isOf(Blocks.BARREL) || state.isOf(Blocks.FURNACE)
				|| state.isOf(Blocks.BLAST_FURNACE) || state.isOf(Blocks.SMOKER) || state.isOf(Blocks.CRAFTING_TABLE) || state.isOf(Blocks.TORCH)
				|| state.isOf(Blocks.WALL_TORCH) || state.isOf(Blocks.LANTERN) || state.isOf(Blocks.BOOKSHELF) || state.isOf(Blocks.BRICKS)
				|| state.isOf(Blocks.STONE_BRICKS) || state.isOf(Blocks.GLASS_PANE) || state.isOf(Blocks.ANVIL) || state.isOf(Blocks.ENCHANTING_TABLE)
				|| state.isOf(Blocks.FLOWER_POT) || state.isOf(Blocks.LADDER) || state.isOf(Blocks.COMPOSTER)
				|| state.isOf(Blocks.FARMLAND) || state.isOf(Blocks.SMOOTH_STONE) || state.isOf(Blocks.POLISHED_ANDESITE);
	}

	// ---------------------------------------------------------------- the gifts

	private static BlockPos gifts(ServerWorld world, Visit visit, ServerPlayerEntity player) {
		BlockPos at = null;
		BlockPos center = player.getBlockPos();
		for (BlockPos pos : BlockPos.iterate(center.add(-2, -1, -2), center.add(2, 1, 2))) {
			if (pos.getSquaredDistance(center) >= 2 && world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
				at = pos.toImmutable();
				break;
			}
		}
		if (at == null) {
			return null;
		}
		world.setBlockState(at, visit.faction == FactionType.NERIS ? Blocks.BARREL.getDefaultState() : Blocks.CHEST.getDefaultState());
		if (!(world.getBlockEntity(at) instanceof Inventory box)) {
			return null;
		}
		int reputation = WorldBetweenState.get(world.getServer()).faction(visit.faction).reputation(player.getUuid());
		boolean dear = reputation >= 80;
		Random random = world.random;
		List<ItemStack> gifts = new ArrayList<>();
		switch (visit.faction) {
			case NERIS -> {
				gifts.add(new ItemStack(Items.BREAD, 8 + random.nextInt(8)));
				gifts.add(new ItemStack(Items.COOKED_BEEF, 4 + random.nextInt(4)));
				gifts.add(new ItemStack(Items.IRON_INGOT, 3 + random.nextInt(4)));
				gifts.add(new ItemStack(Items.ARROW, 16));
				gifts.add(new ItemStack(Items.IRON_NUGGET, 10 + random.nextInt(20)));
				if (dear) {
					gifts.add(new ItemStack(random.nextBoolean() ? Items.IRON_SWORD : Items.BOW));
					gifts.add(new ItemStack(Items.GOLDEN_APPLE));
				}
			}
			case KAMENT -> {
				gifts.add(new ItemStack(WorldBetweenItems.FLINT_SHARD, 12 + random.nextInt(12)));
				gifts.add(new ItemStack(Items.IRON_INGOT, 4 + random.nextInt(4)));
				gifts.add(new ItemStack(Items.COAL, 12 + random.nextInt(8)));
				gifts.add(new ItemStack(Items.LAPIS_LAZULI, 6 + random.nextInt(6)));
				gifts.add(new ItemStack(WorldBetweenItems.HEWN_GRANITE, 4));
				if (dear) {
					gifts.add(new ItemStack(random.nextBoolean() ? WorldBetweenItems.HEARTSTONE : WorldBetweenItems.BLANK_AMULET));
					gifts.add(new ItemStack(Items.DIAMOND, 1 + random.nextInt(2)));
				}
			}
			case ARKI -> {
				gifts.add(new ItemStack(Items.AMETHYST_SHARD, 10 + random.nextInt(8)));
				gifts.add(new ItemStack(Items.GLOWSTONE_DUST, 6 + random.nextInt(6)));
				gifts.add(new ItemStack(Items.PRISMARINE_CRYSTALS, 4 + random.nextInt(4)));
				gifts.add(new ItemStack(Items.COOKED_COD, 6));
				if (dear) {
					gifts.add(new ItemStack(Items.ENDER_PEARL, 2));
					gifts.add(new ItemStack(Items.EXPERIENCE_BOTTLE, 6));
				}
			}
		}
		for (int i = 0; i < gifts.size() && i < box.size(); i++) {
			box.setStack(i, Ownership.markBought(gifts.get(i), visit.faction));
		}
		world.playSound(null, at, SoundEvents.BLOCK_BARREL_CLOSE, SoundCategory.BLOCKS, 1f, 1f);
		return at;
	}
}
