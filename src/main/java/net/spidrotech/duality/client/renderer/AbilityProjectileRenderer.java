package net.spidrotech.duality.client.renderer;

import org.slf4j.Logger;

import net.spidrotech.duality.entity.AbilityProjectileEntity;
import net.spidrotech.duality.ProjectileVisualRegistry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.model.EntityModel;

import java.util.Set;
import java.util.HashSet;
import java.util.Collections;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.PoseStack;

public class AbilityProjectileRenderer extends MobRenderer<AbilityProjectileEntity, EntityModel<AbilityProjectileEntity>> {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Set<ResourceLocation> WARNED_MISSING_SHAPE = Collections.synchronizedSet(new HashSet<>());
	private static final Set<ResourceLocation> WARNED_MISSING_TEXTURE = Collections.synchronizedSet(new HashSet<>());

	public AbilityProjectileRenderer(EntityRendererProvider.Context context) {
		// bakeAllAndGetDefault does the actual baking AND returns a model, all within this one
		// expression - see the comment on that method for why it can't be split into two lines.
		super(context, ProjectileVisualRegistry.bakeAllAndGetDefault(context), 0f);
	}

	@Override
	public void render(AbilityProjectileEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		ResourceLocation shapeKey = entity.definition().modelKey();
		EntityModel<AbilityProjectileEntity> model = ProjectileVisualRegistry.getModel(shapeKey);
		if (model != null) {
			this.model = model;
		} else if (WARNED_MISSING_SHAPE.add(shapeKey)) {
			LOGGER.warn("[duality] No ProjectileVisualRegistry shape registered for key {} - falling back to whatever model last rendered.", shapeKey);
		}
		super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
	}

	@Override
	public ResourceLocation getTextureLocation(AbilityProjectileEntity entity) {
		ResourceLocation override = entity.definition().texture();
		if (override != null)
			return override;
		ResourceLocation fallback = ProjectileVisualRegistry.getDefaultTexture(entity.definition().modelKey());
		if (fallback != null)
			return fallback;
		if (WARNED_MISSING_TEXTURE.add(entity.definition().id())) {
			LOGGER.warn("[duality] Projectile {} has no texture set and its shape has no default texture - rendering missing-texture.", entity.definition().id());
		}
		return MissingTextureAtlasSprite.getLocation();
	}

	@Override
	protected boolean isShaking(AbilityProjectileEntity entity) {
		return entity.definition().isShaking();
	}

	@Override
	protected void scale(AbilityProjectileEntity entity, PoseStack poseStack, float partialTickTime) {
		float scale = entity.definition().visualScale();
		poseStack.scale(scale, scale, scale);
	}
}