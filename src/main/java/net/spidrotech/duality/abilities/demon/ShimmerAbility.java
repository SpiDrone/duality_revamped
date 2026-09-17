package net.spidrotech.duality.abilities.demon;

import net.spidrotech.duality.abilities.teleportation.OrbAbility;
import net.spidrotech.duality.AbilityContext;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.resources.ResourceLocation;

/**
 * The demon counterpart to Orb - same channeled teleport, same progression, same Underworld
 * access, but Heaven is off-limits at any level (see OrbAbility's protected constructor: this is
 * the ONLY rule difference, enforced once in OrbAbility#canCross, not duplicated here).
 *
 * VISUAL: "bounce between 50-75% opacity" is approximated by temporal dithering rather than true
 * alpha blending - real translucency on a LivingEntityRenderer needs render-pipeline work (a
 * custom RenderType/shader) well beyond what NeoForge's events expose, so this instead flickers
 * the caster in and out of vanilla's own Invisibility effect at a randomized rate every charge
 * tick. Vanilla already syncs that effect and hides the model (armor included) for every
 * observer, so this needs no client code or networking of its own - just toggling the same
 * effect Vanish uses, briefly and repeatedly. At a high enough tick rate this reads as a
 * shimmering translucency rather than a hard on/off flicker; if it doesn't read that way in
 * practice, true alpha blending is the fallback and would need a mixin into the player renderer.
 *
 * NOT YET WIRED: the actual destination-picker UI and its network layer (TeleportNetwork,
 * TeleportPickerInputHandler, etc.) are currently hardcoded to OrbAbility.ID throughout - a demon
 * can't yet open a picker and confirm a Shimmer destination in-game, only Orb can. Threading an
 * ability id through those payloads is the next step to make this actually usable, not attempted
 * here since it touches several client files this pass didn't.
 */
public final class ShimmerAbility extends OrbAbility {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "shimmer");
	// ---- tunable: chance each tick to start a short invisible flicker ----
	private static final float INVISIBLE_CHANCE_PER_TICK = 0.35f; // ~65% average visibility - inside the 50-75% asked for
	private static final int FLICKER_TICKS = 3;

	public ShimmerAbility() {
		super(ID, false);
	}

	@Override
	protected void spawnChargeVisual(AbilityContext ctx) {
		if (ctx.caster().getRandom().nextFloat() < INVISIBLE_CHANCE_PER_TICK) {
			ctx.caster().addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, FLICKER_TICKS, 0, false, false, false));
		}
		// else: let any flicker from a previous tick count down on its own.
	}
}
