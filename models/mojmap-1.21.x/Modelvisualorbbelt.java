import net.spidrotech.duality.client.model.Modelvisualorbbelt;

import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.EntityModel;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

// Made with Blockbench 5.1.3
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports
public class Modelvisualorbbelt<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("duality", "modelvisualorbbelt"), "main");
	public final ModelPart bone;
	// Full revolutions per second - tune freely. 0.5 = one full spin every 2 seconds.
	private static final float SPIN_REVOLUTIONS_PER_SECOND = 0.5F;
	// ageInTicks is in ticks (20/sec); converting straight to radians and modding by TWO_PI
	// keeps the float from growing unbounded over a long play session (pure precision hygiene -
	// sin/cos wrap fine regardless, this just keeps the raw angle value itself small).
	private static final float RADIANS_PER_TICK = SPIN_REVOLUTIONS_PER_SECOND * ((float) Math.PI * 2f) / 20f;

	public Modelvisualorbbelt(ModelPart root) {
		this.bone = root.getChild("bone");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();
		PartDefinition bone = partdefinition.addOrReplaceChild("bone", CubeListBuilder.create().texOffs(0, 0).addBox(-9.0F, -3.5F, -9.0F, 18.0F, 7.0F, 18.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 11.5F, 0.0F));
		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int rgb) {
		bone.render(poseStack, vertexConsumer, packedLight, packedOverlay, rgb);
	}

	/**
	 * Spins the belt around its VERTICAL axis (yRot/yaw), not xRot/pitch - see class doc
	 * reasoning in the chat that produced this: a short, wide plate (18x7x18) tumbling in pitch
	 * cycles between "flat and wide" and "edge-on and thin" every quarter-turn, which reads as
	 * up-and-down jitter rather than a clean spin. Rotating around Y instead keeps the flat face
	 * oriented the same way at all times and just rotates the texture around in place - the
	 * visually correct motion for a belt/ring effect.
	 *
	 * ageInTicks is entity.tickCount + partialTick (supplied by the renderer), so this is
	 * already continuous/smooth across render frames, not just tick boundaries - no separate
	 * interpolation needed here.
	 */
	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.bone.yRot = (ageInTicks * RADIANS_PER_TICK) % (Mth.PI * 2f);
	}
}