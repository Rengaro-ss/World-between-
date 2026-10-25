package com.worldbetween.war;

import com.worldbetween.entity.FactionMember;
import com.worldbetween.faction.FactionType;
import com.worldbetween.world.WorldBetweenState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;

/** Who fights whom when they meet: the folk of peoples at war, from the first degree of it on. */
public final class WarRules {
	private WarRules() {
	}

	public static boolean enemies(MinecraftServer server, FactionType a, FactionType b) {
		return a != b && WorldBetweenState.get(server).wars().level(a, b) >= 1;
	}

	/** Whether {@code self} goes for {@code target} on sight. */
	public static boolean isEnemy(LivingEntity self, LivingEntity target) {
		if (!(self instanceof FactionMember me) || !me.fights() || !(target instanceof FactionMember them)
				|| !(self.getWorld() instanceof ServerWorld world) || !target.isAlive()) {
			return false;
		}
		return enemies(world.getServer(), me.faction(), them.faction());
	}
}
