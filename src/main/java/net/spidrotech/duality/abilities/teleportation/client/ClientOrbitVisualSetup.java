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
 * Runs on the MOD bus (FMLClientSetupEvent is a mod-bus lifecycle event, not a GAME-bus runtime
 * event) - see your own "@EventBusSubscriber bus mismatch" note, this is the MOD-bus case.
 */
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
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