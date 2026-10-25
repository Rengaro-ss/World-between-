package com.worldbetween.client;

import com.worldbetween.WorldBetween;
import com.worldbetween.entity.kament.KamentEntity;
import com.worldbetween.entity.kament.KamentRole;
import net.minecraft.client.model.Dilation;
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
 * A Kament: a broad block of a body, a heavy head sunk between the shoulders, arms that hang to the
 * knees with boulders for shoulders, short thick legs. Ancients and sculptors grow crystals.
 */
public class KamentModel extends SinglePartEntityModel<KamentEntity> {
	public static final EntityModelLayer LAYER = new EntityModelLayer(Identifier.of(WorldBetween.MOD_ID, "kament"), "main");

	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;
	private final ModelPart crown;
	private final ModelPart rightCrystal;
	private final ModelPart leftCrystal;

	public KamentModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
		this.crown = head.getChild("crown");
		this.rightCrystal = rightArm.getChild("right_pauldron").getChild("right_crystal");
		this.leftCrystal = leftArm.getChild("left_pauldron").getChild("left_crystal");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		root.addChild("body", ModelPartBuilder.create().uv(32, 0).cuboid(-6f, -18f, -3.5f, 12, 18, 7), ModelTransform.pivot(0f, 12f, 0f));
		ModelPartData head = root.addChild("head", ModelPartBuilder.create().uv(0, 0).cuboid(-4f, -9f, -4f, 8, 9, 8),
				ModelTransform.pivot(0f, -5f, -1.5f));
		ModelPartData crown = head.addChild("crown", ModelPartBuilder.create(), ModelTransform.NONE);
		crown.addChild("crystal_a", ModelPartBuilder.create().uv(88, 25).cuboid(-3f, -13f, -1f, 2, 4, 2), ModelTransform.NONE);
		crown.addChild("crystal_b", ModelPartBuilder.create().uv(96, 25).cuboid(1f, -12f, 0f, 2, 3, 2), ModelTransform.NONE);
		ModelPartData rightArm = root.addChild("right_arm", ModelPartBuilder.create().uv(0, 17).cuboid(-3f, -2f, -3f, 6, 22, 6),
				ModelTransform.pivot(-9f, -4f, 0f));
		ModelPartData leftArm = root.addChild("left_arm", ModelPartBuilder.create().uv(24, 25).cuboid(-3f, -2f, -3f, 6, 22, 6),
				ModelTransform.pivot(9f, -4f, 0f));
		ModelPartData rightPauldron = rightArm.addChild("right_pauldron", ModelPartBuilder.create().uv(70, 0)
				.cuboid(-3.5f, -4f, -3.5f, 7, 5, 7, new Dilation(0.1f)), ModelTransform.NONE);
		ModelPartData leftPauldron = leftArm.addChild("left_pauldron", ModelPartBuilder.create().uv(98, 0)
				.cuboid(-3.5f, -4f, -3.5f, 7, 5, 7, new Dilation(0.1f)), ModelTransform.NONE);
		rightPauldron.addChild("right_crystal", ModelPartBuilder.create().uv(104, 25).cuboid(-1f, -8f, -1f, 2, 4, 2), ModelTransform.NONE);
		leftPauldron.addChild("left_crystal", ModelPartBuilder.create().uv(112, 25).cuboid(-1f, -7f, 0f, 2, 3, 2), ModelTransform.NONE);
		root.addChild("right_leg", ModelPartBuilder.create().uv(48, 25).cuboid(-2.5f, 0f, -2.5f, 5, 12, 5), ModelTransform.pivot(-3.5f, 12f, 0f));
		root.addChild("left_leg", ModelPartBuilder.create().uv(68, 25).cuboid(-2.5f, 0f, -2.5f, 5, 12, 5), ModelTransform.pivot(3.5f, 12f, 0f));
		return TexturedModelData.of(data, 128, 64);
	}

	@Override
	public ModelPart getPart() {
		return root;
	}

	@Override
	public void setAngles(KamentEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
		head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
		head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;
		// A heavy, slow stride.
		float stride = MathHelper.cos(limbAngle * 0.45f) * 0.9f * limbDistance;
		rightLeg.pitch = stride;
		leftLeg.pitch = -stride;
		rightArm.pitch = -stride * 0.6f;
		leftArm.pitch = stride * 0.6f;
		rightArm.roll = 0.05f;
		leftArm.roll = -0.05f;
		// The slow breathing of stone.
		float breath = MathHelper.sin(animationProgress * 0.04f) * 0.03f;
		rightArm.roll += breath;
		leftArm.roll -= breath;
		KamentRole role = entity.role();
		boolean crystals = role == KamentRole.ANCIENT || role == KamentRole.SCULPTOR;
		crown.visible = crystals;
		rightCrystal.visible = role == KamentRole.ANCIENT;
		leftCrystal.visible = role == KamentRole.ANCIENT;
	}

	@Override
	public void animateModel(KamentEntity entity, float limbAngle, float limbDistance, float tickDelta) {
		int attack = entity.attackTicks();
		if (attack > 0) {
			// Both arms come down together, like a falling rock.
			float swing = -2.0f + 1.5f * MathHelper.wrap(attack - tickDelta, 10f) / 10f;
			rightArm.pitch = swing;
			leftArm.pitch = swing;
			return;
		}
		float swing = entity.getHandSwingProgress(tickDelta);
		if (swing > 0) {
			rightArm.pitch = -MathHelper.sin(swing * MathHelper.PI) * 1.6f;
		}
	}
}
