package net.spidrotech.duality.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The event engine. This is the {@code run(villageName, event)} the whole design hangs off.
 *
 * <p>An event is resolved, not scripted: the village's defenses are weighed against the threat,
 * both sides get a die roll, and the resulting band - repelled, costly, partial, overrun - decides
 * how much of the consequence actually lands. That's what makes reinforcing a village mean
 * something. Ten militia and a ward stone is the difference between "the watch turned them back"
 * and "they took the herbalist's daughter".
 *
 * <p>Nothing here needs the chunk to be loaded, or a player to be online, or the server to exist:
 * it reads and writes records through {@link VillageStore} and touches the world only through
 * {@link VillageWorldBridge}. {@link Villages} is the convenience layer that wires both to a
 * running server.
 */
public final class VillageEvents {
	/** How far a raiding party will travel, in blocks. Beyond this the aggressor doesn't know or
	 *  care that the village exists. */
	public static final double MAX_AGGRESSOR_DISTANCE = 1500.0;
	/** Per-roll swing applied to both attack and defense, as a fraction. */
	private static final double VARIANCE = 0.30;

	private VillageEvents() {
	}

	/** Resolves a village by name (or id) and runs the event against it. */
	public static VillageEventResult run(VillageStore store, VillageWorldBridge bridge, String villageNameOrId, VillageEvent event, long day, Random random) {
		VillageRecord village = store.resolve(villageNameOrId);
		if (village == null)
			return VillageEventResult.fizzled(null, event, day, "No village known as \"" + villageNameOrId + "\".");
		return run(store, bridge, village, event, day, random);
	}

	/**
	 * Runs one event against one village, applying every consequence and persisting the result.
	 *
	 * @param bridge how (or whether) the world is touched; {@link VillageWorldBridge#NOOP} is fine
	 * @param day    the in-game day this is happening on, which is what NPC fates count down from
	 */
	public static VillageEventResult run(VillageStore store, VillageWorldBridge bridge, VillageRecord village, VillageEvent event, long day, Random random) {
		if (event == null)
			throw new IllegalArgumentException("event must not be null");
		if (village == null)
			return VillageEventResult.fizzled(null, event, day, "No such village.");
		if (!event.canTarget(village.faction()))
			return VillageEventResult.fizzled(village, event, day, event.displayName() + " doesn't happen to a " + village.faction().displayName() + ".");
		if (village.isAbandoned() && event.hostile())
			return VillageEventResult.fizzled(village, event, day, village.name() + " is already empty; there is nothing left to attack.");

		// Defense is partly the people standing in it, so make sure the roster's contribution is
		// current before anything gets weighed against it.
		VillageEconomy.recompute(store, village);
		VillageRecord aggressor = findAggressor(store, village, event);
		VillageEventOutcome outcome = event.hostile() ? resolveHostile(store, village, event, aggressor, random) : VillageEventOutcome.BOON;

		List<String> narrative = new ArrayList<>();
		List<String> affected = new ArrayList<>();
		applyOutcome(store, bridge, village, aggressor, event, outcome, day, random, narrative, affected);

		double delta = store.applyDuality(dualityDelta(village, event, outcome));
		String headline = narrative.isEmpty() ? event.displayName() : narrative.get(0);
		village.addLogEntry(new VillageRecord.LogEntry(day, event.name(), outcome.name(), headline, delta));
		if (village.isAbandoned() && !village.flags().contains("RAZED")) {
			village.flags().add("RAZED");
			narrative.add(village.name() + " is empty. Nobody is coming back to it.");
		}
		store.save(village);
		if (aggressor != null)
			store.save(aggressor);

		for (String line : narrative) {
			bridge.announce(village, line);
		}
		return new VillageEventResult(village.villageId(), village.name(), event, outcome, day, delta, narrative, affected);
	}

	// ------------------------------------------------------------------------------ resolution
	/** The village of the faction behind this event, if one is close enough to be responsible.
	 *  Null is fine - a forced event still runs, it just has no named author. */
	public static VillageRecord findAggressor(VillageStore store, VillageRecord village, VillageEvent event) {
		if (event.perpetrator() == null)
			return null;
		return store.nearestVillage(village.center(), event.perpetrator(), MAX_AGGRESSOR_DISTANCE, village.villageId());
	}

