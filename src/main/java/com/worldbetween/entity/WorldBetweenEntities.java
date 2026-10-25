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

	private WorldBetweenEntities() {
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(NERIS, NerisEntity.createAttributes());
	}
}
