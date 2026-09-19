package net.spidrotech.duality.abilities.shapeshift.client;

import net.spidrotech.duality.abilities.shapeshift.DualityShapeshiftForms;
import net.spidrotech.duality.abilities.shapeshift.Shapeshift;
import net.spidrotech.duality.abilities.shapeshift.ShapeshiftForm;
import net.spidrotech.duality.creatures.DemonSpiderEntities;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Draws a shapeshifted player as their form instead of as themselves.
 *
 * <p>Built on the approach in TransformationTestProcedure, with the two pieces that were missing
 * there: the player is actually hidden (that one drew the form over the top of them), and which
 * model gets drawn comes from the player's current form rather than being hardcoded, so everyone
 * who isn't shifted still renders normally.
 *
 * <p>Forms are pure server-side data and hold no client classes, so the link between the two is
 * this map, keyed by form id. A form with no entry renders as the player - which is the right
 * fallback, since a form is allowed to change only stats.
 *
 * <p>Models are baked lazily on first use: the entity-model set isn't ready when this class loads,
 * and baking per frame would be wasteful.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class ShapeshiftFormRenderers {
	/**
	 * What a form looks like: which layer to bake, how to wrap it, and what to skin it with.
	 *
	 * @param climbsOnWalls lay the model flat against the wall while wall climbing, head up, like
	 *                      the spider mob does. Leave off for forms that would look wrong sideways;
	 *                      they still climb, they just stay upright doing it.
	 */
	private record FormModel(ModelLayerLocation layer, Function<ModelPart, EntityModel<AbstractClientPlayer>> factory, ResourceLocation texture, boolean climbsOnWalls) {
	}

	/**
	 * How the model is tipped onto the wall. Same numbers as DemonSpiderRenderer, which draws the
	 * same mesh: +90 puts the belly on the wall with the head pointing up it, and the two offsets
	 * (in blocks) slide it back against the wall and up off the floor, since pitching about the feet
	 * swings the body out into the air.
	 */
	private static final float CLIMB_PITCH_DEGREES = 90f;
	private static final float CLIMB_WALL_INSET = 0.3f;
	private static final float CLIMB_LIFT = 0.5f;

	private static final Map<ResourceLocation, FormModel> FORMS = new HashMap<>();
	private static final Map<ResourceLocation, EntityModel<AbstractClientPlayer>> BAKED = new HashMap<>();

	static {
		FORMS.put(DualityShapeshiftForms.DEMON_SPIDER, new FormModel(DemonSpiderEntities.DEMON_SPIDER_LAYER, SpiderFormModel::new,
				ResourceLocation.parse("duality:textures/entities/demonic_spider.png"), true));
	}

	private ShapeshiftFormRenderers() {
	}

	@SubscribeEvent
	public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
		ShapeshiftForm form = Shapeshift.current(event.getEntity()).orElse(null);
		if (form == null)
			return;
		FormModel formModel = FORMS.get(form.id());
		if (formModel == null)
			return;

		// Taking over the render completely, rather than drawing on top of the player.
		event.setCanceled(true);
		render(event, formModel, baked(formModel));
	}

	private static EntityModel<AbstractClientPlayer> baked(FormModel form) {
		return BAKED.computeIfAbsent(form.texture(), key -> form.factory().apply(Minecraft.getInstance().getEntityModels().bakeLayer(form.layer())));
	}

	/**
	 * Positions and draws the form's model where the player would have been. The transform sequence
	 * mirrors what LivingEntityRenderer does for any mob - rotations, then the flip and 1.501 drop
	 * that put a model's feet on the ground - because the form has to end up standing where the
	 * player stands.
	 */
	private static void render(RenderPlayerEvent.Pre event, FormModel form, EntityModel<AbstractClientPlayer> model) {
		AbstractClientPlayer player = (AbstractClientPlayer) event.getEntity();
		PoseStack poseStack = event.getPoseStack();
		float partialTick = event.getPartialTick();

		float limbSwing = player.walkAnimation.position(partialTick);
		float limbSwingAmount = player.walkAnimation.speed(partialTick);
		float ageInTicks = player.tickCount + partialTick;
		float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
		float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot);
		float headPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());

		poseStack.pushPose();
		event.getRenderer().setupRotations(player, poseStack, ageInTicks, bodyYaw, partialTick, 1.0f);
		if (form.climbsOnWalls())
			tiltOntoWall(player, poseStack, bodyYaw, partialTick);
		poseStack.scale(-1.0f, -1.0f, 1.0f);
		poseStack.translate(0.0f, -1.501f, 0.0f);
		model.prepareMobModel(player, limbSwing, limbSwingAmount, partialTick);
		model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, headYaw - bodyYaw, headPitch);
		VertexConsumer consumer = event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(form.texture()));
		model.renderToBuffer(poseStack, consumer, event.getPackedLight(), LivingEntityRenderer.getOverlayCoords(player, 0));
		poseStack.popPose();
	}

	/**
	 * Turns the model to face into the wall, then tips it flat against it - the same sequence
	 * DemonSpiderRenderer uses. setupRotations has already turned it by the player's body yaw, so
	 * rolling in the difference to the wall's yaw faces it straight at the wall, and the tip is then
	 * one clean pitch. Scaled by the eased climb amount, so it rolls on and off rather than snapping.
	 */
	private static void tiltOntoWall(AbstractClientPlayer player, PoseStack poseStack, float bodyYaw, float partialTick) {
		float climb = ShapeshiftClimbPose.amount(player, partialTick);
		if (climb <= 1.0E-4f)
			return;
		float yawToWall = Mth.degreesDifference(bodyYaw, ShapeshiftClimbPose.wallYaw(player)) * climb;
		poseStack.mulPose(Axis.YP.rotationDegrees(-yawToWall));
		poseStack.mulPose(Axis.XP.rotationDegrees(CLIMB_PITCH_DEGREES * climb));
		poseStack.translate(0f, -CLIMB_WALL_INSET * climb, -CLIMB_LIFT * climb);
	}
}
