package com.worldbetween.entity.arki;

import com.worldbetween.entity.Arrival;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.arki.ArkiBehavior;
import com.worldbetween.faction.arki.Comet;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
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
import org.joml.Vector3f;

/**
 * The night sky over the Arki. Comets the simulation has called come down one by one after dark, blue and
 * violet streaks across the sky ending in a crash and a column of spray; capsules of help arc from one
 * colony to another. Everyone hears where a new colony came down.
 */
public final class ArkiSky {
	private static final int COMET_TICKS = 120;
	private static final int CAPSULE_TICKS = 90;
	private static final int GAP = 400;
	private static final int STORM_GAP = 30;
	private static final double SEEN = 600;
	private static final ParticleEffect HEAD = new DustParticleEffect(new Vector3f(0.35f, 0.45f, 1f), 4f);
	private static final ParticleEffect TAIL = new DustParticleEffect(new Vector3f(0.7f, 0.3f, 1f), 3f);
	private static final ParticleEffect CAPSULE = new DustParticleEffect(new Vector3f(0.8f, 0.7f, 1f), 2f);

	private static Comet flying;
	private static Vec3d start;
	private static Vec3d end;
	private static int elapsed;
	private static long lastLanding = -GAP;
	private static boolean forced;
	private static final java.util.Set<String> stormWarned = new java.util.HashSet<>();

	private ArkiSky() {
	}