	private static VillageEventOutcome resolveHostile(VillageStore store, VillageRecord village, VillageEvent event, VillageRecord aggressor, Random random) {
		double threat = event.baseThreat();
		if (aggressor != null) {
			threat *= aggressorStrength(aggressor);
			// A village they already hate gets the bigger war party.
			threat *= 1.0 + Math.max(0, -village.relationTo(aggressor.villageId())) / 200.0;
		}
		// A world that has already slid toward evil emboldens the things that pushed it there.
		double lean = store.world().normalized();
		if (event.evilWhenSuccessful())
			threat *= 1.0 + Math.max(0.0, -lean) * 0.35;
		else
			threat *= 1.0 - Math.max(0.0, lean) * 0.20;

		// A well against a plague, a watchtower against a raid, a church against an envoy with an
		// offer. Buildings don't add defense here so much as take the edge off the specific thing
		// they were put up for.
		threat *= village.buildingThreatFraction(event);
		double defense = village.defenseAgainst(event.threat());
		double ratio = (defense * vary(random)) / Math.max(1.0, threat * vary(random));
		if (ratio >= 1.35)
			return VillageEventOutcome.REPELLED;
		if (ratio >= 0.95)
			return VillageEventOutcome.COSTLY;
		if (ratio >= 0.60)
			return VillageEventOutcome.PARTIAL;
		return VillageEventOutcome.OVERRUN;
	}

	/** How dangerous a village is when it's the one doing the attacking. 0.6x to about 2.2x. */
	public static double aggressorStrength(VillageRecord aggressor) {
		double raw = aggressor.militia() * 3.0 + aggressor.wards() * 2.0 + aggressor.population() * 0.4;
		return Math.max(0.6, Math.min(2.2, 0.6 + raw / 45.0)) * (0.5 + 0.5 * aggressor.morale());
	}

	private static double vary(Random random) {
		return 1.0 + (random.nextDouble() * 2.0 - 1.0) * VARIANCE;
	}

	/**
	 * How far this pushes the world's duality score. Evil landing on a good village is the worst
	 * case; evil landing on an outlaw camp barely registers; an evil village prospering is itself
	 * a small slide toward evil.
	 */
	public static double dualityDelta(VillageRecord village, VillageEvent event, VillageEventOutcome outcome) {
		if (outcome == VillageEventOutcome.FIZZLED)
			return 0.0;
		double weight = event.dualityWeight();
		boolean evilVillage = village.faction().isEvil();
		if (!event.hostile()) {
			// Good things happening to bad places are bad things.
			return evilVillage ? -weight * 0.6 : weight;
		}
		if (outcome.aggressorSucceeded()) {
			double victimScale = 1.0 + 0.5 * Math.max(0.0, village.faction().alignment());
			double magnitude = weight * outcome.severity() * victimScale;
			return event.evilWhenSuccessful() ? -magnitude : magnitude;
		}
		// Held. Worth something if the people holding are worth defending.
		double held = outcome == VillageEventOutcome.REPELLED ? 0.25 : 0.10;
		return evilVillage ? -weight * held * 0.6 : weight * held;
	}

	// --------------------------------------------------------------------------- consequences
	private static void applyOutcome(VillageStore store, VillageWorldBridge bridge, VillageRecord village, VillageRecord aggressor, VillageEvent event,
			VillageEventOutcome outcome, long day, Random random, List<String> narrative, List<String> affected) {
		String from = aggressor != null ? aggressor.name() : null;
		switch (event) {
			case VAMPIRE_RAID -> vampireRaid(store, bridge, village, aggressor, outcome, day, random, narrative, affected, from);
			case DEMON_ATTACK -> demonAttack(store, bridge, village, outcome, day, random, narrative, affected, from);
			case BANDIT_SHAKEDOWN -> banditShakedown(village, outcome, random, narrative, from);
			case BLOOD_CURSE -> bloodCurse(store, bridge, village, outcome, day, random, narrative, affected);
			case PLAGUE -> plague(store, bridge, village, outcome, day, random, narrative, affected);
			case BLIGHT -> blight(village, outcome, random, narrative);
			case DARK_PACT -> darkPact(village, aggressor, outcome, narrative, from);
			case COVEN_PROTECTION -> covenProtection(store, village, random, narrative);
			case WHITELIGHTER_VISIT -> whitelighterVisit(village, narrative);
			case SETTLERS_ARRIVE -> settlersArrive(store, village, day, random, narrative, affected);
			case CONSTRUCTION -> construction(store, village, day, random, narrative);
			case GOOD_HARVEST -> goodHarvest(village, random, narrative);
			case MILITIA_DRILL -> militiaDrill(village, narrative);
		}
		if (aggressor != null && event.hostile()) {
			village.adjustRelation(aggressor.villageId(), outcome.aggressorSucceeded() ? -15 : -8);
			aggressor.adjustRelation(village.villageId(), outcome.aggressorSucceeded() ? 5 : -10);
		}
	}

