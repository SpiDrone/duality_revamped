package net.spidrotech.duality.village.entity;

import net.spidrotech.duality.village.ServerVillageBridge;
import net.spidrotech.duality.village.VillageRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Keeps an NPC inside the settlement they belong to.
 *
 * <p>Without this, the plain stroll goal walks residents out of their own village and they never
 * come back - which reads as broken, and quietly breaks the simulation too, since a raid looks for
 * residents near the village centre. This only engages once they are actually outside the village
 * radius, so inside the bounds they wander normally.
 *
 * <p>Whether they are "home" is read from the record every time rather than cached, because a
 * record's current village changes underneath the entity when they are abducted.
 */
public class NpcStayHomeGoal extends Goal {
	/** How far past the village radius they're allowed before being walked back. */
	private static final double LEEWAY = 6.0;

	/** Stop once they're comfortably back inside rather than marching to the exact centre. */
	private static final double SETTLE_FRACTION = 0.6;

	private final DualityNpcEntity npc;
	private final double speed;
	private Vec3 destination;

	public NpcStayHomeGoal(DualityNpcEntity npc, double speed) {
		this.npc = npc;
		this.speed = speed;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		VillageRecord village = this.npc.village();
		if (village == null || village.center() == null)
			return false;
		BlockPos centre = ServerVillageBridge.blockPos(village.center());
		if (this.npc.distanceToSqr(centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5) < sqr(village.radius() + LEEWAY))
			return false;
		this.destination = Vec3.atBottomCenterOf(centre);
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		VillageRecord village = this.npc.village();
		if (village == null || this.destination == null || this.npc.getNavigation().isDone())
			return false;
		return this.npc.distanceToSqr(this.destination) > sqr(village.radius() * SETTLE_FRACTION);
	}

	@Override
	public void start() {
		this.npc.getNavigation().moveTo(this.destination.x, this.destination.y, this.destination.z, this.speed);
	}

	@Override
	public void stop() {
		this.destination = null;
		this.npc.getNavigation().stop();
	}

	private static double sqr(double value) {
		return value * value;
	}
}
