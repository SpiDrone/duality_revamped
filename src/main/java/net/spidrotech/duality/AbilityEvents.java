package net.spidrotech.duality;

import org.slf4j.Logger;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;

import com.mojang.logging.LogUtils;

/**
 * Wires AbilityManager into the game loop for ANY LivingEntity - players, mobs, custom NPCs.
 *
 * Players are ticked via the well-established PlayerTickEvent.Post; non-player LivingEntity
 * casters go through EntityTickEvent.Post instead (and players are explicitly excluded there,
 * so they don't get double-ticked by both handlers in the same game tick).
 *
 * This is what actually progresses a charging ability tick by tick - without it wired up
 * correctly, tryActivate() starts a charge but nothing ever advances it: no particles, no
 * completion.
 */
@EventBusSubscriber(modid = "duality")
public final class AbilityEvents {
	// TEMP DEBUG - unconditional heartbeat, completely independent of the ability system. If
	// this line never shows up in your log, the problem is 100% event registration, not
	// anything in AbilityManager/OrbAbility. Remove once confirmed working.
	private static final Logger LOGGER = LogUtils.getLogger();

	private AbilityEvents() {
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity().level().isClientSide())
			return;
		if (event.getEntity().level().getGameTime() % 100 == 0) {
			LOGGER.info("[duality] AbilityEvents heartbeat - player tick firing for {}", event.getEntity().getName().getString());
		}
		AbilityManager.get().tick(event.getEntity());
	}

	@SubscribeEvent
	public static void onEntityTick(EntityTickEvent.Post event) {
		if (event.getEntity().level().isClientSide())
			return;
		// Players are already handled by onPlayerTick above - skip them here.
		if (event.getEntity() instanceof LivingEntity living && !(living instanceof Player)) {
			AbilityManager.get().tick(living);
		}
	}

	@SubscribeEvent
	public static void onDamage(LivingIncomingDamageEvent event) {
		if (event.getEntity().level().isClientSide())
			return;
		AbilityManager.get().onDamaged(event.getEntity());
	}

	@SubscribeEvent
	public static void onDeath(LivingDeathEvent event) {
		AbilityManager.get().clearEntity(event.getEntity());
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		AbilityManager.get().clearEntity(event.getEntity());
	}
}