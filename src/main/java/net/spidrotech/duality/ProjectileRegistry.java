package net.spidrotech.duality;

import net.minecraft.resources.ResourceLocation;

import java.util.function.UnaryOperator;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * The "database" for simple projectile abilities. Register a base definition once, then spin
 * off as many variants as you want by overriding just the fields that differ. Every registered
 * definition automatically gets a matching SimpleProjectileAbility registered into
 * AbilityManager, so adding a new projectile power really is one call.
 *
 * Matches exactly what you described:
 *
 *   ProjectileRegistry.register("fire_bolt", b -> b
 *       .speed(1.5)
 *       .damage(3)
 *       .magicSchool("demonic")
 *       .trailParticle(ParticleTypes.FLAME)
 *       .param("firePower", 1)
 *       .addEntityHitEffect(ProjectileEffects.setOnFire(4)));
 *
 *   ProjectileRegistry.registerVariant("greater_fire_bolt", "fire_bolt", b -> b
 *       .param("firePower", 2)
 *       .damage(5));
 *
 * "greater_fire_bolt" inherits everything from "fire_bolt" (speed, particle, the onFire effect)
 * except the two fields the override touches.
 */
public final class ProjectileRegistry {
	private static final String MOD_ID = "duality";
	private static final Map<ResourceLocation, ProjectileDefinition> DEFINITIONS = new LinkedHashMap<>();

	private ProjectileRegistry() {
	}

	public static ProjectileDefinition register(String id, UnaryOperator<ProjectileDefinition.Builder> config) {
		ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(MOD_ID, id);
		ProjectileDefinition definition = config.apply(ProjectileDefinition.builder(rl)).build();
		finish(rl, definition);
		return definition;
	}

	/** Clones baseId's definition and applies your overrides on top - only the fields you touch
	 *  in the lambda actually change from the base. */
	public static ProjectileDefinition registerVariant(String newId, String baseId, UnaryOperator<ProjectileDefinition.Builder> overrides) {
		ProjectileDefinition base = get(baseId);
		ResourceLocation newRl = ResourceLocation.fromNamespaceAndPath(MOD_ID, newId);
		ProjectileDefinition definition = overrides.apply(base.toBuilder(newRl)).build();
		finish(newRl, definition);
		return definition;
	}

	private static void finish(ResourceLocation id, ProjectileDefinition definition) {
		if (DEFINITIONS.putIfAbsent(id, definition) != null) {
			throw new IllegalStateException("Duplicate projectile definition id: " + id);
		}
		AbilityManager.get().register(new SimpleProjectileAbility(definition));
	}

	public static ProjectileDefinition get(String id) {
		ResourceLocation rl = id.indexOf(':') >= 0 ? ResourceLocation.parse(id) : ResourceLocation.fromNamespaceAndPath(MOD_ID, id);
		ProjectileDefinition def = DEFINITIONS.get(rl);
		if (def == null)
			throw new IllegalStateException("No such projectile definition: " + rl);
		return def;
	}
}