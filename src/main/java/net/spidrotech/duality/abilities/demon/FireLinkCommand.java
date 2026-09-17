package net.spidrotech.duality.abilities.demon;

import net.spidrotech.duality.AbilityManager;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;

/**
 * Testing hooks for Flaming until the real "create a link" trigger is designed - guessed as
 * "look at a fire block and run /flame link <name>" purely so the underlying system (FireLinkData,
 * FlameAbility, the auto-prune) has something to exercise. Replace link()'s trigger with the real
 * one whenever that's decided; nothing else needs to change.
 *
 *   /flame link <name>      link the fire block you're looking at
 *   /flame unlink <name>
 *   /flame list
 *   /flame <name>           cast Flame to that waypoint
 */
@EventBusSubscriber(modid = "duality")
public final class FireLinkCommand {
	private static final double REACH = 6.0;
	private static final ResourceKey<Level> HEAVEN_KEY = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("duality", "upper_regions"));

	private FireLinkCommand() {
	}

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("flame") //
				.then(Commands.literal("link").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> link(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))) //
				.then(Commands.literal("unlink").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> unlink(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))) //
				.then(Commands.literal("list").executes(ctx -> list(ctx.getSource()))) //
				.then(Commands.argument("name", StringArgumentType.word()) //
						.suggests((ctx, builder) -> suggestNames(ctx.getSource(), builder)) //
						.executes(ctx -> cast(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));
	}

	private static int link(CommandSourceStack source, String name) {
		if (!(source.getEntity() instanceof ServerPlayer player))
			return 0;
		if (player.level().dimension() == HEAVEN_KEY) {
			source.sendFailure(Component.literal("Can't link a fire in the Upper Regions - Heaven is whitelighters-only."));
			return 0;
		}
		HitResult hit = player.pick(REACH, 1.0f, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			source.sendFailure(Component.literal("Look at a fire block within reach."));
			return 0;
		}
		var state = player.level().getBlockState(blockHit.getBlockPos());
		if (!state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE)) {
			source.sendFailure(Component.literal("That's not fire."));
			return 0;
		}
		FireLinkData.get((ServerLevel) player.level()).link(name, new FireWaypoint(player.level().dimension(), blockHit.getBlockPos()));
		source.sendSuccess(() -> Component.literal("Linked '" + name + "'."), false);
		return 1;
	}

	private static int unlink(CommandSourceStack source, String name) {
		if (!(source.getEntity() instanceof ServerPlayer player))
			return 0;
		boolean removed = FireLinkData.get((ServerLevel) player.level()).unlink(name);
		source.sendSuccess(() -> Component.literal(removed ? "Unlinked '" + name + "'." : "No such link."), false);
		return removed ? 1 : 0;
	}

	private static int list(CommandSourceStack source) {
		if (!(source.getEntity() instanceof ServerPlayer player))
			return 0;
		FireLinkData.get((ServerLevel) player.level()).all().forEach((name, waypoint) -> source
				.sendSuccess(() -> Component.literal(name + " -> " + waypoint.dimension().location() + " " + waypoint.pos().toShortString()), false));
		return 1;
	}

	private static int cast(CommandSourceStack source, String name) {
		if (!(source.getEntity() instanceof ServerPlayer player))
			return 0;
		AbilityManager.ActivationResult result = AbilityManager.get().tryActivate(player, FlameAbility.ID, ctx -> ctx.set(FlameAbility.WAYPOINT_KEY, name));
		if (!result.succeeded()) {
			source.sendFailure(result.message() != null ? result.message() : Component.literal("Failed."));
			return 0;
		}
		return 1;
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestNames(CommandSourceStack source,
			com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
		if (source.getEntity() instanceof Player player)
			return SharedSuggestionProvider.suggest(FireLinkData.get((ServerLevel) player.level()).all().keySet(), builder);
		return builder.buildFuture();
	}
}
