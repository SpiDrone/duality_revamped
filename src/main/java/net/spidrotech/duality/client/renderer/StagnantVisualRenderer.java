package net.spidrotech.duality.client.renderer;

import net.spidrotech.duality.entity.StagnantVisualEntity;
import net.spidrotech.duality.client.model.Modelvisualorbbelt;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.EntityModel;

import java.util.Map;
import java.util.LinkedHashMap;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Shared renderer for every StagnantVisualEntity - one MCreator entity class, many possible
 * Blockbench models, picked per-instance from StagnantVisualEntity#DATA_model (a plain int id
 * you assign yourself - no separate registry class needed for this one, per your own plan of
 * declaring models directly here as you add them).
 *
 * ADDING A NEW MODEL: bake it in the constructor and add one models.put(id, ...) line below,
 * keeping that id in sync with whatever value you write into DATA_model when spawning that
 * variant.
 */
public class StagnantVisualRenderer extends MobRenderer<StagnantVisualEntity, EntityModel<StagnantVisualEntity>> {
	private final Map<Integer, EntityModel<StagnantVisualEntity>> models = new LinkedHashMap<>();
	private final ResourceLocation defaultTexture = ResourceLocation.parse("duality:textures/entities/visualorbbelt.png");

	public StagnantVisualRenderer(EntityRendererProvider.Context context) {
		super(context, new Modelvisualorbbelt<>(context.bakeLayer(Modelvisualorbbelt.LAYER_LOCATION)), 0f);
		// this.model already holds the exact instance just baked above for super() - reuse it
		// directly instead of baking the same layer a second time.
		models.put(1, this.model);
		// Add more models here as you create them, e.g.:
		// models.put(2, new ModelSecondVisual<>(context.bakeLayer(ModelSecondVisual.LAYER_LOCATION)));
	}

	@Override
	public void render(StagnantVisualEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		int modelId = entity.getEntityData().get(StagnantVisualEntity.DATA_model);
		EntityModel<StagnantVisualEntity> selected = models.get(modelId);
		// Unknown id falls back to whichever model was registered first, rather than crashing -
		// same "don't hard-fail on a bad key" spirit as the projectile visual registry.
		this.model = selected != null ? selected : models.values().iterator().next();
		super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
	}

	@Override
	public ResourceLocation getTextureLocation(StagnantVisualEntity entity) {
		String texturePath = entity.getEntityData().get(StagnantVisualEntity.DATA_texture);
		if (texturePath == null || texturePath.isEmpty()) {
			return defaultTexture;
		}
		try {
			return texturePath.contains(":") ? ResourceLocation.parse(texturePath) : ResourceLocation.fromNamespaceAndPath("duality", "textures/entities/" + texturePath + ".png");
		} catch (Exception e) {
			return defaultTexture;
		}
	}
}