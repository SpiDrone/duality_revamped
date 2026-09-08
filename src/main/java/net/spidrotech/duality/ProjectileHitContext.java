package net.spidrotech.duality;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;

/**
 * Passed to a ProjectileDefinition's hit callbacks - who cast it (may be null if the caster has
 * since died/logged off), the definition, where the hit happened, and the level it happened in
 * (needed for anything that spawns particles or otherwise touches the world on impact, not just
 * the target entity - see ProjectileEffects#impactParticles).
 */
public final class ProjectileHitContext {
	private final LivingEntity caster;
	private final ProjectileDefinition definition;
	private final Vec3 hitPos;
	private final ServerLevel level;

	public ProjectileHitContext(LivingEntity caster, ProjectileDefinition definition, Vec3 hitPos, ServerLevel level) {
		this.caster = caster;
		this.definition = definition;
		this.hitPos = hitPos;
		this.level = level;
	}

	public LivingEntity caster() {
		return caster;
	}

	public ProjectileDefinition definition() {
		return definition;
	}

	public Vec3 hitPos() {
		return hitPos;
	}

	public ServerLevel level() {
		return level;
	}
}