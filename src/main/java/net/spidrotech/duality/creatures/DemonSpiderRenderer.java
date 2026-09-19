package net.spidrotech.duality.creatures;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * Renders the demonic spider. Hand-written, like everything else under creatures/ - see
 * DemonSpiderEntity for why none of this can live in an MCreator element.
 *
 * setupRotations is what tips the whole spider onto the wall as it climbs, rather than leaving it
 * standing upright and sliding up the surface.
 *
 * The rotation is deliberately render-only - it never touches the entity's real yRot, so the
 * pathfinder and look control keep working in plain world space and cannot fight the visual.
 */
public class DemonSpiderRenderer extends MobRenderer<DemonSpiderEntity, DemonSpiderModel<DemonSpiderEntity>> {
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

	public DemonSpiderRenderer(EntityRendererProvider.Context context) {
		super(context, new DemonSpiderModel<>(context.bakeLayer(DemonSpiderEntities.DEMON_SPIDER_LAYER)), 0.5f);
	}

	@Override
	protected void setupRotations(DemonSpiderEntity entity, PoseStack poseStack, float ageInTicks, float bodyYRot, float partialTicks, float scale) {
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
	public ResourceLocation getTextureLocation(DemonSpiderEntity entity) {
		return ResourceLocation.parse("duality:textures/entities/demonic_spider.png");
	}
}
