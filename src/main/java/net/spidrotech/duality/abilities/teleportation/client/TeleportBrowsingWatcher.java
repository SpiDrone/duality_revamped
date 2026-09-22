package net.spidrotech.duality.abilities.teleportation.client;

import net.spidrotech.duality.network.DualityModVariables;
import net.spidrotech.duality.abilities.teleportation.TeleportNetwork;
import net.spidrotech.duality.abilities.teleportation.TeleportAbilities;
import net.spidrotech.duality.abilities.teleportation.OrbAbility;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.client.Minecraft;

import java.util.Optional;

/**
 * Opens/closes the teleport picker automatically based on which ability is currently selected -
 * no separate key exists. The moment selected_ability becomes ANY teleport ability's id (see
 * TeleportAbilities - Orb, Shimmer, or any future OrbAbility subclass), this requests marker
 * data, sets that ability's own glow style, and registers its own reachability rule (see
 * TeleportPickerClientState#setReachabilityCheck), built from that same ability instance so a
 * demon's Shimmer correctly refuses Heaven while an angel's Orb doesn't. The moment selection
 * stops being a teleport ability, the picker closes and any orbiting passenger visuals are
 * cleared.
 *
 * Reachability rule breakdown:
 *   - white  (REACHABLE)             - OrbAbility#isDestinationInRange passes (using THIS
 *                                       ability's own allowsHeaven()) AND
 *                                       OrbAbility#canBringPassengers passes for however many
 *                                       passengers are currently selected.
 *   - orange (BLOCKED_BY_PASSENGERS) - in range, but the current passenger selection makes it
 *                                       invalid (too many, or wrong realm for passengers).
 *   - red    (UNREACHABLE)           - out of range, or the wrong dimension for this ability
 *                                       (Heaven for a non-angel), regardless of passengers.
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
		Optional<OrbAbility> wasTeleport = TeleportAbilities.getByPath(lastSelectedAbility);
		Optional<OrbAbility> nowTeleport = TeleportAbilities.getByPath(current);
		lastSelectedAbility = current;
		if (wasTeleport.equals(nowTeleport))
			return; // both empty (neither side is a teleport ability), or genuinely unchanged
		if (wasTeleport.isPresent()) {
			// Closes whichever teleport ability was previously selected - a FULL reset even when
			// the new selection is ALSO a teleport ability (Orb -> Shimmer directly, say), so no
			// stale abilityId/markers/glowStyle from the old ability survive the switch even for
			// a frame; the block below repopulates everything for the new ability right after.
			TeleportPickerClientState.setActive(false); // internally resets everything, including abilityId + glow style + reachability check
			ClientOrbitVisualManager.clearAll(); // passenger visuals aren't part of TeleportPickerClientState's own reset - clear it here
		}
		if (nowTeleport.isPresent()) {
			OrbAbility ability = nowTeleport.get();
			TeleportPickerClientState.setAbilityId(ability.id());
			TeleportPickerClientState.setGlowStyle(ability.glowStyle());
			TeleportPickerClientState.setReachabilityCheck(marker -> {
				if (!OrbAbility.isDestinationInRange(mc.player, marker.dimension(), marker.position(), ability.allowsHeaven())) {
					return TeleportPickerClientState.Reachability.UNREACHABLE;
				}
				int passengerCount = TeleportPickerClientState.selectedPassengers().size();
				boolean passengersOk = OrbAbility.canBringPassengers(mc.player, marker.dimension(), passengerCount);
				return passengersOk ? TeleportPickerClientState.Reachability.REACHABLE : TeleportPickerClientState.Reachability.BLOCKED_BY_PASSENGERS;
			});
			PacketDistributor.sendToServer(new TeleportNetwork.RequestTeleportMarkersPayload());
		}
	}
}
