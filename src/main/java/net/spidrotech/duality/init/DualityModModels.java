/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.spidrotech.duality.init;

import net.spidrotech.duality.client.model.Modelvisualorbbelt;
import net.spidrotech.duality.client.model.Modelfireball;
import net.spidrotech.duality.client.model.Modelempty;
import net.spidrotech.duality.client.model.Modelacid_spit;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

@EventBusSubscriber(Dist.CLIENT)
public class DualityModModels {
	@SubscribeEvent
	public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(Modelacid_spit.LAYER_LOCATION, Modelacid_spit::createBodyLayer);
		event.registerLayerDefinition(Modelvisualorbbelt.LAYER_LOCATION, Modelvisualorbbelt::createBodyLayer);
		event.registerLayerDefinition(Modelfireball.LAYER_LOCATION, Modelfireball::createBodyLayer);
		event.registerLayerDefinition(Modelempty.LAYER_LOCATION, Modelempty::createBodyLayer);
	}
}