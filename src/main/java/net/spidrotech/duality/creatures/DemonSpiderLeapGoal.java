package net.spidrotech.duality.creatures;


import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The demon spider's telegraphed leap.
 *
 * Unlike vanilla's LeapAtTargetGoal, which applies its impulse on the same tick it decides to
 * jump, this goal splits the jump in two: DemonSpiderEntity.JUMP_WINDUP_TICKS of visible
 * crouch (the jump_start animation) during which the spider is rooted and tracking its target,
 * and only then the launch. That gap is the whole point - it is the player's window to read
 * the jump and move.
 *
 * The spider only leaps when it gains something: walking can't reach the target (up a ledge,
 * across a gap), or it's a flat pounce across open ground. It always leaps toward the target and
 * never straight up - DemonSpiderEntity#solveLeap refuses anything without horizontal distance to
 * cover. Everything else stays with the chase goal, so the leap keeps its weight instead of
 * becoming a hop-everywhere gait.
 */
public class DemonSpiderLeapGoal extends Goal {
	/** Horizontal blocks. Closer than this the spider is nearly on top of the target and should just spit. */
	private static final double MIN_LEAP_DISTANCE = 4.0;

	/** Horizontal blocks. Past this even a flat leap spends long enough in the air to be easy to dodge. */
	private static final double MAX_LEAP_DISTANCE = 12.0;

	/** Horizontal blocks. Pounces across level ground stop here; longer gaps are left to walking unless walking can't reach. */
	private static final double MAX_POUNCE_DISTANCE = 8.0;

	/** Height difference (blocks) past which the target no longer counts as level with the spider. */
	private static final double LEAP_UP_THRESHOLD = 1.5;

	private final DemonSpiderEntity spider;
	private LivingEntity target;
	private int windupTicks;

	public DemonSpiderLeapGoal(DemonSpiderEntity spider) {
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

		// Horizontal distance only. Measuring straight-line distance let a target on a ledge
		// directly overhead count as "far enough", and the only leap toward that is straight up.
		double dx = this.target.getX() - this.spider.getX();
		double dz = this.target.getZ() - this.spider.getZ();
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		if (horizontal < MIN_LEAP_DISTANCE || horizontal > MAX_LEAP_DISTANCE)
			return false;

		// Only leap when it gets the spider something walking wouldn't. Either walking can't reach
		// the target at all (it's up a ledge, across a gap), or it's a pounce across open ground at
		// roughly the same height. A target up a staircase the spider can simply walk up is not a
		// reason to jump.
		double dy = this.target.getY() - this.spider.getY();
		boolean walkable = this.spider.getNavigation() instanceof DemonSpiderNavigation nav && nav.lastPathReachesTarget();
		boolean pounce = Math.abs(dy) < LEAP_UP_THRESHOLD && horizontal <= MAX_POUNCE_DISTANCE;
		if (walkable && !pounce)
			return false;

		// Jumping blind into a wall or off a cliff edge looks broken; only commit when the
		// spider can actually see where it is going - and only if the arc can actually get there.
		return this.spider.getSensing().hasLineOfSight(this.target) && DemonSpiderEntity.solveLeap(this.spider.position(), this.target.position()).isPresent();
	}

	@Override
	public boolean canContinueToUse() {
		return this.spider.getAnimState() == DemonSpiderEntity.AnimState.JUMP_WINDUP && this.target != null && this.target.isAlive();
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

		if (++this.windupTicks >= DemonSpiderEntity.JUMP_WINDUP_TICKS)
			this.spider.launchLeapAt(this.target);
	}

	@Override
	public void stop() {
		this.target = null;
	}
}
