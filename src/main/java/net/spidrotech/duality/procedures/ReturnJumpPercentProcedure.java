package net.spidrotech.duality.procedures;

import net.minecraft.world.entity.Entity;

/** Ticks needed for a full leap charge at entity's current vampire rank - pair with
 *  ReturnJumpPowerProcedure as a progress bar's max/current (see UIProgressBarRenderer). */
public class ReturnJumpPercentProcedure {
	public static double execute(Entity entity) {
		double max = ReturnJumpMaxPowerProcedure.execute(entity);
		return max <= 0 ? 0 : 100.0 * Math.min(1.0, ReturnJumpPowerProcedure.execute(entity) / max);
	}
}