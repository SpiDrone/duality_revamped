package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.abilities.AbilityToggles;
import net.spidrotech.duality.AbilityValue;
import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.AbilityContext;
import net.spidrotech.duality.Ability;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Hold space to charge, release to leap. Never cast directly - toggled on by VampireMode, and the
 * charge is started/released by JumpInputClient (see that class for how a hold is told apart from
 * a jump, a bhop and a double tap).
 *
 * The charge never fires on its own: its AbilityManager charge time is effectively infinite, so the
 * player can hold for as long as they like. Power builds up to a full charge over maxChargeTicks
 * (tracked here, not by AbilityManager) and then simply stays full - the particles change once it's
 * there, so the player knows holding longer gains nothing.
 *
 * Releasing goes through AbilityManager#cancel, which for a CHARGING ability calls
 * onChargeInterrupted - that hook is where the leap actually fires.
 *
 * No cooldown: the charge time is already the rate limiter.
 */
public final class LeapAbility extends Ability {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "leap");
	private static final String CHARGE_START_KEY = "chargeStart";

	// ---- tunable: ticks to reach a full charge - longer at higher rank, for a bigger payoff ----
	private static final double BASE_CHARGE_TICKS = 15.0;
	private static final double CHARGE_TICKS_PER_RANK = 5.0;
	// ---- tunable: launch power at a full charge ----
	private static final double VERTICAL_BASE = 0.9;
	private static final double VERTICAL_PER_RANK = 0.3;
	private static final double HORIZONTAL_BASE = 0.6;
	private static final double HORIZONTAL_PER_RANK = 0.3;
	private static final float MIN_PROGRESS_TO_LAUNCH = 0.05f;

	public LeapAbility() {
		super(ID, AbilityType.CHANNELED);
		setChargeTime(AbilityValue.constant(Double.MAX_VALUE)); // never auto-completes - see class doc
		addCastCondition(VampireRank.FLEDGLING.orAbove());
	}

	@Override
	public void onChargeStart(AbilityContext ctx) {
		ctx.set(CHARGE_START_KEY, ctx.caster().level().getGameTime());
	}

	@Override
	public void onChargeTick(AbilityContext ctx, float ignored) {
		LivingEntity caster = ctx.caster();
		if (!(caster.level() instanceof ServerLevel level))
			return;
		if (chargeProgress(ctx) >= 1f)
			level.sendParticles(ParticleTypes.CRIMSON_SPORE, caster.getX(), caster.getY() + 0.3, caster.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
		else
			level.sendParticles(ParticleTypes.ASH, caster.getX(), caster.getY() + 0.1, caster.getZ(), 1, 0.15, 0.0, 0.15, 0.0);
	}

	@Override
	public void onChargeInterrupted(AbilityContext ctx) {
		// Leaving vampire mode mid-charge also ends up here (the client releases the charge) - that
		// should just drop the charge, not fling the player.
		if (!AbilityToggles.isToggled(ctx.caster(), ID))
			return;
		launch(ctx, chargeProgress(ctx));
	}

	/** Ticks needed for a full charge at caster's current rank - public for a HUD/progress-bar
	 *  procedure to pair with a current-ticks value (see procedures.ReturnJumpMaxPowerProcedure).
	 *  Attribute-only, so it's safe to call client-side. */
	public static double maxChargeTicks(LivingEntity caster) {
		return BASE_CHARGE_TICKS + VampireRank.fromAttribute(caster).stepsAboveThrall() * CHARGE_TICKS_PER_RANK;
	}

	private static float chargeProgress(AbilityContext ctx) {
		long now = ctx.caster().level().getGameTime();
		long start = ctx.get(CHARGE_START_KEY, now);
		return (float) Math.min(1.0, (now - start) / maxChargeTicks(ctx.caster()));
	}

	private static void launch(AbilityContext ctx, float progress) {
		if (progress < MIN_PROGRESS_TO_LAUNCH)
			return;
		LivingEntity caster = ctx.caster();
		int rank = VampireRank.fromAttribute(caster).stepsAboveThrall();
		Vec3 horizontal = flatLook(caster);
		double vertical = (VERTICAL_BASE + rank * VERTICAL_PER_RANK) * progress;
		double horizontalMag = (HORIZONTAL_BASE + rank * HORIZONTAL_PER_RANK) * progress;
		caster.setDeltaMovement(horizontal.scale(horizontalMag).add(0, vertical, 0));
		// Players own their movement client-side - without this the launch never reaches the caster's
		// own client. Same mechanism vanilla knockback relies on.
		caster.hurtMarked = true;
	}

	static Vec3 flatLook(LivingEntity caster) {
		Vec3 look = caster.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0, look.z);
		return horizontal.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : horizontal.normalize();
	}
}
