package net.spidrotech.duality.village;

/**
 * Who a village belongs to. Faction decides three things: which way the village's very existence
 * leans the world duality score, how it defends itself (steel vs wards), and which events the
 * background simulation is willing to roll for it - a coven doesn't get raided by the coven.
 *
 * <p>These are the settlements the mod places and tracks itself, not vanilla villages. A vanilla
 * village is scenery; one of these is a bookkeeping entity with residents, defenses and a history.
 */
public enum VillageFaction {
	/** Ordinary mortals. Defends with numbers and walls, has no magic of its own, and is what
	 *  everything else in the world preys on. The baseline the other factions are read against. */
	HUMAN("Human Settlement", 0.0, 1.0, 0.5, true),
	/** Witches. Thin on the ground and poor in a fight, but wards do most of the work, and a
	 *  coven that moves in next door can protect a human village by proxy. */
	WITCH_COVEN("Witch Coven", 0.3, 0.6, 1.6, true),
	/** Whitelighter sanctuary. Almost defenceless in the physical sense and almost untouchable in
	 *  the magical one. Strongly good: its survival is worth duality points on its own. */
	WHITELIGHTER_SANCTUARY("Whitelighter Sanctuary", 1.0, 0.4, 1.8, true),
	/** A vampire clan's hold. This is where abducted villagers end up - see {@link NpcFate}. */
	VAMPIRE_CLAN("Vampire Clan Hold", -0.7, 1.3, 0.9, false),
	/** A demon stronghold in the overworld sense - the surface counterpart to the underworld
	 *  strongholds worldgen already places. */
	DEMON_HOLD("Demon Hold", -1.0, 1.5, 1.2, false),
	/** Mortal bandits. Evil, but mundanely so: no wards, no magic, and vulnerable to everything
	 *  the supernatural factions throw at a human village. */
	OUTLAW_CAMP("Outlaw Camp", -0.4, 1.1, 0.3, false);

	private final String displayName;
	private final double alignment;
	private final double physicalBias;
	private final double magicalBias;
	private final boolean abductable;

	VillageFaction(String displayName, double alignment, double physicalBias, double magicalBias, boolean abductable) {
		this.displayName = displayName;
		this.alignment = alignment;
		this.physicalBias = physicalBias;
		this.magicalBias = magicalBias;
		this.abductable = abductable;
	}

	public String displayName() {
		return displayName;
	}

	/** -1 (wholly evil) .. +1 (wholly good). Scales how hard events here move the world score. */
	public double alignment() {
		return alignment;
	}

	/** Multiplier on this faction's defense against PHYSICAL threats. */
	public double physicalBias() {
		return physicalBias;
	}

	/** Multiplier on this faction's defense against MAGICAL threats. */
	public double magicalBias() {
		return magicalBias;
	}

	/** Whether raiders bother taking prisoners from here. Nobody abducts out of a demon hold. */
	public boolean abductable() {
		return abductable;
	}

	public boolean isEvil() {
		return alignment < -0.1;
	}

	public boolean isGood() {
		return alignment > 0.1;
	}

	/** Lenient parse for commands and hand-edited json; falls back to HUMAN rather than throwing,
	 *  because a typo in a village file shouldn't take the save down with it. */
	public static VillageFaction parse(String raw) {
		if (raw == null)
			return HUMAN;
		for (VillageFaction faction : values()) {
			if (faction.name().equalsIgnoreCase(raw))
				return faction;
		}
		return HUMAN;
	}
}
