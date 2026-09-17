package net.spidrotech.duality.village;

/**
 * The catalogue of things that can happen to a village. Each constant is a threat profile, not an
 * implementation - what it does when it lands lives in {@link VillageEvents}, which keeps the
 * balance numbers in one readable table instead of scattered through the handlers.
 *
 * <p>Add an event by adding a constant here and a case in {@link VillageEvents#applyOutcome}. If
 * it should be able to happen on its own while nobody's watching, give it a non-zero
 * {@link #randomWeight()}; leave it at 0 for events only ever fired deliberately.
 */
public enum VillageEvent {
	// ----------------------------------------------------------------- hostile, evil when it lands
	/** The one the whole abduction chain was built for. A clan hold nearby sends a raiding party;
	 *  if the village can't hold, somebody leaves with them and doesn't come back on their own. */
	VAMPIRE_RAID("Vampire Raid", ThreatType.PHYSICAL, 28.0, 20.0, true, true, 10, VillageFaction.VAMPIRE_CLAN, TargetFilter.NON_EVIL),
	/** Heavier, rarer, and it burns things. Demons don't take prisoners, they take the village. */
	DEMON_ATTACK("Demon Attack", ThreatType.PHYSICAL, 48.0, 30.0, true, true, 5, VillageFaction.DEMON_HOLD, TargetFilter.NON_EVIL),
	/** Mortal, mundane, and constant. Costs prosperity rather than lives, but it grinds morale down
	 *  until something worse can finish the job. */
	BANDIT_SHAKEDOWN("Bandit Shakedown", ThreatType.SOCIAL, 22.0, 8.0, true, true, 8, VillageFaction.OUTLAW_CAMP, TargetFilter.NON_EVIL),
	/** No army, just a curse. Walls are irrelevant; this is what wards exist for. */
	BLOOD_CURSE("Blood Curse", ThreatType.MAGICAL, 32.0, 18.0, true, true, 4, null, TargetFilter.NON_EVIL),
	/** Not anyone's fault. Bad for the village, only mildly bad for the world. */
	PLAGUE("Plague", ThreatType.SOCIAL, 26.0, 5.0, true, true, 5, null, TargetFilter.ANY),
	/** Something goes into the soil. The fields yield a fraction of normal until it breaks, and no
	 *  amount of reassigning people fixes that - a blighted village eats its stores and then eats
	 *  itself. The one hunger a player can actually be asked to do something about. */
	BLIGHT("Blight", ThreatType.MAGICAL, 24.0, 12.0, true, true, 4, null, TargetFilter.ANY),
	/** Someone offers the elders a deal. If the village is desperate enough to take it, it gets
	 *  stronger and the world gets worse - the one hostile event where losing makes a village
	 *  harder to raid next time. */
	DARK_PACT("Dark Pact", ThreatType.SOCIAL, 30.0, 22.0, true, true, 3, null, TargetFilter.NON_EVIL),

	// ------------------------------------------------------------------------------------- boons
	/** A powerful good witch moves in and puts wards over the place. The single best thing that
	 *  can happen to an undefended human village. */
	COVEN_PROTECTION("Coven Protection", ThreatType.NONE, 0.0, 15.0, false, false, 5, VillageFaction.WITCH_COVEN, TargetFilter.NON_EVIL),
	/** A whitelighter passes through: wounds closed, morale lifted, a thin ward left behind. */
	WHITELIGHTER_VISIT("Whitelighter Visit", ThreatType.NONE, 0.0, 10.0, false, false, 5, VillageFaction.WHITELIGHTER_SANCTUARY, TargetFilter.NON_EVIL),
	/** New people arrive, and one of them gets a name and a record - a future abduction victim,
	 *  or a future militiaman. */
	SETTLERS_ARRIVE("Settlers Arrive", ThreatType.NONE, 0.0, 4.0, false, false, 8, null, TargetFilter.ANY),
	/** They built something. Prosperity and, sometimes, another point of wall. */
	CONSTRUCTION("New Construction", ThreatType.NONE, 0.0, 2.0, false, false, 9, null, TargetFilter.ANY),
	/** A good year. Prosperity and morale. */
	GOOD_HARVEST("Good Harvest", ThreatType.NONE, 0.0, 2.0, false, false, 9, null, TargetFilter.ANY),
	/** They drill. Militia up, at a small cost in prosperity - the village that prepares is the
	 *  village that isn't farming that week. */
	MILITIA_DRILL("Militia Drill", ThreatType.NONE, 0.0, 3.0, false, false, 6, null, TargetFilter.ANY);

	/** Which villages an event is allowed to happen to. */
	public enum TargetFilter {
		ANY,
		/** Skips demon holds, vampire clans and outlaw camps. Nobody blesses a demon hold, and
		 *  nobody raids one for peasants. */
		NON_EVIL,
		EVIL_ONLY
	}

	private final String displayName;
	private final ThreatType threat;
	private final double baseThreat;
	private final double dualityWeight;
	private final boolean hostile;
	private final boolean evilWhenSuccessful;
	private final int randomWeight;
	private final VillageFaction perpetrator;
	private final TargetFilter targetFilter;

	VillageEvent(String displayName, ThreatType threat, double baseThreat, double dualityWeight, boolean hostile, boolean evilWhenSuccessful, int randomWeight,
			VillageFaction perpetrator, TargetFilter targetFilter) {
		this.displayName = displayName;
		this.threat = threat;
		this.baseThreat = baseThreat;
		this.dualityWeight = dualityWeight;
		this.hostile = hostile;
		this.evilWhenSuccessful = evilWhenSuccessful;
		this.randomWeight = randomWeight;
		this.perpetrator = perpetrator;
		this.targetFilter = targetFilter;
	}

	public String displayName() {
		return displayName;
	}

	public ThreatType threat() {
		return threat;
	}

	/** Attack power before per-roll variance. Compared against {@link VillageRecord#defenseAgainst}. */
	public double baseThreat() {
		return baseThreat;
	}

	/** Magnitude of the world duality swing when this lands in full. Direction comes from
	 *  {@link #evilWhenSuccessful()}. */
	public double dualityWeight() {
		return dualityWeight;
	}

	/** Whether the village gets to resist. Boons don't get resisted, they just happen. */
	public boolean hostile() {
		return hostile;
	}

	public boolean evilWhenSuccessful() {
		return evilWhenSuccessful;
	}

	/** Relative odds of the background simulation rolling this. 0 means manual-only. */
	public int randomWeight() {
		return randomWeight;
	}

	/** The faction that has to have a village nearby for this to be rolled at random, or null if
	 *  the event needs no author. A forced run doesn't require one. */
	public VillageFaction perpetrator() {
		return perpetrator;
	}

	/** Whether this event takes a prisoner when it succeeds. */
	public boolean abducts() {
		return this == VAMPIRE_RAID;
	}

	public boolean canTarget(VillageFaction faction) {
		return switch (targetFilter) {
			case ANY -> true;
			case NON_EVIL -> !faction.isEvil();
			case EVIL_ONLY -> faction.isEvil();
		};
	}

	/** Lenient parse so commands accept "vampire_raid", "VAMPIRE_RAID" or "Vampire Raid". */
	public static VillageEvent parse(String raw) {
		if (raw == null)
			return null;
		String normalized = raw.trim().replace(' ', '_');
		for (VillageEvent event : values()) {
			if (event.name().equalsIgnoreCase(normalized) || event.displayName.equalsIgnoreCase(raw.trim()))
				return event;
		}
		return null;
	}
}
