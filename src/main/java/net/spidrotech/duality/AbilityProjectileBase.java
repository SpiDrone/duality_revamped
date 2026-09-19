package net.spidrotech.duality;

import org.slf4j.Logger;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

import java.util.UUID;
import java.util.Set;
import java.util.Optional;
import java.util.HashSet;

import com.mojang.logging.LogUtils;

/**
 * Shared behavior for every projectile-visual entity - AbilityProjectileEntity, and any future
 * MCreator entity class for a different shape/model. All the "how projectiles actually work"
 * logic lives here in exactly one place. Each MCreator-generated subclass only needs to change
 * its `extends PathfinderMob` to `extends AbilityProjectileBase` (and add an import for this
 * class, since it's in net.spidrotech.duality while the MCreator entity is likely in
 * net.spidrotech.duality.entity) - everything else (model, renderer, registration) stays exactly
 * as MCreator generated it.
 *
 * Extends PathfinderMob (not the vanilla Projectile hierarchy) on purpose - this is MCreator's
 * standard "flying, AI-less mob" pattern for projectiles. The damage-immunity / no-gravity /
 * no-fall-damage overrides below exist purely to neutralize "mob" behavior a projectile
 * shouldn't have; movement and collision are driven entirely by this class's own tick(), not by
 * any Mob/PathfinderMob AI.
 */
