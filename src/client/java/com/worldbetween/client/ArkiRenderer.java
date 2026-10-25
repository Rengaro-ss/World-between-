package com.worldbetween.client;

import com.worldbetween.WorldBetween;
import com.worldbetween.entity.arki.ArkiEntity;
import com.worldbetween.entity.arki.ArkiRole;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.EnumMap;
import java.util.Map;

/** Draws an Arki full-bright, as an efreet is drawn: it gives its own light. */
public class ArkiRenderer extends MobEntityRenderer<ArkiEntity, ArkiModel> {
	private static final Map<ArkiRole, Identifier> TEXTURES = new EnumMap<>(ArkiRole.class);

	static {
		for (ArkiRole role : ArkiRole.values()) {
			TEXTURES.put(role, Identifier.of(WorldBetween.MOD_ID, "textures/entity/arki/" + role.id() + ".png"));
		}
	}

	public ArkiRenderer(EntityRendererFactory.Context context) {
		super(context, new ArkiModel(context.getPart(ArkiModel.LAYER)), 0.4f);
	}

	@Override
	protected int getBlockLight(ArkiEntity entity, BlockPos pos) {
		return 15;
	}

	@Override
	public Identifier getTexture(ArkiEntity entity) {
		return TEXTURES.get(entity.role());
	}
}
