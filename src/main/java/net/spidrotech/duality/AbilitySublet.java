package net.spidrotech.duality;

import org.slf4j.Logger;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import com.mojang.logging.LogUtils;

/**
 * Single entry point for "cast whatever ability this id string names," for any caster. Despite
 * the name suggesting a fork between "projectile powers" and "real abilities," no branching is
 * actually needed: ProjectileRegistry already wraps every ProjectileDefinition in a
 * SimpleProjectileAbility and registers it into the exact same AbilityManager registry that
 * LightningStrikeAbility, OrbAbility, and every AbilityBuilder-defined power use. Casting
 * "fireball" and casting "lightning_strike" are therefore identical from here down - both are
 * just AbilityManager#tryActivate(caster, id).
 *
 * This class exists so every input source (currently the left-click network message, later
 * maybe a hotbar key or a GUI button) has ONE place to go from "raw ability id string" to
 * "cast attempt," instead of each duplicating id-parsing and failure-logging.
 */
public final class AbilitySublet {
	private static final Logger LOGGER = LogUtils.getLogger();

	private AbilitySublet() {
	}

	/** Attempts to cast whatever ability abilityId names, for any LivingEntity caster - a
	 *  player, a mob, an NPC. Accepts either plain shorthand ("fireball", assumed to be in the
	 *  "duality" namespace) or a fully-qualified string ("duality:fireball"), so callers don't
	 *  need to know or care which they're holding.
	 *
	 *  Returns the same ActivationResult AbilityManager#tryActivate would, so a caller that
	 *  wants to react to failure (a HUD message, a fizzle sound, whatever) still can - this
	 *  just removes the boilerplate of getting there, and logs failures either way instead of
	 *  silently discarding them. */
	public static AbilityManager.ActivationResult use(LivingEntity caster, String abilityId) {
		if (caster == null) {
			return AbilityManager.ActivationResult.fail(Component.literal("No caster."));
		}
		if (abilityId == null || abilityId.isBlank()) {
			return AbilityManager.ActivationResult.fail(Component.literal("No ability selected."));
		}
		ResourceLocation id = abilityId.indexOf(':') >= 0 ? ResourceLocation.parse(abilityId) : ResourceLocation.fromNamespaceAndPath("duality", abilityId);
		AbilityManager.ActivationResult result = AbilityManager.get().tryActivate(caster, id);
		if (!result.succeeded()) {
			LOGGER.info("[duality] Ability cast failed for {} ({}): {}", caster.getName().getString(), id, result.message() != null ? result.message().getString() : "unknown reason");
		}
		return result;
	}
}