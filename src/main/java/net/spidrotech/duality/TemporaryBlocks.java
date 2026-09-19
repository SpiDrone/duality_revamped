package net.spidrotech.duality;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Blocks an ability puts into the world for a few seconds and then takes back - web spit's
 * cobwebs, and anything later that wants the same "it's there, then it isn't".
 *
 * <p>Two rules keep this from ever eating a player's build:
 * <ul>
 *   <li>It only places into air. Grass, snow and water all report as replaceable, and replacing
 *       them would mean removing something that was really there.</li>
 *   <li>On expiry it only removes the block if it is still the block it placed. If someone mined
 *       the web and put a door there, the door stays.</li>
 * </ul>
 *
 * <p>Pending removals live in memory, so everything is cleared on server stop rather than left
 * waiting for a tick that will never come. A hard crash still leaves whatever was out at the time.
 */
@EventBusSubscriber(modid = "duality")
public final class TemporaryBlocks {
	private record Pending(ResourceKey<Level> dimension, BlockPos pos, BlockState placed, int expiresAtTick) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private TemporaryBlocks() {
	}

	/**
	 * Places {@code state} at {@code pos} for {@code ticks} ticks, if and only if that spot is air.
	 *
	 * @return whether it was placed
	 */
	public static boolean place(ServerLevel level, BlockPos pos, BlockState state, int ticks) {
		if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir())
			return false;
		level.setBlockAndUpdate(pos, state);
		PENDING.add(new Pending(level.dimension(), pos.immutable(), state, level.getServer().getTickCount() + ticks));
		return true;
	}

	@SubscribeEvent
	public static void onServerTick(ServerTickEvent.Post event) {
		if (PENDING.isEmpty())
			return;
		MinecraftServer server = event.getServer();
		int now = server.getTickCount();
		Iterator<Pending> it = PENDING.iterator();
		while (it.hasNext()) {
			Pending pending = it.next();
			if (now < pending.expiresAtTick())
				continue;
			ServerLevel level = server.getLevel(pending.dimension());
			// Leave it queued rather than force-loading a chunk just to delete a cobweb; it goes the
			// moment the chunk is back, or at shutdown.
			if (level != null && !level.isLoaded(pending.pos()))
				continue;
			removeIfUnchanged(level, pending);
			it.remove();
		}
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		for (Pending pending : PENDING)
			removeIfUnchanged(event.getServer().getLevel(pending.dimension()), pending);
		PENDING.clear();
	}

	private static void removeIfUnchanged(ServerLevel level, Pending pending) {
		if (level != null && level.getBlockState(pending.pos()).is(pending.placed().getBlock()))
			level.removeBlock(pending.pos(), false);
	}
}
