import net.spidrotech.duality.charactercreation.*;

import java.util.*;

/** Exercises the creator's rules - budgets, pick limits, names, gating - with no Minecraft. */
public class CreationHarness {
	static int failures = 0;

	static void check(String label, boolean condition) {
		System.out.println((condition ? "  ok   " : "  FAIL ") + label);
		if (!condition)
			failures++;
	}

	public static void main(String[] args) {
		System.out.println("== 1. the catalog is internally consistent ==");
		check("there are races to choose from", !RaceCatalog.all().isEmpty());
		boolean baseInRange = true, picksSane = true, grantsExcluded = true, idsUnique = true, subraceIdsUnique = true;
		Set<String> seen = new HashSet<>();
		for (RaceDefinition race : RaceCatalog.all()) {
			idsUnique &= seen.add(race.id());
			picksSane &= race.abilityPicks() >= 0 && race.abilityPicks() <= race.abilityPool().size() + 2;
			for (SkillType skill : SkillType.values()) {
				int free = race.freeStat(skill);
				baseInRange &= free >= 0 && SkillType.MIN + free <= SkillType.MAX;
			}
			Set<String> subIds = new HashSet<>();
			for (SubraceDefinition subrace : race.subraces()) {
				subraceIdsUnique &= subIds.add(subrace.id());
				// A power the lineage hands over shouldn't also be offered as a choice.
				for (String granted : subrace.grantedAbilities()) {
					grantsExcluded &= !race.selectableAbilities(subrace).contains(granted);
				}
				// And a lineage bonus must never push a starting skill out of range.
				for (SkillType skill : SkillType.values()) {
					int start = race.startingSkills(subrace).get(skill);
					baseInRange &= start >= SkillType.MIN && start <= SkillType.MAX;
				}
			}
		}
		check("race ids are unique", idsUnique);
		check("lineage ids are unique within a race", subraceIdsUnique);
		check("starting skills stay in range", baseInRange);
		check("pick counts are sane", picksSane);
		check("granted powers aren't also offered as choices", grantsExcluded);
		boolean affordable = true;
		for (RaceDefinition race : RaceCatalog.all()) {
			affordable &= race.pointCost() <= CharacterDraft.STARTING_POINTS;
			for (SubraceDefinition subrace : race.subraces()) {
				affordable &= race.pointCost() + subrace.pointCost() <= CharacterDraft.STARTING_POINTS;
			}
		}
		check("no race plus lineage costs more than the pool", affordable);
		for (RaceDefinition race : RaceCatalog.all()) {
			System.out.printf("     %-14s costs %d, leaves %d, %d lineage(s), %d pick(s), gives %s%s%n", race.id(), race.pointCost(),
					CharacterDraft.STARTING_POINTS - race.pointCost(), race.subraces().size(), race.abilityPicks(), race.grantedPointsMap(null),
					race.startsUnlocked() ? "" : " [locked]");
		}

		System.out.println("== 2. locked races stay locked ==");
		check("human is open to everyone", RaceCatalog.isSelectableFor(Set.of(), "human"));
		check("vampire is not", !RaceCatalog.isSelectableFor(Set.of(), "vampire"));
		check("until it's been earned", RaceCatalog.isSelectableFor(Set.of("vampire"), "vampire"));
		check("and the display name works too", RaceCatalog.isSelectableFor(Set.of("Vampire"), "vampire"));
		check("one unlock doesn't open the rest", !RaceCatalog.isSelectableFor(Set.of("vampire"), "demon"));
		System.out.printf("     with nothing earned: %d of %d races offered%n", RaceCatalog.selectableFor(Set.of()).size(), RaceCatalog.all().size());

		System.out.println("== 3. a race costs points, and what it grants is a floor ==");
		RaceDefinition vamp = RaceCatalog.get("vampire");
		SubraceDefinition fledgling = vamp.subrace("fledgling_line");
		CharacterDraft v = new CharacterDraft();
		v.selectRace(vamp);
		v.selectSubrace(vamp, fledgling);
		System.out.printf("     vampire costs %d, leaving %d of %d to spend%n", v.raceCost(vamp, fledgling), v.pointsBudget(vamp, fledgling),
				CharacterDraft.STARTING_POINTS);
		System.out.printf("     it granted %s for free, on top of that%n", vamp.grantedPointsMap(fledgling));
		int grantTotal = vamp.grantedPointsMap(fledgling).values().stream().mapToInt(Integer::intValue).sum();
		System.out.printf("     %d free stat points, none of them out of the budget%n", grantTotal);
		check("the grants are worth more than the race cost", grantTotal > vamp.pointCost());
		check("being a vampire costs 4", vamp.pointCost() == 4);
		check("which leaves 1 to distribute", v.pointsBudget(vamp, fledgling) == CharacterDraft.STARTING_POINTS - 4);
		check("strength arrives already raised", vamp.grantedPoints(SkillType.STRENGTH, fledgling) == 2);
		check("and that shows up as the skill's value", v.skillValue(SkillType.STRENGTH, vamp, fledgling) == SkillType.MIN + 2);
		check("the granted points cannot be refunded", !v.allocate(SkillType.STRENGTH, -1, vamp, fledgling).ok());
		check("the one remaining point spends", v.allocate(SkillType.STRENGTH, 1, vamp, fledgling).ok());
		check("strength is now granted 2 plus bought 1", v.skillValue(SkillType.STRENGTH, vamp, fledgling) == SkillType.MIN + 3);
		check("the bought point can be refunded", v.allocate(SkillType.STRENGTH, -1, vamp, fledgling).ok());
		check("but not one past it", !v.allocate(SkillType.STRENGTH, -1, vamp, fledgling).ok());
		v.allocate(SkillType.STRENGTH, 1, vamp, fledgling);
		check("and a second point isn't there to spend", !v.allocate(SkillType.AGILITY, 1, vamp, fledgling).ok());

		RaceDefinition free = RaceCatalog.get("human");
		CharacterDraft h = new CharacterDraft();
		h.selectRace(free);
		check("a free race keeps the whole pool", h.pointsBudget(free, null) == CharacterDraft.STARTING_POINTS);
		System.out.printf("     human costs %d, leaving %d%n", free.pointCost(), h.pointsBudget(free, null));

		System.out.println("== 3a. free stats are free: they never touch the spending budget ==");
		// A race that costs NOTHING and still grants stats is the cleanest proof the two are
		// independent - the player keeps every point AND gets the stats.
		RaceDefinition gifted = RaceDefinition.of("gifted", "Gifted", "Free stats, no cost.", List.of(), List.of(), 0,
				Map.of(SkillType.STRENGTH, 2, SkillType.INSIGHT, 3), true, "", 0);
		CharacterDraft g = new CharacterDraft();
		g.selectRace(gifted);
		System.out.printf("     grants %s, costs %d, budget %d%n", gifted.freeStatMap(), gifted.pointCost(), g.pointsBudget(gifted, null));
		check("five free stat points granted", gifted.freeStat(SkillType.STRENGTH) + gifted.freeStat(SkillType.INSIGHT) == 5);
		check("and the full budget is still there", g.pointsBudget(gifted, null) == CharacterDraft.STARTING_POINTS);
		check("nothing has been spent", g.pointsSpent() == 0);
		check("strength reads as floor plus grant", g.skillValue(SkillType.STRENGTH, gifted, null) == SkillType.MIN + 2);
		check("insight reads as floor plus grant", g.skillValue(SkillType.INSIGHT, gifted, null) == SkillType.MIN + 3);
		// The other half: those points cannot be taken back and moved onto something else.
		check("a free point cannot be refunded", !g.allocate(SkillType.STRENGTH, -1, gifted, null).ok());
		check("not even down to the floor", g.skillValue(SkillType.STRENGTH, gifted, null) == SkillType.MIN + 2);
		check("so the budget cannot be inflated by stripping them", g.pointsRemaining(gifted, null) == CharacterDraft.STARTING_POINTS);
		for (int i = 0; i < CharacterDraft.STARTING_POINTS; i++) {
			g.allocate(SkillType.AGILITY, 1, gifted, null);
		}
		check("all five spend elsewhere, on top of the grants", g.pointsRemaining(gifted, null) == 0);
		check("and the grants are untouched by that", g.skillValue(SkillType.STRENGTH, gifted, null) == SkillType.MIN + 2);
		check("a reset refunds only what was bought", g.resetSkills().ok() && g.skillValue(SkillType.STRENGTH, gifted, null) == SkillType.MIN + 2
				&& g.pointsRemaining(gifted, null) == CharacterDraft.STARTING_POINTS);

		System.out.println("== 3b. a dearer lineage refunds what no longer fits ==");
		CharacterDraft trim = new CharacterDraft();
		RaceDefinition human = RaceCatalog.get("human");
		SubraceDefinition mortal = human.subrace("mortal");
		SubraceDefinition hunter = human.subrace("hunter");
		trim.selectRace(human);
		trim.selectSubrace(human, mortal);
		for (int i = 0; i < CharacterDraft.STARTING_POINTS; i++) {
			trim.allocate(SkillType.FORTUNE, 1, human, mortal);
		}
		check("all five spent on the free lineage", trim.pointsSpent() == CharacterDraft.STARTING_POINTS);
		trim.selectSubrace(human, hunter);
		System.out.printf("     hunter costs %d more; spent trimmed to %d of %d%n", hunter.pointCost(), trim.pointsSpent(), trim.pointsBudget(human, hunter));
		check("switching to a dearer lineage refunds the overspend", trim.pointsSpent() == trim.pointsBudget(human, hunter));
		check("and the draft is never left overspent", trim.pointsRemaining(human, hunter) >= 0);
		check("the skills step accepts it", CreationStep.SKILLS.isSatisfiedBy(trim, human, hunter));

		System.out.println("== 3c. the point budget holds ==");
		CharacterDraft draft = new CharacterDraft();
		RaceDefinition witch = RaceCatalog.get("witch");
		SubraceDefinition seer = witch.subrace("seer");
		draft.selectRace(witch);
		draft.selectSubrace(witch, seer);
		int budget = draft.pointsBudget(witch, seer);
		System.out.printf("     witch costs %d, leaving %d%n", witch.pointCost(), budget);
		check("starts with its budget", draft.pointsRemaining(witch, seer) == budget);
		for (int i = 0; i < budget; i++) {
			draft.allocate(SkillType.ENDURANCE, 1, witch, seer);
		}
		check("the budget spends", draft.pointsRemaining(witch, seer) == 0);
		check("one past it is refused", !draft.allocate(SkillType.ENDURANCE, 1, witch, seer).ok());
		check("refunding works", draft.allocate(SkillType.ENDURANCE, -1, witch, seer).ok() && draft.pointsRemaining(witch, seer) == 1);
		check("refunding past the start is refused", !draft.allocate(SkillType.STRENGTH, -1, witch, seer).ok());
		check("reset hands everything back", draft.resetSkills().ok() && draft.pointsRemaining(witch, seer) == budget);
		// Attunement starts high for a witch, so the cap should bite before the budget does.
		int attunement = draft.skillValue(SkillType.ATTUNEMENT, witch, seer);
		int room = SkillType.MAX - attunement;
		System.out.printf("     witch/seer attunement starts at %d, %d below the cap of %d%n", attunement, room, SkillType.MAX);
		boolean cappedOut = false;
		for (int i = 0; i < budget; i++) {
			if (!draft.allocate(SkillType.ATTUNEMENT, 1, witch, seer).ok())
				cappedOut = true;
		}
		check("the per-skill cap is enforced", room >= budget || cappedOut);
		check("and never exceeded", draft.skillValue(SkillType.ATTUNEMENT, witch, seer) <= SkillType.MAX);

		System.out.println("== 4. powers are bounded by the pool and the pick count ==");
		CharacterDraft picks = new CharacterDraft();
		picks.selectRace(witch);
		picks.selectSubrace(witch, seer);
		List<String> pool = witch.selectableAbilities(seer);
		check("there's a pool to choose from", pool.size() > witch.abilityPicks());
		for (int i = 0; i < witch.abilityPicks(); i++) {
			check("pick " + (i + 1) + " taken", picks.toggleAbility(pool.get(i), witch, seer).ok());
		}
		check("one pick too many is refused", !picks.toggleAbility(pool.get(witch.abilityPicks()), witch, seer).ok());
		check("dropping one frees a slot", picks.toggleAbility(pool.get(0), witch, seer).ok() && picks.abilityPicksRemaining(witch, seer) == 1);
		check("a power outside the pool is refused", !picks.toggleAbility("nuclear_option", witch, seer).ok());

		System.out.println("== 5. changing your mind cleans up after you ==");
		CharacterDraft mind = new CharacterDraft();
		mind.selectRace(witch);
		mind.selectSubrace(witch, seer);
		mind.toggleAbility(witch.selectableAbilities(seer).get(0), witch, seer);
		mind.allocate(SkillType.ENDURANCE, 2, witch, seer);
		mind.setName("Idra Vane");
		check("choices are held", !mind.abilityIds().isEmpty() && mind.pointsSpent() == 2);
		RaceDefinition vampire = RaceCatalog.get("vampire");
		mind.selectRace(vampire);
		check("switching race drops the old lineage", mind.subraceId().isEmpty());
		check("switching race drops the old powers", mind.abilityIds().isEmpty());
		check("switching race refunds the points", mind.pointsSpent() == 0);
		check("but the name is kept - it isn't race-specific", mind.name().equals("Idra Vane"));

		SubraceDefinition court = vampire.subrace("crimson_court");
		SubraceDefinition ashen = vampire.subrace("ashen_line");
		mind.selectSubrace(vampire, court);
		// vampire_mode is granted by the Crimson Court, so it is NOT in their choosable pool -
		// take something that is, then switch to a lineage that grants it.
		String kept = vampire.selectableAbilities(court).get(0);
		mind.toggleAbility(kept, vampire, court);
		mind.selectSubrace(vampire, ashen);
		check("a pick the new lineage still offers survives", mind.abilityIds().contains(kept) == vampire.selectableAbilities(ashen).contains(kept));

		System.out.println("== 6. names ==");
		check("empty is refused", !CharacterDraft.isNameUsable(""));
		check("whitespace is refused", !CharacterDraft.isNameUsable("   "));
		check("digits alone are refused", !CharacterDraft.isNameUsable("12345"));
		check("formatting codes are refused", !CharacterDraft.isNameUsable("§cMera"));
		check("too long is refused", !CharacterDraft.isNameUsable("M".repeat(CharacterDraft.NAME_MAX + 1)));
		check("a plain name is fine", CharacterDraft.isNameUsable("Mera Holt"));
		check("apostrophes and hyphens are fine", CharacterDraft.isNameUsable("D'Arcy Ashe-Vane"));
		check("accented letters are fine", CharacterDraft.isNameUsable("Zoë"));
		check("names are trimmed", new CharacterDraft().setName("  Bryn  ").ok());

		System.out.println("== 7. you can't commit half a character ==");
		CharacterDraft gate = new CharacterDraft();
		check("nothing chosen is not complete", !gate.isComplete(null, null));
		check("and the first thing wanted is a race", gate.firstIncompleteStep(null, null) == CreationStep.RACE);
		gate.selectRace(vampire);
		check("a race alone is not enough", !gate.isComplete(vampire, null));
		check("now it wants a lineage", gate.firstIncompleteStep(vampire, null) == CreationStep.SUBRACE);
		gate.selectSubrace(vampire, court);
		check("still needs a name", !gate.isComplete(vampire, court));
		check("and says so", gate.firstIncompleteStep(vampire, court) == CreationStep.APPEARANCE);
		gate.setName("Sable Marrow");
		check("named, and now complete", gate.isComplete(vampire, court));
		check("with no powers picked at all", gate.abilityIds().isEmpty());
		check("and every point unspent - they carry over", gate.pointsSpent() == 0);

		System.out.println("== 8. the view the screens draw from ==");
		DraftView view = DraftView.of(gate, vampire, court, List.of("human", "vampire"), "hello");
		check("the race's cost carries", view.raceCost() == vampire.pointCost() + court.pointCost());
		check("the budget carries", view.pointsBudget() == CharacterDraft.STARTING_POINTS - view.raceCost());
		check("the granted floor is readable", view.baseSkill(SkillType.STRENGTH) == vampire.grantedPoints(SkillType.STRENGTH, court) + SkillType.MIN);
		check("it is active", view.active());
		check("race and lineage carry", view.raceId().equals("vampire") && view.subraceId().equals("crimson_court"));
		check("granted powers are listed separately", view.grantedAbilityIds().equals(court.grantedAbilities()));
		check("skill values match the draft", view.skill(SkillType.PRESENCE) == gate.skillValue(SkillType.PRESENCE, vampire, court));
		check("selectable races carry", view.canSelectRace("vampire") && !view.canSelectRace("demon"));
		check("completion carries", view.complete());
		check("the message carries", view.statusMessage().equals("hello"));
		check("an inactive view is safe to read", !DraftView.INACTIVE.active() && DraftView.INACTIVE.skill(SkillType.STRENGTH) == SkillType.MIN);

		System.out.println("== 9. a race with no lineages doesn't block on screen two ==");
		RaceDefinition bare = RaceDefinition.of("bare", "Bare", "No lineages.", List.of(), List.of("orb_normal"), 1, Map.of(), true, "", 0);
		CharacterDraft simple = new CharacterDraft();
		simple.selectRace(bare);
		simple.setName("Test");
		check("subrace step is satisfied by there being none", CreationStep.SUBRACE.isSatisfiedBy(simple, bare, null));
		check("and it can be committed", simple.isComplete(bare, null));

		System.out.println();
		System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
		System.exit(failures == 0 ? 0 : 1);
	}
}
