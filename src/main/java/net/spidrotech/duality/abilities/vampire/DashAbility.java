package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.Ability;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Double-tap space to dash where you're looking - pitch included, so looking up dashes up. Holding
 * shift when the dash fires sends you the opposite way. Never cast directly - toggled on by
 * VampireMode and triggered by JumpInputClient, which passes the shift state as BACKWARDS_KEY.
 *
 * A roughly level dash replaces vertical speed with a small lift so it briefly hangs rather than
 * dropping straight away; one aimed clearly downward keeps diving.
 *
 * On success the caster's client is told (DashFeedbackPayload) so it plays DashScreenEffect - sent
 * from here rather than when the key is pressed, so a dash refused by cooldown shows nothing.
 */
public final class DashAbility {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "dash");
	public static final String BACKWARDS_KEY = "backwards";

	// ---- tunable ----
	private static final double BASE_COOLDOWN_TICKS = 40;
	private static final double COOLDOWN_TICKS_OFF_PER_RANK = 5; // 35 at Fledgling -> 25 at Queen
	private static final double BASE_SPEED = 1.1;
	private static final double SPEED_PER_RANK = 0.25;
	private static final double LIFT = 0.15;
	private static final double DIVE_THRESHOLD = -0.3; // look direction y below this counts as aiming down

	private DashAbility() {
	}

	public static Ability build() {
		return Ability.builder(ID, AbilityType.INSTANT) //
				.castCondition(VampireRank.FLEDGLING.orAbove()) //
				.cooldown(ctx -> BASE_COOLDOWN_TICKS - VampireRank.fromAttribute(ctx.caster()).stepsAboveThrall() * COOLDOWN_TICKS_OFF_PER_RANK) //
				.onActivate(ctx -> {
					LivingEntity caster = ctx.caster();
					boolean backwards = ctx.get(BACKWARDS_KEY, false);
					int rank = VampireRank.fromAttribute(caster).stepsAboveThrall();
					Vec3 direction = caster.getLookAngle().normalize();
					if (backwards)
						direction = direction.scale(-1);
					Vec3 velocity = direction.scale(BASE_SPEED + rank * SPEED_PER_RANK);
					if (velocity.y < LIFT && direction.y > DIVE_THRESHOLD)
						velocity = new Vec3(velocity.x, LIFT, velocity.z);
					caster.setDeltaMovement(velocity);
					caster.hurtMarked = true;
					if (caster.level() instanceof ServerLevel level)
						level.sendParticles(ParticleTypes.SMOKE, caster.getX(), caster.getY() + 0.8, caster.getZ(), 8, 0.25, 0.4, 0.25, 0.01);
					if (caster instanceof ServerPlayer player)
						PacketDistributor.sendToPlayer(player, new JumpInputNetwork.DashFeedbackPayload(backwards));
				}).build();
	}
}
