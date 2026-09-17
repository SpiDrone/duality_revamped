package net.spidrotech.duality.procedures;

import net.minecraft.world.entity.Entity;

public class ShouldJumpbarProcedure {
	public static boolean execute(Entity entity) {
		if (entity == null)
			return false;
		return ReturnJumpPowerProcedure.execute(entity) != 0;
	}
}