import net.spidrotech.duality.village.*;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/** Exercises the pure half of the village system with no Minecraft anywhere in sight. */
public class VillageHarness {
	static int failures = 0;

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
			npc.setRole(VillageNames.role(random));
			store.add(npc);
			ashmere.residents().add(npc.npcId());
		}
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
			for (String note : report.fateNotes()) {
				System.out.println("     day " + report.day() + " FATE: " + note);
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

		System.out.println();
		System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
		System.out.println("data written to " + dir);
		System.exit(failures == 0 ? 0 : 1);
	}

	/** How many of 400 identical raids a village with these defenses turns back. */
	static int repelCount(int militia, int fortification, double wards) {
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
