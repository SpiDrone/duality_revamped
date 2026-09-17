package net.spidrotech.duality.village;

/**
 * What a captor intends to do with a prisoner, and how long they've got. The whole point of the
 * abduction chain is that the answer isn't decided at abduction time and isn't decided when the
 * player shows up either - it's decided by the clock. Get there on day 3 and you rescue someone;
 * get there on day 12 and you're fighting what's left of them.
 */
public enum NpcFate {
	/** Not a prisoner. */
	NONE(0),
	/** Sitting in a cell. Rescuable the whole time, but every day rolls a small chance of the
	 *  captor losing patience - see {@link VillageSimulator}. */
	IMPRISONED(0),
	/** Being turned. On the resolve day they become {@link NpcStatus#TURNED} and join the captor's
	 *  roster; the player who was too slow now meets them as an enemy. */
	TURNING(6),
	/** Being drained. On the resolve day they die and leave a corpse at the captor's village. */
	DRAINED(4),
	/** Kept as labour. Survives indefinitely but the village they came from never gets them back
	 *  unless someone walks in and takes them. */
	ENSLAVED(0),
	/** Ritual fodder. Resolves to dead, and the resolution itself swings the world evil hard. */
	SACRIFICE(3);

	private final int daysToResolve;

	NpcFate(int daysToResolve) {
		this.daysToResolve = daysToResolve;
	}

	/** In-game days between abduction and this fate resolving; 0 means it never resolves on its
	 *  own and the prisoner waits for the player (or for a bad daily roll). */
	public int daysToResolve() {
		return daysToResolve;
	}

	public boolean resolvesOnTimer() {
		return daysToResolve > 0;
	}

	public static NpcFate parse(String raw) {
		if (raw == null)
			return NONE;
		for (NpcFate fate : values()) {
			if (fate.name().equalsIgnoreCase(raw))
				return fate;
		}
		return NONE;
	}
}
