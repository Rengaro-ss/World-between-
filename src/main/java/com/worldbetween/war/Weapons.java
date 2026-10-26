package com.worldbetween.war;

import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.faction.arki.Arkis;
import com.worldbetween.faction.arki.Comet;
import com.worldbetween.faction.kament.Kaments;
import com.worldbetween.faction.projects.Project;
import com.worldbetween.faction.projects.Projects;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.town.Ground;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The peoples' weapons in a war that has come to campaigns. The Kaments' crystal shakes an enemy town to
 * pieces from far off; the Arki call a storm of comets down on one. What they break, the builders mend.
 */
public final class Weapons {
	/** How far the quake crystal reaches. */
	private static final int QUAKE_RANGE = 4000;
	private static final int QUAKE_INTERVAL = 10;
	private static final int STORM_RANGE = 3200;
	private static final int STORM_INTERVAL = 20;
	private static final int STORM_COMETS = 8;
	public static final long STORM_STARLIGHT = 900;
	public static final long STORM_AMETHYST = 400;
	private static final int QUAKE_SHAKE_TICKS = 140;

	/** Quakes being felt right now: where, and until when. */
	private static final List<long[]> SHAKING = new ArrayList<>();

	private Weapons() {
	}

	/** In a war at its third degree or worse, a people uses the weapon it has, if it can. */
	public static void use(SimulationContext context, War war, Faction side, Faction enemy) {
		if (war.level() < 3) {
			return;
		}
		if (side.type() == FactionType.KAMENT) {
			quake(context, war, side, enemy);
		} else if (side.type() == FactionType.ARKI) {
			storm(context, war, side, enemy);
		}
	}

	// ---------------------------------------------------------------- the quake crystal

	private static void quake(SimulationContext context, War war, Faction kaments, Faction enemy) {
		if (!Projects.done(kaments, Project.QUAKE_CRYSTAL) || kaments.counter("crystal.charges") <= 0
				|| !kaments.tryEvent("quake_strike_" + enemy.type().id(), context.day(), QUAKE_INTERVAL)) {
			return;
		}
		Settlement seat = Projects.seat(kaments, Project.QUAKE_CRYSTAL);
		if (seat == null) {
			// The hold that kept the crystal has fallen: the crystal with it.
			Projects.setStage(kaments, Project.QUAKE_CRYSTAL, Project.KNOWN);
			kaments.setCounter(Project.QUAKE_CRYSTAL.key("seat"), 0);
			context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.project.crystal_lost");
			return;
		}
		// The crystal shakes the ground; a town on the water or in the sky does not feel it.
		Settlement target = enemy.settlements().stream()
				.filter(t -> t.dimension() == net.minecraft.world.World.OVERWORLD && !(enemy.type() == FactionType.ARKI))
				.filter(t -> WarSimulation.distance(t.pos(), seat.pos()) < QUAKE_RANGE)
				.min(Comparator.comparingDouble(t -> WarSimulation.distance(t.pos(), seat.pos()) - 200 * t.tier())).orElse(null);
		if (target == null) {
			return;
		}
		kaments.addCounter("crystal.charges", -1);
		strike(context, war, enemy, target, 20 + context.random().nextInt(16), 0.06);
		context.chronicle(FactionType.KAMENT, "chronicle.worldbetween.project.quake_strike", seat.name(), target.name());
		feelQuake(context.server(), target.pos());
	}

	/** For testing: the crystal strikes a town now. */
	public static void quakeNow(SimulationContext context, Faction enemy, Settlement town) {
		strike(context, context.state().wars().war(FactionType.KAMENT, enemy.type()), enemy, town, 30, 0.06);
		feelQuake(context.server(), town.pos());
	}

