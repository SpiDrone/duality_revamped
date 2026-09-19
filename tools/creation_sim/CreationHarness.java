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
				int base = race.baseSkill(skill);
				baseInRange &= base >= SkillType.MIN && base <= SkillType.MAX;
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
		for (RaceDefinition race : RaceCatalog.all()) {
			System.out.printf("     %-14s %d lineage(s), pool %d, %d pick(s)%s%n", race.id(), race.subraces().size(), race.abilityPool().size(),
					race.abilityPicks(), race.startsUnlocked() ? "" : " [locked]");
		}

		System.out.println("== 2. locked races stay locked ==");
		check("human is open to everyone", RaceCatalog.isSelectableFor(Set.of(), "human"));
		check("vampire is not", !RaceCatalog.isSelectableFor(Set.of(), "vampire"));
		check("until it's been earned", RaceCatalog.isSelectableFor(Set.of("vampire"), "vampire"));
		check("and the display name works too", RaceCatalog.isSelectableFor(Set.of("Vampire"), "vampire"));
		check("one unlock doesn't open the rest", !RaceCatalog.isSelectableFor(Set.of("vampire"), "demon"));
		System.out.printf("     with nothing earned: %d of %d races offered%n", RaceCatalog.selectableFor(Set.of()).size(), RaceCatalog.all().size());

		System.out.println("== 3. the point budget holds ==");
		CharacterDraft draft = new CharacterDraft();
		RaceDefinition witch = RaceCatalog.get("witch");
		SubraceDefinition seer = witch.subrace("seer");
		draft.selectRace(witch);
		draft.selectSubrace(witch, seer);
		check("starts with the full budget", draft.pointsRemaining() == CharacterDraft.STARTING_POINTS);
		for (int i = 0; i < CharacterDraft.STARTING_POINTS; i++) {
			draft.allocate(SkillType.ENDURANCE, 1, witch, seer);
		}
		check("five points spend", draft.pointsRemaining() == 0);
		check("a sixth is refused", !draft.allocate(SkillType.ENDURANCE, 1, witch, seer).ok());
		check("refunding works", draft.allocate(SkillType.ENDURANCE, -1, witch, seer).ok() && draft.pointsRemaining() == 1);
		check("refunding past the start is refused", !draft.allocate(SkillType.STRENGTH, -1, witch, seer).ok());
		check("reset hands everything back", draft.resetSkills().ok() && draft.pointsRemaining() == CharacterDraft.STARTING_POINTS);
		// Attunement starts high for a witch, so the cap should bite before the budget does.
		int attunement = draft.skillValue(SkillType.ATTUNEMENT, witch, seer);
		int room = SkillType.MAX - attunement;
		System.out.printf("     witch/seer attunement starts at %d, %d below the cap of %d%n", attunement, room, SkillType.MAX);
		boolean cappedOut = false;
		for (int i = 0; i < CharacterDraft.STARTING_POINTS; i++) {
			if (!draft.allocate(SkillType.ATTUNEMENT, 1, witch, seer).ok())
				cappedOut = true;
		}
		check("the per-skill cap is enforced", room >= CharacterDraft.STARTING_POINTS || cappedOut);
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
		check("choices are held", !mind.abilityIds().isEmpty() && mind.pointsRemaining() == 3);
		RaceDefinition vampire = RaceCatalog.get("vampire");
		mind.selectRace(vampire);
		check("switching race drops the old lineage", mind.subraceId().isEmpty());
		check("switching race drops the old powers", mind.abilityIds().isEmpty());
		check("switching race refunds the points", mind.pointsRemaining() == CharacterDraft.STARTING_POINTS);
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
		check("and every point unspent - they carry over", gate.pointsRemaining() == CharacterDraft.STARTING_POINTS);

		System.out.println("== 8. the view the screens draw from ==");
		DraftView view = DraftView.of(gate, vampire, court, List.of("human", "vampire"), "hello");
		check("it is active", view.active());
		check("race and lineage carry", view.raceId().equals("vampire") && view.subraceId().equals("crimson_court"));
		check("granted powers are listed separately", view.grantedAbilityIds().equals(court.grantedAbilities()));
		check("skill values match the draft", view.skill(SkillType.PRESENCE) == gate.skillValue(SkillType.PRESENCE, vampire, court));
		check("selectable races carry", view.canSelectRace("vampire") && !view.canSelectRace("demon"));
		check("completion carries", view.complete());
		check("the message carries", view.statusMessage().equals("hello"));
		check("an inactive view is safe to read", !DraftView.INACTIVE.active() && DraftView.INACTIVE.skill(SkillType.STRENGTH) == SkillType.MIN);

		System.out.println("== 9. a race with no lineages doesn't block on screen two ==");
		RaceDefinition bare = RaceDefinition.of("bare", "Bare", "No lineages.", List.of(), List.of("orb_normal"), 1, Map.of(), true, "");
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
