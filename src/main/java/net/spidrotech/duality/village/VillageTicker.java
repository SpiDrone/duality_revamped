package net.spidrotech.duality.village;

import net.spidrotech.duality.DualityMod;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Wires the village simulation into the server's life cycle.
 *
 * <p>There is deliberately no per-tick work here. Villages advance on the day, not the tick: once
 * per in-game day the whole world's worth of settlements gets one pass, and between passes this
 * costs a modulo every five seconds. That's what makes it affordable to keep simulating places
 * nobody is standing in.
 */
@EventBusSubscriber(modid = "duality")
public final class VillageTicker {
	/** How often to check whether the day has rolled over. Five seconds is plenty. */
	private static final int CHECK_INTERVAL_TICKS = 100;
	/** Days simulated in one rollover check, so a /time set 30 days forward doesn't stall the
	 *  server thread. Anything past this is picked up on the following checks. */
	private static final int MAX_DAYS_PER_CHECK = 3;

	private static int tickCounter = 0;

	private VillageTicker() {
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		Villages.attach(event.getServer());
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		Villages.detach();
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (!Villages.isReady())
			return;
		if (++tickCounter < CHECK_INTERVAL_TICKS)
			return;
		tickCounter = 0;
		long today = Villages.currentDay();
		WorldDualityState world = Villages.store().world();
		int simulated = 0;
		while (world.lastSimulatedDay() < today && simulated < MAX_DAYS_PER_CHECK) {
			// simulateDay reads the current day itself, so bump the clock a day at a time rather
			// than jumping - a three-week gap should play out as three weeks of history.
			long day = world.lastSimulatedDay() + 1;
			VillageSimulator.DayReport report = VillageSimulator.simulateDay(Villages.store(), Villages.bridge(), day, Villages.random());
			world.setLastSimulatedDay(day);
			world.addSimulatedDays(1);
			simulated++;
			for (VillageEventResult result : report.events()) {
				DualityMod.LOGGER.info("[duality/village] day {} {}", day, result.summary());
			}
		}
		if (simulated > 0)
			Villages.store().saveWorld();
		// Records that should have an entity and don't get one whenever their village is loaded.
		Villages.embodySweep();
		// Being a little ahead of the world clock is normal - /village simulate deliberately runs
		// the simulation forward, and it just means there's nothing to do until the days catch up.
		// Only a gap too big to be that is a real rollback (/time set, a restored backup).
		if (world.lastSimulatedDay() > today + VillageSimulator.MAX_CATCHUP_DAYS) {
			world.setLastSimulatedDay(today);
			Villages.store().saveWorld();
		}
	}
}
