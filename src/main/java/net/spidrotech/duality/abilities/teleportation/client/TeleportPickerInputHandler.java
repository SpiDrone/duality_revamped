package net.spidrotech.duality.abilities.teleportation.client;

import org.slf4j.Logger;

import net.spidrotech.duality.abilities.teleportation.TeleportNetwork;
import net.spidrotech.duality.abilities.teleportation.TeleportMarker;
import net.spidrotech.duality.abilities.teleportation.OrbAbility;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;

import java.util.UUID;
import java.util.List;

import com.mojang.logging.LogUtils;

/**
 * Right-click handling while a teleport picker is active:
 *   Entity right-click (BROWSING or LOCKED) - toggles that LivingEntity as a passenger, in both
 *     TeleportPickerClientState#selectedPassengers() (the local mirror, used for BOTH states -
 *     see class doc on that field) and ClientOrbitVisualManager (a private, caster-only
 *     spinning visual entity orbiting the target, so it's clear at a glance who's coming
 *     along). Selecting beyond OrbAbility#maxPassengers is refused outright with an action-bar
 *     message. While LOCKED, the toggle is ALSO sent to the server
 *     (ToggleTeleportPassengerPayload) since Orb is already charging there - the local mirror
 *     above is optimistic; the server remains authoritative and will cancel the whole charge
 *     (see TeleportNetwork#handleTogglePassenger / OrbCancelledPayload) if an addition doesn't
 *     actually fit, correcting our guess.
 *   Empty air, BROWSING - confirms whatever's currently targeted, but ONLY if
 *     TeleportPickerClientState#reachabilityOf says REACHABLE; otherwise refuses and explains
 *     why (out of range, or blocked by current passengers).
 *   Empty air, LOCKED - cancels the confirmed selection and backs out to BROWSING, telling the
 *     server to cancel the in-progress charge. Passengers and markers stay intact.
 * No-ops entirely once INACTIVE - normal interaction behaves exactly as vanilla then.
 *
 * DOUBLE-FIRING (two separate causes, both filtered below):
 *   1. HAND - both PlayerInteractEvent.EntityInteract and RightClickEmpty fire ONCE PER HAND
 *      (main hand, then off hand) for a single physical right-click - a vanilla quirk.
 *   2. SIDE - PlayerInteractEvent.EntityInteract ALSO fires on both the logical client AND the
 *      logical server, and in an integrated singleplayer server those share one JVM, so this
 *      @OnlyIn(Dist.CLIENT)-loaded class receives BOTH firings. Without filtering this out,
 *      togglePassenger(...) runs twice per click (once from each side's independent firing),
 *      which flips membership on then immediately back off - this was the actual cause of
 *      "only sometimes marks an entity as selected." event.getEntity().level().isClientSide()
 *      is the same check used everywhere else in this codebase (e.g.
 *      AbilityProjectileBase#tick) to distinguish which side is currently running, applied here
 *      for the first time to an interaction event instead of a tick.
 * Both checks together mean each physical click runs this logic exactly once.
 *
 * PRE-LOCK RANGE RE-VALIDATION: selection-time range checking alone isn't enough - a passenger
 * selected while BROWSING (not yet LOCKED) can simply walk away afterward with nothing to catch
 * it, since nothing re-checks range after the initial toggle. onClientTick below re-validates
 * every currently-selected passenger each tick (BROWSING only - once LOCKED, OrbAbility's own
 * server-side prunePassengersOutOfRange takes over), dropping and de-registering the orbit
 * visual for anything that's wandered out of PASSENGER_MAX_DISTANCE or despawned.
 *
 * EventPriority.HIGHEST + receiveCanceled=true on onEntityInteract below: TEMP DIAGNOSTIC
 * change. If passenger selection was silently doing nothing (no logs, no message, no toggle),
 * the leading suspect is some OTHER PlayerInteractEvent.EntityInteract handler (most likely
 * whatever triggers ability-casting on right click) cancelling the event before this one ever
 * ran - by default NeoForge does NOT deliver a cancelled cancelable event to subscribers unless
 * they opt in. Running first, at highest priority, and opting into cancelled events removes
 * that as a variable so the logs below are trustworthy. Once confirmed working, you can decide
 * whether to keep this priority/opt-in permanently or fix the ordering conflict at its source.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "duality", value = Dist.CLIENT)
public final class TeleportPickerInputHandler {
	// TEMP DEBUG - confirms which guard clause (if any) is eating the interaction. Remove all
	// LOGGER.info calls in this file once passenger selection is confirmed working end to end.
	private static final Logger LOGGER = LogUtils.getLogger();
	// Client-side hint only, so the UI doesn't even offer an out-of-range entity - the server
	// never trusts this and re-checks live range itself every charge tick (see OrbAbility).
	private static final double PASSENGER_MAX_DISTANCE = 5.0;

	private TeleportPickerInputHandler() {
	}

	@SubscribeEvent
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (!event.getEntity().level().isClientSide())
			return; // see class doc SIDE - only handle the logical-client firing of this event
		if (event.getHand() != InteractionHand.MAIN_HAND)
			return; // see class doc HAND - this event fires once per hand per click
		if (!TeleportPickerClientState.isActive())
			return;
		Player player = event.getEntity();
		Entity target = event.getTarget();
		if (target == player || !(target instanceof LivingEntity))
			return;
		if (player.distanceTo(target) > PASSENGER_MAX_DISTANCE)
			return;
		UUID targetId = target.getUUID();
		boolean alreadySelected = TeleportPickerClientState.selectedPassengers().contains(targetId);
		if (!alreadySelected && TeleportPickerClientState.selectedPassengers().size() >= OrbAbility.maxPassengers(player)) {
			player.displayClientMessage(Component.literal("You can't carry that many passengers.").withStyle(ChatFormatting.RED), true);
			event.setCanceled(true);
			return;
		}
		TeleportPickerClientState.togglePassenger(targetId);
		if (alreadySelected) {
			ClientOrbitVisualManager.deselect(targetId);
		} else {
			ClientOrbitVisualManager.select(targetId);
		}
		if (TeleportPickerClientState.isLocked()) {
			// Mid-charge: the server is authoritative here and will cancel the whole cast (and
			// tell us via OrbCancelledPayload) if this addition doesn't actually fit - our
			// local mirror above is a best guess for instant visual feedback, not the final word.
			PacketDistributor.sendToServer(new TeleportNetwork.ToggleTeleportPassengerPayload(targetId));
		}
		event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
		if (!event.getEntity().level().isClientSide())
			return; // see class doc SIDE - RightClickEmpty is client-only in practice, but this
					// costs nothing and keeps both handlers consistent if that ever changes
		if (event.getHand() != InteractionHand.MAIN_HAND)
			return; // see class doc HAND - this event fires once per hand per click
		if (!TeleportPickerClientState.isActive())
			return;
		Player player = event.getEntity();
		if (TeleportPickerClientState.isLocked()) {
			// Second right-click while LOCKED: back out to BROWSING and tell the server to
			// cancel whatever charging Orb instance the earlier confirm started. Passengers,
			// their orbit visuals, and markers stay intact - only the locked destination itself
			// clears.
			TeleportPickerClientState.cancelSelection();
			PacketDistributor.sendToServer(new TeleportNetwork.CancelTeleportSelectionPayload());
			return;
		}
		TeleportMarker targeted = TeleportPickerClientState.currentlyTargeted();
		if (targeted == null)
			return;
		TeleportPickerClientState.Reachability reachability = TeleportPickerClientState.reachabilityOf(targeted);
		if (reachability != TeleportPickerClientState.Reachability.REACHABLE) {
			String reason = reachability == TeleportPickerClientState.Reachability.BLOCKED_BY_PASSENGERS ? "You can't bring your current passengers there." : "That destination is out of range.";
			player.displayClientMessage(Component.literal(reason).withStyle(ChatFormatting.RED), true);
			return;
		}
		TeleportPickerClientState.confirmSelection(targeted);
		PacketDistributor.sendToServer(new TeleportNetwork.ConfirmTeleportSelectionPayload(targeted.selectionId(), List.copyOf(TeleportPickerClientState.selectedPassengers())));
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if (!TeleportPickerClientState.isActive() || TeleportPickerClientState.isLocked())
			return; // pre-lock only - see class doc PRE-LOCK RANGE RE-VALIDATION
		if (TeleportPickerClientState.selectedPassengers().isEmpty())
			return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !(mc.level instanceof ClientLevel level))
			return;
		for (UUID id : List.copyOf(TeleportPickerClientState.selectedPassengers())) {
			Entity passenger = findByUuid(level, id);
			if (passenger == null || mc.player.distanceTo(passenger) > PASSENGER_MAX_DISTANCE) {
				TeleportPickerClientState.togglePassenger(id); // removes, since it's currently present
				ClientOrbitVisualManager.deselect(id);
			}
		}
	}

	/** Same linear-scan technique used elsewhere in this codebase (e.g.
	 *  ClientOrbitVisualManager#findByUuid) - ClientLevel doesn't expose ServerLevel's
	 *  getEntity(UUID). Fine at the scale this runs at (a handful of selected passengers, once
	 *  per tick). */
	private static Entity findByUuid(ClientLevel level, UUID id) {
		for (Entity entity : level.entitiesForRendering()) {
			if (entity.getUUID().equals(id)) {
				return entity;
			}
		}
		return null;
	}
}