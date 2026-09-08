package net.spidrotech.duality;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;
import java.util.*;

/**
 * Chain lightning: an instant, tree-shaped strike. Fires from the caster's look direction to
 * find the first target, then expands outward in generations - each jump pulls in the closest
 * not-yet-hit LivingEntity within range of ANY currently-hit entity, until either maxJumps
 * generations or maxStems total targets is reached, whichever comes first.
 *
 * Visually, a procedural lightning bolt (LightningVisualBase) is drawn along each edge of the actual chain tree
 * (caster -> first target, then parent -> child for every jump after that) rather than radiating
 * from the caster, so it reads as a real chain rather than a starburst. All bolts are spawned in
 * the same method call (same server tick), so they appear simultaneously.
 *
 * Register once at startup:
 *   AbilityManager.get().register(LightningStrikeAbility.builder(ID)
 *       .range(16).maxJumps(4).maxStems(10).damage(6)
 *       .damageType("magic", "lightning")
 *       .colorLighting(0xFFFFFFFF, 0xFF99CCFF, 0xFF3388FF)
 *       .boltDurationTicks(10)
 *       .build());
 */
public final class LightningStrikeAbility extends Ability {
	// Rough guess at "chest/arm height" as a fraction of the caster's own height - matches the
	// same convention used in SimpleProjectileAbility.
	private static final double CHEST_HEIGHT_FRACTION = 0.6;
	private final double range;
	private final int maxJumps;
	private final int maxStems;
	private final double damage;
	private final String damageCategory;
	private final String damageElement;
	private final LightningColorScheme colors;
	private final int boltDurationTicks;
	private final Function<ServerLevel, LightningVisualBase> spawner;

	private LightningStrikeAbility(Builder builder) {
		super(builder.id, AbilityType.INSTANT);
		this.range = builder.range;
		this.maxJumps = builder.maxJumps;
		this.maxStems = builder.maxStems;
		this.damage = builder.damage;
		this.damageCategory = builder.damageCategory;
		this.damageElement = builder.damageElement;
		this.colors = builder.colors;
		this.boltDurationTicks = builder.boltDurationTicks;
		this.spawner = builder.spawner;
		if (this.spawner == null) {
			throw new IllegalStateException("LightningStrikeAbility " + builder.id + " never called Builder#spawner(...) - needs to know which concrete " + "LightningVisualBase subclass to spawn (see the MCreator entity you create).");
		}
		setCooldown(AbilityValue.constant(builder.cooldownTicks));
	}

	public static Builder builder(ResourceLocation id) {
		return new Builder(id);
	}

	public double range() {
		return range;
	}

	public int maxJumps() {
		return maxJumps;
	}

	public int maxStems() {
		return maxStems;
	}

	public double damage() {
		return damage;
	}

	public String damageCategory() {
		return damageCategory;
	}

	public String damageElement() {
		return damageElement;
	}

	public LightningColorScheme colors() {
		return colors;
	}

	public int boltDurationTicks() {
		return boltDurationTicks;
	}

	@Override
	public void onActivate(AbilityContext ctx) {
		LivingEntity caster = ctx.caster();
		if (!(caster.level() instanceof ServerLevel level))
			return;
		LivingEntity initialTarget = findInitialTarget(caster, range);
		if (initialTarget == null)
			return; // TODO: visual/audio indicator for "no target found"
		ChainResult chain = buildChain(caster, initialTarget, range, maxJumps, maxStems);
		Random seedSource = new Random();
		Vec3 chestPos = caster.position().add(0, caster.getBbHeight() * CHEST_HEIGHT_FRACTION, 0);
		// Caster -> first target. Visual origin is chest height, deliberately decoupled from the
		// eye-level raycast above (which is about aim direction, not where the bolt appears to
		// come from).
		strikeVisual(level, chestPos, initialTarget.position(), seedSource.nextInt());
		spawnDecorativeSparks(caster, level, initialTarget.position(), seedSource);
		applyDamage(caster, initialTarget);
		// Every subsequent hop, along the actual tree edge it arrived by.
		for (LivingEntity target : chain.hitOrder()) {
			if (target == initialTarget)
				continue;
			LivingEntity parent = chain.parentOf().get(target);
			if (parent == null)
				continue;
			strikeVisual(level, parent.position(), target.position(), seedSource.nextInt());
			spawnDecorativeSparks(caster, level, target.position(), seedSource);
			applyDamage(caster, target);
		}
	}

