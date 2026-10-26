package com.worldbetween.client;

import com.worldbetween.WorldBetween;
import com.worldbetween.entity.ManticoreEntity;
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
 * A manticore: the body, head and mane of a lion, the wings of a great bat, and a tail that curls up over
 * the back to a sting. The wings beat slowly in flight and fold in a dive; the legs tuck up off the ground.
 */
public class ManticoreModel extends SinglePartEntityModel<ManticoreEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Identifier.of(WorldBetween.MOD_ID, "manticore"), "main");

	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart leftWing;
	private final ModelPart rightWing;
	private final ModelPart tail;
	private final ModelPart tailTip;
	private final ModelPart[] legs = new ModelPart[4];

	public ManticoreModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		this.leftWing = root.getChild("left_wing");
		this.rightWing = root.getChild("right_wing");
		this.tail = root.getChild("tail");
		this.tailTip = tail.getChild("tip");
		for (int i = 0; i < 4; i++) {
			legs[i] = root.getChild("leg_" + i);
		}
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("body", ModelPartBuilder.create().uv(0, 0).cuboid(-5f, -5f, -9f, 10, 10, 18), ModelTransform.pivot(0f, 11f, 0f));
		ModelPartData head = root.addChild("head", ModelPartBuilder.create().uv(0, 28).cuboid(-4f, -4f, -6f, 8, 8, 6)
				.uv(0, 42).cuboid(-2f, 0f, -8f, 4, 3, 2), ModelTransform.pivot(0f, 8f, -9f));
		head.addChild("mane", ModelPartBuilder.create().uv(28, 28).cuboid(-6f, -6f, -1f, 12, 12, 3), ModelTransform.NONE);
		for (int i = 0; i < 4; i++) {
			float x = i % 2 == 0 ? -3.5f : 3.5f;
			float z = i < 2 ? -6f : 6.5f;
			root.addChild("leg_" + i, ModelPartBuilder.create().uv(0, 48).cuboid(-1.5f, 0f, -1.5f, 3, 10, 3), ModelTransform.pivot(x, 14f, z));
		}
		ModelPartData tail = root.addChild("tail", ModelPartBuilder.create().uv(12, 48).cuboid(-1f, -1f, 0f, 2, 2, 9),
				ModelTransform.of(0f, 7f, 8.5f, 0.9f, 0f, 0f));
		ModelPartData tip = tail.addChild("tip", ModelPartBuilder.create().uv(36, 48).cuboid(-1f, -1f, 0f, 2, 2, 7),
				ModelTransform.of(0f, 0f, 9f, 1.1f, 0f, 0f));
		tip.addChild("sting", ModelPartBuilder.create().uv(56, 48).cuboid(-1.5f, -1.5f, 0f, 3, 3, 4), ModelTransform.of(0f, 0f, 7f, 0.6f, 0f, 0f));
		root.addChild("left_wing", ModelPartBuilder.create().uv(0, 64).cuboid(0f, -0.5f, -6f, 22, 1, 13), ModelTransform.pivot(4.5f, 7f, -2f));
		root.addChild("right_wing", ModelPartBuilder.create().uv(0, 64).mirrored().cuboid(-22f, -0.5f, -6f, 22, 1, 13),
				ModelTransform.pivot(-4.5f, 7f, -2f));
		return TexturedModelData.of(data, 128, 128);
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	@Override
	public void setAngles(ManticoreEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
		head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;
		boolean flying = !entity.isOnGround();
		float flap;
		if (entity.isDiving()) {
			flap = -0.15f; // folded back in a dive
		} else if (flying) {
			flap = MathHelper.sin(animationProgress * 0.35f) * 0.7f;
		} else {
			flap = -1.2f; // folded along the flanks on the ground
		}
		leftWing.roll = flap;
		rightWing.roll = -flap;
		leftWing.yaw = entity.isDiving() ? -0.6f : 0f;
		rightWing.yaw = entity.isDiving() ? 0.6f : 0f;
		for (int i = 0; i < 4; i++) {
			if (flying) {
				legs[i].pitch = i < 2 ? -0.9f : 0.9f; // tucked
			} else {
				legs[i].pitch = MathHelper.cos(limbAngle * 0.6662f + (i == 0 || i == 3 ? 0 : MathHelper.PI)) * 1.2f * limbDistance;
			}
		}
		tail.yaw = MathHelper.sin(animationProgress * 0.1f) * 0.2f;
		tailTip.pitch = 1.1f + MathHelper.sin(animationProgress * 0.15f) * 0.15f;
	}
}
