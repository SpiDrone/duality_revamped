package net.spidrotech.duality.creatures;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The demonic spider's geometry - the Blockbench export's createBodyLayer, and only that.
 *
 * <p>Kept here rather than in Modeldemonic_spider because that class is MCreator's and is
 * rewritten from its own copy of the model on every build. Both things that draw the spider - the
 * mob (DemonSpiderModel) and the shapeshift form (SpiderFormModel) - bake this through
 * DemonSpiderEntities.DEMON_SPIDER_LAYER, so replacing the mesh here updates both.
 *
 * <p>To swap in a new export: replace the body of createBodyLayer with the new one. The animations
 * find bones by name, so it works as long as the bone names demonic_spiderAnimation uses still
 * exist - root, body, head, abdomen, stinger(_tip), mandible_*, and leg_{left,right}_{1-4}(_upper,
 * _lower). Nothing else needs to change.
 */
public final class DemonSpiderMesh {
	private DemonSpiderMesh() {
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(78, 0).addBox(-5.0F, -3.0F, -5.0F, 10.0F, 6.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -13.0F, 0.0F));

		PartDefinition abdomen = body.addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -6.0F, 0.0F, 12.0F, 10.0F, 14.0F, new CubeDeformation(0.0F))
		.texOffs(53, 0).addBox(-4.0F, -4.5F, 14.0F, 8.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.2094F, 0.0F, 0.0F));

		PartDefinition spike_1 = abdomen.addOrReplaceChild("spike_1", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, -6.0F, 4.0F, -0.5585F, 0.0F, 0.3142F));

		PartDefinition spike_1_tip = spike_1.addOrReplaceChild("spike_1_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition spike_2 = abdomen.addOrReplaceChild("spike_2", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, -6.0F, 4.0F, -0.5585F, 0.0F, -0.3142F));

		PartDefinition spike_2_tip = spike_2.addOrReplaceChild("spike_2_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition spike_3 = abdomen.addOrReplaceChild("spike_3", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, -6.0F, 9.0F, -0.5585F, 0.0F, 0.384F));

		PartDefinition spike_3_tip = spike_3.addOrReplaceChild("spike_3_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition spike_4 = abdomen.addOrReplaceChild("spike_4", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, -6.0F, 9.0F, -0.5585F, 0.0F, -0.384F));

		PartDefinition spike_4_tip = spike_4.addOrReplaceChild("spike_4_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition spike_5 = abdomen.addOrReplaceChild("spike_5", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -6.0F, 2.0F, -0.5585F, 0.0F, 0.0F));

		PartDefinition spike_5_tip = spike_5.addOrReplaceChild("spike_5_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition spike_6 = abdomen.addOrReplaceChild("spike_6", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -6.0F, 7.0F, -0.5585F, 0.0F, 0.0F));

		PartDefinition spike_6_tip = spike_6.addOrReplaceChild("spike_6_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition spike_7 = abdomen.addOrReplaceChild("spike_7", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -6.0F, 12.0F, -0.5585F, 0.0F, 0.0F));

		PartDefinition spike_7_tip = spike_7.addOrReplaceChild("spike_7_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition stinger = abdomen.addOrReplaceChild("stinger", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -1.0F, 18.0F, 0.6109F, 0.0F, 0.0F));

		PartDefinition stinger_tip = stinger.addOrReplaceChild("stinger_tip", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, 0.6981F, 0.0F, 0.0F));

		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 25).addBox(-4.0F, -4.0F, -7.0F, 8.0F, 7.0F, 7.0F, new CubeDeformation(0.0F))
		.texOffs(54, 25).addBox(1.5F, -3.0F, -8.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(54, 25).addBox(-3.5F, -3.0F, -8.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -5.0F));

		PartDefinition horn_right = head.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(9, 40).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, -4.0F, -3.0F, -0.6632F, 0.0F, 0.384F));

		PartDefinition horn_right_tip = horn_right.addOrReplaceChild("horn_right_tip", CubeListBuilder.create().texOffs(18, 40).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, -0.733F, 0.0F, 0.1745F));

		PartDefinition horn_left = head.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(9, 40).mirror().addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-3.0F, -4.0F, -3.0F, -0.6632F, 0.0F, -0.384F));

		PartDefinition horn_left_tip = horn_left.addOrReplaceChild("horn_left_tip", CubeListBuilder.create().texOffs(18, 40).mirror().addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, -0.733F, 0.0F, -0.1745F));

		PartDefinition mandible_right = head.addOrReplaceChild("mandible_right", CubeListBuilder.create().texOffs(23, 40).addBox(-1.0F, -0.7718F, -1.9833F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, 2.0F, -6.0F, -0.2618F, 0.0F, 0.0F));

		PartDefinition fang_right = mandible_right.addOrReplaceChild("fang_right", CubeListBuilder.create().texOffs(32, 40).addBox(-0.7891F, -0.3767F, -1.6563F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, -0.3142F, 0.0F, 0.384F));

		PartDefinition mandible_left = head.addOrReplaceChild("mandible_left", CubeListBuilder.create().texOffs(23, 40).mirror().addBox(-1.0F, -0.7718F, -1.9833F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-2.5F, 2.0F, -6.0F, -0.2618F, 0.0F, 0.0F));

		PartDefinition fang_left = mandible_left.addOrReplaceChild("fang_left", CubeListBuilder.create().texOffs(32, 40).mirror().addBox(-0.2109F, -0.3767F, -1.6563F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, -0.3142F, 0.0F, -0.384F));

		PartDefinition leg_right_1 = body.addOrReplaceChild("leg_right_1", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 0.0F, -4.0F, 0.0F, 0.733F, 0.0F));

		PartDefinition leg_right_1_upper = leg_right_1.addOrReplaceChild("leg_right_1_upper", CubeListBuilder.create().texOffs(61, 25).addBox(0.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(84, 25).addBox(5.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.4712F));

		PartDefinition leg_right_1_lower = leg_right_1_upper.addOrReplaceChild("leg_right_1_lower", CubeListBuilder.create().texOffs(89, 25).addBox(0.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.01F))
		.texOffs(89, 31).addBox(0.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.2F))
		.texOffs(0, 40).addBox(16.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.6057F));

		PartDefinition leg_right_2 = body.addOrReplaceChild("leg_right_2", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 0.0F, -2.0F, 0.0F, 0.2793F, 0.0F));

		PartDefinition leg_right_2_upper = leg_right_2.addOrReplaceChild("leg_right_2_upper", CubeListBuilder.create().texOffs(61, 25).addBox(0.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(84, 25).addBox(5.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.4189F));

		PartDefinition leg_right_2_lower = leg_right_2_upper.addOrReplaceChild("leg_right_2_lower", CubeListBuilder.create().texOffs(89, 25).addBox(0.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.01F))
		.texOffs(89, 31).addBox(0.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.2F))
		.texOffs(0, 40).addBox(16.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.5533F));

		PartDefinition leg_right_3 = body.addOrReplaceChild("leg_right_3", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.0F, -0.2443F, 0.0F));

		PartDefinition leg_right_3_upper = leg_right_3.addOrReplaceChild("leg_right_3_upper", CubeListBuilder.create().texOffs(61, 25).addBox(0.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(84, 25).addBox(5.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.4189F));

		PartDefinition leg_right_3_lower = leg_right_3_upper.addOrReplaceChild("leg_right_3_lower", CubeListBuilder.create().texOffs(89, 25).addBox(0.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.01F))
		.texOffs(89, 31).addBox(0.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.2F))
		.texOffs(0, 40).addBox(16.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.5533F));

		PartDefinition leg_right_4 = body.addOrReplaceChild("leg_right_4", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 0.0F, 2.0F, 0.0F, -0.6981F, 0.0F));

		PartDefinition leg_right_4_upper = leg_right_4.addOrReplaceChild("leg_right_4_upper", CubeListBuilder.create().texOffs(61, 25).addBox(0.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(84, 25).addBox(5.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.4712F));

		PartDefinition leg_right_4_lower = leg_right_4_upper.addOrReplaceChild("leg_right_4_lower", CubeListBuilder.create().texOffs(89, 25).addBox(0.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.01F))
		.texOffs(89, 31).addBox(0.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.2F))
		.texOffs(0, 40).addBox(16.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(9.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.6057F));

		PartDefinition leg_left_1 = body.addOrReplaceChild("leg_left_1", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 0.0F, -4.0F, 0.0F, -0.733F, 0.0F));

		PartDefinition leg_left_1_upper = leg_left_1.addOrReplaceChild("leg_left_1_upper", CubeListBuilder.create().texOffs(61, 25).mirror().addBox(-9.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(84, 25).mirror().addBox(-6.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.4712F));

		PartDefinition leg_left_1_lower = leg_left_1_upper.addOrReplaceChild("leg_left_1_lower", CubeListBuilder.create().texOffs(89, 25).mirror().addBox(-16.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.01F)).mirror(false)
		.texOffs(89, 31).mirror().addBox(-16.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.2F)).mirror(false)
		.texOffs(0, 40).mirror().addBox(-19.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-9.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.6057F));

		PartDefinition leg_left_2 = body.addOrReplaceChild("leg_left_2", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 0.0F, -2.0F, 0.0F, -0.2793F, 0.0F));

		PartDefinition leg_left_2_upper = leg_left_2.addOrReplaceChild("leg_left_2_upper", CubeListBuilder.create().texOffs(61, 25).mirror().addBox(-9.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(84, 25).mirror().addBox(-6.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.4189F));

		PartDefinition leg_left_2_lower = leg_left_2_upper.addOrReplaceChild("leg_left_2_lower", CubeListBuilder.create().texOffs(89, 25).mirror().addBox(-16.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.01F)).mirror(false)
		.texOffs(89, 31).mirror().addBox(-16.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.2F)).mirror(false)
		.texOffs(0, 40).mirror().addBox(-19.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-9.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.5533F));

		PartDefinition leg_left_3 = body.addOrReplaceChild("leg_left_3", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.0F, 0.2443F, 0.0F));

		PartDefinition leg_left_3_upper = leg_left_3.addOrReplaceChild("leg_left_3_upper", CubeListBuilder.create().texOffs(61, 25).mirror().addBox(-9.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(84, 25).mirror().addBox(-6.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.4189F));

		PartDefinition leg_left_3_lower = leg_left_3_upper.addOrReplaceChild("leg_left_3_lower", CubeListBuilder.create().texOffs(89, 25).mirror().addBox(-16.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.01F)).mirror(false)
		.texOffs(89, 31).mirror().addBox(-16.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.2F)).mirror(false)
		.texOffs(0, 40).mirror().addBox(-19.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-9.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.5533F));

		PartDefinition leg_left_4 = body.addOrReplaceChild("leg_left_4", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 0.0F, 2.0F, 0.0F, 0.6981F, 0.0F));

		PartDefinition leg_left_4_upper = leg_left_4.addOrReplaceChild("leg_left_4_upper", CubeListBuilder.create().texOffs(61, 25).mirror().addBox(-9.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false)
		.texOffs(84, 25).mirror().addBox(-6.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.4712F));

		PartDefinition leg_left_4_lower = leg_left_4_upper.addOrReplaceChild("leg_left_4_lower", CubeListBuilder.create().texOffs(89, 25).mirror().addBox(-16.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.01F)).mirror(false)
		.texOffs(89, 31).mirror().addBox(-16.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.2F)).mirror(false)
		.texOffs(0, 40).mirror().addBox(-19.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-9.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.6057F));

		PartDefinition spike_8 = body.addOrReplaceChild("spike_8", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.5F, -3.0F, -2.0F, -0.5585F, 0.0F, 0.2094F));

		PartDefinition spike_8_tip = spike_8.addOrReplaceChild("spike_8_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));

		PartDefinition spike_9 = body.addOrReplaceChild("spike_9", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-2.5F, -3.0F, -2.0F, -0.5585F, 0.0F, -0.2094F));

		PartDefinition spike_9_tip = spike_9.addOrReplaceChild("spike_9_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 64);
	}
}
