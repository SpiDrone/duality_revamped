package net.spidrotech.duality;

import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.abilities.vampire.VampireMode;

import net.spidrone.uiapi.RadialMenuNetwork;

import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side handler for the MCreator AbilitiesRadial overlay. The generated overlay sends its
 * selection through spis_ui_api's RadialMenuNetwork, but nothing registers a handler for its wheel
 * key, so until now every selection was silently dropped.
 *
 * RadialMenuNetwork keeps the FIRST handler registered per wheel. This one registers at common
 * setup, so if the plugin ever starts generating its own handler for this wheel, this one wins -
 * add any new wedge behaviour here.
 */
@EventBusSubscriber(modid = "duality")
public final class AbilitiesRadialHandler {
	public static final String WHEEL_KEY = "duality:radial_abilities_radial";
	/** Radial value to give the vampire wedge in MCreator. */
	public static final String VAMPIRE_MODE_SELECTION = "vampire_mode";

	private AbilitiesRadialHandler() {
	}

	@SubscribeEvent
	public static void commonSetup(FMLCommonSetupEvent event) {
		RadialMenuNetwork.registerHandler(WHEEL_KEY, AbilitiesRadialHandler::onSelection);
	}

	private static void onSelection(ServerPlayer player, String selection) {
		if (VAMPIRE_MODE_SELECTION.equals(selection)) {
			AbilityManager.ActivationResult result = AbilityManager.get().tryActivate(player, VampireMode.ID);
			if (!result.succeeded() && result.message() != null)
				player.displayClientMessage(result.message(), true);
			return;
		}
		// Every other wedge keeps the old radial's convention (see RadialAbilityNetwork): it becomes
		// the selected ability that AbilityUseProcedure casts.
		DualityModVariables.PlayerVariables vars = player.getData(DualityModVariables.PLAYER_VARIABLES);
		vars.selected_ability = selection;
		vars.markSyncDirty();
	}
}
