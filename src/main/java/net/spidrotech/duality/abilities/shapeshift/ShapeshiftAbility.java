package net.spidrotech.duality.abilities.shapeshift;

import net.spidrotech.duality.AbilityValue;
import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.AbilityContext;
import net.spidrotech.duality.AbilityCondition;
import net.spidrotech.duality.Ability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * The one shapeshift ability every form goes through - which form is passed in the cast context
 * under FORM_KEY (see Shapeshift#requestShift). Keeps a shared cooldown and gives a specific reason
 * when a shift is refused. Leaving the form you're in is always allowed.
 */
public final class ShapeshiftAbility {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "shapeshift");
	public static final String FORM_KEY = "form";
	private static final double COOLDOWN_TICKS = 10;

	private static final AbilityCondition CAN_SHIFT = new AbilityCondition() {
		@Override
		public boolean test(AbilityContext ctx) {
			return refusal(ctx) == null;
		}

		@Override
		public Component failureMessage(AbilityContext ctx) {
			Component reason = refusal(ctx);
			return reason != null ? reason : AbilityCondition.super.failureMessage(ctx);
		}
	};

	private ShapeshiftAbility() {
	}

	public static Ability build() {
		return Ability.builder(ID, AbilityType.INSTANT) //
				.castCondition(CAN_SHIFT) //
				.cooldown(AbilityValue.constant(COOLDOWN_TICKS)) //
				.onActivate(ctx -> {
					ResourceLocation id = ctx.get(FORM_KEY, (ResourceLocation) null);
					if (id != null && ctx.caster() instanceof ServerPlayer player)
						Shapeshift.form(id).ifPresent(form -> Shapeshift.toggle(player, form));
				}).build();
	}

	/** null = allowed. */
	private static Component refusal(AbilityContext ctx) {
		ResourceLocation id = ctx.get(FORM_KEY, (ResourceLocation) null);
		if (id == null)
			return Component.literal("No form chosen.");
		Optional<ShapeshiftForm> form = Shapeshift.form(id);
		if (form.isEmpty())
			return Component.literal("Unknown form: " + id);
		if (Shapeshift.isIn(ctx.caster(), id))
			return null;
		if (!Shapeshift.hasUnlocked(ctx.caster(), id))
			return Component.literal("You haven't unlocked that form.");
		AbilityCondition requirement = form.get().requirement();
		return requirement.test(ctx) ? null : requirement.failureMessage(ctx);
	}
}