public abstract class AbilityProjectileBase extends PathfinderMob {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final EntityDataAccessor<String> DATA_DEFINITION_ID = SynchedEntityData.defineId(AbilityProjectileBase.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Boolean> DATA_HAS_OWNER = SynchedEntityData.defineId(AbilityProjectileBase.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_OWNER_ID = SynchedEntityData.defineId(AbilityProjectileBase.class, EntityDataSerializers.INT);
	private int ticksAlive = 0;
	private int ownerGraceTicks = 4; // ignore the caster for the first few ticks so it doesn't immediately self-hit
	private Vec3 previousTickPosition;
	private int piercedCount = 0;
	// Shared between direct-hit piercing and area-of-effect - once an entity is in here, neither
	// mechanism will hit it again for the rest of this projectile's flight.
	private final Set<UUID> alreadyHitEntities = new HashSet<>();

	protected AbilityProjectileBase(EntityType<? extends AbilityProjectileBase> type, Level world) {
		super(type, world);
		this.xpReward = 0;
		this.setNoAi(true);
		this.setPersistenceRequired();
		this.noPhysics = true; // skips collision resolution - but NOT gravity, see travel() below
		this.setNoGravity(true);
	}

	/** super.tick() (via PathfinderMob/Mob/LivingEntity) runs the full vanilla movement chain,
	 *  which calls travel() every tick to apply input + gravity + friction to deltaMovement -
	 *  silently fighting our own manual movement in tick() below and causing an unwanted arc.
	 *  No-op it entirely: movement (and gravity, via ProjectileDefinition#gravityStrength) is
	 *  handled exclusively by this class's own tick() logic instead.
	 *  Must be public - LivingEntity#travel is public, and overrides can't narrow visibility. */
	@Override
	public void travel(Vec3 travelVector) {
	}

	// ================================================================== setup
	/** Call this right after construction, before level.addFreshEntity(...). */
	public void configure(ProjectileDefinition definition, @Nullable LivingEntity owner, Vec3 direction) {
		this.entityData.set(DATA_DEFINITION_ID, definition.id().toString());
		if (owner != null) {
			this.entityData.set(DATA_HAS_OWNER, true);
			this.entityData.set(DATA_OWNER_ID, owner.getId());
		}
		Vec3 velocity = direction.normalize().scale(definition.speed());
		this.setDeltaMovement(velocity);
		this.faceMotion();
		this.yRotO = this.getYRot();
		this.xRotO = this.getXRot();
		this.previousTickPosition = this.position();
	}

	/**
	 * Points yRot/xRot along the current velocity, in vanilla's convention (yaw 0 faces +Z and turns
	 * toward -X; positive pitch looks down) - the same one every look vector and renderer assumes.
	 * The launch code this replaced had yaw mirrored and pitch inverted, which went unnoticed only
	 * because nothing read the rotation until a projectile shape with a front needed to.
	 */
	private void faceMotion() {
		Vec3 v = this.getDeltaMovement();
		if (v.lengthSqr() < 1.0E-7)
			return; // stopped dead has no direction - keep the last one rather than snapping to 0
		this.setYRot((float) (Mth.atan2(-v.x, v.z) * (180F / Math.PI)));
		this.setXRot((float) (-Mth.atan2(v.y, v.horizontalDistance()) * (180F / Math.PI)));
	}

	public ProjectileDefinition definition() {
		return ProjectileRegistry.get(this.entityData.get(DATA_DEFINITION_ID));
	}

	/** Same as definition(), but never throws - empty if the id is missing/unrecognized. Used
	 *  by tick()'s defensive guard below. */
	private Optional<ProjectileDefinition> definitionOrEmpty() {
		String id = this.entityData.get(DATA_DEFINITION_ID);
		if (id == null || id.isEmpty())
			return Optional.empty();
		try {
			return Optional.of(ProjectileRegistry.get(id));
		} catch (IllegalStateException e) {
			return Optional.empty();
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag compound) {
		super.addAdditionalSaveData(compound);
		compound.putString("ProjectileDefinitionId", this.entityData.get(DATA_DEFINITION_ID));
		compound.putBoolean("HasOwner", this.entityData.get(DATA_HAS_OWNER));
		compound.putInt("OwnerId", this.entityData.get(DATA_OWNER_ID));
		compound.putInt("TicksAlive", this.ticksAlive);
		compound.putInt("PiercedCount", this.piercedCount);
		ListTag hitList = new ListTag();
		for (UUID id : alreadyHitEntities) {
			hitList.add(StringTag.valueOf(id.toString()));
		}
		compound.put("AlreadyHitEntities", hitList);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag compound) {
		super.readAdditionalSaveData(compound);
		if (compound.contains("ProjectileDefinitionId")) {
			this.entityData.set(DATA_DEFINITION_ID, compound.getString("ProjectileDefinitionId"));
		}
		if (compound.contains("HasOwner")) {
			this.entityData.set(DATA_HAS_OWNER, compound.getBoolean("HasOwner"));
		}
		if (compound.contains("OwnerId")) {
			this.entityData.set(DATA_OWNER_ID, compound.getInt("OwnerId"));
		}
		if (compound.contains("TicksAlive")) {
			this.ticksAlive = compound.getInt("TicksAlive");
		}
		if (compound.contains("PiercedCount")) {
			this.piercedCount = compound.getInt("PiercedCount");
		}
		if (compound.contains("AlreadyHitEntities")) {
			alreadyHitEntities.clear();
			ListTag hitList = compound.getList("AlreadyHitEntities", Tag.TAG_STRING);
			for (int i = 0; i < hitList.size(); i++) {
				try {
					alreadyHitEntities.add(UUID.fromString(hitList.getString(i)));
				} catch (IllegalArgumentException ignored) {
					// malformed entry - skip rather than fail the whole load
				}
			}
		}
	}

	@Nullable
	private LivingEntity owner() {
		if (!this.entityData.get(DATA_HAS_OWNER))
			return null;
		Entity found = this.level().getEntity(this.entityData.get(DATA_OWNER_ID));
		return found instanceof LivingEntity living ? living : null;
	}

	/** getDimensions(Pose) itself is final on LivingEntity and can't be overridden directly,
	 *  but it reads from this method underneath, which isn't final - so this is the real,
	 *  correct hook for scaling the entity's actual hitbox (shows correctly in F3, and every
	 *  other system that queries getBoundingBox() sees the right size, not just our own
	 *  collision sweep below). */
	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		EntityDimensions base = super.getDefaultDimensions(pose);
		return definitionOrEmpty().map(def -> new EntityDimensions(base.width() * def.widthScale(), base.height() * def.heightScale(), base.eyeHeight() * def.heightScale(), base.attachments(), base.fixed())).orElse(base);
	}

	// ================================================================== movement + collision
	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide())
			return;
		ticksAlive++;
		if (ownerGraceTicks > 0)
			ownerGraceTicks--;
		Optional<ProjectileDefinition> maybeDef = definitionOrEmpty();
		if (maybeDef.isEmpty()) {
			LOGGER.warn("[duality] Projectile entity {} had no valid definition id on tick - discarding instead of crashing", this.getId());
			this.discard();
			return;
		}
		ProjectileDefinition def = maybeDef.get();
		if (ticksAlive >= def.lifetimeTicks()) {
			this.discard();
			return;
		}
		// Manual gravity, entirely independent of vanilla's (now no-op'd) travel() pipeline.
		// 0 = perfectly straight flight; positive values curve it into an arc.
		if (def.gravityStrength() != 0) {
			this.setDeltaMovement(this.getDeltaMovement().add(0, -def.gravityStrength(), 0));
		}
		// Every tick, not just at launch: gravity bends the path, and a shape with a front should
		// nose down over the arc. Set after super.tick() on purpose - Mob's LookControl zeroes xRot
		// during it, and this is the value the entity tracker then sends to clients.
		this.faceMotion();
		Vec3 start = this.position();
		Vec3 end = start.add(this.getDeltaMovement());
		// previousTickPosition is only set in configure(), which runs for freshly-spawned
		// projectiles - an entity reloaded from a saved chunk (e.g. player left and rejoined
		// while it was still flying) is reconstructed straight from NBT via the constructor,
		// bypassing configure() entirely. Catch that here instead of NPEing on first tick.
		if (previousTickPosition == null) {
			previousTickPosition = start;
		}
		if (def.particleEmitter() != null && this.level() instanceof ServerLevel serverLevel) {
			// Delayed by one tick on purpose - the client always renders an interpolated
			// position lagging roughly one tick behind the server's true position. Using the
			// exact current-tick position here made particles visibly outrun the rendered
			// model on fast projectiles (fine at low speed, where that one-tick gap covers
			// almost no distance - obvious at high speed, where it covers a lot).
			Vec3 particleOrigin = previousTickPosition.add(0, this.getBbHeight() / 2.0, 0);
			def.particleEmitter().spawn(serverLevel, particleOrigin, this.getDeltaMovement(), ticksAlive);
		}
		previousTickPosition = start;
		HitResult hit = findHit(def, start, end);
		if (hit instanceof BlockHitResult blockHit) {
			resolveBlockHit(def, blockHit);
			return; // discarded
		} else if (hit instanceof EntityHitResult entityHit) {
			if (resolveEntityHit(def, entityHit)) {
				return; // pierce exhausted - discarded
			}
			// else: still has pierce left, keep flying - fall through to the AoE check and move
		}
		if (def.aoeRadius() > 0 && this.level() instanceof ServerLevel serverLevel) {
			checkAreaOfEffect(def, serverLevel);
		}
		this.setPos(end.x, end.y, end.z);
	}

