package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.abilities.AbilityToggles;
import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.AbilityManager;
import net.spidrotech.duality.Ability;

import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;

/**
 * Swats an incoming arrow away, once per cooldown. Reflexive: triggered off incoming arrow damage,
 * and only while toggled on (VampireMode switches it on for any vampire rank).
 *
 * AbilityManager is used purely for cooldown bookkeeping - the swat itself happens in the event
 * handler below, where the arrow entity is available.
 */
@EventBusSubscriber(modid = "duality")
public final class DeflectAbility {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "deflect");
	// ---- tunable: 10s base, -1s per rank -> 10/9/8/7s Thrall..Queen ----
	private static final double BASE_COOLDOWN_SECONDS = 10.0;
	private static final double SECONDS_OFF_PER_RANK = 1.0;

	private DeflectAbility() {
	}

	public static Ability build() {
		return Ability.builder(ID, AbilityType.INSTANT) //
				.cooldown(ctx -> (BASE_COOLDOWN_SECONDS - VampireRank.fromAttribute(ctx.caster()).stepsAboveThrall() * SECONDS_OFF_PER_RANK) * 20.0) //
				.castCondition(VampireRank.THRALL.orAbove()) //
				.onActivate(ctx -> {
					LivingEntity caster = ctx.caster();
					caster.swing(InteractionHand.MAIN_HAND, true); // true = the caster sees their own swing too
					caster.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, caster.getSoundSource(), 0.8f, 1.4f);
				}).build();
	}

	@SubscribeEvent
	public static void onIncomingDamage(LivingIncomingDamageEvent event) {
		LivingEntity victim = event.getEntity();
		if (victim.level().isClientSide())
			return;
		if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow))
			return;
		if (!AbilityToggles.isToggled(victim, ID))
			return;
		if (!AbilityManager.get().tryActivate(victim, ID).succeeded())
			return;
		event.setCanceled(true);
		// Knock it away rather than deleting it, so it reads as swatted aside.
		Vec3 away = victim.position().subtract(arrow.position());
		if (away.lengthSqr() < 1.0E-4)
			away = victim.getLookAngle();
		arrow.setDeltaMovement(away.normalize().scale(0.6).add(0, 0.2, 0));
		arrow.hasImpulse = true;
	}
}
