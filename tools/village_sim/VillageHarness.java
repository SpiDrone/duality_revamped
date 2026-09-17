import net.spidrotech.duality.village.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/** Exercises the pure half of the village system with no Minecraft anywhere in sight. */
public class VillageHarness {
	static int failures = 0;

	/**
	 * A pantry with no Minecraft behind it: one number for what's in the chests and one for how much
	 * they hold. Enough to exercise every path VillagePantry has, in both directions.
	 */
	static class FakePantry implements VillageWorldBridge {
		double contents = 0;
		double capacity = 60;
		boolean loaded = true;

		@Override
		public double readPantry(VillageRecord village) {
			return loaded && village.hasPantry() ? contents : -1;
		}

		@Override
		public double writePantry(VillageRecord village, double delta) {
			if (!loaded)
				return 0;
			double moved = delta > 0 ? Math.min(delta, capacity - contents) : Math.max(delta, -contents);
			contents += moved;
			return moved;
		}

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
		public WorldPoint resolveSite(WorldPoint proposal) {
			return proposal;
		}

		@Override
		public void announce(VillageRecord village, String line) {
		}
	}

	static void check(String label, boolean condition) {
		System.out.println((condition ? "  ok   " : "  FAIL ") + label);
		if (!condition)
			failures++;
	}

