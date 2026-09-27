package net.spidrotech.duality.charactercreation;

/**
 * The tree screen three's ability list is filtered through, matching how the races and lineages
 * nest: ALL is the root everyone sees regardless of what they picked, and every other bracket sees
 * its own abilities plus everything its parent (and its parent's parent, up to ALL) can see.
 *
 * <pre>
 *   All()
 *   Demonic(
 *       Vampiric(
 *           VampireQueen()
 *       )
 *   )
 *   Angelic()
 * </pre>
 *
 * <p>So a Vampiric Queen sees VAMPIRE_QUEEN abilities, VAMPIRIC ones (Vampire is the same lineage
 * without the crown), DEMONIC ones (every demon, crowned or not), and ALL - but a Scabber Demon,
 * sitting directly under DEMONIC with no lineage-specific bracket of its own, only ever sees DEMONIC
 * and ALL.
 *
 * <p>{@link #forSubrace} is the other half of this: which bracket a given lineage's OWN abilities
 * live in. Nothing here depends on Minecraft, same as the rest of the creator's data model - see
 * {@link CharacterDraft}'s class doc.
 */
public enum AbilityBracket {
	ALL(null),
	DEMONIC(ALL),
	VAMPIRIC(DEMONIC),
	VAMPIRE_QUEEN(VAMPIRIC),
	ANGELIC(ALL);

	private final AbilityBracket parent;

	AbilityBracket(AbilityBracket parent) {
		this.parent = parent;
	}

	public AbilityBracket parent() {
		return parent;
	}

	/** Whether {@code target} is this bracket or one of its ancestors - what decides "can a
	 *  character in THIS bracket see an ability filed under TARGET". */
	public boolean includes(AbilityBracket target) {
		for (AbilityBracket b = this; b != null; b = b.parent) {
			if (b == target)
				return true;
		}
		return false;
	}

	/**
	 * <b>The other file to edit</b>, alongside {@link AbilityCatalog#registerDefaults()}: which
	 * bracket a lineage's own abilities belong to. A subrace not listed here (every Human lineage
	 * today, since none has its own bracket yet) falls back to {@link #ALL} - every character sees at
	 * least the generic pool, never nothing.
	 */
	public static AbilityBracket forSubrace(String subraceId) {
		if (subraceId == null)
			return ALL;
		return switch (subraceId.toLowerCase()) {
			case "lower_level", "scabber_demon" -> DEMONIC;
			case "vampire" -> VAMPIRIC;
			case "vampiric_queen" -> VAMPIRE_QUEEN;
			case "whitelighter" -> ANGELIC;
			default -> ALL;
		};
	}
}
