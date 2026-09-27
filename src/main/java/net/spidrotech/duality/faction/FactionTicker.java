package net.spidrotech.duality.faction;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Wires the faction system into the server's life cycle - same pattern as
 *  net.spidrotech.duality.village.VillageTicker, minus that class's periodic day simulation:
 *  factions don't advance on their own over time the way villages do. Membership eligibility isn't
 *  swept periodically either - see Factions#onRaceChanged, called directly from the race-change and
 *  faction-kind-change call sites rather than a ticker scanning everything on a timer. */
@EventBusSubscriber(modid = "duality")
public final class FactionTicker {
	private FactionTicker() {
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		Factions.attach(event.getServer());
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		Factions.detach();
	}
}
