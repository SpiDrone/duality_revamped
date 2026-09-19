package net.spidrotech.duality.creatures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Replacement for vanilla WallClimberNavigation.
 *
 * How vanilla spiders climb: the pathfinder itself only knows how to walk. When a path runs out
 * without reaching the target, WallClimberNavigation just steers the mob straight at the
 * target's block, and walking into a wall is what makes it climb. That "beeline" is the whole
 * climbing mechanic, so it has to stay.
 *
 * The bug it causes: with a floor or ceiling between spider and target, the path ends directly
 * underneath the target, and the beeline then asks the move control to reach a point straight
 * overhead - which the move control answers by jumping. Every tick, forever.
 *
 * This keeps the beeline but only uses it when it can actually achieve something: when the
 * target is off to the side (walking toward it may run into a climbable wall), or when a wall
 * is right next to the spider (in which case it steers into the wall instead of straight up).
 * Directly under a target with nothing to climb, it gives up and lets DemonSpiderChaseGoal look
 * for a different route.
 *
 * It also searches a much larger node budget than vanilla, which is what lets real walking
 * detours - round a corner, up a staircase - be found instead of settling for "the closest
 * point I found before running out of nodes".
 */
public class DemonSpiderNavigation extends GroundPathNavigation {
	/**
	 * Multiplies vanilla's node budget (FOLLOW_RANGE x 16 = 256 at the default 16 range). 256 is
	 * enough for a straight chase but runs out long before a detour through a doorway or up some
	 * stairs is found, and the pathfinder then returns a partial path to whatever point it had
	 * got closest to - usually straight under the target.
	 */
	private static final float SEARCH_MULTIPLIER = 4f;

	/**
	 * Within this horizontal distance of a target that is above it, the spider is considered
	 * "underneath" - beelining any closer can only produce a jump in place. Kept above 1.0 because
	 * the move control starts jumping once it is within 1 block horizontally.
	 */
	private static final double UNDERNEATH_RADIUS = 1.5;

	private BlockPos climbToward;
	private boolean lastPathReachesTarget;
	private int climbSuppressedTicks;

	public DemonSpiderNavigation(Mob mob, Level level) {
		super(mob, level);
		this.setMaxVisitedNodesMultiplier(SEARCH_MULTIPLIER);
	}

	@Override
	public Path createPath(Entity entity, int accuracy) {
		this.climbToward = entity.blockPosition();
		Path path = super.createPath(entity, accuracy);
		this.lastPathReachesTarget = path != null && path.canReach();
		return path;
	}

	@Override
	public Path createPath(BlockPos pos, int accuracy) {
		// Only chasing an entity should ever fall back to climbing. Vanilla also beelines after
		// wandering or detour paths, which makes an idle spider scale random walls.
		this.climbToward = null;
		return super.createPath(pos, accuracy);
	}

	@Override
	public boolean moveTo(Entity entity, double speed) {
		Path path = this.createPath(entity, 0);
		if (path != null)
			return this.moveTo(path, speed);
		// No path at all - normally because the spider is mid-climb and off the ground, where
		// ground navigation refuses to pathfind. Keep steering so the climb continues.
		this.speedModifier = speed;
		return true;
	}

	/** Whether the last entity path genuinely reaches the target rather than stopping short. */
	public boolean lastPathReachesTarget() {
		return this.lastPathReachesTarget;
	}

	/**
	 * Turns the off-path fallback off for a while. Used once the chase goal has seen the direct
	 * approach go nowhere, so the spider stops re-attempting the same failed climb and actually
	 * follows the detour it found.
	 */
	public void suppressClimbing(int ticks) {
		this.climbSuppressedTicks = Math.max(this.climbSuppressedTicks, ticks);
		this.climbToward = null;
	}

	@Override
	public void stop() {
		super.stop();
		this.climbToward = null;
	}

	@Override
	public void tick() {
		if (this.climbSuppressedTicks > 0) {
			this.climbSuppressedTicks--;
			this.climbToward = null;
		}
		if (!this.isDone()) {
			super.tick();
			return;
		}
		if (this.climbToward == null)
			return;

		Vec3 wanted = this.climbSteer(this.climbToward);
		if (wanted == null)
			this.climbToward = null;
		else
			this.mob.getMoveControl().setWantedPosition(wanted.x, wanted.y, wanted.z, this.speedModifier);
	}

	/** Where to steer to make progress toward {@code target} off-path, or null if nowhere helps. */
	private Vec3 climbSteer(BlockPos target) {
		Vec3 goal = Vec3.atBottomCenterOf(target);
		double dx = goal.x - this.mob.getX();
		double dz = goal.z - this.mob.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		double dy = goal.y - this.mob.getY();

		boolean above = dy > this.mob.maxUpStep();
		if (!above)
			return horizontal < this.mob.getBbWidth() ? null : goal;
		if (horizontal >= UNDERNEATH_RADIUS)
			return goal;

		// Underneath the target. The only way up from here is a wall: steer into one so the
		// spider collides with it and climbs, preferring the side that faces the target. A
		// ceiling in the way means the climb dead-ends against it, so don't start one.
		if (this.ceilingBetween(target.getY()))
			return null;
		Direction wall = this.adjacentClimbableWall(dx, dz);
		if (wall == null)
			return null;
		return this.mob.position().add(wall.getStepX(), 0.5, wall.getStepZ());
	}

	private boolean ceilingBetween(int targetY) {
		BlockPos.MutableBlockPos pos = this.mob.blockPosition().mutable();
		for (int y = pos.getY() + 1; y < targetY; y++) {
			pos.setY(y);
			if (!this.level.getBlockState(pos).getCollisionShape(this.level, pos).isEmpty())
				return true;
		}
		return false;
	}

	private Direction adjacentClimbableWall(double towardX, double towardZ) {
		BlockPos body = this.mob.blockPosition().above();
		Direction best = null;
		double bestDot = Double.NEGATIVE_INFINITY;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos pos = body.relative(dir);
			if (!this.level.getBlockState(pos).isFaceSturdy(this.level, pos, dir.getOpposite()))
				continue;
			double dot = dir.getStepX() * towardX + dir.getStepZ() * towardZ;
			if (dot > bestDot) {
				bestDot = dot;
				best = dir;
			}
		}
		return best;
	}
}