	private static void vampireRaid(VillageStore store, VillageWorldBridge bridge, VillageRecord village, VillageRecord aggressor, VillageEventOutcome outcome,
			long day, Random random, List<String> narrative, List<String> affected, String from) {
		String source = from != null ? from : "a clan nobody could name";
		switch (outcome) {
			case REPELLED -> {
				village.setMorale(village.morale() + 0.05);
				narrative.add("The watch turned back a raiding party out of " + source + ". Nobody was taken.");
			}
			case COSTLY -> {
				village.setMorale(village.morale() - 0.03);
				village.setWards(village.wards() - 0.5);
				int lost = killAnonymous(village, random.nextInt(2));
				narrative.add("A raiding party out of " + source + " was driven off" + (lost > 0 ? ", but " + lost + " of the watch didn't come back in." : "."));
			}
			case PARTIAL -> {
				village.setMorale(village.morale() - 0.08);
				village.setProsperity(village.prosperity() - 3);
				takeCaptive(store, bridge, village, aggressor, day, random, narrative, affected, source);
			}
			case OVERRUN -> {
				village.setMorale(village.morale() - 0.15);
				village.setProsperity(village.prosperity() - 6);
				int lost = killAnonymous(village, 1 + random.nextInt(2));
				narrative.add("Raiders out of " + source + " went through " + village.name() + " unopposed" + (lost > 0 ? "; " + lost + " dead in the street." : "."));
				takeCaptive(store, bridge, village, aggressor, day, random, narrative, affected, source);
				if (random.nextBoolean())
					takeCaptive(store, bridge, village, aggressor, day, random, narrative, affected, source);
			}
			default -> {
			}
		}
	}

	private static void demonAttack(VillageStore store, VillageWorldBridge bridge, VillageRecord village, VillageEventOutcome outcome, long day, Random random,
			List<String> narrative, List<String> affected, String from) {
		String source = from != null ? from : "the underworld";
		switch (outcome) {
			case REPELLED -> {
				village.setMorale(village.morale() + 0.08);
				village.setWards(village.wards() - 1.0);
				narrative.add("Something came up out of " + source + " and the wards held. They are thinner for it.");
			}
			case COSTLY -> {
				village.setFortification(village.fortification() - 1);
				village.setMorale(village.morale() - 0.05);
				int lost = killAnonymous(village, 1 + random.nextInt(2));
				narrative.add("A demon reached the gate before it was put down. " + lost + " dead, and the gate is kindling.");
			}
			case PARTIAL -> {
				village.setFortification(village.fortification() - 1);
				village.setMorale(village.morale() - 0.15);
				village.setProsperity(village.prosperity() - 8);
				burnBuilding(village, narrative);
				killAnonymous(village, 2 + random.nextInt(3));
				killNamedResident(store, bridge, village, day, random, "burned in a demon attack", narrative, affected);
			}
			case OVERRUN -> {
				int before = village.population();
				village.setPopulation((int) Math.floor(before * 0.55));
				village.setWards(0);
				village.setFortification(Math.max(0, village.fortification() - 2));
				village.setMorale(village.morale() - 0.3);
				village.setProsperity(village.prosperity() - 20);
				burnBuilding(village, narrative);
				burnBuilding(village, narrative);
				village.setFoodStores(0);
				killNamedResident(store, bridge, village, day, random, "burned in a demon attack", narrative, affected);
				narrative.add(village.name() + " burned. " + (before - village.population()) + " dead, the wards broken, most of it still standing only because it was stone.");
			}
			default -> {
			}
		}
	}

