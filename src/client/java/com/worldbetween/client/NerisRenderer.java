package com.worldbetween.client;

import com.worldbetween.WorldBetween;
import com.worldbetween.entity.NerisEntity;
import com.worldbetween.entity.NerisRole;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws the Neris with the human model: bronze skin, black eyes, clothes by role.
 */
public class NerisRenderer extends BipedEntityRenderer<NerisEntity, PlayerEntityModel<NerisEntity>> {
	private static final Map<NerisRole, Identifier> TEXTURES = new EnumMap<>(NerisRole.class);

	static {
		for (NerisRole role : NerisRole.values()) {
			TEXTURES.put(role, Identifier.of(WorldBetween.MOD_ID, "textures/entity/neris/" + role.id() + ".png"));
		}
	}

	public NerisRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false), 0.5f);
		addFeature(new ArmorFeatureRenderer<>(this,
				new ArmorEntityModel<>(context.getPart(EntityModelLayers.PLAYER_INNER_ARMOR)),
				new ArmorEntityModel<>(context.getPart(EntityModelLayers.PLAYER_OUTER_ARMOR)),
				context.getModelManager()));
	}

	@Override
	public Identifier getTexture(NerisEntity entity) {
		return TEXTURES.get(entity.role());
	}
}
