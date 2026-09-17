package net.spidrotech.duality.abilities.demon;

import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.Ability;

import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;

/**
 * "Flight" - elytra-style gliding, no elytra (or rockets) required. Vanilla only ever starts a
 * glide through the elytra-equipped check in Player#tryToStartFallFlying, triggered by the
 * jump-while-falling input packet - this bypasses all of that and starts the glide directly
 * whenever the caster is airborne and falling, using Player#startFallFlying, the same method that
 * check calls into. Everything past that point (steering, speed, landing) is unmodified vanilla
 * glide physics. Player-only - gliding is a Player feature, not a LivingEntity one.
 *
 * No opinion on the DEMON side - it's just a toggle; a real elytra still glides normally on top
 * of this, and unequipping it doesn't turn this off.
 */
public final class FlightAbility {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "flight");

	private FlightAbility() {
	}

	public static Ability build() {
		return Ability.builder(ID, AbilityType.TOGGLE) //
				.onTick(ctx -> {
					if (!(ctx.caster() instanceof Player player))
						return;
					if (!player.isFallFlying() && !player.onGround() && !player.getAbilities().flying && player.getDeltaMovement().y < 0) {
						player.startFallFlying();
					}
				}).build();
	}
}
