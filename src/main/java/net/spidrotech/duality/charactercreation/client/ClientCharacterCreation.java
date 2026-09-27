package net.spidrotech.duality.charactercreation.client;

import net.spidrotech.duality.charactercreation.AbilityBracket;
import net.spidrotech.duality.charactercreation.AbilityCatalog;
import net.spidrotech.duality.charactercreation.AbilityDefinition;
import net.spidrotech.duality.charactercreation.CharacterCreationNetwork;
import net.spidrotech.duality.charactercreation.CharacterDisplay;
import net.spidrotech.duality.charactercreation.CharacterIdentity;
import net.spidrotech.duality.charactercreation.CharacterDraft;
import net.spidrotech.duality.charactercreation.CreationAction;
import net.spidrotech.duality.charactercreation.CreationStep;
import net.spidrotech.duality.charactercreation.DraftView;
import net.spidrotech.duality.charactercreation.RaceCatalog;
import net.spidrotech.duality.charactercreation.RaceDefinition;
import net.spidrotech.duality.charactercreation.SkillType;
import net.spidrotech.duality.charactercreation.SubraceDefinition;

import net.spidrotech.duality.client.gui.CharacterSelectorScreen;
import net.spidrotech.duality.client.gui.CharacterSelectorPage2Screen;
import net.spidrotech.duality.client.gui.CharacterSelectorPage3Screen;
import net.spidrotech.duality.client.gui.CharacterSelectorPage4Screen;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

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
	/** The race the codex should be describing - the last one clicked on screen one, locked or not.
	 *  See {@link #previewedRace()}; cleared whenever the creator isn't active so a fresh session
	 *  doesn't inherit the last one's browsing. */
	private static String previewedRaceId = "";
	/** Same idea as {@link #previewedRaceId}, for screen two's lineage list. See
	 *  {@link #previewedSubrace()}. */
	private static String previewedSubraceId = "";
	/** Same idea again, for screen three's power list. See {@link #previewedAbility()}. Unlike race
	 *  and subrace this has no "confirmed" fallback - screen three is multi-select, so there's no one
	 *  answer to fall back to before anything's been clicked. */
	private static String previewedAbilityId = "";
	/** Screen four's equivalent - the last skill whose arrow was clicked. See {@link #previewedSkill()}. */
	private static SkillType previewedSkill = null;

	private ClientCharacterCreation() {
	}

	/** Called by the network layer when the server sends a new picture. Bump your screen's
	 *  widgets off this if you cache anything. */
	public static void accept(DraftView incoming) {
		view = incoming == null ? DraftView.INACTIVE : incoming;
		if (!view.active()) {
			previewedRaceId = "";
			previewedSubraceId = "";
			previewedAbilityId = "";
			previewedSkill = null;
		}
		refreshOpenScreen();
	}

	/**
	 * Every one of the creator's five screens builds its widget list once, in its own {@code init()}
	 * - that's where a locked-behind-{@link RaceDefinition#requirementId()} race gets skipped or the
	 * "next" button gets left out (see ShowRightButtonProcedure/ShowLeftButtonProcedure). None of
	 * that is re-evaluated on its own once the screen is up, so a pick that changes what should be
	 * showing - a race chosen, a lineage undone by "back" - would otherwise sit there unreflected
	 * until the player closed and reopened the screen.
	 *
	 * <p>Re-running {@link Screen#init(Minecraft, int, int)} (not the bare {@code init()} override)
	 * forces that rebuild properly: the two-argument overload clears the old widgets first, so this
	 * can't leave stale buttons stacked underneath the new ones. Page five is left out on purpose: it
	 * reads everything live each frame (see AppearanceEditor), and a rebuild per keystroke of the name
	 * would knock focus out of its name box. Only touches the screen if it's
	 * actually one of the creator's five - a view update has no reason to arrive while some unrelated
	 * screen is open, but there's equally no reason to risk poking one if it somehow did.
	 */
	private static void refreshOpenScreen() {
		Minecraft minecraft = Minecraft.getInstance();
		Screen current = minecraft.screen;
		if (current instanceof CharacterSelectorScreen || current instanceof CharacterSelectorPage2Screen || current instanceof CharacterSelectorPage3Screen
				|| current instanceof CharacterSelectorPage4Screen) {
			current.init(minecraft, current.width, current.height);
		}
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

	/**
	 * Which race the codex should currently describe - the last one clicked on screen one, whether
	 * or not the server actually accepted it, falling back to the draft's real race before anything
	 * has been clicked this session. This is deliberately NOT {@link #race()}: a locked race refuses
	 * silently server-side (the draft's raceId never moves), so reading the draft alone would make a
	 * click on a locked row do nothing visible at all. Reading this instead lets a player browse
	 * what they haven't earned - see {@link CharacterCodex}.
	 */
	public static RaceDefinition previewedRace() {
		if (!previewedRaceId.isEmpty()) {
			RaceDefinition previewed = RaceCatalog.get(previewedRaceId);
			if (previewed != null)
				return previewed;
		}
		return race();
	}

	/** Whether the previewed race (see {@link #previewedRace()}) is one this player could actually
	 *  pick right now - what the codex's "you haven't earned this" line should gate on. */
	public static boolean canSelectPreviewed() {
		return canSelect(previewedRace());
	}

	public static SubraceDefinition subrace() {
		return view.subrace();
	}

	/**
	 * Which lineage the codex should currently describe on screen two - same idea as
	 * {@link #previewedRace()}, one step later: the last one clicked, whether or not it's actually
	 * this player's final answer, falling back to the draft's real lineage before anything's been
	 * clicked. Resolved against {@link #race()} (the CONFIRMED race, not the preview) since that's
	 * the list a subrace id has to be looked up in.
	 */
	public static SubraceDefinition previewedSubrace() {
		RaceDefinition confirmedRace = race();
		if (confirmedRace == null)
			return null;
		if (!previewedSubraceId.isEmpty()) {
			SubraceDefinition previewed = confirmedRace.subrace(previewedSubraceId);
			if (previewed != null)
				return previewed;
		}
		return subrace();
	}

	/** Lineages screen two should offer. Empty means this race has none and screen two can be
	 *  skipped. */
	public static List<SubraceDefinition> subraces() {
		RaceDefinition race = race();
		if (race == null)
			return List.of();
		// Only lineages this player has unlocked - the rest stay hidden until discovered.
		List<SubraceDefinition> unlocked = new ArrayList<>();
		for (SubraceDefinition subrace : race.subraces()) {
			if (view.isSubraceUnlocked(subrace.id()))
				unlocked.add(subrace);
		}
		return unlocked;
	}

	/** Whether this player has unlocked the race at all - as opposed to {@link #canSelect}, which
	 *  also needs its requirement met. */
	public static boolean isUnlocked(RaceDefinition race) {
		return race != null && view.isRaceUnlocked(race.id());
	}

	public static void selectRace(String raceId) {
		previewRace(raceId);
		send(CreationAction.SELECT_RACE, raceId, 0);
	}

	/**
	 * Marks which race the codex should describe next, without sending anything. Screen one's own
	 * buttons send their click straight through spis_ui_api's UIButtonElement rather than through
	 * {@link #send}, so {@link #selectRace} above - despite the class doc's example - never actually
	 * runs from a real click; call this directly from wherever that click already lives instead. See
	 * {@link #previewedRace()}.
	 */
	public static void previewRace(String raceId) {
		previewedRaceId = raceId == null ? "" : raceId;
	}

	public static void selectSubrace(String subraceId) {
		previewSubrace(subraceId);
		send(CreationAction.SELECT_SUBRACE, subraceId, 0);
	}

	/** The screen-two equivalent of {@link #previewRace} - see that method's doc for why this exists
	 *  separately from {@link #selectSubrace}. */
	public static void previewSubrace(String subraceId) {
		previewedSubraceId = subraceId == null ? "" : subraceId;
	}

	/** Whether the previewed lineage (see {@link #previewedSubrace()}) is one this player could
	 *  actually pick right now - the screen-two equivalent of {@link #canSelectPreviewed()}. */
	public static boolean canSelectPreviewedSubrace() {
		SubraceDefinition subrace = previewedSubrace();
		return subrace != null && view.canSelectSubrace(subrace.id());
	}

	// ----------------------------------------------------------------------------- screen three
	/**
	 * The powers on offer, given the chosen lineage's {@link AbilityBracket} - AbilityCatalog isn't
	 * network-synced (see its class doc: it's static data, identical on both sides already), so this
	 * reads it directly rather than going through the view. Powers the lineage hands over for free
	 * aren't in here - see {@link #grantedAbilities()} - and buying one costs points out of the same
	 * pool a stat point does, so there's no separate "picks remaining" any more.
	 */
	public static List<AbilityDefinition> abilityChoices() {
		SubraceDefinition subrace = subrace();
		if (subrace == null)
			return List.of();
		List<AbilityDefinition> pool = new ArrayList<>();
		for (AbilityDefinition ability : AbilityCatalog.selectableFor(AbilityBracket.forSubrace(subrace.id()))) {
			// Only powers this PLAYER has unlocked for creation - not ones any character owns.
			if (!subrace.grantedAbilities().contains(ability.abilityId()) && view.isAbilityUnlocked(ability.id()))
				pool.add(ability);
		}
		return pool;
	}

	/** Powers the lineage gives outright, as {@link AbilityDefinition}s where the catalog has an
	 *  entry to describe them - see {@link AbilityCatalog#byAbilityId}. Show them, don't let them be
	 *  clicked. A grant with no matching catalog entry is simply not shown here; it's still applied at
	 *  commit regardless, since granting never goes through the catalog. */
	public static List<AbilityDefinition> grantedAbilities() {
		List<AbilityDefinition> granted = new ArrayList<>();
		for (String abilityId : view.grantedAbilityIds()) {
			AbilityDefinition ability = AbilityCatalog.byAbilityId(abilityId);
			if (ability != null)
				granted.add(ability);
		}
		return granted;
	}

	public static List<String> chosenAbilities() {
		return view.abilityIds();
	}

	public static boolean hasChosen(String abilityCatalogId) {
		return view.hasChosen(abilityCatalogId);
	}

	public static void toggleAbility(String abilityCatalogId) {
		previewAbility(abilityCatalogId);
		send(CreationAction.TOGGLE_ABILITY, abilityCatalogId, 0);
	}

	/** Marks which power the codex should describe next - the screen-three equivalent of
	 *  {@link #previewRace}, called from wherever a power button's click already lives. */
	public static void previewAbility(String abilityCatalogId) {
		previewedAbilityId = abilityCatalogId == null ? "" : abilityCatalogId;
	}

	/** Which power the codex should currently describe on screen three - the last one clicked,
	 *  whether it ended up toggled on or off, or null if nothing's been clicked yet this session (no
	 *  "confirmed" fallback the way race/subrace have one - see {@link #previewedAbilityId}). */
	public static AbilityDefinition previewedAbility() {
		return previewedAbilityId.isEmpty() ? null : AbilityCatalog.get(previewedAbilityId);
	}

	// ------------------------------------------------------------------------------ screen four
	/** Points left to distribute. Leaving some is allowed - they carry onto the character and can
	 *  be spent later from the stat screen. */
	public static int pointsRemaining() {
		return view.pointsRemaining();
	}

	/** What the player actually has to spend: {@link CharacterDraft#STARTING_POINTS} less what the
	 *  race and lineage charge. Draw the bar against this, not against the raw pool. */
	public static int pointsBudget() {
		return view.pointsBudget();
	}

	/** What being this race and lineage cost. "Vampire - 4 points" on screen one comes from here. */
	public static int raceCost() {
		return view.raceCost();
	}

	/** The floor a skill sits at because of the race and lineage - the part the player didn't buy
	 *  and can't refund. Draw it differently from the bought part. */
	public static int baseSkill(SkillType type) {
		return view.baseSkill(type);
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

	/** Marks which skill the codex should describe - called from screen four's arrows, which send
	 *  their own click through UIButtonElement (see {@link #previewRace} for why that's separate). */
	public static void previewSkill(SkillType skill) {
		previewedSkill = skill;
	}

	/** The last skill whose arrow was clicked on screen four, or null before any has been. */
	public static SkillType previewedSkill() {
		return previewedSkill;
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

	/** Whether the draft is a vampire lineage - when screen five offers the vampire-look preview. */
	public static boolean isVampireDraft() {
		return net.spidrotech.duality.charactercreation.CharacterCreation.isVampireLineage(view.race(), view.subrace());
	}

	/** Whether the vampire-look preview is on - read off the synced skin, which is what shows it. */
	public static boolean isVampirePreviewOn() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && net.spidrotech.duality.skin.client.ClientSkinState.tempsOf(mc.player.getUUID()).stream()
				.anyMatch(mod -> mod.key().equals(net.spidrotech.duality.charactercreation.CharacterCreation.VAMPIRE_PREVIEW_KEY));
	}

	public static void toggleVampirePreview() {
		send(CreationAction.TOGGLE_VAMPIRE_PREVIEW, "", 0);
	}

	/** Whether screen five may offer this part - mirrors CharacterCreation#cosmeticAvailable. */
	public static boolean isCosmeticAvailable(net.spidrotech.duality.skin.SkinPart part) {
		return part != null && view.isCosmeticAvailable(part.id());
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

	/**
	 * Takes in who everyone is and makes the names already on screen catch up.
	 *
	 * <p>NeoForge caches a player's display name rather than recomputing it per frame, so an
	 * identity arriving for somebody already in the world has to knock that cache over or their
	 * old name stays above their head until they're reloaded.
	 */
	public static void acceptIdentities(List<CharacterIdentity> identities) {
		Minecraft minecraft = Minecraft.getInstance();
		for (CharacterIdentity identity : identities) {
			CharacterDisplay.accept(identity);
			if (minecraft.level == null)
				continue;
			Player player = minecraft.level.getPlayerByUUID(identity.playerId());
			if (player != null)
				CharacterDisplay.refresh(player);
		}
	}

	/** The escape hatch, if you need an action this class doesn't wrap. */
	public static void send(CreationAction action, String arg, int amount) {
		if (action == null)
			return;
		PacketDistributor.sendToServer(new CharacterCreationNetwork.CreationActionPayload(action, arg == null ? "" : arg, amount));
	}
}
