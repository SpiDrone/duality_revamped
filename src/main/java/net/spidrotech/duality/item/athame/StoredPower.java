package net.spidrotech.duality.item.athame;

import net.minecraft.nbt.CompoundTag;

/**
 * A power the athame took from a demon or witch it killed, waiting in the blade for someone to
 * take it in.
 *
 * @param abilityId   the power, as characters store it ("acid_spit")
 * @param proficiency how good the victim was with it, 1-10 - what the new owner starts at
 * @param sourceName  who it was taken from
 * @param demonic     true if it came from a demon; a human taking it in risks becoming one
 */
public record StoredPower(String abilityId, int proficiency, String sourceName, boolean demonic) {
	public CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("ability", abilityId);
		tag.putInt("proficiency", proficiency);
		tag.putString("source_name", sourceName);
		tag.putBoolean("demonic", demonic);
		return tag;
	}

	public static StoredPower load(CompoundTag tag) {
		if (tag == null || tag.getString("ability").isEmpty())
			return null;
		return new StoredPower(tag.getString("ability"), Math.max(1, tag.getInt("proficiency")), tag.getString("source_name"), tag.getBoolean("demonic"));
	}
}
