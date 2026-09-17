package net.spidrotech.duality.village;

/**
 * The seam between the simulation and the actual game.
 *
 * <p>Everything above this interface is arithmetic on json records and can run with no server at
 * all. Everything below it touches entities, levels and players. When a raid decides someone has
 * been carried off, the simulation moves the record and then tells the bridge; the bridge is what
 * finds the entity and removes it - or does nothing, if the chunk isn't loaded, because the record
 * is already correct and the entity will simply not be there when the chunk next loads.
 *
 * <p>{@link #NOOP} is a complete, valid implementation: with it, the whole village simulation runs
 * as pure bookkeeping. That's what the tests use.
 */
public interface VillageWorldBridge {
	/** Remove this NPC's entity from the world if it exists. Called when they're taken or killed. */
	void despawn(NpcRecord npc);

	/**
	 * Put this NPC's entity into the world at a village, if that village is loaded. Used when a
	 * captive is freed, when a turned prisoner joins their captor's roster, and when a village the
	 * player walks into needs its residents present.
	 *
	 * @return true if an entity was actually placed
	 */
	boolean spawnAt(NpcRecord npc, VillageRecord village);

	/** Leave a discoverable corpse where an NPC died, so the player who arrived too late has
	 *  something to find. */
	void placeCorpse(NpcRecord npc, VillageRecord village);

	/** Tell anyone who ought to hear about it - a player standing in the village, the server log.
	 *  Events that happen while nobody is watching still call this; it's the bridge's business
	 *  whether that means anything. */
	void announce(VillageRecord village, String line);

	/** A bridge that changes nothing in the world. The simulation is complete without one. */
	VillageWorldBridge NOOP = new VillageWorldBridge() {
		@Override
		public void despawn(NpcRecord npc) {
		}

		@Override
		public boolean spawnAt(NpcRecord npc, VillageRecord village) {
			return false;
		}

		@Override
		public void placeCorpse(NpcRecord npc, VillageRecord village) {
		}

		@Override
		public void announce(VillageRecord village, String line) {
		}
	};
}
