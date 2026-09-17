package net.spidrotech.duality.abilities.demon;

import net.spidrotech.duality.ModAttachments;
import net.spidrotech.duality.AbilityManager;
import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.Ability;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * "Anti Gravity" - reduces the caster's own GRAVITY attribute (vanilla, generic.gravity - not
 * something this mod added) by a controllable percentage, 0 (normal) to 100 (true zero-g). The
 * spec also mentions targeting others; that's not wired to anything yet (needs a target-selection
 * UI/command), but setReductionPercent(LivingEntity, ...) works on any LivingEntity today, so
 * hooking that up later is just calling it on someone other than the caster.
 */
public final class AntiGravityAbility {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "anti_gravity");
	private static final ResourceLocation MODIFIER_ID = ResourceLocation.fromNamespaceAndPath("duality", "anti_gravity");

	private AntiGravityAbility() {
	}

	public static Ability build() {
		return Ability.builder(ID, AbilityType.TOGGLE) //
				.onActivate(ctx -> applyModifier(ctx.caster(), percentOf(ctx.caster()))) //
				.onDeactivate(ctx -> removeModifier(ctx.caster())) //
				.build();
	}

	/** 0 (normal gravity) .. 100 (true zero-g). Re-applies immediately if the ability is already
	 *  running, so adjusting it takes effect right away instead of waiting for the next toggle. */
	public static void setReductionPercent(LivingEntity entity, float percent) {
		float clamped = Mth.clamp(percent, 0, 100);
		entity.setData(ModAttachments.GRAVITY_REDUCTION_PERCENT, clamped);
		if (AbilityManager.get().isRunning(entity, ID))
			applyModifier(entity, clamped);
	}

	public static float percentOf(LivingEntity entity) {
		return entity.getData(ModAttachments.GRAVITY_REDUCTION_PERCENT);
	}

	private static void applyModifier(LivingEntity entity, float percent) {
		AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
		if (gravity == null)
			return;
		gravity.removeModifier(MODIFIER_ID);
		if (percent > 0)
			gravity.addTransientModifier(new AttributeModifier(MODIFIER_ID, -(percent / 100.0), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static void removeModifier(LivingEntity entity) {
		AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
		if (gravity != null)
			gravity.removeModifier(MODIFIER_ID);
	}
}
