package net.spidrotech.duality.charactercreation;

import net.spidrotech.duality.charactercreation.CharacterDraft.DraftResult;
import net.spidrotech.duality.procedures.SelectRaceProcedure;
import net.spidrotech.duality.DualityMod;

import net.spidrone.uiapi.UIButtonElement;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/**
 * Connects the MCreator-built character creator screen to the real creator backend
 * (CharacterCreation). The screen's race list sends a spis_ui_api button click to the server; this
 * turns that click into an actual CreationAction.SELECT_RACE on the player's draft.
 *
 * WHY THIS EXISTS AS HAND-WRITTEN JAVA rather than being left to the screen: MCreator generates its
 * own handler for this same button group inside CharacterSelectorScreen's static initialiser, and
 * that handler cannot do this job for two structural reasons.
 *   1. DEDICATED SERVERS. CharacterSelectorScreen is a client-only class (AbstractContainerScreen),
 *      so on a real server it is never loaded, its static block never runs, and nothing is
 *      registered - race clicks would silently do nothing for everyone. Registering from common
 *      setup, as this does, runs on both sides.
 *   2. NO PLAYER IN THE PROCEDURE. SelectRaceProcedure takes only its item_value string (it has no
 *      entity dependency), so by itself it cannot touch the player whose draft needs changing.
 *
 * This handler WINS over the generated one rather than fighting it: RadialMenuNetwork keeps the
 * FIRST handler registered per key (putIfAbsent), and common setup happens long before any screen
 * class loads on the client. Same trick, and same reasoning, as AbilitiesRadialHandler already uses
 * for its wheel.
 *
 * SelectRaceProcedure stays in the live path: the click is forwarded to it and it calls applyRace
 * below, so the procedure really is what selects the race and anything added to it runs on every
 * race click. Only the two things a procedure structurally cannot do - being registered at all on a
 * dedicated server, and receiving the clicking player - happen here.
 */
@EventBusSubscriber(modid = "duality")
public final class CharacterCreatorScreenHandler {
	/** Button group the creator's race list sends under - must match the id the screen's scroll
	 *  region uses (see CharacterSelectorScreen's UIButtonElement calls). */
	public static final String RACE_GROUP = "duality:race_scroll";
	/** Button group screen two's lineage list sends under - see CharacterSelectorPage2Screen's
	 *  init(), which builds one of these per RaceCatalog subrace of whichever race the player picked
	 *  rather than a fixed slot per race, so the group carries the bare subrace id with no prefix to
	 *  strip (there's no fixed-name-per-screen-slot problem to solve here the way "race_human" had). */
	public static final String SUBRACE_GROUP = "duality:subrace_scroll";
	/** Button group screen three's power list sends under - same shape as SUBRACE_GROUP: built and
	 *  wired straight to this group in hand-written Java (see CharacterSelectorPage3Screen#init), one
	 *  button per AbilityCatalog entry the chosen lineage's bracket can see, carrying that entry's
	 *  bare catalog id. */
	public static final String ABILITY_GROUP = "duality:ability_scroll";
	/** Screen four's per-skill arrows (see CharacterSelectorPage4Screen#init). Two groups rather than
	 *  one because each row carries both a minus and a plus under the same skill id. */
	public static final String SKILL_MINUS_GROUP = "duality:skill_minus";
	public static final String SKILL_PLUS_GROUP = "duality:skill_plus";
	/** MCreator's screen editor sends the row's id, which is prefixed to keep button ids unique
	 *  within the screen ("race_human"), while RaceCatalog and SelectRaceProcedure both speak the
	 *  bare race id ("human"). Stripped in one place so either spelling works and neither side has
	 *  to care what the other does. */
	private static final String RACE_ID_PREFIX = "race_";

	private CharacterCreatorScreenHandler() {
	}

