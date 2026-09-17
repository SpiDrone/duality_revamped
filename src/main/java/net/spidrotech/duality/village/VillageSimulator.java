package net.spidrotech.duality.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The part that runs while nobody is watching.
 *
 * <p>Villages are simulated one in-game day at a time. A day is: every village drifts a little
 * (wards fade, stores grow, morale settles), some villages roll an event, and every prisoner's
 * clock advances. None of it needs the chunks loaded, so a player who leaves for three weeks and
 * comes back has three weeks of history waiting - which is the entire point of keeping the village
 * in a file instead of in the world.
 *
 * <p>The catch-up is capped. A save left alone for a year shouldn't spend a minute on world load
 * generating three hundred days of raids nobody will read.
 */
public final class VillageSimulator {
	/** Most days of backlog worked off in one catch-up. Beyond this the clock just skips forward. */
	public static final int MAX_CATCHUP_DAYS = 30;
	/** Chance per village per day that anything happens at all. */
	private static final double EVENT_CHANCE_PER_DAY = 0.35;
	/** Wards lost per day. A blessing is worth roughly a month unless it's renewed. */
	private static final double WARD_DECAY_PER_DAY = 0.12;
	/** Morale pulls back toward this much per day. */
	private static final double MORALE_BASELINE = 0.75;
	private static final double MORALE_RECOVERY_PER_DAY = 0.02;

	/** What one simulated day produced. */
	public record DayReport(long day, List<VillageEventResult> events, List<String> fateNotes) {
		public DayReport {
			events = List.copyOf(events);
			fateNotes = List.copyOf(fateNotes);
		}

		public boolean isEmpty() {
			return events.isEmpty() && fateNotes.isEmpty();
		}
	}

	private VillageSimulator() {
	}

	/**
	 * Runs every day between the world's last simulated day and now, then records where it got to.
	 *
	 * @return one report per simulated day, oldest first; empty if there was nothing to catch up on
	 */
	public static List<DayReport> catchUp(VillageStore store, VillageWorldBridge bridge, long currentDay, Random random) {
		WorldDualityState world = store.world();
		List<DayReport> reports = new ArrayList<>();
		long from = world.lastSimulatedDay();
		if (from <= 0) {
			// First run on an existing save: start the clock here rather than simulating from day 0.
			world.setLastSimulatedDay(currentDay);
			store.saveWorld();
			return reports;
		}
		long backlog = currentDay - from;
		if (backlog <= 0)
			return reports;
		if (backlog > MAX_CATCHUP_DAYS) {
			// Skip the days nobody is going to read, but keep the tail so the return is eventful.
			from = currentDay - MAX_CATCHUP_DAYS;
			for (VillageRecord village : store.villages()) {
				// Only skip villages forward, never back: one founded inside the window is already
				// ahead of where the world clock is restarting from.
				if (village.lastSimulatedDay() < from)
					village.setLastSimulatedDay(from);
			}
		}
		for (long day = from + 1; day <= currentDay; day++) {
			DayReport report = simulateDay(store, bridge, day, random);
			if (!report.isEmpty())
				reports.add(report);
		}
		world.setLastSimulatedDay(currentDay);
		world.addSimulatedDays(currentDay - from);
		store.saveWorld();
		return reports;
	}

	/** One day for every village, plus everyone's fate clock. */
	public static DayReport simulateDay(VillageStore store, VillageWorldBridge bridge, long day, Random random) {
		List<VillageEventResult> events = new ArrayList<>();
		for (VillageRecord village : new ArrayList<>(store.villages())) {
			if (village.lastSimulatedDay() >= day)
				continue;
			village.setLastSimulatedDay(day);
			if (village.isAbandoned()) {
				store.save(village);
				continue;
			}
			drift(village, random);
			VillageEvent rolled = random.nextDouble() < EVENT_CHANCE_PER_DAY ? rollEvent(store, village, random) : null;
			if (rolled != null) {
				VillageEventResult result = VillageEvents.run(store, bridge, village, rolled, day, random);
				if (result.happened())
					events.add(result);
			} else {
				store.save(village);
			}
		}
		return new DayReport(day, events, resolveFates(store, bridge, day, random));
	}