	/** noClip skips the block check entirely (never returns a block hit). Entities already in
	 *  alreadyHitEntities (pierced through, or already splash-hit by AoE) are excluded, so a
	 *  slow-moving or lingering target doesn't get direct-hit repeatedly. */
	@Nullable
	private HitResult findHit(ProjectileDefinition def, Vec3 start, Vec3 end) {
		BlockHitResult blockHit = def.noClip() ? null : this.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		Vec3 blockHitPos = (blockHit != null && blockHit.getType() != HitResult.Type.MISS) ? blockHit.getLocation() : end;
		LivingEntity owner = owner();
		double closestDistSqr = Double.MAX_VALUE;
		Entity hitEntity = null;
		Vec3 hitPos = null;
		AABB sweep = this.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0);
		for (Entity candidate : this.level().getEntities(this, sweep, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
			if (ownerGraceTicks > 0 && candidate == owner)
				continue;
			if (alreadyHitEntities.contains(candidate.getUUID()))
				continue;
			AABB box = candidate.getBoundingBox().inflate(candidate.getPickRadius());
			Optional<Vec3> clip = box.clip(start, end);
			if (clip.isPresent()) {
				double distSqr = start.distanceToSqr(clip.get());
				if (distSqr < closestDistSqr) {
					closestDistSqr = distSqr;
					hitEntity = candidate;
					hitPos = clip.get();
				}
			}
		}
		boolean entityIsCloser = hitEntity != null && (blockHit == null || blockHit.getType() == HitResult.Type.MISS || closestDistSqr < start.distanceToSqr(blockHitPos));
		if (entityIsCloser)
			return new EntityHitResult(hitEntity, hitPos);
		return (blockHit != null && blockHit.getType() != HitResult.Type.MISS) ? blockHit : null;
	}