	/** Extra, purely-cosmetic bolts that shoot off from a strike point in random directions and
	 *  just fizzle out - hit nothing, deal no damage. Existing in addition to the "real" chain
	 *  bolts, meant to make each strike feel busier/more chaotic rather than one clean line per
	 *  hit. Still respects blocks (clipped to the nearest solid surface), same as real bolts. */
	private void spawnDecorativeSparks(Entity contextEntity, ServerLevel level, Vec3 origin, Random random) {
		int sparkCount = 1 + random.nextInt(3); // 1-3 per strike point
		for (int i = 0; i < sparkCount; i++) {
			Vec3 direction = randomDirection(random);
			double length = 1.5 + random.nextDouble() * 2.5;
			Vec3 desiredEnd = origin.add(direction.scale(length));
			Vec3 clippedEnd = firstBlockHit(contextEntity, origin, desiredEnd).orElse(desiredEnd);
			strikeVisual(level, origin, clippedEnd, random.nextInt());
		}
	}

	private static Vec3 randomDirection(Random random) {
		double theta = random.nextDouble() * Math.PI * 2;
		double phi = Math.acos(2 * random.nextDouble() - 1);
		return new Vec3(Math.sin(phi) * Math.cos(theta), Math.cos(phi), Math.sin(phi) * Math.sin(theta));
	}

