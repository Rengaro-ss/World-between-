package com.worldbetween.entity.kament;

import com.worldbetween.entity.Arrival;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.kament.KamentBehavior;
import com.worldbetween.faction.kament.Kaments;
import com.worldbetween.hold.HoldLayout;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * The opening of Hell wakes the Kaments. For a day the earth shakes, again and again, hardest near the
 * peaks where they sleep; then the mountains crack open in several places at once and the stone walks.
 */
public final class KamentAwakening {
	private static final int QUAKE_TICKS = 140;
	private static final int DAY = 24000;

	private static long nextQuake = -1;
	private static long quakeEnds = -1;
	private static float burst;

	private KamentAwakening() {
	}

	/** Hell has opened: the sleepers stir. The places they will wake are chosen now, and the shaking begins. */
	public static void stir(MinecraftServer server) {
		WorldBetweenState state = WorldBetweenState.get(server);
		Faction faction = state.faction(FactionType.KAMENT);
		if (faction.hasArrived()) {
			return;
		}
		ServerWorld world = server.getOverworld();
		if (faction.knownSites().isEmpty()) {
			SimulationContext context = new SimulationContext(server, state);
			faction.knownSites().addAll(KamentBehavior.awakeningSites(context, world, world.getSpawnPos(),
					context.config().kamentAwakeningSites));
		}
		faction.setCounter("quake_until", world.getTime() + DAY);
		state.markDirty();
		nextQuake = server.getTicks() + 200;
		new SimulationContext(server, state).chronicle(FactionType.KAMENT, "chronicle.worldbetween.kament.quakes");
	}

	/** A short quake right now, for testing. */
	public static void quakeNow(MinecraftServer server, float strength) {
		nextQuake = server.getTicks();
		burst = strength;
	}

