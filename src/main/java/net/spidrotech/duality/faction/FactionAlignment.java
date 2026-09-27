package net.spidrotech.duality.faction;

/** A faction's standing on the good/evil axis - same spirit as
 *  net.spidrotech.duality.village.VillageFaction#alignment(), simplified to three bands to match
 *  how the alliance and angel-membership rules were phrased ("good and evil factions"), rather
 *  than that class's continuous -1..1 score. */
public enum FactionAlignment {
	GOOD, NEUTRAL, EVIL;

	/** The one hard alliance rule: good and evil can never be allied, in either direction.
	 *  Neutral can ally with anyone, and evil can ally with evil (a vampire bloodline and a demon
	 *  faction, say) - this only vetoes the specific GOOD/EVIL pairing. */
	public static boolean canAlly(FactionAlignment a, FactionAlignment b) {
		return !((a == GOOD && b == EVIL) || (a == EVIL && b == GOOD));
	}

	public static FactionAlignment parse(String raw) {
		if (raw == null)
			return NEUTRAL;
		for (FactionAlignment alignment : values()) {
			if (alignment.name().equalsIgnoreCase(raw))
				return alignment;
		}
		return NEUTRAL;
	}
}
