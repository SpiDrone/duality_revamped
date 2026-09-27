package net.spidrotech.duality.charactercreation;

import java.util.ArrayList;
import java.util.List;

/**
 * The read-only picture of a draft that the screens draw from.
 *
 * <p>Everything a screen needs is precomputed here - remaining points, remaining picks, which steps
 * are ticked off, whether the name will be accepted - so a button's enabled state is a field read
 * rather than a rule the screen has to reimplement and keep in step with the server's copy.
 *
 * <p>The client gets one of these whenever anything changes and holds nothing else. It is a
 * picture, not the truth: acting on a stale one is safe, because the server re-checks.
 */
public record DraftView(boolean active, CreationStep step, String raceId, String subraceId, List<String> abilityIds, List<String> grantedAbilityIds,
		List<Integer> skillValues, List<Integer> allocatedPoints, int pointsRemaining, int pointsBudget, int raceCost, String name, boolean nameUsable,
		List<CreationStep> completedSteps, boolean complete, List<String> selectableRaceIds, List<String> selectableSubraceIds, List<String> unlockedRaceIds,
		List<String> unlockedSubraceIds, List<String> unlockedAbilityIds, List<String> availableCosmeticIds, String statusMessage) {

	/** What the client holds when the player isn't in the creator. */
	public static final DraftView INACTIVE = new DraftView(false, CreationStep.RACE, "", "", List.of(), List.of(), zeroes(), zeroes(), CharacterDraft.STARTING_POINTS,
			CharacterDraft.STARTING_POINTS, 0, "", false, List.of(), false, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "");

	public DraftView {
		abilityIds = List.copyOf(abilityIds);
		grantedAbilityIds = List.copyOf(grantedAbilityIds);
		skillValues = List.copyOf(skillValues);
		allocatedPoints = List.copyOf(allocatedPoints);
		completedSteps = List.copyOf(completedSteps);
		selectableRaceIds = List.copyOf(selectableRaceIds);
		selectableSubraceIds = List.copyOf(selectableSubraceIds);
		unlockedRaceIds = List.copyOf(unlockedRaceIds);
		unlockedSubraceIds = List.copyOf(unlockedSubraceIds);
		unlockedAbilityIds = List.copyOf(unlockedAbilityIds);
		availableCosmeticIds = List.copyOf(availableCosmeticIds);
	}

	/**
	 * Snapshots a live draft. The only place a view is made on the server.
	 *
	 * @param selectableRaceIds    which races this player may actually pick right now - unlocked AND
	 *                             meeting any requirement. The client gets the whole catalog so screen
	 *                             one can show locked races rather than hiding them.
	 * @param selectableSubraceIds the same idea for the CURRENT race's lineages - unambiguous despite
	 *                             carrying only bare ids, since a view is always scoped to one race.
	 * @param unlockedRaceIds      races this PLAYER has unlocked, requirement met or not - what tells
	 *                             "not discovered yet" apart from "discovered, conditions not met".
	 * @param unlockedSubraceIds   the current race's lineages this player has unlocked - the only ones
	 *                             screen two shows at all.
	 * @param unlockedAbilityIds   AbilityCatalog ids this player may buy at creation - the only ones
	 *                             screen three offers. Not the same as what any character owns.
	 * @param nameTaken            a living character (anyone's) already has the draft's name - makes it unusable
	 * @param appearanceReady      the required parts are worn (see AppearanceRequirements) - part of {@link #complete()}
	 * @param availableCosmeticIds SkinPart ids screen five may offer - free parts plus those whose
	 *                             unlock is on this player's profile (see CharacterCreation#cosmeticAvailable).
	 */
	public static DraftView of(CharacterDraft draft, RaceDefinition race, SubraceDefinition subrace, boolean nameTaken, boolean appearanceReady, List<String> selectableRaceIds,
			List<String> selectableSubraceIds, List<String> unlockedRaceIds, List<String> unlockedSubraceIds, List<String> unlockedAbilityIds,
			List<String> availableCosmeticIds, String statusMessage) {
		List<Integer> values = new ArrayList<>();
		List<Integer> allocated = new ArrayList<>();
		for (SkillType skill : SkillType.values()) {
			values.add(draft.skillValue(skill, race, subrace));
			allocated.add(draft.allocatedTo(skill));
		}
		return new DraftView(true, draft.step(), draft.raceId(), draft.subraceId(), List.copyOf(draft.abilityIds()),
				subrace == null ? List.of() : subrace.grantedAbilities(), values, allocated, draft.pointsRemaining(race, subrace), draft.pointsBudget(race, subrace),
				draft.raceCost(race, subrace), draft.name(), CharacterDraft.isNameUsable(draft.name()) && !nameTaken, draft.completedSteps(race, subrace),
				draft.isComplete(race, subrace) && !nameTaken && appearanceReady, selectableRaceIds, selectableSubraceIds, unlockedRaceIds, unlockedSubraceIds, unlockedAbilityIds, availableCosmeticIds, statusMessage);
	}

	/** The race this draft is on, resolved against whichever catalog copy is local. */
	public RaceDefinition race() {
		return RaceCatalog.get(raceId);
	}

	public SubraceDefinition subrace() {
		return RaceCatalog.subrace(raceId, subraceId);
	}

	/** Total value of a skill - base from race and lineage, plus points spent. */
	public int skill(SkillType type) {
		return skillValues.get(type.ordinal());
	}

	/** Points the player has put into this skill, which is what a "refund" arrow should gate on. */
	public int allocated(SkillType type) {
		return allocatedPoints.get(type.ordinal());
	}

	/**
	 * The floor: what being this race and lineage put into the skill for free.
	 *
	 * <p>The player cannot refund below this, so a screen showing "5" made of a granted 3 and a
	 * bought 2 should draw the granted part differently and grey the minus arrow once
	 * {@link #allocated} hits zero.
	 */
	public int baseSkill(SkillType type) {
		return skill(type) - allocated(type);
	}

	public boolean isStepComplete(CreationStep candidate) {
		return completedSteps.contains(candidate);
	}

	/** Whether the "next" button on a given screen should be live. */
	public boolean canAdvanceFrom(CreationStep candidate) {
		return isStepComplete(candidate);
	}

	/** Whether screen one should let this race be clicked, as opposed to shown and locked. */
	public boolean canSelectRace(String candidateRaceId) {
		return selectableRaceIds.contains(candidateRaceId);
	}

	/** Whether screen two should let this lineage of the CURRENT race be clicked - see
	 *  {@link #of}'s note on why the id alone is enough to ask this. */
	public boolean canSelectSubrace(String candidateSubraceId) {
		return selectableSubraceIds.contains(candidateSubraceId);
	}

	public boolean isRaceUnlocked(String candidateRaceId) {
		return unlockedRaceIds.contains(candidateRaceId);
	}

	public boolean isSubraceUnlocked(String candidateSubraceId) {
		return unlockedSubraceIds.contains(candidateSubraceId);
	}

	/** candidateAbilityId is an AbilityCatalog entry's id. */
	public boolean isAbilityUnlocked(String candidateAbilityId) {
		return unlockedAbilityIds.contains(candidateAbilityId);
	}

	/** Whether screen five may offer this SkinPart id. */
	public boolean isCosmeticAvailable(String partId) {
		return availableCosmeticIds.contains(partId);
	}

	/** Every race in the catalog, with the ones this player can't pick still in the list - use
	 *  {@link #canSelectRace} to decide how to draw each. */
	public List<RaceDefinition> allRaces() {
		return List.copyOf(RaceCatalog.all());
	}

	/** abilityId here is an {@link AbilityCatalog} entry's own id, not the power string it grants -
	 *  see {@link AbilityDefinition#id()} vs {@link AbilityDefinition#abilityId()}. */
	public boolean hasChosen(String abilityId) {
		return abilityIds.contains(abilityId);
	}

	/** Chosen powers and the ones the lineage hands over, together, as the actual power strings the
	 *  character will start with (not catalog ids) - what a summary panel should list. Resolves each
	 *  chosen catalog id through {@link AbilityCatalog}; one with no catalog entry (a stale id from
	 *  before a catalog edit) is skipped rather than shown as a raw internal key. */
	public List<String> allAbilities() {
		List<String> combined = new ArrayList<>(grantedAbilityIds);
		for (String chosenId : abilityIds) {
			AbilityDefinition ability = AbilityCatalog.get(chosenId);
			if (ability != null && !combined.contains(ability.abilityId()))
				combined.add(ability.abilityId());
		}
		return List.copyOf(combined);
	}

	private static List<Integer> zeroes() {
		Integer[] out = new Integer[SkillType.values().length];
		for (int i = 0; i < out.length; i++) {
			out[i] = SkillType.MIN;
		}
		return List.of(out);
	}
}
