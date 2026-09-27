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
	/** The wedges client.RadialExtras adds: letting the vampire out, hiding it, and clearing the selected power. */
	public static final String VAMPIRIZE = "vampirize", HUMANIZE = "humanize", DESELECT = "deselect";

	private AbilitiesRadialHandler() {
	}

	@SubscribeEvent
	public static void commonSetup(FMLCommonSetupEvent event) {
		RadialMenuNetwork.registerHandler(WHEEL_KEY, AbilitiesRadialHandler::onSelection);
	}

	private static void onSelection(ServerPlayer player, String selection) {
		if (handleSpecial(player, selection))
			return;
		// Every other wedge keeps the old radial's convention (see RadialAbilityNetwork): it becomes
		// the selected ability that AbilityUseProcedure casts.
		DualityModVariables.PlayerVariables vars = player.getData(DualityModVariables.PLAYER_VARIABLES);
		vars.selected_ability = selection;
		vars.markSyncDirty();
	}

	/**
	 * The wedges that aren't just "select this power": deselect, vampirize/humanize and vampire
	 * mode. Returns false for anything else.
	 *
	 * <p>Called from two places, because which handler gets a radial selection depends on load
	 * order: RadialMenuNetwork keeps the FIRST one registered for a wheel, and in singleplayer the
	 * MCreator overlay's own (registered as its class loads) beats onSelection's (common setup). That
	 * overlay forwards anything its switch doesn't know here - see its default case.
	 */
	public static boolean handleSpecial(ServerPlayer player, String selection) {
		// Clears the selected power, so a stray left click can't fire lightning at a friend.
		if (DESELECT.equals(selection)) {
			DualityModVariables.PlayerVariables vars = player.getData(DualityModVariables.PLAYER_VARIABLES);
			vars.selected_ability = "";
			vars.markSyncDirty();
			return true;
		}
		// Only one of the two is ever on the wheel (see client.RadialExtras), but check the direction
		// anyway: vampirize only lets the vampire out, humanize only hides it.
		if (VAMPIRIZE.equals(selection) || HUMANIZE.equals(selection)) {
			boolean wantOut = VAMPIRIZE.equals(selection);
			if (VampireMode.isActive(player) != wantOut) {
				AbilityManager.ActivationResult result = AbilityManager.get().tryActivate(player, VampireMode.ID);
				if (!result.succeeded() && result.message() != null)
					player.displayClientMessage(result.message(), true);
			}
			return;
		}
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
