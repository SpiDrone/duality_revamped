package net.spidrotech.duality.abilities.demon;

import net.spidrotech.duality.AbilityValue;
import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.AbilityContext;
import net.spidrotech.duality.AbilityCondition;
import net.spidrotech.duality.Ability;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.Set;

/**
 * "Flaming" - teleport to a named linked fire (see FireWaypoint/FireLinkData/FireLinkCommand for
 * how links are created and pruned). Which waypoint is passed in via the cast context under
 * WAYPOINT_KEY, same pattern as ShapeshiftAbility's FORM_KEY - see FireLinkCommand for the only
 * caller today; a real in-game picker (choose among your linked fires) isn't wired up yet, same
 * gap as Shimmer's destination picker.
 *
 * Unlike Orb/Shimmer this has no range limit at all - the entire point of a link is a direct jump
 * regardless of distance - but Heaven is still off-limits, enforced when a link is CREATED
 * (FireLinkCommand) rather than here, so an invalid link can't exist in the first place.
 */
public final class FlameAbility {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "flame");
	public static final String WAYPOINT_KEY = "waypoint";
	private static final double COOLDOWN_TICKS = 60;

	private FlameAbility() {
	}

	public static Ability build() {
		return Ability.builder(ID, AbilityType.INSTANT) //
				.cooldown(AbilityValue.constant(COOLDOWN_TICKS)) //
				.castCondition(((AbilityCondition) ctx -> resolve(ctx).isPresent()).withMessage(Component.literal("No such linked fire."))) //
				.onActivate(ctx -> resolve(ctx).ifPresent(dest -> {
					LivingEntity caster = ctx.caster();
					ServerLevel destLevel = ((ServerLevel) caster.level()).getServer().getLevel(dest.dimension());
					if (destLevel == null)
						return;
					caster.teleportTo(destLevel, dest.pos().getX() + 0.5, dest.pos().getY() + 1, dest.pos().getZ() + 0.5, Set.of(), caster.getYRot(), caster.getXRot());
				})) //
				.build();
	}

	private static Optional<FireWaypoint> resolve(AbilityContext ctx) {
		String name = ctx.get(WAYPOINT_KEY, (String) null);
		if (name == null || !(ctx.caster().level() instanceof ServerLevel level))
			return Optional.empty();
		return FireLinkData.get(level).get(name);
	}
}
