package net.spidrotech.duality.village;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * What a building is for.
 *
 * <p>Buildings used to be a count. They aren't any more: each one does something specific, and the
 * something is the reason to build it. A church is how a village gets whitelighters to come by. A
 * palisade is walls. A pantry is where the food physically is - the only place the village will
 * look for it, and the only place a player can put more.
 *
 * <p>Two of these fields aren't numbers. {@link #favors()} lists the events this building makes
 * likelier to be rolled here, which is how a church buys a village better odds of being blessed.
 * {@link #resists()} lists the events it blunts, which is how a well makes a plague survivable.
 */
public enum BuildingType {
	/** Somewhere to live. Population above the housing it has grows slower and sleeps worse. */
	HOUSE("House", 4, 0.0, 0.0, 0, 0.0, 0.0, 0.002, 0.1),
	/** Worked land. Makes every farmer in the village better at being one. */
	FARM("Farm", 0, 2.0, 0.0, 0, 0.0, 0.0, 0.0, 0.1),
	/**
	 * The food store, and the thing this whole system hangs off. A village with no pantry lives hand
	 * to mouth - see {@link VillageRecord#foodCapacity()} - and a village with one keeps its food in
	 * a real container a player can walk up to and fill.
	 */
	PANTRY("Pantry", 0, 0.0, 60.0, 0, 0.0, 0.0, 0.0, 0.0),
	/** A bigger pantry. Same job, more of it. */
	GRANARY("Granary", 0, 0.0, 140.0, 0, 0.0, 0.0, 0.0, 0.1),
	/** Livestock and stores. A small pantry that also feeds people a little. */
	BARN("Barn", 0, 0.5, 40.0, 0, 0.0, 0.0, 0.0, 0.1),

	/** Consecrated ground. Whitelighters come here, covens offer protection here, and the envoy who
	 *  turns up at night with an offer finds the elders much harder to talk to. */
	CHURCH("Church", 0, 0.0, 0.0, 0, 0.5, 0.02, 0.01, 0.2, EnumSet.of(VillageEvent.WHITELIGHTER_VISIT, VillageEvent.COVEN_PROTECTION),
			EnumSet.of(VillageEvent.DARK_PACT, VillageEvent.BLOOD_CURSE)),
	/** A smaller, older kind of holy place. Keeps a thin ward lit on its own. */
	SHRINE("Shrine", 0, 0.0, 0.0, 0, 0.0, 0.05, 0.005, 0.0, EnumSet.of(VillageEvent.COVEN_PROTECTION), EnumSet.of(VillageEvent.BLOOD_CURSE)),
	/** Where an evil settlement does its business. The mirror of a church, and it works the same way. */
	ALTAR("Altar", 0, 0.0, 0.0, 0, 1.0, 0.10, 0.0, 0.0, EnumSet.of(VillageEvent.DARK_PACT), EnumSet.of(VillageEvent.WHITELIGHTER_VISIT)),

	/** Walls. */
	PALISADE("Palisade", 0, 0.0, 0.0, 2, 0.0, 0.0, 0.0, 0.0),
	/** Walls with someone on them. */
	WATCHTOWER("Watchtower", 0, 0.0, 0.0, 1, 2.0, 0.0, 0.0, 0.0, EnumSet.noneOf(VillageEvent.class), EnumSet.of(VillageEvent.VAMPIRE_RAID)),
	/** Quarters for the watch. Makes drilling worth doing. */
	BARRACKS("Barracks", 2, 0.0, 0.0, 1, 3.0, 0.0, 0.0, 0.0, EnumSet.of(VillageEvent.MILITIA_DRILL), EnumSet.noneOf(VillageEvent.class)),
	/** Tools, nails and spearheads. */
	SMITHY("Smithy", 0, 0.0, 0.0, 0, 1.0, 0.0, 0.0, 0.4),

	/** Clean water. The difference between a sickness and an epidemic. */
	WELL("Well", 0, 0.0, 0.0, 0, 0.0, 0.0, 0.002, 0.0, EnumSet.noneOf(VillageEvent.class), EnumSet.of(VillageEvent.PLAGUE)),
	/** Somewhere to put the sick and the cursed crop. */
	INFIRMARY("Infirmary", 0, 0.0, 0.0, 0, 0.0, 0.0, 0.005, 0.2, EnumSet.noneOf(VillageEvent.class), EnumSet.of(VillageEvent.PLAGUE, VillageEvent.BLIGHT)),
	/** A reason to stay, and a reason for travellers to stop. */
	TAVERN("Tavern", 2, 0.0, 0.0, 0, 0.0, 0.0, 0.01, 0.3, EnumSet.of(VillageEvent.SETTLERS_ARRIVE), EnumSet.noneOf(VillageEvent.class));

	/** How much likelier a favored event is to be rolled, per building. */
	public static final double FAVOR_MULTIPLIER = 2.0;
	/** What fraction of the threat gets through, per resisting building. Stacks multiplicatively. */
	public static final double RESIST_FACTOR = 0.75;
	/** Threat never drops below this fraction however many buildings resist it. */
	public static final double MIN_THREAT_FRACTION = 0.35;

	private final String displayName;
	private final int housing;
	private final double foodProduction;
	private final double foodCapacity;
	private final int fortification;
	private final double garrison;
	private final double wardUpkeep;
	private final double morale;
	private final double prosperity;
	private final Set<VillageEvent> favors;
	private final Set<VillageEvent> resists;

	BuildingType(String displayName, int housing, double foodProduction, double foodCapacity, int fortification, double garrison, double wardUpkeep, double morale,
			double prosperity) {
		this(displayName, housing, foodProduction, foodCapacity, fortification, garrison, wardUpkeep, morale, prosperity, EnumSet.noneOf(VillageEvent.class),
				EnumSet.noneOf(VillageEvent.class));
	}

	BuildingType(String displayName, int housing, double foodProduction, double foodCapacity, int fortification, double garrison, double wardUpkeep, double morale,
			double prosperity, Set<VillageEvent> favors, Set<VillageEvent> resists) {
		this.displayName = displayName;
		this.housing = housing;
		this.foodProduction = foodProduction;
		this.foodCapacity = foodCapacity;
		this.fortification = fortification;
		this.garrison = garrison;
		this.wardUpkeep = wardUpkeep;
		this.morale = morale;
		this.prosperity = prosperity;
		this.favors = favors;
		this.resists = resists;
	}

	public String displayName() {
		return displayName;
	}

	public int housing() {
		return housing;
	}

	public double foodProduction() {
		return foodProduction;
	}

	/** Food units this building can hold. Anything above zero makes it part of the pantry - the
	 *  village's food lives in containers inside these, and nowhere else. */
	public double foodCapacity() {
		return foodCapacity;
	}

	public boolean storesFood() {
		return foodCapacity > 0;
	}

	public int fortification() {
		return fortification;
	}

	public double garrison() {
		return garrison;
	}

	public double wardUpkeep() {
		return wardUpkeep;
	}

	public double morale() {
		return morale;
	}

	public double prosperity() {
		return prosperity;
	}

	public Set<VillageEvent> favors() {
		return favors;
	}

	public Set<VillageEvent> resists() {
		return resists;
	}

	/** Whether a village of this faction would build one. Altars are for evil settlements, churches
	 *  aren't. */
	public boolean suits(VillageFaction faction) {
		if (this == ALTAR)
			return faction.isEvil();
		if (this == CHURCH)
			return !faction.isEvil();
		return true;
	}

	/** Lenient parse, including the loose type strings buildings were stored as before they had
	 *  meanings. */
	public static BuildingType parse(String raw) {
		if (raw == null)
			return HOUSE;
		String normalized = raw.trim().replace(' ', '_');
		for (BuildingType type : values()) {
			if (type.name().equalsIgnoreCase(normalized) || type.displayName.equalsIgnoreCase(raw.trim()))
				return type;
		}
		return switch (normalized.toUpperCase()) {
			case "WORKSHOP" -> SMITHY;
			case "STORE", "STOREHOUSE", "SILO" -> PANTRY;
			default -> HOUSE;
		};
	}

	/** The types worth naming in a command suggestion, in build order. */
	public static BuildingType[] buildOrder() {
		return Arrays.copyOf(values(), values().length);
	}
}
