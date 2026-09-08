package net.spidrotech.duality;

import net.minecraft.network.chat.Component;

/**
 * A pass/fail check, used two ways:
 *  - castConditions: checked once, right before an ability starts charging/activating.
 *  - maintainConditions: re-checked every tick while an ability is charging or active. If any
 *    fail, the ability is interrupted/deactivated automatically.
 */
@FunctionalInterface
public interface AbilityCondition {
	boolean test(AbilityContext ctx);

	default Component failureMessage(AbilityContext ctx) {
		return Component.literal("You can't do that right now.");
	}

	default AbilityCondition withMessage(Component message) {
		AbilityCondition self = this;
		return new AbilityCondition() {
			@Override
			public boolean test(AbilityContext ctx) {
				return self.test(ctx);
			}

			@Override
			public Component failureMessage(AbilityContext ctx) {
				return message;
			}
		};
	}

	default AbilityCondition and(AbilityCondition other) {
		return ctx -> this.test(ctx) && other.test(ctx);
	}

	default AbilityCondition or(AbilityCondition other) {
		return ctx -> this.test(ctx) || other.test(ctx);
	}

	default AbilityCondition negate() {
		return ctx -> !this.test(ctx);
	}
}