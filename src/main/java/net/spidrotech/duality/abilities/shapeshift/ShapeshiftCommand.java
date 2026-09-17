package net.spidrotech.duality.abilities.shapeshift;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.function.BiConsumer;

/**
 * Testing/admin hooks until a real unlock source exists:
 *
 *   /shapeshift bat              shift into (or out of) a form - same path as the forms radial
 *   /shapeshift revert           back to normal
 *   /shapeshift unlock bat       op: unlock a form for yourself
 *   /shapeshift lock bat         op: lock it again (ends the form if you're in it)
 */
@EventBusSubscriber(modid = "duality")
public final class ShapeshiftCommand {
	private ShapeshiftCommand() {
	}

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("shapeshift") //
				.then(Commands.literal("revert").executes(ctx -> run(ctx, player -> Shapeshift.revert(player)))) //
				.then(Commands.literal("unlock").requires(source -> source.hasPermission(2)).then(formArgument().executes(ctx -> editUnlock(ctx, Shapeshift::unlock, "Unlocked")))) //
				.then(Commands.literal("lock").requires(source -> source.hasPermission(2)).then(formArgument().executes(ctx -> editUnlock(ctx, Shapeshift::lock, "Locked")))) //
				.then(formArgument().executes(ctx -> run(ctx, player -> Shapeshift.requestShift(player, StringArgumentType.getString(ctx, "form"))))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> formArgument() {
		return Commands.argument("form", StringArgumentType.greedyString()) //
				.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Shapeshift.formIds().stream().map(ResourceLocation::getPath), builder));
	}

	private static int run(CommandContext<CommandSourceStack> ctx, java.util.function.Consumer<ServerPlayer> action) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
			return 0;
		action.accept(player);
		return 1;
	}

	private static int editUnlock(CommandContext<CommandSourceStack> ctx, BiConsumer<ServerPlayer, ResourceLocation> edit, String verb) {
		String raw = StringArgumentType.getString(ctx, "form");
		ResourceLocation id = Shapeshift.parseFormId(raw).filter(parsed -> Shapeshift.form(parsed).isPresent()).orElse(null);
		if (id == null) {
			ctx.getSource().sendFailure(Component.literal("Unknown form: " + raw));
			return 0;
		}
		return run(ctx, player -> {
			edit.accept(player, id);
			ctx.getSource().sendSuccess(() -> Component.literal(verb + " form " + id), false);
		});
	}
}
