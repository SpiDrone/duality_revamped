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
	/**
	 * The whole pool a character is built from. A race's {@link RaceDefinition#pointCost()} comes
	 * out of this before the player spends anything, so picking something powerful leaves less to
	 * distribute - see {@link #pointsBudget}.
	 *
	 * <p>Whatever's left at the end isn't lost: it's carried onto the character sheet and spent
	 * later from the stat screen, so walking away with points in hand is a real choice rather than
	 * a mistake to block on.
	 */
	public static final int STARTING_POINTS = 10;
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

	/**
	 * Clears the race choice entirely - used when a click lands on a race this player can't have, so
	 * the last VALID pick doesn't linger as the answer just because it was the most recent one to
	 * actually go through. Without this, clicking a locked race left the draft on whatever race was
	 * already chosen, which let "next" stay live and the creator move on as if the locked click had
	 * never happened.
	 */
	public DraftResult clearRace() {
		if (raceId.isEmpty())
			return DraftResult.OK;
		raceId = "";
		subraceId = "";
		abilityIds.clear();
		allocated.clear();
		return DraftResult.ok("Race unset.");
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
		// The bracket just changed; drop anything the new lineage's own choices() list (see below)
		// doesn't offer rather than carrying a pick that belonged to the old one.
		retainOnlyValidAbilities(race, subrace);
		// And the stat line moved under the allocation, so re-clamp it.
		clampAllocations(race, subrace);
		// A dearer lineage can shrink the budget out from under what's already spent; refund the
		// difference rather than leaving the draft overspent.
		trimToBudget(race, subrace);
		return DraftResult.ok("Lineage set to " + subrace.displayName() + ".");
	}

	/**
	 * Undoes the lineage choice - the "go back" answer for screen two. Drops any ability picks the
	 * old lineage's pool doesn't share with a bare race pick and re-clamps the stat allocation, same
	 * as choosing a different lineage would, since the pool and stat floor both come from it.
	 */
	public DraftResult clearSubrace(RaceDefinition race) {
		if (subraceId.isEmpty())
			return DraftResult.OK;
		subraceId = "";
		// No subrace means no bracket, and no bracket means nothing purchasable can still be valid.
		abilityIds.clear();
		clampAllocations(race, null);
		trimToBudget(race, null);
		return DraftResult.ok("Lineage unset.");
	}

	// ---------------------------------------------------------------------------- screen three
	/**
	 * Everything this lineage's {@link AbilityBracket} can see and doesn't already get for free -
	 * what screen three should offer as purchasable. Empty (rather than every ability in the
	 * catalog) with no subrace chosen yet, same as {@link RaceDefinition#subraces()} being empty
	 * means "nothing to choose from" elsewhere in this class.
	 */
	public List<AbilityDefinition> abilityChoices(RaceDefinition race, SubraceDefinition subrace) {
		if (subrace == null)
			return List.of();
		List<AbilityDefinition> pool = new ArrayList<>();
		for (AbilityDefinition ability : AbilityCatalog.selectableFor(AbilityBracket.forSubrace(subrace.id()))) {
			if (!subrace.grantedAbilities().contains(ability.abilityId()))
				pool.add(ability);
		}
		return pool;
	}

	/** Drops the subset of what's currently chosen not in {@link #abilityChoices} any more - used
	 *  whenever the lineage changes shape under an existing pick (see {@link #selectSubrace}). */
	private void retainOnlyValidAbilities(RaceDefinition race, SubraceDefinition subrace) {
		List<String> validIds = new ArrayList<>();
		for (AbilityDefinition ability : abilityChoices(race, subrace)) {
			validIds.add(ability.id());
		}
		abilityIds.retainAll(validIds);
	}

	/** What every currently-chosen power costs, added up - counts against the exact same budget
	 *  spending a skill point does; see {@link #pointsSpent}. */
	public int abilitiesCost() {
		int total = 0;
		for (String id : abilityIds) {
			AbilityDefinition ability = AbilityCatalog.get(id);
			if (ability != null)
				total += ability.pointCost();
		}
		return total;
	}

	/**
	 * Adds the power if it's on offer and affordable, removes it (refunding its cost) if it's already
	 * taken. There is deliberately no separate "pick limit" any more: a power costs points out of the
	 * same pool a stat point does, so how many a character ends up with is just however many they
	 * could afford - including none at all, which is a perfectly good answer.
	 */
	public DraftResult toggleAbility(String abilityId, RaceDefinition race, SubraceDefinition subrace) {
		if (race == null || subrace == null)
			return DraftResult.no("Choose your lineage first.");
		if (abilityId == null || abilityId.isBlank())
			return DraftResult.no("No power named.");
		if (abilityIds.remove(abilityId))
			return DraftResult.ok("Dropped " + abilityId + ".");
		AbilityDefinition ability = AbilityCatalog.get(abilityId);
		if (ability == null || !abilityChoices(race, subrace).contains(ability))
			return DraftResult.no("That power isn't available to you.");
		if (ability.pointCost() > pointsRemaining(race, subrace))
			return DraftResult.no("Only " + pointsRemaining(race, subrace) + " point(s) left.");
		abilityIds.add(abilityId);
		return DraftResult.ok("Took " + ability.displayName() + ".");
	}

	/** Undoes every power pick - the "go back" answer for screen three. */
	public DraftResult clearAbilities() {
		if (abilityIds.isEmpty())
			return DraftResult.OK;
		abilityIds.clear();
		return DraftResult.ok("Powers unset.");
	}

	// ----------------------------------------------------------------------------- screen four
	/** Skill points spent plus every chosen power's cost - one shared pool, so buying a power leaves
	 *  less to put into stats and the other way around. */
	public int pointsSpent() {
		int total = abilitiesCost();
		for (int spent : allocated.values()) {
			total += spent;
		}
		return total;
	}

	/** What being this race and lineage costs, before the player spends a thing. */
	public int raceCost(RaceDefinition race, SubraceDefinition subrace) {
		return (race == null ? 0 : race.pointCost()) + (subrace == null ? 0 : subrace.pointCost());
	}

	/**
	 * Points actually available to distribute on screen four: the pool, less what the race and
	 * lineage charge for themselves.
	 *
	 * <p>Floored at zero. A race that costs more than the pool is a catalog authoring mistake, and
	 * the right behaviour is "you get nothing to spend", not a negative budget that makes every
	 * refund look like an overspend.
	 */
	public int pointsBudget(RaceDefinition race, SubraceDefinition subrace) {
		return Math.max(0, STARTING_POINTS - raceCost(race, subrace));
	}

	public int pointsRemaining(RaceDefinition race, SubraceDefinition subrace) {
		return pointsBudget(race, subrace) - pointsSpent();
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
		// The floor is whatever the race and lineage granted: those points were never the player's
		// to refund, so "back to where it started" is as low as this goes.
		if (spent + delta < 0)
			return DraftResult.no(skill.displayName() + " is already down to what being a " + race.displayName() + " gives you.");
		if (delta > pointsRemaining(race, subrace))
			return DraftResult.no("Only " + pointsRemaining(race, subrace) + " point(s) left.");
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
		// Kept even when unusable, so the draft always mirrors the name box: an emptied or invalid box
		// must not leave a previous valid name standing in for it. An unusable name can't be
		// committed (see CreationStep.APPEARANCE) and is never shown to anyone. Capped only so a
		// modified client can't park an enormous string here.
		name = trimmed.length() > NAME_MAX * 2 ? trimmed.substring(0, NAME_MAX * 2) : trimmed;
		if (!isNameUsable(name))
			return DraftResult.no("Names are 1 to " + NAME_MAX + " characters, letters, digits, spaces, apostrophes and hyphens.");
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

	/** Hands points back, highest-spend first, until the draft fits its budget again. */
	private void trimToBudget(RaceDefinition race, SubraceDefinition subrace) {
		int budget = pointsBudget(race, subrace);
		while (pointsSpent() > budget) {
			SkillType biggest = null;
			for (SkillType skill : SkillType.values()) {
				if (allocatedTo(skill) > 0 && (biggest == null || allocatedTo(skill) > allocatedTo(biggest)))
					biggest = skill;
			}
			if (biggest == null)
				return;
			setAllocated(biggest, allocatedTo(biggest) - 1);
		}
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
