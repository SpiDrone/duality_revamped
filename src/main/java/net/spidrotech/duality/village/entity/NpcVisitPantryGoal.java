package net.spidrotech.duality.village.entity;

import net.spidrotech.duality.village.BuildingType;
import net.spidrotech.duality.village.ServerVillageBridge;
import net.spidrotech.duality.village.VillageBuilding;
import net.spidrotech.duality.village.VillageRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Sends an NPC to the village pantry now and then.
 *
 * <p>This is the first strand of the "ledger behaviour becomes world behaviour" thread the design
 * notes call for. Be clear about what it is and isn't: eating is still resolved entirely in the
 * daily simulation, and walking here consumes nothing. What it buys is that the pantry chest
 * becomes a place residents visibly use, so a player watching a village sees it being lived in, and
 * a player who empties the chest can watch who comes looking.
 *
 * <p>Making the trip actually feed them means moving the food draw out of the day tick and into
 * arrival here, which is a simulation change, not an AI one - and it needs deciding whether an
 * unloaded village still eats. It does today, and that's why the ledger owns it.
 */
public class NpcVisitPantryGoal extends Goal {
	/** Roughly how often a resident thinks about the pantry, in ticks (about once a minute). */
	private static final int CHECK_INTERVAL = 1200;

	/** Random spread so a village doesn't queue up at the chest in lockstep. */
	private static final int CHECK_JITTER = 600;

	private static final double ARRIVED_DISTANCE = 2.5;

	private final DualityNpcEntity npc;
	private final double speed;
	private BlockPos pantry;
	private int cooldown;

	public NpcVisitPantryGoal(DualityNpcEntity npc, double speed) {
		this.npc = npc;
		this.speed = speed;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (--this.cooldown > 0)
			return false;
		this.cooldown = CHECK_INTERVAL + this.npc.getRandom().nextInt(CHECK_JITTER);

		VillageRecord village = this.npc.village();
		if (village == null)
			return false;
		for (VillageBuilding building : village.buildings()) {
			if (building.type() == BuildingType.PANTRY && building.isPlaced()) {
				this.pantry = ServerVillageBridge.blockPos(building.position());
				return this.npc.distanceToSqr(this.pantry.getX() + 0.5, this.pantry.getY(), this.pantry.getZ() + 0.5) > ARRIVED_DISTANCE * ARRIVED_DISTANCE;
			}
		}
		return false;
	}

	@Override
	public boolean canContinueToUse() {
		return this.pantry != null && !this.npc.getNavigation().isDone();
	}

	@Override
	public void start() {
		this.npc.getNavigation().moveTo(this.pantry.getX() + 0.5, this.pantry.getY(), this.pantry.getZ() + 0.5, this.speed);
	}

	@Override
	public void stop() {
		this.pantry = null;
		this.npc.getNavigation().stop();
	}
}
