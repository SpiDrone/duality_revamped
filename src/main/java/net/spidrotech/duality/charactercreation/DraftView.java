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
		List<Integer> skillValues, List<Integer> allocatedPoints, int pointsRemaining, int pointsBudget, int raceCost, int abilityPicksRemaining, String name,
		boolean nameUsable, List<CreationStep> completedSteps, boolean complete, List<String> selectableRaceIds, String statusMessage) {

	/** What the client holds when the player isn't in the creator. */
	public static final DraftView INACTIVE = new DraftView(false, CreationStep.RACE, "", "", List.of(), List.of(), zeroes(), zeroes(), CharacterDraft.STARTING_POINTS,
			CharacterDraft.STARTING_POINTS, 0, 0, "", false, List.of(), false, List.of(), "");

	public DraftView {
		abilityIds = List.copyOf(abilityIds);
		grantedAbilityIds = List.copyOf(grantedAbilityIds);
		skillValues = List.copyOf(skillValues);
		allocatedPoints = List.copyOf(allocatedPoints);
		completedSteps = List.copyOf(completedSteps);
		selectableRaceIds = List.copyOf(selectableRaceIds);
	}

	/**
	 * Snapshots a live draft. The only place a view is made on the server.
	 *
	 * @param selectableRaceIds which races this player may actually pick. The client gets the whole
	 *                          catalog so screen one can show locked races greyed out rather than
	 *                          hiding them - knowing what you haven't earned is half the draw.
	 */
	public static DraftView of(CharacterDraft draft, RaceDefinition race, SubraceDefinition subrace, List<String> selectableRaceIds, String statusMessage) {
		List<Integer> values = new ArrayList<>();
		List<Integer> allocated = new ArrayList<>();
		for (SkillType skill : SkillType.values()) {
			values.add(draft.skillValue(skill, race, subrace));
			allocated.add(draft.allocatedTo(skill));
		}
		return new DraftView(true, draft.step(), draft.raceId(), draft.subraceId(), List.copyOf(draft.abilityIds()),
				subrace == null ? List.of() : subrace.grantedAbilities(), values, allocated, draft.pointsRemaining(race, subrace), draft.pointsBudget(race, subrace),
				draft.raceCost(race, subrace), draft.abilityPicksRemaining(race, subrace), draft.name(), CharacterDraft.isNameUsable(draft.name()),
				draft.completedSteps(race, subrace), draft.isComplete(race, subrace), selectableRaceIds, statusMessage);
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

	/** Every race in the catalog, with the ones this player can't pick still in the list - use
	 *  {@link #canSelectRace} to decide how to draw each. */
	public List<RaceDefinition> allRaces() {
		return List.copyOf(RaceCatalog.all());
	}

	public boolean hasChosen(String abilityId) {
		return abilityIds.contains(abilityId);
	}

	/** Chosen powers and the ones the lineage hands over, together - what the character will
	 *  actually start with, and what a summary panel should list. */
	public List<String> allAbilities() {
		List<String> combined = new ArrayList<>(grantedAbilityIds);
		for (String chosen : abilityIds) {
			if (!combined.contains(chosen))
				combined.add(chosen);
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