	public static void main(String[] args) throws Exception {
		File dir = Files.createTempDirectory("duality_data").toFile();
		VillageStore store = new VillageStore(dir).load();
		Random random = new Random(20250917L);

		VillageRecord ashmere = VillageRecord.create("Ashmere", VillageFaction.HUMAN, new WorldPoint("minecraft:overworld", 100, 68, -300), 1);
		VillageRecord hold = VillageRecord.create("Blackmere Hold", VillageFaction.VAMPIRE_CLAN, new WorldPoint("minecraft:overworld", 480, 62, -240), 1);
		store.add(ashmere);
		store.add(hold);
		for (int i = 0; i < 5; i++) {
			NpcRecord npc = NpcRecord.create(VillageNames.person(random), ashmere.villageId());
			npc.setJob(i < 3 ? NpcJob.FARMER : NpcJob.LABORER);
			store.add(npc);
			ashmere.residents().add(npc.npcId());
		}
		VillageEconomy.recompute(store, ashmere);
		store.save(ashmere);

		System.out.println("== 1. files land where they should ==");
		check("villages dir written", new File(dir, "villages").list().length == 2);
		check("npcs dir written", new File(dir, "npcs").list().length == 5);

		System.out.println("== 2. a defenceless village loses people ==");
		int before = ashmere.residents().size();
		VillageEventResult raid = null;
		for (int attempt = 0; attempt < 12 && (raid == null || !raid.outcome().aggressorSucceeded()); attempt++) {
			raid = VillageEvents.run(store, VillageWorldBridge.NOOP, "Ashmere", VillageEvent.VAMPIRE_RAID, 2, random);
		}
		raid.fullReport().forEach(line -> System.out.println("     " + line));
		check("the raid landed", raid.outcome().aggressorSucceeded());
		check("somebody was taken", ashmere.residents().size() < before);
		check("world leaned evil", store.world().dualityScore() < 0);

		NpcRecord taken = store.npc(raid.affectedNpcIds().get(0));
		check("captive's record moved to the clan hold", hold.villageId().equals(taken.currentVillageId()));
		check("captive is marked captive", taken.status() == NpcStatus.CAPTIVE);
		check("clan hold lists them as a captive", hold.captives().contains(taken.npcId()));
		System.out.println("     fate: " + taken.fate() + (taken.fate().resolvesOnTimer() ? " due day " + taken.fateDay() : " (open-ended)"));

		System.out.println("== 3. json round-trips ==");
		VillageStore reloaded = new VillageStore(dir).load();
		VillageRecord ashmere2 = reloaded.villageByName("Ashmere");
		check("village found by name after reload", ashmere2 != null);
		check("residents survived the round trip", ashmere2.residents().equals(ashmere.residents()));
		check("morale survived the round trip", Math.abs(ashmere2.morale() - ashmere.morale()) < 1e-9);
		check("duality score survived the round trip", Math.abs(reloaded.world().dualityScore() - store.world().dualityScore()) < 1e-9);
		check("captive survived the round trip", reloaded.npc(taken.npcId()).fate() == taken.fate());

		System.out.println("== 4. reinforcement actually matters ==");
		System.out.printf("     %-28s %s%n", "village", "raids repelled out of 400");
		System.out.printf("     %-28s %d%n", "undefended (0 militia)", repelCount(0, 0, 0.0));
		System.out.printf("     %-28s %d%n", "8 militia", repelCount(8, 0, 0.0));
		System.out.printf("     %-28s %d%n", "8 militia + walls 5", repelCount(8, 5, 0.0));
		System.out.printf("     %-28s %d%n", "8 militia + walls 5 + wards 8", repelCount(8, 5, 8.0));
		check("militia beats no militia", repelCount(8, 0, 0.0) > repelCount(0, 0, 0.0));
		check("walls beat no walls", repelCount(8, 5, 0.0) > repelCount(8, 0, 0.0));

		System.out.println("== 5. wards are what stop a curse, not walls ==");
		VillageRecord walled = VillageRecord.create("Walls", VillageFaction.HUMAN, WorldPoint.ORIGIN, 1);
		walled.setFortification(10);
		walled.setMilitia(10);
		VillageRecord warded = VillageRecord.create("Wards", VillageFaction.HUMAN, WorldPoint.ORIGIN, 1);
		warded.setWards(10);
		check("walls barely help against MAGICAL",
				warded.defenseAgainst(ThreatType.MAGICAL) > walled.defenseAgainst(ThreatType.MAGICAL) * 2);
		check("wards barely help against PHYSICAL",
				walled.defenseAgainst(ThreatType.PHYSICAL) > warded.defenseAgainst(ThreatType.PHYSICAL) * 2);

		System.out.println("== 6. sixty days of nobody watching ==");
		store.world().setLastSimulatedDay(2);
		store.saveWorld();
		List<VillageSimulator.DayReport> reports = VillageSimulator.catchUp(store, VillageWorldBridge.NOOP, 62, random);
		int eventCount = 0;
		for (VillageSimulator.DayReport report : reports) {
			for (VillageEventResult result : report.events()) {
				eventCount++;
				System.out.println("     day " + report.day() + " " + result.summary());
			}
			for (String note : report.notes()) {
				System.out.println("     day " + report.day() + "      " + note);
			}
		}
		check("the backlog was capped at " + VillageSimulator.MAX_CATCHUP_DAYS + " days", reports.size() <= VillageSimulator.MAX_CATCHUP_DAYS);
		check("things happened", eventCount > 0);
		check("clock recorded", store.world().lastSimulatedDay() == 62);
		System.out.printf("     world duality: %.1f (%s)%n", store.world().dualityScore(), store.world().descriptor());
		System.out.printf("     %s: pop %d, militia %d, walls %d, wards %.1f, morale %.2f, prosperity %.0f%n", ashmere.name(), ashmere.population(),
				ashmere.militia(), ashmere.fortification(), ashmere.wards(), ashmere.morale(), ashmere.prosperity());

		System.out.println("== 7. fates resolve on the clock, not on arrival ==");
		NpcRecord doomed = NpcRecord.create("Test Subject", ashmere.villageId());
		store.add(doomed);
		ashmere.residents().add(doomed.npcId());
		doomed.setStatus(NpcStatus.CAPTIVE);
		doomed.setCurrentVillageId(hold.villageId());
		doomed.setFate(NpcFate.TURNING, 100);
		hold.captives().add(doomed.npcId());
		store.save(doomed);
		store.save(hold);
		VillageSimulator.resolveFates(store, VillageWorldBridge.NOOP, 100 + NpcFate.TURNING.daysToResolve() - 1, random);
		check("still a captive the day before", store.npc(doomed.npcId()).status() == NpcStatus.CAPTIVE);
		VillageSimulator.resolveFates(store, VillageWorldBridge.NOOP, 100 + NpcFate.TURNING.daysToResolve(), random);
		check("turned on the day it came due", store.npc(doomed.npcId()).status() == NpcStatus.TURNED);
		check("now lives with their captors", hold.residents().contains(doomed.npcId()));
		check("no longer listed as a captive", !hold.captives().contains(doomed.npcId()));
		check("reads as a vampire now", store.npc(doomed.npcId()).species().equals("Vampire"));

		System.out.println("== 8. rescue in time ==");
		NpcRecord saved = NpcRecord.create("Rescued Soul", ashmere.villageId());
		store.add(saved);
		saved.setStatus(NpcStatus.CAPTIVE);
		saved.setCurrentVillageId(hold.villageId());
		saved.setFate(NpcFate.DRAINED, 200);
		hold.captives().add(saved.npcId());
		store.save(saved);
		double beforeScore = store.world().dualityScore();
		check("rescue reported success", VillageSimulator.rescue(store, VillageWorldBridge.NOOP, saved, 202));
		check("home again", ashmere.residents().contains(saved.npcId()));
		check("world leaned good", store.world().dualityScore() > beforeScore);

		System.out.println("== 9. an evil village prospering is still bad news ==");
		double evilBoon = VillageEvents.dualityDelta(hold, VillageEvent.GOOD_HARVEST, VillageEventOutcome.BOON);
		double goodBoon = VillageEvents.dualityDelta(ashmere, VillageEvent.GOOD_HARVEST, VillageEventOutcome.BOON);
		check("boon on a clan hold leans evil", evilBoon < 0);
		check("boon on a human village leans good", goodBoon > 0);

		System.out.println("== 10. a long backlog skips forward without rewinding anyone ==");
		File dir2 = Files.createTempDirectory("duality_data").toFile();
		VillageStore fresh = new VillageStore(dir2).load();
		VillageRecord old = VillageRecord.create("Oldtown", VillageFaction.HUMAN, new WorldPoint("minecraft:overworld", 0, 64, 0), 1);
		VillageRecord recent = VillageRecord.create("Newtown", VillageFaction.HUMAN, new WorldPoint("minecraft:overworld", 900, 64, 0), 495);
		fresh.add(old);
		fresh.add(recent);
		fresh.world().setLastSimulatedDay(1);
		fresh.saveWorld();
		List<VillageSimulator.DayReport> caught = VillageSimulator.catchUp(fresh, VillageWorldBridge.NOOP, 500, new Random(7));
		check("500 days of backlog capped to " + VillageSimulator.MAX_CATCHUP_DAYS, caught.size() <= VillageSimulator.MAX_CATCHUP_DAYS);
		check("the stale village skipped forward", old.lastSimulatedDay() == 500);
		check("the recently founded one wasn't rewound past its founding", recent.lastSimulatedDay() >= 495);
		check("world clock landed on today", fresh.world().lastSimulatedDay() == 500);
		deleteRecursively(dir2);

		System.out.println("== 11. jobs feed the village, or don't ==");
		VillageStore econ = new VillageStore(Files.createTempDirectory("econ").toFile()).load();
		VillageRecord farm = VillageRecord.create("Farmstead", VillageFaction.HUMAN, WorldPoint.ORIGIN, 1);
		farm.setPopulation(10);
		econ.add(farm);
		List<NpcRecord> crew = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			NpcRecord npc = NpcRecord.create("Worker " + i, farm.villageId());
			npc.setJob(NpcJob.FARMER);
			econ.add(npc);
			farm.residents().add(npc.npcId());
			crew.add(npc);
		}
		VillageEconomy.recompute(econ, farm);
		System.out.printf("     5 farmers, pop 10: %+.1f food a day%n", farm.foodBalance());
		check("five farmers feed ten people", farm.foodBalance() > 0);

