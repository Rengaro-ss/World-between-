package com.worldbetween.client;

import com.worldbetween.WorldBetween;
import com.worldbetween.entity.morok.StoneHoundEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

public class StoneHoundRenderer extends MobEntityRenderer<StoneHoundEntity, StoneHoundModel> {
	private static final Identifier TEXTURE = Identifier.of(WorldBetween.MOD_ID, "textures/entity/stone_hound.png");

	public StoneHoundRenderer(EntityRendererFactory.Context context) {
		super(context, new StoneHoundModel(context.getPart(StoneHoundModel.LAYER)), 0.5f);
	}

	@Override
	public Identifier getTexture(StoneHoundEntity entity) {
		return TEXTURE;
	}
}
