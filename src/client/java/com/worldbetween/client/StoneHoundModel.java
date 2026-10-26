package com.worldbetween.client;

import com.worldbetween.WorldBetween;
import com.worldbetween.entity.morok.StoneHoundEntity;
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

/** A hound of stone: a blocky, heavy-shouldered dog hewn out of a single block. */
public class StoneHoundModel extends SinglePartEntityModel<StoneHoundEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Identifier.of(WorldBetween.MOD_ID, "stone_hound"), "main");

	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart tail;
	private final ModelPart[] legs = new ModelPart[4];

	public StoneHoundModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		this.tail = root.getChild("tail");
		for (int i = 0; i < 4; i++) {
			legs[i] = root.getChild("leg_" + i);
		}
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-3f, -3f, -5f, 6, 6, 10), ModelTransform.pivot(0f, 14f, 0f));
		root.addChild("head", ModelPartBuilder.create().uv(32, 0).cuboid(-3f, -3f, -5f, 6, 6, 5)
				.uv(54, 0).cuboid(-1.5f, 0f, -7f, 3, 3, 2), ModelTransform.pivot(0f, 12f, -5f));
		for (int i = 0; i < 4; i++) {
			root.addChild("leg_" + i, ModelPartBuilder.create().uv(0, 16).cuboid(-1f, 0f, -1f, 2, 7, 2),
					ModelTransform.pivot(i % 2 == 0 ? -2f : 2f, 17f, i < 2 ? -3.5f : 3.5f));
		}
		root.addChild("tail", ModelPartBuilder.create().uv(8, 16).cuboid(-1f, -1f, 0f, 2, 2, 6), ModelTransform.of(0f, 12f, 5f, 0.6f, 0f, 0f));
		return TexturedModelData.of(data, 64, 32);
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	@Override
	public void setAngles(StoneHoundEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
		head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;
		for (int i = 0; i < 4; i++) {
			legs[i].pitch = MathHelper.cos(limbAngle * 0.6662f + (i == 0 || i == 3 ? 0 : MathHelper.PI)) * 1.4f * limbDistance;
		}
		tail.yaw = MathHelper.sin(animationProgress * 0.3f) * 0.3f * limbDistance;
	}
}
