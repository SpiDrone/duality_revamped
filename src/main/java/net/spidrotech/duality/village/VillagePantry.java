package net.spidrotech.duality.village;

/**
 * Keeps the food ledger and the actual chests in the village agreeing with each other.
 *
 * <p>The awkward bit of this whole system is that the simulation runs on cold chunks, where there
 * are no chests to read. So the ledger ({@link VillageRecord#foodStores()}) stays authoritative -
 * it has to be, it's the thing that runs while nobody is there - and the containers in the village's
 * pantry are reconciled against it whenever the chunks happen to be loaded.
 *
 * <p>Reconciling is a two-way trade, and both directions matter:
 *
 * <ul>
 * <li><b>The world talks back.</b> The difference between what's in the chests now and what was in
 * them last time anyone looked is exactly what a player added or took. That lands on the ledger, so
 * walking up and dumping in a stack of bread genuinely feeds the village.</li>
 * <li><b>The ledger talks back.</b> Whatever the village ate or grew while the chunks were cold is
 * then pushed into the containers, so a player who comes back after a hard month finds the pantry
 * emptied rather than exactly as they left it.</li>
 * </ul>
 *
 * <p>And if there is no pantry, there is nowhere to put anything: the village is capped at
 * {@link VillageRecord#NO_PANTRY_CAPACITY} and lives hand to mouth.
 */
public final class VillagePantry {
	/**
	 * How big a shortfall counts as "the chests are simply full" rather than "you cannot put a
	 * third of a loaf in a chest". Below this, an unstorable remainder stays on the ledger; above
	 * it, there is genuinely nowhere to put the food and it spoils.
	 */
	public static final double GRANULARITY = 2.0;

	/** What one food unit is worth in vanilla nutrition points. One person eats one unit a day, so
	 *  a loaf of bread (5 nutrition) is a bit over a day's food for one villager. */
	public static final double NUTRITION_PER_FOOD_UNIT = 4.0;

	/** The result of one reconciliation, for logging and for telling a player what they just did. */
	public record SyncResult(boolean synced, double playerDelta, double worldDelta, double stored, double capacity) {
		/** Nothing could be read - no pantry, or the chunks are cold. Not an error. */
		public static final SyncResult NOT_LOADED = new SyncResult(false, 0, 0, 0, 0);

		public boolean playerContributed() {
			return playerDelta > 0.01;
		}

		public boolean isFull() {
			return stored >= capacity - 0.01;
		}
	}

	private VillagePantry() {
	}

	/**
	 * Reconciles ledger and containers in both directions. Safe to call often and cheap when the
	 * pantry isn't loaded; the bridge is what decides whether it can be read at all.
	 */
	public static SyncResult sync(VillageStore store, VillageWorldBridge bridge, VillageRecord village) {
		if (!village.hasPantry())
			return SyncResult.NOT_LOADED;
		double inWorld = bridge.readPantry(village);
		if (inWorld < 0)
			return SyncResult.NOT_LOADED;

		// 1. Whatever changed in the chests since we last looked was a player doing it.
		double playerDelta = inWorld - village.pantryCheckpoint();
		if (Math.abs(playerDelta) > 0.001)
			village.setFoodStores(village.foodStores() + playerDelta);
		else
			playerDelta = 0;

		// 2. Now push the ledger back out, so the chests show what the village has actually got.
		double target = village.foodStores();
		double wanted = target - inWorld;
		double moved = wanted == 0 ? 0 : bridge.writePantry(village, wanted);
		double stored = inWorld + moved;

		// The containers are the last word on how much FITS: whatever the bridge could not move is
		// food with nowhere to go, and it spoils. But a shortfall smaller than GRANULARITY is only
		// item granularity - you cannot put a third of a loaf in a chest - and stays on the ledger.
		// Rounding that away would shave a village's fractional surplus every few seconds and
		// quietly starve it.
		double residual = target - stored;
		boolean spoiled = residual > GRANULARITY;
		double reconciled = spoiled ? stored : target;
		boolean changed = Math.abs(reconciled - target) > 0.001 || Math.abs(stored - village.pantryCheckpoint()) > 0.001;
		village.setFoodStores(reconciled);
		village.setPantryCheckpoint(stored);
		// Only touch the file when something actually moved; this runs every few seconds.
		if (changed)
			store.save(village);
		return new SyncResult(true, playerDelta, moved, village.foodStores(), village.foodCapacity());
	}

	/** Food units a stack of this nutrition and size is worth. */
	public static double foodUnits(int nutrition, int count) {
		return nutrition * count / NUTRITION_PER_FOOD_UNIT;
	}

	/** A line for a chat readout. */
	public static String describe(VillageRecord village) {
		if (!village.hasPantry())
			return "no pantry - " + village.name() + " has nowhere to keep food and lives hand to mouth";
		return String.format("%.0f/%.0f food units across %d pantry building(s)", village.foodStores(), village.foodCapacity(), village.pantries().size());
	}
}
