package net.spidrotech.duality.charactercreation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A race - screen one, and the spine of everything after it.
 *
 * <p>A race decides which lineages screen two offers, which powers screen three can choose from and
 * how many, and where the stat line starts on screen four. It is also the thing stored on the
 * character sheet as {@code species_profiles[0].class}, which is what the rest of the mod already
 * keys species behaviour off.
 *
 * @param id              stable id; also the species name written to the character sheet
 * @param displayName     what screen one shows
 * @param description     body text for the screen
 * @param iconHint        a texture path for your button art, or "" - only your screen reads it
 * @param subraces        lineages offered on screen two; empty means screen two is skipped
 * @param abilityPool     powers screen three offers, before any subrace additions
 * @param abilityPicks    how many of them a character may take
 * @param baseSkills      starting value per skill before allocation, in {@link SkillType} order
 * @param startsUnlocked  false means the player must have earned it - see {@link RaceCatalog#selectableFor}
 * @param equippedTag     marker written into the player's EquippedAbilities string, e.g. "vampire",
 *                        which is what {@code VampireRank#isVampire} and friends already look for.
 *                        Empty for races with no such marker.
 * @param pointCost       how much of the creation budget being this costs. A race that starts you
 *                        strong should charge for it: a vampire at cost 4 walks into screen four
 *                        with one point to spend, on top of whatever its base stats already gave.
 *                        0 for a race that's free to be.
 */
public record RaceDefinition(String id, String displayName, String description, String iconHint, List<SubraceDefinition> subraces, List<String> abilityPool,
		int abilityPicks, List<Integer> baseSkills, boolean startsUnlocked, String equippedTag, int pointCost) {

	public RaceDefinition {
		subraces = List.copyOf(subraces);
		abilityPool = List.copyOf(abilityPool);
		baseSkills = normalizeBase(baseSkills);
		abilityPicks = Math.max(0, abilityPicks);
		pointCost = Math.max(0, pointCost);
	}

	public SubraceDefinition subrace(String subraceId) {
		if (subraceId == null)
			return null;
		for (SubraceDefinition subrace : subraces) {
			if (subrace.id().equalsIgnoreCase(subraceId))
				return subrace;
		}
		return null;
	}

	public boolean hasSubraces() {
		return !subraces.isEmpty();
	}

	public int baseSkill(SkillType skill) {
		return baseSkills.get(skill.ordinal());
	}

	/**
	 * What being this race put into a skill for free - the amount above the floor everyone starts
	 * at, with the lineage folded in.
	 *
	 * <p>This is the other half of "a vampire costs 4 points and 2 of them go into Strength": the
	 * cost is {@link #pointCost()}, and the 2 is this. It is also the value the player cannot
	 * refund below, because points spent on top are the only ones they own.
	 */
	public int grantedPoints(SkillType skill, SubraceDefinition subrace) {
		return Math.max(0, startingSkills(subrace).get(skill) - SkillType.MIN);
	}

	/** Every skill this race and lineage put something into, for a "what you get" panel. */
	public Map<SkillType, Integer> grantedPointsMap(SubraceDefinition subrace) {
		Map<SkillType, Integer> granted = new EnumMap<>(SkillType.class);
		for (SkillType skill : SkillType.values()) {
			int points = grantedPoints(skill, subrace);
			if (points != 0)
				granted.put(skill, points);
		}
		return granted;
	}

	public Map<SkillType, Integer> baseSkillMap() {
		Map<SkillType, Integer> map = new EnumMap<>(SkillType.class);
		for (SkillType skill : SkillType.values()) {
			map.put(skill, baseSkill(skill));
		}
		return map;
	}

	/** Everything screen three may offer for this race and the chosen lineage, in a stable order.
	 *  Powers the lineage grants outright are not in here - those aren't a choice. */
	public List<String> selectableAbilities(SubraceDefinition subrace) {
		List<String> pool = new ArrayList<>(abilityPool);
		if (subrace != null) {
			for (String extra : subrace.extraAbilityPool()) {
				if (!pool.contains(extra))
					pool.add(extra);
			}
			pool.removeAll(subrace.grantedAbilities());
		}
		return List.copyOf(pool);
	}

	/** Base values with the lineage's adjustment folded in, clamped to the legal range. This is
	 *  what screen four should draw the sliders from. */
	public Map<SkillType, Integer> startingSkills(SubraceDefinition subrace) {
		Map<SkillType, Integer> map = new EnumMap<>(SkillType.class);
		for (SkillType skill : SkillType.values()) {
			int value = baseSkill(skill) + (subrace == null ? 0 : subrace.skillBonus(skill));
			map.put(skill, Math.max(SkillType.MIN, Math.min(SkillType.MAX, value)));
		}
		return map;
	}

	/** Builder-ish helper so the catalog reads as a table rather than a wall of arguments. */
	public static RaceDefinition of(String id, String displayName, String description, List<SubraceDefinition> subraces, List<String> abilityPool, int abilityPicks,
			Map<SkillType, Integer> baseSkills, boolean startsUnlocked, String equippedTag, int pointCost) {
		return new RaceDefinition(id, displayName, description, "", subraces, abilityPool, abilityPicks, SubraceDefinition.toList(baseSkills), startsUnlocked,
				equippedTag, pointCost);
	}

	/** Missing entries default to {@link SkillType#MIN} rather than zero - a race that forgets to
	 *  list a skill should start it at the floor, not below it. */
	private static List<Integer> normalizeBase(List<Integer> values) {
		Integer[] out = new Integer[SkillType.values().length];
		for (int i = 0; i < out.length; i++) {
			int raw = values != null && i < values.size() && values.get(i) != null ? values.get(i) : SkillType.MIN;
			out[i] = Math.max(SkillType.MIN, Math.min(SkillType.MAX, raw));
		}
		return List.of(out);
	}
}
