package net.spidrotech.duality.creatures;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;

/**
 * Runs the spider down, and notices when the direct approach is going nowhere.
 *
 * Normal case: repath to the target every REPATH_TICKS. When the pathfinder finds a complete
 * route (DemonSpiderNavigation's larger search budget finds most walking detours on its own),
 * the spider just follows it and none of the rest of this class runs.
 *
 * Stuck case: when the path only gets partway - a floor, ceiling or wall between them - the
 * navigation's climb fallback gets a chance first. If the spider still hasn't closed the
 * distance after STUCK_TICKS, this stops re-trying the same thing: it disables climbing for a
 * while, samples standable spots around the target, and walks to the best one it can fully
 * reach. Spots next to a climbable wall, or with open air up to the target's height, score
 * better, since those are where a climb or leap can finish the job. Spots already tried are
 * remembered so it works through alternatives instead of bouncing between the same two.
 */
public class DemonSpiderChaseGoal extends Goal {
	private static final int REPATH_TICKS = 10;

	/** How long the spider may fail to close the distance before looking for another way. */
	private static final int STUCK_TICKS = 40;
	private static final double PROGRESS_EPSILON = 1.0;

	/** Long enough to walk a detour without the climb fallback dragging it back to the dead end. */
	private static final int CLIMB_SUPPRESS_TICKS = 100;

	/** Upper bound on following one detour before re-evaluating. */
	private static final int DETOUR_COMMIT_TICKS = 160;

	private static final int DETOUR_RADIUS = 10;
	private static final int DETOUR_SAMPLES = 12;

	/** Pathfinding is the expensive part, so only this many sampled spots get a full path check. */
	private static final int DETOUR_PATH_CHECKS = 6;

	/** Vertical range, above and below the target's height, searched for a standable spot. */
	private static final int DETOUR_VERTICAL_SCAN = 6;

	private static final int REMEMBERED_DETOURS = 6;
	private static final double REMEMBERED_RADIUS_SQR = 3 * 3;

	private static final double WALL_BONUS = 2.0;
	private static final double OPEN_COLUMN_BONUS = 2.0;

	/** If stuck while on a wall, let go and retry this soon rather than waiting a full STUCK_TICKS. */
	private static final int RETRY_AFTER_DROP_TICKS = 10;

	private final DemonSpiderEntity spider;
	private final DemonSpiderNavigation navigation;
	private final double speed;
	private final Deque<BlockPos> triedDetours = new ArrayDeque<>();

	private LivingEntity target;
	private int repathTicks;
	private double bestDistance;
	private int noProgressTicks;
	private int detourTicks;

	public DemonSpiderChaseGoal(DemonSpiderEntity spider, double speed) {
		this.spider = spider;
		this.navigation = (DemonSpiderNavigation) spider.getNavigation();
		this.speed = speed;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		LivingEntity candidate = this.spider.getTarget();
		if (candidate == null || !candidate.isAlive())
			return false;
		this.target = candidate;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		if (this.target == null || !this.target.isAlive() || this.spider.getTarget() != this.target)
			return false;
		return !(this.target instanceof Player player) || (!player.isCreative() && !player.isSpectator());
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		this.repathTicks = 0;
		this.detourTicks = 0;
		this.triedDetours.clear();
		this.resetProgress();
		this.spider.setAggressive(true);
	}

	@Override
	public void stop() {
		this.target = null;
		this.navigation.stop();
		this.spider.setAggressive(false);
	}