	/** Passive day-to-day change: nothing dramatic, but it's what makes a neglected village slide
	 *  and a thriving one grow. */
	private static void drift(VillageRecord village, Random random) {
		village.setWards(village.wards() - WARD_DECAY_PER_DAY);
		if (village.morale() < MORALE_BASELINE)
			village.setMorale(Math.min(MORALE_BASELINE, village.morale() + MORALE_RECOVERY_PER_DAY));
		else
			village.setMorale(Math.max(MORALE_BASELINE, village.morale() - MORALE_RECOVERY_PER_DAY * 0.5));
		village.setProsperity(village.prosperity() + village.population() * 0.05 * village.morale());
		// A comfortable village grows. A frightened or hungry one doesn't.
		if (village.prosperity() > 50 && village.morale() > 0.6 && random.nextInt(100) < 12)
			village.addPopulation(1);
		// A village can't keep more people under arms than it can feed - but it gives them up one
		// at a time, so an unsustainable militia decays instead of vanishing between one day and
		// the next.
		if (village.militia() > Math.max(1, village.population() / 2))
			village.setMilitia(village.militia() - 1);
	}

	/**
	 * Picks what happens to a village today, weighted by the event table and bent by how the world
	 * is leaning. An evil world rolls more raids; a good one rolls more blessings.
	 *
	 * @return the event, or null if nothing this village is eligible for could happen
	 */
	public static VillageEvent rollEvent(VillageStore store, VillageRecord village, Random random) {
		double lean = store.world().normalized();
		List<VillageEvent> pool = new ArrayList<>();
		List<Double> weights = new ArrayList<>();
		double total = 0;
		for (VillageEvent event : VillageEvent.values()) {
			if (event.randomWeight() <= 0 || !event.canTarget(village.faction()))
				continue;
			VillageRecord aggressor = VillageEvents.findAggressor(store, village, event);
			// An event with an author needs that author to exist somewhere nearby. No clan hold in
			// range, no vampire raid - which is exactly the lever that makes clearing one out mean
			// something.
			if (event.perpetrator() != null && aggressor == null)
				continue;
			double weight = event.randomWeight();
			if (event.evilWhenSuccessful())
				weight *= 1.0 + Math.max(0.0, -lean) * 1.5;
			else
				weight *= 1.0 + Math.max(0.0, lean) * 1.5;
			// Neighbours who hate each other find reasons.
			if (aggressor != null)
				weight *= 1.0 + Math.max(0, -village.relationTo(aggressor.villageId())) / 100.0;
			// Nothing to steal, nobody to take.
			if (event.abducts() && store.residentsOf(village).isEmpty())
				weight *= 0.35;
			pool.add(event);
			weights.add(weight);
			total += weight;
		}
		if (pool.isEmpty() || total <= 0)
			return null;
		double pick = random.nextDouble() * total;
		for (int i = 0; i < pool.size(); i++) {
			pick -= weights.get(i);
			if (pick <= 0)
				return pool.get(i);
		}
		return pool.get(pool.size() - 1);
	}

	/**
	 * Advances every prisoner's clock. This is where "maybe they become a vampire, maybe you find
	 * their corpse" is actually decided - by the calendar, not by the player's arrival.
	 */
	public static List<String> resolveFates(VillageStore store, VillageWorldBridge bridge, long day, Random random) {
		List<String> notes = new ArrayList<>();
		for (NpcRecord npc : new ArrayList<>(store.npcs())) {
			if (npc.status() != NpcStatus.CAPTIVE)
				continue;
			VillageRecord captor = store.village(npc.currentVillageId());
			if (captor == null)
				continue;
			if (!npc.fate().resolvesOnTimer()) {
				// Open-ended captivity still isn't safe. Captors get bored.
				int patience = npc.fate() == NpcFate.IMPRISONED ? 8 : 2;
				if (random.nextInt(100) < patience) {
					npc.setFate(captor.faction() == VillageFaction.VAMPIRE_CLAN ? NpcFate.DRAINED : NpcFate.SACRIFICE, day);
					npc.addLogEntry(day, "Their captors have stopped waiting.");
					store.save(npc);
				}
				continue;
			}
			if (day < npc.fateDay())
				continue;
			notes.add(resolveFate(store, bridge, npc, captor, day));
		}
		return notes;
	}

