package net.spidrotech.duality.charactercreation;

import net.spidrotech.duality.DualityDatabaseManager;
import net.spidrotech.duality.DualityMod;
import net.spidrotech.duality.ModAttachments;
import net.spidrotech.duality.charactercreation.CharacterDraft.DraftResult;
import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.skin.SkinLoadoutCodec;
import net.spidrotech.duality.skin.SkinManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server side of the character creator, and the thing your screens talk to.
 *
 * <p>The flow is: {@link #begin} puts a draft on the player, each screen sends a
 * {@link CreationAction} which lands in {@link #act}, and {@link #commit} turns the finished draft
 * into a real character. After every change the player gets a fresh {@link DraftView}, so a screen
 * never computes state itself - it draws what it was last sent.
 *
 * <p>Everything is re-checked here. The client tells the server what the player clicked; it does not
 * tell the server what is true. A screen asking for a race the player hasn't earned, a sixth point
 * out of five, or a power outside its pool gets a refusal and an unchanged draft.
 *
 * <pre>
 *   CharacterCreation.beginIfNeeded(player);              // on join, when they have no character
 *   CharacterCreation.act(player, SELECT_RACE, "witch", 0);
 *   CharacterCreation.act(player, ALLOCATE_SKILL, "attunement", 1);
 *   CharacterCreation.commit(player);                     // builds it, links it, applies it
 * </pre>
 */
public final class CharacterCreation {
	private static final Map<UUID, CharacterDraft> DRAFTS = new ConcurrentHashMap<>();

	/** What came of a commit. On success, characterId is the new character. */
	public record CommitResult(boolean ok, String message, String characterId) {
		public static CommitResult no(String message) {
			return new CommitResult(false, message, "");
		}
	}

	private CharacterCreation() {
	}

	// ------------------------------------------------------------------------------ lifecycle
	public static boolean isCreating(ServerPlayer player) {
		return player != null && DRAFTS.containsKey(player.getUUID());
	}

	/** The live draft, or null. Server-side callers only; screens read a {@link DraftView}. */
	public static CharacterDraft draft(ServerPlayer player) {
		return player == null ? null : DRAFTS.get(player.getUUID());
	}

	/** Starts a fresh draft and sends the player the catalog and their first view. */
	public static void begin(ServerPlayer player) {
		if (player == null)
			return;
		DRAFTS.put(player.getUUID(), new CharacterDraft());
		CharacterCreationNetwork.sendCatalog(player);
		// Whoever they were, they aren't any more - drop the old name off their head before they
		// start building a new one.
		CharacterDisplay.publish(player);
		sync(player, "Let's begin.");
	}

	/**
	 * Starts the creator only if this player actually needs a character - no active one, or their
	 * last one is dead. This is what the join hook calls; calling it for a player who already has a
	 * character does nothing.
	 */
	public static boolean beginIfNeeded(ServerPlayer player) {
		if (player == null || isCreating(player) || !DualityDatabaseManager.shouldStartCreateCharacter(player))
			return false;
		DualityDatabaseManager.startCreateCharacter(player);
		begin(player);
		return true;
	}

	/** Drops the draft without building anything. */
	public static void cancel(ServerPlayer player) {
		if (player == null)
			return;
		DRAFTS.remove(player.getUUID());
		CharacterCreationNetwork.sendInactive(player);
	}

	public static void onPlayerLeave(ServerPlayer player) {
		if (player != null)
			DRAFTS.remove(player.getUUID());
	}

	// --------------------------------------------------------------------------------- reads
	/** The races screen one should offer this player. */
	public static List<RaceDefinition> selectableRaces(ServerPlayer player) {
		return RaceCatalog.selectableFor(unlockedSpecies(player));
	}

	/** Species this player's profile has earned the right to play, from their player file. */
	public static Set<String> unlockedSpecies(ServerPlayer player) {
		Set<String> unlocked = new LinkedHashSet<>();
		JsonObject profile = DualityDatabaseManager.getPlayerProfile(player);
		if (profile == null || !profile.has("unlocked_species"))
			return unlocked;
		JsonArray array = profile.getAsJsonArray("unlocked_species");
		for (int i = 0; i < array.size(); i++) {
			unlocked.add(array.get(i).getAsString());
		}
		return unlocked;
	}

	/** A snapshot of the player's draft for the screens, or {@link DraftView#INACTIVE}. */
	public static DraftView view(ServerPlayer player, String message) {
		CharacterDraft draft = draft(player);
		if (draft == null)
			return DraftView.INACTIVE;
		RaceDefinition race = RaceCatalog.get(draft.raceId());
		SubraceDefinition subrace = race == null ? null : race.subrace(draft.subraceId());
		List<String> selectable = new ArrayList<>();
		for (RaceDefinition candidate : selectableRaces(player)) {
			selectable.add(candidate.id());
		}
		return DraftView.of(draft, race, subrace, selectable, message == null ? "" : message);
	}

	public static void sync(ServerPlayer player, String message) {
		CharacterCreationNetwork.sendView(player, view(player, message));
	}

	// ------------------------------------------------------------------------------- actions
	/**
	 * Applies one screen action. Every rule lives behind this call, so a screen only has to know
	 * which button was pressed.
	 */
	public static DraftResult act(ServerPlayer player, CreationAction action, String arg, int amount) {
		CharacterDraft draft = draft(player);
		if (draft == null)
			return DraftResult.no("You aren't making a character.");
		if (action == null)
			return DraftResult.no("Unknown action.");

		RaceDefinition race = RaceCatalog.get(draft.raceId());
		SubraceDefinition subrace = race == null ? null : race.subrace(draft.subraceId());
		DraftResult result = switch (action) {
			case SELECT_RACE -> selectRace(player, draft, arg);
			case SELECT_SUBRACE -> draft.selectSubrace(race, race == null ? null : race.subrace(arg));
			case TOGGLE_ABILITY -> draft.toggleAbility(arg, race, subrace);
			case ALLOCATE_SKILL -> draft.allocate(SkillType.parse(arg), amount, race, subrace);
			case RESET_SKILLS -> draft.resetSkills();
			case SET_NAME -> setName(player, draft, arg);
			case GOTO_STEP -> gotoStep(draft, arg, race, subrace);
			case RESTART -> restart(player);
			case COMMIT -> {
				CommitResult commit = commit(player);
				// A refused commit leaves the draft alive and un-synced, so push the reason out
				// here - otherwise the screen's "done" button just does nothing visible.
				if (!commit.ok())
					sync(player, commit.message());
				yield new DraftResult(commit.ok(), commit.message());
			}
		};
		// A successful COMMIT has already sent an inactive view and RESTART has re-synced; every
		// other action syncs here.
		if (action != CreationAction.COMMIT && action != CreationAction.RESTART)
			sync(player, result.message());
		return result;
	}

	private static DraftResult selectRace(ServerPlayer player, CharacterDraft draft, String raceId) {
		if (!RaceCatalog.isSelectableFor(unlockedSpecies(player), raceId))
			return DraftResult.no("You haven't earned the right to play that yet.");
		return draft.selectRace(RaceCatalog.get(raceId));
	}

	/** Names are checked for shape by the draft and for collisions here, since only the server
	 *  knows what else this player has living. */
	private static DraftResult setName(ServerPlayer player, CharacterDraft draft, String candidate) {
		DraftResult shaped = draft.setName(candidate);
		if (!shaped.ok())
			return shaped;
		for (String existingId : DualityDatabaseManager.getAliveCharacterIds(player)) {
			JsonObject sheet = DualityDatabaseManager.getCharacterSheet(player, existingId);
			if (sheet != null && sheet.has("name") && sheet.get("name").getAsString().equalsIgnoreCase(draft.name())) {
				draft.setName("");
				return DraftResult.no("You already have a living character called that.");
			}
		}
		return DraftResult.OK;
	}

	private static DraftResult gotoStep(CharacterDraft draft, String stepName, RaceDefinition race, SubraceDefinition subrace) {
		CreationStep target;
		if (stepName == null || stepName.isBlank()) {
			// "Next" - refuse to move off a step whose question isn't answered.
			if (!draft.step().isSatisfiedBy(draft, race, subrace))
				return DraftResult.no(draft.step().title() + " first.");
			target = draft.step().next();
		} else {
			target = parseStep(stepName);
			if (target == null)
				return DraftResult.no("No such step.");
			// Jumping forward past an unanswered question isn't allowed; going back always is.
			if (target.isAfter(draft.step()) && !draft.step().isSatisfiedBy(draft, race, subrace))
				return DraftResult.no(draft.step().title() + " first.");
		}
		draft.setStep(target);
		return DraftResult.OK;
	}

	private static DraftResult restart(ServerPlayer player) {
		begin(player);
		return DraftResult.ok("Starting over.");
	}

	private static CreationStep parseStep(String raw) {
		for (CreationStep step : CreationStep.values()) {
			if (step.name().equalsIgnoreCase(raw.trim()))
				return step;
		}
		return null;
	}

	// -------------------------------------------------------------------------------- commit
	/**
	 * Builds the character, links it to the player, and gives them what they chose.
	 *
	 * <p>In order: the sheet is written first (race, lineage, stat line, powers, appearance), then
	 * the character is made active, then the player is given the attributes and ability unlocks.
	 * The sheet has to be complete before {@link SkinManager#onActiveCharacterChanged} runs, because
	 * that reloads the appearance <em>from</em> the sheet.
	 */
	public static CommitResult commit(ServerPlayer player) {
		CharacterDraft draft = draft(player);
		if (draft == null)
			return CommitResult.no("You aren't making a character.");
		RaceDefinition race = RaceCatalog.get(draft.raceId());
		SubraceDefinition subrace = race == null ? null : race.subrace(draft.subraceId());
		if (!draft.isComplete(race, subrace))
			return CommitResult.no(draft.firstIncompleteStep(race, subrace).title() + " first.");
		if (!RaceCatalog.isSelectableFor(unlockedSpecies(player), draft.raceId()))
			return CommitResult.no("You haven't earned the right to play that.");

		String characterId = DualityDatabaseManager.createNewCharacter(player, draft.name(), race.id());
		if (characterId == null || characterId.isEmpty())
			return CommitResult.no("The character couldn't be written to disk.");

		JsonObject sheet = DualityDatabaseManager.getCharacterSheet(player, characterId);
		if (sheet == null)
			return CommitResult.no("The character couldn't be read back after writing.");

		Map<SkillType, Integer> skills = draft.skillValues(race, subrace);
		List<String> abilities = startingAbilities(draft, subrace);
		writeSheet(sheet, race, subrace, skills, abilities, draft.pointsRemaining(race, subrace));
		// The appearance the player built during creation lives in SkinManager's in-memory slot
		// (it had no character to save to); this is where it finally gets a home.
		JsonObject charCreator = sheet.has(SkinLoadoutCodec.SECTION) ? sheet.getAsJsonObject(SkinLoadoutCodec.SECTION) : new JsonObject();
		SkinLoadoutCodec.writeInto(charCreator, SkinManager.get().loadoutOf(player.getUUID()));
		sheet.add(SkinLoadoutCodec.SECTION, charCreator);
		DualityDatabaseManager.saveCharacterSheet(player, characterId, sheet);

		applyToPlayer(player, race, skills, abilities);
		SkinManager.get().onActiveCharacterChanged(player);
		// Name over their head, in chat, and in the tab list - and the face in tab, which follows
		// the appearance the line above just reloaded.
		CharacterDisplay.publish(player);

		DRAFTS.remove(player.getUUID());
		CharacterCreationNetwork.sendInactive(player);
		DualityMod.LOGGER.info("[duality] {} created {} ({} {}) with {} power(s)", player.getScoreboardName(), draft.name(), race.id(),
				subrace == null ? "-" : subrace.id(), abilities.size());
		return new CommitResult(true, draft.name() + " it is.", characterId);
	}

	/** Chosen powers and the ones the lineage hands over, in a stable order and deduplicated. */
	public static List<String> startingAbilities(CharacterDraft draft, SubraceDefinition subrace) {
		Set<String> abilities = new LinkedHashSet<>();
		if (subrace != null)
			abilities.addAll(subrace.grantedAbilities());
		abilities.addAll(draft.abilityIds());
		return new ArrayList<>(abilities);
	}

	private static void writeSheet(JsonObject sheet, RaceDefinition race, SubraceDefinition subrace, Map<SkillType, Integer> skills, List<String> abilities,
			int unspentPoints) {
		// Replace the species profile rather than appending: createNewCharacter writes one only for
		// non-Human species, so this is the one place the shape is guaranteed.
		JsonObject profile = new JsonObject();
		profile.addProperty("class", race.id());
		profile.addProperty("subspecies", subrace == null ? race.id() : subrace.id());
		profile.addProperty("subtier", "Baseline");
		profile.addProperty("level", 1);
		profile.addProperty("xp", 0);
		profile.add("species_bound_unlocks", new JsonArray());
		JsonArray profiles = new JsonArray();
		profiles.add(profile);
		sheet.add("species_profiles", profiles);

		CharacterAttributes.writeSkills(sheet, skills);
		sheet.addProperty(CharacterAttributes.SHEET_UNSPENT, Math.max(0, unspentPoints));
		JsonArray abilityArray = new JsonArray();
		for (String ability : abilities) {
			abilityArray.add(ability);
		}
		sheet.add(CharacterAttributes.SHEET_ABILITIES, abilityArray);
	}

	/**
	 * Gives the player the body and the powers their sheet says they have.
	 *
	 * <p>Also the re-application path: call it on login and after a character switch, not just at
	 * creation, so a character's stat line survives a relog.
	 */
	public static void applyToPlayer(ServerPlayer player, RaceDefinition race, Map<SkillType, Integer> skills, List<String> abilities) {
		CharacterAttributes.apply(player, skills);
		if (abilities != null && !abilities.isEmpty()) {
			HashSet<ResourceLocation> unlocked = new HashSet<>(player.getData(ModAttachments.UNLOCKED_ABILITIES));
			for (String ability : abilities) {
				unlocked.add(ability.indexOf(':') >= 0 ? ResourceLocation.parse(ability) : ResourceLocation.fromNamespaceAndPath("duality", ability));
			}
			player.setData(ModAttachments.UNLOCKED_ABILITIES, unlocked);
		}
		applyRaceTag(player, race);
	}

	/** Re-applies whatever the player's current character is. Login and character-switch path. */
	public static void applyActiveCharacter(ServerPlayer player) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (characterId.isEmpty()) {
			CharacterAttributes.clear(player);
			return;
		}
		JsonObject sheet = DualityDatabaseManager.getCharacterSheet(player, characterId);
		if (sheet == null) {
			CharacterAttributes.clear(player);
			return;
		}
		RaceDefinition race = RaceCatalog.get(raceIdOf(sheet));
		List<String> abilities = new ArrayList<>();
		if (sheet.has(CharacterAttributes.SHEET_ABILITIES)) {
			JsonArray array = sheet.getAsJsonArray(CharacterAttributes.SHEET_ABILITIES);
			for (int i = 0; i < array.size(); i++) {
				abilities.add(array.get(i).getAsString());
			}
		}
		applyToPlayer(player, race, CharacterAttributes.readSkills(sheet), abilities);
	}

	/** The race id on a character sheet, or "" - reads the first species profile. */
	public static String raceIdOf(JsonObject sheet) {
		if (sheet == null || !sheet.has("species_profiles"))
			return "";
		JsonArray profiles = sheet.getAsJsonArray("species_profiles");
		if (profiles.isEmpty())
			return "";
		JsonObject first = profiles.get(0).getAsJsonObject();
		return first.has("class") ? first.get("class").getAsString() : "";
	}

	/**
	 * Keeps the EquippedAbilities marker string in step with the character's race.
	 *
	 * <p>That string is how {@code VampireRank#isVampire} and the Ret* procedures already ask "is
	 * this player a vampire", so a race with a marker has to set it and every other race has to
	 * clear it - otherwise a player whose vampire died comes back as a human who still reads as
	 * one.
	 */
	private static void applyRaceTag(ServerPlayer player, RaceDefinition race) {
		DualityModVariables.PlayerVariables vars = player.getData(DualityModVariables.PLAYER_VARIABLES);
		String tag = race == null ? "" : race.equippedTag();
		String others = vars.EquippedAbilities;
		for (RaceDefinition known : RaceCatalog.all()) {
			if (!known.equippedTag().isEmpty())
				others = others.replace(known.equippedTag(), "");
		}
		others = others.trim();
		vars.EquippedAbilities = tag.isEmpty() ? others : (others.isEmpty() ? tag : others + " " + tag);
		vars.markSyncDirty();
	}
}
