package net.spidrotech.duality.item.athame;

import net.minecraft.nbt.CompoundTag;

/**
 * Blood taken by the athame, as it sits in the hilt and later in a vial.
 *
 * @param color       1 = red, 2 = green (acidic demons). Also the athame model's fill state.
 * @param kind        whose blood: a player's character, a village NPC, or a creature
 * @param sourceId    the character id, NPC id, or - for a creature - its entity type ("minecraft:cow")
 * @param sourceName  what to call the source on a tooltip
 * @param quality     the source's blood quality, -100..100: strength when positive, toxic when negative
 * @param doses       how much is in there, 1..{@link #MAX_DOSES}
 * @param mana        magic sapped from the source along with the blood (see AthameEvents), up to
 *                    {@link #MAX_MANA}. Whoever drinks it gets it back (see BloodDrinking).
 * @param willing     given freely - the source cut themselves for it (see AthameEvents). A person's
 *                    blood taken any other way costs a Vegan vampire the trait when drunk.
 * @param vampireRank the source's vampire rank, 1-4, or 0 if they weren't a vampire. Vampire blood
 *                    infects whoever isn't one (see Vampirism) - and remembers who, to sire them.
 * @param vegan       the source was a Vegan vampire: their blood doesn't redden a Vegan's eyes.
 */
public record BloodSample(int color, Kind kind, String sourceId, String sourceName, double quality, int doses, double mana, boolean willing, int vampireRank,
		boolean vegan) {
	public static final int RED = 1, GREEN = 2;
	public static final int MAX_DOSES = 4;
	public static final double MAX_MANA = 300;

	public enum Kind {
		/** A player's character - a person, with a soul. */
		CHARACTER,
		/** A village NPC - also a person. */
		NPC,
		/** Anything else: cows, pigs, zombies. */
		CREATURE;

		/** Whether this is a person's blood rather than an animal's. */
		public boolean sapient() {
			return this != CREATURE;
		}
	}

	/** An ordinary sample: taken, not given, from someone who isn't a vampire. */
	public BloodSample(int color, Kind kind, String sourceId, String sourceName, double quality, int doses, double mana) {
		this(color, kind, sourceId, sourceName, quality, doses, mana, false, 0, false);
	}

	public boolean sameSource(BloodSample other) {
		return other != null && kind == other.kind && sourceId.equals(other.sourceId);
	}

	public boolean isVampireBlood() {
		return vampireRank > 0;
	}

	public BloodSample withDoses(int newDoses) {
		return new BloodSample(color, kind, sourceId, sourceName, quality, Math.max(1, Math.min(MAX_DOSES, newDoses)), mana, willing, vampireRank, vegan);
	}

	public BloodSample withMana(double newMana) {
		return new BloodSample(color, kind, sourceId, sourceName, quality, doses, Math.max(0, Math.min(MAX_MANA, newMana)), willing, vampireRank, vegan);
	}

	/** Once any of it was taken rather than given, the whole hilt counts as taken. */
	public BloodSample withWilling(boolean newWilling) {
		return new BloodSample(color, kind, sourceId, sourceName, quality, doses, mana, newWilling, vampireRank, vegan);
	}

	public BloodSample asVampire(int rank, boolean isVegan) {
		return new BloodSample(color, kind, sourceId, sourceName, quality, doses, mana, willing, rank, isVegan);
	}

	/** 0 at the weakest positive blood, 1.0 at quality 25 (ordinary), 2.0 at 50 and up. Toxic
	 *  (negative) blood reads as 0. */
	public double strength() {
		return Math.max(0, Math.min(2.0, quality / 25.0));
	}

	public boolean toxic() {
		return quality < 0;
	}

	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("color", color);
		tag.putString("kind", kind.name());
		tag.putString("source_id", sourceId);
		tag.putString("source_name", sourceName);
		tag.putDouble("quality", quality);
		tag.putInt("doses", doses);
		tag.putDouble("mana", mana);
		tag.putBoolean("willing", willing);
		tag.putInt("vampire_rank", vampireRank);
		tag.putBoolean("vegan", vegan);
		return tag;
	}

	public static BloodSample load(CompoundTag tag) {
		if (tag == null || !tag.contains("color"))
			return null;
		Kind kind;
		try {
			kind = Kind.valueOf(tag.getString("kind"));
		} catch (IllegalArgumentException unknown) {
			kind = Kind.CREATURE;
		}
		return new BloodSample(tag.getInt("color") == GREEN ? GREEN : RED, kind, tag.getString("source_id"), tag.getString("source_name"), tag.getDouble("quality"),
				Math.max(1, Math.min(MAX_DOSES, tag.getInt("doses"))), Math.max(0, Math.min(MAX_MANA, tag.getDouble("mana"))), tag.getBoolean("willing"),
				Math.max(0, Math.min(4, tag.getInt("vampire_rank"))), tag.getBoolean("vegan"));
	}
}
