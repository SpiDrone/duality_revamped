package net.spidrotech.duality.abilities.demon;

import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.Ability;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * "Levitate" - a fixed hover roughly HOVER_HEIGHT blocks above the ground, not vanilla's own
 * Levitation effect (which is a steady rise, not a hover at one height). Every tick, raycasts
 * straight down to find the ground and nudges vertical velocity toward the target height with a
 * proportional correction, clamped so it eases in rather than snapping; horizontal movement
 * (WASD) is left completely alone. Gravity is switched off for the caster while toggled on so it
 * never fights the correction, and restored on deactivate.
 *
 * Over open air (nothing found within MAX_RAY_DEPTH below) it just holds the current altitude
 * rather than trying to chase a target that doesn't exist.
 */
public final class LevitateAbility {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "levitate");
	// ---- tunable ----
	private static final double HOVER_HEIGHT = 6.0;
	private static final double MAX_RAY_DEPTH = 64.0;
	private static final double CORRECTION_GAIN = 0.15;
	private static final double MAX_VERTICAL_SPEED = 0.6;

	private LevitateAbility() {
	}

	public static Ability build() {
		return Ability.builder(ID, AbilityType.TOGGLE) //
				.onActivate(ctx -> ctx.caster().setNoGravity(true)) //
				.onTick(ctx -> hover(ctx.caster())) //
				.onDeactivate(ctx -> ctx.caster().setNoGravity(false)) //
				.build();
	}

	private static void hover(LivingEntity caster) {
		Level level = caster.level();
		Vec3 from = caster.position();
		Vec3 to = from.subtract(0, MAX_RAY_DEPTH, 0);
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		if (hit.getType() == HitResult.Type.MISS)
			return;
		double targetY = hit.getLocation().y + HOVER_HEIGHT;
		double error = targetY - caster.getY();
		double velY = Mth.clamp(error * CORRECTION_GAIN, -MAX_VERTICAL_SPEED, MAX_VERTICAL_SPEED);
		Vec3 delta = caster.getDeltaMovement();
		caster.setDeltaMovement(delta.x, velY, delta.z);
		caster.hurtMarked = true;
		caster.fallDistance = 0;
	}
}
