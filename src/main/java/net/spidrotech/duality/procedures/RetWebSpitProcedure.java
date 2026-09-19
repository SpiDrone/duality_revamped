package net.spidrotech.duality.procedures;

import net.spidrotech.duality.network.DualityModVariables;

import net.minecraft.world.entity.Entity;

public class RetWebSpitProcedure {
	public static boolean execute(Entity entity) {
		if (entity == null)
			return false;
		return entity.getData(DualityModVariables.PLAYER_VARIABLES).EquippedAbilities.contains("web_spit");
	}
}