		for (NpcRecord npc : crew) {
			npc.setJob(NpcJob.GUARD);
		}
		VillageEconomy.recompute(econ, farm);
		System.out.printf("     5 guards,  pop 10: %+.1f food a day, garrison %.0f%n", farm.foodBalance(), farm.garrison());
		check("a village of guards starves", farm.foodBalance() < 0);
		check("but it is well defended", farm.garrison() > 15);

		System.out.println("== 12. a blight is the one hunger you can't reassign your way out of ==");
		for (NpcRecord npc : crew) {
			npc.setJob(NpcJob.FARMER);
		}
		VillageEconomy.recompute(econ, farm);
		farm.setFoodStores(20);
		System.out.printf("     5 farmers, pop 10, healthy fields: %+.1f food a day%n", farm.foodBalance());
		check("a properly staffed village feeds itself", farm.foodBalance() > 0);

		VillageEventResult blight = VillageEvents.run(econ, VillageWorldBridge.NOOP, farm, VillageEvent.BLIGHT, 2, new Random(4));
		while (!farm.isBlighted()) {
			blight = VillageEvents.run(econ, VillageWorldBridge.NOOP, farm, VillageEvent.BLIGHT, 2, random);
		}
		blight.fullReport().forEach(line -> System.out.println("     " + line));
		VillageEconomy.recompute(econ, farm);
		System.out.printf("     blighted for %d days: %+.1f food a day, %.0f days of stores%n", farm.blightDays(), farm.foodBalance(), farm.daysOfFoodLeft());
		check("the fields stop feeding anyone", farm.foodBalance() < 0);

