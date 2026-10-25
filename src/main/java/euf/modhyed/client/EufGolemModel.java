package euf.modhyed.client;

import euf.modhyed.EufGolemEntity;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.SinglePartEntityModel;
import net.minecraft.util.math.MathHelper;

/**
 * Euf golem, 22 px tall plus a floating crystal. Entity model space: y grows downward, the ground is at y=24.
 * The UV layout matches tools/gen_textures.py (golem_texture).
 */
public class EufGolemModel extends SinglePartEntityModel<EufGolemEntity> {
	private static final float CRYSTAL_Y = -11.0f;

	private final ModelPart root;
	private final ModelPart head;
	private final ModelPart crystal;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public EufGolemModel(ModelPart root) {
		this.root = root;
		this.head = root.getChild("head");
		this.crystal = head.getChild("crystal");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData data = new ModelData();
		ModelPartData root = data.getRoot();
		ModelPartData head = root.addChild("head",
				ModelPartBuilder.create().uv(0, 0).cuboid(-4, -8, -4, 8, 8, 8), ModelTransform.pivot(0, 10, 0));
		head.addChild("crystal",
				ModelPartBuilder.create().uv(56, 0).cuboid(-1, -2, -1, 2, 4, 2), ModelTransform.pivot(0, CRYSTAL_Y, 0));
		root.addChild("body",
				ModelPartBuilder.create().uv(0, 16).cuboid(-4, -8, -2.5f, 8, 8, 5), ModelTransform.pivot(0, 18, 0));
		root.addChild("right_arm",
				ModelPartBuilder.create().uv(32, 0).cuboid(-1.5f, -1, -1.5f, 3, 9, 3), ModelTransform.pivot(-5.5f, 11, 0));
		root.addChild("left_arm",
				ModelPartBuilder.create().uv(44, 0).cuboid(-1.5f, -1, -1.5f, 3, 9, 3), ModelTransform.pivot(5.5f, 11, 0));
		root.addChild("right_leg",
				ModelPartBuilder.create().uv(32, 16).cuboid(-1.5f, 0, -1.5f, 3, 6, 3), ModelTransform.pivot(-2, 18, 0));
		root.addChild("left_leg",
				ModelPartBuilder.create().uv(44, 16).cuboid(-1.5f, 0, -1.5f, 3, 6, 3), ModelTransform.pivot(2, 18, 0));
		return TexturedModelData.of(data, 64, 64);
	}

	@Override
	public void setAngles(EufGolemEntity entity, float limbAngle, float limbDistance, float animationProgress,
			float headYaw, float headPitch) {
		head.yaw = headYaw * MathHelper.RADIANS_PER_DEGREE;
		head.pitch = headPitch * MathHelper.RADIANS_PER_DEGREE;

		// Walking: legs and arms swing in opposition
		float swing = MathHelper.cos(limbAngle * 0.6662f) * 1.2f * limbDistance;
		rightLeg.pitch = swing;
		leftLeg.pitch = -swing;
		rightArm.pitch = -swing * 0.8f;
		leftArm.pitch = swing * 0.8f;

		// Idle "breathing": arms drift outward and back, the head nods slightly
		float breath = MathHelper.cos(animationProgress * 0.09f);
		rightArm.roll = 0.06f + breath * 0.05f;
		leftArm.roll = -0.06f - breath * 0.05f;
		head.pivotY = 10.0f + breath * 0.25f;

		// Hurt: flinch with both arms thrown up, easing back as hurtTime runs out
		if (entity.hurtTime > 0) {
			float flinch = entity.hurtTime / 10.0f;
			rightArm.pitch = -2.2f * flinch;
			leftArm.pitch = -2.2f * flinch;
			rightArm.roll += 0.4f * flinch;
			leftArm.roll -= 0.4f * flinch;
		}

		// The crystal floats: bobs up and down and spins slowly
		crystal.pivotY = CRYSTAL_Y + MathHelper.sin(animationProgress * 0.1f) * 0.8f;
		crystal.yaw = animationProgress * 0.05f;
	}

	@Override
	public ModelPart getPart() {
		return root;
	}
}
