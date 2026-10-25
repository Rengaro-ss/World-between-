package com.worldbetween.entity;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.court.Court;
import com.worldbetween.faction.court.Noble;
import com.worldbetween.faction.seekers.Seeker;
import com.worldbetween.faction.seekers.Seekers;
import com.worldbetween.town.Blueprints;
import com.worldbetween.town.Canvas;
import com.worldbetween.town.Ground;
import com.worldbetween.town.TownFeatures;
import com.worldbetween.town.TownLayout;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The coming of Neris. Everyone in the world sees the sky flare in the portal's direction. Whoever is near
 * enough sees the portal burn, the founders walk out of it one by one, the king among them, and the portal
 * crack and die behind them: there is no way back.
 */
public final class Arrival {
	/** Ticks the portal keeps burning for a player to come and see it. */
	private static final int WINDOW = 12000;
	private static final double SEE = 112;
	private static final int WALKERS = 8;
	private static final int SPAWN_EVERY = 30;
	private static final int FIRST_WALKER = 60;

	private static final class Scene {
		final Faction faction;
		final Settlement camp;
		final BlockPos portal;
		final long start;
		long opened = -1;
		long collapsed = -1;
		int spawned;
		final List<String> slots = new ArrayList<>();

		Scene(Faction faction, Settlement camp, BlockPos portal, long start) {
			this.faction = faction;
			this.camp = camp;
			this.portal = portal;
			this.start = start;
		}
	}

	private static Scene scene;

	private Arrival() {
	}

	/** The founders have come through: the portal is placed, the world is told, and the scene waits for a witness. */
	public static void begin(MinecraftServer server, Faction faction, Settlement camp) {
		ServerWorld world = server.getOverworld();
		BlockPos portal = portalSite(world, camp);
		faction.setPortal(portal);
		scene = new Scene(faction, camp, portal, server.getTicks());
		announce(world, portal);
		// Hell has opened, and deep under the mountains something old hears it.
		com.worldbetween.entity.kament.KamentAwakening.stir(server);
	}

	/** Stages the scene again at the existing portal, for a witness who missed it. Returns the portal, or null. */
	public static BlockPos replay(MinecraftServer server, Faction faction) {
		if (faction.portal() == null || faction.settlements().isEmpty()) {
			return null;
		}
		scene = new Scene(faction, faction.settlements().get(0), faction.portal(), server.getTicks());
		return faction.portal();
	}

	/** Whether the founders of this camp are still walking in from the portal. */
	public static boolean isPlaying(int settlement) {
		return scene != null && scene.camp.id() == settlement && scene.opened >= 0;
	}

