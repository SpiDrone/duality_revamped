package net.spidrotech.duality.abilities.demon;

import net.spidrotech.duality.abilities.AbilityToggles;
import net.spidrotech.duality.AbilityValue;
import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.Ability;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Wall climber - walk into a wall and you go up it, like a spider. The movement itself lives in
 * WallClimbing; this is just the on/off switch.
 *
 * Deliberately an INSTANT ability that flips an AbilityToggles entry, not a TOGGLE-type ability.
 * A TOGGLE's on/off state lives only in AbilityManager, which is server-side and forgotten on
 * relog - but a player's movement is computed on their own client, so the client has to know
 * whether climbing is on. AbilityToggles is saved and mirrored to the owning client, which is
 * exactly why VampireMode uses the same pattern.
 */
public final class WallClimberAbility {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "wall_climber");
	private static final double TOGGLE_COOLDOWN_TICKS = 10;

	private WallClimberAbility() {
	}

	public static Ability build() {
		return Ability.builder(ID, AbilityType.INSTANT) //
				.cooldown(AbilityValue.constant(TOGGLE_COOLDOWN_TICKS)) //
				.onActivate(ctx -> {
					if (ctx.caster() instanceof ServerPlayer player)
						AbilityToggles.set(player, ID, !isEnabled(player));
				}).build();
	}

	/** Safe on either side - the client's copy is kept in sync by AbilityToggleNetwork. */
	public static boolean isEnabled(LivingEntity entity) {
		return AbilityToggles.isToggled(entity, ID);
	}
}