	private static void banditShakedown(VillageRecord village, VillageEventOutcome outcome, Random random, List<String> narrative, String from) {
		String source = from != null ? from : "a camp somewhere upriver";
		switch (outcome) {
			case REPELLED -> {
				village.setMorale(village.morale() + 0.04);
				village.setProsperity(village.prosperity() + 1);
				narrative.add("Riders out of " + source + " came for a tithe and left without it.");
			}
			case COSTLY -> {
				village.setProsperity(village.prosperity() - 4);
				narrative.add("A tithe was paid to " + source + " to make them go away.");
			}
			case PARTIAL -> {
				village.setProsperity(village.prosperity() - 10);
				village.setMorale(village.morale() - 0.05);
				village.setFoodStores(village.foodStores() * 0.7);
				narrative.add(source + " took the winter stores and half the coin.");
			}
			case OVERRUN -> {
				village.setProsperity(village.prosperity() - 20);
				village.setMorale(village.morale() - 0.10);
				village.setFoodStores(village.foodStores() * 0.4);
				killAnonymous(village, random.nextInt(2));
				narrative.add(source + " emptied " + village.name() + " of everything worth carrying, the granary included.");
			}
			default -> {
			}
		}
	}

	private static void bloodCurse(VillageStore store, VillageWorldBridge bridge, VillageRecord village, VillageEventOutcome outcome, long day, Random random,
			List<String> narrative, List<String> affected) {
		switch (outcome) {
			case REPELLED -> {
				village.setWards(village.wards() - 1.0);
				narrative.add("Something was thrown at " + village.name() + " in the night. The wards drank it.");
			}
			case COSTLY -> {
				village.setWards(village.wards() - 2.0);
				village.setMorale(village.morale() - 0.05);
				narrative.add("A curse got as far as the well before it was broken. The wards are nearly spent.");
			}
			case PARTIAL -> {
				village.setWards(0);
				village.setMorale(village.morale() - 0.10);
				killNamedResident(store, bridge, village, day, random, "died of a blood curse", narrative, affected);
			}
			case OVERRUN -> {
				village.setWards(0);
				village.setMorale(village.morale() - 0.20);
				village.flags().add("CURSED");
				village.setBlightDays(village.blightDays() + 6 + random.nextInt(6));
				killAnonymous(village, 2 + random.nextInt(2));
				killNamedResident(store, bridge, village, day, random, "died of a blood curse", narrative, affected);
				narrative.add("The curse settled into " + village.name() + " itself. It will have to be lifted, not waited out.");
			}
			default -> {
			}
		}
	}

	private static void plague(VillageStore store, VillageWorldBridge bridge, VillageRecord village, VillageEventOutcome outcome, long day, Random random,
			List<String> narrative, List<String> affected) {
		switch (outcome) {
			case REPELLED -> narrative.add("A sickness went through " + village.name() + " and burned itself out.");
			case COSTLY -> {
				killAnonymous(village, 1);
				village.setProsperity(village.prosperity() - 2);
				narrative.add("A sickness took one of the old folk.");
			}
			case PARTIAL -> {
				int before = village.population();
				village.setPopulation((int) Math.round(before * 0.9));
				village.setProsperity(village.prosperity() - 6);
				village.setMorale(village.morale() - 0.08);
				narrative.add("Fever went through " + village.name() + ". " + (before - village.population()) + " buried.");
			}
			case OVERRUN -> {
				int before = village.population();
				village.setPopulation((int) Math.round(before * 0.75));
				village.setProsperity(village.prosperity() - 12);
				village.setMorale(village.morale() - 0.20);
				killNamedResident(store, bridge, village, day, random, "died of plague", narrative, affected);
				narrative.add("Plague. " + (before - village.population()) + " dead, and the living are burning the houses of the dead.");
			}
			default -> {
			}
		}
	}

