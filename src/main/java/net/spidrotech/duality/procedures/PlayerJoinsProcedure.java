package net.spidrotech.duality.procedures;

import net.spidrotech.duality.DualityMod;
import net.spidrotech.duality.DualityDatabaseManager;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.Event;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

@EventBusSubscriber
public class PlayerJoinsProcedure {
	@SubscribeEvent
	public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		execute(event, event.getEntity());
	}

	public static void execute(Entity entity) {
		execute(null, entity);
	}

	private static void execute(@Nullable Event event, Entity entity) {
		if (entity == null)
			return;
		DualityMod.LOGGER.info(entity + "PlayerJoinEvent.Run");
		if (DualityDatabaseManager.shouldStartCreateCharacter((net.minecraft.world.entity.player.Player) entity)) {
			DualityMod.LOGGER.info(entity + "PlayerJoinEvent.Success");
			DualityDatabaseManager.startCreateCharacter((net.minecraft.world.entity.player.Player) entity);
		}
	}
}