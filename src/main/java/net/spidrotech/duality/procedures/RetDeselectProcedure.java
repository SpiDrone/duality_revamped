package net.spidrotech.duality.procedures;

import net.spidrotech.duality.network.DualityModVariables;

import net.minecraft.world.entity.Entity;

public class RetDeselectProcedure {
	public static boolean execute(Entity entity) {
		if (entity == null)
			return false;
		return !(entity.getData(DualityModVariables.PLAYER_VARIABLES).selected_ability).isEmpty();
	}
}