	private static void blight(VillageRecord village, VillageEventOutcome outcome, Random random, List<String> narrative) {
		switch (outcome) {
			case REPELLED -> {
				village.setWards(village.wards() - 0.5);
				narrative.add("Something was worked against " + village.name() + "'s fields and didn't take.");
			}
			case COSTLY -> {
				village.setBlightDays(village.blightDays() + 3 + random.nextInt(3));
				narrative.add("A corner of " + village.name() + "'s fields has gone black. It was caught early.");
			}
			case PARTIAL -> {
				village.setBlightDays(village.blightDays() + 8 + random.nextInt(5));
				village.setFoodStores(village.foodStores() * 0.6);
				village.setMorale(village.morale() - 0.08);
				narrative.add(village.name() + "'s crop is blighted. What's in the granary is what they have.");
			}
			case OVERRUN -> {
				village.setBlightDays(village.blightDays() + 16 + random.nextInt(9));
				village.setFoodStores(0);
				village.setMorale(village.morale() - 0.18);
				village.flags().add("BLIGHTED");
				narrative.add("Nothing will grow at " + village.name() + ". The granary is empty and the fields are dead.");
			}
			default -> {
			}
		}
	}

	private static void darkPact(VillageRecord village, VillageRecord aggressor, VillageEventOutcome outcome, List<String> narrative, String from) {
		String source = from != null ? from : "something that came to the door at night";
		switch (outcome) {
			case REPELLED -> {
				village.setMorale(village.morale() + 0.06);
				narrative.add("An envoy from " + source + " made an offer. " + village.name() + " sent it away.");
			}
			case COSTLY -> {
				village.setProsperity(village.prosperity() - 3);
				narrative.add("An offer was refused, expensively.");
			}
			case PARTIAL -> {
				village.setWards(village.wards() + 2);
				village.setMilitia(village.militia() + 1);
				village.flags().add("PACT_TOUCHED");
				if (aggressor != null)
					village.adjustRelation(aggressor.villageId(), 25);
				narrative.add(village.name() + " took the offer. The nights are quieter now, and nobody will say why.");
			}
			case OVERRUN -> {
				boolean secondTime = village.flags().contains("PACT_TOUCHED");
				village.setWards(village.wards() + 3);
				village.setMilitia(village.militia() + 2);
				village.setFortification(village.fortification() + 1);
				village.flags().add("PACT_TOUCHED");
				village.flags().add("SWORN");
				if (aggressor != null)
					village.adjustRelation(aggressor.villageId(), 45);
				if (secondTime && !village.faction().isEvil()) {
					village.setFaction(VillageFaction.OUTLAW_CAMP);
					narrative.add(village.name() + " is not a village any more. Whatever it swore to the second time, it kept.");
				} else {
					narrative.add(village.name() + " swore to " + source + ". It is stronger, and it is theirs.");
				}
			}
			default -> {
			}
		}
	}

	private static void covenProtection(VillageStore store, VillageRecord village, Random random, List<String> narrative) {
		double added = 3 + random.nextInt(3);
		village.setWards(village.wards() + added);
		village.setMorale(village.morale() + 0.05);
		village.flags().add("WARDED");
		VillageRecord coven = store.nearestVillage(village.center(), VillageFaction.WITCH_COVEN, MAX_AGGRESSOR_DISTANCE, village.villageId());
		if (coven != null) {
			village.adjustRelation(coven.villageId(), 30);
			coven.adjustRelation(village.villageId(), 30);
			store.save(coven);
			narrative.add("A witch out of " + coven.name() + " has moved into " + village.name() + " and set wards over it.");
		} else {
			narrative.add("A witch has moved into " + village.name() + " and set wards over it.");
		}
	}

	private static void whitelighterVisit(VillageRecord village, List<String> narrative) {
		village.setMorale(village.morale() + 0.15);
		village.setWards(village.wards() + 1);
		village.setProsperity(village.prosperity() + 3);
		narrative.add("A whitelighter came through " + village.name() + ". The sick got up and walked, and people have been sleeping better since.");
		if (village.isBlighted()) {
			village.setBlightDays(0);
			village.flags().remove("BLIGHTED");
			narrative.add("They put their hands in the soil on the way out. " + village.name() + "'s fields are clean.");
		}
	}

