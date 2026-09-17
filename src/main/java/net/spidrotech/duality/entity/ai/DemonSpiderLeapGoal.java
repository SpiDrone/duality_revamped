package net.spidrotech.duality.entity.ai;

import net.spidrotech.duality.entity.SpiderQueenEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The demon spider's telegraphed leap.
 *
 * Unlike vanilla's LeapAtTargetGoal, which applies its impulse on the same tick it decides to
 * jump, this goal splits the jump in two: SpiderQueenEntity.JUMP_WINDUP_TICKS of visible
 * crouch (the jump_start animation) during which the spider is rooted and tracking its target,
 * and only then the launch. That gap is the whole point - it is the player's window to read
 * the jump and move.
 *
 * The spider only leaps when walking will not do: the target is meaningfully above it, or far
 * enough that closing on foot would be slow. Ordinary pursuit on flat ground stays with the
 * chase goal, so the leap keeps its weight instead of becoming a hop-everywhere gait.
 */
public class DemonSpiderLeapGoal extends Goal {
	/** Below this the spider is already on top of the target and should just spit. */
	private static final double MIN_LEAP_DISTANCE = 4.0;

	/** Past this the arc solver starts producing floaty, unreadable jumps. */
	private static final double MAX_LEAP_DISTANCE = 12.0;

	/** Height difference that justifies a jump on its own, regardless of distance. */
	private static final double LEAP_UP_THRESHOLD = 1.5;

	private final SpiderQueenEntity spider;
	private LivingEntity target;
	private int windupTicks;

	public DemonSpiderLeapGoal(SpiderQueenEntity spider) {
		this.spider = spider;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (this.spider.isVehicle() || !this.spider.canStartLeap())
			return false;
		this.target = this.spider.getTarget();
		if (this.target == null || !this.target.isAlive())
			return false;

		double distance = this.spider.distanceTo(this.target);
		if (distance > MAX_LEAP_DISTANCE)
			return false;
		boolean targetIsAbove = this.target.getY() - this.spider.getY() > LEAP_UP_THRESHOLD;
		if (!targetIsAbove && distance < MIN_LEAP_DISTANCE)
			return false;
		// Jumping blind into a wall or off a cliff edge looks broken; only commit when the
		// spider can actually see where it is going.
		return this.spider.getSensing().hasLineOfSight(this.target);
	}

	@Override
	public boolean canContinueToUse() {
		return this.spider.getAnimState() == SpiderQueenEntity.AnimState.JUMP_WINDUP && this.target != null && this.target.isAlive();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		this.windupTicks = 0;
		this.spider.getNavigation().stop();
		this.spider.beginLeapWindup();
	}

	@Override
	public void tick() {
		// Keep facing the target through the crouch so the launch direction matches what the
		// wind-up was pointing at - the spider re-aims right up to the last tick.
		this.spider.getLookControl().setLookAt(this.target, 30f, 30f);
		this.spider.getNavigation().stop();
		this.spider.setDeltaMovement(this.spider.getDeltaMovement().multiply(0.2, 1, 0.2));

		if (++this.windupTicks >= SpiderQueenEntity.JUMP_WINDUP_TICKS)
			this.spider.launchLeapAt(this.target);
	}

	@Override
	public void stop() {
		this.target = null;
	}
}
