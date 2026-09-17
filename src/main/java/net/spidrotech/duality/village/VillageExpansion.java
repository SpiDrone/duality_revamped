package net.spidrotech.duality.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * How new settlements come into being.
 *
 * <p>A village that is fed, full and well off eventually runs out of room and sends people out to
 * start another one. That's the only way the map gains settlements on its own - there's no worldgen
 * placement - and it means the shape of the world a hundred days in is a consequence of what
 * survived rather than a seed.
 *
 * <p>It cuts both ways: a clan hold that keeps winning spreads too. Left alone, a corner of the map
 * fills up with vampire holds seeding more vampire holds, and the duality score goes with it.
 */
public final class VillageExpansion {
	/** People before a village will consider splitting. */
	public static final int MIN_POPULATION = 18;
	/** Prosperity before it can afford to. */
	public static final double MIN_PROSPERITY = 55.0;
	/**
	 * How full the pantry has to be before anyone leaves with a cart, as a fraction of what the
	 * village can store.
	 *
	 * <p>Expressed against capacity rather than as an absolute, because capacity is now the pantry
	 * - so "we have food to spare" means "we filled what we built", and a village that wants to
	 * spread has to have built granaries to do it. {@link #MIN_STORES} stops a village with one
	 * small pantry qualifying on a few days' worth.
	 */
	public static final double MIN_STORES_FRACTION = 0.6;
	/** Absolute floor on banked food, whatever the pantry's size. */
	public static final double MIN_STORES = 40.0;
	/** How far out daughter settlements are placed. */
	public static final int MIN_DISTANCE = 420;
	public static final int MAX_DISTANCE = 900;
	/** No new settlement closer than this to any existing one. */
	public static final int MIN_SPACING = 380;
	/** Chance per eligible village per day. Rare on purpose - this should feel like the map
	 *  changing, not like a tick. */
	private static final int FOUND_PERCENT = 6;
	/** How many tries at finding a site before giving up for the day. */
	private static final int SITE_ATTEMPTS = 6;

	private VillageExpansion() {
	}

	/** Whether this village is in any state to send people out. */
	public static boolean canExpand(VillageStore store, VillageRecord village) {
		if (village.isAbandoned() || village.flags().contains("HUNGRY") || village.flags().contains("RAZED"))
			return false;
		if (village.population() < MIN_POPULATION || village.prosperity() < MIN_PROSPERITY)
			return false;
		if (village.foodStores() < Math.max(MIN_STORES, village.foodCapacity() * MIN_STORES_FRACTION) || village.foodBalance() <= 0)
			return false;
		// Somebody has to be able to build the thing when they get there.
		return VillageEconomy.countJob(store, village, NpcJob.MASON) + VillageEconomy.countJob(store, village, NpcJob.LABORER) > 0;
	}

