package net.spidrotech.duality.charactercreation;

import net.spidrotech.duality.faction.Factions;
import net.spidrotech.duality.faction.FactionKind;
import net.spidrotech.duality.faction.FactionRecord;
import net.spidrotech.duality.faction.FactionRole;
import net.spidrotech.duality.abilities.vampire.VampireRank;
import net.spidrotech.duality.init.DualityModAttributes;
import net.spidrotech.duality.DualityDatabaseManager;
import net.spidrotech.duality.DualityMod;
import net.spidrotech.duality.ModAttachments;
import net.spidrotech.duality.charactercreation.CharacterDraft.DraftResult;
import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.skin.SkinLoadoutCodec;
import net.spidrotech.duality.skin.SkinManager;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
 *   CharacterCreation.act(player, SELECT_RACE, "demon", 0);
 *   CharacterCreation.act(player, ALLOCATE_SKILL, "attunement", 1);
 *   CharacterCreation.commit(player);                     // builds it, links it, applies it
 * </pre>
 */
public final class CharacterCreation {
	private static final Map<UUID, CharacterDraft> DRAFTS = new ConcurrentHashMap<>();
	/** Players whose draft name a LIVING character (anyone's) already has - checked once per name change
	 *  in setName, since the check reads every character sheet, and re-checked for real on commit. */
	private static final Set<UUID> NAME_TAKEN = ConcurrentHashMap.newKeySet();
	private static final Random RANDOM = new Random();
	/** Chances rolled once, silently, for a Mundane character - see {@link #rollLatentBlood}. */
	private static final double MUNDANE_WICCAN_CHANCE = 0.10;
	private static final double MUNDANE_HEMOVOID_CHANCE = 0.05;

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
		// A clean slate: vampire mode, its toggles and every temporary skin effect (the vampire look,
		// a vanquish's cracks) all live on the player, not the character, and would otherwise show
		// the last character's look on the new one in the creator's preview.
		if (net.spidrotech.duality.abilities.vampire.VampireMode.isActive(player))
			net.spidrotech.duality.abilities.vampire.VampireMode.setActive(player, false);
		net.spidrotech.duality.abilities.AbilityToggles.update(player, toggled -> net.spidrotech.duality.abilities.vampire.VampireMode.AUTO_ABILITIES.forEach(toggled::remove));
		net.spidrotech.duality.skin.SkinTempModify.removeKey(player);
		equipDefaultSkin(player);
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
		NAME_TAKEN.remove(player.getUUID());
		CharacterCreationNetwork.sendInactive(player);
	}

	public static void onPlayerLeave(ServerPlayer player) {
		if (player == null)
			return;
		DRAFTS.remove(player.getUUID());
		NAME_TAKEN.remove(player.getUUID());
	}

	// ------------------------------------------------------------------------------ unlocks
	// What the PLAYER has unlocked for character creation, kept on their player profile - separate
	// from anything a character owns. Nothing a character learns in play is ever copied in here; each
	// list only grows through grantUnlock (the /dualityadmin creator command today, whatever in-game
	// unlock mechanic comes later).
	/** Races: the pre-existing profile list, which also accepts a race's display name. */
	public static final String UNLOCKED_RACES_KEY = "unlocked_species";
	/** Lineages beyond each race's starter (see RaceCatalog#isStarterSubrace). Hidden until unlocked. */
	public static final String UNLOCKED_SUBRACES_KEY = "unlocked_subraces";
	/** AbilityCatalog ids this player may buy on screen three. None start unlocked. */
	public static final String UNLOCKED_ABILITIES_KEY = "unlocked_abilities";
	/** SkinPart unlock ids (see SkinPart#unlock) screen five may offer. Free parts need no entry. */
	public static final String UNLOCKED_COSMETICS_KEY = "unlocked_cosmetics";

	/** Players who've turned on /dualityadmin debug characterCreator: everything reads as unlocked
	 *  for them. Requirements (a vampire bloodline, a good last life) still apply - this only lifts
	 *  the unlock gate. In memory only, so it's off again after a restart. */
	private static final Set<UUID> DEBUG_UNLOCK_ALL = ConcurrentHashMap.newKeySet();

	public static boolean isDebugUnlockAll(ServerPlayer player) {
		return player != null && DEBUG_UNLOCK_ALL.contains(player.getUUID());
	}

	/** Turns the debug override on or off, re-syncing an open creator so its screens update at once. */
	public static void setDebugUnlockAll(ServerPlayer player, boolean enabled) {
		if (player == null)
			return;
		if (enabled)
			DEBUG_UNLOCK_ALL.add(player.getUUID());
		else
			DEBUG_UNLOCK_ALL.remove(player.getUUID());
		if (isCreating(player))
			sync(player, "");
	}

	public static boolean raceUnlocked(ServerPlayer player, RaceDefinition race) {
		return race != null && (isDebugUnlockAll(player) || RaceCatalog.isSelectableFor(unlockedSpecies(player), race.id()));
	}

	public static boolean subraceUnlocked(ServerPlayer player, SubraceDefinition subrace) {
		return subrace != null && (isDebugUnlockAll(player) || RaceCatalog.isStarterSubrace(subrace.id())
				|| containsIgnoreCase(profileList(player, UNLOCKED_SUBRACES_KEY), subrace.id()));
	}

	public static boolean abilityUnlocked(ServerPlayer player, AbilityDefinition ability) {
		return ability != null && (isDebugUnlockAll(player) || containsIgnoreCase(profileList(player, UNLOCKED_ABILITIES_KEY), ability.id()));
	}

	/** Whether screen five may offer this part: free, debug, or its unlock is on the profile. Used in
	 *  place of SkinUnlocks while creating, since there's no character yet to own anything. */
	public static boolean cosmeticAvailable(ServerPlayer player, net.spidrotech.duality.skin.SkinPart part) {
		if (part == null)
			return false;
		if (part.free() || isDebugUnlockAll(player))
			return true;
		return part.unlock().isPresent() && containsIgnoreCase(profileList(player, UNLOCKED_COSMETICS_KEY), part.unlock().get());
	}

	/** Adds an id to one of the unlock lists above. Returns false if it was already there. */
	public static boolean grantUnlock(ServerPlayer player, String key, String id) {
		JsonObject profile = DualityDatabaseManager.getPlayerProfile(player);
		if (profile == null || id == null || id.isBlank())
			return false;
		JsonArray array = profile.has(key) ? profile.getAsJsonArray(key) : new JsonArray();
		for (int i = 0; i < array.size(); i++) {
			if (array.get(i).getAsString().equalsIgnoreCase(id))
				return false;
		}
		array.add(id);
		profile.add(key, array);
		DualityDatabaseManager.savePlayerProfile(player, profile);
		if (isCreating(player))
			sync(player, "");
		return true;
	}

	/** Removes an id from one of the unlock lists above. Returns false if it wasn't there. */
	public static boolean revokeUnlock(ServerPlayer player, String key, String id) {
		JsonObject profile = DualityDatabaseManager.getPlayerProfile(player);
		if (profile == null || id == null || !profile.has(key))
			return false;
		JsonArray array = profile.getAsJsonArray(key);
		JsonArray kept = new JsonArray();
		boolean removed = false;
		for (int i = 0; i < array.size(); i++) {
			if (array.get(i).getAsString().equalsIgnoreCase(id))
				removed = true;
			else
				kept.add(array.get(i));
		}
		if (!removed)
			return false;
		profile.add(key, kept);
		DualityDatabaseManager.savePlayerProfile(player, profile);
		if (isCreating(player))
			sync(player, "");
		return true;
	}

	/** One of the player profile's unlock lists, or empty. */
	public static Set<String> profileList(ServerPlayer player, String key) {
		Set<String> values = new LinkedHashSet<>();
		JsonObject profile = DualityDatabaseManager.getPlayerProfile(player);
		if (profile == null || !profile.has(key))
			return values;
		JsonArray array = profile.getAsJsonArray(key);
		for (int i = 0; i < array.size(); i++) {
			values.add(array.get(i).getAsString());
		}
		return values;
	}

	private static boolean containsIgnoreCase(Set<String> values, String candidate) {
		for (String value : values) {
			if (value.equalsIgnoreCase(candidate))
				return true;
		}
		return false;
	}

	// --------------------------------------------------------------------------------- reads
	/** The races this player may pick right now - unlocked AND, for a race that also carries a
	 *  {@link RaceDefinition#requirementId()}, currently eligible. Kept in step with the same two
	 *  checks {@link #selectRace} re-does on the actual pick. */
	public static List<RaceDefinition> selectableRaces(ServerPlayer player) {
		List<RaceDefinition> available = new ArrayList<>();
		for (RaceDefinition race : RaceCatalog.all()) {
			if (raceUnlocked(player, race) && requirementMessage(player, race.requirementId()) == null)
				available.add(race);
		}
		return available;
	}

	/** Species this player's profile has earned the right to play, from their player file. */
	public static Set<String> unlockedSpecies(ServerPlayer player) {
		return profileList(player, UNLOCKED_RACES_KEY);
	}

	/** A snapshot of the player's draft for the screens, or {@link DraftView#INACTIVE}. */
	public static DraftView view(ServerPlayer player, String message) {
		CharacterDraft draft = draft(player);
		if (draft == null)
			return DraftView.INACTIVE;
		RaceDefinition race = RaceCatalog.get(draft.raceId());
		SubraceDefinition subrace = race == null ? null : race.subrace(draft.subraceId());
		List<String> selectable = new ArrayList<>();
		List<String> unlockedRaces = new ArrayList<>();
		for (RaceDefinition candidate : RaceCatalog.all()) {
			if (!raceUnlocked(player, candidate))
				continue;
			unlockedRaces.add(candidate.id());
			if (requirementMessage(player, candidate.requirementId()) == null)
				selectable.add(candidate.id());
		}
		List<String> selectableSubraces = new ArrayList<>();
		List<String> unlockedSubraces = new ArrayList<>();
		if (race != null) {
			for (SubraceDefinition candidate : race.subraces()) {
				if (!subraceUnlocked(player, candidate))
					continue;
				unlockedSubraces.add(candidate.id());
				if (requirementMessage(player, candidate.requirementId()) == null)
					selectableSubraces.add(candidate.id());
			}
		}
		List<String> unlockedAbilities = new ArrayList<>();
		for (AbilityDefinition ability : AbilityCatalog.all()) {
			if (abilityUnlocked(player, ability))
				unlockedAbilities.add(ability.id());
		}
		List<String> cosmetics = new ArrayList<>();
		for (net.spidrotech.duality.skin.SkinPart part : net.spidrotech.duality.skin.SkinPartCatalog.all()) {
			if (cosmeticAvailable(player, part))
				cosmetics.add(part.id());
		}
		return DraftView.of(draft, race, subrace, NAME_TAKEN.contains(player.getUUID()), appearanceMissing(player).isEmpty(), selectable, selectableSubraces,
				unlockedRaces, unlockedSubraces, unlockedAbilities, cosmetics, message == null ? "" : message);
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
		if (action == CreationAction.REOPEN) {
			CharacterCreatorNavigation.reopen(player);
			return new DraftResult(true, "");
		}

		RaceDefinition race = RaceCatalog.get(draft.raceId());
		SubraceDefinition subrace = race == null ? null : race.subrace(draft.subraceId());
		if (action == CreationAction.TOGGLE_VAMPIRE_PREVIEW) {
			// Purely a look - it lives on the skin, which syncs itself, so no view re-sync.
			if (isVampireLineage(race, subrace)) {
				if (net.spidrotech.duality.skin.SkinTempModify.hasKey(player, VAMPIRE_PREVIEW_KEY))
					net.spidrotech.duality.skin.SkinTempModify.removeKey(player, VAMPIRE_PREVIEW_KEY);
				else
					net.spidrotech.duality.abilities.vampire.VampireMode.applyLook(player, VAMPIRE_PREVIEW_KEY);
			}
			return new DraftResult(true, "");
		}
		DraftResult result = switch (action) {
			case SELECT_RACE -> selectRace(player, draft, arg);
			case SELECT_SUBRACE -> selectSubrace(player, draft, race, arg);
			case TOGGLE_ABILITY -> toggleAbility(player, draft, arg, race, subrace);
			case ALLOCATE_SKILL -> draft.allocate(SkillType.parse(arg), amount, race, subrace);
			case RESET_SKILLS -> draft.resetSkills();
			case SET_NAME -> setName(player, draft, arg);
			case GOTO_STEP -> gotoStep(draft, arg, race, subrace);
			case GO_BACK -> goBack(draft, race);
			case RESTART -> restart(player);
			case REOPEN, TOGGLE_VAMPIRE_PREVIEW -> new DraftResult(true, ""); // handled above, without a re-sync
			case COMMIT -> {
				CommitResult commit = commit(player);
				// A refused commit leaves the draft alive and un-synced, so push the reason out
				// here - otherwise the screen's "done" button just does nothing visible.
				if (!commit.ok())
					sync(player, commit.message());
				yield new DraftResult(commit.ok(), commit.message());
			}
		};
		// The vampire preview goes the moment the draft stops being a vampire (a different lineage
		// picked, "back" undoing it) - and on commit, where the real look is vampire mode's business.
		RaceDefinition nowRace = RaceCatalog.get(draft.raceId());
		boolean stillVampire = !(action == CreationAction.COMMIT && result.ok()) && isVampireLineage(nowRace, nowRace == null ? null : nowRace.subrace(draft.subraceId()));
		if (!stillVampire && net.spidrotech.duality.skin.SkinTempModify.hasKey(player, VAMPIRE_PREVIEW_KEY))
			net.spidrotech.duality.skin.SkinTempModify.removeKey(player, VAMPIRE_PREVIEW_KEY);
		// A successful COMMIT has already sent an inactive view and RESTART has re-synced; every
		// other action syncs here.
		if (action != CreationAction.COMMIT && action != CreationAction.RESTART)
			sync(player, result.message());
		return result;
	}

	/** Skin key for the creator's "show me as a vampire" preview (page five). */
	public static final String VAMPIRE_PREVIEW_KEY = "vampire_preview";

	/** Whether a draft on this race and lineage would be a vampire - what the preview button needs. */
	public static boolean isVampireLineage(RaceDefinition race, SubraceDefinition subrace) {
		return (race != null && "vampire".equals(race.equippedTag())) || (subrace != null && "vampire".equals(subrace.equippedTag()));
	}

	/**
	 * A refusal here clears the draft's race choice rather than leaving the last valid pick standing
	 * in for it - clicking a locked race is not "keep whatever I already had", and leaving it alone
	 * let a stale-but-valid race carry a player through screen one's "next" as if the locked click
	 * had never happened.
	 */
	private static DraftResult selectRace(ServerPlayer player, CharacterDraft draft, String raceId) {
		if (!raceUnlocked(player, RaceCatalog.get(raceId))) {
			draft.clearRace();
			return DraftResult.no("You haven't earned the right to play that yet.");
		}
		RaceDefinition race = RaceCatalog.get(raceId);
		String refusal = requirementMessage(player, race == null ? "" : race.requirementId());
		if (refusal != null) {
			draft.clearRace();
			return DraftResult.no(refusal);
		}
		return draft.selectRace(race);
	}

	/** Same reasoning as {@link #selectRace}'s doc, one screen later: a locked lineage clears the
	 *  draft's lineage choice instead of leaving whatever was already picked standing in for it. */
	private static DraftResult selectSubrace(ServerPlayer player, CharacterDraft draft, RaceDefinition race, String subraceId) {
		if (race == null)
			return DraftResult.no("Choose a race first.");
		SubraceDefinition subrace = race.subrace(subraceId);
		if (subrace != null && !subraceUnlocked(player, subrace)) {
			draft.clearSubrace(race);
			return DraftResult.no("You haven't discovered that lineage yet.");
		}
		String refusal = requirementMessage(player, subrace == null ? "" : subrace.requirementId());
		if (refusal != null) {
			draft.clearSubrace(race);
			return DraftResult.no(refusal);
		}
		return draft.selectSubrace(race, subrace);
	}

	/** Dropping a power is always allowed; taking one needs it unlocked on the player's profile
	 *  first. Everything else (bracket, affordability) is CharacterDraft#toggleAbility's. */
	private static DraftResult toggleAbility(ServerPlayer player, CharacterDraft draft, String abilityId, RaceDefinition race, SubraceDefinition subrace) {
		if (abilityId != null && !draft.abilityIds().contains(abilityId) && !abilityUnlocked(player, AbilityCatalog.get(abilityId)))
			return DraftResult.no("You haven't unlocked that power yet.");
		return draft.toggleAbility(abilityId, race, subrace);
	}

	/**
	 * Null if satisfied, else the refusal a screen should show. The one place a real-world
	 * requirement id off {@link RaceDefinition#requirementId()} / {@link SubraceDefinition#requirementId()}
	 * actually gets checked - those records stay Minecraft-free on purpose (see CharacterDraft's
	 * class doc), so a live ServerPlayer, the faction store and the player's own history are only
	 * ever needed here.
	 */
	private static String requirementMessage(ServerPlayer player, String requirementId) {
		if (requirementId == null || requirementId.isEmpty())
			return null;
		return switch (requirementId) {
			case "vampire_lineage" -> hasVampireLineage(player) ? null
					: "No vampire bloodline of yours still stands to sire you into it - another of your characters needs to have belonged to one that's still there.";
			case "not_previously_evil" -> wasLastCharacterEvil(player)
					? "Your last life ran too far into the dark for this one to be called back." : null;
			default -> null;
		};
	}

	/** Every character id this player has, alive or dead, other than the one named. */
	private static List<String> otherCharacterIds(ServerPlayer player, String excludeId) {
		List<String> ids = new ArrayList<>(DualityDatabaseManager.getAliveCharacterIds(player));
		ids.addAll(DualityDatabaseManager.getDeadCharacterIds(player));
		ids.remove(excludeId);
		return ids;
	}

	private static boolean hasVampireLineage(ServerPlayer player) {
		if (!Factions.isReady())
			return false;
		for (String characterId : otherCharacterIds(player, "")) {
			FactionRecord faction = Factions.factionOf(characterId);
			if (faction != null && faction.kind() == FactionKind.VAMPIRE)
				return true;
		}
		return false;
	}

	/** False if they have no prior character - nothing to disqualify them on. */
	private static boolean wasLastCharacterEvil(ServerPlayer player) {
		String lastId = DualityDatabaseManager.getLastCharacterId(player);
		if (lastId == null || lastId.isEmpty())
			return false;
		JsonObject sheet = DualityDatabaseManager.getCharacterSheet(player, lastId);
		if (sheet == null || !sheet.has("stats"))
			return false;
		JsonObject stats = sheet.getAsJsonObject("stats");
		double score = stats.has("duality_score") ? stats.get("duality_score").getAsDouble() : 0.0;
		// Same -0.25-normalized band FactionRecord#alignment uses for EVIL, so "evil" means the same
		// thing for a character as it already does for a faction.
		return score <= FactionRecord.DUALITY_LIMIT * -0.25;
	}

	/** Names are checked for shape by the draft and for collisions here, since only the server
	 *  knows what else this player has living. */
	private static DraftResult setName(ServerPlayer player, CharacterDraft draft, String candidate) {
		DraftResult shaped = draft.setName(candidate);
		if (!shaped.ok()) {
			NAME_TAKEN.remove(player.getUUID());
			return shaped;
		}
		// The typed name stays on the draft even when it's taken (wiping it would fight the name box
		// mid-typing); it's just marked unusable until changed. See NAME_TAKEN.
		if (DualityDatabaseManager.isLivingCharacterName(player, draft.name())) {
			NAME_TAKEN.add(player.getUUID());
			return DraftResult.no("Someone is already living under that name.");
		}
		NAME_TAKEN.remove(player.getUUID());
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

	/**
	 * The "back" button. Undoes whatever the step being LEFT answered - not the one being entered -
	 * so returning to it later starts clean rather than resuming a choice the player already walked
	 * away from once. Refused on screen one, where there is no prior step to land on.
	 */
	private static DraftResult goBack(CharacterDraft draft, RaceDefinition race) {
		CreationStep current = draft.step();
		if (current == CreationStep.RACE)
			return DraftResult.no("Already at the start.");
		switch (current) {
			case SUBRACE -> draft.clearSubrace(race);
			case ABILITIES -> draft.clearAbilities();
			case SKILLS -> draft.resetSkills();
			default -> {
			}
		}
		draft.setStep(current.previous());
		return DraftResult.ok("Back to " + draft.step().title() + ".");
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
		if (DualityDatabaseManager.isLivingCharacterName(player, draft.name())) {
			NAME_TAKEN.add(player.getUUID());
			return CommitResult.no("Someone is already living under that name.");
		}
		List<String> missing = appearanceMissing(player);
		if (!missing.isEmpty())
			return CommitResult.no("Choose " + String.join(", ", missing) + " first.");
		if (!raceUnlocked(player, race))
			return CommitResult.no("You haven't earned the right to play that.");
		String raceRefusal = requirementMessage(player, race.requirementId());
		if (raceRefusal != null)
			return CommitResult.no(raceRefusal);
		if (subrace != null) {
			if (!subraceUnlocked(player, subrace))
				return CommitResult.no("You haven't discovered that lineage yet.");
			String subraceRefusal = requirementMessage(player, subrace.requirementId());
			if (subraceRefusal != null)
				return CommitResult.no(subraceRefusal);
		}
		// Re-checked here too: the debug override could have been switched off mid-draft.
		for (String abilityId : draft.abilityIds()) {
			if (!abilityUnlocked(player, AbilityCatalog.get(abilityId)))
				return CommitResult.no("You haven't unlocked " + abilityId + " yet.");
		}

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

		applyToPlayer(player, race, subrace, skills, abilities);
		// A new character starts with a full pool - which, for a vampire, is blood (see Mana#usesBlood).
		// Without this a new vampire would arrive starving and powerless.
		net.spidrotech.duality.mana.Mana.set(player, net.spidrotech.duality.mana.Mana.max(player));
		onCharacterCreated(player, subrace, characterId);
		SkinManager.get().onActiveCharacterChanged(player);
		// Name over their head, in chat, and in the tab list - and the face in tab, which follows
		// the appearance the line above just reloaded.
		CharacterDisplay.publish(player);

		DRAFTS.remove(player.getUUID());
		NAME_TAKEN.remove(player.getUUID());
		CharacterCreationNetwork.sendInactive(player);
		// Take the creator off screen - with the draft gone nothing will reopen it (see CreatorKeepOpen).
		player.closeContainer();
		DualityMod.LOGGER.info("[duality] {} created {} ({} {}) with {} power(s)", player.getScoreboardName(), draft.name(), race.id(),
				subrace == null ? "-" : subrace.id(), abilities.size());
		return new CommitResult(true, draft.name() + " it is.", characterId);
	}

	/**
	 * A skin is required and has no "None" option, so creation starts with one on: the first skin
	 * in the catalog this player may pick, at its own colour, as both the worn part and the base
	 * (see SkinNetwork#handleEquip for why both). Leaves an already-worn skin alone, and does nothing
	 * if no skin is available at all.
	 */
	private static void equipDefaultSkin(ServerPlayer player) {
		net.spidrotech.duality.skin.SkinLoadout loadout = SkinManager.get().loadoutOf(player.getUUID());
		for (net.spidrotech.duality.skin.SkinLoadout.Equipped equipped : loadout.parts()) {
			net.spidrotech.duality.skin.SkinPart worn = net.spidrotech.duality.skin.SkinPartCatalog.get(equipped.partId());
			if (worn != null && worn.target() == net.spidrotech.duality.skin.SkinPartTarget.SKIN_TONE)
				return;
		}
		for (net.spidrotech.duality.skin.SkinPart part : net.spidrotech.duality.skin.SkinPartCatalog.all()) {
			if (part.target() != net.spidrotech.duality.skin.SkinPartTarget.SKIN_TONE || !cosmeticAvailable(player, part))
				continue;
			List<Integer> tints = new ArrayList<>();
			for (int i = 0; i < part.tintableCount(); i++) {
				tints.add(-1);
			}
			SkinManager.get().setLoadout(player, loadout.with(new net.spidrotech.duality.skin.SkinLoadout.Equipped(part.id(), List.copyOf(tints))).withBase(part.id()));
			return;
		}
	}

	/** Required parts (skin, eyes, pants) this player isn't wearing yet - see AppearanceRequirements. */
	public static List<String> appearanceMissing(ServerPlayer player) {
		return AppearanceRequirements.missing(SkinManager.get().loadoutOf(player.getUUID()));
	}

	/** Chosen powers and the ones the lineage hands over, in a stable order and deduplicated. */
	public static List<String> startingAbilities(CharacterDraft draft, SubraceDefinition subrace) {
		Set<String> abilities = new LinkedHashSet<>();
		if (subrace != null)
			abilities.addAll(subrace.grantedAbilities());
		// draft.abilityIds() holds AbilityCatalog ids, not power strings - resolve each through the
		// catalog to what it actually grants. A chosen id with no catalog entry (stale, from before a
		// catalog edit) is dropped rather than granted as a raw internal key.
		for (String catalogId : draft.abilityIds()) {
			AbilityDefinition ability = AbilityCatalog.get(catalogId);
			if (ability != null)
				abilities.add(ability.abilityId());
		}
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
	 * creation, so a character's stat line survives a relog. That means the faction recheck below
	 * fires on every login/switch too, not only a genuine race change - harmless, since it's a
	 * no-op whenever membership is still valid; see Factions#onRaceChanged.
	 */
	public static void applyToPlayer(ServerPlayer player, RaceDefinition race, SubraceDefinition subrace, Map<SkillType, Integer> skills, List<String> abilities) {
		CharacterAttributes.apply(player, skills);
		// Replaced, not added to: the attachment lives on the PLAYER, but it has to describe the
		// CHARACTER, so switching from a Whitelighter to a Demon can't carry Orb across.
		HashSet<ResourceLocation> unlocked = new HashSet<>();
		if (abilities != null) {
			for (String ability : abilities) {
				ResourceLocation id = ability.indexOf(':') >= 0 ? ResourceLocation.tryParse(ability) : ResourceLocation.tryBuild("duality", ability);
				if (id != null)
					unlocked.add(id);
			}
		}
		player.setData(ModAttachments.UNLOCKED_ABILITIES, unlocked);
		applyEquippedAbilities(player, race, subrace, unlocked);
		// Toggles live on the player and survive a character change, so a vampire's leap/dash/deflect
		// and vampire mode would otherwise carry over to whoever the player is next.
		if (!net.spidrotech.duality.abilities.vampire.VampireRank.isVampire(player)) {
			if (net.spidrotech.duality.abilities.vampire.VampireMode.isActive(player))
				net.spidrotech.duality.abilities.vampire.VampireMode.setActive(player, false);
			net.spidrotech.duality.abilities.AbilityToggles.update(player, toggled -> net.spidrotech.duality.abilities.vampire.VampireMode.AUTO_ABILITIES.forEach(toggled::remove));
		}
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		if (characterId != null && !characterId.isEmpty())
			Factions.onRaceChanged(characterId);
	}

	/** Re-applies whatever the player's current character is. Login and character-switch path. */
	public static void applyActiveCharacter(ServerPlayer player) {
		String characterId = DualityDatabaseManager.getActiveCharacterId(player);
		JsonObject sheet = characterId.isEmpty() ? null : DualityDatabaseManager.getCharacterSheet(player, characterId);
		if (sheet == null) {
			// Nobody (yet): no stats and no powers, so a player back in the creator can't still cast
			// their last character's abilities.
			CharacterAttributes.clear(player);
			player.setData(ModAttachments.UNLOCKED_ABILITIES, new HashSet<>());
			setEquippedAbilities(player, "");
			return;
		}
		RaceDefinition race = RaceCatalog.get(raceIdOf(sheet));
		SubraceDefinition subrace = race == null ? null : race.subrace(subspeciesIdOf(sheet));
		List<String> abilities = new ArrayList<>();
		if (sheet.has(CharacterAttributes.SHEET_ABILITIES)) {
			JsonArray array = sheet.getAsJsonArray(CharacterAttributes.SHEET_ABILITIES);
			for (int i = 0; i < array.size(); i++) {
				abilities.add(array.get(i).getAsString());
			}
		}
		applyToPlayer(player, race, subrace, CharacterAttributes.readSkills(sheet), abilities);
		applyVampireRank(player, sheet);
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

	/** The lineage id on a character sheet, or "" - reads the first species profile. */
	public static String subspeciesIdOf(JsonObject sheet) {
		if (sheet == null || !sheet.has("species_profiles"))
			return "";
		JsonArray profiles = sheet.getAsJsonArray("species_profiles");
		if (profiles.isEmpty())
			return "";
		JsonObject first = profiles.get(0).getAsJsonObject();
		return first.has("subspecies") ? first.get("subspecies").getAsString() : "";
	}

	/**
	 * One-time effects a handful of lineages need at the moment of creation - siring into an
	 * existing bloodline, taking up a crown, or quietly rolling what's dormant in ordinary blood.
	 * Kept separate from {@link #writeSheet}/{@link #applyToPlayer}: none of this is "what the
	 * character has forever", it's "what happens once, right now".
	 */
	private static void onCharacterCreated(ServerPlayer player, SubraceDefinition subrace, String characterId) {
		if (subrace == null)
			return;
		switch (subrace.id()) {
			case "vampire" -> sireVampire(player, characterId);
			case "vampiric_queen" -> crownQueen(player);
			case "mundane" -> rollLatentBlood(player, characterId);
			default -> {
			}
		}
	}

	/** Joins the new vampire into whichever of the player's other characters' bloodlines made them
	 *  eligible in the first place - see requirementMessage's "vampire_lineage" case. That faction is
	 *  guaranteed to exist by the time this runs (commit re-checks the requirement right before
	 *  writing the character), so a miss here means nothing to sire into and is left alone rather
	 *  than treated as an error. */
	private static void sireVampire(ServerPlayer player, String characterId) {
		FactionRecord bloodline = null;
		for (String otherId : otherCharacterIds(player, characterId)) {
			FactionRecord faction = Factions.factionOf(otherId);
			if (faction != null && faction.kind() == FactionKind.VAMPIRE) {
				bloodline = faction;
				break;
			}
		}
		if (bloodline == null)
			return;
		Factions.addPlayerMember(bloodline, player, FactionRole.MEMBER, "");
		setVampireRank(player, VampireRank.FLEDGLING); // a playable vampire - Thralls are mindless (see Vampirism)
	}

	/** A Vampiric Queen starts ranked as one immediately - the crown isn't waiting on a faction to
	 *  back it up, the faction is what she's expected to go make now that she has it. */
	private static void crownQueen(ServerPlayer player) {
		setVampireRank(player, VampireRank.QUEEN);
		player.displayClientMessage(Component.literal("You are the first of your own line now. Found your court with /faction create <name> when you're "
				+ "ready - any vampire sired before you do won't be sired to you.").withStyle(ChatFormatting.GRAY), false);
	}

	/** A vampire character's rank (1-4), kept on their sheet. The VAMPIRE_RANK attribute lives on the
	 *  player and would otherwise follow them to their next character - applyActiveCharacter puts
	 *  the right one back from here. */
	public static final String SHEET_VAMPIRE_RANK = "vampire_rank";

	private static void setVampireRank(ServerPlayer player, VampireRank rank) {
		AttributeInstance attribute = player.getAttribute(DualityModAttributes.VAMPIRE_RANK);
		if (attribute != null)
			attribute.setBaseValue(rank.level());
		JsonObject sheet = CharacterProgress.activeSheet(player);
		if (sheet != null) {
			sheet.addProperty(SHEET_VAMPIRE_RANK, rank.level());
			CharacterProgress.saveActive(player, sheet);
		}
	}

	/** Puts the active character's vampire rank on the player: their saved one, or none for a
	 *  character who isn't a vampire. A vampire from before ranks were saved keeps whatever the
	 *  attribute already says. */
	private static void applyVampireRank(ServerPlayer player, JsonObject sheet) {
		AttributeInstance attribute = player.getAttribute(DualityModAttributes.VAMPIRE_RANK);
		if (attribute == null)
			return;
		if (sheet != null && sheet.has(SHEET_VAMPIRE_RANK))
			attribute.setBaseValue(sheet.get(SHEET_VAMPIRE_RANK).getAsInt());
		else if (!VampireRank.isVampire(player))
			attribute.setBaseValue(0);
		// A vampire someone plays is never below a Fledgling - a Thrall is mindless, and a turned
		// Thrall stops being played (see Vampirism). Also what keeps vampire mode (a Thrall+ power)
		// always usable by every playable vampire.
		if (VampireRank.isVampire(player) && attribute.getBaseValue() < VampireRank.FLEDGLING.level()) {
			attribute.setBaseValue(VampireRank.FLEDGLING.level());
			if (sheet != null) {
				sheet.addProperty(SHEET_VAMPIRE_RANK, VampireRank.FLEDGLING.level());
				CharacterProgress.saveActive(player, sheet);
			}
		}
		net.spidrotech.duality.abilities.vampire.VampireMode.refreshAccess(player);
		net.spidrotech.duality.abilities.vampire.VampireStatus.sync(player);
	}

	/** A Mundane's blood is secretly and silently rolled once, on the odd chance something in it
	 *  hasn't woken up yet. Stored on the sheet for a future system to read; nothing acts on it yet,
	 *  and the character themselves is never told - that's the point of it being latent. */
	private static void rollLatentBlood(ServerPlayer player, String characterId) {
		JsonObject sheet = DualityDatabaseManager.getCharacterSheet(player, characterId);
		if (sheet == null)
			return;
		double roll = RANDOM.nextDouble();
		String latent = roll < MUNDANE_WICCAN_CHANCE ? "wiccan" : roll < MUNDANE_WICCAN_CHANCE + MUNDANE_HEMOVOID_CHANCE ? "hemovoid" : "";
		if (latent.isEmpty())
			return;
		sheet.addProperty("latent_species", latent);
		DualityDatabaseManager.saveCharacterSheet(player, characterId, sheet);
	}

	/**
	 * Rewrites the EquippedAbilities string from scratch so it describes this character.
	 *
	 * <p>That MCreator player variable is what the ability radial reads. Each Ret* procedure asks
	 * {@code EquippedAbilities.contains("acid_spit")} and so on to decide whether a wedge shows. It's
	 * also how {@code VampireRank#isVampire} asks "is this player a vampire". So it holds:
	 * <ul>
	 * <li>every power the character owns, by id (a "duality:" id is written bare, since that's how
	 * the Ret* procedures spell them), then
	 * <li>the race's and lineage's markers, like "vampire" (see {@link SubraceDefinition#equippedTag()}).
	 * </ul>
	 * Comma-separated. It's replaced rather than edited, like UNLOCKED_ABILITIES, so a new or
	 * switched character never keeps the last one's powers or markers.
	 */
	private static void applyEquippedAbilities(ServerPlayer player, RaceDefinition race, SubraceDefinition subrace, java.util.Collection<ResourceLocation> abilities) {
		Set<String> entries = new LinkedHashSet<>();
		if (abilities != null) {
			for (ResourceLocation id : abilities) {
				entries.add("duality".equals(id.getNamespace()) ? id.getPath() : id.toString());
			}
		}
		if (race != null && !race.equippedTag().isEmpty())
			entries.add(race.equippedTag());
		if (subrace != null && !subrace.equippedTag().isEmpty())
			entries.add(subrace.equippedTag());
		// Every vampire can let the vampire out and hide it again, whatever else they were given.
		if (entries.contains("vampire"))
			entries.add("vampire_mode");
		setEquippedAbilities(player, String.join(",", entries));
	}

	private static void setEquippedAbilities(ServerPlayer player, String value) {
		DualityModVariables.PlayerVariables vars = player.getData(DualityModVariables.PLAYER_VARIABLES);
		vars.EquippedAbilities = value;
		vars.markSyncDirty();
	}
}
