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

	/** A sphere of the Arki's magic. */
	public static final EntityType<com.worldbetween.entity.arki.ArkiOrbEntity> ARKI_ORB = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(WorldBetween.MOD_ID, "arki_orb"),
			EntityType.Builder.<com.worldbetween.entity.arki.ArkiOrbEntity>create(com.worldbetween.entity.arki.ArkiOrbEntity::new, SpawnGroup.MISC)
					.dimensions(0.35f, 0.35f).maxTrackingRange(8).trackingTickInterval(1).build(WorldBetween.MOD_ID + ":arki_orb"));

	/** A manticore of the kingdom, bred in its menagerie and ridden by a seeker. */
	public static final EntityType<ManticoreEntity> MANTICORE = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(WorldBetween.MOD_ID, "manticore"),
			EntityType.Builder.create(ManticoreEntity::new, SpawnGroup.MISC).dimensions(1.6f, 1.5f).passengerAttachments(1.75f)
					.maxTrackingRange(12).build(WorldBetween.MOD_ID + ":manticore"));

	/** The Ancient Morok: half as tall again as any Kament. */
	public static final EntityType<com.worldbetween.entity.morok.MorokEntity> MOROK = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(WorldBetween.MOD_ID, "morok"),
			EntityType.Builder.create(com.worldbetween.entity.morok.MorokEntity::new, SpawnGroup.MISC).dimensions(1.4f, 3.6f)
					.maxTrackingRange(12).build(WorldBetween.MOD_ID + ":morok"));

	/** A hound of stone that Morok calls up out of the rock. */
	public static final EntityType<com.worldbetween.entity.morok.StoneHoundEntity> STONE_HOUND = Registry.register(Registries.ENTITY_TYPE,
			Identifier.of(WorldBetween.MOD_ID, "stone_hound"),
			EntityType.Builder.create(com.worldbetween.entity.morok.StoneHoundEntity::new, SpawnGroup.MISC).dimensions(0.8f, 0.9f)
					.maxTrackingRange(10).build(WorldBetween.MOD_ID + ":stone_hound"));

	private WorldBetweenEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(NERIS, NerisEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(KAMENT, com.worldbetween.entity.kament.KamentEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(ARKI, com.worldbetween.entity.arki.ArkiEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(MANTICORE, ManticoreEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(MOROK, com.worldbetween.entity.morok.MorokEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(STONE_HOUND, com.worldbetween.entity.morok.StoneHoundEntity.createAttributes());
	}
}
