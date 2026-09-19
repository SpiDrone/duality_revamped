package net.spidrotech.duality.charactercreation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A character being made: everything the five screens have answered so far, and the rules about
 * what may be answered next.
 *
 * <p>One of these lives on the server per player, for as long as they're in the creator. The client
 * never holds an authoritative copy - it gets a {@link DraftView} to draw from, sends intents back,
 * and every one of them is re-checked here. That ordering is the whole point: a screen can be
 * wrong, or replaced, or lying, and the worst that happens is a refused action.
 *
 * <p>Nothing in this file imports Minecraft, so the point budget, the pick limits and the name
 * rules can be exercised without a game running - see {@code tools/creation_sim}.
 */
public class CharacterDraft {
	/** Points the player gets to spend on screen four. Whatever's left over is carried onto the
	 *  character sheet and spent later from the stat screen, so leaving some unspent is a real
	 *  choice rather than a mistake to block on. */
	public static final int STARTING_POINTS = 5;
	/** Longest name a character may have. */
	public static final int NAME_MAX = 24;

	/** What came of an attempted change. The message is meant to be shown to the player. */
	public record DraftResult(boolean ok, String message) {
		public static final DraftResult OK = new DraftResult(true, "");

		public static DraftResult ok(String message) {
			return new DraftResult(true, message);
		}

		public static DraftResult no(String message) {
			return new DraftResult(false, message);
		}
	}

	private String raceId = "";
	private String subraceId = "";
	private final Set<String> abilityIds = new LinkedHashSet<>();
	/** Points spent ON TOP of the starting value, per skill. Never negative. */
	private final Map<SkillType, Integer> allocated = new EnumMap<>(SkillType.class);
	private String name = "";
	private CreationStep step = CreationStep.RACE;

	public String raceId() {
		return raceId;
	}

	public String subraceId() {
		return subraceId;
	}

	public Set<String> abilityIds() {
		return abilityIds;
	}

	public String name() {
		return name;
	}

	public CreationStep step() {
		return step;
	}

	public void setStep(CreationStep step) {
		this.step = step;
	}

	// ------------------------------------------------------------------------------ screen one
	/** Choosing a race resets everything downstream of it - the old lineage, powers and point
	 *  spread belonged to a different character. Better to clear them than to quietly keep a
	 *  vampire's picks on a witch. */
	public DraftResult selectRace(RaceDefinition race) {
		if (race == null)
			return DraftResult.no("No such race.");
		if (race.id().equals(raceId))
			return DraftResult.OK;
		raceId = race.id();
		subraceId = "";
		abilityIds.clear();
		allocated.clear();
		return DraftResult.ok("Race set to " + race.displayName() + ".");
	}

	// ------------------------------------------------------------------------------ screen two
	public DraftResult selectSubrace(RaceDefinition race, SubraceDefinition subrace) {
		if (race == null)
			return DraftResult.no("Choose a race first.");
		if (subrace == null)
			return DraftResult.no("No such lineage for " + race.displayName() + ".");
		if (subrace.id().equals(subraceId))
			return DraftResult.OK;
		subraceId = subrace.id();
		// The pool just changed shape; drop anything that isn't in the new one rather than
		// carrying a pick the new lineage doesn't offer.
		abilityIds.retainAll(race.selectableAbilities(subrace));
		// And the stat line moved under the allocation, so re-clamp it.
		clampAllocations(race, subrace);
		return DraftResult.ok("Lineage set to " + subrace.displayName() + ".");
	}

	// ---------------------------------------------------------------------------- screen three
	/** How many powers this race and lineage let a character start with. */
	public int abilityPickLimit(RaceDefinition race, SubraceDefinition subrace) {
		return race == null ? 0 : race.abilityPicks();
	}

	public int abilityPicksRemaining(RaceDefinition race, SubraceDefinition subrace) {
		return Math.max(0, abilityPickLimit(race, subrace) - abilityIds.size());
	}

	/** Adds the power if it's on offer and there's room, removes it if it's already taken. */
	public DraftResult toggleAbility(String abilityId, RaceDefinition race, SubraceDefinition subrace) {
		if (race == null)
			return DraftResult.no("Choose a race first.");
		if (abilityId == null || abilityId.isBlank())
			return DraftResult.no("No power named.");
		if (abilityIds.remove(abilityId))
			return DraftResult.ok("Dropped " + abilityId + ".");
		if (!race.selectableAbilities(subrace).contains(abilityId))
			return DraftResult.no("A " + race.displayName() + " can't start with that.");
		if (abilityIds.size() >= abilityPickLimit(race, subrace))
			return DraftResult.no("That's all " + abilityPickLimit(race, subrace) + " of your picks. Drop one first.");
		abilityIds.add(abilityId);
		return DraftResult.ok("Took " + abilityId + ".");
	}

	// ----------------------------------------------------------------------------- screen four
	public int pointsSpent() {
		int total = 0;
		for (int spent : allocated.values()) {
			total += spent;
		}
		return total;
	}

	public int pointsRemaining() {
		return STARTING_POINTS - pointsSpent();
	}

