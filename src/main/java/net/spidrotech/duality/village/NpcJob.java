package net.spidrotech.duality.village;

/**
 * What a villager does all day, and what the village gets out of it.
 *
 * <p>Every soul in a village eats one food a day, named or not. Unnamed population feeds itself and
 * no more - it's subsistence. Named NPCs are the difference: a farmer feeds three more mouths than
 * their own, a guard feeds none and stands in the way of the things that come at night.
 *
 * <p>That's the tension the whole economy runs on. Guards don't grow food. A village that appoints
 * everyone to the watch starves; a village of nothing but farmers gets carried off one at a time.
 * And it's why a vampire raid that takes the farmers is worse than one that takes the smith - the
 * raid is over in a night, the famine takes a month.
 *
 * <p>All figures are per in-game day.
 */
public enum NpcJob {
	// ------------------------------------------------------------------------------- food
	/** The backbone. Feeds themselves and three others. */
	FARMER("Farmer", 3.0, 0.1, 0.5, 0.0, 0.0, 0.0),
	/** Less reliable than a farm, but a hunter can hold a spear. */
	HUNTER("Hunter", 2.5, 0.2, 1.5, 0.0, 0.0, 0.0),
	SHEPHERD("Shepherd", 2.0, 0.3, 0.5, 0.0, 0.0, 0.0),
	/** Unskilled hands. A little food, a little building, useful anywhere. */
	LABORER("Laborer", 1.0, 0.2, 0.5, 0.0, 0.4, 0.0),
	/** Forced labour. Produces like a laborer and costs the holding nothing but food; only evil
	 *  settlements keep them, and keeping them is part of why they read as evil. */
	THRALL("Thrall", 1.5, 0.1, 0.5, 0.0, 0.3, -0.002),

	// ------------------------------------------------------------------------------ defense
	/** The professional watch. Feeds nobody, and is most of what stops a raid. */
	GUARD("Guard", 0.0, 0.0, 4.0, 0.0, 0.0, 0.01),

	// ------------------------------------------------------------------------------- crafts
	SMITH("Smith", 0.0, 0.6, 1.5, 0.0, 0.2, 0.0),
	MASON("Mason", 0.0, 0.3, 0.5, 0.0, 1.0, 0.0),
	MERCHANT("Merchant", 0.0, 0.8, 0.5, 0.0, 0.0, 0.005),

	// -------------------------------------------------------------------------------- magic
	/** Keeps a small ward lit and treats the sick. The cheap answer to a curse. */
	HERBALIST("Herbalist", 0.5, 0.3, 0.5, 0.05, 0.0, 0.005),
	/** A resident witch renews wards faster than they decay. A village with one does not lose its
	 *  protection while nobody is looking - which is exactly why raiders come for her first. */
	WITCH("Witch", 0.0, 0.2, 1.0, 0.25, 0.0, 0.005),

	// ------------------------------------------------------------------------- non-producing
	/** Keeps the village's nerve up and remembers things. Eats, doesn't work. */
	ELDER("Elder", 0.0, 0.2, 0.0, 0.02, 0.0, 0.01),
	/** Eats, doesn't work, grows up into whatever the village needs by then. */
	CHILD("Child", 0.0, 0.0, 0.0, 0.0, 0.0, 0.005),
	/** No assigned work. The default for someone just enrolled; the economy will give them a job. */
	IDLE("Idle", 0.0, 0.0, 0.25, 0.0, 0.0, 0.0);

	private final String displayName;
	private final double foodProduction;
	private final double prosperity;
	private final double garrison;
	private final double wardUpkeep;
	private final double buildPoints;
	private final double morale;

	NpcJob(String displayName, double foodProduction, double prosperity, double garrison, double wardUpkeep, double buildPoints, double morale) {
		this.displayName = displayName;
		this.foodProduction = foodProduction;
		this.prosperity = prosperity;
		this.garrison = garrison;
		this.wardUpkeep = wardUpkeep;
		this.buildPoints = buildPoints;
		this.morale = morale;
	}

	public String displayName() {
		return displayName;
	}

	/** Food produced per day. Everyone consumes 1.0 regardless, counted against population. */
	public double foodProduction() {
		return foodProduction;
	}

	public double prosperity() {
		return prosperity;
	}

	/** Contribution to the village's standing defense, on top of the untrained levy. */
	public double garrison() {
		return garrison;
	}

	/** Wards renewed per day. Only a witch out-paces the natural decay. */
	public double wardUpkeep() {
		return wardUpkeep;
	}

	/** Progress toward the next building. */
	public double buildPoints() {
		return buildPoints;
	}

	public double morale() {
		return morale;
	}

	/** Net food this job adds to the village, after its own mouth. Negative means it has to be paid
	 *  for by somebody else's work - which is the point of guards. */
	public double netFood() {
		return foodProduction - VillageEconomy.FOOD_PER_PERSON;
	}

	public boolean feedsOthers() {
		return netFood() > 0;
	}

	/** Whether a village of this faction will assign this job. Only evil settlements keep thralls. */
	public boolean allowedIn(VillageFaction faction) {
		if (this == THRALL)
			return faction.isEvil();
		return true;
	}

	/** Jobs the economy is willing to hand out on its own - not children, not the idle, not thralls
	 *  (which you become by being captured, not by applying). */
	public boolean assignable() {
		return this != CHILD && this != IDLE && this != THRALL && this != ELDER;
	}

	public static NpcJob parse(String raw) {
		if (raw == null)
			return IDLE;
		String normalized = raw.trim().replace(' ', '_');
		for (NpcJob job : values()) {
			if (job.name().equalsIgnoreCase(normalized) || job.displayName.equalsIgnoreCase(raw.trim()))
				return job;
		}
		return IDLE;
	}
}
