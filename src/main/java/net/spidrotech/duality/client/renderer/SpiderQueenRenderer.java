package net.spidrotech.duality.client.renderer;

import net.spidrotech.duality.entity.SpiderQueenEntity;
import net.spidrotech.duality.client.model.Modeldemonic_spider;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class SpiderQueenRenderer extends MobRenderer<SpiderQueenEntity, Modeldemonic_spider<SpiderQueenEntity>> {
	public SpiderQueenRenderer(EntityRendererProvider.Context context) {
		super(context, new Modeldemonic_spider<SpiderQueenEntity>(context.bakeLayer(Modeldemonic_spider.LAYER_LOCATION)), 0.5f);
	}

	@Override
	public ResourceLocation getTextureLocation(SpiderQueenEntity entity) {
		return ResourceLocation.parse("duality:textures/entities/demonic_spider.png");
	}
}