		int startingPopulation = farm.population();
		int lowestPopulation = startingPopulation;
		boolean wentHungry = false;
		boolean starved = false;
		boolean lifted = false;
		for (long day = 3; day <= 60; day++) {
			for (String note : VillageEconomy.tickDay(econ, VillageWorldBridge.NOOP, farm, day, random)) {
				System.out.println("     day " + day + ": " + note);
				starved |= note.contains("starved");
				lifted |= note.contains("blight on");
			}
			wentHungry |= farm.flags().contains("HUNGRY");
			lowestPopulation = Math.min(lowestPopulation, farm.population());
		}
		check("the granary ran dry", wentHungry);
		check("and then it cost lives", starved);
		check("population fell before it recovered", lowestPopulation < startingPopulation);
		check("the blight broke eventually", lifted && !farm.isBlighted());
		System.out.printf("     %d at worst, %d now, %d farmers%n", lowestPopulation, farm.population(), VillageEconomy.countJob(econ, farm, NpcJob.FARMER));

		System.out.println("== 13. guards are worth more on the wall than farmers ==");
		System.out.printf("     %-28s %d%n", "4 farmers, no militia", repelCount(0, 0, 0.0, NpcJob.FARMER, 4));
		System.out.printf("     %-28s %d%n", "4 guards,  no militia", repelCount(0, 0, 0.0, NpcJob.GUARD, 4));
		check("guards repel raids farmers don't", repelCount(0, 0, 0.0, NpcJob.GUARD, 4) > repelCount(0, 0, 0.0, NpcJob.FARMER, 4));

		System.out.println("== 14. a thriving village founds another ==");
		VillageStore world2 = new VillageStore(Files.createTempDirectory("expand").toFile()).load();
		VillageRecord seed = VillageRecord.create("Seedholm", VillageFaction.HUMAN, new WorldPoint("minecraft:overworld", 0, 64, 0), 1);
		seed.setPopulation(30);
		seed.setProsperity(90);
		// It built granaries, which is what having food to spare means now.
		seed.buildings().add(new VillageBuilding(BuildingType.GRANARY, 1, seed.center()));
		seed.buildings().add(new VillageBuilding(BuildingType.GRANARY, 1, seed.center()));
		seed.setFoodStores(seed.foodCapacity());
		world2.add(seed);
		System.out.printf("     %s can store %.0f and has %.0f%n", seed.name(), seed.foodCapacity(), seed.foodStores());
		for (int i = 0; i < 8; i++) {
			NpcRecord npc = NpcRecord.create(VillageNames.person(random), seed.villageId());
			npc.setJob(i < 5 ? NpcJob.FARMER : i < 7 ? NpcJob.MASON : NpcJob.LABORER);
			world2.add(npc);
			seed.residents().add(npc.npcId());
		}
		VillageEconomy.recompute(world2, seed);
		check("a fed, rich, well-staffed village is ready to expand", VillageExpansion.canExpand(world2, seed));
		VillageRecord daughter = null;
		for (long day = 2; day <= 200 && daughter == null; day++) {
			daughter = VillageExpansion.tryFound(world2, VillageWorldBridge.NOOP, seed, day, random, new ArrayList<>());
		}
		check("it founded a daughter settlement", daughter != null);
		if (daughter != null) {
			double distance = Math.sqrt(daughter.center().horizontalDistanceSqr(seed.center()));
			System.out.printf("     %s founded %s, %.0f blocks out, pop %d%n", seed.name(), daughter.name(), distance, daughter.population());
			check("far enough out to be its own place", distance >= VillageExpansion.MIN_DISTANCE);
			check("the parent paid for it in people", seed.population() < 30);
			check("they remember where they came from", daughter.relationTo(seed.villageId()) > 50);
			check("it left with somebody who can work", !world2.residentsOf(daughter).isEmpty());
			check("the parent kept a food producer", seed.foodBalance() > 0);
		}

