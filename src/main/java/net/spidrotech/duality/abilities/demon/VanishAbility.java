package net.spidrotech.duality.abilities.demon;

import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.Ability;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;

/**
 * "Vanish" - true invisibility, meant to require an actual detection spell to see through. It's
 * just the vanilla Invisibility effect (silent, no particles) kept topped up while toggled on - see
 * onTick, driven by AbilityManager for every ACTIVE toggle - so it never has a visible countdown or
 * a moment where it lapses.
 *
 * isVanished() is the hook a future detection spell/hunter system should read, rather than
 * checking hasEffect(INVISIBILITY) directly - if "true" invisibility ever needs to be more than
 * the vanilla effect (immune to being revealed by glowing, water droplets, etc.), this is the one
 * place that changes.
 */
public final class VanishAbility {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "vanish");
	private static final int EFFECT_DURATION_TICKS = 15 * 20; // reapplied well before it can expire
	private static final int REFRESH_INTERVAL_TICKS = 5 * 20;

	private VanishAbility() {
	}

	public static Ability build() {
		return Ability.builder(ID, AbilityType.TOGGLE) //
				.onActivate(ctx -> refresh(ctx.caster())) //
				.onTick(ctx -> {
					if (ctx.caster().level().getGameTime() % REFRESH_INTERVAL_TICKS == 0)
						refresh(ctx.caster());
				}) //
				.onDeactivate(ctx -> ctx.caster().removeEffect(MobEffects.INVISIBILITY)) //
				.build();
	}

	public static boolean isVanished(LivingEntity entity) {
		return entity.hasEffect(MobEffects.INVISIBILITY);
	}

	private static void refresh(LivingEntity caster) {
		caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, EFFECT_DURATION_TICKS, 0, false, false, false));
	}
}
