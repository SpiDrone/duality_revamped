package net.spidrotech.duality.faction;

/**
 * What a faction fundamentally IS, for membership purposes - separate from both
 * {@link net.spidrotech.duality.village.VillageFaction} (a settlement's fixed archetype) and
 * {@link MemberKind} (what one member is). Most player-founded factions should stay OPEN; a kind
 * is only worth declaring when the faction is meant to be exclusive to one nature, the way a
 * vampire bloodline is (see {@link net.spidrotech.duality.abilities.vampire.VampireBloodlines}).
 *
 * <p>Kind and {@link FactionAlignment} are deliberately independent facts about a faction, not two
 * views of the same choice - alignment is read live off {@link FactionRecord#dualityScore()}
 * (see {@link FactionRecord#alignment()}), which nobody sets directly; a faction is evil because
 * of what it's done, not because its leader ticked a box. Declaring a kind only SEEDS that score
 * (see {@link #startingDualityScore}) to a sensible starting point - a vampire faction reads as
 * evil from the moment it exists - and an OPEN or HUMAN-kind faction can still drift EVIL over
 * time (bandits, say) independent of its kind, which is what lets the angel membership rule
 * ("won't join a demon faction OR a low [i.e. evil] faction") mean something broader than just the
 * VAMPIRE/DEMON kinds - see {@link Factions#isEligible}.
 */
public enum FactionKind {
	OPEN(0.0), HUMAN(0.0), WITCH(0.0), VAMPIRE(-400.0), DEMON(-500.0), ANGEL(400.0);

	private final double startingDualityScore;

	FactionKind(double startingDualityScore) {
		this.startingDualityScore = startingDualityScore;
	}

	/** Where Factions#setKind seeds FactionRecord#dualityScore the moment this kind is set -
	 *  landing VAMPIRE/DEMON in EVIL territory and ANGEL in GOOD immediately (see
	 *  FactionRecord#alignment's +-0.25 band), without alignment being a separate stored fact
	 *  that could drift out of step with the score. Purely a starting point - nothing keeps the
	 *  score pinned here afterward. */
	public double startingDualityScore() {
		return startingDualityScore;
	}

	/**
	 * Whether someone presenting as apparentKind may join a faction of this kind. OPEN accepts
	 * everyone; every other kind accepts only a matching APPARENT kind - which is what lets a
	 * humanized vampire (apparent kind HUMAN, see {@link MemberKinds#apparentKind}) pass into a
	 * HUMAN faction, and what stops a vampire (apparent kind VAMPIRE or HUMAN, never DEMON) from
	 * passing into a DEMON one even though vampires are demons by nature - there's simply no
	 * disguise that produces an apparent kind of DEMON today.
	 */
	public boolean accepts(MemberKind apparentKind) {
		return switch (this) {
			case OPEN -> true;
			case HUMAN -> apparentKind == MemberKind.HUMAN;
			case WITCH -> apparentKind == MemberKind.WITCH;
			case VAMPIRE -> apparentKind == MemberKind.VAMPIRE;
			case DEMON -> apparentKind == MemberKind.DEMON;
			case ANGEL -> apparentKind == MemberKind.ANGEL;
		};
	}

	public static FactionKind parse(String raw) {
		if (raw == null)
			return OPEN;
		for (FactionKind kind : values()) {
			if (kind.name().equalsIgnoreCase(raw))
				return kind;
		}
		return OPEN;
	}
}