		System.out.println("== 15. the chest and the ledger agree, in both directions ==");
		VillageStore pantryStore = new VillageStore(Files.createTempDirectory("pantry").toFile()).load();
		VillageRecord larder = VillageRecord.create("Larderton", VillageFaction.HUMAN, WorldPoint.ORIGIN, 1);
		pantryStore.add(larder);
		FakePantry chest = new FakePantry();
		check("a new village has somewhere to put food", larder.hasPantry());

		larder.setFoodStores(20);
		VillagePantry.sync(pantryStore, chest, larder);
		System.out.printf("     ledger 20 -> chest holds %.0f%n", chest.contents);
		check("the ledger materialises into the chest", Math.abs(chest.contents - 20) < 1.5);

		chest.contents += 30; // a player walks up and empties their inventory into it
        VillagePantry.SyncResult donated = VillagePantry.sync(pantryStore, chest, larder);
		System.out.printf("     player added 30 -> ledger %.0f, reported %+.0f%n", larder.foodStores(), donated.playerDelta());
		check("food a player puts in becomes food the village has", larder.foodStores() > 45);
		check("and the donation is reported", donated.playerContributed());

		chest.loaded = false; // everyone walks away and the village eats for a fortnight
		larder.setFoodStores(larder.foodStores() - 14);
		check("nothing syncs while the chunks are cold", !VillagePantry.sync(pantryStore, chest, larder).synced());
		double eaten = chest.contents;
		chest.loaded = true;
		VillagePantry.sync(pantryStore, chest, larder);
		System.out.printf("     chest %.0f -> %.0f after a fortnight away%n", eaten, chest.contents);
		check("coming back, the chest shows what was eaten", chest.contents < eaten - 10);

		chest.contents = 0;
		chest.capacity = 10;
		larder.setFoodStores(500);
		VillagePantry.sync(pantryStore, chest, larder);
		check("the chests are the last word on how much fits", larder.foodStores() <= 10.5);

		System.out.println("== 16. no pantry, no future ==");
		VillageRecord roofless = VillageRecord.create("Roofless", VillageFaction.HUMAN, WorldPoint.ORIGIN, 1);
		roofless.buildings().clear();
		System.out.printf("     with a pantry %.0f, without %.0f%n", larder.foodCapacity(), roofless.foodCapacity());
		check("a village with nowhere to keep food can barely keep any", roofless.foodCapacity() == VillageRecord.NO_PANTRY_CAPACITY);
		check("and a pantry is worth many times that", larder.foodCapacity() > roofless.foodCapacity() * 4);
		roofless.setFoodStores(999);
		check("a good year mostly spoils", roofless.foodStores() <= VillageRecord.NO_PANTRY_CAPACITY);

		System.out.println("== 17. buildings change what kind of place a village is ==");
		VillageRecord plain = VillageRecord.create("Plain", VillageFaction.HUMAN, WorldPoint.ORIGIN, 1);
		VillageRecord devout = VillageRecord.create("Devout", VillageFaction.HUMAN, WorldPoint.ORIGIN, 1);
		devout.buildings().add(new VillageBuilding(BuildingType.CHURCH, 1, WorldPoint.ORIGIN));
		System.out.printf("     whitelighter odds: plain x%.1f, with a church x%.1f%n", plain.buildingEventFavor(VillageEvent.WHITELIGHTER_VISIT),
				devout.buildingEventFavor(VillageEvent.WHITELIGHTER_VISIT));
		check("a church brings whitelighters", devout.buildingEventFavor(VillageEvent.WHITELIGHTER_VISIT) > plain.buildingEventFavor(VillageEvent.WHITELIGHTER_VISIT));
		check("and makes a dark pact harder to sell", devout.buildingThreatFraction(VillageEvent.DARK_PACT) < 1.0);

