package net.spidrotech.duality.procedures;

import net.spidrotech.duality.DualityMod;

public class DeezProcedure {
	public static void execute() {
		for (int index = (int) 0; index <= (int) 10; index++) {
			DualityMod.LOGGER.info(index);
		}
	}
}