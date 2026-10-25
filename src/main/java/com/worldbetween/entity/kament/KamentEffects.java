package com.worldbetween.entity.kament;

import com.worldbetween.WorldBetween;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

/** "Kament land": a sign on the screen that the ground under a player's feet belongs to the stone. */
public final class KamentEffects {
	public static final RegistryEntry<StatusEffect> KAMENT_LAND = Registry.registerReference(Registries.STATUS_EFFECT,
			Identifier.of(WorldBetween.MOD_ID, "kament_land"), new LandEffect());

	private KamentEffects() {
	}

	public static void register() {
		// Loading the class registers the effect.
	}

	private static final class LandEffect extends StatusEffect {
		LandEffect() {
			super(StatusEffectCategory.NEUTRAL, 0x8E8E86);
		}
	}
}
