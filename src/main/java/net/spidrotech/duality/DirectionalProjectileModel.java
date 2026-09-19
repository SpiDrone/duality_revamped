package net.spidrotech.duality;

import net.spidrotech.duality.entity.AbilityProjectileEntity;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import net.minecraft.client.model.EntityModel;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

/**
 * Wraps a projectile shape so it points the way it's flying - nose along the path, tilting down
 * as gravity bends the arc.
 *
 * <p>Opt-in per shape, in DualityProjectileModels. A round fireball doesn't need it and doesn't
 * get it, so wrapping one shape changes nothing about how the others render.
 *
 * <p>Why a wrapper and not the renderer: AbilityProjectileRenderer belongs to an MCreator element,
 * and MobRenderer only ever turns a model left/right by body yaw - never pitch. So this undoes that
 * body yaw and applies the real flight direction itself, read from the entity's yRot/xRot, which
 * AbilityProjectileBase keeps pointed along its velocity and the server syncs to every client.
 *
 * <p>The authored model is assumed to point its front along -Z (Blockbench's north), the usual
 * convention. If a shape comes out flying backwards, give it a yawOffset of 180.
 */
@OnlyIn(Dist.CLIENT)
public final class DirectionalProjectileModel extends EntityModel<AbilityProjectileEntity> {
	private final EntityModel<AbilityProjectileEntity> inner;
	private final float pivotX, pivotY, pivotZ;
	private final float yawOffset;

	// Set in setupAnim and consumed by the renderToBuffer that immediately follows it, for the same
	// entity - the renderer always calls the two back to back, so one shared instance is safe.
	private float yawFromBody;
	private float pitch;

	/**
	 * @param pivotPixels where to spin the model around, in Blockbench pixels - the centre of its
	 *                    geometry, so it turns in place instead of swinging around its base
	 */
	public DirectionalProjectileModel(EntityModel<AbilityProjectileEntity> inner, float pivotXPixels, float pivotYPixels, float pivotZPixels, float yawOffset) {
		this.inner = inner;
		this.pivotX = pivotXPixels / 16f;
		this.pivotY = pivotYPixels / 16f;
		this.pivotZ = pivotZPixels / 16f;
		this.yawOffset = yawOffset;
	}

	@Override
	public void prepareMobModel(AbilityProjectileEntity entity, float limbSwing, float limbSwingAmount, float partialTick) {
		this.inner.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTick);
	}

	@Override
	public void setupAnim(AbilityProjectileEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.inner.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
		float partialTick = ageInTicks - entity.tickCount;
		// The renderer has already turned the model by this interpolated body yaw; the rotation
		// applied below is relative to it, so it has to be taken back out.
		float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
		float flightYaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
		this.yawFromBody = Mth.wrapDegrees(flightYaw - bodyYaw) + this.yawOffset;
		this.pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int packedLight, int packedOverlay, int color) {
		poseStack.pushPose();
		// By now the renderer has flipped X and Y (scale -1,-1,1), and a Y rotation seen through
		// that flip runs backwards - which is why +yawFromBody here equals turning the body by
		// -yawFromBody before the flip. In this flipped space the model's front is -Z and down is
		// +Y, so positive pitch (vanilla's "looking down") tips the nose down, as it should.
		poseStack.translate(this.pivotX, this.pivotY, this.pivotZ);
		poseStack.mulPose(Axis.YP.rotationDegrees(this.yawFromBody));
		poseStack.mulPose(Axis.XP.rotationDegrees(this.pitch));
		poseStack.translate(-this.pivotX, -this.pivotY, -this.pivotZ);
		this.inner.renderToBuffer(poseStack, consumer, packedLight, packedOverlay, color);
		poseStack.popPose();
	}
}
