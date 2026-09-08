package net.spidrotech.duality;

import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleOptions;

import java.util.function.Function;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * All the tunable data for one projectile-based power - speed, lifetime, damage, particles,
 * magic school, and free-form "params" for whatever ad-hoc knobs a specific projectile needs
 * (firePower, acidStrength, whatever). Immutable once built; use toBuilder() to clone-with-
 * changes for variants (that's what ProjectileRegistry#registerVariant uses under the hood).
 */
public final class ProjectileDefinition {
	private final ResourceLocation id;
	private final double speed;
	private final double lifetimeTicks;
	private final double damage;
	private final String magicSchool;
	private final ParticleOptions trailParticle;
	private final ParticleOptions impactParticle;
	private final double cooldownTicks;
	private final List<AbilityCondition> castConditions;
	private final Map<String, Double> params;
	private final BiConsumer<ProjectileHitContext, Entity> onEntityHit;
	private final Consumer<ProjectileHitContext> onBlockHit;
	private final Function<ServerLevel, AbilityProjectileBase> spawner;
	private final boolean shaking;
	private final float widthScale;
	private final float heightScale;
	private final float visualScale;
	private final ParticleEmitter particleEmitter;
	private final double gravityStrength;
	private final int pierce;
	private final boolean noClip;
	private final double aoeRadius;
	private final BiConsumer<ProjectileHitContext, Entity> onAreaHit;
	private final ResourceLocation modelKey;
	private final ResourceLocation texture;

	private ProjectileDefinition(Builder builder) {
		this.id = builder.id;
		this.speed = builder.speed;
		this.lifetimeTicks = builder.lifetimeTicks;
		this.damage = builder.damage;
		this.magicSchool = builder.magicSchool;
		this.trailParticle = builder.trailParticle;
		this.impactParticle = builder.impactParticle;
		this.cooldownTicks = builder.cooldownTicks;
		this.castConditions = List.copyOf(builder.castConditions);
		this.params = Map.copyOf(builder.params);
		this.onEntityHit = builder.onEntityHit;
		this.onBlockHit = builder.onBlockHit;
		this.spawner = builder.spawner;
		this.shaking = builder.shaking;
		this.widthScale = builder.widthScale;
		this.heightScale = builder.heightScale;
		this.visualScale = builder.visualScale;
		this.particleEmitter = builder.particleEmitter;
		this.gravityStrength = builder.gravityStrength;
		this.pierce = Math.max(0, Math.min(100, builder.pierce));
		this.noClip = builder.noClip;
		this.aoeRadius = builder.aoeRadius;
		this.onAreaHit = builder.onAreaHit;
		this.modelKey = builder.modelKey;
		this.texture = builder.texture;
		if (this.spawner == null) {
			throw new IllegalStateException("ProjectileDefinition " + id + " never called Builder#spawner(...) - " + "every definition needs to say which concrete entity class represents it.");
		}
	}

	public ResourceLocation id() {
		return id;
	}

	public double speed() {
		return speed;
	}

	public double lifetimeTicks() {
		return lifetimeTicks;
	}

	public double damage() {
		return damage;
	}

	public String magicSchool() {
		return magicSchool;
	}

	public ParticleOptions trailParticle() {
		return trailParticle;
	}

	public ParticleOptions impactParticle() {
		return impactParticle;
	}

	public double cooldownTicks() {
		return cooldownTicks;
	}

	public List<AbilityCondition> castConditions() {
		return castConditions;
	}

	public BiConsumer<ProjectileHitContext, Entity> onEntityHit() {
		return onEntityHit;
	}

	public Consumer<ProjectileHitContext> onBlockHit() {
		return onBlockHit;
	}

	/** Constructs the concrete entity instance (a subclass of AbilityProjectileBase) this
	 *  definition should spawn when cast - e.g. `level -> new FireballEntity(MY_TYPE.get(), level)`.
	 *  Every definition must set this via Builder#spawner. */
	public Function<ServerLevel, AbilityProjectileBase> spawner() {
		return spawner;
	}

	public boolean isShaking() {
		return shaking;
	}

	public float widthScale() {
		return widthScale;
	}

	public float heightScale() {
		return heightScale;
	}

	/** Purely cosmetic - scales the rendered model via PoseStack in the renderer, completely
	 *  independent of widthScale/heightScale (which affect the actual collision hitbox). A
	 *  projectile can look bigger or smaller than what it actually hits. 1.0 = normal size. */
	public float visualScale() {
		return visualScale;
	}

	/** May be null - not every projectile needs particles (e.g. one relying entirely on a 3D
	 *  model like the fireball). Always null-check before use. */
	public ParticleEmitter particleEmitter() {
		return particleEmitter;
	}

	/** Downward acceleration applied each tick, in blocks/tick^2. 0 (the default) = perfectly
	 *  straight flight - no arc. Vanilla's own gravity is fully disabled for all projectiles
	 *  (see AbilityProjectileBase#travel), so this is the only source of any curve. */
	public double gravityStrength() {
		return gravityStrength;
	}

	/** How many entities beyond the first a direct hit can pass through before the projectile
	 *  actually stops, 0-100. 0 (default) = stops on the first hit, matching the old behavior. */
	public int pierce() {
		return pierce;
	}

	/** If true, block collisions never stop the projectile - it flies straight through terrain.
	 *  Entity collisions (direct hits / pierce) are unaffected by this. */
	public boolean noClip() {
		return noClip;
	}

	/** Radius (blocks) beyond the actual hitbox to splash-damage nearby entities each tick,
	 *  without ever stopping the projectile - "near misses" still take damage, and each entity
	 *  only gets hit once per projectile (tracked internally), no matter how long it lingers
	 *  nearby. 0 (default) = disabled. */
	public double aoeRadius() {
		return aoeRadius;
	}

	/** Callback for area-of-effect hits (see aoeRadius) - separate from onEntityHit, since a
	 *  near-miss splash is conceptually different from (and often weaker than) a direct hit. */
	public BiConsumer<ProjectileHitContext, Entity> onAreaHit() {
		return onAreaHit;
	}

	/** Which registered GEOMETRY (see ProjectileVisualRegistry) this projectile uses - the
	 *  Blockbench/MCreator model shape only, no texture attached. Defaults to this definition's
	 *  own id() if never set via Builder#model(...) - override it when several definitions
	 *  (e.g. a whole family of same-shaped, different-colored bolts) should share one shape
	 *  instead of each needing its own registry entry. */
	public ResourceLocation modelKey() {
		return modelKey != null ? modelKey : id;
	}

	/** Texture override for this specific definition. May be null - when null, the renderer
	 *  falls back to the DEFAULT texture registered alongside modelKey()'s shape (see
	 *  ProjectileVisualRegistry#register). Setting this is how a variant reskins the same shape
	 *  (e.g. a "shadow fireball") without registering a whole new model entry - just
	 *  toBuilder(...).texture(...) on top of the base definition. */
	public ResourceLocation texture() {
		return texture;
	}

	/** Reads one of the free-form numeric knobs (e.g. "firePower", "acidStrength"), falling
	 *  back to a default if this definition never set one. This is what lets an override say
	 *  param("firePower", 2) without every projectile needing a dedicated field for it. */
	public double param(String key, double fallback) {
		return params.getOrDefault(key, fallback);
	}

	public static Builder builder(ResourceLocation id) {
		return new Builder(id);
	}

	/** Starts a new Builder pre-populated with everything from this definition, under a new id -
	 *  the basis for the "override" pattern (see ProjectileRegistry#registerVariant). */
	public Builder toBuilder(ResourceLocation newId) {
		Builder b = new Builder(newId);
		b.speed = this.speed;
		b.lifetimeTicks = this.lifetimeTicks;
		b.damage = this.damage;
		b.magicSchool = this.magicSchool;
		b.trailParticle = this.trailParticle;
		b.impactParticle = this.impactParticle;
		b.cooldownTicks = this.cooldownTicks;
		b.castConditions = new ArrayList<>(this.castConditions);
		b.params = new HashMap<>(this.params);
		b.onEntityHit = this.onEntityHit;
		b.onBlockHit = this.onBlockHit;
		b.spawner = this.spawner;
		b.shaking = this.shaking;
		b.widthScale = this.widthScale;
		b.heightScale = this.heightScale;
		b.visualScale = this.visualScale;
		b.particleEmitter = this.particleEmitter;
		b.gravityStrength = this.gravityStrength;
		b.pierce = this.pierce;
		b.noClip = this.noClip;
		b.aoeRadius = this.aoeRadius;
		b.onAreaHit = this.onAreaHit;
		b.modelKey = this.modelKey;
		b.texture = this.texture;
		return b;
	}

	public static final class Builder {
		private final ResourceLocation id;
		private double speed = 1.5;
		private double lifetimeTicks = 200; // 10s max flight time before despawning
		private double damage = 2.0;
		private String magicSchool = "neutral"; // e.g. "demonic", "wiccan", "angelic" - free-form tag for now
		private ParticleOptions trailParticle;
		private ParticleOptions impactParticle;
		private double cooldownTicks = 0;
		private List<AbilityCondition> castConditions = new ArrayList<>();
		private Map<String, Double> params = new LinkedHashMap<>();
		private BiConsumer<ProjectileHitContext, Entity> onEntityHit = (hit, target) -> {
		};
		private Consumer<ProjectileHitContext> onBlockHit = hit -> {
		};
		private Function<ServerLevel, AbilityProjectileBase> spawner;
		private boolean shaking = false;
		private float widthScale = 1.0f;
		private float heightScale = 1.0f;
		private float visualScale = 1.0f;
		private ParticleEmitter particleEmitter;
		private double gravityStrength = 0.0;
		private int pierce = 0;
		private boolean noClip = false;
		private double aoeRadius = 0.0;
		private BiConsumer<ProjectileHitContext, Entity> onAreaHit = (hit, target) -> {
		};
		private ResourceLocation modelKey;
		private ResourceLocation texture;

		private Builder(ResourceLocation id) {
			this.id = id;
		}

		public Builder speed(double value) {
			this.speed = value;
			return this;
		}

		public Builder lifetimeTicks(double value) {
			this.lifetimeTicks = value;
			return this;
		}

		public Builder damage(double value) {
			this.damage = value;
			return this;
		}

		public Builder magicSchool(String value) {
			this.magicSchool = value;
			return this;
		}

		public Builder trailParticle(ParticleOptions value) {
			this.trailParticle = value;
			return this;
		}

		public Builder impactParticle(ParticleOptions value) {
			this.impactParticle = value;
			return this;
		}

		public Builder cooldownTicks(double value) {
			this.cooldownTicks = value;
			return this;
		}

		public Builder castCondition(AbilityCondition condition) {
			castConditions.add(condition);
			return this;
		}

		public Builder param(String key, double value) {
			params.put(key, value);
			return this;
		}

		public Builder onEntityHit(BiConsumer<ProjectileHitContext, Entity> callback) {
			this.onEntityHit = callback;
			return this;
		}

		public Builder onBlockHit(Consumer<ProjectileHitContext> callback) {
			this.onBlockHit = callback;
			return this;
		}

		/** Required. Says which concrete AbilityProjectileBase subclass represents this
		 *  definition visually - e.g. spawner(level -> new AbilityProjectileEntity(MY_TYPE.get(), level))
		 *  for the plain MCreator entity, or a different constructor for a differently-modeled one. */
		public Builder spawner(Function<ServerLevel, AbilityProjectileBase> spawner) {
			this.spawner = spawner;
			return this;
		}

		public Builder isShaking(boolean value) {
			this.shaking = value;
			return this;
		}

		public Builder addAreaHitEffect(BiConsumer<ProjectileHitContext, Entity> effect) {
			this.onAreaHit = this.onAreaHit.andThen(effect);
			return this;
		}

		/** Scales both the visual model (via the renderer - see AbilityProjectileRenderer) and
		 *  the actual collision hitbox (via AbilityProjectileBase#getDimensions). 1.0 = normal
		 *  size for both axes. */
		public Builder projectileScale(float widthScale, float heightScale) {
			this.widthScale = widthScale;
			this.heightScale = heightScale;
			return this;
		}

		/** Purely visual, independent of projectileScale (the hitbox). 1.0 = normal size. */
		public Builder visualScale(float scale) {
			this.visualScale = scale;
			return this;
		}

		public Builder particleEmitter(ParticleEmitter emitter) {
			this.particleEmitter = emitter;
			return this;
		}

		/** 0 = dead straight flight (the default). Positive values curve it downward into an
		 *  arc over time - roughly, 0.05-0.1 gives a gentle lob; vanilla arrow-like gravity is
		 *  around 0.05/tick for reference. */
		public Builder gravityStrength(double value) {
			this.gravityStrength = value;
			return this;
		}

		/** 0-100. How many entities beyond the first a direct hit can pass through before the
		 *  projectile stops. Clamped to that range. */
		public Builder pierce(int count) {
			this.pierce = count;
			return this;
		}

		/** If true, the projectile flies straight through blocks instead of stopping on them. */
		public Builder noClip(boolean value) {
			this.noClip = value;
			return this;
		}

		/** Sets both the splash radius and its hit callback together, since one without the
		 *  other doesn't do anything. See ProjectileDefinition#aoeRadius. */
		public Builder areaOfEffect(double radius, BiConsumer<ProjectileHitContext, Entity> onHit) {
			this.aoeRadius = radius;
			this.onAreaHit = onHit;
			return this;
		}

		/** Chains an additional entity-hit effect after whatever's already set, instead of
		 *  replacing it - use this to layer effects (e.g. base damage + an acidic DOT) via
		 *  ProjectileEffects. */
		public Builder addEntityHitEffect(BiConsumer<ProjectileHitContext, Entity> effect) {
			this.onEntityHit = this.onEntityHit.andThen(effect);
			return this;
		}

		/** Which registered shape (see ProjectileVisualRegistry) this projectile uses. Defaults
		 *  to the definition's own id() if never called - only needs setting when several
		 *  definitions should share one shape instead of each getting its own registry entry. */
		public Builder model(ResourceLocation key) {
			this.modelKey = key;
			return this;
		}

		public Builder model(String key) {
			return model(ResourceLocation.fromNamespaceAndPath("duality", key));
		}

		/** Texture override, independent of the model/shape. Leave unset to use whatever
		 *  DEFAULT texture the shape was registered with - this is the one call a reskinned
		 *  variant needs, with no registry entry required. */
		public Builder texture(ResourceLocation location) {
			this.texture = location;
			return this;
		}

		public Builder texture(String path) {
			return texture(ResourceLocation.parse(path));
		}

		public ProjectileDefinition build() {
			return new ProjectileDefinition(this);
		}
	}
}