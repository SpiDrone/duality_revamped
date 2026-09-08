package net.spidrotech.duality;

import org.checkerframework.checker.units.qual.min;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.core.Holder;

/**
 * A number that's computed per-cast rather than hardcoded - this is what lets you write things
 * like "chargeUpTime - (player variable)" instead of a flat constant. Any Ability timing field
 * (chargeTime, duration, cooldown) is one of these, so all of them can scale off attributes,
 * enchantments, selected targets, whatever you want.
 */
@FunctionalInterface
public interface AbilityValue {
	/** Value is always resolved fresh against the current context - never cached - so it can
	 *  change mid-charge (e.g. a target gets added, and the charge bar's target duration stretches). */
	double resolve(AbilityContext ctx);

	static AbilityValue constant(double value) {
		return ctx -> value;
	}

	/** Reads a value directly off one of the caster's attributes. */
	static AbilityValue fromAttribute(Holder<Attribute> attribute) {
		return ctx -> ctx.caster().getAttributeValue(attribute);
	}

	/** Scales with how many extra entities have been added to this cast (see AbilityContext#targets). */
	static AbilityValue perTarget(double amountPerTarget) {
		return ctx -> ctx.targets().size() * amountPerTarget;
	}

	default AbilityValue plus(AbilityValue other) {
		return ctx -> this.resolve(ctx) + other.resolve(ctx);
	}

	default AbilityValue minus(AbilityValue other) {
		return ctx -> this.resolve(ctx) - other.resolve(ctx);
	}

	default AbilityValue times(double factor) {
		return ctx -> this.resolve(ctx) * factor;
	}

	default AbilityValue clamped(double min, double max) {
		return ctx -> Math.max(min, Math.min(max, this.resolve(ctx)));
	}
}