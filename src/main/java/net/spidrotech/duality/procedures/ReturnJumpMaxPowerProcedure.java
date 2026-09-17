package net.spidrotech.duality.procedures;

import net.spidrotech.duality.abilities.vampire.LeapAbility;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;

/** Ticks needed for a full leap charge at entity's current vampire rank - pair with
 *  ReturnJumpPowerProcedure as a progress bar's max/current (see UIProgressBarRenderer). */
public class ReturnJumpMaxPowerProcedure {
	public static double execute(Entity entity) {
		return entity instanceof LivingEntity living ? LeapAbility.maxChargeTicks(living) : 0;
	}
}
