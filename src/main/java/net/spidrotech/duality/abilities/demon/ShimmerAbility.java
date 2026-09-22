package net.spidrotech.duality.abilities.demon;

import net.spidrotech.duality.abilities.teleportation.TeleportGlowStyle;
import net.spidrotech.duality.abilities.teleportation.OrbAbility;
import net.spidrotech.duality.AbilityContext;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.resources.ResourceLocation;

/**
 * The demon counterpart to Orb - same channeled teleport, same progression, same Underworld
 * access, but Heaven is off-limits at any level (see OrbAbility's protected constructor: this is
 * the ONLY rule difference, enforced once in OrbAbility#canCross, not duplicated here).
 *
 * VISUAL: real alpha blending, oscillating between 50% and 70% opacity - not the on/off flicker
 * an earlier version of this class approximated it with via temporal dithering (toggling vanilla
 * Invisibility on and off), which read as a hard 0%/100% strobe rather than a smooth fade.
 *
 * Getting real translucency onto a LivingEntityRenderer needs two pieces working together, since
 * neither alone is enough - forcing the render type without touching alpha still draws fully
 * opaque, and multiplying alpha without forcing the render type still draws fully opaque too
 * (vanilla's default entity render type is alpha-TESTED, not alpha-BLENDED, so a sub-1.0 alpha
 * that still clears the alpha-test threshold has no visible effect):
 *   1. mixin.LivingEntityRendererMixin forces getRenderType() to RenderType.itemEntityTranslucentCull
 *      (a real blending type - the same one vanilla itself uses for the "translucent teammate
 *      ghost" effect on invisible allies) whenever the entity carries SHIMMERING, AND replaces the
 *      vertex color vanilla hardcodes to fully opaque for every non-ghost entity.
 *   2. client.ShimmerRenderer computes that color, oscillating 50-70% off the entity's own tick
 *      clock so every observer's client draws the same phase without any networking of its own.
 *      It has to travel as a vertex color rather than as a RenderSystem.setShaderColor bracket
 *      around the render call - entity geometry is buffered and flushed after that bracket would
 *      already have been reset, which is exactly why the first attempt at this changed nothing on
 *      screen. See that class for the full reasoning.
 * SHIMMERING (see ShimmerEffects) is the marker both of those key off - refreshed every charge
 * tick here, the same "vanilla already syncs this to every observer" trick the old flicker
 * leaned on, just via a dedicated effect instead of borrowed Invisibility (which plenty of OTHER
 * things apply too - Vanish, a vanilla potion - and would've been wrongly caught by the mixin).
 *
 * PICKER/NETWORK WIRING: the destination-picker UI and TeleportNetwork used to be hardcoded to
 * OrbAbility.ID, so a demon couldn't open a picker or confirm a Shimmer destination in-game -
 * that's now generalized (see TeleportAbilities, which treats any OrbAbility subclass as a
 * teleport ability), so this needs no wiring of its own beyond what it already inherits.
 */
public final class ShimmerAbility extends OrbAbility {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "shimmer");
	// Reapplied every tick rather than given the whole charge's duration up front, since charge
	// time can change live as the destination/passengers change (see OrbAbility#computeChargeTicks)
	// - a few ticks of slack is plenty to outlast the gap between ticks, and it fades itself out
	// within a quarter second of charging actually stopping without this needing its own
	// onChargeInterrupted/onDeactivate cleanup.
	private static final int MARKER_TICKS = 5;
	// Violet pulse instead of Orb's white/cyan PULSE_WHITELIGHTER - a demon's locked destination
	// shouldn't borrow the angel's own visual identity.
	private static final TeleportGlowStyle PULSE_SHIMMER = TeleportGlowStyle.pulse(0xFFFFFFFF, 0xFF9933FF, 0.6f);

	public ShimmerAbility() {
		super(ID, false);
	}

	@Override
	protected void spawnChargeVisual(AbilityContext ctx) {
		ctx.caster().addEffect(new MobEffectInstance(ShimmerEffects.SHIMMERING, MARKER_TICKS, 0, false, false, false));
	}

	@Override
	public TeleportGlowStyle glowStyle() {
		return PULSE_SHIMMER;
	}
}
