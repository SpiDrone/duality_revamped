package net.spidrotech.duality;

import org.slf4j.Logger;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

import com.mojang.logging.LogUtils;

/**
 * Test-only: a /fireball command to cast whatever's registered in DualityProjectiles. No chat
 * messages, matching /orb - failures only go to the server log.
 *
 * Usage:
 *   /fireball          -> casts duality:fireball
 *   /fireball greater  -> casts duality:greater_fireball
 *   /fireball lesser   -> casts duality:lesser_fireball
 *   /fireball atomic   -> casts duality:atomic_fireball
 */
@EventBusSubscriber(modid = "duality")
public final class FireballTestCommand {
	private static final Logger LOGGER = LogUtils.getLogger();

	private FireballTestCommand() {
	}

	@SubscribeEvent
	public static void registerCommand(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("fireball").executes(context -> cast(context.getSource(), "fireball"))//
				.then(Commands.literal("greater").executes(context -> cast(context.getSource(), "greater_fireball")))
				//
				.then(Commands.literal("lesser").executes(context -> cast(context.getSource(), "lesser_fireball")))//
				.then(Commands.literal("inferno").executes(context -> cast(context.getSource(), "inferno"))));
	}

	private static int cast(CommandSourceStack source, String projectileId) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			LOGGER.warn("/fireball was run by a non-player source, ignoring");
			return 0;
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("duality", projectileId);
		AbilityManager.ActivationResult result = AbilityManager.get().tryActivate(player, id);
		if (!result.succeeded()) {
			LOGGER.info("/fireball failed for {}: {}", player.getName().getString(), result.message());
			return 0;
		}
		return 1;
	}
}