	private static void settlersArrive(VillageStore store, VillageRecord village, long day, Random random, List<String> narrative, List<String> affected) {
		int arrivals = 2 + random.nextInt(3);
		village.addPopulation(arrivals);
		if (random.nextInt(3) == 0)
			village.setMilitia(village.militia() + 1);
		village.setFoodStores(village.foodStores() + arrivals * 2.0);
		NpcRecord newcomer = null;
		if (random.nextBoolean()) {
			newcomer = NpcRecord.create(VillageNames.person(random), village.villageId());
			newcomer.setSpecies(VillageEconomy.speciesFor(village));
			// Newcomers take whatever the village is short of, so a raided village fills up with
			// guards and a hungry one with farmers.
			newcomer.setJob(VillageEconomy.neededJob(store, village, random));
			newcomer.addLogEntry(day, "Arrived in " + village.name() + " as a " + newcomer.jobLabel() + ".");
			store.add(newcomer);
			village.residents().add(newcomer.npcId());
			affected.add(newcomer.npcId());
		}
		narrative.add(arrivals + " new faces in " + village.name() + (newcomer != null ? ", among them " + newcomer.name() + ", " + newcomer.jobLabel() + "." : "."));
	}

	private static void construction(VillageStore store, VillageRecord village, long day, Random random, List<String> narrative) {
		BuildingType type = VillageEconomy.neededBuilding(store, village, random);
		village.buildings().add(new VillageBuilding(type, day, VillageEconomy.scatter(village, random)));
		village.setProsperity(village.prosperity() + 4);
		String what = type.displayName().toLowerCase();
		if (type.storesFood())
			narrative.add(village.name() + " has finished a " + what + ". There is somewhere to keep food now.");
		else if (type.fortification() > 0)
			narrative.add(village.name() + " has finished a " + what + ". The place is harder to walk into than it was.");
		else if (!type.favors().isEmpty())
			narrative.add(village.name() + " has finished a " + what + ". People have started going there.");
		else
			narrative.add(village.name() + " has finished a " + what + ".");
	}

	private static void goodHarvest(VillageRecord village, Random random, List<String> narrative) {
		village.setProsperity(village.prosperity() + 8);
		village.setMorale(village.morale() + 0.06);
		double before = village.foodStores();
		village.setFoodStores(before + village.population() * 3.0);
		if (random.nextInt(3) == 0)
			village.addPopulation(1);
		double kept = village.foodStores() - before;
		if (village.hasPantry())
			narrative.add(String.format("A good year in %s. %.0f more days of food in the pantry.", village.name(), kept));
		else
			narrative.add("A good year in " + village.name() + ", and nowhere to put it. Most of it will spoil before it's eaten.");
	}

	private static void militiaDrill(VillageRecord village, List<String> narrative) {
		village.setMilitia(village.militia() + 1);
		village.setProsperity(village.prosperity() - 2);
		village.setMorale(village.morale() + 0.03);
		narrative.add(village.name() + " has been drilling. One more of them knows which end of a spear is which.");
	}

	// -------------------------------------------------------------------------------- helpers
	/** Kills unnamed population. Returns how many actually died. */
	private static int killAnonymous(VillageRecord village, int count) {
		int actual = Math.min(count, village.population());
		village.addPopulation(-actual);
		return actual;
	}

	/** Kills one named resident, leaves a corpse, and reports it. Does nothing if the village has
	 *  no named residents left - the anonymous population takes those losses instead. */
	private static NpcRecord killNamedResident(VillageStore store, VillageWorldBridge bridge, VillageRecord village, long day, Random random, String cause,
			List<String> narrative, List<String> affected) {
		List<NpcRecord> candidates = store.residentsOf(village);
		if (candidates.isEmpty())
			return null;
		NpcRecord victim = candidates.get(random.nextInt(candidates.size()));
		victim.setStatus(NpcStatus.DEAD);
		victim.clearFate();
		victim.setCorpseLocation(village.center());
		victim.addLogEntry(day, cause + " at " + village.name() + ".");
		victim.setInWorld(false);
		bridge.despawn(victim);
		bridge.placeCorpse(victim, village);
		store.save(victim);
		village.residents().remove(victim.npcId());
		village.addPopulation(-1);
		affected.add(victim.npcId());
		narrative.add(victim.name() + ", " + victim.jobLabel() + ", " + cause + ".");
		return victim;
	}