	/** Resolves one prisoner's fate on the day it comes due. */
	private static String resolveFate(VillageStore store, VillageWorldBridge bridge, NpcRecord npc, VillageRecord captor, long day) {
		captor.captives().remove(npc.npcId());
		String note;
		double dualityDelta;
		switch (npc.fate()) {
			case TURNING -> {
				npc.setStatus(NpcStatus.TURNED);
				npc.setSpecies(captor.faction() == VillageFaction.VAMPIRE_CLAN ? "Vampire" : "Thrall");
				npc.setCombatValue(npc.combatValue() * 2.5 + 2.0);
				npc.setHomeVillageId(captor.villageId());
				npc.clearFate();
				captor.residents().add(npc.npcId());
				captor.addPopulation(1);
				captor.setMilitia(captor.militia() + 1);
				bridge.spawnAt(npc, captor);
				note = npc.name() + " is one of them now, at " + captor.name() + ".";
				dualityDelta = -8.0;
			}
			case DRAINED -> {
				npc.setStatus(NpcStatus.DEAD);
				npc.setCorpseLocation(captor.center());
				npc.clearFate();
				bridge.placeCorpse(npc, captor);
				note = npc.name() + " did not survive " + captor.name() + ".";
				dualityDelta = -10.0;
			}
			case SACRIFICE -> {
				npc.setStatus(NpcStatus.DEAD);
				npc.setCorpseLocation(captor.center());
				npc.clearFate();
				captor.setWards(captor.wards() + 1.0);
				bridge.placeCorpse(npc, captor);
				note = npc.name() + " was used for something at " + captor.name() + ". The place is better warded for it.";
				dualityDelta = -15.0;
			}
			default -> {
				npc.clearFate();
				note = npc.name() + "'s captivity at " + captor.name() + " goes on.";
				dualityDelta = 0.0;
			}
		}
		npc.addLogEntry(day, note);
		npc.setInWorld(false);
		store.save(npc);
		captor.addLogEntry(new VillageRecord.LogEntry(day, "FATE_" + npc.status().name(), "RESOLVED", note, dualityDelta));
		store.save(captor);
		if (dualityDelta != 0)
			store.applyDuality(dualityDelta);
		bridge.announce(captor, note);
		return note;
	}

	/**
	 * Frees a prisoner - the payoff for a player who got there in time. The NPC goes home if their
	 * home village still stands, and the world gets marginally better for it.
	 *
	 * @return true if there was someone to free
	 */
	public static boolean rescue(VillageStore store, VillageWorldBridge bridge, NpcRecord npc, long day) {
		if (npc == null || npc.status() != NpcStatus.CAPTIVE)
			return false;
		VillageRecord captor = store.village(npc.currentVillageId());
		if (captor != null) {
			captor.captives().remove(npc.npcId());
			captor.addLogEntry(new VillageRecord.LogEntry(day, "RESCUE", "RESOLVED", npc.name() + " was taken back out of " + captor.name() + ".", 12.0));
			store.save(captor);
		}
		VillageRecord home = store.village(npc.homeVillageId());
		npc.setStatus(NpcStatus.RESIDENT);
		npc.clearFate();
		if (home != null) {
			npc.setCurrentVillageId(home.villageId());
			home.residents().add(npc.npcId());
			home.addPopulation(1);
			home.setMorale(home.morale() + 0.10);
			home.addLogEntry(new VillageRecord.LogEntry(day, "RESCUE", "RESOLVED", npc.name() + " came home.", 0.0));
			store.save(home);
			bridge.spawnAt(npc, home);
		} else {
			npc.setCurrentVillageId("");
		}
		npc.addLogEntry(day, "Freed" + (captor != null ? " from " + captor.name() : "") + ".");
		store.save(npc);
		store.applyDuality(12.0);
		return true;
	}
}
