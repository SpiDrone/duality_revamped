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
	/** MCreator's screen editor sends the row's id, which is prefixed to keep button ids unique
	 *  within the screen ("race_human"), while RaceCatalog and SelectRaceProcedure both speak the
	 *  bare race id ("human"). Stripped in one place so either spelling works and neither side has
	 *  to care what the other does. */
	private static final String RACE_ID_PREFIX = "race_";

	private CharacterCreatorScreenHandler() {
	}

	@SubscribeEvent
	public static void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> UIButtonElement.registerServerHandler(RACE_GROUP, CharacterCreatorScreenHandler::selectRace));
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
		// lineage/ability/point choices that belonged to the old race. Its own message is what the
		// player sees, refusal or not, so a locked race explains itself.
		DraftResult result = CharacterCreation.act(player, CreationAction.SELECT_RACE, raceId, 0);
		if (!result.message().isEmpty()) {
			player.displayClientMessage(Component.literal(result.message()).withStyle(result.ok() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
		}
		return result.ok();
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
