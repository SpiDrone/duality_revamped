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

	/**
	 * Lineages every player can pick without unlocking them first - one base lineage per race, so a
	 * race that's unlocked always has something to choose on screen two. Everything else has to be
	 * unlocked on the player's profile (see CharacterCreation#subraceUnlocked) and is hidden from
	 * screen two until it is. Edit freely.
	 */
	private static final Set<String> STARTER_SUBRACES = Set.of("mundane", "lower_level", "whitelighter");

	public static Set<String> starterSubraces() {
		return STARTER_SUBRACES;
	}

	public static boolean isStarterSubrace(String subraceId) {
		return subraceId != null && STARTER_SUBRACES.contains(subraceId.toLowerCase());
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

		register(RaceDefinition.of("human", "Human", //
		"No power of your own..\nBut every choice still yours to make..\nWhat will you choose..?",
				List.of(SubraceDefinition.of("mundane", "Mundane",
						"An ordinary person, free from destinys hand.. \nOr maybe not so Ordinary..\n- Nobody's checked yet.",
						List.of(), Map.of(SkillType.ENDURANCE, 1)),
						SubraceDefinition.of("wiccan", "Wiccan", "Born to magic, some call you a natural...\nOthers call you unnatural..",
								List.of(), List.of("fireball", "fireball_greater", "lightning_hands_normal", "shimmer"),
								Map.of(SkillType.ATTUNEMENT, 2, SkillType.ENDURANCE, -1), 2),
						SubraceDefinition.of("hemovoid", "Hemovoid", "Your blood runs void of magic\nYou may not be able to wield it so easily...\nBut others struggle to use it against you just as much as you struggle to use it..",
								List.of(), Map.of(SkillType.ENDURANCE, 1), 2)),
				List.of("dash", "deflect"), 1, Map.of(SkillType.ENDURANCE, 1, SkillType.CHARISMA, 1), true, "", 0));

		register(RaceDefinition.of("demon", "Demon", "Born of the underworld, or remade there...",
				List.of(SubraceDefinition.of("lower_level", "Lower-Level", "Expendable, numerous, and underestimated.", List.of("firebolt"),
						Map.of(SkillType.ENDURANCE, 2, SkillType.CHARISMA, -2)),
						SubraceDefinition.of("vampire", "Vampire",
								"Fast, strong, and on a clock. Everything you are runs on somebody else's blood, and somebody else's bloodline.",
								List.of("leap", "dash", "deflect"), Map.of(SkillType.STRENGTH, 1, SkillType.AGILITY, 1, SkillType.CHARISMA, 1), 1, "vampire",
								"vampire_lineage"),
						SubraceDefinition.of("vampiric_queen", "Vampiric Queen",
								"The first of a line..\nThe villages would run red if you left any blood behind...",
								List.of("leap", "dash", "deflect", "vampire_mode"), Map.of(SkillType.STRENGTH, 1, SkillType.AGILITY, 1, SkillType.CHARISMA, 3), 4,
								"vampire", ""),
						SubraceDefinition.of("scabber_demon", "Scabber Demon",
								"Vile, acidic, and powerful\nConsidered to be a bottom feeder by some", List.of("blink"),
								List.of("screech", "acid_spit"), Map.of(SkillType.AGILITY, 2, SkillType.INSIGHT, 1, SkillType.CHARISMA, -2), 2)),
				List.of("blink", "flame", "screech", "shimmer", "vanish", "levitate", "anti_gravity", "flight", "lightning_hands_demonic", "acid_spit"), 2,
				Map.of(SkillType.STRENGTH, 2, SkillType.ATTUNEMENT, 1, SkillType.ENDURANCE, 1), false, "", 2));

		register(RaceDefinition.of("angelic", "Angelic",
				"Death isnt always the end..\nFor some its a chance to prove they could be more..",
				List.of(SubraceDefinition.of("whitelighter", "Whitelighter",
						"Assigned to someone, and answerable for them. You cannot hurt anyone, and you can save everyone.", List.of("orb_normal"),
						Map.of(SkillType.ATTUNEMENT, 2, SkillType.CHARISMA, 2))),
				List.of("orb_normal"), 1, Map.of(SkillType.ATTUNEMENT, 1, SkillType.CHARISMA, 1), false, "", 2, "not_previously_evil"));
	}
}
