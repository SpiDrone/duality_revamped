package net.spidrotech.duality.charactercreation.client;

import net.spidrotech.duality.charactercreation.CharacterCreationNetwork;
import net.spidrotech.duality.charactercreation.CharacterDraft;
import net.spidrotech.duality.charactercreation.CreationAction;
import net.spidrotech.duality.charactercreation.CreationStep;
import net.spidrotech.duality.charactercreation.DraftView;
import net.spidrotech.duality.charactercreation.RaceCatalog;
import net.spidrotech.duality.charactercreation.RaceDefinition;
import net.spidrotech.duality.charactercreation.SkillType;
import net.spidrotech.duality.charactercreation.SubraceDefinition;

import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>This is the class your screens talk to.</b>
 *
 * <p>Everything a screen needs to draw is a getter here, and everything a button does is a one-line
 * call. Nothing in a screen should build a packet, check a rule, or track state of its own: read
 * {@link #view()} to draw, call a method to act, and redraw when the next view arrives.
 *
 * <pre>
 *   // screen one
 *   for (RaceDefinition race : ClientCharacterCreation.allRaces()) {
 *       boolean locked = !ClientCharacterCreation.canSelect(race);
 *       // ...draw a button; on press:
 *       ClientCharacterCreation.selectRace(race.id());
 *   }
 *
 *   // screen four
 *   int left = ClientCharacterCreation.pointsRemaining();
 *   int value = ClientCharacterCreation.skill(SkillType.STRENGTH);
 *   ClientCharacterCreation.allocate(SkillType.STRENGTH, +1);
 *
 *   // screen five
 *   ClientCharacterCreation.setName(nameField.getValue());
 *   if (ClientCharacterCreation.canCommit())
 *       ClientCharacterCreation.commit();
 * </pre>
 *
 * <p>The skin editor on screen five isn't here on purpose - it already works through SkinNetwork's
 * existing equip packet, and what the player builds during creation is saved onto the new character
 * when they commit.
 *
 * <p>Nothing here is authoritative. Calling {@link #allocate} doesn't change the number
 * {@link #skill} returns - it asks the server, which answers with a new view a tick later. Draw
 * from the view and the screen can never disagree with the character that actually gets made.
 */
public final class ClientCharacterCreation {
	private static DraftView view = DraftView.INACTIVE;

	private ClientCharacterCreation() {
	}

	/** Called by the network layer when the server sends a new picture. Bump your screen's
	 *  widgets off this if you cache anything. */
	public static void accept(DraftView incoming) {
		view = incoming == null ? DraftView.INACTIVE : incoming;
	}

	/** The current picture. Never null. */
	public static DraftView view() {
		return view;
	}

	/** Whether the player is in the creator at all - the cue to open (or close) your screen. */
	public static boolean isActive() {
		return view.active();
	}

	/** Which of the five screens the player is on. */
	public static CreationStep step() {
		return view.step();
	}

	/** The last thing the server had to say - a refusal, a confirmation. Worth showing somewhere. */
	public static String statusMessage() {
		return view.statusMessage();
	}

	// ------------------------------------------------------------------------ screens one & two
	/** Every race, including ones this player hasn't earned - draw those locked rather than
	 *  hiding them. */
	public static List<RaceDefinition> allRaces() {
		return new ArrayList<>(RaceCatalog.all());
	}

	/** Only the races this player may pick, if you'd rather not draw locked ones at all. */
	public static List<RaceDefinition> selectableRaces() {
		List<RaceDefinition> races = new ArrayList<>();
		for (RaceDefinition race : RaceCatalog.all()) {
			if (view.canSelectRace(race.id()))
				races.add(race);
		}
		return races;
	}

	public static boolean canSelect(RaceDefinition race) {
		return race != null && view.canSelectRace(race.id());
	}

	/** The chosen race, or null on screen one before anything is picked. */
	public static RaceDefinition race() {
		return view.race();
	}

	public static SubraceDefinition subrace() {
		return view.subrace();
	}

	/** Lineages screen two should offer. Empty means this race has none and screen two can be
	 *  skipped. */
	public static List<SubraceDefinition> subraces() {
		RaceDefinition race = race();
		return race == null ? List.of() : race.subraces();
	}

	public static void selectRace(String raceId) {
		send(CreationAction.SELECT_RACE, raceId, 0);
	}

	public static void selectSubrace(String subraceId) {
		send(CreationAction.SELECT_SUBRACE, subraceId, 0);
	}

	// ----------------------------------------------------------------------------- screen three
	/** The powers on offer, given the chosen race and lineage. Powers the lineage hands over for
	 *  free aren't in here - see {@link #grantedAbilities()}. */
	public static List<String> abilityChoices() {
		RaceDefinition race = race();
		return race == null ? List.of() : race.selectableAbilities(subrace());
	}

	/** Powers the lineage gives outright. Show them, don't let them be clicked. */
	public static List<String> grantedAbilities() {
		return view.grantedAbilityIds();
	}

	public static List<String> chosenAbilities() {
		return view.abilityIds();
	}

	public static boolean hasChosen(String abilityId) {
		return view.hasChosen(abilityId);
	}

	public static int abilityPicksRemaining() {
		return view.abilityPicksRemaining();
	}

	public static void toggleAbility(String abilityId) {
		send(CreationAction.TOGGLE_ABILITY, abilityId, 0);
	}

	// ------------------------------------------------------------------------------ screen four
	/** Points left of {@link CharacterDraft#STARTING_POINTS}. Leaving some is allowed - they carry
	 *  onto the character and can be spent later from the stat screen. */
	public static int pointsRemaining() {
		return view.pointsRemaining();
	}

	/** A skill's total: where the race and lineage put it, plus what's been spent. */
	public static int skill(SkillType type) {
		return view.skill(type);
	}

	/** How much of that total the player paid for - gate your minus arrow on this being above 0. */
	public static int allocated(SkillType type) {
		return view.allocated(type);
	}

	public static boolean canRaise(SkillType type) {
		return pointsRemaining() > 0 && skill(type) < SkillType.MAX;
	}

	public static boolean canLower(SkillType type) {
		return allocated(type) > 0;
	}

	public static void allocate(SkillType type, int delta) {
		send(CreationAction.ALLOCATE_SKILL, type == null ? "" : type.id(), delta);
	}

	public static void resetSkills() {
		send(CreationAction.RESET_SKILLS, "", 0);
	}

	// ------------------------------------------------------------------------------ screen five
	public static String name() {
		return view.name();
	}

	/** Whether the typed name will be accepted. Note it can still be refused on send for colliding
	 *  with another of this player's living characters - only the server knows those. */
	public static boolean isNameUsable() {
		return view.nameUsable();
	}

	public static void setName(String candidate) {
		send(CreationAction.SET_NAME, candidate, 0);
	}

	// ----------------------------------------------------------------------------- flow & commit
	/** Whether the "next" button should be live on the screen the player is on. */
	public static boolean canAdvance() {
		return view.canAdvanceFrom(view.step());
	}

	public static boolean isStepComplete(CreationStep candidate) {
		return view.isStepComplete(candidate);
	}

	/** Every question answered - the "done" button's enabled state. */
	public static boolean canCommit() {
		return view.complete();
	}

	public static void next() {
		send(CreationAction.GOTO_STEP, "", 0);
	}

	/** Jump to a screen. Going back is always allowed; jumping forward past an unanswered question
	 *  is refused. */
	public static void goTo(CreationStep target) {
		send(CreationAction.GOTO_STEP, target == null ? "" : target.name(), 0);
	}

	/** Build the character. The server answers with an inactive view, which is your cue to close. */
	public static void commit() {
		send(CreationAction.COMMIT, "", 0);
	}

	public static void restart() {
		send(CreationAction.RESTART, "", 0);
	}

	/** The escape hatch, if you need an action this class doesn't wrap. */
	public static void send(CreationAction action, String arg, int amount) {
		if (action == null)
			return;
		PacketDistributor.sendToServer(new CharacterCreationNetwork.CreationActionPayload(action, arg == null ? "" : arg, amount));
	}
}
