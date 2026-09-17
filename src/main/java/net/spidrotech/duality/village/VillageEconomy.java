package net.spidrotech.duality.village;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Jobs, food and the day-to-day running of a settlement.
 *
 * <p>The premise is that a village is a set of people who eat. Everyone consumes
 * {@link #FOOD_PER_PERSON} a day; unnamed population feeds itself and no more; named NPCs produce
 * whatever their {@link NpcJob} produces. The surplus goes in the granary, the granary buys growth,
 * and an empty granary kills people.
 *
 * <p>This is what makes an abduction expensive twice over. Losing the smith costs the village some
 * prosperity. Losing two farmers tips the food balance negative, and a month later the village that
 * survived the raid is starving because of it - which is a slower, more interesting problem for a
 * player to walk into than a burned house.
 */
public final class VillageEconomy {
	/** What one person eats per day, named or not. The unit everything else here is priced in. */
	public static final double FOOD_PER_PERSON = 1.0;
	/** Unnamed population is subsistence: it feeds itself exactly and contributes nothing spare. */
	public static final double UNNAMED_FOOD_PRODUCTION = 1.0;
	/** What a blighted field yields, as a fraction of normal. Low enough that a blighted village
	 *  runs a deficit however it shuffles its people - the only ways out are stores, outside food,
	 *  or somebody lifting it. */
	public static final double BLIGHT_YIELD = 0.4;
	/** Build points needed for one new building. */
	public static final double BUILD_COST = 20.0;
	/** Days of food a village wants banked before it will grow or found anything. */
	public static final double COMFORTABLE_STORES_DAYS = 8.0;
	/** Chance per day that a child grows into a working adult. Averages a bit under a fortnight. */
	private static final int CHILD_MATURES_PERCENT = 8;
	/** Chance per day that a settled resident changes trade. Low on purpose: people don't retrain
	 *  every week, and a roster that churns daily reads as noise rather than as a village. Someone
	 *  with no job at all is placed immediately regardless. */
	private static final int REASSIGN_PERCENT = 5;

	private VillageEconomy() {
	}

	// ------------------------------------------------------------------------------- derived
	/**
	 * Rebuilds the numbers that come from who lives here and what they do: garrison, food
	 * production, food upkeep. Cheap, and safe to call as often as you like.
	 */
	public static void recompute(VillageStore store, VillageRecord village) {
		List<NpcRecord> residents = store.residentsOf(village);
		double garrison = 0;
		double production = 0;
		for (NpcRecord npc : residents) {
			garrison += npc.job().garrison() * npc.combatValue();
			production += npc.job().foodProduction();
		}
		int unnamed = Math.max(0, village.population() - residents.size());
		production += unnamed * UNNAMED_FOOD_PRODUCTION;
		production += village.buildingFoodProduction();
		if (village.isBlighted())
			production *= BLIGHT_YIELD;
		village.setDerived(garrison + village.buildingGarrison(), production, village.population() * FOOD_PER_PERSON);
	}

	/** Rebuilds every village's derived numbers. Run on load, before anything reads a defense value. */
	public static void recomputeAll(VillageStore store) {
		for (VillageRecord village : store.villages()) {
			recompute(store, village);
		}
	}

	/** How many named residents hold a given job. */
	public static int countJob(VillageStore store, VillageRecord village, NpcJob job) {
		int count = 0;
		for (NpcRecord npc : store.residentsOf(village)) {
			if (npc.job() == job)
				count++;
		}
		return count;
	}

	public static Map<NpcJob, Integer> jobCensus(VillageStore store, VillageRecord village) {
		Map<NpcJob, Integer> census = new EnumMap<>(NpcJob.class);
		for (NpcRecord npc : store.residentsOf(village)) {
			census.merge(npc.job(), 1, Integer::sum);
		}
		return census;
	}

	// ----------------------------------------------------------------------------- the day
	/**
	 * One day of living: work done, food eaten, buildings raised, children grown, jobs reshuffled.
	 * Called by {@link VillageSimulator} before the day's events are rolled, so an event resolves
	 * against a village that has already had its breakfast.
	 *
	 * @return narrative lines worth telling someone about, usually empty
	 */
	public static List<String> tickDay(VillageStore store, VillageWorldBridge bridge, VillageRecord village, long day, Random random) {
		List<String> notes = new ArrayList<>();
		if (village.isBlighted()) {
			village.setBlightDays(village.blightDays() - 1);
			if (!village.isBlighted())
				notes.add("The blight on " + village.name() + "'s fields has broken. They can grow food again.");
		}
		List<NpcRecord> residents = store.residentsOf(village);
		recompute(village, residents);

		double prosperityGain = 0;
		double wardUpkeep = 0;
		double buildPoints = 0;
		double moraleGain = 0;
		for (NpcRecord npc : residents) {
			prosperityGain += npc.job().prosperity();
			wardUpkeep += npc.job().wardUpkeep();
			buildPoints += npc.job().buildPoints();
			moraleGain += npc.job().morale();
		}
		// Buildings work too - a shrine keeps its own thin ward lit, a tavern keeps people cheerful.
		village.setProsperity(village.prosperity() + prosperityGain + village.buildingProsperity());
		village.setMorale(village.morale() + moraleGain + village.buildingMorale());
		// A resident witch renews wards faster than they decay; everyone else only slows the loss.
		village.setWards(village.wards() + wardUpkeep + village.buildingWardUpkeep());

		buildPoints += village.population() * 0.05;
		village.setBuildProgress(village.buildProgress() + buildPoints);
		if (village.buildProgress() >= BUILD_COST) {
			village.setBuildProgress(village.buildProgress() - BUILD_COST);
			raiseBuilding(store, village, day, random, notes);
		}

		// At most one person changes what they do in a day. Hunger gets first claim on that - a
		// starving village puts someone in a field before it worries about who should be smithing.
		boolean jobChanged = feed(store, bridge, village, residents, day, random, notes);
		matureChildren(store, village, residents, day, random, notes);
		if (!jobChanged)
			reassign(store, village, residents, day, random, notes);
		recompute(store, village);
		return notes;
	}

	/** The food ledger, and what happens when it doesn't balance.
	 *
	 *  @return true if hunger moved somebody into the fields, which uses up the day's one job change */
	private static boolean feed(VillageStore store, VillageWorldBridge bridge, VillageRecord village, List<NpcRecord> residents, long day, Random random,
			List<String> notes) {
		double balance = village.foodBalance();
		double stores = village.foodStores() + balance;
		if (stores >= 0) {
			village.setFoodStores(stores);
			if (village.flags().remove("HUNGRY"))
				notes.add(village.name() + " is eating properly again.");
			// Well fed and unafraid is the only state a village grows in.
			// Fed, unafraid, and with a roof to put them under. A village that outgrows its housing
			// stops growing until somebody builds.
			boolean hasRoom = village.housing() > village.population();
			if (village.foodStores() > village.population() * COMFORTABLE_STORES_DAYS && village.morale() > 0.6 && hasRoom && random.nextInt(100) < 10) {
				village.addPopulation(1);
				village.setFoodStores(village.foodStores() - COMFORTABLE_STORES_DAYS);
				if (random.nextInt(100) < 35) {
					NpcRecord child = NpcRecord.create(VillageNames.person(random), village.villageId());
					child.setJob(NpcJob.CHILD);
					child.setSpecies(speciesFor(village));
					child.addLogEntry(day, "Born in " + village.name() + ".");
					store.add(child);
					village.residents().add(child.npcId());
					notes.add("A child was born in " + village.name() + ": " + child.name() + ".");
				}
			}
			return false;
		}

		// The granary is empty and the shortfall has to come out of people.
		village.setFoodStores(0);
		double shortfall = -stores;
		village.setMorale(village.morale() - Math.min(0.12, 0.02 + shortfall * 0.01));
		village.setProsperity(village.prosperity() - Math.min(6.0, shortfall * 0.5));
		if (!village.flags().contains("HUNGRY")) {
			village.flags().add("HUNGRY");
			notes.add(village.name() + " has eaten through its stores. They are going hungry.");
		}
		// Hunger kills slowly at first. The bigger the gap, the faster.
		if (random.nextDouble() < Math.min(0.6, shortfall / Math.max(1.0, village.population()))) {
			NpcRecord starved = pickStarvationVictim(residents);
			if (starved != null) {
				killResident(store, bridge, village, starved, day, "starved");
				notes.add(starved.name() + ", " + starved.jobLabel() + ", starved at " + village.name() + ".");
			} else if (village.population() > 0) {
				village.addPopulation(-1);
				notes.add("Someone starved at " + village.name() + ".");
			}
		}
		// Starving villages stop standing watch and start growing food - one pair of hands a day,
		// so a village stripped of its farmers takes real time to dig itself out.
		return conscriptFarmers(store, village, residents, day, notes);
	}

	/** A starving village eats its seed corn last: the ones who don't produce food go first. */
	private static NpcRecord pickStarvationVictim(List<NpcRecord> residents) {
		NpcRecord worst = null;
		for (NpcRecord npc : residents) {
			if (npc.job() == NpcJob.CHILD || npc.job() == NpcJob.ELDER)
				return npc;
			if (!npc.job().feedsOthers() && worst == null)
				worst = npc;
		}
		return worst;
	}

	/** Hunger overrides everything else: take one person off the wall and put them in a field.
	 *  Pointless while the fields are blighted, so a cursed village keeps its guards and starves
	 *  with them instead of disarming itself for nothing. */
	private static boolean conscriptFarmers(VillageStore store, VillageRecord village, List<NpcRecord> residents, long day, List<String> notes) {
		if (village.isBlighted())
			return false;
		for (NpcRecord npc : residents) {
			if (npc.job().feedsOthers() || npc.job() == NpcJob.CHILD || npc.job() == NpcJob.ELDER)
				continue;
			NpcJob was = npc.job();
			npc.setJob(NpcJob.FARMER);
			npc.addLogEntry(day, "Put to the fields; " + village.name() + " is starving.");
			store.save(npc);
			notes.add(npc.name() + " has left the " + was.displayName().toLowerCase() + "'s work for the fields.");
			return true;
		}
		return false;
	}

	private static void matureChildren(VillageStore store, VillageRecord village, List<NpcRecord> residents, long day, Random random, List<String> notes) {
		for (NpcRecord npc : residents) {
			if (npc.job() != NpcJob.CHILD || random.nextInt(100) >= CHILD_MATURES_PERCENT)
				continue;
			NpcJob job = neededJob(store, village, random);
			npc.setJob(job);
			npc.addLogEntry(day, "Grown, and put to work as a " + job.displayName().toLowerCase() + ".");
			store.save(npc);
			notes.add(npc.name() + " is old enough to work " + village.name() + "'s " + job.displayName().toLowerCase() + "'s trade now.");
			return;
		}
	}

	/** Idle residents get put to work, and occasionally someone is moved to where they're needed. */
	private static void reassign(VillageStore store, VillageRecord village, List<NpcRecord> residents, long day, Random random, List<String> notes) {
		for (NpcRecord npc : residents) {
			boolean idle = npc.job() == NpcJob.IDLE;
			if (!idle && random.nextInt(100) >= REASSIGN_PERCENT)
				continue;
			NpcJob needed = neededJob(store, village, random);
			if (needed == npc.job() || npc.job() == NpcJob.CHILD || npc.job() == NpcJob.ELDER || npc.job() == NpcJob.THRALL)
				continue;
			// Never take the food out of a village's mouth to staff a forge. A producer only moves
			// if the books still balance without their work in them.
			if (npc.job().feedsOthers() && village.foodBalance() - npc.job().netFood() <= 0)
				continue;
			npc.setJob(needed);
			npc.addLogEntry(day, "Took up work as a " + needed.displayName().toLowerCase() + " in " + village.name() + ".");
			store.save(npc);
			if (idle)
				notes.add(npc.name() + " is " + village.name() + "'s " + needed.displayName().toLowerCase() + " now.");
			return;
		}
	}

	// ------------------------------------------------------------------------ job assignment
	/**
	 * The job this village most needs filled right now.
	 *
	 * <p>Ordered by how quickly the lack kills you: food, then the watch, then wards, then trades.
	 * A village that keeps being raided ends up full of guards and short of bread, which is a
	 * failure mode worth being able to walk into.
	 */
	public static NpcJob neededJob(VillageStore store, VillageRecord village, Random random) {
		recompute(store, village);
		// 1. Nobody eats tomorrow.
		if (village.foodBalance() <= 0 || village.foodStores() < village.population() * 2.0)
			return random.nextInt(4) == 0 ? NpcJob.HUNTER : NpcJob.FARMER;
		// 2. Nothing between the village and whatever comes for it.
		double garrisonWanted = 2.0 + village.population() * 0.4;
		if (village.garrison() < garrisonWanted)
			return NpcJob.GUARD;
		// 3. Nothing between the village and a curse.
		if (village.wards() < 2.0 && countJob(store, village, NpcJob.WITCH) == 0)
			return countJob(store, village, NpcJob.HERBALIST) == 0 ? NpcJob.HERBALIST : NpcJob.WITCH;
		// 4. Comfort, in rough order of usefulness.
		if (countJob(store, village, NpcJob.MASON) == 0)
			return NpcJob.MASON;
		if (countJob(store, village, NpcJob.SMITH) == 0)
			return NpcJob.SMITH;
		NpcJob[] trades = {NpcJob.FARMER, NpcJob.MERCHANT, NpcJob.SHEPHERD, NpcJob.LABORER, NpcJob.GUARD, NpcJob.SMITH};
		for (int attempt = 0; attempt < trades.length; attempt++) {
			NpcJob candidate = trades[random.nextInt(trades.length)];
			if (candidate.allowedIn(village.faction()))
				return candidate;
		}
		return NpcJob.LABORER;
	}

	/** What species a newcomer to this village is, which is mostly the faction's own kind. */
	public static String speciesFor(VillageRecord village) {
		return switch (village.faction()) {
			case VAMPIRE_CLAN -> "Vampire";
			case DEMON_HOLD -> "Demon";
			case WITCH_COVEN -> "Witch";
			case WHITELIGHTER_SANCTUARY -> "Whitelighter";
			default -> "Human";
		};
	}

	// -------------------------------------------------------------------------------- helpers
	private static void raiseBuilding(VillageStore store, VillageRecord village, long day, Random random, List<String> notes) {
		BuildingType type = neededBuilding(store, village, random);
		village.buildings().add(new VillageBuilding(type, day, scatter(village, random)));
		village.setProsperity(village.prosperity() + 2);
		notes.add(village.name() + " has finished a " + type.displayName().toLowerCase() + ".");
	}

	/**
	 * What this village should build next, in order of what it would most regret not having.
	 *
	 * <p>Somewhere to put food comes first, always: without a pantry the village is capped at
	 * {@link VillageRecord#NO_PANTRY_CAPACITY} and cannot survive a bad month however well it farms.
	 */
	public static BuildingType neededBuilding(VillageStore store, VillageRecord village, Random random) {
		if (!village.hasPantry())
			return BuildingType.PANTRY;
		if (village.housing() <= village.population())
			return BuildingType.HOUSE;
		// A full pantry with no room to grow into is a wasted good year.
		if (village.foodStores() > village.foodCapacity() * 0.9)
			return village.countBuildings(BuildingType.GRANARY) == 0 ? BuildingType.GRANARY : BuildingType.BARN;
		if (village.foodBalance() < village.population() * 0.2)
			return BuildingType.FARM;
		// Somewhere to be blessed from, or to swear at.
		BuildingType holy = village.faction().isEvil() ? BuildingType.ALTAR : BuildingType.CHURCH;
		if (village.countBuildings(holy) == 0 && village.countBuildings(BuildingType.SHRINE) > 0)
			return holy;
		if (village.countBuildings(BuildingType.SHRINE) == 0)
			return BuildingType.SHRINE;
		if (village.countBuildings(BuildingType.WELL) == 0)
			return BuildingType.WELL;
		if (village.fortification() + village.buildingFortification() < 3)
			return random.nextBoolean() ? BuildingType.PALISADE : BuildingType.WATCHTOWER;
		if (village.countBuildings(BuildingType.SMITHY) == 0)
			return BuildingType.SMITHY;
		if (village.countBuildings(BuildingType.INFIRMARY) == 0 && village.isBlighted())
			return BuildingType.INFIRMARY;
		for (int attempt = 0; attempt < 8; attempt++) {
			BuildingType candidate = BuildingType.values()[random.nextInt(BuildingType.values().length)];
			if (candidate.suits(village.faction()))
				return candidate;
		}
		return BuildingType.HOUSE;
	}

	/** Somewhere inside the village to put a new building. Roughly placed on purpose - the exact
	 *  spot only matters for pantries, and the chest snaps to the surface when it's placed. */
	public static WorldPoint scatter(VillageRecord village, Random random) {
		int spread = Math.max(4, village.radius() / 2);
		return new WorldPoint(village.center().dimension(), village.center().x() + random.nextInt(spread * 2 + 1) - spread, village.center().y(),
				village.center().z() + random.nextInt(spread * 2 + 1) - spread);
	}

	/** Recompute against a roster already in hand, to save fetching it twice in the same tick. */
	private static void recompute(VillageRecord village, List<NpcRecord> residents) {
		double garrison = 0;
		double production = 0;
		for (NpcRecord npc : residents) {
			garrison += npc.job().garrison() * npc.combatValue();
			production += npc.job().foodProduction();
		}
		int unnamed = Math.max(0, village.population() - residents.size());
		production += unnamed * UNNAMED_FOOD_PRODUCTION;
		production += village.buildingFoodProduction();
		if (village.isBlighted())
			production *= BLIGHT_YIELD;
		village.setDerived(garrison + village.buildingGarrison(), production, village.population() * FOOD_PER_PERSON);
	}

	private static void killResident(VillageStore store, VillageWorldBridge bridge, VillageRecord village, NpcRecord npc, long day, String cause) {
		npc.setStatus(NpcStatus.DEAD);
		npc.clearFate();
		npc.setCorpseLocation(village.center());
		npc.setInWorld(false);
		npc.addLogEntry(day, cause + " at " + village.name() + ".");
		bridge.despawn(npc);
		bridge.placeCorpse(npc, village);
		store.save(npc);
		village.residents().remove(npc.npcId());
		village.addPopulation(-1);
	}
}
