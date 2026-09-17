package net.spidrotech.duality.client.renderer;

import net.spidrotech.duality.entity.SpiderQueenEntity;
import net.spidrotech.duality.creatures.DemonSpiderModel;
import net.spidrotech.duality.client.model.Modeldemonic_spider;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * MCreator generates this renderer; the DemonSpiderModel swap and the setupRotations override
 * are hand-written and will be lost if you regenerate the Spider Queen element. The model swap is
 * what makes the spider animate at all (see DemonSpiderModel for why the Blockbench class can't).
 * setupRotations tips the entire spider onto the wall as it climbs, rather than leaving it
 * standing upright and sliding up the surface.
 *
 * The rotation is deliberately render-only - it never touches the entity's real yRot, so the
 * pathfinder and look control keep working in plain world space and cannot fight the visual.
 */
public class SpiderQueenRenderer extends MobRenderer<SpiderQueenEntity, DemonSpiderModel<SpiderQueenEntity>> {
	/**
	 * How far the spider tips onto the wall at full climb. After the yaw rotation the model's
	 * forward is local -Z and its up is +Y; +90 about X sends forward to world up and up to
	 * away-from-the-wall, i.e. belly on the wall, head up. (-90 was the upside-down version.)
	 */
	private static final float CLIMB_PITCH_DEGREES = 90f;

	/**
	 * Pitching about the feet leaves the belly floating half a hitbox off the wall and the long
	 * abdomen hanging down through the floor. These slide it back, in blocks, scaled by climb
	 * progress. They are applied in the rotated frame, where local -Y is toward the wall and
	 * local -Z is world up - see TUNING.md.
	 */
	private static final float CLIMB_WALL_INSET = 0.3f;
	private static final float CLIMB_LIFT = 0.5f;

	public SpiderQueenRenderer(EntityRendererProvider.Context context) {
		super(context, new DemonSpiderModel<>(context.bakeLayer(Modeldemonic_spider.LAYER_LOCATION)), 0.5f);
	}

	@Override
	protected void setupRotations(SpiderQueenEntity entity, PoseStack poseStack, float ageInTicks, float bodyYRot, float partialTicks, float scale) {
		super.setupRotations(entity, poseStack, ageInTicks, bodyYRot, partialTicks, scale);

		float climb = entity.getClimbAmount(partialTicks);
		if (climb <= 1.0E-4f)
			return;

		// super already rotated by (180 - bodyYRot). Rolling in the difference between the
		// body yaw and the wall's yaw makes the spider face straight into the wall, which
		// turns the tip-onto-the-wall into a single clean pitch about one axis.
		float yawToWall = Mth.degreesDifference(bodyYRot, entity.getClimbWallYaw()) * climb;
		poseStack.mulPose(Axis.YP.rotationDegrees(-yawToWall));
		poseStack.mulPose(Axis.XP.rotationDegrees(CLIMB_PITCH_DEGREES * climb));
		poseStack.translate(0f, -CLIMB_WALL_INSET * climb, -CLIMB_LIFT * climb);
	}

	@Override
	public ResourceLocation getTextureLocation(SpiderQueenEntity entity) {
		return ResourceLocation.parse("duality:textures/entities/demonic_spider.png");
	}
}
