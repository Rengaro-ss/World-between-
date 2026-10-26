package com.worldbetween.faction.projects;

import com.worldbetween.entity.ManticoreEntity;
import com.worldbetween.entity.WorldBetweenEntities;
import com.worldbetween.faction.Faction;
import com.worldbetween.faction.FactionType;
import com.worldbetween.faction.Settlement;
import com.worldbetween.war.WarPresence;
import com.worldbetween.war.WarSimulation;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The kingdom's manticores at home in their menagerie, for a player who comes to look. */
public final class WorksPresence {
	private static final double MEET = 80;
	private static final List<UUID> BEASTS = new ArrayList<>();

	private WorksPresence() {
	}

	public static void onServerTick(MinecraftServer server) {
		if (server.getTicks() % 40 != 0) {
			return;
		}
		ServerWorld world = server.getOverworld();
		Faction neris = WorldBetweenState.get(server).faction(FactionType.NERIS);
		Settlement seat = Projects.seat(neris, Project.MANTICORES);
		BlockPos lab = seat == null || !Projects.done(neris, Project.MANTICORES) ? null : WorksFeatures.origin(world, neris, seat, Project.MANTICORES);
		BEASTS.removeIf(id -> {
			Entity beast = world.getEntity(id);
			boolean gone = beast == null || !beast.isAlive();
			if (!gone && (lab == null || !near(world, lab))) {
				beast.discard();
				gone = true;
			}
			if (gone) {
				WarPresence.release(id);
			}
			return gone;
		});
		if (lab == null || !near(world, lab) || !world.isChunkLoaded(lab) || !world.shouldTickEntity(lab)) {
			return;
		}
		long wanted = Math.min(2, neris.counter("manticores"));
		while (BEASTS.size() < wanted) {
			ManticoreEntity beast = WorldBetweenEntities.MANTICORE.create(world);
			if (beast == null) {
				return;
			}
			int side = BEASTS.size() == 0 ? -1 : 1;
			BlockPos at = lab.add(side * 4, 1, 3);
			beast.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, world.random.nextFloat() * 360f, 0);
			beast.setAnchor(at, 1);
			world.spawnEntity(beast);
			BEASTS.add(beast.getUuid());
			WarPresence.adopt(beast.getUuid());
		}
	}

	private static boolean near(ServerWorld world, BlockPos at) {
		for (PlayerEntity player : world.getPlayers()) {
			if (WarSimulation.distance(player.getBlockPos(), at) < MEET) {
				return true;
			}
		}
		return false;
	}
}
