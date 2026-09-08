package net.spidrotech.duality.abilities.teleportation.client;

import net.spidrotech.duality.abilities.teleportation.TeleportMarker;
import net.spidrotech.duality.abilities.teleportation.TeleportGlowStyle;

import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import javax.annotation.Nullable;

import java.util.function.Function;
import java.util.UUID;
import java.util.Set;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.ArrayList;

/**
 * Client-side-only state for a teleport ability's destination picker. Three phases:
 *   INACTIVE - active() false. Nothing renders, right-click behaves normally.
 *   BROWSING - active() true, isLocked() false. All markers() render; whichever one the player
 *              is looking at gets a hover glow (see TeleportMarkerRenderer) colored by
 *              reachabilityOf() - white if fully reachable, orange if it'd be reachable WITHOUT
 *              the player's currently selected passengers, red if unreachable outright. Right-
 *              click only confirms a marker (see TeleportPickerInputHandler) when it's fully
 *              REACHABLE.
 *   LOCKED   - selectedMarker() non-null. Only that one marker renders, using glowStyle()
 *              instead. Right-clicking empty air again while LOCKED backs out to BROWSING (see
 *              cancelSelection). Right-clicking a LivingEntity while LOCKED toggles them as a
 *              passenger on the ALREADY-CHARGING cast (see TeleportPickerInputHandler /
 *              TeleportNetwork#handleTogglePassenger) rather than touching selectedPassengers
 *              here - once locked, passenger changes are server-authoritative.
 *
 * Shared by every teleport ability, not Orb-specific - whichever ability's selection triggers
 * the picker (see TeleportBrowsingWatcher) is responsible for populating this, INCLUDING the
 * reachabilityCheck function (see setReachabilityCheck), so this class never needs to know
 * Orb's own range/dimension/passenger rules directly.
 *
 * currentlyTargeted() is written once per frame by TeleportMarkerRenderer and read by
 * TeleportPickerInputHandler - single source of truth so "what's glowing" and "what right-click
 * selects" can never disagree.
 */
@OnlyIn(Dist.CLIENT)
public final class TeleportPickerClientState {
	/** Three-way outcome for "could the player teleport to this marker right now": REACHABLE -
	 *  yes, outright. BLOCKED_BY_PASSENGERS - the destination itself is in range, but the
	 *  player's currently selected passengers make it invalid (too many, or wrong realm for
	 *  passengers specifically) - shown as orange, since dropping passengers would fix it.
	 *  UNREACHABLE - out of range or the wrong dimension for the player's level, regardless of
	 *  passengers - shown as red. */
	public enum Reachability {
		REACHABLE, BLOCKED_BY_PASSENGERS, UNREACHABLE
	}

	private static boolean active = false;
	private static List<TeleportMarker> markers = new ArrayList<>();
	private static TeleportGlowStyle glowStyle = TeleportGlowStyle.WHITE;
	private static final Set<UUID> selectedPassengers = new LinkedHashSet<>();
	// Defaults to "everything is fully reachable" - if some future teleport ability never calls
	// setReachabilityCheck, markers just behave as they did before this feature existed rather
	// than silently showing every marker as unreachable.
	private static Function<TeleportMarker, Reachability> reachabilityCheck = marker -> Reachability.REACHABLE;
	@Nullable
	private static TeleportMarker currentlyTargeted = null;
	@Nullable
	private static TeleportMarker selectedMarker = null;

	private TeleportPickerClientState() {
	}

	public static boolean isActive() {
		return active;
	}

	public static void setActive(boolean value) {
		active = value;
		if (!value) {
			reset();
		}
	}

	public static List<TeleportMarker> markers() {
		return markers;
	}

	public static void setMarkers(List<TeleportMarker> value) {
		markers = value;
	}

	public static TeleportGlowStyle glowStyle() {
		return glowStyle;
	}

	/** Only used for the LOCKED marker's glow - the BROWSING hover glow uses reachabilityOf()
	 *  instead (white/orange/red), regardless of this. Defaults to WHITE if never called. */
	public static void setGlowStyle(TeleportGlowStyle value) {
		glowStyle = value;
	}

	/** Ability-specific reachability rule for the picker's BROWSING outline color and for
	 *  TeleportPickerInputHandler refusing to confirm a non-REACHABLE marker - see
	 *  TeleportBrowsingWatcher for where Orb registers its own (built from
	 *  OrbAbility#isDestinationInRange + OrbAbility#canBringPassengers). */
	public static void setReachabilityCheck(Function<TeleportMarker, Reachability> check) {
		reachabilityCheck = check != null ? check : (marker -> Reachability.REACHABLE);
	}

	public static Reachability reachabilityOf(TeleportMarker marker) {
		return reachabilityCheck.apply(marker);
	}

	public static boolean isLocked() {
		return selectedMarker != null;
	}

	@Nullable
	public static TeleportMarker selectedMarker() {
		return selectedMarker;
	}

	public static void confirmSelection(TeleportMarker marker) {
		selectedMarker = marker;
	}

	/** Backs out of LOCKED to BROWSING - markers(), glowStyle(), and selectedPassengers() are
	 *  deliberately left untouched, so whatever the player picked as passengers stays picked
	 *  while they choose a different destination. Only full reset() (via setActive(false))
	 *  clears those. */
	public static void cancelSelection() {
		selectedMarker = null;
	}

	@Nullable
	public static TeleportMarker currentlyTargeted() {
		return currentlyTargeted;
	}

	/** Written once per frame by TeleportMarkerRenderer only - not meant to be called elsewhere. */
	public static void setCurrentlyTargeted(@Nullable TeleportMarker value) {
		currentlyTargeted = value;
	}

	/** Pre-lock passenger selections only - see class doc. Once LOCKED, passenger changes go
	 *  straight to the server (TeleportNetwork#handleTogglePassenger) and this set is no longer
	 *  consulted. */
	public static Set<UUID> selectedPassengers() {
		return selectedPassengers;
	}

	public static void togglePassenger(UUID id) {
		if (!selectedPassengers.remove(id)) {
			selectedPassengers.add(id);
		}
	}

	public static void reset() {
		markers = new ArrayList<>();
		currentlyTargeted = null;
		selectedMarker = null;
		selectedPassengers.clear();
		glowStyle = TeleportGlowStyle.WHITE;
		reachabilityCheck = marker -> Reachability.REACHABLE;
	}
}