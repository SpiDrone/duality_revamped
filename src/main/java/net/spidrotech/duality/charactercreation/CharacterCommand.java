package net.spidrotech.duality.charactercreation;

import net.spidrotech.duality.DualityDatabaseManager;
import net.spidrotech.duality.charactercreation.CharacterDraft.DraftResult;
import net.spidrotech.duality.skin.SkinManager;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The creator, driven from chat.
 *
 * <p>Every connection point the screens will use is reachable from here, so the whole flow can be
 * exercised - and the balance of it argued with - before a single button exists. It goes through
 * exactly the same {@link CharacterCreation#act} the packets do, so anything that works here works
 * from a screen and vice versa.
 *
 * <pre>
 *   /character begin
 *   /character races                     what screen one would draw
 *   /character race witch
 *   /character subrace elemental
 *   /character ability lightning_hands_normal
 *   /character skill attunement 2
 *   /character name "Mera Holt"
 *   /character status                    the whole DraftView, as the screens see it
 *   /character commit
 * </pre>
 *
 * <p>Op-gated: it can hand out powers and attributes, which the real flow only does once.
 */
@EventBusSubscriber(modid = "duality")
public final class CharacterCommand {
	private static final SuggestionProvider<CommandSourceStack> RACE_IDS = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		for (RaceDefinition race : RaceCatalog.all()) {
			ids.add(race.id());
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> SUBRACE_IDS = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
			CharacterDraft draft = CharacterCreation.draft(player);
			RaceDefinition race = draft == null ? null : RaceCatalog.get(draft.raceId());
			if (race != null) {
				for (SubraceDefinition subrace : race.subraces()) {
					ids.add(subrace.id());
				}
			}
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> ABILITY_IDS = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
			CharacterDraft draft = CharacterCreation.draft(player);
			RaceDefinition race = draft == null ? null : RaceCatalog.get(draft.raceId());
			if (race != null)
				ids.addAll(race.selectableAbilities(race.subrace(draft.subraceId())));
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	private static final SuggestionProvider<CommandSourceStack> SKILL_IDS = (ctx, builder) -> {
		List<String> ids = new ArrayList<>();
		for (SkillType skill : SkillType.values()) {
			ids.add(skill.id());
		}
		return SharedSuggestionProvider.suggest(ids, builder);
	};

	private CharacterCommand() {
	}

	@SubscribeEvent
	public static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("character").requires(source -> source.hasPermission(2)) //
				.then(Commands.literal("begin").executes(ctx -> run(ctx, player -> {
					CharacterCreation.begin(player);
					return DraftResult.ok("Creator open.");
				}))) //
				.then(Commands.literal("cancel").executes(ctx -> run(ctx, player -> {
					CharacterCreation.cancel(player);
					return DraftResult.ok("Draft thrown away.");
				}))) //
				.then(Commands.literal("status").executes(CharacterCommand::status)) //
				.then(Commands.literal("races").executes(CharacterCommand::races)) //
				.then(Commands.literal("race").then(Commands.argument("race", StringArgumentType.word()).suggests(RACE_IDS)
						.executes(ctx -> act(ctx, CreationAction.SELECT_RACE, StringArgumentType.getString(ctx, "race"), 0)))) //
				.then(Commands.literal("subraces").executes(CharacterCommand::subraces)) //
				.then(Commands.literal("subrace").then(Commands.argument("subrace", StringArgumentType.word()).suggests(SUBRACE_IDS)
						.executes(ctx -> act(ctx, CreationAction.SELECT_SUBRACE, StringArgumentType.getString(ctx, "subrace"), 0)))) //
				.then(Commands.literal("abilities").executes(CharacterCommand::abilities)) //
				.then(Commands.literal("ability").then(Commands.argument("ability", StringArgumentType.word()).suggests(ABILITY_IDS)
						.executes(ctx -> act(ctx, CreationAction.TOGGLE_ABILITY, StringArgumentType.getString(ctx, "ability"), 0)))) //
				.then(Commands.literal("skills").executes(CharacterCommand::skills)) //
				.then(Commands.literal("skill").then(Commands.argument("skill", StringArgumentType.word()).suggests(SKILL_IDS)
						.then(Commands.argument("delta", IntegerArgumentType.integer(-CharacterDraft.STARTING_POINTS, CharacterDraft.STARTING_POINTS))
								.executes(ctx -> act(ctx, CreationAction.ALLOCATE_SKILL, StringArgumentType.getString(ctx, "skill"),
										IntegerArgumentType.getInteger(ctx, "delta")))))) //
				.then(Commands.literal("resetskills").executes(ctx -> act(ctx, CreationAction.RESET_SKILLS, "", 0))) //
				.then(Commands.literal("name").then(Commands.argument("name", StringArgumentType.greedyString())
						.executes(ctx -> act(ctx, CreationAction.SET_NAME, StringArgumentType.getString(ctx, "name"), 0)))) //
				.then(Commands.literal("commit").executes(CharacterCommand::commit)) //
				.then(Commands.literal("list").executes(CharacterCommand::list)) //
				.then(Commands.literal("switch").then(Commands.argument("id", StringArgumentType.word()).executes(CharacterCommand::switchCharacter))) //
				.then(Commands.literal("spend").then(Commands.argument("skill", StringArgumentType.word()).suggests(SKILL_IDS).executes(CharacterCommand::spend))));
	}

	// ------------------------------------------------------------------------------- readouts
	private static int status(CommandContext<CommandSourceStack> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
			return 0;
		DraftView view = CharacterCreation.view(player, "");
		if (!view.active()) {
			reply(ctx, "§7Not making a character. /character begin");
			return listActive(ctx, player);
		}
		RaceDefinition race = view.race();
		SubraceDefinition subrace = view.subrace();
		reply(ctx, "§6On " + view.step().name() + " - " + view.step().title());
		reply(ctx, "  §7race §f" + (race == null ? "-" : race.displayName()) + "§7, lineage §f" + (subrace == null ? "-" : subrace.displayName()));
		reply(ctx, "  §7powers §f" + (view.allAbilities().isEmpty() ? "-" : String.join(", ", view.allAbilities())) + " §8(" + view.abilityPicksRemaining()
				+ " pick(s) left)");
		StringBuilder line = new StringBuilder();
		for (SkillType skill : SkillType.values()) {
			line.append(line.isEmpty() ? "" : "  ").append(skill.displayName(), 0, 3).append(" §f").append(view.skill(skill)).append("§7");
		}
		reply(ctx, "  §7" + line + " §8(" + view.pointsRemaining() + " point(s) left)");
		reply(ctx, "  §7name §f" + (view.name().isEmpty() ? "-" : view.name()) + (view.nameUsable() ? " §a(ok)" : " §c(not set)"));
		StringBuilder steps = new StringBuilder();
		for (CreationStep step : CreationStep.values()) {
			if (step == CreationStep.READY)
				continue;
			steps.append(view.isStepComplete(step) ? "§a" : "§8").append(step.name()).append(" ");
		}
		reply(ctx, "  " + steps);
		reply(ctx, view.complete() ? "  §aReady. /character commit" : "  §7Next up: " + view.step().title());
		return 1;
	}

	private static int races(CommandContext<CommandSourceStack> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
			return 0;
		DraftView view = CharacterCreation.view(player, "");
		reply(ctx, "§6Races (what screen one would draw):");
		for (RaceDefinition race : RaceCatalog.all()) {
			boolean open = view.active() ? view.canSelectRace(race.id()) : RaceCatalog.isSelectableFor(CharacterCreation.unlockedSpecies(player), race.id());
			reply(ctx, "  " + (open ? "§f" : "§8") + race.displayName() + " §8" + race.id() + (open ? "" : " (locked)") + " §7- "
					+ race.subraces().size() + " lineage(s), " + race.abilityPicks() + " pick(s)");
		}
		return 1;
	}

	private static int subraces(CommandContext<CommandSourceStack> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
			return 0;
		RaceDefinition race = CharacterCreation.view(player, "").race();
		if (race == null) {
			reply(ctx, "§cPick a race first.");
			return 0;
		}
		reply(ctx, "§6" + race.displayName() + " lineages:");
		for (SubraceDefinition subrace : race.subraces()) {
			StringBuilder bonuses = new StringBuilder();
			subrace.skillBonusMap().forEach((skill, bonus) -> bonuses.append(bonuses.isEmpty() ? "" : ", ").append(skill.displayName()).append(" ")
					.append(bonus > 0 ? "+" : "").append(bonus));
			reply(ctx, "  §f" + subrace.displayName() + " §8" + subrace.id());
			reply(ctx, "    §7" + subrace.description());
			if (!subrace.grantedAbilities().isEmpty())
				reply(ctx, "    §7grants §f" + String.join(", ", subrace.grantedAbilities()));
			if (!bonuses.isEmpty())
				reply(ctx, "    §7" + bonuses);
		}
		return 1;
	}

	private static int abilities(CommandContext<CommandSourceStack> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
			return 0;
		DraftView view = CharacterCreation.view(player, "");
		RaceDefinition race = view.race();
		if (race == null) {
			reply(ctx, "§cPick a race first.");
			return 0;
		}
		reply(ctx, "§6Powers on offer (" + view.abilityPicksRemaining() + " pick(s) left):");
		for (String ability : race.selectableAbilities(view.subrace())) {
			reply(ctx, "  " + (view.hasChosen(ability) ? "§a[x] " : "§7[ ] ") + ability);
		}
		if (!view.grantedAbilityIds().isEmpty())
			reply(ctx, "§7Granted by your lineage: §f" + String.join(", ", view.grantedAbilityIds()));
		return 1;
	}

	private static int skills(CommandContext<CommandSourceStack> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
			return 0;
		DraftView view = CharacterCreation.view(player, "");
		if (!view.active()) {
			reply(ctx, "§6" + player.getScoreboardName() + "'s live character:");
			for (SkillType skill : SkillType.values()) {
				reply(ctx, String.format("  §7%-11s §f%d", skill.displayName(), CharacterAttributes.skillOf(player, skill)));
			}
			reply(ctx, "§7Unspent points: §f" + CharacterAttributes.unspentPoints(player) + " §8(/character spend <skill>)");
			return 1;
		}
		reply(ctx, "§6Stat line (" + view.pointsRemaining() + " of " + CharacterDraft.STARTING_POINTS + " point(s) left):");
		for (SkillType skill : SkillType.values()) {
			reply(ctx, String.format("  §7%-11s §f%d §8(%d spent) §7%s", skill.displayName(), view.skill(skill), view.allocated(skill),
					skill.description()));
		}
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
			return 0;
		return listActive(ctx, player);
	}

	private static int listActive(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		List<String> alive = DualityDatabaseManager.getAliveCharacterIds(player);
		String activeId = DualityDatabaseManager.getActiveCharacterId(player);
		if (alive.isEmpty()) {
			reply(ctx, "§7No living characters.");
			return 1;
		}
		reply(ctx, "§6Living characters:");
		for (String id : alive) {
			JsonObject sheet = DualityDatabaseManager.getCharacterSheet(player, id);
			String name = sheet != null && sheet.has("name") ? sheet.get("name").getAsString() : "?";
			String raceId = CharacterCreation.raceIdOf(sheet);
			reply(ctx, "  " + (id.equals(activeId) ? "§a* " : "§7  ") + name + " §8" + raceId + " / " + id);
		}
		return 1;
	}

	// ------------------------------------------------------------------------------- mutations
	private static int act(CommandContext<CommandSourceStack> ctx, CreationAction action, String arg, int amount) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
			return 0;
		DraftResult result = CharacterCreation.act(player, action, arg, amount);
		if (!result.message().isEmpty())
			reply(ctx, (result.ok() ? "§a" : "§c") + result.message());
		else if (result.ok())
			reply(ctx, "§aDone.");
		return result.ok() ? 1 : 0;
	}

	private static int commit(CommandContext<CommandSourceStack> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
			return 0;
		CharacterCreation.CommitResult result = CharacterCreation.commit(player);
		reply(ctx, (result.ok() ? "§a" : "§c") + result.message());
		if (result.ok())
			reply(ctx, "§7Character §f" + result.characterId() + "§7 is live. /character skills to see it.");
		return result.ok() ? 1 : 0;
	}

	private static int switchCharacter(CommandContext<CommandSourceStack> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
			return 0;
		String id = StringArgumentType.getString(ctx, "id");
		if (!DualityDatabaseManager.setActiveCharacter(player, id)) {
			reply(ctx, "§cNot one of your living characters.");
			return 0;
		}
		// Everything that keys off the active character has to be told, in this order: the sheet is
		// already on disk, so appearance and attributes just reload from it.
		CharacterCreation.applyActiveCharacter(player);
		SkinManager.get().onActiveCharacterChanged(player);
		reply(ctx, "§aSwitched.");
		return listActive(ctx, player);
	}

	private static int spend(CommandContext<CommandSourceStack> ctx) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
			return 0;
		SkillType skill = SkillType.parse(StringArgumentType.getString(ctx, "skill"));
		if (skill == null) {
			reply(ctx, "§cNo such skill.");
			return 0;
		}
		if (!CharacterAttributes.spendPoint(player, skill)) {
			reply(ctx, "§cNo points left, or " + skill.displayName() + " is already capped.");
			return 0;
		}
		reply(ctx, "§a" + skill.displayName() + " is now " + CharacterAttributes.skillOf(player, skill) + ". "
				+ CharacterAttributes.unspentPoints(player) + " point(s) left.");
		return 1;
	}

	private interface PlayerAction {
		DraftResult apply(ServerPlayer player);
	}

	private static int run(CommandContext<CommandSourceStack> ctx, PlayerAction action) {
		if (!(ctx.getSource().getEntity() instanceof ServerPlayer player))
			return 0;
		DraftResult result = action.apply(player);
		reply(ctx, (result.ok() ? "§a" : "§c") + result.message());
		return result.ok() ? 1 : 0;
	}

	private static void reply(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
	}
}
