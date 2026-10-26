package com.worldbetween.entity;

import com.worldbetween.mixin.MobEntityAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.world.ServerWorld;

/**
 * The world's monsters do not care whose people they find in the dark: a zombie goes for a Neris townsman,
 * a Kament at the gate or an Arki low over the water just as it goes for a player.
 */
public final class Monsters {
	private Monsters() {
	}

	public static void onEntityLoad(Entity entity, ServerWorld world) {
		// Neutral ones (endermen, the Nether's zombified piglins) keep to themselves until angered.
		if (!(entity instanceof MobEntity mob) || !(entity instanceof Monster) || entity instanceof Angerable || entity instanceof FactionMember) {
			return;
		}
		// Same priority as their goal for players, so whichever is nearer and in sight comes first.
		((MobEntityAccessor) mob).worldbetween$targetSelector().add(2,
				new ActiveTargetGoal<>(mob, LivingEntity.class, 10, true, false, Monsters::isPrey));
	}

	private static boolean isPrey(LivingEntity target) {
		return target instanceof FactionMember && !(target instanceof com.worldbetween.entity.morok.MorokEntity);
	}

	/** For the peoples' guards: a monster in sight is a monster to be fought. */
	public static boolean isMonster(LivingEntity target) {
		return target instanceof HostileEntity && !(target instanceof FactionMember);
	}
}