	@Override
	public void tick() {
		this.spider.getLookControl().setLookAt(this.target, 30f, 30f);

		if (this.detourTicks > 0) {
			this.detourTicks--;
			if (!this.navigation.isDone() && this.detourTicks > 0)
				return;
			this.detourTicks = 0;
			this.repathTicks = 0;
			this.resetProgress();
		}

		if (--this.repathTicks <= 0) {
			this.repathTicks = REPATH_TICKS;
			this.navigation.moveTo(this.target, this.speed);
		}

		if (this.navigation.lastPathReachesTarget()) {
			this.resetProgress();
			return;
		}

		double distance = this.spider.distanceTo(this.target);
		if (distance < this.bestDistance - PROGRESS_EPSILON) {
			this.bestDistance = distance;
			this.noProgressTicks = 0;
			return;
		}
		if (++this.noProgressTicks < STUCK_TICKS)
			return;

		this.navigation.suppressClimbing(CLIMB_SUPPRESS_TICKS);
		if (!this.spider.onGround()) {
			// Ground navigation can't pathfind off the ground, so every detour check would come
			// back empty. Dropping off the wall happens on its own now that climbing is off.
			this.noProgressTicks = STUCK_TICKS - RETRY_AFTER_DROP_TICKS;
			return;
		}

		this.resetProgress();
		BlockPos detour = this.findDetour();
		if (detour == null) {
			this.navigation.stop();
			return;
		}
		Path path = this.navigation.createPath(detour, 0);
		if (path != null && this.navigation.moveTo(path, this.speed)) {
			this.detourTicks = DETOUR_COMMIT_TICKS;
			this.remember(detour);
		}
	}

	private void resetProgress() {
		this.noProgressTicks = 0;
		this.bestDistance = this.target != null ? this.spider.distanceTo(this.target) : Double.MAX_VALUE;
	}

	private BlockPos findDetour() {
		Level level = this.spider.level();
		BlockPos targetPos = this.target.blockPosition();
		BlockPos best = null;
		double bestScore = Double.MAX_VALUE;
		int pathChecks = 0;

		for (int i = 0; i < DETOUR_SAMPLES && pathChecks < DETOUR_PATH_CHECKS; i++) {
			int dx = this.spider.getRandom().nextInt(DETOUR_RADIUS * 2 + 1) - DETOUR_RADIUS;
			int dz = this.spider.getRandom().nextInt(DETOUR_RADIUS * 2 + 1) - DETOUR_RADIUS;
			BlockPos spot = this.standableNear(level, targetPos.offset(dx, 0, dz));
			if (spot == null || spot.closerToCenterThan(this.spider.position(), 2.0) || this.alreadyTried(spot))
				continue;

			double score = Math.sqrt(spot.distSqr(targetPos));
			if (spot.getY() < targetPos.getY()) {
				if (this.hasAdjacentWall(level, spot))
					score -= WALL_BONUS;
				if (this.isColumnOpen(level, spot, targetPos.getY()))
					score -= OPEN_COLUMN_BONUS;
			}
			if (score >= bestScore)
				continue;

			pathChecks++;
			Path path = this.navigation.createPath(spot, 0);
			if (path != null && path.canReach()) {
				best = spot;
				bestScore = score;
			}
		}
		return best;
	}

	/** Nearest spot in this column the spider can stand in, searching outward from the target's height. */
	private BlockPos standableNear(Level level, BlockPos column) {
		for (int offset = 0; offset <= DETOUR_VERTICAL_SCAN; offset++) {
			BlockPos below = column.below(offset);
			if (this.isStandable(level, below))
				return below;
			if (offset > 0) {
				BlockPos above = column.above(offset);
				if (this.isStandable(level, above))
					return above;
			}
		}
		return null;
	}

	private boolean isStandable(Level level, BlockPos pos) {
		BlockPos floor = pos.below();
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
				&& level.getFluidState(pos).isEmpty() && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
	}

	private boolean hasAdjacentWall(Level level, BlockPos pos) {
		BlockPos body = pos.above();
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos wall = body.relative(dir);
			if (level.getBlockState(wall).isFaceSturdy(level, wall, dir.getOpposite()))
				return true;
		}
		return false;
	}

	private boolean isColumnOpen(Level level, BlockPos pos, int upToY) {
		BlockPos.MutableBlockPos cursor = pos.mutable();
		for (int y = pos.getY() + 2; y <= upToY; y++) {
			cursor.setY(y);
			if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty())
				return false;
		}
		return true;
	}

	private boolean alreadyTried(BlockPos spot) {
		for (BlockPos tried : this.triedDetours)
			if (tried.distSqr(spot) < REMEMBERED_RADIUS_SQR)
				return true;
		return false;
	}

	private void remember(BlockPos spot) {
		if (this.triedDetours.size() >= REMEMBERED_DETOURS)
			this.triedDetours.removeFirst();
		this.triedDetours.addLast(spot);
	}
}
