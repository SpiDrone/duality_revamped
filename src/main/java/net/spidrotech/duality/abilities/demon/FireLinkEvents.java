package net.spidrotech.duality.abilities.demon;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;

/** Periodically drops any fire waypoint whose fire is gone - see FireLinkData#pruneMissing. A
 *  sweep rather than reacting to a specific removal event, since fire can go out from burning
 *  out, rain, water, an explosion, or being mined, and this catches all of them uniformly. */
@EventBusSubscriber(modid = "duality")
public final class FireLinkEvents {
	private static final int SWEEP_INTERVAL_TICKS = 100; // 5s

	private FireLinkEvents() {
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		MinecraftServer server = event.getServer();
		if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0)
			return;
		FireLinkData.get(server.overworld()).pruneMissing((dimension, pos) -> {
			ServerLevel level = server.getLevel(dimension);
			if (level == null || !level.isLoaded(pos))
				return true; // unloaded, not confirmed gone - don't prune on a guess
			return level.getBlockState(pos).is(Blocks.FIRE) || level.getBlockState(pos).is(Blocks.SOUL_FIRE);
		});
	}
}