	/** First solid-block hit point along a ray, if any - shared by both line-of-sight checks and
	 *  clipping decorative sparks so they don't visually poke through walls. */
	private static Optional<Vec3> firstBlockHit(Entity contextEntity, Vec3 from, Vec3 to) {
		BlockHitResult hit = contextEntity.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, contextEntity));
		return hit.getType() == HitResult.Type.MISS ? Optional.empty() : Optional.of(hit.getLocation());
	}

	private static boolean hasLineOfSight(Entity contextEntity, Vec3 from, Vec3 to) {
		return firstBlockHit(contextEntity, from, to).isEmpty();
	}

	private void applyDamage(LivingEntity caster, LivingEntity target) {
		DamageSource source = caster.damageSources().indirectMagic(caster, caster);
		target.hurt(source, (float) damage);
	}

	private void strikeVisual(ServerLevel level, Vec3 from, Vec3 to, int seed) {
		LightningVisualBase bolt = spawner.apply(level);
		bolt.setPos(from.x, from.y, from.z);
		bolt.configure(to, colors, boltDurationTicks, seed);
		level.addFreshEntity(bolt);
	}

	/** The starting point of the chain - the closest LivingEntity along the caster's look
	 *  direction, within range. */
	private static LivingEntity findInitialTarget(LivingEntity caster, double range) {
		Vec3 eyePos = caster.getEyePosition();
		Vec3 look = caster.getLookAngle();
		Vec3 reachEnd = eyePos.add(look.scale(range));
		AABB searchBox = caster.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
		LivingEntity closest = null;
		double closestDistSqr = Double.MAX_VALUE;
		for (Entity candidate : caster.level().getEntities(caster, searchBox, e -> e instanceof LivingEntity && e != caster && e.isAlive() && !e.isSpectator())) {
			AABB box = candidate.getBoundingBox().inflate(0.3);
			Optional<Vec3> hit = box.clip(eyePos, reachEnd);
			if (hit.isPresent() && hasLineOfSight(caster, eyePos, hit.get())) {
				double distSqr = eyePos.distanceToSqr(hit.get());
				if (distSqr < closestDistSqr) {
					closestDistSqr = distSqr;
					closest = (LivingEntity) candidate;
				}
			}
		}
		return closest;
	}

	private record ChainResult(List<LivingEntity> hitOrder, Map<LivingEntity, LivingEntity> parentOf) {
	}

	/** BFS-style tree expansion: each generation pulls in the closest not-yet-hit entities
	 *  within range of ANY currently-hit entity, globally sorted by distance (not per-branch),
	 *  bounded by maxStems total and maxJumps generations. Tracks which existing hit each new
	 *  target actually jumped from, so the visual can connect real tree edges. */
	private static ChainResult buildChain(LivingEntity caster, LivingEntity initialTarget, double range, int maxJumps, int maxStems) {
		List<LivingEntity> hitOrder = new ArrayList<>();
		Set<LivingEntity> hitSet = new HashSet<>();
		Map<LivingEntity, LivingEntity> parentOf = new HashMap<>();
		hitOrder.add(initialTarget);
		hitSet.add(initialTarget);
		List<LivingEntity> frontier = new ArrayList<>(List.of(initialTarget));
		for (int jump = 0; jump < maxJumps && hitOrder.size() < maxStems && !frontier.isEmpty(); jump++) {
			// (candidate, source-it-was-found-from, distance-to-that-source)
			List<Object[]> candidates = new ArrayList<>();
			Set<LivingEntity> seenThisRound = new HashSet<>();
			for (LivingEntity source : frontier) {
				AABB searchBox = source.getBoundingBox().inflate(range);
				for (Entity candidate : source.level().getEntities(source, searchBox, e -> e instanceof LivingEntity && e != caster && e.isAlive() && !e.isSpectator())) {
					LivingEntity le = (LivingEntity) candidate;
					if (hitSet.contains(le) || seenThisRound.contains(le))
						continue;
					double dist = source.position().distanceTo(le.position());
					if (dist <= range && hasLineOfSight(source, source.getEyePosition(), le.getEyePosition())) {
						candidates.add(new Object[]{le, source, dist});
						seenThisRound.add(le);
					}
				}
			}
			candidates.sort(Comparator.comparingDouble(entry -> (double) entry[2]));
			List<LivingEntity> newFrontier = new ArrayList<>();
			for (Object[] entry : candidates) {
				if (hitOrder.size() >= maxStems)
					break;
				LivingEntity le = (LivingEntity) entry[0];
				LivingEntity source = (LivingEntity) entry[1];
				hitOrder.add(le);
				hitSet.add(le);
				parentOf.put(le, source);
				newFrontier.add(le);
			}
			frontier = newFrontier;
		}
		return new ChainResult(hitOrder, parentOf);
	}

	public static final class Builder {
		private final ResourceLocation id;
		private double range = 8.0;
		private int maxJumps = 3;
		private int maxStems = 5;
		private double damage = 4.0;
		private double cooldownTicks = 0;
		private String damageCategory = "magic";
		private String damageElement = "lightning";
		private LightningColorScheme colors = LightningColorScheme.classicBlue();
		private int boltDurationTicks = 10;
		private Function<ServerLevel, LightningVisualBase> spawner;

		private Builder(ResourceLocation id) {
			this.id = id;
		}

		/** Max distance, in blocks, each individual jump can reach - both the initial cast and
		 *  every subsequent chain link. */
		public Builder range(double value) {
			this.range = value;
			return this;
		}

		/** Max number of chain "generations" - how many rounds of jumping happen. */
		public Builder maxJumps(int value) {
			this.maxJumps = value;
			return this;
		}

		/** Hard cap on total entities struck across the whole chain, regardless of maxJumps. */
		public Builder maxStems(int value) {
			this.maxStems = value;
			return this;
		}

		public Builder damage(double value) {
			this.damage = value;
			return this;
		}

		public Builder cooldownTicks(double value) {
			this.cooldownTicks = value;
			return this;
		}

		/** Freeform category/element tags (e.g. "magic", "lightning") - not tied to a specific
		 *  vanilla DamageType, just metadata for your own systems (resistances, UI) to read via
		 *  damageCategory()/damageElement(). Actual damage uses a generic magic damage source. */
		public Builder damageType(String category, String element) {
			this.damageCategory = category;
			this.damageElement = element;
			return this;
		}

		/** Colors for the procedural bolt rendering - reusable across other abilities by just
		 *  passing a different scheme. See LightningColorScheme. */
		public Builder colorLighting(int innerColor, int outerColor, int glowColor) {
			this.colors = new LightningColorScheme(innerColor, outerColor, glowColor);
			return this;
		}

		/** How long (in ticks) each bolt takes to fade out after appearing. */
		public Builder boltDurationTicks(int value) {
			this.boltDurationTicks = value;
			return this;
		}

		/** Required. Which concrete LightningVisualBase subclass to spawn - e.g.
		 *  spawner(level -> new YourLightningEntity(DualityModEntities.LIGHTNING_VISUAL.get(), level))
		 *  once you've created that entity in MCreator. */
		public Builder spawner(Function<ServerLevel, LightningVisualBase> spawner) {
			this.spawner = spawner;
			return this;
		}

		public LightningStrikeAbility build() {
			return new LightningStrikeAbility(this);
		}
	}
}