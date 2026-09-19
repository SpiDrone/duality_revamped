package net.spidrotech.duality.charactercreation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A lineage within a race - screen two.
 *
 * <p>A subrace is where the interesting divergence goes: it can hand out powers outright, widen the
 * pool the player chooses from on screen three, and shift the stat line before a single point is
 * spent. The race says what you are; this says what kind.
 *
 * @param id               stable id, stored on the character sheet as {@code species_profiles[0].subspecies}
 * @param displayName      what screen two shows
 * @param description      body text for the screen
 * @param iconHint         a texture path for your button art, or "" - nothing reads this but your screen
 * @param grantedAbilities powers every character of this lineage gets, no choice involved
 * @param extraAbilityPool powers added to what screen three offers
 * @param skillBonuses     per-skill adjustment applied before allocation, in {@link SkillType} order
 * @param pointCost        points this lineage takes out of the budget on top of the race's own cost.
 *                         Usually 0 - a race pays for what it is, and a lineage only charges when it
 *                         is a real step up from its siblings.
 */
public record SubraceDefinition(String id, String displayName, String description, String iconHint, List<String> grantedAbilities, List<String> extraAbilityPool,
		List<Integer> skillBonuses, int pointCost) {

	public SubraceDefinition {
		grantedAbilities = List.copyOf(grantedAbilities);
		extraAbilityPool = List.copyOf(extraAbilityPool);
		skillBonuses = normalize(skillBonuses);
		pointCost = Math.max(0, pointCost);
	}

	/** The common case: a lineage that grants one power, nudges two stats and costs nothing extra. */
	public static SubraceDefinition of(String id, String displayName, String description, List<String> grantedAbilities, Map<SkillType, Integer> bonuses) {
		return of(id, displayName, description, grantedAbilities, bonuses, 0);
	}

	/** As above, for a lineage that charges for itself. */
	public static SubraceDefinition of(String id, String displayName, String description, List<String> grantedAbilities, Map<SkillType, Integer> bonuses,
			int pointCost) {
		return new SubraceDefinition(id, displayName, description, "", grantedAbilities, List.of(), toList(bonuses), pointCost);
	}

	public int skillBonus(SkillType skill) {
		return skillBonuses.get(skill.ordinal());
	}

	public Map<SkillType, Integer> skillBonusMap() {
		Map<SkillType, Integer> map = new EnumMap<>(SkillType.class);
		for (SkillType skill : SkillType.values()) {
			int bonus = skillBonus(skill);
			if (bonus != 0)
				map.put(skill, bonus);
		}
		return map;
	}

	static List<Integer> toList(Map<SkillType, Integer> bonuses) {
		Integer[] values = new Integer[SkillType.values().length];
		for (SkillType skill : SkillType.values()) {
			values[skill.ordinal()] = bonuses == null ? 0 : bonuses.getOrDefault(skill, 0);
		}
		return List.of(values);
	}

	/** Pads or trims to exactly one entry per skill, so a hand-written or out-of-date definition
	 *  can't blow up an index lookup later. */
	static List<Integer> normalize(List<Integer> values) {
		Integer[] out = new Integer[SkillType.values().length];
		for (int i = 0; i < out.length; i++) {
			out[i] = values != null && i < values.size() && values.get(i) != null ? values.get(i) : 0;
		}
		return List.of(out);
	}
}
