package net.spidrotech.duality.skin;

import net.spidrotech.duality.DualityDatabaseManager;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.ChatFormatting;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;

import java.util.List;
import java.util.ArrayList;

/**
 * Test/admin command for the skin system. Bypasses ownership checks entirely - it talks to
 * SkinManager directly rather than going through SkinNetwork's equip handler, so an op can put
 * a locked part on someone to check how it looks without unlocking it for real. That's exactly
 * why this is permission level 2 and not something the builder GUI ever routes through.
 *
 *   /dualityadmin skineditor add &lt;part&gt; [player]     equip a part
 *   /dualityadmin skineditor remove &lt;part&gt; [player]  unequip it
 *   /dualityadmin skineditor base &lt;part&gt; [player]    set the bottom layer (a skins_* part)
 *   /dualityadmin skineditor clear [player]          strip everything back to the vanilla skin
 *   /dualityadmin skineditor list [player]           what they're currently wearing
 *   /dualityadmin skineditor parts [category]        what exists in the catalog
 *   /dualityadmin skineditor active show [player]     which character is active, and the alive list
 *   /dualityadmin skineditor active set &lt;charId&gt; [player]  switch active character (reloads skin)
 *   /dualityadmin skineditor vampire &lt;on|off&gt; [player]  fire two real temp augmentations
 *
 * vampire on/off is the end-to-end check that matters most: it exercises SkinTempModify across
 * TWO targets sharing one key, the effect registry, the per-target mask that lets a color
 * change follow a specific pupil's own shape, the animating branch of the composite cache, and
 * the client sync, all at once. If parts work but vampire doesn't, the problem is in effects or
 * caching, not the catalog.
 *
 * WHAT A GOOD FIRST TEST LOOKS LIKE:
 *   /dualityadmin skineditor base skins_skin
 *   /dualityadmin skineditor add hair_&lt;whatever you shipped&gt;
 *   /dualityadmin skineditor add pupils_&lt;whatever you shipped, if you have one&gt;
 *   /dualityadmin skineditor vampire on
 * You should see the base body appear, then hair land on top of it, then a small branching
 * crack spread out from the eye over about a second and hold, and the pupil (if equipped) turn
 * red and stay drawn ON TOP of the crack rather than getting darkened by it.
 */
@EventBusSubscriber(modid = "duality")
public final class SkinAdminCommand {
	private SkinAdminCommand() {
	}

