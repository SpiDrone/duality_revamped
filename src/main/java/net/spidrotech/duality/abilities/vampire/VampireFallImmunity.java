package net.spidrotech.duality.abilities.vampire;

import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Vampires take no fall damage - an innate trait like VampireStatBuffs, so every rank has it.
 *
 * Zeroes the damage multiplier rather than cancelling the event: cancelling skips the whole
 * landing (sounds, particles, and any other mod's fall handling), whereas a zero multiplier only
 * removes the damage and leaves the landing looking normal.
 */
@EventBusSubscriber(modid = "duality")
public final class VampireFallImmunity {
	private VampireFallImmunity() {
	}

	@SubscribeEvent
	public static void onLivingFall(LivingFallEvent event) {
		if (VampireRank.isVampire(event.getEntity()))
			event.setDamageMultiplier(0f);
	}
}
