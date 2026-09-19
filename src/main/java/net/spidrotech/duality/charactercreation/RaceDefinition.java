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
 * <p><b>Free stats and the budget are two separate things.</b> {@link #freeStats()} is what being
 * this race gives you for nothing: it never touches the spending budget, and it can't be refunded
 * and moved onto another skill - those points were never the player's to move. {@link #pointCost()}
 * is a separate charge against the budget for being this race at all, and it can be 0. A race that
 * grants +2 Strength and costs nothing leaves the player all five points to spend and a Strength of
 * 3 they can build on but never take apart.
 *
 * @param id              stable id; also the species name written to the character sheet
 * @param displayName     what screen one shows
 * @param description     body text for the screen
 * @param iconHint        a texture path for your button art, or "" - only your screen reads it
 * @param subraces        lineages offered on screen two; empty means screen two is skipped
 * @param abilityPool     powers screen three offers, before any subrace additions
 * @param abilityPicks    how many of them a character may take
 * @param freeStats       stats the race hands over for nothing, as points above the floor every
 *                        character starts at, in {@link SkillType} order. These do not come out of
 *                        the creation budget and cannot be refunded into other skills - see the
 *                        class note below.
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
		int abilityPicks, List<Integer> freeStats, boolean startsUnlocked, String equippedTag, int pointCost) {

	public RaceDefinition {
		subraces = List.copyOf(subraces);
		abilityPool = List.copyOf(abilityPool);
		freeStats = normalizeFree(freeStats);
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

	/** Points this race hands over in a skill for free, before any lineage adjustment. */
	public int freeStat(SkillType skill) {
		return freeStats.get(skill.ordinal());
	}

	/**
	 * Everything this skill gets for free once the lineage is folded in - the race's
	 * {@link #freeStat} plus the lineage's adjustment, clamped into the legal range.
	 *
	 * <p>This is the number the player cannot take back: it never came out of their budget, so
	 * there is nothing to refund and nothing to move somewhere else. Points they buy on top are the
	 * only ones they own.
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

	public Map<SkillType, Integer> freeStatMap() {
		Map<SkillType, Integer> map = new EnumMap<>(SkillType.class);
		for (SkillType skill : SkillType.values()) {
			int free = freeStat(skill);
			if (free != 0)
				map.put(skill, free);
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

	/** Where each skill sits before the player spends anything: the floor, plus the race's free
	 *  stats, plus the lineage's adjustment. What screen four draws the sliders from. */
	public Map<SkillType, Integer> startingSkills(SubraceDefinition subrace) {
		Map<SkillType, Integer> map = new EnumMap<>(SkillType.class);
		for (SkillType skill : SkillType.values()) {
			int value = SkillType.MIN + freeStat(skill) + (subrace == null ? 0 : subrace.skillBonus(skill));
			map.put(skill, Math.max(SkillType.MIN, Math.min(SkillType.MAX, value)));
		}
		return map;
	}

	/** Builder-ish helper so the catalog reads as a table rather than a wall of arguments. */
	/**
	 * Builder-ish helper so the catalog reads as a table rather than a wall of arguments.
	 *
	 * @param freeStats what the race grants, written as the grant itself: {@code Map.of(STRENGTH, 2)}
	 *                  means "+2 Strength, free". A skill left out gets nothing.
	 */
	public static RaceDefinition of(String id, String displayName, String description, List<SubraceDefinition> subraces, List<String> abilityPool, int abilityPicks,
			Map<SkillType, Integer> freeStats, boolean startsUnlocked, String equippedTag, int pointCost) {
		return new RaceDefinition(id, displayName, description, "", subraces, abilityPool, abilityPicks, SubraceDefinition.toList(freeStats), startsUnlocked,
				equippedTag, pointCost);
	}

	/** A skill nobody listed grants nothing. Clamped to what the floor can actually absorb, so a
	 *  race can't grant a skill past its ceiling. */
	private static List<Integer> normalizeFree(List<Integer> values) {
		Integer[] out = new Integer[SkillType.values().length];
		for (int i = 0; i < out.length; i++) {
			int raw = values != null && i < values.size() && values.get(i) != null ? values.get(i) : 0;
			out[i] = Math.max(0, Math.min(SkillType.MAX - SkillType.MIN, raw));
		}
		return List.of(out);
	}
}
