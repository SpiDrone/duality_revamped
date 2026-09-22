package net.spidrotech.duality.abilities.teleportation;

import net.spidrotech.duality.AbilityManager;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Which registered abilities count as "teleport abilities" for the picker/networking machinery
 * in this package. Anything that IS-A OrbAbility - Orb itself, ShimmerAbility, and any future
 * subclass - automatically gets full picker support (opening/closing on selection, confirming a
 * destination, toggling passengers mid-charge - see client.TeleportBrowsingWatcher and
 * TeleportNetwork) without those classes needing to know about each concrete ability by name.
 * What stays genuinely per-ability is only what OrbAbility itself already exposes per instance -
 * range/dimension policy (allowsHeaven()) and glowStyle() - everything else (the range table,
 * Underworld access, passenger rules) is shared code, not per-ability configuration. See
 * OrbAbility's own class doc for why Heaven specifically stays angel-only regardless of level.
 *
 * "duality" is assumed as the namespace throughout this package - selected_ability (as read off
 * DualityModVariables) is stored as a bare path, matching every id lookup already in this
 * package.
 */
public final class TeleportAbilities {
	private TeleportAbilities() {
	}

	public static Optional<OrbAbility> get(ResourceLocation id) {
		return AbilityManager.get().get(id).filter(ability -> ability instanceof OrbAbility).map(ability -> (OrbAbility) ability);
	}

	/** Convenience for callers that only have the bare path a la DualityModVariables#selected_ability
	 *  (e.g. TeleportBrowsingWatcher) - selected_ability starts out "" (nothing selected yet), and
	 *  ResourceLocation rejects an empty path outright, so that case is short-circuited here rather
	 *  than left to throw. */
	public static Optional<OrbAbility> getByPath(String path) {
		if (path == null || path.isEmpty())
			return Optional.empty();
		return get(ResourceLocation.fromNamespaceAndPath("duality", path));
	}

	public static boolean isTeleportAbility(ResourceLocation id) {
		return get(id).isPresent();
	}
}
