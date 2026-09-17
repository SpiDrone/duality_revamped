package net.spidrotech.duality.abilities.shapeshift;

import net.spidrone.uiapi.RadialMenuNetwork;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Server-side handler for the forms radial. spis_ui_api keys a wheel as "duality:<component name>",
 * so name the radial component in MCreator {@code radial_shapeshift_forms} and give each wedge a
 * radial value of its form id ("bat"), or "revert" for a back-to-normal wedge.
 *
 * RadialMenuNetwork keeps the first handler registered per wheel; this registers at common setup
 * so it wins over anything generated later.
 */
@EventBusSubscriber(modid = "duality")
public final class ShapeshiftRadialHandler {
	public static final String WHEEL_KEY = "duality:radial_shapeshift_forms";
	public static final String REVERT_SELECTION = "revert";

	private ShapeshiftRadialHandler() {
	}

	@SubscribeEvent
	public static void commonSetup(FMLCommonSetupEvent event) {
		RadialMenuNetwork.registerHandler(WHEEL_KEY, (player, selection) -> {
			if (REVERT_SELECTION.equals(selection))
				Shapeshift.revert(player);
			else
				Shapeshift.requestShift(player, selection);
		});
	}
}
