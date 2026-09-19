package net.spidrotech.duality.abilities.demon;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The movement behind WallClimberAbility, plus the queries anything else needs to react to it -
 * shapeshift forms read isClimbing/wallDirection to switch to a climbing animation and tip their
 * model onto the wall.
 *
 * <p>Modelled on vanilla ladders, so it feels native:
 * <ul>
 *   <li>push into a wall - climb at ladder speed</li>
 *   <li>stop pushing - slide down slowly instead of dropping</li>
 *   <li>sneak - cling in place</li>
 * </ul>
 *
 * <p>Why not just make walls count as ladders: that is decided by LivingEntity#onClimbable, which
 * only asks the block, never the entity. Changing it would take a mixin, and the mixin config here
 * is MCreator's. So this applies the same velocities directly, from a tick event.
 *
 * <p>The velocity is only applied on the owning client, because a player's own client decides where
 * they move and the server takes its word. The server only clears fall distance, so a long slide
 * down a wall never ends in fall damage. On a dedicated server the "floating" kick doesn't trigger
 * either - it only fires with no blocks around the player, and a wall is right there.
 */
@EventBusSubscriber(modid = "duality")
public final class WallClimbing {
	/** Vanilla's ladder climb speed, per tick. */
	public static final double CLIMB_SPEED = 0.2;

	/** Vanilla's ladder slide limit - the fastest you drop while on the wall but not climbing. */
	public static final double SLIDE_SPEED = 0.15;

	/** How close a block has to be to the player's side to count as a wall they're on. */
	private static final double WALL_REACH = 0.06;

	/** Trimmed off the top and bottom of the probe so the floor and ceiling never count as walls. */
	private static final double PROBE_VERTICAL_TRIM = 0.1;

	private WallClimbing() {
	}

	/** Whether climbing is on and nothing else already owns this player's movement. */
	public static boolean canClimb(Player player) {
		return WallClimberAbility.isEnabled(player) && !player.getAbilities().flying && !player.isPassenger() && !player.isFallFlying() && !player.isSpectator();
	}

	/**
	 * The horizontal side a wall is pressed against, or null if none. With walls on more than one
	 * side (a corner, a shaft), prefers the one the player is facing, since that's the one they're
	 * climbing.
	 */
	public static Direction wallDirection(Player player) {
		AABB body = player.getBoundingBox().deflate(0, PROBE_VERTICAL_TRIM, 0);
		Vec3 facing = Vec3.directionFromRotation(0, player.getYRot());
		Direction best = null;
		double bestDot = Double.NEGATIVE_INFINITY;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			AABB probe = body.move(dir.getStepX() * WALL_REACH, 0, dir.getStepZ() * WALL_REACH);
			if (player.level().noCollision(player, probe))
				continue;
			double dot = dir.getStepX() * facing.x + dir.getStepZ() * facing.z;
			if (dot > bestDot) {
				bestDot = dot;
				best = dir;
			}
		}
		return best;
	}

	/**
	 * Whether the player is on a wall right now - for animations. Covers both hanging on it and the
	 * moment of stepping onto it from the ground.
	 *
	 * <p>Only reliable for players whose ability toggles this client knows: yourself, or anyone on
	 * the server side. Other players' toggles aren't sent to your client.
	 */
	public static boolean isClimbing(Player player) {
		return canClimb(player) && (!player.onGround() || player.horizontalCollision) && wallDirection(player) != null;
	}

	@SubscribeEvent
	public static void onPlayerTick(PlayerTickEvent.Post event) {
		Player player = event.getEntity();
		if (!canClimb(player) || wallDirection(player) == null)
			return;

		if (player.level().isClientSide()) {
			// Other players on this client are moved by position packets, not by physics.
			if (!player.isLocalPlayer())
				return;
			Vec3 motion = player.getDeltaMovement();
			double vy;
			if (player.isShiftKeyDown() && !player.onGround())
				vy = 0; // cling
			else if (player.horizontalCollision)
				vy = CLIMB_SPEED; // pushing into the wall
			else if (player.onGround())
				return; // just standing next to a wall
			else
				vy = Math.max(motion.y, -SLIDE_SPEED); // let go - slide rather than drop
			// Set after this tick's move, like a ladder: next tick moves by exactly this before
			// gravity is applied to it.
			player.setDeltaMovement(motion.x, vy, motion.z);
		}
		player.resetFallDistance();
	}
}