	/** Shakes everyone near a town for a few seconds. */
	public static void feelQuake(MinecraftServer server, BlockPos at) {
		ServerWorld world = server.getOverworld();
		SHAKING.add(new long[]{at.getX(), at.getZ(), server.getTicks() + QUAKE_SHAKE_TICKS});
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (WarSimulation.distance(player.getBlockPos(), at) < 220) {
				player.sendMessage(Text.translatable("message.worldbetween.kament.crystal").formatted(Formatting.GRAY), false);
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.AMBIENT, 2f, 0.4f);
			}
		}
	}

	public static void onServerTick(MinecraftServer server) {
		if (SHAKING.isEmpty() || server.getTicks() % 2 != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		long now = server.getTicks();
		SHAKING.removeIf(q -> q[2] < now);
		Random random = world.random;
		for (long[] quake : SHAKING) {
			BlockPos at = new BlockPos((int) quake[0], 0, (int) quake[1]);
			for (ServerPlayerEntity player : world.getPlayers()) {
				double distance = WarSimulation.distance(player.getBlockPos(), at);
				if (distance > 220 || player.isSpectator()) {
					continue;
				}
				float strength = (float) (1.4 * (1 - distance / 260));
				player.addVelocity((random.nextDouble() - 0.5) * 0.09 * strength, player.isOnGround() ? random.nextDouble() * 0.04 * strength : 0,
						(random.nextDouble() - 0.5) * 0.09 * strength);
				player.velocityModified = true;
				BlockState dust = Blocks.STONE.getDefaultState();
				world.spawnParticles(player, new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, dust), true,
						player.getX(), player.getY() + 3, player.getZ(), Math.round(8 * strength), 5, 1, 5, 0);
				if (now % 20 == 0) {
					world.playSound(null, player.getBlockPos().add(random.nextInt(9) - 4, -2, random.nextInt(9) - 4),
							random.nextBoolean() ? SoundEvents.BLOCK_DEEPSLATE_BREAK : SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.AMBIENT, strength, 0.5f);
					// Loose stuff comes down: gravel, sand, the odd block off a wall.
					for (int i = 0; i < 4; i++) {
						BlockPos pos = player.getBlockPos().add(random.nextInt(13) - 6, 2 + random.nextInt(6), random.nextInt(13) - 6);
						BlockState state = world.getBlockState(pos);
						if (state.getBlock() instanceof FallingBlock && world.getBlockState(pos.down()).isAir()) {
							FallingBlockEntity.spawnFromBlock(world, pos, state);
							break;
						}
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- the comet storm

	private static void storm(SimulationContext context, War war, Faction arki, Faction enemy) {
		if (!Projects.done(arki, Project.COMET_STORM) || !arki.tryEvent("storm_" + enemy.type().id(), context.day(), STORM_INTERVAL)) {
			return;
		}
		long starlight = arki.settlements().stream().mapToLong(s -> s.counter(Arkis.STARLIGHT)).sum();
		long amethyst = arki.settlements().stream().mapToLong(s -> s.counter(Arkis.AMETHYST)).sum();
		if (starlight < STORM_STARLIGHT || amethyst < STORM_AMETHYST) {
			return;
		}
		Settlement target = enemy.settlements().stream()
				.filter(t -> t.dimension() == net.minecraft.world.World.OVERWORLD && !(enemy.type() == FactionType.KAMENT && Kaments.isDeep(t)))
				.min(Comparator.comparingDouble(t -> nearestColony(arki, t.pos()) - 150 * t.tier())).orElse(null);
		if (target == null || nearestColony(arki, target.pos()) > STORM_RANGE) {
			return;
		}
		Projects.drain(arki, Arkis.STARLIGHT, STORM_STARLIGHT);
		Projects.drain(arki, Arkis.AMETHYST, STORM_AMETHYST);
		queueStorm(context.server(), arki, enemy.type(), target, context.random());
		context.chronicle(FactionType.ARKI, "chronicle.worldbetween.project.storm", target.name());
	}

	/** Calls a storm of comets down on a town; they burst where they fall, seen or unseen. */
	public static void queueStorm(MinecraftServer server, Faction arki, FactionType enemy, Settlement target, Random random) {
		ServerWorld world = server.getOverworld();
		for (int i = 0; i < STORM_COMETS; i++) {
			int x = target.pos().getX() + random.nextInt(41) - 20;
			int z = target.pos().getZ() + random.nextInt(41) - 20;
			BlockPos at = new BlockPos(x, Ground.surface(world, x, z), z);
			// Storm comets go first, before any colonist waiting for the dark.
			arki.comets().add(i, new Comet((int) arki.counter("next_comet"), Comet.Kind.STRIKE, at, null, target.id(), 0, enemy.id(), 0));
			arki.addCounter("next_comet", 1);
		}
	}

	/** One comet of a storm has come down on its town. */
	public static void cometStrike(SimulationContext context, Comet comet) {
		FactionType type = FactionType.byId(comet.resource());
		if (type == null) {
			return;
		}
		Faction enemy = context.state().faction(type);
		Settlement town = Armies.settlement(enemy, comet.colony());
		if (town == null) {
			return;
		}
		War war = context.state().wars().war(FactionType.ARKI, type);
		strike(context, war, enemy, town, 7, 0.03);
	}

	/** A blow from afar: buildings broken, some of the people killed, and the war hotter for it. */
	private static void strike(SimulationContext context, War war, Faction enemy, Settlement town, int damage, double killed) {
		Repairs.damage(town, damage);
		int dead = (int) Math.ceil(town.population() * killed);
		town.setPopulation(Math.max(1, town.population() - dead));
		if (enemy.type() == FactionType.NERIS) {
			enemy.setSoldiers(Math.max(0, enemy.soldiers() - dead / 2));
		}
		if (war != null) {
			war.addTension(3);
		}
		context.state().markDirty();
	}

	private static double nearestColony(Faction arki, BlockPos to) {
		return arki.settlements().stream().mapToDouble(c -> WarSimulation.distance(c.pos(), to)).min().orElse(Double.MAX_VALUE);
	}

}
