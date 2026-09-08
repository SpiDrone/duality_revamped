// Made with Blockbench 4.9.4
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports

public class Modelfireball<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in
	// the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
			new ResourceLocation("modid", "fireball"), "main");
	private final ModelPart head;
	private final ModelPart MoltenCube1;
	private final ModelPart MoltenCube2;
	private final ModelPart spinhorizontal;
	private final ModelPart spinvertical;

	public Modelfireball(ModelPart root) {
		this.head = root.getChild("head");
		this.MoltenCube1 = root.getChild("MoltenCube1");
		this.MoltenCube2 = root.getChild("MoltenCube2");
		this.spinhorizontal = root.getChild("spinhorizontal");
		this.spinvertical = root.getChild("spinvertical");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition head = partdefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(
				-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition MoltenCube1 = partdefinition
				.addOrReplaceChild("MoltenCube1",
						CubeListBuilder.create().texOffs(20, 30).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F,
								new CubeDeformation(0.0F)),
						PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, 1.5708F, 0.0F, 0.0F));

		PartDefinition MoltenCube2 = partdefinition.addOrReplaceChild("MoltenCube2", CubeListBuilder.create(),
				PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition cube_r1 = MoltenCube2.addOrReplaceChild("cube_r1",
				CubeListBuilder.create().texOffs(20, 30).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F,
						new CubeDeformation(0.01F)),
				PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.7854F));

		PartDefinition spinhorizontal = partdefinition.addOrReplaceChild("spinhorizontal", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-6.0F, -1.0F, -6.0F, 12.0F, 2.0F, 12.0F, new CubeDeformation(0.0F)),
				PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition spinvertical = partdefinition.addOrReplaceChild("spinvertical", CubeListBuilder.create(),
				PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition cube_r2 = spinvertical
				.addOrReplaceChild("cube_r2",
						CubeListBuilder.create().texOffs(0, 14).addBox(-1.0F, -6.0F, -6.0F, 2.0F, 12.0F, 12.0F,
								new CubeDeformation(0.0F)),
						PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
			float red, float green, float blue, float alpha) {
		head.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		MoltenCube1.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		MoltenCube2.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		spinhorizontal.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		spinvertical.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}

	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
			float headPitch) {
		this.MoltenCube2.yRot = ageInTicks;
		this.MoltenCube1.xRot = ageInTicks;
		this.spinvertical.zRot = ageInTicks;
		this.spinhorizontal.xRot = ageInTicks;
	}
}