	/** The mountains open. Everyone hears from where; whoever is close sees the rock burst. */
	public static void awaken(MinecraftServer server, Faction faction) {
		ServerWorld world = server.getOverworld();
		faction.setCounter("quake_until", 0);
		quakeNow(server, 1.6f);
		List<BlockPos> gates = new ArrayList<>();
		for (Settlement settlement : faction.settlements()) {
			if (Kaments.kind(settlement) == Kaments.MOUNTAIN) {
				HoldLayout layout = HoldLayout.of(world, settlement);
				gates.add(layout.local().world(0, 2, -layout.exit()));
			}
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			BlockPos nearest = null;
			for (BlockPos gate : gates) {
				if (nearest == null || gate.getSquaredDistance(player.getBlockPos()) < nearest.getSquaredDistance(player.getBlockPos())) {
					nearest = gate;
				}
			}
			if (nearest == null) {
				continue;
			}
			double dx = nearest.getX() - player.getX();
			double dz = nearest.getZ() - player.getZ();
			double distance = Math.hypot(dx, dz);
			if (distance < 128) {
				player.sendMessage(Text.translatable("message.worldbetween.kament.awaken_near").formatted(Formatting.GRAY), false);
			} else {
				player.sendMessage(Text.translatable("message.worldbetween.kament.awaken_far",
						Text.translatable("direction.worldbetween." + Arrival.compass(dx, dz)), Math.max(50, Math.round(distance / 50) * 50),
						gates.size()).formatted(Formatting.GRAY), false);
			}
			Vec3d toward = new Vec3d(dx, 0, dz).normalize().multiply(16);
			sound(player, SoundEvents.ENTITY_WARDEN_EMERGE, player.getPos().add(toward), 1.5f, 0.5f);
		}
		for (BlockPos gate : gates) {
			if (world.isChunkLoaded(gate)) {
				world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, gate.getX() + 0.5, gate.getY() + 2, gate.getZ() + 0.5, 2, 2, 2, 2, 0);
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState()),
						gate.getX() + 0.5, gate.getY() + 2, gate.getZ() + 0.5, 300, 3, 3, 3, 0.4);
				world.playSound(null, gate, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 4f, 0.5f);
			}
		}
	}

	public static void onServerTick(MinecraftServer server) {
		long now = server.getTicks();
		ServerWorld world = server.getOverworld();
		Faction faction = WorldBetweenState.get(server).faction(FactionType.KAMENT);
		boolean shaking = faction.counter("quake_until") > world.getTime();
		if (shaking && nextQuake < 0) {
			nextQuake = now + 400;
		}
		if (nextQuake >= 0 && now >= nextQuake && quakeEnds < now) {
			quakeEnds = now + QUAKE_TICKS;
			if (shaking) {
				nextQuake = now + 1800 + world.random.nextInt(2400);
			} else {
				nextQuake = -1;
			}
			for (ServerPlayerEntity player : world.getPlayers()) {
				float strength = strength(faction, player);
				sound(player, SoundEvents.ENTITY_WARDEN_EMERGE, player.getPos().add(0, -6, 0), 0.6f + strength * 0.5f, 0.5f);
				if (!faction.hasEventHappened("quake_told_" + player.getUuid())) {
					faction.tryEvent("quake_told_" + player.getUuid(), 0, Long.MAX_VALUE);
					player.sendMessage(Text.translatable("message.worldbetween.kament.quake").formatted(Formatting.GRAY), false);
				}
			}
		}
		if (now < quakeEnds && now % 2 == 0) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (!player.isSpectator()) {
					shake(world, player, strength(faction, player) * (burst > 0 ? burst : 1f), now);
				}
			}
		} else if (now >= quakeEnds) {
			burst = 0;
		}
	}

	/** Stronger the closer to where they sleep: a tremor far off, a real quake under the peaks. */
	private static float strength(Faction faction, ServerPlayerEntity player) {
		double best = Double.MAX_VALUE;
		List<BlockPos> places = new ArrayList<>(faction.knownSites());
		faction.settlements().forEach(s -> places.add(s.pos()));
		for (BlockPos place : places) {
			best = Math.min(best, Math.sqrt(place.withY(0).getSquaredDistance(player.getBlockPos().withY(0))));
		}
		return best == Double.MAX_VALUE ? 0.3f : (float) (0.3 + 0.7 * Math.max(0, 1 - best / 1500));
	}

	private static void shake(ServerWorld world, ServerPlayerEntity player, float strength, long now) {
		Random random = world.random;
		double jolt = 0.07 * strength;
		player.addVelocity((random.nextDouble() - 0.5) * jolt, player.isOnGround() ? random.nextDouble() * 0.03 * strength : 0,
				(random.nextDouble() - 0.5) * jolt);
		player.velocityModified = true;
		BlockState dust = player.getBlockY() < 40 ? Blocks.DEEPSLATE.getDefaultState() : Blocks.STONE.getDefaultState();
		world.spawnParticles(player, new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, dust), true,
				player.getX(), player.getY() + 3, player.getZ(), Math.round(6 * strength), 4, 1, 4, 0);
		if (now % 20 == 0) {
			sound(player, random.nextBoolean() ? SoundEvents.BLOCK_DEEPSLATE_BREAK : SoundEvents.BLOCK_BASALT_BREAK,
					player.getPos().add(random.nextInt(9) - 4, -2, random.nextInt(9) - 4), 0.8f * strength, 0.5f);
		}
		// Underground, loose gravel and sand come down from the roof.
		if (now % 10 == 0 && !world.isSkyVisible(player.getBlockPos()) && random.nextFloat() < strength * 0.6f) {
			for (int i = 0; i < 6; i++) {
				BlockPos pos = player.getBlockPos().add(random.nextInt(9) - 4, 2 + random.nextInt(5), random.nextInt(9) - 4);
				BlockState state = world.getBlockState(pos);
				if (state.getBlock() instanceof FallingBlock && world.getBlockState(pos.down()).isAir()) {
					FallingBlockEntity.spawnFromBlock(world, pos, state);
					break;
				}
			}
		}
	}

	private static void sound(ServerPlayerEntity player, SoundEvent sound, Vec3d at, float volume, float pitch) {
		RegistryEntry<SoundEvent> entry = Registries.SOUND_EVENT.getEntry(sound);
		player.networkHandler.sendPacket(new PlaySoundS2CPacket(entry, SoundCategory.AMBIENT, at.x, at.y, at.z, volume, pitch,
				player.getRandom().nextLong()));
	}
}
