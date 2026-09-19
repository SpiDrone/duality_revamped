package net.spidrotech.duality.procedures;

import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.DualityMod;

import net.minecraft.world.entity.Entity;

public class AbilitiesRadialSelectProcedure {
	public static void execute(Entity entity, String radial_value) {
		if (entity == null || radial_value == null)
			return;
		{
			DualityModVariables.PlayerVariables _vars = entity.getData(DualityModVariables.PLAYER_VARIABLES);
			_vars.selected_ability = radial_value;
			_vars.markSyncDirty();
		}
		DualityMod.LOGGER.info("Active ability set to " + radial_value);
	}
}