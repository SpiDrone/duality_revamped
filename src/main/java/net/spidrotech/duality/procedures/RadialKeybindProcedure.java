package net.spidrotech.duality.procedures;

import net.spidrotech.duality.client.DualityKeyMappings;

/** Whether the radial-menu keybind (default Left Alt, rebindable) is currently held. */
public class RadialKeybindProcedure {
	public static boolean execute() {
		return DualityKeyMappings.RADIAL_MENU.isDown();
	}
}
