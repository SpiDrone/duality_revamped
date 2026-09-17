package net.spidrotech.duality.village;

/**
 * How an event landed. Hostile events resolve into one of the four combat bands; boons land as
 * BOON, and anything that couldn't happen at all comes back FIZZLED so callers can tell "the
 * village fought it off" apart from "there was no vampire clan within fifty chunks".
 */
public enum VillageEventOutcome {
	/** Beaten cleanly. Defenders take nothing worse than a scare, and morale goes up. */
	REPELLED(false),
	/** Beaten, but it cost something - a few dead, walls down, wards spent. */
	COSTLY(false),
	/** Got in. Some damage done, someone taken, but the village is still standing. */
	PARTIAL(true),
	/** The village couldn't stop it. The full consequence lands. */
	OVERRUN(true),
	/** A non-hostile event that simply happened. */
	BOON(false),
	/** Preconditions weren't met - no aggressor nearby, nobody left to take, wrong faction. */
	FIZZLED(false);

	private final boolean aggressorSucceeded;

	VillageEventOutcome(boolean aggressorSucceeded) {
		this.aggressorSucceeded = aggressorSucceeded;
	}

	/** True when whatever came at the village got what it came for. Drives the duality swing. */
	public boolean aggressorSucceeded() {
		return aggressorSucceeded;
	}

	/** 0 (nothing got through) .. 1 (everything did). Scales damage and the duality delta so an
	 *  OVERRUN is meaningfully worse than a PARTIAL rather than just a different word. */
	public double severity() {
		return switch (this) {
			case REPELLED, FIZZLED -> 0.0;
			case COSTLY -> 0.25;
			case PARTIAL -> 0.6;
			case OVERRUN -> 1.0;
			case BOON -> 1.0;
		};
	}
}
