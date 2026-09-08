package net.spidrotech.duality;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.core.BlockPos;

/**
 * Shared behavior for a purely-visual procedural lightning bolt entity. The actual entity still
 * needs to be created through MCreator's wizard first (any placeholder model/texture is fine -
 * see LightningBoltRenderer, which replaces whatever MCreator generates and never uses the
 * model), then modified to extend this instead of PathfinderMob directly - same pattern as
 * AbilityProjectileEntity/AbilityProjectileBase.
 *
 * Deals no damage and has no collision - LightningStrikeAbility applies damage separately,
 * directly to whatever its chain-targeting logic selects, completely decoupled from whether this
 * entity even exists. This is purely the visual: renders a procedural bolt from its own position
 * to a synced end point, fading out over its lifetime, then discards itself.
 */
public abstract class LightningVisualBase extends PathfinderMob {
	private static final EntityDataAccessor<Float> DATA_END_X = SynchedEntityData.defineId(LightningVisualBase.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_END_Y = SynchedEntityData.defineId(LightningVisualBase.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_END_Z = SynchedEntityData.defineId(LightningVisualBase.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> DATA_INNER_COLOR = SynchedEntityData.defineId(LightningVisualBase.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_OUTER_COLOR = SynchedEntityData.defineId(LightningVisualBase.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_GLOW_COLOR = SynchedEntityData.defineId(LightningVisualBase.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_DURATION_TICKS = SynchedEntityData.defineId(LightningVisualBase.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SEED = SynchedEntityData.defineId(LightningVisualBase.class, EntityDataSerializers.INT);
	private int ticksAlive = 0;

	protected LightningVisualBase(EntityType<? extends LightningVisualBase> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setNoAi(true);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Call right after construction (and after setPos), before level.addFreshEntity(...).
	 *  This entity's own position is the bolt's start point; end is where it visually reaches to. */
	public void configure(Vec3 end, LightningColorScheme colors, int durationTicks, int seed) {
		this.entityData.set(DATA_END_X, (float) end.x);
		this.entityData.set(DATA_END_Y, (float) end.y);
		this.entityData.set(DATA_END_Z, (float) end.z);
		this.entityData.set(DATA_INNER_COLOR, colors.innerColor());
		this.entityData.set(DATA_OUTER_COLOR, colors.outerColor());
		this.entityData.set(DATA_GLOW_COLOR, colors.glowColor());
		this.entityData.set(DATA_DURATION_TICKS, durationTicks);
		this.entityData.set(DATA_SEED, seed);
	}

	public Vec3 endPoint() {
		return new Vec3(this.entityData.get(DATA_END_X), this.entityData.get(DATA_END_Y), this.entityData.get(DATA_END_Z));
	}

	public LightningColorScheme colors() {
		return new LightningColorScheme(this.entityData.get(DATA_INNER_COLOR), this.entityData.get(DATA_OUTER_COLOR), this.entityData.get(DATA_GLOW_COLOR));
	}

	public int durationTicks() {
		return this.entityData.get(DATA_DURATION_TICKS);
	}

	public int seed() {
		return this.entityData.get(DATA_SEED);
	}

	/** 1.0 at spawn, fading to 0.0 at durationTicks - used by the renderer for alpha. */
	public float lifeFraction(float partialTick) {
		int duration = durationTicks();
		if (duration <= 0)
			return 1f;
		float elapsed = ticksAlive + partialTick;
		return Math.max(0f, 1f - (elapsed / duration));
	}

	@Override
	public void tick() {
		super.tick();
		ticksAlive++;
		if (!this.level().isClientSide() && ticksAlive >= durationTicks()) {
			this.discard();
		}
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_END_X, 0f);
		builder.define(DATA_END_Y, 0f);
		builder.define(DATA_END_Z, 0f);
		builder.define(DATA_INNER_COLOR, 0xFFFFFFFF);
		builder.define(DATA_OUTER_COLOR, 0xFFAACCFF);
		builder.define(DATA_GLOW_COLOR, 0xFF3388FF);
		builder.define(DATA_DURATION_TICKS, 10);
		builder.define(DATA_SEED, 0);
	}

	// ---- neutralizing "mob" behavior - same category of overrides as AbilityProjectileBase,
	// since MCreator's wizard produces a PathfinderMob regardless of how little of that we need ----
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

	@Override
	public boolean hurt(DamageSource damagesource, float amount) {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 128.0 * 128.0; // generous - a dramatic effect should read from far away
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		Vec3 start = this.position();
		Vec3 end = this.endPoint();
		double minX = Math.min(start.x, end.x);
		double minY = Math.min(start.y, end.y);
		double minZ = Math.min(start.z, end.z);
		double maxX = Math.max(start.x, end.x);
		double maxY = Math.max(start.y, end.y);
		double maxZ = Math.max(start.z, end.z);
		double margin = Math.max(1.5, start.distanceTo(end) * 0.15);
		return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(margin);
	}

	/** MCreator's wizard will generate its own createAttributes() on the concrete entity - feel
	 *  free to use that instead of this one, or point it here for consistency. None of these
	 *  values matter much since this entity has no AI and can't be damaged. */
	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.MAX_HEALTH, 1).add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.FOLLOW_RANGE, 0).add(Attributes.STEP_HEIGHT, 0).add(Attributes.KNOCKBACK_RESISTANCE, 100);
	}
}