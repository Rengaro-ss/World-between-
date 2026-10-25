package com.worldbetween.entity;

import com.worldbetween.WorldBetween;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class WorldBetweenEntities {
	public static final EntityType<NerisEntity> NERIS = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(WorldBetween.MOD_ID, "neris"),
			EntityType.Builder.create(NerisEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f).maxTrackingRange(10)
					.build(WorldBetween.MOD_ID + ":neris"));

	/** A Kament: a little taller than a man, a little shorter than an enderman. */
	public static final EntityType<com.worldbetween.entity.kament.KamentEntity> KAMENT = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(WorldBetween.MOD_ID, "kament"),
			EntityType.Builder.create(com.worldbetween.entity.kament.KamentEntity::new, SpawnGroup.MISC).dimensions(0.95f, 2.4f)
					.maxTrackingRange(10).build(WorldBetween.MOD_ID + ":kament"));

	/** An Arki: an efreet of the night sky, a glowing core in a ring of crystal shards. */
	public static final EntityType<com.worldbetween.entity.arki.ArkiEntity> ARKI = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(WorldBetween.MOD_ID, "arki"),
			EntityType.Builder.create(com.worldbetween.entity.arki.ArkiEntity::new, SpawnGroup.MISC).dimensions(0.6f, 1.8f)
					.maxTrackingRange(10).build(WorldBetween.MOD_ID + ":arki"));

	private WorldBetweenEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(NERIS, NerisEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(KAMENT, com.worldbetween.entity.kament.KamentEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ARKI, com.worldbetween.entity.arki.ArkiEntity.createAttributes());
	}
}
