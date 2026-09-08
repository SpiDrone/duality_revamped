/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.spidrotech.duality.init;

import net.spidrotech.duality.client.renderer.StagnantVisualRenderer;
import net.spidrotech.duality.client.renderer.LightningVisualRenderer;
import net.spidrotech.duality.client.renderer.AbilityProjectileRenderer;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

@EventBusSubscriber(Dist.CLIENT)
public class DualityModEntityRenderers {
	@SubscribeEvent
	public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(DualityModEntities.ABILITY_PROJECTILE.get(), AbilityProjectileRenderer::new);
		event.registerEntityRenderer(DualityModEntities.LIGHTNING_VISUAL.get(), LightningVisualRenderer::new);
		event.registerEntityRenderer(DualityModEntities.STAGNANT_VISUAL.get(), StagnantVisualRenderer::new);
	}
}