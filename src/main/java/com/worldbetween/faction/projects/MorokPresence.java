package com.worldbetween.faction.projects;

import com.worldbetween.entity.WorldBetweenEntities;
import com.worldbetween.entity.morok.MorokEntity;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.item.WorldBetweenItems;
import com.worldbetween.sim.SimulationContext;
import com.worldbetween.war.WarPresence;
import com.worldbetween.war.WarSimulation;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.UUID;

/**
 * Morok met in person. He is wherever the simulation has walked him; a player who comes near meets him
 * there, and wherever he walks in person is where he is afterwards. Whoever strikes him down has three days
 * before he rises again on his seat.
 */
public final class MorokPresence {
	private static final double MEET = 112;
	private static final double LEAVE = 160;
	private static UUID morok;

	private MorokPresence() {
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % 20 != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		WorldBetweenState state = WorldBetweenState.get(server);
		Faction kaments = state.faction(FactionType.KAMENT);
		Entity present = morok == null ? null : world.getEntity(morok);
		if (!Morok.awake(kaments)) {
			if (present != null) {
				present.discard();
			}
			release();
			return;
		}
		if (present instanceof MorokEntity entity && entity.isAlive()) {
			Morok.setPosition(kaments, entity.getBlockPos());
			if (nearest(world, entity.getBlockPos(), LEAVE) == null) {
				entity.discard();
				release();
			} else if (entity.getTarget() == null && server.getTicks() % 1200 == 0) {
				// On through the stone, never long in one place.
				BlockPos ahead = entity.getBlockPos().add(world.random.nextInt(61) - 30, 0, world.random.nextInt(61) - 30);
				entity.setDestination(ahead.withY(world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ahead.getX(), ahead.getZ())));
			}
			return;
		}
		release();
		BlockPos at = Morok.position(kaments);
		BlockPos ground = at.withY(world.isChunkLoaded(at) ? world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) : 0);
		PlayerEntity player = nearest(world, ground, MEET);
		if (player == null || !world.isChunkLoaded(ground) || !world.shouldTickEntity(ground)) {
			return;
		}
		MorokEntity entity = WorldBetweenEntities.MOROK.create(world);
		if (entity == null) {
			return;
		}
		entity.refreshPositionAndAngles(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, world.random.nextFloat() * 360f, 0);
		entity.setCustomName(Text.translatable("entity.worldbetween.morok_named"));
		entity.setSpeech("speech.worldbetween.morok.0");
		entity.setOnDeath(source -> {
			ServerPlayerEntity killer = source.getAttacker() instanceof ServerPlayerEntity p ? p : null;
			entity.dropStack(new ItemStack(WorldBetweenItems.HEARTSTONE, 3));
			if (killer != null) {
				kaments.changeReputation(killer.getUuid(), -40);
				kaments.declareCriminal(killer.getUuid(), state.day());
			}
			Morok.slain(new SimulationContext(server, state), kaments, killer != null ? killer.getName().getString() : "?");
			for (ServerPlayerEntity p : world.getPlayers()) {
				if (WarSimulation.distance(p.getBlockPos(), entity.getBlockPos()) < 200) {
					p.sendMessage(Text.translatable(Morok.capitalStands(kaments) ? "message.worldbetween.morok.fallen" : "message.worldbetween.morok.gone")
							.formatted(Formatting.DARK_PURPLE), false);
				}
			}
			state.markDirty();
		});
		world.spawnEntity(entity);
		morok = entity.getUuid();
		WarPresence.adopt(morok);
		player.sendMessage(Text.translatable("message.worldbetween.morok.near").formatted(Formatting.DARK_PURPLE), false);
	}

	private static void release() {
		if (morok != null) {
			WarPresence.release(morok);
			morok = null;
		}
	}

	private static PlayerEntity nearest(ServerWorld world, BlockPos at, double distance) {
		for (PlayerEntity player : world.getPlayers()) {
			if (!player.isSpectator() && WarSimulation.distance(player.getBlockPos(), at) < distance) {
				return player;
			}
		}
		return null;
	}

	/** For testing: sets Morok down beside a point. */
	public static void place(Faction kaments, BlockPos at) {
		Morok.setPosition(kaments, at);
		kaments.setCounter("morok.alive", 1);
	}
}