	/**
	 * Carries one resident off to the captor's village and decides what's waiting for them there.
	 *
	 * <p>This is the whole quest hook in one method. The record moves villages, the entity is
	 * despawned, and a fate with a day on it is set. Everything after this is the clock: the
	 * player has until {@link NpcRecord#fateDay()} to walk into the clan hold, and what they find
	 * when they get there depends entirely on whether they made it.
	 */
	public static NpcRecord takeCaptive(VillageStore store, VillageWorldBridge bridge, VillageRecord village, VillageRecord captor, long day, Random random,
			List<String> narrative, List<String> affected, String source) {
		List<NpcRecord> candidates = store.residentsOf(village);
		candidates.removeIf(npc -> npc.status() != NpcStatus.RESIDENT);
		if (candidates.isEmpty()) {
			int lost = killAnonymous(village, 1);
			if (lost > 0)
				narrative.add("Someone was dragged out of " + village.name() + " by " + source + ". Nobody knew their name well enough to say who.");
			return null;
		}
		// Raiders take whoever is easiest to take, which means the guards are the last to go - and
		// a village whose guards are all that's left is a village that has already lost its farmers.
		candidates.sort(Comparator.comparingDouble(npc -> npc.combatValue() + npc.job().garrison()));
		NpcRecord victim = candidates.get(random.nextInt(Math.max(1, (candidates.size() + 1) / 2)));

		village.residents().remove(victim.npcId());
		village.addPopulation(-1);
		victim.setInWorld(false);
		bridge.despawn(victim);

		if (captor == null) {
			// Taken by something with no home the world knows about. A dead-end lead, on purpose:
			// not every disappearance has to be solvable.
			victim.setStatus(NpcStatus.MISSING);
			victim.setCurrentVillageId("");
			victim.clearFate();
			victim.addLogEntry(day, "Taken from " + village.name() + ". No trail.");
			store.save(victim);
			affected.add(victim.npcId());
			narrative.add(victim.name() + " was taken out of " + village.name() + " in the night. There is no trail to follow.");
			return victim;
		}

		NpcFate fate = rollFate(captor, random);
		victim.setStatus(NpcStatus.CAPTIVE);
		victim.setCurrentVillageId(captor.villageId());
		victim.setFate(fate, day);
		victim.addLogEntry(day, "Taken from " + village.name() + " to " + captor.name() + " (" + fate.name().toLowerCase() + ").");
		store.save(victim);
		captor.captives().add(victim.npcId());
		store.save(captor);
		affected.add(victim.npcId());

		narrative.add(victim.name() + ", " + victim.jobLabel() + ", was carried out of " + village.name() + " toward " + captor.name() + "."
				+ (fate.resolvesOnTimer() ? " Whatever they mean to do, they mean to do it by day " + victim.fateDay() + "." : ""));
		return victim;
	}

	/** What the captor intends. Demons don't keep prisoners for long; vampires sometimes do. */
	private static NpcFate rollFate(VillageRecord captor, Random random) {
		int roll = random.nextInt(100);
		if (captor.faction() == VillageFaction.DEMON_HOLD)
			return roll < 45 ? NpcFate.SACRIFICE : roll < 75 ? NpcFate.IMPRISONED : NpcFate.ENSLAVED;
		if (captor.faction() == VillageFaction.VAMPIRE_CLAN)
			return roll < 35 ? NpcFate.IMPRISONED : roll < 65 ? NpcFate.TURNING : roll < 88 ? NpcFate.DRAINED : NpcFate.ENSLAVED;
		return roll < 60 ? NpcFate.IMPRISONED : NpcFate.ENSLAVED;
	}

	private static void burnBuilding(VillageRecord village, List<String> narrative) {
		if (village.buildings().isEmpty())
			return;
		VillageBuilding lost = village.buildings().remove(village.buildings().size() - 1);
		// Losing the pantry doesn't just cost a building, it costs everything that was in it - the
		// stores clamp down to whatever the remaining pantries can hold.
		double before = village.foodStores();
		// foodCapacity() just got smaller; re-clamping against it is what spills the stores that
		// building was holding.
		village.setFoodStores(before);
		if (lost.type().storesFood() && before > village.foodStores())
			narrative.add("The " + lost.type().displayName().toLowerCase() + " burned, and " + Math.round(before - village.foodStores()) + " days of food with it.");
		else
			narrative.add("The " + lost.type().displayName().toLowerCase() + " burned.");
	}
}