	/** Brings the next comet down now, night or not. Returns false if none is on its way. */
	public static boolean fireNow(MinecraftServer server, BlockPos near) {
		Faction faction = WorldBetweenState.get(server).faction(FactionType.ARKI);
		if (faction.comets().isEmpty()) {
			// Nothing on its way: call one more exile down to the nearest colony.
			com.worldbetween.faction.Settlement colony = faction.settlements().stream()
					.min(java.util.Comparator.comparingDouble(s -> s.pos().getSquaredDistance(near))).orElse(null);
			if (colony == null) {
				return false;
			}
			faction.comets().add(new Comet((int) faction.counter("next_comet"), Comet.Kind.ARRIVALS, colony.pos(), null, colony.id(), 1, null, 0));
			faction.addCounter("next_comet", 1);
		}
		forced = true;
		lastLanding = -GAP;
		return true;
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % 2 != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		WorldBetweenState state = WorldBetweenState.get(server);
		Faction faction = state.faction(FactionType.ARKI);
		long now = server.getTicks();
		if (flying == null) {
			long time = world.getTimeOfDay() % 24000L;
			boolean night = time > 13000 && time < 23000;
			// A comet storm comes down one after another, day or night; everything else waits for the dark.
			boolean storm = !faction.comets().isEmpty() && faction.comets().get(0).kind() == Comet.Kind.STRIKE;
			if ((night || forced || storm) && !faction.comets().isEmpty() && now - lastLanding > (storm ? STORM_GAP : GAP)) {
				launch(world, faction.comets().get(0));
				forced = false;
			}
			return;
		}
		if (!faction.comets().contains(flying)) {
			flying = null; // it came down in the simulation meanwhile
			return;
		}
		elapsed += 2;
		boolean capsule = flying.kind() == Comet.Kind.HELP;
		int duration = capsule ? CAPSULE_TICKS : COMET_TICKS;
		double t = Math.min(1, elapsed / (double) duration);
		Vec3d at = position(t, capsule);
		Vec3d behind = position(Math.max(0, t - 0.04), capsule);
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.getPos().squaredDistanceTo(at) > SEEN * SEEN) {
				continue;
			}
			if (capsule) {
				world.spawnParticles(player, CAPSULE, true, at.x, at.y, at.z, 6, 0.4, 0.4, 0.4, 0);
				world.spawnParticles(player, ParticleTypes.END_ROD, true, behind.x, behind.y, behind.z, 3, 0.3, 0.3, 0.3, 0.01);
			} else {
				world.spawnParticles(player, ParticleTypes.FLASH, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
				world.spawnParticles(player, HEAD, true, at.x, at.y, at.z, 24, 1.4, 1.4, 1.4, 0);
				world.spawnParticles(player, ParticleTypes.SOUL_FIRE_FLAME, true, at.x, at.y, at.z, 12, 1, 1, 1, 0.08);
				// A long tail: violet dust and blue fire all along the last stretch flown.
				for (int k = 1; k <= 6; k++) {
					Vec3d tail = position(Math.max(0, t - 0.012 * k), capsule);
					world.spawnParticles(player, TAIL, true, tail.x, tail.y, tail.z, 12 - k, 1.2 + k * 0.2, 1.2 + k * 0.2, 1.2 + k * 0.2, 0);
					world.spawnParticles(player, ParticleTypes.SOUL_FIRE_FLAME, true, tail.x, tail.y, tail.z, 4, 0.8, 0.8, 0.8, 0.02);
				}
				world.spawnParticles(player, ParticleTypes.END_ROD, true, behind.x, behind.y, behind.z, 6, 1.5, 1.5, 1.5, 0.03);
			}
		}
		if (t >= 1) {
			impact(world, state, faction, flying, capsule);
			flying = null;
			lastLanding = now;
		}
	}

	private static Vec3d position(double t, boolean capsule) {
		Vec3d line = start.lerp(end, t);
		// A capsule arcs from one colony to the other; a comet falls along a long, shallow slope.
		return capsule ? line.add(0, Math.sin(t * Math.PI) * 90, 0) : line;
	}

	private static void launch(ServerWorld world, Comet comet) {
		flying = comet;
		elapsed = 0;
		end = Vec3d.ofCenter(comet.target()).add(0, 1, 0);
		if (comet.kind() == Comet.Kind.HELP && comet.from() != null) {
			start = Vec3d.ofCenter(comet.from()).add(0, 6, 0);
			world.playSound(null, comet.from(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.NEUTRAL, 3f, 0.6f);
			return;
		}
		double angle = world.random.nextDouble() * Math.PI * 2;
		start = end.add(Math.cos(angle) * 500, 0, Math.sin(angle) * 500).withAxis(net.minecraft.util.math.Direction.Axis.Y, 330);
		boolean colony = comet.kind() == Comet.Kind.SEA_COLONY || comet.kind() == Comet.Kind.SKY_COLONY;
		if (comet.kind() == Comet.Kind.STRIKE) {
			start = end.add(Math.cos(angle) * 260, 0, Math.sin(angle) * 260).withAxis(net.minecraft.util.math.Direction.Axis.Y, 300);
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (player.getPos().squaredDistanceTo(end) < SEEN * SEEN && stormWarned.add(player.getUuid() + "/" + comet.colony() + "/" + comet.resource())) {
					player.sendMessage(Text.translatable("message.worldbetween.arki.storm").formatted(Formatting.LIGHT_PURPLE), false);
				}
			}
			return;
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			double dx = end.x - player.getX();
			double dz = end.z - player.getZ();
			double distance = Math.hypot(dx, dz);
			if (colony || distance < SEEN) {
				player.sendMessage(Text.translatable(distance < SEEN ? "message.worldbetween.arki.comet_near" : "message.worldbetween.arki.comet_far",
						Text.translatable("direction.worldbetween." + Arrival.compass(dx, dz)), Math.max(50, Math.round(distance / 50) * 50))
						.formatted(Formatting.LIGHT_PURPLE), false);
				Vec3d toward = new Vec3d(dx, 0, dz).normalize().multiply(16);
				sound(player, SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), player.getPos().add(toward).add(0, 12, 0), 1.2f, 0.6f);
			}
		}
	}

	private static void impact(ServerWorld world, WorldBetweenState state, Faction faction, Comet comet, boolean capsule) {
		BlockPos at = comet.target();
		if (capsule) {
			world.spawnParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 40, 1.5, 1, 1.5, 0.05);
			world.playSound(null, at, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.NEUTRAL, 3f, 0.8f);
		} else {
			if (comet.kind() == Comet.Kind.STRIKE) {
				// It bursts where it falls: whoever stands there is thrown and burnt, though the ground keeps (the town does not).
				world.createExplosion(null, end.x, end.y, end.z, 4f, false, net.minecraft.world.World.ExplosionSourceType.NONE);
			}
			world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, end.x, end.y, end.z, 2, 1, 1, 1, 0);
			world.spawnParticles(ParticleTypes.SPLASH, end.x, end.y, end.z, 400, 4, 2, 4, 0.6);
			world.spawnParticles(ParticleTypes.REVERSE_PORTAL, end.x, end.y, end.z, 300, 3, 3, 3, 0.4);
			world.spawnParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 120, 3, 4, 3, 0.15);
			world.playSound(null, at, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.AMBIENT, 8f, 0.6f);
			world.playSound(null, at, SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.AMBIENT, 8f, 0.8f);
			world.playSound(null, at, SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.AMBIENT, 6f, 0.5f);
		}
		ArkiBehavior.land(new SimulationContext(world.getServer(), state), faction, comet);
		state.markDirty();
	}

	private static void sound(ServerPlayerEntity player, SoundEvent sound, Vec3d at, float volume, float pitch) {
		player.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(sound), SoundCategory.AMBIENT,
				at.x, at.y, at.z, volume, pitch, player.getRandom().nextLong()));
	}
}
