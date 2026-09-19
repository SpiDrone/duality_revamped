package net.spidrotech.duality.abilities.demon;

import net.spidrotech.duality.AbilityManager;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

import com.mojang.brigadier.arguments.FloatArgumentType;

/**
 * Testing hooks for the generic demon powers, same spirit as /fireball - no cast conditions on any
 * of these yet (see DualityAbilities#demonGeneric), so anyone can try them.
 *
 *   /demon blink | vanish | levitate | flight | screech    cast/toggle
 *   /demon wallclimber                                     toggle wall climbing
 *   /demon antigravity <0-100>                             set percent and turn it on
 *   /demon antigravity off                                 turn it off
 */
@EventBusSubscriber(modid = "duality")
public final class DemonTestCommand {
	private DemonTestCommand() {
	}

	@SubscribeEvent
	public static void registerCommand(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("demon") //
				.then(Commands.literal("blink").executes(ctx -> cast(ctx.getSource(), BlinkAbility.ID))) //
				.then(Commands.literal("vanish").executes(ctx -> cast(ctx.getSource(), VanishAbility.ID))) //
				.then(Commands.literal("levitate").executes(ctx -> cast(ctx.getSource(), LevitateAbility.ID))) //
				.then(Commands.literal("flight").executes(ctx -> cast(ctx.getSource(), FlightAbility.ID))) //
				.then(Commands.literal("screech").executes(ctx -> cast(ctx.getSource(), ScreechAbility.ID))) //
				.then(Commands.literal("wallclimber").executes(ctx -> cast(ctx.getSource(), WallClimberAbility.ID))) //
				.then(Commands.literal("antigravity") //
						.then(Commands.literal("off").executes(ctx -> antiGravityOff(ctx.getSource()))) //
						.then(Commands.argument("percent", FloatArgumentType.floatArg(0, 100))
								.executes(ctx -> antiGravityOn(ctx.getSource(), FloatArgumentType.getFloat(ctx, "percent"))))));
	}

	private static int cast(CommandSourceStack source, ResourceLocation id) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only a player can use this."));
			return 0;
		}
		AbilityManager.ActivationResult result = AbilityManager.get().tryActivate(player, id);
		if (!result.succeeded()) {
			source.sendFailure(result.message() != null ? result.message() : Component.literal("Failed."));
			return 0;
		}
		return 1;
	}

	private static int antiGravityOn(CommandSourceStack source, float percent) {
		if (!(source.getEntity() instanceof ServerPlayer player))
			return 0;
		AntiGravityAbility.setReductionPercent(player, percent);
		if (!AbilityManager.get().isRunning(player, AntiGravityAbility.ID))
			AbilityManager.get().tryActivate(player, AntiGravityAbility.ID);
		source.sendSuccess(() -> Component.literal("Anti-gravity set to " + percent + "%"), false);
		return 1;
	}

	private static int antiGravityOff(CommandSourceStack source) {
		if (!(source.getEntity() instanceof ServerPlayer player))
			return 0;
		if (AbilityManager.get().isRunning(player, AntiGravityAbility.ID))
			AbilityManager.get().tryActivate(player, AntiGravityAbility.ID);
		return 1;
	}
}
