package net.spidrotech.duality.charactercreation;

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
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Character-creator admin tools, added under the same /dualityadmin root SkinAdminCommand uses
 * (brigadier merges the two registrations into one tree):
 *
 * <pre>
 *   /dualityadmin debug characterCreator [on|off] [player]   show every race, lineage and power
 *                                                            as unlocked (toggles with no on/off)
 *   /dualityadmin creator unlock race|subrace|ability|cosmetic &lt;id&gt; [player]
 *   /dualityadmin creator revoke race|subrace|ability|cosmetic &lt;id&gt; [player]
 *   /dualityadmin creator unlocks [player]                    what's unlocked on their profile
 * </pre>
 *
 * The unlock lists are the PLAYER's (what the creator may offer), never a character's own powers -
 * see CharacterCreation's unlock section.
 */
@EventBusSubscriber(modid = "duality")
public final class CharacterCreatorAdminCommand {
	private CharacterCreatorAdminCommand() {
	}

	private enum Kind {
		RACE(CharacterCreation.UNLOCKED_RACES_KEY), SUBRACE(CharacterCreation.UNLOCKED_SUBRACES_KEY), ABILITY(CharacterCreation.UNLOCKED_ABILITIES_KEY),
		COSMETIC(CharacterCreation.UNLOCKED_COSMETICS_KEY);

		final String profileKey;

		Kind(String profileKey) {
			this.profileKey = profileKey;
		}

		boolean exists(String id) {
			return switch (this) {
				case RACE -> RaceCatalog.has(id);
				case SUBRACE -> allSubraceIds().contains(id.toLowerCase());
				case ABILITY -> AbilityCatalog.get(id) != null;
				case COSMETIC -> net.spidrotech.duality.skin.SkinUnlocks.knownUnlockIds().contains(id);
			};
		}

		List<String> ids() {
			List<String> ids = new ArrayList<>();
			switch (this) {
				case RACE -> RaceCatalog.all().forEach(race -> ids.add(race.id()));
				case SUBRACE -> ids.addAll(allSubraceIds());
				case ABILITY -> AbilityCatalog.all().forEach(ability -> ids.add(ability.id()));
				case COSMETIC -> ids.addAll(net.spidrotech.duality.skin.SkinUnlocks.knownUnlockIds());
			}
			return ids;
		}
	}

	/** Cosmetic unlock ids look like "duality:kicks" - the colon needs quotes in a command. */
	private static String quoteIfNeeded(String id) {
		for (char c : id.toCharArray()) {
			if (!com.mojang.brigadier.StringReader.isAllowedInUnquotedString(c))
				return "\"" + id + "\"";
		}
		return id;
	}

	private static List<String> allSubraceIds() {
		List<String> ids = new ArrayList<>();
		for (RaceDefinition race : RaceCatalog.all()) {
			for (SubraceDefinition subrace : race.subraces()) {
				ids.add(subrace.id());
			}
		}
		return ids;
	}

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(root());
	}

	private static LiteralArgumentBuilder<CommandSourceStack> root() {
		LiteralArgumentBuilder<CommandSourceStack> creator = Commands.literal("creator");
		for (Kind kind : Kind.values()) {
			creator.then(Commands.literal("unlock").then(idArgument(kind, true)));
			creator.then(Commands.literal("revoke").then(idArgument(kind, false)));
		}
		creator.then(Commands.literal("unlocks").executes(ctx -> listUnlocks(ctx.getSource(), ctx.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> listUnlocks(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))));

		return Commands.literal("dualityadmin").requires(source -> source.hasPermission(2))
				.then(Commands.literal("debug").then(Commands.literal("characterCreator")
						.executes(ctx -> debug(ctx.getSource(), ctx.getSource().getPlayerOrException(), null))
						.then(debugSwitch("on", true)).then(debugSwitch("off", false))))
				.then(creator);
	}

	private static ArgumentBuilder<CommandSourceStack, ?> debugSwitch(String word, boolean enabled) {
		return Commands.literal(word).executes(ctx -> debug(ctx.getSource(), ctx.getSource().getPlayerOrException(), enabled))
				.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> debug(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), enabled)));
	}

	/** "unlock race &lt;id&gt; [player]" and friends - one literal per kind, then the id, then an
	 *  optional player. */
	private static ArgumentBuilder<CommandSourceStack, ?> idArgument(Kind kind, boolean grant) {
		SuggestionProvider<CommandSourceStack> suggestions = (ctx, builder) -> SharedSuggestionProvider.suggest(kind.ids().stream().map(CharacterCreatorAdminCommand::quoteIfNeeded).toList(), builder);
		Function<CommandContext<CommandSourceStack>, String> id = ctx -> StringArgumentType.getString(ctx, "id");
		return Commands.literal(kind.name().toLowerCase()).then(Commands.argument("id", StringArgumentType.string()).suggests(suggestions)
				.executes(ctx -> change(ctx.getSource(), ctx.getSource().getPlayerOrException(), kind, id.apply(ctx), grant))
				.then(Commands.argument("player", EntityArgument.player()).executes(ctx -> change(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), kind, id.apply(ctx), grant))));
	}

	private static int debug(CommandSourceStack source, ServerPlayer target, Boolean enabled) {
		boolean on = enabled != null ? enabled : !CharacterCreation.isDebugUnlockAll(target);
		CharacterCreation.setDebugUnlockAll(target, on);
		source.sendSuccess(() -> Component.literal("Character creator debug " + (on ? "ON - everything shows as unlocked" : "OFF - back to their real unlocks") + " for "
				+ target.getGameProfile().getName()).withStyle(on ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
		return 1;
	}

	private static int change(CommandSourceStack source, ServerPlayer target, Kind kind, String id, boolean grant) throws CommandSyntaxException {
		if (!kind.exists(id)) {
			source.sendFailure(Component.literal("No " + kind.name().toLowerCase() + " called '" + id + "'."));
			return 0;
		}
		boolean changed = grant ? CharacterCreation.grantUnlock(target, kind.profileKey, id) : CharacterCreation.revokeUnlock(target, kind.profileKey, id);
		String name = target.getGameProfile().getName();
		if (!changed) {
			source.sendFailure(Component.literal(name + (grant ? " already has " : " doesn't have ") + kind.name().toLowerCase() + " '" + id + "' unlocked."));
			return 0;
		}
		source.sendSuccess(() -> Component.literal((grant ? "Unlocked " : "Revoked ") + kind.name().toLowerCase() + " '" + id + "' for " + name)
				.withStyle(grant ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
		return 1;
	}

	private static int listUnlocks(CommandSourceStack source, ServerPlayer target) {
		String name = target.getGameProfile().getName();
		source.sendSuccess(() -> Component.literal("Creator unlocks for " + name + (CharacterCreation.isDebugUnlockAll(target) ? " (debug ON - everything shows)" : ""))
				.withStyle(ChatFormatting.GOLD), false);
		for (Kind kind : Kind.values()) {
			List<String> values = new ArrayList<>(CharacterCreation.profileList(target, kind.profileKey));
			String extra = kind == Kind.SUBRACE ? " (plus starters: " + String.join(", ", RaceCatalog.starterSubraces()) + ")" : "";
			source.sendSuccess(() -> Component.literal("  " + kind.name().toLowerCase() + ": " + (values.isEmpty() ? "-" : String.join(", ", values)) + extra)
					.withStyle(ChatFormatting.GRAY), false);
		}
		return 1;
	}
}
