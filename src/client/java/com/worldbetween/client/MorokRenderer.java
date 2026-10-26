package com.worldbetween.client;

import com.worldbetween.WorldBetween;
import com.worldbetween.entity.kament.KamentEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Morok: a Kament half as tall again, of deep stone cracked through with the light of the souls in him. */
public class MorokRenderer extends KamentRenderer {
	private static final Identifier TEXTURE = Identifier.of(WorldBetween.MOD_ID, "textures/entity/kament/morok.png");

	public MorokRenderer(EntityRendererFactory.Context context) {
		super(context);
		shadowRadius = 1.3f;
	}

	@Override
	public Identifier getTexture(KamentEntity entity) {
		return TEXTURE;
	}

	@Override
	protected void scale(KamentEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(1.5f, 1.5f, 1.5f);
	}
}