	/**
	 * Rolls for, sites and founds a daughter settlement.
	 *
	 * @return the new village, or null if this village didn't found one today
	 */
	public static VillageRecord tryFound(VillageStore store, VillageWorldBridge bridge, VillageRecord parent, long day, Random random, List<String> notes) {
		if (random.nextInt(100) >= FOUND_PERCENT || !canExpand(store, parent))
			return null;
		WorldPoint site = findSite(store, bridge, parent, random);
		if (site == null)
			return null;

		String name = uniqueName(store, random);
		VillageRecord child = VillageRecord.create(name, parent.faction(), site, day);
		// A daughter settlement starts from what the parent could spare, not from the faction
		// defaults - it is a splinter, not a new spawn.
		int settlers = 5 + random.nextInt(4);
		child.setPopulation(settlers);
		child.setMilitia(Math.max(1, settlers / 4));
		child.setFortification(0);
		child.setWards(0);
		child.setMorale(Math.min(0.95, parent.morale() + 0.05));
		child.setProsperity(15);
		child.setFoodStores(Math.min(settlers * 6.0, parent.foodStores() * 0.4));
		store.add(child);

		parent.addPopulation(-settlers);
		parent.setProsperity(parent.prosperity() - 15);
		parent.setFoodStores(parent.foodStores() - child.foodStores());
		parent.setRelation(child.villageId(), 75);
		child.setRelation(parent.villageId(), 75);
		child.flags().add("FOUNDED_BY_" + parent.villageId());

		List<NpcRecord> movers = pickSettlers(store, parent, random);
		for (NpcRecord npc : movers) {
			parent.residents().remove(npc.npcId());
			parent.addPopulation(-1);
			child.residents().add(npc.npcId());
			child.addPopulation(1);
			npc.setHomeVillageId(child.villageId());
			npc.setCurrentVillageId(child.villageId());
			// They walk there. Whatever entity was standing in the old village isn't theirs now.
			bridge.despawn(npc);
			npc.addLogEntry(day, "Left " + parent.name() + " to help found " + child.name() + ".");
			store.save(npc);
		}

		VillageEconomy.recompute(store, parent);
		VillageEconomy.recompute(store, child);
		String note = settlers + " left " + parent.name() + " and founded " + child.name() + " at " + site + ".";
		parent.addLogEntry(new VillageRecord.LogEntry(day, "SETTLEMENT_FOUNDED", "RESOLVED", note, 0.0));
		child.addLogEntry(new VillageRecord.LogEntry(day, "SETTLEMENT_FOUNDED", "RESOLVED", child.name() + " was founded by people out of " + parent.name() + ".", 0.0));
		store.save(parent);
		store.save(child);
		notes.add(note);
		bridge.announce(parent, note);

		// A faction spreading is the faction winning. Good or evil, the world notices.
		double lean = parent.faction().alignment() * 8.0;
		if (lean != 0)
			store.applyDuality(lean);
		return child;
	}

	/** Somewhere far enough out to be its own place and not so far the parent forgets it exists. */
	private static WorldPoint findSite(VillageStore store, VillageWorldBridge bridge, VillageRecord parent, Random random) {
		for (int attempt = 0; attempt < SITE_ATTEMPTS; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
			WorldPoint proposal = new WorldPoint(parent.center().dimension(), parent.center().x() + (int) Math.round(Math.cos(angle) * distance),
					parent.center().y(), parent.center().z() + (int) Math.round(Math.sin(angle) * distance));
			// Ask the world whether that's a real place. Off-server, or with the chunks cold, the
			// proposal comes straight back - the simulation is not allowed to generate terrain just
			// to answer this.
			WorldPoint sited = bridge.resolveSite(proposal);
			if (sited == null)
				continue;
			if (store.nearestVillage(sited, null, MIN_SPACING, parent.villageId()) == null)
				return sited;
		}
		return null;
	}

	/** Who goes. A founding party wants hands and food, not a garrison. */
	private static List<NpcRecord> pickSettlers(VillageStore store, VillageRecord parent, Random random) {
		List<NpcRecord> chosen = new ArrayList<>();
		List<NpcRecord> pool = store.residentsOf(parent);
		int wanted = 1 + random.nextInt(2);
		for (NpcJob preferred : new NpcJob[]{NpcJob.FARMER, NpcJob.LABORER, NpcJob.MASON, NpcJob.HUNTER}) {
			for (NpcRecord npc : pool) {
				if (chosen.size() >= wanted)
					return chosen;
				if (npc.job() != preferred || chosen.contains(npc) || npc.status() != NpcStatus.RESIDENT)
					continue;
				// Never strip the parent of its last food producer to stock a new village.
				if (npc.job().feedsOthers() && countFeeders(pool, chosen) <= 1)
					continue;
				chosen.add(npc);
			}
		}
		return chosen;
	}

	private static int countFeeders(List<NpcRecord> pool, List<NpcRecord> alreadyLeaving) {
		int count = 0;
		for (NpcRecord npc : pool) {
			if (npc.job().feedsOthers() && !alreadyLeaving.contains(npc))
				count++;
		}
		return count;
	}

	private static String uniqueName(VillageStore store, Random random) {
		for (int attempt = 0; attempt < 24; attempt++) {
			String candidate = VillageNames.place(random);
			if (!store.nameIsTaken(candidate))
				return candidate;
		}
		return VillageNames.place(random) + "-" + random.nextInt(1000);
	}
}