	private void resolveBlockHit(ProjectileDefinition def, BlockHitResult hit) {
		// TODO: check a MagicBarrier registry here before resolving as a normal hit, once
		// shields/wards exist - this is the natural place to intercept.
		Vec3 hitPos = hit.getLocation();
		ProjectileHitContext hitContext = new ProjectileHitContext(owner(), def, hitPos, (ServerLevel) this.level());
		def.onBlockHit().accept(hitContext);
		this.setPos(hitPos.x, hitPos.y, hitPos.z);
		this.discard();
	}

	/** Returns true if the projectile should stop here (pierce exhausted - already discarded),
	 *  false if it should keep flying (pierce remaining, or an unlimited-pierce definition). */
	private boolean resolveEntityHit(ProjectileDefinition def, EntityHitResult hit) {
		Vec3 hitPos = hit.getLocation();
		Entity target = hit.getEntity();
		alreadyHitEntities.add(target.getUUID());
		ProjectileHitContext hitContext = new ProjectileHitContext(owner(), def, hitPos, (ServerLevel) this.level());
		def.onEntityHit().accept(hitContext, target);
		piercedCount++;
		if (piercedCount > def.pierce()) {
			this.setPos(hitPos.x, hitPos.y, hitPos.z);
			this.discard();
			return true;
		}
		return false;
	}

	/** Splash-damages any not-yet-hit LivingEntity within aoeRadius of the current hitbox,
	 *  every tick, without ever stopping the projectile. Each entity only gets hit once per
	 *  projectile - Set#add returning false means it was already present. */
	private void checkAreaOfEffect(ProjectileDefinition def, ServerLevel level) {
		LivingEntity owner = owner();
		AABB aoeBox = this.getBoundingBox().inflate(def.aoeRadius());
		for (Entity candidate : level.getEntities(this, aoeBox, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
			if (ownerGraceTicks > 0 && candidate == owner)
				continue;
			if (!alreadyHitEntities.add(candidate.getUUID()))
				continue;
			ProjectileHitContext hitContext = new ProjectileHitContext(owner, def, candidate.position(), level);
			def.onAreaHit().accept(hitContext, candidate);
		}
	}

	/** The tiny collision hitbox (correct, for precise projectile collision) would otherwise
	 *  make vanilla's default distance-based render culling kick in way too close - Minecraft
	 *  bases that cutoff on bounding box size, and a small box means a short cutoff, regardless
	 *  of how big the actual visual model is. Override with a flat, generous distance instead. */
	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		double renderDistance = 64.0; // blocks
		return distance < renderDistance * renderDistance;
	}

	// ================================================================== neutralizing "mob" behavior
	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_DEFINITION_ID, "");
		builder.define(DATA_HAS_OWNER, false);
		builder.define(DATA_OWNER_ID, -1);
	}

	/** configure() (which calls refreshDimensions()) only ever runs on the server - the client
	 *  builds its own separate copy of this entity and only learns the real definition id later,
	 *  via a normal synced-data packet. Without this, the client's dimensions/hitbox (what F3
	 *  and the renderer actually show) stay stuck at the unscaled default forever, even though
	 *  the server's own collision math is scaled correctly. This is the standard vanilla hook
	 *  for reacting to a synced value changing on either side, including from network sync. */
	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_DEFINITION_ID.equals(key)) {
			this.refreshDimensions();
		}
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean causeFallDamage(float l, float d, DamageSource source) {
		return false;
	}

	@Override
	protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public void setNoGravity(boolean ignored) {
		super.setNoGravity(true);
	}

	/** Projectiles aren't damageable by anything by default - they're removed via lifetime or
	 *  collision only. If you want some projectiles to be shootable-down (e.g. intercepting a
	 *  fireball with an arrow, or another spell), this is the one method to change - make it
	 *  conditional on definition().param(...) or similar instead of a flat `return false`. */
	@Override
	public boolean hurt(DamageSource damagesource, float amount) {
		return false;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.MAX_HEALTH, 1).add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.FOLLOW_RANGE, 0).add(Attributes.STEP_HEIGHT, 0).add(Attributes.KNOCKBACK_RESISTANCE, 100);
	}
}