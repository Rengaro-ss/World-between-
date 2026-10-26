package com.worldbetween.client;

import com.worldbetween.WorldBetween;
import com.worldbetween.entity.ManticoreEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Draws a manticore of the kingdom, a little larger than life. */
public class ManticoreRenderer extends MobEntityRenderer<ManticoreEntity, ManticoreModel> {
	private static final Identifier TEXTURE = Identifier.of(WorldBetween.MOD_ID, "textures/entity/manticore.png");

	public ManticoreRenderer(EntityRendererFactory.Context context) {
		super(context, new ManticoreModel(context.getPart(ManticoreModel.LAYER)), 0.9f);
	}

	@Override
	protected void scale(ManticoreEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(1.3f, 1.3f, 1.3f);
	}

	@Override
	public Identifier getTexture(ManticoreEntity entity) {
		return TEXTURE;
	}
}
