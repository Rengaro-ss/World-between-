package com.worldbetween.client;

import com.worldbetween.WorldBetween;
import com.worldbetween.entity.kament.KamentEntity;
import com.worldbetween.entity.kament.KamentMaterial;
import com.worldbetween.entity.kament.KamentRole;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/** Draws a Kament in the stone it was hewn from, with eyes (and a sculptor's soul) that glow in the dark. */
public class KamentRenderer extends MobEntityRenderer<KamentEntity, KamentModel> {
	private static final Map<KamentRole, Map<KamentMaterial, Identifier>> TEXTURES = new EnumMap<>(KamentRole.class);
	private static final Map<KamentRole, Identifier> EYES = new EnumMap<>(KamentRole.class);

	static {
		for (KamentRole role : KamentRole.values()) {
			Map<KamentMaterial, Identifier> byMaterial = new EnumMap<>(KamentMaterial.class);
			for (KamentMaterial material : KamentMaterial.values()) {
				byMaterial.put(material, Identifier.of(WorldBetween.MOD_ID, "textures/entity/kament/" + role.id() + "_" + material.id() + ".png"));
			}
			TEXTURES.put(role, byMaterial);
			EYES.put(role, Identifier.of(WorldBetween.MOD_ID, "textures/entity/kament/eyes_" + role.id() + ".png"));
		}
	}

	public KamentRenderer(EntityRendererFactory.Context context) {
		super(context, new KamentModel(context.getPart(KamentModel.LAYER)), 0.8f);
		addFeature(new Eyes(this));
	}

	@Override
	public Identifier getTexture(KamentEntity entity) {
		return TEXTURES.get(entity.role()).get(entity.material());
	}

	@Override
	protected void scale(KamentEntity entity, MatrixStack matrices, float amount) {
		float scale = entity.role() == KamentRole.ANCIENT ? 1.08f : entity.role() == KamentRole.GUARD ? 1.04f : 1f;
		matrices.scale(scale, scale, scale);
	}

	private static final class Eyes extends FeatureRenderer<KamentEntity, KamentModel> {
		Eyes(FeatureRendererContext<KamentEntity, KamentModel> context) {
			super(context);
		}

		@Override
		public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, KamentEntity entity, float limbAngle,
				float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
			VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEyes(EYES.get(entity.role())));
			getContextModel().render(matrices, consumer, 0xF00000, OverlayTexture.DEFAULT_UV);
		}
	}
}
