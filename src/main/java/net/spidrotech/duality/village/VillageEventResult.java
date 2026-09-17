package net.spidrotech.duality.village;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What one run of an event did. Returned by {@link VillageEvents#run} so callers - a command, a
 * quest hook, the background simulator - can report it, chain off it, or ignore it.
 *
 * <p>The narrative lines are written to be shown to a player verbatim. They're the "here's what
 * happened while you were away" text, so they name names.
 */
public record VillageEventResult(String villageId, String villageName, VillageEvent event, VillageEventOutcome outcome, long day, double dualityDelta,
		List<String> narrative, List<String> affectedNpcIds) {

	public VillageEventResult {
		narrative = List.copyOf(narrative);
		affectedNpcIds = List.copyOf(affectedNpcIds);
	}

	/** The event couldn't run at all. Never touches village state or the world duality score. */
	public static VillageEventResult fizzled(VillageRecord village, VillageEvent event, long day, String reason) {
		return new VillageEventResult(village == null ? "" : village.villageId(), village == null ? "?" : village.name(), event, VillageEventOutcome.FIZZLED, day, 0.0,
				List.of(reason), Collections.emptyList());
	}

	public boolean happened() {
		return outcome != VillageEventOutcome.FIZZLED;
	}

	/** One line suitable for a log file or a chat readout. */
	public String summary() {
		return "[" + villageName + "] " + event.displayName() + " -> " + outcome.name() + (dualityDelta == 0 ? "" : String.format(" (duality %+.1f)", dualityDelta));
	}

	/** Summary plus the narrative, one line each - what a /village event command prints. */
	public List<String> fullReport() {
		List<String> lines = new ArrayList<>();
		lines.add(summary());
		lines.addAll(narrative);
		return lines;
	}
}