	public int allocatedTo(SkillType skill) {
		return allocated.getOrDefault(skill, 0);
	}

	/** The number screen four should show: where the race and lineage put this skill, plus what
	 *  the player has spent on it. */
	public int skillValue(SkillType skill, RaceDefinition race, SubraceDefinition subrace) {
		int base = race == null ? SkillType.MIN : race.startingSkills(subrace).get(skill);
		return base + allocatedTo(skill);
	}

	public Map<SkillType, Integer> skillValues(RaceDefinition race, SubraceDefinition subrace) {
		Map<SkillType, Integer> values = new EnumMap<>(SkillType.class);
		for (SkillType skill : SkillType.values()) {
			values.put(skill, skillValue(skill, race, subrace));
		}
		return values;
	}

	/** Spends or refunds points. delta is normally +1 or -1; anything bigger is applied whole or
	 *  refused, so a screen can't half-apply a drag. */
	public DraftResult allocate(SkillType skill, int delta, RaceDefinition race, SubraceDefinition subrace) {
		if (skill == null)
			return DraftResult.no("No such skill.");
		if (race == null)
			return DraftResult.no("Choose a race first.");
		if (delta == 0)
			return DraftResult.OK;
		int spent = allocatedTo(skill);
		if (spent + delta < 0)
			return DraftResult.no(skill.displayName() + " is already back to where it started.");
		if (delta > pointsRemaining())
			return DraftResult.no("Only " + pointsRemaining() + " point(s) left.");
		if (skillValue(skill, race, subrace) + delta > SkillType.MAX)
			return DraftResult.no(skill.displayName() + " caps at " + SkillType.MAX + " during creation.");
		setAllocated(skill, spent + delta);
		return DraftResult.OK;
	}

	/** Hands every spent point back. The "reset" button on screen four. */
	public DraftResult resetSkills() {
		allocated.clear();
		return DraftResult.ok("Points refunded.");
	}

	// ----------------------------------------------------------------------------- screen five
	public DraftResult setName(String candidate) {
		String trimmed = candidate == null ? "" : candidate.trim();
		if (!isNameUsable(trimmed))
			return DraftResult.no("Names are 1 to " + NAME_MAX + " characters, letters, digits, spaces, apostrophes and hyphens.");
		name = trimmed;
		return DraftResult.OK;
	}

	/**
	 * Whether a name is one a character may have. Kept deliberately permissive about what letters
	 * count so non-English names work, and deliberately strict about the rest so a name can't be
	 * used to smuggle formatting codes into somebody else's chat.
	 */
	public static boolean isNameUsable(String candidate) {
		if (candidate == null)
			return false;
		String trimmed = candidate.trim();
		if (trimmed.isEmpty() || trimmed.length() > NAME_MAX)
			return false;
		boolean hasLetter = false;
		for (char c : trimmed.toCharArray()) {
			if (Character.isLetter(c)) {
				hasLetter = true;
				continue;
			}
			if (!Character.isDigit(c) && c != ' ' && c != '\'' && c != '-')
				return false;
		}
		return hasLetter;
	}

	// ---------------------------------------------------------------------------------- status
	/** Every step whose question has been answered - what a screen would tick off in a stepper. */
	public List<CreationStep> completedSteps(RaceDefinition race, SubraceDefinition subrace) {
		List<CreationStep> done = new ArrayList<>();
		for (CreationStep candidate : CreationStep.values()) {
			if (candidate != CreationStep.READY && candidate.isSatisfiedBy(this, race, subrace))
				done.add(candidate);
		}
		return done;
	}

	/** Whether the draft can be committed. Every step must be satisfied, not just the current one. */
	public boolean isComplete(RaceDefinition race, SubraceDefinition subrace) {
		for (CreationStep candidate : CreationStep.values()) {
			if (candidate != CreationStep.READY && !candidate.isSatisfiedBy(this, race, subrace))
				return false;
		}
		return true;
	}

	/** The first unanswered step, or READY. What a "resume where I left off" button jumps to. */
	public CreationStep firstIncompleteStep(RaceDefinition race, SubraceDefinition subrace) {
		for (CreationStep candidate : CreationStep.values()) {
			if (candidate != CreationStep.READY && !candidate.isSatisfiedBy(this, race, subrace))
				return candidate;
		}
		return CreationStep.READY;
	}

	// --------------------------------------------------------------------------------- helpers
	private void setAllocated(SkillType skill, int value) {
		if (value <= 0)
			allocated.remove(skill);
		else
			allocated.put(skill, value);
	}

	/** Refunds any allocation a changed lineage has pushed over the cap, rather than silently
	 *  carrying an illegal value into the commit. */
	private void clampAllocations(RaceDefinition race, SubraceDefinition subrace) {
		for (SkillType skill : SkillType.values()) {
			int base = race.startingSkills(subrace).get(skill);
			int allowed = Math.max(0, SkillType.MAX - base);
			if (allocatedTo(skill) > allowed)
				setAllocated(skill, allowed);
		}
	}
}
