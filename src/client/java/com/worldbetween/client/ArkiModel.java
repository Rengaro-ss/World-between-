package com.worldbetween.client;

import com.worldbetween.WorldBetween;
import com.worldbetween.entity.arki.ArkiEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * An Arki: like an efreet, a glowing core with shards turning round it in three rings, but the shards are
 * crystal and the light is the light of the night sky. The rings turn the other way to an efreet's, and
 * the core tilts as it watches.
 */
public class ArkiModel extends SinglePartEntityModel<ArkiEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Identifier.of(WorldBetween.MOD_ID, "arki"), "main");
	private static final int SHARDS = 12;

	private final ModelPart root;
	private final ModelPart core;
	private final ModelPart[] shards = new ModelPart[SHARDS];

	public ArkiModel(ModelPart root) {
		this.root = root;
		this.core = root.getChild("core");
		for (int i = 0; i < SHARDS; i++) {
			shards[i] = root.getChild("shard_" + i);
		}
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("core", ModelPartBuilder.create().uv(0, 0).cuboid(-4f, -4f, -4f, 8, 8, 8), ModelTransform.NONE);
		for (int i = 0; i < SHARDS; i++) {
			int length = i < 4 ? 8 : i < 8 ? 7 : 5;
			root.addChild("shard_" + i, ModelPartBuilder.create().uv(i < 4 ? 0 : i < 8 ? 8 : 16, 16).cuboid(-1f, 0f, -1f, 2, length, 2),
					ModelTransform.NONE);
		}
		return TexturedModelData.of(data, 64, 32);
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	@Override
	public void setAngles(ArkiEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		float spin = -animationProgress * MathHelper.PI * 0.025f;
		// The highest ring: long shards round the core.
		for (int i = 0; i < 4; i++) {
			float a = spin + i * MathHelper.HALF_PI;
			shards[i].pivotX = MathHelper.cos(a) * 8f;
			shards[i].pivotY = -3f + MathHelper.cos((i * 2 + animationProgress) * 0.25f);
			shards[i].pivotZ = MathHelper.sin(a) * 8f;
			shards[i].roll = MathHelper.cos(a) * 0.25f;
			shards[i].pitch = MathHelper.sin(a) * -0.25f;
		}
		// The middle ring turns the other way.
		for (int i = 4; i < 8; i++) {
			float a = -spin * 1.3f + i * MathHelper.HALF_PI + MathHelper.PI / 4;
			shards[i].pivotX = MathHelper.cos(a) * 6f;
			shards[i].pivotY = 4f + MathHelper.cos((i * 2 + animationProgress) * 0.25f);
			shards[i].pivotZ = MathHelper.sin(a) * 6f;
			shards[i].roll = 0;
			shards[i].pitch = 0;
		}
		// The lowest ring: short shards close in, where an efreet has its smoke.
		for (int i = 8; i < SHARDS; i++) {
			float a = spin * 1.8f + i * MathHelper.HALF_PI;
			shards[i].pivotX = MathHelper.cos(a) * 3.5f;
			shards[i].pivotY = 11f + MathHelper.cos((i * 1.5f + animationProgress) * 0.5f);
			shards[i].pivotZ = MathHelper.sin(a) * 3.5f;
			shards[i].roll = MathHelper.cos(a) * -0.2f;
			shards[i].pitch = MathHelper.sin(a) * 0.2f;
		}
		core.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
		core.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;
		core.pivotY = MathHelper.sin(animationProgress * 0.08f) * 0.6f;
	}
}
