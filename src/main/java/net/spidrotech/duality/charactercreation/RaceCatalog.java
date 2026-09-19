package net.spidrotech.duality.charactercreation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every race and lineage the creator offers.
 *
 * <p><b>This is the file to edit.</b> Everything else in the package is machinery; the content of
 * the creator is {@link #registerDefaults()}, and the entries in it are starting data meant to be
 * rewritten. Add a race there and screens one and two gain an option with no other change; widen a
 * pool and screen three offers more.
 *
 * <p>The server owns the catalog and ships it to clients on join (see
 * {@code CharacterCreationNetwork}), so the screens can draw options without hard-coding any of
 * them. A client's copy is a mirror - the server re-checks every choice against its own.
 */
public final class RaceCatalog {
	private static final Map<String, RaceDefinition> RACES = new LinkedHashMap<>();

	static {
		registerDefaults();
	}

	private RaceCatalog() {
	}

	public static Collection<RaceDefinition> all() {
		return RACES.values();
	}

	public static RaceDefinition get(String raceId) {
		return raceId == null ? null : RACES.get(raceId.toLowerCase());
	}

	public static boolean has(String raceId) {
		return get(raceId) != null;
	}

	/** Resolves a lineage without the caller having to hold the race first. */
	public static SubraceDefinition subrace(String raceId, String subraceId) {
		RaceDefinition race = get(raceId);
		return race == null ? null : race.subrace(subraceId);
	}

	public static void register(RaceDefinition race) {
		RACES.put(race.id().toLowerCase(), race);
	}

	/** Used by the client when the server's catalog arrives. */
	public static void replaceAll(Collection<RaceDefinition> races) {
		RACES.clear();
		for (RaceDefinition race : races) {
			register(race);
		}
	}

	/**
	 * The races this player may actually pick, which is the list screen one should draw.
	 *
	 * <p>A race is offered if it starts unlocked, or if the player's profile has earned it - the
	 * {@code unlocked_species} array on their player file, which is already how the rest of the mod
	 * tracks this. Passing the set in rather than reading it here keeps this class free of any
	 * server dependency.
	 */
	public static List<RaceDefinition> selectableFor(Set<String> unlockedSpecies) {
		List<RaceDefinition> available = new ArrayList<>();
		for (RaceDefinition race : RACES.values()) {
			if (race.startsUnlocked() || containsIgnoreCase(unlockedSpecies, race.id()) || containsIgnoreCase(unlockedSpecies, race.displayName()))
				available.add(race);
		}
		return available;
	}

	public static boolean isSelectableFor(Set<String> unlockedSpecies, String raceId) {
		RaceDefinition race = get(raceId);
		if (race == null)
			return false;
		return race.startsUnlocked() || containsIgnoreCase(unlockedSpecies, race.id()) || containsIgnoreCase(unlockedSpecies, race.displayName());
	}

	private static boolean containsIgnoreCase(Set<String> values, String candidate) {
		if (values == null)
			return false;
		for (String value : values) {
			if (value.equalsIgnoreCase(candidate))
				return true;
		}
		return false;
	}

	// ============================================================================ starting data
	/**
	 * Placeholder content, wired to ability ids that actually exist today. Retune freely - the
	 * numbers here are a starting point, not a balance pass.
	 *
	 * <p>Read a race as a package: what it costs out of the five-point pool, what it puts into the
	 * stat line for free, and what it can do. The stat maps here are <b>grants</b> - {@code
	 * Map.of(STRENGTH, 2)} means "+2 Strength, free", landing the character on a Strength of 3.
	 * Those points never come out of the budget and can never be moved onto another skill.
	 *
	 * <p>So a vampire costs 4 of the 5, leaving one point to distribute, and separately arrives
	 * with five points' worth of free stats it did not pay for. A human costs nothing, grants
	 * little, and has all five to spend wherever it likes.
	 */
	public static void registerDefaults() {
		RACES.clear();

		register(RaceDefinition.of("human", "Human", "No power of your own, and every road still open. What you become is up to what finds you.",
				List.of(SubraceDefinition.of("mortal", "Mortal", "Ordinary, and harder to kill than that makes you sound.", List.of(),
						Map.of(SkillType.FORTUNE, 1, SkillType.ENDURANCE, 1)),
						SubraceDefinition.of("latent", "Latent", "Something is in the blood. It hasn't woken up yet.", List.of(),
								Map.of(SkillType.ATTUNEMENT, 1, SkillType.INSIGHT, 1)),
						SubraceDefinition.of("hunter", "Hunter", "You were taught what's out there, and what to do about it.", List.of("dash"),
								Map.of(SkillType.AGILITY, 1, SkillType.INSIGHT, 1), 1)),
				List.of("orb_normal", "dash", "deflect"), 1, Map.of(SkillType.ENDURANCE, 1, SkillType.PRESENCE, 1), true, "", 0));

		register(RaceDefinition.of("witch", "Witch", "Power through craft and will. The strongest hands in the world and the frailest body holding them.",
				List.of(SubraceDefinition.of("elemental", "Elemental", "Fire and storm answer you first.", List.of("fireball"),
						Map.of(SkillType.ATTUNEMENT, 2, SkillType.ENDURANCE, -1)),
						SubraceDefinition.of("seer", "Seer", "You see it coming. Whether you can stop it is another question.", List.of(),
								Map.of(SkillType.INSIGHT, 3, SkillType.STRENGTH, -1)),
						SubraceDefinition.of("warlock_touched", "Warlock-Touched", "You took something you shouldn't have, and it took back.", List.of("shimmer"),
								Map.of(SkillType.ATTUNEMENT, 2, SkillType.PRESENCE, -2), 1)),
				List.of("fireball", "fireball_greater", "lightning_hands_normal", "orb_normal", "shimmer"), 2, Map.of(SkillType.ATTUNEMENT, 3, SkillType.INSIGHT, 1),
				false, "", 2));

		register(RaceDefinition.of("vampire", "Vampire", "Fast, strong, and on a clock. Everything you are runs on somebody else's blood.",
				List.of(SubraceDefinition.of("fledgling_line", "Fledgling Line", "Turned recently, and still mostly who you were.", List.of("leap"),
						Map.of(SkillType.AGILITY, 1, SkillType.PRESENCE, 1)),
						SubraceDefinition.of("ashen_line", "Ashen Line", "An old, quiet bloodline that has outlasted better ones.", List.of("deflect"),
								Map.of(SkillType.ENDURANCE, 2, SkillType.FORTUNE, -1)),
						SubraceDefinition.of("crimson_court", "Crimson Court", "Highborn, and it shows. Everyone in the room knows what you are.",
								List.of("vampire_mode"), Map.of(SkillType.PRESENCE, 3, SkillType.INSIGHT, -1))),
				List.of("leap", "dash", "deflect", "vampire_mode", "shapeshift"), 2, Map.of(SkillType.STRENGTH, 2, SkillType.AGILITY, 2, SkillType.PRESENCE, 1),
				false, "vampire", 4));

		register(RaceDefinition.of("demon", "Demon", "Born of the underworld, or made there. Either way the surface is not yours.",
				List.of(SubraceDefinition.of("lower_level", "Lower-Level", "Expendable, numerous, and underestimated.", List.of("shimmer"),
						Map.of(SkillType.ENDURANCE, 2, SkillType.PRESENCE, -2)),
						SubraceDefinition.of("upper_level", "Upper-Level", "You give the orders down there. Up here that means less than you think.",
								List.of("flame", "blink"), Map.of(SkillType.ATTUNEMENT, 2, SkillType.PRESENCE, 2, SkillType.FORTUNE, -1)),
						SubraceDefinition.of("shapeshifter", "Shapeshifter", "No face of your own worth keeping.", List.of("shapeshift", "vanish"),
								Map.of(SkillType.INSIGHT, 2, SkillType.STRENGTH, -1))),
				List.of("blink", "flame", "screech", "shimmer", "vanish", "levitate", "anti_gravity", "flight", "lightning_hands_demonic", "acid_spit"), 3,
				Map.of(SkillType.STRENGTH, 3, SkillType.ATTUNEMENT, 2, SkillType.ENDURANCE, 1), false, "", 4));

		register(RaceDefinition.of("whitelighter", "Whitelighter", "You died well enough that they gave you a job. You cannot hurt anyone, and you can save everyone.",
				List.of(SubraceDefinition.of("guardian", "Guardian", "Assigned to someone, and answerable for them.", List.of("orb_normal"),
						Map.of(SkillType.PRESENCE, 2, SkillType.STRENGTH, -2)),
						SubraceDefinition.of("elder_touched", "Elder-Touched", "They have plans for you, and they don't explain them.", List.of("orb_normal"),
								Map.of(SkillType.ATTUNEMENT, 2, SkillType.INSIGHT, 2, SkillType.STRENGTH, -2))),
				List.of("orb_normal", "shimmer"), 1, Map.of(SkillType.ATTUNEMENT, 2, SkillType.PRESENCE, 2, SkillType.FORTUNE, 1), false, "", 3));
	}
}
