package net.spidrotech.duality.village;

import java.util.Random;

/**
 * Names for people and places the simulation invents on its own. A villager who gets carried off
 * needs to have had a name before it happened, or the player has nothing to go looking for.
 */
public final class VillageNames {
	private static final String[] GIVEN = {"Mera", "Tomas", "Idra", "Calder", "Wren", "Joss", "Alia", "Bryn", "Odo", "Sable", "Harrow", "Nessa", "Pell", "Rook",
			"Ilsa", "Garrick", "Mabel", "Corin", "Vesna", "Aldous", "Thessa", "Morgen", "Perrin", "Lysa"};
	private static final String[] FAMILY = {"Holt", "Ashdown", "Ferrow", "Blackmere", "Vane", "Carrow", "Sedge", "Thorn", "Quillon", "Marrow", "Fenn", "Dray",
			"Ostler", "Winnow", "Garrow", "Stell"};
	private static final String[] PLACE_PREFIX = {"Ash", "Black", "Grey", "Still", "Thorn", "Cold", "Fair", "Glass", "Red", "Hollow", "Bright", "Dun"};
	private static final String[] PLACE_SUFFIX = {"mere", "ford", "hollow", "reach", "fell", "wick", "barrow", "gate", "crest", "vale", "rest", "march"};

	private VillageNames() {
	}

	public static String person(Random random) {
		return GIVEN[random.nextInt(GIVEN.length)] + " " + FAMILY[random.nextInt(FAMILY.length)];
	}

	public static String place(Random random) {
		return PLACE_PREFIX[random.nextInt(PLACE_PREFIX.length)] + PLACE_SUFFIX[random.nextInt(PLACE_SUFFIX.length)];
	}
}
