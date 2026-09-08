package net.spidrotech.duality.entity;

import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;

/**
 * Shared base for every "stagnant visual" effect - a purely cosmetic, server-synced entity with
 * no AI, no physics, and no combat behavior, whose model AND texture are chosen per-instance via
 * two synced fields (DATA_model, DATA_texture) instead of being fixed at the class level. Same
 * "one shared base, per-instance appearance" pattern as AbilityProjectileBase and
 * LightningVisualBase - MCreator owns model/renderer/registration for each Blockbench export
 * you add, this class owns the actual runtime behavior (or rather, deliberate lack of it).
 *
 * Usage: spawn one, set DATA_texture (a bare name under textures/entities/, or a full
 * "namespace:path" string) and DATA_model (whatever int id StagnantVisualRenderer maps to a
 * baked model) before adding it to the level, then drive its position/lifetime however your
 * ability logic needs to.
 *
 * Deliberately switched from Monster to PathfinderMob directly - Monster brings hostile-mob-
 * specific behavior (aggro sounds, threat-tuned despawn rules) that has nothing to do with a
 * cosmetic effect, and the natural-spawn registration MCreator generated (init(), tied to
 * RegisterSpawnPlacementsEvent) has been removed entirely - this entity should ONLY ever be
 * spawned deliberately by ability code, never naturally.
 */
public class StagnantVisualEntity extends PathfinderMob {
	public static final EntityDataAccessor<String> DATA_texture = SynchedEntityData.defineId(StagnantVisualEntity.class, EntityDataSerializers.STRING);
	public static final EntityDataAccessor<Integer> DATA_model = SynchedEntityData.defineId(StagnantVisualEntity.class, EntityDataSerializers.INT);

	public StagnantVisualEntity(EntityType<StagnantVisualEntity> type, Level world) {
		super(type, world);
		this.xpReward = 0;
		this.setNoAi(true);
		this.setPersistenceRequired();
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_texture, "");
		builder.define(DATA_model, 0);
	}

	/** No-op, matching AbilityProjectileBase/LightningVisualBase - vanilla's travel() would
	 *  otherwise apply gravity/input movement every tick, fighting whatever ability logic is
	 *  positioning this entity manually. Must be public - LivingEntity#travel is public, and
	 *  overrides can't narrow visibility. */
	@Override
	public void travel(Vec3 travelVector) {
	}

	@Override
	protected void registerGoals() {
		// Deliberately empty - setNoAi(true) above means none of these would run anyway, but
		// leaving the list empty (rather than MCreator's combat goals) keeps that explicit.
	}

	@Override
	public void addAdditionalSaveData(CompoundTag compound) {
		super.addAdditionalSaveData(compound);
		compound.putString("Datatexture", this.entityData.get(DATA_texture));
		compound.putInt("Datamodel", this.entityData.get(DATA_model));
	}

	@Override
	public void readAdditionalSaveData(CompoundTag compound) {
		super.readAdditionalSaveData(compound);
		if (compound.contains("Datatexture"))
			this.entityData.set(DATA_texture, compound.getString("Datatexture"));
		if (compound.contains("Datamodel"))
			this.entityData.set(DATA_model, compound.getInt("Datamodel"));
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

	@Override
	public boolean hurt(DamageSource damagesource, float amount) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entityIn) {
	}

	@Override
	protected void pushEntities() {
	}

	public static void init(RegisterSpawnPlacementsEvent event) {
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.MAX_HEALTH, 1).add(Attributes.ATTACK_DAMAGE, 0).add(Attributes.FOLLOW_RANGE, 0).add(Attributes.STEP_HEIGHT, 0).add(Attributes.KNOCKBACK_RESISTANCE, 100);
	}
}