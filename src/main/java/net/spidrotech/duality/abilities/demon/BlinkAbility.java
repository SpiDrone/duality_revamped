package net.spidrotech.duality.abilities.demon;

import net.spidrotech.duality.AbilityValue;
import net.spidrotech.duality.AbilityType;
import net.spidrotech.duality.Ability;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;

/**
 * "Blinking" - short-range teleport, straight ahead, capped by line of sight. Raycasts from the
 * caster's eyes along their look vector; a solid block in the way shortens the blink instead of
 * refusing it outright, landing just short of the wall rather than inside it.
 *
 * Demon-generic (not vampire-specific) - registered from DualityAbilities#demonic() directly, not
 * gated to any one demon type. Add a rank/type cast condition here once demon archetypes exist.
 */
public final class BlinkAbility {
	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("duality", "blink");
	// ---- tunable ----
	private static final double MAX_DISTANCE = 20.0;
	private static final double WALL_CLEARANCE = 0.5;
	private static final double COOLDOWN_TICKS = 20;

	private BlinkAbility() {
	}

	public static Ability build() {
		return Ability.builder(ID, AbilityType.INSTANT) //
				.cooldown(AbilityValue.constant(COOLDOWN_TICKS)) //
				.onActivate(ctx -> {
					LivingEntity caster = ctx.caster();
					Level level = caster.level();
					Vec3 eyeFrom = caster.getEyePosition();
					Vec3 look = caster.getLookAngle();
					Vec3 eyeTo = eyeFrom.add(look.scale(MAX_DISTANCE));
					BlockHitResult hit = level.clip(new ClipContext(eyeFrom, eyeTo, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
					Vec3 landEye = hit.getType() == HitResult.Type.MISS ? eyeTo
							: eyeFrom.add(look.scale(Math.max(0, hit.getLocation().subtract(eyeFrom).length() - WALL_CLEARANCE)));
					double feetY = landEye.y - caster.getEyeHeight();
					if (level instanceof ServerLevel serverLevel) {
						serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, eyeFrom.x, eyeFrom.y, eyeFrom.z, 15, 0.3, 0.4, 0.3, 0.02);
						serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, landEye.x, feetY + 0.5, landEye.z, 15, 0.3, 0.4, 0.3, 0.02);
					}
					caster.teleportTo(landEye.x, feetY, landEye.z);
					caster.fallDistance = 0;
				}).build();
	}
}
