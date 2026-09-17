package net.spidrotech.duality.abilities.shapeshift;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;

/**
 * Enforces each form's restriction flags, and ends forms whose requirement stops holding.
 *
 * The restrictions run on both sides: the form is synced to the client (ShapeshiftNetwork), so
 * cancelling client-side too avoids ghost block placements the server would undo a moment later.
 */
@EventBusSubscriber(modid = "duality")
public final class ShapeshiftEvents {
	private static final int REQUIREMENT_CHECK_INTERVAL_TICKS = 20;

	private ShapeshiftEvents() {
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity() instanceof ServerPlayer player && player.level().getGameTime() % REQUIREMENT_CHECK_INTERVAL_TICKS == 0)
			Shapeshift.revertIfNotAllowed(player);
	}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (Shapeshift.current(event.getEntity()).map(ShapeshiftForm::blocksItemUse).orElse(false))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
		if (Shapeshift.current(event.getEntity()).map(ShapeshiftForm::blocksItemUse).orElse(false))
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
		if (event.getEntity() instanceof LivingEntity living && Shapeshift.current(living).map(ShapeshiftForm::blocksBlockPlacing).orElse(false))
			event.setCanceled(true);
	}
}
