package com.worldbetween.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the mod hand the world's monsters a taste for the peoples' folk. */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {
	@Accessor("targetSelector")
	GoalSelector worldbetween$targetSelector();
}
