package net.spidrotech.duality.abilities.vampire;

import net.spidrotech.duality.AbilityManager;

/**
 * Registration point for the vampire kit - called from DualityAbilities#demonic().
 *
 * Only Ability instances and shapeshift forms are registered here. Everything else (stat buffs,
 * the deflect damage hook, input/network/client classes) is @EventBusSubscriber and wires itself.
 *
 *   Vampire mode  - all ranks. Toggled from the abilities radial; off = humanized (VampireMode)
 *   Stat buffs    - passive, all ranks, regardless of mode (VampireStatBuffs)
 *   Deflect       - auto in vampire mode, all ranks (DeflectAbility)
 *   Leap          - auto in vampire mode, Fledgling+ (LeapAbility)
 *   Dash          - auto in vampire mode, Fledgling+ (DashAbility)
 *   Bat form      - Fledgling+, vampire mode only, must be unlocked (VampireForms; picked via the
 *                   shared shapeshift radial - see abilities.shapeshift.Shapeshift)
 *
 * The rank gates are the abilities' cast conditions, which is also what VampireMode checks for
 * "has access" when auto-toggling - change a gate in one place and both follow.
 */
public final class VampireAbilities {
	private VampireAbilities() {
	}

	public static void registerAll() {
		AbilityManager.get().register(VampireMode.build());
		AbilityManager.get().register(DeflectAbility.build());
		AbilityManager.get().register(new LeapAbility());
		AbilityManager.get().register(DashAbility.build());
		VampireForms.registerAll();
	}
}
