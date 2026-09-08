package net.spidrotech.duality.abilities.teleportation.client;

import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.abilities.teleportation.TeleportNetwork;
import net.spidrotech.duality.abilities.teleportation.TeleportGlowStyle;
import net.spidrotech.duality.abilities.teleportation.OrbAbility;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.client.Minecraft;

/**
 * Opens/closes the teleport picker automatically based on which ability is currently selected -
 * no separate key exists. The moment selected_ability becomes Orb's id, this requests marker
 * data, sets the whitelighter glow style, and registers Orb's own reachability rule (see
 * TeleportPickerClientState#setReachabilityCheck). The moment it stops being Orb, the picker
 * closes and any orbiting passenger visuals are cleared.
 *
 * Reachability rule breakdown:
 *   - white  (REACHABLE)             - OrbAbility#isDestinationInRange passes AND
 *                                       OrbAbility#canBringPassengers passes for however many
 *                                       passengers are currently selected.
 *   - orange (BLOCKED_BY_PASSENGERS) - in range, but the current passenger selection makes it
 *                                       invalid (too many, or wrong realm for passengers).
 *   - red    (UNREACHABLE)           - out of range or wrong dimension for the player's level,
 *                                       regardless of passengers.
 *
 * Polls once per client tick and compares against the last-seen value - simple and robust,
 * matching the rest of this codebase's minimal-machinery style, rather than needing a dedicated
 * "selection changed" event.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class TeleportBrowsingWatcher {
	private static String lastSelectedAbility = "";

	private TeleportBrowsingWatcher() {
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null)
			return;
		String current = mc.player.getData(DualityModVariables.PLAYER_VARIABLES).selected_ability;
		if (current == null)
			current = "";
		if (current.equals(lastSelectedAbility))
			return;
		boolean wasOrb = OrbAbility.ID.getPath().equals(lastSelectedAbility);
		boolean isOrb = OrbAbility.ID.getPath().equals(current);
		lastSelectedAbility = current;
		if (isOrb && !wasOrb) {
			ClientOrbitVisualManager.clearAll(); // defensive - guard against stale entries from a previous session
			TeleportPickerClientState.setGlowStyle(TeleportGlowStyle.PULSE_WHITELIGHTER);
			TeleportPickerClientState.setReachabilityCheck(marker -> {
				if (!OrbAbility.isDestinationInRange(mc.player, marker.dimension(), marker.position())) {
					return TeleportPickerClientState.Reachability.UNREACHABLE;
				}
				int passengerCount = TeleportPickerClientState.selectedPassengers().size();
				boolean passengersOk = OrbAbility.canBringPassengers(mc.player, marker.dimension(), passengerCount);
				return passengersOk ? TeleportPickerClientState.Reachability.REACHABLE : TeleportPickerClientState.Reachability.BLOCKED_BY_PASSENGERS;
			});
			PacketDistributor.sendToServer(new TeleportNetwork.RequestTeleportMarkersPayload());
		} else if (wasOrb && !isOrb) {
			TeleportPickerClientState.setActive(false); // internally resets everything, including glow style + reachability check
			ClientOrbitVisualManager.clearAll(); // passenger visuals aren't part of TeleportPickerClientState's own reset - clear it here
		}
	}
}