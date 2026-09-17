package net.spidrotech.duality.client.model;

import net.minecraft.world.entity.Entity;
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
public class Modeldemonic_spider<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("duality", "modeldemonic_spider"), "main");
	public final ModelPart root;
	public final ModelPart body;
	public final ModelPart abdomen;
	public final ModelPart spike_1;
	public final ModelPart spike_1_tip;
	public final ModelPart spike_2;
	public final ModelPart spike_2_tip;
	public final ModelPart spike_3;
	public final ModelPart spike_3_tip;
	public final ModelPart spike_4;
	public final ModelPart spike_4_tip;
	public final ModelPart spike_5;
	public final ModelPart spike_5_tip;
	public final ModelPart spike_6;
	public final ModelPart spike_6_tip;
	public final ModelPart spike_7;
	public final ModelPart spike_7_tip;
	public final ModelPart stinger;
	public final ModelPart stinger_tip;
	public final ModelPart head;
	public final ModelPart horn_right;
	public final ModelPart horn_right_tip;
	public final ModelPart horn_left;
	public final ModelPart horn_left_tip;
	public final ModelPart mandible_right;
	public final ModelPart fang_right;
	public final ModelPart mandible_left;
	public final ModelPart fang_left;
	public final ModelPart leg_right_1;
	public final ModelPart leg_right_1_upper;
	public final ModelPart leg_right_1_lower;
	public final ModelPart leg_right_2;
	public final ModelPart leg_right_2_upper;
	public final ModelPart leg_right_2_lower;
	public final ModelPart leg_right_3;
	public final ModelPart leg_right_3_upper;
	public final ModelPart leg_right_3_lower;
	public final ModelPart leg_right_4;
	public final ModelPart leg_right_4_upper;
	public final ModelPart leg_right_4_lower;
	public final ModelPart leg_left_1;
	public final ModelPart leg_left_1_upper;
	public final ModelPart leg_left_1_lower;
	public final ModelPart leg_left_2;
	public final ModelPart leg_left_2_upper;
	public final ModelPart leg_left_2_lower;
	public final ModelPart leg_left_3;
	public final ModelPart leg_left_3_upper;
	public final ModelPart leg_left_3_lower;
	public final ModelPart leg_left_4;
	public final ModelPart leg_left_4_upper;
	public final ModelPart leg_left_4_lower;
	public final ModelPart spike_8;
	public final ModelPart spike_8_tip;
	public final ModelPart spike_9;
	public final ModelPart spike_9_tip;

	public Modeldemonic_spider(ModelPart root) {
		this.root = root.getChild("root");
		this.body = this.root.getChild("body");
		this.abdomen = this.body.getChild("abdomen");
		this.spike_1 = this.abdomen.getChild("spike_1");
		this.spike_1_tip = this.spike_1.getChild("spike_1_tip");
		this.spike_2 = this.abdomen.getChild("spike_2");
		this.spike_2_tip = this.spike_2.getChild("spike_2_tip");
		this.spike_3 = this.abdomen.getChild("spike_3");
		this.spike_3_tip = this.spike_3.getChild("spike_3_tip");
		this.spike_4 = this.abdomen.getChild("spike_4");
		this.spike_4_tip = this.spike_4.getChild("spike_4_tip");
		this.spike_5 = this.abdomen.getChild("spike_5");
		this.spike_5_tip = this.spike_5.getChild("spike_5_tip");
		this.spike_6 = this.abdomen.getChild("spike_6");
		this.spike_6_tip = this.spike_6.getChild("spike_6_tip");
		this.spike_7 = this.abdomen.getChild("spike_7");
		this.spike_7_tip = this.spike_7.getChild("spike_7_tip");
		this.stinger = this.abdomen.getChild("stinger");
		this.stinger_tip = this.stinger.getChild("stinger_tip");
		this.head = this.body.getChild("head");
		this.horn_right = this.head.getChild("horn_right");
		this.horn_right_tip = this.horn_right.getChild("horn_right_tip");
		this.horn_left = this.head.getChild("horn_left");
		this.horn_left_tip = this.horn_left.getChild("horn_left_tip");
		this.mandible_right = this.head.getChild("mandible_right");
		this.fang_right = this.mandible_right.getChild("fang_right");
		this.mandible_left = this.head.getChild("mandible_left");
		this.fang_left = this.mandible_left.getChild("fang_left");
		this.leg_right_1 = this.body.getChild("leg_right_1");
		this.leg_right_1_upper = this.leg_right_1.getChild("leg_right_1_upper");
		this.leg_right_1_lower = this.leg_right_1_upper.getChild("leg_right_1_lower");
		this.leg_right_2 = this.body.getChild("leg_right_2");
		this.leg_right_2_upper = this.leg_right_2.getChild("leg_right_2_upper");
		this.leg_right_2_lower = this.leg_right_2_upper.getChild("leg_right_2_lower");
		this.leg_right_3 = this.body.getChild("leg_right_3");
		this.leg_right_3_upper = this.leg_right_3.getChild("leg_right_3_upper");
		this.leg_right_3_lower = this.leg_right_3_upper.getChild("leg_right_3_lower");
		this.leg_right_4 = this.body.getChild("leg_right_4");
		this.leg_right_4_upper = this.leg_right_4.getChild("leg_right_4_upper");
		this.leg_right_4_lower = this.leg_right_4_upper.getChild("leg_right_4_lower");
		this.leg_left_1 = this.body.getChild("leg_left_1");
		this.leg_left_1_upper = this.leg_left_1.getChild("leg_left_1_upper");
		this.leg_left_1_lower = this.leg_left_1_upper.getChild("leg_left_1_lower");
		this.leg_left_2 = this.body.getChild("leg_left_2");
		this.leg_left_2_upper = this.leg_left_2.getChild("leg_left_2_upper");
		this.leg_left_2_lower = this.leg_left_2_upper.getChild("leg_left_2_lower");
		this.leg_left_3 = this.body.getChild("leg_left_3");
		this.leg_left_3_upper = this.leg_left_3.getChild("leg_left_3_upper");
		this.leg_left_3_lower = this.leg_left_3_upper.getChild("leg_left_3_lower");
		this.leg_left_4 = this.body.getChild("leg_left_4");
		this.leg_left_4_upper = this.leg_left_4.getChild("leg_left_4_upper");
		this.leg_left_4_lower = this.leg_left_4_upper.getChild("leg_left_4_lower");
		this.spike_8 = this.body.getChild("spike_8");
		this.spike_8_tip = this.spike_8.getChild("spike_8_tip");
		this.spike_9 = this.body.getChild("spike_9");
		this.spike_9_tip = this.spike_9.getChild("spike_9_tip");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();
		PartDefinition root = partdefinition.addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(78, 0).addBox(-5.0F, -3.0F, -5.0F, 10.0F, 6.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -13.0F, 0.0F));
		PartDefinition abdomen = body.addOrReplaceChild("abdomen",
				CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -6.0F, 0.0F, 12.0F, 10.0F, 14.0F, new CubeDeformation(0.0F)).texOffs(53, 0).addBox(-4.0F, -4.5F, 14.0F, 8.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 0.0F, 3.0F, 0.2094F, 0.0F, 0.0F));
		PartDefinition spike_1 = abdomen.addOrReplaceChild("spike_1", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(3.5F, -6.0F, 4.0F, -0.5585F, 0.0F, 0.3142F));
		PartDefinition spike_1_tip = spike_1.addOrReplaceChild("spike_1_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));
		PartDefinition spike_2 = abdomen.addOrReplaceChild("spike_2", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-3.5F, -6.0F, 4.0F, -0.5585F, 0.0F, -0.3142F));
		PartDefinition spike_2_tip = spike_2.addOrReplaceChild("spike_2_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));
		PartDefinition spike_3 = abdomen.addOrReplaceChild("spike_3", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(3.5F, -6.0F, 9.0F, -0.5585F, 0.0F, 0.384F));
		PartDefinition spike_3_tip = spike_3.addOrReplaceChild("spike_3_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));
		PartDefinition spike_4 = abdomen.addOrReplaceChild("spike_4", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-3.5F, -6.0F, 9.0F, -0.5585F, 0.0F, -0.384F));
		PartDefinition spike_4_tip = spike_4.addOrReplaceChild("spike_4_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));
		PartDefinition spike_5 = abdomen.addOrReplaceChild("spike_5", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -6.0F, 2.0F, -0.5585F, 0.0F, 0.0F));
		PartDefinition spike_5_tip = spike_5.addOrReplaceChild("spike_5_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));
		PartDefinition spike_6 = abdomen.addOrReplaceChild("spike_6", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -6.0F, 7.0F, -0.5585F, 0.0F, 0.0F));
		PartDefinition spike_6_tip = spike_6.addOrReplaceChild("spike_6_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));
		PartDefinition spike_7 = abdomen.addOrReplaceChild("spike_7", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -6.0F, 12.0F, -0.5585F, 0.0F, 0.0F));
		PartDefinition spike_7_tip = spike_7.addOrReplaceChild("spike_7_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));
		PartDefinition stinger = abdomen.addOrReplaceChild("stinger", CubeListBuilder.create().texOffs(51, 40).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -1.0F, 18.0F, 0.6109F, 0.0F, 0.0F));
		PartDefinition stinger_tip = stinger.addOrReplaceChild("stinger_tip", CubeListBuilder.create().texOffs(66, 40).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 0.0F, 5.0F, 0.6981F, 0.0F, 0.0F));
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 25).addBox(-4.0F, -4.0F, -7.0F, 8.0F, 7.0F, 7.0F, new CubeDeformation(0.0F)).texOffs(54, 25)
				.addBox(1.5F, -3.0F, -8.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).texOffs(54, 25).addBox(-3.5F, -3.0F, -8.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, -5.0F));
		PartDefinition horn_right = head.addOrReplaceChild("horn_right", CubeListBuilder.create().texOffs(9, 40).addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(3.0F, -4.0F, -3.0F, -0.6632F, 0.0F, 0.384F));
		PartDefinition horn_right_tip = horn_right.addOrReplaceChild("horn_right_tip", CubeListBuilder.create().texOffs(18, 40).addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, -0.733F, 0.0F, 0.1745F));
		PartDefinition horn_left = head.addOrReplaceChild("horn_left", CubeListBuilder.create().texOffs(9, 40).mirror().addBox(-1.0F, -4.0F, -1.0F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(-3.0F, -4.0F, -3.0F, -0.6632F, 0.0F, -0.384F));
		PartDefinition horn_left_tip = horn_left.addOrReplaceChild("horn_left_tip", CubeListBuilder.create().texOffs(18, 40).mirror().addBox(-0.5F, -4.0F, -0.5F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, -0.733F, 0.0F, -0.1745F));
		PartDefinition mandible_right = head.addOrReplaceChild("mandible_right", CubeListBuilder.create().texOffs(23, 40).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(2.5F, 2.0F, -6.0F, -0.2618F, 0.0F, 0.0F));
		PartDefinition fang_right = mandible_right.addOrReplaceChild("fang_right", CubeListBuilder.create().texOffs(32, 40).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, -0.3142F, 0.0F, 0.384F));
		PartDefinition mandible_left = head.addOrReplaceChild("mandible_left", CubeListBuilder.create().texOffs(23, 40).mirror().addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(-2.5F, 2.0F, -6.0F, -0.2618F, 0.0F, 0.0F));
		PartDefinition fang_left = mandible_left.addOrReplaceChild("fang_left", CubeListBuilder.create().texOffs(32, 40).mirror().addBox(-0.5F, 0.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false),
				PartPose.offsetAndRotation(0.0F, 3.0F, 0.0F, -0.3142F, 0.0F, -0.384F));
		PartDefinition leg_right_1 = body.addOrReplaceChild("leg_right_1", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 0.0F, -4.0F, 0.0F, 0.733F, 0.0F));
		PartDefinition leg_right_1_upper = leg_right_1.addOrReplaceChild("leg_right_1_upper",
				CubeListBuilder.create().texOffs(61, 25).addBox(0.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(84, 25).addBox(5.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.4712F));
		PartDefinition leg_right_1_lower = leg_right_1_upper.addOrReplaceChild("leg_right_1_lower",
				CubeListBuilder.create().texOffs(89, 25).addBox(0.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(0, 40).addBox(16.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(9.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.6057F));
		PartDefinition leg_right_2 = body.addOrReplaceChild("leg_right_2", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 0.0F, -2.0F, 0.0F, 0.2793F, 0.0F));
		PartDefinition leg_right_2_upper = leg_right_2.addOrReplaceChild("leg_right_2_upper",
				CubeListBuilder.create().texOffs(61, 25).addBox(0.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(84, 25).addBox(5.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.4189F));
		PartDefinition leg_right_2_lower = leg_right_2_upper.addOrReplaceChild("leg_right_2_lower",
				CubeListBuilder.create().texOffs(89, 25).addBox(0.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(0, 40).addBox(16.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(9.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.5533F));
		PartDefinition leg_right_3 = body.addOrReplaceChild("leg_right_3", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, 0.0F, -0.2443F, 0.0F));
		PartDefinition leg_right_3_upper = leg_right_3.addOrReplaceChild("leg_right_3_upper",
				CubeListBuilder.create().texOffs(61, 25).addBox(0.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(84, 25).addBox(5.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.4189F));
		PartDefinition leg_right_3_lower = leg_right_3_upper.addOrReplaceChild("leg_right_3_lower",
				CubeListBuilder.create().texOffs(89, 25).addBox(0.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(0, 40).addBox(16.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(9.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.5533F));
		PartDefinition leg_right_4 = body.addOrReplaceChild("leg_right_4", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 0.0F, 2.0F, 0.0F, -0.6981F, 0.0F));
		PartDefinition leg_right_4_upper = leg_right_4.addOrReplaceChild("leg_right_4_upper",
				CubeListBuilder.create().texOffs(61, 25).addBox(0.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(84, 25).addBox(5.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, -0.4712F));
		PartDefinition leg_right_4_lower = leg_right_4_upper.addOrReplaceChild("leg_right_4_lower",
				CubeListBuilder.create().texOffs(89, 25).addBox(0.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).texOffs(0, 40).addBox(16.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(9.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.6057F));
		PartDefinition leg_left_1 = body.addOrReplaceChild("leg_left_1", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 0.0F, -4.0F, 0.0F, -0.733F, 0.0F));
		PartDefinition leg_left_1_upper = leg_left_1.addOrReplaceChild("leg_left_1_upper", CubeListBuilder.create().texOffs(61, 25).mirror().addBox(-9.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(84, 25)
				.mirror().addBox(-6.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.4712F));
		PartDefinition leg_left_1_lower = leg_left_1_upper.addOrReplaceChild("leg_left_1_lower", CubeListBuilder.create().texOffs(89, 25).mirror().addBox(-16.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(0, 40)
				.mirror().addBox(-19.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-9.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.6057F));
		PartDefinition leg_left_2 = body.addOrReplaceChild("leg_left_2", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 0.0F, -2.0F, 0.0F, -0.2793F, 0.0F));
		PartDefinition leg_left_2_upper = leg_left_2.addOrReplaceChild("leg_left_2_upper", CubeListBuilder.create().texOffs(61, 25).mirror().addBox(-9.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(84, 25)
				.mirror().addBox(-6.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.4189F));
		PartDefinition leg_left_2_lower = leg_left_2_upper.addOrReplaceChild("leg_left_2_lower", CubeListBuilder.create().texOffs(89, 25).mirror().addBox(-16.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(0, 40)
				.mirror().addBox(-19.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-9.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.5533F));
		PartDefinition leg_left_3 = body.addOrReplaceChild("leg_left_3", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, 0.0F, 0.2443F, 0.0F));
		PartDefinition leg_left_3_upper = leg_left_3.addOrReplaceChild("leg_left_3_upper", CubeListBuilder.create().texOffs(61, 25).mirror().addBox(-9.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(84, 25)
				.mirror().addBox(-6.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.4189F));
		PartDefinition leg_left_3_lower = leg_left_3_upper.addOrReplaceChild("leg_left_3_lower", CubeListBuilder.create().texOffs(89, 25).mirror().addBox(-16.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(0, 40)
				.mirror().addBox(-19.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-9.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.5533F));
		PartDefinition leg_left_4 = body.addOrReplaceChild("leg_left_4", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 0.0F, 2.0F, 0.0F, 0.6981F, 0.0F));
		PartDefinition leg_left_4_upper = leg_left_4.addOrReplaceChild("leg_left_4_upper", CubeListBuilder.create().texOffs(61, 25).mirror().addBox(-9.0F, -1.0F, -1.0F, 9.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(84, 25)
				.mirror().addBox(-6.5F, -3.0F, -0.5F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.4712F));
		PartDefinition leg_left_4_lower = leg_left_4_upper.addOrReplaceChild("leg_left_4_lower", CubeListBuilder.create().texOffs(89, 25).mirror().addBox(-16.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)).mirror(false).texOffs(0, 40)
				.mirror().addBox(-19.0F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)).mirror(false), PartPose.offsetAndRotation(-9.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.6057F));
		PartDefinition spike_8 = body.addOrReplaceChild("spike_8", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(2.5F, -3.0F, -2.0F, -0.5585F, 0.0F, 0.2094F));
		PartDefinition spike_8_tip = spike_8.addOrReplaceChild("spike_8_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));
		PartDefinition spike_9 = body.addOrReplaceChild("spike_9", CubeListBuilder.create().texOffs(37, 40).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(-2.5F, -3.0F, -2.0F, -0.5585F, 0.0F, -0.2094F));
		PartDefinition spike_9_tip = spike_9.addOrReplaceChild("spike_9_tip", CubeListBuilder.create().texOffs(46, 40).addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)),
				PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, -0.3491F, 0.0F, 0.0F));
		return LayerDefinition.create(meshdefinition, 128, 64);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int rgb) {
		root.render(poseStack, vertexConsumer, packedLight, packedOverlay, rgb);
	}

	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
	}
}