		VillageRecord watered = VillageRecord.create("Watered", VillageFaction.HUMAN, WorldPoint.ORIGIN, 1);
		watered.buildings().add(new VillageBuilding(BuildingType.WELL, 1, WorldPoint.ORIGIN));
		check("a well blunts a plague", watered.buildingThreatFraction(VillageEvent.PLAGUE) < 1.0);
		check("but not a vampire raid", watered.buildingThreatFraction(VillageEvent.VAMPIRE_RAID) == 1.0);

		VillageRecord walled2 = VillageRecord.create("Walled", VillageFaction.HUMAN, WorldPoint.ORIGIN, 1);
		double bare = walled2.defenseAgainst(ThreatType.PHYSICAL);
		walled2.buildings().add(new VillageBuilding(BuildingType.WATCHTOWER, 1, WorldPoint.ORIGIN));
		check("a watchtower is worth real defense", walled2.defenseAgainst(ThreatType.PHYSICAL) > bare);
		check("an altar is not for human villages", !BuildingType.ALTAR.suits(VillageFaction.HUMAN));
		check("nor a church for a demon hold", !BuildingType.CHURCH.suits(VillageFaction.DEMON_HOLD));

		System.out.println("== 18. a village builds what it most needs first ==");
		VillageRecord bare2 = VillageRecord.create("Bare", VillageFaction.HUMAN, WorldPoint.ORIGIN, 1);
		bare2.buildings().clear();
		VillageStore needStore = new VillageStore(Files.createTempDirectory("need").toFile()).load();
		needStore.add(bare2);
		check("with nowhere to keep food, it builds a pantry", VillageEconomy.neededBuilding(needStore, bare2, random) == BuildingType.PANTRY);
		bare2.buildings().add(new VillageBuilding(BuildingType.PANTRY, 1, WorldPoint.ORIGIN));
		check("then somewhere to live", VillageEconomy.neededBuilding(needStore, bare2, random) == BuildingType.HOUSE);

		System.out.println();
		System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
		System.out.println("data written to " + dir);
		System.exit(failures == 0 ? 0 : 1);
	}

	static int repelCount(int militia, int fortification, double wards) {
		return repelCount(militia, fortification, wards, null, 0);
	}

	/** How many of 400 identical raids a village with these defenses turns back. */
	static int repelCount(int militia, int fortification, double wards, NpcJob job, int staff) {
		int repelled = 0;
		for (int seed = 0; seed < 400; seed++) {
			try {
				File dir = Files.createTempDirectory("dq").toFile();
				VillageStore store = new VillageStore(dir).load();
				VillageRecord village = VillageRecord.create("Target", VillageFaction.HUMAN, new WorldPoint("minecraft:overworld", 0, 64, 0), 1);
				village.setPopulation(20);
				village.setMilitia(militia);
				village.setFortification(fortification);
				village.setWards(wards);
				VillageRecord clan = VillageRecord.create("Clan", VillageFaction.VAMPIRE_CLAN, new WorldPoint("minecraft:overworld", 300, 64, 0), 1);
				store.add(village);
				store.add(clan);
				for (int i = 0; i < staff; i++) {
					NpcRecord npc = NpcRecord.create("Staff " + i, village.villageId());
					npc.setJob(job);
					store.add(npc);
					village.residents().add(npc.npcId());
				}
				VillageEconomy.recompute(store, village);
				VillageEventResult result = VillageEvents.run(store, VillageWorldBridge.NOOP, village, VillageEvent.VAMPIRE_RAID, 2, new Random(seed));
				if (!result.outcome().aggressorSucceeded())
					repelled++;
				deleteRecursively(dir);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return repelled;
	}

	static void deleteRecursively(File file) {
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				deleteRecursively(child);
			}
		}
		file.delete();
	}
}
