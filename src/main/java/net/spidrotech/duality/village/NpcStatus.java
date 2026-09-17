package net.spidrotech.duality.village;

/** Where an NPC stands in the world's bookkeeping, independent of whether their entity is loaded. */
public enum NpcStatus {
	/** Living at home, entity spawns normally when the chunk loads. */
	RESIDENT,
	/** Taken from their village and held somewhere else. Their entity is despawned; where they
	 *  physically are is {@link NpcRecord#currentVillageId()}, and what happens to them next is
	 *  {@link NpcRecord#fate()}. */
	CAPTIVE,
	/** The raid took them and no record of where survived - a dead-end lead for the player. */
	MISSING,
	/** Their fate resolved into becoming something else (vampire thrall, cultist). They now live
	 *  in the captor's village as one of its own, and can be found there. */
	TURNED,
	/** Dead. If they died somewhere specific the corpse is left as a discoverable marker. */
	DEAD;

	public static NpcStatus parse(String raw) {
		if (raw == null)
			return RESIDENT;
		for (NpcStatus status : values()) {
			if (status.name().equalsIgnoreCase(raw))
				return status;
		}
		return RESIDENT;
	}
}
