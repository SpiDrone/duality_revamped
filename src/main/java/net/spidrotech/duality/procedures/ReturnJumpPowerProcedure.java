package net.spidrotech.duality.procedures;

import net.spidrotech.duality.abilities.vampire.client.JumpInputClient;

import net.minecraft.world.entity.Entity;

/**
 * How many ticks the local player's current leap charge has been building - 0 if not charging.
 * Pair with ReturnJumpMaxPowerProcedure as a progress bar's current/max.
 *
 * CLIENT-ONLY: reads local input state (JumpInputClient), not server data - only wire this into
 * something that renders client-side (a HUD/GUI overlay), same restriction as RadialKeybindProcedure.
 * entity is unused: this always reads the local client's own charge, which is the only one that
 * ever needs to render on this client anyway.
 */
public class ReturnJumpPowerProcedure {
	public static double execute(Entity entity) {
		return JumpInputClient.heldChargeTicks();
	}
}
