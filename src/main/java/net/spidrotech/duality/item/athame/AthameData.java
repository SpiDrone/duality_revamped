package net.spidrotech.duality.item.athame;

import net.spidrotech.duality.charactercreation.AbilityCatalog;
import net.spidrotech.duality.charactercreation.AbilityDefinition;

import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;

/**
 * Reads and writes what an athame (or blood vial) carries, in the stack's custom data.
 *
 * <p>The hilt's blood and the blade's power are separate, so one athame can hold both. The
 * top-level {@code "blood"} string is kept in step with the hilt for the athame's model: the
 * MCreator procedure behind its {@code duality:athame_blood} predicate reads that string's first
 * character ("0" empty, "1" red, "2" green).
 */
public final class AthameData {
	private static final String BLOOD = "duality_blood";
	private static final String POWER = "duality_power";
	private static final String MODEL_STATE = "blood";

	private AthameData() {
	}

	private static CompoundTag tag(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
	}

	private static void write(ItemStack stack, CompoundTag tag) {
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	public static BloodSample blood(ItemStack stack) {
		CompoundTag tag = tag(stack);
		return tag.contains(BLOOD) ? BloodSample.load(tag.getCompound(BLOOD)) : null;
	}

	/** null empties the hilt. */
	public static void setBlood(ItemStack stack, BloodSample sample) {
		CompoundTag tag = tag(stack);
		if (sample == null) {
			tag.remove(BLOOD);
			tag.putString(MODEL_STATE, "0");
		} else {
			tag.put(BLOOD, sample.save());
			tag.putString(MODEL_STATE, String.valueOf(sample.color()));
		}
		write(stack, tag);
	}

	public static StoredPower power(ItemStack stack) {
		CompoundTag tag = tag(stack);
		return tag.contains(POWER) ? StoredPower.load(tag.getCompound(POWER)) : null;
	}

	/** null empties the blade. */
	public static void setPower(ItemStack stack, StoredPower power) {
		CompoundTag tag = tag(stack);
		if (power == null)
			tag.remove(POWER);
		else
			tag.put(POWER, power.save());
		write(stack, tag);
	}

	/** "acid_spit" reads as "Acid Spit", from the creator's catalog when it knows the power. */
	public static String abilityName(String abilityId) {
		AbilityDefinition known = AbilityCatalog.byAbilityId(abilityId);
		if (known != null)
			return known.displayName();
		StringBuilder out = new StringBuilder();
		for (String word : abilityId.split("_")) {
			if (word.isEmpty())
				continue;
			if (out.length() > 0)
				out.append(' ');
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}
}
