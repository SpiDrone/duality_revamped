package net.spidrotech.duality.procedures;

import net.spidrotech.duality.network.DualityModVariables;

import net.minecraft.world.entity.Entity;

public class RetOrbProcedure {
	public static boolean execute(Entity entity) {
		if (entity == null)
			return false;
		return entity.getData(DualityModVariables.PLAYER_VARIABLES).EquippedAbilities.contains("orb_normal");
	}
}