	/** Every part id in the catalog. Rebuilt per keystroke, which is fine at chat-typing rates
	 *  even with a few thousand parts - Brigadier filters the list client-side after this. */
	private static final SuggestionProvider<CommandSourceStack> PART_SUGGESTIONS = (context, builder) -> {
		List<String> ids = new ArrayList<>();
		for (SkinPart part : SkinPartCatalog.all()) {
			ids.add(part.id());
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> UNLOCK_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggest(SkinUnlocks.knownUnlockIds(), builder);

	private static final SuggestionProvider<CommandSourceStack> CATEGORY_SUGGESTIONS = (context, builder) -> SharedSuggestionProvider.suggest(SkinPartCatalog.byCategory().keySet(), builder);

	/** This player's own alive character ids. Uses the command SOURCE's player when possible so
	 *  it suggests the right person's characters even in the "[player]" overload; falls back to
	 *  no suggestions off-thread rather than guessing. */
	private static final SuggestionProvider<CommandSourceStack> CHARACTER_SUGGESTIONS = (context, builder) -> {
		try {
			ServerPlayer player = context.getSource().getPlayerOrException();
			return SharedSuggestionProvider.suggest(DualityDatabaseManager.getAliveCharacterIds(player), builder);
		} catch (Exception noPlayer) {
			return builder.buildFuture();
		}
	};

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(root());
	}

	private static LiteralArgumentBuilder<CommandSourceStack> root() {
		return Commands.literal("dualityadmin").requires(source -> source.hasPermission(2)).then(Commands.literal("skineditor")
				.then(Commands.literal("add").then(Commands.argument("part", StringArgumentType.word()).suggests(PART_SUGGESTIONS)
						.executes(ctx -> equip(ctx.getSource(), ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "part")))
						.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> equip(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "part"))))))
				.then(Commands.literal("remove").then(Commands.argument("part", StringArgumentType.word()).suggests(PART_SUGGESTIONS)
						.executes(ctx -> unequip(ctx.getSource(), ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "part")))
						.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> unequip(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "part"))))))
				.then(Commands.literal("base").then(Commands.argument("part", StringArgumentType.word()).suggests(PART_SUGGESTIONS)
						.executes(ctx -> setBase(ctx.getSource(), ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "part")))
						.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> setBase(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "part"))))))
				.then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> clear(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("list").executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> list(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(Commands.literal("parts").executes(ctx -> listCatalog(ctx.getSource(), null))
						.then(Commands.argument("category", StringArgumentType.word()).suggests(CATEGORY_SUGGESTIONS).executes(ctx -> listCatalog(ctx.getSource(), StringArgumentType.getString(ctx, "category")))))
				.then(Commands.literal("active")
						.then(Commands.literal("show").executes(ctx -> showActive(ctx.getSource(), ctx.getSource().getPlayerOrException()))
								.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> showActive(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
						.then(Commands.literal("set").then(Commands.argument("characterId", StringArgumentType.word()).suggests(CHARACTER_SUGGESTIONS)
								.executes(ctx -> setActive(ctx.getSource(), ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "characterId")))
								.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> setActive(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "characterId")))))))
				.then(Commands.literal("vampire")
						.then(Commands.literal("on").executes(ctx -> vampire(ctx.getSource(), ctx.getSource().getPlayerOrException(), true))
								.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> vampire(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), true))))
						.then(Commands.literal("off").executes(ctx -> vampire(ctx.getSource(), ctx.getSource().getPlayerOrException(), false))
								.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> vampire(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), false)))))
				.then(Commands.literal("unlock")
						.then(Commands.literal("grant").then(Commands.argument("unlockId", StringArgumentType.string()).suggests(UNLOCK_SUGGESTIONS)
								.executes(ctx -> unlock(ctx.getSource(), ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "unlockId"), true))
								.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> unlock(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "unlockId"), true)))))
						.then(Commands.literal("revoke").then(Commands.argument("unlockId", StringArgumentType.string()).suggests(UNLOCK_SUGGESTIONS)
								.executes(ctx -> unlock(ctx.getSource(), ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "unlockId"), false))
								.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> unlock(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "unlockId"), false)))))
						.then(Commands.literal("list").executes(ctx -> listUnlocks(ctx.getSource(), ctx.getSource().getPlayerOrException()))
								.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> listUnlocks(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))));
	}

	// ================================================================== subcommands
	private static int equip(CommandSourceStack source, ServerPlayer target, String partId) {
		SkinPart part = SkinPartCatalog.get(partId);
		if (part == null)
			return unknownPart(source, partId);
		// Tints default to -1 across the board, meaning "whatever the catalog calls default" -
		// see SkinLoadout.Equipped. Nothing here needs to know how many tintable subparts the
		// part has, which is why an empty list would also work.
		List<Integer> tints = new ArrayList<>();
		for (int i = 0; i < part.tintableCount(); i++) {
			tints.add(-1);
		}
		SkinManager manager = SkinManager.get();
		manager.setLoadout(target, manager.loadoutOf(target.getUUID()).with(new SkinLoadout.Equipped(partId, List.copyOf(tints))));
		source.sendSuccess(() -> Component.literal("Equipped " + partId + " (" + part.target().name() + ") on " + target.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int unequip(CommandSourceStack source, ServerPlayer target, String partId) {
		SkinManager manager = SkinManager.get();
		if (manager.loadoutOf(target.getUUID()).parts().stream().noneMatch(eq -> eq.partId().equals(partId))) {
			source.sendFailure(Component.literal(target.getGameProfile().getName() + " isn't wearing " + partId));
			return 0;
		}
		manager.setLoadout(target, manager.loadoutOf(target.getUUID()).without(partId));
		source.sendSuccess(() -> Component.literal("Removed " + partId + " from " + target.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW), true);
		return 1;
	}

	private static int setBase(CommandSourceStack source, ServerPlayer target, String partId) {
		if (!SkinPartCatalog.has(partId))
			return unknownPart(source, partId);
		SkinManager manager = SkinManager.get();
		manager.setLoadout(target, manager.loadoutOf(target.getUUID()).withBase(partId));
		source.sendSuccess(() -> Component.literal("Base skin set to " + partId + " for " + target.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	/** Wipes the loadout AND every augmentation - back to the player's real Mojang skin, which
	 *  is also the fastest way to confirm the system is genuinely getting out of the way when
	 *  it has nothing to do. */
	private static int showActive(CommandSourceStack source, ServerPlayer target) {
		String active = DualityDatabaseManager.getActiveCharacterId(target);
		List<String> alive = DualityDatabaseManager.getAliveCharacterIds(target);
		source.sendSuccess(() -> Component.literal(target.getGameProfile().getName() + " active character: " + (active.isEmpty() ? "(none)" : active)).withStyle(ChatFormatting.AQUA), false);
		for (String id : alive) {
			boolean isActive = id.equals(active);
			source.sendSuccess(() -> Component.literal((isActive ? "> " : "  ") + id).withStyle(isActive ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
		}
		return 1;
	}

	/** Switches the active character and re-syncs the skin to match. setActiveCharacter refuses
	 *  ids not in the player's own alive list, so this can't activate a dead or foreign
	 *  character; onActiveCharacterChanged then reloads that character's saved appearance and
	 *  unlocks and clears any temp augmentations from the previous one. */
	private static int setActive(CommandSourceStack source, ServerPlayer target, String characterId) {
		if (!DualityDatabaseManager.setActiveCharacter(target, characterId)) {
			source.sendFailure(Component.literal(target.getGameProfile().getName() + " has no living character '" + characterId + "' (try /dualityadmin skineditor active show)"));
			return 0;
		}
		SkinManager.get().onActiveCharacterChanged(target);
		source.sendSuccess(() -> Component.literal("Active character for " + target.getGameProfile().getName() + " set to " + characterId + " - skin reloaded").withStyle(ChatFormatting.GREEN), true);
		return 1;
	}

	private static int clear(CommandSourceStack source, ServerPlayer target) {
		SkinManager.get().setLoadout(target, SkinLoadout.EMPTY);
		SkinTempModify.removeKey(target);
		source.sendSuccess(() -> Component.literal("Cleared all skin parts and augmentations from " + target.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW), true);
		return 1;
	}

	private static int list(CommandSourceStack source, ServerPlayer target) {
		SkinLoadout loadout = SkinManager.get().loadoutOf(target.getUUID());
		source.sendSuccess(() -> Component.literal("--- " + target.getGameProfile().getName() + " ---").withStyle(ChatFormatting.AQUA), false);
		source.sendSuccess(() -> Component.literal("base: " + loadout.baseTexturePartId().orElse("(vanilla skin)")), false);
		if (loadout.parts().isEmpty()) {
			source.sendSuccess(() -> Component.literal("parts: none"), false);
		} else {
			for (SkinLoadout.Equipped equipped : loadout.sortedForRender()) {
				SkinPart part = SkinPartCatalog.get(equipped.partId());
				source.sendSuccess(() -> Component.literal("  " + equipped.partId() + "  [" + (part == null ? "?" : part.target().name()) + "]"), false);
			}
		}
		List<TempSkinModification> temps = SkinManager.get().tempModifications(target);
		if (temps.isEmpty()) {
			source.sendSuccess(() -> Component.literal("augmentations: none"), false);
		} else {
			for (TempSkinModification mod : temps) {
				source.sendSuccess(() -> Component.literal("  key=" + mod.key() + " target=" + mod.target().name() + " effect=" + mod.effect().map(Object::toString).orElse("none")).withStyle(ChatFormatting.LIGHT_PURPLE), false);
			}
		}
		source.sendSuccess(() -> Component.literal("appearance version: " + SkinManager.get().versionOf(target.getUUID()) + (SkinManager.get().isDisguised(target.getUUID()) ? " (disguised)" : "")).withStyle(ChatFormatting.DARK_GRAY), false);
		return 1;
	}

	/** Catalog dump. Capped, because a catalog of a few thousand parts would otherwise flood
	 *  chat and drop the earlier lines out of the buffer anyway - use the tab completion on
	 *  add/remove for actual browsing. */
	private static int listCatalog(CommandSourceStack source, String category) {
		int shown = 0;
		int total = 0;
		for (SkinPart part : SkinPartCatalog.all()) {
			if (category != null && !part.category().equalsIgnoreCase(category))
				continue;
			total++;
			if (shown++ < 40) {
				source.sendSuccess(() -> Component.literal(part.id() + "  [" + part.target().name() + (part.free() ? ", free" : ", locked: " + part.unlock().orElse("NO UNLOCK ID")) + "]").withStyle(part.free() ? ChatFormatting.WHITE : ChatFormatting.GOLD), false);
			}
		}
		int finalTotal = total;
		int finalShown = Math.min(shown, 40);
		source.sendSuccess(() -> Component.literal(finalShown + " of " + finalTotal + " part(s)" + (category == null ? " in catalog" : " in category '" + category + "'")).withStyle(ChatFormatting.AQUA), false);
		return 1;
	}

	/** The full augmentation round trip - see class doc. */
	/** Two modifications under one shared key, not one - see SkinPartTarget's PUPIL LAYERING
	 *  doc. The crack effect targets EYES so it composites and finishes before PUPIL draws over
	 *  it; the color change targets PUPIL so it recolors exactly that player's actual pupil
	 *  pixels (via SkinCompositor's per-target mask) rather than a generic box. removeKey still
	 *  clears both in one call, since a key groups modifications, not a 1:1 id. */
	private static int vampire(CommandSourceStack source, ServerPlayer target, boolean on) {
		if (on) {
			SkinTempModify.add(target).part(SkinPartTarget.EYES).effect(SkinEffects.VAMPIRIZE).key("vampire");
			SkinTempModify.add(target).part(SkinPartTarget.PUPIL).color(0xFFCC0000).key("vampire");
			source.sendSuccess(() -> Component.literal("Vampirised " + target.getGameProfile().getName()).withStyle(ChatFormatting.DARK_RED), true);
		} else {
			SkinTempModify.removeKey(target, "vampire");
			source.sendSuccess(() -> Component.literal("Cured " + target.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
		}
		return 1;
	}

	private static int unlock(CommandSourceStack source, ServerPlayer target, String unlockId, boolean grant) {
		boolean changed = grant ? SkinUnlocks.get().grant(target, unlockId) : SkinUnlocks.get().revoke(target, unlockId);
		if (!changed) {
			source.sendFailure(Component.literal(target.getGameProfile().getName() + (grant ? " already has " : " doesn't have ") + unlockId));
			return 0;
		}
		int affected = 0;
		for (SkinPart part : SkinPartCatalog.all()) {
			if (part.unlock().filter(unlockId::equals).isPresent())
				affected++;
		}
		int finalAffected = affected;
		source.sendSuccess(() -> Component.literal((grant ? "Granted " : "Revoked ") + unlockId + " " + (grant ? "to " : "from ") + target.getGameProfile().getName() + " (" + finalAffected + " part(s))")
				.withStyle(grant ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
		return 1;
	}

	/** Shows earned AND unearned unlocks, because "which ids exist" is the thing you actually
	 *  need when checking a rules file did what you meant - a bare list of what they have can't
	 *  tell you whether a rule matched nothing at all. */
	private static int listUnlocks(CommandSourceStack source, ServerPlayer target) {
		java.util.Set<String> owned = SkinUnlocks.get().grantsFor(target.getUUID());
		java.util.Set<String> known = SkinUnlocks.knownUnlockIds();
		source.sendSuccess(() -> Component.literal("--- unlocks for " + target.getGameProfile().getName() + " ---").withStyle(ChatFormatting.AQUA), false);
		if (known.isEmpty()) {
			source.sendSuccess(() -> Component.literal("No part in the catalog names an unlock id - everything is free.").withStyle(ChatFormatting.GRAY), false);
		}
		for (String id : known) {
			boolean has = owned.contains(id);
			source.sendSuccess(() -> Component.literal((has ? "[x] " : "[ ] ") + id).withStyle(has ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
		}
		for (String id : owned) {
			if (!known.contains(id)) {
				source.sendSuccess(() -> Component.literal("[x] " + id + " (no parts use this id)").withStyle(ChatFormatting.DARK_GRAY), false);
			}
		}
		return 1;
	}

	private static int unknownPart(CommandSourceStack source, String partId) {
		source.sendFailure(Component.literal("No such part: " + partId + " (try /dualityadmin skineditor parts)"));
		return 0;
	}
}
