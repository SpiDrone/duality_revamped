package net.spidrotech.duality.abilities.teleportation.client;

import net.spidrotech.duality.init.DualityModEntities;
import net.spidrotech.duality.entity.StagnantVisualEntity;

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

/**
 * Registers the client-only "ghost" entity used by ClientOrbitVisualManager to show which
 * entities are currently selected as Orb passengers - visible only to the person orbing, never
 * added to the level, never synced to the server or other players (see ClientOrbitVisualManager
 * class doc). Model/texture come from StagnantVisualEntity's DATA_model/DATA_texture fields;
 * model id 1 is the orb-belt model already registered in StagnantVisualRenderer.
 *
 * FMLClientSetupEvent is a mod-bus lifecycle event. The explicit bus = Bus.MOD this used to carry
 * is deprecated for removal in NeoForge 21.1 because the bus is now worked out from the event type
 * itself - MCreator's own DualityModEntities subscribes to mod-bus events with no bus argument.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class ClientOrbitVisualSetup {
	private ClientOrbitVisualSetup() {
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> ClientOrbitVisualManager.setSpawner(level -> {
			StagnantVisualEntity ghost = new StagnantVisualEntity(DualityModEntities.STAGNANT_VISUAL.get(), level);
			ghost.getEntityData().set(StagnantVisualEntity.DATA_model, 1); // orb-belt model
			return ghost;
		}));
	}
}