	/** The portal stands a short walk out from the camp, behind its hall, on dry and level ground. */
	private static BlockPos portalSite(ServerWorld world, Settlement camp) {
		TownLayout layout = TownLayout.of(world, camp);
		Direction back = layout.hallSide();
		for (Direction side : new Direction[]{back, back.rotateYClockwise(), back.rotateYCounterclockwise()}) {
			for (int d = 90; d <= 120; d += 10) {
				BlockPos p = layout.center().offset(side, d);
				int x = p.getX();
				int z = p.getZ();
				if (Ground.isWater(world, x, z)) {
					continue;
				}
				int min = Integer.MAX_VALUE;
				int max = Integer.MIN_VALUE;
				for (int[] o : new int[][]{{0, 0}, {-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
					int h = Ground.floor(world, x + o[0], z + o[1]);
					min = Math.min(min, h);
					max = Math.max(max, h);
				}
				if (max - min <= 3) {
					return new BlockPos(x, Ground.floor(world, x, z), z);
				}
			}
		}
		BlockPos p = layout.center().offset(back, 100);
		return p.withY(Ground.floor(world, p.getX(), p.getZ()));
	}

	/** Everyone hears thunder from the portal's direction and is told roughly where it was. */
	private static void announce(ServerWorld world, BlockPos portal) {
		for (ServerPlayerEntity player : world.getPlayers()) {
			double dx = portal.getX() - player.getX();
			double dz = portal.getZ() - player.getZ();
			double distance = Math.hypot(dx, dz);
			if (distance < SEE) {
				continue; // they will see it for themselves
			}
			long rounded = Math.max(50, Math.round(distance / 50) * 50);
			player.sendMessage(Text.translatable("message.worldbetween.arrival.far",
					Text.translatable("direction.worldbetween." + compass(dx, dz)), rounded).formatted(Formatting.RED), false);
			Vec3d toward = new Vec3d(dx, 0, dz).normalize().multiply(16);
			player.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER),
					SoundCategory.WEATHER, player.getX() + toward.x, player.getY() + 8, player.getZ() + toward.z, 1.2f, 0.5f,
					world.random.nextLong()));
		}
	}

	/** One of eight winds, north being towards negative Z. */
	public static String compass(double dx, double dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		int sector = Math.floorMod((int) Math.round(angle / 45), 8);
		return new String[]{"n", "ne", "e", "se", "s", "sw", "w", "nw"}[sector];
	}

	public static void onServerTick(MinecraftServer server) {
		Scene s = scene;
		if (s == null || server.getTicks() % 5 != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		long now = server.getTicks();
		BlockPos origin = s.portal.withY(Ground.floor(world, s.portal.getX(), s.portal.getZ()));
		Canvas.Local local = new Canvas().at(origin, TownFeatures.facingToward(origin, s.camp.pos()));
		if (s.opened < 0) {
			if (now - s.start > WINDOW) {
				scene = null;
			} else if (world.isChunkLoaded(origin) && near(world, origin, SEE)
					&& world.getBlockState(local.world(-1, 1, 0)).isOf(Blocks.OBSIDIAN)) {
				open(world, s, local);
				s.opened = now;
			}
			return;
		}
		long t = now - s.opened;
		BlockPos camp = TownLayout.of(world, s.camp).center().up();
		if (s.collapsed < 0) {
			Vec3d middle = middle(local);
			world.spawnParticles(ParticleTypes.PORTAL, middle.x, middle.y, middle.z, 40, 1.0, 1.5, 1.0, 0.6);
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, middle.x, middle.y + 2, middle.z, 4, 0.8, 0.5, 0.8, 0.02);
			if (t % 40 == 0) {
				world.playSound(null, origin, SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.BLOCKS, 1.5f, 0.6f);
			}
			if (s.spawned < WALKERS && t >= FIRST_WALKER + (long) s.spawned * SPAWN_EVERY) {
				walkOut(world, s, local, camp, s.spawned++);
			}
			if (s.spawned >= WALKERS && t >= FIRST_WALKER + (long) WALKERS * SPAWN_EVERY + 100) {
				collapse(world, s, local, origin);
				s.collapsed = now;
			}
		}
		// The founders walk to the camp and become part of it there.
		s.slots.removeIf(slot -> {
			NerisEntity walker = NerisPresence.entity(world, slot);
			if (walker == null) {
				return true;
			}
			if (walker.getBlockPos().getSquaredDistance(camp) < 36) {
				NerisPresence.discard(world, slot);
				return true;
			}
			return false;
		});
		if (s.collapsed >= 0 && (s.slots.isEmpty() || now - s.collapsed > 2400)) {
			s.slots.forEach(slot -> NerisPresence.discard(world, slot));
			scene = null;
		}
	}

	private static void open(ServerWorld world, Scene s, Canvas.Local local) {
		Direction.Axis axis = local.dir(Direction.EAST).getAxis();
		for (int[] p : Blueprints.PORTAL_INSIDE) {
			world.setBlockState(local.world(p[0], p[1], 0), Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, axis),
					Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		}
		BlockPos at = local.world(0, 1, 0);
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
		if (bolt != null) {
			bolt.setCosmetic(true);
			bolt.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(local.world(4, 1, 2)));
			world.spawnEntity(bolt);
		}
		world.playSound(null, at, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 4f, 0.7f);
		world.playSound(null, at, SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.BLOCKS, 2f, 0.8f);
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(Vec3d.of(at)) < SEE * SEE) {
				player.sendMessage(Text.translatable("message.worldbetween.arrival.near").formatted(Formatting.RED), false);
			}
		}
	}

	/** One of the founders steps out of the portal and makes for the camp. */
	private static void walkOut(ServerWorld world, Scene s, Canvas.Local local, BlockPos camp, int index) {
		Faction faction = s.faction;
		List<Seeker> seekers = faction.seekers().stream().filter(Seeker::isAlive).toList();
		Noble king = faction.king();
		NerisRole role;
		if (index < 2 && index < seekers.size()) {
			role = NerisRole.SEEKER;
		} else if (index == 2 && king != null) {
			role = NerisRole.KING;
		} else {
			role = index % 2 == 0 ? NerisRole.WORKER : NerisRole.CITIZEN;
		}
		String slot = "arrival:" + index;
		NerisEntity walker = NerisPresence.spawn(world, slot, role, local.world(index % 2, 1, -1));
		if (walker == null) {
			return;
		}
		s.slots.add(slot);
		walker.setDestination(camp);
		walker.setSpeech("speech.worldbetween.arrival." + Math.min(index, 3));
		switch (role) {
			case SEEKER -> {
				Seeker seeker = seekers.get(index);
				walker.setCustomName(Text.translatable("entity.worldbetween.seeker_named", seeker.name()));
				walker.setOnDeath(source -> Seekers.killed(world.getServer(), faction, seeker, walker.getBlockPos(), NerisEntity.killer(source)));
			}
			case KING -> {
				walker.setCustomName(Text.translatable("entity.worldbetween.king_named", king.displayName()));
				walker.setOnDeath(source -> Court.slain(world.getServer(), faction, king, NerisEntity.killer(source)));
			}
			default -> {
				if (role == NerisRole.WORKER) {
					walker.setTool(index % 4 == 0 ? Items.IRON_PICKAXE : Items.IRON_AXE);
				}
				walker.setOnDeath(source -> {
					ServerPlayerEntity killer = NerisEntity.killer(source);
					if (killer != null) {
						faction.changeReputation(killer.getUuid(), -10);
						faction.declareCriminal(killer.getUuid(), WorldBetweenState.get(world.getServer()).day());
						s.camp.setPopulation(s.camp.population() - 1);
					}
				});
			}
		}
		world.spawnParticles(ParticleTypes.FLAME, walker.getX(), walker.getY() + 1, walker.getZ(), 12, 0.3, 0.6, 0.3, 0.02);
		world.playSound(null, walker.getBlockPos(), SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.BLOCKS, 0.25f, 1.4f);
	}

	/** The last of them is through: the portal cracks and goes dark. There is no way back. */
	private static void collapse(ServerWorld world, Scene s, Canvas.Local local, BlockPos origin) {
		for (int[] p : Blueprints.PORTAL_INSIDE) {
			world.setBlockState(local.world(p[0], p[1], 0), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
		}
		Vec3d middle = middle(local);
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, middle.x, middle.y, middle.z, 1, 0, 0, 0, 0);
		world.spawnParticles(ParticleTypes.LAVA, middle.x, middle.y, middle.z, 30, 1.2, 1.2, 1.2, 0.2);
		world.playSound(null, origin, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 2f, 0.6f);
		world.playSound(null, origin, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 2f, 0.5f);
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(middle) < SEE * SEE) {
				player.sendMessage(Text.translatable("message.worldbetween.arrival.collapse").formatted(Formatting.GRAY), false);
			}
		}
	}

	private static Vec3d middle(Canvas.Local local) {
		return Vec3d.ofCenter(local.world(0, 3, 0)).add(Vec3d.ofCenter(local.world(1, 3, 0))).multiply(0.5);
	}

	private static boolean near(ServerWorld world, BlockPos pos, double distance) {
		for (PlayerEntity player : world.getPlayers()) {
			if (!player.isSpectator() && player.squaredDistanceTo(Vec3d.ofCenter(pos)) < distance * distance) {
				return true;
			}
		}
		return false;
	}
}
