package net.spidrotech.duality.entity.ai;

import net.spidrotech.duality.entity.SpiderQueenEntity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The demon spider's spit attack.
 *
 * This goal owns the timing and the pose; the projectile itself is still a placeholder inside
 * SpiderQueenEntity#performSpit. It holds the spider still, plays the attack animation, and
 * fires the payload on RELEASE_TICK - the frame the existing attack animation throws the head
 * forward and the mandibles snap - so when a real projectile is dropped in it already comes
 * out of the mouth on the right frame.
 */
public class DemonSpiderSpitGoal extends Goal {
	/** Do not spit at point blank; that range belongs to the leap closing in. */
	private static final double MIN_RANGE = 3.0;
	private static final double MAX_RANGE = 16.0;

	/**
	 * Tick within the attack animation that the spit leaves the mouth. The animation's lunge
	 * peaks at 0.35s, which is tick 7.
	 */
	private static final int RELEASE_TICK = 7;

	/** Full length of the attack animation (0.7s), after which the goal releases the spider. */
	private static final int ATTACK_TICKS = 14;

	private static final int COOLDOWN_TICKS = 50;

	private final SpiderQueenEntity spider;
	private LivingEntity target;
	private int attackTicks;
	private int cooldown;

	public DemonSpiderSpitGoal(SpiderQueenEntity spider) {
		this.spider = spider;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (this.cooldown > 0) {
			this.cooldown--;
			return false;
		}
		if (this.spider.getAnimState() != SpiderQueenEntity.AnimState.GROUND)
			return false;
		this.target = this.spider.getTarget();
		if (this.target == null || !this.target.isAlive())
			return false;

		double distance = this.spider.distanceTo(this.target);
		return distance >= MIN_RANGE && distance <= MAX_RANGE && this.spider.getSensing().hasLineOfSight(this.target);
	}

	@Override
	public boolean canContinueToUse() {
		return this.attackTicks < ATTACK_TICKS && this.target != null && this.target.isAlive();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		this.attackTicks = 0;
		this.spider.getNavigation().stop();
		this.spider.setAnimState(SpiderQueenEntity.AnimState.SPIT);
	}

	@Override
	public void tick() {
		this.spider.getLookControl().setLookAt(this.target, 30f, 30f);
		if (++this.attackTicks == RELEASE_TICK)
			this.spider.performSpit(this.target);
	}

	@Override
	public void stop() {
		this.target = null;
		this.cooldown = COOLDOWN_TICKS;
		// Only hand the state back if the spit is still what owns it - a leap or a landing
		// that interrupted this goal has already claimed the state machine.
		if (this.spider.getAnimState() == SpiderQueenEntity.AnimState.SPIT)
			this.spider.setAnimState(SpiderQueenEntity.AnimState.GROUND);
	}
}