	@SubscribeEvent
	public static void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			UIButtonElement.registerServerHandler(RACE_GROUP, CharacterCreatorScreenHandler::selectRace);
			UIButtonElement.registerServerHandler(SUBRACE_GROUP, CharacterCreatorScreenHandler::selectSubrace);
			UIButtonElement.registerServerHandler(ABILITY_GROUP, CharacterCreatorScreenHandler::selectAbility);
			UIButtonElement.registerServerHandler(SKILL_MINUS_GROUP, (player, skillId) -> applySkill(player, skillId, -1));
			UIButtonElement.registerServerHandler(SKILL_PLUS_GROUP, (player, skillId) -> applySkill(player, skillId, 1));
		});
	}

	/** Click callback. Deliberately does no work of its own beyond handing off: SelectRaceProcedure
	 *  is the procedure the creator screen is built around, so the click goes THROUGH it rather than
	 *  around it, and anything added to it applies to every race click. */
	private static void selectRace(ServerPlayer player, String selectionId) {
		if (player == null || selectionId == null)
			return;
		SelectRaceProcedure.execute(player, selectionId);
	}

	/**
	 * The actual race selection, shared so it has exactly one implementation no matter which entry
	 * point reaches it. Lives here rather than in the procedure because this file is hand-written and
	 * the procedure is MCreator-generated - see that class's warning about code locking.
	 *
	 * Returns whether the race was accepted, so a caller that wants to advance the screen to the
	 * lineage step can tell a successful pick from a refused one.
	 */
	public static boolean applyRace(ServerPlayer player, String selectionId) {
		if (player == null || selectionId == null)
			return false;
		String raceId = normalizeRaceId(selectionId);
		if (!RaceCatalog.has(raceId)) {
			// A button whose id doesn't name a real race is a screen/catalog mismatch, not player
			// input worth a message - log it so it's findable when a new row is added to the list.
			DualityMod.LOGGER.warn("[duality] Character creator sent unknown race id '{}' (from '{}')", raceId, selectionId);
			return false;
		}
		ensureDraft(player);
		// act() is the authority: it re-checks that this player has actually earned the race (see
		// CharacterCreation#selectRace / RaceCatalog#isSelectableFor) and resets the downstream
		// lineage/ability/point choices that belonged to the old race. Nothing here goes to the action
		// bar - the creator screen sits directly over it, so nothing sent there is ever actually seen.
		// A refusal shows in the codex instead, in red, right on the row the player just clicked; a
		// success is already visible as the row's own highlight and the codex updating to match.
		DraftResult result = CharacterCreation.act(player, CreationAction.SELECT_RACE, raceId, 0);
		return result.ok();
	}

	/** Click callback for screen two's lineage list - the SELECT_SUBRACE mirror of selectRace above. */
	private static void selectSubrace(ServerPlayer player, String selectionId) {
		if (player == null || selectionId == null)
			return;
		applySubrace(player, selectionId);
	}

	/**
	 * The actual lineage selection. No procedure to forward through here, unlike race - nothing else
	 * (no MCreator element) currently needs to call this, since screen two's buttons are built and
	 * wired straight to this group in hand-written Java (see CharacterSelectorPage2Screen#init),
	 * rather than through MCreator's fixed-item-list screen editor the way the race buttons are.
	 *
	 * <p>Returns whether the lineage was accepted, same reason as applyRace: a caller advancing the
	 * screen wants to tell a real pick from a refused one.
	 */
	public static boolean applySubrace(ServerPlayer player, String subraceId) {
		if (player == null || subraceId == null)
			return false;
		if (!CharacterCreation.isCreating(player))
			return false;
		// Same reasoning as applyRace above: nothing here goes to the action bar, since the creator
		// screen hides it completely. The codex covers both outcomes on its own.
		DraftResult result = CharacterCreation.act(player, CreationAction.SELECT_SUBRACE, subraceId, 0);
		return result.ok();
	}

	/** Click callback for screen three's power list - toggles it on the draft, same as the others. */
	private static void selectAbility(ServerPlayer player, String selectionId) {
		if (player == null || selectionId == null)
			return;
		applyAbility(player, selectionId);
	}

	/** Toggles one power on or off. Affordability, availability and everything else live in
	 *  CharacterDraft#toggleAbility; this is just the "get a live player onto that call" layer, same
	 *  as applySubrace above. */
	public static boolean applyAbility(ServerPlayer player, String abilityCatalogId) {
		if (player == null || abilityCatalogId == null)
			return false;
		if (!CharacterCreation.isCreating(player))
			return false;
		DraftResult result = CharacterCreation.act(player, CreationAction.TOGGLE_ABILITY, abilityCatalogId, 0);
		return result.ok();
	}

	/** Spends (delta +1) or refunds (delta -1) one point on a skill. The floor, the cap and the
	 *  budget are all CharacterDraft#allocate's to enforce. */
	public static boolean applySkill(ServerPlayer player, String skillId, int delta) {
		if (player == null || skillId == null || !CharacterCreation.isCreating(player))
			return false;
		return CharacterCreation.act(player, CreationAction.ALLOCATE_SKILL, skillId, delta).ok();
	}

	/**
	 * The race currently picked on this player's draft, or "" if they have no draft yet or haven't
	 * chosen. Meant to be callable straight from a procedure's custom-code block, so it takes any
	 * Entity and answers "" for anything that isn't a server player rather than throwing.
	 *
	 * SERVER SIDE ONLY, and that is not a limitation to work around: the draft is deliberately
	 * server-owned (see CharacterCreation's class doc - the client is never trusted to hold it), so
	 * this reads as "" if called from screen render code. A screen wanting to show which row is
	 * picked should ask spis_ui_api for its own selection instead:
	 * UIButtonElement.getSelected(RACE_GROUP).
	 */
	public static String selectedRaceId(Entity entity) {
		if (!(entity instanceof ServerPlayer player))
			return "";
		CharacterDraft draft = CharacterCreation.draft(player);
		return draft == null ? "" : draft.raceId();
	}

	/**
	 * The creator screen can only edit a draft, so clicking a race has to have one. Opening the
	 * creator and picking a race IS the intent to build a character, so one is started on demand
	 * rather than refusing the click.
	 *
	 * Nothing is destroyed by this: a character the player already had stays in their alive list and
	 * stays switchable (/character switch), and nothing is written to disk until COMMIT. The message
	 * says so, so a draft never starts silently under someone who only wanted to look.
	 */
	private static void ensureDraft(ServerPlayer player) {
		if (CharacterCreation.isCreating(player))
			return;
		if (CharacterCreation.beginIfNeeded(player))
			return; // they had no living character - this is the normal "make your first one" path
		CharacterCreation.begin(player);
		player.displayClientMessage(Component.literal("Started a new character - your current one is still yours, use /character switch to go back.").withStyle(ChatFormatting.GRAY), false);
	}

	private static String normalizeRaceId(String selectionId) {
		String trimmed = selectionId.trim();
		return trimmed.startsWith(RACE_ID_PREFIX) ? trimmed.substring(RACE_ID_PREFIX.length()) : trimmed;
	}
}
