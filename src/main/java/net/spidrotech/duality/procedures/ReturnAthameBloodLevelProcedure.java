package net.spidrotech.duality.procedures;

import org.checkerframework.checker.units.qual.s;

import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

public class ReturnAthameBloodLevelProcedure {
	public static double execute(ItemStack itemstack) {
		if (!(itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("blood")).equals("")) {
			return new Object() {
				double convert(String s) {
					try {
						return Double.parseDouble(s.trim());
					} catch (Exception e) {
					}
					return 0;
				}
			}.convert((itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("blood")).substring(0, 1));
		}
		return 0;
	}
}