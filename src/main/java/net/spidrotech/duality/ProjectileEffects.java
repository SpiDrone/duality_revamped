package net.spidrotech.duality;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleOptions;

import java.util.function.Consumer;
import java.util.function.BiConsumer;

/**
 * Small library of reusable, composable projectile hit-effects. Chain these with
 * ProjectileDefinition.Builder#addEntityHitEffect to build up "contents" like acidic, fiery,
 * etc, instead of writing bespoke hit-handling code for every projectile.
 *
 * Example - a fire bolt that both burns and does its base damage:
 *   ProjectileRegistry.register("fire_bolt", b -> b
 *       .addEntityHitEffect(ProjectileEffects.damage(3))
 *       .addEntityHitEffect(ProjectileEffects.setOnFire(4)));
 *
 * NOTE: the damage() effect below uses a generic magic damage source as a placeholder - if you
 * want damage type to matter (e.g. angelic damage being extra effective against demons), that's
 * a bigger design piece (custom DamageType per magicSchool, resistances, etc) I haven't built
 * since you haven't asked for it yet - say the word if you want to go there.
 */
public final class ProjectileEffects {
	private ProjectileEffects() {
	}

	public static BiConsumer<ProjectileHitContext, Entity> damage(double amount) {
		return (hit, target) -> {
			if (target instanceof LivingEntity living) {
				Entity caster = hit.caster();
				living.hurt(living.damageSources().indirectMagic(caster, caster), (float) amount);
			}
		};
	}

	public static BiConsumer<ProjectileHitContext, Entity> setOnFire(int seconds) {
		return (hit, target) -> target.igniteForSeconds(seconds);
	}

	/** A damage-over-time poison effect - originally written as a placeholder illustration, now
	 *  the real mechanic behind acid-spit-type attacks (see spitter demon acid_spit). Swap
	 *  MobEffects.POISON for a custom mob effect later if you want acid to look/behave distinct
	 *  from vanilla poison (different particle color, different resistance interactions, etc) -
	 *  say the word and I'll build that out. */
	public static BiConsumer<ProjectileHitContext, Entity> acidic(int durationTicks, int amplifier) {
		return (hit, target) -> {
			if (target instanceof LivingEntity living) {
				living.addEffect(new MobEffectInstance(MobEffects.POISON, durationTicks, amplifier));
			}
		};
	}

	public static BiConsumer<ProjectileHitContext, Entity> knockback(double strength) {
		return (hit, target) -> {
			var caster = hit.caster();
			if (caster == null)
				return;
			var direction = target.position().subtract(caster.position()).normalize();
			target.push(direction.x * strength, 0.2 * strength, direction.z * strength);
		};
	}

	/** Damage + fire duration that scale with a named param on the definition (e.g. "firePower"),
	 *  falling back to 1.0 (no scaling) if the definition never set one. */
	public static BiConsumer<ProjectileHitContext, Entity> scaledFireDamage(double baseDamage, int baseFireSeconds, String paramKey) {
		return (hit, target) -> {
			double scale = hit.definition().param(paramKey, 1.0);
			if (target instanceof LivingEntity living) {
				Entity caster = hit.caster();
				living.hurt(living.damageSources().indirectMagic(caster, caster), (float) (baseDamage * scale));
			}
			target.igniteForSeconds((float) (baseFireSeconds * scale));
		};
	}

	/** One-time particle burst at wherever a direct entity hit happened - independent of the
	 *  projectile's own in-flight ParticleEmitter trail (that only fires every tick while
	 *  flying, via AbilityProjectileBase#tick - never on impact). spread controls how far the
	 *  burst scatters on each axis (0 = all particles spawn dead-center). */
	public static BiConsumer<ProjectileHitContext, Entity> impactParticles(ParticleOptions particle, int count, double spread) {
		return (hit, target) -> spawnBurst(hit, particle, count, spread);
	}

	/** Same as impactParticles, but for onBlockHit instead of onEntityHit - a projectile that
	 *  splatters on a wall wants the same kind of burst as one that splatters on a mob. */
	public static Consumer<ProjectileHitContext> impactParticlesOnBlock(ParticleOptions particle, int count, double spread) {
		return hit -> spawnBurst(hit, particle, count, spread);
	}

	private static void spawnBurst(ProjectileHitContext hit, ParticleOptions particle, int count, double spread) {
		ServerLevel level = hit.level();
		if (level == null)
			return; // defensive only - hit contexts are always constructed with a real level server-side
		Vec3 pos = hit.hitPos();
		level.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, 0.02);
	}
}